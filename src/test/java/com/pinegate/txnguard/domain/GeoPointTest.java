package com.pinegate.txnguard.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeoPointTest {

    private static final GeoPoint AUSTIN = new GeoPoint(30.2672, -97.7431);
    private static final GeoPoint DALLAS = new GeoPoint(32.7767, -96.7970);
    private static final GeoPoint LONDON = new GeoPoint(51.5074, -0.1278);

    @Test
    void matchesKnownGreatCircleDistances() {
        // Published great-circle values: Austin-Dallas 293 km, Austin-London 7,910 km.
        assertThat(AUSTIN.distanceKmTo(DALLAS)).isCloseTo(293.0, within(3.0));
        assertThat(AUSTIN.distanceKmTo(LONDON)).isCloseTo(7910.0, within(15.0));
    }

    @Test
    void isSymmetricAndZeroAgainstItself() {
        assertThat(AUSTIN.distanceKmTo(DALLAS)).isCloseTo(DALLAS.distanceKmTo(AUSTIN), within(1e-9));
        assertThat(AUSTIN.distanceKmTo(AUSTIN)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void staysPreciseOverShortDistances() {
        // The regime where the law of cosines loses precision: two terminals ~1.1 km
        // apart. Distinguishing this from zero is the whole point of the travel rule.
        GeoPoint nearby = new GeoPoint(30.2672 + 0.01, -97.7431);
        assertThat(AUSTIN.distanceKmTo(nearby)).isCloseTo(1.11, within(0.02));
    }

    @ParameterizedTest
    @CsvSource({"91.0, 0.0", "-90.1, 0.0", "0.0, 180.1", "0.0, -181.0"})
    void rejectsCoordinatesOffTheGlobe(double latitude, double longitude) {
        assertThatThrownBy(() -> new GeoPoint(latitude, longitude))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
