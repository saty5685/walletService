package com.deezyWallet.walletService.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Generic paginated response wrapper.
 *
 * Spring's Page<T> serialises fine but exposes internal Pageable fields
 * that clients don't need (sort direction details, pageable object etc.).
 * This wrapper gives a clean, minimal pagination envelope.
 *
 * Response shape:
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 157,
 *   "totalPages": 8,
 *   "last": false
 * }
 */
@Getter
@Builder
public class PagedResponse<T> {

	private final List<T> content;
	private final int     page;
	private final int     size;
	private final long    totalElements;
	private final int     totalPages;
	private final boolean last;

	/**
	 * Factory — builds from Spring Data Page<T> after mapping content.
	 */
	public static <T> PagedResponse<T> from(
			org.springframework.data.domain.Page<T> page) {
		return PagedResponse.<T>builder()
				.content(page.getContent())
				.page(page.getNumber())
				.size(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.last(page.isLast())
				.build();
	}
}
