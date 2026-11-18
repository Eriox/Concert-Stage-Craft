package com.concertstage.client.light;

import com.concertstage.client.GpuMesh;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grouped drawing of the light (1.2.0 rev. 5). The fixtures write their beams, projected light, halos and emitters here instead of
 * straight into the game's buffers: the game flushes its shared buffer each time two block entities in a row use different render
 * types, so 50 fixtures drawing 3 kinds of light each would cost ~150 small draw calls. Recorded here, everything is sent in one draw
 * per render type once the block entities are done (the same moment as the mod's GPU meshes, {@link GpuMesh#flush()}), projected light
 * first, then the beams, then the emitters.
 *
 * <p>The recorder keeps positions already transformed by the block entity pose, so they are replayed as they are. Arrays are reused
 * frame after frame (no allocation once they have grown). When the GPU meshes are off (shader pack, option, error) everything goes
 * straight to the game's buffers as before.
 */
public final class LightBatch {
    /** Order of the passes when flushed. */
    static final int PASS_SURFACE = 0, PASS_BEAM = 1, PASS_EMIT = 2;

    private static final class Rec implements VertexConsumer {
        final RenderType type;
        final int pass;
        float[] f = new float[5 * 1024];
        int[] c = new int[1024];
        int n;                                   // committed vertices
        float x, y, z, u, v;
        int col = -1;

        Rec(RenderType type, int pass) { this.type = type; this.pass = pass; }

        @Override public VertexConsumer vertex(double x, double y, double z) { this.x = (float) x; this.y = (float) y; this.z = (float) z; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { col = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255); return this; }
        @Override public VertexConsumer uv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        // 1.2.0: the transformed forms without the vector the game's default methods make for each vertex
        @Override public VertexConsumer vertex(org.joml.Matrix4f m, float x, float y, float z) {
            this.x = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
            this.y = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
            this.z = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
            return this;
        }
        @Override public VertexConsumer normal(org.joml.Matrix3f m, float x, float y, float z) { return this; }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }

        @Override
        public void endVertex() {
            if (n >= 1 << 21) n = 0;              // never flushed (no world rendering hook): do not grow without end
            if (n == c.length) {
                c = java.util.Arrays.copyOf(c, n * 2);
                f = java.util.Arrays.copyOf(f, n * 10);
            }
            int i = n * 5;
            f[i] = x; f[i + 1] = y; f[i + 2] = z; f[i + 3] = u; f[i + 4] = v;
            c[n++] = col;
        }

        void replay(VertexConsumer out) {
            int whole = n - n % 4;                // quads only
            // 1.2.0: the game's buffer takes each vertex in one call (its fast path for this vertex layout, the one of the
            // entities): the element-by-element writes below were 38 % of the render thread with 288 fixtures (visual test v140,
            // flight recording: BufferBuilder.nextElement)
            if (out instanceof com.mojang.blaze3d.vertex.BufferBuilder bb) {
                final float inv = 1f / 255f;
                for (int k = 0; k < whole; k++) {
                    int i = k * 5, cc = c[k];
                    bb.vertex(f[i], f[i + 1], f[i + 2], (((cc >> 16) & 255) + 0.25f) * inv, (((cc >> 8) & 255) + 0.25f) * inv, ((cc & 255) + 0.25f) * inv, ((cc >>> 24) + 0.25f) * inv,
                        f[i + 3], f[i + 4], OverlayTexture.NO_OVERLAY, 0xF000F0, 0f, 1f, 0f);
                }
                n = 0;
                return;
            }
            for (int k = 0; k < whole; k++) {
                int i = k * 5, cc = c[k];
                out.vertex(f[i], f[i + 1], f[i + 2]).color((cc >> 16) & 255, (cc >> 8) & 255, cc & 255, (cc >>> 24) & 255).uv(f[i + 3], f[i + 4])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0f, 1f, 0f).endVertex();
            }
            n = 0;
        }
    }

    private static final Map<RenderType, Rec> RECS = new IdentityHashMap<>();
    private static final List<Rec> ORDER = new ArrayList<>();
    private static boolean failed;
    /** Statistics for the F3 screen (vertices sent and grouped draws since the last read). */
    public static long vertices, flushes;

    private LightBatch() {}

    /** F3 line about Shimmer (rev. 8). */
    public static String shimmerStats() {
        return ShimmerLights.stats() + " | " + VolumeLights.stats() + " | " + LightDiagnostics.stats();
    }

    /** Where a fixture writes light of the given render type this frame. */
    static VertexConsumer buffer(MultiBufferSource buffers, RenderType type, int pass) {
        if (failed || !GpuMesh.enabled()) return buffers.getBuffer(type);
        Rec r = RECS.get(type);
        if (r == null) {
            r = new Rec(type, pass);
            RECS.put(type, r);
            ORDER.add(r);
            ORDER.sort((a, b) -> Integer.compare(a.pass, b.pass));
        }
        return r;
    }

    /** Start of a frame: nothing recorded is kept from a frame that was not drawn. */
    public static void clear() {
        for (Rec r : ORDER) r.n = 0;
    }

    /** Rev. 20: something is recorded (GpuMesh keeps the opaque depth for it). */
    public static boolean pending() {
        for (Rec r : ORDER) if (r.n >= 4) return true;
        return false;
    }

    /** Sends everything recorded (called with the GPU meshes, after the block entities). */
    public static void flush() {
        boolean any = false;
        for (Rec r : ORDER) if (r.n >= 4) { any = true; break; }
        if (!any) return;
        try {
            MultiBufferSource.BufferSource src = Minecraft.getInstance().renderBuffers().bufferSource();
            for (Rec r : ORDER) {
                if (r.n < 4) { r.n = 0; continue; }
                vertices += r.n;
                flushes++;
                r.replay(src.getBuffer(r.type));
                src.endBatch(r.type);
            }
            VolumeLights.resetBlend();   // rev. 16f: the game's blend record back to opaque (vignette, see resetBlend)
        } catch (Throwable t) {
            failed = true;
            for (Rec r : ORDER) r.n = 0;
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: grouped light drawing turned off after an error", t);
        }
    }
}
