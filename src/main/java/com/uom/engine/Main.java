package com.uom.engine;

import com.uom.engine.discrete.Graph;
import com.uom.engine.discrete.RoadClass;

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

        System.out.println("V=" + g.vertexCount() + " E=" + g.edgeCount());
        System.out.println("Leaving Gampaha: " + g.outgoing(1).size() + " edges");
        for (double[] row : g.toAdjacencyMatrix()) System.out.println(Arrays.toString(row));
    }

    private static void notYet(String mode, int step) {
        System.out.println("Mode '" + mode + "' is not built yet - it arrives in Step " + step + ".");
    }
}