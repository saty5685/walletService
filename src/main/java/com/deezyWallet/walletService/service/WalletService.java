package com.deezyWallet.walletService.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deezyWallet.walletService.constants.WalletConstants;
import com.deezyWallet.walletService.dto.request.BlockFundsRequest;
import com.deezyWallet.walletService.dto.request.CreditRequest;
import com.deezyWallet.walletService.dto.request.DebitRequest;
import com.deezyWallet.walletService.dto.request.UpdateLimitsRequest;
import com.deezyWallet.walletService.dto.response.BalanceResponse;
import com.deezyWallet.walletService.dto.response.PagedResponse;
import com.deezyWallet.walletService.dto.response.WalletResponse;
import com.deezyWallet.walletService.dto.response.WalletStatusResponse;
import com.deezyWallet.walletService.dto.response.WalletTransactionResponse;
import com.deezyWallet.walletService.entity.Wallet;
import com.deezyWallet.walletService.entity.WalletTransaction;
import com.deezyWallet.walletService.enums.TransactionTypeEnum;
import com.deezyWallet.walletService.enums.WalletStatusEnum;
import com.deezyWallet.walletService.event.EventFactory;
import com.deezyWallet.walletService.event.producer.WalletEventProducer;
import com.deezyWallet.walletService.exception.DuplicateOperationException;
import com.deezyWallet.walletService.exception.InsufficientBalanceException;
import com.deezyWallet.walletService.exception.WalletAccessDeniedException;
import com.deezyWallet.walletService.exception.WalletFrozenException;
import com.deezyWallet.walletService.exception.WalletNotFoundException;
import com.deezyWallet.walletService.mapper.WalletMapper;
import com.deezyWallet.walletService.repository.WalletRepository;
import com.deezyWallet.walletService.repository.WalletTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WalletService — core business logic orchestrator.
 *
 * DEPENDENCY GRAPH:
 *   WalletService
 *     ├─ WalletRepository              (DB — wallet CRUD + locking)
 *     ├─ WalletTransactionRepository   (DB — immutable tx records)
 *     ├─ WalletLimitService            (DB — limit enforcement + recording)
 *     ├─ BalanceCacheService           (Redis — cache + processing locks)
 *     ├─ WalletEventProducer           (Kafka — outbound events)
 *     ├─ WalletMapper                  (entity → DTO)
 *     └─ EventFactory                  (builds outbound event POJOs)
 *
 * TRANSACTION BOUNDARIES:
 *   @Transactional           debit, credit, blockFunds, unblockFunds,
 *                            provisionWallet, activateWallet, freezeWallet,
 *                            unfreezeWallet, updateLimits
 *   @Transactional(readOnly) getWalletByUserId, getBalance, getTransactionHistory,
 *                            getBalanceInternal, getWalletStatusEnum
 *   NO @Transactional        validateOwnership (read-only, no tx needed)
 *
 * THREE SECURITY LAYERS enforced here:
 *   Layer 1 — JwtAuthFilter:     token valid?
 *   Layer 2 — @PreAuthorize:     role allowed for this endpoint?
 *   Layer 3 — validateOwnership: does this wallet belong to THIS user?
 *   Without Layer 3: valid USER token could access any wallet by ID (IDOR).
 *
 * LOCKING STRATEGY:
 *   DEBIT  → findByIdWithLock()  PESSIMISTIC_WRITE (SELECT FOR UPDATE)
 *            Serialises concurrent debits at DB level — no race condition possible.
 *   CREDIT → findById() + @Version  OPTIMISTIC
 *            Concurrent credits compete; loser retries via @Retryable(3 attempts).
 *   READ   → no lock, cache-first via BalanceCacheService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

	private final WalletRepository            walletRepository;
	private final WalletTransactionRepository transactionRepository;
	private final WalletLimitService          limitService;
	private final BalanceCacheService         cacheService;
	private final WalletEventProducer         eventProducer;
	private final WalletMapper                walletMapper;
	private final EventFactory                eventFactory;

	// ─────────────────────────────────────────────────────────────────────────
	// WALLET LIFECYCLE
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Provision new INACTIVE wallet for a newly registered user.
	 * Called by UserEventConsumer on USER_REGISTERED.
	 *
	 * IDEMPOTENT: existsByUserId() guard prevents duplicate wallets
	 * if USER_REGISTERED is redelivered by Kafka.
	 */
	@Transactional
	public void provisionWallet(String userId) {
		if (walletRepository.existsByUserId(userId)) {
			log.info("Wallet already exists for userId={} — skipping (idempotent)", userId);
			return;
		}

		Wallet wallet = Wallet.builder()
				.userId(userId)
				.walletNumber(generateWalletNumber())
				.balance(WalletConstants.ZERO)
				.blockedBalance(WalletConstants.ZERO)
				.currency(WalletConstants.DEFAULT_CURRENCY)
				.status(WalletStatusEnum.INACTIVE)
				.dailyLimit(WalletConstants.DEFAULT_DAILY_LIMIT)
				.monthlyLimit(WalletConstants.DEFAULT_MONTHLY_LIMIT)
				.build();

		wallet = walletRepository.save(wallet);
		cacheService.cacheUserWalletId(userId, wallet.getId());

		log.info("Wallet provisioned: walletId={} userId={} walletNumber={}",
				wallet.getId(), userId, wallet.getWalletNumber());

		eventProducer.publish(eventFactory.buildProvisionedEvent(wallet));
	}

	/** Activate wallet: INACTIVE → ACTIVE. Called on USER_VERIFIED. */
	@Transactional
	public void activateWallet(String userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> new WalletNotFoundException("No wallet for userId=" + userId));

		if (wallet.getStatus() == WalletStatusEnum.ACTIVE) {
			log.info("Wallet already ACTIVE userId={} — skipping (idempotent)", userId);
			return;
		}

		WalletStatusEnum previous = wallet.getStatus();
		wallet.setStatus(WalletStatusEnum.ACTIVE);
		walletRepository.save(wallet);

		log.info("Wallet ACTIVATED: walletId={} userId={}", wallet.getId(), userId);
		eventProducer.publish(
				eventFactory.buildStatusChangedEvent(wallet, previous, "SYSTEM", "USER_VERIFIED"));
	}

	/** Freeze wallet: ACTIVE → FROZEN. Called on USER_SUSPENDED or admin action. */
	@Transactional
	public void freezeWallet(String userId, String changedBy, String reason) {
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> new WalletNotFoundException("No wallet for userId=" + userId));

		if (wallet.getStatus() == WalletStatusEnum.FROZEN) {
			log.info("Wallet already FROZEN userId={} — skipping (idempotent)", userId);
			return;
		}

		WalletStatusEnum previous = wallet.getStatus();
		wallet.setStatus(WalletStatusEnum.FROZEN);
		walletRepository.save(wallet);
		cacheService.evictBalance(wallet.getId()); // frozen wallet must not serve stale balance

		log.info("Wallet FROZEN: walletId={} userId={} by={} reason={}",
				wallet.getId(), userId, changedBy, reason);
		eventProducer.publish(
				eventFactory.buildStatusChangedEvent(wallet, previous, changedBy, reason));
	}

	/** Unfreeze wallet: FROZEN → ACTIVE. Admin-only. */
	@Transactional
	public void unfreezeWallet(String walletId, String changedBy) {
		Wallet wallet = walletRepository.findById(walletId)
				.orElseThrow(() -> new WalletNotFoundException(walletId));

		WalletStatusEnum previous = wallet.getStatus();
		wallet.setStatus(WalletStatusEnum.ACTIVE);
		walletRepository.save(wallet);

		log.info("Wallet UNFROZEN: walletId={} by={}", walletId, changedBy);
		eventProducer.publish(
				eventFactory.buildStatusChangedEvent(wallet, previous, changedBy, "ADMIN_UNFREEZE"));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// DEBIT  — pessimistic lock, synchronous limit check
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Debit the wallet.
	 *
	 * FULL EXECUTION SEQUENCE (single @Transactional boundary):
	 *
	 *   1. Redis SETNX — fast idempotency check (acquireProcessingLock)
	 *   2. DB EXISTS   — durable idempotency check (existsByIdempotencyKey)
	 *   3. Limit check + record  (WalletLimitService.checkAndRecord — Propagation.MANDATORY)
	 *   4. SELECT FOR UPDATE     (findByIdWithLock — pessimistic lock)
	 *   5. Status validation     (wallet must be ACTIVE)
	 *   6. Balance validation    (balance >= amount)
	 *   7. balance -= amount     (in-memory mutation)
	 *   8. walletRepository.save (persist new balance)
	 *   9. transactionRepository.save (persist immutable tx record)
	 *
	 *   After @Transactional commits:
	 *  10. cacheService.evictBalance    (stale balance gone from Redis)
	 *  11. cacheService.releaseProcessingLock (DB is durable dedup now)
	 *  12. eventProducer.publish WALLET_DEBITED
	 *
	 * Steps 3–9 are atomic (one DB transaction).
	 * Steps 10–12 are post-commit (intentionally outside the transaction).
	 *
	 * NOT @Retryable — pessimistic lock prevents OptimisticLockException.
	 * Business failures must not be retried.
	 */
	@Transactional
	public WalletTransaction debit(DebitRequest request) {
		String walletId       = request.getWalletId();
		String idempotencyKey = request.getIdempotencyKey();

		log.info("Debit started: walletId={} amount={} idempotencyKey={}",
				walletId, request.getAmount(), idempotencyKey);

		// ── 1. Redis fast-path dedup ───────────────────────────────────────
		if (!cacheService.acquireProcessingLock(idempotencyKey)) {
			WalletTransaction existing =
					transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
			throw new DuplicateOperationException(idempotencyKey, existing);
		}

		try {
			// ── 2. DB idempotency check ────────────────────────────────────
			if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
				WalletTransaction existing =
						transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
				log.info("Duplicate debit (DB). idempotencyKey={}", idempotencyKey);
				throw new DuplicateOperationException(idempotencyKey, existing);
			}

			// ── 3. Limit check + record (Propagation.MANDATORY — same tx) ─
			// Lightweight read for limit thresholds (no lock needed here)
			Wallet walletForLimits = walletRepository.findById(walletId)
					.orElseThrow(() -> new WalletNotFoundException(walletId));
			limitService.checkAndRecord(walletForLimits, request.getAmount());

			// ── 4. Acquire pessimistic DB lock for balance mutation ─────────
			Wallet wallet = walletRepository.findByIdWithLock(walletId)
					.orElseThrow(() -> new WalletNotFoundException(walletId));

			// ── 5. Status check ────────────────────────────────────────────
			if (!wallet.isOperational()) {
				throw new WalletFrozenException(walletId, wallet.getStatus());
			}

			// ── 6. Balance check ───────────────────────────────────────────
			if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
				throw new InsufficientBalanceException(
						walletId, wallet.getBalance(), request.getAmount());
			}

			// ── 7 + 8. Deduct and save ─────────────────────────────────────
			BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
			wallet.setBalance(newBalance);
			walletRepository.save(wallet);

			// ── 9. Persist immutable transaction record ────────────────────
			WalletTransaction saved = transactionRepository.save(
					WalletTransaction.builder()
							.walletId(walletId)
							.type(TransactionTypeEnum.DEBIT)
							.amount(request.getAmount())
							.balanceAfter(newBalance)
							.currency(wallet.getCurrency())
							.description(request.getDescription())
							.referenceId(request.getTransactionRef())
							.idempotencyKey(idempotencyKey)
							.metadata(request.getMetadata())
							.build()
			);

			log.info("Debit committed: walletId={} amount={} newBalance={} txId={}",
					walletId, request.getAmount(), newBalance, saved.getId());

			// ── 10–12. Post-commit: evict cache, release lock, publish event ─
			performPostDebitActions(wallet, request, saved);
			return saved;

		} catch (DuplicateOperationException | WalletFrozenException |
				 InsufficientBalanceException | WalletNotFoundException ex) {
			// Business exceptions — release Redis lock immediately so retries work
			cacheService.releaseProcessingLock(idempotencyKey);
			throw ex;
			// System exceptions (RuntimeException) — do NOT release lock.
			// Let TTL (10s) expire it — retry should re-process from scratch.
		}
	}

	private void performPostDebitActions(Wallet wallet, DebitRequest req, WalletTransaction saved) {
		cacheService.evictBalance(wallet.getId());
		cacheService.releaseProcessingLock(req.getIdempotencyKey());
		eventProducer.publish(eventFactory.buildDebitedEvent(wallet, req));
		log.info("Post-debit done: walletId={} txId={}", wallet.getId(), saved.getId());
	}

	// ─────────────────────────────────────────────────────────────────────────
	// CREDIT — optimistic lock, no limit check
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Credit the wallet.
	 *
	 * Identical structure to debit EXCEPT:
	 *   - No limit check (limits track outgoing spend only)
	 *   - No pessimistic lock — uses @Version optimistic locking
	 *   - @Retryable on OptimisticLockingFailureException (3 attempts, 100ms backoff)
	 *
	 * Two concurrent credits on the same wallet:
	 *   Both read version=5. One saves → version becomes 6.
	 *   Other tries to save → Hibernate sees version mismatch → exception.
	 *   @Retryable kicks in → second thread retries → reads version=6 → saves.
	 */
	@Transactional
	@Retryable(
			retryFor    = org.springframework.orm.ObjectOptimisticLockingFailureException.class,
			maxAttempts = 3,
			backoff     = @Backoff(delay = 100, multiplier = 2)
	)
	public WalletTransaction credit(CreditRequest request) {
		String walletId       = request.getWalletId();
		String idempotencyKey = request.getIdempotencyKey();

		log.info("Credit started: walletId={} amount={} idempotencyKey={}",
				walletId, request.getAmount(), idempotencyKey);

		if (!cacheService.acquireProcessingLock(idempotencyKey)) {
			WalletTransaction existing =
					transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
			throw new DuplicateOperationException(idempotencyKey, existing);
		}

		try {
			if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
				WalletTransaction existing =
						transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
				throw new DuplicateOperationException(idempotencyKey, existing);
			}

			// No lock — optimistic via @Version
			Wallet wallet = walletRepository.findById(walletId)
					.orElseThrow(() -> new WalletNotFoundException(walletId));

			if (!wallet.canReceive()) {
				throw new WalletFrozenException(walletId, wallet.getStatus());
			}

			BigDecimal newBalance = wallet.getBalance().add(request.getAmount());
			wallet.setBalance(newBalance);
			walletRepository.save(wallet); // throws ObjectOptimisticLockingFailureException on conflict

			WalletTransaction saved = transactionRepository.save(
					WalletTransaction.builder()
							.walletId(walletId)
							.type(TransactionTypeEnum.CREDIT)
							.amount(request.getAmount())
							.balanceAfter(newBalance)
							.currency(wallet.getCurrency())
							.description(request.getDescription())
							.referenceId(request.getTransactionRef())
							.idempotencyKey(idempotencyKey)
							.metadata(request.getMetadata())
							.build()
			);

			log.info("Credit committed: walletId={} amount={} newBalance={} txId={}",
					walletId, request.getAmount(), newBalance, saved.getId());

			performPostCreditActions(wallet, request, saved);
			return saved;

		} catch (DuplicateOperationException | WalletFrozenException | WalletNotFoundException ex) {
			cacheService.releaseProcessingLock(idempotencyKey);
			throw ex;
		}
	}

	private void performPostCreditActions(Wallet wallet, CreditRequest req, WalletTransaction saved) {
		cacheService.evictBalance(wallet.getId());
		cacheService.releaseProcessingLock(req.getIdempotencyKey());
		eventProducer.publish(eventFactory.buildCreditedEvent(wallet, req));
		log.info("Post-credit done: walletId={} txId={}", wallet.getId(), saved.getId());
	}

	// ─────────────────────────────────────────────────────────────────────────
	// BLOCK / UNBLOCK FUNDS (Saga escrow pattern)
	// ─────────────────────────────────────────────────────────────────────────

	/** Move amount from balance → blockedBalance. PESSIMISTIC lock (same as debit). */
	@Transactional
	public void blockFunds(BlockFundsRequest request) {
		Wallet wallet = walletRepository.findByIdWithLock(request.getWalletId())
				.orElseThrow(() -> new WalletNotFoundException(request.getWalletId()));

		if (!wallet.isOperational()) throw new WalletFrozenException(request.getWalletId(), wallet.getStatus());
		if (wallet.getBalance().compareTo(request.getAmount()) < 0)
			throw new InsufficientBalanceException(request.getWalletId(), wallet.getBalance(), request.getAmount());

		wallet.setBalance(wallet.getBalance().subtract(request.getAmount()));
		wallet.setBlockedBalance(wallet.getBlockedBalance().add(request.getAmount()));
		walletRepository.save(wallet);
		cacheService.evictBalance(wallet.getId());

		log.info("Funds BLOCKED: walletId={} amount={}", request.getWalletId(), request.getAmount());
	}

	/** Move amount from blockedBalance → balance. Allowed even on FROZEN (compensation). */
	@Transactional
	public void unblockFunds(BlockFundsRequest request) {
		Wallet wallet = walletRepository.findByIdWithLock(request.getWalletId())
				.orElseThrow(() -> new WalletNotFoundException(request.getWalletId()));

		// Never unblock more than what's blocked (safety floor)
		BigDecimal toUnblock = request.getAmount().min(wallet.getBlockedBalance());
		wallet.setBlockedBalance(wallet.getBlockedBalance().subtract(toUnblock));
		wallet.setBalance(wallet.getBalance().add(toUnblock));
		walletRepository.save(wallet);
		cacheService.evictBalance(wallet.getId());

		log.info("Funds UNBLOCKED: walletId={} amount={}", request.getWalletId(), toUnblock);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// READ OPERATIONS
	// ─────────────────────────────────────────────────────────────────────────

	/** Full wallet details including computed limit remainders. */
	@Transactional(readOnly = true)
	public WalletResponse getWalletByUserId(String userId) {
		Wallet wallet = findWalletByUserId(userId);
		BigDecimal dailySpent   = limitService.getDailySpent(wallet.getId());
		BigDecimal monthlySpent = limitService.getMonthlySpent(wallet.getId());
		return walletMapper.toResponse(wallet, dailySpent, monthlySpent);
	}

	/** Balance — Redis cache-first, DB fallback. source field = "CACHE" or "DB". */
	@Transactional(readOnly = true)
	public BalanceResponse getBalance(String userId) {
		String walletId = resolveWalletId(userId);
		return cacheService.getCachedBalance(walletId)
				.map(cached -> BalanceResponse.of(walletId, cached, "CACHE"))
				.orElseGet(() -> {
					Wallet wallet = walletRepository.findById(walletId)
							.orElseThrow(() -> new WalletNotFoundException(walletId));
					cacheService.cacheBalance(walletId, wallet.getBalance());
					return walletMapper.toBalanceResponse(wallet, "DB");
				});
	}

	/** Paginated transaction history. Page size capped at MAX_PAGE_SIZE. */
	@Transactional(readOnly = true)
	public PagedResponse<WalletTransactionResponse> getTransactionHistory(
			String userId, int page, int size) {
		String walletId = resolveWalletId(userId);
		int    safeSize = Math.min(size, WalletConstants.MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<WalletTransaction> txPage =
				transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
		return walletMapper.toTransactionPagedResponse(txPage);
	}

	/** Internal balance — for Transaction/Ledger Services. No ownership check. */
	@Transactional(readOnly = true)
	public BalanceResponse getBalanceInternal(String walletId) {
		return cacheService.getCachedBalance(walletId)
				.map(cached -> BalanceResponse.of(walletId, cached, "CACHE"))
				.orElseGet(() -> {
					Wallet wallet = walletRepository.findById(walletId)
							.orElseThrow(() -> new WalletNotFoundException(walletId));
					cacheService.cacheBalance(walletId, wallet.getBalance());
					return walletMapper.toBalanceResponse(wallet, "DB");
				});
	}

	/** Status check — for internal service pre-transaction validation. */
	@Transactional(readOnly = true)
	public WalletStatusResponse getWalletStatusEnum(String walletId) {
		Wallet wallet = walletRepository.findById(walletId)
				.orElseThrow(() -> new WalletNotFoundException(walletId));
		return walletMapper.toStatusResponse(wallet);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// LIMIT MANAGEMENT
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Update spend limits. Cross-field rule: dailyLimit <= monthlyLimit.
	 * Both fields required — partial update intentionally blocked.
	 */
	@Transactional
	public WalletResponse updateLimits(String userId, UpdateLimitsRequest request) {
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> WalletNotFoundException.byUserId(userId));

		if (request.getDailyLimit().compareTo(request.getMonthlyLimit()) > 0) {
			throw new IllegalArgumentException("Daily limit cannot exceed monthly limit");
		}

		wallet.setDailyLimit(request.getDailyLimit());
		wallet.setMonthlyLimit(request.getMonthlyLimit());
		walletRepository.save(wallet);

		log.info("Limits updated: walletId={} daily={} monthly={}",
				wallet.getId(), request.getDailyLimit(), request.getMonthlyLimit());

		BigDecimal dailySpent   = limitService.getDailySpent(wallet.getId());
		BigDecimal monthlySpent = limitService.getMonthlySpent(wallet.getId());
		return walletMapper.toResponse(wallet, dailySpent, monthlySpent);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// SECURITY — OWNERSHIP VALIDATION (Layer 3)
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Layer 3 IDOR protection — ensures walletId belongs to requestingUserId.
	 * Throws WalletAccessDeniedException with vague message (no wallet existence hint).
	 * Called from controllers when walletId is a path variable.
	 */
	public void validateOwnership(String walletId, String requestingUserId) {
		Wallet wallet = walletRepository.findById(walletId)
				.orElseThrow(() -> new WalletNotFoundException(walletId));
		if (!wallet.getUserId().equals(requestingUserId)) {
			log.warn("IDOR attempt: walletId={} requestingUserId={}", walletId, requestingUserId);
			throw new WalletAccessDeniedException(walletId, requestingUserId);
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// PRIVATE HELPERS
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Resolve userId → walletId (Redis cache-first, 5 min TTL).
	 * This mapping is immutable — a user always owns the same wallet.
	 */
	private String resolveWalletId(String userId) {
		return cacheService.getCachedWalletId(userId)
				.orElseGet(() -> {
					String walletId = walletRepository.findWalletIdByUserId(userId)
							.orElseThrow(() -> WalletNotFoundException.byUserId(userId));
					cacheService.cacheUserWalletId(userId, walletId);
					return walletId;
				});
	}

	private Wallet findWalletByUserId(String userId) {
		return walletRepository.findByUserId(userId)
				.orElseThrow(() -> WalletNotFoundException.byUserId(userId));
	}

	/**
	 * Generates human-readable wallet number.
	 * Format: WLT-YYYYMMDD-XXXXXXXX  e.g. "WLT-20240308-A3F7B2C1"
	 * DB UNIQUE constraint on wallet_number is the final collision guard.
	 */
	private String generateWalletNumber() {
		String datePart   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
		return WalletConstants.WALLET_NUMBER_PREFIX + "-" + datePart + "-" + randomPart;
	}
}
