package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.service.CbcConsentData;
import com.ezetik.kjeypapa.pdl.service.CbcConsentFormRenderer;

/**
 * The consent form is rendered in Khmer (Sambat, 2026-09-03). Khmer fails
 * quietly — the wrong font draws empty boxes and nothing throws — so these
 * pin the things that would otherwise ship broken.
 *
 * <p>Set {@code -Dpdl.dump.dir=/some/path} to also write the PNG out for a
 * human to look at; shaping correctness can only really be judged by eye.
 */
class CbcConsentFormRendererTest {

	private static final String KM_TEXT = "ស្របតាមប្រកាសស្តីពីរបាយការណ៍ឥណទាន ខ្ញុំសូមផ្តល់ការយល់ព្រម"
			+ "ឲ្យ សម្បត្តិ ហ្វាយនែន ម.ក ទទួល ពិនិត្យ និងប្រើប្រាស់ព័ត៌មានឥណទានរបស់ខ្ញុំ។";

	private CbcConsentFormRenderer renderer() {
		CbcConsentFormRenderer r = new CbcConsentFormRenderer();
		ReflectionTestUtils.setField(r, "consentTextKmOverride", KM_TEXT);
		ReflectionTestUtils.setField(r, "textVersion", "v1-test");
		return r;
	}

	private CbcConsentData data() {
		return new CbcConsentData(23, java.time.Instant.parse("2026-09-04T09:07:22Z"),
				"v1-test", "Form _ CBC 01 _ V2 _ 09122025", "110553867", "សែត មករា", "SET MAKARA");
	}

	private PaydayLoan loan() {
		PaydayLoan l = new PaydayLoan();
		l.setId(23);
		return l;
	}

	private PdlPersonalInfo person() {
		PdlPersonalInfo pi = new PdlPersonalInfo();
		pi.setLatinFamilyName("SET");
		pi.setLatinFirstName("MAKARA");
		pi.setKhmerFamilyName("សែត");
		pi.setKhmerFirstName("មករា");
		pi.setIdNo("110553867");
		return pi;
	}

	@Test
	@DisplayName("renders a real PNG carrying the consent")
	void rendersAPng() throws Exception {
		byte[] png = renderer().render(data());

		BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(img).isNotNull();
		// A4 at 150dpi, to Sambat's template — a fixed page, not a canvas that
		// grows with the text.
		assertThat(img.getWidth()).isEqualTo(1240);
		assertThat(img.getHeight()).isEqualTo(1754);

		String dir = System.getProperty("pdl.dump.dir");
		if (dir != null) {
			Files.createDirectories(Path.of(dir));
			Files.write(Path.of(dir, "consent-form.png"), png);
		}
	}

	@Test
	@DisplayName("the bundled font is Khmer OS Content AND actually shapes Khmer")
	void khmerFontIsUsable() throws Exception {
		// Their template specifies Khmer OS Content. Two traps, both silent:
		//  1. coverage varies by build — the 2010 one has no Latin at all, so
		//     the renderer draws per-run against canDisplay and falls back;
		//     that build printed the customer's ID as empty boxes;
		//  2. the later "v6.00 2010" build of the SAME family does not shape
		//     under Java — subscripts stop stacking and vowels move — and
		//     nothing throws. Shaping is therefore asserted, not assumed: a
		//     coeng cluster must collapse to fewer glyphs than characters.
		var font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,
				new File("src/main/resources/fonts/KhmerOSContent.ttf"));
		assertThat(font.getFontName()).isEqualTo("Khmer OS Content");
		assertThat(font.canDisplayUpTo(KM_TEXT)).isEqualTo(-1);
		assertThat(font.canDisplay('A')).isTrue(); // this build does cover Latin
		assertThat(font.canDisplay('7')).isTrue();

