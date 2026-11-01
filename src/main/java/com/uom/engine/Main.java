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

import java.util.function.DoubleUnaryOperator;
import java.util.Random;
import java.util.BitSet;
import java.util.Arrays;
import java.util.List;

public final class Main {
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "server";
        switch (mode) {
            case "demo"   -> demo();                 // <- changed: was notYet("demo", 6)
            case "errors" -> ErrorAnalysis.run();
            case "bench"  -> notYet("bench", 8);
            case "test"   -> notYet("test", 9);
            case "server" -> notYet("server", 7);
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

           // ---- Step 5.1: time profiles ----
        System.out.println("\n hour   congestion g   night factor");
        for (double h : new double[]{0, 2, 3, 7.5, 8.5, 9.5, 12, 17.5, 23, 25}) {
            System.out.printf("%5.1f   %10.3f   %12.3f%n", h, CostFunctions.congestion(h), CostFunctions.nightFactor(h));
        }

        // ---- Step 5.2: fuel and risk integrals ----
        MarketConditions market = MarketConditions.defaults();
        System.out.println("\n" + market);
        Edge expressway = g.edge(3);      // Gampaha -> Kandy, 80 km EXPRESSWAY (from Step 1)
        Edge highway    = g.edge(2);      // Gampaha -> Kandy, 88.2 km HIGHWAY
        for (Edge e : List.of(expressway, highway)) {
            for (double hour : new double[]{3.0, 8.0, 12.0}) {
                CostFunctions.EdgePhysics p = CostFunctions.physics(e, hour, market.intervals());
                CostFunctions.EdgeCost c = CostFunctions.price(e, p, market, 0.0);
                System.out.printf("%-10s %5.1f km  dep %5.2f  litres %6.2f  hazard %.6f  fuel %5.0f  risk %6.0f  total %6.0f%n",
                        e.roadClass(), e.distanceKm(), hour, p.litres(), p.hazard(), c.fuel(), c.risk(), c.total());
            }
        }
        CostFunctions.EdgeCost cheapFuel = CostFunctions.evaluate(expressway, 8.0, market, 0);
        CostFunctions.EdgeCost dearFuel  = CostFunctions.evaluate(expressway, 8.0, market.withFuelPrice(500), 0);
        System.out.printf("Fuel at 350 -> %.0f, at 500 -> %.0f%n", cheapFuel.fuel(), dearFuel.fuel());

        // ---- Step 5.3: fee equilibrium ----
        System.out.println();
        for (RoadClass rc : RoadClass.values()) {
            for (double hour : new double[]{3, 8.5, 17.5}) {
                NumericalSolver.RootResult r = CostFunctions.equilibriumFeePerKm(rc, hour);
                System.out.printf("%-10s %5.2fh  p* = %7.4f per km  (%d Newton steps, %d bisections)%n",
                        rc, hour, r.root(), r.iterations(), r.bisectionSteps());
            }
        }
        for (Edge e : List.of(expressway, highway)) {
            for (double hour : new double[]{3, 8, 12}) {
                CostFunctions.EdgeCost c = CostFunctions.evaluate(e, hour, market);
                System.out.printf("%-10s dep %5.2f  fuel %5.0f  risk %6.0f  fee %5.0f  total %6.0f%n",
                        e.roadClass(), hour, c.fuel(), c.risk(), c.fee(), c.total());
            }
        }

        // ---- Step 5.4: A* lower bound with money weights ----
        double hour = 8.0;
        double minFee = Double.POSITIVE_INFINITY;
        for (RoadClass rc : RoadClass.values()) {
            minFee = Math.min(minFee, CostFunctions.equilibriumFeePerKm(rc, hour).root());
        }
        double fuelOnly = CostFunctions.lowerBoundPerKm(market, 0);
        double withFee  = CostFunctions.lowerBoundPerKm(market, minFee);
        System.out.printf("%nBound per km at 08:00: fuel-only %.2f, fuel+fee %.2f%n", fuelOnly, withFee);

        double[] money = new double[grid.edgeCount()];             // weight of every grid edge at 08:00
        for (int i = 0; i < money.length; i++) money[i] = CostFunctions.evaluate(grid.edge(i), hour, market).total();
        PathFinder.EdgeWeight cost = e -> money[e.id()];

        for (String[] q : new String[][]{{"N15_0", "N15_29"}, {"N0_0", "N10_12"}, {"N5_3", "N22_25"}, {"N0_0", "N29_29"}}) {
            int src = grid.indexOf(q[0]), dst = grid.indexOf(q[1]);
            PathFinder.Search dj = PathFinder.dijkstra(grid, src, dst, cost, null);
            PathFinder.Search a1 = PathFinder.aStar(grid, src, dst, cost, v -> Geo.chordKm(gxyz, v, dst) * fuelOnly, null);
            PathFinder.Search a2 = PathFinder.aStar(grid, src, dst, cost, v -> Geo.chordKm(gxyz, v, dst) * withFee, null);
            System.out.printf("%-6s -> %-6s cost %.2f | settled: Dijkstra %d, A* fuel-only %d, A* fuel+fee %d%n",
                    q[0], q[1], dj.cost(), dj.settled(), a1.settled(), a2.settled());
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

    private static void notYet(String mode, int step) {
        System.out.println("Mode '" + mode + "' is not built yet - it arrives in Step " + step + ".");
    }
}