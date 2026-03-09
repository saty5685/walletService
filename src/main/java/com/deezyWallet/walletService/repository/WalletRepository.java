package com.deezyWallet.walletService.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.deezyWallet.walletService.entity.Wallet;
import com.deezyWallet.walletService.enums.WalletStatusEnum;

import jakarta.persistence.LockModeType;

/**
 * Repository for Wallet entity.
 *
 * LOCKING STRATEGY — two different locks for two different operations:
 *
 * PESSIMISTIC_WRITE (SELECT FOR UPDATE):
 *   Used on DEBIT path — findByIdWithLock()
 *   Why: Debit is non-idempotent and non-reversible without compensation.
 *        Two concurrent debits on the same wallet MUST be serialised.
 *        Thread 1 holds the lock, Thread 2 waits at the DB level.
 *        Lock is held for the entire @Transactional duration then released.
 *   Risk: Can cause deadlocks if two services try to lock each other's wallets.
 *         Mitigated by: always locking in a consistent order (sender first, never receiver).
 *
 * OPTIMISTIC (@Version on entity):
 *   Used on CREDIT path — regular findById()
 *   Why: Credits are additive. If two credits conflict (rare), one retries cleanly.
 *        No blocking — better throughput for high-frequency credit scenarios.
 *   Risk: OptimisticLockException on conflict — caller must retry.
 *         Handled in WalletService with @Retryable.
 *
 * NEVER use findByIdWithLock() for credits — you'd serialize all incoming
 * payments to a popular merchant wallet unnecessarily.
 *
 * NEVER use regular findById() for debits — you'd have a TOCTOU race condition
 * (check balance, then deduct — someone else deducts in between).
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {

	// ── Basic lookups ─────────────────────────────────────────────────────────

	/**
	 * Find wallet by owner's userId.
	 * Most common query — called on every authenticated API request.
	 * Covered by idx_wallets_user_id index.
	 *
	 * Returns Optional because a user might not have a wallet yet
	 * (edge case: event processing delay after registration).
	 */
	Optional<Wallet> findByUserId(String userId);

	/**
	 * Existence check before provisioning.
	 * Cheaper than findByUserId() — no entity hydration, just COUNT(*).
	 * Used in WalletService.provisionWallet() as idempotency guard.
	 */
	boolean existsByUserId(String userId);

	/**
	 * Find by human-readable wallet number.
	 * Used for: customer support lookups, receipt validation, admin queries.
	 * Covered by uq_wallets_wallet_number unique index.
	 */
	Optional<Wallet> findByWalletNumber(String walletNumber);

	// ── Locking queries ───────────────────────────────────────────────────────

	/**
	 * PESSIMISTIC WRITE LOCK — SELECT ... FOR UPDATE
	 *
	 * Called exclusively on the DEBIT path inside @Transactional.
	 * Blocks any other transaction from reading OR writing this wallet row
	 * until the current transaction commits or rolls back.
	 *
	 * Execution flow:
	 *   1. WalletService.debit() is called inside @Transactional
	 *   2. This query runs: SELECT * FROM wallets WHERE id = ? FOR UPDATE
	 *   3. DB acquires row-level lock on this wallet
	 *   4. If another thread calls debit() on same wallet → it WAITS at DB level
	 *   5. First transaction completes → lock released → waiting thread proceeds
	 *   6. Waiting thread now reads the updated balance — race condition impossible
	 *
	 * MySQL InnoDB note: FOR UPDATE lock is on the INDEX entry + row.
	 * innodb_lock_wait_timeout default = 50 seconds.
	 * We set it to 5s in application.yml to fail-fast on contention.
	 *
	 * @param id wallet UUID
	 * @return wallet with exclusive lock held
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT w FROM Wallet w WHERE w.id = :id")
	Optional<Wallet> findByIdWithLock(@Param("id") String id);

	/**
	 * PESSIMISTIC WRITE LOCK by userId.
	 * Used when we know the userId but not the walletId yet (debit by user).
	 * Avoids a second round-trip to get the walletId first.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
	Optional<Wallet> findByUserIdWithLock(@Param("userId") String userId);

	// ── Lightweight projections ───────────────────────────────────────────────

	/**
	 * Balance-only query — avoids loading the full Wallet entity.
	 * Used by InternalWalletController for fast balance checks by Transaction Service.
	 * Returns Optional<BigDecimal> — null if wallet doesn't exist.
	 *
	 * Performance: SELECT balance FROM wallets WHERE id = ?
	 * vs full entity: SELECT id, wallet_number, user_id, balance, blocked_balance,
	 *                        currency, status, type, daily_limit, monthly_limit,
	 *                        version, created_at, updated_at FROM wallets WHERE id = ?
	 */
	@Query("SELECT w.balance FROM Wallet w WHERE w.id = :id")
	Optional<BigDecimal> findBalanceById(@Param("id") String id);

	/**
	 * Status-only check — used by other services to validate wallet is ACTIVE
	 * before initiating a transaction. Minimal data transfer.
	 */
	@Query("SELECT w.status FROM Wallet w WHERE w.id = :id")
	Optional<WalletStatusEnum> findStatusById(@Param("id") String id);

	/**
	 * walletId lookup by userId — avoids loading full entity when only ID needed.
	 * Cached in Redis (REDIS_USER_WALLET_PREFIX) — this query is the cache-miss path.
	 */
	@Query("SELECT w.id FROM Wallet w WHERE w.userId = :userId")
	Optional<String> findWalletIdByUserId(@Param("userId") String userId);

	// ── Status mutation queries ───────────────────────────────────────────────

	/**
	 * Bulk status update — used by WalletService.activateWallet() and freezeWallet().
	 * @Modifying tells Spring Data this is a DML statement (UPDATE/DELETE), not SELECT.
	 * clearAutomatically = true clears the EntityManager cache after the update —
	 * prevents stale entity reads from the 1st-level cache after a bulk update.
	 *
	 * Why bulk UPDATE instead of load + save?
	 *   load + save = SELECT + UPDATE (2 queries, entity in memory)
	 *   @Query UPDATE  = 1 query, no entity hydration, faster for status-only changes
	 */
	@Modifying(clearAutomatically = true)
	@Query("UPDATE Wallet w SET w.status = :status, w.updatedAt = :now " +
			"WHERE w.userId = :userId")
	int updateStatusByUserId(
			@Param("userId") String userId,
			@Param("status") WalletStatusEnum status,
			@Param("now")    LocalDateTime now);

	// ── Admin / reporting queries ─────────────────────────────────────────────

	/**
	 * Admin: paginated list of all wallets with optional status filter.
	 * Returns Page<Wallet> — includes total count for pagination UI.
	 * Covered by idx_wallets_status index when status filter is applied.
	 */
	Page<Wallet> findAllByStatus(WalletStatusEnum status, Pageable pageable);

	/**
	 * Admin: find wallets that haven't been updated recently.
	 * Used for dormancy detection and compliance reporting.
	 * "Stale" = ACTIVE wallet with no activity in X days.
	 */
	@Query("SELECT w FROM Wallet w " +
			"WHERE w.status = :status " +
			"AND w.updatedAt < :before")
	Page<Wallet> findStaleWallets(
			@Param("status") WalletStatusEnum status,
			@Param("before") LocalDateTime before,
			Pageable pageable);

	/**
	 * Count active wallets — used for metrics/monitoring endpoints.
	 */
	long countByStatus(WalletStatusEnum status);
}

