-- =============================================================================
-- Migration: V2__create_accounts_table.sql
-- Purpose  : Create the accounts table for tracking user accounts.
-- Spec     : t_f4bcedde — Account entity (id UUID, name String, balance
--            BigDecimal, type enum BASE/SAVINGS/EMERGENCY/CREDIT, userId
--            UUID FK to users).
-- Notes    :
--   * Hibernate ddl-auto=update creates this table automatically from the
--     com.expensetracker.entity.Account entity. This script is provided as
--     authoritative reference for environments that run SQL migrations.
--   * The incomes table's account_id FK was left unconstrained in V1;
--     a follow-up migration can add the FK constraint once the accounts
--     table exists.
--   * The `type` column is VARCHAR(32) with a CHECK constraint enforcing
--     the four enum values — kept in sync with @Enumerated(EnumType.STRING)
--     on the entity, so renaming a value here requires a corresponding
--     enum change in AccountType.java (and vice versa).
--   * The migration is idempotent (CREATE TABLE IF NOT EXISTS, CREATE INDEX
--     IF NOT EXISTS) so it is safe to re-run.
-- =============================================================================

CREATE TABLE IF NOT EXISTS accounts (
    id            UUID            PRIMARY KEY,
    name          VARCHAR(255)    NOT NULL,
    balance       NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    type          VARCHAR(32)     NOT NULL,
    user_id       UUID            NOT NULL,
    created_at    TIMESTAMP       NOT NULL,
    updated_at    TIMESTAMP       NOT NULL,

    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT chk_accounts_type
        CHECK (type IN ('BASE', 'SAVINGS', 'EMERGENCY', 'CREDIT'))
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id      ON accounts (user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_type         ON accounts (type);
