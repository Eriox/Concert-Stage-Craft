package com.concertstage.light.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every action of the LUMICORE X1 is a command: a few bytes (op code + arguments). The server checks the permission, applies it to its
 * show and live state, then sends the very same bytes (with its revision and clock) to the clients, which apply them to their copy.
 * Being deterministic, both copies stay identical; a client that misses a revision asks for the full state.
 *
 * <p>{@link #apply} validates every argument (ranges, list sizes, existing ids) and returns what changed, or {@link #REJECTED}.
 */
public final class Commands {
    private Commands() {}

    public static final int REJECTED = 0, LIVE = 1, SHOW = 2;

    // programmer
    public static final int P_SET = 1, P_RGB = 2, P_ZONE = 3, P_CLEAR = 4, P_CLEAR_FAMILY = 5, P_PRESET = 6, P_AIM = 7, P_HOME = 8,
        P_EFFECT_ADD = 9, P_EFFECT_CLEAR = 10, P_ZONES_FILL = 11,
        P_FOCUS = 12, P_FOCUS_CLEAR = 13;                     // rev. 4c: focus tool over an external desk (Live.focus)
    // recording
    public static final int STORE_CUE = 20, UPDATE_CUE = 21, CUE_EDIT = 22, CUE_DELETE = 23, LIST_NEW = 24, LIST_EDIT = 25, LIST_DELETE = 26,
        STORE_PRESET = 27, PRESET_DELETE = 28, PRESET_RENAME = 29, GROUP_STORE = 30, GROUP_DELETE = 31, GROUP_RENAME = 32, GROUP_SET = 33,
        SAVE_COLOR = 34, DELETE_COLOR = 35, CUE_COPY = 36, EFFECT_EDIT = 37, EFFECT_DELETE = 38;
    // playback
    public static final int GO = 40, BACK = 41, PAUSE = 42, STOP = 43, GOTO = 44, FADER = 45, FLASH = 46, STOP_ALL = 47;
    // masters and safety
    public static final int GRAND_MASTER = 50, BLACKOUT = 51, NO_STROBE = 52, STROBE_CAP = 53, MOVE_LIMIT = 54, AUTO_ENABLE = 55,
        TIMELINE_ARM = 56, HOME_ALL = 57, CONTROL_MODE = 58;
    // show, patch, timeline
    public static final int TL_EVENT_ADD = 60, TL_EVENT_DELETE = 61, TL_EVENT_MOVE = 62, TL_MARKER_ADD = 63, TL_MARKER_DELETE = 64,
        TL_TRACK = 65, TL_OFFSET = 66, TL_TEMPO = 67, TL_CLEAR = 68, TL_SECTION_RENAME = 69,
        AUTO_SETTINGS = 70, PATCH_ADD = 71, PATCH_EDIT = 72, PATCH_REMOVE = 73, PATCH_MOVE = 74, SHOW_NAME = 75, SHOW_REPLACE = 76,
        LINK_AUDIO = 77, POSITIONS = 78, PATCH_DMX = 79, PATCH_BULK = 80, MATRIX_SETTINGS = 81,
        PATCH_POSE = 82,                                                          // 1.2.1: free pose of a fixture
        PATCH_GEL = 83;                                                           // 1.2.1: gel of a conventional

    /** Ops that change the show (they are saved in the undo history of the desk). */
    public static boolean editsShow(int op) {
        return op >= STORE_CUE && op <= EFFECT_DELETE || op >= TL_EVENT_ADD && op <= PATCH_GEL && op != LINK_AUDIO;
    }

    /** Ops that drive the show (playback, masters, safety): operators with the "playback only" permission may send them. */
    public static boolean playbackOnly(int op) {
        return op >= GO && op <= CONTROL_MODE;
    }

    // ------------------------------------------------------------ building (client side)
    public static Bytes.Writer op(int op) {
        return new Bytes.Writer().varInt(op);
    }

    public static void fids(Bytes.Writer w, int[] fids) {
        w.varInt(fids.length);
        for (int f : fids) w.varInt(f);
    }

    // ------------------------------------------------------------ applying (server and clients)
    /**
     * Applies one command. {@code nowMs} is the server clock of the command (clients pass the value the server sent), {@code stage}
     * the geometry used by AIM / POSITIONS (null: those are rejected).
     */
    public static int apply(Show show, Live live, byte[] cmd, long nowMs, Movement.Stage stage) {
        Bytes.Reader r = new Bytes.Reader(cmd);
        int op = r.varInt();
        int result;
        try {
            result = run(show, live, op, r, nowMs, stage);
        } catch (RuntimeException e) {
            return REJECTED;
        }
        if (!r.ok()) return REJECTED;
        if ((result & SHOW) != 0) { show.edits++; show.modified = Math.max(show.modified, nowMs); }
        if (result != REJECTED) live.revision++;
        return result;
    }

    private static int[] readFids(Bytes.Reader r, Show show) {
        int n = r.count(Show.MAX_FIXTURES);
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int f = r.varInt();
            if (show.fixture(f) != null) out[k++] = f;
        }
        return Arrays.copyOf(out, k);
    }

    private static float unit(float f) {
        return Float.isNaN(f) ? Float.NaN : Math.max(0f, Math.min(1f, f));
    }

    private static int run(Show show, Live live, int op, Bytes.Reader r, long now, Movement.Stage stage) {
        switch (op) {
            // ---------------------------------------------------------------- programmer
            case P_SET -> {
                int[] f = readFids(r, show);
                Attr a = Attr.byId(r.varInt());
                float v = r.unit();
                if (a == null) return REJECTED;
                for (int fid : f) {
                    if (!show.fixture(fid).profile().has(a)) continue;
                    Look l = live.programmer.computeIfAbsent(fid, k -> new Look());
                    if (Float.isNaN(v)) l.clear(a); else l.set(a, v);
                }
                cleanProgrammer(live);
                return LIVE;
            }
            case P_RGB -> {
                int[] f = readFids(r, show);
                float red = r.unit(), green = r.unit(), blue = r.unit(), white = r.unit();
                for (int fid : f) {
                    FixtureProfile pr = show.fixture(fid).profile();
                    Look l = live.programmer.computeIfAbsent(fid, k -> new Look());
                    l.set(Attr.RED, red).set(Attr.GREEN, green).set(Attr.BLUE, blue);
                    if (pr.has(Attr.WHITE) && !Float.isNaN(white)) l.set(Attr.WHITE, white);
                    if (pr.zones > 0 && l.zones != null) for (int z = 0; z < pr.zones; z++) {
                        if (pr.zoneKind(z) == 1) continue;
                        l.zones[z * 4] = Float.NaN; l.zones[z * 4 + 1] = Float.NaN; l.zones[z * 4 + 2] = Float.NaN;
                    }
                }
                return LIVE;
            }
            case P_ZONE -> {
                int fid = r.varInt();
                int z = r.varInt();
                float zr = r.unit(), zg = r.unit(), zb = r.unit(), zl = r.unit();
                Show.Patch p = show.fixture(fid);
                if (p == null || z < 0 || z >= p.profile().zones) return REJECTED;
                live.programmer.computeIfAbsent(fid, k -> new Look()).setZone(z, p.profile().zones, zr, zg, zb, zl);
                return LIVE;
            }
            case P_ZONES_FILL -> {
                // pixel pattern on every zone of the fixtures: mode 0 all, 1 odd / even, 2 gradient left-right (level), 3 clear zones
                int[] f = readFids(r, show);
                int mode = r.varInt();
                float zr = r.unit(), zg = r.unit(), zb = r.unit();
                for (int fid : f) {
                    FixtureProfile pr = show.fixture(fid).profile();
                    if (pr.zones == 0) continue;
                    Look l = live.programmer.computeIfAbsent(fid, k -> new Look());
                    for (int z = 0; z < pr.zones; z++) {
                        if (mode == 3) { if (l.zones != null) Arrays.fill(l.zones, Float.NaN); break; }
                        float lvl = mode == 1 ? (z % 2 == 0 ? 1f : 0f) : mode == 2 ? (pr.zones <= 1 ? 1f : z / (float) (pr.zones - 1)) : 1f;
                        boolean white = pr.zoneKind(z) == 1;
                        l.setZone(z, pr.zones, white ? Float.NaN : zr, white ? Float.NaN : zg, white ? Float.NaN : zb, lvl);
                    }
                }
                return LIVE;
            }
            case P_CLEAR -> {
                int[] f = readFids(r, show);
                boolean effects = r.bool();
                if (f.length == 0) live.programmer.clear(); else for (int fid : f) live.programmer.remove(fid);
                if (effects) live.programmerEffects.clear();
                return LIVE;
            }
            case P_CLEAR_FAMILY -> {
                int[] f = readFids(r, show);
                String fam = r.str(16);
                for (int fid : f) {
                    Look l = live.programmer.get(fid);
                    if (l == null) continue;
                    for (Attr a : Attr.ALL) if (a.family.equals(fam)) l.clear(a);
                    if ("color".equals(fam)) l.zones = null;
                }
                cleanProgrammer(live);
                return LIVE;
            }
            case P_PRESET -> {
                int[] f = readFids(r, show);
                Show.Preset p = show.preset(r.varInt());
                if (p == null) return REJECTED;
                for (int fid : f) {
                    Look src = p.forFixture(fid);
                    Look l = live.programmer.computeIfAbsent(fid, k -> new Look());
                    FixtureProfile pr = show.fixture(fid).profile();
                    for (Attr a : Attr.ALL) if (src.has(a) && pr.has(a)) l.set(a, src.get(a));
                    if (src.zones != null && pr.zones * 4 == src.zones.length) l.merge(onlyZones(src));
                }
                cleanProgrammer(live);
                return LIVE;
            }
            case P_AIM -> {
                int[] f = readFids(r, show);
                double x = r.f32(), y = r.f32(), z = r.f32();
                for (int fid : f) {
                    Show.Patch p = show.fixture(fid);
                    float[] pt = Movement.aim(p, x, y, z);
                    if (pt == null) continue;
                    Look l = live.programmer.computeIfAbsent(fid, k -> new Look());
                    if (p.profile().hasPan()) l.set(Attr.PAN, pt[0]);
                    l.set(Attr.TILT, pt[1]);
                }
                return LIVE;
            }
            case P_FOCUS -> {                                    // rev. 4c: held over the DMX until cleared (focus tool, external desk)
                int[] f = readFids(r, show);
                double x = r.f32(), y = r.f32(), z = r.f32();
                for (int fid : f) {
                    Show.Patch p = show.fixture(fid);
                    float[] pt = Movement.aim(p, x, y, z);
                    if (pt != null) live.focus.put(fid, new float[] {p.profile().hasPan() ? pt[0] : Float.NaN, pt[1]});
                }
                return LIVE;
            }
            case P_FOCUS_CLEAR -> {
                int[] f = readFids(r, show);
                if (f.length == 0) live.focus.clear();
                for (int fid : f) live.focus.remove(fid);
                return LIVE;
            }
            case P_HOME -> {
                int[] f = readFids(r, show);
                for (int fid : f) {
                    Show.Patch p = show.fixture(fid);
                    Look l = live.programmer.computeIfAbsent(fid, k -> new Look());
                    if (p.profile().hasPan()) l.set(Attr.PAN, 0.5f);
                    l.set(Attr.TILT, Movement.restTilt(p));
                }
                return LIVE;
            }
            case P_EFFECT_ADD -> {
                Show.Effect e = Show.readEffect(r);
                e.fids = validFids(show, e.fids);
                if (e.fids.length == 0 || live.programmerEffects.size() >= Show.MAX_EFFECTS) return REJECTED;
                live.programmerEffects.removeIf(x -> x.type == e.type && Arrays.equals(x.fids, e.fids));
                live.programmerEffects.add(e);
                live.programmerEffectsMs = now;
                return LIVE;
            }
            case P_EFFECT_CLEAR -> {
                live.programmerEffects.clear();
                return LIVE;
            }
            // ---------------------------------------------------------------- recording
            case STORE_CUE -> {
                int listId = r.varInt();
                float number = r.f32();
                String name = clean(r.str(Show.MAX_NAME * 4));
                float fade = Show.clampTime(r.f32());
                int mode = r.varInt();                       // 0 replace / new, 1 merge
                Set<String> fam = families(r.varInt());
                if (number <= 0 || number > 9999) return REJECTED;
                Show.CueList list = show.list(listId);
                if (list == null) {
                    if (show.lists.size() >= Show.MAX_LISTS) return REJECTED;
                    list = new Show.CueList();
                    list.id = listId > 0 && show.list(listId) == null ? listId : show.nextListId();
                    list.name = "Sequence " + list.id;
                    show.lists.add(list);
                }
                int idx = list.indexOf(number);
                Show.Cue cue;
                if (idx >= 0) {
                    cue = list.cues.get(idx);
                    if (mode == 0) { cue.looks.clear(); cue.effects.clear(); }
                } else {
                    if (list.cues.size() >= Show.MAX_CUES) return REJECTED;
                    cue = new Show.Cue();
                    cue.number = number;
                    list.cues.add(cue);
                    list.sort();
                }
                if (!name.isEmpty()) cue.name = name;
                cue.fadeIn = fade;
                cue.fadeOut = fade;
                for (Map.Entry<Integer, Look> e : live.programmer.entrySet()) {
                    Look l = e.getValue().filter(fam);
                    if (l.isEmpty()) continue;
                    cue.looks.computeIfAbsent(e.getKey(), k -> new Look()).merge(l);
                }
                if (fam.contains("intensity") || fam.size() == 4) for (Show.Effect e : live.programmerEffects) cue.effects.add(e.copy());
                return SHOW;
            }
            case UPDATE_CUE -> {
                Show.CueList list = show.list(r.varInt());
                int idx = r.varInt();
                if (list == null || idx < 0 || idx >= list.cues.size()) return REJECTED;
                Show.Cue cue = list.cues.get(idx);
                for (Map.Entry<Integer, Look> e : live.programmer.entrySet()) cue.looks.computeIfAbsent(e.getKey(), k -> new Look()).merge(e.getValue());
                for (Show.Effect e : live.programmerEffects) cue.effects.add(e.copy());
                return SHOW;
            }
            case CUE_EDIT -> {
                Show.CueList list = show.list(r.varInt());
                int idx = r.varInt();
                float number = r.f32();
                String name = clean(r.str(Show.MAX_NAME * 4));
                float in = Show.clampTime(r.f32()), out = Show.clampTime(r.f32()), delay = Show.clampTime(r.f32()), follow = r.f32();
                int trigger = r.varInt(), prio = r.sInt();
                if (list == null || idx < 0 || idx >= list.cues.size()) return REJECTED;
                Show.Cue c = list.cues.get(idx);
                if (number > 0 && number <= 9999 && (list.indexOf(number) < 0 || list.indexOf(number) == idx)) c.number = number;
                c.name = name; c.fadeIn = in; c.fadeOut = out; c.delay = delay;
                c.follow = Float.isFinite(follow) ? (follow < 0 ? -1f : Math.min(600f, follow)) : -1f;
                c.trigger = Math.max(0, Math.min(2, trigger));
                c.priority = Math.max(-1, Math.min(100, prio));
                list.sort();
                return SHOW;
            }
            case CUE_DELETE -> {
                Show.CueList list = show.list(r.varInt());
                int idx = r.varInt();
                if (list == null || idx < 0 || idx >= list.cues.size()) return REJECTED;
                list.cues.remove(idx);
                Live.Playback pb = live.playbacks.get(list.id);
                if (pb != null && pb.index >= list.cues.size()) { pb.index = -1; pb.from = -1; }
                return SHOW | LIVE;
            }
            case CUE_COPY -> {
                Show.CueList list = show.list(r.varInt());
                int idx = r.varInt();
                float number = r.f32();
                if (list == null || idx < 0 || idx >= list.cues.size() || number <= 0 || list.indexOf(number) >= 0) return REJECTED;
                Bytes.Writer w = new Bytes.Writer();
                Show.writeCue(w, list.cues.get(idx));
                Show.Cue c = Show.readCue(new Bytes.Reader(w.toBytes()));
                c.number = number;
                list.cues.add(c);
                list.sort();
                return SHOW;
            }
            case EFFECT_EDIT -> {
                Show.CueList list = show.list(r.varInt());
                int idx = r.varInt(), e = r.varInt();
                Show.Effect ne = Show.readEffect(r);
                if (list == null || idx < 0 || idx >= list.cues.size()) return REJECTED;
                List<Show.Effect> effs = list.cues.get(idx).effects;
                if (e < 0 || e >= effs.size()) return REJECTED;
                ne.fids = validFids(show, ne.fids);
                effs.set(e, ne);
                return SHOW;
            }
            case EFFECT_DELETE -> {
                Show.CueList list = show.list(r.varInt());
                int idx = r.varInt(), e = r.varInt();
                if (list == null || idx < 0 || idx >= list.cues.size()) return REJECTED;
                List<Show.Effect> effs = list.cues.get(idx).effects;
                if (e < 0 || e >= effs.size()) return REJECTED;
                effs.remove(e);
                return SHOW;
            }
            case LIST_NEW -> {
                String name = clean(r.str(Show.MAX_NAME * 4));
                if (show.lists.size() >= Show.MAX_LISTS) return REJECTED;
                Show.CueList l = new Show.CueList();
                l.id = show.nextListId();
                l.name = name.isEmpty() ? "Sequence " + l.id : name;
                show.lists.add(l);
                return SHOW;
            }
            case LIST_EDIT -> {
                Show.CueList l = show.list(r.varInt());
                String name = clean(r.str(Show.MAX_NAME * 4));
                int prio = r.varInt();
                boolean loop = r.bool();
                if (l == null) return REJECTED;
                if (!name.isEmpty()) l.name = name;
                l.priority = Math.max(0, Math.min(100, prio));
                l.loop = loop;
                return SHOW;
            }
            case LIST_DELETE -> {
                int id = r.varInt();
                Show.CueList l = show.list(id);
                if (l == null) return REJECTED;
                show.lists.remove(l);
                live.playbacks.remove(id);
                show.timeline.events.removeIf(e -> e.list == id && e.kind != Show.Event.ACCENT);
                return SHOW | LIVE;
            }
            case STORE_PRESET -> {
                int kind = r.varInt();
                String name = clean(r.str(Show.MAX_NAME * 4));
                int[] f = readFids(r, show);
                int overwrite = r.sInt();
                if (kind < 0 || kind > 3 || f.length == 0) return REJECTED;
                Set<String> fam = switch (kind) {
                    case Show.Preset.COLOR -> Set.of("color");
                    case Show.Preset.POSITION -> Set.of("position");
                    case Show.Preset.BEAM -> Set.of("beam");
                    default -> Set.of("intensity", "position", "color", "beam");
                };
                Show.Preset p = overwrite >= 0 ? show.preset(overwrite) : null;
                if (p == null) {
                    if (show.presets.size() >= Show.MAX_PRESETS) return REJECTED;
                    p = new Show.Preset();
                    p.id = show.nextPresetId();
                    show.presets.add(p);
                } else p.perFixture.clear();
                p.kind = kind;
                p.name = name.isEmpty() ? defaultPresetName(kind) + " " + p.id : name;
                Look first = null;
                for (int fid : f) {
                    Look l = live.programmer.get(fid);
                    if (l == null) continue;
                    Look v = l.filter(fam);
                    if (v.isEmpty()) continue;
                    if (first == null) first = v;
                    p.perFixture.put(fid, v);
                }
                // colours and beams are generic (they apply to any fixture); positions stay per fixture
                if (first != null && kind != Show.Preset.POSITION) { p.generic = first.copy(); p.generic.zones = null; }
                if (first != null && kind == Show.Preset.COLOR) {
                    int rr = (int) (255 * val(first, Attr.RED)), gg = (int) (255 * val(first, Attr.GREEN)), bb = (int) (255 * val(first, Attr.BLUE));
                    p.swatch = 0xFF000000 | rr << 16 | gg << 8 | bb;
                }
                return SHOW;
            }
            case PRESET_DELETE -> {
                Show.Preset p = show.preset(r.varInt());
                if (p == null) return REJECTED;
                show.presets.remove(p);
                return SHOW;
            }
            case PRESET_RENAME -> {
                Show.Preset p = show.preset(r.varInt());
                String name = clean(r.str(Show.MAX_NAME * 4));
                if (p == null || name.isEmpty()) return REJECTED;
                p.name = name;
                return SHOW;
            }
            case GROUP_STORE -> {
                String name = clean(r.str(Show.MAX_NAME * 4));
                int[] f = readFids(r, show);
                int color = r.i32();
                if (f.length == 0 || show.groups.size() >= Show.MAX_GROUPS) return REJECTED;
                Show.Group g = new Show.Group();
                g.id = show.nextGroupId();
                g.name = name.isEmpty() ? "Group " + g.id : name;
                g.fids = f;
                g.color = color | 0xFF000000;
                show.groups.add(g);
                return SHOW;
            }
            case GROUP_DELETE -> {
                Show.Group g = show.group(r.varInt());
                if (g == null) return REJECTED;
                show.groups.remove(g);
                return SHOW;
            }
            case GROUP_RENAME -> {
                Show.Group g = show.group(r.varInt());
                String name = clean(r.str(Show.MAX_NAME * 4));
                if (g == null || name.isEmpty()) return REJECTED;
                g.name = name;
                return SHOW;
            }
            case GROUP_SET -> {
                Show.Group g = show.group(r.varInt());
                int[] f = readFids(r, show);
                if (g == null) return REJECTED;
                g.fids = f;
                return SHOW;
            }
            case SAVE_COLOR -> {
                int c = r.i32() & 0xFFFFFF;
                if (show.savedColors.contains(c)) return REJECTED;
                if (show.savedColors.size() >= Show.MAX_COLORS) show.savedColors.remove(0);
                show.savedColors.add(c);
                return SHOW;
            }
            case DELETE_COLOR -> {
                int i = r.varInt();
                if (i < 0 || i >= show.savedColors.size()) return REJECTED;
                show.savedColors.remove(i);
                return SHOW;
            }
            // ---------------------------------------------------------------- playback
            case GO -> {
                Show.CueList l = show.list(r.varInt());
                if (l == null || l.cues.isEmpty()) return REJECTED;
                Live.Playback pb = live.playback(l.id);
                int[] st = pb.running() ? Engine.follow(l, pb.index, pb.from, pb.elapsed(now)) : new int[] {-1, -1, 0, 0};
                int cur = st[0];
                int next = cur < 0 ? 0 : cur + 1 < l.cues.size() ? cur + 1 : l.loop ? 0 : -1;
                if (next < 0) return REJECTED;
                pb.from = cur;
                pb.index = next;
                pb.goMs = now;
                pb.paused = false;
                pb.releaseMs = -1;
                return LIVE;
            }
            case BACK -> {
                Show.CueList l = show.list(r.varInt());
                if (l == null) return REJECTED;
                Live.Playback pb = live.playback(l.id);
                if (!pb.running()) return REJECTED;
                int cur = Engine.follow(l, pb.index, pb.from, pb.elapsed(now))[0];
                int prev = cur - 1 >= 0 ? cur - 1 : l.loop ? l.cues.size() - 1 : -1;
                if (prev < 0) return REJECTED;
                pb.from = cur;
                pb.index = prev;
                pb.goMs = now;
                pb.paused = false;
                return LIVE;
            }
            case GOTO -> {
                Show.CueList l = show.list(r.varInt());
                int idx = r.varInt();
                if (l == null || idx < 0 || idx >= l.cues.size()) return REJECTED;
                Live.Playback pb = live.playback(l.id);
                pb.from = pb.running() ? Engine.follow(l, pb.index, pb.from, pb.elapsed(now))[0] : -1;
                pb.index = idx;
                pb.goMs = now;
                pb.paused = false;
                pb.releaseMs = -1;
                return LIVE;
            }
            case PAUSE -> {
                Show.CueList l = show.list(r.varInt());
                if (l == null) return REJECTED;
                Live.Playback pb = live.playback(l.id);
                if (!pb.running()) return REJECTED;
                if (pb.paused) { pb.goMs += now - pb.pausedAtMs; pb.paused = false; }
                else { pb.paused = true; pb.pausedAtMs = now; }
                return LIVE;
            }
            case STOP -> {
                Show.CueList l = show.list(r.varInt());
                if (l == null) return REJECTED;
                Live.Playback pb = live.playback(l.id);
                release(l, pb, now);
                return LIVE;
            }
            case STOP_ALL -> {
                for (Show.CueList l : show.lists) release(l, live.playback(l.id), now);
                live.timelineArmed = false;
                live.programmerEffects.clear();
                return LIVE;
            }
            case FADER -> {
                Show.CueList l = show.list(r.varInt());
                float v = r.unit();
                if (l == null || Float.isNaN(v)) return REJECTED;
                live.playback(l.id).level = v;
                return LIVE;
            }
            case FLASH -> {
                Show.CueList l = show.list(r.varInt());
                boolean on = r.bool();
                if (l == null) return REJECTED;
                Live.Playback pb = live.playback(l.id);
                pb.flash = on;
                // a list started by its flash key (from = -2) goes off again when the key is released
                if (on && !pb.running()) { pb.index = 0; pb.from = -2; pb.goMs = now - 100000; pb.releaseMs = -1; }
                else if (!on && pb.from == -2) { pb.index = -1; pb.from = -1; }
                return LIVE;
            }
            // ---------------------------------------------------------------- masters and safety
            case GRAND_MASTER -> {
                float v = r.unit();
                if (Float.isNaN(v)) return REJECTED;
                live.grandMaster = v;
                return LIVE;
            }
            case BLACKOUT -> { live.blackout = r.bool(); return LIVE; }
            case NO_STROBE -> { live.noStrobe = r.bool(); return LIVE; }
            case STROBE_CAP -> {
                float hz = r.f32();
                live.strobeCapHz = Math.max(0f, Math.min(25f, hz));
                return LIVE;
            }
            case MOVE_LIMIT -> { live.moveLimit = r.bool(); return LIVE; }
            case AUTO_ENABLE -> { live.autoEnabled = r.bool(); return LIVE; }
            case TIMELINE_ARM -> { live.timelineArmed = r.bool(); return LIVE; }
            case CONTROL_MODE -> {
                // External Show Control: one source drives the fixtures; the change cross-fades (Engine) from the previous one
                int mode = r.varInt();
                if (mode < Live.INTERNAL || mode > Live.PLAYBACK) return REJECTED;
                if (mode == live.control) return LIVE;
                live.previousControl = live.control;
                live.control = mode;
                live.controlMs = now;
                if (mode == Live.INTERNAL) live.autoEnabled = false;
                if (mode == Live.AUTOSHOW) live.autoEnabled = true;
                return LIVE;
            }
            case HOME_ALL -> {
                live.programmer.clear();
                live.programmerEffects.clear();
                for (Show.Patch p : show.patch) {
                    Look l = new Look();
                    if (p.profile().hasPan()) l.set(Attr.PAN, 0.5f);
                    l.set(Attr.TILT, Movement.restTilt(p));
                    l.set(Attr.DIMMER, 0f);
                    live.programmer.put(p.fid, l);
                }
                return LIVE;
            }
            // ---------------------------------------------------------------- timeline
            case TL_EVENT_ADD -> {
                Show.Event e = new Show.Event();
                e.ms = Math.max(0, r.varLong()); e.kind = r.varInt(); e.list = r.varInt(); e.cue = r.f32(); e.group = r.sInt();
                e.strength = unit(r.unit()); e.lengthMs = Math.max(20, Math.min(4000, r.varInt()));
                if (Float.isNaN(e.strength)) e.strength = 1f;
                if (e.kind < 0 || e.kind > 2 || show.timeline.events.size() >= Show.MAX_EVENTS) return REJECTED;
                if (e.kind != Show.Event.ACCENT && show.list(e.list) == null) return REJECTED;
                show.timeline.events.add(e);
                show.timeline.sort();
                return SHOW;
            }
            case TL_EVENT_DELETE -> {
                int i = r.varInt();
                if (i < 0 || i >= show.timeline.events.size()) return REJECTED;
                show.timeline.events.remove(i);
                return SHOW;
            }
            case TL_EVENT_MOVE -> {
                int i = r.varInt();
                long ms = Math.max(0, r.varLong());
                if (i < 0 || i >= show.timeline.events.size()) return REJECTED;
                show.timeline.events.get(i).ms = ms;
                show.timeline.sort();
                return SHOW;
            }
            case TL_MARKER_ADD -> {
                Show.Marker m = new Show.Marker();
                m.ms = Math.max(0, r.varLong());
                m.label = clean(r.str(Show.MAX_NAME * 4));
                if (show.timeline.markers.size() >= Show.MAX_MARKERS) return REJECTED;
                show.timeline.markers.add(m);
                show.timeline.sort();
                return SHOW;
            }
            case TL_MARKER_DELETE -> {
                int i = r.varInt();
                if (i < 0 || i >= show.timeline.markers.size()) return REJECTED;
                show.timeline.markers.remove(i);
                return SHOW;
            }
            case TL_TRACK -> {
                show.timeline.trackSha1 = r.str(64);
                show.timeline.trackName = clean(r.str(1024));
                return SHOW;
            }
            case TL_OFFSET -> {
                show.timeline.offsetMs = Math.max(-2000, Math.min(2000, r.sInt()));
                return SHOW;
            }
            case TL_TEMPO -> {
                float bpm = r.f32();
                long off = r.sLong();
                if (!(bpm == 0 || bpm >= 30 && bpm <= 300)) return REJECTED;
                show.timeline.bpm = bpm;
                show.timeline.beatOffsetMs = off;
                return SHOW;
            }
            case TL_CLEAR -> {
                show.timeline.events.clear();
                show.timeline.markers.clear();
                return SHOW;
            }
            case TL_SECTION_RENAME -> {
                int i = r.varInt();
                String name = clean(r.str(Show.MAX_NAME * 4));
                if (i < 0 || i >= show.timeline.sections.size()) return REJECTED;
                show.timeline.sections.get(i).label = name;
                return SHOW;
            }
            case AUTO_SETTINGS -> {
                Show.AutoSettings a = show.auto;
                int style = r.varInt(), mode = r.varInt();
                float in = r.unit(), mv = r.unit(), cv = r.unit(), ea = r.unit();
                boolean strobes = r.bool();
                float hz = r.f32();
                int mask = r.varInt();
                int n = r.count(Show.MAX_COLORS);
                List<Integer> fav = new ArrayList<>();
                for (int i = 0; i < n; i++) fav.add(r.varInt() & 0xFFFFFF);
                if (style < 0 || style > 4 || mode < 0 || mode > 1) return REJECTED;
                a.style = style; a.mode = mode;
                a.intensity = nz(in, a.intensity); a.movement = nz(mv, a.movement); a.colorVariety = nz(cv, a.colorVariety); a.effectAmount = nz(ea, a.effectAmount);
                a.strobes = strobes; a.strobeMaxHz = Math.max(0f, Math.min(15f, hz)); a.effectMask = mask & Show.Effect.ALL_MASK;
                a.favoriteColors.clear(); a.favoriteColors.addAll(fav);
                return SHOW;
            }
            // ---------------------------------------------------------------- patch and show
            case PATCH_ADD -> {
                Show.Patch p = new Show.Patch();
                p.fid = r.varInt(); p.type = r.str(64); p.name = clean(r.str(Show.MAX_NAME * 4));
                p.x = r.sInt(); p.y = r.sInt(); p.z = r.sInt(); p.yaw = r.f32(); p.mount = r.varInt();
                FixtureProfile pr = FixtureProfile.get(p.type);
                if (pr == null || p.fid <= 0 || show.fixture(p.fid) != null || show.patch.size() >= Show.MAX_FIXTURES) return REJECTED;
                int[] ad = show.nextAddress(pr);
                p.universe = ad[0]; p.address = ad[1];
                p.dmxMode = Show.defaultMode(pr);                              // rev. 25: blinders on one channel
                if (p.name.isEmpty()) p.name = pr.name.replace("CSC ", "") + " " + p.fid;
                show.patch.add(p);
                return SHOW;
            }
            case PATCH_EDIT -> {
                Show.Patch p = show.fixture(r.varInt());
                String name = clean(r.str(Show.MAX_NAME * 4));
                int u = r.varInt(), a = r.varInt();
                if (p == null || !com.concertstage.light.dmx.ArtNetSettings.validUniverse(u) || a < 1 || a > 512) return REJECTED;   // rev. 16h: universe 0 valid
                if (a + p.footprint() - 1 > 512) return REJECTED;
                if (!name.isEmpty()) p.name = name;
                p.universe = u; p.address = a;
                return SHOW;
            }
            case PATCH_DMX -> {
                // DMX patch of one fixture: universe, address, mode and its local transforms
                Show.Patch p = show.fixture(r.varInt());
                int u = r.varInt(), a = r.varInt(), mode = r.varInt();
                boolean ip = r.bool(), it = r.bool();
                float po = r.f32(), to = r.f32();
                if (p == null || !com.concertstage.light.dmx.ArtNetSettings.validUniverse(u) || a < 1 || a > 512 || mode < 0 || mode > com.concertstage.light.dmx.DmxMode.LAST || !Float.isFinite(po) || !Float.isFinite(to)) return REJECTED;
                if (a + com.concertstage.light.dmx.DmxMode.of(p.profile(), mode).footprint() - 1 > 512) return REJECTED;
                p.universe = u; p.address = a; p.dmxMode = mode;
                p.invertPan = ip; p.invertTilt = it;
                p.panOffset = Math.max(-360f, Math.min(360f, po)); p.tiltOffset = Math.max(-360f, Math.min(360f, to));
                return SHOW;
            }
            case PATCH_BULK -> {
                // a whole patch plan (auto patch): fid, universe, address, mode for each; all or nothing
                int n = r.count(Show.MAX_FIXTURES);
                int[][] plan = new int[n][4];
                for (int i = 0; i < n; i++) {
                    plan[i][0] = r.varInt(); plan[i][1] = r.varInt(); plan[i][2] = r.varInt(); plan[i][3] = r.varInt();
                    if (show.fixture(plan[i][0]) == null || !com.concertstage.light.dmx.ArtNetSettings.validUniverse(plan[i][1]) || plan[i][2] < 1 || plan[i][2] > 512
                        || plan[i][3] < 0 || plan[i][3] > 3) return REJECTED;
                    if (plan[i][2] + com.concertstage.light.dmx.DmxMode.of(show.fixture(plan[i][0]).profile(), plan[i][3]).footprint() - 1 > 512) return REJECTED;
                }
                for (int[] e : plan) {
                    Show.Patch p = show.fixture(e[0]);
                    p.universe = e[1]; p.address = e[2]; p.dmxMode = e[3];
                }
                return SHOW;
            }
            case MATRIX_SETTINGS -> {
                int u = r.varInt(), a = r.varInt(), order = r.varInt();
                if (!com.concertstage.light.dmx.ArtNetSettings.validUniverse(u) || a < 1 || a > 497 || order < 0 || order > 2) return REJECTED;
                show.external.matrixUniverse = u; show.external.matrixAddress = a; show.external.matrixOrder = order;
                return SHOW;
            }
            case PATCH_REMOVE -> {
                int fid = r.varInt();
                Show.Patch p = show.fixture(fid);
                if (p == null) return REJECTED;
                show.patch.remove(p);
                live.programmer.remove(fid);
                for (Show.Group g : show.groups) g.fids = Arrays.stream(g.fids).filter(x -> x != fid).toArray();
                return SHOW | LIVE;
            }
            case PATCH_MOVE -> {
                Show.Patch p = show.fixture(r.varInt());
                int x = r.sInt(), y = r.sInt(), z = r.sInt();
                float yaw = r.f32();
                int mount = r.varInt();
                if (p == null) return REJECTED;
                p.x = x; p.y = y; p.z = z; p.yaw = yaw; p.mount = mount == 1 ? 1 : 0;
                return SHOW;
            }
            case PATCH_GEL -> {
                Show.Patch p = show.fixture(r.varInt());
                int gel = r.varInt();
                if (p == null || gel < 0 || gel > 0xFFFFFF) return REJECTED;
                p.gel = gel;
                return SHOW;
            }
            case PATCH_POSE -> {
                Show.Patch p = show.fixture(r.varInt());
                float ox = r.f32(), oy = r.f32(), oz = r.f32(), pitch = r.f32(), roll = r.f32();
                if (p == null) return REJECTED;
                Show.setPose(p, ox, oy, oz, pitch, roll);
                return SHOW;
            }
            case SHOW_NAME -> {
                String name = clean(r.str(Show.MAX_NAME * 4));
                if (name.isEmpty()) return REJECTED;
                show.name = name;
                return SHOW;
            }
            case SHOW_REPLACE -> {
                Show n = Show.fromBytes(r.bytes(4 << 20));
                if (n == null) return REJECTED;
                // keep the fixtures of the desk (the patch describes the real rig, not the show file)
                List<Show.Patch> rig = new ArrayList<>(show.patch);
                boolean keepRig = r.bool();
                copyInto(n, show);
                if (keepRig) {
                    show.patch.clear();
                    show.patch.addAll(rig);
                }
                live.programmer.keySet().removeIf(f -> show.fixture(f) == null);
                live.playbacks.keySet().removeIf(id -> show.list(id) == null);
                for (Live.Playback pb : live.playbacks.values()) if (pb.index >= 0) { pb.index = -1; pb.from = -1; }
                return SHOW | LIVE;
            }
            case LINK_AUDIO -> {
                live.hasAudio = r.bool();
                live.audioX = r.sInt(); live.audioY = r.sInt(); live.audioZ = r.sInt();
                return LIVE;
            }
            case POSITIONS -> {
                // (re)builds the standard position palettes from the rig geometry
                if (stage == null) return REJECTED;
                show.presets.removeIf(p -> p.kind == Show.Preset.POSITION && Arrays.asList(Movement.POSITION_NAMES).contains(p.name));
                show.presets.addAll(Movement.standardPositions(show, stage, show.nextPresetId()));
                return SHOW;
            }
            default -> {
                return REJECTED;
            }
        }
    }

    private static void release(Show.CueList l, Live.Playback pb, long now) {
        if (pb.running()) {
            int cur = Engine.follow(l, pb.index, pb.from, pb.elapsed(now))[0];
            pb.released = cur;
            pb.releaseMs = now;
        }
        pb.index = -1;
        pb.from = -1;
        pb.paused = false;
        pb.flash = false;
    }

    /** Replaces every field of {@code dst} by those of {@code src} (the desk keeps its Show object). */
    public static void copyInto(Show src, Show dst) {
        dst.id = src.id; dst.name = src.name; dst.created = src.created; dst.modified = src.modified;
        dst.patch.clear(); dst.patch.addAll(src.patch);
        dst.groups.clear(); dst.groups.addAll(src.groups);
        dst.presets.clear(); dst.presets.addAll(src.presets);
        dst.savedColors.clear(); dst.savedColors.addAll(src.savedColors);
        dst.lists.clear(); dst.lists.addAll(src.lists);
        Show.Timeline t = dst.timeline, s = src.timeline;
        t.trackSha1 = s.trackSha1; t.trackName = s.trackName; t.bpm = s.bpm; t.beatOffsetMs = s.beatOffsetMs; t.beatsPerBar = s.beatsPerBar;
        t.offsetMs = s.offsetMs;
        t.events.clear(); t.events.addAll(s.events);
        t.markers.clear(); t.markers.addAll(s.markers);
        t.sections.clear(); t.sections.addAll(s.sections);
        Show.AutoSettings a = dst.auto, b = src.auto;
        a.style = b.style; a.mode = b.mode; a.intensity = b.intensity; a.movement = b.movement; a.colorVariety = b.colorVariety;
        a.effectAmount = b.effectAmount; a.strobes = b.strobes; a.strobeMaxHz = b.strobeMaxHz; a.effectMask = b.effectMask;
        a.favoriteColors.clear(); a.favoriteColors.addAll(b.favoriteColors);
        dst.edits++;
    }

    private static void cleanProgrammer(Live live) {
        live.programmer.values().removeIf(Look::isEmpty);
    }

    private static Look onlyZones(Look l) {
        Look z = new Look();
        z.zones = l.zones == null ? null : l.zones.clone();
        return z;
    }

    private static int[] validFids(Show show, int[] f) {
        return Arrays.stream(f).filter(x -> show.fixture(x) != null).distinct().limit(Show.MAX_FIXTURES).toArray();
    }

    /** Families bit mask: 1 intensity, 2 position, 4 color, 8 beam (0 = everything). */
    public static Set<String> families(int mask) {
        if (mask == 0) mask = 15;
        java.util.HashSet<String> s = new java.util.HashSet<>();
        if ((mask & 1) != 0) s.add("intensity");
        if ((mask & 2) != 0) s.add("position");
        if ((mask & 4) != 0) s.add("color");
        if ((mask & 8) != 0) s.add("beam");
        return s;
    }

    private static String defaultPresetName(int kind) {
        return switch (kind) { case 0 -> "Color"; case 1 -> "Position"; case 2 -> "Beam"; default -> "Look"; };
    }

    private static float val(Look l, Attr a) {
        return l.has(a) ? l.get(a) : 1f;
    }

    private static float nz(float v, float def) {
        return Float.isNaN(v) ? def : v;
    }

    /** Text from a client: control characters removed, trimmed, at most {@link Show#MAX_NAME} characters. */
    public static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < Show.MAX_NAME; i++) {
            char c = s.charAt(i);
            if (!Character.isISOControl(c)) b.append(c);
        }
        return b.toString().strip();
    }
}
