package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.RuleSignal;
import com.pinegate.txnguard.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fires when an amount dwarfs what this cardholder normally spends.
 *
 * <p>Compared against the <em>median</em>, not the mean. A single previous large
 * purchase drags a mean upwards far enough to hide the next one, which is precisely the
 * pattern a tester probing card limits produces.
 */
@Component
public class AmountDeviationRule implements FraudRule {

    static final String CODE = "AMOUNT_DEVIATION";

    private final ScreeningProperties properties;

    public AmountDeviationRule(ScreeningProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleSignal> evaluate(Transaction transaction, CardholderProfile profile) {
        // No baseline means no deviation to measure. A first transaction is handled by
        // the new-account rule, not by treating an unknown median as if it were zero.
        if (profile.medianTransactionAmount().amount().signum() == 0) {
            return Optional.empty();
        }

        double multiple = transaction.amount().ratioTo(profile.medianTransactionAmount());
        if (multiple < properties.amountDeviationMultiple()) {
            return Optional.empty();
        }

        // Points grow with the log of the excess so that a 200x transaction does not
        // score twenty times a 10x one; both are simply "far outside normal".
        double excess = multiple / properties.amountDeviationMultiple();
        int points = (int) Math.min(45, Math.round(22 + 14 * Math.log(excess + 1)));

        return Optional.of(new RuleSignal(
                CODE,
                points,
                "Amount %s is %.1fx the cardholder's median of %s"
                        .formatted(transaction.amount(), multiple, profile.medianTransactionAmount())));
    }
}
