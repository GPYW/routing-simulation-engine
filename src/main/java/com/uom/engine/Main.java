package com.uom.engine;

public final class Main {
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "server";
        switch (mode) {
            case "demo"   -> notYet("demo", 6);  // print a walkthrough of every layer
            case "errors" -> notYet("errors", 4);  // floating-point and integration error experiments
            case "bench"  -> notYet("bench", 8);  // scaling, A* vs Dijkstra, concurrency stress test
            case "test"   -> notYet("test", 9);   // self-checks
            case "server" -> notYet("server", 7);  // start the HTTP API (default)
            default       -> System.err.println("Unknown mode " + mode + " (use server|demo|errors|bench|test)");
        }
    }

    private static void notYet(String mode, int step) {
        System.out.println("Mode '" + mode + "' is not built yet - it arrives in Step " + step + ".");
    }
}