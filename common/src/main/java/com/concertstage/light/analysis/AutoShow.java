package com.concertstage.light.analysis;

import com.concertstage.light.core.Attr;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Look;
import com.concertstage.light.core.Movement;
import com.concertstage.light.core.Show;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSC AUTOshow Engine, pre-analysis mode: from the analysis of a track it writes a normal, editable show — a cue list "AutoShow" with
 * one look per musical section (and steps inside builds), the timeline events that fire them on the music, the section markers, and
 * accents on the downbeats of the strongest sections. The look of a section follows its role and energy, the style, and its motif:
 * the same chorus gets the same palette and the same motif each time, with small variations (beams / washes swapped, effect
 * direction mirrored). Big effects are kept for drops and choruses; strobes stay within the configured rate (and never in Ambient).
 *
 * <p>Fixtures are sorted by role from their profile: VORTEX X1 = beams, LUMORA W7 / W19 = washes, PULSESTRIKE M = strobes / blinders.
 */
public final class AutoShow {
    private AutoShow() {}

    public static final String LIST_NAME = "AutoShow";

    /** Rev. 24: tempo of the track being written (the looks choose their effect rates from it); 0 = unknown (live mode). */
    static final ThreadLocal<Float> TEMPO = ThreadLocal.withInitial(() -> 0f);

    /** Position palettes used by the looks (built from the rig geometry, see {@link Movement#standardPositions}). */
    private static final int POS_CENTRE = 0, POS_AUDIENCE = 1, POS_UPSTAGE = 2, POS_CROSS = 3, POS_VERTICAL = 4, POS_FAN = 5, POS_REST = 6;

    /** Result for the report shown on the desk. */
    public record Report(int cues, int events, int accents, int sections, float bpm) {}

    static int[] byRole(Show show, int role) {
        return show.patch.stream().filter(p -> p.profile().autoRole() == role).mapToInt(p -> p.fid).toArray();
    }

    public static Report generate(Show show, Analysis a, Movement.Stage stage) {
        TEMPO.set(a.bpm > 0 ? a.bpm : 0f);
        try {
            return write(show, a, stage);
        } finally {
            TEMPO.set(0f);
        }
    }

