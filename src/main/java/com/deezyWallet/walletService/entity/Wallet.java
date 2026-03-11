package com.deezyWallet.walletService.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.deezyWallet.walletService.constants.WalletConstants;
import com.deezyWallet.walletService.enums.WalletStatusEnum;
import com.deezyWallet.walletService.enums.WalletTypeEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Core financial entity representing a user's wallet.
 *
 * Design decisions baked in:
 *
 * 1. UUID primary key (GenerationType.UUID)
 *    — Non-sequential. Cannot be enumerated by attackers.
 *    — Globally unique across all services — safe to pass in events.
 *
 * 2. No @ManyToOne to User entity
 *    — userId is a plain UUID column, NOT a JPA foreign key.
 *    — Wallet DB and User DB are separate schemas on separate services.
 *    — Referential integrity enforced via domain events, not DB constraints.
 *
 * 3. NUMERIC(19,4) for all monetary columns
 *    — Never float or double. Those types cannot represent ₹0.10 exactly.
 *    — Scale 4 supports paisa (₹0.0001) — necessary for interest calculations.
 *    — Precision 19 supports up to ₹999,999,999,999,999 — well above any limit.
 *
 * 4. @Version for optimistic locking
 *    — Applied to the whole entity row.
 *    — Used on CREDIT paths (additive, safe to retry on conflict).
 *    — DEBIT path uses pessimistic lock (SELECT FOR UPDATE) instead — see Repository.
 *
 * 5. blockedBalance column
 *    — Funds reserved for in-flight transactions (during Saga execution).
 *    — Available balance for spending = balance (blocked funds are already
 *      subtracted during BLOCK_CMD and restored on UNBLOCK_CMD or failure).
 *    — DB constraint ensures balance + blockedBalance never goes negative.
 *
 * 6. Audit fields via Spring Data JPA @EntityListeners
 *    — createdAt / updatedAt set automatically.
 *    — Requires @EnableJpaAuditing on the main application class.
 */
@Entity
@Table(
		name = "wallets",
		indexes = {
				@Index(name = "idx_wallets_user_id",       columnList = "user_id"),
				@Index(name = "idx_wallets_wallet_number",  columnList = "wallet_number"),
				@Index(name = "idx_wallets_status",         columnList = "status")
		}
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

	// ── Identity ──────────────────────────────────────────────────────────────

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(updatable = false, nullable = false)
	private String id;

	/**
	 * Human-readable wallet identifier.
	 * Format: WLT-YYYYMMDD-NNNNN  e.g. "WLT-20240308-00001"
	 * Generated in WalletService.generateWalletNumber() — not by DB.
	 * Exposed to users (in receipts, statements). The internal UUID id is never shown.
	 */
	@Column(name = "wallet_number", nullable = false, unique = true, length = 25)
	private String walletNumber;

	/**
	 * Owner of this wallet. Cross-service reference — no JPA FK.
	 * Indexed for fast lookups: "find wallet by user".
	 */
	@Column(name = "user_id", nullable = false, updatable = false)
	private String userId;

	// ── Financial fields ──────────────────────────────────────────────────────

	/**
	 * Spendable balance. Always >= 0 (enforced by DB CHECK constraint).
	 * Mutated ONLY inside @Transactional methods with appropriate locking.
	 * Never read without cache check first — see BalanceCacheService.
	 */
	@Column(nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal balance = WalletConstants.ZERO;

	/**
	 * Funds reserved for in-flight Saga transactions.
	 * During a P2P transfer Saga:
	 *   BLOCK_CMD  → balance -= amount, blockedBalance += amount
	 *   DEBIT_CMD  → blockedBalance -= amount  (actual debit from blocked)
	 *   UNBLOCK_CMD→ blockedBalance -= amount, balance += amount (on failure)
	 *
	 * NOTE: Current simplified implementation uses direct DEBIT without
	 * blocking. BLOCK/UNBLOCK is wired for future escrow-style transactions.
	 */
	@Column(name = "blocked_balance", nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal blockedBalance = WalletConstants.ZERO;

	/**
	 * ISO-4217 currency code. Hardcoded "INR" for now.
	 * Schema is ready for multi-currency — just add conversion logic later.
	 */
	@Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
	@Builder.Default
	private String currency = WalletConstants.DEFAULT_CURRENCY;

	// ── Status & Type ─────────────────────────────────────────────────────────

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private WalletStatusEnum status = WalletStatusEnum.INACTIVE;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private WalletTypeEnum type = WalletTypeEnum.PERSONAL;

	// ── Spend limits ──────────────────────────────────────────────────────────

	/**
	 * Maximum total debits allowed in a single calendar day.
	 * Default: ₹50,000 (RBI semi-closed PPI guideline).
	 * User can lower this but not exceed MAX_DAILY_LIMIT (₹1,00,000).
	 */
	@Column(name = "daily_limit", nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal dailyLimit = WalletConstants.DEFAULT_DAILY_LIMIT;

	/**
	 * Maximum total debits in a calendar month.
	 * Default: ₹2,00,000 (RBI full-KYC PPI guideline).
	 */
	@Column(name = "monthly_limit", nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal monthlyLimit = WalletConstants.DEFAULT_MONTHLY_LIMIT;

	// ── Concurrency control ───────────────────────────────────────────────────

	/**
	 * JPA optimistic locking version counter.
	 * Incremented by Hibernate on every UPDATE.
	 * If two transactions read version=5 and both try to UPDATE,
	 * the second will get an OptimisticLockException → retry.
	 * Used on CREDIT path. DEBIT uses pessimistic lock instead.
	 */
	@Version
	@Column(nullable = false)
	@Builder.Default
	private Long version = 0L;

	// ── Audit ─────────────────────────────────────────────────────────────────

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	// ── Business helpers ──────────────────────────────────────────────────────

	/** Computed field — NOT persisted. Use for UI display only. */
	@Transient
	public BigDecimal getAvailableBalance() {
		return balance; // in simplified model, balance IS available balance
	}

	/** True if this wallet can send money right now. */
	@Transient
	public boolean isOperational() {
		return status == WalletStatusEnum.ACTIVE;
	}

	/** True if credits are allowed (ACTIVE only — frozen wallets reject credits too). */
	@Transient
	public boolean canReceive() {
		return status == WalletStatusEnum.ACTIVE;
	}
}
