package com.ezetik.kjeypapa.pdl.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;


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

	/**
	 * Sambat's current form reference. Copied onto a consent record when one is
	 * created; a form is then printed from the record, so an old consent keeps
	 * showing the reference it was filed under rather than today's.
	 */
	@Value("${pdl.cbc.form-reference:}")
	private String formReference;

	public String formReference() {
		return formReference;
	}

	/** The wording version a new consent record should be stamped with. */
	public String currentTextVersion() {
		return textVersion;
	}

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
	public byte[] renderPdf(CbcConsentData data) {
		try {
			return SimplePdfWriter.singleImagePage(renderImage(data));
		} catch (IOException e) {
			throw new LosSubmitException("LOS_CONSENT_RENDER", "Could not render the CBC consent form");
		}
	}

	/** The same document as a PNG, for viewing in the app. */
	public byte[] render(CbcConsentData data) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(renderImage(data), "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new LosSubmitException("LOS_CONSENT_RENDER", "Could not render the CBC consent form");
		}
	}

	private BufferedImage renderImage(CbcConsentData data) {
		// Every value is taken from the record, never from config or the clock:
		// the document and the record it belongs to are the same facts.
		int loanId = data.loanId();
		String version = data.textVersion();
		ZonedDateTime at = data.consentDate().atZone(KH);

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
		String khmerName = s(data.customerNameKm());
		String latinName = s(data.customerNameLatin());
		y = row(g, label, marginX, valueX, y, lineH, "ឈ្មោះអតិថិជន", khmerName.isEmpty() ? latinName : khmerName);
		y = row(g, label, marginX, valueX, y, lineH, "ជាអក្សរឡាតាំង", latinName);
		y = row(g, label, marginX, valueX, y, lineH, "លេខអត្តសញ្ញាណប័ណ្ណ", s(data.idNo()));
		y = row(g, label, marginX, valueX, y, lineH, "លេខសំណើ", String.valueOf(loanId));
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
		String formRef = s(data.formReference());
		if (notBlank(formRef)) {
			int fy = height - Math.round(MARGIN_BOTTOM_PT * DPI);
			draw(g, formRef, width - marginX - widthOf(g, formRef, footer), fy, footer);
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
	 * Draws one line, justified to {@code width} when asked.
	 *
	 * <p>Khmer cannot be justified by stretching spaces: it puts them at phrase
	 * boundaries, not between every word, so a line may have two or three gaps
	 * to absorb all the slack and stretching them tears holes through the
	 * paragraph. Word justifies Khmer by opening every cluster boundary
	 * slightly, and that is what this does — the line is shaped first, then the
	 * slack is spread across the boundaries between clusters.
	 *
	 * <p>The spreading deliberately skips zero-advance glyphs: those are the
	 * vowels, coeng subscripts and diacritics that sit on a base character, and
	 * moving one away from its base would break the syllable.
	 */
	private void drawLine(Graphics2D g, String line, int x, int y, Font font, int width, boolean justify) {
		List<GlyphVector> pieces = shape(g, line, font);
		double natural = 0;
		int stretchPoints = 0;
		for (GlyphVector gv : pieces) {
			natural += gv.getGlyphPosition(gv.getNumGlyphs()).getX();
			for (int i = 0; i < gv.getNumGlyphs(); i++)
				if (gv.getGlyphMetrics(i).getAdvanceX() > 0)
					stretchPoints++;
		}
		stretchPoints--; // no gap after the final cluster

		double extra = justify ? width - natural : 0;
		// Never squeeze, and never open gaps so wide the line reads as broken:
		// past roughly a character's width the paragraph looks worse justified.
		double perGap = extra <= 0 || stretchPoints <= 0 ? 0
				: Math.min(extra / stretchPoints, font.getSize2D() * 0.22);

		double cx = x;
		for (GlyphVector gv : pieces) {
			double offset = 0;
			int n = gv.getNumGlyphs();
			for (int i = 0; i < n; i++) {
				Point2D p = gv.getGlyphPosition(i);
				gv.setGlyphPosition(i, new Point2D.Double(p.getX() + offset, p.getY()));
				if (gv.getGlyphMetrics(i).getAdvanceX() > 0)
					offset += perGap;
			}
			g.drawGlyphVector(gv, (float) cx, y);
			cx += gv.getGlyphPosition(n).getX() + offset;
		}
	}

	/**
	 * Shapes a line into glyph vectors, one per stretch of characters the font
	 * can draw, so a fallback face covers anything it cannot. Shaping per
	 * stretch rather than per character keeps each Khmer syllable in one piece.
	 */
	private List<GlyphVector> shape(Graphics2D g, String text, Font font) {
		List<GlyphVector> out = new ArrayList<>();
		FontRenderContext frc = g.getFontRenderContext();
		int i = 0;
		while (i < text.length()) {
			boolean ok = font.canDisplay(text.charAt(i));
			int j = i;
			while (j < text.length() && font.canDisplay(text.charAt(j)) == ok)
				j++;
			char[] run = text.substring(i, j).toCharArray();
			out.add((ok ? font : fallback(font)).layoutGlyphVector(frc, run, 0, run.length,
					Font.LAYOUT_LEFT_TO_RIGHT));
			i = j;
		}
		return out;
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

	/** Measured from the shaped glyphs, exactly as the line is drawn. */
	private int widthOf(Graphics2D g, String text, Font font) {
		double w = 0;
		for (GlyphVector gv : shape(g, text, font))
			w += gv.getGlyphPosition(gv.getNumGlyphs()).getX();
		return (int) Math.ceil(w);
	}

	private static Font fallback(Font like) {
		return new Font(Font.SANS_SERIF, like.getStyle(), like.getSize());
	}

	/** Zero-width space — Khmer's word separator, invisible when drawn. */
	private static final char ZWSP = '\u200B';

	/**
	 * Greedy wrap that breaks between WORDS.
	 *
	 * <p>Khmer does not put a space between every word; it marks the boundaries
	 * with zero-width spaces, and Sambat's document carries them. Those, plus
	 * ordinary spaces, are the break opportunities — so a line ends where a word
	 * ends, as it does on their form, rather than mid-word.
	 *
	 * <p>A single word wider than the measure is still broken between clusters:
	 * running off the page would be worse than an ugly break, and this is the
	 * only case where a word is split.
	 */
	private List<String> wrap(Graphics2D g, String text, Font font, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();

		for (String word : words(text)) {
			boolean spaced = word.startsWith(" ");
			String w = spaced ? word.substring(1) : word;
			if (w.isEmpty())
				continue;
			String candidate = line.isEmpty() ? w : line + (spaced ? " " : "") + w;
			if (widthOf(g, candidate, font) <= maxWidth) {
				line = new StringBuilder(candidate);
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder();
			}
			if (widthOf(g, w, font) <= maxWidth) {
				line = new StringBuilder(w);
			} else {
				line = new StringBuilder(breakByCluster(g, w, font, maxWidth, lines));
			}
		}
		if (!line.isEmpty())
			lines.add(line.toString());
		return lines;
	}

	/**
	 * Splits on the break opportunities, keeping a leading space on a word that
	 * followed a real space so it is still drawn with one. A zero-width space
	 * is a break opportunity only — it never reaches the page.
	 */
	private static List<String> words(String text) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean spaceBefore = false;
		for (char c : text.trim().toCharArray()) {
			if (c == ZWSP || Character.isWhitespace(c)) {
				if (cur.length() > 0) {
					out.add((spaceBefore ? " " : "") + cur);
					cur.setLength(0);
				}
				spaceBefore = c != ZWSP;
				continue;
			}
			cur.append(c);
		}
		if (cur.length() > 0)
			out.add((spaceBefore ? " " : "") + cur);
		return out;
	}

	/** Last resort for a single word wider than the measure. */
	private String breakByCluster(Graphics2D g, String word, Font font, int maxWidth, List<String> lines) {
		BreakIterator clusters = BreakIterator.getCharacterInstance(new Locale("km"));
		clusters.setText(word);
		StringBuilder chunk = new StringBuilder();
		int start = clusters.first();
		for (int end = clusters.next(); end != BreakIterator.DONE; start = end, end = clusters.next()) {
			String cluster = word.substring(start, end);
			if (chunk.length() > 0 && widthOf(g, chunk + cluster, font) > maxWidth) {
				lines.add(chunk.toString());
				chunk = new StringBuilder();
			}
			chunk.append(cluster);
		}
		return chunk.toString();
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
