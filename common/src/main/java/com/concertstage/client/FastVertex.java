package com.concertstage.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 1.2.0: one vertex written in one call. When the target is the game's buffer it takes the whole vertex at once (its fast
 * path for the entity and block layouts) instead of element by element, and the position and the normal are transformed here without
 * the two objects the game's {@code vertex(Matrix4f, ...)} / {@code normal(Matrix3f, ...)} make for each vertex. Measured on the
 * stage lights (visual test v140, 288 fixtures: the element-by-element writes were 38 % of the render thread). Anything else (the
 * mod's own recorders, another mod's consumer) gets the usual calls.
 */
public final class FastVertex {
    private FastVertex() {}

    private static final org.joml.Vector3f N = new org.joml.Vector3f();

    /** Render thread only. {@code overlay} < 0: the layout has none (block render types). */
    public static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, int r, int g, int b, int a, float u, float v,
                           int overlay, int light, float nx, float ny, float nz) {
        if (vc instanceof BufferBuilder bb) {
            float px = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
            float py = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
            float pz = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
            org.joml.Vector3f q = n.transform(N.set(nx, ny, nz));
            float qx = q.x, qy = q.y, qz = q.z;
            // (+ 0.25: the buffer stores (int) (value × 255), a colour must not come back one step lower)
            float inv = 1f / 255f;
            bb.vertex(px, py, pz, (r + 0.25f) * inv, (g + 0.25f) * inv, (b + 0.25f) * inv, (a + 0.25f) * inv, u, v, overlay < 0 ? net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY : overlay, light, qx, qy, qz);
            return;
        }
        VertexConsumer c = vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v);
        if (overlay >= 0) c = c.overlayCoords(overlay);                     // < 0: a layout without overlay (blocks)
        c.uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
