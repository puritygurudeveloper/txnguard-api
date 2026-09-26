package com.pinegate.txnguard.application;

import com.pinegate.txnguard.application.port.CaseRepository;
import com.pinegate.txnguard.domain.CaseStatus;
import com.pinegate.txnguard.domain.ReviewCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Inbound use case: the analyst queue. */
@Service
public class CaseService {

    private final CaseRepository cases;
    private final Clock clock;

    public CaseService(CaseRepository cases, Clock clock) {
        this.cases = cases;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReviewCase> openQueue(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be within 1..500, got " + limit);
        }
        return cases.findOpenCases(limit);
    }

    @Transactional(readOnly = true)
    public ReviewCase get(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    @Transactional
    public ReviewCase assign(String caseId, String analyst) {
        return cases.save(get(caseId).assignTo(analyst));
    }

    @Transactional
    public ReviewCase resolve(String caseId, CaseStatus outcome, String notes) {
        return cases.save(get(caseId).resolve(outcome, notes, clock.instant()));
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return cases.countOpen();
    }

    /** Thrown when a case id does not exist; mapped to 404 by the web layer. */
    public static final class CaseNotFoundException extends RuntimeException {
        public CaseNotFoundException(String caseId) {
            super("no case with id " + caseId);
        }
    }
}
