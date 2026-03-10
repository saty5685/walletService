package com.deezyWallet.walletService.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deezyWallet.walletService.constants.WalletConstants;
import com.deezyWallet.walletService.dto.response.BalanceResponse;
import com.deezyWallet.walletService.dto.response.WalletStatusResponse;
import com.deezyWallet.walletService.security.UserPrincipal;
import com.deezyWallet.walletService.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal service-to-service controller.
 * Base path: /internal/v1/wallets
 *
 * SECURITY:
 *   ALL endpoints require ROLE_INTERNAL_SERVICE exclusively.
 *   This role exists ONLY in JWTs issued by other microservices using the
 *   shared secret — never in user-facing JWTs.
 *   SecurityConfig enforces this at route level; @PreAuthorize is depth-2 guard.
 *
 *   These endpoints are NOT routed through the API Gateway.
 *   They are only reachable within the Kubernetes cluster network (service mesh).
 *
 * PRINCIPLE OF LEAST PRIVILEGE:
 *   Each endpoint returns ONLY the fields the calling service needs.
 *   Transaction Service checking status → gets no balance.
 *   Fraud Detection checking balance → gets no transaction history.
 *
 * AUDIT:
 *   @AuthenticationPrincipal on every method captures the calling service's
 *   identity from the JWT "sub" field (e.g., "transaction-service").
 *   Logged for tracing inter-service calls.
 *
 * CONSUMERS:
 *   Transaction Service  → /status before every transaction initiation
 *   Transaction Service  → /balance for pre-flight balance check
 *   Ledger Service       → /status for ledger entry metadata
 *   Fraud Detection Svc  → /balance for risk scoring
 */
@RestController
@RequestMapping(WalletConstants.INTERNAL_V1_WALLETS)
@RequiredArgsConstructor
@Slf4j
public class InternalWalletController {

	private final WalletService walletService;

	// ── GET /internal/v1/wallets/{walletId}/balance ───────────────────────────

	/**
	 * Balance by walletId. Cache-first (30s TTL), DB fallback.
	 * No ownership check — INTERNAL_SERVICE callers are trusted peers.
	 */
	@GetMapping("/{walletId}/balance")
	@PreAuthorize("hasRole('INTERNAL_SERVICE')")
	public ResponseEntity<BalanceResponse> getBalance(
			@PathVariable String walletId,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.debug("INTERNAL GET /{}/balance caller={}", walletId, principal.getUserId());
		return ResponseEntity.ok(walletService.getBalanceInternal(walletId));
	}

	// ── GET /internal/v1/wallets/{walletId}/status ────────────────────────────

	/**
	 * Wallet status by walletId.
	 * Returns: walletId, userId, status, type, operational, currency.
	 * operational == true → safe to proceed with transaction.
	 * Does NOT return balance or limits.
	 */
	@GetMapping("/{walletId}/status")
	@PreAuthorize("hasRole('INTERNAL_SERVICE')")
	public ResponseEntity<WalletStatusResponse> getStatus(
			@PathVariable String walletId,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.debug("INTERNAL GET /{}/status caller={}", walletId, principal.getUserId());
		return ResponseEntity.ok(walletService.getWalletStatus(walletId));
	}

	// ── GET /internal/v1/wallets/user/{userId}/balance ────────────────────────

	/**
	 * Balance by userId — for services holding userId but not walletId.
	 * Resolves userId → walletId internally (Redis cache-first).
	 * Avoids the caller needing a separate lookup step.
	 */
	@GetMapping("/user/{userId}/balance")
	@PreAuthorize("hasRole('INTERNAL_SERVICE')")
	public ResponseEntity<BalanceResponse> getBalanceByUserId(
			@PathVariable String userId,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.debug("INTERNAL GET /user/{}/balance caller={}", userId, principal.getUserId());
		return ResponseEntity.ok(walletService.getBalance(userId));
	}

	// ── GET /internal/v1/wallets/user/{userId}/status ─────────────────────────

	/**
	 * Wallet status by userId — avoids two-step userId → walletId → status.
	 */
	@GetMapping("/user/{userId}/status")
	@PreAuthorize("hasRole('INTERNAL_SERVICE')")
	public ResponseEntity<WalletStatusResponse> getStatusByUserId(
			@PathVariable String userId,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.debug("INTERNAL GET /user/{}/status caller={}", userId, principal.getUserId());
		// Resolve userId → walletId via balance lookup (uses cache), then get status
		BalanceResponse balance = walletService.getBalance(userId);
		return ResponseEntity.ok(walletService.getWalletStatus(balance.getWalletId()));
	}
}
