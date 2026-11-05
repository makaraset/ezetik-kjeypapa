package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.service.ImageService;
import com.ezetik.kjeypapa.pdl.service.LosDocumentAssembler;

/**
 * Sambat's application has ONE {@code Doc_NID} slot and a Cambodian ID has two
 * sides; they asked for the two merged into a single image (2026-09-03).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LosDocumentAssemblerTest {

	@Mock ImageService imageService;
	@InjectMocks LosDocumentAssembler assembler;

	/** A solid JPEG of the given size, standing in for a photographed card. */
	private byte[] jpeg(int w, int h, Color color) throws Exception {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, w, h);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "jpg", out);
		return out.toByteArray();
	}

	private void stored(String ref, byte[] bytes) {
		Image i = new Image();
		i.setFileName(ref);
		i.setFileType("image/jpeg");
		i.setData(bytes);
		when(imageService.findByFileName(ref)).thenReturn(i);
	}

	private BufferedImage decode(String base64) throws Exception {
		return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
	}

	@Test
	@DisplayName("both sides are stacked into one image, neither squashed")
	void mergesBothSides() throws Exception {
		stored("front.jpg", jpeg(600, 380, Color.RED));
		stored("back.jpg", jpeg(300, 190, Color.BLUE)); // half size: must scale up, not distort

		var doc = assembler.mergedNid("front.jpg", "back.jpg", 23);
		BufferedImage merged = decode(doc.base64());

		assertThat(doc.fileName()).isEqualTo("NID-23.jpg");
		assertThat(merged.getWidth()).isEqualTo(600);
		// front 380 + gap + back scaled to 600 wide => 380 high, so ~772 with the gap
		assertThat(merged.getHeight()).isGreaterThan(760).isLessThan(790);
		// The top half is the front and the lower half the back: proof both are
		// actually drawn, rather than one overwriting the other.
		assertThat(new Color(merged.getRGB(300, 100)).getRed()).isGreaterThan(200);
		assertThat(new Color(merged.getRGB(300, 700)).getBlue()).isGreaterThan(200);
	}

	@Test
	@DisplayName("only a front? send it — a readable side beats failing the application")
	void frontOnly() throws Exception {
		stored("front.jpg", jpeg(600, 380, Color.RED));

		var doc = assembler.mergedNid("front.jpg", null, 23);

		assertThat(doc.base64()).isNotEmpty();
		assertThat(decode(doc.base64()).getHeight()).isEqualTo(380);
	}

	@Test
	@DisplayName("an undecodable side falls back to the front rather than throwing")
	void undecodableBack() throws Exception {
		stored("front.jpg", jpeg(600, 380, Color.RED));
		// e.g. a PDF stored under an image ref — ImageIO returns null for it
		stored("back.pdf", "%PDF-1.4 not an image".getBytes());

		var doc = assembler.mergedNid("front.jpg", "back.pdf", 23);

		assertThat(decode(doc.base64()).getHeight()).isEqualTo(380);
	}
}
