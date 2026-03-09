package com.deezyWallet.walletService.enums;

public enum TransactionTypeEnum {
	DEBIT(1),
	CREDIT(2);

	private final int id;

	TransactionTypeEnum(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public static TransactionTypeEnum getEnumById(int id) {
		switch (id) {
			case 1 -> {
				return DEBIT;
			}
			case 2 -> {
				return CREDIT;
			}
			default -> {
				return null;
			}
		}
	}
}
