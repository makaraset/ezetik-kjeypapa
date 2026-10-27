-- Every address has a country (2026-10-27).
--
-- RUN AS:  psql -v ON_ERROR_STOP=1 --single-transaction -f 007_pdl_address_country.sql
-- Idempotent: it only fills blanks.
--
-- SBF's testers reported "customer addresses are missing country". The loan
-- application was never short of it: LosApplicationMapper files KHM in every
-- country slot. What was blank was ours. The signup screen set birth_country
-- and work_country but never corr_country or perm_country, so every account
-- showed "Country: -" under both home addresses in My Profile, right beside a
-- work address that read "Cambodia".
--
-- Migration 002 already backfilled corr/perm country, and hid this on dev. SBF
-- UAT was built from 001-006 on empty tables, so 002 touched no rows there, and
-- every account created since went in blank. The server now fills the country
-- on every write (PersonalInfoValidator / EmploymentInfoValidator.normalise);
-- this repairs what was written before.
--
-- 'Cambodia' is the canonical label the app stores and localises (km កម្ពុជា).
-- It is right for every row: a PDL account requires a Cambodian NID, and every
-- stored address code resolves against Sambat's Cambodia-only gazetteer.
--
-- PRE-CHECK (how many rows each statement will touch):
--   select count(*) filter (where coalesce(btrim(corr_country),'')  = '') corr,
--          count(*) filter (where coalesce(btrim(perm_country),'')  = '') perm,
--          count(*) filter (where coalesce(btrim(birth_country),'') = '') birth
--     from pdl_personal_info;
--   select count(*) filter (where coalesce(btrim(work_country),'') = '') work
--     from pdl_employment_info;

UPDATE pdl_personal_info   SET corr_country  = 'Cambodia' WHERE coalesce(btrim(corr_country),  '') = '';
UPDATE pdl_personal_info   SET perm_country  = 'Cambodia' WHERE coalesce(btrim(perm_country),  '') = '';
UPDATE pdl_personal_info   SET birth_country = 'Cambodia' WHERE coalesce(btrim(birth_country), '') = '';
UPDATE pdl_employment_info SET work_country  = 'Cambodia' WHERE coalesce(btrim(work_country),  '') = '';