    private static Report write(Show show, Analysis a, Movement.Stage stage) {
        Show.AutoSettings set = show.auto;
        // ---- groups by role (created if missing, kept if the player made them)
        int[] beams = byRole(show, 0);
        int[] washes = byRole(show, 1);
        int[] strobes = byRole(show, 2);
        ensureGroup(show, "Beams", beams, 0xFF35C9E8);
        ensureGroup(show, "Washes", washes, 0xFFB07CFF);
        // rev. 24: the blinders get their own group: they hit the big moments instead of glowing and chasing with the strobes
        int[] blinders = java.util.Arrays.stream(strobes).filter(f -> blinder(show, f)).toArray();
        ensureGroup(show, "Strobes", java.util.Arrays.stream(strobes).filter(f -> !blinder(show, f)).toArray(), 0xFFF2B33D);
        int strobeGroup = groupId(show, "Strobes");
        ensureGroup(show, "Blinders", blinders, 0xFFFFC27A);
        int blinderGroup = groupId(show, "Blinders");
        // ---- positions from the geometry of the rig
        List<Show.Preset> pos = Movement.standardPositions(show, stage, 1);
        // ---- the cue list
        Show.CueList list = null;
        for (Show.CueList l : show.lists) if (LIST_NAME.equals(l.name)) list = l;
        if (list == null) {
            list = new Show.CueList();
            list.id = show.nextListId();
            list.name = LIST_NAME;
            show.lists.add(list);
        }
        list.priority = 20;
        list.cues.clear();
        final int listId = list.id;
        show.timeline.events.removeIf(e -> e.list == listId || e.kind == Show.Event.ACCENT);
        show.timeline.markers.clear();
        show.timeline.sections.clear();
        show.timeline.bpm = a.bpm;
        show.timeline.beatOffsetMs = a.firstBeatMs;
        show.timeline.beatsPerBar = a.beatsPerBar;
        show.timeline.trackSha1 = a.sha1;

        float number = 1;
        int accents = 0;
        List<Integer> used = new ArrayList<>();
        Map<Integer, Palettes.Palette> motifPalette = new LinkedHashMap<>();
        double beatMs = a.beatMs();
        for (int i = 0; i < a.sections.size(); i++) {
            Analysis.Section s = a.sections.get(i);
            Analysis.Section next = i + 1 < a.sections.size() ? a.sections.get(i + 1) : null;
            Analysis.Section prev = i > 0 ? a.sections.get(i - 1) : null;
            // timeline section and marker
            Show.Section ts = new Show.Section();
            ts.startMs = s.startMs; ts.endMs = s.endMs; ts.kind = s.kind; ts.label = s.label(); ts.energy = s.energy;
            ts.confidence = s.confidence; ts.motif = s.motif;
            show.timeline.sections.add(ts);
            Show.Marker mk = new Show.Marker();
            mk.ms = s.startMs; mk.label = s.label();
            mk.color = Palettes.hot(s.kind) ? 0xFFE5484D : s.kind == Analysis.BUILD ? 0xFFF2B33D : 0xFF35C9E8;
            show.timeline.markers.add(mk);
            // palette: per motif, so a repetition finds its colours again
            Palettes.Palette pal = motifPalette.get(s.motif * 2 + (Palettes.hot(s.kind) ? 1 : 0));
            if (pal == null) {
                pal = Palettes.choose(set, s.motif, s.kind, 0, used);
                motifPalette.put(s.motif * 2 + (Palettes.hot(s.kind) ? 1 : 0), pal);
            }
            if (s.occurrence % 2 == 1 && set.colorVariety > 0.3f) pal = pal.swapped();
            boolean mirror = s.occurrence % 2 == 1;
            double barMs = beatMs > 0 ? beatMs * Math.max(2, a.beatsPerBar) : 2000;

            if (s.kind == Analysis.BUILD) {
                // a build rises in steps: 0 %, 50 %, 75 % of its length, brighter, more open, faster
                float[] steps = {0f, 0.5f, 0.75f};
                for (int k = 0; k < steps.length; k++) {
                    float prog = k / (float) (steps.length - 1);
                    Show.Cue c = look(show, set, s, pal, pos, beams, washes, strobes, prog, mirror, s.motif + k);
                    c.number = number++;
                    c.name = s.label() + (k == 0 ? "" : " +" + k);
                    c.fadeIn = c.fadeOut = (float) Math.min(4.0, Math.max(0.6, beatMs * (k == 0 ? 4 : 2) / 1000.0));
                    list.cues.add(c);
                    go(show, listId, c.number, snap(a, s.startMs + (long) ((s.endMs - s.startMs) * steps[k])));
                }
                // the cut just before a drop: one beat of near darkness
                if (next != null && next.kind == Analysis.DROP && next.confidence >= 0.5f && beatMs > 0) {
                    Show.Cue cut = new Show.Cue();
                    cut.number = number++;
                    cut.name = "Cut before drop";
                    cut.fadeIn = cut.fadeOut = 0.12f;
                    for (Show.Patch p : show.patch) cut.looks.put(p.fid, new Look().set(Attr.DIMMER, 0.02f));
                    list.cues.add(cut);
                    go(show, listId, cut.number, Math.max(s.startMs, next.startMs - Math.round(beatMs)));
                }
                continue;
            }
            // rev. 2: fades follow the bar, so a change breathes with the music; long sections change their effects every 8 bars
            // (a phrase) with the same colours and positions, so the picture evolves without losing its identity
            float fade = switch (s.kind) {
                // a drop after a cut (heads already turned in the dark) hits at once; any other drop glides in over 0.8 s
                case Analysis.DROP -> prev != null && prev.kind == Analysis.BUILD && s.confidence >= 0.5f ? 0.15f : 0.8f;
                case Analysis.CHORUS -> (float) Math.max(0.8, Math.min(1.5, barMs / 2000.0));
                case Analysis.SILENCE -> 2f;
                case Analysis.BREAK, Analysis.OUTRO, Analysis.INTRO -> (float) Math.min(8.0, barMs * 2 / 1000.0);
                default -> (float) Math.min(4.0, barMs / 1000.0);
            };
            if (set.style == Show.AutoSettings.AMBIENT) fade = Math.max(fade, 4f);
            float phraseFade = (float) Math.min(set.style == Show.AutoSettings.AMBIENT ? 6.0 : 3.0, Math.max(0.8, beatMs * (set.style == Show.AutoSettings.AMBIENT ? 8 : 2) / 1000.0));
            long phraseMs = beatMs > 0 ? Math.round(barMs * 8) : 16000;
            long secLen = s.endMs - s.startMs;
            int phrases = (int) Math.max(1, Math.min(8, Math.round(secLen / (double) Math.max(1, phraseMs))));
            if (secLen < phraseMs * 1.5 || s.kind == Analysis.SILENCE) phrases = 1;
            for (int ph = 0; ph < phrases; ph++) {
                Show.Cue c = look(show, set, s, pal, pos, beams, washes, strobes, 1f, mirror, s.motif + ph);
                c.number = number++;
                c.name = ph == 0 ? s.label() : s.label() + " ·" + (ph + 1);
                float f = ph == 0 ? fade : phraseFade;          // phrase changes: two beats (ambient: two bars)
                c.fadeIn = c.fadeOut = f;
                list.cues.add(c);
                go(show, listId, c.number, ph == 0 ? (prev == null ? 0 : s.startMs) : snap(a, s.startMs + ph * phraseMs));
            }
            accents += accents(show, set, a, s, strobeGroup, strobes.length > blinders.length);
            accents += blinderHits(show, set, a, s, prev, blinderGroup, blinders.length > 0);
            // rev. 24: a fill on the last bar before a chorus or a drop that no build announces: the beams chase, the strobes pulse
            if (next != null && Palettes.hot(next.kind) && s.kind != Analysis.BUILD && beatMs > 0 && !list.cues.isEmpty()
                && next.startMs - s.startMs > barMs * 2 && (set.effectMask & (1 << Show.Effect.DIMMER_CHASE)) != 0 && set.style != Show.AutoSettings.AMBIENT) {
                Show.Cue last = list.cues.get(list.cues.size() - 1), fill = new Show.Cue();
                fill.number = number++;
                fill.name = s.label() + " fill";
                fill.fadeIn = fill.fadeOut = 0.3f;
                for (Map.Entry<Integer, Look> en : last.looks.entrySet()) fill.looks.put(en.getKey(), en.getValue().copy());
                for (Show.Effect e : last.effects) fill.effects.add(e.copy());
                add(fill, set.effectMask, Show.Effect.DIMMER_CHASE, beams, 0.5f, true, 0.7f * Math.max(0.3f, set.effectAmount), 1f, 1, 0.35f);
                int[] flash = java.util.Arrays.stream(strobes).filter(f -> !blinder(show, f)).toArray();
                if (set.strobes) add(fill, set.effectMask, Show.Effect.PULSE, flash, 1f, true, 0.4f * set.effectAmount, 0f, 1, 0.3f);
                list.cues.add(fill);
                go(show, listId, fill.number, snap(a, next.startMs - Math.round(barMs)));
            }
        }
        moveInDark(show, list, listId, beatMs);
        show.timeline.sort();
        return new Report(list.cues.size(), (int) show.timeline.events.stream().filter(e -> e.list == listId).count(), accents,
            a.sections.size(), a.bpm);
    }

