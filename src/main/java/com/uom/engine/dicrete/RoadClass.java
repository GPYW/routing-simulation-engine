package com.uom.engine.discrete;

/**
 * Physical class of an edge. Each class carries the constants the cost model needs.
 *
 * speedKmh       - free-flow speed, so transit time = km / speed
 * litresPerKm    - fuel burn at free flow
 * hazardPerHour  - base loss probability per hour in transit
 * capacity       - shipments/day the edge can carry (supply side of the fee market)
 * baseDemand     - shipments/day that want this edge at zero fee (demand side)
 */

public enum RoadClass{
    EXPRESSWAY(90, 0.10, 0.0008, 1200, 1500),
    HIGHWAY(50, 0.12, 0.0015, 600, 700),
    MOUNTAIN(30, 0.16, 0.0030, 250, 260);

    private final double speedKmh,
            litresPerKm,
            hazardPerHour,
            capacity,
            baseDemand;

    RoadClass(double speedKmh, double litresPerKm, double hazardPerHour, double capacity, double baseDemand) {
        this.speedKmh = speedKmh;
        this.litresPerKm = litresPerKm;
        this.hazardPerHour = hazardPerHour;
        this.capacity = capacity;
        this.baseDemand = baseDemand;
    }

    public double speedKmh() {
        return speedKmh;
    }
    public double litresPerKm() {
        return litresPerKm;
    }
    public double hazardPerHour() {
        return hazardPerHour;
    }
    public double capacity() {
        return capacity;
    }
    public double baseDemand() {
        return baseDemand;
    }

        /** Smallest fuel burn of any class. The A* heuristic needs this lower bound. */
    public static double minLitresPerKm() {
        double min = Double.POSITIVE_INFINITY;
        for (RoadClass rc : RoadClass.values()) {
            min = Math.min(min, rc.litresPerKm());
        }
        return min;
    }
}