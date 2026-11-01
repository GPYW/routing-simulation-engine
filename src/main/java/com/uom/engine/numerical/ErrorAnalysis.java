package com.uom.engine.numerical;

import java.util.function.DoubleUnaryOperator;

public final class ErrorAnalysis {

    private ErrorAnalysis() { }

    public static void run() {
        System.out.println("=== Machine epsilon (gap between 1.0 and the next number) ===");
        System.out.printf("FP32 (float):  %.3e  (~7 decimal digits)%n", Math.ulp(1.0f));
        System.out.printf("FP64 (double): %.3e  (~16 decimal digits)%n%n", Math.ulp(1.0));

        System.out.println("=== Accumulating 0.1 per edge along a long route (exact total = n / 10) ===");
        System.out.printf("%10s %16s %16s %16s %16s%n", "edges", "float", "float+Kahan", "double", "double+Kahan");
        for (int n : new int[]{1_000, 100_000, 10_000_000}) {
            float f = 0f, fc = 0f, fs = 0f;   // naive float; Kahan float (compensation + sum)
            double d = 0.0;
            KahanSum k = new KahanSum();
            for (int i = 0; i < n; i++) {
                f += 0.1f;
                float y = 0.1f - fc; float t = fs + y; fc = (t - fs) - y; fs = t;   // classic Kahan, in float
                d += 0.1;
                k.add(0.1);
            }
            double exact = n / 10.0;
            System.out.printf("%10d %16.4e %16.4e %16.4e %16.4e   (relative error)%n", n,
                    Math.abs(f - exact) / exact, Math.abs(fs - exact) / exact,
                    Math.abs(d - exact) / exact, Math.abs(k.value() - exact) / exact);
        }

        System.out.println();
        System.out.println("=== Integration error on ∫_0^π sin(x) dx = 2 ===");
        System.out.printf("%6s %14s %8s %14s %8s%n", "n", "trapezoid err", "order", "simpson err", "order");
        DoubleUnaryOperator sin = Math::sin;
        double prevT = Double.NaN, prevS = Double.NaN;
        for (int n = 2; n <= 16384; n *= 2) {
            double eT = Math.abs(NumericalSolver.trapezoid(sin, 0, Math.PI, n) - 2.0);
            double eS = Math.abs(NumericalSolver.simpson(sin, 0, Math.PI, n) - 2.0);
            // observed order p = log2(error(n/2) / error(n)); expect 2 and 4
            System.out.printf("%6d %14.3e %8s %14.3e %8s%n", n,
                    eT, Double.isNaN(prevT) ? "" : String.format("%.2f", log2(prevT / eT)),
                    eS, Double.isNaN(prevS) ? "" : String.format("%.2f", log2(prevS / eS)));
            prevT = eT; prevS = eS;
        }
        System.out.println("Simpson's order drops once its error reaches ~1e-15: that is FP64 round-off,");
        System.out.println("not the method. More intervals past that point only add cost.");
    }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }
}