    /** The look of a section (or of a step of a build, {@code prog} 0..1). */
    static Show.Cue look(Show show, Show.AutoSettings set, Analysis.Section s, Palettes.Palette pal, List<Show.Preset> pos,
                         int[] beams, int[] washes, int[] strobes, float prog, boolean mirror) {
        return look(show, set, s, pal, pos, beams, washes, strobes, prog, mirror, s.motif);
    }

    /** {@code variant}: which effect combination of the section's pools (same motif + same phrase = same variant: repetitions match). */
    static Show.Cue look(Show show, Show.AutoSettings set, Analysis.Section s, Palettes.Palette pal, List<Show.Preset> pos,
                         int[] beams, int[] washes, int[] strobes, float prog, boolean mirror, int variant) {
        Show.Cue c = new Show.Cue();
        int style = set.style;
        float in = set.intensity, mv = set.movement, fx = set.effectAmount;
        int kind = s.kind;
        float energy = Math.max(0.15f, s.energy);
        float washLevel, beamLevel, strobeLevel;
        int beamPos, washPos;
        switch (kind) {
            case Analysis.SILENCE -> { washLevel = 0f; beamLevel = 0f; strobeLevel = 0f; beamPos = POS_REST; washPos = POS_CENTRE; }
            case Analysis.INTRO -> { washLevel = 0.45f; beamLevel = 0.35f; strobeLevel = 0f; beamPos = POS_UPSTAGE; washPos = POS_CENTRE; }
            case Analysis.VERSE -> { washLevel = 0.6f; beamLevel = 0.45f; strobeLevel = 0f; beamPos = POS_AUDIENCE; washPos = POS_CENTRE; }
            case Analysis.BUILD -> { washLevel = 0.5f + 0.4f * prog; beamLevel = 0.4f + 0.5f * prog; strobeLevel = 0.3f * prog; beamPos = POS_FAN; washPos = POS_CENTRE; }
            case Analysis.CHORUS -> { washLevel = 0.95f; beamLevel = 0.85f; strobeLevel = style == Show.AutoSettings.ROCK ? 0.55f : 0.45f; beamPos = POS_CROSS; washPos = POS_AUDIENCE; }
            case Analysis.DROP -> { washLevel = 1f; beamLevel = 1f; strobeLevel = 0.9f; beamPos = POS_VERTICAL; washPos = POS_AUDIENCE; }
            case Analysis.BREAK -> { washLevel = 0.35f; beamLevel = 0.2f; strobeLevel = 0f; beamPos = POS_UPSTAGE; washPos = POS_CENTRE; }
            default -> { washLevel = 0.4f; beamLevel = 0.25f; strobeLevel = 0f; beamPos = POS_REST; washPos = POS_CENTRE; }      // outro
        }
        if (style == Show.AutoSettings.ROCK) { beamPos = kind == Analysis.DROP || kind == Analysis.CHORUS ? POS_AUDIENCE : beamPos; }
        if (style == Show.AutoSettings.AMBIENT) { beamLevel *= 0.6f; strobeLevel = 0f; }
        float scale = in * (0.55f + 0.45f * energy);
        int washColor = kind == Analysis.BUILD ? Palettes.towardsWhite(pal.primary(), prog) : pal.primary();
        int beamColor = kind == Analysis.DROP && style == Show.AutoSettings.EDM ? 0xFFFFFF : pal.secondary();
        Show.Preset bp = pos.get(beamPos), wp = pos.get(washPos);
        for (int f : washes) {
            Look l = color(new Look(), washColor);
            l.set(Attr.DIMMER, clamp(washLevel * scale));
            l.set(Attr.ZOOM, kind == Analysis.DROP || kind == Analysis.CHORUS ? 0.75f : 0.45f);
            l.set(Attr.STROBE, 0f);
            l.set(Attr.WHITE, 0f);
            positions(l, wp.perFixture.get(f));
            c.looks.put(f, l);
        }
        for (int f : beams) {
            Look l = color(new Look(), beamColor);
            l.set(Attr.DIMMER, clamp(beamLevel * scale));
            boolean hot = Palettes.hot(kind);
            l.set(Attr.ZOOM, hot ? 0.02f : kind == Analysis.BUILD ? 0.15f - 0.1f * prog : 0.3f);
            l.set(Attr.IRIS, hot ? 0.25f : 0.8f);
            l.set(Attr.FOCUS, 0.5f);
            l.set(Attr.GOBO, hot || kind == Analysis.BUILD ? 0f : (s.motif % 7 + 1) / 8f);
            l.set(Attr.GOBO_ROT, hot ? 0.5f : 0.56f);
            l.set(Attr.PRISM, kind == Analysis.DROP && style != Show.AutoSettings.ROCK ? 1f : 0f);
            l.set(Attr.PRISM_ROT, mirror ? 0.42f : 0.58f);
            l.set(Attr.STROBE, 0f);
            positions(l, bp.perFixture.get(f));
            c.looks.put(f, l);
        }
        for (int f : strobes) {
            Look l = color(new Look(), kind == Analysis.DROP ? pal.accent() : pal.primary());
            l.set(Attr.DIMMER, clamp(strobeLevel * in));
            boolean buildEnd = kind == Analysis.BUILD && prog >= 0.75f;
            l.set(Attr.WHITE, kind == Analysis.CHORUS || kind == Analysis.DROP || buildEnd ? 1f : 0f);
            l.set(Attr.CTO, style == Show.AutoSettings.ROCK ? 0.7f : 0.3f);
            // rev. 4c: the tilt is centred on the straight position for every motorised fixture now: the strobes still face the crowd
            FixtureProfile tp = show.fixture(f) != null ? show.fixture(f).profile() : null;
            l.set(Attr.TILT, tp != null ? tp.tiltValue(kind == Analysis.DROP ? 19f : 0f) : (kind == Analysis.DROP ? 0.6f : 0.5f));
            // rev. 4: the end of a build speeds a white strobe up into the drop (the classic riser), within the configured rate
            float hz = !set.strobes || style == Show.AutoSettings.AMBIENT ? 0f
                : kind == Analysis.DROP ? Math.min(set.strobeMaxHz, 8f)
                : buildEnd ? Math.min(set.strobeMaxHz, 3f + 7f * Math.min(1f, (prog - 0.75f) / 0.25f)) : 0f;
            FixtureProfile pr = show.fixture(f).profile();
            l.set(Attr.STROBE, hz <= 0 ? 0f : (hz - 1f) / (pr.strobeMaxHz - 1f));
            if (pr.hasSections()) zones(l, pr, kind, buildEnd, prog, hz, pal, variant, mirror);
            if (blinder(show, f)) {
                // rev. 24: a blinder is dark, rises with the end of a build, and hits on the accents (timeline); halogen white
                l = new Look();
                l.set(Attr.DIMMER, buildEnd ? clamp(0.15f + 0.5f * (prog - 0.75f) / 0.25f) * in : 0f);
                l.set(Attr.WHITE, 1f);
                l.set(Attr.CTO, 0f);                                             // the plain halogen lamp (Engine.halogen)
                l.set(Attr.STROBE, 0f);
            }
            c.looks.put(f, l);
        }
        for (Map.Entry<Integer, Look> entry : c.looks.entrySet()) {
            FixtureProfile pr = show.fixture(entry.getKey()).profile();
            Look l = entry.getValue();
            if (pr.has(Attr.LASER_PATTERN)) {
                l.set(Attr.LASER_PATTERN, (variant % 5) / 5f);
                l.set(Attr.LASER_SIZE, Palettes.hot(kind) ? 0.9f : 0.4f);
                l.set(Attr.LASER_SPEED, Palettes.hot(kind) ? 0.75f : 0.15f);
            }
            // Unsupported attributes must never manufacture motor/optical functions on fixed devices.
            for (Attr a : Attr.ALL) if (!pr.has(a)) l.clear(a);
        }
        int[] fxStrobes = java.util.Arrays.stream(strobes).filter(f -> !blinder(show, f)).toArray();   // rev. 24: blinders only hit
        effects(c, set, s, pal, beams, washes, fxStrobes, prog, mirror, fx, mv, variant, TEMPO.get());
        return c;
    }

