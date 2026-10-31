package com.uom.engine;

import com.uom.engine.discrete.Graph;
import com.uom.engine.discrete.RoadClass;
import com.uom.engine.discrete.Edge;
import com.uom.engine.discrete.PathFinder;
import com.uom.engine.discrete.Geo;
import com.uom.engine.discrete.AccessPolicy;
import com.uom.engine.numerical.NumericalSolver;

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
            case "errors" -> notYet("errors", 4);
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

                // ---- Step 4.1: numerical integration ----
        DoubleUnaryOperator traffic = t -> -t * t + 4 * t + 2;     // the starter-code curve
        System.out.printf("%nExact 56/3          = %.10f%n", 56.0 / 3);
        for (int n : new int[]{2, 4, 100}) {
            System.out.printf("n=%3d  Simpson = %.10f   Trapezoid = %.10f%n", n,
                    NumericalSolver.simpson(traffic, 0, 4, n), NumericalSolver.trapezoid(traffic, 0, 4, n));
        }
        System.out.printf("Cubic x^3-2x+1 on [0,2], n=2: Simpson = %.10f (exact 2)%n",
                NumericalSolver.simpson(t -> t * t * t - 2 * t + 1, 0, 2, 2));

        System.out.println("sin x on [0, pi] (exact 2):");
        double prevT = Double.NaN, prevS = Double.NaN;
        for (int n = 4; n <= 64; n *= 2) {
            double eT = Math.abs(NumericalSolver.trapezoid(Math::sin, 0, Math.PI, n) - 2);
            double eS = Math.abs(NumericalSolver.simpson(Math::sin, 0, Math.PI, n) - 2);
            System.out.printf("  n=%2d  trapezoid error %.2e%s   Simpson error %.2e%s%n", n,
                    eT, Double.isNaN(prevT) ? "         " : String.format(" (÷%.1f)", prevT / eT),
                    eS, Double.isNaN(prevS) ? "" : String.format(" (÷%.1f)", prevS / eS));
            prevT = eT;
            prevS = eS;
        }

        // ---- Step 4.2: root finding ----
        System.out.println("\nsqrt(2), plain Newton:  "
                + NumericalSolver.newtonRaphson(t -> t * t - 2, t -> 2 * t, 1, 1e-15, 50));

        DoubleUnaryOperator cbrt  = t -> Math.cbrt(t - 1);
        DoubleUnaryOperator dcbrt = t -> 1 / (3 * Math.cbrt((t - 1) * (t - 1)));
        System.out.println("cbrt(x-1), plain:       " + NumericalSolver.newtonRaphson(cbrt, dcbrt, 1.5, 1e-12, 50));
        System.out.println("cbrt(x-1), safeguarded: " + NumericalSolver.safeguardedNewton(cbrt, dcbrt, 0, 3, 1e-12, 200));
        System.out.println("sqrt(2), safeguarded:   "
                + NumericalSolver.safeguardedNewton(t -> t * t - 2, t -> 2 * t, 0, 2, 1e-15, 50));
        System.out.println("x^2+1, plain:           " + NumericalSolver.newtonRaphson(t -> t * t + 1, t -> 2 * t, 0, 1e-12, 50));
        try {
            NumericalSolver.safeguardedNewton(t -> t * t + 1, t -> 2 * t, -1, 1, 1e-12, 50);
        } catch (IllegalArgumentException e) {
            System.out.println("x^2+1, safeguarded:     " + e.getMessage());
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