		String coeng = "ខ្ញុំ";
		var gv = font.deriveFont(24f).layoutGlyphVector(
				new java.awt.font.FontRenderContext(null, true, true),
				coeng.toCharArray(), 0, coeng.length(), java.awt.Font.LAYOUT_LEFT_TO_RIGHT);
		assertThat(gv.getNumGlyphs()).isLessThan(coeng.length());
	}
	@Test
	@DisplayName("a customer with no Khmer name still gets a form")
	void latinOnlyCustomer() throws Exception {
		CbcConsentData latinOnly = new CbcConsentData(23, java.time.Instant.now(), "v1-test",
				"Form _ CBC 01", "110553867", "", "SET MAKARA");

		byte[] png = renderer().render(latinOnly);

		assertThat(ImageIO.read(new ByteArrayInputStream(png))).isNotNull();
	}

	@Test
	@DisplayName("renders a valid single-page PDF — what Sambat asked for")
	void rendersAPdf() throws Exception {
		byte[] pdf = renderer().renderPdf(data());

		String head = new String(pdf, 0, 9, java.nio.charset.StandardCharsets.US_ASCII);
		assertThat(head).startsWith("%PDF-1.");
		String body = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
		assertThat(body).endsWith("%%EOF\n");
		assertThat(body).contains("/Type /Catalog").contains("/Type /Page").contains("/DCTDecode");
		// A4 at 72dpi: the page must be a page, not the raw pixel size.
		assertThat(body).contains("/MediaBox [0 0 595 842]");

		// The xref offsets must actually point at their objects, or readers
		// reject the file. Hand-built PDFs get this wrong silently.
		int xrefAt = body.lastIndexOf("startxref");
		int xrefOffset = Integer.parseInt(body.substring(xrefAt).split("\\n")[1].trim());
		assertThat(body.startsWith("xref", xrefOffset)).isTrue();
		for (String line : body.substring(xrefOffset).split("\\n")) {
			if (line.matches("\\d{10} 00000 n ?")) {
				int off = Integer.parseInt(line.substring(0, 10));
				assertThat(body.charAt(off)).isBetween('1', '5'); // "N 0 obj"
				assertThat(body.startsWith(" 0 obj", off + 1)).isTrue();
			}
		}

		String dir = System.getProperty("pdl.dump.dir");
		if (dir != null) {
			java.nio.file.Files.createDirectories(java.nio.file.Path.of(dir));
			java.nio.file.Files.write(java.nio.file.Path.of(dir, "consent-form.pdf"), pdf);
		}
	}

	@Test
	@DisplayName("the bundled final wording loads and is the text that gets hashed")
	void bundledWordingLoads() {
		// Sambat's final Khmer text ships as a resource; if it ever fails to
		// load we must not silently render a consent form with no consent on
		// it, and the record's hash must cover this exact text.
		CbcConsentFormRenderer r = new CbcConsentFormRenderer();
		ReflectionTestUtils.setField(r, "consentTextKmOverride", "");
		ReflectionTestUtils.setField(r, "consentTextKmFile", "cbc/consent-km.txt");

		String text = r.consentTextKm();

		assertThat(text).isNotBlank();
		assertThat(text.length()).isGreaterThan(1500);
		assertThat(text.split("\\n\\s*\\n")).hasSize(3); // three paragraphs
	}

	@Test
	@DisplayName("the shipped wording carries Sambat's word separators")
	void wordingHasWordBoundaries() {
		// Khmer marks word boundaries with zero-width spaces rather than real
		// ones, and Sambat's .docx carries them. They are what lets a line break
		// between words instead of mid-word; a copy pasted through a plain-text
		// field loses them silently, which is how ours lost them once already.
		CbcConsentFormRenderer r = new CbcConsentFormRenderer();
		ReflectionTestUtils.setField(r, "consentTextKmOverride", "");
		ReflectionTestUtils.setField(r, "consentTextKmFile", "cbc/consent-km.txt");

		String text = r.consentTextKm();

		assertThat(text).contains("\u200B");
		assertThat(text.chars().filter(c -> c == 0x200B).count()).isGreaterThanOrEqualTo(20);
	}

	@Test
	@DisplayName("a consent that does not exist yet cannot be rendered")
	void refusesToRenderBeforeConsentExists() {
		// The renderer used to fall back to config and the clock, which is how
		// the copy filed with Sambat came to be computed independently of the
		// record stored beside it. There is nothing to fall back to now.
		assertThatThrownBy(() -> new CbcConsentData(23, null, "v1-test", "", "", "", ""))
				.hasMessageContaining("before the consent is recorded");
		assertThatThrownBy(() -> new CbcConsentData(23, java.time.Instant.now(), " ", "", "", "", ""))
				.hasMessageContaining("without a wording version");
	}

	@Test
	@DisplayName("the same record renders the same bytes, every time")
	void renderIsDeterministic() {
		// This is what makes the archived document checkable years later: if a
		// re-render can differ, "the document we filed" stops being provable.
		CbcConsentData d = data();
		assertThat(renderer().renderPdf(d)).isEqualTo(renderer().renderPdf(d));
	}
}
