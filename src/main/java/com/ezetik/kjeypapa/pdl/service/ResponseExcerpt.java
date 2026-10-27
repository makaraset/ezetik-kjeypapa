package com.ezetik.kjeypapa.pdl.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * A third-party reply made safe to keep, for an LPO to read later.
 *
 * <p>CamDigi's {@code data} is untyped and undocumented, and identity services
 * commonly echo the images they were sent, or return a registry photo. Any
 * string longer than {@link #MAX_STRING} characters is therefore replaced by a
 * note of its length — no person's name or date is that long, every image is —
 * and the whole is capped. The input is never modified.
 */
final class ResponseExcerpt {

	private ResponseExcerpt() {
	}

	static final int MAX_STRING = 256;

	static String of(JsonNode node, int maxChars) {
		if (node == null)
			return null;
		String s = redact(node.deepCopy()).toString();
		return s.length() <= maxChars ? s : s.substring(0, maxChars) + "...";
	}

	private static JsonNode redact(JsonNode n) {
		if (n instanceof ObjectNode o) {
			List<Map.Entry<String, JsonNode>> entries = new ArrayList<>(o.properties());
			for (Map.Entry<String, JsonNode> e : entries) {
				if (isLong(e.getValue()))
					o.set(e.getKey(), omitted(e.getValue()));
				else
					redact(e.getValue());
			}
		} else if (n instanceof ArrayNode a) {
			for (int i = 0; i < a.size(); i++) {
				if (isLong(a.get(i)))
					a.set(i, omitted(a.get(i)));
				else
					redact(a.get(i));
			}
		}
		return n;
	}

	private static boolean isLong(JsonNode v) {
		return v.isTextual() && v.asText().length() > MAX_STRING;
	}

	private static TextNode omitted(JsonNode v) {
		return TextNode.valueOf("<omitted " + v.asText().length() + " chars>");
	}
}
