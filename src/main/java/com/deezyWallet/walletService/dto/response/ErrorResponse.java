package com.deezyWallet.walletService.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

/**
 * Standardised error response envelope — returned for ALL error conditions.
 *
 * Every error in the system MUST use this shape. Clients depend on this
 * contract for programmatic error handling.
 *
 * Response shape (single error):
 * {
 *   "code": "WALLET_INSUFFICIENT_BALANCE",
 *   "message": "Available: ₹234.50, Required: ₹500.00",
 *   "timestamp": "2024-03-08T14:23:11.456",
 *   "path": "/api/v1/wallets/me/balance"
 * }
 *
 * Response shape (validation error — multiple field errors):
 * {
 *   "code": "WALLET_VALIDATION_FAILED",
 *   "message": "Request validation failed",
 *   "errors": [
 *     "dailyLimit: Daily limit must be at least ₹100",
 *     "monthlyLimit: Monthly limit cannot exceed ₹5,00,000"
 *   ],
 *   "timestamp": "2024-03-08T14:23:11.456",
 *   "path": "/api/v1/wallets/me/limits"
 * }
 *
 * Code field:
 *   Always matches a constant in WalletErrorCode.
 *   Clients switch on code — NOT on message (messages can change).
 *   NOT the HTTP status code — that's in the response header.
 *
 * @JsonInclude(NON_NULL) — errors list only appears for validation failures.
 * path only appears when populated by GlobalExceptionHandler.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

	/** Machine-readable error code from WalletErrorCode constants. */
	private final String            code;

	/** Human-readable description. For validation errors, summarises the issue. */
	private final String            message;

	/**
	 * Field-level validation errors — only present for 400 Bad Request responses.
	 * Each entry: "<fieldName>: <constraint message>"
	 */
	private final List<String>      errors;

	/** When the error occurred. Useful for log correlation. */
	@Builder.Default
	private final LocalDateTime     timestamp = LocalDateTime.now();

	/** The request path — populated by GlobalExceptionHandler from HttpServletRequest. */
	private final String            path;

	// ── Factory methods — for clean construction at call sites ────────────────

	/**
	 * Simple single-message error.
	 * Used for: business exceptions (insufficient balance, wallet frozen, etc.)
	 */
	public static ErrorResponse of(String code, String message) {
		return ErrorResponse.builder()
				.code(code)
				.message(message)
				.build();
	}

	/**
	 * Error with request path — richer context for API consumers.
	 */
	public static ErrorResponse of(String code, String message, String path) {
		return ErrorResponse.builder()
				.code(code)
				.message(message)
				.path(path)
				.build();
	}

	/**
	 * Validation error with multiple field errors.
	 * Used by GlobalExceptionHandler for MethodArgumentNotValidException.
	 */
	public static ErrorResponse ofValidation(String message,
			List<String> errors,
			String path) {
		return ErrorResponse.builder()
				.code(com.deezyWallet.walletService.constants.WalletErrorCode.VALIDATION_FAILED)
				.message(message)
				.errors(errors)
				.path(path)
				.build();
	}
}
