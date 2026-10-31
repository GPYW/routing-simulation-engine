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

        // ---- Step 3.2: clusters as a partition ----
        AccessPolicy policy = new AccessPolicy(g);
        System.out.println("\nClusters: " + policy.clusterNames());
        BitSet west = policy.cluster("WESTERN"), central = policy.cluster("CENTRAL");
        System.out.println("WESTERN = " + west + "   CENTRAL = " + central + "   V = " + policy.universe());
        System.out.println("V \\ WESTERN = " + policy.complement(west));
        System.out.println("WESTERN ∩ CENTRAL empty? " + !west.intersects(central));
        System.out.println("Partition? " + policy.isPartition());

        west.clear(0);   // edit MY copy only
        System.out.println("After editing my copy, stored WESTERN = " + policy.cluster("WESTERN"));
        try {
            policy.cluster("SOUTHERN");
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
        // ---- Step 3.3: the allowed set drives the search ----
        int gampaha = g.indexOf("Gampaha");
        BitSet all       = policy.allowedSet(List.of(), List.of());
        BitSet westOnly  = policy.allowedSet(List.of("WESTERN"), List.of());
        BitSet avoidGam  = policy.allowedSet(List.of("WESTERN", "CENTRAL"), List.of(gampaha));
        BitSet avoidGam2 = policy.allowedSet(null, List.of(gampaha));

        System.out.println("\nNo restriction:                 " + all);
        System.out.println("WESTERN only:                   " + westOnly);
        System.out.println("WESTERN ∪ CENTRAL \\ {Gampaha}:  " + avoidGam);
        System.out.println("Everything \\ {Gampaha}:         " + avoidGam2);

        for (BitSet allowed : List.of(all, westOnly, avoidGam)) {
            PathFinder.Search r = PathFinder.dijkstra(g, 0, 2, km, allowed);
            System.out.println("Colombo -> Kandy within " + allowed + ": found=" + r.found() + ", cost " + r.cost());
        }

        // ---- Step 3.4: validation ----
        System.out.println();
        route(g, policy, km, "Colombo", "Kandy",   List.of(),           List.of());
        route(g, policy, km, "Colombo", "Kandy",   List.of("WESTERN"),  List.of());
        route(g, policy, km, "Kandy",   "Gampaha", List.of(),           List.of("Kandy"));
        route(g, policy, km, "Colombo", "Kandy",   List.of("SOUTHERN"), List.of());
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