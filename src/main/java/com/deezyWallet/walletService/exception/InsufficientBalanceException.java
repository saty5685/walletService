package com.deezyWallet.walletService.exception;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;

import com.deezyWallet.walletService.constants.WalletErrorCode;

import lombok.Getter;

/**
 * Thrown when a debit would take the wallet balance below zero.
 *
 * HTTP: 422 Unprocessable Entity
 *   - Not 400 (the request is syntactically valid)
 *   - Not 402 Payment Required (semantically wrong for wallets)
 *   - 422 = "we understand the request, but cannot process it due to business state"
 *
 * Carries available and required amounts so the error response can tell
 * the user exactly what they have vs what they need — without a follow-up call.
 *
 * NOT retried by Kafka consumer — insufficient balance won't fix itself
 * between retries. Immediately routes to DLQ as a business failure.
 */
@Getter
public class InsufficientBalanceException extends WalletBaseException {

	private final String     walletId;
	private final BigDecimal available;   // current wallet balance
	private final BigDecimal required;    // amount that was requested

	public InsufficientBalanceException(String walletId,
			BigDecimal available,
			BigDecimal required) {
		super(
				WalletErrorCode.INSUFFICIENT_BALANCE,
				String.format("Insufficient balance in wallet %s. Available: ₹%s, Required: ₹%s",
						walletId,
						available.toPlainString(),
						required.toPlainString()),
				HttpStatus.UNPROCESSABLE_ENTITY
		);
		this.walletId  = walletId;
		this.available = available;
		this.required  = required;
	}
}
