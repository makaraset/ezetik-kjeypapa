package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;

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
		ReflectionTestUtils.setField(r, "consentTextKm", KM_TEXT);
		ReflectionTestUtils.setField(r, "consentTextEn", "I consent to a CBC credit enquiry.");
		ReflectionTestUtils.setField(r, "textVersion", "v1-test");
		return r;
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
		byte[] png = renderer().render(loan(), person());

		BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(img).isNotNull();
		assertThat(img.getWidth()).isEqualTo(1000);
		// Tall enough to hold the header, the identity block and the wrapped
		// consent — a collapsed layout would still "render" without this.
		assertThat(img.getHeight()).isGreaterThan(500);

		String dir = System.getProperty("pdl.dump.dir");
		if (dir != null) {
			Files.createDirectories(Path.of(dir));
			Files.write(Path.of(dir, "consent-form.png"), png);
		}
	}

	@Test
	@DisplayName("the bundled Khmer font is present and actually shapes Khmer")
	void khmerFontIsUsable() throws Exception {
		// Without a Khmer-capable font the JVM draws .notdef boxes: it does not
		// throw, so the only signal is that the glyphs are missing.
		var font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,
				new File("src/main/resources/fonts/NotoSansKhmer-Regular.ttf"));
		assertThat(font.getFontName()).contains("Khmer");
		assertThat(font.canDisplayUpTo(KM_TEXT)).isEqualTo(-1); // every glyph present
	}

	@Test
	@DisplayName("a customer with no Khmer name still gets a form")
	void latinOnlyCustomer() throws Exception {
		PdlPersonalInfo pi = person();
		pi.setKhmerFamilyName(null);
		pi.setKhmerFirstName(null);

		byte[] png = renderer().render(loan(), pi);

		assertThat(ImageIO.read(new ByteArrayInputStream(png))).isNotNull();
	}
}
