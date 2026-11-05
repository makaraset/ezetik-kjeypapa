package com.ezetik.kjeypapa.pdl.payload;

/**
 * What a liveness submit came to. {@code message} is the generic, customer-safe
 * text; CamDigi's own message goes to the LPO, not here.
 */
public record LivenessResultResponse(String outcome, int attemptsLeft, String message) {
}
