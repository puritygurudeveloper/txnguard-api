package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.Channel;
import com.pinegate.txnguard.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static com.pinegate.txnguard.domain.rule.RuleTestSupport.NOW;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.profile;
import static com.pinegate.txnguard.domain.rule.RuleTestSupport.transaction;
import static org.assertj.core.api.Assertions.assertThat;

class VelocityRuleTest {

    private final VelocityRule rule = new VelocityRule(ScreeningProperties.defaults());

    private static List<Transaction> burst(int count, Duration spacing) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> transaction("20.00", Channel.ECOMMERCE, "US", null,
                        NOW.minus(spacing.multipliedBy(i))))
                .toList();
    }

    @Test
    void staysQuietAtTheLimit() {
        // Four priors plus the transaction under assessment is exactly the limit of 5.
        assertThat(rule.evaluate(transaction("20.00"), profile("20.00", burst(4, Duration.ofMinutes(1)))))
                .isEmpty();
    }

    @Test
    void countsTheTransactionUnderAssessmentTowardsItsOwnBurst() {
        // Five priors plus this one is six, one over. Excluding the current
        // transaction would make the effective limit six rather than five.
        assertThat(rule.evaluate(transaction("20.00"), profile("20.00", burst(5, Duration.ofMinutes(1)))))
                .isPresent();
    }

    @Test
    void scoresHarderAsTheBurstGrows() {
        int justOver = rule.evaluate(transaction("20.00"), profile("20.00", burst(5, Duration.ofMinutes(1))))
                .orElseThrow().points();
        int wayOver = rule.evaluate(transaction("20.00"), profile("20.00", burst(12, Duration.ofSeconds(20))))
                .orElseThrow().points();

        assertThat(wayOver).isGreaterThan(justOver).isLessThanOrEqualTo(50);
    }

    @Test
    void ignoresTransactionsOutsideTheWindow() {
        // Twenty transactions, one an hour: high volume, no burst.
        assertThat(rule.evaluate(transaction("20.00"), profile("20.00", burst(20, Duration.ofHours(1)))))
                .isEmpty();
    }

    @Test
    void ignoresTransactionsAfterTheOneBeingScreened() {
        // Replaying an old authorisation must not count the future against it.
        List<Transaction> future = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> transaction("20.00", Channel.ECOMMERCE, "US", null,
                        NOW.plus(Duration.ofMinutes(i))))
                .toList();
        assertThat(rule.evaluate(transaction("20.00"), profile("20.00", future))).isEmpty();
    }
}
