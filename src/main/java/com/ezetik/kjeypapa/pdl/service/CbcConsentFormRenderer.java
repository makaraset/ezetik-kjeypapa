package com.ezetik.kjeypapa.pdl.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;

/**
 * Renders the {@code Doc_ECBCConsentForm} document from the consent the
 * customer actually gave in-app (the CBC page checkbox, V8 screen 15) — the
 * consent text, their identity, the deterministic consent reference and a
 * timestamp.
 *
 * <p><b>In Khmer</b>, per Sambat (2026-09-03): this is a consent a Cambodian
 * customer is held to, so it is rendered in the language they agreed in. Java
 * cannot shape Khmer with the JVM's default fonts, so Noto Sans Khmer is
 * bundled at {@code resources/fonts} (SIL Open Font License 1.1, which permits
 * redistribution); JDK 9+ shapes complex scripts with HarfBuzz, so the coeng
 * stacking and vowel placement come out correctly.
 *
 * <p>The Khmer wording is our interim translation. Sambat is sending final
 * EN+KM legal text; when it lands, replace {@code pdl.cbc.consent-text-km} and
 * bump {@code pdl.cbc.text-version} so filed consents stay attributable to the
 * exact wording each customer saw.
 */
@Component
public class CbcConsentFormRenderer {

	private static final int WIDTH = 1000;
	private static final int MARGIN = 70;
	private static final ZoneId KH = ZoneId.of("Asia/Phnom_Penh");

	/** Khmer consent wording — the same text the app shows on the CBC page. */
	@Value("${pdl.cbc.consent-text-km:ស្របតាមប្រកាសស្តីពីរបាយការណ៍ឥណទាន ខ្ញុំសូមផ្តល់ការយល់ព្រមឲ្យ សម្បត្តិ ហ្វាយនែន ម.ក ទទួល ពិនិត្យ និងប្រើប្រាស់ព័ត៌មានឥណទានរបស់ខ្ញុំពី ក្រុមហ៊ុន ក្រេឌីត ប្យួរ៉ូ (ខេមបូឌា) (CBC) សម្រាប់គោលបំណងវាយតម្លៃពាក្យសុំកម្ចីនេះ និងបញ្ជូនព័ត៌មានឥណទានរបស់ខ្ញុំទៅ CBC តាមតម្រូវការនៃរបាយការណ៍ឥណទាន។ ខ្ញុំយល់ថាមានកម្រៃស៊ើបអង្កេត CBC ហើយការយល់ព្រមនេះត្រូវបានកត់ត្រាជាមួយពាក្យសុំរបស់ខ្ញុំ។}")
	private String consentTextKm;

	/** English wording, kept beneath the Khmer for Sambat's own reviewers. */
	@Value("${pdl.cbc.consent-text:I consent to Sambat Finance conducting a credit enquiry with the Credit Bureau of Cambodia (CBC) for the purpose of assessing this loan application, in accordance with the Prakas on Credit Reporting.}")
	private String consentTextEn;

	@Value("${pdl.cbc.text-version:v1}")
	private String textVersion;

	/**
	 * The exact Khmer wording rendered into the form. The consent record hashes
	 * THIS string, so the hash and the document can never describe different
	 * text — reading the property in two places once produced a hash of the
	 * empty string, which proves nothing.
	 */
	public String consentTextKm() {
		return consentTextKm;
	}

	/** Loaded once: creating a Font from bytes on every submit is wasteful. */
	private volatile Font khmerBase;

	/**
	 * The consent as a PDF — what Sambat asked for (2026-09-04); they take the
	 * structured record alongside it.
	 */
	public byte[] renderPdf(PaydayLoan loan, PdlPersonalInfo pi) {
		try {
			return SimplePdfWriter.singleImagePage(renderImage(loan, pi));
		} catch (IOException e) {
			throw new LosSubmitException("LOS_CONSENT_RENDER", "Could not render the CBC consent form");
		}
	}

