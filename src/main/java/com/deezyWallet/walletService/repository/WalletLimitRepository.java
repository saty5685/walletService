package com.deezyWallet.walletService.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.deezyWallet.walletService.entity.WalletLimit;

/**
 * Repository for WalletLimit entity.
 *
 * The WalletLimit table uses a "one row per wallet per day" pattern.
 * Every debit upserts today's row: insert if new day, update if existing.
 *
 * MySQL upsert strategy:
 *   We use a native query with INSERT INTO ... ON DUPLICATE KEY UPDATE
 *   instead of application-layer findOrCreate + save.
 *
 *   Why native upsert?
 *   - Atomic: no race condition between "does today's row exist?" and "create it"
 *   - Single round-trip: one query instead of SELECT + (INSERT or UPDATE)
 *   - Safe under concurrent load: two threads debiting the same wallet
 *     simultaneously will both upsert correctly — the UNIQUE constraint
 *     on (wallet_id, limit_date) serialises them at the DB level.
 *
 * Monthly spend is derived by summing daily_spent rows for the current month —
 * no separate monthly column needed. This keeps the schema simple and the
 * historical data intact (you can query any past month's spend).
 */
@Repository
public interface WalletLimitRepository extends JpaRepository<WalletLimit, String> {

	// ── Daily limit queries ───────────────────────────────────────────────────

	/**
	 * Find today's limit row for a wallet.
	 * Returns empty Optional on the first debit of a new day —
	 * WalletLimitService handles this by creating the row via upsert.
	 *
	 * Covered by idx_wlimit_wallet_date index.
	 */
	Optional<WalletLimit> findByWalletIdAndLimitDate(
			String walletId,
			LocalDate limitDate);

	/**
	 * UPSERT today's limit row — atomic, race-condition safe.
	 *
	 * MySQL: INSERT INTO wallet_limits ... ON DUPLICATE KEY UPDATE daily_spent = daily_spent + ?
	 *
	 * Behaviour:
	 *   - If no row exists for (walletId, today): INSERT with dailySpent = amount
	 *   - If row exists: UPDATE daily_spent = daily_spent + amount
	 *
	 * The UNIQUE constraint on (wallet_id, limit_date) makes the ON DUPLICATE KEY
	 * trigger correctly when two threads try to insert the same day row simultaneously.
	 *
	 * @Modifying(flushAutomatically = true) — flushes pending JPA changes before
	 * running this native query, ensuring consistency with any preceding JPQL operations
	 * in the same transaction.
	 *
	 * @param walletId  wallet UUID
	 * @param limitDate today's date
	 * @param amount    amount to add to daily_spent
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
           INSERT INTO wallet_limits (id, wallet_id, daily_spent, limit_date, updated_at)
           VALUES (UUID(), :walletId, :amount, :limitDate, NOW(6))
           ON DUPLICATE KEY UPDATE
               daily_spent = daily_spent + :amount,
               updated_at  = NOW(6)
           """,
			nativeQuery = true)
	void upsertDailySpent(
			@Param("walletId")  String walletId,
			@Param("limitDate") LocalDate limitDate,
			@Param("amount")    BigDecimal amount);

	/**
	 * Get the daily spent amount directly — fast path for limit check.
	 * Returns 0 if no row exists yet (first transaction of the day).
	 *
	 * Called BEFORE every debit in WalletLimitService.checkAndRecord().
	 * If dailySpent + newAmount > dailyLimit → reject before touching balance.
	 */
	@Query("""
           SELECT COALESCE(wl.dailySpent, 0)
           FROM WalletLimit wl
           WHERE wl.walletId  = :walletId
             AND wl.limitDate = :limitDate
           """)
	Optional<BigDecimal> findDailySpentByWalletIdAndDate(
			@Param("walletId")  String walletId,
			@Param("limitDate") LocalDate limitDate);

	// ── Monthly limit queries ─────────────────────────────────────────────────

	/**
	 * Sum of all daily_spent values in the current calendar month.
	 *
	 * Monthly spend = SUM of daily rows from first of month to today.
	 * This approach avoids a separate monthly column while giving full
	 * historical flexibility (query any month by changing the date range).
	 *
	 * Called BEFORE every debit alongside the daily check.
	 * Returns 0.0000 via COALESCE if no rows exist yet this month.
	 *
	 * Index used: idx_wlimit_wallet_date — range scan on (wallet_id, limit_date)
	 * for the current month's rows.
	 *
	 * @param walletId   wallet UUID
	 * @param monthStart first day of current month (e.g. 2024-03-01)
	 * @param today      today's date (inclusive upper bound)
	 */
	@Query("""
           SELECT COALESCE(SUM(wl.dailySpent), 0)
           FROM WalletLimit wl
           WHERE wl.walletId  = :walletId
             AND wl.limitDate >= :monthStart
             AND wl.limitDate <= :today
           """)
	BigDecimal sumMonthlySpent(
			@Param("walletId")   String walletId,
			@Param("monthStart") LocalDate monthStart,
			@Param("today")      LocalDate today);

	/**
	 * Get all daily limit rows for a wallet in a date range.
	 * Used for:
	 *   - Spending analytics / charts in the admin dashboard
	 *   - Monthly statement generation
	 *   - Fraud pattern analysis (sudden spike in daily spend)
	 */
	@Query("""
           SELECT wl FROM WalletLimit wl
           WHERE wl.walletId  = :walletId
             AND wl.limitDate >= :from
             AND wl.limitDate <= :to
           ORDER BY wl.limitDate DESC
           """)
	List<WalletLimit> findByWalletIdAndDateRange(
			@Param("walletId") String walletId,
			@Param("from")     LocalDate from,
			@Param("to")       LocalDate to);

	// ── Admin / compliance ────────────────────────────────────────────────────

	/**
	 * Find wallets that have spent more than a threshold today.
	 * Used by compliance team to identify high-activity wallets for review.
	 * Also used by Fraud Detection service via internal REST endpoint.
	 *
	 * @param limitDate today's date
	 * @param threshold amount threshold (e.g. ₹40,000 = 80% of daily limit)
	 */
	@Query("""
           SELECT wl FROM WalletLimit wl
           WHERE wl.limitDate  = :limitDate
             AND wl.dailySpent >= :threshold
           ORDER BY wl.dailySpent DESC
           """)
	List<WalletLimit> findHighSpendWallets(
			@Param("limitDate")  LocalDate limitDate,
			@Param("threshold")  BigDecimal threshold);
}
