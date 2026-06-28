-- =============================================================================
-- Migration: V2__add_account_id_fk_to_expenses.sql
-- Purpose  : Add foreign key constraint for account_id in expenses table.
-- Notes    :
--   * The account_id column is created by Hibernate ddl-auto=update from
--     Expense.account (@ManyToOne). This script ensures the FK constraint
--     exists explicitly for environments where ddl-auto is not used or
--     where a manual migration is preferred.
--   * Reversible: ALTER TABLE ... DROP CONSTRAINT fk_expenses_account
--   * Idempotent: checks constraint existence before creating
-- =============================================================================

DO $$
BEGIN
    -- Add account_id column if not present (safe for fresh schemas)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'expenses' AND column_name = 'account_id'
    ) THEN
        ALTER TABLE expenses ADD COLUMN account_id UUID;
    END IF;

    -- Add FK constraint if not present
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_expenses_account'
          AND table_name = 'expenses'
    ) THEN
        ALTER TABLE expenses
            ADD CONSTRAINT fk_expenses_account
            FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_expenses_account_id ON expenses (account_id);