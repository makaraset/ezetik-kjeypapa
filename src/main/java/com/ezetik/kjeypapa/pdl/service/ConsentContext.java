package com.ezetik.kjeypapa.pdl.service;

/**
 * Where and how a consent was given: the app build that displayed the wording,
 * the device it was read on, and the address it arrived from.
 *
 * <p>None of this is trusted. Two of the three fields are headers the client
 * writes, and the third is only as good as the proxy configuration behind
 * {@link com.ezetik.kjeypapa.pdl.api.ClientAddress}. They are kept because a
 * consent record that says nothing about its circumstances cannot be
 * corroborated at all, not because they prove anything on their own.
 *
 * <p>Built in the controller, which is the only layer that should know an HTTP
 * request exists — the service reads a value object, not
 * {@code RequestContextHolder}.
 */
public record ConsentContext(String appVersion, String deviceModel, String ipAddress) {

	/** Nothing captured. Used by callers with no request behind them. */
	public static final ConsentContext NONE = new ConsentContext("", "", "");

	// Column widths in pdl_cbc_consent. A header is whatever the caller sends,
	// and an over-long one would fail the insert at the very end of a submit
	// that has already filed a real credit application with Sambat.
	private static final int MAX_APP_VERSION = 32;
	private static final int MAX_DEVICE = 64;
	private static final int MAX_IP = 45;

	/**
	 * Cleans client-supplied values into something storable.
	 *
	 * <p>Control characters go first. The canonical form escapes newlines, so a
	 * value carrying one cannot forge a field boundary in the hash — but it can
	 * still make a stored record unreadable, and there is no legitimate device
	 * name containing one. Then each value is cut to its column width, because
	 * failing an insert here would roll back a submission Sambat has already
	 * accepted.
	 */
	public ConsentContext {
		appVersion = clean(appVersion, MAX_APP_VERSION);
		deviceModel = clean(deviceModel, MAX_DEVICE);
		ipAddress = clean(ipAddress, MAX_IP);
	}

	private static String clean(String v, int max) {
		if (v == null)
			return "";
		String stripped = v.replaceAll("\\p{Cntrl}", "").trim();
		return stripped.length() <= max ? stripped : stripped.substring(0, max);
	}
}
