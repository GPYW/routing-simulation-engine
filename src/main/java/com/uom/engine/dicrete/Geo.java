package com.uom.engine.discrete;

import java.util.List;

public final class Geo {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Geo() { }

    /** Haversine formula: distance in km between two lat/lon points, along the Earth's surface. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double haversineKm(Vertex a, Vertex b) {
        return haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }

    /**
     * Each vertex as a 3-D point on a sphere of radius R: [x0,y0,z0, x1,y1,z1, ...].
     * Computed once, so the heuristic needs no trigonometry per call.
     */
    public static double[] toCartesian(List<Vertex> vs) {
        double[] xyz = new double[vs.size() * 3];
        for (Vertex v : vs) {
            double lat = Math.toRadians(v.lat()), lon = Math.toRadians(v.lon());
            xyz[3 * v.id()]     = EARTH_RADIUS_KM * Math.cos(lat) * Math.cos(lon);
            xyz[3 * v.id() + 1] = EARTH_RADIUS_KM * Math.cos(lat) * Math.sin(lon);
            xyz[3 * v.id() + 2] = EARTH_RADIUS_KM * Math.sin(lat);
        }
        return xyz;
    }

    /**
     * Straight chord through the Earth between two vertices. Chord <= great-circle arc <= road
     * length, so it never overestimates, and it satisfies the triangle inequality.
     */
    public static double chordKm(double[] xyz, int a, int b) {
        double dx = xyz[3 * a] - xyz[3 * b];
        double dy = xyz[3 * a + 1] - xyz[3 * b + 1];
        double dz = xyz[3 * a + 2] - xyz[3 * b + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}