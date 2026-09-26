package com.pinegate.txnguard.adapter.web.dto;

import com.pinegate.txnguard.domain.Channel;
import com.pinegate.txnguard.domain.GeoPoint;
import com.pinegate.txnguard.domain.Money;
import com.pinegate.txnguard.domain.Transaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Wire format for an authorisation screening request.
 *
 * <p>A separate type from {@link Transaction} so that the public contract can stay
 * stable while the domain model evolves, and so that malformed input is rejected by bean
 * validation before it can reach a domain constructor.
 */
public record ScreeningRequest(
        @NotBlank String transactionId,
        @NotBlank String cardholderId,
        @NotBlank String merchantId,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must be a four-digit MCC")
        String merchantCategoryCode,
        @NotNull @DecimalMin(value = "0.00", message = "amount cannot be negative")
        BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code")
        String currency,
        @NotNull Channel channel,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "must be ISO 3166-1 alpha-2")
        String countryCode,
        Double latitude,
        Double longitude,
        Instant occurredAt) {

    public Transaction toDomain(Instant fallbackTimestamp) {
        GeoPoint point = (latitude != null && longitude != null)
                ? new GeoPoint(latitude, longitude)
                : null;
        return new Transaction(
                transactionId,
                cardholderId,
                merchantId,
                merchantCategoryCode,
                new Money(amount, Currency.getInstance(currency)),
                channel,
                countryCode,
                point,
                occurredAt != null ? occurredAt : fallbackTimestamp);
    }
}
