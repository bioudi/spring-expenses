-- =============================================================================
-- Migration: V4__drop_payment_method_and_card_name_columns.sql
-- Purpose  : Drop the payment_method and card_name columns from the
--            expenses and recurring_expenses tables. The fields have been
--            removed from the JPA entities and DTOs in t_ff592339; the
--            account relation now carries that information.
-- Notes    :
--   * Idempotent: checks information_schema.columns before issuing
--     ALTER TABLE ... DROP COLUMN. Safe to re-run on environments where
--     a previous failed attempt may have partially applied.
--   * These columns were created by Hibernate ddl-auto=update from
--     @Column(name = "payment_method") / @Column(name = "card_name") on
--     the Expense / RecurringExpense entities. With the entity fields
--     gone, ddl-auto would no longer re-create them; this migration
--     removes the existing ones explicitly.
--   * No data migration needed — the values were free-form user input
--     with no downstream consumers.
-- =============================================================================

DO $$
BEGIN
    -- expenses.payment_method
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'expenses' AND column_name = 'payment_method'
    ) THEN
        ALTER TABLE expenses DROP COLUMN payment_method;
    END IF;

    -- expenses.card_name
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'expenses' AND column_name = 'card_name'
    ) THEN
        ALTER TABLE expenses DROP COLUMN card_name;
    END IF;

    -- recurring_expenses.payment_method
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'recurring_expenses' AND column_name = 'payment_method'
    ) THEN
        ALTER TABLE recurring_expenses DROP COLUMN payment_method;
    END IF;

    -- recurring_expenses.card_name
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'recurring_expenses' AND column_name = 'card_name'
    ) THEN
        ALTER TABLE recurring_expenses DROP COLUMN card_name;
    END IF;
END $$;