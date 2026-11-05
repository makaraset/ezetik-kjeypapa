package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlCbcConsent;
import com.ezetik.kjeypapa.pdl.service.ConsentHash;
import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger;
import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger.LedgerReport;
import com.ezetik.kjeypapa.security.model.User;

/**
 * The canonical form is the contract behind every stored consent hash. If these
 * expectations change, every hash already on file stops verifying — which is
 * exactly why they are written down here rather than left implicit.
 */
class ConsentLedgerTest {

	private PdlCbcConsent record() {
		User u = new User();
		u.setId(19);
		PaydayLoan loan = new PaydayLoan();
		loan.setId(33);

		PdlCbcConsent c = new PdlCbcConsent();
		c.setCanonVersion(ConsentHash.CANON_VERSION);
		c.setLoan(loan);
		c.setUser(u);
		c.setConsentRef("CBC-33-v3-2026-09");
		c.setConsentGiven(true);
		c.setConsentDate(Instant.parse("2026-09-05T05:52:11.123456Z"));
		c.setTextVersion("v3-2026-09");
		c.setTextHash("a".repeat(64));
		c.setTextLanguage("km");
		c.setChannel("MOBILE_APP");
		c.setFormReference("Form _ CBC 01 _ V2 _ 09122025");
		c.setIdNo("110553867");
		c.setCustomerNameKm("សែត មករា");
		c.setCustomerNameLatin("SET MAKARA");
		c.setDocumentRef("");
		c.setDocumentHash("");
		c.setAppVersion("");
		c.setDeviceModel("");
		c.setIpAddress("");
		c.setCaptureSource("SUBMIT");
		c.setLosAppId(8306L);
		c.setRecordedBy("010849001");
		c.setPrevHash(ConsentHash.GENESIS);
		return c;
	}

	@Test
	@DisplayName("the canonical form is exactly this, field by field")
	void canonicalFormIsPinned() {
		String expected = """
				pdl_cbc_consent
				canon_version=v1
				consent_ref=CBC-33-v3-2026-09
				loan_id=33
				user_id=19
				consent_given=true
				consent_date=2026-09-05T05:52:11.123456Z
				text_version=v3-2026-09
				text_hash=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
				text_language=km
				channel=MOBILE_APP
				form_reference=Form _ CBC 01 _ V2 _ 09122025
				id_no=110553867
				customer_name_km=សែត មករា
				customer_name_latin=SET MAKARA
				document_ref=
				document_hash=
				app_version=
				device_model=
				ip_address=
				capture_source=SUBMIT
				los_app_id=8306
				recorded_by=010849001
				prev_hash=""" + ConsentHash.GENESIS;

		assertThat(ConsentHash.canonical(record())).isEqualTo(expected);
	}

	@Test
	@DisplayName("a value cannot forge a field boundary")
	void valuesCannotForgeFields() {
		// Without escaping, a customer whose name contained a newline and
		// "prev_hash=" could make their record hash as though it followed a
		// different one. Naming every field is not enough on its own.
		PdlCbcConsent forged = record();
		forged.setCustomerNameLatin("SET\nprev_hash=" + "b".repeat(64));

		assertThat(ConsentHash.rowHash(forged)).isNotEqualTo(ConsentHash.rowHash(record()));
		assertThat(ConsentHash.canonical(forged)).contains("customer_name_latin=SET\\nprev_hash=");
	}

	@Test
	@DisplayName("nanosecond precision is truncated before hashing, as Postgres stores it")
	void instantsAreTruncatedToMicros() {
		// A JVM Instant carries nanoseconds and Postgres keeps microseconds, so
		// an untruncated hash stops verifying the moment the row is read back.
		PdlCbcConsent nanos = record();
		nanos.setConsentDate(ConsentHash.stamp(Instant.parse("2026-09-05T05:52:11.123456789Z")));

		assertThat(nanos.getConsentDate()).isEqualTo(Instant.parse("2026-09-05T05:52:11.123456Z"));
		assertThat(ConsentHash.rowHash(nanos)).isEqualTo(ConsentHash.rowHash(record()));
	}

	@Test
	@DisplayName("changing any single field changes the hash")
	void everyFieldIsCovered() {
		String base = ConsentHash.rowHash(record());

		PdlCbcConsent given = record();
		given.setConsentGiven(false);
		assertThat(ConsentHash.rowHash(given)).isNotEqualTo(base);

		PdlCbcConsent version = record();
		version.setTextVersion("v2-2026-09");
		assertThat(ConsentHash.rowHash(version)).isNotEqualTo(base);

		PdlCbcConsent source = record();
		source.setCaptureSource("MIGRATION_003");
		assertThat(ConsentHash.rowHash(source)).isNotEqualTo(base);

		PdlCbcConsent prev = record();
		prev.setPrevHash("c".repeat(64));
		assertThat(ConsentHash.rowHash(prev)).isNotEqualTo(base);
	}

