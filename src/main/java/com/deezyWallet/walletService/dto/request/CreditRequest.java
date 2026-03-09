package com.deezyWallet.walletService.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Credit command DTO — mirrors DebitRequest structure.
 *
 * Key difference from DebitRequest:
 *   Credits do NOT enforce spend limits (daily/monthly limits track outgoing
 *   money only). The WalletLimitService is not called for credits.
 *
 * Credits CAN be applied to FROZEN wallets in some scenarios
 * (e.g., refund to a frozen wallet). This is enforced in WalletService —
 * the DTO itself is neutral.
 */
@Getter
@Builder
public class CreditRequest {

	@NotBlank(message = "Wallet ID is required")
	private final String walletId;

	@NotNull(message = "Amount is required")
	@DecimalMin(value = "0.01", message = "Credit amount must be at least ₹0.01")
	@Digits(integer = 15, fraction = 4,
			message = "Amount must have at most 15 integer digits and 4 decimal places")
	private final BigDecimal amount;

	@NotBlank(message = "Idempotency key is required")
	private final String idempotencyKey;

	@NotBlank(message = "Transaction reference is required")
	private final String transactionRef;

	private final String description;
	private final String metadata;

	/**
	 * Factory method — builds CreditRequest from a Kafka WalletCommandEvent.
	 */
	public static CreditRequest from(
			com.deezyWallet.walletService.event.inbound.WalletCommandEvent cmd) {
		return CreditRequest.builder()
				.walletId(cmd.getWalletId())
				.amount(cmd.getAmount())
				.idempotencyKey(cmd.getIdempotencyKey())
				.transactionRef(cmd.getTransactionRef())
				.description(cmd.getDescription())
				.metadata(cmd.getMetadata())
				.build();
	}
}

