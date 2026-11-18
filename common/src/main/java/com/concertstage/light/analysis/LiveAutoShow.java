package com.concertstage.light.analysis;

import com.concertstage.light.core.Movement;
import com.concertstage.light.core.Show;
import java.util.List;

/**
 * CSC AUTOshow Engine, live mode: the music is analysed while it plays (only what has already been heard, no look-ahead), and the
 * desk switches between prepared looks — one per musical state — of a cue list "AutoShow Live". Stabilisation, so the light never
 * jumps around: a state must hold for 1.5 s before it is taken (a drop is taken at once), a look stays at least 4 s, changes wait
 * for the next downbeat when the tempo is known, and the tempo itself is re-estimated only every 10 s on the last 20 s.
 */
public final class LiveAutoShow {
    public static final String LIST_NAME = "AutoShow Live";
    /** Order of the cues of the live list (one look per state). */
    public static final int[] STATES = {Analysis.INTRO, Analysis.VERSE, Analysis.BUILD, Analysis.CHORUS, Analysis.DROP, Analysis.BREAK, Analysis.SILENCE};

    private int state = -1, candidate = -1;
    private long candidateSince, stateSince = Long.MIN_VALUE / 2, lastTempoMs = Long.MIN_VALUE / 2;
    private float bpm;
    private long firstBeatMs;
    private float runningMax = 0.05f;

    /** Writes (or rewrites) the live cue list: one look per state, in the order of {@link #STATES}. Returns its id. */
    public static int prepare(Show show, Movement.Stage stage) {
        Show.CueList list = null;
        for (Show.CueList l : show.lists) if (LIST_NAME.equals(l.name)) list = l;
        if (list == null) {
            list = new Show.CueList();
            list.id = show.nextListId();
            list.name = LIST_NAME;
            show.lists.add(list);
        }
        list.priority = 15;
        list.cues.clear();
        int[] beams = AutoShow.byRole(show, 0);
        int[] washes = AutoShow.byRole(show, 1);
        int[] strobes = AutoShow.byRole(show, 2);
        List<Show.Preset> pos = Movement.standardPositions(show, stage, 1);
        for (int i = 0; i < STATES.length; i++) {
            Analysis.Section s = new Analysis.Section();
            s.kind = STATES[i];
            s.motif = STATES[i] == Analysis.CHORUS || STATES[i] == Analysis.DROP ? 1 : 0;
            s.energy = switch (STATES[i]) { case Analysis.DROP -> 1f; case Analysis.CHORUS -> 0.85f; case Analysis.BUILD -> 0.7f; case Analysis.VERSE -> 0.6f; default -> 0.4f; };
            Palettes.Palette pal = Palettes.choose(show.auto, s.motif, s.kind, 0, null);
            Show.Cue c = AutoShow.look(show, show.auto, s, pal, pos, beams, washes, strobes, 0.6f, false);
            c.number = i + 1;
            c.name = Analysis.KINDS[STATES[i]];
            c.fadeIn = c.fadeOut = STATES[i] == Analysis.DROP ? 0.05f : STATES[i] == Analysis.BREAK ? 4f : 1.5f;
            list.cues.add(c);
        }
        return list.id;
    }

    public void reset() {
        state = -1;
        candidate = -1;
        stateSince = Long.MIN_VALUE / 2;
        lastTempoMs = Long.MIN_VALUE / 2;
        runningMax = 0.05f;
    }

    public float bpm() {
        return bpm;
    }

    /**
     * Decides the state at playhead {@code ms} from the features heard so far. Returns the index in {@link #STATES} to switch to, or -1
     * to stay.
     */
    public int update(Analyzer.Features f, long ms) {
        int n = f.frames();
        if (n < 20) return -1;
        float hop = Analyzer.HOP_MS;
        int end = Math.min(n, (int) (ms / hop));
        if (end < 20) return -1;
        // tempo on the last 20 s, every 10 s
        if (ms - lastTempoMs > 10000 && end > (int) (8000 / hop)) {
            lastTempoMs = ms;
            int from = Math.max(0, end - (int) (20000 / hop));
            float[] flux = java.util.Arrays.copyOfRange(f.flux.a, from, end);
            Analysis t = new Analysis();
            t.hopMs = hop;
            float[] low = Analyzer.normalise(java.util.Arrays.copyOfRange(f.low.a, from, end));
            t.low = low;
            float[] env = Analyzer.onsetEnvelope(flux);
            t.onset = Analyzer.normalise(env.clone());
            Analyzer.tempo(t, env);
            if (t.bpm > 0 && t.tempoConfidence > 0.15f) { bpm = t.bpm; firstBeatMs = Math.round(from * hop) + t.firstBeatMs; }
        }
        float shortE = mean(f.rms.a, end - (int) (2000 / hop), end);
        float longE = mean(f.rms.a, end - (int) (16000 / hop), end);
        float prevE = mean(f.rms.a, end - (int) (4000 / hop), end - (int) (2000 / hop));
        float lowShort = mean(f.low.a, end - (int) (2000 / hop), end);
        float highNow = mean(f.high.a, end - (int) (2000 / hop), end), highBefore = mean(f.high.a, end - (int) (8000 / hop), end - (int) (6000 / hop));
        // the reference level: the loudest 2 s heard so far, but never below the long-term level (no false drop in the first seconds)
        runningMax = Math.max(Math.max(runningMax * 0.99995f, shortE), longE * 1.25f);
        float rel = shortE / runningMax, relLong = longE / runningMax;
        int want;
        if (rel < 0.06f) want = Analysis.SILENCE;
        else if (rel > 0.78f && shortE > prevE * 1.3f && lowShort / Math.max(1e-6f, runningMax) > 0.5f) want = Analysis.DROP;
        else if (state == indexOf(Analysis.DROP) && rel > 0.7f) want = Analysis.DROP;
        else if (shortE > longE * 1.12f && highNow > highBefore * 1.15f && rel < 0.85f) want = Analysis.BUILD;
        else if (rel < relLong * 0.62f) want = Analysis.BREAK;
        else if (rel > 0.7f) want = Analysis.CHORUS;
        else if (ms < 15000 && rel < 0.5f) want = Analysis.INTRO;
        else want = Analysis.VERSE;
        int idx = indexOf(want);
        if (idx == state) { candidate = -1; return -1; }
        if (idx != candidate) { candidate = idx; candidateSince = ms; }
        boolean immediate = (want == Analysis.DROP || want == Analysis.SILENCE) && ms - stateSince >= 2000;
        if (ms < 6000 && want != Analysis.SILENCE) return -1;                // warm-up: the levels are not known yet
        if (!immediate && ms - candidateSince < 1500) return -1;
        if (!immediate && ms - stateSince < 4000) return -1;
        // wait for the downbeat (within the beat after it) when the tempo is known
        if (!immediate && bpm > 0) {
            double beatMs = 60000.0 / bpm;
            double barPos = ((ms - firstBeatMs) / beatMs) % 4;
            if (barPos > 0.5) return -1;
        }
        state = idx;
        stateSince = ms;
        candidate = -1;
        return idx;
    }

    private static int indexOf(int kind) {
        for (int i = 0; i < STATES.length; i++) if (STATES[i] == kind) return i;
        return 1;
    }

    private static float mean(float[] a, int from, int to) {
        from = Math.max(0, from);
        to = Math.min(a.length, to);
        if (to <= from) return 0f;
        double s = 0;
        for (int i = from; i < to; i++) s += a[i];
        return (float) (s / (to - from));
    }
}
