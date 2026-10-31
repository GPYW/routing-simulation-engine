package com.uom.engine.discrete;

import java.util.BitSet;

public final class AccessPolicy {

    public static BitSet union(BitSet a, BitSet b)        { BitSet r = (BitSet) a.clone(); r.or(b);     return r; }
    public static BitSet intersection(BitSet a, BitSet b) { BitSet r = (BitSet) a.clone(); r.and(b);    return r; }
    public static BitSet difference(BitSet a, BitSet b)   { BitSet r = (BitSet) a.clone(); r.andNot(b); return r; }

    // 3.2 adds: constructor, clusters, universe(), cluster(), complement(), isPartition()
    // 3.3 adds: allowedSet()
    // 3.4 adds: validate() and AccessDeniedException
}