package com.concertstage.light.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A complete lighting show of the CSC LUMICORE X1: patch (fixtures), groups, presets (colour / position / beam palettes), saved colours,
 * cue lists with their cues and effects, the music timeline and the AutoShow settings. Pure data, serialised in a compact versioned
 * binary format ({@link #toBytes()} / {@link #fromBytes(byte[])}); older formats are migrated on reading.
 */
public final class Show {
    /**
     * Format of the bytes. 1 = 1.2.0, 2 = 1.2.0 External Show Control (DMX mode and local transforms of each fixture, Virtual Matrix
     * settings). Increase it when a field is added, and read the old layout in {@link #read}.
     */
    public static final int FORMAT = 12;         // 11 (1.2.1): free pose of the fixtures; 12 (1.2.1): old DMX modes moved to the current ones
    // was 10:         // 1.2.0: RGB in every DMX mode (Compact / Ultra saved before keep theirs)
    // was 9:          // 1.2.0 beta 2b: Full of the pixel and zone fixtures as a pixel mode (FULL_M before)
    // was 8:          // 1.2.0 beta 2: tilt of the bars / strobes centred on straight, gobo rotation in Compact
    // was 7:          // rev. 1.3.0-2: tilt of the moving heads centred on the straight position (migrateTilt)          // rev. 21: Full / Compact of the zone fixtures (DmxMode.migrateRev21); rev. 24: Compact in two instances (migrateRev24)
    private static final int MAGIC = 0x43534353;          // "CSCS"
    public static final int MAX_FIXTURES = 1024, MAX_GROUPS = 128, MAX_PRESETS = 256, MAX_LISTS = 64, MAX_CUES = 512, MAX_EFFECTS = 64,
        MAX_EVENTS = 8192, MAX_MARKERS = 512, MAX_SECTIONS = 512, MAX_COLORS = 48, MAX_NAME = 64;

    /** Edit counter (not saved): the engine rebuilds its tracking cache when it changes. */
    public int edits;
    public String id = "";
    public String name = "Show";
    public long created, modified;
    public final List<Patch> patch = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<Preset> presets = new ArrayList<>();
    public final List<Integer> savedColors = new ArrayList<>();
    public final List<CueList> lists = new ArrayList<>();
    public final Timeline timeline = new Timeline();
    public final AutoSettings auto = new AutoSettings();
    public final External external = new External();

    // ------------------------------------------------------------ model
    /** One patched fixture. {@code fid} is unique in the show; universe / address are the logical control address. */
    public static final class Patch {
        public int fid;
        public String type = FixtureProfile.VORTEX_X1.id;
        public String name = "";
        public int universe = 0, address = 1;            // rev. 16h: universe 0 is a normal universe (Art-Net 0:0:0)
        /** Block of the fixture in the world (for positions and the AutoShow geometry). */
        public int x, y, z;
        /** Yaw of the base (degrees, 0 = facing north) and mount: 0 standing, 1 hung under a truss. */
        public float yaw;
        public int mount;
        /**
         * External Show Control: DMX mode ({@code DmxMode.FULL / COMPACT / ULTRA / MATRIX}). Several fixtures of the same type and mode
         * on the same universe and address share it (they all get the same values).
         */
        public int dmxMode;
        /** Local transforms applied after the DMX values (they give no extra DMX control): pan / tilt inverted, offsets in degrees. */
        public boolean invertPan, invertTilt;
        public float panOffset, tiltOffset;
        /**
         * 1.2.1, free placement: offset of the base from the centre of its block (blocks) and its pitch / roll (degrees), applied
         * after the yaw (which includes the fine yaw): world = translate(block + offset) * rotY(yaw) * rotX(pitch) * rotZ(roll) *
         * (hung ? rotZ(180)) * local. All 0 = the placement before 1.2.1.
         */
        public float ox, oy, oz, pitch, roll;
        /** 1.2.1: gel of a conventional (RGB, 0xFFFFFF = open white). */
        public int gel = 0xFFFFFF;

        public boolean posed() {
            return ox != 0 || oy != 0 || oz != 0 || pitch != 0 || roll != 0;
        }

        public FixtureProfile profile() {
            FixtureProfile p = FixtureProfile.get(type);
            return p != null ? p : FixtureProfile.VORTEX_X1;
        }

        /** Number of DMX channels this fixture takes in its universe (its DMX mode). */
        public int footprint() {
            return com.concertstage.light.dmx.DmxMode.of(profile(), dmxMode).footprint();
        }
    }

    public static final class Group {
        public int id;
        public String name = "";
        public int color = 0xFFB07CFF;
        public int[] fids = new int[0];
    }

    /** Palette: a value for every fixture (generic) and / or per fixture (positions). Kind: 0 colour, 1 position, 2 beam, 3 all. */
    public static final class Preset {
        public static final int COLOR = 0, POSITION = 1, BEAM = 2, ALL = 3;
        public int id, kind;
        public String name = "";
        public int swatch = 0xFFFFFFFF;
        public Look generic = new Look();
        public final Map<Integer, Look> perFixture = new LinkedHashMap<>();

        /** The values for fixture {@code fid} (its own if stored, otherwise the generic ones). */
        public Look forFixture(int fid) {
            Look l = perFixture.get(fid);
            return l != null ? l : generic;
        }
    }

    /** Effect run by a cue or by the programmer. Rates are cycles per second, or beats per cycle when {@code bpmSync}. */
    public static final class Effect {
        public static final int CIRCLE = 0, WAVE = 1, FAN = 2, CHASE = 3, PULSE = 4, SWEEP = 5, DIMMER_CHASE = 6, COLOR_CHASE = 7, PIXEL = 8;
        /** Rev. 24: more effects (desk and AutoShow). Appended: the numbers of the effects above do not change. */
        public static final int FIGURE8 = 9, TILT_WAVE = 10, BALLYHOO = 11, RAINBOW = 12, SPARKLE = 13, RANDOM_STROBE = 14, ZOOM_PULSE = 15, ALTERNATE = 16;
        public static final String[] NAMES = {"Circle", "Wave", "Fan", "Chase", "Pulse", "Sweep", "Dimmer Chase", "Color Chase", "Pixel",
            "Figure 8", "Tilt Wave", "Ballyhoo", "Rainbow", "Sparkle", "Random Strobe", "Zoom Pulse", "Alternate"};
        /** Every effect enabled (AutoShow effect mask). */
        public static final int ALL_MASK = (1 << NAMES.length) - 1;
        public int type;
        public int[] fids = new int[0];
        public float rate = 0.5f;
        public boolean bpmSync;
        /** Size of the movement (0..1 of half the mechanical range for position effects, depth of the modulation otherwise). */
        public float amplitude = 0.3f;
        /** Phase spread over the fixtures, 0..1 of a cycle (0 = all together, 1 = evenly spread). */
        public float spread = 1f;
        public int direction = 1;
        /** Mirror the phase from the middle of the fixture list (symmetric fans and waves). */
        public boolean symmetric;
        /** On-time of chases / pulses, 0..1. */
        public float duty = 0.5f;
        /** Fade-in of the effect itself when its cue starts, seconds. */
        public float fade = 1f;
        /** Colours of a colour chase (0xRRGGBB). */
        public int[] colors = new int[0];

        public Effect copy() {
            Effect e = new Effect();
            e.type = type; e.fids = fids.clone(); e.rate = rate; e.bpmSync = bpmSync; e.amplitude = amplitude; e.spread = spread;
            e.direction = direction; e.symmetric = symmetric; e.duty = duty; e.fade = fade; e.colors = colors.clone();
            return e;
        }
    }

    public static final class Cue {
        public static final int TRIGGER_GO = 0, TRIGGER_FOLLOW = 1, TRIGGER_TIME = 2;
        public float number = 1;
        public String name = "";
        public float fadeIn = 2f, fadeOut = 2f, delay = 0f;
        /** Seconds after the end of the fade before the next cue starts by itself; negative = waits for GO. */
        public float follow = -1f;
        public int trigger = TRIGGER_GO;
        /** Priority of this cue's values (-1 = the cue list's). */
        public int priority = -1;
        public final Map<Integer, Look> looks = new LinkedHashMap<>();
        public final List<Effect> effects = new ArrayList<>();

        /** Total time of the transition: delay plus the longest fade. */
        public float duration() {
            return delay + Math.max(fadeIn, fadeOut);
        }
    }

    public static final class CueList {
        public int id;
        public String name = "";
        public int priority = 50;
        public boolean loop;
        public final List<Cue> cues = new ArrayList<>();

        /** Index of the cue with that number, or -1. */
        public int indexOf(float number) {
            for (int i = 0; i < cues.size(); i++) if (Math.abs(cues.get(i).number - number) < 1e-4f) return i;
            return -1;
        }

        public void sort() {
            cues.sort((a, b) -> Float.compare(a.number, b.number));
        }
    }

    /** Light event at a time of the track: GO a cue of a list, stop a list, or a short accent (flash) of a group. */
    public static final class Event {
        public static final int GO = 0, STOP = 1, ACCENT = 2;
        public long ms;
        public int kind;
        public int list;
        public float cue;
        /** ACCENT: group id (-1 = everything), strength 0..1, length ms. */
        public int group = -1;
        public float strength = 1f;
        public int lengthMs = 150;
    }

    public static final class Marker {
        public long ms;
        public String label = "";
        public int color = 0xFFF2B33D;
    }

    /** Music section shown on the timeline (filled by the analysis; the player may rename it). */
    public static final class Section {
        public long startMs, endMs;
        public String label = "";
        /** 0 intro, 1 verse, 2 build, 3 chorus, 4 drop, 5 break, 6 outro, 7 silence. */
        public int kind;
        public float energy, confidence;
        public int motif;
    }

    public static final class Timeline {
        public String trackSha1 = "", trackName = "";
        public float bpm;
        public long beatOffsetMs;
        public int beatsPerBar = 4;
        /** Lights later (positive) or earlier than the music, for the audio buffering of the clients. */
        public int offsetMs;
        public final List<Event> events = new ArrayList<>();
        public final List<Marker> markers = new ArrayList<>();
        public final List<Section> sections = new ArrayList<>();

        public void sort() {
            events.sort((a, b) -> Long.compare(a.ms, b.ms));
            markers.sort((a, b) -> Long.compare(a.ms, b.ms));
            sections.sort((a, b) -> Long.compare(a.startMs, b.startMs));
        }
    }

    /** External Show Control settings of the show (Virtual Matrix). */
    public static final class External {
        public static final int ORDER_FID = 0, ORDER_X = 1, ORDER_Z = 2;
        /** Address of the 16 channels of the CSC VIRTUAL MATRIX (universe 0..63999, address 1..497). */
        public int matrixUniverse = 0, matrixAddress = 497;
        /** Order of the fixtures along the matrix (phase, chases, gradients): fixture number, or their position east-west / north-south. */
        public int matrixOrder = ORDER_X;
    }

    public static final class AutoSettings {
        public static final int POP = 0, EDM = 1, ROCK = 2, AMBIENT = 3, CUSTOM = 4;
        public static final String[] STYLES = {"Concert Pop", "EDM Festival", "Rock Live", "Ambient", "Custom"};
        public int style = POP;
        /** 0 = pre-analysis (whole track, editable timeline), 1 = live analysis during playback. */
        public int mode;
        public float intensity = 0.8f, movement = 0.6f, colorVariety = 0.5f, effectAmount = 0.6f;
        public boolean strobes = true;
        /** Hard cap of the strobe rate written by the AutoShow, Hz (the console safety cap applies on top). */
        public float strobeMaxHz = 8f;
        /** Bits of effect types the AutoShow may use (see {@link Effect}). */
        public int effectMask = Effect.ALL_MASK;
        public final List<Integer> favoriteColors = new ArrayList<>();
    }

    // ------------------------------------------------------------ helpers
    public Patch fixture(int fid) {
        for (Patch p : patch) if (p.fid == fid) return p;
        return null;
    }

    public int nextFid() {
        int max = 0;
        for (Patch p : patch) max = Math.max(max, p.fid);
        return max + 1;
    }

    public Group group(int id) {
        for (Group g : groups) if (g.id == id) return g;
        return null;
    }

    public Preset preset(int id) {
        for (Preset p : presets) if (p.id == id) return p;
        return null;
    }

    public CueList list(int id) {
        for (CueList l : lists) if (l.id == id) return l;
        return null;
    }

    public int nextGroupId() {
        int max = 0;
        for (Group g : groups) max = Math.max(max, g.id);
        return max + 1;
    }

    public int nextPresetId() {
        int max = 0;
        for (Preset p : presets) max = Math.max(max, p.id);
        return max + 1;
    }

    public int nextListId() {
        int max = 0;
        for (CueList l : lists) max = Math.max(max, l.id);
        return max + 1;
    }

    /** Next free address in a universe after the fixtures already patched there (auto patch). */
    /** Rev. 25: the mode a new fixture is patched in: Full, except the blinders, on one channel (their dimmer). */
    public static int defaultMode(FixtureProfile p) {
        return com.concertstage.light.dmx.DmxMode.tungsten(p) ? com.concertstage.light.dmx.DmxMode.ULTRA : com.concertstage.light.dmx.DmxMode.FULL;
    }

    public int[] nextAddress(FixtureProfile p) {
        int foot = com.concertstage.light.dmx.DmxMode.of(p, defaultMode(p)).footprint();
        // rev. 16h: from the lowest universe the show already uses (0 for a new show: universe 0 is a normal universe)
        int first = Integer.MAX_VALUE;
        for (Patch q : patch) if (q.dmxMode != com.concertstage.light.dmx.DmxMode.MATRIX) first = Math.min(first, q.universe);
        if (first == Integer.MAX_VALUE) first = com.concertstage.light.dmx.ArtNetSettings.MIN_UNIVERSE;
        for (int u = first; u <= com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE; u++) {
            int end = 0;
            for (Patch q : patch) if (q.universe == u) end = Math.max(end, q.address + q.footprint() - 1);
            if (end + foot <= 512) return new int[] {u, end + 1};
        }
        return new int[] {com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE, 1};
    }

    public Show copy() {
        return fromBytes(toBytes());
    }

    // ------------------------------------------------------------ codec
    public byte[] toBytes() {
        Bytes.Writer w = new Bytes.Writer();
        w.i32(MAGIC).varInt(FORMAT);
        write(w);
        return w.toBytes();
    }

    /** Decodes a show; null if the bytes are not a show or are damaged. */
    public static Show fromBytes(byte[] b) {
        if (b == null || b.length < 5) return null;
        Bytes.Reader r = new Bytes.Reader(b);
        if (r.i32() != MAGIC) return null;
        int format = r.varInt();
        if (format < 1 || format > FORMAT) return null;
        Show s = read(r, format);
        return r.ok() ? s : null;
    }

    private void write(Bytes.Writer w) {
        w.str(id).str(name).varLong(created).varLong(modified);
        w.varInt(patch.size());
        for (Patch p : patch) {
            w.varInt(p.fid).str(p.type).str(p.name).varInt(p.universe).varInt(p.address).sInt(p.x).sInt(p.y).sInt(p.z).f32(p.yaw).varInt(p.mount);
        }
        w.varInt(groups.size());
        for (Group g : groups) {
            w.varInt(g.id).str(g.name).i32(g.color).varInt(g.fids.length);
            for (int f : g.fids) w.varInt(f);
        }
        w.varInt(presets.size());
        for (Preset p : presets) {
            w.varInt(p.id).varInt(p.kind).str(p.name).i32(p.swatch);
            p.generic.write(w);
            w.varInt(p.perFixture.size());
            for (Map.Entry<Integer, Look> e : p.perFixture.entrySet()) { w.varInt(e.getKey()); e.getValue().write(w); }
        }
        w.varInt(savedColors.size());
        for (int c : savedColors) w.varInt(c & 0xFFFFFF);
        w.varInt(lists.size());
        for (CueList l : lists) {
            w.varInt(l.id).str(l.name).varInt(l.priority).bool(l.loop).varInt(l.cues.size());
            for (Cue c : l.cues) writeCue(w, c);
        }
        Timeline t = timeline;
        w.str(t.trackSha1).str(t.trackName).f32(t.bpm).sLong(t.beatOffsetMs).varInt(t.beatsPerBar).sInt(t.offsetMs);
        w.varInt(t.events.size());
        for (Event e : t.events) {
            w.varLong(e.ms).varInt(e.kind).varInt(e.list).f32(e.cue).sInt(e.group).unit(e.strength).varInt(e.lengthMs);
        }
        w.varInt(t.markers.size());
        for (Marker m : t.markers) w.varLong(m.ms).str(m.label).i32(m.color);
        w.varInt(t.sections.size());
        for (Section s : t.sections) w.varLong(s.startMs).varLong(s.endMs).str(s.label).varInt(s.kind).unit(s.energy).unit(s.confidence).varInt(s.motif);
        AutoSettings a = auto;
        w.varInt(a.style).varInt(a.mode).unit(a.intensity).unit(a.movement).unit(a.colorVariety).unit(a.effectAmount).bool(a.strobes)
            .f32(a.strobeMaxHz).varInt(a.effectMask).varInt(a.favoriteColors.size());
        for (int c : a.favoriteColors) w.varInt(c & 0xFFFFFF);
        // format 2
        w.varInt(patch.size());
        for (Patch p : patch) w.varInt(p.fid).varInt(p.dmxMode).bool(p.invertPan).bool(p.invertTilt).f32(p.panOffset).f32(p.tiltOffset);
        w.varInt(external.matrixUniverse).varInt(external.matrixAddress).varInt(external.matrixOrder);
        // format 11 (1.2.1): the free poses
        int posed = 0;
        for (Patch p : patch) if (p.posed()) posed++;
        w.varInt(posed);
        for (Patch p : patch) if (p.posed()) w.varInt(p.fid).f32(p.ox).f32(p.oy).f32(p.oz).f32(p.pitch).f32(p.roll);
        // format 12 (1.2.1): the gels of the conventionals
        int gels = 0;
        for (Patch p : patch) if (p.gel != 0xFFFFFF) gels++;
        w.varInt(gels);
        for (Patch p : patch) if (p.gel != 0xFFFFFF) w.varInt(p.fid).varInt(p.gel & 0xFFFFFF);
    }

    static void writeCue(Bytes.Writer w, Cue c) {
        w.f32(c.number).str(c.name).f32(c.fadeIn).f32(c.fadeOut).f32(c.delay).f32(c.follow).varInt(c.trigger).sInt(c.priority);
        w.varInt(c.looks.size());
        for (Map.Entry<Integer, Look> e : c.looks.entrySet()) { w.varInt(e.getKey()); e.getValue().write(w); }
        w.varInt(c.effects.size());
        for (Effect e : c.effects) writeEffect(w, e);
    }

    static void writeEffect(Bytes.Writer w, Effect e) {
        w.varInt(e.type).varInt(e.fids.length);
        for (int f : e.fids) w.varInt(f);
        w.f32(e.rate).bool(e.bpmSync).unit(e.amplitude).unit(e.spread).sInt(e.direction).bool(e.symmetric).unit(e.duty).f32(e.fade);
        w.varInt(e.colors.length);
        for (int c : e.colors) w.varInt(c & 0xFFFFFF);
    }

    private static Show read(Bytes.Reader r, int format) {
        Show s = new Show();
        s.id = r.str(64);
        s.name = r.str(MAX_NAME * 4);
        s.created = r.varLong();
        s.modified = r.varLong();
        int n = r.count(MAX_FIXTURES);
        for (int i = 0; i < n && r.ok(); i++) {
            Patch p = new Patch();
            p.fid = r.varInt(); p.type = r.str(64); p.name = r.str(MAX_NAME * 4); p.universe = r.varInt(); p.address = r.varInt();
            p.x = r.sInt(); p.y = r.sInt(); p.z = r.sInt(); p.yaw = r.f32(); p.mount = r.varInt();
            s.patch.add(p);
        }
        n = r.count(MAX_GROUPS);
        for (int i = 0; i < n && r.ok(); i++) {
            Group g = new Group();
            g.id = r.varInt(); g.name = r.str(MAX_NAME * 4); g.color = r.i32();
            int k = r.count(MAX_FIXTURES);
            g.fids = new int[k];
            for (int j = 0; j < k; j++) g.fids[j] = r.varInt();
            s.groups.add(g);
        }
        n = r.count(MAX_PRESETS);
        for (int i = 0; i < n && r.ok(); i++) {
            Preset p = new Preset();
            p.id = r.varInt(); p.kind = r.varInt(); p.name = r.str(MAX_NAME * 4); p.swatch = r.i32();
            p.generic = Look.read(r);
            int k = r.count(MAX_FIXTURES);
            for (int j = 0; j < k && r.ok(); j++) p.perFixture.put(r.varInt(), Look.read(r));
            s.presets.add(p);
        }
        n = r.count(MAX_COLORS);
        for (int i = 0; i < n; i++) s.savedColors.add(r.varInt());
        n = r.count(MAX_LISTS);
        for (int i = 0; i < n && r.ok(); i++) {
            CueList l = new CueList();
            l.id = r.varInt(); l.name = r.str(MAX_NAME * 4); l.priority = r.varInt(); l.loop = r.bool();
            int k = r.count(MAX_CUES);
            for (int j = 0; j < k && r.ok(); j++) l.cues.add(readCue(r));
            s.lists.add(l);
        }
        Timeline t = s.timeline;
        t.trackSha1 = r.str(64); t.trackName = r.str(1024); t.bpm = r.f32(); t.beatOffsetMs = r.sLong(); t.beatsPerBar = Math.max(1, r.varInt());
        t.offsetMs = r.sInt();
        n = r.count(MAX_EVENTS);
        for (int i = 0; i < n && r.ok(); i++) {
            Event e = new Event();
            e.ms = r.varLong(); e.kind = r.varInt(); e.list = r.varInt(); e.cue = r.f32(); e.group = r.sInt(); e.strength = r.unit(); e.lengthMs = r.varInt();
            if (Float.isNaN(e.strength)) e.strength = 1f;
            t.events.add(e);
        }
        n = r.count(MAX_MARKERS);
        for (int i = 0; i < n && r.ok(); i++) {
            Marker m = new Marker();
            m.ms = r.varLong(); m.label = r.str(MAX_NAME * 4); m.color = r.i32();
            t.markers.add(m);
        }
        n = r.count(MAX_SECTIONS);
        for (int i = 0; i < n && r.ok(); i++) {
            Section x = new Section();
            x.startMs = r.varLong(); x.endMs = r.varLong(); x.label = r.str(MAX_NAME * 4); x.kind = r.varInt(); x.energy = r.unit();
            x.confidence = r.unit(); x.motif = r.varInt();
            t.sections.add(x);
        }
        AutoSettings a = s.auto;
        a.style = r.varInt(); a.mode = r.varInt(); a.intensity = r.unit(); a.movement = r.unit(); a.colorVariety = r.unit();
        a.effectAmount = r.unit(); a.strobes = r.bool(); a.strobeMaxHz = r.f32(); a.effectMask = r.varInt() & Effect.ALL_MASK;
        if (format < 6 && (a.effectMask & 0x1FF) == 0x1FF) a.effectMask = Effect.ALL_MASK;     // rev. 24: every effect was on: the new ones too
        n = r.count(MAX_COLORS);
        for (int i = 0; i < n; i++) a.favoriteColors.add(r.varInt());
        if (format >= 2) {
            n = r.count(MAX_FIXTURES);
            for (int i = 0; i < n && r.ok(); i++) {
                Patch p = s.fixture(r.varInt());
                int mode = r.varInt();
                boolean ip = r.bool(), it = r.bool();
                float po = r.f32(), to = r.f32();
                if (p == null) continue;
                p.dmxMode = Math.max(0, Math.min(com.concertstage.light.dmx.DmxMode.LAST, mode));
                // format 5 (rev. 21): the PULSESTRIKE M and the FLASHCORE FX got three light zones in Full and Compact. A show saved
                // before keeps their former layouts (FULL_V1 / COMPACT_V1): the desk that drives them is not reinterpreted
                if (format < 5) p.dmxMode = com.concertstage.light.dmx.DmxMode.migrateRev21(p.profile(), p.dmxMode);
                // format 6 (rev. 24): Compact of the zone fixtures in two instances, Ultra Compact of the blinders in one channel
                if (format < 6) p.dmxMode = com.concertstage.light.dmx.DmxMode.migrateRev24(p.profile(), p.dmxMode);
                // format 8 (1.2.0 beta 2): the Compact of the gobo fixtures got a gobo rotation channel; a show saved before keeps 8 channels
                if (format < 8 && p.profile() != null) p.dmxMode = com.concertstage.light.dmx.DmxMode.migrateGoboRot(p.profile(), p.dmxMode);
                // format 9 (1.2.0 beta 2b): the Full of the pixel and zone fixtures became a pixel mode; a show saved before keeps the
                // master dimmer layout its desk was set up for
                if (format < 9 && p.profile() != null) p.dmxMode = com.concertstage.light.dmx.DmxMode.migratePixelFull(p.profile(), p.dmxMode);
                // format 4 (rev. 19): the Ultra Compact of the gobo fixtures got a strobe and a gobo channel (6 channels instead of 4).
                // A show saved before keeps the 4-channel layout its desk was set up for (no address moves, no overlap); the auto patch
                // or the patch screen moves it to the new one
                if (format < 4 && p.dmxMode == com.concertstage.light.dmx.DmxMode.ULTRA && com.concertstage.light.dmx.DmxMode.hasGoboWheel(p.profile()))
                    p.dmxMode = com.concertstage.light.dmx.DmxMode.ULTRA_V1;
                // format 10 (1.2.0, after format 4: an ULTRA_V1 stays): the colour in RGB in every mode; a show saved before keeps its hue / saturation, colour macro
                if (format < 10 && p.profile() != null) p.dmxMode = com.concertstage.light.dmx.DmxMode.migrateRgb(p.profile(), p.dmxMode);
                p.invertPan = ip; p.invertTilt = it;
                p.panOffset = Float.isFinite(po) ? Math.max(-360f, Math.min(360f, po)) : 0f;
                p.tiltOffset = Float.isFinite(to) ? Math.max(-360f, Math.min(360f, to)) : 0f;
            }
            s.external.matrixUniverse = Math.max(com.concertstage.light.dmx.ArtNetSettings.MIN_UNIVERSE, Math.min(com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE, r.varInt()));
            s.external.matrixAddress = Math.max(1, Math.min(497, r.varInt()));
            s.external.matrixOrder = Math.max(0, Math.min(2, r.varInt()));
        }
        if (format >= 11) {
            int n2 = r.count(MAX_FIXTURES);
            for (int i = 0; i < n2 && r.ok(); i++) {
                Patch p = s.fixture(r.varInt());
                float ox = r.f32(), oy = r.f32(), oz = r.f32(), pitch = r.f32(), roll = r.f32();
                if (p != null) setPose(p, ox, oy, oz, pitch, roll);
            }
        }
        if (format >= 12) {
            int n3 = r.count(MAX_FIXTURES);
            for (int i = 0; i < n3 && r.ok(); i++) {
                Patch p = s.fixture(r.varInt());
                int gel = r.varInt() & 0xFFFFFF;
                if (p != null) p.gel = gel;
            }
        }
        if (format < 7) s.migrateTilt();
        else if (format < 8) s.migrateTiltNoPan();
        if (format < 12) s.upgradeLegacyDmx();
        return s;
    }

    /** 1.2.1: fixtures moved from an old DMX layout to the current one when the show was loaded / kept in the old one (overlap). */
    public transient int legacyUpgraded, legacyKept;

    /**
     * 1.2.1 (15 November: "the wash is still on hue and sat in Compact but the fixture profile is RGB now"): a show saved before 1.2.0
     * kept the layouts its desk was patched with (hue / saturation in Compact, a colour macro in Ultra Compact, the Compacts and Fulls
     * of the betas), while the profiles given to the consoles are the current ones: Compact on the console, hue + saturation in the
     * game. Every fixture in an old layout moves to the current layout of the same mode, at the same address, when its larger footprint
     * still fits the universe and overlaps no other fixture; else it keeps its old layout (patch screen or auto patch to move it).
     */
    public void upgradeLegacyDmx() {
        legacyUpgraded = 0; legacyKept = 0;
        for (Patch p : patch) {
            if (!com.concertstage.light.dmx.DmxMode.legacy(p.dmxMode) || p.profile() == null) continue;
            int nm = com.concertstage.light.dmx.DmxMode.current(p.dmxMode);
            int foot = com.concertstage.light.dmx.DmxMode.of(p.profile(), nm).footprint();
            boolean clash = p.address + foot - 1 > 512;
            for (Patch q : patch) {
                if (clash) break;
                if (q == p || q.universe != p.universe || q.dmxMode == com.concertstage.light.dmx.DmxMode.MATRIX || q.profile() == null) continue;
                if (q.address == p.address && q.type.equals(p.type)) continue;      // same type at the same address: they share the channels
                int qa = q.address, qb = q.address + com.concertstage.light.dmx.DmxMode.of(q.profile(), q.dmxMode).footprint() - 1;
                if (p.address <= qb && qa <= p.address + foot - 1) clash = true;
            }
            if (clash) { legacyKept++; continue; }
            p.dmxMode = nm;
            legacyUpgraded++;
        }
    }

    /** 1.2.1: the free pose of a fixture, checked (offsets within 2 blocks, pitch within 90°, roll within 180°). */
    public static void setPose(Patch p, float ox, float oy, float oz, float pitch, float roll) {
        p.ox = clampF(ox, 2f); p.oy = clampF(oy, 2f); p.oz = clampF(oz, 2f);
        p.pitch = clampF(pitch, 90f); p.roll = clampF(roll, 180f);
    }

    private static float clampF(float v, float lim) {
        return Float.isFinite(v) ? Math.max(-lim, Math.min(lim, v)) : 0f;
    }

    /** Rev. 1.3.0-2: set when this show was read from a format before 7 (its tilts were moved, the desk moves its programmer too). */
    public transient boolean tiltMigrated;

    /**
     * Format 7: the tilt of the moving heads is centred on the straight position instead of the horizontal (FixtureProfile.tiltZero).
     * Every stored tilt keeps the same physical angle: value - tiltZero / tiltRange (a third of the range for the 270-degree heads).
     * Generic looks (no fixture) take the shift of the 270-degree heads.
     */
    void migrateTilt() {
        java.util.Map<Integer, FixtureProfile> types = new java.util.HashMap<>();
        for (Patch p : patch) types.put(p.fid, p.profile());
        for (Preset p : presets) {
            migrateTilt(p.generic, null);
            for (Map.Entry<Integer, Look> e : p.perFixture.entrySet()) migrateTilt(e.getValue(), types.get(e.getKey()));
        }
        for (CueList l : lists) for (Cue c : l.cues) for (Map.Entry<Integer, Look> e : c.looks.entrySet()) migrateTilt(e.getValue(), types.get(e.getKey()));
        tiltMigrated = true;
    }

    /** Format 8: the motorised fixtures without pan (bars, strobes) are centred on the straight position too; generic looks stay. */
    void migrateTiltNoPan() {
        java.util.Map<Integer, FixtureProfile> types = new java.util.HashMap<>();
        for (Patch p : patch) if (!p.profile().hasPan() && p.profile().tiltRange > 0) types.put(p.fid, p.profile());
        if (types.isEmpty()) return;
        for (Preset p : presets)
            for (Map.Entry<Integer, Look> e : p.perFixture.entrySet()) if (types.containsKey(e.getKey())) migrateTilt(e.getValue(), types.get(e.getKey()));
        for (CueList l : lists) for (Cue c : l.cues) for (Map.Entry<Integer, Look> e : c.looks.entrySet())
            if (types.containsKey(e.getKey())) migrateTilt(e.getValue(), types.get(e.getKey()));
        tiltMigratedNoPan = true;
    }

    /** Rev. 4c: set when this show was read from format 7 (the tilts of its bars and strobes were moved). */
    public transient boolean tiltMigratedNoPan;

    public static void migrateTilt(Look l, FixtureProfile pr) {
        if (l == null || !l.has(Attr.TILT)) return;
        float zero = pr == null ? 90f : pr.tiltZero, range = pr == null ? 270f : pr.tiltRange;
        if (zero == 0f || range <= 0f) return;
        float v = l.get(Attr.TILT) - zero / range;
        l.set(Attr.TILT, v < 0f ? 0f : v > 1f ? 1f : v);
    }

    static Cue readCue(Bytes.Reader r) {
        Cue c = new Cue();
        c.number = r.f32(); c.name = r.str(MAX_NAME * 4); c.fadeIn = clampTime(r.f32()); c.fadeOut = clampTime(r.f32());
        c.delay = clampTime(r.f32()); c.follow = r.f32(); c.trigger = r.varInt(); c.priority = r.sInt();
        int k = r.count(MAX_FIXTURES);
        for (int j = 0; j < k && r.ok(); j++) c.looks.put(r.varInt(), Look.read(r));
        k = r.count(MAX_EFFECTS);
        for (int j = 0; j < k && r.ok(); j++) c.effects.add(readEffect(r));
        return c;
    }

    static Effect readEffect(Bytes.Reader r) {
        Effect e = new Effect();
        e.type = Math.min(Effect.NAMES.length - 1, r.varInt());
        int k = r.count(MAX_FIXTURES);
        e.fids = new int[k];
        for (int j = 0; j < k; j++) e.fids[j] = r.varInt();
        e.rate = Math.max(0.01f, Math.min(20f, r.f32())); e.bpmSync = r.bool(); e.amplitude = r.unit(); e.spread = r.unit();
        e.direction = r.sInt() < 0 ? -1 : 1; e.symmetric = r.bool(); e.duty = r.unit(); e.fade = clampTime(r.f32());
        if (Float.isNaN(e.amplitude)) e.amplitude = 0.3f;
        if (Float.isNaN(e.spread)) e.spread = 1f;
        if (Float.isNaN(e.duty)) e.duty = 0.5f;
        k = r.count(16);
        e.colors = new int[k];
        for (int j = 0; j < k; j++) e.colors[j] = r.varInt();
        return e;
    }

    static float clampTime(float f) {
        return Float.isFinite(f) ? Math.max(0f, Math.min(600f, f)) : 0f;
    }
}