    /**
     * Rev. 21 — the looks of the zone fixtures (PULSESTRIKE M, FLASHCORE FX: top RGB bar, centre white strobe, bottom RGB bar). Never all
     * three zones flashing together, the shared strobe off (each zone has its own):
     * <ul>
     * <li>drop: the centre flashes white, both bars steady in the accent colour (the centre flash with fixed RGB bars);</li>
     * <li>end of a build: top and bottom bars flash in turn (same rate: they alternate), centre dark;</li>
     * <li>build: only the centre flashes, slowly then faster;</li>
     * <li>chorus: no strobe, top and bottom in two colours of the palette, centre low;</li>
     * <li>other sections: the bars in the primary colour, centre off.</li>
     * </ul>
     * The rate is the one the look already chose (within the strobe cap of the settings); with strobes off the zones stay steady.
     */
    public static void zones(Look l, FixtureProfile pr, int kind, boolean buildEnd, float prog, float hz, Palettes.Palette pal, int variant, boolean mirror) {
        float rate = hz <= 0 ? 0f : (hz - 1f) / (pr.strobeMaxHz - 1f);
        int a = pal.accent(), p = pal.primary(), s2 = pal.secondary();
        l.set(Attr.STROBE, 0f);
        float top, mid, bot, ts = 0f, ms = 0f, bs = 0f;
        int tc = p, bc = p;
        if (kind == Analysis.DROP) { top = 1f; bot = 1f; mid = 1f; ms = rate; tc = a; bc = a; }
        else if (buildEnd) { top = 1f; bot = 1f; mid = 0f; ts = rate; bs = rate; tc = mirror ? s2 : p; bc = mirror ? p : s2; }
        else if (kind == Analysis.BUILD) { top = 0f; bot = 0f; mid = 1f; ms = rate > 0 ? rate : 0f; }
        else if (kind == Analysis.CHORUS) { top = 1f; bot = 1f; mid = 0.25f; tc = variant % 2 == 0 ? p : s2; bc = variant % 2 == 0 ? s2 : a; }
        else { top = 1f; bot = 1f; mid = 0f; }
        l.set(Attr.TOP_DIM, top); l.set(Attr.MID_DIM, mid); l.set(Attr.BOT_DIM, bot);
        l.set(Attr.TOP_STROBE, ts); l.set(Attr.MID_STROBE, ms); l.set(Attr.BOT_STROBE, bs);
        l.set(Attr.TOP_RED, ((tc >> 16) & 255) / 255f); l.set(Attr.TOP_GREEN, ((tc >> 8) & 255) / 255f); l.set(Attr.TOP_BLUE, (tc & 255) / 255f);
        l.set(Attr.BOT_RED, ((bc >> 16) & 255) / 255f); l.set(Attr.BOT_GREEN, ((bc >> 8) & 255) / 255f); l.set(Attr.BOT_BLUE, (bc & 255) / 255f);
        // the bars carry the colour: the colour of the whole fixture stays white so that it does not tint them twice
        l.set(Attr.RED, 1f); l.set(Attr.GREEN, 1f); l.set(Attr.BLUE, 1f);
        l.set(Attr.WHITE, 1f);
    }

