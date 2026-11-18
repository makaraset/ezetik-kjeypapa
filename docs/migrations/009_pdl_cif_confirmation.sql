-- An LPO's confirmation of a customer's CIF (2026-11-18).
--
-- RUN AS:  psql -v ON_ERROR_STOP=1 --single-transaction -f 009_pdl_cif_confirmation.sql
-- Idempotent: IF NOT EXISTS throughout. Adds four nullable columns, no data.
--
-- When Sambat hold a customer's ID number under other details (an older name
-- spelling, a mistyped date of birth), the automatic match refuses the CIF and
-- the customer's applications wait. The LPO can now confirm, when approving the
-- account request, that the CIF Sambat hold for that ID number is this
-- customer. These columns record that judgement: the CIF, what differed, who
-- confirmed and when. They stay null when the CIF came from a full match.
--
-- APPLY BEFORE DEPLOYING. SBF UAT runs with ddl-auto off, and PdlAccountRequest
-- maps these columns, so without them every read of an account request fails:
-- the LPO list, the review, the pending screen's status probe and sign-up.
--
-- PRE-CHECK (expect 0 rows before, 4 after):
--   select column_name from information_schema.columns
--    where table_name = 'pdl_account_request' and column_name like 'cif_confirmed%';

ALTER TABLE public.pdl_account_request
    ADD COLUMN IF NOT EXISTS cif_confirmed             bigint,
    -- Our field names, comma-separated: idNo, dateOfBirth, gender, name.
    ADD COLUMN IF NOT EXISTS cif_confirmed_differences character varying(255),
    ADD COLUMN IF NOT EXISTS cif_confirmed_by          character varying(255),
    ADD COLUMN IF NOT EXISTS cif_confirmed_date        timestamp(6) with time zone;
