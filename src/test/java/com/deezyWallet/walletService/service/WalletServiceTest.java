package com.deezyWallet.walletService.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deezyWallet.walletService.constants.WalletConstants;
import com.deezyWallet.walletService.dto.request.BlockFundsRequest;
import com.deezyWallet.walletService.dto.request.CreditRequest;
import com.deezyWallet.walletService.dto.request.DebitRequest;
import com.deezyWallet.walletService.dto.request.UpdateLimitsRequest;
import com.deezyWallet.walletService.dto.response.BalanceResponse;
import com.deezyWallet.walletService.dto.response.WalletResponse;
import com.deezyWallet.walletService.entity.Wallet;
import com.deezyWallet.walletService.entity.WalletTransaction;
import com.deezyWallet.walletService.enums.TransactionTypeEnum;
import com.deezyWallet.walletService.enums.WalletStatusEnum;
import com.deezyWallet.walletService.event.EventFactory;
import com.deezyWallet.walletService.event.outbound.WalletCreditedEvent;
import com.deezyWallet.walletService.event.outbound.WalletDebitedEvent;
import com.deezyWallet.walletService.event.outbound.WalletProvisionedEvent;
import com.deezyWallet.walletService.event.producer.WalletEventProducer;
import com.deezyWallet.walletService.exception.DuplicateOperationException;
import com.deezyWallet.walletService.exception.InsufficientBalanceException;
import com.deezyWallet.walletService.exception.WalletAccessDeniedException;
import com.deezyWallet.walletService.exception.WalletFrozenException;
import com.deezyWallet.walletService.exception.WalletNotFoundException;
import com.deezyWallet.walletService.mapper.WalletMapper;
import com.deezyWallet.walletService.repository.WalletRepository;
import com.deezyWallet.walletService.repository.WalletTransactionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WalletService unit tests.
 *
 * STRATEGY:
 *   Every dependency is mocked — no DB, no Redis, no Kafka.
 *   Tests verify BEHAVIOUR: what is called, in what order, with what args.
 *   Tests verify EXCEPTIONS: correct type thrown with correct fields.
 *   Tests verify STATE CHANGES: what the saved entity looks like.
 *
 * STRUCTURE:
 *   @Nested classes group tests by method — easier to find failures in CI output.
 *   Each @Nested class sets up its own fixtures relevant to that method.
 *
 * NAMING CONVENTION:
 *   methodName_scenario_expectedOutcome
 *   e.g. debit_insufficientBalance_throwsInsufficientBalanceException
 *
 * WHY @ExtendWith(MockitoExtension.class) instead of @SpringBootTest?
 *   @SpringBootTest loads the full ApplicationContext (~3s startup).
 *   MockitoExtension loads in <10ms — the whole unit suite runs in ~2s.
 *   We don't need Spring's DI here — @InjectMocks does constructor injection.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService")
class WalletServiceTest {

	// ── Mocks ─────────────────────────────────────────────────────────────────
	@Mock WalletRepository            walletRepository;
	@Mock WalletTransactionRepository transactionRepository;
	@Mock WalletLimitService          limitService;
	@Mock BalanceCacheService         cacheService;
	@Mock
	WalletEventProducer eventProducer;
	@Mock WalletMapper                walletMapper;
	@Mock EventFactory                eventFactory;

	@InjectMocks
	WalletService walletService;

	// ── Shared fixtures ───────────────────────────────────────────────────────

	private static final String WALLET_ID        = UUID.randomUUID().toString();
	private static final String USER_ID          = UUID.randomUUID().toString();
	private static final String IDEMPOTENCY_KEY  = UUID.randomUUID().toString();
	private static final String TRANSACTION_REF  = "TXN-20240308-ABCD1234";

	/** Builds a standard ACTIVE wallet. Mutated per test as needed. */
	private Wallet activeWallet() {
		return Wallet.builder()
				.id(WALLET_ID)
				.userId(USER_ID)
				.walletNumber("WLT-20240308-ABCD1234")
				.balance(new BigDecimal("10000.0000"))
				.blockedBalance(BigDecimal.ZERO)
				.currency("INR")
				.status(WalletStatusEnum.ACTIVE)
				.dailyLimit(new BigDecimal("50000.0000"))
				.monthlyLimit(new BigDecimal("200000.0000"))
				.build();
	}

