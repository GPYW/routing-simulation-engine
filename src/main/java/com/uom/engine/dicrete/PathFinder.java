package com.uom.engine.discrete;

import java.util.*;
 
public final class PathFinder {

    @FunctionalInterface
    public interface EdgeWeight { double of(Edge e); }

    @FunctionalInterface
    public interface Heuristic { double estimate(int vertex); }

    public record Search(boolean found, double cost, List<Edge> path, int settled) { }

    private PathFinder() { }

    public static Search dijkstra(Graph g, int source, int target, EdgeWeight w, BitSet allowed) {
        return aStar(g, source, target, w, v -> 0.0, allowed);
    }

    /**
     * @param allowed vertices the route may pass through (null = all)
     * @param target  pass -1 to compute the full shortest-path tree (used by the benchmark)
     */
    public static Search aStar(Graph g, int source, int target, EdgeWeight w, Heuristic h, BitSet allowed) {
        int n = g.vertexCount();
        double[] best = new double[n];          // g(v): cheapest known cost source -> v
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Edge[] via = new Edge[n];               // via[v] = last edge on the best path to v
        boolean[] settled = new boolean[n];     // v's cost is final

        MinHeap open = new MinHeap(64);
        best[source] = 0.0;
        open.push(h.estimate(source), source);
        int settledCount = 0;

        while (!open.isEmpty()) {
            int u = open.popValue();
            if (settled[u]) continue;           // stale heap entry ("lazy deletion")
            settled[u] = true;
            settledCount++;
            if (u == target) break;             // early exit: target's cost is now final

            for (Edge e : g.outgoing(u)) {
                int v = e.to();
                if (settled[v]) continue;
                if (allowed != null && !allowed.get(v)) continue;   // v not in the allowed set

                double weight = w.of(e);
                if (!(weight >= 0) || Double.isInfinite(weight)) {
                    throw new IllegalStateException("Invalid weight " + weight + " on edge " + e.id()
                            + " (Dijkstra/A* need finite, non-negative weights)");
                }
                double candidate = best[u] + weight;          // relaxation
                if (candidate < best[v]) {
                    best[v] = candidate;
                    via[v] = e;
                    open.push(candidate + h.estimate(v), v);
                }
            }
        }

        if (target < 0 || !settled[target]) {
            return new Search(false, Double.POSITIVE_INFINITY, List.of(), settledCount);
        }
        // Walk the via[] pointers backwards from target to source.
        LinkedList<Edge> path = new LinkedList<>();
        for (int v = target; v != source; v = via[v].from()) path.addFirst(via[v]);
        return new Search(true, best[target], List.copyOf(path), settledCount);
    }
}