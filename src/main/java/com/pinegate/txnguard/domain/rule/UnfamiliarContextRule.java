package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.RuleSignal;
import com.pinegate.txnguard.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fires on a combination of unfamiliar elements: a new merchant, a new country, or a
 * young account.
 *
 * <p>Any one of these alone is weak — everyone shops somewhere new eventually — so the
 * rule scores the <em>combination</em>. A new merchant in a new country on a two-week-old
 * account is a different proposition from a new merchant on a five-year-old account.
 */
@Component
public class UnfamiliarContextRule implements FraudRule {

    static final String CODE = "UNFAMILIAR_CONTEXT";

    private final ScreeningProperties properties;

    public UnfamiliarContextRule(ScreeningProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleSignal> evaluate(Transaction transaction, CardholderProfile profile) {
        List<String> unfamiliar = new ArrayList<>(3);
        int points = 0;

        if (!profile.hasSeenCountry(transaction.countryCode())) {
            unfamiliar.add("country " + transaction.countryCode());
            points += 18;
        }
        if (!profile.hasSeenMerchant(transaction.merchantId())) {
            unfamiliar.add("merchant " + transaction.merchantId());
            points += 8;
        }
        if (profile.isNewAccount(properties.newAccountDays())) {
            unfamiliar.add("account age " + profile.accountAgeDays() + "d");
            points += 12;
        }

        // A single unfamiliar element is ordinary behaviour and is not worth a signal.
        if (unfamiliar.size() < 2) {
            return Optional.empty();
        }

        return Optional.of(new RuleSignal(
                CODE, Math.min(points, 40), "Unfamiliar: " + String.join(", ", unfamiliar)));
    }
}
