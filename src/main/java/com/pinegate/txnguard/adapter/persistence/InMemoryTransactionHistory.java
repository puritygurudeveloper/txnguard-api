package com.pinegate.txnguard.adapter.persistence;

import com.pinegate.txnguard.application.port.TransactionHistory;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.Money;
import com.pinegate.txnguard.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory behavioural history.
 *
 * <p>Stands in for the feature store this would read in production. It is behind the
 * {@link TransactionHistory} port precisely so that substitution is a one-class change,
 * and it is written to be correct under concurrency rather than treated as a stub:
 * screening runs on the authorisation path and is called from many threads at once.
 *
 * <p>Only the most recent {@value #HISTORY_DEPTH} transactions per cardholder are kept.
 * Velocity and travel rules look at minutes, and an unbounded per-cardholder list is a
 * memory leak with a long fuse.
 */
@Component
class InMemoryTransactionHistory implements TransactionHistory {

    private static final int HISTORY_DEPTH = 100;
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("USD");

    private final Map<String, Deque<Transaction>> byCardholder = new ConcurrentHashMap<>();

    @Override
    public CardholderProfile profileFor(String cardholderId) {
        Deque<Transaction> history = byCardholder.get(cardholderId);
        if (history == null) {
            return CardholderProfile.empty(cardholderId, Money.zero(DEFAULT_CURRENCY));
        }

        List<Transaction> snapshot;
        synchronized (history) {
            snapshot = List.copyOf(history);
        }
        if (snapshot.isEmpty()) {
            return CardholderProfile.empty(cardholderId, Money.zero(DEFAULT_CURRENCY));
        }

        Currency currency = snapshot.get(0).amount().currency();
        List<Transaction> sameCurrency = snapshot.stream()
                .filter(t -> t.amount().currency().equals(currency))
                .toList();

        return new CardholderProfile(
                cardholderId,
                median(sameCurrency, currency),
                largest(sameCurrency, currency),
                snapshot.stream().map(Transaction::merchantId).collect(Collectors.toUnmodifiableSet()),
                snapshot.stream().map(Transaction::countryCode).collect(Collectors.toUnmodifiableSet()),
                snapshot,
                accountAgeDays(snapshot));
    }

    @Override
    public void record(Transaction transaction) {
        // computeIfAbsent gives one deque per cardholder; the synchronized block then
        // makes the bounded append atomic. Contention is per cardholder, not global.
        Deque<Transaction> history =
                byCardholder.computeIfAbsent(transaction.cardholderId(), key -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(transaction);
            while (history.size() > HISTORY_DEPTH) {
                history.removeFirst();
            }
        }
    }

    private static Money median(List<Transaction> history, Currency currency) {
        if (history.isEmpty()) {
            return Money.zero(currency);
        }
        List<BigDecimal> amounts = new ArrayList<>(
                history.stream().map(t -> t.amount().amount()).sorted().toList());
        int middle = amounts.size() / 2;
        if (amounts.size() % 2 == 1) {
            return new Money(amounts.get(middle), currency);
        }
        return new Money(
                amounts.get(middle - 1).add(amounts.get(middle)).divide(BigDecimal.valueOf(2)), currency);
    }

    private static Money largest(List<Transaction> history, Currency currency) {
        return history.stream()
                .map(Transaction::amount)
                .max(Comparator.naturalOrder())
                .orElse(Money.zero(currency));
    }

    private static int accountAgeDays(List<Transaction> history) {
        return history.stream()
                .map(Transaction::occurredAt)
                .min(Comparator.naturalOrder())
                .map(first -> (int) java.time.Duration.between(first, java.time.Instant.now()).toDays())
                .orElse(0);
    }
}
