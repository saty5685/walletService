package com.deezyWallet.walletService.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

/**
 * Lightweight balance response — returned by GET /api/v1/wallets/me/balance
 * and GET /internal/v1/wallets/{id}/balance
 *
 * Intentionally minimal — callers (Transaction Service, UI) only need
 * the balance number, not full wallet details.
 *
 * source: "CACHE" or "DB" — useful for debugging, hidden in production via
 * @JsonInclude(NON_NULL) when source is not set.
 *
 * The 'asOf' timestamp tells the caller when this balance was last confirmed.
 * For CACHE responses, this is when the value was cached (may be up to 30s old).
 * For DB responses, this is NOW.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceResponse {

	private final String     walletId;
	private final BigDecimal balance;
	private final BigDecimal blockedBalance;
	private final String     currency;
	private final String     source;          // "CACHE" | "DB" — debug only
	private final LocalDateTime asOf;

	/**
	 * Factory method for service layer — avoids Builder boilerplate at call sites.
	 */
	public static BalanceResponse of(String walletId,
			BigDecimal balance,
			String source) {
		return BalanceResponse.builder()
				.walletId(walletId)
				.balance(balance)
				.currency("INR")
				.source(source)
				.asOf(LocalDateTime.now())
				.build();
	}

	/**
	 * Full factory with blocked balance — for internal service calls that need
	 * the complete picture (e.g., Transaction Service checking available funds).
	 */
	public static BalanceResponse of(String walletId,
			BigDecimal balance,
			BigDecimal blockedBalance,
			String source) {
		return BalanceResponse.builder()
				.walletId(walletId)
				.balance(balance)
				.blockedBalance(blockedBalance)
				.currency("INR")
				.source(source)
				.asOf(LocalDateTime.now())
				.build();
	}
}