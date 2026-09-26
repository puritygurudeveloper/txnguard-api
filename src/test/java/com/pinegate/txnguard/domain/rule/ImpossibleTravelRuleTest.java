package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.Channel;
import com.pinegate.txnguard.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.pinegate.txnguard.domain.rule.RuleTestSupport.AUSTIN;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.LONDON;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.NOW;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.profile;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.transaction;
import static org.assertj.core.api.Assertions.assertThat;

class ImpossibleTravelRuleTest {

    private final ImpossibleTravelRule rule = new ImpossibleTravelRule(ScreeningProperties.defaults());

    private static Transaction cardPresent(String country, com.pinegate.txnguard.domain.GeoPoint where, Duration ago) {
        return transaction("50.00", Channel.CARD_PRESENT, country, where, NOW.minus(ago));
    }

    @Test
    void firesWhenTheImpliedSpeedExceedsAnAircraft() {
        // Austin to London in one hour is roughly 7,900 km/h.
        Transaction here = transaction("50.00", Channel.CARD_PRESENT, "GB", LONDON, NOW);
        var signal = rule.evaluate(here, profile("50.00", List.of(cardPresent("US", AUSTIN, Duration.ofHours(1)))));

        assertThat(signal).isPresent();
        assertThat(signal.get().code()).isEqualTo("IMPOSSIBLE_TRAVEL");
        assertThat(signal.get().explanation()).contains("US").contains("GB");
    }

    @Test
    void staysQuietWhenTheTravelIsMerelyALongFlight() {
        // The same journey over eleven hours is an ordinary transatlantic trip.
        Transaction here = transaction("50.00", Channel.CARD_PRESENT, "GB", LONDON, NOW);
        assertThat(rule.evaluate(here, profile("50.00", List.of(cardPresent("US", AUSTIN, Duration.ofHours(11))))))
                .isEmpty();
    }

    @Test
    void ignoresCardNotPresentTransactionsEntirely() {
        // An e-commerce purchase carries the merchant's location, not the cardholder's.
        // Treating it as a position fix alerts on every overseas website purchase.
        Transaction online = transaction("50.00", Channel.ECOMMERCE, "GB", LONDON, NOW);
        assertThat(rule.evaluate(online, profile("50.00", List.of(cardPresent("US", AUSTIN, Duration.ofHours(1))))))
                .isEmpty();
    }

    @Test
    void ignoresACardNotPresentPredecessor() {
        Transaction here = transaction("50.00", Channel.CARD_PRESENT, "GB", LONDON, NOW);
        Transaction onlineEarlier = transaction("50.00", Channel.ECOMMERCE, "US", AUSTIN, NOW.minus(Duration.ofHours(1)));
        assertThat(rule.evaluate(here, profile("50.00", List.of(onlineEarlier)))).isEmpty();
    }

    @Test
    void handlesSimultaneousTransactionsWithoutDividingByZero() {
        // Clock skew in a payment feed genuinely produces identical timestamps.
        Transaction here = transaction("50.00", Channel.CARD_PRESENT, "GB", LONDON, NOW);
        var signal = rule.evaluate(here, profile("50.00", List.of(cardPresent("US", AUSTIN, Duration.ZERO))));
        assertThat(signal).isPresent();
        assertThat(signal.get().explanation()).contains("no elapsed time");
    }

    @Test
    void staysQuietWithNoPriorLocation() {
        Transaction here = transaction("50.00", Channel.CARD_PRESENT, "GB", LONDON, NOW);
        assertThat(rule.evaluate(here, profile("50.00", List.of()))).isEmpty();
    }
}