	/** Builds a standard DebitRequest. */
	private DebitRequest debitRequest(BigDecimal amount) {
		return DebitRequest.builder()
				.walletId(WALLET_ID)
				.amount(amount)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.transactionRef(TRANSACTION_REF)
				.description("Test debit")
				.build();
	}

	/** Builds a standard CreditRequest. */
	private CreditRequest creditRequest(BigDecimal amount) {
		return CreditRequest.builder()
				.walletId(WALLET_ID)
				.amount(amount)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.transactionRef(TRANSACTION_REF)
				.description("Test credit")
				.build();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// DEBIT TESTS
	// ─────────────────────────────────────────────────────────────────────────
	@Nested
	@DisplayName("debit()")
	class DebitTests {

		@BeforeEach
		void setUp() {
			// Default happy-path stubs — overridden per test as needed
			lenient().when(cacheService.acquireProcessingLock(IDEMPOTENCY_KEY)).thenReturn(true);
			lenient().when(transactionRepository.existsByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(false);
			lenient().when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(activeWallet()));
			lenient().when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(activeWallet()));
		}

		// ── Happy path ────────────────────────────────────────────────────────

		@Test
		@DisplayName("success — balance deducted, transaction saved, event published")
		void debit_success_balanceDeductedAndEventPublished() {
			DebitRequest req = debitRequest(new BigDecimal("500.0000"));

			WalletTransaction savedTx = WalletTransaction.builder()
					.id(UUID.randomUUID().toString())
					.walletId(WALLET_ID)
					.type(TransactionTypeEnum.DEBIT)
					.amount(req.getAmount())
					.balanceAfter(new BigDecimal("9500.0000"))
					.idempotencyKey(IDEMPOTENCY_KEY)
					.build();

			when(transactionRepository.save(any())).thenReturn(savedTx);
			when(eventFactory.buildDebitedEvent(any(), any()))
					.thenReturn(mock(WalletDebitedEvent.class));

			WalletTransaction result = walletService.debit(req);

			// Verify balance was deducted on the saved wallet
			ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
			verify(walletRepository).save(walletCaptor.capture());
			assertThat(walletCaptor.getValue().getBalance())
					.isEqualByComparingTo("9500.0000");

			// Verify transaction record was persisted
			ArgumentCaptor<WalletTransaction> txCaptor =
					ArgumentCaptor.forClass(WalletTransaction.class);
			verify(transactionRepository).save(txCaptor.capture());
			assertThat(txCaptor.getValue().getType()).isEqualTo(TransactionTypeEnum.DEBIT);
			assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("500.0000");
			assertThat(txCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("9500.0000");
			assertThat(txCaptor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);

			// Verify post-commit actions: evict cache, release lock, publish event
			verify(cacheService).evictBalance(WALLET_ID);
			verify(cacheService).releaseProcessingLock(IDEMPOTENCY_KEY);
			verify(eventProducer).publish(any(WalletDebitedEvent.class));

			assertThat(result).isNotNull();
		}

		@Test
		@DisplayName("success — limit check called before acquiring pessimistic lock")
		void debit_success_limitCheckedBeforeLock() {
			DebitRequest req = debitRequest(new BigDecimal("100.0000"));
			when(transactionRepository.save(any())).thenReturn(mock(WalletTransaction.class));
			when(eventFactory.buildDebitedEvent(any(), any()))
					.thenReturn(mock(WalletDebitedEvent.class));

			walletService.debit(req);

			// Verify call ORDER: limitService before findByIdWithLock
			// limitService uses findById (no lock) for limit thresholds
			// findByIdWithLock comes after to minimise lock hold time
			var inOrder = inOrder(walletRepository, limitService);
			inOrder.verify(walletRepository).findById(WALLET_ID);          // for limit thresholds
			inOrder.verify(limitService).checkAndRecord(any(), any());
			inOrder.verify(walletRepository).findByIdWithLock(WALLET_ID);  // for balance mutation
		}

		// ── Redis lock failure paths ──────────────────────────────────────────

		@Test
		@DisplayName("duplicate — Redis lock rejected → DuplicateOperationException")
		void debit_redisLockRejected_throwsDuplicateOperationException() {
			when(cacheService.acquireProcessingLock(IDEMPOTENCY_KEY)).thenReturn(false);

			WalletTransaction existing = mock(WalletTransaction.class);
			when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> walletService.debit(debitRequest(new BigDecimal("100.0000"))))
					.isInstanceOf(DuplicateOperationException.class)
					.satisfies(ex -> {
						DuplicateOperationException dupe = (DuplicateOperationException) ex;
						assertThat(dupe.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
						assertThat(dupe.getExistingTransaction()).isEqualTo(existing);
					});

			// Verify no DB mutation happened
			verify(walletRepository, never()).save(any());
			verify(transactionRepository, never()).save(any());
		}

		@Test
		@DisplayName("duplicate — DB idempotency check hit → DuplicateOperationException")
		void debit_dbIdempotencyCheckHit_throwsDuplicateOperationException() {
			when(transactionRepository.existsByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(true);
			WalletTransaction existing = mock(WalletTransaction.class);
			when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> walletService.debit(debitRequest(new BigDecimal("100.0000"))))
					.isInstanceOf(DuplicateOperationException.class);

			verify(walletRepository, never()).save(any());
		}

		// ── Business failure paths ────────────────────────────────────────────

		@Test
		@DisplayName("insufficient balance → InsufficientBalanceException with amounts")
		void debit_insufficientBalance_throwsInsufficientBalanceExceptionWithAmounts() {
			// Wallet has ₹100, we try to debit ₹500
			Wallet wallet = activeWallet();
			wallet.setBalance(new BigDecimal("100.0000"));
			when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(wallet));

			DebitRequest req = debitRequest(new BigDecimal("500.0000"));

			assertThatThrownBy(() -> walletService.debit(req))
					.isInstanceOf(InsufficientBalanceException.class)
					.satisfies(ex -> {
						InsufficientBalanceException ibe = (InsufficientBalanceException) ex;
						assertThat(ibe.getAvailable()).isEqualByComparingTo("100.0000");
						assertThat(ibe.getRequired()).isEqualByComparingTo("500.0000");
						assertThat(ibe.getWalletId()).isEqualTo(WALLET_ID);
					});

			// Redis lock must be released on business exception
			verify(cacheService).releaseProcessingLock(IDEMPOTENCY_KEY);
			// No money should have moved
			verify(walletRepository, never()).save(any());
		}

		@Test
		@DisplayName("frozen wallet → WalletFrozenException with actual status")
		void debit_frozenWallet_throwsWalletFrozenException() {
			Wallet frozen = activeWallet();
			frozen.setStatus(WalletStatusEnum.FROZEN);
			when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(frozen));

			assertThatThrownBy(() -> walletService.debit(debitRequest(new BigDecimal("100.0000"))))
					.isInstanceOf(WalletFrozenException.class)
					.satisfies(ex -> {
						WalletFrozenException wfe = (WalletFrozenException) ex;
						assertThat(wfe.getActualStatus()).isEqualTo(WalletStatusEnum.FROZEN);
					});

			verify(cacheService).releaseProcessingLock(IDEMPOTENCY_KEY);
			verify(walletRepository, never()).save(any());
		}

