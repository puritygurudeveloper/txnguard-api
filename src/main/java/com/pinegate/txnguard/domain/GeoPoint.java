package com.pinegate.txnguard.domain;

/**
 * A point on the surface of the earth, used to reason about travel between
 * consecutive transactions.
 */
public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6_371.0088;

    public GeoPoint {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    /**
     * Great-circle distance in kilometres.
     *
     * <p>Haversine rather than the spherical law of cosines: the latter loses precision
     * catastrophically for short distances, which is exactly the regime that matters
     * when distinguishing two card-present transactions in the same city from two in
     * neighbouring countries.
     */
    public double distanceKmTo(GeoPoint other) {
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLon = Math.toRadians(other.longitude - longitude);
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);

        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
