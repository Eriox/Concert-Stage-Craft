package com.concertstage.light.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lighting engine of the LUMICORE X1: from the show, the live state and the shared clock, the value of every attribute of every fixture.
 * It is a pure function of its inputs (no hidden timer), so the server and every client compute the same light at the same game time,
 * and pausing, seeking or changing the track can never desynchronise it.
 *
 * <p>Order of the layers, lowest first: home values, cue lists by priority (AutoShow lists sit at low priority), effects of the running
 * cues, timeline accents, the programmer (highest), its effects, then the masters and the safety controls. Intensity is
 * Highest-Takes-Precedence between playbacks (scaled by their faders); the other attributes are Latest-Takes-Precedence. Cues track:
 * a cue keeps the values of the previous cues unless it changes them.
 */
public final class Engine {
    /** State of the linked music: is it valid, which track, is it playing, where is the playhead (ms). */
    public record Music(boolean valid, String sha1, boolean playing, long positionMs) {
        public static final Music NONE = new Music(false, "", false, 0);
    }

    /** Final values of one fixture. Normalised like the programmer; {@link #strobeHz} already capped by the safety settings. */
    public static final class Output {
        public final int fid;
        public FixtureProfile profile;
        public final float[] v = new float[Attr.COUNT];
        /** Per zone: red, green, blue, level (0..1), already including colour effects and pixel effects (not the dimmer). */
        public float[] zones = new float[0];
        public float strobeHz;
        /**
         * Rev. 21: strobe rate of each light zone (top, centre, bottom) of a zone fixture (FixtureProfile#hasSections): its own strobe
         * when it has one, else the shared one; capped by the safety settings like {@link #strobeHz}. 0 = steady.
         */
        public final float[] sectionHz = new float[3];
        /** Rev. 21: top and bottom strobe with their own channels at the same rate: they flash in turn (ZoneStrobe). */
        public boolean alternate;
        /** Rev. 24: DMX in the two-instance Compact: top and bottom bars are one instance and strobe together. */
        public boolean barsLinked;
        /** Movement limited (render moves slowly). */
        public boolean slow;
        /** Average emitted colour (for the beam of zone fixtures). */
        public float beamR, beamG, beamB;
        /** 1.2.1: colour of the gel in front of a conventional (0xFFFFFF = none). */
        public int gel = 0xFFFFFF;

        Output(int fid) {
            this.fid = fid;
        }

        public float get(Attr a) {
            return v[a.ordinal()];
        }

        /**
         * 1.2.2: a copy for a look-only change (the tungsten lamp of a conventional or a blinder lags its dimmer and warms with
         * it). The desk's outputs are reused every frame: the renderer must never write a lagging level back into them.
         */
        public Output copy() {
            Output c = new Output(fid);
            c.profile = profile;
            System.arraycopy(v, 0, c.v, 0, v.length);
            c.zones = zones.clone();
            c.strobeHz = strobeHz;
            System.arraycopy(sectionHz, 0, c.sectionHz, 0, sectionHz.length);
            c.alternate = alternate;
            c.barsLinked = barsLinked;
            c.slow = slow;
            c.beamR = beamR;
            c.beamG = beamG;
            c.beamB = beamB;
            c.gel = gel;
            return c;
        }
    }

    private record Source(int priority, long order, Show.CueList list, int cur, int prev, long elapsedMs, float level, boolean releasing,
                          float releaseProgress) {}

    private final Map<Integer, Output> outputs = new LinkedHashMap<>();
    private final Map<Integer, List<Map<Integer, Look>>> tracked = new HashMap<>();
    private Show trackedShow;
    private int trackedEdits = -1;
    private final float[] htp = new float[Show.MAX_FIXTURES + 1];

    /** Values of every fixture of the show. The returned map and its outputs are reused by the next call. */
    public Map<Integer, Output> evaluate(Show show, Live live, long nowMs, Music music) {
        return evaluate(show, live, nowMs, music, null);
    }

    private final Map<Integer, Output> dmxOutputs = new LinkedHashMap<>();

