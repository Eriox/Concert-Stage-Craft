import java.util.Locale;

/**
 * Rev. 24 — washes + gobos on the same floor: the CPU copy of the accumulation and of concertstage_light_tonemap.fsh, before (8-bit
 * target clipped at L = 4, saturation per channel) and after (half floats, saturation on the brightest channel). Floor: light concrete.
 */
public class MixTest {
    static int fails, checks;
    static void check(boolean ok, String what) { checks++; if (!ok) fails++; System.out.println((ok ? "  ok   " : "  FAIL ") + what); }

    static float[] tone(float[] scene, float[] L, boolean after) {
        float gain = 1.2f, addK = 0.10f, knee = 0.65f;
        float[] l = L.clone();
        if (!after) for (int i = 0; i < 3; i++) l[i] = Math.min(4f, Math.round(l[i] / 4f * 255f) / 255f * 4f);   // RGBA8, stored / 4
        float m = Math.max(l[0], Math.max(l[1], l[2]));
        float mn = Math.min(l[0], Math.min(l[1], l[2]));
        float sat = 1 - mn / m;
        float[] c = new float[3];
        for (int i = 0; i < 3; i++) {
            float hue = l[i] / m;
            float add = after ? hue * Math.min(m / gain, 1f) * addK * (0.75f + 1.25f * sat) : Math.min(l[i] / gain, 1f) * addK * (0.75f + 1.25f * sat);
            float f = after ? 1 + 2.2f * hue * (float) Math.tanh(m / 1.83f) : 1 + 2.2f * (float) Math.tanh(l[i] / 1.83f);
            c[i] = scene[i] * f + add;
        }
        float peak = Math.max(c[0], Math.max(c[1], c[2]));
        if (peak > knee) { float x = (peak - knee) / (1 - knee); float np = knee + (1 - knee) * x / (1 + x); for (int i = 0; i < 3; i++) c[i] *= np / peak; }
        return c;
    }

    static float hueDist(float[] a, float[] b) {
        float sa = a[0] + a[1] + a[2], sb = b[0] + b[1] + b[2];
        float d = 0; for (int i = 0; i < 3; i++) d += Math.abs(a[i] / sa - b[i] / sb);
        return d;
    }

    static String rgb(float[] c) { return String.format(Locale.ROOT, "%3d %3d %3d", (int) (255 * c[0]), (int) (255 * c[1]), (int) (255 * c[2])); }

    public static void main(String[] a) {
        float[] floor = {.62f, .60f, .57f};
        float[][] washes = {{0, 3.5f, 4.5f}, {0, 6f, 8f}, {4.5f, 0, 6f}, {6f, 6f, 6f}};
        float[][] gobos = {{4f, 0, 3f}, {4f, .8f, 0}, {0, 4f, 0}};
        System.out.println("wash L              gobo L          | before: wash | wash+gobo | hue gap || after: wash | wash+gobo | hue gap");
        boolean worse = false; float floorLike = 0; int floorBefore = 0, floorAfter = 0;
        for (float[] w : washes) for (float[] g : gobos) {
            float[] wg = {w[0] + g[0], w[1] + g[1], w[2] + g[2]};
            float[] b0 = tone(floor, w, false), b1 = tone(floor, wg, false), a0 = tone(floor, w, true), a1 = tone(floor, wg, true);
            float hb = hueDist(b0, b1), ha = hueDist(a0, a1);
            System.out.println(String.format(Locale.ROOT, "%-18s %-15s | %s | %s | %.3f || %s | %s | %.3f",
                java.util.Arrays.toString(w), java.util.Arrays.toString(g), rgb(b0), rgb(b1), hb, rgb(a0), rgb(a1), ha));
            // the colour of the light that falls there (wash + gobo): how far the result is from it, before / after
            float db = hueDist(b1, wg), da = hueDist(a1, wg);
            float lsat = 1 - Math.min(wg[0], Math.min(wg[1], wg[2])) / Math.max(wg[0], Math.max(wg[1], wg[2]));
            if (lsat < 0.25f) continue;   // wash + gobo is itself near white: the floor colour is right there
            if (ha < 0.07f) { worse = true; System.out.println("   gobo not visible on the wash: " + java.util.Arrays.toString(w) + " + " + java.util.Arrays.toString(g)); }
            if (!(w[0] == 6f && w[1] == 6f)) floorLike = Math.max(floorLike, 1 - hueDist(a1, floor) / 0.3f);
            if (hb > 0.4f && hueDist(b1, floor) < 0.05f) floorBefore++;
            if (hueDist(a1, floor) < 0.05f) floorAfter++;
        }
        System.out.println("   wash + gobo with the colour of the floor: before " + floorBefore + " cases, after " + floorAfter);
        check(floorBefore > 0, "before: reproduces the bug (a coloured wash + a gobo gives the colour of the floor)");
        check(floorAfter == 0, "after: no wash + gobo mix gives the colour of the floor");
        check(!worse, "after: the gobo is visible on every coloured wash (hue gap > 0.07)");
        // one fixture: unchanged for a white light (the factor is the same on the three channels)
        float[] w1 = tone(floor, new float[] {.8f, .8f, .8f}, true), w0 = tone(floor, new float[] {.8f, .8f, .8f}, false);
        check(Math.abs(w1[0] - w0[0]) < .01f, "one white fixture: as before (" + rgb(w0) + " / " + rgb(w1) + ")");
        // monotonic
        float prev = 0; boolean mono = true;
        for (int n = 1; n <= 10; n++) { float[] c = tone(floor, new float[] {n * .9f, n * .5f, 0}, true); float s = Math.max(c[0], Math.max(c[1], c[2])); mono &= s >= prev - 1e-6f; prev = s; }
        check(mono, "after: more light = brighter (brightest channel)");
        // never clipped
        float[] hot = tone(floor, new float[] {40, 40, 40}, true);
        check(hot[0] < 1f, "after: 40 white fixtures not clipped (" + rgb(hot) + ")");
        System.out.println(fails == 0 ? "MixTest PASS (" + checks + " checks)" : "MixTest FAIL " + fails + "/" + checks);
        System.exit(fails == 0 ? 0 : 1);
    }
}
