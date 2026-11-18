package com.concertstage.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/**
 * Render quality of the blocks of the mod (client setting, saved in {@code config/concertstage-client.properties}): how far the
 * pieces drawn by the mod (rigging, tarps, roofs, mounted pieces...) are drawn, how close the detailed meshes of the towers start, and
 * whether the small hardware (bolts, clamps) is drawn. High is the default.
 */
public enum RenderQuality {
    LOW(0.5, 0, false),
    MEDIUM(0.75, 12, true),
    HIGH(1.0, 24, true);

    /** Share of Minecraft's render distance (the number of chunks of the video settings) at which the mod's pieces are drawn. */
    public final double distance;
    /** Up to this distance (blocks) the towers use their detailed mesh. */
    public final int nearRadius;
    /** Bolts, clamps and other small hardware of the mounted pieces. */
    public final boolean hardware;

    RenderQuality(double distance, int nearRadius, boolean hardware) {
        this.distance = distance;
        this.nearRadius = nearRadius;
        this.hardware = hardware;
    }

    private static volatile RenderQuality current;
    /** 1.1.1: AUTO — the quality follows the frame rate (AutoQuality), between LOW and HIGH. Saved as render_quality=AUTO. */
    private static volatile boolean auto;
    private static AutoQuality controller;

    public static RenderQuality current() {
        RenderQuality q = current;
        if (q == null) current = q = load();
        return q;
    }

    public static boolean auto() {
        current();
        return auto;
    }

    /** Turns AUTO on (from the current quality) or off (keeps the quality it reached). */
    public static void setAuto(boolean on) {
        RenderQuality q = current();
        auto = on;
        controller = on ? new AutoQuality(q.ordinal()) : null;
        save(on ? null : q);
    }

    /**
     * 1.2.1: the frame rate Minecraft is held to (Max Framerate of the video settings, or the screen's rate with V-Sync), 0 = unlimited.
     * A game held to 20 or 30 images a second is not slow: the automatic qualities do not lower the detail for that (seen 15 November:
     * "T: 20" in F3, the beams on their lowest detail although CSC took 1.4 ms of each frame).
     */
    public static int capFps() {
        try {
            var o = net.minecraft.client.Minecraft.getInstance().options;
            int limit = o.framerateLimit().get();
            int cap = limit >= 260 ? 0 : limit;
            if (o.enableVsync().get()) {
                var w = net.minecraft.client.Minecraft.getInstance().getWindow();
                int hz = w == null ? 60 : w.getRefreshRate();
                if (hz > 0) cap = cap == 0 ? hz : Math.min(cap, hz);
            }
            return cap;
        } catch (Throwable e) {
            return 0;
        }
    }

    /** Frame time (ms) of the cap above, 0 = unlimited. */
    public static double capPeriodMs() {
        int c = capFps();
        return c <= 0 ? 0 : 1000.0 / c;
    }

    /** Once per client tick (ClientAudio.tick): with AUTO on, follows Minecraft's frame counter. */
    public static void autoTick() {
        if (!auto()) return;
        AutoQuality c = controller;
        if (c == null) controller = c = new AutoQuality(current().ordinal());
        int fps = fps();
        int level = c.update(fps, System.currentTimeMillis(), HIGH.ordinal(), capFps());
        if (level != current().ordinal()) apply(values()[level]);
    }

