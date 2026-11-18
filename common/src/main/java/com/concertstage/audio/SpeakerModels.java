package com.concertstage.audio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Speaker Registry: the CSC Veyra (lot 01) and CSC Noryx (lot 02) cabinets. Dimensions come from the GAME_READY exports (1 m = 1 block,
 * origin at the centre of the front bottom edge, front towards +Z, back at -depth). The acoustic figures are game settings chosen for
 * each role (line array element / subwoofer), not measurements of a real product.
 */
public final class SpeakerModels {
    private SpeakerModels() {}

    public enum Kind { LINE_ARRAY, SUBWOOFER, MAIN, MONITOR }

    /**
     * @param splayMax  largest angle between two cabinets of an array (degrees), 0 for a subwoofer
     * @param hDispersion horizontal coverage (full angle, degrees)
     * @param vDispersion vertical coverage of one cabinet (full angle, degrees)
     * @param rear       level behind the cabinet compared with the front (0..1)
     */
    public record Model(String id, String family, Kind kind, double width, double height, double depth, double splayMax,
                        double hDispersion, double vDispersion, double rear) {
        public boolean isSub() { return kind == Kind.SUBWOOFER; }
        public boolean isMonitor() { return kind == Kind.MONITOR; }
        public boolean isSingle() { return isMonitor(); }          // 1.2.0 beta 2: the main cabinets (AUREX M12) stack / fly in columns
        /** Cabinets that can be rigged together: same family and same kind. */
        public boolean fits(Model other) {
            if (isSingle() || other == null || !family.equals(other.family) || kind != other.kind) return false;
            if (kind == Kind.MAIN) return id.equals(other.id);        // a column of the same main cabinet
            double a = scale(id), b = scale(other.id);                // rev. 4b: compared at their delivered size (A1 + A2 still rig together)
            return Math.abs(width / a - other.width / b) < .12 && Math.abs(depth / a - other.depth / b) < .15;
        }
    }

    public static final Map<String, Model> ALL = new LinkedHashMap<>();

    /** Rev. 4b: size of the C6 portable system (B18P, pole, C6) compared with the delivered models. */
    public static final double PORTABLE = 1.6;
    private static final Map<String, Double> SCALE = new java.util.HashMap<>();

    private static void add(Model m) { ALL.put(m.id(), m); }

