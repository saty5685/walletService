package com.deezyWallet.walletService.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka test configuration — used by Kafka consumer integration tests.
 *
 * Provides a KafkaTemplate that can publish test messages to EmbeddedKafka.
 * The embedded broker URL is injected via @Value from the
 * spring.kafka.bootstrap-servers property which @EmbeddedKafka sets
 * automatically on the test application context.
 *
 * WHY a separate test KafkaTemplate?
 *   The production KafkaTemplate uses JsonSerializer with production config.
 *   For tests we want String keys and Object values with type info disabled —
 *   same settings as production but pointed at the embedded broker.
 *   Sharing the production bean would require the full KafkaProducerConfig
 *   to be loaded, which pulls in properties that aren't set in test YAML.
 */
@TestConfiguration
public class TestKafkaConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Bean("testKafkaTemplate")
	public KafkaTemplate<String, Object> testKafkaTemplate() {
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
		ProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(props);
		return new KafkaTemplate<>(factory);
	}
}