    /**
     * Effects of a look (rev. 2): several layers — beam movement, beam intensity, wash colour / pixels, wash intensity, strobes — each
     * taken from a pool that fits the section (calm, build, hot) and the style. The variant picks the combination: the same chorus gets
     * the same combination each time it comes back, the next phrase of a long section gets the next one. Effect density decides how
     * many layers run; every effect fades in (and the previous ones fade out) with the cue.
     */
    static void effects(Show.Cue c, Show.AutoSettings set, Analysis.Section s, Palettes.Palette pal, int[] beams, int[] washes, int[] strobes,
                        float prog, boolean mirror, float fx, float mv, int variant) {
        effects(c, set, s, pal, beams, washes, strobes, prog, mirror, fx, mv, variant, 0f);
    }

    /**
     * Rev. 24 — {@code bpm}: tempo of the track (0 if unknown). The rates are in beats, chosen for the tempo: a fast track (above
     * 150 BPM) moves in half time, a slow one (below 85 BPM) in double time, so a movement always lasts about the same number of
     * seconds and lands on the bar. The pools hold the effects of rev. 24 too (figure 8, tilt wave, ballyhoo, rainbow, sparkle, random
     * strobe, zoom pulse, alternate), each where it suits the music: wandering and figures in the calm parts, waves and fans that
     * open in the builds, fast circles / ballyhoo / alternations on the drops, random strobes only on drops.
     */
    static void effects(Show.Cue c, Show.AutoSettings set, Analysis.Section s, Palettes.Palette pal, int[] beams, int[] washes, int[] strobes,
                        float prog, boolean mirror, float fx, float mv, int variant, float bpm) {
        int kind = s.kind, style = set.style, mask = set.effectMask;
        if (fx <= 0.01f || kind == Analysis.SILENCE) return;
        int dir = mirror ? -1 : 1;
        int v = Math.floorMod(variant, 60);
        boolean hot = kind == Analysis.DROP || kind == Analysis.CHORUS, build = kind == Analysis.BUILD, drop = kind == Analysis.DROP;
        boolean ambient = style == Show.AutoSettings.AMBIENT, rock = style == Show.AutoSettings.ROCK, edm = style == Show.AutoSettings.EDM;
        float slow = (ambient ? 2f : 1f) * tempoFactor(bpm);
        float mvK = mv * (rock ? 0.6f : ambient ? 0.6f : edm ? 1.15f : 1f);
        float fxK = fx * (ambient ? 0.7f : edm ? 1.15f : 1f);
        // how many layers: hot sections more than calm ones, density setting on top
        int layers = hot ? 2 + Math.round(fx * 3) : build ? 2 + Math.round(fx * 2) : 1 + Math.round(fx * 1.5f);
        int made = 0;
        int F8 = Show.Effect.FIGURE8, TW = Show.Effect.TILT_WAVE, BH = Show.Effect.BALLYHOO;
        // ---- 1. beam movement (always first: it is what makes a rig look alive)
        if (beams.length > 0 && made < layers) {
            int[][] calm = {{Show.Effect.SWEEP, 16}, {Show.Effect.CIRCLE, 16}, {F8, 16}, {Show.Effect.FAN, 16}, {BH, 32}, {TW, 16}};
            int[][] rise = {{Show.Effect.FAN, 8}, {TW, 8}, {Show.Effect.SWEEP, 8}, {Show.Effect.CIRCLE, 8}, {F8, 8}};
            int[][] peak = {{Show.Effect.CIRCLE, 4}, {Show.Effect.SWEEP, 4}, {BH, 8}, {Show.Effect.FAN, 8}, {F8, 4}, {TW, 2}, {Show.Effect.CIRCLE, 2}};
            if (rock) peak = new int[][] {{Show.Effect.SWEEP, 4}, {TW, 4}, {Show.Effect.FAN, 8}, {Show.Effect.SWEEP, 2}};
            int[] e = pick(hot ? peak : build ? rise : calm, v, mask);
            if (e != null) {
                float amp = (hot ? 0.45f : build ? 0.2f + 0.45f * prog : 0.2f) * mvK;
                float rate = e[1] * slow * (build && prog > 0.6f ? 0.5f : 1f);
                if (add(c, mask, e[0], beams, rate, true, amp, e[0] == Show.Effect.FAN ? 1f : 0.75f, dir, 0.5f) != null) made++;
            }
        }
        // ---- 2. wash colour / pixels
        if (washes.length > 0 && made < layers) {
            int[][] calm = {{Show.Effect.COLOR_CHASE, 16}, {Show.Effect.PIXEL, 8}, {Show.Effect.WAVE, 16}, {Show.Effect.RAINBOW, 32}};
            int[][] rise = {{Show.Effect.PIXEL, 2}, {Show.Effect.DIMMER_CHASE, 2}, {Show.Effect.PIXEL, 1}, {Show.Effect.SPARKLE, 1}};
            int[][] peak = {{Show.Effect.PIXEL, 2}, {Show.Effect.COLOR_CHASE, 4}, {Show.Effect.PULSE, 1}, {Show.Effect.WAVE, 4}, {Show.Effect.ALTERNATE, 2},
                {Show.Effect.PIXEL, 1}, {Show.Effect.SPARKLE, 1}};
            if (rock || ambient) calm = new int[][] {{Show.Effect.COLOR_CHASE, 16}, {Show.Effect.WAVE, 16}, {Show.Effect.PIXEL, 8}};
            int[] e = pick(hot ? peak : build ? rise : calm, v + 1, mask);
            if (e != null && ambient && (e[0] == Show.Effect.PULSE || e[0] == Show.Effect.DIMMER_CHASE || e[0] == Show.Effect.SPARKLE || e[0] == Show.Effect.ALTERNATE))
                e = new int[] {Show.Effect.COLOR_CHASE, 16};
            if (e != null) {
                boolean colour = e[0] == Show.Effect.COLOR_CHASE || e[0] == Show.Effect.RAINBOW;
                float amp = (colour ? 1f : hot ? 0.6f : build ? 0.35f + 0.35f * prog : 0.35f) * fxK;
                if (e[0] == Show.Effect.RAINBOW) amp = Math.min(amp, 0.55f);       // a rainbow tints, the palette stays
                float rate = e[1] * slow * (build && prog > 0.6f ? 0.5f : 1f);
                Show.Effect ef = add(c, mask, e[0], washes, rate, true, amp, 1f, dir, e[0] == Show.Effect.PULSE ? 0.35f : 0.5f);
                if (ef != null) { made++; if (e[0] == Show.Effect.COLOR_CHASE) ef.colors = Palettes.chase(pal); }
            }
        }
        // ---- 3. beam intensity (chases on the hot parts and the builds; never in ambient)
        if (beams.length > 1 && made < layers && !ambient && (hot || build)) {
            int[][] pool = build ? new int[][] {{Show.Effect.DIMMER_CHASE, prog < 0.5f ? 2 : 1}, {Show.Effect.SPARKLE, 1}, {Show.Effect.DIMMER_CHASE, 2}}
                : new int[][] {{Show.Effect.DIMMER_CHASE, 1}, {Show.Effect.PULSE, 2}, {Show.Effect.ALTERNATE, 2}, {Show.Effect.DIMMER_CHASE, 2}, {Show.Effect.SPARKLE, 1}};
            int[] e = pick(pool, v + 2, mask);
            if (e != null && add(c, mask, e[0], beams, e[1] * tempoFactor(bpm), true, (hot ? 0.55f : 0.3f + 0.4f * prog) * fxK, 1f, -dir, 0.45f) != null) made++;
        }
        // ---- 4. beam zoom: the beams tighten and breathe in the builds, punch on the drops
        if (beams.length > 0 && made < layers && !ambient && (build && prog > 0.3f || drop))
            if (add(c, mask, Show.Effect.ZOOM_PULSE, beams, (drop ? 2 : prog > 0.7f ? 1 : 2) * tempoFactor(bpm), true, (drop ? 0.25f : 0.15f + 0.2f * prog) * fxK, 0.5f, dir, 0.5f) != null) made++;
        // ---- 5. wash movement (slow tilt waves keep the washes breathing)
        if (washes.length > 1 && made < layers && !rock) {
            float amp = (hot ? 0.3f : 0.15f) * mvK;
            int type = (mask & (1 << TW)) != 0 && v % 2 == 0 ? TW : Show.Effect.WAVE;
            if (add(c, mask, type, washes, (hot ? 8 : 16) * slow, true, amp, 1f, dir, 0.5f) != null) made++;
        }
        // ---- 6. strobes
        if (strobes.length > 0 && set.strobes && !ambient) {
            if (drop) {
                int t = edm && (mask & (1 << Show.Effect.RANDOM_STROBE)) != 0 && v % 2 == 1 ? Show.Effect.RANDOM_STROBE : Show.Effect.CHASE;
                add(c, mask, t, strobes, t == Show.Effect.RANDOM_STROBE ? 2f * tempoFactor(bpm) : 1f, true, 0.6f * fx, 1f, dir, 0.3f);
            }
            else if (kind == Analysis.CHORUS && (edm || rock)) add(c, mask, rock && strobes.length > 1 ? Show.Effect.ALTERNATE : Show.Effect.PULSE, strobes, 2f, true, 0.35f * fx, 0f, 1, 0.3f);
            else if (build && prog > 0.4f) add(c, mask, Show.Effect.DIMMER_CHASE, strobes, prog > 0.8f ? 0.5f : 1f, true, 0.4f * prog * fx, 1f, dir, 0.3f);
        }
    }

