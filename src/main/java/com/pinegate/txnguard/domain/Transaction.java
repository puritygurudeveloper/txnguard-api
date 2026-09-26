package com.pinegate.txnguard.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An authorisation request being screened.
 *
 * <p>Immutable, and deliberately carries no mutable state about its own outcome: the
 * screening result is a separate value so that re-screening the same transaction with
 * a different rule set cannot corrupt the original.
 */
public record Transaction(
        String transactionId,
        String cardholderId,
        String merchantId,
        String merchantCategoryCode,
        Money amount,
        Channel channel,
        String countryCode,
        GeoPoint location,
        Instant occurredAt) {

    public Transaction {
        requireText(transactionId, "transactionId");
        requireText(cardholderId, "cardholderId");
        requireText(merchantId, "merchantId");
        requireText(merchantCategoryCode, "merchantCategoryCode");
        requireText(countryCode, "countryCode");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(occurredAt, "occurredAt");

        if (amount.isNegative()) {
            throw new IllegalArgumentException("transaction amount cannot be negative: " + amount);
        }
        if (countryCode.length() != 2) {
            throw new IllegalArgumentException("countryCode must be ISO 3166-1 alpha-2: " + countryCode);
        }
        countryCode = countryCode.toUpperCase();
    }

    /** Location is optional: card-not-present transactions frequently have none. */
    public Optional<GeoPoint> locationIfKnown() {
        return Optional.ofNullable(location);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
