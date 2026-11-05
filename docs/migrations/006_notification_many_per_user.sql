-- ez_notification: a user may hold more than one notification (2026-09-07).
--
-- RUN AS:  psql -v ON_ERROR_STOP=1 --single-transaction -f 006_notification_many_per_user.sql
-- Idempotent — it drops a constraint only if it is there.
--
-- NotificationModel.user was mapped @OneToOne, so Hibernate generated a UNIQUE
-- constraint on user_id: one notification per user, ever. The first insert
-- succeeded and every one after it failed, marked the surrounding transaction
-- rollback-only, and took the whole request down.
--
-- The damage was worst on the inbound LOS webhooks. A loan status update from
-- Sambat was discarded because a NOTIFICATION could not be written, and the
-- caller was answered with an opaque HTTP 403 — Spring's rollback exception
-- reaching the error dispatch, which is not on the security whitelist.
--
-- It hid because notify() skips the database write when the user has no FCM
-- token, so most test users never stored even a first row.
--
-- ddl-auto=update will NOT drop this constraint — the same trap as the check
-- constraint in migration 002 and the column types in 004. It has to be done here.
--
-- PRE-CHECK (expect exactly one row, the unique index):
--   select conname, pg_get_constraintdef(oid) from pg_constraint
--    where conrelid = 'ez_notification'::regclass and contype = 'u';

DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'ez_notification'::regclass
           AND contype = 'u'
           AND pg_get_constraintdef(oid) = 'UNIQUE (user_id)'
    LOOP
        EXECUTE format('ALTER TABLE ez_notification DROP CONSTRAINT %I', c.conname);
        RAISE NOTICE 'dropped %', c.conname;
    END LOOP;
END $$;

-- Reading a user's notifications is the only query this table serves.
CREATE INDEX IF NOT EXISTS ix_ez_notification_user ON ez_notification (user_id);

-- ---------------------------------------------------------------------------
-- POST-APPLY VERIFICATION:
--   select conname, pg_get_constraintdef(oid) from pg_constraint
--    where conrelid = 'ez_notification'::regclass and contype = 'u';
--     -- must return no rows
--
-- Then send the same LOS webhook twice for one customer: both must answer 200.
-- ---------------------------------------------------------------------------
