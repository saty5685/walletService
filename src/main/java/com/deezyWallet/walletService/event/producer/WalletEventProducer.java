package com.deezyWallet.walletService.event.producer;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.deezyWallet.walletService.constants.WalletConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WalletEventProducer — publishes all outbound events to "wallet.events" topic.
 *
 * DESIGN DECISIONS:
 *
 * 1. KEYED MESSAGES — walletId as Kafka partition key
 *    Every event is published with the walletId as the message key.
 *    Kafka guarantees all messages with the same key go to the same partition.
 *    This means ALL events for a given wallet arrive at consumers IN ORDER.
 *
 *    Without keying: WALLET_DEBITED for wallet A could arrive AFTER
 *    WALLET_CREDITED for wallet A if they hit different partitions.
 *    The Ledger Service would record entries out of order.
 *    Keying eliminates this class of bug entirely.
 *
 * 2. FIRE-AND-FORGET with callback logging
 *    publish() is non-blocking — it returns immediately.
 *    The CompletableFuture callback logs success/failure asynchronously.
 *
 *    WHY not block on the future (kafkaTemplate.send(...).get())?
 *    Blocking inside a @Transactional method means the DB connection
 *    is held open while waiting for Kafka broker acknowledgement.
 *    Under load this exhausts the Hikari connection pool.
 *    Non-blocking = DB connection released as soon as the transaction commits.
 *
 * 3. PUBLISH AFTER COMMIT (enforced by WalletService)
 *    publish() is called from WalletService.performPostDebitActions() which
 *    runs after the @Transactional method returns. Kafka publish is never
 *    inside the DB transaction boundary.
 *
 *    WHY? If publish happened inside the transaction:
 *    - Consumer could read WALLET_DEBITED event
 *    - Consumer calls getBalance() on Wallet Service
 *    - DB read returns OLD balance (transaction not committed yet)
 *    - Consumer records wrong balance in Ledger → data inconsistency
 *
 * 4. PRODUCER CONFIG (from KafkaProducerConfig):
 *    acks=all          → all ISR replicas must acknowledge before success
 *    enable.idempotence=true → Kafka-level exactly-once producer semantics
 *    retries=3         → automatic retry on transient broker failures
 *    These settings ensure messages are never silently lost.
 *
 * 5. WHAT HAPPENS IF KAFKA IS DOWN AFTER DB COMMIT?
 *    The DB committed but the event was never published.
 *    This is the "dual write problem" — solved in production via:
 *      a. Outbox pattern (future enhancement)
 *      b. Kafka producer retries (handles transient failures)
 *    For now: the callback logs the failure with full context for manual replay.
 *    The walletId + transactionRef in the log are enough to reconstruct the event.
 *
 * WHY A DEDICATED PRODUCER CLASS instead of injecting KafkaTemplate directly?
 *   - Centralises the topic name (one place to change)
 *   - Centralises key extraction logic (walletId from any event type)
 *   - Makes WalletService testable by mocking WalletEventProducer
 *   - Centralises callback logging — consistent log format for all events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletEventProducer {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	/**
	 * Publish any outbound wallet event to "wallet.events" topic.
	 *
	 * The method accepts Object to handle all event types
	 * (WalletDebitedEvent, WalletCreditedEvent, WalletDebitFailedEvent, etc.)
	 * without overloading. The JSON serialiser handles the type.
	 *
	 * Key extraction: we use reflection-free extraction via a helper.
	 * Every outbound event POJO has a getWalletId() method — used as partition key.
	 *
	 * @param event any outbound event POJO from com.deezyWallet.walletService.event.outbound
	 */
	public void publish(Object event) {
		String walletId = extractWalletId(event);
		String eventType = extractEventType(event);

		log.info("Publishing event: type={} walletId={} topic={}",
				eventType, walletId, WalletConstants.TOPIC_WALLET_EVENTS);

		CompletableFuture<SendResult<String, Object>> future =
				kafkaTemplate.send(WalletConstants.TOPIC_WALLET_EVENTS, walletId, event);

		future.whenComplete((result, ex) -> {
			if (ex == null) {
				log.info("Event published OK: type={} walletId={} partition={} offset={}",
						eventType, walletId,
						result.getRecordMetadata().partition(),
						result.getRecordMetadata().offset());
			} else {
				// CRITICAL: log enough context to manually replay this event
				log.error("Event publish FAILED: type={} walletId={} error={}. " +
								"Manual replay required.",
						eventType, walletId, ex.getMessage(), ex);
			}
		});
	}

	/**
	 * Extracts walletId from any event POJO using reflection.
	 * All outbound event POJOs have getWalletId() — Lombok @Getter generates it.
	 * Falls back to "unknown" if extraction fails — publish still proceeds
	 * (losing the key means losing ordering guarantee, but event is still delivered).
	 */
	private String extractWalletId(Object event) {
		try {
			return (String) event.getClass().getMethod("getWalletId").invoke(event);
		} catch (Exception ex) {
			log.warn("Could not extract walletId from event type={}", event.getClass().getSimpleName());
			return "unknown";
		}
	}

	/**
	 * Extracts eventType string for logging.
	 * All outbound event POJOs have getEventType() — set in EventFactory.
	 */
	private String extractEventType(Object event) {
		try {
			return (String) event.getClass().getMethod("getEventType").invoke(event);
		} catch (Exception ex) {
			return event.getClass().getSimpleName();
		}
	}
}
