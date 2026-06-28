-- =============================================================================
-- Rollback: V1__create_incomes_table.rollback.sql
-- Purpose  : Reverse the V1 migration that created the incomes table.
-- Usage    :
--   * Not auto-applied — this codebase uses Hibernate ddl-auto=update and
--     does not yet run a migration framework (Flyway/Liquibase). This file
--     is the authoritative "down" counterpart to V1__create_incomes_table.sql
--     for when such a tool is introduced, or for manual psql rollback:
--         psql -f V1__create_incomes_table.rollback.sql
--   * Drop order matters: indexes first (drop with the table), then the table.
--     The DROP TABLE ... CASCADE clause also removes the foreign-key
--     constraint to users and the check constraints declared in V1.
-- =============================================================================

DROP TABLE IF EXISTS incomes CASCADE;