    /**
     * Same, with the DMX the desk receives or plays back (External Show Control). One source drives the fixtures ({@link Live#control}):
     * the desk (cue lists, programmer, timeline, AutoShow) or DMX. Switching cross-fades over {@link Live#CONTROL_FADE_MS}; the masters
     * and the safety controls (grand master, blackout, no strobe, strobe cap, move limit) apply to both.
     */
    public Map<Integer, Output> evaluate(Show show, Live live, long nowMs, Music music, com.concertstage.light.dmx.DmxData dmx) {
        float from = Live.dmxDriven(live.previousControl) ? 1f : 0f, to = Live.dmxDriven(live.control) ? 1f : 0f;
        float k = Math.max(0f, Math.min(1f, (nowMs - live.controlMs) / (float) Live.CONTROL_FADE_MS));
        float w = from + (to - from) * k;
        if (dmx == null) w = 0f;
        if (w < 1f) evaluateInternal(show, live, nowMs, music);
        else homes(show);
        if (w <= 0f) return outputs;
        evaluateDmx(show, live, nowMs, dmx);
        for (Show.Patch p : show.patch) {
            Output o = outputs.get(p.fid), e = dmxOutputs.get(p.fid);
            if (o == null || e == null) continue;
            if (w >= 1f) {
                System.arraycopy(e.v, 0, o.v, 0, o.v.length);
                System.arraycopy(e.zones, 0, o.zones, 0, Math.min(o.zones.length, e.zones.length));
                o.strobeHz = e.strobeHz; o.slow = e.slow; o.beamR = e.beamR; o.beamG = e.beamG; o.beamB = e.beamB;
                System.arraycopy(e.sectionHz, 0, o.sectionHz, 0, 3);
                o.alternate = e.alternate;
                continue;
            }
            for (Attr a : Attr.ALL) {
                int i = a.ordinal();
                o.v[i] = a.snaps() ? (w >= 0.5f ? e.v[i] : o.v[i]) : o.v[i] + (e.v[i] - o.v[i]) * w;
            }
            for (int i = 0; i < Math.min(o.zones.length, e.zones.length); i++) o.zones[i] += (e.zones[i] - o.zones[i]) * w;
            o.beamR += (e.beamR - o.beamR) * w; o.beamG += (e.beamG - o.beamG) * w; o.beamB += (e.beamB - o.beamB) * w;
            o.strobeHz = w >= 0.5f ? e.strobeHz : o.strobeHz;
            if (w >= 0.5f) { System.arraycopy(e.sectionHz, 0, o.sectionHz, 0, 3); o.alternate = e.alternate; }
            o.slow |= e.slow;
        }
        // rev. 4c: the focus tool's positions over the external desk
        for (Map.Entry<Integer, float[]> f : live.focus.entrySet()) {
            Output o = outputs.get(f.getKey());
            if (o == null) continue;
            float[] pt = f.getValue();
            if (!Float.isNaN(pt[0])) o.v[Attr.PAN.ordinal()] += (pt[0] - o.v[Attr.PAN.ordinal()]) * w;
            if (!Float.isNaN(pt[1])) o.v[Attr.TILT.ordinal()] += (pt[1] - o.v[Attr.TILT.ordinal()]) * w;
        }
        return outputs;
    }

    /** Home values in {@link #outputs} (the desk layers are not computed while DMX fully drives the fixtures). */
    private void homes(Show show) {
        outputs.keySet().removeIf(fid -> show.fixture(fid) == null);
        for (Show.Patch p : show.patch) {
            Output o = outputs.computeIfAbsent(p.fid, Output::new);
            o.profile = p.profile();
            if (o.zones.length != o.profile.zones * 4) o.zones = new float[o.profile.zones * 4];
        }
    }

