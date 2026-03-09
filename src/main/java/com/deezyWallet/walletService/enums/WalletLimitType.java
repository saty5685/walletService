package com.deezyWallet.walletService.enums;

/**
 * Spend limit windows. Used in WalletLimitExceededException to tell the caller
 * which limit was breached without exposing internal logic.
 */

public enum WalletLimitType {
    DAILY(1),
    MONTHLY(2);

    private final int id;

    WalletLimitType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static WalletLimitType getEnumById(int id) {
        switch (id) {
            case 1 -> {
                return DAILY;
            }
            case 2 -> {
                return MONTHLY;
            }
            default -> {
                return null;
            }
        }
    }
}
