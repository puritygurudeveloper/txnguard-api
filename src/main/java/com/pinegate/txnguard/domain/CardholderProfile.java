package com.pinegate.txnguard.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What is known about a cardholder's normal behaviour at screening time.
 *
 * <p>This is a snapshot, assembled once per screening request and passed to every rule.
 * Rules therefore cannot issue their own queries, which keeps a screening decision a
 * pure function of (transaction, profile, configuration) — reproducible in a test and
 * replayable in a dispute six months later.
 */
public record CardholderProfile(
        String cardholderId,
        Money medianTransactionAmount,
        Money largestEverAmount,
        Set<String> knownMerchantIds,
        Set<String> knownCountryCodes,
        List<Transaction> recentTransactions,
        int accountAgeDays) {

    public CardholderProfile {
        Objects.requireNonNull(cardholderId, "cardholderId");
        Objects.requireNonNull(medianTransactionAmount, "medianTransactionAmount");
        Objects.requireNonNull(largestEverAmount, "largestEverAmount");
        knownMerchantIds = Set.copyOf(knownMerchantIds);
        knownCountryCodes = Set.copyOf(knownCountryCodes);
        recentTransactions = List.copyOf(recentTransactions);
        if (accountAgeDays < 0) {
            throw new IllegalArgumentException("accountAgeDays cannot be negative");
        }
    }

    /** A profile for a cardholder with no history at all. */
    public static CardholderProfile empty(String cardholderId, Money zero) {
        return new CardholderProfile(cardholderId, zero, zero, Set.of(), Set.of(), List.of(), 0);
    }

    public boolean isNewAccount(int thresholdDays) {
        return accountAgeDays < thresholdDays;
    }

    public boolean hasSeenMerchant(String merchantId) {
        return knownMerchantIds.contains(merchantId);
    }

    public boolean hasSeenCountry(String countryCode) {
        return knownCountryCodes.contains(countryCode);
    }

    /**
     * Other transactions within {@code window} before {@code current}, most recent first.
     *
     * <p>Peers are excluded by transaction id rather than by timestamp. Screening is
     * idempotent, so a replayed authorisation is already in its own history; filtering
     * on {@code occurredAt} alone would let it count itself towards its own velocity
     * burst and manufacture a decline on the second delivery of the same message.
     *
     * <p>The window is inclusive at {@code current}'s instant, because payment feeds do
     * produce genuinely simultaneous timestamps under clock skew and those are exactly
     * the ones worth seeing.
     */
    public List<Transaction> precedingWithin(Transaction current, Duration window) {
        Instant cutoff = current.occurredAt().minus(window);
        return peersOf(current)
                .filter(t -> t.occurredAt().isAfter(cutoff))
                .filter(t -> !t.occurredAt().isAfter(current.occurredAt()))
                .sorted((a, b) -> b.occurredAt().compareTo(a.occurredAt()))
                .toList();
    }

    /** The most recent other transaction at or before {@code current} with a known location. */
    public Optional<Transaction> lastLocatedBefore(Transaction current) {
        return peersOf(current)
                .filter(t -> !t.occurredAt().isAfter(current.occurredAt()))
                .filter(t -> t.locationIfKnown().isPresent())
                .max((a, b) -> a.occurredAt().compareTo(b.occurredAt()));
    }

    private java.util.stream.Stream<Transaction> peersOf(Transaction current) {
        return recentTransactions.stream()
                .filter(t -> !t.transactionId().equals(current.transactionId()));
    }
}
