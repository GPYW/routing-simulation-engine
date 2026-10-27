package com.uom.engine.discrete;

/**
 * A node in the network (warehouse, router, trading pool).
 *
 * id       - dense integer index 0..V-1, used for array-based lookups in the algorithms
 * name     - human-readable label used by the API ("Colombo")
 * lat, lon - coordinates, used by the A* heuristic (straight-line distance)
 * cluster  - the one region this node belongs to; clusters partition V (see AccessPolicy)
 */
public record Vertex(int id, String name, double lat, double lon, String cluster) { }