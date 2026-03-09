package com.deezyWallet.walletService.enums;

/**
 * The category of a wallet, which controls business rules applied to it.
 *
 * PERSONAL  — Standard user wallet. Subject to RBI limits (₹2L/month).
 * MERCHANT  — Business wallet. Higher limits; MDR fees apply on incoming payments.
 * ESCROW    — System-controlled holding wallet for in-flight transactions.
 *             Funds sit here between DEBIT and CREDIT in a Saga.
 *             No user can directly access an ESCROW wallet.
 */

public enum WalletTypeEnum {
    PERSONAL(1),
    MERCHANT(2),
    ESCROW(3);

    private final int id;

    WalletTypeEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static WalletTypeEnum getEnumById(int id) {
        switch (id) {
            case 1 -> {
                return PERSONAL;
            }
            case 2 -> {
                return MERCHANT;
            }
            case 3 -> {
                return ESCROW;
            }
            default -> {
                return null;
            }
        }
    }
}
