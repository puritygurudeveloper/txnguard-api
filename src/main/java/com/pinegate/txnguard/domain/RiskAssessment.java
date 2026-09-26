package com.pinegate.txnguard.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The full, explainable result of screening one transaction.
 *
 * @param score    0-100, saturating rather than wrapping
 * @param signals  every rule that fired, strongest first
 */
public record RiskAssessment(
        String transactionId,
        int score,
        Decision decision,
        List<RuleSignal> signals,
        Instant assessedAt) {

    public RiskAssessment {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(assessedAt, "assessedAt");
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within 0..100, got " + score);
        }
        signals = signals.stream()
                .sorted(Comparator.comparingInt(RuleSignal::points).reversed())
                .toList();
    }

    public boolean requiresHumanReview() {
        return decision == Decision.REVIEW || decision == Decision.DECLINE;
    }

    /** The codes that drove the decision, for alerting and reporting. */
    public List<String> signalCodes() {
        return signals.stream().map(RuleSignal::code).toList();
    }
}
