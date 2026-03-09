package com.deezyWallet.walletService.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.deezyWallet.walletService.dto.response.BalanceResponse;
import com.deezyWallet.walletService.dto.response.PagedResponse;
import com.deezyWallet.walletService.dto.response.WalletResponse;
import com.deezyWallet.walletService.dto.response.WalletStatusResponse;
import com.deezyWallet.walletService.dto.response.WalletTransactionResponse;
import com.deezyWallet.walletService.entity.Wallet;
import com.deezyWallet.walletService.entity.WalletTransaction;

/**
 * WalletMapper — converts JPA entities to response DTOs.
 *
 * WHY A HAND-WRITTEN MAPPER OVER MAPSTRUCT?
 *
 * MapStruct is excellent for simple field-to-field mappings. We chose a
 * hand-written mapper here for three reasons:
 *
 * 1. COMPUTED FIELDS
 *    WalletResponse includes dailyLimitRemaining and monthlyLimitRemaining.
 *    These are NOT on the Wallet entity — they require external data
 *    (today's spent amount from WalletLimitRepository) passed as parameters.
 *    MapStruct can't handle this without custom @AfterMapping methods that
 *    end up more complex than just writing the mapper manually.
 *
 * 2. CONDITIONAL LOGIC
 *    toStatusResponse() sets operational = (status == ACTIVE).
 *    toTransactionResponse() formats amounts consistently.
 *    These small decisions are clearer in plain Java than in MapStruct annotations.
 *
 * 3. TESTABILITY
 *    A plain Spring @Component is trivially unit-testable with no framework magic.
 *    You pass in entities, assert on DTOs. Done.
 *
 * WHAT MAPSTRUCT IS STILL GOOD FOR:
 *    If a future entity has 30+ fields with 1:1 field names, add MapStruct then.
 *    Don't over-engineer upfront.
 *
 * THREADING:
 *    All methods are stateless — this bean is thread-safe as a singleton.
 *    No instance variables, no shared state.
 *
 * NULL SAFETY:
 *    All public methods perform null checks on the entity parameter.
 *    Computed parameters (dailySpent, monthlySpent) default to ZERO if null —
 *    prevents NullPointerException if the WalletLimit row doesn't exist yet
 *    (first transaction of the day / month).
 */
@Component
public class WalletMapper {

	// ── Wallet → WalletResponse ───────────────────────────────────────────────

	/**
	 * Full wallet response — for GET /api/v1/wallets/me
	 *
	 * Includes computed remaining limits:
	 *   dailyLimitRemaining  = wallet.dailyLimit  - dailySpent
	 *   monthlyLimitRemaining= wallet.monthlyLimit - monthlySpent
	 *
	 * Both remainders are floored at ZERO — never return a negative remaining
	 * (edge case: limit was lowered after spending exceeded the new limit).
	 *
	 * @param wallet        the Wallet entity from DB
	 * @param dailySpent    today's cumulative debit from WalletLimitRepository
	 * @param monthlySpent  this month's cumulative debit from WalletLimitRepository
	 */
	public WalletResponse toResponse(Wallet wallet,
			BigDecimal dailySpent,
			BigDecimal monthlySpent) {
		if (wallet == null) return null;

		// Null-safe: if no limit row exists yet, spent = 0
		BigDecimal safeDaily   = dailySpent   != null ? dailySpent   : BigDecimal.ZERO;
		BigDecimal safeMonthly = monthlySpent != null ? monthlySpent : BigDecimal.ZERO;

		// Floor at zero — remaining cannot be negative
		BigDecimal dailyRemaining = wallet.getDailyLimit()
				.subtract(safeDaily)
				.max(BigDecimal.ZERO);

		BigDecimal monthlyRemaining = wallet.getMonthlyLimit()
				.subtract(safeMonthly)
				.max(BigDecimal.ZERO);

		return WalletResponse.builder()
				.id(wallet.getId())
				.walletNumber(wallet.getWalletNumber())
				.balance(wallet.getBalance())
				.blockedBalance(wallet.getBlockedBalance())
				.currency(wallet.getCurrency())
				.status(wallet.getStatus())
				.type(wallet.getType())
				.dailyLimit(wallet.getDailyLimit())
				.monthlyLimit(wallet.getMonthlyLimit())
				.dailyLimitRemaining(dailyRemaining)
				.monthlyLimitRemaining(monthlyRemaining)
				.createdAt(wallet.getCreatedAt())
				.updatedAt(wallet.getUpdatedAt())
				.build();
	}

