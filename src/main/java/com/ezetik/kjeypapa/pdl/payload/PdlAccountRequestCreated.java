package com.ezetik.kjeypapa.pdl.payload;

/**
 * What {@code POST /api/v1/pdl/account-request} answers with: enough to refer
 * to the request, and nothing about the person.
 *
 * <p>It used to return the whole {@code PdlAccountRequest}, whose eager
 * {@code user} serialised {@code registedId}: the SBF CIF the backend had just
 * looked up from the submitted NID number. Anyone could submit a stranger's NID
 * and learn whether they bank with SBF, and their CIF. That is the same oracle
 * removed from the OCR endpoint. The app never read the field.
 */
public record PdlAccountRequestCreated(Integer requestId, String status) {
}
