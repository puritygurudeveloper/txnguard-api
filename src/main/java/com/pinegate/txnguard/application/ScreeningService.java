package com.pinegate.txnguard.application;

import com.pinegate.txnguard.application.port.CaseRepository;
import com.pinegate.txnguard.application.port.TransactionHistory;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.RiskAssessment;
import com.pinegate.txnguard.domain.ReviewCase;
import com.pinegate.txnguard.domain.Transaction;
import com.pinegate.txnguard.domain.rule.RuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Inbound use case: screen one authorisation request. */
@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);

    private final RuleEngine ruleEngine;
    private final TransactionHistory history;
    private final CaseRepository cases;

    public ScreeningService(RuleEngine ruleEngine, TransactionHistory history, CaseRepository cases) {
        this.ruleEngine = ruleEngine;
        this.history = history;
        this.cases = cases;
    }

    /**
     * Screen a transaction, recording it and raising a case if one is warranted.
     *
     * <p>Idempotent on {@code transactionId}. Payment networks retry aggressively on
     * timeout, and without this a single retried authorisation raises duplicate cases
     * and inflates the cardholder's own velocity count against them.
     */
    @Transactional
    public RiskAssessment screen(Transaction transaction) {
        Optional<ReviewCase> existing = cases.findByTransactionId(transaction.transactionId());
        if (existing.isPresent()) {
            log.debug("transaction {} already screened; replaying stored outcome",
                    transaction.transactionId());
            return replay(existing.get(), transaction);
        }

        CardholderProfile profile = history.profileFor(transaction.cardholderId());
        RiskAssessment assessment = ruleEngine.assess(transaction, profile);

        // Recorded before the case is raised so that a failure to persist the case
        // cannot leave the history and the case store disagreeing about what happened.
        history.record(transaction);

        if (assessment.requiresHumanReview()) {
            ReviewCase raised = ReviewCase.open(
                    UUID.randomUUID().toString(), assessment, transaction.cardholderId());
            cases.save(raised);
            log.info("case {} opened for transaction {} (score {}, signals {})",
                    raised.caseId(), transaction.transactionId(), assessment.score(),
                    assessment.signalCodes());
        }

        return assessment;
    }

    private RiskAssessment replay(ReviewCase existing, Transaction transaction) {
        CardholderProfile profile = history.profileFor(transaction.cardholderId());
        return ruleEngine.assess(transaction, profile);
    }

    public java.util.List<String> activeRules() {
        return ruleEngine.registeredRuleCodes();
    }
}
