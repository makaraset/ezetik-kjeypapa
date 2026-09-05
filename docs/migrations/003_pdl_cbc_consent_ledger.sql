-- pdl_cbc_consent: indexes and append-only enforcement (2026-09-05).
-- The TABLE itself arrives via ddl-auto=update when the app starts; Hibernate
-- never creates indexes, constraints or triggers, so those live here. Apply
-- AFTER the application has started once. Idempotent.
--
-- RUN AS:  psql -v ON_ERROR_STOP=1 --single-transaction -f 003_pdl_cbc_consent_ledger.sql
-- so a failing statement rolls the whole file back rather than leaving half of it.
--
-- PRE-CHECKS:
--   select count(*) from pdl_payday_loan where cbc_consent_date is not null;  -- consents to backfill
--   select count(*) from pdl_cbc_consent;                                     -- expect 0 before the backfill
--   select cbc_consent_text_version, count(*) from pdl_payday_loan
--    where cbc_consent_date is not null group by 1;                           -- the wording versions in play
--
-- THE BACKFILL IS NOT IN THIS FILE. It runs through
--   POST /api/v1/pdl/admin/consent-ledger/backfill
-- because the canonical form and the hashing live in Java (ConsentHash), and
-- writing them a second time in SQL would create two definitions of the same
-- evidence that can drift. It must run BEFORE the first live consent: a hash
-- chain can be appended to but never prepended to.

-- ---------------------------------------------------------------------------
-- One encoding for "not captured". Every text column is '' rather than NULL, so
-- that later work populating them does not change the canonical form and so
-- invalidate hashes written before it.
-- ---------------------------------------------------------------------------
ALTER TABLE pdl_cbc_consent ALTER COLUMN document_ref        SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN document_hash       SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN app_version         SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN device_model        SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN ip_address          SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN form_reference      SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN id_no               SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN customer_name_km    SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN customer_name_latin SET DEFAULT '';
ALTER TABLE pdl_cbc_consent ALTER COLUMN recorded_by         SET DEFAULT '';

UPDATE pdl_cbc_consent SET
    document_ref        = COALESCE(document_ref, ''),
    document_hash       = COALESCE(document_hash, ''),
    app_version         = COALESCE(app_version, ''),
    device_model        = COALESCE(device_model, ''),
    ip_address          = COALESCE(ip_address, ''),
    form_reference      = COALESCE(form_reference, ''),
    id_no               = COALESCE(id_no, ''),
    customer_name_km    = COALESCE(customer_name_km, ''),
    customer_name_latin = COALESCE(customer_name_latin, ''),
    recorded_by         = COALESCE(recorded_by, '')
WHERE recorded_by IS NULL OR document_ref IS NULL OR document_hash IS NULL OR app_version IS NULL
   OR device_model IS NULL OR ip_address IS NULL OR form_reference IS NULL
   OR id_no IS NULL OR customer_name_km IS NULL OR customer_name_latin IS NULL;

-- ---------------------------------------------------------------------------
-- Shape constraints. A hash that is not a hash is a corrupted record, and it is
-- cheaper to refuse it at the door than to discover it during an audit.
-- ---------------------------------------------------------------------------
ALTER TABLE pdl_cbc_consent DROP CONSTRAINT IF EXISTS ck_pdl_cbc_consent_row_hash;
ALTER TABLE pdl_cbc_consent ADD  CONSTRAINT ck_pdl_cbc_consent_row_hash
    CHECK (row_hash ~ '^[0-9a-f]{64}$');

ALTER TABLE pdl_cbc_consent DROP CONSTRAINT IF EXISTS ck_pdl_cbc_consent_prev_hash;
ALTER TABLE pdl_cbc_consent ADD  CONSTRAINT ck_pdl_cbc_consent_prev_hash
    CHECK (prev_hash ~ '^[0-9a-f]{64}$');

ALTER TABLE pdl_cbc_consent DROP CONSTRAINT IF EXISTS ck_pdl_cbc_consent_text_hash;
ALTER TABLE pdl_cbc_consent ADD  CONSTRAINT ck_pdl_cbc_consent_text_hash
    CHECK (text_hash = '' OR text_hash ~ '^[0-9a-f]{64}$');