		@Test
		@DisplayName("inactive wallet → WalletFrozenException (INACTIVE status)")
		void debit_inactiveWallet_throwsWalletFrozenExceptionWithInactiveStatus() {
			Wallet inactive = activeWallet();
			inactive.setStatus(WalletStatusEnum.INACTIVE);
			when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(inactive));

			assertThatThrownBy(() -> walletService.debit(debitRequest(new BigDecimal("100.0000"))))
					.isInstanceOf(WalletFrozenException.class)
					.satisfies(ex -> {
						WalletFrozenException wfe = (WalletFrozenException) ex;
						assertThat(wfe.getActualStatus()).isEqualTo(WalletStatusEnum.INACTIVE);
					});
		}

		@Test
		@DisplayName("wallet not found → WalletNotFoundException")
		void debit_walletNotFound_throwsWalletNotFoundException() {
			when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> walletService.debit(debitRequest(new BigDecimal("100.0000"))))
					.isInstanceOf(WalletNotFoundException.class);

			verify(cacheService).releaseProcessingLock(IDEMPOTENCY_KEY);
		}

		@Test
		@DisplayName("system exception — Redis lock NOT released (retry should re-process)")
		void debit_systemException_redisLockNotReleased() {
			// Simulate a DB crash AFTER lock acquisition — a system failure
			when(walletRepository.findById(WALLET_ID))
					.thenThrow(new RuntimeException("DB connection lost"));

			assertThatThrownBy(() -> walletService.debit(debitRequest(new BigDecimal("100.0000"))))
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("DB connection lost");

			// CRITICAL: lock must NOT be released on system exceptions.
			// Retry should re-process after TTL expiry.
			verify(cacheService, never()).releaseProcessingLock(any());
		}

		@Test
		@DisplayName("exact balance debit — ₹0.0000 remaining, no exception")
		void debit_exactBalance_succeeds() {
			Wallet wallet = activeWallet();
			wallet.setBalance(new BigDecimal("500.0000"));
			when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(wallet));
			when(transactionRepository.save(any())).thenReturn(mock(WalletTransaction.class));
			when(eventFactory.buildDebitedEvent(any(), any()))
					.thenReturn(mock(WalletDebitedEvent.class));

			walletService.debit(debitRequest(new BigDecimal("500.0000")));

			ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
			verify(walletRepository).save(captor.capture());
			assertThat(captor.getValue().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// CREDIT TESTS
	// ─────────────────────────────────────────────────────────────────────────
	@Nested
	@DisplayName("credit()")
	class CreditTests {

		@BeforeEach
		void setUp() {
			lenient().when(cacheService.acquireProcessingLock(IDEMPOTENCY_KEY)).thenReturn(true);
			lenient().when(transactionRepository.existsByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(false);
			lenient().when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(activeWallet()));
		}

		@Test
		@DisplayName("success — balance added, transaction saved, event published")
		void credit_success_balanceAddedAndEventPublished() {
			CreditRequest req = creditRequest(new BigDecimal("1000.0000"));
			when(transactionRepository.save(any())).thenReturn(mock(WalletTransaction.class));
			when(eventFactory.buildCreditedEvent(any(), any()))
					.thenReturn(mock(WalletCreditedEvent.class));

			walletService.credit(req);

			// Balance should be 10000 + 1000 = 11000
			ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
			verify(walletRepository).save(walletCaptor.capture());
			assertThat(walletCaptor.getValue().getBalance())
					.isEqualByComparingTo("11000.0000");

			verify(cacheService).evictBalance(WALLET_ID);
			verify(cacheService).releaseProcessingLock(IDEMPOTENCY_KEY);
			verify(eventProducer).publish(any(WalletCreditedEvent.class));
		}

		@Test
		@DisplayName("success — limitService NOT called (credits don't count against limits)")
		void credit_success_limitServiceNotCalled() {
			when(transactionRepository.save(any())).thenReturn(mock(WalletTransaction.class));
			when(eventFactory.buildCreditedEvent(any(), any()))
					.thenReturn(mock(WalletCreditedEvent.class));

			walletService.credit(creditRequest(new BigDecimal("500.0000")));

			// Credits must never touch limit tracking
			verify(limitService, never()).checkAndRecord(any(), any());
		}

		@Test
		@DisplayName("frozen wallet — credit rejected (FROZEN rejects both debit and credit)")
		void credit_frozenWallet_throwsWalletFrozenException() {
			Wallet frozen = activeWallet();
			frozen.setStatus(WalletStatusEnum.FROZEN);
			when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(frozen));

			assertThatThrownBy(() -> walletService.credit(creditRequest(new BigDecimal("100.0000"))))
					.isInstanceOf(WalletFrozenException.class);

			verify(walletRepository, never()).save(any());
			verify(cacheService).releaseProcessingLock(IDEMPOTENCY_KEY);
		}

		@Test
		@DisplayName("duplicate credit → DuplicateOperationException, no double credit")
		void credit_duplicate_throwsDuplicateOperationException() {
			when(transactionRepository.existsByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(true);
			when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
					.thenReturn(Optional.of(mock(WalletTransaction.class)));

			assertThatThrownBy(() -> walletService.credit(creditRequest(new BigDecimal("100.0000"))))
					.isInstanceOf(DuplicateOperationException.class);

			verify(walletRepository, never()).save(any());
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// BLOCK / UNBLOCK TESTS
	// ─────────────────────────────────────────────────────────────────────────
	@Nested
	@DisplayName("blockFunds() / unblockFunds()")
	class BlockUnblockTests {

		private BlockFundsRequest blockRequest(BigDecimal amount) {
			return BlockFundsRequest.builder()
					.walletId(WALLET_ID)
					.amount(amount)
					.build();
		}

		@Test
		@DisplayName("blockFunds — balance moves to blockedBalance")
		void blockFunds_success_balanceMovesToBlocked() {
			when(walletRepository.findByIdWithLock(WALLET_ID))
					.thenReturn(Optional.of(activeWallet())); // balance=10000, blocked=0

			walletService.blockFunds(blockRequest(new BigDecimal("3000.0000")));

			ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
			verify(walletRepository).save(captor.capture());
			assertThat(captor.getValue().getBalance()).isEqualByComparingTo("7000.0000");
			assertThat(captor.getValue().getBlockedBalance()).isEqualByComparingTo("3000.0000");
			verify(cacheService).evictBalance(WALLET_ID);
		}

		@Test
		@DisplayName("blockFunds — insufficient balance → InsufficientBalanceException")
		void blockFunds_insufficientBalance_throws() {
			Wallet wallet = activeWallet();
			wallet.setBalance(new BigDecimal("100.0000"));
			when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(wallet));

			assertThatThrownBy(() -> walletService.blockFunds(blockRequest(new BigDecimal("500.0000"))))
					.isInstanceOf(InsufficientBalanceException.class);
		}

		@Test
		@DisplayName("unblockFunds — blocked amount returns to balance")
		void unblockFunds_success_blockedReturnsToBalance() {
			Wallet wallet = activeWallet();
			wallet.setBalance(new BigDecimal("7000.0000"));
			wallet.setBlockedBalance(new BigDecimal("3000.0000"));
			when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(wallet));

			walletService.unblockFunds(blockRequest(new BigDecimal("3000.0000")));

			ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
			verify(walletRepository).save(captor.capture());
			assertThat(captor.getValue().getBalance()).isEqualByComparingTo("10000.0000");
			assertThat(captor.getValue().getBlockedBalance()).isEqualByComparingTo("0.0000");
		}

		@Test
		@DisplayName("unblockFunds — safety floor: cannot unblock more than blockedBalance")
		void unblockFunds_safetyFloor_cannotUnblockMoreThanBlocked() {
			// Only ₹500 is blocked, but we try to unblock ₹2000
			Wallet wallet = activeWallet();
			wallet.setBalance(new BigDecimal("9500.0000"));
			wallet.setBlockedBalance(new BigDecimal("500.0000"));
			when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(wallet));

			walletService.unblockFunds(blockRequest(new BigDecimal("2000.0000")));

			ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
			verify(walletRepository).save(captor.capture());
			// Only ₹500 should return (the floor: min(requested, blocked))
			assertThat(captor.getValue().getBalance()).isEqualByComparingTo("10000.0000");
			assertThat(captor.getValue().getBlockedBalance()).isEqualByComparingTo("0.0000");
		}

		@Test
		@DisplayName("unblockFunds — allowed on FROZEN wallet (compensation must always work)")
		void unblockFunds_frozenWallet_succeeds() {
			Wallet frozen = activeWallet();
			frozen.setStatus(WalletStatusEnum.FROZEN);
			frozen.setBlockedBalance(new BigDecimal("1000.0000"));
			when(walletRepository.findByIdWithLock(WALLET_ID)).thenReturn(Optional.of(frozen));

			// Should NOT throw — unblocking is a compensation, must work regardless of status
			assertThatCode(() -> walletService.unblockFunds(blockRequest(new BigDecimal("1000.0000"))))
					.doesNotThrowAnyException();
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// PROVISIONING TESTS
	// ─────────────────────────────────────────────────────────────────────────
	@Nested
	@DisplayName("provisionWallet()")
	class ProvisionWalletTests {

		@Test
		@DisplayName("success — INACTIVE wallet created with correct defaults")
		void provisionWallet_success_createsInactiveWalletWithDefaults() {
			when(walletRepository.existsByUserId(USER_ID)).thenReturn(false);
			when(walletRepository.save(any())).thenAnswer(inv -> {
				Wallet w = inv.getArgument(0);
				w.setId(UUID.randomUUID().toString());
				return w;
			});
			when(eventFactory.buildProvisionedEvent(any()))
					.thenReturn(mock(WalletProvisionedEvent.class));

			walletService.provisionWallet(USER_ID);

			ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
			verify(walletRepository).save(captor.capture());

			Wallet created = captor.getValue();
			assertThat(created.getUserId()).isEqualTo(USER_ID);
			assertThat(created.getStatus()).isEqualTo(WalletStatusEnum.INACTIVE);
			assertThat(created.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(created.getBlockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(created.getCurrency()).isEqualTo(WalletConstants.DEFAULT_CURRENCY);
			assertThat(created.getDailyLimit())
					.isEqualByComparingTo(WalletConstants.DEFAULT_DAILY_LIMIT);
			assertThat(created.getMonthlyLimit())
					.isEqualByComparingTo(WalletConstants.DEFAULT_MONTHLY_LIMIT);
			assertThat(created.getWalletNumber()).startsWith(WalletConstants.WALLET_NUMBER_PREFIX);
		}

		@Test
		@DisplayName("idempotent — second call for same userId does nothing")
		void provisionWallet_alreadyExists_skipsWithoutError() {
			when(walletRepository.existsByUserId(USER_ID)).thenReturn(true);

			walletService.provisionWallet(USER_ID);

			// Wallet must not be created again
			verify(walletRepository, never()).save(any());
			verify(eventProducer, never()).publish(any());
		}

		@Test
		@DisplayName("success — WALLET_PROVISIONED event published after save")
		void provisionWallet_success_provisionedEventPublished() {
			when(walletRepository.existsByUserId(USER_ID)).thenReturn(false);
			when(walletRepository.save(any())).thenAnswer(inv -> {
				Wallet w = inv.getArgument(0);
				w.setId(UUID.randomUUID().toString());
				return w;
			});
			WalletProvisionedEvent event = mock(WalletProvisionedEvent.class);
			when(eventFactory.buildProvisionedEvent(any())).thenReturn(event);

			walletService.provisionWallet(USER_ID);

			verify(eventProducer).publish(event);
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// BALANCE READ TESTS
	// ─────────────────────────────────────────────────────────────────────────
	@Nested
	@DisplayName("getBalance()")
	class GetBalanceTests {

		@Test
		@DisplayName("cache hit — returns cached balance without DB call")
		void getBalance_cacheHit_returnsFromCacheNoDbCall() {
			lenient().when(cacheService.getCachedWalletId(USER_ID)).thenReturn(Optional.of(WALLET_ID));
			lenient().when(cacheService.getCachedBalance(WALLET_ID))
					.thenReturn(Optional.of(new BigDecimal("10000.0000")));
			lenient().when(walletMapper.toBalanceResponse(any(), anyString()))
					.thenReturn(mock(BalanceResponse.class));

			walletService.getBalance(USER_ID);

			// DB must NOT be called on cache hit
			verify(walletRepository, never()).findById(any());
			verify(cacheService, never()).cacheBalance(any(), any());
		}

		@Test
		@DisplayName("cache miss — reads DB, warms cache, returns source=DB")
		void getBalance_cacheMiss_readsDbAndWarmsCache() {
			when(cacheService.getCachedWalletId(USER_ID)).thenReturn(Optional.of(WALLET_ID));
			when(cacheService.getCachedBalance(WALLET_ID)).thenReturn(Optional.empty());

			Wallet wallet = activeWallet();
			when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));

			BalanceResponse mockResponse = mock(BalanceResponse.class);
			when(walletMapper.toBalanceResponse(eq(wallet), eq("DB"))).thenReturn(mockResponse);

			BalanceResponse result = walletService.getBalance(USER_ID);

			// Cache should be warmed after DB read
			verify(cacheService).cacheBalance(WALLET_ID, wallet.getBalance());
			assertThat(result).isEqualTo(mockResponse);
		}

		@Test
		@DisplayName("userId → walletId cache miss — resolved from DB and cached")
		void getBalance_userWalletIdCacheMiss_resolvedAndCached() {
			when(cacheService.getCachedWalletId(USER_ID)).thenReturn(Optional.empty());
			when(walletRepository.findWalletIdByUserId(USER_ID)).thenReturn(Optional.of(WALLET_ID));
			when(cacheService.getCachedBalance(WALLET_ID)).thenReturn(Optional.empty());
			when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(activeWallet()));
			when(walletMapper.toBalanceResponse(any(), any())).thenReturn(mock(BalanceResponse.class));

			walletService.getBalance(USER_ID);

			// walletId should be cached after resolution
			verify(cacheService).cacheUserWalletId(USER_ID, WALLET_ID);
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// OWNERSHIP VALIDATION TESTS
	// ─────────────────────────────────────────────────────────────────────────
	@Nested
	@DisplayName("validateOwnership()")
	class ValidateOwnershipTests {

		@Test
		@DisplayName("matching userId — no exception")
		void validateOwnership_matchingUser_noException() {
			when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(activeWallet()));

			assertThatCode(() -> walletService.validateOwnership(WALLET_ID, USER_ID))
					.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("different userId — WalletAccessDeniedException (IDOR protection)")
		void validateOwnership_differentUser_throwsAccessDenied() {
			when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(activeWallet()));

			String attackerId = UUID.randomUUID().toString(); // different from USER_ID

			assertThatThrownBy(() -> walletService.validateOwnership(WALLET_ID, attackerId))
					.isInstanceOf(WalletAccessDeniedException.class);
		}

		@Test
		@DisplayName("wallet not found — WalletNotFoundException (not access denied)")
		void validateOwnership_walletNotFound_throwsNotFoundException() {
			when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> walletService.validateOwnership(WALLET_ID, USER_ID))
					.isInstanceOf(WalletNotFoundException.class);
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// UPDATE LIMITS TESTS
	// ─────────────────────────────────────────────────────────────────────────
	@Nested
	@DisplayName("updateLimits()")
	class UpdateLimitsTests {

		@Test
		@DisplayName("valid limits — saved and returned")
		void updateLimits_valid_savedCorrectly() {
			when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeWallet()));
			when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(limitService.getDailySpent(any())).thenReturn(BigDecimal.ZERO);
			when(limitService.getMonthlySpent(any())).thenReturn(BigDecimal.ZERO);
			when(walletMapper.toResponse(any(), any(), any())).thenReturn(mock(WalletResponse.class));

			UpdateLimitsRequest req = UpdateLimitsRequest.builder()
					.dailyLimit(new BigDecimal("30000.0000"))
					.monthlyLimit(new BigDecimal("100000.0000"))
					.build();

			walletService.updateLimits(USER_ID, req);

			ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
			verify(walletRepository).save(captor.capture());
			assertThat(captor.getValue().getDailyLimit()).isEqualByComparingTo("30000.0000");
			assertThat(captor.getValue().getMonthlyLimit()).isEqualByComparingTo("100000.0000");
		}

		@Test
		@DisplayName("dailyLimit > monthlyLimit → IllegalArgumentException")
		void updateLimits_dailyExceedsMonthly_throwsIllegalArgumentException() {
			when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeWallet()));

			UpdateLimitsRequest req = UpdateLimitsRequest.builder()
					.dailyLimit(new BigDecimal("150000.0000"))  // daily > monthly — invalid
					.monthlyLimit(new BigDecimal("100000.0000"))
					.build();

			assertThatThrownBy(() -> walletService.updateLimits(USER_ID, req))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Daily limit cannot exceed monthly limit");
		}

		@Test
		@DisplayName("equal daily and monthly limits — valid edge case")
		void updateLimits_dailyEqualsMonthly_succeeds() {
			when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(activeWallet()));
			when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(limitService.getDailySpent(any())).thenReturn(BigDecimal.ZERO);
			when(limitService.getMonthlySpent(any())).thenReturn(BigDecimal.ZERO);
			when(walletMapper.toResponse(any(), any(), any())).thenReturn(mock(WalletResponse.class));

			UpdateLimitsRequest req = UpdateLimitsRequest.builder()
					.dailyLimit(new BigDecimal("50000.0000"))
					.monthlyLimit(new BigDecimal("50000.0000")) // equal is valid
					.build();

			assertThatCode(() -> walletService.updateLimits(USER_ID, req))
					.doesNotThrowAnyException();
		}
	}
}
