package com.deezyWallet.walletService.enums;

public enum WalletStatusEnum {
	/**
	 * Wallet provisioned but not yet usable.
	 * Set on creation; activated when USER_VERIFIED event arrives from User Service.
	 */
	INACTIVE(1),

	/**
	 * Fully operational. Debits and credits both allowed.
	 */
	ACTIVE(2),

	/**
	 * Temporarily locked — no debits OR credits allowed.
	 * Triggered by: admin action, suspicious activity, user self-freeze.
	 * Reversible via unfreeze.
	 */
	FROZEN(3),

	/**
	 * Permanently closed. Terminal state — cannot be reversed.
	 * All remaining balance must be withdrawn before closure.
	 */
	CLOSED(4);

	private final int id;

	WalletStatusEnum(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public static WalletStatusEnum getEnumById(int id) {
		switch (id) {
			case 1 -> {
				return INACTIVE;
			}
			case 2 -> {
				return ACTIVE;
			}
			case 3 -> {
				return FROZEN;
			}
			case 4 -> {
				return CLOSED;
			}
			default -> {
				return null;
			}
		}
	}

}
