-- pdl_personal_info hardening (2026-08-28, extended 2026-08-29). Hibernate
-- ddl-auto=update does NOT add indexes to existing tables, so this is applied
-- by hand, like the pdl_payday_loan_status_check note. Idempotent.
--
-- RUN AS:  psql -v ON_ERROR_STOP=1 --single-transaction -f 002_pdl_personal_info_hardening.sql
-- so a failing constraint rolls everything back instead of leaving half of it.
--
-- PRE-CHECKS (all must return 0 rows, or fix the data first):
--   select trim(id_no), count(*) from pdl_personal_info where coalesce(id_no,'')<>'' group by 1 having count(*)>1;
--   select user_id, count(*) from pdl_personal_info group by 1 having count(*)>1;
--   select username, count(*) from ez_user group by 1 having count(*)>1;
--   select max(length(id_no)), max(length(mobile_phone)) from pdl_personal_info;   -- must be <= 30 / 15
--   -- approved accounts whose stored KYC would fail the new validator get the
--   -- identity lock; they can still edit addresses, but review these first:
--   select p.user_id from pdl_personal_info p join pdl_account_request r on r.user_id=p.user_id and r.status='APPROVED'
--    where coalesce(p.id_type,'N') in ('N','NID','National ID','National ID Card','') and (p.id_no is null or p.id_no !~ '^[0-9]{9}$');

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

-- Canonical encodings BEFORE any constraint or lock reads them: dd/MM/yyyy
-- dates (some rows were stored ISO) and M/F gender.
UPDATE pdl_personal_info SET date_of_birth  = to_char(to_date(date_of_birth,'YYYY-MM-DD'),'DD/MM/YYYY')  WHERE date_of_birth  ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$';
UPDATE pdl_personal_info SET id_issued_date = to_char(to_date(id_issued_date,'YYYY-MM-DD'),'DD/MM/YYYY') WHERE id_issued_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$';
UPDATE pdl_personal_info SET id_expiry_date = to_char(to_date(id_expiry_date,'YYYY-MM-DD'),'DD/MM/YYYY') WHERE id_expiry_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$';
UPDATE pdl_personal_info SET gender = CASE WHEN upper(left(trim(gender),1))='M' THEN 'M'
                                          WHEN upper(left(trim(gender),1))='F' THEN 'F' ELSE NULL END
 WHERE gender IS NOT NULL AND gender NOT IN ('M','F');
UPDATE pdl_personal_info SET id_no = upper(trim(id_no)) WHERE id_no IS NOT NULL AND id_no <> upper(trim(id_no));

-- Approved accounts were reviewed by the LPO: record that as verified so the
-- identity lock applies to them from now on. A verified row always names its
-- source, even where the request row predates decided_by.
UPDATE pdl_personal_info p SET verified = true,
       verified_by = COALESCE(r.decided_by, 'migration-002'), verified_date = COALESCE(r.decided_date, r.updated_at, now())
  FROM pdl_account_request r
 WHERE r.user_id = p.user_id AND r.status = 'APPROVED' AND COALESCE(p.verified, false) = false;
UPDATE pdl_employment_info e SET verified = true,
       verified_by = COALESCE(r.decided_by, 'migration-002'), verified_date = COALESCE(r.decided_date, r.updated_at, now())
  FROM pdl_account_request r
 WHERE r.user_id = e.user_id AND r.status = 'APPROVED' AND COALESCE(e.verified, false) = false;
UPDATE pdl_bank_info b SET verified = true,
       verified_by = COALESCE(r.decided_by, 'migration-002'), verified_date = COALESCE(r.decided_date, r.updated_at, now())
  FROM pdl_account_request r
 WHERE r.user_id = b.user_id AND r.status = 'APPROVED' AND COALESCE(b.verified, false) = false;

-- Blank-vs-NULL: one encoding.
UPDATE pdl_personal_info SET id_issued_date = NULL WHERE id_issued_date = '';
UPDATE pdl_personal_info SET id_expiry_date = NULL WHERE id_expiry_date = '';
UPDATE pdl_personal_info SET corr_country = 'Cambodia' WHERE corr_country IS NULL OR corr_country = '';
UPDATE pdl_personal_info SET perm_country = 'Cambodia' WHERE perm_country IS NULL OR perm_country = '';

-- Bounded columns (scripted here so they are reviewed, not left to ddl-auto)
-- and the gender domain.
ALTER TABLE pdl_personal_info ALTER COLUMN gender         TYPE varchar(1);
ALTER TABLE pdl_personal_info ALTER COLUMN date_of_birth  TYPE varchar(10);
ALTER TABLE pdl_personal_info ALTER COLUMN id_type        TYPE varchar(8);
ALTER TABLE pdl_personal_info ALTER COLUMN id_no          TYPE varchar(30);
ALTER TABLE pdl_personal_info ALTER COLUMN id_issued_date TYPE varchar(10);
ALTER TABLE pdl_personal_info ALTER COLUMN id_expiry_date TYPE varchar(10);
ALTER TABLE pdl_personal_info ALTER COLUMN mobile_phone   TYPE varchar(15);
ALTER TABLE pdl_personal_info ALTER COLUMN verified SET DEFAULT false;
UPDATE pdl_personal_info SET verified = false WHERE verified IS NULL;
ALTER TABLE pdl_personal_info ALTER COLUMN verified SET NOT NULL;
ALTER TABLE pdl_personal_info DROP CONSTRAINT IF EXISTS ck_pdl_personal_info_gender;
ALTER TABLE pdl_personal_info ADD CONSTRAINT ck_pdl_personal_info_gender
    CHECK (gender IS NULL OR gender IN ('M', 'F'));

-- Login id is the phone number; it must be unique.
CREATE UNIQUE INDEX IF NOT EXISTS ux_ez_user_username ON ez_user (username);

-- Rows already verified with no source (stamped by the first version of this
-- file, which copied NULL decided_by).
UPDATE pdl_personal_info   SET verified_by = 'migration-002', verified_date = COALESCE(verified_date, now()) WHERE verified AND verified_by IS NULL;
UPDATE pdl_employment_info SET verified_by = 'migration-002', verified_date = COALESCE(verified_date, now()) WHERE verified AND verified_by IS NULL;
UPDATE pdl_bank_info       SET verified_by = 'migration-002', verified_date = COALESCE(verified_date, now()) WHERE verified AND verified_by IS NULL;
