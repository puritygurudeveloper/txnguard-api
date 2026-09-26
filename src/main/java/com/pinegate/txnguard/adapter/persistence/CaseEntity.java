package com.pinegate.txnguard.adapter.persistence;

import com.pinegate.txnguard.domain.CaseStatus;
import com.pinegate.txnguard.domain.ReviewCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * JPA mapping for {@link ReviewCase}.
 *
 * <p>Separate from the domain record on purpose: JPA needs a no-arg constructor and
 * mutable fields, and letting those requirements reach the aggregate would forfeit its
 * immutability. The translation cost is two small methods.
 */
@Entity
@Table(name = "review_case", indexes = {
        @Index(name = "idx_case_status", columnList = "status"),
        @Index(name = "idx_case_transaction", columnList = "transactionId", unique = true)
})
class CaseEntity {

    @Id
    private String caseId;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private String cardholderId;

    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CaseStatus status;

    private String assignedTo;

    @Column(length = 2000)
    private String resolutionNotes;

    @Column(nullable = false)
    private Instant openedAt;

    private Instant resolvedAt;

    /**
     * Optimistic locking. Two analysts opening the queue at the same moment will both
     * try to assign the top case; the second write fails loudly instead of silently
     * overwriting the first.
     */
    @Version
    private long version;

    protected CaseEntity() {
        // required by JPA
    }

    static CaseEntity fromDomain(ReviewCase source) {
        CaseEntity entity = new CaseEntity();
        entity.caseId = source.caseId();
        entity.transactionId = source.transactionId();
        entity.cardholderId = source.cardholderId();
        entity.riskScore = source.riskScore();
        entity.status = source.status();
        entity.assignedTo = source.assignedTo();
        entity.resolutionNotes = source.resolutionNotes();
        entity.openedAt = source.openedAt();
        entity.resolvedAt = source.resolvedAt();
        entity.version = source.version();
        return entity;
    }

    ReviewCase toDomain() {
        return new ReviewCase(caseId, transactionId, cardholderId, riskScore, status,
                assignedTo, resolutionNotes, openedAt, resolvedAt, version);
    }
}