    /** The fixtures as DMX sets them: home values, then the DMX mode of each fixture (or the Virtual Matrix), then masters and safety. */
    private void evaluateDmx(Show show, Live live, long nowMs, com.concertstage.light.dmx.DmxData dmx) {
        dmxOutputs.keySet().removeIf(fid -> show.fixture(fid) == null);
        java.util.List<Show.Patch> members = com.concertstage.light.dmx.VirtualMatrix.members(show);
        int[] matrix = members.isEmpty() ? null : com.concertstage.light.dmx.VirtualMatrix.channels(show, dmx);
        for (Show.Patch p : show.patch) {
            Output o = dmxOutputs.computeIfAbsent(p.fid, Output::new);
            o.profile = p.profile();
            for (Attr a : Attr.ALL) o.v[a.ordinal()] = o.profile.home(a);
            int nz = o.profile.zones;
            if (o.zones.length != nz * 4) o.zones = new float[nz * 4];
            java.util.Arrays.fill(o.zones, Float.NaN);
            if (p.dmxMode == com.concertstage.light.dmx.DmxMode.MATRIX) {
                if (matrix != null) {
                    com.concertstage.light.dmx.VirtualMatrix.values(o.profile, matrix, members.indexOf(p), members.size(), nowMs, o.v);
                    com.concertstage.light.dmx.DmxDecode.transform(p, o.v);
                }
            } else {
                com.concertstage.light.dmx.DmxDecode.decode(p, dmx, o.v, o.zones);
            }
            o.barsLinked = com.concertstage.light.dmx.DmxMode.barsLinked(o.profile, p.dmxMode);
        }
        masters(show, live, dmxOutputs);
    }

