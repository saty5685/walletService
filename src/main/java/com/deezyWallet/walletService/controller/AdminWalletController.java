package com.deezyWallet.walletService.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deezyWallet.walletService.constants.WalletConstants;
import com.deezyWallet.walletService.dto.response.PagedResponse;
import com.deezyWallet.walletService.dto.response.WalletResponse;
import com.deezyWallet.walletService.dto.response.WalletTransactionResponse;
import com.deezyWallet.walletService.enums.WalletStatusEnum;
import com.deezyWallet.walletService.security.UserPrincipal;
import com.deezyWallet.walletService.service.WalletService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin operations controller.
 * Base path: /api/v1/admin/wallets
 *
 * SECURITY:
 *   ALL endpoints require ROLE_ADMIN exclusively.
 *   SecurityConfig routes /api/v1/admin/wallets/** → hasRole("ADMIN").
 *   @PreAuthorize is method-level defense-in-depth.
 *
 * KEY DIFFERENCES FROM WalletController:
 *   - Operates on arbitrary walletId (not /me)
 *   - validateOwnership() is NOT called — admins bypass data-level auth
 *   - changedBy = admin's userId from JWT (immutable audit trail)
 *
 * AUDIT LOGGING:
 *   Every mutating admin action logs the admin's userId from their JWT.
 *   "Admin userId=ABC froze walletId=XYZ reason=AML_FLAG"
 *   Admin userId comes from @AuthenticationPrincipal — cannot be spoofed
 *   (it's in the signed JWT, not the request body).
 *
 *   WHY log in controller and not service?
 *   Service doesn't know WHO is calling — it knows WHAT to do.
 *   changedBy is passed to service as a parameter, derived here from JWT.
 *   Keeps service testable without requiring a SecurityContext in tests.
 */
@RestController
@RequestMapping(WalletConstants.ADMIN_V1_WALLETS)
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminWalletController {

	private final WalletService walletService;

	// ── GET /api/v1/admin/wallets ─────────────────────────────────────────────

	/**
	 * Paginated list of all wallets, optionally filtered by status.
	 *
	 * ?status=FROZEN&page=0&size=20
	 *
	 * Used for: admin dashboard, compliance reports, dormancy checks.
	 *
	 * HTTP 200 → paginated list (empty content[] if none match)
	 */
	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PagedResponse<WalletResponse>> listWallets(
			@RequestParam(required = false) WalletStatusEnum status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.info("ADMIN GET /wallets status={} page={} size={} adminId={}",
				status, page, size, principal.getUserId());
		return ResponseEntity.ok(walletService.listWallets(status, page, size));
	}

	// ── GET /api/v1/admin/wallets/{walletId} ──────────────────────────────────

	/**
	 * Full wallet details by walletId — including computed limit remainders.
	 * Used for: admin wallet detail page, customer support lookup.
	 */
	@GetMapping("/{walletId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<WalletResponse> getWallet(
			@PathVariable String walletId,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.info("ADMIN GET /wallets/{} adminId={}", walletId, principal.getUserId());
		return ResponseEntity.ok(walletService.getWalletById(walletId));
	}

	// ── POST /api/v1/admin/wallets/{walletId}/freeze ──────────────────────────

	/**
	 * Admin-freeze a wallet.
	 * Used for: AML flag, suspicious activity, regulatory hold.
	 *
	 * ?reason=AML_FLAG  (optional, defaults to "ADMIN_ACTION")
	 * changedBy = admin's userId from JWT — stored in WalletStatusChangedEvent.
	 *
	 * HTTP 200 → frozen, updated WalletResponse returned
	 * HTTP 404 → wallet not found
	 */
	@PostMapping("/{walletId}/freeze")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<WalletResponse> freezeWallet(
			@PathVariable String walletId,
			@RequestParam(defaultValue = "ADMIN_ACTION") String reason,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.info("ADMIN POST /wallets/{}/freeze adminId={} reason={}",
				walletId, principal.getUserId(), reason);
		walletService.freezeWalletById(walletId, principal.getUserId(), reason);
		return ResponseEntity.ok(walletService.getWalletById(walletId));
	}

	// ── POST /api/v1/admin/wallets/{walletId}/unfreeze ────────────────────────

	/**
	 * Admin-unfreeze a wallet. FROZEN → ACTIVE.
	 * ONLY admins can unfreeze — users cannot self-unfreeze.
	 * (Attacker who gains account access cannot lift a security freeze.)
	 *
	 * HTTP 200 → unfrozen, updated WalletResponse returned
	 */
	@PostMapping("/{walletId}/unfreeze")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<WalletResponse> unfreezeWallet(
			@PathVariable String walletId,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.info("ADMIN POST /wallets/{}/unfreeze adminId={}", walletId, principal.getUserId());
		walletService.unfreezeWallet(walletId, principal.getUserId());
		return ResponseEntity.ok(walletService.getWalletById(walletId));
	}

	// ── GET /api/v1/admin/wallets/{walletId}/transactions ────────────────────

	/**
	 * Admin view of transaction history for any wallet.
	 * Used for: customer support, dispute resolution, compliance audit.
	 */
	@GetMapping("/{walletId}/transactions")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PagedResponse<WalletTransactionResponse>> getTransactions(
			@PathVariable String walletId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal UserPrincipal principal) {

		log.info("ADMIN GET /wallets/{}/transactions adminId={}", walletId, principal.getUserId());
		return ResponseEntity.ok(
				walletService.getTransactionHistoryById(walletId, page, size));
	}
}
