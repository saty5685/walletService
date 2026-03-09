package com.deezyWallet.walletService.event.outbound;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound event — published when a debit FAILS (insufficient balance,
 * frozen wallet, limit exceeded, etc.).
 *
 * This is the SAGA COMPENSATION TRIGGER.
 *
 * CONSUMER: Transaction Service ONLY.
 *   On receipt → marks TXN as FAILED → no credit is issued → Saga ends.
 *   Transaction Service may also publish its own compensation events
 *   (e.g., notify the sender that the transfer failed).
 *
 * WHY a failure event instead of just not publishing a success event?
 *   Silence is ambiguous. If the Wallet Service crashes after debiting
 *   but before publishing, the Transaction Service would wait forever.
 *   An explicit failure event is unambiguous: "this debit did not happen".
 *
 * failureCode maps to WalletErrorCode constants so Transaction Service
 * can show the right error message to the user without string parsing.
 *
 * NOTE: This event is published even when the debit is ACK'd (i.e., it was
 * a valid business failure, not a system error). The message is ACK'd to
 * prevent Kafka redelivery — the failure event IS the response.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletDebitFailedEvent {

	private String        eventId;
	private String        eventType;        // "WALLET_DEBIT_FAILED"
	private String        walletId;
	private String        userId;
	private String        transactionRef;   // correlates back to the original TXN
	private String        idempotencyKey;

	/**
	 * Machine-readable failure reason — maps to WalletErrorCode constants.
	 * e.g., "WALLET_INSUFFICIENT_BALANCE", "WALLET_FROZEN", "WALLET_DAILY_LIMIT_EXCEEDED"
	 * Transaction Service switches on this to show the right error to the user.
	 */
	private String        failureCode;

	/** Human-readable message — for logging and support tooling. */
	private String        failureMessage;

	private LocalDateTime occurredAt;
}

