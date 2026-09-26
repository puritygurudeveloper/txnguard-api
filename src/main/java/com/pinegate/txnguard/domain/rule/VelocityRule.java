package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.RuleSignal;
import com.pinegate.txnguard.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fires on a burst of transactions in a short window — the signature of an automated
 * card-testing script working through a stolen number.
 */
@Component
public class VelocityRule implements FraudRule {

    static final String CODE = "VELOCITY";

    private final ScreeningProperties properties;

    public VelocityRule(ScreeningProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleSignal> evaluate(Transaction transaction, CardholderProfile profile) {
        List<Transaction> recent = profile.precedingWithin(transaction, properties.velocityWindow());
        // The transaction under assessment counts towards its own burst; excluding it
        // means the limit is silently one higher than configured.
        int count = recent.size() + 1;
        if (count <= properties.velocityMaxTransactions()) {
            return Optional.empty();
        }

        int excess = count - properties.velocityMaxTransactions();
        int points = Math.min(50, 20 + 10 * excess);

        return Optional.of(new RuleSignal(
                CODE,
                points,
                "%d transactions within %s, limit is %d"
                        .formatted(count, properties.velocityWindow(), properties.velocityMaxTransactions())));
    }
}
