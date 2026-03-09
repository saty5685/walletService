package com.deezyWallet.walletService.exception;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;

import com.deezyWallet.walletService.constants.WalletErrorCode;
import com.deezyWallet.walletService.enums.WalletLimitTypeEnum;

import lombok.Getter;

/**
 * Thrown when a debit would exceed the wallet's daily or monthly spend limit.
 *
 * HTTP: 422 Unprocessable Entity — same reasoning as InsufficientBalanceException.
 *
 * Carries the full limit context so the client can show:
 *   "Daily limit: ₹50,000 | Spent today: ₹48,500 | This transaction: ₹2,000"
 *   → "You have ₹1,500 remaining in your daily limit."
 *
 * limitType (DAILY | MONTHLY) drives the error code:
 *   DAILY   → WalletErrorCode.DAILY_LIMIT_EXCEEDED
 *   MONTHLY → WalletErrorCode.MONTHLY_LIMIT_EXCEEDED
 *
 * These map to separate error codes (not one generic LIMIT_EXCEEDED) so
 * Transaction Service can show the user the right remediation action:
 *   DAILY   → "Try again tomorrow"
 *   MONTHLY → "Try again next month or upgrade your KYC"
 */
@Getter
public class WalletLimitExceededException extends WalletBaseException {

	private final WalletLimitTypeEnum  limitType;
	private final BigDecimal limit;       // the configured limit
	private final BigDecimal spent;       // already spent in the window
	private final BigDecimal attempted;   // amount that triggered this exception

	public WalletLimitExceededException(String walletId,
			WalletLimitTypeEnum limitType,
			BigDecimal limit,
			BigDecimal spent,
			BigDecimal attempted) {
		super(
				resolveErrorCode(limitType),
				String.format("%s limit exceeded for wallet %s. Limit: ₹%s, Spent: ₹%s, Attempted: ₹%s",
						limitType.name(),
						walletId,
						limit.toPlainString(),
						spent.toPlainString(),
						attempted.toPlainString()),
				HttpStatus.UNPROCESSABLE_ENTITY
		);
		this.limitType = limitType;
		this.limit     = limit;
		this.spent     = spent;
		this.attempted = attempted;
	}

	private static String resolveErrorCode(WalletLimitTypeEnum limitType) {
		return limitType == WalletLimitTypeEnum.DAILY
				? WalletErrorCode.DAILY_LIMIT_EXCEEDED
				: WalletErrorCode.MONTHLY_LIMIT_EXCEEDED;
	}
}
