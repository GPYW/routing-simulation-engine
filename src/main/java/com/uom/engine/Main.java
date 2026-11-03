package com.uom.engine;

import com.uom.engine.discrete.Graph;
import com.uom.engine.discrete.RoadClass;
import com.uom.engine.discrete.Edge;
import com.uom.engine.discrete.PathFinder;
import com.uom.engine.discrete.Geo;
import com.uom.engine.discrete.AccessPolicy;
import com.uom.engine.numerical.NumericalSolver;
import com.uom.engine.numerical.ErrorAnalysis;
import com.uom.engine.numerical.CostFunctions;
import com.uom.engine.numerical.MarketConditions;
import com.uom.engine.model.NetworkFactory;
import com.uom.engine.numerical.KahanSum;
import com.uom.engine.model.RouteQuery;
import com.uom.engine.model.RouteResult;
import com.uom.engine.model.RoutingEngine;
import com.uom.engine.api.RouteServer;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleUnaryOperator;
import java.util.Random;
import java.util.BitSet;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class Main {
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "server";
        switch (mode) {
            case "demo"   -> demo();                 // <- changed: was notYet("demo", 6)
            case "errors" -> ErrorAnalysis.run();
            case "bench"  -> notYet("bench", 8);
            case "test"   -> notYet("test", 9);
            case "server" -> server(args); 
            default       -> System.err.println("Unknown mode " + mode + " (use server|demo|errors|bench|test)");
        }
    }

    private static void demo() {
        Graph g = new Graph();
        g.addVertex("Colombo", 6.93, 79.86, "WESTERN");
        g.addVertex("Gampaha", 7.09, 80.00, "WESTERN");
        g.addVertex("Kandy",   7.29, 80.63, "CENTRAL");
        g.addRoad("Colombo", "Gampaha", 29.3, RoadClass.HIGHWAY);
        g.addEdge("Gampaha", "Kandy", 88.2, RoadClass.HIGHWAY);
        g.addEdge("Gampaha", "Kandy", 80.0, RoadClass.EXPRESSWAY);   // parallel road
        g.addRoad("Colombo", "Kandy", 117.9, RoadClass.HIGHWAY);     // direct road, so there is a choice
        PathFinder.EdgeWeight km = Edge::distanceKm;                  // weight = distance

        System.out.println("V=" + g.vertexCount() + " E=" + g.edgeCount());
        System.out.println("Leaving Gampaha: " + g.outgoing(1).size() + " edges");
        for (double[] row : g.toAdjacencyMatrix()) System.out.println(Arrays.toString(row));

        PathFinder.Search s = PathFinder.dijkstra(g, 0, 2, km, null);
        System.out.println("\nColombo -> Kandy: cost " + s.cost() + ", settled " + s.settled());
        for (Edge e : s.path()) {
            System.out.println("  " + g.vertex(e.from()).name() + " -> " + g.vertex(e.to()).name()
                    + "  " + e.distanceKm() + " km (" + e.roadClass() + ")");
        }

        BitSet noGampaha = new BitSet();
        noGampaha.set(0, 3);          // allow vertices 0, 1, 2
        noGampaha.clear(1);           // ...then remove Gampaha
        s = PathFinder.dijkstra(g, 0, 2, km, noGampaha);
        System.out.println("Avoiding Gampaha: cost " + s.cost() + ", " + s.path().size() + " edge(s)");

        BitSet noColombo = new BitSet();
        noColombo.set(0, 3);
        noColombo.clear(0);
        s = PathFinder.dijkstra(g, 2, 1, km, noColombo);
        System.out.println("Kandy -> Gampaha avoiding Colombo: found=" + s.found() + ", cost " + s.cost());

        // ---- Step 2.3: A* vs Dijkstra ----
        double[] xyz = Geo.toCartesian(g.vertices());
        PathFinder.Search a = PathFinder.aStar(g, 0, 2, km, v -> Geo.chordKm(xyz, v, 2), null);
        System.out.println("\nA* Colombo -> Kandy: cost " + a.cost() + ", settled " + a.settled());

        Graph grid = buildGrid(30);
        double[] gxyz = Geo.toCartesian(grid.vertices());
        compare(grid, gxyz, "N15_0", "N15_29", km);
        compare(grid, gxyz, "N0_0",  "N10_12", km);
        compare(grid, gxyz, "N0_0",  "N29_29", km);

        int from = grid.indexOf("N0_0"), to = grid.indexOf("N29_29");
        PathFinder.Search bad = PathFinder.aStar(grid, from, to, km, v -> 5 * Geo.chordKm(gxyz, v, to), null);
        System.out.printf("5x heuristic N0_0 -> N29_29: cost %.4f, settled %d%n", bad.cost(), bad.settled());

        
        // ---- Step 6.1: the engine and its lock ----
        RoutingEngine engine = new RoutingEngine(NetworkFactory.sriLanka(), MarketConditions.defaults());
        RouteResult rr = engine.route(RouteQuery.of("Colombo", "Jaffna", 8.5));
        System.out.printf("%nEngine: found=%s cost=%,.0f km=%.1f legs=%d%n", rr.found(), rr.totalCost(), rr.totalKm(), rr.legs().size());

        try {
            engine.route(new RouteQuery("Colombo", "Jaffna", 8.5, RouteQuery.Algorithm.ASTAR, Set.of("WESTERN"), Set.of()));
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected: " + ex.getMessage());       // the lock was still released (finally)
        }
        engine.updateMarket(engine.market().withFuelPrice(500));       // would hang here if the lock leaked
        System.out.printf("After fuel 500: cost=%,.0f%n", engine.route(RouteQuery.of("Colombo", "Jaffna", 8.5)).totalCost());

        // 4 reader threads x 500 queries, plus 1 writer changing the price 50 times
        engine.setLogQueries(false);
        String[] cities = {"Colombo", "Kandy", "Galle", "Jaffna", "Badulla", "Trincomalee", "Matara", "Negombo"};
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        List<Future<?>> jobs = new java.util.ArrayList<>();
        try {
            for (int t = 0; t < 4; t++) {
                int seed = t;
                jobs.add(pool.submit(() -> {
                    Random rnd = new Random(seed);
                    go.await();
                    for (int i = 0; i < 500; i++) {
                        String o = cities[rnd.nextInt(cities.length)], d = cities[rnd.nextInt(cities.length)];
                        try {
                            if (!engine.route(RouteQuery.of(o, d, rnd.nextInt(24))).found()) errors.incrementAndGet();
                        } catch (RuntimeException ex) { errors.incrementAndGet(); }
                    }
                    return null;
                }));
            }
            jobs.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < 50; i++) { engine.updateMarket(engine.market().withFuelPrice(300 + i)); Thread.sleep(2); }
                return null;
            }));
            long t0 = System.nanoTime();
            go.countDown();                                              // release all threads at once
            for (Future<?> f : jobs) f.get(30, TimeUnit.SECONDS);        // a deadlock would time out here
            System.out.printf("2000 queries + 50 updates in %.0f ms, errors=%d%n", (System.nanoTime() - t0) / 1e6, errors.get());
        } catch (Exception ex) {
            System.out.println("Concurrency test failed: " + ex);
        } finally {
            pool.shutdown();
        }

        // ---- Step 6.2: measuring the two-level cache ----
        RoutingEngine big = new RoutingEngine(NetworkFactory.randomGrid(100_000, 7), MarketConditions.defaults());
        big.setLogQueries(false);
        String cacheFrom = NetworkFactory.name(10, 10), cacheTo = NetworkFactory.name(40, 45);
        System.out.println();
        timeQuery(big, "1. cold (bucket 08:00 empty)", cacheFrom, cacheTo, 8.0);
        timeQuery(big, "2. warm, same bucket", cacheFrom, cacheTo, 8.0);
        timeQuery(big, "3. 08:10 -> same 08:00 bucket", cacheFrom, cacheTo, 8.17);
        timeQuery(big, "4. new bucket 08:15", cacheFrom, cacheTo, 8.25);
        big.updateMarket(big.market().withFuelPrice(400));
        timeQuery(big, "5. after fuel price change", cacheFrom, cacheTo, 8.0);
        big.addRoad(NetworkFactory.name(0, 0), NetworkFactory.name(0, 5), 80, RoadClass.HIGHWAY);
        timeQuery(big, "6. after addRoad", cacheFrom, cacheTo, 8.0);

        { // ---- Step 6.4: why Metrics uses LongAdder ----
            try {
                AtomicLong atomic = new AtomicLong();
                LongAdder adder = new LongAdder();
                long tPlain  = hammer(() -> plainCounter++);
                long tAtomic = hammer(atomic::incrementAndGet);
                long tAdder  = hammer(adder::increment);
                System.out.printf("%nExpected 40,000,000%n");
                System.out.printf("plain long: %,d  (%d ms)%n", plainCounter, tPlain);
                System.out.printf("AtomicLong: %,d  (%d ms)%n", atomic.get(), tAtomic);
                System.out.printf("LongAdder:  %,d  (%d ms)%n", adder.sum(), tAdder);
            } catch (Exception ex) {
                System.out.println("Counter test failed: " + ex);
            }
        }

        { // ---- Step 6.5: result breakdown and road validation ----
            RoutingEngine eng = new RoutingEngine(NetworkFactory.sriLanka(), MarketConditions.defaults());
            eng.setLogQueries(false);
            RouteResult res = eng.route(RouteQuery.of("Colombo", "Jaffna", 8.5));
            System.out.println();
            for (RouteResult.Leg l : res.legs()) {
                System.out.printf("%-12s -> %-12s %-10s %6.1f km  fuel %,7.0f  risk %,7.0f  fee %,7.0f  leg %,8.0f%n",
                        l.from(), l.to(), l.roadClass(), l.km(), l.fuel(), l.risk(), l.fee(), l.cost());
            }
            System.out.printf("total %,.2f  |  %.1f km  |  %.2f h  |  settled %d of %d%n",
                    res.totalCost(), res.totalKm(), res.totalHours(), res.verticesSettled(), res.graphVertices());

            try {
                eng.addRoad("Colombo", "Kurunegala", 60, RoadClass.EXPRESSWAY);         // impossible: too short
            } catch (IllegalArgumentException ex) {
                System.out.println("Rejected: " + ex.getMessage());
            }
            eng.addRoad("Colombo", "Kurunegala", 95.9, RoadClass.EXPRESSWAY);         // realistic: 1.15 x straight line
            RouteResult after = eng.route(RouteQuery.of("Colombo", "Jaffna", 8.5));
            StringBuilder path = new StringBuilder(after.origin());
            for (RouteResult.Leg l : after.legs()) path.append(" -> ").append(l.to());
            System.out.printf("With new expressway: %s%n   cost %,.0f, %.1f km, graph now has %d edges%n",
                    path, after.totalCost(), after.totalKm(), after.graphEdges());
        }
    }

     private static Graph buildGrid(int size) {
        Graph grid = new Graph();
        Random rnd = new Random(42);
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                grid.addVertex("N" + r + "_" + c, 6.0 + r * 0.1, 79.8 + c * 0.1, "X");
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++) {
                if (c + 1 < size) gridRoad(grid, rnd, "N" + r + "_" + c, "N" + r + "_" + (c + 1));
                if (r + 1 < size) gridRoad(grid, rnd, "N" + r + "_" + c, "N" + (r + 1) + "_" + c);
            }
        return grid;
    }

    private static void gridRoad(Graph g, Random rnd, String a, String b) {
        double straight = Geo.haversineKm(g.vertex(g.indexOf(a)), g.vertex(g.indexOf(b)));
        g.addRoad(a, b, straight * (1.05 + rnd.nextDouble() * 0.35), RoadClass.HIGHWAY);
    }

    private static void compare(Graph g, double[] xyz, String fromName, String toName, PathFinder.EdgeWeight w) {
        int s = g.indexOf(fromName), t = g.indexOf(toName);
        PathFinder.Search d = PathFinder.dijkstra(g, s, t, w, null);
        PathFinder.Search a = PathFinder.aStar(g, s, t, w, v -> Geo.chordKm(xyz, v, t), null);
        System.out.printf("%-6s -> %-6s  Dijkstra %.4f km, settled %3d | A* %.4f km, settled %3d%n",
                fromName, toName, d.cost(), d.settled(), a.cost(), a.settled());
    }

    /** Runs one query through all of Step 3: allowed set -> validation -> search. */
    private static void route(Graph g, AccessPolicy policy, PathFinder.EdgeWeight w,
                              String from, String to, List<String> clusters, List<String> avoid) {
        String label = from + " -> " + to + " clusters=" + clusters + " avoid=" + avoid + ": ";
        try {
            int s = g.indexOf(from), t = g.indexOf(to);
            List<Integer> blocked = new java.util.ArrayList<>();
            for (String name : avoid) blocked.add(g.indexOf(name));

            BitSet allowed = policy.allowedSet(clusters, blocked);
            policy.validate(allowed, s, t, g);
            PathFinder.Search r = PathFinder.dijkstra(g, s, t, w, allowed);
            System.out.println(label + "cost " + r.cost());
        } catch (AccessPolicy.AccessDeniedException e) {      // specific first
            System.out.println(label + "403 DENIED - " + e.getMessage());
        } catch (IllegalArgumentException e) {                // general second
            System.out.println(label + "400 BAD REQUEST - " + e.getMessage());
        }
    }

    private static void timeQuery(RoutingEngine e, String label, String from, String to, double hour) {
        long t0 = System.nanoTime();
        RouteResult r = e.route(RouteQuery.of(from, to, hour));
        System.out.printf("%-32s %8.1f ms  bucket %.2f  cost %,.0f%n",
                label, (System.nanoTime() - t0) / 1e6, r.departHour(), r.totalCost());
    }

    private static long plainCounter = 0;

    /** 8 threads each increment a counter 5 million times; returns elapsed ms. */
    private static long hammer(Runnable increment) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                try { go.await(); } catch (InterruptedException ignored) { }
                for (int i = 0; i < 5_000_000; i++) increment.run();
            });
        }
        long t0 = System.nanoTime();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
        return (System.nanoTime() - t0) / 1_000_000;
    }

    private static void server(String[] args) throws Exception {
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : Runtime.getRuntime().availableProcessors();
        RoutingEngine engine = new RoutingEngine(NetworkFactory.sriLanka(), MarketConditions.defaults());
        RouteServer server = new RouteServer(engine, port, threads);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        System.out.printf("Routing engine listening on http://localhost:%d with %d worker threads%n", port, threads);
        System.out.printf("Try: http://localhost:%d/route?origin=Colombo&destination=Jaffna&depart=8.5%n", port);
    }

    private static void notYet(String mode, int step) {
        System.out.println("Mode '" + mode + "' is not built yet - it arrives in Step " + step + ".");
    }
}