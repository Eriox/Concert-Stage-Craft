package com.concertstage.light.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live state of a LUMICORE X1 desk: the programmer, the state of every playback (cue list), the masters and the safety controls. It
 * changes with every command and is replicated on the clients (the same commands are applied there), the show itself only when it is
 * edited. Times are milliseconds of the shared game clock (game time x 50).
 */
public final class Live {
    public static final class Playback {
        /** Current cue index, -1 = not running. */
        public int index = -1;
        /** Cue the fade comes from (-1 = from nothing / home). */
        public int from = -1;
        public long goMs;
        public float level = 1f;
        public boolean paused;
        public long pausedAtMs;
        /** Flash button held: level forced to full. */
        public boolean flash;
        /** Fading out after STOP: start of the release, and the cue index it releases. */
        public long releaseMs = -1;
        public int released = -1;

        public boolean running() {
            return index >= 0;
        }

        /** Elapsed milliseconds since GO, with the pauses removed. */
        public long elapsed(long nowMs) {
            return Math.max(0, (paused ? pausedAtMs : nowMs) - goMs);
        }

        Playback copy() {
            Playback p = new Playback();
            p.index = index; p.from = from; p.goMs = goMs; p.level = level; p.paused = paused; p.pausedAtMs = pausedAtMs;
            p.flash = flash; p.releaseMs = releaseMs; p.released = released;
            return p;
        }
    }

    public final Map<Integer, Look> programmer = new LinkedHashMap<>();
    public final List<Show.Effect> programmerEffects = new ArrayList<>();
    public long programmerEffectsMs;
    /**
     * Rev. 4c (13 November: "the focus tool does not work" with dot2): pan / tilt set with the focus tool while an external desk drives
     * the fixtures, held over its DMX until the tool releases them (pan NaN = fixture without pan). Saved with the live state.
     */
    public final Map<Integer, float[]> focus = new LinkedHashMap<>();
    public final Map<Integer, Playback> playbacks = new LinkedHashMap<>();
    public float grandMaster = 1f;
    public boolean blackout;
    /** Safety: strobes forced open (no flashing at all) and the cap of the strobe rate in Hz. */
    public boolean noStrobe;
    public float strobeCapHz = 10f;
    /** Safety: pan / tilt limited to a small range around the neutral position, and moved slowly. */
    public boolean moveLimit;
    /** AutoShow allowed to run (the STOP AUTO key clears it at once). */
    public boolean autoEnabled = true;
    /** Timeline follows the linked audio console. */
    public boolean timelineArmed = true;
    /** Linked CSC audio console (block position), or none. */
    public boolean hasAudio;
    public int audioX, audioY, audioZ;
    /** Count of the commands applied (clients detect a missed one and ask for the full state). */
    public long revision;
    /**
     * External Show Control: who drives the fixtures. {@link #INTERNAL} the desk (cue lists, programmer, timeline), {@link #AUTOSHOW} the
     * desk with the AutoShow allowed, {@link #EXTERNAL} a third-party desk over Art-Net / sACN, {@link #PLAYBACK} a recorded show.
     * Only one of them at a time: switching cross-fades over {@link #CONTROL_FADE_MS} from what the previous one gave.
     */
    public static final int INTERNAL = 0, AUTOSHOW = 1, EXTERNAL = 2, PLAYBACK = 3;
    public static final String[] CONTROL_NAMES = {"Internal", "AutoShow", "External", "Playback"};
    public static final int CONTROL_FADE_MS = 1000;
    public int control = AUTOSHOW;
    public int previousControl = AUTOSHOW;
    public long controlMs = Long.MIN_VALUE / 4;

    /** True when the fixtures take their values from DMX (live or recorded). */
    public static boolean dmxDriven(int control) {
        return control == EXTERNAL || control == PLAYBACK;
    }

    public Playback playback(int list) {
        return playbacks.computeIfAbsent(list, k -> new Playback());
    }

