-- =============================================================================
-- Migration: V3__add_incomes_account_fk.sql
-- Purpose  : Add foreign key constraint from incomes.account_id to accounts.id.
--            Both tables now exist (V1 = incomes, V2 = accounts), so we can
--            safely add the FK that was intentionally deferred.
-- Notes    : Idempotent — uses DO $$ block to skip if constraint already exists.
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_incomes_account'
    ) THEN
        ALTER TABLE incomes
            ADD CONSTRAINT fk_incomes_account
            FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE SET NULL;
    END IF;
END $$;
