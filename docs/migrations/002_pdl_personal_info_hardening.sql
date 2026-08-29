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

-- ---------------------------------------------------------------------------
-- 2026-08-29 additions
-- ---------------------------------------------------------------------------

-- id_type now holds Sambat's idCode (N = National ID, P = Passport).
UPDATE pdl_personal_info SET id_type = 'N'
 WHERE id_type IS NULL OR id_type = '' OR id_type IN ('NID', 'National ID', 'National ID Card');
UPDATE pdl_personal_info SET id_type = 'P' WHERE id_type = 'Passport';

-- Approved accounts were reviewed by the LPO: record that as verified so the
-- identity lock applies to them from now on.
UPDATE pdl_personal_info p SET verified = true, verified_by = r.decided_by, verified_date = r.decided_date
  FROM pdl_account_request r
 WHERE r.user_id = p.user_id AND r.status = 'APPROVED' AND COALESCE(p.verified, false) = false;
UPDATE pdl_employment_info e SET verified = true, verified_by = r.decided_by, verified_date = r.decided_date
  FROM pdl_account_request r
 WHERE r.user_id = e.user_id AND r.status = 'APPROVED' AND COALESCE(e.verified, false) = false;
UPDATE pdl_bank_info b SET verified = true, verified_by = r.decided_by, verified_date = r.decided_date
  FROM pdl_account_request r
 WHERE r.user_id = b.user_id AND r.status = 'APPROVED' AND COALESCE(b.verified, false) = false;

-- Blank-vs-NULL: one encoding.
UPDATE pdl_personal_info SET id_issued_date = NULL WHERE id_issued_date = '';
UPDATE pdl_personal_info SET id_expiry_date = NULL WHERE id_expiry_date = '';
UPDATE pdl_personal_info SET corr_country = 'Cambodia' WHERE corr_country IS NULL OR corr_country = '';
UPDATE pdl_personal_info SET perm_country = 'Cambodia' WHERE perm_country IS NULL OR perm_country = '';

-- Bounded columns and the gender domain.
ALTER TABLE pdl_personal_info ALTER COLUMN verified SET DEFAULT false;
UPDATE pdl_personal_info SET verified = false WHERE verified IS NULL;
ALTER TABLE pdl_personal_info ALTER COLUMN verified SET NOT NULL;
ALTER TABLE pdl_personal_info DROP CONSTRAINT IF EXISTS ck_pdl_personal_info_gender;
ALTER TABLE pdl_personal_info ADD CONSTRAINT ck_pdl_personal_info_gender
    CHECK (gender IS NULL OR gender IN ('M', 'F'));

-- Login id is the phone number; it must be unique.
CREATE UNIQUE INDEX IF NOT EXISTS ux_ez_user_username ON ez_user (username);
