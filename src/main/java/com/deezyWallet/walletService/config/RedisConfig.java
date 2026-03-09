package com.deezyWallet.walletService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration.
 *
 * We use TWO RedisTemplate beans:
 *
 *  1. redisTemplate (String, String) — for balance cache and distributed locks.
 *     Keys and values are plain strings — fast, compact, human-readable in Redis CLI.
 *     Balance stored as "12345.6789" (BigDecimal.toPlainString()).
 *
 *  2. (Future) redisTemplate (String, Object) — if we need to cache full wallet objects.
 *     Not needed for now — keeps things simple.
 *
 * Connection pooling is configured in application.yml via Lettuce pool settings.
 * Lettuce is preferred over Jedis for non-blocking reactive-compatible connections.
 */
@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, String> redisTemplate(
			RedisConnectionFactory connectionFactory) {

		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		// Both keys and values are plain UTF-8 strings — no Java serialization
		StringRedisSerializer stringSerializer = new StringRedisSerializer();
		template.setKeySerializer(stringSerializer);
		template.setValueSerializer(stringSerializer);
		template.setHashKeySerializer(stringSerializer);
		template.setHashValueSerializer(stringSerializer);

		template.afterPropertiesSet();
		return template;
	}
}
