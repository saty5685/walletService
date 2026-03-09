package com.deezyWallet.walletService.constants;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Application-wide constants for the Wallet Service.
 *
 * Rules:
 *  - All monetary BigDecimal constants use scale=4 and HALF_EVEN rounding
 *    (banker's rounding — standard for financial systems).
 *  - Never use magic numbers or inline strings in business logic.
 *    Reference these constants instead.
 */

public final class WalletConstants {
	private WalletConstants() { /* utility class — no instantiation */ }

	// ── Monetary defaults (RBI PPI guidelines for semi-closed wallets) ────────
	public static final BigDecimal DEFAULT_DAILY_LIMIT   =
			new BigDecimal("50000.0000");      // ₹50,000/day

	public static final BigDecimal DEFAULT_MONTHLY_LIMIT =
			new BigDecimal("200000.0000");     // ₹2,00,000/month

	public static final BigDecimal MAX_DAILY_LIMIT       =
			new BigDecimal("100000.0000");     // User cannot self-set above ₹1L

	public static final BigDecimal MAX_MONTHLY_LIMIT     =
			new BigDecimal("500000.0000");     // ₹5L — requires full KYC

	public static final BigDecimal ZERO =
			BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

	public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
	public static final int          MONETARY_SCALE = 4;

	// ── Default currency ──────────────────────────────────────────────────────
	public static final String DEFAULT_CURRENCY = "INR";

	// ── Wallet number prefix ──────────────────────────────────────────────────
	public static final String WALLET_NUMBER_PREFIX = "WLT";

	// ── Redis key prefixes ────────────────────────────────────────────────────
	public static final String REDIS_BALANCE_PREFIX      = "wallet:balance:";
	public static final String REDIS_LOCK_PREFIX         = "wallet:lock:";
	public static final String REDIS_USER_WALLET_PREFIX  = "wallet:user:";    // userId → walletId mapping

	// ── Redis TTLs (seconds) ──────────────────────────────────────────────────
	public static final long BALANCE_CACHE_TTL_SECONDS       = 30L;
	public static final long PROCESSING_LOCK_TTL_SECONDS     = 10L;
	public static final long USER_WALLET_CACHE_TTL_SECONDS   = 300L;  // 5 min — rarely changes

	// ── Kafka topics ──────────────────────────────────────────────────────────
	public static final String TOPIC_USER_EVENTS        = "user.events";
	public static final String TOPIC_WALLET_COMMANDS    = "wallet.commands";
	public static final String TOPIC_WALLET_EVENTS      = "wallet.events";
	public static final String TOPIC_WALLET_DLQ         = "wallet.commands.dlq";

	// ── Kafka consumer group IDs ──────────────────────────────────────────────
	public static final String GROUP_WALLET_SERVICE     = "wallet-service";

	// ── Retry config ──────────────────────────────────────────────────────────
	public static final int    MAX_RETRY_ATTEMPTS       = 3;
	public static final long   RETRY_BACKOFF_MS_1       = 1_000L;   // 1s
	public static final long   RETRY_BACKOFF_MS_2       = 5_000L;   // 5s
	public static final long   RETRY_BACKOFF_MS_3       = 30_000L;  // 30s

	// ── Security ──────────────────────────────────────────────────────────────
	public static final String ROLE_USER             = "ROLE_USER";
	public static final String ROLE_ADMIN            = "ROLE_ADMIN";
	public static final String ROLE_MERCHANT         = "ROLE_MERCHANT";
	public static final String ROLE_INTERNAL_SERVICE = "ROLE_INTERNAL_SERVICE";

	// ── API path prefixes ─────────────────────────────────────────────────────
	public static final String API_V1_WALLETS          = "/api/v1/wallets";
	public static final String INTERNAL_V1_WALLETS     = "/internal/v1/wallets";
	public static final String ADMIN_V1_WALLETS        = "/api/v1/admin/wallets";

	// ── Pagination defaults ───────────────────────────────────────────────────
	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE     = 100;

	// ── Service identity (for internal JWT claims) ────────────────────────────
	public static final String SERVICE_NAME = "wallet-service";
}
