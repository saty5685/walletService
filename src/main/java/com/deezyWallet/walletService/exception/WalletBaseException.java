package com.deezyWallet.walletService.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Abstract base for all Wallet Service business exceptions.
 *
 * WHY A BASE EXCEPTION?
 *
 * Without a base class, GlobalExceptionHandler needs a separate @ExceptionHandler
 * for every exception type. With a base class, we get:
 *
 * 1. SHARED STRUCTURE
 *    Every business exception carries: errorCode (WalletErrorCode constant),
 *    httpStatus (what HTTP code to return), and a message.
 *    GlobalExceptionHandler handles ALL subclasses in one method.
 *
 * 2. CONSISTENT KAFKA BEHAVIOUR
 *    TransactionEventConsumer's catch block can catch WalletBaseException
 *    and route ALL business failures to the DLQ correctly — without listing
 *    every exception type individually.
 *
 * 3. RETRY DISCRIMINATION
 *    KafkaConsumerConfig.addNotRetryableExceptions() registers WalletBaseException.
 *    Business exceptions (insufficient balance, frozen wallet) will NEVER
 *    be retried — they won't fix themselves. System exceptions (DB timeout,
 *    Redis unavailable) ARE retried. This split is clean when one class covers all.
 *
 * HttpStatus is on the exception itself — not decided in the handler.
 * Each exception knows what HTTP code it maps to. Handler just reads it.
 * This keeps the HTTP contract close to the domain concept.
 */
@Getter
public abstract class WalletBaseException extends RuntimeException {

	/** Maps to WalletErrorCode constant — machine-readable, stable API contract. */
	private final String     errorCode;

	/** HTTP status to return — set by each subclass. */
	private final HttpStatus httpStatus;

	protected WalletBaseException(String errorCode,
			String message,
			HttpStatus httpStatus) {
		super(message);
		this.errorCode  = errorCode;
		this.httpStatus = httpStatus;
	}

	protected WalletBaseException(String errorCode,
			String message,
			HttpStatus httpStatus,
			Throwable cause) {
		super(message, cause);
		this.errorCode  = errorCode;
		this.httpStatus = httpStatus;
	}
}

