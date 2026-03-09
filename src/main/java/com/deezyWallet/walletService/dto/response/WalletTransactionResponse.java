package com.deezyWallet.walletService.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.deezyWallet.walletService.enums.TransactionTypeEnum;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

/**
 * Single transaction entry in the wallet history.
 * Returned as Page<WalletTransactionResponse> from
 * GET /api/v1/wallets/me/transactions
 *
 * Design choices:
 *   - balanceAfter included — lets the client show a running balance timeline
 *   - referenceId included — lets user tap through to full transaction details
 *     served by the Transaction Service
 *   - metadata excluded from this response — too verbose for list views
 *     Metadata available via GET /transactions/{referenceId} on Transaction Service
 *   - idempotencyKey NEVER included — internal implementation detail
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletTransactionResponse {

	private final String          id;
	private final TransactionTypeEnum type;           // DEBIT | CREDIT
	private final BigDecimal      amount;
	private final BigDecimal      balanceAfter;   // running balance snapshot
	private final String          currency;
	private final String          description;
	private final String          referenceId;    // link to Transaction Service
	private final LocalDateTime   createdAt;
}

