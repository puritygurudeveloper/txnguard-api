package com.pinegate.txnguard.domain;

/** How the card was presented. Drives which rules are even applicable. */
public enum Channel {
    CARD_PRESENT,
    ECOMMERCE,
    MAIL_ORDER,
    RECURRING,
    ATM_WITHDRAWAL;

    /**
     * Whether the physical card was at the terminal.
     *
     * <p>Only these can support an impossible-travel inference; a card-not-present
     * transaction says nothing about where the cardholder is.
     */
    public boolean impliesPhysicalPresence() {
        return this == CARD_PRESENT || this == ATM_WITHDRAWAL;
    }
}
