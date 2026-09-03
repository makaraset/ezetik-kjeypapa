package com.ezetik.kjeypapa.pdl.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;

/**
 * Renders the {@code Doc_ECBCConsentForm} document for the loan application
 * from the consent the customer actually gave in-app (the CBC page checkbox,
 * V8 screen 15) — the consent text, the customer's identity, the deterministic
 * consent reference and a timestamp.
 *
 * <p>Sambat's {@code MissingData} confirmed this slot is mandatory
 * (2026-09-03). Their own reference payload carries an arbitrary reused file
 * here ("Consent_Rental agreement.pdf"), so a system-rendered record of the
 * real consent is well within what the slot accepts — and it is honest: it
 * reproduces exactly what the customer agreed to, rather than a scan of
 * something they never saw. Java2D + ImageIO only; no new dependencies.
 *
 * <p>Khmer text is deliberately NOT rendered here: the JVM's default fonts do
 * not shape Khmer script reliably, and a garbled consent form is worse than an
 * English one. When Sambat sends the final EN+KM legal text, revisit with a
 * bundled Khmer-capable font.
 */
@Component
public class CbcConsentFormRenderer {

	private static final int WIDTH = 1000;
	private static final int MARGIN = 70;
	private static final ZoneId KH = ZoneId.of("Asia/Phnom_Penh");

	/** Same interim consent text the submit stamps and the record endpoint serves. */
	@Value("${pdl.cbc.consent-text:I consent to Sambat Finance conducting a credit enquiry with the Credit Bureau of Cambodia (CBC) for the purpose of assessing this loan application, in accordance with the Prakas on Credit Reporting.}")
	private String consentText;

	@Value("${pdl.cbc.text-version:v1}")
	private String textVersion;

	public byte[] render(PaydayLoan loan, PdlPersonalInfo pi) {
		int loanId = loan.getId() == null ? 0 : loan.getId();
		String customer = ((s(pi.getLatinFamilyName()) + " " + s(pi.getLatinFirstName())).trim());
		String ref = "CBC-" + loanId + "-" + textVersion;
		String when = ZonedDateTime.now(KH).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm 'ICT'"));

		Font title = new Font(Font.SANS_SERIF, Font.BOLD, 34);
		Font label = new Font(Font.SANS_SERIF, Font.BOLD, 22);
		Font body = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
		Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 18);

		// Measure pass with a throwaway canvas, then draw for real.
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		List<String> lines = wrap(consentText, probe.createGraphics(), body, WIDTH - 2 * MARGIN);
		int textBlock = lines.size() * 34;
		int height = 430 + textBlock + 170;

		BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, WIDTH, height);
		g.setColor(new Color(0x1A, 0x35, 0x7A));
		g.fillRect(0, 0, WIDTH, 8);

		int y = 90;
		g.setColor(Color.BLACK);
		g.setFont(title);
		g.drawString("CBC Credit Enquiry Consent", MARGIN, y);
		y += 40;
		g.setFont(small);
		g.setColor(Color.DARK_GRAY);
		g.drawString("Kjey PAPA · Sambat Finance PLC — Payday Loan application", MARGIN, y);
		y += 50;

		g.setColor(Color.BLACK);
		y = kv(g, label, body, "Customer", customer, y);
		y = kv(g, label, body, "ID No. (NID)", s(pi.getIdNo()), y);
		y = kv(g, label, body, "Application", "#" + loanId, y);
		y = kv(g, label, body, "Consent Ref", ref, y);
		y = kv(g, label, body, "Date", when, y);
		y += 30;

		g.setFont(body);
		for (String line : lines) {
			g.drawString(line, MARGIN, y);
			y += 34;
		}
		y += 50;

		g.setFont(small);
		g.setColor(Color.DARK_GRAY);
		g.drawString("Consent given electronically in the Kjey PAPA app (text version " + textVersion + ").",
				MARGIN, y);
		y += 26;
		g.drawString("Recorded by the Kjey PAPA lending platform at submission time.", MARGIN, y);
		g.dispose();

		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		} catch (Exception e) {
			throw new LosSubmitException("LOS_CONSENT_RENDER", "Could not render the CBC consent form");
		}
	}

	private static int kv(Graphics2D g, Font label, Font body, String k, String v, int y) {
		g.setFont(label);
		g.drawString(k, MARGIN, y);
		g.setFont(body);
		g.drawString(v, MARGIN + 260, y);
		return y + 40;
	}

	/** Greedy word wrap by real rendered width. */
	private static List<String> wrap(String text, Graphics2D g, Font font, int maxWidth) {
		g.setFont(font);
		var fm = g.getFontMetrics();
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (fm.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
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
