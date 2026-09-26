package com.pinegate.txnguard.adapter.persistence;

import com.pinegate.txnguard.application.port.CaseRepository;
import com.pinegate.txnguard.domain.CaseStatus;
import com.pinegate.txnguard.domain.ReviewCase;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Adapter implementing the {@link CaseRepository} port over Spring Data JPA. */
@Repository
class JpaCaseRepository implements CaseRepository {

    /** Everything an analyst could still act on. */
    private static final Set<CaseStatus> ACTIONABLE = Set.of(CaseStatus.OPEN, CaseStatus.IN_REVIEW);

    private final SpringDataCaseRepository delegate;

    JpaCaseRepository(SpringDataCaseRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public ReviewCase save(ReviewCase reviewCase) {
        return delegate.save(CaseEntity.fromDomain(reviewCase)).toDomain();
    }

    @Override
    public Optional<ReviewCase> findById(String caseId) {
        return delegate.findById(caseId).map(CaseEntity::toDomain);
    }

    @Override
    public Optional<ReviewCase> findByTransactionId(String transactionId) {
        return delegate.findByTransactionId(transactionId).map(CaseEntity::toDomain);
    }

    @Override
    public List<ReviewCase> findOpenCases(int limit) {
        // Highest risk first, then oldest: an analyst should never have to sort the
        // queue by hand, and a low-scoring case should not starve behind new arrivals.
        return delegate
                .findByStatusInOrderByRiskScoreDescOpenedAtAsc(ACTIONABLE, Limit.of(limit))
                .stream()
                .map(CaseEntity::toDomain)
                .toList();
    }

    @Override
    public long countOpen() {
        return delegate.countByStatusIn(ACTIONABLE);
    }
}
