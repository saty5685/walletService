package com.deezyWallet.walletService.event.outbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound event — published to "wallet.events" topic after a successful debit.
 *
 * CONSUMERS of this event:
 *   1. Transaction Service — confirms Saga Step 1 succeeded, proceeds to CREDIT_CMD
 *   2. Ledger Service      — records the DEBIT side of the double-entry
 *   3. Notification Service— sends "₹X debited" push/SMS to user
 *
 * DESIGN RULES for outbound events:
 *
 * 1. Include newBalance
 *    Transaction Service needs it to update the transaction record.
 *    Notification Service uses it for the "new balance: ₹X" message.
 *    Ledger Service uses it as a sanity check for reconciliation.
 *
 * 2. Include idempotencyKey
 *    Consumers (especially Ledger Service) use this to dedup their own
 *    processing — same key seen twice = already recorded, skip.
 *
 * 3. Include transactionRef
 *    Transaction Service uses this to correlate the event back to the
 *    original transaction record and update its status to DEBIT_CONFIRMED.
 *
 * 4. @JsonInclude(NON_NULL) on the class
 *    metadata is optional — don't include null metadata field in the JSON.
 *    Keeps event payloads lean — Kafka messages are small by design.
 *
 * 5. @NoArgsConstructor required
 *    If this event is ever consumed by another service using Jackson,
 *    no-arg constructor is needed. Always include it on event POJOs.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletDebitedEvent {

	private String        eventId;          // UUID — unique per event instance
	private String        eventType;        // "WALLET_DEBITED" — constant string
	private String        walletId;
	private String        userId;
	private BigDecimal    amount;           // amount debited (positive)
	private BigDecimal    newBalance;       // balance after debit
	private String        currency;
	private String        transactionRef;   // links back to Transaction Service
	private String        idempotencyKey;   // for consumer-side dedup
	private String        metadata;         // optional JSON string
	private LocalDateTime occurredAt;
}

