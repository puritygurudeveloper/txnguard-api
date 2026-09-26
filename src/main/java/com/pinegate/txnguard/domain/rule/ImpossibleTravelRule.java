package com.pinegate.txnguard.domain.rule;

import com.pinegate.txnguard.config.ScreeningProperties;
import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.GeoPoint;
import com.pinegate.txnguard.domain.RuleSignal;
import com.pinegate.txnguard.domain.Transaction;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Fires when the cardholder would have had to travel faster than commercial aviation to
 * be present at both this transaction and the previous one.
 *
 * <p>Applies only when <em>both</em> transactions were card-present. An e-commerce
 * purchase carries the merchant's geolocation, not the cardholder's, and treating it as
 * a position fix generates an impossible-travel alert every time somebody buys
 * something from an overseas website.
 */
@Component
public class ImpossibleTravelRule implements FraudRule {

    static final String CODE = "IMPOSSIBLE_TRAVEL";

    private final ScreeningProperties properties;

    public ImpossibleTravelRule(ScreeningProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleSignal> evaluate(Transaction transaction, CardholderProfile profile) {
        if (!transaction.channel().impliesPhysicalPresence()) {
            return Optional.empty();
        }
        Optional<GeoPoint> here = transaction.locationIfKnown();
        if (here.isEmpty()) {
            return Optional.empty();
        }

        Optional<Transaction> previous = profile.lastLocatedBefore(transaction)
                .filter(t -> t.channel().impliesPhysicalPresence());
        if (previous.isEmpty()) {
            return Optional.empty();
        }

        Transaction last = previous.get();
        double distanceKm = last.location().distanceKmTo(here.get());
        Duration elapsed = Duration.between(last.occurredAt(), transaction.occurredAt());

        // Two transactions at the same instant in different places are impossible at any
        // speed; guarding the division also stops a clock-skewed feed dividing by zero.
        double hours = elapsed.toMillis() / 3_600_000.0;
        if (hours <= 0) {
            return distanceKm > 1.0
                    ? Optional.of(new RuleSignal(CODE, 50,
                            "Two card-present transactions %.0f km apart with no elapsed time".formatted(distanceKm)))
                    : Optional.empty();
        }

        double impliedSpeed = distanceKm / hours;
        if (impliedSpeed <= properties.impossibleTravelSpeedKmh()) {
            return Optional.empty();
        }

        int points = impliedSpeed > 3 * properties.impossibleTravelSpeedKmh() ? 50 : 35;
        return Optional.of(new RuleSignal(
                CODE,
                points,
                "Implied travel of %.0f km/h between %s and %s (%.0f km in %s)"
                        .formatted(impliedSpeed, last.countryCode(), transaction.countryCode(), distanceKm, elapsed)));
    }
}
