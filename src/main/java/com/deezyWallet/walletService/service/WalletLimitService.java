package com.deezyWallet.walletService.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.deezyWallet.walletService.entity.Wallet;
import com.deezyWallet.walletService.enums.WalletLimitTypeEnum;
import com.deezyWallet.walletService.exception.WalletLimitExceededException;
import com.deezyWallet.walletService.repository.WalletLimitRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces and records daily and monthly spend limits.
 *
 * CALL ORDER inside WalletService.debit():
 *
 *   WalletService.debit()   @Transactional (REQUIRED)
 *       │
 *       ├─ walletLimitService.checkAndRecord()  @Transactional (MANDATORY)
 *       │       ├─ checkDailyLimit()   → throws WalletLimitExceededException
 *       │       ├─ checkMonthlyLimit() → throws WalletLimitExceededException
 *       │       └─ recordSpend()       → upserts wallet_limits row
 *       │
 *       ├─ walletRepository.findByIdWithLock()   ← acquire DB row lock
 *       ├─ balance check + deduction
 *       └─ walletRepository.save()
 *
 * WHY Propagation.MANDATORY on checkAndRecord()?
 *   It MUST share the same DB transaction as the balance deduction.
 *   If balance deduction fails and rolls back → limit recording rolls back too.
 *   No phantom spend is recorded for a failed debit.
 *   If called outside a transaction → throws IllegalTransactionStateException
 *   immediately, revealing a programming error early rather than silently
 *   recording spend without an actual debit.
 *
 * WHY check AND record in one method?
 *   Separate check() + record() calls would create a TOCTOU window:
 *   Thread 1: check() → passes
 *   Thread 2: check() → passes (Thread 1 hasn't recorded yet)
 *   Thread 1: record()
 *   Thread 2: record() → both recorded, effective double-spend on limits
 *   Atomic check+record inside the locked transaction eliminates this window.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletLimitService {

	private final WalletLimitRepository walletLimitRepository;

	/**
	 * Check daily + monthly limits and record the spend atomically.
	 * Must be called inside an active @Transactional context.
	 *
	 * @param wallet the wallet being debited (carries the limit thresholds)
	 * @param amount the debit amount to validate and record
	 * @throws WalletLimitExceededException if either limit would be exceeded
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void checkAndRecord(Wallet wallet, BigDecimal amount) {
		String    walletId   = wallet.getId();
		LocalDate today      = LocalDate.now();
		LocalDate monthStart = today.withDayOfMonth(1);

		// ── Read current spend ─────────────────────────────────────────────
		BigDecimal dailySpent = walletLimitRepository
				.findDailySpentByWalletIdAndDate(walletId, today)
				.orElse(BigDecimal.ZERO);

		BigDecimal monthlySpent = walletLimitRepository
				.sumMonthlySpent(walletId, monthStart, today);

		log.debug("Limit check wallet={} dailySpent={} monthlySpent={} amount={}",
				walletId, dailySpent, monthlySpent, amount);

		// ── Enforce limits ─────────────────────────────────────────────────
		checkDailyLimit(walletId, wallet.getDailyLimit(), dailySpent, amount);
		checkMonthlyLimit(walletId, wallet.getMonthlyLimit(), monthlySpent, amount);

		// ── Record spend (only reached if both checks pass) ────────────────
		walletLimitRepository.upsertDailySpent(walletId, today, amount);

		log.debug("Spend recorded wallet={} amount={}", walletId, amount);
	}

	/** Today's cumulative spend — for WalletResponse.dailyLimitRemaining calculation. */
	@Transactional(readOnly = true)
	public BigDecimal getDailySpent(String walletId) {
		return walletLimitRepository
				.findDailySpentByWalletIdAndDate(walletId, LocalDate.now())
				.orElse(BigDecimal.ZERO);
	}

	/** This month's cumulative spend — for WalletResponse.monthlyLimitRemaining calculation. */
	@Transactional(readOnly = true)
	public BigDecimal getMonthlySpent(String walletId) {
		LocalDate today      = LocalDate.now();
		LocalDate monthStart = today.withDayOfMonth(1);
		return walletLimitRepository.sumMonthlySpent(walletId, monthStart, today);
	}

	// ── Private limit checks ──────────────────────────────────────────────────

	private void checkDailyLimit(String walletId,
			BigDecimal dailyLimit,
			BigDecimal dailySpent,
			BigDecimal amount) {
		// compareTo, never .equals() — BigDecimal(2.0) != BigDecimal(2.00) via equals
		if (dailySpent.add(amount).compareTo(dailyLimit) > 0) {
			log.warn("Daily limit exceeded wallet={} limit={} spent={} attempted={}",
					walletId, dailyLimit, dailySpent, amount);
			throw new WalletLimitExceededException(
					walletId, WalletLimitTypeEnum.DAILY, dailyLimit, dailySpent, amount);
		}
	}

	private void checkMonthlyLimit(String walletId,
			BigDecimal monthlyLimit,
			BigDecimal monthlySpent,
			BigDecimal amount) {
		if (monthlySpent.add(amount).compareTo(monthlyLimit) > 0) {
			log.warn("Monthly limit exceeded wallet={} limit={} spent={} attempted={}",
					walletId, monthlyLimit, monthlySpent, amount);
			throw new WalletLimitExceededException(
					walletId, WalletLimitTypeEnum.MONTHLY, monthlyLimit, monthlySpent, amount);
		}
	}
}
