package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.Channel;
import com.pinegate.txnguard.domain.GeoPoint;
import com.pinegate.txnguard.domain.Money;
import com.pinegate.txnguard.domain.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Builders so each rule test states only the one fact it is about. */
final class RuleTestSupport {

    static final Instant NOW = Instant.parse("2024-06-01T12:00:00Z");
    static final GeoPoint AUSTIN = new GeoPoint(30.2672, -97.7431);
    static final GeoPoint LONDON = new GeoPoint(51.5074, -0.1278);

    /** Ids must be distinct even for identical timestamps; clock skew is a real case. */
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private RuleTestSupport() {}

    static Transaction transaction(String amount, Channel channel, String country, GeoPoint where, Instant at) {
        return new Transaction("txn-" + SEQUENCE.incrementAndGet(), "card-1", "merch-1", "5411",
                Money.of(amount, "USD"), channel, country, where, at);
    }

    static Transaction transaction(String amount) {
        return transaction(amount, Channel.ECOMMERCE, "US", null, NOW);
    }

    static CardholderProfile profile(String median, List<Transaction> recent) {
        return new CardholderProfile("card-1", Money.of(median, "USD"), Money.of("5000.00", "USD"),
                Set.of("merch-1"), Set.of("US"), recent, 900);
    }

    static CardholderProfile profile(String median) {
        return profile(median, List.of());
    }
}
