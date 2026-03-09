package com.deezyWallet.walletService.exception;

import org.springframework.http.HttpStatus;

import com.deezyWallet.walletService.constants.WalletErrorCode;

/**
 * Thrown when a wallet lookup by ID or userId returns no result.
 *
 * HTTP: 404 Not Found
 *
 * Two constructors for the two lookup paths:
 *   byId     — looking up by wallet UUID (internal operations, Kafka commands)
 *   byUserId — looking up by userId (user-facing REST endpoints)
 *
 * The message intentionally does NOT echo back the full wallet UUID in
 * user-facing paths — reduces information leakage to external callers.
 * For internal Kafka consumers, the full ID is fine (internal log context).
 */
public class WalletNotFoundException extends WalletBaseException {

	public WalletNotFoundException(String walletId) {
		super(
				WalletErrorCode.WALLET_NOT_FOUND,
				"Wallet not found: " + walletId,
				HttpStatus.NOT_FOUND
		);
	}

	public static WalletNotFoundException byUserId(String userId) {
		WalletNotFoundException ex = new WalletNotFoundException(
				"No wallet found for user " + userId
		);
		return ex;
	}
}

