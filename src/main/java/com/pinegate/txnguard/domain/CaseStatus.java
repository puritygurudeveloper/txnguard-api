package com.pinegate.txnguard.domain;

/** Lifecycle of an analyst review case. */
public enum CaseStatus {
    OPEN,
    IN_REVIEW,
    CONFIRMED_FRAUD,
    CLEARED;

    /** Terminal statuses cannot be reassigned or re-resolved. */
    public boolean isTerminal() {
        return this == CONFIRMED_FRAUD || this == CLEARED;
    }
}
