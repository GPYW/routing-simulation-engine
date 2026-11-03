package com.uom.engine.api;

import com.uom.engine.discrete.Vertex;
import com.uom.engine.model.Metrics;
import com.uom.engine.model.RouteResult;
import com.uom.engine.numerical.MarketConditions;

import java.util.List;
import java.util.Locale;

/** Tiny hand-written JSON serialiser so the project has zero dependencies. */
final class Json {
    private Json() { }

    static String route(RouteResult r) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        field(sb, "found", r.found()).append(',');
        field(sb, "origin", r.origin()).append(',');
        field(sb, "destination", r.destination()).append(',');
        field(sb, "algorithm", r.algorithm()).append(',');
        field(sb, "departHour", r.departHour()).append(',');
        field(sb, "totalCost", r.totalCost()).append(',');
        field(sb, "totalKm", r.totalKm()).append(',');
        field(sb, "totalHours", r.totalHours()).append(',');
        sb.append("\"path\":[");
        if (!r.legs().isEmpty()) {
            sb.append(str(r.legs().get(0).from()));
            for (RouteResult.Leg l : r.legs()) sb.append(',').append(str(l.to()));
        }
        sb.append("],\"legs\":[");
        for (int i = 0; i < r.legs().size(); i++) {
            RouteResult.Leg l = r.legs().get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            field(sb, "from", l.from()).append(',');
            field(sb, "to", l.to()).append(',');
            field(sb, "roadClass", l.roadClass()).append(',');
            field(sb, "km", l.km()).append(',');
            field(sb, "fuel", l.fuel()).append(',');
            field(sb, "risk", l.risk()).append(',');
            field(sb, "fee", l.fee()).append(',');
            field(sb, "cost", l.cost());
            sb.append('}');
        }
        sb.append("],\"stats\":{");
        field(sb, "verticesSettled", r.verticesSettled()).append(',');
        field(sb, "graphVertices", r.graphVertices()).append(',');
        field(sb, "graphEdges", r.graphEdges()).append(',');
        field(sb, "elapsedMs", r.elapsedMs());
        sb.append("},");
        field(sb, "message", r.message());
        return sb.append('}').toString();
    }

    static String nodes(List<Vertex> vs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vs.size(); i++) {
            Vertex v = vs.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            field(sb, "name", v.name()).append(',');
            field(sb, "cluster", v.cluster()).append(',');
            field(sb, "lat", v.lat()).append(',');
            field(sb, "lon", v.lon());
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    static String metrics(Metrics m, int[] size, MarketConditions market) {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "queries", m.queries()).append(',');
        field(sb, "failures", m.failures()).append(',');
        field(sb, "avgMs", m.avgMillis()).append(',');
        field(sb, "maxMs", m.maxMillis()).append(',');
        field(sb, "avgVerticesSettled", m.avgSettled()).append(',');
        field(sb, "vertices", size[0]).append(',');
        field(sb, "edges", size[1]).append(',');
        field(sb, "fuelPricePerLitre", market.fuelPricePerLitre()).append(',');
        field(sb, "cargoValue", market.cargoValue());
        return sb.append('}').toString();
    }

    static String error(String message) {
        return "{" + str("error") + ":" + str(message) + "}";
    }

    private static StringBuilder field(StringBuilder sb, String name, Object value) {
        sb.append(str(name)).append(':');
        if (value instanceof String s) sb.append(str(s));
        else if (value instanceof Double d) sb.append(Double.isFinite(d) ? String.format(Locale.ROOT, "%.4f", d) : "null");
        else sb.append(value);
        return sb;
    }

    static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}