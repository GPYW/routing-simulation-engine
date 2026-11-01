package com.uom.engine.numerical;

import com.uom.engine.discrete.Edge;
import com.uom.engine.discrete.RoadClass;
import java.util.function.DoubleUnaryOperator;

public final class CostFunctions {

    // Fee market constants
    static final double DEMAND_SENSITIVITY = 0.02;   // k: demand falls by e^{-k p}
    static final double SUPPLY_SCALE = 50.0;         // p0: supply reaches 63% of capacity at p = p0

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
    
     /** Evaluate all three parts for an edge, solving the fee rate for this hour. */
    public static EdgeCost evaluate(Edge e, double departHour, MarketConditions m) {
        return evaluate(e, departHour, m, equilibriumFeePerKm(e.roadClass(), departHour).root());
    }

    /**
     * Equilibrium fee per km: the price p* where demand meets supply.
     *   Demand D(p) = D0 x g(T) x e^{-k p}        (fewer shippers as the fee rises)
     *   Supply S(p) = C x (1 - e^{-p / p0})       (more capacity released as the fee rises)
     *   f(p) = D(p) - S(p),   f'(p) = -k D(p) - (C / p0) e^{-p / p0}
     * f(0) = D0 g > 0, f(p) -> -C < 0, and f' < 0 everywhere, so there is exactly one root.
     */
    public static NumericalSolver.RootResult equilibriumFeePerKm(RoadClass rc, double hour) {
        double d0 = rc.baseDemand() * congestion(hour);
        double cap = rc.capacity();
        double k = DEMAND_SENSITIVITY, p0 = SUPPLY_SCALE;
        DoubleUnaryOperator f  = p -> d0 * Math.exp(-k * p) - cap * (1 - Math.exp(-p / p0));
        DoubleUnaryOperator df = p -> -k * d0 * Math.exp(-k * p) - (cap / p0) * Math.exp(-p / p0);

        double hi = p0;
        while (f.applyAsDouble(hi) > 0) hi *= 2;    // grow the bracket until f changes sign
        NumericalSolver.RootResult r = NumericalSolver.safeguardedNewton(f, df, 0.0, hi, 1e-12, 100);
        if (!r.converged()) throw new ArithmeticException("Fee equilibrium did not converge for " + rc + " at " + hour);
        return r;
    }

    // 5.4 adds: lowerBoundPerKm()
}