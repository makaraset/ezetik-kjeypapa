package com.ezetik.kjeypapa.notification.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationMessage {

	private String token;
	private String title;
	private String body;
	private String data;
	private String username;

	// --- V8 notification routing (Sambat answers 2026-08-13; G19) ---
	/** Machine event type for app-side severity/deep-link routing (e.g. loan_approved). */
	private String type;
	/** Entity id for deep-linking (e.g. the PDL loan id). */
	private String refId;

	/** Legacy 5-arg form — pre-G19 call sites (no type routing). */
	public NotificationMessage(String token, String title, String body, String data, String username) {
		this(token, title, body, data, username, null, null);
	}
}
