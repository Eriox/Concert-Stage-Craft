package com.concertstage.client.light;

import com.concertstage.ConcertStage;
import com.concertstage.light.core.BeamOptics;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Volumetric-looking light cones (1.2.0 rev. 2, rev. 4). A beam is a cone mesh drawn additively with culling off ({@link LightRenderTypes}):
 * where the camera looks through more of the cone (its middle) the near and far sides add up, and the brightness of each vertex also
 * follows how squarely its surface faces the camera, so the edges stay soft — the look of light in haze, without any volumetric
 * simulation. Along the beam the light ramps in at the lens, then fades with distance (and towards the end of the throw when it hits
 * nothing), a smooth gradient like the beams of Theatrical / ExtraLights. Rev. 4: each quad is sent once (no second winding), fewer
 * segments and rings, no {@code Math.pow} per vertex, and plain beams sample the centre of the glow texture so beams, emitters, pools
 * and halos share one render type (one batch for all the fixtures).
 *
 * <p>Allocation free per frame: one table of the circle per detail level, and scratch vectors reused on the render thread.
 */
final class BeamRenderer {
    static final ResourceLocation SOLID = tex("beam_solid"), HAZE = tex("beam_haze"), GOBO_STRANDS = tex("beam_gobo");

    /** Segments around and rings along the cone, per beam quality (Low / Medium / High). */
    private static final int[] SEGMENTS = {6, 10, 14}, RINGS = {2, 4, 6};
    private static final float[][] COS = new float[3][], SIN = new float[3][];
    static {
        for (int q = 0; q < 3; q++) {
            int n = SEGMENTS[q];
            COS[q] = new float[n + 1];
            SIN[q] = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                double a = Math.PI * 2 * i / n;
                COS[q][i] = (float) Math.cos(a);
                SIN[q][i] = (float) Math.sin(a);
            }
        }
    }

    // scratch (render thread only)
    private static final Vector3f U = new Vector3f(), V = new Vector3f(), P = new Vector3f(), N = new Vector3f(), VIEW = new Vector3f();
    private static final float[] RING_D = new float[16], RING_R = new float[16], RING_K = new float[16];
    private static float EDGE_POW = 3f, FLOOR = 0.03f;

    private static final float[][] CORNERS = {{-1, -1, 0, 0}, {1, -1, 1, 0}, {1, 1, 1, 1}, {-1, 1, 0, 1}};

    private BeamRenderer() {}

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(ConcertStage.MOD_ID, "textures/lightshow/" + name + ".png");
    }

    /**
     * One cone.
     *
     * @param o       lens centre (block-local coordinates of the pose)
     * @param a       unit axis
     * @param len     length (to the block it hits, or the throw)
     * @param r0      radius at the lens
     * @param tanHalf tangent of the half angle
     * @param r,g,b   colour times brightness at the lens (may exceed 1: clamped per vertex)
     * @param hits    true when the beam ends on a block (no fade-out before the end)
     * @param fade    distance attenuation per block (washes fade sooner than the tight beams)
     * @param strands >0 for a gobo beam: number of strands around the cone (texture u repeats)
     * @param scroll  haze scroll (blocks), from the game time
     */
    static void cone(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f o, Vector3f a, float len, float r0, float tanHalf, Vector3f cam,
                     float r, float g, float b, boolean hits, float fade, int quality, float strands, float scroll) {
        cone(vc, mat, nm, o, a, len, r0, tanHalf, cam, r, g, b, hits, fade, quality, strands, scroll, 3f, 0.03f);
    }

    /**
     * {@code edgePow}: how fast the brightness drops where the cone is seen edge-on (3 = crisp shaft, 1.2 = full, soft wash);
     * {@code floor}: brightness kept even edge-on (a wide wash still reads as a volume of light).
     */
    static void cone(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f o, Vector3f a, float len, float r0, float tanHalf, Vector3f cam,
                     float r, float g, float b, boolean hits, float fade, int quality, float strands, float scroll, float edgePow, float floor) {
        EDGE_POW = edgePow;
        FLOOR = floor;
        int q = Math.max(0, Math.min(2, quality));
        int seg = SEGMENTS[q], rings = RINGS[q];
        // basis around the axis
        if (Math.abs(a.y) < 0.9f) U.set(0, 1, 0); else U.set(1, 0, 0);
        U.cross(a).normalize();
        V.set(a).cross(U).normalize();
        // rings: denser near the lens, where the light changes fastest
        for (int k = 0; k <= rings; k++) {
            float t = (float) k / rings;
            float d = len * (float) Math.pow(t, 1.35);
            RING_D[k] = d;
            RING_R[k] = r0 + d * tanHalf;
            float ramp = Math.min(1f, 0.25f + d / 0.6f);                         // soft start at the lens
            float att = 1f / (1f + fade * d);                                    // haze absorbs with distance
            float tail = hits ? 1f : Math.max(0f, 1f - t * t);                  // the throw ends softly in the air
            RING_K[k] = ramp * att * tail;
        }
        float uRep = strands > 0 ? strands : 1f;
        for (int k = 0; k < rings; k++) {
            for (int i = 0; i < seg; i++) {
                // quad (k,i) (k,i+1) (k+1,i+1) (k+1,i): sent once, the render type does not cull
                for (int c = 0; c < 4; c++)
                    emit(vc, mat, nm, o, a, cam, q, k + (c >= 2 ? 1 : 0), i + (c == 1 || c == 2 ? 1 : 0), seg, r, g, b, uRep, scroll, strands > 0);
            }
        }
    }

    private static void emit(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f o, Vector3f a, Vector3f cam, int q, int k, int i, int seg,
                             float r, float g, float b, float uRep, float scroll, boolean textured) {
        float cs = COS[q][i], sn = SIN[q][i];
        float rad = RING_R[k], d = RING_D[k];
        N.set(U).mul(cs).add(V.x * sn, V.y * sn, V.z * sn);                     // outward radial direction
        P.set(a).mul(d).add(o).add(N.x * rad, N.y * rad, N.z * rad);
        VIEW.set(P).sub(cam);
        float dist = VIEW.length();
        float facing = dist < 1e-4f ? 1f : Math.abs(N.dot(VIEW)) / dist;          // 1 = surface seen square on
        // facing^EDGE_POW without Math.pow: f^3 for the crisp shafts, f^1.25 for the soft washes / floods
        float shaped = EDGE_POW >= 2f ? facing * facing * facing : facing * (float) Math.sqrt(Math.sqrt(facing));
        float k2 = RING_K[k] * (FLOOR + (1f - FLOOR) * shaped);
        // the camera inside the beam: no blinding wall of light
        if (dist < 1.5f) k2 *= dist / 1.5f;
        int ri = clamp(r * k2), gi = clamp(g * k2), bi = clamp(b * k2);
        // gobo strands: the strand texture around the cone; otherwise the flat centre of the glow texture (smooth gradient)
        float u = textured ? (float) i / seg * uRep : 0.5f;
        float v = textured ? d * 0.18f - scroll : 0.5f;
        vc.vertex(mat, P.x, P.y, P.z).color(ri, gi, bi, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT).normal(nm, 0, 1, 0).endVertex();
    }

    private static int clamp(float x) {
        return (int) Math.max(0, Math.min(255, x * 255));
    }

    /**
     * Light projected on a surface (rev. 5: drawn with {@link LightRenderTypes#modulate}, it multiplies the surface instead of covering
     * it): a soft disc (glow texture) on the face, centred on {@code c}, facing {@code n}.
     */
    static void pool(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f c, Vector3f n, float rad, float r, float g, float b) {
        if (Math.abs(n.y) < 0.9f) U.set(0, 1, 0); else U.set(1, 0, 0);
        U.cross(n).normalize();
        V.set(n).cross(U).normalize();
        int ri = clamp(r), gi = clamp(g), bi = clamp(b);
        for (int c0 = 0; c0 < 4; c0++) {
            float[] k = CORNERS[c0];
            P.set(U).mul(k[0] * rad).add(V.x * k[1] * rad, V.y * k[1] * rad, V.z * k[1] * rad).add(c);
            vc.vertex(mat, P.x, P.y, P.z).color(ri, gi, bi, 255).uv(k[2], k[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(nm, 0, 1, 0).endVertex();
        }
    }

    /**
     * Soft beam (rev. 5), the visible light in the air. Two parts, weighted by the angle between the beam and the view
     * ({@link BeamOptics#sideWeight}, {@link BeamOptics#axisWeight}) so the beam reads the same from the front, the side or behind:
     * <ul>
     *   <li>a ribbon along the axis, turned towards the camera at every ring and as wide as the cone there, textured with the soft
     *   profile of the glow texture across its width: from the side it looks like a round volume of lit haze with no hard edge, no
     *   facets and no triangles;</li>
     *   <li>soft discs across the beam, seen when the camera looks along it (into the light, or from behind the fixture).</li>
     * </ul>
     * Brightness along the beam follows {@link BeamOptics#air}. {@code width}: ribbon width as a fraction of the cone radius (a narrower
     * inner ribbon gives the hard core of a beam fixture).
     */
    static void softBeam(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f o, Vector3f a, float len, float r0, float tanHalf, Vector3f cam,
                         float r, float g, float b, boolean hits, int quality, float width, boolean discs) {
        softBeam(vc, mat, nm, o, a, len, r0, tanHalf, cam, r, g, b, hits, quality, width, discs, r0, tanHalf);
    }

    /**
     * Rev. 14: {@code airR0} / {@code airTan}: the beam whose light this one carries, for the brightness law. A gobo shaft is a thin
     * part of a wide beam: its light thins out like the whole beam's, not like a beam of its own small size (which would never thin
     * out, and 37 of them burnt the sky to white).
     */
    static void softBeam(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f o, Vector3f a, float len, float r0, float tanHalf, Vector3f cam,
                         float r, float g, float b, boolean hits, int quality, float width, boolean discs, float airR0, float airTan) {
        int segs = BeamOptics.segments(quality);
        // view angle, from the middle of the beam
        P.set(a).mul(len * 0.5f).add(o).sub(cam);
        float pl = P.length();
        float cosView = pl < 1e-4f ? 1f : a.dot(P) / pl;
        float side = BeamOptics.sideWeight(cosView);
        // rings
        for (int k = 0; k <= segs; k++) {
            float t = (float) k / segs;
            float d = len * t * (0.45f + 0.55f * t);              // denser near the lens, where the light changes fastest
            RING_D[k] = d;
            float rr = BeamOptics.radius(r0, tanHalf, d) * width;
            float kk = BeamOptics.air(d, len, hits, airR0, airTan) * side;
            // rev. 8: the camera in the beam or next to it: the beam fades instead of becoming a wall of light
            float cx = o.x + a.x * d - cam.x, cy = o.y + a.y * d - cam.y, cz = o.z + a.z * d - cam.z;
            float dc = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            kk *= BeamOptics.cameraFade(dc, rr);
            // rev. 9: seen from far away a beam keeps a soft width of a few pixels (wider and dimmer) instead of shrinking to a hard
            // one-pixel line that flickers
            float wMin = BeamOptics.minBeamRadius(dc);
            if (rr < wMin) { kk *= (float) Math.sqrt(rr / wMin); rr = wMin; }
            RING_R[k] = rr;
            RING_K[k] = kk;
        }
        for (int k = 0; k < segs; k++) {
            if (RING_K[k] < 0.002f && RING_K[k + 1] < 0.002f) continue;
            ribbonEdge(vc, mat, nm, o, a, cam, k, 0f, r, g, b);
            ribbonEdge(vc, mat, nm, o, a, cam, k, 1f, r, g, b);
            ribbonEdge(vc, mat, nm, o, a, cam, k + 1, 1f, r, g, b);
            ribbonEdge(vc, mat, nm, o, a, cam, k + 1, 0f, r, g, b);
        }
        // discs across the beam
        int nd = discs ? BeamOptics.discs(quality) : 0;
        float aw = BeamOptics.axisWeight(cosView);
        if (nd == 0 || aw <= 0.01f) return;
        if (Math.abs(a.y) < 0.9f) U.set(0, 1, 0); else U.set(1, 0, 0);
        U.cross(a).normalize();
        V.set(a).cross(U).normalize();
        // the same total whatever the number of discs; much less for a wide wash (rev. 8: no flat walls seen along its axis)
        float per = aw * 0.45f * 6f / nd * BeamOptics.discWeight(BeamOptics.radius(r0, tanHalf, len * 0.5f) * width);
        for (int j = 0; j < nd; j++) {
            float d = len * (j + 0.6f) / (nd + 0.4f);
            float rad = BeamOptics.radius(r0, tanHalf, d) * 1.1f * width;
            float dx = o.x + a.x * d - cam.x, dy = o.y + a.y * d - cam.y, dz = o.z + a.z * d - cam.z;
            float k = BeamOptics.air(d, len, hits, r0, tanHalf) * per * BeamOptics.cameraFade((float) Math.sqrt(dx * dx + dy * dy + dz * dz), rad);
            if (k < 0.003f) continue;
            N.set(a).mul(d).add(o);
            int ri = clamp(r * k), gi = clamp(g * k), bi = clamp(b * k);
            for (int c0 = 0; c0 < 4; c0++) {
                float[] q = CORNERS[c0];
                P.set(U).mul(q[0] * rad).add(V.x * q[1] * rad, V.y * q[1] * rad, V.z * q[1] * rad).add(N);
                vc.vertex(mat, P.x, P.y, P.z).color(ri, gi, bi, 255).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(nm, 0, 1, 0).endVertex();
            }
        }
    }

    /** One edge vertex of the ribbon at ring {@code k}: {@code u} 0 or 1 = the two sides, the soft profile is in the texture. */
    private static void ribbonEdge(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f o, Vector3f a, Vector3f cam, int k, float u,
                                   float r, float g, float b) {
        float d = RING_D[k];
        P.set(a).mul(d).add(o);                                     // ring centre
        VIEW.set(P).sub(cam);
        N.set(a).cross(VIEW);                                       // across the beam, facing the camera
        float nl = N.length();
        if (nl < 1e-5f) { if (Math.abs(a.y) < 0.9f) N.set(0, 1, 0).cross(a); else N.set(1, 0, 0).cross(a); nl = N.length(); }
        float w = RING_R[k] * 1.15f / nl;                           // the profile reaches ~0 at the edges: a little wider than the cone
        float s = u * 2f - 1f;
        P.add(N.x * w * s, N.y * w * s, N.z * w * s);
        float kk = RING_K[k];
        vc.vertex(mat, P.x, P.y, P.z).color(clamp(r * kk), clamp(g * kk), clamp(b * kk), 255).uv(u, 0.5f)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, 0, 1, 0).endVertex();
    }

    /**
     * Halo (rev. 4): a soft glow quad facing the camera around a lens — the bloom of a bright source seen through haze (the glowing
     * lenses of the ExtraLights pictures, and the flash of a blinder). {@code left} / {@code up}: camera axes, same frame as {@code c}.
     */
    static void halo(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f c, Vector3f left, Vector3f up, float size, float r, float g, float b) {
        int ri = clamp(r), gi = clamp(g), bi = clamp(b);
        if ((ri | gi | bi) == 0) return;
        for (int c0 = 0; c0 < 4; c0++) {
            float[] k = CORNERS[c0];
            P.set(left).mul(k[0] * size).add(up.x * k[1] * size, up.y * k[1] * size, up.z * k[1] * size).add(c);
            vc.vertex(mat, P.x, P.y, P.z).color(ri, gi, bi, 255).uv(k[2], k[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(nm, 0, 1, 0).endVertex();
        }
    }
}
