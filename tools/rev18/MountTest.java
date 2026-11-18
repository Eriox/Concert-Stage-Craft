import com.concertstage.client.light.MountGeometry;
import com.concertstage.client.light.MountGeometry.Tube;
import java.nio.file.*;
import java.util.*;

/**
 * Rev. 18: hanging a fixture under every truss model of the mod (truss_tubes.json, extracted from the models drawn in the world):
 * which fixing is chosen, where the fixture top ends up, how heavy the hardware is. Writes OBJ files of truss + hardware for the
 * pictures (tools/rev18/render_mounts.py). Usage: java MountTest <repo root> <out dir>
 */
public class MountTest {
    static int fail;
    static void check(boolean ok, String w) { if (!ok) { fail++; System.out.println("FAIL " + w); } }

    public static void main(String[] a) throws Exception {
        Path root = Path.of(a[0]), out = Path.of(a.length > 1 ? a[1] : "/tmp/mounts");
        Files.createDirectories(out);
        String json = Files.readString(root.resolve("common/src/main/resources/assets/concertstage/lightshow/truss_tubes.json"));
        Map<String, List<float[]>> all = new TreeMap<>();
        var m = java.util.regex.Pattern.compile("\"([a-z0-9_]+)\":\\[(.*?)\\]\\]?(?=,\"|\\}$)").matcher(json);
        for (var e : json.substring(1, json.length() - 1).split("(?<=\\]\\]),|(?<=\\[\\]),")) {
            int k = e.indexOf("\":");
            String name = e.substring(1, k);
            List<float[]> tubes = new ArrayList<>();
            var tm = java.util.regex.Pattern.compile("\\[([-0-9.,]+)\\]").matcher(e.substring(k + 2));
            while (tm.find()) { String[] v = tm.group(1).split(","); float[] f = new float[7]; for (int i = 0; i < 7; i++) f[i] = Float.parseFloat(v[i]); tubes.add(f); }
            all.put(name, tubes);
        }
        Map<Integer, Integer> kinds = new TreeMap<>();
        int maxTris = 0, models = 0;
        for (var e : all.entrySet()) {
            if (!e.getKey().endsWith("_lit_r0") && !e.getKey().endsWith("_lit_r1")) continue;
            models++;
            // fixture under cell 0 of the truss (its cell is one block lower: tube y + 1)
            List<Tube> tubes = new ArrayList<>();
            for (float[] t : e.getValue()) tubes.add(new Tube((int) t[0], t[1] + 1, t[2] + 1, t[3], t[4], t[5], t[6]));
            MountGeometry.Layout l = MountGeometry.plan(tubes, Float.NaN, 1.95f);
            int tris = 0; for (float[] t : l.tris()) tris += t.length / 9;
            maxTris = Math.max(maxTris, tris);
            kinds.merge(l.kind(), 1, Integer::sum);
            String n = e.getKey().replace("_lit_r0", "").replace("_lit_r1", " (turned)");
            System.out.printf(Locale.ROOT, "%-34s %-5s top %.3f offset %+.2f/%+.2f  %4d triangles%n", n, new String[] {"none", "BAR", "TUBE", "PLATE"}[l.kind()], l.top(), l.ox(), l.oz(), tris);
            boolean vertical = n.startsWith("truss_v2") || n.startsWith("truss_ladder_v2");
            if (!vertical) check(l.kind() == MountGeometry.BAR || l.kind() == MountGeometry.TUBE, n + ": hung on its tubes");
            float lowestTube = Float.MAX_VALUE;
            for (Tube t : tubes) if (t.aMin() <= 0.4f && t.aMax() >= 0.6f) lowestTube = Math.min(lowestTube, t.yMin());
            if (l.kind() != MountGeometry.NONE) check(l.top() < lowestTube && l.top() > lowestTube - 0.35f, n + ": fixture just under the truss (" + l.top() + " / " + lowestTube + ")");
            check(tris < 1200, n + ": light hardware (" + tris + " triangles)");
            if (e.getKey().endsWith("_lit_r0")) writeObj(out.resolve(e.getKey() + "_mount.obj"), l);
        }
        // every cell under every truss, the four facings (the shift FixtureMount applies: tube + (part-0 cell - fixture cell))
        int cells = 0, hung = 0, bars = 0, centred = 0;
        for (var e : all.entrySet()) {
            String n = e.getKey();
            if (n.startsWith("truss_v2") || n.startsWith("truss_ladder_v2")) continue;
            float x0 = 1e9f, x1 = -1e9f, z0 = 1e9f, z1 = -1e9f, yLow = 1e9f;
            for (float[] t : e.getValue()) yLow = Math.min(yLow, t[1]);
            for (float[] t : e.getValue()) {
                if (t[1] > yLow + 0.06f) continue;
                float ax0 = t[5], ax1 = t[6], c0 = t[3], c1 = t[4];
                if (t[0] == 0) { x0 = Math.min(x0, ax0); x1 = Math.max(x1, ax1); z0 = Math.min(z0, c0); z1 = Math.max(z1, c1); }
                else { z0 = Math.min(z0, ax0); z1 = Math.max(z1, ax1); x0 = Math.min(x0, c0); x1 = Math.max(x1, c1); }
            }
            for (int cx = (int) Math.floor(x0 + 0.01f); cx < Math.ceil(x1 - 0.01f); cx++)
                for (int cz = (int) Math.floor(z0 + 0.01f); cz < Math.ceil(z1 - 0.01f); cz++) {
                    List<Tube> tubes = new ArrayList<>();
                    for (float[] t : e.getValue()) {
                        float dc = t[0] == 0 ? -cz : -cx, da = t[0] == 0 ? -cx : -cz;
                        tubes.add(new Tube((int) t[0], t[1] + 1, t[2] + 1, t[3] + dc, t[4] + dc, t[5] + da, t[6] + da));
                    }
                    // a cell with a bottom tube passing over its centre must get hardware
                    boolean over = false;
                    for (Tube t : tubes) if (t.yMin() < yLow + 1.07f && t.aMin() <= 0.4f && t.aMax() >= 0.6f && t.c() >= -0.3f && t.c() <= 1.3f) over = true;
                    if (!over) continue;
                    cells++;
                    MountGeometry.Layout l = MountGeometry.plan(tubes, Float.NaN, 1.95f);
                    if (l.kind() != MountGeometry.NONE) hung++;
                    if (l.kind() == MountGeometry.BAR) bars++;
                    if (Math.abs(l.ox()) < 0.01f && Math.abs(l.oz()) < 0.01f) centred++;
                    check(l.kind() != MountGeometry.NONE, n + " cell " + cx + "," + cz + ": hung");
                }
        }
        System.out.println(cells + " cells under the trusses (4 facings): " + hung + " hung (" + bars + " on a bar across two chords, "
            + centred + " centred in their cell)");

        // the plate when there is no tube; nothing when there is nothing
        check(MountGeometry.plan(List.of(), 1.0f, 1.95f).kind() == MountGeometry.PLATE, "plate under a plain block");
        check(MountGeometry.plan(List.of(), Float.NaN, 1.95f).kind() == MountGeometry.NONE, "nothing above: no hardware");
        System.out.println(models + " truss models (2 facings each), kinds " + kinds + ", heaviest hardware " + maxTris + " triangles");
        System.out.println(fail == 0 ? "MountTest PASS" : "MountTest FAIL " + fail);
        if (fail > 0) System.exit(1);
    }

    static void writeObj(Path p, MountGeometry.Layout l) throws Exception {
        StringBuilder s = new StringBuilder("# top " + l.top() + " offset " + l.ox() + " " + l.oz() + "\n");
        int v = 0;
        for (int c = 0; c < l.tris().size(); c++) {
            s.append("usemtl c").append(Integer.toHexString(l.colors().get(c))).append('\n');
            float[] t = l.tris().get(c);
            for (int i = 0; i + 8 < t.length; i += 9) {
                for (int k = 0; k < 3; k++) s.append(String.format(Locale.ROOT, "v %.4f %.4f %.4f%n", t[i + 3 * k], t[i + 3 * k + 1], t[i + 3 * k + 2]));
                s.append("f ").append(v + 1).append(' ').append(v + 2).append(' ').append(v + 3).append('\n');
                v += 3;
            }
        }
        Files.writeString(p, s);
    }
}
