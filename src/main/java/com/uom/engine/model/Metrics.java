package com.uom.engine.model;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class Metrics {
    private final LongAdder queries = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final LongAdder totalSettled = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong();

    void record(long nanos, int settled, boolean ok) {
        queries.increment();
        if (!ok) failures.increment();
        totalNanos.add(nanos);
        totalSettled.add(settled);
        maxNanos.accumulateAndGet(nanos, Math::max);
    }

    public long queries() { 
        return queries.sum(); 
    }
    public long failures() { 
        return failures.sum(); 
    }
    public double avgMillis() { 
        long q = queries(); 
        return q == 0 ? 0 : totalNanos.sum() / 1e6 / q; 
    }
    public double maxMillis() { 
        return maxNanos.get() / 1e6; 
    }
    public double avgSettled() { 
        long q = queries(); 
        return q == 0 ? 0 : (double) totalSettled.sum() / q; 
    }
}