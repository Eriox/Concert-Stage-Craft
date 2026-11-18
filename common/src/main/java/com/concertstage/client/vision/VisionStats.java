package com.concertstage.client.vision;

import java.util.ArrayList;
import java.util.List;

/** CSC 1.3.0 - figures of LiveVision and Dynamic Truss for the F3 screen and the in-game measurements (per frame, averaged). */
public final class VisionStats {
    private static long trussTris, trussBuilt, modelTris, modelDraws, walls, wallsFar, wallPixels;
    public static long wallNanos, feedNanos, feedsRendered, mediaBytes, mediaDecoded;
    /** rev. 1: fixtures drawn in the last camera image, sections and other block entities in it. */
    public static int feedFixtures, feedChunks, feedOthers;
    /** rev. 1: time of each part of the camera images (sky, terrain, entities, block entities, fixtures, meshes, translucent, walls,
     * light), summed until read by {@link #phases()}. */
    public static final long[] phaseNanos = new long[9];
    private static long phaseImages;
    private static final String[] PHASES = {"sky + section list", "terrain", "entities", "block entities", "fixtures", "meshes", "translucent", "walls", "light"};

    public static long phase(int i, long since) {
        long now = System.nanoTime();
        phaseNanos[i] += now - since;
        if (i == 0) phaseImages++;
        return now;
    }

    private static final java.util.Map<String, long[]> BY_TYPE = new java.util.HashMap<>();

    /** Time of a block entity drawn in a camera image, by class (the F3 / test figures). */
    public static void byType(Object be, long nanos) {
        long[] t = BY_TYPE.computeIfAbsent(be.getClass().getSimpleName(), k -> new long[2]);
        t[0] += nanos;
        t[1]++;
    }

    /** The block entity classes that cost the most in the camera images since the last call: total ms per image and count. */
    public static String byTypes() {
        double n = Math.max(1, phaseImages);
        StringBuilder sb = new StringBuilder("by type (ms per image, count):");
        BY_TYPE.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0])).limit(8)
            .forEach(e -> sb.append(String.format(java.util.Locale.ROOT, " %s %.2f/%.0f", e.getKey(), e.getValue()[0] / n / 1e6, e.getValue()[1] / n)));
        BY_TYPE.clear();
        long[] f = com.concertstage.client.light.FixtureRenderer.FEED_NANOS;
        sb.append(String.format(java.util.Locale.ROOT, " | fixtures: output %.2f, mount %.2f, body %.2f, light %.2f",
            f[0] / n / 1e6, f[1] / n / 1e6, f[2] / n / 1e6, f[3] / n / 1e6));
        java.util.Arrays.fill(f, 0);
        return sb.toString();
    }

    /** Average time of each part of a camera image since the last call (ms), and the counts of the last image. */
    public static String phases() {
        String types = byTypes();
        StringBuilder sb = new StringBuilder("camera image parts (ms):");
        double n = Math.max(1, phaseImages);
        for (int i = 0; i < PHASES.length; i++) {
            sb.append(String.format(java.util.Locale.ROOT, " %s %.2f", PHASES[i], phaseNanos[i] / n / 1e6));
            phaseNanos[i] = 0;
        }
        phaseImages = 0;
        return sb.append(" | sections ").append(feedChunks).append(", block entities ").append(feedOthers).append(", fixtures ").append(feedFixtures)
            .append(" | ").append(types).toString();
    }
    private static long frames, windowStart = System.nanoTime();
    private static String last = "";

    private VisionStats() {}

    public static void trussDrawn(int tris) { trussTris += tris; }
    public static void trussBuilt(int tris) { trussBuilt += tris; }
    public static void model(int tris) { modelTris += tris; modelDraws++; }
    public static void wall(boolean far, long pixels) { walls++; if (far) wallsFar++; wallPixels += pixels; }

    /** Called once per frame (end of the world render). */
    public static void endFrame() {
        frames++;
        long now = System.nanoTime();
        if (now - windowStart < 1_000_000_000L) return;              // one-second windows: per-frame averages, feeds per second
        double secs = (now - windowStart) / 1e9;
        double f = Math.max(1, frames);
        windowStart = now;
        frames = 0;
        last = String.format("CSC LiveVision: walls %.1f (far %.1f) %.3f ms, panels %.0f | feeds %.1f/s %.2f ms (%d fixtures) | media %d MB (%d decoded) | models %.0f draws %.0f k tris | trusses %.0f k tris (built %d k)",
            walls / f, wallsFar / f, wallNanos / f / 1e6, wallPixels / f, feedsRendered / secs, feedNanos / 1e6 / Math.max(1, feedsRendered), feedFixtures,
            mediaBytes >> 20, mediaDecoded, modelDraws / f, modelTris / f / 1000, trussTris / f / 1000, trussBuilt / 1000);
        trussTris = modelTris = modelDraws = walls = wallsFar = wallPixels = 0;
        feedNanos = feedsRendered = wallNanos = 0;
    }

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        if (!last.isEmpty()) out.add(last);
        return out;
    }
}