    /** Rev. 24: rate multiplier for the tempo: half time above 150 BPM, double time below 85 BPM. */
    static float tempoFactor(float bpm) {
        if (bpm <= 0) return 1f;
        return bpm > 150f ? 2f : bpm < 85f ? 0.5f : 1f;
    }

    /** Rev. 24: the {@code v}-th effect of the pool that the effect mask allows (null if none). */
    static int[] pick(int[][] pool, int v, int mask) {
        for (int i = 0; i < pool.length; i++) {
            int[] e = pool[Math.floorMod(v + i, pool.length)];
            if ((mask & (1 << e[0])) != 0) return e;
        }
        return null;
    }

    /** Compatibility with the first pass (live AutoShow): the section's own variant. */
    static void effects(Show.Cue c, Show.AutoSettings set, Analysis.Section s, Palettes.Palette pal, int[] beams, int[] washes, int[] strobes,
                        float prog, boolean mirror, float fx, float mv) {
        effects(c, set, s, pal, beams, washes, strobes, prog, mirror, fx, mv, s.motif);
    }

    /**
     * Move in dark (rev. 2): during the short cut before a drop, once the light is out, the heads turn towards the drop's positions,
     * colours and beam settings, gliding until the drop — which then starts at full speed without heads jumping across the stage.
     */
    private static void moveInDark(Show show, Show.CueList list, int listId, double beatMs) {
        for (int i = 0; i + 1 < list.cues.size(); i++) {
            Show.Cue cut = list.cues.get(i), drop = list.cues.get(i + 1);
            if (!"Cut before drop".equals(cut.name)) continue;
            long cutMs = -1;
            for (Show.Event e : show.timeline.events)
                if (e.kind == Show.Event.GO && e.list == listId && Math.abs(e.cue - cut.number) < 1e-4f) cutMs = e.ms;
            if (cutMs < 0) continue;
            // the moves start once the light is out (150 ms after the cut), and end on the drop
            Show.Cue mib = new Show.Cue();
            mib.number = cut.number + 0.5f;
            mib.name = "Move in dark";
            for (Map.Entry<Integer, Look> en : drop.looks.entrySet()) {
                Look dark = new Look();
                for (Attr at : Attr.ALL) if (at != Attr.DIMMER && en.getValue().has(at)) dark.set(at, en.getValue().get(at));
                dark.set(Attr.DIMMER, 0.02f);
                dark.set(Attr.STROBE, 0f);
                mib.looks.put(en.getKey(), dark);
            }
            mib.fadeOut = 0.12f;
            mib.fadeIn = (float) Math.max(0.2, beatMs / 1000.0 - 0.2);
            list.cues.add(i + 1, mib);
            go(show, listId, mib.number, cutMs + 150);
            i++;
        }
    }

