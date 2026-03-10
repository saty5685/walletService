package com.deezyWallet.walletService.event.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.deezyWallet.walletService.constants.WalletConstants;
import com.deezyWallet.walletService.event.inbound.UserRegisteredEvent;
import com.deezyWallet.walletService.exception.WalletBaseException;
import com.deezyWallet.walletService.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes events from "user.events" topic — published by User Service.
 *
 * EVENTS HANDLED:
 *   USER_REGISTERED → provisionWallet()  : create INACTIVE wallet
 *   USER_VERIFIED   → activateWallet()   : INACTIVE → ACTIVE
 *   USER_SUSPENDED  → freezeWallet()     : ACTIVE → FROZEN
 *
 * EVENTS IGNORED (logged at DEBUG):
 *   Any other eventType — forward-compatible with new User Service events.
 *
 * ACK STRATEGY: MANUAL
 *   We only acknowledge (ACK) the offset AFTER successful processing.
 *   If processing throws, we do NOT ACK → Kafka redelivers → retry.
 *   After max retries (KafkaConsumerConfig), routed to DLQ.
 *
 * RETRY BEHAVIOUR (from KafkaConsumerConfig):
 *   WalletBaseException subclasses are NOT retried — business failures.
 *   System exceptions (DB timeout, etc.) ARE retried with exponential backoff.
 *
 * CONCURRENCY:
 *   3 threads (setConcurrency(3) in KafkaConsumerConfig).
 *   Each thread handles one partition independently.
 *   WalletService.provisionWallet() is idempotent — safe under concurrent redelivery.
 *
 * WHY ConsumerRecord<String, Object> instead of UserRegisteredEvent directly?
 *   Gives access to Kafka metadata (partition, offset, key) for logging.
 *   The key = userId (set by User Service) — used for tracing.
 *   Jackson deserialises the value to UserRegisteredEvent via the container factory.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

	private final WalletService walletService;

	@KafkaListener(
			topics            = WalletConstants.TOPIC_USER_EVENTS,
			groupId           = WalletConstants.GROUP_WALLET_SERVICE,
			containerFactory  = "kafkaListenerContainerFactory"
	)
	public void consume(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		log.info("UserEventConsumer received: topic={} partition={} offset={} key={}",
				record.topic(), record.partition(), record.offset(), record.key());

		try {
			// Deserialise value to UserRegisteredEvent
			UserRegisteredEvent event = deserialise(record.value());
			if (event == null || event.getEventType() == null) {
				log.warn("Null or malformed UserRegisteredEvent at offset={} — ACKing to skip",
						record.offset());
				ack.acknowledge(); // skip malformed messages — don't block the partition
				return;
			}

			log.info("Processing user event: type={} userId={} eventId={}",
					event.getEventType(), event.getUserId(), event.getEventId());

			// Route by eventType
			switch (event.getEventType()) {
				case "USER_REGISTERED" -> {
					walletService.provisionWallet(event.getUserId());
					log.info("Wallet provisioned for userId={}", event.getUserId());
				}
				case "USER_VERIFIED" -> {
					walletService.activateWallet(event.getUserId());
					log.info("Wallet activated for userId={}", event.getUserId());
				}
				case "USER_SUSPENDED" -> {
					walletService.freezeWallet(event.getUserId(), "SYSTEM", "USER_SUSPENDED");
					log.info("Wallet frozen for userId={}", event.getUserId());
				}
				default -> {
					// Unknown event type — ignore and ACK (forward compatible)
					log.debug("Ignoring unknown user event type={} userId={}",
							event.getEventType(), event.getUserId());
				}
			}

			// SUCCESS — commit offset
			ack.acknowledge();

		} catch (WalletBaseException ex) {
			// Business exception — not retryable. Log as WARN, ACK to move forward.
			// Examples: wallet already exists (idempotent), userId missing.
			// These won't fix themselves on retry — route to DLQ via error handler.
			log.warn("Business exception in UserEventConsumer offset={} error={}: {}",
					record.offset(), ex.getErrorCode(), ex.getMessage());
			// DO NOT ACK — let KafkaConsumerConfig's error handler route to DLQ
			throw ex;

		} catch (Exception ex) {
			// System exception — retryable (DB timeout, network issue).
			// DO NOT ACK — KafkaConsumerConfig retries with exponential backoff.
			log.error("System exception in UserEventConsumer offset={}: {}",
					record.offset(), ex.getMessage(), ex);
			throw ex;
		}
	}

	/**
	 * Safe deserialisation — Kafka delivers value as Object (from JsonDeserializer).
	 * Handles both pre-deserialised UserRegisteredEvent and raw LinkedHashMap
	 * (when type info headers are disabled).
	 */
	@SuppressWarnings("unchecked")
	private UserRegisteredEvent deserialise(Object value) {
		if (value instanceof UserRegisteredEvent event) {
			return event;
		}
		// Jackson deserialized to LinkedHashMap when USE_TYPE_INFO_HEADERS=false
		// Convert manually if needed — typically handled by JsonDeserializer config
		if (value instanceof java.util.Map) {
			java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
			return UserRegisteredEvent.builder()
					.eventType((String) map.get("eventType"))
					.userId((String) map.get("userId"))
					.eventId((String) map.get("eventId"))
					.build();
		}
		log.warn("Unexpected value type in UserEventConsumer: {}", value.getClass().getName());
		return null;
	}
}
