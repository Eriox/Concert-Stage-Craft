package com.concertstage.client.light;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rev. 13: the shape of a gobo seen in the air. A beam through a gobo is not a full cone: in haze it is a bundle of shafts — the
 * light that passes through the holes of the pattern — with dark gaps where the metal of the gobo stops it (the beams of the
 * reference picture). The gobo texture is read once and cut into a 7 × 7 grid over its disc; every cell that lets light through
 * becomes one shaft, placed and sized like that part of the pattern and as bright as the light the cell passes. The renderer draws
 * those shafts as thin soft beams, turned with the gobo wheel, so the beam carries the pattern exactly as it lands on the truss.
 */
final class GoboShafts {
    /** One shaft: position in the pattern (−1..1, texture frame), share of light. */
    record Shaft(float qx, float qy, float weight) {}

    static final int GRID = 7;
    /** Radius of a shaft in pattern units: half a cell, slightly more so neighbouring cells merge into one shape. */
    static final float CELL_RADIUS = 1f / GRID * 1.15f;
    private static final Map<ResourceLocation, List<Shaft>> CACHE = new HashMap<>();

    private GoboShafts() {}

    static List<Shaft> of(ResourceLocation texture) {
        List<Shaft> l = CACHE.get(texture);
        if (l == null) { l = read(texture); CACHE.put(texture, l); }
        return l;
    }

    static void clear() {
        CACHE.clear();
    }

    private static List<Shaft> read(ResourceLocation texture) {
        List<Shaft> out = new ArrayList<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (res.isEmpty()) return out;
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth(), h = img.getHeight();
                for (int gy = 0; gy < GRID; gy++) {
                    for (int gx = 0; gx < GRID; gx++) {
                        float cx = (gx + 0.5f) / GRID * 2f - 1f, cy = (gy + 0.5f) / GRID * 2f - 1f;
                        if (cx * cx + cy * cy > 1.05f) continue;                  // outside the disc of the gobo
                        int x0 = gx * w / GRID, x1 = (gx + 1) * w / GRID, y0 = gy * h / GRID, y1 = (gy + 1) * h / GRID;
                        long sum = 0;
                        int n = 0;
                        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) { sum += img.getPixelRGBA(x, y) & 0xFF; n++; }
                        float wgt = n == 0 ? 0f : sum / (255f * n);
                        if (wgt > 0.2f) out.add(new Shaft(cx, cy, wgt));
                    }
                }
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: could not read gobo {} for its beam", texture, e);
        }
        return out;
    }
}
