package com.deezyWallet.walletService.exception;

import org.springframework.http.HttpStatus;

import com.deezyWallet.walletService.constants.WalletErrorCode;

/**
 * Thrown when a user tries to access a wallet that doesn't belong to them.
 *
 * HTTP: 403 Forbidden
 *
 * This is the DATA-LEVEL access check — the third layer of security:
 *   Layer 1: JwtAuthFilter      — is the token valid?
 *   Layer 2: @PreAuthorize      — does the role allow this endpoint?
 *   Layer 3: WalletService      — does this wallet belong to THIS user?
 *
 * Without Layer 3, a valid USER token could access any wallet by ID
 * (horizontal privilege escalation / IDOR — Insecure Direct Object Reference).
 *
 * Called in WalletService.validateOwnership(walletId, requestingUserId).
 *
 * SECURITY NOTE:
 *   The message deliberately says "Wallet not found" instead of
 *   "Access denied to wallet X" — this prevents enumeration.
 *   An attacker probing random wallet IDs gets the same 403 response
 *   whether the wallet exists (but belongs to someone else) or doesn't exist.
 *   We use 403 (not 404) because the HTTP spec says 403 when auth is the issue.
 */
public class WalletAccessDeniedException extends WalletBaseException {

	public WalletAccessDeniedException(String walletId, String requestingUserId) {
		super(
				WalletErrorCode.ACCESS_DENIED,
				// Deliberately vague — don't confirm the wallet exists to an attacker
				"Access denied",
				HttpStatus.FORBIDDEN
		);
	}
}
