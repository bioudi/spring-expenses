-- =============================================================================
-- Migration: V6__create_recurring_incomes_table.sql
-- Purpose  : Create the recurring_incomes table for templates that describe
--            repeating income events (e.g. bi-weekly paycheck).
-- Spec     : t_a144b3cc — recurring income model mirroring RecurringExpense.
-- Notes    :
--   * Hibernate ddl-auto=update creates this table automatically from the
--     com.expensetracker.entity.RecurringIncome entity. This script is the
--     authoritative reference for environments that run SQL migrations
--     against an existing database (manual psql, fresh-prod provisioning,
--     etc.).
--   * Schema mirrors recurring_expenses: identical recurrence columns
--     (frequency, day_of_week, day_of_month, start_date, end_date,
--     next_occurrence, active) and FK columns (user_id, account_id).
--   * Income-specific columns: name (template label), type (IncomeType
--     enum), category (IncomeCategory enum) — both stored as VARCHAR with
--     CHECK constraints kept in sync with the JPA enums.
--   * account_id FK uses ON DELETE SET NULL so deleting an account
--     doesn't cascade-orphan templates.
--   * Idempotent: CREATE TABLE / CREATE INDEX IF NOT EXISTS, DO $$ block
--     for the FK constraint so re-runs are no-ops.
--   * Reversibility: there is no companion .rollback.sql in this PR —
--     drop with `DROP TABLE IF EXISTS recurring_incomes CASCADE;` if you
--     need to undo the migration.
-- =============================================================================

CREATE TABLE IF NOT EXISTS recurring_incomes (
    id                UUID            PRIMARY KEY,
    name              VARCHAR(255)    NOT NULL,
    type              VARCHAR(32)     NOT NULL,
    category          VARCHAR(32)     NOT NULL,
    amount            NUMERIC(19, 4)  NOT NULL,
    notes             TEXT            NULL,
    frequency         VARCHAR(32)     NOT NULL,
    day_of_week       VARCHAR(16)     NULL,
    day_of_month      INTEGER         NULL,
    start_date        DATE            NOT NULL,
    end_date          DATE            NULL,
    next_occurrence   DATE            NOT NULL,
    active            BOOLEAN         NOT NULL DEFAULT TRUE,
    user_id           UUID            NULL,
    account_id        UUID            NULL,
    created_at        TIMESTAMP       NOT NULL,
    updated_at        TIMESTAMP       NULL,

    CONSTRAINT fk_recurring_incomes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_recurring_incomes_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
        ON DELETE SET NULL,

    CONSTRAINT chk_recurring_incomes_type
        CHECK (type IN ('CASH', 'TRANSFER')),

    CONSTRAINT chk_recurring_incomes_category
        CHECK (category IN ('PAYCHECK', 'REFUND', 'TAX_RETURN')),

    CONSTRAINT chk_recurring_incomes_frequency
        CHECK (frequency IN ('DAILY', 'WEEKLY', 'BI_WEEKLY', 'MONTHLY')),

    CONSTRAINT chk_recurring_incomes_day_of_month
        CHECK (day_of_month IS NULL OR (day_of_month BETWEEN 1 AND 31))
);

CREATE INDEX IF NOT EXISTS idx_recurring_incomes_user_id          ON recurring_incomes (user_id);
CREATE INDEX IF NOT EXISTS idx_recurring_incomes_account_id       ON recurring_incomes (account_id);
CREATE INDEX IF NOT EXISTS idx_recurring_incomes_next_occurrence  ON recurring_incomes (next_occurrence);
CREATE INDEX IF NOT EXISTS idx_recurring_incomes_active           ON recurring_incomes (active);