    static {
        add(new Model("csc_aurex_m12", "aurex_main", Kind.MAIN, 1.60, .60, .90, 0, 90, 60, .16));
        add(new Model("csc_aurex_s218_f", "aurex", Kind.SUBWOOFER, 1.80, 1.15, 1.10, 0, 360, 360, .75));
        add(new Model("csc_aurex_m15_w", "aurex_monitor", Kind.MONITOR, .78, .47, .70, 0, 80, 70, .12));
        add(new Model("csc_orvane_lx12", "orvane", Kind.LINE_ARRAY, 2.0, 0.78, 1.10, 10, 100, 10, 0.20));
        add(new Model("csc_orvane_lx8", "orvane", Kind.LINE_ARRAY, 1.60, 0.60, 0.90, 10, 110, 12, 0.22));
        add(new Model("csc_orvane_sb28", "orvane", Kind.SUBWOOFER, 1.80, 1.20, 1.35, 0, 360, 360, 0.75));
        add(new Model("csc_veyra_a1", "veyra", Kind.LINE_ARRAY, 1.18, 0.42, 0.62, 10, 110, 10, 0.18));
        add(new Model("csc_veyra_a2", "veyra", Kind.LINE_ARRAY, 1.24, 0.40, 0.58, 10, 90, 8, 0.18));
        add(new Model("csc_veyra_b2", "veyra", Kind.SUBWOOFER, 1.12, 0.82, 0.88, 0, 360, 360, 0.75));
        add(new Model("csc_noryx_a1", "noryx", Kind.LINE_ARRAY, 0.88, 0.32, 0.47, 10, 110, 12, 0.2));
        add(new Model("csc_noryx_a2", "noryx", Kind.LINE_ARRAY, 0.94, 0.305, 0.445, 10, 90, 10, 0.2));
        add(new Model("csc_noryx_b1", "noryx", Kind.SUBWOOFER, 0.86, 0.64, 0.72, 0, 360, 360, 0.75));
        // 1.3.0 rev. 4 — CSC Professional Audio Expansion and AUREX C6 Portable (13 November 2026). Height = distance between the
        // front pivots (the rigging pitch, measured on the delivered models), depth = twice the front pivot to the centre (the cabinet
        // stays centred on its frame); width = cabinet. Coverage figures: game settings for the role, like the other cabinets.
        add(new Model("csc_aurex_la12", "aurex_la", Kind.LINE_ARRAY, 1.80, 0.50, 0.69, 10, 90, 9, 0.18));
        add(new Model("csc_aurex_la8", "aurex_la", Kind.LINE_ARRAY, 1.40, 0.40, 0.54, 10, 100, 12, 0.20));
        add(new Model("csc_aurex_c6", "aurex_c6", Kind.LINE_ARRAY, 0.38, 0.30, 0.346, 10, 90, 40, 0.22));
        add(new Model("csc_aurex_b18p", "aurex_portable", Kind.SUBWOOFER, 0.676, 0.776, 0.754, 0, 360, 360, 0.75));
        // rev. 4b — every rigged line array at the size of the Orvane ones: the large cabinet of each family as wide as the LX12, the
        // compact one as wide as the LX8, scaled evenly (the delivered models keep their proportions). The C6 column stays as delivered.
        resize("csc_veyra_a2", "csc_orvane_lx12");
        resize("csc_noryx_a2", "csc_orvane_lx12");
        resize("csc_aurex_la12", "csc_orvane_lx12");
        resize("csc_veyra_a1", "csc_orvane_lx8");
        resize("csc_noryx_a1", "csc_orvane_lx8");
        resize("csc_aurex_la8", "csc_orvane_lx8");
        // rev. 4b — the C6 portable system was too small next to the rest: the B18P, its pole and the C6 together, ×1.6 (the B18P
        // then stands about as high as an Orvane SB28)
        scaleBy("csc_aurex_c6", PORTABLE);
        scaleBy("csc_aurex_b18p", PORTABLE);
    }


    private static void resize(String id, String like) {
        scaleBy(id, ALL.get(like).width() / ALL.get(id).width());
    }

    private static void scaleBy(String id, double k) {
        Model m = ALL.get(id);
        SCALE.put(id, k);
        ALL.put(id, new Model(m.id(), m.family(), m.kind(), m.width() * k, m.height() * k, m.depth() * k, m.splayMax(), m.hDispersion(),
            m.vDispersion(), m.rear()));
    }

    /** Rev. 4b: size of a cabinet (or of a piece made for it) compared with its delivered mesh; 1 for the unchanged ones. */
    public static double scale(String id) {
        if (id == null) return 1;
        Double k = SCALE.get(id);
        if (k != null) return k;
        var f = SpeakerFrames.get(id);
        return f != null && f.centred() ? SCALE.getOrDefault(f.model(), 1.0) : 1;
    }

    /** Rev. 4: most cabinets of a model in one array (the portable system: one B18P, two C6 on its pole). */
    public static int max(Model m) {
        if (m == null) return 0;
        return switch (m.id()) {
            case "csc_aurex_c6" -> 2;
            case "csc_aurex_b18p" -> 1;
            case "csc_aurex_m12" -> 3;                          // 1.2.0 beta 2: a column of up to 3 M12
            default -> m.isSub() ? SpeakerBlockEntity.MAX_SUB : SpeakerBlockEntity.MAX_LINE;
        };
    }

    /** Rev. 4: models that only stand (no flown version: portable system). */
    public static boolean groundOnly(Model m) {
        return m != null && (m.family().equals("aurex_c6") || m.family().equals("aurex_portable"));
    }

    public static Model get(String id) {
        return id == null ? null : ALL.get(id);
    }

    public static java.util.Set<String> ids() {
        return ALL.keySet();
    }
}