	/** The same document as a PNG (kept for previews and tests). */
	public byte[] render(PaydayLoan loan, PdlPersonalInfo pi) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(renderImage(loan, pi), "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new LosSubmitException("LOS_CONSENT_RENDER", "Could not render the CBC consent form");
		}
	}

	private BufferedImage renderImage(PaydayLoan loan, PdlPersonalInfo pi) {
		int loanId = loan.getId() == null ? 0 : loan.getId();
		String customer = (s(pi.getLatinFamilyName()) + " " + s(pi.getLatinFirstName())).trim();
		String khmerName = (s(pi.getKhmerFamilyName()) + " " + s(pi.getKhmerFirstName())).trim();
		String ref = "CBC-" + loanId + "-" + textVersion;
		// The stamped consent time, not "now": a customer re-opening the form
		// months later must see the moment they consented, and the document
		// they view must be the document we filed.
		ZonedDateTime at = loan.getCbcConsentDate() != null
				? loan.getCbcConsentDate().atZone(KH)
				: ZonedDateTime.now(KH);
		String when = at.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));

		Font km = khmer();
		Font title = km.deriveFont(Font.BOLD, 34f);
		Font label = km.deriveFont(Font.BOLD, 22f);
		Font body = km.deriveFont(Font.PLAIN, 24f);
		Font small = km.deriveFont(Font.PLAIN, 18f);
		Font smallEn = new Font(Font.SANS_SERIF, Font.PLAIN, 17);

		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D pg = probe.createGraphics();
		List<String> kmLines = wrap(consentTextKm, pg, body, WIDTH - 2 * MARGIN);
		List<String> enLines = wrap(consentTextEn, pg, smallEn, WIDTH - 2 * MARGIN);
		pg.dispose();

		int height = 400 + kmLines.size() * 38 + enLines.size() * 24 + 150;
		BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, WIDTH, height);
		g.setColor(new Color(0x1A, 0x35, 0x7A));
		g.fillRect(0, 0, WIDTH, 8);

		int y = 90;
		g.setColor(Color.BLACK);
		drawMixed(g, "ការយល់ព្រមឲ្យធ្វើការស៊ើបអង្កេតឥណទាន (CBC)", MARGIN, y, title);
		y += 42;
		g.setColor(Color.DARK_GRAY);
		drawMixed(g, "Kjey PAPA · សម្បត្តិ ហ្វាយនែន ម.ក — ឥណទានបើកប្រាក់ខែ", MARGIN, y, small);
		y += 52;

		g.setColor(Color.BLACK);
		y = kv(g, label, body, "ឈ្មោះអតិថិជន", khmerName.isEmpty() ? customer : khmerName, y);
		if (!khmerName.isEmpty() && !customer.isEmpty())
			y = kv(g, label, body, "ជាអក្សរឡាតាំង", customer, y);
		y = kv(g, label, body, "លេខអត្តសញ្ញាណប័ណ្ណ", s(pi.getIdNo()), y);
		y = kv(g, label, body, "លេខពាក្យសុំ", "#" + loanId, y);
		y = kv(g, label, body, "លេខយោង", ref, y);
		y = kv(g, label, body, "កាលបរិច្ឆេទ", when, y);
		y += 28;

		for (String line : kmLines) {
			drawMixed(g, line, MARGIN, y, body);
			y += 38;
		}

		y += 26;
		g.setFont(smallEn);
		g.setColor(Color.GRAY);
		for (String line : enLines) {
			g.drawString(line, MARGIN, y);
			y += 24;
		}

		y += 30;
		g.setColor(Color.DARK_GRAY);
		drawMixed(g, "បានយល់ព្រមតាមប្រព័ន្ធអេឡិចត្រូនិកក្នុងកម្មវិធី Kjey PAPA (កំណែអត្ថបទ " + textVersion + ")។",
				MARGIN, y, small);
		g.dispose();
		return img;
	}

	/**
	 * Noto Sans Khmer from the classpath. Falls back to the platform sans only
	 * if the bundled font cannot be read — that renders Khmer as empty boxes,
	 * so it is a last resort to keep a submit alive rather than an acceptable
	 * output.
	 */
	private Font khmer() {
		Font f = khmerBase;
		if (f != null)
			return f;
		synchronized (this) {
			if (khmerBase == null) {
				try (InputStream in = new ClassPathResource("fonts/NotoSansKhmer-Regular.ttf").getInputStream()) {
					khmerBase = Font.createFont(Font.TRUETYPE_FONT, in);
				} catch (IOException | FontFormatException e) {
					khmerBase = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
				}
			}
			return khmerBase;
		}
	}

	private static int kv(Graphics2D g, Font label, Font body, String k, String v, int y) {
		drawMixed(g, k, MARGIN, y, label);
		drawMixed(g, v, MARGIN + 320, y, body);
		return y + 40;
	}

	private static boolean isKhmer(char c) {
		return (c >= 0x1780 && c <= 0x17FF) || (c >= 0x19E0 && c <= 0x19FF);
	}

	/** Spaces and punctuation belong to whichever run they sit in. */
	private static boolean isNeutral(char c) {
		return c == ' ' || c == '\t';
	}

	/**
	 * Draws a mixed Khmer/Latin string run by run.
	 *
	 * <p>Noto Sans Khmer contains Khmer only — no Latin letters and, critically,
	 * <b>no digits</b>. Drawing the whole line with it turned the customer's ID
	 * number, the consent reference and the date into empty boxes (caught by
	 * eye on the first render). Each run is therefore drawn with the face that
	 * actually has its glyphs: the bundled Khmer font, or the JVM's own sans
	 * for everything else.
	 */
	private static void drawMixed(Graphics2D g, String text, int x, int y, Font khmerFont) {
		Font latinFont = new Font(Font.SANS_SERIF, khmerFont.getStyle(), khmerFont.getSize());
		int cx = x;
		int i = 0;
		while (i < text.length()) {
			boolean km = runIsKhmer(text, i);
			int j = runEnd(text, i, km);
			String run = text.substring(i, j);
			Font f = km ? khmerFont : latinFont;
			g.setFont(f);
			g.drawString(run, cx, y);
			cx += g.getFontMetrics(f).stringWidth(run);
			i = j;
		}
	}

	/** Rendered width of a mixed string under the same per-run rule. */
	private static int widthMixed(Graphics2D g, String text, Font khmerFont) {
		Font latinFont = new Font(Font.SANS_SERIF, khmerFont.getStyle(), khmerFont.getSize());
		int w = 0;
		int i = 0;
		while (i < text.length()) {
			boolean km = runIsKhmer(text, i);
			int j = runEnd(text, i, km);
			w += g.getFontMetrics(km ? khmerFont : latinFont).stringWidth(text.substring(i, j));
			i = j;
		}
		return w;
	}

	private static boolean runIsKhmer(String text, int from) {
		for (int k = from; k < text.length(); k++)
			if (!isNeutral(text.charAt(k)))
				return isKhmer(text.charAt(k));
		return false;
	}

	private static int runEnd(String text, int from, boolean khmerRun) {
		int j = from;
		while (j < text.length()) {
			char c = text.charAt(j);
			if (!isNeutral(c) && isKhmer(c) != khmerRun)
				break;
			j++;
		}
		return j;
	}

	/**
	 * Greedy wrap by rendered width. Khmer does not use spaces between words,
	 * so a space-only split would overflow the page; when a "word" is itself
	 * wider than the line it is broken by character.
	 */
	private static List<String> wrap(String text, Graphics2D g, Font font, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (widthMixed(g, candidate, font) <= maxWidth) {
				line = new StringBuilder(candidate);
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder();
			}
			if (widthMixed(g, word, font) <= maxWidth) {
				line = new StringBuilder(word);
			} else {
				StringBuilder chunk = new StringBuilder();
				for (char c : word.toCharArray()) {
					if (chunk.length() > 0 && widthMixed(g, chunk.toString() + c, font) > maxWidth) {
						lines.add(chunk.toString());
						chunk = new StringBuilder();
					}
					chunk.append(c);
				}
				line = chunk;
			}
		}
		if (!line.isEmpty())
			lines.add(line.toString());
		return lines;
	}

	private static String s(String v) {
		return v == null ? "" : v;
	}
}
