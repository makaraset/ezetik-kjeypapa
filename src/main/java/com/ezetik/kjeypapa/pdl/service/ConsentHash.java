package com.ezetik.kjeypapa.pdl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.ezetik.kjeypapa.pdl.model.PdlCbcConsent;

/**
 * The canonical form of a consent record, and its hash.
 *
 * <p><b>This class defines evidence.</b> Every stored {@code rowHash} was
 * computed by the code below, so changing the canonical form invalidates every
 * hash on file — a test pins the exact string for that reason. If a field ever
 * has to be added, raise {@link #CANON_VERSION} and keep the old form working
 * for rows written under it, rather than editing this one.
 */
public final class ConsentHash {

	/** The canonical form's own version, so it can be changed without lying about old rows. */
	public static final String CANON_VERSION = "v1";

	/** {@code prevHash} of the first record; never null, so the chain has no special case. */
	public static final String GENESIS = "0".repeat(64);

	private ConsentHash() {
	}

	/**
	 * Postgres stores microseconds; a JVM {@link Instant} carries nanoseconds.
	 * Hash an untruncated instant and the row stops verifying the moment it is
	 * read back from the database — so truncation happens here, once, and every
	 * caller stores what was hashed.
	 */
	public static Instant stamp(Instant at) {
		return at == null ? null : at.truncatedTo(ChronoUnit.MICROS);
	}

	/**
	 * The exact bytes hashed for a record.
	 *
	 * <p>Each field is written as {@code name=value} on its own line. Naming
	 * every field and escaping the separators means no value can forge a field
	 * boundary — a name containing a newline and {@code prev_hash=} cannot make
	 * the record look like a different one.
	 *
	 * <p>Deliberately absent: {@code id}, which the database assigns after the
	 * hash must already exist, and the {@link com.ezetik.kjeypapa.security.audit.UserDateAudit}
	 * columns. Those belong to Spring's auditing listener, which writes them on
	 * flush — after this hash has been computed — so a field it owns can never be
	 * covered here. {@code recordedBy} exists for that reason, and
	 * {@code createdAt} has nothing to add that {@code consentDate} does not say.
	 */
	public static String canonical(PdlCbcConsent c) {
		StringBuilder sb = new StringBuilder(768);
		sb.append("pdl_cbc_consent\n");
		field(sb, "canon_version", c.getCanonVersion());
		field(sb, "consent_ref", c.getConsentRef());
		field(sb, "loan_id", c.getLoan() == null ? null : String.valueOf(c.getLoan().getId()));
		field(sb, "user_id", c.getUser() == null ? null : String.valueOf(c.getUser().getId()));
		field(sb, "consent_given", String.valueOf(c.isConsentGiven()));
		field(sb, "consent_date", c.getConsentDate() == null ? null
				: DateTimeFormatter.ISO_INSTANT.format(c.getConsentDate()));
		field(sb, "text_version", c.getTextVersion());
		field(sb, "text_hash", c.getTextHash());
		field(sb, "text_language", c.getTextLanguage());
		field(sb, "channel", c.getChannel());
		field(sb, "form_reference", c.getFormReference());
		field(sb, "id_no", c.getIdNo());
		field(sb, "customer_name_km", c.getCustomerNameKm());
		field(sb, "customer_name_latin", c.getCustomerNameLatin());
		field(sb, "document_ref", c.getDocumentRef());
		field(sb, "document_hash", c.getDocumentHash());
		field(sb, "app_version", c.getAppVersion());
		field(sb, "device_model", c.getDeviceModel());
		field(sb, "ip_address", c.getIpAddress());
		field(sb, "capture_source", c.getCaptureSource());
		field(sb, "los_app_id", c.getLosAppId() == null ? null : String.valueOf(c.getLosAppId()));
		field(sb, "recorded_by", c.getRecordedBy());
		// last, and with no trailing newline
		sb.append("prev_hash=").append(escape(c.getPrevHash()));
		return sb.toString();
	}

	public static String rowHash(PdlCbcConsent c) {
		return sha256(canonical(c));
	}

	public static String sha256(String text) {
		return sha256((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
	}

	public static String sha256(byte[] bytes) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
			StringBuilder sb = new StringBuilder(64);
			for (byte b : d)
				sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	private static void field(StringBuilder sb, String name, String value) {
		sb.append(name).append('=').append(escape(value)).append('\n');
	}

	/**
	 * Backslash first, then the line separators — any other order would let an
	 * escaped value be read back ambiguously. No trimming, no case folding and
	 * explicitly no Unicode normalisation: normalising would rewrite Khmer, and
	 * the stored bytes are the evidence.
	 */
	private static String escape(String v) {
		if (v == null)
			return "";
		return v.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
	}
}
