-- pdl_payday_loan: real timestamps, and a recorded loan-closure date (2026-09-05).
--
-- RUN AS:  psql -v ON_ERROR_STOP=1 --single-transaction -f 004_pdl_loan_dates.sql
-- so a failing statement rolls the whole file back rather than leaving half of it.
--
-- Apply AFTER the application has started once on this version. Idempotent.
--
-- On the dev database (Hibernate 7 / Postgres 14) ddl-auto=update DID widen the
-- two columns from date to timestamptz on its own, so section 1 found nothing
-- to do. It stays because that behaviour is not something to depend on: it is
-- version-specific, it is not what ddl-auto does for check constraints (see
-- migration 002), and a deployment with ddl-auto off — which production should
-- be — would leave the columns as date while the entity reads Instant. Section
-- 1 is guarded on the current type, so it is a no-op wherever that already
-- happened.
--
-- PRE-CHECKS:
--   select column_name, data_type from information_schema.columns
--    where table_name = 'pdl_payday_loan'
--      and column_name in ('accepted_date','revoked_date','closed_date');
--   select status, count(*) from pdl_payday_loan group by 1;

-- ---------------------------------------------------------------------------
-- 1. accepted_date / revoked_date: date -> timestamptz
--
-- These two were java.sql.Date while every other date on the entity was an
-- Instant. Two consequences, both real: the time of day was silently discarded,
-- and java.sql.Date.toInstant() throws UnsupportedOperationException — the
-- consent-retention service called it and would have failed on the first
-- revoked loan. The entity now declares Instant, so the column has to follow or
-- Hibernate reads a type it no longer expects.
--
-- The cast puts existing values at local midnight, which loses nothing: a date
-- column never held a time to begin with.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'pdl_payday_loan' AND column_name = 'accepted_date'
                  AND data_type = 'date') THEN
        ALTER TABLE pdl_payday_loan
            ALTER COLUMN accepted_date TYPE timestamptz USING accepted_date::timestamptz;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'pdl_payday_loan' AND column_name = 'revoked_date'
                  AND data_type = 'date') THEN
        ALTER TABLE pdl_payday_loan
            ALTER COLUMN revoked_date TYPE timestamptz USING revoked_date::timestamptz;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2. Backfill closed_date for loans that ended before the column existed.
--
-- Going forward PaydayLoan.stampClosure() writes this on every transition into
-- a terminal state. History has to be reconstructed, and this is the one place
-- it is honest to do so in SQL: the same precedence the Java fallback uses, run
-- once, rather than every retention report re-deriving it forever.
--
--   Revoked  -> revoked_date, the date it was actually revoked
--   Closed   -> the later of last_transaction_date and updated_at
--   Rejected -> updated_at, the only signal there is
--
-- Deliberately conservative: where two dates could serve, the later one wins,
-- because it starts the retention clock later. Holding a record slightly too
-- long is recoverable; disposing of it slightly too early is not.
--
-- Only ever fills a NULL, so re-running cannot move a date that has since been
-- stamped for real.
-- ---------------------------------------------------------------------------
UPDATE pdl_payday_loan SET closed_date = revoked_date
 WHERE closed_date IS NULL AND status = 'Revoked' AND revoked_date IS NOT NULL;

UPDATE pdl_payday_loan SET closed_date = GREATEST(last_transaction_date, updated_at)
 WHERE closed_date IS NULL AND status = 'Closed' AND last_transaction_date IS NOT NULL;

UPDATE pdl_payday_loan SET closed_date = COALESCE(updated_at, created_at)
 WHERE closed_date IS NULL AND status IN ('Closed', 'Rejected', 'Revoked');

-- ---------------------------------------------------------------------------
-- 3. A live loan must not carry an end date.
--
-- Nothing should have written one, but a stale value here starts a retention
-- clock against a loan still in use, so it is worth being certain.
-- ---------------------------------------------------------------------------
UPDATE pdl_payday_loan SET closed_date = NULL
 WHERE closed_date IS NOT NULL AND status NOT IN ('Closed', 'Rejected', 'Revoked');

CREATE INDEX IF NOT EXISTS ix_pdl_payday_loan_closed_date ON pdl_payday_loan (closed_date);

-- ---------------------------------------------------------------------------
-- POST-APPLY VERIFICATION:
--   select column_name, data_type from information_schema.columns
--    where table_name = 'pdl_payday_loan'
--      and column_name in ('accepted_date','revoked_date','closed_date');
--     -- all three must read 'timestamp with time zone'
--
--   select status, count(*) filter (where closed_date is null) as missing, count(*)
--     from pdl_payday_loan group by 1 order by 1;
--     -- terminal statuses must show missing = 0; every other status must show
--     -- missing = the full count
--
-- Then GET /api/v1/pdl/admin/consent-ledger/retention: every candidate should
-- now report loanEndedSource = 'closedDate', with no 'approximate' rows left.
-- ---------------------------------------------------------------------------
