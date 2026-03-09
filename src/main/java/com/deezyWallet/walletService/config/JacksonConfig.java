package com.deezyWallet.walletService.config;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jackson ObjectMapper configuration.
 *
 * Two critical settings for a financial service:
 *
 * 1. BigDecimal serialisation as plain string
 *    JavaScript's Number type is IEEE 754 double — it cannot represent
 *    ₹12345.6789 exactly. 12345.6789 in JS becomes 12345.678900000001.
 *    Solution: serialise BigDecimal as a JSON string "12345.6789" instead
 *    of a JSON number. The client parses it back with a decimal library.
 *    This is the industry standard for financial APIs (Stripe, Razorpay use this).
 *
 *    Custom serialiser also:
 *    - Enforces scale=4 consistently (₹100 becomes "100.0000")
 *    - Uses HALF_EVEN rounding (banker's rounding — standard for finance)
 *    - Never outputs scientific notation (toPlainString vs toString)
 *
 * 2. LocalDateTime serialisation as ISO-8601 string
 *    Jackson's default writes LocalDateTime as an array [2024,3,8,14,23,11].
 *    That's ugly. ISO-8601 "2024-03-08T14:23:11.456" is what every client expects.
 *    WRITE_DATES_AS_TIMESTAMPS = false gives us the ISO string.
 *
 * 3. FAIL_ON_UNKNOWN_PROPERTIES = false
 *    Kafka events may add new fields in future. Consumers must not break.
 *    This setting makes deserialization tolerant of unknown fields.
 *
 * 4. FAIL_ON_EMPTY_BEANS = false
 *    Prevents exceptions when serialising objects with no visible properties.
 */
@Configuration
public class JacksonConfig {

	@Bean
	@Primary
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();

		// ── LocalDateTime as ISO-8601 string ──────────────────────────────────
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		// ── Tolerant deserialization ───────────────────────────────────────────
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

		// ── BigDecimal as plain string with scale=4 ────────────────────────────
		SimpleModule financialModule = new SimpleModule("FinancialModule");
		financialModule.addSerializer(BigDecimal.class, new BigDecimalSerializer());
		mapper.registerModule(financialModule);

		return mapper;
	}

	/**
	 * Custom BigDecimal serialiser — writes as JSON string with exactly 4
	 * decimal places and HALF_EVEN rounding.
	 *
	 * Input  → Output
	 * 100    → "100.0000"
	 * 1234.5 → "1234.5000"
	 * 0.001  → "0.0010"
	 * 1.23456789 → "1.2346"  (rounded HALF_EVEN)
	 *
	 * The value is a JSON STRING (quoted), not a JSON NUMBER.
	 * Clients must parse it with a decimal-aware library.
	 */
	private static class BigDecimalSerializer
			extends JsonSerializer<BigDecimal> {

		@Override
		public void serialize(BigDecimal value,
				JsonGenerator gen,
				SerializerProvider provider)
				throws IOException {
			if (value == null) {
				gen.writeNull();
				return;
			}
			// setScale with HALF_EVEN — banker's rounding
			// toPlainString — never scientific notation (no "1.23E+5")
			gen.writeString(
					value.setScale(4, RoundingMode.HALF_EVEN).toPlainString()
			);
		}
	}
}
