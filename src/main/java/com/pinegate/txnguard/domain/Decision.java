package com.pinegate.txnguard.domain;

/** The outcome of screening a single transaction. */
public enum Decision {
    /** Authorise without human involvement. */
    APPROVE,
    /** Authorise, but raise a case for an analyst to review after the fact. */
    REVIEW,
    /** Refuse the authorisation outright. */
    DECLINE
}