    private void evaluateInternal(Show show, Live live, long nowMs, Music music) {
        retrack(show);
        // ---- home values
        outputs.keySet().removeIf(fid -> show.fixture(fid) == null);
        Map<Integer, Integer> slot = new HashMap<>();
        int si = 0;
        for (Show.Patch p : show.patch) {
            Output o = outputs.computeIfAbsent(p.fid, Output::new);
            o.profile = p.profile();
            for (Attr a : Attr.ALL) o.v[a.ordinal()] = o.profile.home(a);
            int nz = o.profile.zones;
            if (o.zones.length != nz * 4) o.zones = new float[nz * 4];
            java.util.Arrays.fill(o.zones, Float.NaN);
            slot.put(p.fid, si);
            htp[si++] = 0f;
        }
        // ---- playbacks (manual and timeline), lowest priority first
        long musicMs = music.valid() ? music.positionMs() - show.timeline.offsetMs : 0;
        boolean timelineActive = live.timelineArmed && music.valid() && !show.timeline.events.isEmpty()
            && (show.timeline.trackSha1.isEmpty() || show.timeline.trackSha1.equals(music.sha1()));
        List<Source> sources = new ArrayList<>();
        for (Show.CueList list : show.lists) {
            if (list.cues.isEmpty()) continue;
            Source s = timelineActive ? timelineSource(show, list, musicMs) : null;
            if (s == null && !(timelineActive && hasTimelineEvents(show, list.id))) s = manualSource(list, live.playbacks.get(list.id), nowMs);
            if (s != null) sources.add(s);
        }
        sources.sort((a, b) -> a.priority != b.priority ? Integer.compare(a.priority, b.priority) : Long.compare(a.order, b.order));
        List<Map<Integer, Look>> listTracks;
        List<Object[]> activeEffects = new ArrayList<>();
        for (Source s : sources) {
            listTracks = tracked.get(s.list.id);
            if (listTracks == null) continue;
            Map<Integer, Look> to = s.cur >= 0 && s.cur < listTracks.size() ? listTracks.get(s.cur) : Map.of();
            Map<Integer, Look> from = s.prev >= 0 && s.prev < listTracks.size() ? listTracks.get(s.prev) : Map.of();
            Show.Cue cue = s.list.cues.get(Math.max(0, Math.min(s.list.cues.size() - 1, s.cur >= 0 ? s.cur : s.prev)));
            float tDelay = cue.delay * 1000f;
            float t = s.elapsedMs - tDelay;
            for (Show.Patch p : show.patch) {
                Look lt = to.get(p.fid), lf = from.get(p.fid);
                if (lt == null && lf == null) continue;
                Output o = outputs.get(p.fid);
                int k = slot.get(p.fid);
                if (s.releasing) {
                    Look l = lt != null ? lt : lf;
                    float d = l.has(Attr.DIMMER) ? l.get(Attr.DIMMER) : 0f;
                    htp[k] = Math.max(htp[k], d * (1f - s.releaseProgress) * s.level);
                    if (s.releaseProgress < 1f) applyLtp(o, l, null, 1f);
                    continue;
                }
                // intensity: HTP, fades up with fade in, down with fade out
                float dt = lt != null && lt.has(Attr.DIMMER) ? lt.get(Attr.DIMMER) : 0f;
                float df = lf != null && lf.has(Attr.DIMMER) ? lf.get(Attr.DIMMER) : (s.prev < 0 ? 0f : dt);
                float fade = (dt >= df ? cue.fadeIn : cue.fadeOut) * 1000f;
                float pd = progress(t, fade);
                htp[k] = Math.max(htp[k], (df + (dt - df) * pd) * s.level);
                if (s.level > 0.001f) applyLtpFade(o, lf, lt, progress(t, cue.fadeIn * 1000f));
            }
            // effects cross-fade between two cues (rev. 2): the effects of the cue being left fade out while the new ones fade in, over the
            // same time, so an effect that continues (same type, same fixtures) keeps its level and a new one takes over without a jump
            float xfade = Math.max(cue.fadeIn, 0.25f) * 1000f;
            if (!s.releasing && s.cur >= 0 && t >= 0) {
                for (Show.Effect e : cue.effects) {
                    float env = progress(t, Math.max(xfade, e.fade * 1000f));
                    activeEffects.add(new Object[] {e, env * s.level, s.elapsedMs});
                }
            }
            if (!s.releasing && s.prev >= 0 && s.prev != s.cur && s.prev < s.list.cues.size()) {
                float out = 1f - progress(t, xfade);
                if (out > 0.001f) for (Show.Effect e : s.list.cues.get(s.prev).effects) activeEffects.add(new Object[] {e, out * s.level, s.elapsedMs});
            }
        }
        for (Show.Patch p : show.patch) outputs.get(p.fid).v[Attr.DIMMER.ordinal()] = htp[slot.get(p.fid)];
        // ---- effects of the cues
        double secs = nowMs / 1000.0;
        double beats = beatPosition(show, music, timelineActive, nowMs);
        for (Object[] ae : activeEffects) Effects.apply((Show.Effect) ae[0], (Float) ae[1], secs, beats, show, outputs);
        // ---- timeline accents
        if (timelineActive) {
            for (Show.Event e : show.timeline.events) {
                if (e.kind != Show.Event.ACCENT || e.ms > musicMs) continue;
                if (musicMs >= e.ms + Math.max(20, e.lengthMs)) continue;
                float x = (musicMs - e.ms) / (float) Math.max(20, e.lengthMs);
                float env = (1f - x) * (1f - x) * e.strength;
                Show.Group g = e.group >= 0 ? show.group(e.group) : null;
                for (Show.Patch p : show.patch) {
                    if (g != null && !contains(g.fids, p.fid)) continue;
                    Output o = outputs.get(p.fid);
                    o.v[Attr.DIMMER.ordinal()] = Math.max(o.v[Attr.DIMMER.ordinal()], env);
                }
            }
        }
        // ---- programmer: overrides everything it sets
        for (Map.Entry<Integer, Look> e : live.programmer.entrySet()) {
            Output o = outputs.get(e.getKey());
            if (o == null) continue;
            applyLtp(o, e.getValue(), e.getValue().zones, 1f);
            if (e.getValue().has(Attr.DIMMER)) o.v[Attr.DIMMER.ordinal()] = e.getValue().get(Attr.DIMMER);
        }
        float progEnv = 1f;
        for (Show.Effect e : live.programmerEffects) {
            float env = e.fade <= 0.01f ? 1f : Math.min(1f, (nowMs - live.programmerEffectsMs) / (e.fade * 1000f));
            Effects.apply(e, env * progEnv, secs, beats, show, outputs);
        }
        masters(show, live, outputs);
    }

