package com.deezyWallet.walletService.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import com.deezyWallet.walletService.constants.WalletConstants;

/**
 * Kafka Consumer Configuration.
 *
 * Key decisions:
 *
 * 1. MANUAL ACK (AckMode.MANUAL)
 *    We only acknowledge after successful processing.
 *    Failed messages stay unacknowledged → Kafka redelivers them.
 *
 * 2. EXPONENTIAL BACKOFF + DLQ
 *    On failure: retry at 1s → 5s → 30s (3 attempts).
 *    After 3 failures: message published to "wallet.commands.dlq" for
 *    manual inspection + reprocessing. Never lost.
 *
 * 3. CONCURRENCY = 3
 *    3 consumer threads = can process 3 partitions in parallel.
 *    Should match or be a factor of topic partition count.
 *
 * 4. TRUSTED PACKAGES
 *    JsonDeserializer only deserializes from com.deezyWallet.* — prevents
 *    deserialization gadget attacks from untrusted Kafka data.
 */
@Configuration
public class KafkaConsumerConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Bean
	public ConsumerFactory<String, Object> consumerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, WalletConstants.GROUP_WALLET_SERVICE);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // manual ACK
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
				StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
				JsonDeserializer.class);
		props.put(JsonDeserializer.TRUSTED_PACKAGES,
				"com.deezyWallet.*");
		props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
		props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
				"java.util.LinkedHashMap");
		return new DefaultKafkaConsumerFactory<>(props);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, Object>
	kafkaListenerContainerFactory(
			ConsumerFactory<String, Object> consumerFactory,
			KafkaTemplate<String, Object>   kafkaTemplate) {

		ConcurrentKafkaListenerContainerFactory<String, Object> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory);

		// Manual offset commit — only after successful processing
		factory.getContainerProperties()
				.setAckMode(ContainerProperties.AckMode.MANUAL);

		// Concurrency: 3 threads / 3 partitions
		factory.setConcurrency(3);

		// ── Dead Letter Queue error handler ───────────────────────────────────
		// After 3 retries (exponential: 1s, 5s, 30s), route to DLQ topic
		DeadLetterPublishingRecoverer recoverer =
				new DeadLetterPublishingRecoverer(kafkaTemplate,
						(record, ex) -> new org.apache.kafka.common.TopicPartition(
								record.topic() + ".dlq", record.partition()));

		ExponentialBackOff backOff = new ExponentialBackOff(
				WalletConstants.RETRY_BACKOFF_MS_1,  // initial interval: 1s
				2.0                                   // multiplier: 1s → 2s → 4s... capped at 30s
		);
		backOff.setMaxInterval(WalletConstants.RETRY_BACKOFF_MS_3);  // max 30s
		backOff.setMaxElapsedTime(WalletConstants.RETRY_BACKOFF_MS_3 * 3); // stop after ~90s

		DefaultErrorHandler errorHandler =
				new DefaultErrorHandler(recoverer, backOff);

		// Do NOT retry on business logic exceptions — they won't fix themselves
		errorHandler.addNotRetryableExceptions(
				com.deezyWallet.walletService.exception.InsufficientBalanceException.class,
				com.deezyWallet.walletService.exception.WalletFrozenException.class,
				com.deezyWallet.walletService.exception.WalletNotFoundException.class
		);

		factory.setCommonErrorHandler(errorHandler);
		return factory;
	}
}
