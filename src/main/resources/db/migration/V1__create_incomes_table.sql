-- =============================================================================
-- Migration: V1__create_incomes_table.sql
-- Purpose  : Create the incomes table for tracking income entries.
-- Notes    :
--   * Hibernate ddl-auto=update creates this table automatically from the
--     com.expensetracker.entity.Income entity. This script is provided as
--     authoritative reference for environments that run SQL migrations
--     (e.g. when Flyway/Liquibase are introduced, or for manual psql runs
--     against an existing database that was created before the entity).
--   * The accounts table referenced by account_id is intentionally NOT created
--     here — it does not exist in the application schema yet. Once the
--     Account entity is added, replace the plain account_id column with a
--     proper FK and add a follow-up migration.
-- =============================================================================

CREATE TABLE IF NOT EXISTS incomes (
    id            UUID            PRIMARY KEY,
    name          VARCHAR(255)    NOT NULL,
    type          VARCHAR(32)     NOT NULL,
    category      VARCHAR(32)     NOT NULL,
    amount        NUMERIC(19, 4)  NOT NULL,
    account_id    UUID            NULL,
    user_id       UUID            NOT NULL,
    timestamp     TIMESTAMP       NOT NULL,
    notes         TEXT            NULL,
    created_at    TIMESTAMP       NOT NULL,
    updated_at    TIMESTAMP       NOT NULL,

    CONSTRAINT fk_incomes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT chk_incomes_type
        CHECK (type IN ('CASH', 'TRANSFER')),

    CONSTRAINT chk_incomes_category
        CHECK (category IN ('PAYCHECK', 'REFUND', 'TAX_RETURN'))
);

CREATE INDEX IF NOT EXISTS idx_incomes_user_id        ON incomes (user_id);
CREATE INDEX IF NOT EXISTS idx_incomes_account_id     ON incomes (account_id);
CREATE INDEX IF NOT EXISTS idx_incomes_timestamp      ON incomes (timestamp);
CREATE INDEX IF NOT EXISTS idx_incomes_user_timestamp ON incomes (user_id, timestamp);