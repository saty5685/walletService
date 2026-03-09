package com.deezyWallet.walletService.event.outbound;

import java.time.LocalDateTime;

import com.deezyWallet.walletService.enums.WalletStatusEnum;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound event — published when wallet status changes.
 *
 * Covers all status transitions:
 *   INACTIVE   → ACTIVE   : eventType = "WALLET_ACTIVATED"
 *   ACTIVE     → FROZEN   : eventType = "WALLET_FROZEN"
 *   FROZEN     → ACTIVE   : eventType = "WALLET_UNFROZEN"
 *   ACTIVE     → CLOSED   : eventType = "WALLET_CLOSED"
 *
 * CONSUMERS:
 *   Notification Service — sends appropriate push/SMS to user
 *     ("Your wallet has been frozen", "Your wallet is now active", etc.)
 *   KYC Service          — listens for WALLET_ACTIVATED to record KYC completion
 *   Transaction Service  — listens for WALLET_FROZEN to reject pending transactions
 *
 * previousStatus is included so consumers don't need to track state themselves.
 * e.g., Notification Service can say "Your wallet was active and is now frozen"
 * without a separate DB lookup.
 *
 * changedBy: "USER" | "ADMIN" | "SYSTEM"
 *   Notification Service uses this to phrase the message differently:
 *     USER   → "You froze your wallet"
 *     ADMIN  → "Your wallet has been frozen by support"
 *     SYSTEM → "Your wallet has been frozen due to suspicious activity"
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletStatusChangedEvent {

	private String        eventId;
	private String        eventType;        // "WALLET_ACTIVATED" | "WALLET_FROZEN" | etc.
	private String        walletId;
	private String        userId;
	private WalletStatusEnum  previousStatus;
	private WalletStatusEnum  newStatus;
	private String        changedBy;        // "USER" | "ADMIN" | "SYSTEM"
	private String        reason;           // optional — for admin/system changes
	private LocalDateTime occurredAt;
}