    public Live copy() {
        return fromBytes(toBytes());
    }

    // ------------------------------------------------------------ codec
    public byte[] toBytes() {
        Bytes.Writer w = new Bytes.Writer();
        w.varInt(3);
        w.varInt(programmer.size());
        for (Map.Entry<Integer, Look> e : programmer.entrySet()) { w.varInt(e.getKey()); e.getValue().write(w); }
        w.varInt(programmerEffects.size());
        for (Show.Effect e : programmerEffects) Show.writeEffect(w, e);
        w.varLong(programmerEffectsMs);
        w.varInt(playbacks.size());
        for (Map.Entry<Integer, Playback> e : playbacks.entrySet()) {
            Playback p = e.getValue();
            w.varInt(e.getKey()).sInt(p.index).sInt(p.from).sLong(p.goMs).unit(p.level).bool(p.paused).sLong(p.pausedAtMs).bool(p.flash)
                .sLong(p.releaseMs).sInt(p.released);
        }
        w.unit(grandMaster).bool(blackout).bool(noStrobe).f32(strobeCapHz).bool(moveLimit).bool(autoEnabled).bool(timelineArmed)
            .bool(hasAudio).sInt(audioX).sInt(audioY).sInt(audioZ).varLong(revision);
        w.varInt(control).varInt(previousControl).sLong(controlMs);
        w.varInt(focus.size());
        for (Map.Entry<Integer, float[]> e : focus.entrySet()) w.varInt(e.getKey()).f32(e.getValue()[0]).f32(e.getValue()[1]);
        return w.toBytes();
    }

    public static Live fromBytes(byte[] b) {
        Live l = new Live();
        if (b == null || b.length == 0) return l;
        Bytes.Reader r = new Bytes.Reader(b);
        int version = r.varInt();
        if (version < 1 || version > 3) return l;
        int n = r.count(Show.MAX_FIXTURES);
        for (int i = 0; i < n && r.ok(); i++) l.programmer.put(r.varInt(), Look.read(r));
        n = r.count(Show.MAX_EFFECTS);
        for (int i = 0; i < n && r.ok(); i++) l.programmerEffects.add(Show.readEffect(r));
        l.programmerEffectsMs = r.varLong();
        n = r.count(Show.MAX_LISTS);
        for (int i = 0; i < n && r.ok(); i++) {
            int id = r.varInt();
            Playback p = new Playback();
            p.index = r.sInt(); p.from = r.sInt(); p.goMs = r.sLong(); p.level = r.unit(); p.paused = r.bool(); p.pausedAtMs = r.sLong();
            p.flash = r.bool(); p.releaseMs = r.sLong(); p.released = r.sInt();
            if (Float.isNaN(p.level)) p.level = 1f;
            l.playbacks.put(id, p);
        }
        l.grandMaster = r.unit(); l.blackout = r.bool(); l.noStrobe = r.bool(); l.strobeCapHz = r.f32(); l.moveLimit = r.bool();
        l.autoEnabled = r.bool(); l.timelineArmed = r.bool(); l.hasAudio = r.bool(); l.audioX = r.sInt(); l.audioY = r.sInt(); l.audioZ = r.sInt();
        l.revision = r.varLong();
        if (version >= 2) {
            l.control = Math.max(0, Math.min(3, r.varInt()));
            l.previousControl = Math.max(0, Math.min(3, r.varInt()));
            l.controlMs = r.sLong();
        }
        if (version >= 3) {
            n = r.count(Show.MAX_FIXTURES);
            for (int i = 0; i < n && r.ok(); i++) l.focus.put(r.varInt(), new float[] {r.f32(), r.f32()});
        }
        if (Float.isNaN(l.grandMaster)) l.grandMaster = 1f;
        l.strobeCapHz = Math.max(0f, Math.min(25f, l.strobeCapHz));
        return r.ok() ? l : new Live();
    }
}
