package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.RuleSignal;
import com.pinegate.txnguard.domain.Transaction;

import java.util.Optional;

/**
 * A single, independently testable fraud heuristic.
 *
 * <p>A rule is a pure function. It may not perform I/O, read a clock, or hold mutable
 * state: everything it needs arrives in the {@link CardholderProfile}. That constraint
 * is what makes a six-month-old decision reproducible from the audit record.
 */
public interface FraudRule {

    /** Stable identifier, also used as the {@link RuleSignal} code. */
    String code();

    /** Empty when the rule does not fire. */
    Optional<RuleSignal> evaluate(Transaction transaction, CardholderProfile profile);
}
