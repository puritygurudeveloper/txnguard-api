package com.pinegate.txnguard.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("scale is pinned to the currency so equality behaves")
    void normalisesScaleToTheCurrencyMinorUnit() {
        assertThat(Money.of("10.00", "USD")).isEqualTo(Money.of("10.000", "USD"));
        // JPY has no minor unit at all.
        assertThat(Money.of("1000.4", "JPY").amount()).isEqualTo(new BigDecimal("1000"));
    }

    @Test
    @DisplayName("rounding is half-even, not half-up")
    void roundsHalfEven() {
        // Half-up would bias a book of millions of transactions upwards.
        assertThat(Money.of("2.125", "USD").amount()).isEqualTo(new BigDecimal("2.12"));
        assertThat(Money.of("2.135", "USD").amount()).isEqualTo(new BigDecimal("2.14"));
    }

    @Test
    @DisplayName("mixing currencies throws instead of silently comparing")
    void refusesToMixCurrencies() {
        Money usd = Money.of("100.00", "USD");
        Money jpy = Money.of("100", "JPY");

        assertThatThrownBy(() -> usd.plus(jpy))
                .isInstanceOf(Money.CurrencyMismatchException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("JPY");
        assertThatThrownBy(() -> usd.compareTo(jpy)).isInstanceOf(Money.CurrencyMismatchException.class);
        assertThatThrownBy(() -> usd.ratioTo(jpy)).isInstanceOf(Money.CurrencyMismatchException.class);
    }

    @Test
    void addsAndSubtractsWithinACurrency() {
        assertThat(Money.of("10.50", "USD").plus(Money.of("4.25", "USD"))).isEqualTo(Money.of("14.75", "USD"));
        assertThat(Money.of("10.50", "USD").minus(Money.of("14.75", "USD"))).isEqualTo(Money.of("-4.25", "USD"));
    }

    @ParameterizedTest
    @CsvSource({"100.00, 10.00, 10.0", "5.00, 10.00, 0.5", "0.00, 10.00, 0.0"})
    void computesRatios(String left, String right, double expected) {
        assertThat(Money.of(left, "USD").ratioTo(Money.of(right, "USD"))).isEqualTo(expected);
    }

    @Test
    @DisplayName("a ratio against a zero baseline is infinite, not an exception")
    void treatsAZeroBaselineAsUnboundedDeviation() {
        // A first-ever transaction has no baseline; the rules read this as a signal.
        assertThat(Money.of("500.00", "USD").ratioTo(Money.zero(Currency.getInstance("USD"))))
                .isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(Money.zero(Currency.getInstance("USD")).ratioTo(Money.zero(Currency.getInstance("USD"))))
                .isEqualTo(1.0);
    }

    @Test
    void detectsNegativeAmountsAndOrdering() {
        assertThat(Money.of("-0.01", "USD").isNegative()).isTrue();
        assertThat(Money.of("10.00", "USD").isGreaterThan(Money.of("9.99", "USD"))).isTrue();
        assertThat(Money.of("10.00", "USD").isGreaterThan(Money.of("10.00", "USD"))).isFalse();
    }

    @Test
    void rendersPredictably() {
        assertThat(Money.of("1234.50", "USD")).hasToString("1234.50 USD");
    }
}
