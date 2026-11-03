package com.uom.engine.bench;

import com.uom.engine.discrete.*;
import com.uom.engine.model.NetworkFactory;
import com.uom.engine.model.RouteQuery;
import com.uom.engine.model.RoutingEngine;
import com.uom.engine.numerical.CostFunctions;
import com.uom.engine.numerical.MarketConditions;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Benchmark {
    /** Shipments leave at a handful of scheduled times, so the weight cache is reused. */
    static final double[] DISPATCH_HOURS = {6.0, 8.0, 8.5, 12.0, 15.0, 17.5, 20.0, 23.0};

    public static void run() throws Exception {
        scaling();
        dijkstraVsAStar();
        concurrency();
    }

    static void scaling() {
        System.out.println("=== (1) Scaling of a full Dijkstra sweep ===");
        System.out.printf("%9s %10s %10s %14s %22s%n", "V", "E", "avg ms", "(V+E)log2V", "ns per (V+E)log2V");
        MarketConditions m = MarketConditions.defaults();
        for (int v : new int[]{1_000, 10_000, 100_000, 400_000}) {
            Graph g = NetworkFactory.randomGrid(v, 42);
            double[] w = weights(g, 8.0, m);
            PathFinder.EdgeWeight ew = e -> w[e.id()];
            for (int i = 0; i < 5; i++) PathFinder.dijkstra(g, i, -1, ew, null);    // JIT warm-up
            int runs = v >= 100_000 ? 5 : 20;
            long t0 = System.nanoTime();
            for (int i = 0; i < runs; i++) PathFinder.dijkstra(g, (i * 7919) % g.vertexCount(), -1, ew, null);
            double ms = (System.nanoTime() - t0) / 1e6 / runs;
            int V = g.vertexCount(), E = g.edgeCount();
            double bound = (V + E) * (Math.log(V) / Math.log(2));
            System.out.printf("%9d %10d %10.2f %14.0f %22.2f%n", V, E, ms, bound, ms * 1e6 / bound);
        }
        System.out.println();
    }

    static void dijkstraVsAStar() {
        System.out.println("=== (2) Dijkstra vs A* on point-to-point queries (V ≈ 100,000) ===");
        Graph g = NetworkFactory.randomGrid(100_000, 7);
        MarketConditions m = MarketConditions.defaults();
        double[] w = weights(g, 8.0, m);
        PathFinder.EdgeWeight ew = e -> w[e.id()];
        double minFee = Double.POSITIVE_INFINITY;
        for (RoadClass rc : RoadClass.values()) minFee = Math.min(minFee, CostFunctions.equilibriumFeePerKm(rc, 8.0).root());
        double perKm = CostFunctions.lowerBoundPerKm(m, minFee);
        double[] xyz = Geo.toCartesian(g.vertices());
        Random rnd = new Random(1);
        long dNanos = 0, aNanos = 0, dSettled = 0, aSettled = 0;
        int queries = 200, mismatches = 0;
        for (int i = 0; i < queries + 20; i++) {
            int s = rnd.nextInt(g.vertexCount()), t = rnd.nextInt(g.vertexCount());
            long t0 = System.nanoTime();
            PathFinder.Search d = PathFinder.dijkstra(g, s, t, ew, null);
            long t1 = System.nanoTime();
            PathFinder.Search a = PathFinder.aStar(g, s, t, ew, v -> Geo.chordKm(xyz, v, t) * perKm, null);
            long t2 = System.nanoTime();
            if (i < 20) continue;                                          // warm-up
            dNanos += t1 - t0; aNanos += t2 - t1;
            dSettled += d.settled(); aSettled += a.settled();
            if (Math.abs(d.cost() - a.cost()) > 1e-9 * Math.max(1, d.cost())) mismatches++;
        }
        System.out.printf("Dijkstra: %.2f ms/query, %.0f vertices settled%n", dNanos / 1e6 / queries, (double) dSettled / queries);
        System.out.printf("A*:       %.2f ms/query, %.0f vertices settled%n", aNanos / 1e6 / queries, (double) aSettled / queries);
        System.out.printf("Cost mismatches between the two: %d of %d (must be 0)%n%n", mismatches, queries);
    }

    static void concurrency() throws Exception {
        System.out.println("=== (3) Concurrency: 8 reader threads + 1 writer, V ≈ 10,000 ===");
        Graph g = NetworkFactory.randomGrid(10_000, 3);
        RoutingEngine engine = new RoutingEngine(g, MarketConditions.defaults());
        engine.setLogQueries(false);
        int readers = 8, perReader = 500;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        int side = (int) Math.ceil(Math.sqrt(10_000));

        for (int r = 0; r < readers; r++) {
            long seed = r;
            futures.add(pool.submit(() -> {
                Random rnd = new Random(seed);
                go.await();
                for (int i = 0; i < perReader; i++) {
                    String a = NetworkFactory.name(rnd.nextInt(side), rnd.nextInt(side));
                    String b = NetworkFactory.name(rnd.nextInt(side), rnd.nextInt(side));
                    try {
                        if (!engine.route(new RouteQuery(a, b, DISPATCH_HOURS[rnd.nextInt(DISPATCH_HOURS.length)],
                                RouteQuery.Algorithm.ASTAR, Set.of(), Set.of())).found()) errors.incrementAndGet();
                    } catch (RuntimeException ex) { errors.incrementAndGet(); }
                }
                return null;
            }));
        }
        AtomicInteger updates = new AtomicInteger();
        Future<?> writer = pool.submit(() -> {
            go.await();
            Random rnd = new Random(99);
            while (updates.get() < 40) {
                engine.updateMarket(engine.market().withFuelPrice(300 + rnd.nextDouble() * 100));
                updates.incrementAndGet();
                Thread.sleep(25);
            }
            return null;
        });

        long t0 = System.nanoTime();
        go.countDown();                                   // release all threads at once
        for (Future<?> f : futures) f.get(120, TimeUnit.SECONDS);   // a deadlock would time out here
        writer.get(120, TimeUnit.SECONDS);
        double secs = (System.nanoTime() - t0) / 1e9;
        pool.shutdown();

        int total = readers * perReader;
        System.out.printf("%d queries + %d price updates in %.2f s -> %.0f queries/s, errors: %d%n",
                total, updates.get(), secs, total / secs, errors.get());
        System.out.printf("Engine metrics: avg %.2f ms, max %.2f ms, avg settled %.0f%n",
                engine.metrics().avgMillis(), engine.metrics().maxMillis(), engine.metrics().avgSettled());
    }

    /** All edge weights at one departure hour (what RoutingEngine caches per bucket). */
    static double[] weights(Graph g, double hour, MarketConditions m) {
        EnumMap<RoadClass, Double> fee = new EnumMap<>(RoadClass.class);
        for (RoadClass rc : RoadClass.values()) fee.put(rc, CostFunctions.equilibriumFeePerKm(rc, hour).root());
        double[] w = new double[g.edgeCount()];
        for (int i = 0; i < w.length; i++) {
            Edge e = g.edge(i);
            w[i] = CostFunctions.evaluate(e, hour, m, fee.get(e.roadClass())).total();
        }
        return w;
    }
}