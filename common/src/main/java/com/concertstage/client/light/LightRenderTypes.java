package com.concertstage.client.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Render types of the light (1.2.0 rev. 4 / rev. 5). All three start from the game's "eyes" type (full bright, no depth write, depth
 * tested, fog fading to nothing) with back-face culling off, so each quad is sent once, and differ by how they blend:
 * <ul>
 *   <li>{@link #of}: additive — emitters, lens glows (a light source adds light);</li>
 *   <li>{@link #screen}: "screen" blending {@code src + dst × (1 − src)} — the beams in the air. Overlapping beams brighten without
 *   ever burning into flat white, and a beam over a lit area adds less than over a dark sky, as light in haze does;</li>
 *   <li>{@link #modulate}: {@code dst × (1 + src)} — light projected on surfaces. It multiplies what is already drawn (the textures of
 *   the floor, instruments, truss): a lit surface keeps its texture and takes the colour of the light, black stays black, and a projected
 *   disc that overhangs an edge brightens nothing in the empty sky. Drawn with a small polygon offset against z-fighting.</li>
 * </ul>
 * Made with the public constructor of {@link RenderType} around the vanilla type's own state: no access widener, the same on Forge and
 * Fabric. One instance per texture and blending, so the batches stay together ({@link LightBatch}).
 */
final class LightRenderTypes extends RenderType {
    private static final Map<ResourceLocation, RenderType> ADD = new HashMap<>(), SCREEN = new HashMap<>(), MODULATE = new HashMap<>();

    private LightRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, Runnable setup, Runnable clear) {
        super(name, format, mode, size, false, false, setup, clear);
    }

    /** Additive, full bright, both sides visible. Render thread only. */
    static RenderType of(ResourceLocation texture) {
        return ADD.computeIfAbsent(texture, t -> make("add", t, null, false));
    }

    /** Screen blending (beams in the air). */
    static RenderType screen(ResourceLocation texture) {
        return SCREEN.computeIfAbsent(texture, t -> make("screen", t,
            () -> RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR), false));
    }

    /** dst × (1 + src): light projected on surfaces. */
    static RenderType modulate(ResourceLocation texture) {
        return MODULATE.computeIfAbsent(texture, t -> make("modulate", t,
            () -> RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE), true));
    }

    private static RenderType make(String kind, ResourceLocation texture, Runnable blend, boolean offset) {
        RenderType base = RenderType.eyes(texture);
        return new LightRenderTypes("concertstage_light_" + kind + "_" + texture.getPath().replace('/', '_'), base.format(), base.mode(), 4096,
            () -> {
                base.setupRenderState();
                RenderSystem.disableCull();
                // rev. 16f: the blending of the light, protected from the json blending of the eyes shader (VolumeLights.lightTypeBlend)
                VolumeLights.lightTypeBlend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                if (blend != null) blend.run();
                if (offset) { RenderSystem.polygonOffset(-1.0f, -8.0f); RenderSystem.enablePolygonOffset(); }
            },
            () -> {
                if (offset) { RenderSystem.polygonOffset(0f, 0f); RenderSystem.disablePolygonOffset(); }
                RenderSystem.enableCull();
                base.clearRenderState();
                RenderSystem.defaultBlendFunc();
            });
    }
}
