package com.deezyWallet.walletService.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks cumulative spend per wallet for limit enforcement.
 *
 * Why a separate table (not on the Wallet row)?
 *
 * Option A: Store dailySpent/monthlySpent on the Wallet entity.
 *   Problem: Every debit must UPDATE the wallet row — which holds the
 *   pessimistic lock. Daily/monthly reset also requires a batch UPDATE on
 *   millions of rows at midnight.
 *
 * Option B: Separate WalletLimit table (this approach).
 *   Benefits:
 *   — Limit tracking and balance mutation are separate locks.
 *   — Easy to query: "all wallets that hit their daily limit today".
 *   — Reset is trivial: new row per day (upsert pattern).
 *   — Historical limit data is preserved — useful for fraud analysis.
 *
 * Row-per-day pattern:
 *   One row per (wallet_id, limit_date).
 *   UNIQUE constraint on (wallet_id, limit_date).
 *   On first debit of a new day → INSERT new row with dailySpent = amount.
 *   On subsequent debits same day → UPDATE dailySpent += amount.
 *   WalletLimitService uses "INSERT ... ON CONFLICT DO UPDATE" (upsert).
 *
 * Monthly tracking uses the same row (limit_date = first day of month).
 * The WalletLimitService queries WHERE limit_date >= first_day_of_month
 * and sums dailySpent — no separate monthly column needed.
 *
 * Why not Redis for limit tracking?
 *   Redis is volatile. If it goes down mid-day, limits are lost.
 *   For financial compliance (RBI), limits MUST be durable.
 *   Redis can be used as a read-cache for limit checks, but DB is the source.
 */
@Entity
@Table(
		name = "wallet_limits",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uq_wallet_limit_date",
						columnNames = {"wallet_id", "limit_date"}
				)
		},
		indexes = {
				@Index(name = "idx_wlimit_wallet_date", columnList = "wallet_id, limit_date DESC")
		}
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletLimit {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(updatable = false, nullable = false)
	private String id;

	/** FK to wallets.id — same DB, safe for FK here. */
	@Column(name = "wallet_id", nullable = false, updatable = false)
	private String walletId;

	/**
	 * Total amount debited from this wallet on limit_date.
	 * Incremented atomically on every successful debit.
	 * Never decremented — credits do NOT reduce daily spend.
	 * (Returning money to a wallet doesn't reset how much you spent.)
	 */
	@Column(name = "daily_spent", nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal dailySpent = BigDecimal.ZERO;

	/**
	 * The calendar date this row tracks.
	 * WalletLimitService creates one row per day via upsert.
	 * Row for today: limitDate = LocalDate.now()
	 */
	@Column(name = "limit_date", nullable = false, updatable = false)
	private LocalDate limitDate;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}

