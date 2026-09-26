package com.pinegate.txnguard.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary amount in a single currency.
 *
 * <p>{@code BigDecimal} rather than {@code double}, and the type exists at all so that
 * currency travels with the amount. A screening engine that compares a JPY amount
 * against a USD threshold does not throw; it silently approves a transaction a hundred
 * times larger than the limit, which is the kind of defect that is only ever found in
 * production.
 *
 * <p>The scale is pinned to the currency's minor unit on construction, so equality is
 * well defined: {@code 10.00 USD} and {@code 10.000 USD} are the same amount, whereas
 * raw {@code BigDecimal#equals} says otherwise.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int minorUnits = Math.max(currency.getDefaultFractionDigits(), 0);
        amount = amount.setScale(minorUnits, RoundingMode.HALF_EVEN);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(double amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    /** Scales the amount; the result keeps the currency's minor-unit scale. */
    public Money times(double factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    /**
     * Ratio of this amount to another in the same currency.
     *
     * <p>Returns {@link Double#POSITIVE_INFINITY} for a non-zero numerator over a zero
     * denominator rather than throwing: a first-ever transaction legitimately has no
     * baseline, and the rules treat unbounded deviation as a signal, not an error.
     */
    public double ratioTo(Money other) {
        requireSameCurrency(other);
        if (other.amount.signum() == 0) {
            return amount.signum() == 0 ? 1.0 : Double.POSITIVE_INFINITY;
        }
        return amount.doubleValue() / other.amount.doubleValue();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    /** Thrown when two amounts in different currencies are combined or compared. */
    public static final class CurrencyMismatchException extends IllegalArgumentException {
        public CurrencyMismatchException(Currency left, Currency right) {
            super("cannot combine %s with %s without an explicit conversion"
                    .formatted(left.getCurrencyCode(), right.getCurrencyCode()));
        }
    }
}