-- '' is permitted on the three below, and it means one specific thing: the
-- fact was never recorded, because the consent predates the column. Twelve of
-- the consents backfilled by MIGRATION_003 are in exactly that state — they
-- were taken before channel, language and the wording hash were captured.
--
-- The constraint is deliberately not tightened to forbid it, because the
-- alternative is to invent 'MOBILE_APP' and 'km' for records where nobody
-- wrote them down, and a record that guesses is worth less than one that says
-- it does not know. What must not happen is a NEW consent being written this
-- way, and that is enforced where it belongs — PdlCbcConsentLedger.append()
-- rejects a live record missing any of them. SQL cannot make that distinction
-- without also rejecting the legacy rows it must accept.
ALTER TABLE pdl_cbc_consent DROP CONSTRAINT IF EXISTS ck_pdl_cbc_consent_document_hash;
ALTER TABLE pdl_cbc_consent ADD  CONSTRAINT ck_pdl_cbc_consent_document_hash
    CHECK (document_hash = '' OR document_hash ~ '^[0-9a-f]{64}$');

ALTER TABLE pdl_cbc_consent DROP CONSTRAINT IF EXISTS ck_pdl_cbc_consent_language;
ALTER TABLE pdl_cbc_consent ADD  CONSTRAINT ck_pdl_cbc_consent_language
    CHECK (text_language IN ('km', 'en', ''));

ALTER TABLE pdl_cbc_consent DROP CONSTRAINT IF EXISTS ck_pdl_cbc_consent_channel;
ALTER TABLE pdl_cbc_consent ADD  CONSTRAINT ck_pdl_cbc_consent_channel
    CHECK (channel IN ('MOBILE_APP', 'WEB', 'BRANCH', ''));

-- ---------------------------------------------------------------------------
-- Indexes. ux on row_hash because a repeated row hash means a duplicated
-- record; the partial unique on loan leaves room to append a future withdrawal
-- row (consent_given = false) without a second consent being possible.
-- ---------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS ux_pdl_cbc_consent_ref       ON pdl_cbc_consent (consent_ref);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pdl_cbc_consent_row_hash  ON pdl_cbc_consent (row_hash);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pdl_cbc_consent_loan_given
    ON pdl_cbc_consent (loan_id) WHERE consent_given;
CREATE        INDEX IF NOT EXISTS ix_pdl_cbc_consent_user      ON pdl_cbc_consent (user_id);
CREATE        INDEX IF NOT EXISTS ix_pdl_cbc_consent_date      ON pdl_cbc_consent (consent_date);

-- ---------------------------------------------------------------------------
-- Append-only.
--
-- This is a trigger rather than a REVOKE because the application connects as
-- the postgres superuser, and a superuser ignores table privileges — the grant
-- would look like protection while enforcing nothing. The trigger stops
-- application bugs and accidents, which is what it is for. It does NOT stop a
-- determined administrator: a superuser can disable it and recompute the chain.
-- The answer to that is off-box, not in this file — the nightly job logs the
-- ledger head hash, so the tip of the chain also exists somewhere this database
-- cannot rewrite.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pdl_block_update_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'append-only table %: % is not permitted', TG_TABLE_NAME, TG_OP
        USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_pdl_cbc_consent_append_only ON pdl_cbc_consent;
CREATE TRIGGER trg_pdl_cbc_consent_append_only
    BEFORE UPDATE OR DELETE ON pdl_cbc_consent
    FOR EACH ROW EXECUTE FUNCTION pdl_block_update_delete();

-- A row-level trigger never fires on TRUNCATE. Without this one statement
-- empties the ledger and every row-level protection above is irrelevant.
DROP TRIGGER IF EXISTS trg_pdl_cbc_consent_no_truncate ON pdl_cbc_consent;
CREATE TRIGGER trg_pdl_cbc_consent_no_truncate
    BEFORE TRUNCATE ON pdl_cbc_consent
    FOR EACH STATEMENT EXECUTE FUNCTION pdl_block_update_delete();

-- ---------------------------------------------------------------------------
-- Recommended for production, once the application stops connecting as
-- postgres. Defence in depth: with a non-superuser role the privileges below
-- are enforced even if a trigger is dropped.
--
--   CREATE ROLE kjeypapa_app LOGIN PASSWORD '...';
--   GRANT SELECT, INSERT ON pdl_cbc_consent TO kjeypapa_app;
--   REVOKE UPDATE, DELETE, TRUNCATE ON pdl_cbc_consent FROM kjeypapa_app;
--
-- POST-APPLY VERIFICATION (each must fail):
--   update pdl_cbc_consent set channel = 'WEB' where id = 1;
--   delete from pdl_cbc_consent where id = 1;
--   truncate pdl_cbc_consent;
--
-- Then GET /api/v1/pdl/admin/consent-ledger/verify: expect broken = 0, and
-- incomplete = the number of pre-ledger consents (12 in dev), each named.
-- ---------------------------------------------------------------------------
