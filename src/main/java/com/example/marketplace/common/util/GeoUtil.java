package com.example.marketplace.common.util;

import com.example.marketplace.common.exception.ValidationException;
import java.util.Map;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/** Helpers for optional latitude/longitude input and distance maths. */
public final class GeoUtil {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtil() {
    }

    /**
     * Builds a GeoJSON point (longitude first, as MongoDB expects).
     * Returns null when both values are empty; throws if only one is supplied.
     */
    public static GeoJsonPoint toPoint(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return null;
        }
        if (latitude == null || longitude == null) {
            throw new ValidationException("Please fix the highlighted fields.",
                    Map.of(latitude == null ? "latitude" : "longitude",
                            "Enter both latitude and longitude, or leave both empty."));
        }
        return new GeoJsonPoint(longitude, latitude);
    }

    /** Great-circle distance in kilometres (haversine). */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
