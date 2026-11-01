package com.uom.engine.model;

import com.uom.engine.discrete.Geo;
import com.uom.engine.discrete.Graph;
import com.uom.engine.discrete.RoadClass;

import java.util.Random;

/** Builds sample networks: a small Sri Lankan one for demos, a large synthetic one for benchmarks. */
public final class NetworkFactory {

    private NetworkFactory() { }

    /** 15 cities, approximate coordinates, clusters = provinces. Road km = straight line x a road factor. */
    public static Graph sriLanka() {
        Graph g = new Graph();
        g.addVertex("Colombo",      6.9271, 79.8612, "WESTERN");
        g.addVertex("Negombo",      7.2083, 79.8358, "WESTERN");
        g.addVertex("Gampaha",      7.0873, 79.9994, "WESTERN");
        g.addVertex("Kandy",        7.2906, 80.6337, "CENTRAL");
        g.addVertex("NuwaraEliya",  6.9497, 80.7891, "CENTRAL");
        g.addVertex("Kurunegala",   7.4863, 80.3647, "NORTH_WESTERN");
        g.addVertex("Galle",        6.0535, 80.2210, "SOUTHERN");
        g.addVertex("Matara",       5.9549, 80.5550, "SOUTHERN");
        g.addVertex("Hambantota",   6.1241, 81.1185, "SOUTHERN");
        g.addVertex("Ratnapura",    6.6828, 80.3992, "SABARAGAMUWA");
        g.addVertex("Badulla",      6.9934, 81.0550, "UVA");
        g.addVertex("Anuradhapura", 8.3114, 80.4037, "NORTH_CENTRAL");
        g.addVertex("Jaffna",       9.6615, 80.0255, "NORTHERN");
        g.addVertex("Trincomalee",  8.5874, 81.2152, "EASTERN");
        g.addVertex("Batticaloa",   7.7310, 81.6747, "EASTERN");

        road(g, "Colombo", "Negombo", 1.20, RoadClass.EXPRESSWAY);
        road(g, "Colombo", "Gampaha", 1.25, RoadClass.HIGHWAY);
        road(g, "Negombo", "Gampaha", 1.30, RoadClass.HIGHWAY);
        road(g, "Colombo", "Kandy", 1.25, RoadClass.HIGHWAY);
        road(g, "Gampaha", "Kandy", 1.20, RoadClass.HIGHWAY);
        road(g, "Negombo", "Kurunegala", 1.25, RoadClass.HIGHWAY);
        road(g, "Gampaha", "Kurunegala", 1.20, RoadClass.EXPRESSWAY);
        road(g, "Kurunegala", "Kandy", 1.30, RoadClass.HIGHWAY);
        road(g, "Kurunegala", "Anuradhapura", 1.20, RoadClass.HIGHWAY);
        road(g, "Anuradhapura", "Jaffna", 1.25, RoadClass.HIGHWAY);
        road(g, "Anuradhapura", "Trincomalee", 1.25, RoadClass.HIGHWAY);
        road(g, "Kandy", "Trincomalee", 1.35, RoadClass.HIGHWAY);
        road(g, "Kandy", "NuwaraEliya", 1.60, RoadClass.MOUNTAIN);
        road(g, "NuwaraEliya", "Badulla", 1.70, RoadClass.MOUNTAIN);
        road(g, "Kandy", "Badulla", 1.50, RoadClass.MOUNTAIN);
        road(g, "Badulla", "Batticaloa", 1.40, RoadClass.HIGHWAY);
        road(g, "Trincomalee", "Batticaloa", 1.25, RoadClass.HIGHWAY);
        road(g, "Colombo", "Galle", 1.15, RoadClass.EXPRESSWAY);
        road(g, "Galle", "Matara", 1.20, RoadClass.EXPRESSWAY);
        road(g, "Matara", "Hambantota", 1.25, RoadClass.EXPRESSWAY);
        road(g, "Colombo", "Ratnapura", 1.30, RoadClass.HIGHWAY);
        road(g, "Ratnapura", "Badulla", 1.50, RoadClass.MOUNTAIN);
        road(g, "Hambantota", "Badulla", 1.40, RoadClass.HIGHWAY);
        return g;
    }

    private static void road(Graph g, String a, String b, double roadFactor, RoadClass rc) {
        double straight = Geo.haversineKm(g.vertex(g.indexOf(a)), g.vertex(g.indexOf(b)));
        g.addRoad(a, b, Math.round(straight * roadFactor * 10) / 10.0, rc);
    }

    /**
     * Synthetic network for benchmarks: a jittered grid of ~V nodes over a 4° x 4° box.
     * Each node links to its right and lower neighbour (always connected) plus a diagonal
     * with probability 0.5, giving E ≈ 5V directed edges. Four quadrant clusters.
     */
    public static Graph randomGrid(int targetVertices, long seed) {
        Random rnd = new Random(seed);
        int side = (int) Math.ceil(Math.sqrt(targetVertices));
        double step = 4.0 / side;
        Graph g = new Graph();
        for (int r = 0; r < side; r++) {
            for (int c = 0; c < side; c++) {
                double lat = 6.0 + r * step + (rnd.nextDouble() - 0.5) * step * 0.6;
                double lon = 79.5 + c * step + (rnd.nextDouble() - 0.5) * step * 0.6;
                String cluster = (r < side / 2 ? "N" : "S") + (c < side / 2 ? "W" : "E");
                g.addVertex(name(r, c), lat, lon, cluster);
            }
        }
        RoadClass[] classes = RoadClass.values();
        for (int r = 0; r < side; r++) {
            for (int c = 0; c < side; c++) {
                if (c + 1 < side) gridRoad(g, rnd, name(r, c), name(r, c + 1), classes);
                if (r + 1 < side) gridRoad(g, rnd, name(r, c), name(r + 1, c), classes);
                if (r + 1 < side && c + 1 < side && rnd.nextBoolean()) gridRoad(g, rnd, name(r, c), name(r + 1, c + 1), classes);
            }
        }
        return g;
    }

    private static void gridRoad(Graph g, Random rnd, String a, String b, RoadClass[] classes) {
        double straight = Geo.haversineKm(g.vertex(g.indexOf(a)), g.vertex(g.indexOf(b)));
        double factor = 1.05 + rnd.nextDouble() * 0.35;    // always >= 1, keeps A* admissible
        g.addRoad(a, b, straight * factor, classes[rnd.nextInt(classes.length)]);
    }

    public static String name(int r, int c) { return "N" + r + "_" + c; }
}