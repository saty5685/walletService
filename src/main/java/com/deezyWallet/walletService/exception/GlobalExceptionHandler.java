package com.deezyWallet.walletService.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.deezyWallet.walletService.constants.WalletErrorCode;
import com.deezyWallet.walletService.dto.response.ErrorResponse;
import com.deezyWallet.walletService.dto.response.WalletTransactionResponse;
import com.deezyWallet.walletService.mapper.WalletMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralised exception handler — intercepts all exceptions thrown from
 * controllers and service layer, converts them to clean ErrorResponse JSON.
 *
 * HANDLER PRIORITY (Spring processes in this order):
 *   1. Most specific exception type first
 *   2. DuplicateOperationException — returns 200 OK (special case)
 *   3. All WalletBaseException subclasses — returns their configured httpStatus
 *   4. Spring/framework exceptions (validation, type mismatch, optimistic lock)
 *   5. Catch-all Exception — returns 500
 *
 * LOGGING STRATEGY:
 *   Business exceptions (4xx): log.warn — expected, no stack trace needed
 *   System exceptions (5xx):   log.error with stack trace — unexpected, needs investigation
 *   DuplicateOperation (200):  log.debug — high frequency in normal operation, noisy at INFO
 *
 * SECURITY — never leak internal details in error responses:
 *   - No stack traces in response body
 *   - No internal class names
 *   - No SQL error details (DataIntegrityViolationException)
 *   - No internal IDs in access denied messages
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

	private final WalletMapper walletMapper;

	// ── Idempotency: duplicate operation returns 200 OK ───────────────────────

	/**
	 * SPECIAL CASE: returns 200 OK, not an error response.
	 *
	 * If the original transaction is available, returns the same
	 * WalletTransactionResponse as the first successful call.
	 * If not (Redis-layer dedup before DB write), returns a minimal
	 * acknowledgement with the idempotency key.
	 */
	@ExceptionHandler(DuplicateOperationException.class)
	public ResponseEntity<?> handleDuplicate(DuplicateOperationException ex,
			HttpServletRequest request) {
		log.debug("Duplicate operation detected for key: {}", ex.getIdempotencyKey());

		if (ex.getExistingTransaction() != null) {
			// Return original transaction response — same as first successful call
			WalletTransactionResponse original =
					walletMapper.toTransactionResponse(ex.getExistingTransaction());
			return ResponseEntity.ok(original);
		}

		// No transaction record yet — return acknowledgement
		return ResponseEntity.ok(
				ErrorResponse.of(
						WalletErrorCode.ALREADY_PROCESSED,
						"Operation already processed: " + ex.getIdempotencyKey(),
						request.getRequestURI()
				)
		);
	}

	// ── All business exceptions (WalletBaseException subclasses) ─────────────

	/**
	 * Handles ALL subclasses of WalletBaseException in one method:
	 *   InsufficientBalanceException  → 422
	 *   WalletNotFoundException       → 404
	 *   WalletFrozenException         → 403
	 *   WalletLimitExceededException  → 422
	 *   WalletAccessDeniedException   → 403
	 *
	 * Each exception carries its own httpStatus and errorCode — handler
	 * just reads them. No switch/instanceof logic needed here.
	 */
	@ExceptionHandler(WalletBaseException.class)
	public ResponseEntity<ErrorResponse> handleWalletException(
			WalletBaseException ex,
			HttpServletRequest request) {

		log.warn("Business exception [{}]: {} | path={}",
				ex.getErrorCode(), ex.getMessage(), request.getRequestURI());

		return ResponseEntity
				.status(ex.getHttpStatus())
				.body(ErrorResponse.of(
						ex.getErrorCode(),
						ex.getMessage(),
						request.getRequestURI()
				));
	}

	// ── Spring Validation: @Valid on request body ─────────────────────────────

	/**
	 * Handles @Valid failures on @RequestBody.
	 * Collects ALL field errors in one response — not just the first one.
	 *
	 * Response format:
	 * {
	 *   "code": "WALLET_VALIDATION_FAILED",
	 *   "message": "Request validation failed",
	 *   "errors": [
	 *     "dailyLimit: Daily limit must be at least ₹100",
	 *     "monthlyLimit: Monthly limit cannot exceed ₹5,00,000"
	 *   ]
	 * }
	 *
	 * Collecting ALL errors at once = better UX. User fixes everything in one
	 * round trip rather than getting one error at a time.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(
			MethodArgumentNotValidException ex,
			HttpServletRequest request) {

		List<String> errors = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.toList());

		log.warn("Validation failed at {}: {}", request.getRequestURI(), errors);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.ofValidation(
						"Request validation failed",
						errors,
						request.getRequestURI()
				));
	}

	// ── Type mismatch: wrong type in path variable or request param ───────────

	/**
	 * e.g., GET /api/v1/wallets/not-a-valid-uuid
	 * Path variable can't be bound to expected type.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {

		log.warn("Type mismatch for parameter '{}' at {}", ex.getName(), request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of(
						WalletErrorCode.VALIDATION_FAILED,
						"Invalid value for parameter: " + ex.getName(),
						request.getRequestURI()
				));
	}

	// ── Optimistic lock failure: concurrent credit conflict ───────────────────

	/**
	 * Thrown by Hibernate when two concurrent transactions update the same
	 * wallet row and the @Version field has changed since the entity was read.
	 *
	 * HTTP: 409 Conflict
	 *   The request was valid but conflicted with current state.
	 *   Client should retry.
	 *
	 * In normal operation this is rare but possible under high concurrency
	 * (many concurrent top-ups to the same wallet — merchant receiving many payments).
	 * WalletService wraps credits with @Retryable — this handler is the
	 * last resort when retries are exhausted.
	 */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleOptimisticLock(
			ObjectOptimisticLockingFailureException ex,
			HttpServletRequest request) {

		log.warn("Optimistic lock conflict at {}: {}", request.getRequestURI(), ex.getMessage());

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of(
						WalletErrorCode.INTERNAL_ERROR,
						"Transaction conflict — please retry",
						request.getRequestURI()
				));
	}

	// ── Data integrity violation: DB constraint breach ────────────────────────

	/**
	 * Thrown when a DB constraint is violated — most commonly the
	 * UNIQUE constraint on wallet_transactions.idempotency_key.
	 *
	 * This should be caught earlier by existsByIdempotencyKey() check in
	 * WalletService. If it reaches here, it means two threads raced past
	 * the application-level check simultaneously — the DB constraint saved us.
	 *
	 * Never expose the original SQL error message — it leaks table/column names.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrity(
			DataIntegrityViolationException ex,
			HttpServletRequest request) {

		// Check if it's an idempotency key collision specifically
		String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
		if (msg.contains("idempotency_key")) {
			log.warn("Idempotency key collision (DB safety net) at {}", request.getRequestURI());
			return ResponseEntity
					.status(HttpStatus.OK) // treat as duplicate — 200 OK
					.body(ErrorResponse.of(
							WalletErrorCode.ALREADY_PROCESSED,
							"Operation already processed",
							request.getRequestURI()
					));
		}

		log.error("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());
		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of(
						WalletErrorCode.INTERNAL_ERROR,
						"Data conflict — please retry",
						request.getRequestURI()
				));
	}

	// ── Spring Security exceptions ────────────────────────────────────────────

	/**
	 * Handles Spring Security's AccessDeniedException (insufficient role).
	 * This fires AFTER CustomAccessDeniedHandler in some edge cases —
	 * having both is defense-in-depth.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(
			AccessDeniedException ex,
			HttpServletRequest request) {

		log.warn("Access denied at {}", request.getRequestURI());
		return ResponseEntity
				.status(HttpStatus.FORBIDDEN)
				.body(ErrorResponse.of(
						WalletErrorCode.ACCESS_DENIED,
						"You do not have permission to access this resource",
						request.getRequestURI()
				));
	}

	/**
	 * Handles Spring Security's AuthenticationException (not authenticated).
	 * Defense-in-depth alongside CustomAuthEntryPoint.
	 */
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> handleAuthentication(
			AuthenticationException ex,
			HttpServletRequest request) {

		log.warn("Authentication required at {}", request.getRequestURI());
		return ResponseEntity
				.status(HttpStatus.UNAUTHORIZED)
				.body(ErrorResponse.of(
						WalletErrorCode.UNAUTHORIZED,
						"Authentication required",
						request.getRequestURI()
				));
	}

	// ── Catch-all: unexpected system errors ───────────────────────────────────

	/**
	 * Last resort — catches any exception not handled above.
	 *
	 * HTTP: 500 Internal Server Error
	 *
	 * LOG: log.error WITH stack trace — this is unexpected and needs investigation.
	 *
	 * RESPONSE: deliberately vague — "An unexpected error occurred"
	 *   Never leak stack traces, class names, or internal messages to clients.
	 *   All details are in the server logs with a correlation ID.
	 *
	 * In production: correlate with a requestId header or MDC trace ID
	 * so the client can quote it to support.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneral(
			Exception ex,
			HttpServletRequest request) {

		log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of(
						WalletErrorCode.INTERNAL_ERROR,
						"An unexpected error occurred. Please try again later.",
						request.getRequestURI()
				));
	}
}
