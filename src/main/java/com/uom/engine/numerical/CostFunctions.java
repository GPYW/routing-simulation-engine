package com.uom.engine.numerical;

import com.uom.engine.discrete.Edge;
import com.uom.engine.discrete.RoadClass;

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

    /** One edge's cost split into its parts. */
    public record EdgeCost(double fuel, double risk, double fee, double hours) {
        public double total() { return fuel + risk + fee; }
    }

     public record EdgePhysics(double litres, double hazard, double hours) { }

    /** Expensive, market-independent part: integrate fuel burn and hazard over the trip. */
    public static EdgePhysics physics(Edge e, double departHour, int intervals) {
        RoadClass rc = e.roadClass();
        double hours = e.distanceKm() / rc.speedKmh();
        double arrive = departHour + hours;

        // (1) Fuel burn: litres per hour at free flow, scaled by congestion over the whole trip.
        double litresPerHour = rc.litresPerKm() * rc.speedKmh();
        double congestedHours = NumericalSolver.simpson(CostFunctions::congestion, departHour, arrive, intervals);

        // (2) Cumulative hazard: λ(t) = h0 x nightFactor(t), integrated over the trip.
        double h0 = rc.hazardPerHour();
        double cumulativeHazard = NumericalSolver.simpson(t -> h0 * nightFactor(t), departHour, arrive, intervals);

        return new EdgePhysics(litresPerHour * congestedHours, cumulativeHazard, hours);
    }

    /** Cheap, market-dependent part: turn physics into money. O(1) per edge. */
    public static EdgeCost price(Edge e, EdgePhysics p, MarketConditions m, double feePerKm) {
        double fuel = m.fuelPricePerLitre() * p.litres();
        double risk = m.cargoValue() * -Math.expm1(-p.hazard());   // P(loss) = 1 - e^(-∫λ)
        double fee = feePerKm * e.distanceKm();
        return new EdgeCost(fuel, risk, fee, p.hours());
    }

    /** Both steps together, with the fee rate supplied (5.3 adds a version that solves it). */
    public static EdgeCost evaluate(Edge e, double departHour, MarketConditions m, double feePerKm) {
        return price(e, physics(e, departHour, m.intervals()), m, feePerKm);
    }
    // 5.3 adds: equilibriumFeePerKm()
    // 5.4 adds: lowerBoundPerKm()
}