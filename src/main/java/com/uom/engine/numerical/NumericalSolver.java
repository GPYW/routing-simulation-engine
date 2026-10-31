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

    // 4.2 adds: RootResult, newtonRaphson, safeguardedNewton, tolerance
}