    private static Show.Effect add(Show.Cue c, int mask, int type, int[] fids, float rate, boolean bpm, float amp, float spread, int dir, float duty) {
        if (fids.length == 0 || (mask & (1 << type)) == 0 || amp <= 0.01f) return null;
        Show.Effect e = new Show.Effect();
        e.type = type; e.fids = fids.clone(); e.rate = Math.max(0.0625f, rate); e.bpmSync = bpm; e.amplitude = clamp(amp); e.spread = spread;
        e.direction = dir; e.duty = duty; e.fade = 1.5f;      // rev. 2: effects ease in
        if (type == Show.Effect.FAN) e.symmetric = true;
        c.effects.add(e);
        return e;
    }

    /** Accents on the downbeats of drops and rock choruses: one per bar at most (never faster than the strobe cap). */
    private static int accents(Show show, Show.AutoSettings set, Analysis a, Analysis.Section s, int strobeGroup, boolean haveStrobes) {
        if (a.bpm <= 0 || !set.strobes || set.style == Show.AutoSettings.AMBIENT) return 0;
        boolean wanted = s.kind == Analysis.DROP || (set.style == Show.AutoSettings.ROCK && s.kind == Analysis.CHORUS)
            || (set.style == Show.AutoSettings.EDM && s.kind == Analysis.CHORUS);
        if (!wanted) return 0;
        double beatMs = a.beatMs();
        long k0 = (long) Math.ceil((s.startMs - a.firstBeatMs) / beatMs);
        int n = 0;
        long lastMs = -1_000_000L;
        long minGap = Math.max(1000L / Math.max(1, Math.round(Math.min(set.strobeMaxHz, 3f))), Math.round(beatMs * 2));
        for (long k = Math.max(0, k0); ; k++) {
            long t = a.beat(k);
            if (t >= s.endMs) break;
            if (!a.downbeat(k)) continue;
            // only where the music really hits
            if (a.at(a.onset, t) < 0.25f && a.at(a.low, t) < 0.5f) continue;
            if (t - lastMs < minGap) continue;
            Show.Event e = new Show.Event();
            e.ms = t; e.kind = Show.Event.ACCENT; e.group = haveStrobes ? strobeGroup : -1;
            e.strength = Math.min(1f, set.intensity); e.lengthMs = (int) Math.min(220, beatMs * 0.4);
            show.timeline.events.add(e);
            lastMs = t;
            n++;
            if (show.timeline.events.size() >= Show.MAX_EVENTS - 64) break;
        }
        return n;
    }

