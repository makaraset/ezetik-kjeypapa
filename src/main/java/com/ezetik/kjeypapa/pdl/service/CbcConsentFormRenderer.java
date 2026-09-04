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
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;

/**
 * Renders Sambat's CBC consent form — the document filed as
 * {@code Doc_ECBCConsentForm} and shown back to the customer.
 *
 * <p>Laid out to their own template (CBCConsentforMobileApp_KH_Final,
 * 2026-09-04): A4, <b>Khmer OS Content</b>, 11pt justified body, bold
 * underlined heading, the borrower block, and their form reference in the
 * footer. Their page geometry is followed exactly — 0.59in side margins,
 * 0.30in top, 0.49in bottom.
 *
 * <p>Text is drawn run by run against what the font can actually display,
 * rather than by Unicode range, because Khmer OS Content's coverage varies by
 * build: the 2010 release carries no Latin letters at all, and drawing a whole
 * line in it printed the customer's ID number as empty boxes. The fallback
 * costs nothing when the font does cover the character.
 *
 * <p>The wording used is the version the application was filed under, so a
 * document shown back to a customer always matches the record and the hash
 * filed against it.
 */
@Component
public class CbcConsentFormRenderer {

	/** A4 in points, with their margins from the template. */
	private static final float PAGE_W_PT = 595.28f;
	private static final float PAGE_H_PT = 841.89f;
	private static final float MARGIN_X_PT = 42.5f; // 851 twips
	private static final float MARGIN_TOP_PT = 21.3f; // 426 twips
	private static final float MARGIN_BOTTOM_PT = 35.5f; // 709 twips

	/** Rendered at 150dpi so the Khmer stays crisp in print. */
	private static final float DPI = 150f / 72f;

	private static final ZoneId KH = ZoneId.of("Asia/Phnom_Penh");

	@Value("${pdl.cbc.consent-text-km:}")
	private String consentTextKmOverride;

	@Value("${pdl.cbc.consent-text-km-file:cbc/consent-km.txt}")
	private String consentTextKmFile;

	@Value("${pdl.cbc.text-version:v1}")
	private String textVersion;

	/** Sambat's own reference for this form, printed as its footer. */
	@Value("${pdl.cbc.form-reference:}")
	private String formReference;

	private final Map<String, String> textCache = new ConcurrentHashMap<>();
	private volatile Font khmerRegular;
	private volatile Font khmerBold;

	public String consentTextKm() {
		return consentTextKm(null);
	}

	/**
	 * The wording for a given text version.
	 *
	 * <p>A consent filed under an older version must re-render as the customer
	 * saw it, not as today's wording — otherwise the document we show back
	 * silently disagrees with the hash recorded against it. Versions live
	 * beside the current text as {@code cbc/consent-km-<version>.txt}; an
	 * unknown version falls back to the current wording, the best we can do for
	 * anything filed before the files were versioned.
	 */
	public String consentTextKm(String version) {
		if (consentTextKmOverride != null && !consentTextKmOverride.isBlank())
			return consentTextKmOverride;
		String key = version == null || version.isBlank() ? consentTextKmFile
				: "cbc/consent-km-" + version.trim() + ".txt";
		String hit = textCache.get(key);
		if (hit != null)
			return hit;
		String text = read(key);
		if (text == null && !key.equals(consentTextKmFile))
			text = read(consentTextKmFile);
		if (text == null)
			// Never render a consent form with no consent on it.
			throw new LosSubmitException("LOS_CONSENT_TEXT_MISSING",
					"The consent wording could not be read.");
		textCache.put(key, text);
		return text;
	}

	/** The consent as a PDF — the format Sambat file. */
	public byte[] renderPdf(PaydayLoan loan, PdlPersonalInfo pi) {
		try {
			return SimplePdfWriter.singleImagePage(renderImage(loan, pi));
		} catch (IOException e) {
			throw new LosSubmitException("LOS_CONSENT_RENDER", "Could not render the CBC consent form");
		}
	}

	/** The same document as a PNG, for viewing in the app. */
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
		// The stored version and reference, not today's config: this document
		// must match the record filed against it.
		String version = notBlank(loan.getCbcConsentTextVersion()) ? loan.getCbcConsentTextVersion() : textVersion;
		String ref = notBlank(loan.getCbcConsentRef()) ? loan.getCbcConsentRef() : "CBC-" + loanId + "-" + version;
		ZonedDateTime at = loan.getCbcConsentDate() != null ? loan.getCbcConsentDate().atZone(KH)
				: ZonedDateTime.now(KH);

