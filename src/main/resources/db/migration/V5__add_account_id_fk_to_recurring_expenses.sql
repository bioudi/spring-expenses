-- =============================================================================
-- Migration: V5__add_account_id_fk_to_recurring_expenses.sql
-- Purpose  : Add foreign key constraint for account_id in recurring_expenses
--            table, so generated Expenses can carry the same account the user
--            chose on the recurring template.
-- Notes    :
--   * The account_id column is created by Hibernate ddl-auto=update from
--     RecurringExpense.account (@ManyToOne). This script ensures the FK
--     constraint exists explicitly for environments where ddl-auto is not
--     used or where a manual migration is preferred.
--   * Mirrors V2__add_account_id_fk_to_expenses.sql and
--     V3__add_incomes_account_fk.sql.
--   * Idempotent: skips column/FK creation when either already exists.
-- =============================================================================

DO $$
BEGIN
    -- Add account_id column if not present (safe for fresh schemas)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'recurring_expenses' AND column_name = 'account_id'
    ) THEN
        ALTER TABLE recurring_expenses ADD COLUMN account_id UUID;
    END IF;

    -- Add FK constraint if not present
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_recurring_expenses_account'
          AND table_name = 'recurring_expenses'
    ) THEN
        ALTER TABLE recurring_expenses
            ADD CONSTRAINT fk_recurring_expenses_account
            FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_recurring_expenses_account_id ON recurring_expenses (account_id);
