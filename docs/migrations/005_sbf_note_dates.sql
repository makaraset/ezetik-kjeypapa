-- sbf_note: the workflow dates become real timestamps (2026-09-05).
--
-- RUN AS:  psql -v ON_ERROR_STOP=1 --single-transaction -f 005_sbf_note_dates.sql
-- so a failing statement rolls the whole file back rather than leaving half of it.
--
-- Apply AFTER the application has started once on this version. Idempotent —
-- every statement is guarded on the column's current type.
--
-- The same defect migration 004 fixed on the PDL side, swept across the TFF
-- Note product: six workflow dates were java.sql.Date while disbursement and
-- repayment on the same row were Instant. Two consequences, both real:
--
--   * the time of day was discarded, so "when was this approved" answered only
--     to the day, and two events on the same day could not be ordered;
--   * java.sql.Date.toInstant() throws UnsupportedOperationException, so any
--     code treating these like the neighbouring date fields fails at runtime,
--     not at compile time. That is exactly how it was found on the PDL side.
--
-- The cast puts existing values at local midnight, which loses nothing: a date
-- column never held a time to begin with.
--
-- NOTE: on the dev database ddl-auto=update performed these widenings itself.
-- That is version-specific and must not be relied on — it is emphatically NOT
-- what ddl-auto does for check constraints (migration 002) — and a deployment
-- with ddl-auto off, which production should be, needs this file.
--
-- PRE-CHECKS:
--   select column_name, data_type from information_schema.columns
--    where table_name = 'sbf_note' and column_name like '%date%' order by 1;

DO $$
DECLARE
    col text;
BEGIN
    FOREACH col IN ARRAY ARRAY[
        'reviewed_date',
        'rejected_date',
        'approved_date',
        'accepted_date',
        'received_date',
        'delivery_confirmed_date',
        -- Mapped by no field today (Note.confirmedDate is commented out), but
        -- widened with the rest: leaving one column behind is how the next
        -- person reintroduces the same bug by uncommenting a line.
        'confirmed_date'
    ]
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'sbf_note' AND column_name = col
                      AND data_type = 'date') THEN
            EXECUTE format(
                'ALTER TABLE sbf_note ALTER COLUMN %I TYPE timestamptz USING %I::timestamptz',
                col, col);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- POST-APPLY VERIFICATION:
--   select column_name, data_type from information_schema.columns
--    where table_name = 'sbf_note' and column_name like '%date%' order by 1;
--     -- every row must read 'timestamp with time zone'
--
-- Then exercise one Note through review → approve → accept and confirm the
-- stamped values carry a time, not midnight.
--
-- UNRELATED, NOT FIXED HERE: sbf_note has BOTH repayment_date and
-- repyament_date (a misspelling), each timestamptz. Only the correctly spelled
-- one is mapped. Filed as F8 — dropping a column is not something to slip into
-- a type migration.
-- ---------------------------------------------------------------------------
