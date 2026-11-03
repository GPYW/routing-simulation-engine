package com.uom.engine.bench;

import com.uom.engine.discrete.*;
import com.uom.engine.model.*;
import com.uom.engine.numerical.*;

import java.util.*;

/**
 * Dependency-free checks (run: Main test). Each check compares the engine with an
 * independent answer: a closed form, a brute-force algorithm, or a hand-computed set.
 */
public final class SelfTest {
    private static int passed, failed;

    public static boolean run() {
        // --- numerical ---------------------------------------------------------
        check("Simpson is exact for a cubic",
                Math.abs(NumericalSolver.simpson(x -> x * x * x - 2 * x + 1, 0, 2, 2) - 2.0) < 1e-12);
        check("Simpson on the starter-code curve -t^2+4t+2 over [0,4] = 56/3",
                Math.abs(NumericalSolver.simpson(t -> -t * t + 4 * t + 2, 0, 4, 100) - 56.0 / 3) < 1e-12);
        check("Trapezoid converges to ∫_0^1 e^x = e-1",
                Math.abs(NumericalSolver.trapezoid(Math::exp, 0, 1, 10_000) - (Math.E - 1)) < 1e-8);

        NumericalSolver.RootResult sqrt2 = NumericalSolver.newtonRaphson(x -> x * x - 2, x -> 2 * x, 1, 1e-15, 50);
        check("Newton finds sqrt(2) in under 10 steps", sqrt2.converged() && sqrt2.iterations() < 10
                && Math.abs(sqrt2.root() - Math.sqrt(2)) < 1e-15);
        NumericalSolver.RootResult flat = NumericalSolver.newtonRaphson(x -> x * x + 1, x -> 2 * x, 0, 1e-12, 50);
        check("Newton reports failure instead of returning garbage (x^2+1 has no real root)", !flat.converged());
        NumericalSolver.RootResult cube = NumericalSolver.safeguardedNewton(x -> Math.cbrt(x - 1), x -> 1 / (3 * Math.cbrt((x - 1) * (x - 1))), 0, 3, 1e-12, 200);
        check("Safeguarded Newton converges where plain Newton diverges (cbrt(x-1))", cube.converged() && Math.abs(cube.root() - 1) < 1e-9);
        NumericalSolver.RootResult plainCube = NumericalSolver.newtonRaphson(x -> Math.cbrt(x - 1), x -> 1 / (3 * Math.cbrt((x - 1) * (x - 1))), 1.5, 1e-12, 50);
        check("...and plain Newton really does diverge on cbrt(x-1)", !plainCube.converged());

        for (RoadClass rc : RoadClass.values()) {
            for (double hour : new double[]{3, 8.5, 17.5}) {
                double p = CostFunctions.equilibriumFeePerKm(rc, hour).root();
                double demand = rc.baseDemand() * CostFunctions.congestion(hour) * Math.exp(-0.02 * p);
                double supply = rc.capacity() * (1 - Math.exp(-p / 50.0));
                check("Fee clears the market for " + rc + " at " + hour + "h", Math.abs(demand - supply) < 1e-7 * supply);
            }
        }
        check("Rush hour (08:00) costs more than late morning (11:00)", CostFunctions.evaluate(new Edge(0, 0, 1, 100, RoadClass.HIGHWAY), 8.0, MarketConditions.defaults()).total()
                > CostFunctions.evaluate(new Edge(0, 0, 1, 100, RoadClass.HIGHWAY), 11.0, MarketConditions.defaults()).total());

        KahanSum ks = new KahanSum();
        double naive = 0;
        for (int i = 0; i < 10_000_000; i++) { ks.add(0.1); naive += 0.1; }
        check("Kahan sum beats naive summation", Math.abs(ks.value() - 1e6) < Math.abs(naive - 1e6));

        // --- set theory -------------------------------------------------------
        Graph lk = NetworkFactory.sriLanka();
        AccessPolicy policy = new AccessPolicy(lk);
        check("Provinces partition the vertex set", policy.isPartition());
        BitSet west = policy.cluster("WESTERN"), central = policy.cluster("CENTRAL");
        check("|WESTERN ∪ CENTRAL| = 5", AccessPolicy.union(west, central).cardinality() == 5);
        check("WESTERN ∩ CENTRAL = ∅", AccessPolicy.intersection(west, central).isEmpty());
        check("|V \\ WESTERN| = 12", policy.complement(west).cardinality() == 12);

        // --- graph algorithms against brute force ------------------------------
        Random rnd = new Random(5);
        int mismatches = 0;
        for (int trial = 0; trial < 200; trial++) {
            Graph g = randomDense(rnd, 12);
            double[] w = new double[g.edgeCount()];
            for (int i = 0; i < w.length; i++) w[i] = rnd.nextDouble() * 10;
            PathFinder.EdgeWeight ew = e -> w[e.id()];
            int s = rnd.nextInt(12), t = rnd.nextInt(12);
            double expected = bellmanFord(g, w, s)[t];
            PathFinder.Search d = PathFinder.dijkstra(g, s, t, ew, null);
            double got = d.found() ? d.cost() : Double.POSITIVE_INFINITY;
            if (!(Math.abs(got - expected) < 1e-9 || (Double.isInfinite(got) && Double.isInfinite(expected)))) mismatches++;
            if (d.found()) {                                  // path must be connected and sum to the cost
                double sum = 0; int at = s;
                for (Edge e : d.path()) { if (e.from() != at) mismatches++; sum += w[e.id()]; at = e.to(); }
                if (at != t || Math.abs(sum - d.cost()) > 1e-9) mismatches++;
            }
        }
        check("Dijkstra matches Bellman-Ford on 200 random graphs", mismatches == 0);

        // --- engine ----------------------------------------------------------
        RoutingEngine engine = new RoutingEngine(lk, MarketConditions.defaults());
        engine.setLogQueries(false);
        RouteResult a = engine.route(new RouteQuery("Colombo", "Jaffna", 8.5, RouteQuery.Algorithm.ASTAR, Set.of(), Set.of()));
        RouteResult d = engine.route(new RouteQuery("Colombo", "Jaffna", 8.5, RouteQuery.Algorithm.DIJKSTRA, Set.of(), Set.of()));
        check("A* and Dijkstra agree on Colombo->Jaffna", a.found() && Math.abs(a.totalCost() - d.totalCost()) < 1e-6);
        check("A* settles no more vertices than Dijkstra", a.verticesSettled() <= d.verticesSettled());
        RouteResult avoid = engine.route(new RouteQuery("Colombo", "Jaffna", 8.5, RouteQuery.Algorithm.ASTAR, Set.of(), Set.of("Kurunegala")));
        check("Avoiding Kurunegala reroutes and costs more",
                avoid.found() && avoid.legs().stream().noneMatch(l -> l.to().equals("Kurunegala")) && avoid.totalCost() >= a.totalCost());
        RouteResult boxed = engine.route(new RouteQuery("Colombo", "Kandy", 8.5, RouteQuery.Algorithm.ASTAR, Set.of("WESTERN", "CENTRAL"), Set.of()));
        check("Cluster restriction keeps every stop inside WESTERN ∪ CENTRAL",
                boxed.found() && boxed.legs().stream().allMatch(l -> Set.of("Colombo", "Negombo", "Gampaha", "Kandy", "NuwaraEliya").contains(l.to())));
        boolean denied = false;
        try { engine.route(new RouteQuery("Colombo", "Jaffna", 8, null, Set.of("WESTERN"), Set.of())); }
        catch (AccessPolicy.AccessDeniedException ex) { denied = true; }
        check("Destination outside the allowed set is rejected", denied);
        boolean rejected = false;
        try { engine.addRoad("Colombo", "Jaffna", 50, RoadClass.EXPRESSWAY); }
        catch (IllegalArgumentException ex) { rejected = true; }
        check("Road shorter than the straight line is rejected", rejected);

        System.out.printf("%nSelf-test: %d passed, %d failed%n", passed, failed);
        return failed == 0;
    }

    private static void check(String name, boolean ok) {
        System.out.printf("  [%s] %s%n", ok ? "PASS" : "FAIL", name);
        if (ok) passed++; else failed++;
    }

    private static Graph randomDense(Random rnd, int n) {
        Graph g = new Graph();
        for (int i = 0; i < n; i++) g.addVertex("v" + i, 0, 0, "C");
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (i != j && rnd.nextDouble() < 0.25) g.addEdge("v" + i, "v" + j, 1, RoadClass.HIGHWAY);
        }
        return g;
    }

    /** O(V·E) reference algorithm: relax every edge V-1 times. */
    private static double[] bellmanFord(Graph g, double[] w, int s) {
        double[] dist = new double[g.vertexCount()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[s] = 0;
        for (int round = 1; round < g.vertexCount(); round++) {
            for (int id = 0; id < g.edgeCount(); id++) {
                Edge e = g.edge(id);
                if (dist[e.from()] + w[id] < dist[e.to()]) dist[e.to()] = dist[e.from()] + w[id];
            }
        }
        return dist;
    }
}
