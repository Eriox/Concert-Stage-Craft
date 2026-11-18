import com.concertstage.light.core.*;
import java.nio.file.*;
import java.util.*;

/**
 * CSC shader harness: the light parameters of a fixture state, computed as FixtureRenderer#beam does (same formulas, same BeamOptics
 * and OpticalProfile calls), for the scene pictures of scene.py. One case per input line:
 *   id zoom focus gobo(0..8) prism(0/1) dimmer iris haze flash(0/1) [zones: none | split | rainbow | rings]
 * Output, one line per case: key=value pairs (floats), opt = the 32 floats of the common optical mask.
 * Keep it in step with FixtureRenderer#beam (strengths of the beam in the haze and of the projection).
 */
public class DumpOptics {
    public static void main(String[] a) throws Exception {
        Map<String, float[][]> cells = new HashMap<>();
        Map<String, float[]> meta = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("tools/lightshow_tests/optics_cells.txt"))) {
            String[] t = line.split(";");
            String[] cs = t[3].split(" ");
            float[][] c = new float[cs.length][5];
            for (int i = 0; i < cs.length; i++) { String[] v = cs[i].split(","); for (int j = 0; j < 5; j++) c[i][j] = Float.parseFloat(v[j]); }
            cells.put(t[0], c);
            meta.put(t[0], new float[] {Float.parseFloat(t[1]), Float.parseFloat(t[2])});
        }
        Scanner in = new Scanner(System.in);
        while (in.hasNextLine()) {
            String line = in.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            FixtureProfile pr = FixtureProfile.get(t[0]);
            float zoom = Float.parseFloat(t[1]), focus = Float.parseFloat(t[2]);
            int slot = Integer.parseInt(t[3]);
            boolean prism = t[4].equals("1");
            float dimmer = Float.parseFloat(t[5]), iris = Float.parseFloat(t[6]), haze = Float.parseFloat(t[7]);
            boolean flash = t[8].equals("1");
            boolean spotType = pr == FixtureProfile.VORTEX_X1, strobeType = pr == FixtureProfile.PULSESTRIKE_M;
            float angle = pr.beamAngle(zoom);
            if (pr.has(Attr.IRIS)) angle *= 0.25f + 0.75f * iris;
            float tanHalf = (float) Math.tan(Math.toRadians(Math.max(0.6f, angle) / 2));
            float r0 = meta.get(t[0])[1];
            float intensity = dimmer;
            if (pr.has(Attr.IRIS)) { r0 *= BeamOptics.irisExit(iris); intensity *= BeamOptics.irisLight(iris); }
            boolean gobo = slot > 0 && pr.has(Attr.GOBO);
            float sharpO = pr.has(Attr.FOCUS) ? BeamOptics.focusSharpness(focus) : 1f;
            OpticalProfile op = OpticalProfile.of(pr, cells.get(t[0]), meta.get(t[0])[0]);
            float[] u = new float[OpticalProfile.UNIFORM_FLOATS];
            op.uniforms(gobo, prism, 0.4f, angle, sharpO, u, 0);
            float tanO = prism ? tanHalf * OpticalProfile.PRISM_WIDEN + 0.02f : tanHalf;
            float flux = intensity * pr.output;
            float air = flux * haze * BeamOptics.AIR_DENSITY;
            // rev. 17: the calibration of FixtureRenderer#beam (VORTEX_AIR, WASH_AIR, STAGE_GAIN; -Dcsc.va / csc.wa / csc.sg to try
            // others); -Dcsc.calib=16 gives the rev. 16d one (before / after pictures). The wash factors here were still those of rev. 16b
            // (x1.9), dropped by rev. 16d.
            boolean rev16 = "16".equals(System.getProperty("csc.calib"));
            float vortexAir = rev16 ? 0.6f : Float.parseFloat(System.getProperty("csc.va","0.8")), washAir = rev16 ? 1f : Float.parseFloat(System.getProperty("csc.wa","1.7")), stageGain = rev16 ? 1f : Float.parseFloat(System.getProperty("csc.sg","1.45"));
            float kv;
            if (spotType) kv = vortexAir * air * (gobo ? 0.9f * (0.6f + 0.4f * sharpO) : 0.65f);
            else if (strobeType) kv = (flash ? 0.32f : 0.1f) * BeamOptics.washAirScale(tanHalf) * air;
            else kv = (pr == FixtureProfile.LUMORA_W19 ? 0.85f : 0.75f) * washAir * BeamOptics.washAirScale(tanHalf) * air;
            // zone colours (as Engine.Output.zones: r g b level)
            String pat = t.length > 9 ? t[9] : "none";
            float[] zones = new float[pr.zones * 4];
            float[][] cz = cells.get(t[0]);
            for (int z = 0; z < pr.zones; z++) {
                float[] c = {1, 1, 1};
                if (pat.equals("split")) c = cz[z][0] < 0 ? new float[] {1, 0, 0} : new float[] {0, 0.2f, 1};
                else if (pat.equals("rainbow")) { int col = op.groupOf[z]; float h = col / (float) Math.max(1, op.groups); c = hue(h); }
                else if (pat.equals("rings")) c = op.groupOf[z] == 0 ? new float[] {1, 0.1f, 0} : op.groupOf[z] == 1 ? new float[] {1, 0.8f, 0} : new float[] {0.1f, 0.3f, 1};
                if (pr.zoneKind(z) == 1) c = new float[] {1, 1, 1};
                zones[z * 4] = c[0]; zones[z * 4 + 1] = c[1]; zones[z * 4 + 2] = c[2]; zones[z * 4 + 3] = 1f;
            }
            float[] tint = new float[OpticalProfile.TINT_FLOATS];
            boolean tinted = !pat.equals("none") && op.tints(zones, tint, 0);
            float rV = spotType ? Math.max(r0, 0.09f) : r0;
            float vrange = strobeType ? (flash ? 9f : 5f) : pr.throwBlocks;
            float sharpP = pr.has(Attr.FOCUS) && (spotType || strobeType) ? sharpO : 1f;
            StringBuilder s = new StringBuilder();
            s.append(String.format(Locale.ROOT, "angle=%f tanO=%f r0=%f rV=%f kv=%f flux=%f absorb=%f vspread=%f pspread=%f throw=%f strobe=%d",
                angle, tanO, r0, rV, kv, flux * stageGain * (strobeType && flash ? 1.5f : 1f), BeamOptics.ABSORPTION * (0.6f + 0.4f * haze),
                spotType ? (1f - sharpO) * 0.09f : 0f, spotType ? (1f - sharpP) * 0.09f : 0f, pr.throwBlocks, strobeType ? 1 : 0));
            s.append(String.format(Locale.ROOT, " vrange=%f tinted=%d", vrange, tinted ? 1 : 0));
            s.append(" tint=");
            for (int i = 0; i < tint.length; i++) s.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%f", tint[i]));
            s.append(" opt=");
            for (int i = 0; i < u.length; i++) s.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%f", u[i]));
            System.out.println(s);
        }
    }

    static float[] hue(float h) {
        float r = Math.abs(h * 6 - 3) - 1, g = 2 - Math.abs(h * 6 - 2), b = 2 - Math.abs(h * 6 - 4);
        return new float[] {Math.max(0, Math.min(1, r)), Math.max(0, Math.min(1, g)), Math.max(0, Math.min(1, b))};
    }
}
