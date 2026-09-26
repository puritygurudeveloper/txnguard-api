package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.Decision;
import com.pinegate.txnguard.domain.RiskAssessment;
import com.pinegate.txnguard.domain.RuleSignal;
import com.pinegate.txnguard.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.pinegate.txnguard.domain.rule.RuleTestSupport.NOW;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.profile;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.transaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ScreeningProperties PROPERTIES = ScreeningProperties.defaults();

    /** A rule that always contributes a fixed number of points. */
    private static FraudRule scoring(String code, int points) {
        return new FraudRule() {
            public String code() { return code; }
            public Optional<RuleSignal> evaluate(Transaction t, CardholderProfile p) {
                return Optional.of(new RuleSignal(code, points, code + " fired"));
            }
        };
    }

    private static FraudRule exploding(String code) {
        return new FraudRule() {
            public String code() { return code; }
            public Optional<RuleSignal> evaluate(Transaction t, CardholderProfile p) {
                throw new IllegalStateException("downstream feature store is down");
            }
        };
    }

    private RiskAssessment assessWith(List<FraudRule> rules) {
        return new RuleEngine(rules, PROPERTIES, FIXED)
                .assess(transaction("100.00"), profile("100.00"));
    }

    @Test
    void approvesBelowTheReviewThreshold() {
        RiskAssessment assessment = assessWith(List.of(scoring("A", 20)));
        assertThat(assessment.score()).isEqualTo(20);
        assertThat(assessment.decision()).isEqualTo(Decision.APPROVE);
        assertThat(assessment.requiresHumanReview()).isFalse();
    }

    @Test
    void reviewsAtTheThresholdItself() {
        // Boundaries are inclusive; an off-by-one here silently changes the queue volume.
        assertThat(assessWith(List.of(scoring("A", 40))).decision()).isEqualTo(Decision.REVIEW);
        assertThat(assessWith(List.of(scoring("A", 39))).decision()).isEqualTo(Decision.APPROVE);
    }

    @Test
    void declinesAtTheDeclineThreshold() {
        assertThat(assessWith(List.of(scoring("A", 75))).decision()).isEqualTo(Decision.DECLINE);
        assertThat(assessWith(List.of(scoring("A", 74))).decision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    void accumulatesSignalsAcrossRules() {
        RiskAssessment assessment = assessWith(List.of(scoring("A", 30), scoring("B", 25)));
        assertThat(assessment.score()).isEqualTo(55);
        assertThat(assessment.signalCodes()).containsExactly("A", "B");
    }

    @Test
    void saturatesRatherThanOverflowingPastOneHundred() {
        // Three strong independent signals must not dilute or wrap into a pass.
        RiskAssessment assessment = assessWith(List.of(scoring("A", 50), scoring("B", 50), scoring("C", 50)));
        assertThat(assessment.score()).isEqualTo(100);
        assertThat(assessment.decision()).isEqualTo(Decision.DECLINE);
    }

    @Test
    void failsOpenWhenARuleThrows() {
        // A payment network has a hard timeout; declining everything because one
        // heuristic has a bug is a worse outage than screening with one rule fewer.
        RiskAssessment assessment = assessWith(List.of(exploding("BROKEN"), scoring("A", 30)));
        assertThat(assessment.score()).isEqualTo(30);
        assertThat(assessment.signalCodes()).containsExactly("A");
    }

    @Test
    void refusesToStartWithNoRulesAtAll() {
        assertThatThrownBy(() -> new RuleEngine(List.of(), PROPERTIES, FIXED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approve everything");
    }

    @Test
    void stampsTheAssessmentFromTheInjectedClock() {
        assertThat(assessWith(List.of(scoring("A", 10))).assessedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsAThresholdConfigurationThatCanNeverReview() {
        assertThatThrownBy(() -> new ScreeningProperties(
                80, 80, 8.0, 5, java.time.Duration.ofMinutes(10), 900.0, 30, java.util.Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("merely reviewed");
    }

    @Test
    void reportsTheRulesItIsRunning() {
        RuleEngine engine = new RuleEngine(List.of(scoring("Z", 1), scoring("A", 1)), PROPERTIES, FIXED);
        assertThat(engine.registeredRuleCodes()).containsExactly("A", "Z");
    }

    @Test
    void assessmentRejectsAnOutOfRangeScore() {
        assertThatThrownBy(() -> new RiskAssessment("t", 101, Decision.APPROVE, List.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
