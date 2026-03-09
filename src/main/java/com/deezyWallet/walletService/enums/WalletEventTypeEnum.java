package com.deezyWallet.walletService.enums;

/**
 * Event types published by the Wallet Service to the "wallet.events" Kafka topic.
 * Other services (Transaction, Ledger, Notification) consume these.
 */

public enum WalletEventTypeEnum {
    WALLET_PROVISIONED(1),    // New wallet created — consumed by Notification
    WALLET_ACTIVATED(2),      // INACTIVE → ACTIVE — consumed by Notification
    WALLET_DEBITED(3),        // Debit successful — consumed by Txn Svc, Ledger, Notif
    WALLET_CREDITED(4),       // Credit successful — consumed by Txn Svc, Ledger, Notif
    WALLET_DEBIT_FAILED(5),   // Debit failed — Saga compensation trigger for Txn Svc
    WALLET_FROZEN(6),         // Wallet frozen — consumed by Notification, KYC
    WALLET_UNFROZEN(7),       // Wallet unfrozen — consumed by Notification
    WALLET_CLOSED(8);         // Wallet closed permanently

    private final int id;

    WalletEventTypeEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static WalletEventTypeEnum getEnumById(int id) {
        switch (id) {
            case 1 -> {
                return WALLET_PROVISIONED;
            }
            case 2 -> {
                return WALLET_ACTIVATED;
            }
            case 3 -> {
                return WALLET_DEBITED;
            }
            case 4 -> {
                return WALLET_CREDITED;
            }
            case 5 -> {
                return WALLET_DEBIT_FAILED;
            }
            case 6 -> {
                return WALLET_FROZEN;
            }
            case 7 -> {
                return WALLET_UNFROZEN;
            }
            case 8 -> {
                return WALLET_CLOSED;
            }
            default -> {
                return null;
            }
        }
    }
}
