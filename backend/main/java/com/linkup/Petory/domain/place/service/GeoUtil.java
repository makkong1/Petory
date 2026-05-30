package com.linkup.Petory.domain.place.service;

public final class GeoUtil {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtil() {}

    /** Haversine 공식으로 두 좌표 사이 거리(미터) 계산. */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** meters 반경을 커버하는 lat/lng 오프셋(도). bounding box 사전 필터에 사용. */
    public static double latLngDeltaForMeters(double meters) {
        return meters / EARTH_RADIUS_M * (180.0 / Math.PI);
    }
}
