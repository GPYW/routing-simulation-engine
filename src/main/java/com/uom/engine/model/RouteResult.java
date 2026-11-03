package com.uom.engine.model;

import java.util.List;

public record RouteResult(boolean found, String origin, String destination, String algorithm, double departHour, List<Leg> legs,
                          double totalCost, double totalKm, double totalHours, int verticesSettled, int graphVertices, int graphEdges,
                          double elapsedMs, String message) {

    /** One edge of the chosen route. */
    public record Leg(String from, String to, String roadClass, double km, double departHour, double fuel, double risk, double fee, double cost) { }
}