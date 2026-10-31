package com.uom.engine.discrete;

import java.util.*;

/**
 * Set theory over the vertex set V, implemented with BitSet.
 * Bit i of a BitSet is 1 iff vertex i is in the set.
 */
public final class AccessPolicy {
    private final int universeSize;                                  // |V|
    private final Map<String, BitSet> clusters = new TreeMap<>();    // cluster name -> its member set

    public AccessPolicy(Graph g) {
        universeSize = g.vertexCount();
        for (Vertex v : g.vertices()) {
            clusters.computeIfAbsent(v.cluster(), k -> new BitSet(universeSize)).set(v.id());
        }
    }

    public Set<String> clusterNames() { return Collections.unmodifiableSet(clusters.keySet()); }

    /** The universe V (all vertices). */
    public BitSet universe() {
        BitSet u = new BitSet(universeSize);
        u.set(0, universeSize);
        return u;
    }

    /** A copy of one cluster's member set. */
    public BitSet cluster(String name) {
        BitSet c = clusters.get(name);
        if (c == null) throw new IllegalArgumentException("Unknown cluster: " + name + ", known: " + clusters.keySet());
        return (BitSet) c.clone();
    }

    public static BitSet union(BitSet a, BitSet b)        { BitSet r = (BitSet) a.clone(); r.or(b);     return r; }
    public static BitSet intersection(BitSet a, BitSet b) { BitSet r = (BitSet) a.clone(); r.and(b);    return r; }
    public static BitSet difference(BitSet a, BitSet b)   { BitSet r = (BitSet) a.clone(); r.andNot(b); return r; }
    public BitSet complement(BitSet a)                    { return difference(universe(), a); }

    /**
     * Clusters must form a PARTITION of V:
     *   (1) pairwise disjoint: Ci ∩ Cj = ∅ for i != j
     *   (2) covering:          C1 ∪ C2 ∪ ... ∪ Ck = V
     */
    public boolean isPartition() {
        BitSet seen = new BitSet(universeSize);
        for (BitSet c : clusters.values()) {
            if (seen.intersects(c)) return false;   // overlap breaks (1)
            seen.or(c);
        }
        return seen.cardinality() == universeSize;  // (2)
    }

    // 3.3 adds: allowedSet()
    // 3.4 adds: validate() and AccessDeniedException
}