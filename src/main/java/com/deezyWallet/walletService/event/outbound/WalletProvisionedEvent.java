package com.deezyWallet.walletService.event.outbound;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound event — published after a new wallet is successfully provisioned.
 *
 * CONSUMERS:
 *   Notification Service — sends "Welcome! Your wallet WLT-XXXX is ready" message.
 *
 * NOTE: Wallet is INACTIVE at this point — not yet usable.
 * The WALLET_ACTIVATED event (via WalletStatusChangedEvent) fires separately
 * when USER_VERIFIED arrives from User Service.
 *
 * walletNumber is included so Notification Service can show it to the user
 * without a separate lookup.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletProvisionedEvent {

	private String        eventId;
	private String        eventType;      // "WALLET_PROVISIONED"
	private String        walletId;
	private String        userId;
	private String        walletNumber;   // "WLT-20240308-00001" — shown to user
	private String        currency;
	private LocalDateTime occurredAt;
}

