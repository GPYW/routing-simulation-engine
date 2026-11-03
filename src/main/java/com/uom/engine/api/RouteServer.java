package com.uom.engine.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uom.engine.discrete.AccessPolicy;
import com.uom.engine.discrete.RoadClass;
import com.uom.engine.model.RouteQuery;
import com.uom.engine.model.RoutingEngine;
import com.uom.engine.numerical.MarketConditions;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP API on the JDK's built-in server (no framework needed).
 *
 *   GET  /route?origin=A&destination=B[&depart=8.5][&algo=astar|dijkstra][&clusters=X,Y][&avoid=Z]
 *   GET  /nodes
 *   GET  /metrics
 *   GET  /health
 *   POST /admin/market?fuel=400[&cargo=2500000]
 *   POST /admin/road?from=A&to=B&km=42&class=HIGHWAY
 *
 * A fixed thread pool runs requests in parallel; RoutingEngine handles the locking.
 */
public final class RouteServer {
    private final HttpServer server;
    private final ExecutorService pool;
    private final RoutingEngine engine;

    public RouteServer(RoutingEngine engine, int port, int threads) throws IOException {
        this.engine = engine;
        this.server = HttpServer.create(new InetSocketAddress(port), 128);
        AtomicInteger workerId = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(threads,
                r -> new Thread(r, "route-worker-" + workerId.incrementAndGet()));
        server.setExecutor(pool);
        server.createContext("/route",        ex -> handle(ex, "GET",  this::route));
        server.createContext("/nodes",        ex -> handle(ex, "GET",  p -> ok(Json.nodes(engine.vertices()))));
        server.createContext("/metrics",      ex -> handle(ex, "GET",  p -> ok(Json.metrics(engine.metrics(), engine.size(), engine.market()))));
        server.createContext("/health",       ex -> handle(ex, "GET",  p -> ok("{\"status\":\"up\"}")));
        server.createContext("/admin/market", ex -> handle(ex, "POST", this::updateMarket));
        server.createContext("/admin/road",   ex -> handle(ex, "POST", this::addRoad));
    }

    public void start() { server.start(); }

    public void stop() {
        server.stop(1);
        pool.shutdown();
        try { pool.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ---------------------------------------------------------------- handlers (7.3)

    private Response route(Map<String, String> p) {
        RouteQuery q = new RouteQuery(
                p.get("origin"), p.get("destination"),
                parseDouble(p.getOrDefault("depart", "8.0"), "depart"),
                RouteQuery.Algorithm.valueOf(p.getOrDefault("algo", "astar").toUpperCase(Locale.ROOT)),
                csv(p.get("clusters")), csv(p.get("avoid")));
        return ok(Json.route(engine.route(q)));
    }

    private Response updateMarket(Map<String, String> p) {
        MarketConditions m = engine.market();
        if (p.containsKey("fuel"))  m = m.withFuelPrice(parseDouble(p.get("fuel"), "fuel"));
        if (p.containsKey("cargo")) m = m.withCargoValue(parseDouble(p.get("cargo"), "cargo"));
        engine.updateMarket(m);
        return ok(Json.metrics(engine.metrics(), engine.size(), engine.market()));
    }

    private Response addRoad(Map<String, String> p) {
        engine.addRoad(p.get("from"), p.get("to"), parseDouble(p.get("km"), "km"),
                RoadClass.valueOf(p.getOrDefault("class", "HIGHWAY").toUpperCase(Locale.ROOT)));
        int[] s = engine.size();
        return ok("{\"vertices\":" + s[0] + ",\"edges\":" + s[1] + "}");
    }

    // ---------------------------------------------------------------- plumbing (7.2)

    private record Response(int status, String body) { }
    private static Response ok(String body) { return new Response(200, body); }

    @FunctionalInterface
    private interface Handler { Response apply(Map<String, String> params); }

    /** Common wrapper: method check, query parsing, error mapping, writing the response. */
    private void handle(HttpExchange ex, String method, Handler h) throws IOException {
        Response res;
        try {
            if (!method.equals(ex.getRequestMethod())) {
                res = new Response(405, Json.error("Use " + method));
            } else {
                res = h.apply(parseQuery(ex.getRequestURI().getRawQuery()));
            }
        } catch (AccessPolicy.AccessDeniedException e) {
            res = new Response(403, Json.error(e.getMessage()));
        } catch (IllegalArgumentException e) {      // bad input -> client error
            res = new Response(400, Json.error(e.getMessage()));
        } catch (RuntimeException e) {              // anything else -> our bug
            e.printStackTrace();
            res = new Response(500, Json.error("Internal error: " + e));
        }
        byte[] bytes = res.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(res.status(), bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }

    private static Set<String> csv(String s) {
        if (s == null || s.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String part : s.split(",")) if (!part.isBlank()) out.add(part.trim());
        return out;
    }

    private static double parseDouble(String s, String name) {
        if (s == null) throw new IllegalArgumentException(name + " is required");
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + " must be a number, got " + s); }
    }
}