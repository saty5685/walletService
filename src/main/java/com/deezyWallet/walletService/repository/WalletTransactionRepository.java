package com.deezyWallet.walletService.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.deezyWallet.walletService.entity.WalletTransaction;
import com.deezyWallet.walletService.enums.TransactionTypeEnum;

/**
 * Repository for WalletTransaction entity.
 *
 * WalletTransaction records are IMMUTABLE after creation:
 *   - No UPDATE queries here (no @Modifying methods)
 *   - No delete methods exposed (soft-delete via status, never hard delete)
 *   - Only INSERT (via save()) and SELECT operations
 *
 * The most critical method here is existsByIdempotencyKey() —
 * it is the application-layer idempotency check called BEFORE
 * every debit/credit to prevent duplicate processing.
 * The DB UNIQUE constraint on idempotency_key is the safety net.
 */
@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

	// ── Idempotency ───────────────────────────────────────────────────────────

	/**
	 * THE most important query in the Wallet Service.
	 *
	 * Called at the start of every debit/credit operation BEFORE touching
	 * the balance. If this returns true, the operation is a duplicate and
	 * must return 200 OK (already processed) without re-applying.
	 *
	 * Two-layer idempotency:
	 *   Layer 1 (fast): Redis SETNX lock — checked first in BalanceCacheService
	 *   Layer 2 (durable): This DB query — source of truth
	 *   Layer 3 (safety net): UNIQUE constraint on idempotency_key column
	 *
	 * Performance: Uses idx_wtxn_idempotency_key index — O(log n) lookup.
	 * Generates: SELECT COUNT(*) > 0 FROM wallet_transactions WHERE idempotency_key = ?
	 */
	boolean existsByIdempotencyKey(String idempotencyKey);

	/**
	 * Fetch the existing transaction for a duplicate idempotency key.
	 * Returns the original response when a duplicate is detected —
	 * allows the caller to return the same result as the first call.
	 */
	Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

	// ── Transaction history ───────────────────────────────────────────────────

	/**
	 * Paginated transaction history for a wallet — newest first.
	 * Primary access pattern for GET /api/v1/wallets/me/transactions
	 *
	 * Spring Data translates to:
	 *   SELECT * FROM wallet_transactions
	 *   WHERE wallet_id = ?
	 *   ORDER BY created_at DESC
	 *   LIMIT ? OFFSET ?
	 *
	 * Uses composite index idx_wtxn_wallet_date (wallet_id, created_at DESC).
	 * With this index, MySQL can satisfy both the WHERE and ORDER BY
	 * from the index alone — no filesort needed.
	 *
	 * @param walletId wallet UUID
	 * @param pageable page number, size, sort (default: createdAt DESC, size 20)
	 */
	Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(
			String walletId,
			Pageable pageable);

	/**
	 * Filtered history — by transaction type (DEBIT or CREDIT only).
	 * Used for "show only outgoing" / "show only incoming" filter on the UI.
	 */
	Page<WalletTransaction> findByWalletIdAndTypeOrderByCreatedAtDesc(
			String walletId,
			TransactionTypeEnum type,
			Pageable pageable);

	/**
	 * Cross-service tracing: find all wallet entries for a given Transaction
	 * Service reference ID.
	 * Used by: support tooling, admin reconciliation, dispute resolution.
	 * Example: "show me both sides of transfer TXN-ABC123"
	 * Uses idx_wtxn_reference_id index.
	 */
	java.util.List<WalletTransaction> findByReferenceId(String referenceId);

	// ── Aggregation for limit enforcement ─────────────────────────────────────

	/**
	 * Sum of all DEBIT amounts for a wallet within a time window.
	 *
	 * Called by WalletLimitService to check:
	 *   - Daily spend: from = start of today, to = now
	 *   - Monthly spend: from = first of month, to = now
	 *
	 * COALESCE(..., 0) ensures we get 0.0000 instead of NULL
	 * when no debits exist in the window (new wallet, first transaction of day).
	 *
	 * MySQL execution plan:
	 *   Uses idx_wtxn_wallet_date index for the wallet_id + created_at range scan.
	 *   Aggregation happens on the filtered rows — no full table scan.
	 *
	 * NOTE: This is a fallback. WalletLimitRepository.findDailySpent() is faster
	 * for the common case (reads from wallet_limits table directly).
	 * This method is used for reconciliation and when limit row doesn't exist yet.
	 *
	 * @param walletId wallet UUID
	 * @param from     start of window (inclusive)
	 * @param to       end of window (exclusive)
	 * @return total debited in window, 0 if none
	 */
	@Query("""
           SELECT COALESCE(SUM(wt.amount), 0)
           FROM WalletTransaction wt
           WHERE wt.walletId = :walletId
             AND wt.type     = com.deezyWallet.walletService.enums.TransactionTypeEnum.DEBIT
             AND wt.createdAt >= :from
             AND wt.createdAt <  :to
           """)
	BigDecimal sumDebitAmountBetween(
			@Param("walletId") String walletId,
			@Param("from")     LocalDateTime from,
			@Param("to")       LocalDateTime to);

	/**
	 * Count transactions in a time window — used for velocity checks in
	 * fraud detection (WalletLimitService.checkVelocity()).
	 * Example: "more than 10 debits in the last 1 hour" → flag for review.
	 */
	@Query("""
           SELECT COUNT(wt)
           FROM WalletTransaction wt
           WHERE wt.walletId   = :walletId
             AND wt.type       = com.deezyWallet.walletService.enums.TransactionTypeEnum.DEBIT
             AND wt.createdAt >= :from
           """)
	long countDebitsAfter(
			@Param("walletId") String walletId,
			@Param("from")     LocalDateTime from);

	// ── Admin / reporting ─────────────────────────────────────────────────────

	/**
	 * Date-range query — for admin transaction reports and statements.
	 * Uses idx_wtxn_wallet_date composite index efficiently.
	 */
	@Query("""
           SELECT wt FROM WalletTransaction wt
           WHERE wt.walletId   = :walletId
             AND wt.createdAt >= :from
             AND wt.createdAt <  :to
           ORDER BY wt.createdAt DESC
           """)
	Page<WalletTransaction> findByWalletIdAndDateRange(
			@Param("walletId") String walletId,
			@Param("from")     LocalDateTime from,
			@Param("to")       LocalDateTime to,
			Pageable pageable);
}

