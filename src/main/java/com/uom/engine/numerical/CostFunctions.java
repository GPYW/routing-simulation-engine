package com.uom.engine.numerical;

public final class CostFunctions {

    private CostFunctions() { }

    public static double congestion(double hourOfDay) {
        return 1.0 + 0.8 * bump(hourOfDay, 8.5, 1.0) + 0.6 * bump(hourOfDay, 17.5, 1.5);
    }

    /** Hazard multiplier: risk rises at night (fatigue, theft), peaking at 02:00. */
    public static double nightFactor(double hourOfDay) {
        return 1.0 + 1.5 * bump(hourOfDay, 2.0, 2.5);
    }

    /** Gaussian bump using the circular distance on a 24-hour clock (23:00 is 1h from 00:00). */
    static double bump(double t, double peak, double width) {
        double d = ((t - peak) % 24 + 36) % 24 - 12;   // maps any difference into [-12, 12)
        return Math.exp(-(d * d) / (2 * width * width));
    }

    // 5.2 adds: EdgeCost, EdgePhysics, physics(), price(), evaluate()
    // 5.3 adds: equilibriumFeePerKm()
    // 5.4 adds: lowerBoundPerKm()
}