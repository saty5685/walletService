package com.deezyWallet.walletService.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Debit command DTO — used internally between WalletService and the Kafka
 * consumer layer. NOT exposed directly as a REST request body.
 *
 * The REST layer never accepts raw debit/credit requests from end-users —
 * those go through the Transaction Service which then commands this service
 * via Kafka. This prevents users from directly manipulating wallet balances.
 *
 * Sources:
 *   1. Built from WalletCommandEvent consumed from "wallet.commands" Kafka topic
 *   2. Built directly in WalletService for internal operations (top-up, refund)
 *
 * Validation annotations here serve as a safety net for programmatic creation —
 * e.g., if TransactionEventConsumer.DebitRequest.from(cmd) produces invalid data.
 */
@Getter
@Builder
public class DebitRequest {

	/**
	 * The wallet to debit.
	 * This is the Wallet's internal UUID — not the wallet number.
	 */
	@NotBlank(message = "Wallet ID is required")
	private final String walletId;

	/**
	 * Amount to debit — must be positive and have at most 4 decimal places.
	 *
	 * @DecimalMin("0.01") — minimum transaction amount ₹0.01 (1 paisa)
	 * @Digits — prevents amounts like 123456789012345.12345 that would
	 *           overflow DECIMAL(19,4)
	 */
	@NotNull(message = "Amount is required")
	@DecimalMin(value = "0.01", message = "Debit amount must be at least ₹0.01")
	@Digits(integer = 15, fraction = 4,
			message = "Amount must have at most 15 integer digits and 4 decimal places")
	private final BigDecimal amount;

	/**
	 * UUID from the Transaction Service — used for idempotency.
	 * This exact key is stored in wallet_transactions.idempotency_key.
	 * If the same key arrives again (Kafka redelivery), it is silently ignored.
	 */
	@NotBlank(message = "Idempotency key is required")
	private final String idempotencyKey;

	/**
	 * Transaction Service's global transaction reference.
	 * Stored in WalletTransaction.referenceId — enables cross-service tracing.
	 * Format: "TXN-YYYYMMDD-XXXXXXXX"
	 */
	@NotBlank(message = "Transaction reference is required")
	private final String transactionRef;

	/**
	 * Human-readable description shown to the user in transaction history.
	 * e.g., "Transfer to Ravi Kumar", "Merchant payment – Amazon"
	 */
	private final String description;

	/**
	 * JSON string — optional contextual metadata.
	 * e.g., {"merchantName":"Amazon","category":"Shopping","upiRef":"UPI123"}
	 * Passed through to WalletTransaction.metadata as-is.
	 */
	private final String metadata;

	/**
	 * Factory method — builds DebitRequest from a Kafka WalletCommandEvent.
	 * Centralises the mapping in one place so consumers don't duplicate logic.
	 */
	public static DebitRequest from(
			com.deezyWallet.walletService.event.inbound.WalletCommandEvent cmd) {
		return DebitRequest.builder()
				.walletId(cmd.getWalletId())
				.amount(cmd.getAmount())
				.idempotencyKey(cmd.getIdempotencyKey())
				.transactionRef(cmd.getTransactionRef())
				.description(cmd.getDescription())
				.metadata(cmd.getMetadata())
				.build();
	}
}