    /**
     * View distance of the mod's renderers, in blocks (1.0.2): Minecraft's render distance (chunks of the video settings, or less if the
     * server allows less) x 16, times the share of the quality setting (100 / 75 / 50 %). The pieces of the mod are thus drawn as far as
     * the world itself, and no further.
     */
    public static int distance() {
        int chunks = 12;
        double share = current().distance * blockRange();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) chunks = mc.options.getEffectiveRenderDistance();
        } catch (RuntimeException ignored) {
            // options not ready yet
        }
        return Math.max(16, (int) Math.round(chunks * 16 * share));
    }

    /**
     * Rev. 18: Minecraft's own render distance in blocks (chunks x 16), not cut by the quality: the fixtures and their light are drawn
     * as far as the world (their far levels of detail keep them cheap).
     */
    public static int worldDistance() {
        int chunks = 12;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) chunks = mc.options.getEffectiveRenderDistance();
        } catch (RuntimeException ignored) {
            // options not ready yet
        }
        return Math.max(32, (int) Math.round(chunks * 16 * Math.min(1.0, blockRange())));
    }

    // rev. 4c — two client settings of the configuration screen (Mods menu > Concert Stage Craft > Config), in percent
    private static volatile int beamPct = -1, blockPct = -1;
    public static final int RANGE_MIN = 25, RANGE_MAX = 300, BLOCK_MAX = 200;

    /** Reach of the beams and of their light on the blocks, x the profile's throw (1 = as designed). */
    public static double beamRange() { if (beamPct < 0) current(); return beamPct / 100.0; }
    /** Distance at which the mod's blocks are drawn, x the share of the quality (1 = as before; at most Minecraft's own distance for the fixtures). */
    public static double blockRange() { if (blockPct < 0) current(); return blockPct / 100.0; }
    public static int beamPercent() { beamRange(); return beamPct; }
    public static int blockPercent() { blockRange(); return blockPct; }

    public static void setBeamPercent(int v) {
        beamRange();
        beamPct = Math.max(RANGE_MIN, Math.min(RANGE_MAX, v));
        saveProp("beam_range", Integer.toString(beamPct));
    }

    public static void setBlockPercent(int v) {
        blockRange();
        int n = Math.max(RANGE_MIN, Math.min(BLOCK_MAX, v));
        if (n == blockPct) return;
        blockPct = n;
        saveProp("block_range", Integer.toString(blockPct));
        clearCaches();
    }

    private static int pct(Properties p, String key, int max) {
        try { return Math.max(RANGE_MIN, Math.min(max, Integer.parseInt(p.getProperty(key, "100").trim()))); }
        catch (RuntimeException e) { return 100; }
    }

    private static void saveProp(String key, String value) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Properties p = new Properties();
            if (Files.isRegularFile(f)) try (Reader r = Files.newBufferedReader(f)) { p.load(r); }
            p.setProperty(key, value);
            try (Writer w = Files.newBufferedWriter(f)) { p.store(w, "Concert Stage Craft - client settings"); }
        } catch (IOException ignored) {
            // the setting still applies for this session
        }
    }

    /** Kept for the renderers: the argument (their old fixed distance) no longer matters, see {@link #distance()}. */
    public static int distance(int base) {
        return distance();
    }

    public static void set(RenderQuality q) {
        if (q == null) return;
        current();
        boolean wasAuto = auto;
        auto = false; controller = null;
        if (q == current && !wasAuto) return;
        if (q == current) { save(q); return; }
        current = q;
        save(q);
        clearCaches();
    }

    /** Minecraft's frame counter (the "NN fps" of its debug string, refreshed every second); 0 when unknown. */
    static int fps() {
        try {
            String s = Minecraft.getInstance().fpsString;
            int i = 0, v = 0;
            while (s != null && i < s.length() && Character.isDigit(s.charAt(i))) v = v * 10 + (s.charAt(i++) - '0');
            return i == 0 ? 0 : v;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** AUTO: a new level, not saved (the file keeps AUTO). */
    private static void apply(RenderQuality q) {
        current = q;
        clearCaches();
    }

    private static void clearCaches() {
        // meshes baked for the old setting are dropped
        StageAttachmentRenderer.clear();
        RoofTrussRenderer.clear();
        RingTrussRenderer.clear();
        RegieRoofRenderer.clear();
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("concertstage-client.properties");
    }

    private static RenderQuality load() {
        beamPct = 100; blockPct = 100;
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                Properties p = new Properties();
                try (Reader r = Files.newBufferedReader(f)) { p.load(r); }
                beamPct = pct(p, "beam_range", RANGE_MAX);
                blockPct = pct(p, "block_range", BLOCK_MAX);
                String v = p.getProperty("render_quality", "HIGH").trim().toUpperCase(java.util.Locale.ROOT);
                if (v.equals("AUTO")) { auto = true; return HIGH; }
                return valueOf(v);
            }
        } catch (IOException | RuntimeException ignored) {
            // unreadable or unknown value: default
        }
        return HIGH;
    }

    private static void save(RenderQuality q) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Properties p = new Properties();
            if (Files.isRegularFile(f)) try (Reader r = Files.newBufferedReader(f)) { p.load(r); }   // keep the other settings (gpu_meshes)
            p.setProperty("render_quality", q == null ? "AUTO" : q.name());
            p.putIfAbsent("gpu_meshes", "true");
            try (Writer w = Files.newBufferedWriter(f)) { p.store(w, "Concert Stage Craft - client settings: render_quality LOW / MEDIUM / HIGH / AUTO, gpu_meshes true / false"); }
        } catch (IOException ignored) {
            // the setting still applies for this session
        }
    }
}
