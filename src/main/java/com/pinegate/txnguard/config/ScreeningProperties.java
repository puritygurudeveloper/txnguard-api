package com.pinegate.txnguard.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Set;

/**
 * Every threshold in the screening engine, in one place.
 *
 * <p>Thresholds are configuration, not constants. Fraud patterns move faster than
 * release cycles, and the alternative to this class is a set of magic numbers scattered
 * across five rules that can only be changed by a deployment.
 */
@Validated
@ConfigurationProperties(prefix = "txnguard.screening")
public record ScreeningProperties(
        @Min(1) int reviewThreshold,
        @Min(1) int declineThreshold,
        @Positive double amountDeviationMultiple,
        @Min(1) int velocityMaxTransactions,
        Duration velocityWindow,
        @Positive double impossibleTravelSpeedKmh,
        @Min(0) int newAccountDays,
        Set<String> highRiskMerchantCategoryCodes) {

    public ScreeningProperties {
        if (declineThreshold <= reviewThreshold) {
            throw new IllegalArgumentException(
                    "declineThreshold (%d) must exceed reviewThreshold (%d), otherwise no transaction is ever merely reviewed"
                            .formatted(declineThreshold, reviewThreshold));
        }
        highRiskMerchantCategoryCodes = Set.copyOf(highRiskMerchantCategoryCodes);
    }

    /** Defaults matching src/main/resources/application.yml, for use in unit tests. */
    public static ScreeningProperties defaults() {
        return new ScreeningProperties(
                40,
                75,
                8.0,
                5,
                Duration.ofMinutes(10),
                900.0,
                30,
                Set.of("7995", "6051", "4829", "5967"));
    }
}
