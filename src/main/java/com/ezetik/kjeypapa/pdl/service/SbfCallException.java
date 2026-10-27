package com.ezetik.kjeypapa.pdl.service;

/**
 * A call to SBF's gateway that produced no usable reply, saying how far it got.
 *
 * <p>None of these kinds is the customer's doing — each is ours or SBF's to
 * fix — and a caller that records attempts must keep them apart from a real
 * "no" from SBF. Whether the request reached SBF also decides what a retry
 * costs.
 */
public class SbfCallException extends Exception {

	private static final long serialVersionUID = 1L;

	public enum Kind {
		/** Never reached SBF: no connection, or no token. Nothing was sent. */
		UNREACHABLE,
		/** Sent, but no reply arrived in time: a read timeout or a reset. */
		NO_REPLY,
		/** SBF answered 5xx: they received it and something broke on their side. */
		SERVER_ERROR,
		/** SBF answered another non-200: they refused the request as sent. */
		REJECTED,
		/** A 200 whose body is not the JSON object we expect. */
		BAD_REPLY
	}

	private final Kind kind;
	private final Integer httpStatus;
	private final String detail;

	public SbfCallException(Kind kind, Integer httpStatus, String detail) {
		super(kind + (httpStatus == null ? "" : " (HTTP " + httpStatus + ")") + (detail == null ? "" : ": " + detail));
		this.kind = kind;
		this.httpStatus = httpStatus;
		this.detail = detail;
	}

	public Kind getKind() {
		return kind;
	}

	/** The HTTP status SBF answered with, or null when there was no reply. */
	public Integer getHttpStatus() {
		return httpStatus;
	}

	/** A short, size-capped description: the exception, or the start of SBF's body. */
	public String getDetail() {
		return detail;
	}
}
