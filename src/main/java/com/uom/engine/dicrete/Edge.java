package com.uom.engine.discrete;

/**
 * A directed edge u -> v.
 *
 * id is a dense index 0..E-1 so the engine can store per-edge weights in a plain double[]
 * (weights[e.id()]) instead of a nested Map<String, Map<String, Double>>.
 */
public record Edge(int id, int from, int to, double distanceKm, RoadClass roadClass) { }