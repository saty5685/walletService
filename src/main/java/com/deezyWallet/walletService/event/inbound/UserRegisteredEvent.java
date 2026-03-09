package com.deezyWallet.walletService.event.inbound;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inbound event — published by User Service to "user.events" topic.
 * Consumed by UserEventConsumer to trigger wallet provisioning.
 *
 * We only deserialise the fields we care about.
 * User Service publishes more fields (email, phone, etc.) but we
 * deliberately ignore them — Wallet Service has no business storing PII.
 * @JsonIgnoreProperties(ignoreUnknown = true) silently drops the rest.
 *
 * Fields we need:
 *   eventType → route to correct handler (USER_REGISTERED vs USER_VERIFIED etc.)
 *   userId    → provision wallet for this user
 *   eventId   → tracing / dedup logging
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRegisteredEvent {

	/**
	 * Discriminator field — determines which handler runs.
	 * Values consumed by Wallet Service:
	 *   USER_REGISTERED → provisionWallet()
	 *   USER_VERIFIED   → activateWallet()
	 *   USER_SUSPENDED  → freezeWallet()
	 *
	 * All other values are silently ignored (logged at DEBUG level).
	 * This future-proofs the consumer — User Service can publish new
	 * event types without breaking the Wallet Service.
	 */
	private String        eventType;

	/** UUID of the user — becomes wallet.userId */
	private String        userId;

	/** Saga/tracing correlation ID. Logged but not stored. */
	private String        eventId;

	/** When the event occurred in User Service. For audit trail. */
	private LocalDateTime occurredAt;
}