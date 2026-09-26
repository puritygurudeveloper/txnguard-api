package com.pinegate.txnguard.adapter.web.dto;

import com.pinegate.txnguard.domain.Decision;
import com.pinegate.txnguard.domain.RiskAssessment;

import java.time.Instant;
import java.util.List;

/** Wire format for a screening outcome. */
public record ScreeningResponse(
        String transactionId,
        int riskScore,
        Decision decision,
        List<SignalView> signals,
        Instant assessedAt) {

    public record SignalView(String code, int points, String explanation) {}

    public static ScreeningResponse from(RiskAssessment assessment) {
        return new ScreeningResponse(
                assessment.transactionId(),
                assessment.score(),
                assessment.decision(),
                assessment.signals().stream()
                        .map(s -> new SignalView(s.code(), s.points(), s.explanation()))
                        .toList(),
                assessment.assessedAt());
    }
}
