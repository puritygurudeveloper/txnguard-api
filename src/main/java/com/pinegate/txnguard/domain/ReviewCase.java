package com.pinegate.txnguard.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An analyst's work item, raised when screening cannot settle a transaction on its own.
 *
 * <p>Mutation returns a new instance. Case state transitions are the part of this system
 * most likely to be touched concurrently — two analysts opening the same queue — and an
 * immutable aggregate makes an illegal interleaving impossible to express rather than
 * merely unlikely.
 */
public record ReviewCase(
        String caseId,
        String transactionId,
        String cardholderId,
        int riskScore,
        CaseStatus status,
        String assignedTo,
        String resolutionNotes,
        Instant openedAt,
        Instant resolvedAt,
        long version) {

    public ReviewCase {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(openedAt, "openedAt");
    }

    public static ReviewCase open(String caseId, RiskAssessment assessment, String cardholderId) {
        return new ReviewCase(
                caseId,
                assessment.transactionId(),
                cardholderId,
                assessment.score(),
                CaseStatus.OPEN,
                null,
                null,
                assessment.assessedAt(),
                null,
                0L);
    }

    public ReviewCase assignTo(String analyst) {
        requireOpenForWork();
        if (analyst == null || analyst.isBlank()) {
            throw new IllegalArgumentException("analyst must not be blank");
        }
        return new ReviewCase(caseId, transactionId, cardholderId, riskScore,
                CaseStatus.IN_REVIEW, analyst, resolutionNotes, openedAt, null, version);
    }

    public ReviewCase resolve(CaseStatus outcome, String notes, Instant at) {
        requireOpenForWork();
        if (!outcome.isTerminal()) {
            throw new IllegalArgumentException("not a resolution status: " + outcome);
        }
        if (notes == null || notes.isBlank()) {
            // A resolved fraud case with no rationale is useless to the next analyst
            // and indefensible in a chargeback dispute.
            throw new IllegalArgumentException("a resolution must record why");
        }
        return new ReviewCase(caseId, transactionId, cardholderId, riskScore,
                outcome, assignedTo, notes, openedAt, at, version);
    }

    public Optional<String> assignee() {
        return Optional.ofNullable(assignedTo);
    }

    private void requireOpenForWork() {
        if (status.isTerminal()) {
            throw new CaseAlreadyResolvedException(caseId, status);
        }
    }

    /** Thrown when a terminal case is assigned or resolved again. */
    public static final class CaseAlreadyResolvedException extends IllegalStateException {
        public CaseAlreadyResolvedException(String caseId, CaseStatus status) {
            super("case %s is already %s".formatted(caseId, status));
        }
    }
}
