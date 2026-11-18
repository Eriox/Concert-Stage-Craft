package com.concertstage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Performance figures of the mod in the F3 screen (1.1.0): for each kind of piece the mod draws itself, how many were drawn and the
 * processor time spent per frame (average over one second), and the state of the graphics-card meshes. To find what costs frames on a
 * given stage: send a screenshot of F3.
 */
public final class RenderStats {
    private static final class Slot {
        long nanos, count;
        double avgMs, avgCount;
    }

    private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();
    private static long frames, windowStart = System.nanoTime();
    static int meshesDrawn, meshesBuilt;
    static long flushNanos, buildNanos;
    private static double avgDrawn, avgBuilt, avgFlushMs, avgBuildMs;

    private RenderStats() {}

    public static void add(String name, long nanos) {
        Slot s = SLOTS.computeIfAbsent(name, k -> new Slot());
        s.nanos += nanos;
        s.count++;
    }

    /** End of a world frame (called by GpuMesh.endFrame). */
    private static double avgLightVerts, avgLightDraws;

    static void frame() {
        frames++;
        long now = System.nanoTime();
        if (now - windowStart < 1_000_000_000L) return;
        double f = Math.max(1, frames);
        for (Slot s : SLOTS.values()) {
            s.avgMs = s.nanos / 1e6 / f;
            s.avgCount = s.count / f;
            s.nanos = s.count = 0;
        }
        avgDrawn = meshesDrawn / f; avgBuilt = meshesBuilt / f; avgFlushMs = flushNanos / 1e6 / f; avgBuildMs = buildNanos / 1e6 / f;
        meshesDrawn = meshesBuilt = 0; flushNanos = buildNanos = 0;
        avgLightVerts = com.concertstage.client.light.LightBatch.vertices / f; avgLightDraws = com.concertstage.client.light.LightBatch.flushes / f;
        com.concertstage.client.light.LightBatch.vertices = 0; com.concertstage.client.light.LightBatch.flushes = 0;
        frames = 0;
        windowStart = now;
    }

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("[Concert Stage] GPU meshes: " + (GpuMesh.enabledForStats() ? "on" : "off (" + GpuMesh.offReason() + ")"));
        out.add(String.format(java.util.Locale.ROOT, "  drawn %.0f/frame, built %.1f/frame (%.2f ms), sent %.2f ms", avgDrawn, avgBuilt, avgBuildMs, avgFlushMs));
        out.add(String.format(java.util.Locale.ROOT, "  light (1.2.0): %.0f vertices/frame in %.1f grouped draws", avgLightVerts, avgLightDraws));
        out.add("  " + com.concertstage.client.light.LightBatch.shimmerStats());
        out.add("  " + com.concertstage.client.light.GpuTimers.stats());
        double total = 0;
        for (Map.Entry<String, Slot> e : SLOTS.entrySet()) {
            Slot s = e.getValue();
            if (s.avgCount < 0.01) continue;
            total += s.avgMs;
            out.add(String.format(java.util.Locale.ROOT, "  %s: %.0f x, %.2f ms", e.getKey(), s.avgCount, s.avgMs));
        }
        out.add(String.format(java.util.Locale.ROOT, "  total of the mod: %.2f ms/frame", total + avgFlushMs));
        out.addAll(com.concertstage.client.vision.VisionStats.lines());
        return out;
    }

    /** A block entity renderer that measures the time of another one (registered by the loaders). */
    public static <T extends BlockEntity> BlockEntityRenderer<T> timed(String name, BlockEntityRenderer<T> r) {
        return new BlockEntityRenderer<T>() {
            @Override
            public void render(T be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
                long t = System.nanoTime();
                try {
                    r.render(be, partial, pose, buffers, light, overlay);
                } finally {
                    add(name, System.nanoTime() - t);
                }
            }

            @Override
            public boolean shouldRenderOffScreen(T be) {
                return r.shouldRenderOffScreen(be);
            }

            @Override
            public int getViewDistance() {
                return r.getViewDistance();
            }

            @Override
            public boolean shouldRender(T be, Vec3 camera) {
                return r.shouldRender(be, camera);
            }
        };
    }
}
