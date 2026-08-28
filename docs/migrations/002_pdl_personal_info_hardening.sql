-- pdl_personal_info hardening (2026-08-28). Hibernate ddl-auto=update does NOT
-- add indexes to existing tables, so this is applied by hand, like the
-- pdl_payday_loan_status_check note. Idempotent.

-- One identity, one account. Partial: rows with no ID number are not in scope.
CREATE UNIQUE INDEX IF NOT EXISTS ux_pdl_personal_info_id_no
    ON pdl_personal_info (id_no)
    WHERE id_no IS NOT NULL AND id_no <> '';

-- One profile row per user; the service already assumes it (findByUser().get(0)).
CREATE UNIQUE INDEX IF NOT EXISTS ux_pdl_personal_info_user
    ON pdl_personal_info (user_id);

-- The webhook lookup key for every LOS callback.
CREATE INDEX IF NOT EXISTS ix_pdl_payday_loan_los_application_no
    ON pdl_payday_loan (los_application_no);
