-- ─────────────────────────────────────────────────────────────────────────────
-- FILE: src/main/resources/db/migration/V2__seed_escrow_wallet.sql
-- Seeds the system ESCROW wallet used for in-flight Saga funds
-- ─────────────────────────────────────────────────────────────────────────────

INSERT IGNORE INTO wallets (
    id,
    wallet_number,
    user_id,
    balance,
    blocked_balance,
    currency,
    status,
    type,
    daily_limit,
    monthly_limit,
    version,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'WLT-SYSTEM-ESCROW',
    '00000000-0000-0000-0000-000000000000',
    0.0000,
    0.0000,
    'INR',
    'ACTIVE',
    'ESCROW',
    99999999.9999,
    99999999.9999,
    0,
    NOW(6),
    NOW(6)
);