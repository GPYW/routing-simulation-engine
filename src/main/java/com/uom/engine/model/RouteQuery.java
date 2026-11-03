package com.uom.engine.model;

import java.util.Set;

public record RouteQuery(String origin, String destination, double departHour, Algorithm algorithm, Set<String> clusters, Set<String> avoid) {

    public enum Algorithm { DIJKSTRA, ASTAR }

    public RouteQuery {
        if (origin == null || origin.isBlank()) throw new IllegalArgumentException("origin is required");
        if (destination == null || destination.isBlank()) throw new IllegalArgumentException("destination is required");
        if (!Double.isFinite(departHour) || departHour < 0 || departHour >= 24) {
            throw new IllegalArgumentException("depart must be an hour in [0, 24), got " + departHour);
        }
        algorithm = algorithm == null ? Algorithm.ASTAR : algorithm;
        clusters = clusters == null ? Set.of() : Set.copyOf(clusters);
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
    }

    public static RouteQuery of(String origin, String destination, double departHour) {
        return new RouteQuery(origin, destination, departHour, Algorithm.ASTAR, Set.of(), Set.of());
    }
}