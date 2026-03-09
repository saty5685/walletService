package com.deezyWallet.walletService.constants;

/**
 * Standardised error codes returned in ErrorResponse.code.
 *
 * Format: <DOMAIN>_<PROBLEM>
 * These codes are stable API contracts — clients use them for programmatic
 * error handling. Never change an existing code; only add new ones.
 *
 * Usage in GlobalExceptionHandler:
 *   ErrorResponse.of(WalletErrorCode.INSUFFICIENT_BALANCE, message)
 */

public class WalletErrorCode {
	private WalletErrorCode() {}

	// ── Balance errors ────────────────────────────────────────────────────────
	public static final String INSUFFICIENT_BALANCE   = "WALLET_INSUFFICIENT_BALANCE";

	// ── Wallet state errors ───────────────────────────────────────────────────
	public static final String WALLET_NOT_FOUND       = "WALLET_NOT_FOUND";
	public static final String WALLET_FROZEN          = "WALLET_FROZEN";
	public static final String WALLET_INACTIVE        = "WALLET_INACTIVE";
	public static final String WALLET_CLOSED          = "WALLET_CLOSED";
	public static final String WALLET_ALREADY_EXISTS  = "WALLET_ALREADY_EXISTS";

	// ── Limit errors ──────────────────────────────────────────────────────────
	public static final String DAILY_LIMIT_EXCEEDED   = "WALLET_DAILY_LIMIT_EXCEEDED";
	public static final String MONTHLY_LIMIT_EXCEEDED = "WALLET_MONTHLY_LIMIT_EXCEEDED";

	// ── Idempotency ───────────────────────────────────────────────────────────
	public static final String ALREADY_PROCESSED      = "WALLET_ALREADY_PROCESSED";
	public static final String DUPLICATE_REQUEST      = "WALLET_DUPLICATE_REQUEST";

	// ── Validation ────────────────────────────────────────────────────────────
	public static final String INVALID_AMOUNT         = "WALLET_INVALID_AMOUNT";
	public static final String INVALID_CURRENCY       = "WALLET_INVALID_CURRENCY";
	public static final String VALIDATION_FAILED      = "WALLET_VALIDATION_FAILED";

	// ── Auth / Access ─────────────────────────────────────────────────────────
	public static final String ACCESS_DENIED          = "WALLET_ACCESS_DENIED";
	public static final String UNAUTHORIZED           = "WALLET_UNAUTHORIZED";

	// ── Internal / System ─────────────────────────────────────────────────────
	public static final String INTERNAL_ERROR         = "WALLET_INTERNAL_ERROR";
	public static final String SERVICE_UNAVAILABLE    = "WALLET_SERVICE_UNAVAILABLE";
}
