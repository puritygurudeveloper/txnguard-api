package com.pinegate.txnguard.adapter.persistence;

import com.pinegate.txnguard.domain.CaseStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SpringDataCaseRepository extends JpaRepository<CaseEntity, String> {

    Optional<CaseEntity> findByTransactionId(String transactionId);

    List<CaseEntity> findByStatusInOrderByRiskScoreDescOpenedAtAsc(
            Collection<CaseStatus> statuses, Limit limit);

    long countByStatusIn(Collection<CaseStatus> statuses);
}
