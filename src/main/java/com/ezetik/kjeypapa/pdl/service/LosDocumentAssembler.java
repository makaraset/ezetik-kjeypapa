package com.ezetik.kjeypapa.pdl.service;

import java.util.Base64;

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
