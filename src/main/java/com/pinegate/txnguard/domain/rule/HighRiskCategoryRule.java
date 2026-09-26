package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.RuleSignal;
import com.pinegate.txnguard.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fires on merchant category codes with elevated chargeback rates — gambling, quasi-cash,
 * money transfer, and direct marketing.
 *
 * <p>Weak on its own by design. The category is a property of the merchant, not of the
 * transaction, so scoring it highly would decline every legitimate customer of an entire
 * industry. It exists to push a transaction that is already borderline over the line.
 */
@Component
public class HighRiskCategoryRule implements FraudRule {

    static final String CODE = "HIGH_RISK_MCC";

    private final ScreeningProperties properties;

    public HighRiskCategoryRule(ScreeningProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleSignal> evaluate(Transaction transaction, CardholderProfile profile) {
        String mcc = transaction.merchantCategoryCode();
        if (!properties.highRiskMerchantCategoryCodes().contains(mcc)) {
            return Optional.empty();
        }
        boolean familiar = profile.hasSeenMerchant(transaction.merchantId());
        return Optional.of(new RuleSignal(
                CODE,
                familiar ? 5 : 15,
                "Merchant category %s carries elevated chargeback risk%s"
                        .formatted(mcc, familiar ? " (cardholder has used this merchant before)" : "")));
    }
}
