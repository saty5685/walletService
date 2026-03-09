package com.deezyWallet.walletService.dto.response;

import com.deezyWallet.walletService.enums.WalletStatusEnum;
import com.deezyWallet.walletService.enums.WalletTypeEnum;
import lombok.Builder;
import lombok.Getter;

/**
 * Minimal status response — used by internal endpoints.
 * Transaction Service calls GET /internal/v1/wallets/{id}/status before
 * initiating any transaction to confirm the wallet is ACTIVE.
 *
 * Deliberately minimal — Transaction Service does NOT need balance or limits.
 * Principle of least privilege: expose only what the caller needs.
 */
@Getter
@Builder
public class WalletStatusResponse {

	private final String       walletId;
	private final String       userId;
	private final WalletStatusEnum status;
	private final WalletTypeEnum   type;
	private final boolean      operational;   // = status == ACTIVE
	private final String       currency;
}
