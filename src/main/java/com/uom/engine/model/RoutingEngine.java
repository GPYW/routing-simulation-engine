package com.uom.engine.model;

import com.uom.engine.discrete.*;
import com.uom.engine.numerical.CostFunctions;
import com.uom.engine.numerical.KahanSum;
import com.uom.engine.numerical.MarketConditions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class RoutingEngine {
    public static final int BUCKETS_PER_HOUR = 4;

    /** Market-independent data for one departure bucket. */
    private record Physics(CostFunctions.EdgePhysics[] perEdge, EnumMap<RoadClass, Double> feePerKm, double minFeePerKm) { }

    /** Final edge weights for one bucket under the current market. */
    private record Weights(double[] perEdge, double minFeePerKm) { }

    private final Graph graph;
    private final AccessPolicy policy;
    private final double[] xyz;                     // vertex positions for the A* heuristic
    private MarketConditions market;                // guarded by lock
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ConcurrentHashMap<Integer, Physics> physicsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Weights> weightCache = new ConcurrentHashMap<>();
    private final Metrics metrics = new Metrics();
    private volatile boolean logQueries = true;

    public RoutingEngine(Graph graph, MarketConditions market) {
        this.graph = graph;
        this.market = market;
        this.policy = new AccessPolicy(graph);
        if (!policy.isPartition()) throw new IllegalStateException("Clusters must partition the vertex set");
        this.xyz = Geo.toCartesian(graph.vertices());   // vertices are fixed after construction
    }

    // ------------------------------------------------------------------ queries (read lock)

    public RouteResult route(RouteQuery q) {
        long start = System.nanoTime();
        lock.readLock().lock();
        try {
            int source = requireVertex(q.origin());
            int target = requireVertex(q.destination());

            // Set theory: allowed = (⋃ requested clusters) \ avoided vertices
            Set<Integer> blocked = new HashSet<>();
            for (String name : q.avoid()) blocked.add(requireVertex(name));
            BitSet allowed = policy.allowedSet(q.clusters(), blocked);
            policy.validate(allowed, source, target, graph);

            // Numerical layer -> edge weights for this departure time
            int bucket = bucketOf(q.departHour());
            Weights weights = weightsFor(bucket);
            double[] perEdge = weights.perEdge();
            PathFinder.EdgeWeight w = e -> perEdge[e.id()];

            // Discrete layer -> optimal path
            PathFinder.Search s = switch (q.algorithm()) {
                case DIJKSTRA -> PathFinder.dijkstra(graph, source, target, w, allowed);
                case ASTAR    -> PathFinder.aStar(graph, source, target, w,
                                     heuristicTo(target, weights.minFeePerKm()), allowed);
            };

            RouteResult result = buildResult(q, s, bucket, start);
            metrics.record(System.nanoTime() - start, s.settled(), s.found());
            log(result);
            return result;
        } catch (RuntimeException ex) {
            metrics.record(System.nanoTime() - start, 0, false);
            throw ex;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Vertex> vertices() {
        lock.readLock().lock();
        try { return List.copyOf(graph.vertices()); }
        finally { lock.readLock().unlock(); }
    }

    public int[] size() {
        lock.readLock().lock();
        try { return new int[]{graph.vertexCount(), graph.edgeCount()}; }
        finally { lock.readLock().unlock(); }
    }

    public MarketConditions market() {
        lock.readLock().lock();
        try { return market; }
        finally { lock.readLock().unlock(); }
    }

    public Metrics metrics() { return metrics; }
    public void setLogQueries(boolean on) { logQueries = on; }

    // ------------------------------------------------------------------ updates (write lock)

    public void updateMarket(MarketConditions newMarket) {
        lock.writeLock().lock();
        try {
            boolean resolutionChanged = newMarket.intervals() != market.intervals();
            market = Objects.requireNonNull(newMarket);
            weightCache.clear();                         // prices changed: re-price (cheap)
            if (resolutionChanged) physicsCache.clear(); // integration accuracy changed: re-integrate
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addRoad(String a, String b, double km, RoadClass rc) {
        lock.writeLock().lock();
        try {
            int u = requireVertex(a), v = requireVertex(b);
            double straight = Geo.haversineKm(graph.vertex(u), graph.vertex(v));
            if (km < straight) {
                // Shorter than the straight line is impossible and would break A*'s admissibility.
                throw new IllegalArgumentException(String.format(
                        "Road %s-%s of %.1f km is shorter than the straight line (%.1f km)", a, b, km, straight));
            }
            graph.addRoad(a, b, km, rc);
            physicsCache.clear();                        // arrays are indexed by edge id; E changed
            weightCache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------------ internals (6.2 - 6.5)

    static int bucketOf(double hour) {
        return (int) Math.floor(hour * BUCKETS_PER_HOUR) % (24 * BUCKETS_PER_HOUR);
    }

    static double hourOf(int bucket) { return (double) bucket / BUCKETS_PER_HOUR; }

    /** Expensive level. Called only under the read lock, so graph and market are stable. */
    private Physics physicsFor(int bucket) {
        return physicsCache.computeIfAbsent(bucket, b -> {
            double hour = hourOf(b);
            EnumMap<RoadClass, Double> fee = new EnumMap<>(RoadClass.class);
            for (RoadClass rc : RoadClass.values()) fee.put(rc, CostFunctions.equilibriumFeePerKm(rc, hour).root());
            CostFunctions.EdgePhysics[] phys = new CostFunctions.EdgePhysics[graph.edgeCount()];
            for (int id = 0; id < phys.length; id++) {
                phys[id] = CostFunctions.physics(graph.edge(id), hour, market.intervals());
            }
            double minFee = fee.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
            return new Physics(phys, fee, minFee);
        });
    }

    /** Cheap level: physics x current prices. */
    private Weights weightsFor(int bucket) {
        return weightCache.computeIfAbsent(bucket, b -> {
            Physics p = physicsFor(b);
            double[] w = new double[p.perEdge().length];
            for (int id = 0; id < w.length; id++) {
                Edge e = graph.edge(id);
                w[id] = CostFunctions.price(e, p.perEdge()[id], market, p.feePerKm().get(e.roadClass())).total();
            }
            return new Weights(w, p.minFeePerKm());
        });
    }

    /** h(v) = straight-line km to target x cheapest possible cost per km. Never overestimates. */
    private PathFinder.Heuristic heuristicTo(int target, double minFeePerKm) {
        double perKm = CostFunctions.lowerBoundPerKm(market, minFeePerKm);
        return v -> Geo.chordKm(xyz, v, target) * perKm;
    }

    private int requireVertex(String name) {
        int i = graph.indexOf(name);
        if (i < 0) throw new IllegalArgumentException("Unknown node: " + name);
        return i;
    }

    private RouteResult buildResult(RouteQuery q, PathFinder.Search s, int bucket, long startNanos) {
        int v = graph.vertexCount(), e = graph.edgeCount();
        double hour = hourOf(bucket);
        if (!s.found()) {
            return new RouteResult(false, q.origin(), q.destination(), q.algorithm().name(), hour,
                    List.of(), Double.NaN, 0, 0, s.settled(), v, e,
                    (System.nanoTime() - startNanos) / 1e6, "No route within the allowed node set");
        }
        Physics p = physicsFor(bucket);
        List<RouteResult.Leg> legs = new ArrayList<>();
        KahanSum cost = new KahanSum(), km = new KahanSum(), hours = new KahanSum();
        for (Edge edge : s.path()) {
            CostFunctions.EdgeCost c = CostFunctions.price(edge, p.perEdge()[edge.id()], market,
                    p.feePerKm().get(edge.roadClass()));
            legs.add(new RouteResult.Leg(graph.vertex(edge.from()).name(), graph.vertex(edge.to()).name(),
                    edge.roadClass().name(), edge.distanceKm(), hour, c.fuel(), c.risk(), c.fee(), c.total()));
            cost.add(c.total());
            km.add(edge.distanceKm());
            hours.add(c.hours());
        }
        return new RouteResult(true, q.origin(), q.destination(), q.algorithm().name(), hour,
                legs, cost.value(), km.value(), hours.value(), s.settled(), v, e,
                (System.nanoTime() - startNanos) / 1e6, "ok");
    }

    private void log(RouteResult r) {
        if (!logQueries) return;
        double bound = (r.graphVertices() + r.graphEdges()) * (Math.log(Math.max(2, r.graphVertices())) / Math.log(2));
        System.out.printf("[route] %-16s %s->%s algo=%s settled=%d/%d (V+E)log2V=%.0f time=%.3fms%n",
                Thread.currentThread().getName(), r.origin(), r.destination(), r.algorithm(),
                r.verticesSettled(), r.graphVertices(), bound, r.elapsedMs());
    }
}