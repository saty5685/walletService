package com.deezyWallet.walletService.exception;

import org.springframework.http.HttpStatus;

import com.deezyWallet.walletService.constants.WalletErrorCode;
import com.deezyWallet.walletService.entity.WalletTransaction;

import lombok.Getter;

/**
 * Thrown when a debit/credit arrives with an idempotency key that was
 * already processed — i.e., this is a duplicate operation.
 *
 * HTTP: 200 OK — NOT an error status!
 *
 * IDEMPOTENCY SEMANTICS:
 *   A duplicate request with the same idempotency key must return the SAME
 *   response as the original successful request. This is the HTTP idempotency
 *   contract used by Stripe, Razorpay, and all major payment APIs.
 *
 *   Returning 409 Conflict or 422 would break Saga retry logic — the Transaction
 *   Service would think the operation failed and trigger compensation, even though
 *   the money was already moved correctly on the first attempt.
 *
 *   So GlobalExceptionHandler catches this and returns 200 OK with the
 *   original transaction data.
 *
 * existingTransaction: the original WalletTransaction record for this key.
 *   Fetched by WalletService before throwing — passed here so GlobalExceptionHandler
 *   can return the original response without a second DB lookup.
 */
@Getter
public class DuplicateOperationException extends WalletBaseException {

	private final String            idempotencyKey;

	/**
	 * The already-processed transaction record — used by GlobalExceptionHandler
	 * to return the same response as the original call.
	 * Nullable: if the duplicate is detected via Redis lock (before DB write),
	 * the transaction may not yet be retrievable.
	 */
	private final WalletTransaction existingTransaction;

	public DuplicateOperationException(String idempotencyKey,
			WalletTransaction existingTransaction) {
		super(
				WalletErrorCode.ALREADY_PROCESSED,
				"Operation already processed. Idempotency key: " + idempotencyKey,
				HttpStatus.OK   // 200 — not an error
		);
		this.idempotencyKey      = idempotencyKey;
		this.existingTransaction = existingTransaction;
	}

	/** Constructor when existing transaction not yet available (Redis-layer dedup). */
	public DuplicateOperationException(String idempotencyKey) {
		this(idempotencyKey, null);
	}
}