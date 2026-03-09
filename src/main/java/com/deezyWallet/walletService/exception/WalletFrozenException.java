package com.deezyWallet.walletService.exception;

import org.springframework.http.HttpStatus;

import com.deezyWallet.walletService.constants.WalletErrorCode;
import com.deezyWallet.walletService.enums.WalletStatusEnum;

import lombok.Getter;

/**
 * Thrown when a debit or credit is attempted on a non-ACTIVE wallet.
 *
 * HTTP: 403 Forbidden
 *   - 403 = "authenticated, but not allowed to perform this action"
 *   - The wallet exists, the user owns it, but the state prevents the operation.
 *
 * Covers both FROZEN and CLOSED states — and also INACTIVE (KYC not done yet).
 * The actualStatus field lets the client show a specific message:
 *   INACTIVE → "Complete KYC to activate your wallet"
 *   FROZEN   → "Your wallet is frozen. Contact support."
 *   CLOSED   → "This wallet has been permanently closed."
 *
 * NOT retried by Kafka consumer — status won't change between retries.
 */
@Getter
public class WalletFrozenException extends WalletBaseException {

	private final String       walletId;
	private final WalletStatusEnum actualStatus;

	public WalletFrozenException(String walletId, WalletStatusEnum actualStatus) {
		super(
				WalletErrorCode.WALLET_FROZEN,
				String.format("Wallet %s is not operational. Current status: %s",
						walletId, actualStatus),
				HttpStatus.FORBIDDEN
		);
		this.walletId     = walletId;
		this.actualStatus = actualStatus;
	}

	/** Convenience constructor when status is known to be FROZEN */
	public WalletFrozenException(String walletId) {
		this(walletId, WalletStatusEnum.FROZEN);
	}
}
