package com.deezyWallet.walletService.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Block funds request — moves amount from balance → blockedBalance.
 * Used in Saga step: reserve funds before confirming the debit.
 *
 * Flow:
 *   BLOCK_CMD → balance -= amount, blockedBalance += amount
 *   DEBIT_CMD → blockedBalance -= amount  (actual debit from held funds)
 *   UNBLOCK_CMD → blockedBalance -= amount, balance += amount  (on failure)
 */
@Getter
@Builder
public class BlockFundsRequest {

	@NotBlank(message = "Wallet ID is required")
	private final String walletId;

	@NotNull(message = "Amount is required")
	@DecimalMin(value = "0.01", message = "Block amount must be at least ₹0.01")
	@Digits(integer = 15, fraction = 4,
			message = "Amount must have at most 15 integer digits and 4 decimal places")
	private final BigDecimal amount;

	@NotBlank(message = "Idempotency key is required")
	private final String idempotencyKey;

	@NotBlank(message = "Transaction reference is required")
	private final String transactionRef;

	public static BlockFundsRequest from(
			com.deezyWallet.walletService.event.inbound.WalletCommandEvent cmd) {
		return BlockFundsRequest.builder()
				.walletId(cmd.getWalletId())
				.amount(cmd.getAmount())
				.idempotencyKey(cmd.getIdempotencyKey())
				.transactionRef(cmd.getTransactionRef())
				.build();
	}
}
