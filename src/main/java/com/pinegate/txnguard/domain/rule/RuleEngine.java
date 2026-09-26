package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.Decision;
import com.pinegate.txnguard.domain.RiskAssessment;
import com.pinegate.txnguard.domain.RuleSignal;
import com.pinegate.txnguard.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Runs every rule and turns the signals into a decision.
 *
 * <p>Rules are injected as a list, so adding one is a matter of dropping a
 * {@code @Component} into the package. The engine itself knows nothing about what any
 * individual rule does.
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<FraudRule> rules;
    private final ScreeningProperties properties;
    private final Clock clock;

    public RuleEngine(List<FraudRule> rules, ScreeningProperties properties, Clock clock) {
        if (rules.isEmpty()) {
            throw new IllegalStateException("no fraud rules registered; screening would approve everything");
        }
        this.rules = List.copyOf(rules);
        this.properties = properties;
        this.clock = clock;
    }

    public RiskAssessment assess(Transaction transaction, CardholderProfile profile) {
        List<RuleSignal> signals = rules.stream()
                .map(rule -> evaluateSafely(rule, transaction, profile))
                .flatMap(Optional::stream)
                .toList();

        // Scores saturate at 100 rather than wrapping or being renormalised. Three
        // independent strong signals should not dilute each other into a pass.
        int score = Math.min(100, signals.stream().mapToInt(RuleSignal::points).sum());

        return new RiskAssessment(
                transaction.transactionId(), score, decide(score), signals, clock.instant());
    }

    private Decision decide(int score) {
        if (score >= properties.declineThreshold()) {
            return Decision.DECLINE;
        }
        if (score >= properties.reviewThreshold()) {
            return Decision.REVIEW;
        }
        return Decision.APPROVE;
    }

    /**
     * A rule that throws must not take down the authorisation path.
     *
     * <p>Failing open is the deliberate choice: a payment network has a hard timeout and
     * declining every transaction because one heuristic has a bug is a worse outage than
     * screening with four rules instead of five. The failure is logged at error level so
     * it is visible rather than silent.
     */
    private Optional<RuleSignal> evaluateSafely(
            FraudRule rule, Transaction transaction, CardholderProfile profile) {
        try {
            return rule.evaluate(transaction, profile);
        } catch (RuntimeException e) {
            log.error("rule {} failed for transaction {}; continuing without it",
                    rule.code(), transaction.transactionId(), e);
            return Optional.empty();
        }
    }

    public List<String> registeredRuleCodes() {
        return rules.stream().map(FraudRule::code).sorted().toList();
    }
}
