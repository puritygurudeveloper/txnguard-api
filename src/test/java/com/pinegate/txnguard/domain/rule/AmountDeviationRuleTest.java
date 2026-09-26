package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.Money;
import com.pinegate.txnguard.domain.RuleSignal;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.pinegate.txnguard.domain.rule.RuleTestSupport.profile;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.transaction;
import static org.assertj.core.api.Assertions.assertThat;

class AmountDeviationRuleTest {

    private final AmountDeviationRule rule = new AmountDeviationRule(ScreeningProperties.defaults());

    @Test
    void staysQuietWithinTheNormalRange() {
        // 7x the median, below the configured 8x threshold.
        assertThat(rule.evaluate(transaction("700.00"), profile("100.00"))).isEmpty();
    }

    @Test
    void firesAndExplainsItselfOnALargeDeviation() {
        Optional<RuleSignal> signal = rule.evaluate(transaction("1200.00"), profile("100.00"));

        assertThat(signal).isPresent();
        assertThat(signal.get().code()).isEqualTo("AMOUNT_DEVIATION");
        assertThat(signal.get().points()).isPositive();
        assertThat(signal.get().explanation()).contains("12.0x").contains("100.00 USD");
    }

    @Test
    void scoresSublinearlySoOneHugeAmountDoesNotSaturateAlone() {
        int at12x = rule.evaluate(transaction("1200.00"), profile("100.00")).orElseThrow().points();
        int at200x = rule.evaluate(transaction("20000.00"), profile("100.00")).orElseThrow().points();

        assertThat(at200x).isGreaterThan(at12x);
        // Log scaling: 200x must not score anywhere near 16x the points of 12x.
        assertThat(at200x).isLessThan(3 * at12x);
        assertThat(at200x).isLessThanOrEqualTo(45);
    }

    @Test
    void staysQuietWhenThereIsNoBaselineToDeviateFrom() {
        // A first-ever transaction is the new-account rule's business, not this one's.
        CardholderProfile noHistory = CardholderProfile.empty("card-1", Money.of("0.00", "USD"));
        assertThat(rule.evaluate(transaction("9999.00"), noHistory)).isEmpty();
    }

    @Test
    void comparesAgainstTheMedianNotTheMean() {
        // One previous large purchase must not raise the bar for the next one; the
        // profile exposes a median precisely so this rule cannot be masked that way.
        assertThat(rule.evaluate(transaction("1000.00"), profile("50.00"))).isPresent();
    }
}
