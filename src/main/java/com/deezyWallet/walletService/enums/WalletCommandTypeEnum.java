package com.deezyWallet.walletService.enums;
/**
 * Commands sent to the "wallet.commands" Kafka topic by the Transaction Service.
 * The Wallet Service consumes these and executes the corresponding operation.
 *
 * This is the Saga command vocabulary between Transaction ↔ Wallet.
 *
 * DEBIT_CMD   — Subtract amount from available balance.
 * CREDIT_CMD  — Add amount to available balance.
 * BLOCK_CMD   — Move amount from balance → blockedBalance (funds held).
 * UNBLOCK_CMD — Move amount from blockedBalance → balance (release hold).
 */
public enum WalletCommandTypeEnum {
    DEBIT_CMD(1),
    CREDIT_CMD(2),
    BLOCK_CMD(3),
    UNBLOCK_CMD(4);

    private final int id;

    WalletCommandTypeEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static WalletCommandTypeEnum getEnumById(int id) {
        switch (id) {
            case 1 -> {
                return DEBIT_CMD;
            }
            case 2 -> {
                return CREDIT_CMD;
            }
            case 3 -> {
                return BLOCK_CMD;
            }
            case 4 -> {
                return UNBLOCK_CMD;
            }
            default -> {
                return null;
            }
        }
    }
}