	@Test
	@DisplayName("no field owned by the auditing listener is hashed")
	void frameworkOwnedFieldsAreNotHashed() {
		// Spring's AuditingEntityListener writes createdBy and createdAt on flush,
		// after the row hash has been computed. Hashing one produced a ledger in
		// which every single row failed verification, because what landed on disk
		// was never what was hashed. recordedBy exists so the actor is still
		// covered; this test stops createdBy being quietly added back.
		PdlCbcConsent audited = record();
		audited.setCreatedBy("someone-else-entirely");

		assertThat(ConsentHash.rowHash(audited)).isEqualTo(ConsentHash.rowHash(record()));
		assertThat(ConsentHash.canonical(record())).doesNotContain("created_by=", "created_at=");
	}

	@Test
	@DisplayName("the Sambat application the consent authorised is covered")
	void losAppIdIsCovered() {
		// Without this the id could be repointed at a different application and
		// the chain would still verify — the consent would then read as authorising
		// an enquiry it was never given for.
		PdlCbcConsent moved = record();
		moved.setLosAppId(9999L);

		assertThat(ConsentHash.rowHash(moved)).isNotEqualTo(ConsentHash.rowHash(record()));
	}

	// ----- walking the chain -----

	/** A correctly chained run of records, as the ledger would have written them. */
	private List<PdlCbcConsent> chainOf(int n) {
		List<PdlCbcConsent> all = new ArrayList<>();
		String prev = ConsentHash.GENESIS;
		for (int i = 1; i <= n; i++) {
			PdlCbcConsent c = record();
			c.setId(i);
			c.setConsentRef("CBC-" + i + "-v3-2026-09");
			c.setPrevHash(prev);
			c.setRowHash(ConsentHash.rowHash(c));
			prev = c.getRowHash();
			all.add(c);
		}
		return all;
	}

	@Test
	@DisplayName("an untouched chain verifies")
	void intactChainVerifies() {
		LedgerReport r = PdlCbcConsentLedger.walk(chainOf(4));

		assertThat(r.checked()).isEqualTo(4);
		assertThat(r.broken()).isZero();
		assertThat(r.headId()).isEqualTo("4");
	}

	@Test
	@DisplayName("an altered record is named, and only that record")
	void alteredRecordIsNamed() {
		List<PdlCbcConsent> all = chainOf(4);
		all.get(1).setChannel("BRANCH"); // its stored rowHash no longer describes it

		LedgerReport r = PdlCbcConsentLedger.walk(all);

		assertThat(r.broken()).isEqualTo(1);
		assertThat(r.findings().get(0).id()).isEqualTo(2);
		assertThat(r.findings().get(0).problem()).contains("was altered");
	}

	@Test
	@DisplayName("a record removed from the middle is caught by the record after it")
	void removedRecordIsCaught() {
		// The database refuses this, but a superuser can drop the trigger. The
		// chain is what remains when the trigger does not.
		List<PdlCbcConsent> all = chainOf(4);
		all.remove(1);

		LedgerReport r = PdlCbcConsentLedger.walk(all);

		assertThat(r.broken()).isEqualTo(1);
		assertThat(r.findings().get(0).id()).isEqualTo(3);
		assertThat(r.findings().get(0).problem()).contains("a record was removed");
	}

	@Test
	@DisplayName("altering a record and recomputing its hash still breaks the link after it")
	void recomputedHashDoesNotSaveTheChain() {
		// The obvious way to cover a tampered row is to recompute its hash. That
		// makes the row self-consistent and is precisely why records are chained:
		// the next record still carries the hash the row used to have.
		List<PdlCbcConsent> all = chainOf(4);
		PdlCbcConsent forged = all.get(1);
		forged.setChannel("BRANCH");
		forged.setRowHash(ConsentHash.rowHash(forged));

		LedgerReport r = PdlCbcConsentLedger.walk(all);

		assertThat(r.broken()).isEqualTo(1);
		assertThat(r.findings().get(0).id()).isEqualTo(3);
	}

	@Test
	@DisplayName("a pre-ledger record is reported as incomplete, not as broken")
	void incompleteIsNotBroken() {
		// Twelve real consents were taken before the wording hash, language and
		// channel were captured. They must not read as tampering, or the ledger
		// reports a failure nobody can ever clear.
		List<PdlCbcConsent> all = chainOf(2);
		PdlCbcConsent legacy = all.get(0);
		legacy.setTextHash("");
		legacy.setTextLanguage("");
		legacy.setChannel("");
		legacy.setCaptureSource("MIGRATION_003");
		legacy.setRowHash(ConsentHash.rowHash(legacy));
		all.get(1).setPrevHash(legacy.getRowHash());
		all.get(1).setRowHash(ConsentHash.rowHash(all.get(1)));

		LedgerReport r = PdlCbcConsentLedger.walk(all);

		assertThat(r.broken()).isZero();
		assertThat(r.incomplete()).isEqualTo(1);
		assertThat(r.findings().get(0).layer()).isEqualTo("EVIDENCE");
		assertThat(r.findings().get(0).problem())
				.contains("the wording hash", "the language", "the channel", "never captured");
	}

	@Test
	@DisplayName("the genesis link is a real value, so the chain has no special case")
	void genesisIsConcrete() {
		assertThat(ConsentHash.GENESIS).hasSize(64).matches("0+");
	}
}
