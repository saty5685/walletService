package com.deezyWallet.walletService.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.deezyWallet.walletService.enums.WalletStatusEnum;
import com.deezyWallet.walletService.enums.WalletTypeEnum;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

/**
 * Full wallet details response — returned by GET /api/v1/wallets/me
 *
 * What is INTENTIONALLY excluded:
 *   - version (internal JPA field — leaks implementation detail)
 *   - userId (caller already knows their own userId from JWT)
 *   - blockedBalance is included — useful for UX ("₹500 pending")
 *
 * @JsonInclude(NON_NULL) — omits null fields from JSON output.
 * e.g., if dailyLimitRemaining is null (not calculated), it won't appear in response.
 *
 * All BigDecimal amounts serialised as strings in JSON to prevent
 * floating-point precision loss in JavaScript clients.
 * Configured globally via Jackson's WRITE_BIGDECIMAL_AS_PLAIN + string serializer.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletResponse {

	private final String       id;
	private final String       walletNumber;       // "WLT-20240308-00001"
	private final BigDecimal   balance;
	private final BigDecimal   blockedBalance;     // funds held for pending txns
	private final String       currency;           // "INR"
	private final WalletStatusEnum status;
	private final WalletTypeEnum   type;
	private final BigDecimal   dailyLimit;
	private final BigDecimal   monthlyLimit;
	private final BigDecimal   dailyLimitRemaining;   // computed: dailyLimit - todaySpent
	private final BigDecimal   monthlyLimitRemaining; // computed: monthlyLimit - monthSpent
	private final LocalDateTime createdAt;
	private final LocalDateTime updatedAt;
}

