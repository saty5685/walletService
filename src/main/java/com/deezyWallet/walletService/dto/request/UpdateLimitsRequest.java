package com.deezyWallet.walletService.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * REST request body — user updating their own spend limits.
 * Exposed via PUT /api/v1/wallets/me/limits
 *
 * Business rules enforced here at validation layer:
 *   1. Both fields are required — partial updates not allowed (prevents
 *      incoherent state where daily > monthly)
 *   2. dailyLimit cannot exceed MAX_DAILY_LIMIT (₹1,00,000)
 *   3. monthlyLimit cannot exceed MAX_MONTHLY_LIMIT (₹5,00,000)
 *   4. dailyLimit <= monthlyLimit enforced in WalletService (cross-field
 *      validation — not expressible with single-field annotations)
 *
 * NOTE: Users can only LOWER their limits here. Raising above the default
 * requires KYC upgrade — enforced in WalletService.updateLimits().
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLimitsRequest {

	@NotNull(message = "Daily limit is required")
	@DecimalMin(value = "100.00",
			message = "Daily limit must be at least ₹100")
	@DecimalMax(value = "100000.00",
			message = "Daily limit cannot exceed ₹1,00,000")
	@Digits(integer = 9, fraction = 4,
			message = "Invalid amount format")
	private BigDecimal dailyLimit;

	@NotNull(message = "Monthly limit is required")
	@DecimalMin(value = "1000.00",
			message = "Monthly limit must be at least ₹1,000")
	@DecimalMax(value = "500000.00",
			message = "Monthly limit cannot exceed ₹5,00,000")
	@Digits(integer = 9, fraction = 4,
			message = "Invalid amount format")
	private BigDecimal monthlyLimit;
}
