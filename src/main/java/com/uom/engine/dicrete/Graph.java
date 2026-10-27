package com.uom.engine.discrete;

import java.util.*;

public final class Graph {
    private final List<Vertex> vertices = new ArrayList<>();
    private final Map<String, Integer> indexByName = new HashMap<>();
    private final List<List<Edge>> adjacency = new ArrayList<>();  // adjacency.get(u) = edges leaving u
    private final List<Edge> edges = new ArrayList<>();            // edges.get(id)

    public int addVertex(String name, double lat, double lon, String cluster) {
        Integer existing = indexByName.get(name);
        if (existing != null) return existing;     // idempotent: adding twice is harmless
        int id = vertices.size();                  // next dense id
        vertices.add(new Vertex(id, name, lat, lon, cluster));
        indexByName.put(name, id);
        adjacency.add(new ArrayList<>());
        return id;
    }

    public Edge addEdge(String from, String to, double distanceKm, RoadClass roadClass) {
        int u = requireIndex(from), v = requireIndex(to);
        if (u == v) throw new IllegalArgumentException("Self-loops are not allowed");
        if (!(distanceKm > 0) || !Double.isFinite(distanceKm))
            throw new IllegalArgumentException("Distance must be positive and finite");
        Edge e = new Edge(edges.size(), u, v, distanceKm, roadClass);
        edges.add(e);
        adjacency.get(u).add(e);
        return e;
    }

    public void addRoad(String a, String b, double km, RoadClass rc) {  // two-way = two edges
        addEdge(a, b, km, rc);
        addEdge(b, a, km, rc);
    }

    // ---- needed by addEdge ----
    public int indexOf(String name) {
        Integer i = indexByName.get(name);
        return i == null ? -1 : i;
    }

    private int requireIndex(String name) {
        int i = indexOf(name);
        if (i < 0) throw new IllegalArgumentException("Unknown vertex: " + name);
        return i;
    }
}