    /** Rev. 24: a blinder (BLINDER X2 / X4 / X8). */
    static boolean blinder(Show show, int fid) {
        Show.Patch p = show.fixture(fid);
        return p != null && com.concertstage.light.dmx.DmxMode.tungsten(p.profile());
    }

    /**
     * Rev. 24 — blinder hits: on the first downbeat of a drop (and of a chorus in Rock / Pop when it follows a calmer part), then every
     * fourth bar of a drop where the music hits. A hit is a short flash of the blinders group that fades like a halogen lamp (longer
     * than a strobe accent).
     */
    private static int blinderHits(Show show, Show.AutoSettings set, Analysis a, Analysis.Section s, Analysis.Section prev, int group, boolean have) {
        if (!have || a.bpm <= 0 || !set.strobes || set.style == Show.AutoSettings.AMBIENT) return 0;   // flashes: off with the strobes
        boolean drop = s.kind == Analysis.DROP;
        boolean chorus = s.kind == Analysis.CHORUS && (set.style == Show.AutoSettings.ROCK || set.style == Show.AutoSettings.POP)
            && (prev == null || !Palettes.hot(prev.kind));
        if (!drop && !chorus) return 0;
        double beatMs = a.beatMs();
        long k0 = (long) Math.ceil((s.startMs - a.firstBeatMs) / beatMs);
        int n = 0, bar = 0;
        for (long k = Math.max(0, k0); ; k++) {
            long t = a.beat(k);
            if (t >= s.endMs || show.timeline.events.size() >= Show.MAX_EVENTS - 64) break;
            if (!a.downbeat(k)) continue;
            boolean first = bar == 0;
            boolean every4 = drop && bar % 4 == 0 && (a.at(a.onset, t) >= 0.3f || a.at(a.low, t) >= 0.55f);
            bar++;
            if (!first && !every4) continue;
            if (!first && chorus) break;
            Show.Event e = new Show.Event();
            e.ms = t; e.kind = Show.Event.ACCENT; e.group = group;
            e.strength = Math.min(1f, set.intensity * (first ? 1f : 0.8f)); e.lengthMs = (int) Math.min(700, beatMs * 1.2);
            show.timeline.events.add(e);
            n++;
        }
        return n;
    }

    private static void go(Show show, int list, float cue, long ms) {
        Show.Event e = new Show.Event();
        e.ms = Math.max(0, ms); e.kind = Show.Event.GO; e.list = list; e.cue = cue;
        show.timeline.events.add(e);
    }

    /** A time snapped to the nearest beat (builds step on the beat). */
    private static long snap(Analysis a, long ms) {
        if (a.bpm <= 0) return ms;
        double b = a.beatMs();
        long k = Math.round((ms - a.firstBeatMs) / b);
        return Math.max(0, a.beat(k));
    }

    private static Look color(Look l, int rgb) {
        return l.set(Attr.RED, ((rgb >> 16) & 255) / 255f).set(Attr.GREEN, ((rgb >> 8) & 255) / 255f).set(Attr.BLUE, (rgb & 255) / 255f);
    }

    private static void positions(Look l, Look pos) {
        if (pos == null) return;
        if (pos.has(Attr.PAN)) l.set(Attr.PAN, pos.get(Attr.PAN));
        if (pos.has(Attr.TILT)) l.set(Attr.TILT, pos.get(Attr.TILT));
    }

    static int[] byType(Show show, String type) {
        return show.patch.stream().filter(p -> p.type.equals(type)).mapToInt(p -> p.fid).toArray();
    }

    static int[] concat(int[] a, int[] b) {
        int[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private static void ensureGroup(Show show, String name, int[] fids, int color) {
        if (fids.length == 0) return;
        for (Show.Group g : show.groups) if (g.name.equals(name)) { g.fids = fids; return; }
        if (show.groups.size() >= Show.MAX_GROUPS) return;
        Show.Group g = new Show.Group();
        g.id = show.nextGroupId(); g.name = name; g.fids = fids; g.color = color;
        show.groups.add(g);
    }

    private static int groupId(Show show, String name) {
        for (Show.Group g : show.groups) if (g.name.equals(name)) return g.id;
        return -1;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
