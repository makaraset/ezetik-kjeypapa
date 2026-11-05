package com.ezetik.kjeypapa.pdl.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.service.ImageService;

/**
 * Turns a stored file reference into the {@code Doc_*} / {@code Doc_*_FileName}
 * pair SBF expects (raw base64, no {@code data:} prefix).
 *
 * <p>Reads the bytes straight from {@link ImageService}, deliberately NOT
 * through {@code /api/v1/file/show}: that endpoint serves a bundled
 * {@code notfound.jpg} placeholder instead of erroring, so a dangling
 * reference would ship a picture of nothing to Sambat in place of a customer's
 * ID — and it would look like a successful submission.
 */
@Component
public class LosDocumentAssembler {

	@Autowired
	private ImageService imageService;

	/** A document ready for the wire. */
	public record Doc(String base64, String fileName) {
		public static final Doc EMPTY = new Doc("", "");
	}

	/**
	 * @param ref     the stored file reference
	 * @param docType short label used to name the file for Sambat
	 * @param loanId  included in the file name so their console can trace it
	 * @throws LosSubmitException when the reference does not resolve — never
	 *         returns an empty document for a reference that was supposed to
	 *         exist, because a document-less application is worse than none.
	 */
	public Doc build(String ref, String docType, int loanId) {
		if (ref == null || ref.isBlank())
			return Doc.EMPTY;

		Image image = imageService.findByFileName(ref);
		if (image == null || image.getData() == null || image.getData().length == 0)
			throw new LosSubmitException("LOS_DOCUMENT_MISSING",
					"A required document could not be read: " + docType);

		return new Doc(Base64.getEncoder().encodeToString(image.getData()),
				docType + "-" + loanId + extensionOf(image));
	}

	/**
	 * Both sides of the ID card stacked into ONE image, for Sambat's single
	 * {@code Doc_NID} slot — they asked for the two merged rather than either
	 * side alone (2026-09-03).
	 *
	 * <p>Stacked vertically and centred on white, scaled to a common width so
	 * neither side is distorted. If only one side resolves it is returned
	 * as-is: a single readable side beats failing the whole application.
	 */
	public Doc mergedNid(String frontRef, String backRef, int loanId) {
		Doc front = build(frontRef, "NID", loanId);
		if (backRef == null || backRef.isBlank())
			return front;
		Doc back = build(backRef, "NID", loanId);
		if (front == Doc.EMPTY)
			return back;

		try {
			BufferedImage a = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(front.base64())));
			BufferedImage b = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(back.base64())));
			if (a == null || b == null)
				return front; // not decodable as an image (a PDF, say) — send the front

			int width = Math.max(a.getWidth(), b.getWidth());
			int aH = scaledHeight(a, width);
			int bH = scaledHeight(b, width);
			int gap = Math.max(8, width / 50);

			BufferedImage merged = new BufferedImage(width, aH + gap + bH, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = merged.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, merged.getWidth(), merged.getHeight());
			g.drawImage(a, 0, 0, width, aH, null);
			g.drawImage(b, 0, aH + gap, width, bH, null);
			g.dispose();

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(merged, "jpg", out);
			return new Doc(Base64.getEncoder().encodeToString(out.toByteArray()), "NID-" + loanId + ".jpg");
		} catch (Exception e) {
			// Never fail a submit over presentation: the front alone still
			// carries every printed field they read.
			return front;
		}
	}

	private static int scaledHeight(BufferedImage img, int width) {
		return Math.max(1, (int) Math.round(img.getHeight() * (width / (double) img.getWidth())));
	}

	/**
	 * Extension from the stored name, falling back to the recorded MIME type
	 * and finally to .jpg. Uses lastIndexOf so a name with several dots does
	 * not yield a bogus extension.
	 */
	private String extensionOf(Image image) {
		String name = image.getFileName();
		if (name != null) {
			int dot = name.lastIndexOf('.');
			if (dot > -1 && dot < name.length() - 1)
				return name.substring(dot).toLowerCase();
		}
		String type = image.getFileType() == null ? "" : image.getFileType().toLowerCase();
		if (type.contains("png"))
			return ".png";
		if (type.contains("pdf"))
			return ".pdf";
		return ".jpg";
	}
}