		int width = Math.round(PAGE_W_PT * DPI);
		int height = Math.round(PAGE_H_PT * DPI);
		int marginX = Math.round(MARGIN_X_PT * DPI);
		int contentW = width - 2 * marginX;

		Font heading = khmer(true).deriveFont(12f * DPI);
		Font label = khmer(false).deriveFont(11f * DPI);
		Font body = khmer(false).deriveFont(11f * DPI);
		Font footer = khmer(false).deriveFont(7.5f * DPI);

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.BLACK);

		int lineH = Math.round(11f * 1.55f * DPI);
		int y = Math.round(MARGIN_TOP_PT * DPI) + Math.round(20f * DPI);

		// Heading — bold and underlined, as on their form.
		String title = "ការយល់ព្រមរបស់អ្នកស្នើសុំខ្ចីប្រាក់៖";
		draw(g, title, marginX, y, heading);
		g.fillRect(marginX, y + Math.round(4f * DPI), widthOf(g, title, heading),
				Math.max(1, Math.round(1.1f * DPI)));
		y += Math.round(lineH * 1.9f);

		// Borrower block — their labels, in their order.
		int valueX = marginX + Math.round(150f * DPI);
		String khmerName = (s(pi.getKhmerFamilyName()) + " " + s(pi.getKhmerFirstName())).trim();
		String latinName = (s(pi.getLatinFamilyName()) + " " + s(pi.getLatinFirstName())).trim();
		y = row(g, label, marginX, valueX, y, lineH, "ឈ្មោះអតិថិជន", khmerName.isEmpty() ? latinName : khmerName);
		y = row(g, label, marginX, valueX, y, lineH, "ជាអក្សរឡាតាំង", latinName);
		y = row(g, label, marginX, valueX, y, lineH, "លេខអត្តសញ្ញាណប័ណ្ណ", s(pi.getIdNo()));
		y = row(g, label, marginX, valueX, y, lineH, "លេខសំណើ", String.valueOf(loanId));
		y = row(g, label, marginX, valueX, y, lineH, "លេខយោង", ref);
		y = row(g, label, marginX, valueX, y, lineH, "កាលបរិច្ឆេទ",
				at.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
		y += Math.round(lineH * 1.1f);

		// Body — justified, blank line between paragraphs, as in their template.
		for (String paragraph : consentTextKm(version).trim().split("\\n\\s*\\n")) {
			List<String> lines = wrap(g, paragraph.trim(), body, contentW);
			for (int i = 0; i < lines.size(); i++) {
				// Their template justifies every line but a paragraph's last.
				drawLine(g, lines.get(i), marginX, y, body, contentW, i < lines.size() - 1);
				y += lineH;
			}
			y += Math.round(lineH * 0.6f);
		}

		// Footer — their form reference, bottom right.
		if (notBlank(formReference)) {
			int fy = height - Math.round(MARGIN_BOTTOM_PT * DPI);
			draw(g, formReference, width - marginX - widthOf(g, formReference, footer), fy, footer);
		}

		g.dispose();
		return img;
	}

	private int row(Graphics2D g, Font f, int labelX, int valueX, int y, int lineH, String label, String value) {
		draw(g, label, labelX, y, f);
		draw(g, value, valueX, y, f);
		return y + lineH;
	}

	// ----- text drawing -----

	/**
	 * Draws one line, optionally justified to {@code width} by stretching the
	 * gaps between space-separated runs. Khmer is not written with a space
	 * between every word, so a line without spaces is left as it is rather than
	 * letter-spaced into something unreadable.
	 */
	private void drawLine(Graphics2D g, String line, int x, int y, Font font, int width, boolean justify) {
		String[] parts = line.split(" ");
		if (!justify || parts.length < 2) {
			draw(g, line, x, y, font);
			return;
		}
		int natural = 0;
		for (String p : parts)
			natural += widthOf(g, p, font);
		int gap = (width - natural) / (parts.length - 1);
		// Khmer puts spaces at phrase boundaries, not between every word, so a
		// line can have two or three gaps to absorb the whole slack. Stretching
		// those tears holes in the paragraph; past a few spaces' worth it reads
		// better ragged, which is what Word's Khmer justification avoids by
		// also stretching between characters.
		int spaceW = g.getFontMetrics(font).stringWidth(" ");
		if (gap <= 0 || gap > spaceW * 4) {
			draw(g, line, x, y, font);
			return;
		}
		int cx = x;
		for (String part : parts) {
			draw(g, part, cx, y, font);
			cx += widthOf(g, part, font) + gap;
		}
	}

	/**
	 * Draws text run by run, switching to a fallback face for any character the
	 * Khmer font cannot show. Khmer OS Content has no Latin letters, so without
	 * this the customer's name and the form reference print as empty boxes.
	 */
	private void draw(Graphics2D g, String text, int x, int y, Font font) {
		int cx = x;
		int i = 0;
		while (i < text.length()) {
			boolean ok = font.canDisplay(text.charAt(i));
			int j = i;
			while (j < text.length() && font.canDisplay(text.charAt(j)) == ok)
				j++;
			String run = text.substring(i, j);
			Font f = ok ? font : fallback(font);
			g.setFont(f);
			g.drawString(run, cx, y);
			cx += g.getFontMetrics(f).stringWidth(run);
			i = j;
		}
	}

	/** Measured exactly the way {@link #draw} paints it. */
	private int widthOf(Graphics2D g, String text, Font font) {
		int w = 0;
		int i = 0;
		while (i < text.length()) {
			boolean ok = font.canDisplay(text.charAt(i));
			int j = i;
			while (j < text.length() && font.canDisplay(text.charAt(j)) == ok)
				j++;
			w += g.getFontMetrics(ok ? font : fallback(font)).stringWidth(text.substring(i, j));
			i = j;
		}
		return w;
	}

	private static Font fallback(Font like) {
		return new Font(Font.SANS_SERIF, like.getStyle(), like.getSize());
	}

	/** Greedy wrap, measured the same way it is drawn. */
	private List<String> wrap(Graphics2D g, String text, Font font, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (widthOf(g, candidate, font) <= maxWidth) {
				line = new StringBuilder(candidate);
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder();
			}
			if (widthOf(g, word, font) <= maxWidth) {
				line = new StringBuilder(word);
			} else {
				// A single Khmer run can be longer than the measure; break it by
				// character rather than let it run off the page.
				StringBuilder chunk = new StringBuilder();
				for (char c : word.toCharArray()) {
					if (chunk.length() > 0 && widthOf(g, chunk.toString() + c, font) > maxWidth) {
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

	// ----- resources -----

	/**
	 * Khmer OS Content — Sambat's own font, and specifically the 2007 v1.10
	 * build.
	 *
	 * <p>The later "v6.00 2010" build of the same family <b>renders Khmer
	 * wrongly under Java</b>: subscripts stop stacking and vowels land in the
	 * wrong place, silently, because Java's shaper does not drive its OpenType
	 * tables. The separately-shipped Content-Bold file has the same fault, so
	 * bold is synthesised from the regular instead — verified to shape
	 * correctly. If this font is ever replaced, render a page and read it
	 * before trusting it: nothing throws when the shaping is wrong.
	 */
	private Font khmer(boolean bold) {
		Font cached = bold ? khmerBold : khmerRegular;
		if (cached != null)
			return cached;
		synchronized (this) {
			Font base = khmerRegular;
			if (base == null) {
				try (InputStream in = new ClassPathResource("fonts/KhmerOSContent.ttf").getInputStream()) {
					base = Font.createFont(Font.TRUETYPE_FONT, in);
				} catch (IOException | FontFormatException e) {
					// Losing the Khmer face prints Khmer as boxes: a last resort
					// to keep a submit alive, never an acceptable result.
					base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
				}
				khmerRegular = base;
			}
			if (!bold)
				return base;
			khmerBold = base.deriveFont(Font.BOLD);
			return khmerBold;
		}
	}

	private static String read(String classpathFile) {
		try (InputStream in = new ClassPathResource(classpathFile).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
		} catch (IOException e) {
			return null;
		}
	}

	private static boolean notBlank(String v) {
		return v != null && !v.isBlank();
	}

	private static String s(String v) {
		return v == null ? "" : v;
	}
}
