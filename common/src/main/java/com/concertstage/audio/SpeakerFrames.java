package com.concertstage.audio;
import java.util.LinkedHashMap;
import java.util.Map;
/** Explicit support items; the original speaker IDs and implicit legacy frames stay valid. */
public final class SpeakerFrames {
    /**
     * @param height  rev. 4: from the bottom of the support to the bottom of the first cabinet when it stands (flown: the bumper)
     * @param sub     rev. 4: the subwoofer model it stands on (null: on the ground only); {@code onSubOnly} — it cannot stand on the floor
     * @param under   rev. 4: a legacy ground frame drawn under it (the stack adapters complete the family's own frame)
     * @param centred rev. 4: its mesh is centred on the cabinet (new pieces) instead of starting at the front edge (legacy frames)
     */
    public record Frame(String id, String family, boolean flying, String model, double height, String sub, boolean onSubOnly, String under,
                        boolean centred) {
        public Frame(String id, String family, boolean flying, String model) {
            this(id, family, flying, model, SpeakerBlockEntity.BUMPER, null, false, null, false);
        }
        public boolean accepts(SpeakerModels.Model m) {
            if (m == null || !family.equals(m.family())) return false;
            if (family.startsWith("aurex")) return model.equals(m.id());
            if (centred) return model.equals(m.id());                 // a stack adapter is made for one cabinet
            return !m.isSub();
        }
        /** Height of everything under the first cabinet of a stack: this support and the legacy frame under it. */
        public double lift() {
            Frame u = under == null ? null : ALL.get(under);
            return height + (u == null ? 0 : u.height());
        }
    }
    public static final Map<String, Frame> ALL = new LinkedHashMap<>();
    static {
        ALL.put("csc_aurex_flying_frame", new Frame("csc_aurex_flying_frame", "aurex", true, "csc_aurex_s218_f"));
        ALL.put("csc_aurex_main_flying_frame", new Frame("csc_aurex_main_flying_frame", "aurex_main", true, "csc_aurex_m12"));
        for (String family : new String[]{"veyra", "noryx", "orvane"}) {
            String model = "csc_" + family + (family.equals("orvane") ? "_lx12" : "_a1");
            for (boolean flying : new boolean[]{true, false}) {
                String id = "csc_" + family + (flying ? "_flying_frame" : "_ground_frame");
                ALL.put(id, new Frame(id, family, flying, model));
            }
        }
        // rev. 4 — AUREX LA: fly frames (under a truss / a hoist) and ground frames (on the floor or on an S218-F stack: module plane
        // 0.23 m above the contact, read on the delivered frames)
        for (String la : new String[]{"la12", "la8"}) {
            String model = "csc_aurex_" + la;
            put(new Frame("csc_aurex_" + la + "_fly_frame", "aurex_la", true, model, SpeakerBlockEntity.BUMPER, null, false, null, true));
            put(new Frame("csc_aurex_" + la + "_ground_frame", "aurex_la", false, model, 0.23 * SpeakerModels.scale(model), "csc_aurex_s218_f", false, null, true));
        }
        // rev. 4 — stack adapters: on the family's own ground frame, itself on the family's sub (Orvane SB28, Veyra B2, Noryx B1)
        String[][] adapters = {{"orvane", "lx8", "csc_orvane_sb28"}, {"orvane", "lx12", "csc_orvane_sb28"}, {"veyra", "a1", "csc_veyra_b2"},
            {"veyra", "a2", "csc_veyra_b2"}, {"noryx", "a1", "csc_noryx_b1"}, {"noryx", "a2", "csc_noryx_b1"}};
        for (String[] a : adapters) {
            String id = "csc_" + a[0] + "_" + a[1] + "_stack_adapter";
            put(new Frame(id, a[0], false, "csc_" + a[0] + "_" + a[1], 0.23 * SpeakerModels.scale("csc_" + a[0] + "_" + a[1]), a[2], true, "csc_" + a[0] + "_ground_frame", true));
        }
        // rev. 4 — AUREX C6 portable: the pole on the B18P (1.05 m from the top of the sub to the base of the lower C6)
        put(new Frame("csc_aurex_portable_pole", "aurex_c6", false, "csc_aurex_c6", 1.05 * SpeakerModels.scale("csc_aurex_c6"), "csc_aurex_b18p", true, null, true));
    }
    private static void put(Frame f) { ALL.put(f.id(), f); }
    public static Frame get(String id) { return ALL.get(id); }

    /**
     * Rev. 4: the support drawn for an array: its own when it has one, else the family's implicit frame (legacy arrays placed without
     * an explicit frame; the AUREX LA a fly frame of its size when flown).
     */
    public static Frame drawn(String support, SpeakerModels.Model m, boolean flying) {
        Frame f = support == null || support.isEmpty() ? null : ALL.get(support);
        if (f != null) return f;
        if (m == null) return null;
        if (m.family().equals("aurex_la")) return flying ? ALL.get(m.id() + "_fly_frame") : null;
        return ALL.get("csc_" + m.family() + (flying ? "_flying_frame" : "_ground_frame"));
    }
    private SpeakerFrames() {}
}