    /** Masters, safety and final colours of every fixture of {@code outs}. */
    private static void masters(Show show, Live live, Map<Integer, Output> outs) {
        for (Show.Patch p : show.patch) {
            Output o = outs.get(p.fid);
            if (o == null) continue;
            FixtureProfile pr = o.profile;
            float[] v = o.v;
            v[Attr.DIMMER.ordinal()] = live.blackout ? 0f : clamp01(v[Attr.DIMMER.ordinal()]) * clamp01(live.grandMaster);
            float hz = pr.strobeHz(v[Attr.STROBE.ordinal()]);
            if (live.noStrobe) hz = 0f;
            o.strobeHz = Math.min(hz, live.strobeCapHz);
            o.slow = live.moveLimit;
            if (live.moveLimit) {
                if (pr.panRange > 0) {
                    float m = 30f / pr.panRange;
                    v[Attr.PAN.ordinal()] = clamp(v[Attr.PAN.ordinal()], 0.5f - m, 0.5f + m);
                }
                if (pr.tiltRange > 0) {
                    float m = 30f / pr.tiltRange;
                    v[Attr.TILT.ordinal()] = clamp(v[Attr.TILT.ordinal()], 0.5f - m, 0.5f + m);
                }
            }
            for (Attr a : Attr.ALL) v[a.ordinal()] = clamp01(v[a.ordinal()]);
            // rev. 21: each light zone strobes at its own rate when it has one, otherwise with the shared strobe; the safety settings
            // (no strobe, strobe cap) hold for every zone
            for (int k = 0; k < 3; k++) {
                float zh = pr.hasSections() && v[FixtureProfile.sectionStrobe(k).ordinal()] > 0f ? pr.strobeHz(v[FixtureProfile.sectionStrobe(k).ordinal()]) : hz;
                o.sectionHz[k] = live.noStrobe ? 0f : Math.min(zh, live.strobeCapHz);
            }
            o.alternate = pr.hasSections() && !o.barsLinked && v[Attr.TOP_STROBE.ordinal()] > 0f && v[Attr.BOT_STROBE.ordinal()] > 0f
                && Math.abs(o.sectionHz[FixtureProfile.TOP] - o.sectionHz[FixtureProfile.BOTTOM]) < 0.01f && o.sectionHz[FixtureProfile.TOP] > 0.05f;
            if (!pr.has(Attr.PAN)) v[Attr.PAN.ordinal()] = 0.5f;
            if (pr.has(Attr.SHUTTER)) v[Attr.DIMMER.ordinal()] *= v[Attr.SHUTTER.ordinal()];
            if (pr.has(Attr.DOUSER)) v[Attr.DIMMER.ordinal()] *= v[Attr.DOUSER.ordinal()];
            o.gel = p.gel;
            finishZones(o);
        }
    }

    // ------------------------------------------------------------ sources
    private Source manualSource(Show.CueList list, Live.Playback pb, long nowMs) {
        if (pb == null) return null;
        float level = pb.flash ? 1f : pb.level;
        if (pb.running()) {
            long el = pb.elapsed(nowMs);
            int[] st = follow(list, pb.index, pb.from, el);
            return new Source(priority(list, st[0]), pb.goMs + st[2], list, st[0], st[1], st[3], level, false, 0f);
        }
        if (pb.releaseMs >= 0 && pb.released >= 0 && pb.released < list.cues.size()) {
            Show.Cue c = list.cues.get(pb.released);
            float p = progress(nowMs - pb.releaseMs, c.fadeOut * 1000f);
            if (p >= 1f) return null;
            return new Source(priority(list, pb.released), pb.releaseMs, list, pb.released, -1, 0, level, true, p);
        }
        return null;
    }

    /** The cue a timecoded list is in at music time {@code ms}: last GO / STOP event of that list before the playhead. */
    private Source timelineSource(Show show, Show.CueList list, long ms) {
        Show.Event last = null, before = null;
        for (Show.Event e : show.timeline.events) {
            if (e.ms > ms) break;
            if (e.list != list.id || (e.kind != Show.Event.GO && e.kind != Show.Event.STOP)) continue;
            before = last;
            last = e;
        }
        if (last == null) return null;
        if (last.kind == Show.Event.STOP) {
            int rel = before != null && before.kind == Show.Event.GO ? list.indexOf(before.cue) : -1;
            if (rel < 0) return null;
            float p = progress(ms - last.ms, list.cues.get(rel).fadeOut * 1000f);
            if (p >= 1f) return null;
            return new Source(priority(list, rel), last.ms, list, rel, -1, 0, 1f, true, p);
        }
        int cur = list.indexOf(last.cue);
        if (cur < 0) return null;
        int prev = before != null && before.kind == Show.Event.GO ? list.indexOf(before.cue) : -1;
        int[] st = follow(list, cur, prev, ms - last.ms);
        return new Source(priority(list, st[0]), last.ms + st[2], list, st[0], st[1], st[3], 1f, false, 0f);
    }

