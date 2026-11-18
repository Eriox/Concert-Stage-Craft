import com.concertstage.light.core.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;

/**
 * Off-game tests of the Optical Simulation System (1.2.0 rev. 16, OpticalProfile + concertstage_optics.glsl):
 * the lens rings found in the models, the energy of each distribution, the signature of each family (what makes a VORTEX beam, a
 * VORTEX spot, a W7, a W19 and a PULSESTRIKE different), and — when the shader harness is built — that the GLSL mask the mod ships
 * gives the same numbers as its Java twin, executed on a real OpenGL implementation (Mesa llvmpipe).
 * Usage: sh tools/lightshow_tests/run_optics.sh
 */
public class OpticsTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  ok   " + what); } else { failed++; System.out.println("  FAIL " + what); }
    }

    record Fx(FixtureProfile p, OpticalProfile op, float r0) {}

    static Map<String, Fx> load() throws Exception {
        Map<String, Fx> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of("tools/lightshow_tests/optics_cells.txt"))) {
            String[] t = line.split(";");
            String[] cs = t[3].split(" ");
            float[][] c = new float[cs.length][5];
            for (int i = 0; i < cs.length; i++) { String[] v = cs[i].split(","); for (int j = 0; j < 5; j++) c[i][j] = Float.parseFloat(v[j]); }
            FixtureProfile p = FixtureProfile.get(t[0]);
            out.put(t[0], new Fx(p, OpticalProfile.of(p, c, Float.parseFloat(t[1])), Float.parseFloat(t[2])));
            OpticsCells.cells.put(t[0], c);
        }
        return out;
    }

    static float[] u(Fx f, float angle, float sharp, boolean prism) {
        float[] u = new float[OpticalProfile.UNIFORM_FLOATS];
        f.op.uniforms(false, prism, 0.3f, angle, sharp, u, 0);
        return u;
    }

    static float tan(float angle) { return (float) Math.tan(Math.toRadians(angle / 2)); }
    static float tp(float angle) { return tan(angle); }

    /** Emitter cells of a fixture as read from optics_cells.txt (x of each cell, model frame). */
    static final class OpticsCells {
        static Map<String, float[][]> cells = new HashMap<>();
        static float x(Fx f, int z) { return cells.get(f.p.id)[z][0]; }
    }

    /** mean over the disc |q| <= 1 */
    static double mean(float[] u, float hw, float along, float tanH) {
        double s = 0; int n = 0;
        for (int i = -50; i <= 50; i++) for (int j = -50; j <= 50; j++) {
            float x = i / 50f, y = j / 50f;
            if (x * x + y * y > 1f) continue;
            s += OpticalProfile.lightAt(u, x, y, hw, along, tanH); n++;
        }
        return s / n;
    }

    /** contrast of the light around a circle of radius rq (max / min over the angle) */
    static double ripple(float[] u, float rq, float hw, float along, float tanH) {
        double mx = 0, mn = 1e9;
        for (int i = 0; i < 720; i++) {
            double a = i * Math.PI / 360;
            float v = OpticalProfile.lightAt(u, (float) (rq * Math.cos(a)), (float) (rq * Math.sin(a)), hw, along, tanH);
            mx = Math.max(mx, v); mn = Math.min(mn, v);
        }
        return mx / Math.max(mn, 1e-4);
    }

    /** half width (in q) where the light falls under half of its centre value, along x and along y */
    static float[] extent(float[] u, float hw, float along, float tanH) {
        float c = OpticalProfile.lightAt(u, 0, 0, hw, along, tanH);
        float[] out = new float[2];
        for (int axis = 0; axis < 2; axis++) {
            float q = 0;
            while (q < 1.1f) { float v = axis == 0 ? OpticalProfile.lightAt(u, q, 0, hw, along, tanH) : OpticalProfile.lightAt(u, 0, q, hw, along, tanH); if (v < 0.5f * c) break; q += 0.005f; }
            out[axis] = q;
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        System.out.println("Optical profiles (CSC PROFESSIONAL LIGHTING RENDER ENGINE)");
        Map<String, Fx> fx = load();
        Fx vx = fx.get("csc_vortex_x1"), w7 = fx.get("csc_lumora_w7"), w19 = fx.get("csc_lumora_w19"), ps = fx.get("csc_pulsestrike_m");
        // families and lens rings from the models
        check(vx.op.kind == OpticalProfile.SPOT && w7.op.kind == OpticalProfile.LENS_ARRAY && w19.op.kind == OpticalProfile.LENS_ARRAY
            && ps.op.kind == OpticalProfile.PANEL, "families: VORTEX spot / beam, W7 and W19 lens arrays, PULSESTRIKE panel");
        check(w7.op.rings == 3 && w7.op.ringN[0] == 1 && w7.op.ringN[1] == 6 && w7.op.ringN[2] == 12, "W7: 19 lenses found as rings 1 + 6 + 12");
        check(w19.op.rings == 4 && w19.op.ringN[0] == 1 && w19.op.ringN[1] == 6 && w19.op.ringN[2] == 12 && w19.op.ringN[3] == 18,
            "W19: 37 lenses found as rings 1 + 6 + 12 + 18");
        check(ps.op.panelX > 2.5f * ps.op.panelY && ps.op.cellX > 0f && ps.op.cellY > 0f,
            String.format(Locale.ROOT, "PULSESTRIKE face %.2f x %.2f blocks, cell pitch %.3f / %.3f", 2 * ps.op.panelX, 2 * ps.op.panelY, ps.op.cellX, ps.op.cellY));

        // energy: the mean over the beam stays close to 1 at every distance (the renderer's calibration does not depend on the family)
        boolean energy = true;
        StringBuilder em = new StringBuilder();
        for (Fx f : fx.values()) {
            float ang = f.p.beamAngle(f.p.home(Attr.ZOOM)), t = tan(ang);
            float[] u = u(f, ang, 1f, false);
            for (float along : new float[] {0.3f, 2f, 8f, 25f}) {
                double m = mean(u, f.r0 + along * t, along, t);
                energy &= m > 0.6 && m < 1.25;
                em.append(String.format(Locale.ROOT, " %.2f", m));
            }
        }
        check(energy, "mean of every distribution over its beam within 0.6..1.25 (VORTEX, W7, W19, PULSESTRIKE at 0.3 / 2 / 8 / 25 blocks:" + em + ")");

        // VORTEX: beam mode = a concentrated core, spot mode = an even disc whose edge follows the focus
        float[] beam = u(vx, 3f, 1f, false), spot = u(vx, 28f, 1f, false), soft = u(vx, 28f, 0f, false);
        float tb = tan(3f), ts = tan(28f);
        float coreRatio = OpticalProfile.lightAt(beam, 0, 0, 1, 10, tb) / OpticalProfile.lightAt(beam, 0.7f, 0, 1, 10, tb);
        float flat = OpticalProfile.lightAt(spot, 0.6f, 0, 1, 10, ts) / OpticalProfile.lightAt(spot, 0, 0, 1, 10, ts);
        check(coreRatio > 5f, String.format(Locale.ROOT, "VORTEX beam: centre %.1f x brighter than at 70 %% of the radius", coreRatio));
        check(flat > 0.8f, String.format(Locale.ROOT, "VORTEX spot: even disc (60 %% of the radius at %.0f %% of the centre)", flat * 100));
        float sharpEdge = OpticalProfile.lightAt(spot, 0.9f, 0, 1, 10, ts), softEdge = OpticalProfile.lightAt(soft, 0.9f, 0, 1, 10, ts);
        check(sharpEdge > 1.5f * softEdge, String.format(Locale.ROOT, "VORTEX focus: edge at 90 %% of the radius %.2f sharp vs %.2f soft", sharpEdge, softEdge));
        // prism: three images, none at the centre
        float[] pr = u(vx, 12f, 1f, true);
        float centre = OpticalProfile.lightAt(pr, 0, 0, 1, 10, tan(12f)), img = OpticalProfile.lightAt(pr, 0.45f * (float) Math.cos(0.3), 0.45f * (float) Math.sin(0.3), 1, 10, tan(12f));
        check(img > 3f * centre, String.format(Locale.ROOT, "VORTEX 3-facet prism: image centre %.2f, beam centre %.2f", img, centre));

        // washes: each lens its own shaft near the face, an even wash further away; W19 not a scaled W7
        for (Fx f : new Fx[] {w7, w19}) {
            // narrowest zoom: every lens beam has the angle of the fixture, so its own shaft is seen for a short way out of the face
            float ang = f.p.beamAngle(0f), t = tan(ang);
            float[] u = u(f, ang, 1f, false);
            float alongN = 0.3f, alongF = 15f;
            float hwN = f.r0 + alongN * t, hwF = f.r0 + alongF * t;
            float rq = f.op.ringR[f.op.rings - 1] / hwN;
            double near = ripple(u, rq, hwN, alongN, t), far = ripple(u, 0.5f, hwF, alongF, t);
            check(near > 1.4 && far < 1.08, String.format(Locale.ROOT, "%s at %.0fdeg: lens shafts out of the face (ripple x%.2f on the outer ring at %.1f block), even wash at %.0f blocks (x%.3f)",
                f.p.name, ang, near, alongN, alongF, far));
        }
        // W19 against W7, both at their narrowest zoom, 1 block out: rings, face and skirt differ (not a scaled copy)
        float n7 = w7.p.beamAngle(0f), n19 = w19.p.beamAngle(0f);
        float[] v7 = u(w7, n7, 1f, false), v19 = u(w19, n19, 1f, false);
        double dd = 0;
        float h7 = w7.r0 + tan(n7), h19 = w19.r0 + tan(n19);
        for (int i = 0; i <= 80; i++) {
            float x = i / 80f * 0.9f * Math.min(h7, h19);         // blocks from the axis, inside both beams
            float l7 = OpticalProfile.lightAt(v7, x / h7, 0, h7, 1f, tan(n7)) / (h7 * h7), l19 = OpticalProfile.lightAt(v19, x / h19, 0, h19, 1f, tan(n19)) / (h19 * h19) * (37f / 19f);
            dd += Math.abs(l7 - l19) / Math.max(0.5f * (l7 + l19), 1e-3f);
        }
        check(dd / 81 > 0.15, String.format(Locale.ROOT, "W19 cross-section differs from the W7 one (mean relative difference %.0f %% at 1 block, same light per lens)", 100 * dd / 81));
        float a7 = w7.p.beamAngle(w7.p.home(Attr.ZOOM)), a19 = w19.p.beamAngle(w19.p.home(Attr.ZOOM));
        // PULSESTRIKE: the rectangle of its face near it, a broad round flood far away; the zoom narrows it
        float ap = ps.p.beamAngle(ps.p.home(Attr.ZOOM)), tp = tan(ap);
        float[] up = u(ps, ap, 1f, false);
        float[] en = extent(up, ps.r0 + 0.15f * tp, 0.15f, tp), ef = extent(up, ps.r0 + 20f * tp, 20f, tp);
        check(en[0] > 1.8f * en[1] && ef[0] < 1.25f * ef[1], String.format(Locale.ROOT, "PULSESTRIKE: rectangular near the face (%.2f x %.2f), round far away (%.2f x %.2f)", en[0], en[1], ef[0], ef[1]));
        float aNarrow = ps.p.beamAngle(0f), tn = tan(aNarrow);
        float wideW = ps.r0 + 10f * tp, narrowW = ps.r0 + 10f * tn;
        check(narrowW < 0.5f * wideW, String.format(Locale.ROOT, "PULSESTRIKE zoom: footprint at 10 blocks %.1f blocks wide at %.0fdeg, %.1f at %.0fdeg", 2 * narrowW, aNarrow, 2 * wideW, ap));
        double gmx = 0, gmn = 1e9;
        for (int i = 0; i <= 200; i++) {
            float x = (i / 200f - 0.5f) * 0.6f * ps.op.panelX / (ps.r0 + 0.05f * tp);
            float v = OpticalProfile.lightAt(up, x, 0, ps.r0 + 0.05f * tp, 0.05f, tp);
            gmx = Math.max(gmx, v); gmn = Math.min(gmn, v);
        }
        check(gmx > 2 * gmn, String.format(Locale.ROOT, "PULSESTRIKE: grid of the cells visible right at the face (x%.1f)", gmx / Math.max(gmn, 1e-4)));

        // rev. 16b: colours of the cells in the light (matrix effects seen on the floor and in the haze)
        float[] zp = new float[ps.p.zones * 4];
        for (int z = 0; z < ps.p.zones; z++) {
            boolean left = OpticsCells.x(ps, z) < 0;
            zp[z * 4] = left ? 1f : 0f; zp[z * 4 + 1] = 0f; zp[z * 4 + 2] = left ? 0f : 1f; zp[z * 4 + 3] = 1f;
        }
        float[] tps = new float[OpticalProfile.TINT_FLOATS];
        boolean onP = ps.op.tints(zp, tps, 0);
        float[] rgb = new float[3];
        float hwNear = ps.r0 + 0.1f * tan(ap);
        OpticalProfile.tint(up, tps, -0.6f * ps.op.panelX / hwNear, 0, hwNear, 0.1f, tan(ap), rgb);
        float lr = rgb[0], lb = rgb[2];
        OpticalProfile.tint(up, tps, 0.6f * ps.op.panelX / hwNear, 0, hwNear, 0.1f, tan(ap), rgb);
        float rr = rgb[0], rb = rgb[2];
        float hwFar = ps.r0 + 20f * tan(ap);
        OpticalProfile.tint(up, tps, 0f, 0, hwFar, 20f, tan(ap), rgb);
        check(onP && ps.op.groups == 7 && lr > 0.8f && lb < 0.2f && rb > 0.8f && rr < 0.2f && Math.abs(rgb[0] - rgb[2]) < 0.2f,
            String.format(Locale.ROOT, "PULSESTRIKE zones: %d columns; red / blue halves near the face (left %.2f/%.2f, right %.2f/%.2f), mixed far away (%.2f/%.2f)",
                ps.op.groups, lr, lb, rr, rb, rgb[0], rgb[2]));
        float[] zw = new float[w7.p.zones * 4];
        for (int z = 0; z < w7.p.zones; z++) {
            boolean inner = w7.op.groupOf[z] == 0;
            zw[z * 4] = inner ? 1f : 0f; zw[z * 4 + 2] = inner ? 0f : 1f; zw[z * 4 + 3] = 1f;
        }
        float[] tw = new float[OpticalProfile.TINT_FLOATS];
        boolean onW = w7.op.tints(zw, tw, 0);
        float n7z = w7.p.beamAngle(0f), h7n = w7.r0 + 0.2f * tan(n7z);
        float[] v7n = u(w7, n7z, 1f, false);
        OpticalProfile.tint(v7n, tw, 0, 0, h7n, 0.2f, tan(n7z), rgb);
        float cR = rgb[0];
        OpticalProfile.tint(v7n, tw, w7.op.ringR[2] / h7n, 0, h7n, 0.2f, tan(n7z), rgb);
        check(onW && cR > 0.7f && rgb[2] > 0.7f, String.format(Locale.ROOT, "W7 rings: centre lens red (%.2f), outer ring blue (%.2f) near the face", cR, rgb[2]));
        float[] same = new float[w7.p.zones * 4];
        for (int z = 0; z < w7.p.zones; z++) { same[z * 4] = 0.2f; same[z * 4 + 1] = 0.9f; same[z * 4 + 3] = 1f; }
        check(!w7.op.tints(same, tw, 0), "one colour on every cell: no tint (plain beam colour, no extra cost)");

        // GLSL twin, on a real OpenGL implementation (Mesa llvmpipe through the shader harness), when it is built
        Path harness = Path.of(System.getProperty("harness", "/tmp/harness"));
        if (!Files.isExecutable(harness)) {
            System.out.println("  (skipped: GLSL comparison, shader harness not built — gcc -O2 -o /tmp/harness tools/shader_harness/harness.c -ldl)");
        } else {
            String shaders = "common/src/main/resources/assets/minecraft/shaders";
            Object[][] cases = {
                {vx, 3f, 1f, false, 10f}, {vx, 28f, 0.3f, false, 4f}, {vx, 12f, 1f, true, 6f},
                {w7, a7, 1f, false, 0.3f}, {w7, a7, 1f, false, 6f}, {w19, a19, 1f, false, 0.3f}, {w19, w19.p.beamAngle(0f), 1f, false, 3f},
                {ps, ap, 1f, false, 0.08f}, {ps, aNarrow, 1f, false, 5f}, {ps, ap, 1f, false, 0.3f, tps}, {w7, n7z, 1f, false, 0.4f, tw}};
            ps.op.tints(zp, tps, 0); w7.op.tints(zw, tw, 0);
            int n = 64;
            double worst = 0;
            for (Object[] c : cases) {
                Fx f = (Fx) c[0];
                float ang = (Float) c[1], along = (Float) c[4], t = tan(ang), hw = f.r0 + along * t;
                float[] u = u(f, ang, (Float) c[2], (Boolean) c[3]);
                StringBuilder s = new StringBuilder("f TargetSize " + n + " " + n + "\n");
                s.append(String.format(Locale.ROOT, "f ProbeArgs %f %f %f 0\n", hw, along, t));
                s.append(String.format(Locale.ROOT, "f OptKind %f %f %f %f\nf OptShape %f %f %f %f\n", u[0], u[1], u[2], u[3], u[4], u[5], u[6], u[7]));
                for (int r = 0; r < OpticalProfile.MAX_RINGS; r++)
                    s.append(String.format(Locale.ROOT, "f OptRing%d %f %f %f %f\n", r, u[8 + r * 4], u[9 + r * 4], u[10 + r * 4], u[11 + r * 4]));
                float[] tt = c.length > 5 ? (float[]) c[5] : new float[OpticalProfile.TINT_FLOATS];
                s.append(String.format(Locale.ROOT, "f OptTintInfo %f %f 0 0\n", tt[0], tt[1]));
                for (int g = 0; g < OpticalProfile.MAX_TINTS; g++)
                    s.append(String.format(Locale.ROOT, "f OptTint%d %f %f %f 0\n", g, tt[4 + g * 4], tt[5 + g * 4], tt[6 + g * 4]));
                Path uf = Files.createTempFile("optics", ".txt"), out = Files.createTempFile("optics", ".raw");
                Files.writeString(uf, s.toString());
                ProcessBuilder pb = new ProcessBuilder(harness.toString(), "tools/shader_harness/passthrough.vsh", "tools/shader_harness/optics_probe.fsh",
                    shaders + "/include", uf.toString(), "" + n, "" + n, out.toString()).redirectErrorStream(true);
                pb.environment().put("EGL_PLATFORM", "surfaceless");
                Process pr2 = pb.start();
                String log = new String(pr2.getInputStream().readAllBytes());
                if (pr2.waitFor() != 0) { check(false, "harness: " + log.trim()); continue; }
                ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(out)).order(ByteOrder.LITTLE_ENDIAN);
                for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                    float g = bb.getFloat((y * n + x) * 16);
                    float qx = ((x + 0.5f) / n * 2f - 1f) * 1.1f, qy = ((y + 0.5f) / n * 2f - 1f) * 1.1f;
                    float j = OpticalProfile.lightAt(u, qx, qy, hw, along, t);
                    worst = Math.max(worst, Math.abs(g - j) / Math.max(1f, Math.abs(j)));
                    float[] jt = new float[3];
                    OpticalProfile.tint(u, tt, qx, qy, hw, along, t, jt);
                    for (int ch = 0; ch < 3; ch++) worst = Math.max(worst, Math.abs(bb.getFloat((y * n + x) * 16 + 4 + 4 * ch) - jt[ch]));
                }
            }
            check(worst < 2e-3, String.format(Locale.ROOT, "GLSL csc_light + csc_tint (Mesa llvmpipe) = Java OpticalProfile on %d cases x %d points (worst relative difference %.1e)",
                cases.length, n * n, worst));
        }
        System.out.println();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }
}
