package com.pinegate.txnguard.application.port;

import com.pinegate.txnguard.domain.ReviewCase;

import java.util.List;
import java.util.Optional;

/** Outbound port: persistence for analyst review cases. */
public interface CaseRepository {

    ReviewCase save(ReviewCase reviewCase);

    Optional<ReviewCase> findById(String caseId);

    Optional<ReviewCase> findByTransactionId(String transactionId);

    List<ReviewCase> findOpenCases(int limit);

    long countOpen();
}
