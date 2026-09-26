package com.pinegate.txnguard.domain;

import java.util.Objects;

/**
 * One rule's contribution to a risk score.
 *
 * <p>Every signal carries a human-readable explanation. A declined payment produces a
 * customer call within minutes, and "the model said so" is not an answer an analyst can
 * give; the signal text is what ends up on the case.
 *
 * @param code      stable identifier, safe to alert and report on
 * @param points    contribution to the total score, 0-100
 * @param explanation what the analyst reads
 */
public record RuleSignal(String code, int points, String explanation) {

    public RuleSignal {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(explanation, "explanation");
        if (code.isBlank()) {
            throw new IllegalArgumentException("signal code must not be blank");
        }
        if (points < 0 || points > 100) {
            throw new IllegalArgumentException("signal points must be within 0..100, got " + points);
        }
    }
}
