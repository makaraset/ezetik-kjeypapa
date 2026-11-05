package com.ezetik.kjeypapa.pdl.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Writes a single-page PDF wrapping one rendered image.
 *
 * <p>Deliberately hand-built rather than pulling in PDFBox: the only PDF we
 * produce is one page containing one picture, and the project builds offline
 * against a fixed local repository, so a new dependency is a bigger change than
 * the forty lines below. PDF carries JPEG natively ({@code DCTDecode}), so the
 * image is embedded as-is with no re-encoding of the pixels by us.
 *
 * <p>Not a general-purpose PDF library — it knows how to do exactly this.
 */
final class SimplePdfWriter {

	/** A4 at 72 dpi, the page size Cambodian offices print on. */
	private static final float PAGE_W = 595f;
	private static final float PAGE_H = 842f;
	private static final float MARGIN = 24f;

	private SimplePdfWriter() {
	}

	static byte[] singleImagePage(BufferedImage image) throws IOException {
		ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
		// A PDF image XObject has no alpha channel, so flatten to RGB first.
		BufferedImage rgb = image;
		if (image.getType() != BufferedImage.TYPE_INT_RGB) {
			rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
			rgb.createGraphics().drawImage(image, 0, 0, null);
		}
		ImageIO.write(rgb, "jpg", jpegBuf);
		byte[] jpeg = jpegBuf.toByteArray();

		// Fit inside the margins without distorting the page.
		float scale = Math.min((PAGE_W - 2 * MARGIN) / image.getWidth(),
				(PAGE_H - 2 * MARGIN) / image.getHeight());
		float drawW = image.getWidth() * scale;
		float drawH = image.getHeight() * scale;
		float x = (PAGE_W - drawW) / 2f;
		float y = PAGE_H - MARGIN - drawH; // PDF's origin is bottom-left

		String content = String.format("q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q%n", drawW, drawH, x, y);
		byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		List<Integer> offsets = new ArrayList<>();
		write(out, "%PDF-1.4\n");
		// A binary comment marks the file as binary for tools that sniff it.
		out.write(new byte[] { '%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n' });

		offsets.add(out.size());
		write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

		offsets.add(out.size());
		write(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

		offsets.add(out.size());
		write(out, String.format(
				"3 0 obj%n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.0f %.0f]"
						+ " /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>%nendobj%n",
				PAGE_W, PAGE_H));

		offsets.add(out.size());
		write(out, String.format(
				"4 0 obj%n<< /Type /XObject /Subtype /Image /Width %d /Height %d"
						+ " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length %d >>%nstream%n",
				image.getWidth(), image.getHeight(), jpeg.length));
		out.write(jpeg);
		write(out, "\nendstream\nendobj\n");

		offsets.add(out.size());
		write(out, String.format("5 0 obj%n<< /Length %d >>%nstream%n", contentBytes.length));
		out.write(contentBytes);
		write(out, "endstream\nendobj\n");

		int xref = out.size();
		write(out, "xref\n0 " + (offsets.size() + 1) + "\n");
		write(out, "0000000000 65535 f \n");
		for (int off : offsets)
			write(out, String.format("%010d 00000 n %n", off));
		write(out, String.format("trailer%n<< /Size %d /Root 1 0 R >>%nstartxref%n%d%n%%%%EOF%n",
				offsets.size() + 1, xref));
		return out.toByteArray();
	}

	private static void write(ByteArrayOutputStream out, String s) throws IOException {
		out.write(s.getBytes(StandardCharsets.US_ASCII));
	}
}
