package com.deezyWallet.walletService.event.outbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound event — published to "wallet.events" topic after a successful credit.
 *
 * CONSUMERS of this event:
 *   1. Transaction Service — confirms Saga Step 2 succeeded → marks TXN COMPLETED
 *   2. Ledger Service      — records the CREDIT side of the double-entry
 *   3. Notification Service— sends "₹X credited" push/SMS to receiver
 *
 * Structurally identical to WalletDebitedEvent — separate classes because:
 *   - Consumers filter on eventType field to route correctly
 *   - Future schema evolution may diverge (credit-specific fields)
 *   - Explicit types are clearer in code than a generic "WalletEvent" with a type flag
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletCreditedEvent {

	private String        eventId;
	private String        eventType;        // "WALLET_CREDITED"
	private String        walletId;
	private String        userId;
	private BigDecimal    amount;           // amount credited (positive)
	private BigDecimal    newBalance;       // balance after credit
	private String        currency;
	private String        transactionRef;
	private String        idempotencyKey;
	private String        metadata;
	private LocalDateTime occurredAt;
}
