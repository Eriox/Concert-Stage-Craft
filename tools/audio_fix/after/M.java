import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerModels;
import com.concertstage.audio.core.*;
import java.util.*;

/** The model AFTER the audio fix: SoundSystems (classification) + ListenerMix (this listener's mix), default settings. */
public final class M {
    public static String name() { return "after"; }
    public static int slots() { return ListenerMix.SLOTS; }
    public static ConsoleMixer mixer() { return new ConsoleMixer(Rig.RATE, ListenerMix.SLOTS, ListenerMix.sides(), ListenerMix.subs()); }

    public static final ListenerMix.Settings SET = new ListenerMix.Settings(true, true, 1, null, 1, 1, 1, 1, 1, true, true, true);
    static final Map<String, Double> RANGE = Map.of("veyra", 1.0, "noryx", .85, "orvane", 1.0, "subs", 1.0, "front_fill", .6, "delay", 1.0, "monitors", .22, "aurex_main", 1.0);

    public static final class State implements ListenerMix.ArrayInfo {
        public final List<SoundSystems.Source> sources = new ArrayList<>();
        public final List<Rig.Arr> arrays;
        public SoundSystems.Layout layout;
        public final ListenerMix.Result result = new ListenerMix.Result();
        public final ListenerMix.Desk desk = new ListenerMix.Desk(1, filled(AudioGroups.COUNT, 1f), new boolean[AudioGroups.COUNT], new float[AudioGroups.COUNT]);
        public final float[] applied = new float[ListenerMix.SLOTS];
        State(List<Rig.Arr> arrays) { this.arrays = arrays; }
        public double rangeFactor(int i, SoundSystems.Source s, SoundSystems.Installation inst) {
            Rig.Arr a = arrays.get(i); SpeakerModels.Model m = a.m();
            String key = m.isSub() ? "subs" : m.isMonitor() ? "monitors" : inst != null && inst.role() == SoundSystems.Role.TOWER ? "delay"
                : a.group() == AudioGroups.FRONT_FILL ? "front_fill" : a.group() == AudioGroups.DELAY ? "delay" : m.family();
            return RANGE.getOrDefault(key, 1.0);
        }
        public double volume(int i) { return 1; }
    }

    static float[] filled(int n, float v) { float[] f = new float[n]; Arrays.fill(f, v); return f; }

    public static SoundSystems.Source source(int i, Rig.Arr a) {
        return new SoundSystems.Source(i, a.group(), a.m(), a.cabinets(), a.length(), a.x(), a.y(), a.z(), a.yaw(), 0, 0);
    }

    public static State prepare(List<Rig.Arr> scene) { return prepare(scene, SoundSystems.Mode.AUTO); }

    public static State prepare(List<Rig.Arr> scene, SoundSystems.Mode mode) {
        State s = new State(scene);
        for (int i = 0; i < scene.size(); i++) s.sources.add(source(i, scene.get(i)));
        s.layout = SoundSystems.classify(s.sources, mode, 10);
        return s;
    }

    public static String classify(List<Rig.Arr> scene) {
        State s = prepare(scene);
        StringBuilder b = new StringBuilder();
        for (SoundSystems.Installation i : s.layout.installations())
            b.append(String.format(Locale.ROOT, "%s(%d arrays, z %.0f, %.1f ms: %s) ", i.role(), i.arrays(), i.z(), i.delayMs(), i.reason()));
        return b.toString();
    }

    public static ListenerMix.Result compute(Object state, double ex, double ey, double ez, double heading) {
        State s = (State) state;
        return ListenerMix.compute(s.sources, s.layout, s.desk, ex, ey, ez, heading, SET, s, ListenerMix.NO_WALLS, s.result);
    }

    public static float[] weights(Object state, List<Rig.Arr> scene, double ex, double ey, double ez, double heading) {
        return compute(state, ex, ey, ez, heading).weights.clone();
    }

    /** The delays of the last {@link #weights} call (the game computes both in one pass; callers ask for the same place). */
    public static float[] delays(Object state, List<Rig.Arr> scene, double ex, double ey, double ez) {
        State s = (State) state;
        ListenerMix.settle(s.result.delays, s.applied, 2f);
        return s.applied.clone();
    }

    public static void configure(ConsoleMixer m, Object state) { m.crossover(crossover(state)); }
    public static float crossover(Object state) { return ((State) state).result.crossoverHz; }

    /** Level of the arrays whose name starts with {@code prefix} alone at the listener (the same ListenerMix, only them). */
    public static double partLevel(Object state, List<Rig.Arr> scene, String prefix, double ex, double ey, double ez) {
        State s = (State) state;
        List<Rig.Arr> only = new ArrayList<>();
        List<SoundSystems.Installation> inst = new ArrayList<>();
        for (int i = 0; i < scene.size(); i++) if (scene.get(i).name().startsWith(prefix)) only.add(scene.get(i));
        if (only.isEmpty()) return 0;
        State p = prepare(only, SoundSystems.Mode.OFF);
        ListenerMix.Result r = ListenerMix.compute(p.sources, p.layout, p.desk, ex, ey, ez, Math.PI, SET, p, ListenerMix.NO_WALLS, null);
        double e = 0;
        for (float v : r.weights) e += v * v;
        return Math.sqrt(e);
    }
}
