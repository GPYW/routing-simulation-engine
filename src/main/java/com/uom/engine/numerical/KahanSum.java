package com.uom.engine.numerical;

public final class KahanSum {
    private double sum;
    private double compensation;

    public void add(double x) {
        double t = sum + x;
        if (Math.abs(sum) >= Math.abs(x)) compensation += (sum - t) + x;   // low bits of x were lost
        else                              compensation += (x - t) + sum;   // low bits of sum were lost
        sum = t;
    }

    public double value() { return sum + compensation; }
}