    private static boolean hasTimelineEvents(Show show, int list) {
        for (Show.Event e : show.timeline.events) if (e.list == list && e.kind != Show.Event.ACCENT) return true;
        return false;
    }

    /**
     * Follows the auto-follow chain: from cue {@code index} started {@code elapsed} ms ago, where are we now?
     * Returns {current index, index the fade comes from, ms between GO and the start of the current cue, ms since that start}.
     */
    static int[] follow(Show.CueList list, int index, int from, long elapsed) {
        int cur = index, prev = from;
        long start = 0;
        for (int guard = 0; guard < 1000; guard++) {
            if (cur < 0 || cur >= list.cues.size()) break;
            Show.Cue c = list.cues.get(cur);
            int next = cur + 1 < list.cues.size() ? cur + 1 : list.loop ? 0 : -1;
            if (next < 0) break;
            Show.Cue n = list.cues.get(next);
            float wait;
            if (c.follow >= 0) wait = c.duration() + c.follow;
            else if (n.trigger == Show.Cue.TRIGGER_FOLLOW) wait = c.duration();
            else break;
            long w = (long) (Math.max(0.05f, wait) * 1000);
            if (elapsed - start < w) break;
            start += w;
            prev = cur;
            cur = next;
        }
        return new int[] {cur, prev, (int) Math.min(Integer.MAX_VALUE, start), (int) Math.min(Integer.MAX_VALUE, elapsed - start)};
    }

    private static int priority(Show.CueList list, int cue) {
        if (cue >= 0 && cue < list.cues.size() && list.cues.get(cue).priority >= 0) return list.cues.get(cue).priority;
        return list.priority;
    }

    // ------------------------------------------------------------ tracking
    private void retrack(Show show) {
        if (show == trackedShow && show.edits == trackedEdits) return;
        tracked.clear();
        for (Show.CueList list : show.lists) {
            List<Map<Integer, Look>> states = new ArrayList<>(list.cues.size());
            Map<Integer, Look> cur = new HashMap<>();
            for (Show.Cue c : list.cues) {
                Map<Integer, Look> next = new HashMap<>();
                for (Map.Entry<Integer, Look> e : cur.entrySet()) next.put(e.getKey(), e.getValue().copy());
                for (Map.Entry<Integer, Look> e : c.looks.entrySet()) next.computeIfAbsent(e.getKey(), k -> new Look()).merge(e.getValue());
                states.add(next);
                cur = next;
            }
            tracked.put(list.id, states);
        }
        trackedShow = show;
        trackedEdits = show.edits;
    }

    /** Tracked state of a list at a cue (for the console display and the AutoShow tests). */
    public Map<Integer, Look> trackedAt(Show show, int list, int index) {
        retrack(show);
        List<Map<Integer, Look>> l = tracked.get(list);
        return l == null || index < 0 || index >= l.size() ? Map.of() : l.get(index);
    }

    // ------------------------------------------------------------ helpers
    private static void applyLtpFade(Output o, Look from, Look to, float p) {
        for (Attr a : Attr.ALL) {
            if (a == Attr.DIMMER) continue;
            int i = a.ordinal();
            float tv = to != null ? to.v[i] : Float.NaN;
            float fv = from != null ? from.v[i] : Float.NaN;
            if (Float.isNaN(tv) && Float.isNaN(fv)) continue;
            if (Float.isNaN(tv)) { o.v[i] = fv; continue; }
            float base = Float.isNaN(fv) ? o.v[i] : fv;
            o.v[i] = a.snaps() ? (p > 0f ? tv : base) : base + (tv - base) * p;
        }
        float[] zt = to != null ? to.zones : null, zf = from != null ? from.zones : null;
        if (zt == null && zf == null) return;
        for (int i = 0; i < o.zones.length; i++) {
            float tv = zt != null && i < zt.length ? zt[i] : Float.NaN;
            float fv = zf != null && i < zf.length ? zf[i] : Float.NaN;
            if (Float.isNaN(tv) && Float.isNaN(fv)) continue;
            if (Float.isNaN(tv)) { o.zones[i] = fv; continue; }
            float base = Float.isNaN(fv) ? (Float.isNaN(o.zones[i]) ? tv : o.zones[i]) : fv;
            o.zones[i] = base + (tv - base) * p;
        }
    }