	/**
	 * Overloaded — for cases where limit data is not needed
	 * (e.g., internal service responses, admin list views).
	 * dailyLimitRemaining / monthlyLimitRemaining will be null in output
	 * and omitted from JSON due to @JsonInclude(NON_NULL).
	 */
	public WalletResponse toResponse(Wallet wallet) {
		return toResponse(wallet, null, null);
	}

	// ── Wallet → WalletStatusResponse ────────────────────────────────────────

	/**
	 * Minimal status projection — for GET /internal/v1/wallets/{id}/status
	 * Called by Transaction Service before initiating any transaction.
	 *
	 * Contains NO financial data — Transaction Service has no business
	 * knowing balances or limits. Principle of least privilege.
	 *
	 * operational flag is a convenience computed field:
	 *   true  = wallet.status == ACTIVE  → transaction can proceed
	 *   false = any other status          → transaction must be rejected
	 *
	 * Callers should check operational == true, not status == "ACTIVE".
	 * This decouples them from the WalletStatus enum string.
	 */
	public WalletStatusResponse toStatusResponse(Wallet wallet) {
		if (wallet == null) return null;

		return WalletStatusResponse.builder()
				.walletId(wallet.getId())
				.userId(wallet.getUserId())
				.status(wallet.getStatus())
				.type(wallet.getType())
				.operational(wallet.isOperational()) // delegates to Wallet.isOperational()
				.currency(wallet.getCurrency())
				.build();
	}

	// ── Wallet → BalanceResponse ──────────────────────────────────────────────

	/**
	 * Balance-only response — for GET /api/v1/wallets/me/balance (cache miss path)
	 *
	 * Called when Redis has no cached balance and we loaded the full entity.
	 * Extracts only balance + blockedBalance — avoids re-querying DB for a
	 * balance-only endpoint that already has the entity in scope.
	 *
	 * @param wallet  loaded Wallet entity (from DB, on cache miss)
	 * @param source  "DB" — indicates this came from DB, not cache
	 */
	public BalanceResponse toBalanceResponse(Wallet wallet, String source) {
		if (wallet == null) return null;

		return BalanceResponse.of(
				wallet.getId(),
				wallet.getBalance(),
				wallet.getBlockedBalance(),
				source
		);
	}

	// ── WalletTransaction → WalletTransactionResponse ────────────────────────

	/**
	 * Single transaction entry — for paginated history list.
	 *
	 * What is deliberately excluded:
	 *   - idempotencyKey  : internal dedup mechanism, not for clients
	 *   - metadata        : too verbose for list view; available on detail endpoint
	 *   - walletId        : caller already knows their own wallet
	 *
	 * balanceAfter IS included — lets the UI render a running balance column:
	 *   "Transfer out  -₹500.00   Balance: ₹4,500.00"
	 *   "Top-up        +₹1000.00  Balance: ₹5,000.00"
	 */
	public WalletTransactionResponse toTransactionResponse(
			WalletTransaction transaction) {
		if (transaction == null) return null;

		return WalletTransactionResponse.builder()
				.id(transaction.getId())
				.type(transaction.getType())
				.amount(transaction.getAmount())
				.balanceAfter(transaction.getBalanceAfter())
				.currency(transaction.getCurrency())
				.description(transaction.getDescription())
				.referenceId(transaction.getReferenceId())
				.createdAt(transaction.getCreatedAt())
				.build();
	}

	/**
	 * Maps a full Page<WalletTransaction> to PagedResponse<WalletTransactionResponse>.
	 *
	 * Two-step:
	 *   1. Map each entity to DTO via toTransactionResponse()
	 *   2. Wrap the List<DTO> in PagedResponse with pagination metadata
	 *
	 * Why not use page.map() directly?
	 *   page.map(this::toTransactionResponse) returns Page<WalletTransactionResponse>
	 *   which Spring serialises with unwanted internal pageable fields.
	 *   PagedResponse gives us a clean, controlled JSON envelope.
	 */
	public PagedResponse<WalletTransactionResponse> toTransactionPagedResponse(
			Page<WalletTransaction> page) {
		if (page == null) return null;

		List<WalletTransactionResponse> content = page.getContent()
				.stream()
				.map(this::toTransactionResponse)
				.collect(Collectors.toList());

		return PagedResponse.<WalletTransactionResponse>builder()
				.content(content)
				.page(page.getNumber())
				.size(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.last(page.isLast())
				.build();
	}
}

