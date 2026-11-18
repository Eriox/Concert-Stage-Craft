import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerModels;
import com.concertstage.audio.core.*;
import java.util.*;

/** The 1.1.1 model BEFORE the audio fix, exactly as ClientAudio#arrayGain + ArrayMix computed it with the default settings. */
public final class M {
    public static String name() { return "before"; }
    public static int slots() { return AudioGroups.COUNT; }
    public static ConsoleMixer mixer() { return new ConsoleMixer(Rig.RATE, AudioGroups.COUNT); }
    public static Object prepare(List<Rig.Arr> scene) { return null; }
    public static String classify(List<Rig.Arr> scene) { return "no detection (every array plays as its console group)"; }

    static final Map<String, Double> RANGE = Map.of("veyra", 1.0, "noryx", .85, "orvane", 1.0, "subs", 1.0, "front_fill", .6, "delay", 1.0, "monitors", .22, "aurex_main", 1.0);

    public static double level(Rig.Arr a, double ex, double ey, double ez) {
        SpeakerModels.Model m = a.m();
        double dx = ex - a.x(), dy = ey - a.y(), dz = ez - a.z(), dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double range = Acoustics.range(m.isSub(), a.length());
        String key = m.isSub() ? "subs" : m.isMonitor() ? "monitors" : a.group() == AudioGroups.FRONT_FILL ? "front_fill" : a.group() == AudioGroups.DELAY ? "delay" : m.family();
        range *= RANGE.getOrDefault(key, 1.0);
        double g = new DistanceCurve(12, 192 * range / 192, .65, 1).gain(dist);
        if (g <= 0) return 0;
        double yr = Math.toRadians(a.yaw()), fx = -Math.sin(yr), fz = Math.cos(yr);
        double hl = Math.sqrt(dx * dx + dz * dz);
        double angleH = hl < 1e-6 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / hl))));
        double elevation = Math.toDegrees(Math.atan2(-dy, Math.max(.5, hl)));
        return g * Acoustics.directivity(m.isSub(), angleH, elevation, m.hDispersion(), m.vDispersion(), 0, 0, m.rear());
    }

    static final ArrayList<ArrayMix.Contribution> C = new ArrayList<>();

    public static float[] weights(Object state, List<Rig.Arr> scene, double ex, double ey, double ez, double heading) {
        C.clear();
        for (Rig.Arr a : scene) {
            double l = level(a, ex, ey, ez);
            if (!(l > 0)) continue;
            double dx = a.x() - ex, dz = a.z() - ez;
            double pan = (-Math.cos(heading) * dx - Math.sin(heading) * dz) / Math.sqrt(dx * dx + dz * dz + 1);
            C.add(new ArrayMix.Contribution(a.group(), a.model(), a.x(), a.y(), a.z(), l, pan));
        }
        return ArrayMix.weights(C, AudioGroups.COUNT, 1);
    }

    /** Before: the console's group delays only (0 by default: a tower plays with no delay unless set by hand). */
    public static float[] delays(Object state, List<Rig.Arr> scene, double ex, double ey, double ez) { return new float[AudioGroups.COUNT]; }
    public static void configure(ConsoleMixer m, Object state) { }
    public static float crossover(Object state) { return Float.NaN; }
    /** Level of the arrays whose name starts with {@code prefix} at the listener (energy sum of their levels), for the transition. */
    public static double partLevel(Object state, List<Rig.Arr> scene, String prefix, double ex, double ey, double ez) {
        double e = 0;
        for (Rig.Arr a : scene) if (a.name().startsWith(prefix)) { double l = level(a, ex, ey, ez); e += l * l; }
        return Math.sqrt(e);
    }
}
