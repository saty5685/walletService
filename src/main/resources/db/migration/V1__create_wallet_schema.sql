-- MySQL 8.0.16+ compatible — Flyway managed

-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: wallets
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE wallets (
    id               VARCHAR(36)   NOT NULL DEFAULT (UUID()),
    wallet_number    VARCHAR(25)   NOT NULL,
    user_id          VARCHAR(36)   NOT NULL,
    balance          DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    blocked_balance  DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency         CHAR(3)       NOT NULL DEFAULT 'INR',
    status           VARCHAR(20)   NOT NULL DEFAULT 'INACTIVE',
    type             VARCHAR(20)   NOT NULL DEFAULT 'PERSONAL',
    daily_limit      DECIMAL(19,4) NOT NULL DEFAULT 50000.0000,
    monthly_limit    DECIMAL(19,4) NOT NULL DEFAULT 200000.0000,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL DEFAULT NOW(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT NOW(6),

    CONSTRAINT pk_wallets
        PRIMARY KEY (id),

    CONSTRAINT uq_wallets_wallet_number
        UNIQUE (wallet_number),

    -- CHECK constraints fully enforced in MySQL 8.0.16+
    CONSTRAINT chk_wallets_balance_non_negative
        CHECK (balance >= 0),

    CONSTRAINT chk_wallets_blocked_non_negative
        CHECK (blocked_balance >= 0),

    CONSTRAINT chk_wallets_status
        CHECK (status IN ('INACTIVE', 'ACTIVE', 'FROZEN', 'CLOSED')),

    CONSTRAINT chk_wallets_type
        CHECK (type IN ('PERSONAL', 'MERCHANT', 'ESCROW')),

    CONSTRAINT chk_wallets_currency
        CHECK (currency = 'INR'),

    CONSTRAINT chk_wallets_daily_limit_positive
        CHECK (daily_limit > 0),

    CONSTRAINT chk_wallets_monthly_limit_positive
        CHECK (monthly_limit > 0),

    CONSTRAINT chk_wallets_limit_coherence
        CHECK (daily_limit <= monthly_limit)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
-- ENGINE=InnoDB is mandatory — MyISAM does not support FK, transactions, or row locking

-- ── Indexes on wallets ────────────────────────────────────────────────────────

CREATE INDEX idx_wallets_user_id
    ON wallets (user_id);

CREATE INDEX idx_wallets_status
    ON wallets (status);

-- Note: UNIQUE KEY on wallet_number already created inline above via UNIQUE constraint


-- ── Trigger: auto-update updated_at ──────────────────────────────────────────
-- FIX 4 + 5: MySQL trigger syntax — NO "CREATE OR REPLACE FUNCTION", NO PL/pgSQL
-- Flyway executes each statement separated by semicolons.
-- To avoid the semicolon-inside-trigger issue, use a single-statement trigger body.
-- MySQL trigger equivalent of the PostgreSQL function + trigger pattern:

CREATE TRIGGER trg_wallets_updated_at
    BEFORE UPDATE ON wallets
    FOR EACH ROW
    SET NEW.updated_at = NOW(6);
-- MySQL allows a single SET statement as the trigger body without BEGIN/END


-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: wallet_transactions
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE wallet_transactions (
    id               VARCHAR(36)   NOT NULL DEFAULT (UUID()),
    wallet_id        VARCHAR(36)   NOT NULL,

    type             VARCHAR(10)   NOT NULL,
    amount           DECIMAL(19,4) NOT NULL,
    balance_after    DECIMAL(19,4) NOT NULL,
    currency         CHAR(3)       NOT NULL DEFAULT 'INR',
    description      VARCHAR(255),
    reference_id     VARCHAR(100),
    idempotency_key  VARCHAR(128),
    metadata         JSON,
    created_at       DATETIME(6)   NOT NULL DEFAULT NOW(6),

    -- ── Constraints ──────────────────────────────────────────────────────────

    CONSTRAINT pk_wallet_transactions
        PRIMARY KEY (id),

    CONSTRAINT uq_wallet_transactions_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT fk_wallet_transactions_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_wtxn_type
        CHECK (type IN ('DEBIT', 'CREDIT')),

    CONSTRAINT chk_wtxn_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_wtxn_balance_after_non_negative
        CHECK (balance_after >= 0)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ── Indexes on wallet_transactions ────────────────────────────────────────────
CREATE INDEX idx_wtxn_wallet_date
    ON wallet_transactions (wallet_id, created_at DESC);

CREATE INDEX idx_wtxn_reference_id
    ON wallet_transactions (reference_id);

-- The unique constraint above on idempotency_key already creates the index.
-- NULL values in a UNIQUE index are treated as distinct in MySQL (safe for nullable key).

-- For JSON querying, use a generated (virtual) column index instead.
-- Example: if you need to index metadata->>'$.category':
--
-- ALTER TABLE wallet_transactions
--   ADD COLUMN metadata_category VARCHAR(100)
--     GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.category'))) VIRTUAL;
-- CREATE INDEX idx_wtxn_metadata_category ON wallet_transactions (metadata_category);
--
-- Uncomment the above when/if metadata querying becomes a requirement.
-- For now metadata is display-only — no index needed.


-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: wallet_limits
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE wallet_limits (
    id            VARCHAR(36)   NOT NULL DEFAULT (UUID()),
    wallet_id     VARCHAR(36)   NOT NULL,
    daily_spent   DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    limit_date    DATE          NOT NULL,
    updated_at    DATETIME(6)   NOT NULL DEFAULT NOW(6),

    -- ── Constraints ──────────────────────────────────────────────────────────

    CONSTRAINT pk_wallet_limits
        PRIMARY KEY (id),

    CONSTRAINT uq_wallet_limits_wallet_date
        UNIQUE (wallet_id, limit_date),

    CONSTRAINT fk_wallet_limits_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id)
        ON DELETE CASCADE,

    CONSTRAINT chk_wlimit_daily_spent_non_negative
        CHECK (daily_spent >= 0)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ── Indexes on wallet_limits ──────────────────────────────────────────────────

-- This enables efficient "ORDER BY limit_date DESC" on the wallet partition
CREATE INDEX idx_wlimit_wallet_date
    ON wallet_limits (wallet_id, limit_date DESC);

-- ── Trigger: auto-update updated_at on wallet_limits ─────────────────────────
-- FIX 4: MySQL trigger syntax
CREATE TRIGGER trg_wallet_limits_updated_at
    BEFORE UPDATE ON wallet_limits
    FOR EACH ROW
    SET NEW.updated_at = NOW(6);
