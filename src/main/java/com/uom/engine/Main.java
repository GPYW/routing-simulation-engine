package com.uom.engine;

import com.uom.engine.discrete.Graph;
import com.uom.engine.discrete.RoadClass;
import com.uom.engine.discrete.Edge;
import com.uom.engine.discrete.PathFinder;
import com.uom.engine.discrete.Geo;
import com.uom.engine.discrete.AccessPolicy;

import java.util.Random;
import java.util.BitSet;
import java.util.Arrays;

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

        // ---- Step 3.1: sets as BitSets ----
        BitSet V = new BitSet(); V.set(0, 6);                  // universe {0..5}
        BitSet A = new BitSet(); A.set(0); A.set(1); A.set(2); // {0,1,2}
        BitSet B = new BitSet(); B.set(2); B.set(3);           // {2,3}

        System.out.println("\nA = " + A + "  B = " + B + "  V = " + V);
        System.out.println("A ∪ B = " + AccessPolicy.union(A, B));
        System.out.println("A ∩ B = " + AccessPolicy.intersection(A, B));
        System.out.println("A \\ B = " + AccessPolicy.difference(A, B));
        System.out.println("V \\ A = " + AccessPolicy.difference(V, A));
        System.out.println("2 ∈ A? " + A.get(2) + "   4 ∈ A? " + A.get(4));
        System.out.println("|A| = " + A.cardinality() + "   A and B overlap? " + A.intersects(B));
        System.out.println("A unchanged after all that? " + A);
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

    private static void notYet(String mode, int step) {
        System.out.println("Mode '" + mode + "' is not built yet - it arrives in Step " + step + ".");
    }
}