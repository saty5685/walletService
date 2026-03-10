package com.deezyWallet.walletService.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deezyWallet.walletService.constants.WalletConstants;
import com.deezyWallet.walletService.dto.request.UpdateLimitsRequest;
import com.deezyWallet.walletService.dto.response.BalanceResponse;
import com.deezyWallet.walletService.dto.response.PagedResponse;
import com.deezyWallet.walletService.dto.response.WalletResponse;
import com.deezyWallet.walletService.dto.response.WalletTransactionResponse;
import com.deezyWallet.walletService.security.UserPrincipal;
import com.deezyWallet.walletService.service.WalletService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * User-facing wallet endpoints.
 * Base path: /api/v1/wallets
 *
 * ALL endpoints use the /me pattern — walletId is NEVER a path variable here.
 * It is always derived from the JWT via @AuthenticationPrincipal.
 *
 * WHY /me and not /{walletId}?
 *   - /{walletId} requires an explicit validateOwnership() check to prevent IDOR
 *   - /me derives walletId from the JWT — the user can only see their own
 *   - Cleaner API contract, one less DB call, zero IDOR risk on this controller
 *
 * SECURITY LAYERS on every endpoint:
 *   1. JwtAuthFilter          — token present and valid (Step 2)
 *   2. SecurityConfig routing  — /api/v1/wallets/** requires USER/MERCHANT role
 *   3. @PreAuthorize           — method-level role confirmation
 *   4. @AuthenticationPrincipal— userId scoped to JWT, cannot access others' wallet
 *
 * CONTROLLER RULES:
 *   - Extract principal from SecurityContext only
 *   - Validate inputs (@Valid, @Min, @Max)
 *   - Delegate ALL business logic to WalletService
 *   - Return ResponseEntity with correct HTTP status
 *   - NO business logic, NO DB calls, NO Kafka calls here
 */
@RestController
@RequestMapping(WalletConstants.API_V1_WALLETS)
@RequiredArgsConstructor
@Validated   // enables @Min/@Max on @RequestParam
@Slf4j
public class WalletController {

	private final WalletService walletService;

	// ── GET /api/v1/wallets/me ────────────────────────────────────────────────

	/**
	 * Full wallet details: balance, limits, status, remainders.
	 *
	 * HTTP 200 → wallet returned
	 * HTTP 404 → wallet not yet provisioned (USER_REGISTERED event pending)
	 * HTTP 401 → missing/invalid token
	 */
	@GetMapping("/me")
	@PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
	public ResponseEntity<WalletResponse> getMyWallet(
			@AuthenticationPrincipal UserPrincipal principal) {

		log.debug("GET /me userId={}", principal.getUserId());
		return ResponseEntity.ok(walletService.getWalletByUserId(principal.getUserId()));
	}

	// ── GET /api/v1/wallets/me/balance ────────────────────────────────────────

	/**
	 * Balance only — lightweight, Redis cache-first (30s TTL).
	 * Prefer this over GET /me when only the balance is needed.
	 * response.source = "CACHE" or "DB" for debugging.
	 *
	 * HTTP 200 → balance returned
	 * HTTP 404 → wallet not found
	 */
	@GetMapping("/me/balance")
	@PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
	public ResponseEntity<BalanceResponse> getMyBalance(
			@AuthenticationPrincipal UserPrincipal principal) {

		log.debug("GET /me/balance userId={}", principal.getUserId());
		return ResponseEntity.ok(walletService.getBalance(principal.getUserId()));
	}

	// ── GET /api/v1/wallets/me/transactions ───────────────────────────────────

	/**
	 * Paginated transaction history, newest first.
	 *
	 * ?page=0&size=20  (defaults)
	 * size is capped at 100 — WalletService enforces MAX_PAGE_SIZE.
	 *
	 * HTTP 200 → paginated list (may be empty content[] if no transactions)
	 * HTTP 400 → invalid page/size params
	 */
	@GetMapping("/me/transactions")
	@PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
	public ResponseEntity<PagedResponse<WalletTransactionResponse>> getMyTransactions(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam(defaultValue = "0")
			@Min(value = 0, message = "Page cannot be negative") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "Size must be at least 1")
			@Max(value = 100, message = "Size cannot exceed 100") int size) {

		log.debug("GET /me/transactions userId={} page={} size={}",
				principal.getUserId(), page, size);
		return ResponseEntity.ok(
				walletService.getTransactionHistory(principal.getUserId(), page, size));
	}

	// ── PUT /api/v1/wallets/me/limits ─────────────────────────────────────────

	/**
	 * Update spend limits. Both fields required.
	 *
	 * Validation: dailyLimit 100–100000, monthlyLimit 1000–500000.
	 * Cross-field rule (daily <= monthly) enforced in WalletService.
	 *
	 * HTTP 200 → limits updated, full WalletResponse returned
	 * HTTP 400 → field validation failed
	 * HTTP 422 → dailyLimit > monthlyLimit
	 */
	@PutMapping("/me/limits")
	@PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
	public ResponseEntity<WalletResponse> updateLimits(
			@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody UpdateLimitsRequest request) {

		log.info("PUT /me/limits userId={} daily={} monthly={}",
				principal.getUserId(), request.getDailyLimit(), request.getMonthlyLimit());
		return ResponseEntity.ok(walletService.updateLimits(principal.getUserId(), request));
	}

	// ── POST /api/v1/wallets/me/freeze ────────────────────────────────────────

	/**
	 * Self-freeze the wallet.
	 * Use case: lost phone, suspected compromise, spending pause.
	 *
	 * After freeze: all debits AND credits rejected.
	 * Unfreeze requires admin action — users CANNOT self-unfreeze.
	 * (Prevents attacker who gains account access from lifting security freeze.)
	 *
	 * HTTP 200 → wallet frozen, updated WalletResponse returned
	 */
	@PostMapping("/me/freeze")
	@PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
	public ResponseEntity<WalletResponse> freezeMyWallet(
			@AuthenticationPrincipal UserPrincipal principal) {

		log.info("POST /me/freeze userId={}", principal.getUserId());
		walletService.freezeWallet(principal.getUserId(), "USER", "USER_SELF_FREEZE");
		return ResponseEntity.ok(walletService.getWalletByUserId(principal.getUserId()));
	}
}

