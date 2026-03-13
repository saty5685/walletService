package com.deezyWallet.walletService.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.deezyWallet.walletService.enums.TransactionTypeEnum;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local transaction snapshot — the Wallet Service's own record of every
 * credit/debit that touched a wallet.
 *
 * IMPORTANT ARCHITECTURE NOTE:
 * This is NOT the canonical financial ledger. It is a denormalised snapshot
 * optimised for "show me my recent transactions" queries (paginated, fast).
 *
 * The canonical double-entry ledger lives in the Ledger Service (Cassandra).
 * These two are eventually consistent — WalletTransaction is written
 * synchronously inside the same @Transactional block as the balance change.
 * The Ledger Service entry is written asynchronously after the Kafka event.
 *
 * Key design choices:
 *
 * 1. idempotencyKey with UNIQUE constraint
 *    — The most critical safety mechanism. If a Kafka message is redelivered
 *      (exactly-once delivery is hard), the UNIQUE constraint prevents
 *      the same transaction from being applied twice to the balance.
 *    — Checked at service layer first (fast); DB constraint is the safety net.
 *
 * 2. balanceAfter snapshot
 *    — Stores the wallet balance immediately after this transaction.
 *    — Allows reconstructing the balance history without replaying all events.
 *    — Critical for customer support, disputes, and audit.
 *
 * 3. metadata as JSON string
 *    — Flexible bag for contextual info: merchant name, transfer note, etc.
 *    — Stored as JSONB in Postgres — supports indexed JSON queries.
 *    — Serialised/deserialised as String here to avoid extra dependency.
 *      WalletMapper handles the ObjectMapper conversion.
 *
 * 4. No @ManyToOne to Wallet
 *    — walletId is a plain column + DB FK (same DB, safe for FK here).
 *    — @ManyToOne would eager-load the wallet on every transaction query — wasteful.
 *
 * 5. Immutable after creation
 *    — No setters for financial fields. CreatedDate only, no LastModifiedDate.
 *    — Transaction records are NEVER updated. Corrections create new entries.
 */
@Entity
@Table(
		name = "wallet_transactions",
		indexes = {
				@Index(name = "idx_wtxn_wallet_date",   columnList = "wallet_id, created_at DESC"),
				@Index(name = "idx_wtxn_reference",     columnList = "reference_id"),
				@Index(name = "idx_wtxn_idempotency",   columnList = "idempotency_key")
		}
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(updatable = false, nullable = false)
	private String id;

	/**
	 * The wallet this transaction belongs to.
	 * FK within the same DB — safe to use here unlike the userId cross-service ref.
	 */
	@Column(name = "wallet_id", nullable = false, updatable = false)
	private String walletId;

	/** DEBIT (money out) or CREDIT (money in). */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10, updatable = false)
	private TransactionTypeEnum type;

	/** Amount of this transaction. Always positive. */
	@Column(nullable = false, precision = 19, scale = 4, updatable = false)
	private BigDecimal amount;

	/** Wallet balance immediately after this transaction was applied. */
	@Column(name = "balance_after", nullable = false, precision = 19, scale = 4, updatable = false)
	private BigDecimal balanceAfter;

	@Column(nullable = false, length = 3, updatable = false, columnDefinition = "CHAR(3)")
	@Builder.Default
	private String currency = "INR";

	/** Human-readable description. e.g. "Transfer to Ravi Kumar", "Top-up via UPI" */
	@Column(length = 255)
	private String description;

	/**
	 * The Transaction Service's global transaction ID.
	 * Allows cross-service tracing: wallet txn ↔ ledger entry ↔ external payment.
	 * Nullable for system-generated entries (wallet provisioning, etc.).
	 */
	@Column(name = "reference_id", length = 100, updatable = false)
	private String referenceId;

	/**
	 * Idempotency key from the caller. Stored with UNIQUE DB constraint.
	 * Prevents double-application of the same operation if Kafka redelivers.
	 * Format: UUID provided by Transaction Service in the command event.
	 */
	@Column(name = "idempotency_key", length = 128, unique = true, updatable = false)
	private String idempotencyKey;

	/**
	 * JSON string — flexible metadata.
	 * Examples:
	 *   {"merchantName":"Amazon","category":"Shopping","upiRef":"UPI12345"}
	 *   {"transferNote":"Splitting dinner","receiverName":"Priya S."}
	 * Stored as JSON in mysql — can be queried with JSON operators.
	 */
	@Column(columnDefinition = "json")
	private String metadata;

	/** Immutable creation timestamp. */
	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
