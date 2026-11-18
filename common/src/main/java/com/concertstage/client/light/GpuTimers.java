package com.concertstage.client.light;

import java.util.Locale;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Rev. 25 — what each pass of the stage light costs, on the graphics card (OpenGL timer queries, read a few frames later, never
 * waiting for the card) and on the processor (the time to send it). Shown in F3 (RenderStats) and in the measurements of the in-game
 * test. Passes do not nest. If the driver refuses timer queries, only the processor times are given.
 */
public final class GpuTimers {
    public static final int SHADOWS = 0, STAGE = 1, HAZE = 2, BEAMS = 3, BLOOM = 4, FIXTURES = 5, N = 6;
    private static final String[] NAMES = {"shadow maps", "stage light", "beams in haze", "beams / lenses", "bloom", "fixtures (block entities)"};
    private static final long[] allocStart = new long[N];
    private static final double[] allocSum = new double[N], allocAvg = new double[N];
    private static final com.sun.management.ThreadMXBean MX = mx();

    private static com.sun.management.ThreadMXBean mx() {
        try {
            var b = java.lang.management.ManagementFactory.getThreadMXBean();
            if (b instanceof com.sun.management.ThreadMXBean t && t.isThreadAllocatedMemorySupported()) {
                t.setThreadAllocatedMemoryEnabled(true);
                return t;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static long allocated() {
        return MX == null ? 0L : MX.getCurrentThreadAllocatedBytes();
    }

    /** Processor time and memory allocated only (no card query): for code that runs many times a frame (one call per fixture). */
    public static void beginCpu(int s) {
        if (LightTargets.feed()) return;                     // rev. 3b: a camera image is not the frame's cost
        tick();
        cpuStart[s] = System.nanoTime();
        allocStart[s] = allocated();
    }

    public static void endCpu(int s) {
        if (LightTargets.feed()) return;                     // rev. 3b: a camera image is not the frame's cost
        cpuSum[s] += System.nanoTime() - cpuStart[s];
        allocSum[s] += allocated() - allocStart[s];
    }
    private static final int RING = 4;
    private static final int[][] ids = new int[N][RING];
    private static final boolean[][] pending = new boolean[N][RING];
    private static final long[] cpuStart = new long[N];
    private static final double[] gpuSum = new double[N], cpuSum = new double[N], gpuAvg = new double[N], cpuAvg = new double[N];
    private static int gpuCount[] = new int[N], frames, active = -1;
    private static long lastSerial = -1, windowStart = System.nanoTime();
    private static boolean failed, made;

    private GpuTimers() {}

    private static void tick() {
        long serial = com.concertstage.client.GpuMesh.frameSerial;
        if (serial == lastSerial) return;
        lastSerial = serial;
        frames++;
        harvest();
        long now = System.nanoTime();
        if (now - windowStart >= 1_000_000_000L && frames > 0) {
            for (int s = 0; s < N; s++) {
                cpuAvg[s] = cpuSum[s] / 1e6 / frames;
                allocAvg[s] = allocSum[s] / 1024.0 / frames;
                allocSum[s] = 0;
                gpuAvg[s] = gpuCount[s] > 0 ? gpuSum[s] / 1e6 / gpuCount[s] : -1;
                cpuSum[s] = gpuSum[s] = 0; gpuCount[s] = 0;
            }
            frames = 0;
            windowStart = now;
            window++;
        }
    }

    private static void harvest() {
        if (failed || !made) return;
        for (int s = 0; s < N; s++) for (int k = 0; k < RING; k++) {
            if (!pending[s][k]) continue;
            if (GL15.glGetQueryObjecti(ids[s][k], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
            gpuSum[s] += GL33.glGetQueryObjecti64(ids[s][k], GL15.GL_QUERY_RESULT);
            gpuCount[s]++;
            pending[s][k] = false;
        }
    }

    public static void begin(int s) {
        if (LightTargets.feed()) return;                     // rev. 3b: a camera image is not the frame's cost
        tick();
        cpuStart[s] = System.nanoTime();
        allocStart[s] = allocated();
        if (failed || active >= 0) return;
        try {
            if (!made) { for (int i = 0; i < N; i++) GL15.glGenQueries(ids[i]); made = true; }
            int slot = -1;
            for (int k = 0; k < RING; k++) if (!pending[s][k]) { slot = k; break; }
            if (slot < 0) return;                                       // all in flight: skip this frame for this pass
            // 1.3.0 rev. 2: another timer query is already running (the game's own GPU measure with F3, or another mod): a second one
            // is a GL error (thousands of "Cannot begin query on an active query object" in the author's log); this pass is skipped
            if (GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_CURRENT_QUERY) != 0) return;
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, ids[s][slot]);
            pending[s][slot] = true;
            active = s;
        } catch (Throwable t) {
            failed = true;
        }
    }

    public static void end(int s) {
        if (LightTargets.feed()) return;                     // rev. 3b: a camera image is not the frame's cost
        cpuSum[s] += System.nanoTime() - cpuStart[s];
        allocSum[s] += allocated() - allocStart[s];
        if (active != s) return;
        try {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        } catch (Throwable t) {
            failed = true;
        }
        active = -1;
    }

    /** Card time of a pass, ms per frame over the last second (-1 if unknown). */
    public static double gpuMs(int s) {
        return failed ? -1 : gpuAvg[s];
    }

    /** Rev. 25: a new one-second average is available (incremented each window). */
    public static int window;

    public static String stats() {
        StringBuilder b = new StringBuilder("CSC passes (card / processor ms):");
        double g = 0, c = 0;
        for (int s = 0; s < N; s++) {
            b.append(String.format(Locale.ROOT, " %s %s / %.2f,", NAMES[s], gpuAvg[s] < 0 ? "?" : String.format(Locale.ROOT, "%.2f", gpuAvg[s]), cpuAvg[s]));
            if (gpuAvg[s] > 0) g += gpuAvg[s];
            c += cpuAvg[s];
        }
        b.append(String.format(Locale.ROOT, " total %.2f / %.2f", g, c));
        double kb = 0;
        for (int s = 0; s < N; s++) kb += allocAvg[s];
        b.append(String.format(Locale.ROOT, "; memory allocated per frame: %.0f KB (fixtures %.0f, shadows %.0f, stage %.0f, haze %.0f, beams %.0f)",
            kb, allocAvg[FIXTURES], allocAvg[SHADOWS], allocAvg[STAGE], allocAvg[HAZE], allocAvg[BEAMS]));
        if (failed) b.append(" (no timer queries on this driver)");
        return b.toString();
    }
}
