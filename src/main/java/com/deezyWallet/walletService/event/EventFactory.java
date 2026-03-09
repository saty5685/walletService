package com.deezyWallet.walletService.event;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.deezyWallet.walletService.dto.request.CreditRequest;
import com.deezyWallet.walletService.dto.request.DebitRequest;
import com.deezyWallet.walletService.entity.Wallet;
import com.deezyWallet.walletService.enums.WalletStatusEnum;
import com.deezyWallet.walletService.event.inbound.WalletCommandEvent;
import com.deezyWallet.walletService.event.outbound.WalletCreditedEvent;
import com.deezyWallet.walletService.event.outbound.WalletDebitFailedEvent;
import com.deezyWallet.walletService.event.outbound.WalletDebitedEvent;
import com.deezyWallet.walletService.event.outbound.WalletProvisionedEvent;
import com.deezyWallet.walletService.event.outbound.WalletStatusChangedEvent;

/**
 * EventFactory — centralises outbound event construction.
 *
 * WHY A FACTORY?
 *
 * Without a factory, every call site (WalletService, consumers) duplicates
 * the builder pattern for each event. When a field is added to an event
 * (e.g., adding walletNumber to WalletDebitedEvent), you'd need to find and
 * update every builder call in the codebase.
 *
 * With a factory:
 *   - One place to add/remove event fields
 *   - Consistent eventId generation (always UUID.randomUUID())
 *   - Consistent occurredAt (always LocalDateTime.now() at event creation)
 *   - Consistent eventType string (no typos — defined once)
 *   - Easy to mock in unit tests
 *
 * All methods are stateless — thread-safe singleton.
 *
 * NAMING CONVENTION:
 *   build<EventName>(params...)
 *   e.g., buildDebitedEvent(), buildCreditedEvent(), buildDebitFailedEvent()
 */
@Component
public class EventFactory {

	// ── Success events ────────────────────────────────────────────────────────

	public WalletDebitedEvent buildDebitedEvent(Wallet wallet, DebitRequest req) {
		return WalletDebitedEvent.builder()
				.eventId(UUID.randomUUID().toString())
				.eventType("WALLET_DEBITED")
				.walletId(wallet.getId())
				.userId(wallet.getUserId())
				.amount(req.getAmount())
				.newBalance(wallet.getBalance())       // balance AFTER debit applied
				.currency(wallet.getCurrency())
				.transactionRef(req.getTransactionRef())
				.idempotencyKey(req.getIdempotencyKey())
				.metadata(req.getMetadata())
				.occurredAt(LocalDateTime.now())
				.build();
	}

	public WalletCreditedEvent buildCreditedEvent(Wallet wallet, CreditRequest req) {
		return WalletCreditedEvent.builder()
				.eventId(UUID.randomUUID().toString())
				.eventType("WALLET_CREDITED")
				.walletId(wallet.getId())
				.userId(wallet.getUserId())
				.amount(req.getAmount())
				.newBalance(wallet.getBalance())       // balance AFTER credit applied
				.currency(wallet.getCurrency())
				.transactionRef(req.getTransactionRef())
				.idempotencyKey(req.getIdempotencyKey())
				.metadata(req.getMetadata())
				.occurredAt(LocalDateTime.now())
				.build();
	}

	// ── Failure / compensation events ─────────────────────────────────────────

	public WalletDebitFailedEvent buildDebitFailedEvent(WalletCommandEvent cmd,
			String failureCode,
			String failureMessage) {
		return WalletDebitFailedEvent.builder()
				.eventId(UUID.randomUUID().toString())
				.eventType("WALLET_DEBIT_FAILED")
				.walletId(cmd.getWalletId())
				.transactionRef(cmd.getTransactionRef())
				.idempotencyKey(cmd.getIdempotencyKey())
				.failureCode(failureCode)
				.failureMessage(failureMessage)
				.occurredAt(LocalDateTime.now())
				.build();
	}

	// ── Status change events ──────────────────────────────────────────────────

	public WalletStatusChangedEvent buildStatusChangedEvent(Wallet wallet,
			WalletStatusEnum previousStatus,
			String changedBy,
			String reason) {
		String eventType = resolveStatusEventType(wallet.getStatus());
		return WalletStatusChangedEvent.builder()
				.eventId(UUID.randomUUID().toString())
				.eventType(eventType)
				.walletId(wallet.getId())
				.userId(wallet.getUserId())
				.previousStatus(previousStatus)
				.newStatus(wallet.getStatus())
				.changedBy(changedBy)
				.reason(reason)
				.occurredAt(LocalDateTime.now())
				.build();
	}

	// ── Provisioning event ────────────────────────────────────────────────────

	public WalletProvisionedEvent buildProvisionedEvent(Wallet wallet) {
		return WalletProvisionedEvent.builder()
				.eventId(UUID.randomUUID().toString())
				.eventType("WALLET_PROVISIONED")
				.walletId(wallet.getId())
				.userId(wallet.getUserId())
				.walletNumber(wallet.getWalletNumber())
				.currency(wallet.getCurrency())
				.occurredAt(LocalDateTime.now())
				.build();
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	/**
	 * Derives the eventType string from the new WalletStatus.
	 * Keeps eventType strings consistent — no magic strings at call sites.
	 */
	private String resolveStatusEventType(WalletStatusEnum newStatus) {
		return switch (newStatus) {
			case ACTIVE   -> "WALLET_ACTIVATED";
			case FROZEN   -> "WALLET_FROZEN";
			case CLOSED   -> "WALLET_CLOSED";
			case INACTIVE -> "WALLET_DEACTIVATED";
		};
	}
}