    private static void applyLtp(Output o, Look l, float[] zones, float level) {
        for (Attr a : Attr.ALL) {
            if (a == Attr.DIMMER) continue;
            int i = a.ordinal();
            if (!Float.isNaN(l.v[i])) o.v[i] = l.v[i];
        }
        float[] z = zones != null ? zones : l.zones;
        if (z != null) for (int i = 0; i < Math.min(z.length, o.zones.length); i++) if (!Float.isNaN(z[i])) o.zones[i] = z[i];
    }

    /** Colour of the fixture's white emitters for a CTO value: 6500 K (cold) to 2700 K (warm). */
    public static float[] white(float cto) {
        float t = clamp01(cto);
        return new float[] {1f, 0.96f - 0.20f * t, 0.94f - 0.52f * t};
    }

    /**
     * Rev. 25 — colour of a halogen blinder lamp (DWE PAR36 650 W) at dimmer {@code dim}: the warm yellow of a lit blinder (measured on
     * a photo of a lit 4-lite blinder: mid tones 1, 0.85, 0.48; edges 1, 0.71, 0.24), redder as it dims like any halogen lamp. The
     * blinder's CTO channel (Full / Compact) warms it further, towards deep amber; at 0 (home) it is the plain lamp.
     */
    public static float[] halogen(float dim, float cto) {
        float t = (float) Math.sqrt(clamp01(dim));
        float g = 0.56f + 0.24f * t, b = 0.17f + 0.25f * t;                // full: 1, 0.80, 0.42 — 10 %: 1, 0.64, 0.25
        float c = 0.5f * clamp01(cto);
        return new float[] {1f, g + (0.58f - g) * c, b + (0.14f - b) * c};
    }

