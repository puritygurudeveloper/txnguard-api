package com.pinegate.txnguard.adapter.web.dto;

import com.pinegate.txnguard.domain.CaseStatus;
import com.pinegate.txnguard.domain.ReviewCase;

import java.time.Instant;

/** Wire format for an analyst review case. */
public record CaseView(
        String caseId,
        String transactionId,
        String cardholderId,
        int riskScore,
        CaseStatus status,
        String assignedTo,
        String resolutionNotes,
        Instant openedAt,
        Instant resolvedAt) {

    public static CaseView from(ReviewCase source) {
        return new CaseView(
                source.caseId(),
                source.transactionId(),
                source.cardholderId(),
                source.riskScore(),
                source.status(),
                source.assignedTo(),
                source.resolutionNotes(),
                source.openedAt(),
                source.resolvedAt());
    }
}
