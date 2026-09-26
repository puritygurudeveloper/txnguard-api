package com.pinegate.txnguard.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewCaseTest {

    private static final Instant NOW = Instant.parse("2024-03-01T12:00:00Z");

    private static ReviewCase openCase() {
        RiskAssessment assessment = new RiskAssessment(
                "txn-1", 62, Decision.REVIEW,
                List.of(new RuleSignal("VELOCITY", 62, "burst")), NOW);
        return ReviewCase.open("case-1", assessment, "card-1");
    }

    @Test
    void opensUnassignedAndUnresolved() {
        ReviewCase opened = openCase();
        assertThat(opened.status()).isEqualTo(CaseStatus.OPEN);
        assertThat(opened.assignee()).isEmpty();
        assertThat(opened.resolvedAt()).isNull();
        assertThat(opened.riskScore()).isEqualTo(62);
    }

    @Test
    void assigningDoesNotMutateTheOriginal() {
        ReviewCase opened = openCase();
        ReviewCase assigned = opened.assignTo("aisha");

        assertThat(assigned.assignee()).contains("aisha");
        assertThat(assigned.status()).isEqualTo(CaseStatus.IN_REVIEW);
        // The aggregate is immutable, so the pre-assignment value is untouched.
        assertThat(opened.assignee()).isEmpty();
        assertThat(opened.status()).isEqualTo(CaseStatus.OPEN);
    }

    @Test
    void resolutionRecordsOutcomeNotesAndTime() {
        ReviewCase resolved = openCase().assignTo("aisha")
                .resolve(CaseStatus.CONFIRMED_FRAUD, "cardholder confirmed by phone", NOW);

        assertThat(resolved.status()).isEqualTo(CaseStatus.CONFIRMED_FRAUD);
        assertThat(resolved.resolutionNotes()).isEqualTo("cardholder confirmed by phone");
        assertThat(resolved.resolvedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesAResolutionWithNoRationale() {
        ReviewCase opened = openCase();
        assertThatThrownBy(() -> opened.resolve(CaseStatus.CLEARED, "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("why");
    }

    @ParameterizedTest
    @EnumSource(value = CaseStatus.class, names = {"OPEN", "IN_REVIEW"})
    void refusesToResolveIntoANonTerminalStatus(CaseStatus notAResolution) {
        ReviewCase opened = openCase();
        assertThatThrownBy(() -> opened.resolve(notAResolution, "notes", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a resolution status");
    }

    @Test
    void refusesToTouchAResolvedCase() {
        ReviewCase resolved = openCase().resolve(CaseStatus.CLEARED, "false positive", NOW);

        assertThatThrownBy(() -> resolved.assignTo("bo"))
                .isInstanceOf(ReviewCase.CaseAlreadyResolvedException.class);
        assertThatThrownBy(() -> resolved.resolve(CaseStatus.CONFIRMED_FRAUD, "changed my mind", NOW))
                .isInstanceOf(ReviewCase.CaseAlreadyResolvedException.class);
    }

    @Test
    void assessmentOrdersSignalsByStrength() {
        RiskAssessment assessment = new RiskAssessment("txn-2", 70, Decision.REVIEW,
                List.of(new RuleSignal("WEAK", 5, "a"), new RuleSignal("STRONG", 50, "b")), NOW);
        assertThat(assessment.signalCodes()).containsExactly("STRONG", "WEAK");
    }
}
