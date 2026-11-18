package com.concertstage.client.light;

import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.core.Attr;
import com.concertstage.light.core.BeamOptics;
import com.concertstage.light.core.Engine;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * CSC 1.3.0 rev. 1 — the beams of the LASERCORE L1 in the air.
 *
 * <p>Before, the laser was drawn like a 30° wash: a cone of haze with the scanned figure as its mask (OpticalProfile kind 3). The
 * figure is a few hundredths of the cone wide, thinner than the steps of the haze pass: in the air it showed as a faint grainy cone,
 * not as laser lines. Now each line of the figure is a line of light of its own — a thin bright core and a soft glow — from the
 * aperture to the first block it meets (one ray per line, at most once per game tick), with a small dot where it lands. The figure is
 * the one of the projection on the surfaces (same pattern, size, scan speed and orientation as {@code ProfessionalOptics.laser} and
 * {@code csc_laser}): dot, fan of 5 beams, circle (a tunnel), cross (two sheets), square.
 */
public final class LaserBeams {
    /** Most lines of a figure (circle and square: 24, cross: 18). */
    public static final int MAX_LINES = 24;

    private LaserBeams() {}

    /**
     * The lines of the figure at this moment, as offsets in the cone (q, -1..1 at the edge of the cone, in the side / up frame of the
     * optical mask). Returns the number of lines; {@code out} gets x, y pairs.
     */
    public static int figure(Engine.Output o, double seconds, float[] out) {
        int pattern = Math.min(4, (int) (o.get(Attr.LASER_PATTERN) * 5));
        lastPattern = pattern;
        float size = 0.1f + 0.8f * o.get(Attr.LASER_SIZE);
        double rot = seconds * (0.1 + 2.9 * o.get(Attr.LASER_SPEED)) % (2 * Math.PI);
        float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
        int n = 0;
        switch (pattern) {
            case 0 -> { out[0] = 0; out[1] = 0; n = 1; }
            case 1 -> { for (int i = 0; i < 5; i++) n = put(out, n, (i - 2) * 0.4f * size, 0f, c, s); }
            case 2 -> {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    n = put(out, n, (float) Math.cos(a) * size, (float) Math.sin(a) * size, c, s);
                }
            }
            case 3 -> {
                // two arms of 9 lines (the centre line twice, so each arm is a sheet of its own)
                for (int i = -4; i <= 4; i++) n = put(out, n, i * 0.22f, 0f, c, s);
                for (int i = -4; i <= 4; i++) n = put(out, n, 0f, i * 0.22f, c, s);
            }
            default -> {
                float h = size * 0.7f;
                for (int side = 0; side < 4; side++) {
                    for (int i = 0; i < 6; i++) {
                        float t = -h + 2 * h * i / 6f;
                        float x = side == 0 ? t : side == 1 ? h : side == 2 ? -t : -h;
                        float y = side == 0 ? -h : side == 1 ? t : side == 2 ? h : -t;
                        n = put(out, n, x, y, c, s);
                    }
                }
            }
        }
        return n;
    }

    /** The point r of the figure (pattern frame) back into the cone: q = R(-rot) r, the inverse of the shader's r = R(rot) q. */
    private static int put(float[] out, int n, float rx, float ry, float c, float s) {
        out[n * 2] = rx * c + ry * s;
        out[n * 2 + 1] = -rx * s + ry * c;
        return n + 1;
    }

    private static final float[] Q = new float[MAX_LINES * 2];
    private static int lastPattern;
    private static final float[] SX = new float[MAX_LINES * 3], EX = new float[MAX_LINES * 3];
    private static final Vector3f SIDE = new Vector3f(), UP = new Vector3f(), START = new Vector3f(), DIR = new Vector3f(),
        END = new Vector3f(), TMP = new Vector3f();

    /**
     * Draws the lines of a laser (block-local coordinates in {@code mat}). {@code k}: strength of the lines in the haze (already with
     * the haze, the brightness settings and the colour of the laser in r, g, b). {@code rays}: whether the lines may be measured again
     * now (the main view; a camera image reuses the last lengths).
     */
    static void draw(VertexConsumer core, VertexConsumer dots, Matrix4f mat, Matrix3f nm, FixtureBlockEntity be, Level level, Engine.Output o,
                     double seconds, Vector3f origin, Vector3f axis, float r0, float tanHalf, float throwLen, Vector3f camL,
                     float r, float g, float b, float k, boolean rays, long tick) {
        int n = figure(o, seconds, Q);
        SIDE.set(Math.abs(axis.y) < 0.9f ? 0 : 1, Math.abs(axis.y) < 0.9f ? 1 : 0, 0).cross(axis).normalize();
        UP.set(axis).cross(SIDE).normalize();
        boolean measure = rays && level != null && (be.laserTick != tick || be.laserCount != n);
        if (measure) { be.laserTick = tick; be.laserCount = n; }
        BlockPos bp = be.getBlockPos();
        // a scanned figure shares the power of the source: each line is dimmer the more lines there are (perceived, not linear)
        float per = k / (float) Math.sqrt(n);
        for (int i = 0; i < n; i++) {
            float qx = Q[i * 2], qy = Q[i * 2 + 1];
            START.set(SIDE).mul(qx * r0).add(UP.x * qy * r0, UP.y * qy * r0, UP.z * qy * r0).add(origin);
            DIR.set(SIDE).mul(qx * tanHalf).add(UP.x * qy * tanHalf, UP.y * qy * tanHalf, UP.z * qy * tanHalf).add(axis).normalize();
            if (measure || be.laserLen[i] <= 0f) {
                if (level != null) {
                    Vec3 from = new Vec3(bp.getX() + START.x + DIR.x * 0.2, bp.getY() + START.y + DIR.y * 0.2, bp.getZ() + START.z + DIR.z * 0.2);
                    Vec3 to = from.add(DIR.x * throwLen, DIR.y * throwLen, DIR.z * throwLen);
                    BlockHitResult hit = FixtureRenderer.clipRig(level, from, to, bp);
                    boolean h = hit != null && hit.getType() == HitResult.Type.BLOCK;
                    be.laserLen[i] = h ? (float) hit.getLocation().distanceTo(from) + 0.2f : throwLen;
                    be.laserHit[i] = h;
                } else {
                    be.laserLen[i] = throwLen;
                    be.laserHit[i] = false;
                }
            }
            float len = be.laserLen[i];
            END.set(DIR).mul(len).add(START);
            line(core, mat, nm, START, END, camL, per, r, g, b);
            if (be.laserHit[i] && dots != null) dot(dots, mat, nm, END, camL, per, r, g, b);
            SX[i * 3] = START.x; SX[i * 3 + 1] = START.y; SX[i * 3 + 2] = START.z;
            EX[i * 3] = DIR.x; EX[i * 3 + 1] = DIR.y; EX[i * 3 + 2] = DIR.z;
        }
        // a scanned figure (circle, cross, square) sweeps a sheet of light between its lines: a faint veil between two neighbours,
        // as long as the shorter of the two (it never reaches past the surface either line meets)
        if (lastPattern >= 2) {
            float ks = 0.10f * per;
            boolean closed = lastPattern != 3;
            for (int i = 0; i < n; i++) {
                int j = i + 1;
                if (j == n) { if (!closed) break; j = 0; }
                if (lastPattern == 3 && i == 8) continue;                // between the two arms of the cross
                sheet(core, mat, nm, i, j, Math.min(be.laserLen[i], be.laserLen[j]), camL, r * ks, g * ks, b * ks);
            }
        }
    }

    private static final Vector3f A0 = new Vector3f(), A1 = new Vector3f(), B0 = new Vector3f(), B1 = new Vector3f();

    /** The veil between the lines i and j (the bright middle row of the soft texture: an even sheet, faded only at its far end). */
    private static void sheet(VertexConsumer vc, Matrix4f mat, Matrix3f nm, int i, int j, float len, Vector3f cam, float r, float g, float b) {
        if (Math.max(r, Math.max(g, b)) < 0.003f || len < 0.3f) return;
        A0.set(SX[i * 3], SX[i * 3 + 1], SX[i * 3 + 2]);
        B0.set(SX[j * 3], SX[j * 3 + 1], SX[j * 3 + 2]);
        A1.set(EX[i * 3], EX[i * 3 + 1], EX[i * 3 + 2]).mul(len).add(A0);
        B1.set(EX[j * 3], EX[j * 3 + 1], EX[j * 3 + 2]).mul(len).add(B0);
        float fa = BeamOptics.cameraFade(TMP.set(A0).sub(cam).length(), 0.05f);
        vertex(vc, mat, nm, A0.x, A0.y, A0.z, 0.5f, 0.5f, r * fa, g * fa, b * fa);
        vertex(vc, mat, nm, B0.x, B0.y, B0.z, 0.5f, 0.5f, r * fa, g * fa, b * fa);
        vertex(vc, mat, nm, B1.x, B1.y, B1.z, 0.5f, 0.5f, r * 0.6f, g * 0.6f, b * 0.6f);
        vertex(vc, mat, nm, A1.x, A1.y, A1.z, 0.5f, 0.5f, r * 0.6f, g * 0.6f, b * 0.6f);
    }

    /**
     * One line: a camera-facing ribbon, a hot core (whiter than the colour, as the eye sees a laser) and a glow 5 times wider. Never
     * thinner than about 2 pixels (a thinner line flickers); the line fades only gently with distance (a laser stays narrow) and in
     * the last 30 cm before the surface.
     */
    private static void line(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f a, Vector3f b, Vector3f cam, float k,
                             float r, float g, float bl) {
        float hot = 0.35f;
        float cr = r + (1 - r) * hot, cg = g + (1 - g) * hot, cb = bl + (1 - bl) * hot;
        float da = TMP.set(a).sub(cam).length(), db = TMP.set(b).sub(cam).length();
        // the camera next to the aperture: the line fades instead of filling the screen
        float fa = BeamOptics.cameraFade(da, 0.05f);
        float wa = Math.max(0.012f, BeamOptics.minBeamRadius(da) * 0.7f), wb = Math.max(0.018f, BeamOptics.minBeamRadius(db) * 0.7f);
        // thin core: the thinner it is drawn (far away) the less light per pixel, so the same line keeps its brightness
        float ka = k * fa * Math.min(1f, 0.012f / wa * 1.6f), kb = k * 0.7f * Math.min(1f, 0.018f / wb * 1.6f);
        ribbon(vc, mat, nm, a, b, cam, wa, wb, cr * ka, cg * ka, cb * ka, cr * kb, cg * kb, cb * kb);
        float ga = 0.22f * k * fa, gb = 0.16f * k;
        ribbon(vc, mat, nm, a, b, cam, wa * 5f, wb * 5f, r * ga, g * ga, bl * ga, r * gb, g * gb, bl * gb);
    }

    private static final Vector3f D = new Vector3f(), V = new Vector3f(), S = new Vector3f();

    private static void ribbon(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f a, Vector3f b, Vector3f cam, float wa, float wb,
                               float ra, float ga, float ba, float rb, float gb, float bb) {
        if (Math.max(ra, Math.max(ga, ba)) < 0.003f && Math.max(rb, Math.max(gb, bb)) < 0.003f) return;
        D.set(b).sub(a);
        V.set(a).add(b).mul(0.5f).sub(cam);
        S.set(D).cross(V);
        if (S.lengthSquared() < 1e-10f) return;                // looking straight down the line
        S.normalize();
        // the soft texture is round: the middle row (v = 0.5) gives the profile across the line, the same all along it
        vertex(vc, mat, nm, a.x - S.x * wa, a.y - S.y * wa, a.z - S.z * wa, 0, 0.5f, ra, ga, ba);
        vertex(vc, mat, nm, a.x + S.x * wa, a.y + S.y * wa, a.z + S.z * wa, 1, 0.5f, ra, ga, ba);
        vertex(vc, mat, nm, b.x + S.x * wb, b.y + S.y * wb, b.z + S.z * wb, 1, 0.5f, rb, gb, bb);
        vertex(vc, mat, nm, b.x - S.x * wb, b.y - S.y * wb, b.z - S.z * wb, 0, 0.5f, rb, gb, bb);
    }

    /** The bright dot where a line lands (a camera-facing glow, a little in front of the surface). */
    private static void dot(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f c, Vector3f cam, float k, float r, float g, float b) {
        float d = new Vector3f(cam).sub(c).length();
        float kk = Math.min(1.5f, k * 2.2f);
        sprite(vc, mat, nm, c, cam, Math.max(0.07f, BeamOptics.minBeamRadius(d) * 2.5f), 0.06f,
            (r + (1 - r) * 0.3f) * kk, (g + (1 - g) * 0.3f) * kk, (b + (1 - b) * 0.3f) * kk);
    }

    /** A camera-facing square of radius {@code rad} at {@code c}, moved {@code lift} towards the camera (glows, dots). */
    static void sprite(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f c, Vector3f cam, float rad, float lift, float cr, float cg, float cb) {
        Vector3f toCam = new Vector3f(cam).sub(c);
        float d = toCam.length();
        if (d < 1e-3f) return;
        toCam.div(d);
        Vector3f p = new Vector3f(c).add(toCam.x * lift, toCam.y * lift, toCam.z * lift);
        Vector3f u = new Vector3f(Math.abs(toCam.y) < 0.9f ? 0 : 1, Math.abs(toCam.y) < 0.9f ? 1 : 0, 0).cross(toCam).normalize();
        Vector3f v = new Vector3f(toCam).cross(u).normalize();
        u.mul(rad); v.mul(rad);
        vertex(vc, mat, nm, p.x - u.x - v.x, p.y - u.y - v.y, p.z - u.z - v.z, 0, 0, cr, cg, cb);
        vertex(vc, mat, nm, p.x + u.x - v.x, p.y + u.y - v.y, p.z + u.z - v.z, 1, 0, cr, cg, cb);
        vertex(vc, mat, nm, p.x + u.x + v.x, p.y + u.y + v.y, p.z + u.z + v.z, 1, 1, cr, cg, cb);
        vertex(vc, mat, nm, p.x - u.x + v.x, p.y - u.y + v.y, p.z - u.z + v.z, 0, 1, cr, cg, cb);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f nm, float x, float y, float z, float u, float v, float r, float g, float b) {
        int ri = (int) Math.min(255, Math.max(0, r * 255)), gi = (int) Math.min(255, Math.max(0, g * 255)), bi = (int) Math.min(255, Math.max(0, b * 255));
        vc.vertex(mat, x, y, z).color(ri, gi, bi, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
            .normal(nm, 0, 1, 0).endVertex();
    }
}
