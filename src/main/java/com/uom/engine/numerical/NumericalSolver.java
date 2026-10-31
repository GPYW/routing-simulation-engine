package com.uom.engine.numerical;

import java.util.function.DoubleUnaryOperator;

public final class NumericalSolver{
    private NumericalSolver() { }  // no instances

    public static double simpson(DoubleUnaryOperator f, double a, double b, int n) {
        if (a==b) return 0.0;
        if (n < 2) n=2;
        if (n % 2 != 0) n++;
        double h = (b - a) / n;
        double oddSum = 0.0, evenSum = 0.0;
        for(int i = 1; i < n; i++) {
            double x =a + i * h;
            if ((i & 1) == 1) oddSum += f.applyAsDouble(x);
            else evenSum += f.applyAsDouble(x);
        }
        return h / 3.0 * (f.applyAsDouble(a) + f.applyAsDouble(b) + 4.0 * oddSum + 2.0 * evenSum);
    }

    public static double trapezoid(DoubleUnaryOperator f, double a, double b, int n) {
        if (a == b) return 0.0;
        if (n < 1) n = 1;                        // guard: n = 0 would divide by zero
        double h = (b - a) / n;
        double sum = 0.5 * (f.applyAsDouble(a) + f.applyAsDouble(b));
        for (int i = 1; i < n; i++) sum += f.applyAsDouble(a + i * h);
        return h * sum;
    }

    public record RootResult(double root, int iterations, boolean converged, int bisectionSteps, String note) { }

    public static RootResult newtonRaphson(DoubleUnaryOperator f, DoubleUnaryOperator df,
                                           double x0, double relTol, int maxIterations) {
        double x = x0;
        for (int i = 1; i <= maxIterations; i++) {
            double fx = f.applyAsDouble(x);
            if (fx == 0.0) return new RootResult(x, i, true, 0, "exact root");
            double dfx = df.applyAsDouble(x);
            if (!Double.isFinite(dfx) || Math.abs(dfx) < 1e-14) {
                return new RootResult(x, i, false, 0, "derivative vanished");
            }
            double next = x - fx / dfx;
            if (!Double.isFinite(next)) return new RootResult(x, i, false, 0, "diverged");
            if (Math.abs(next - x) <= tolerance(next, relTol)) return new RootResult(next, i, true, 0, "step below tolerance");
            x = next;
        }
        return new RootResult(x, maxIterations, false, 0, "hit iteration limit");
    }

    public static RootResult safeguardedNewton(DoubleUnaryOperator f, DoubleUnaryOperator df,
                                               double lo, double hi, double relTol, int maxIterations) {
        double flo = f.applyAsDouble(lo), fhi = f.applyAsDouble(hi);
        if (flo == 0.0) return new RootResult(lo, 0, true, 0, "root at bracket end");
        if (fhi == 0.0) return new RootResult(hi, 0, true, 0, "root at bracket end");
        if (Math.signum(flo) == Math.signum(fhi)) {
            throw new IllegalArgumentException("Root not bracketed: f(lo)=" + flo + ", f(hi)=" + fhi);
        }
        if (flo > 0) { double t = lo; lo = hi; hi = t; }   // orient so that f(lo) < 0 < f(hi)

        double x = 0.5 * (lo + hi);
        int bisections = 0;
        for (int i = 1; i <= maxIterations; i++) {
            double fx = f.applyAsDouble(x);
            if (fx == 0.0) return new RootResult(x, i, true, bisections, "exact root");
            if (fx < 0) lo = x; else hi = x;                // shrink the bracket
            if (Math.abs(hi - lo) <= tolerance(x, relTol)) {
                return new RootResult(0.5 * (lo + hi), i, true, bisections, "bracket below tolerance");
            }

            double dfx = df.applyAsDouble(x);
            double next = x - fx / dfx;
            double min = Math.min(lo, hi), max = Math.max(lo, hi);
            if (!Double.isFinite(next) || next <= min || next >= max) {
                next = 0.5 * (lo + hi);                      // Newton left the bracket: bisect
                bisections++;
            }
            if (Math.abs(next - x) <= tolerance(next, relTol)) {
                return new RootResult(next, i, true, bisections, "step below tolerance");
            }
            x = next;
        }
        return new RootResult(x, maxIterations, false, bisections, "hit iteration limit");
    }

    static double tolerance(double x, double relTol) {
        return Math.max(relTol * Math.abs(x), 4 * Math.ulp(x));
    }
}