    private static void finishZones(Output o) {
        FixtureProfile pr = o.profile;
        float[] v = o.v;
        float[] w = com.concertstage.light.dmx.DmxMode.tungsten(pr) ? halogen(v[Attr.DIMMER.ordinal()], v[Attr.CTO.ordinal()]) : white(v[Attr.CTO.ordinal()]);
        float wl = pr.has(Attr.WHITE) ? v[Attr.WHITE.ordinal()] : 0f;
        float r = v[Attr.RED.ordinal()], g = v[Attr.GREEN.ordinal()], b = v[Attr.BLUE.ordinal()];
        if (pr.has(Attr.CYAN)) r *= 1f - v[Attr.CYAN.ordinal()];
        if (pr.has(Attr.MAGENTA)) g *= 1f - v[Attr.MAGENTA.ordinal()];
        if (pr.has(Attr.YELLOW)) b *= 1f - v[Attr.YELLOW.ordinal()];
        if (pr.has(Attr.COLOR_WHEEL)) {
            int color = com.concertstage.light.dmx.DmxMode.macroColor(Math.round(v[Attr.COLOR_WHEEL.ordinal()] * 255));
            r *= ((color >> 16) & 255) / 255f; g *= ((color >> 8) & 255) / 255f; b *= (color & 255) / 255f;
        }
        float mr, mg, mb;
        if (pr.whiteZones > 0) { mr = r; mg = g; mb = b; }                // PULSESTRIKE: the white is in its own strips
        else { mr = Math.min(1f, r + wl * w[0]); mg = Math.min(1f, g + wl * w[1]); mb = Math.min(1f, b + wl * w[2]); }
        if (pr.conventional) {
            // 1.2.1: a halogen lamp (warmer as it dims) through its gel
            float[] h = halogen(v[Attr.DIMMER.ordinal()], 0f);
            int gel = o.gel;
            mr = h[0] * ((gel >> 16) & 255) / 255f; mg = h[1] * ((gel >> 8) & 255) / 255f; mb = h[2] * (gel & 255) / 255f;
        }
        float sr = 0, sg = 0, sb = 0, sl = 0;
        int nz = pr.zones;
        for (int z = 0; z < nz; z++) {
            int i = z * 4;
            boolean white = pr.zoneKind(z) == 1;
            float zr = o.zones[i], zg = o.zones[i + 1], zb = o.zones[i + 2], zl = o.zones[i + 3];
            if (white) { zr = w[0]; zg = w[1]; zb = w[2]; if (Float.isNaN(zl)) zl = wl; }
            else {
                if (Float.isNaN(zr)) zr = mr;
                if (Float.isNaN(zg)) zg = mg;
                if (Float.isNaN(zb)) zb = mb;
                if (Float.isNaN(zl)) zl = 1f;
            }
            if (pr == FixtureProfile.PIXELTUBE_T1) { zr *= w[0]; zg *= w[1]; zb *= w[2]; }
            // rev. 21: the light zones — a bar cell that follows the fixture takes the colour of its bar (times the colour of the
            // fixture); every cell takes the intensity of its zone (times its own level). A zone at 0 is dark, whatever the others do.
            int sec = pr.section(z);
            if (sec >= 0) {
                Attr red = FixtureProfile.sectionRed(sec);
                if (red != null && !white) {
                    if (Float.isNaN(o.zones[i])) zr *= v[red.ordinal()];
                    if (Float.isNaN(o.zones[i + 1])) zg *= v[red.ordinal() + 1];
                    if (Float.isNaN(o.zones[i + 2])) zb *= v[red.ordinal() + 2];
                }
                zl *= v[FixtureProfile.sectionDim(sec).ordinal()];
            }
            o.zones[i] = clamp01(zr); o.zones[i + 1] = clamp01(zg); o.zones[i + 2] = clamp01(zb); o.zones[i + 3] = clamp01(zl);
            sr += o.zones[i] * o.zones[i + 3]; sg += o.zones[i + 1] * o.zones[i + 3]; sb += o.zones[i + 2] * o.zones[i + 3]; sl += o.zones[i + 3];
        }
        if (nz == 0) { o.beamR = mr; o.beamG = mg; o.beamB = mb; return; }
        // New RGB/RGBW fixtures preserve emitted energy when only some pixels are lit.
        // The three legacy zone fixtures retain their historical output calibration.
        if (pr != FixtureProfile.LUMORA_W7 && pr != FixtureProfile.LUMORA_W19 && pr != FixtureProfile.PULSESTRIKE_M) {
            o.beamR = sr / nz; o.beamG = sg / nz; o.beamB = sb / nz;
            return;
        }
        float m = Math.max(1e-4f, Math.max(sr, Math.max(sg, sb)));
        float avgLevel = sl / nz;
        o.beamR = sr / m * avgLevel; o.beamG = sg / m * avgLevel; o.beamB = sb / m * avgLevel;
        if (sl < 1e-4f) { o.beamR = 0; o.beamG = 0; o.beamB = 0; }
    }

    /** Beat position for BPM-synced effects: the analysed beat grid of the track while it plays, otherwise the show tempo. */
    static double beatPosition(Show show, Music music, boolean timelineActive, long nowMs) {
        float bpm = show.timeline.bpm > 20 ? show.timeline.bpm : 120f;
        double beatMs = 60000.0 / bpm;
        if (timelineActive && music.valid()) return (music.positionMs() - show.timeline.offsetMs - show.timeline.beatOffsetMs) / beatMs;
        return nowMs / beatMs;
    }

    /** Fade progress 0..1 (smooth S curve) of a fade of {@code len} ms at {@code t} ms. */
    static float progress(float t, float len) {
        if (len <= 0) return t >= 0 ? 1f : 0f;
        if (t <= 0) return 0f;
        if (t >= len) return 1f;
        float x = t / len;
        return x * x * (3f - 2f * x);
    }

    static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    static float clamp01(float v) {
        return Float.isNaN(v) ? 0f : v < 0 ? 0 : v > 1 ? 1 : v;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
