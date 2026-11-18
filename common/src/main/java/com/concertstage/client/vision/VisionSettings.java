package com.concertstage.client.vision;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * CSC 1.3.0 - graphics settings of LiveVision and Dynamic Truss, per player ({@code config/concertstage-livevision.properties}, never
 * sent to the server). Four profiles (LOW, BALANCED, HIGH, ULTRA) set every value; changing one value by hand makes the profile CUSTOM.
 *
 * <ul>
 * <li>LED distance: how far LED walls are drawn (blocks); with "long range" they are also drawn beyond the loaded chunks, from the
 *     wall registry the server sends; "unlimited distance (experimental)" removes CSC's own limit (the far plane of the game remains).</li>
 * <li>Video resolution / video FPS: size and refresh rate of the camera images (a camera is a second view of the world).</li>
 * <li>Active cameras: how many camera images may be rendered at the same time (PROGRAM first, then PREVIEW, then the regie).</li>
 * <li>Truss / model detail: distances of the levels of detail (LOD0 to LOD3 of the Blender files).</li>
 * <li>Memory budget: images and videos decoded for the walls (MB); beyond it, the least recently shown media are released.</li>
 * </ul>
 */
public final class VisionSettings {
    public enum Profile { LOW, BALANCED, HIGH, ULTRA, CUSTOM }

    public static Profile profile = Profile.BALANCED;
    public static int ledDistance = 384;
    public static int videoHeight = 270;
    public static int videoFps = 10;
    public static int activeCameras = 2;
    /** Multiplies the LOD distances of the trusses / of the 3D models (0.6 LOW to 2.2 ULTRA). */
    public static float trussDetail = 1f, modelDetail = 1f;
    public static int memoryBudgetMb = 192;
    public static boolean longRange = true;
    public static boolean unlimited = false;
    /** rev. 1: the light of the fixtures (lens glow, beams, laser lines, spots) in the camera images; the bodies are always drawn.
     * Kept in the settings file ({@code feed_light}), whatever the profile. */
    public static boolean feedBeams = true;
    /** Rev. 3b: camera images with the full stage lights of the main view (projections, haze, bloom); false = the simplified light. */
    public static boolean feedFull = true;
    /** 1.2.1: particles (fireworks, smoke...) and the other mods' world rendering (their beams, effects) in the camera images. */
    public static boolean feedParticles = true, feedOtherMods = true;
    /** Rev. 3: share of the frame time the camera images may take (%, besides the idle time under a frame limit). Kept whatever the
     * profile ({@code feed_impact}). */
    public static int feedImpact = 3;

    private static final String FILE = "concertstage-livevision.properties";
    private static boolean loaded;

    private VisionSettings() {}

    public static void apply(Profile p) {
        profile = p;
        switch (p) {
            case LOW -> set(192, 180, 5, 1, 0.6f, 0.6f, 96, false);
            case BALANCED -> set(384, 270, 10, 2, 1f, 1f, 192, true);
            case HIGH -> set(768, 405, 15, 4, 1.5f, 1.5f, 384, true);
            case ULTRA -> set(1536, 540, 20, 6, 2.2f, 2.2f, 768, true);
            default -> { }
        }
    }

    private static void set(int dist, int h, int fps, int cams, float truss, float model, int mem, boolean far) {
        ledDistance = dist; videoHeight = h; videoFps = fps; activeCameras = cams; trussDetail = truss; modelDetail = model;
        memoryBudgetMb = mem; longRange = far;
    }

    /** Level of detail of a truss at this distance (blocks). */
    public static int trussLod(double dist) {
        double k = trussDetail;
        return dist < 20 * k ? 0 : dist < 48 * k ? 1 : dist < 110 * k ? 2 : 3;
    }

    /** Level of detail of a LiveVision model (cameras, processor, panels) at this distance, for a model about {@code size} metres. */
    public static int modelLod(double dist, double size) {
        double k = modelDetail * Math.max(0.6, Math.min(3.0, size));
        return dist < 10 * k ? 0 : dist < 28 * k ? 1 : dist < 70 * k ? 2 : 3;
    }

    /** Height in pixels of the image of a wall seen at this distance whose height is {@code wallBlocks} (adaptive resolution). */
    public static int wallTextureHeight(double dist, int wallBlocks) {
        int max = videoHeight * 2;
        double px = wallBlocks * 1800.0 / Math.max(8, dist);               // about what the screen can show of it
        int h = 32;
        while (h < px && h < max) h *= 2;
        return Math.min(max, h);
    }

    /** Distance tier of a wall: 0 NEAR, 1 MEDIUM, 2 FAR, 3 VERY FAR. */
    public static int tier(double dist) {
        return dist < 48 ? 0 : dist < 160 ? 1 : dist < 480 ? 2 : 3;
    }

    /** Frames between two refreshes of a wall's media at this tier (FAR walls refresh less often). */
    public static int refreshDivider(int tier) {
        return tier <= 1 ? 1 : tier == 2 ? 2 : 4;
    }

    public static double maxWallDistance() {
        return unlimited ? 1e7 : ledDistance;
    }

    // ------------------------------------------------------------------ file
    private static Path path() {
        try {
            return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE);
        } catch (Throwable t) {
            return Path.of("config", FILE);
        }
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path p = path();
        if (!Files.exists(p)) { apply(Profile.BALANCED); return; }
        Properties pr = new Properties();
        try (Reader r = Files.newBufferedReader(p)) {
            pr.load(r);
            feedBeams = Boolean.parseBoolean(pr.getProperty("feed_light", "true"));
            feedFull = Boolean.parseBoolean(pr.getProperty("feed_full_light", "true"));
            feedParticles = Boolean.parseBoolean(pr.getProperty("feed_particles", "true"));
            feedOtherMods = Boolean.parseBoolean(pr.getProperty("feed_other_mods", "true"));
            feedImpact = clamp(Integer.parseInt(pr.getProperty("feed_impact", "3")), 1, 25);
            profile = Profile.valueOf(pr.getProperty("profile", "BALANCED"));
            if (profile != Profile.CUSTOM) { apply(profile); return; }
            ledDistance = clamp(Integer.parseInt(pr.getProperty("led_distance", "384")), 64, 4096);
            videoHeight = clamp(Integer.parseInt(pr.getProperty("video_height", "270")), 90, 1080);
            videoFps = clamp(Integer.parseInt(pr.getProperty("video_fps", "10")), 1, 60);
            activeCameras = clamp(Integer.parseInt(pr.getProperty("active_cameras", "2")), 0, 8);
            trussDetail = Math.max(0.3f, Math.min(4f, Float.parseFloat(pr.getProperty("truss_detail", "1"))));
            modelDetail = Math.max(0.3f, Math.min(4f, Float.parseFloat(pr.getProperty("model_detail", "1"))));
            memoryBudgetMb = clamp(Integer.parseInt(pr.getProperty("memory_mb", "192")), 32, 4096);
            longRange = Boolean.parseBoolean(pr.getProperty("long_range", "true"));
            unlimited = Boolean.parseBoolean(pr.getProperty("unlimited_distance", "false"));
        } catch (Exception e) {
            apply(Profile.BALANCED);
        }
    }

    public static void save() {
        Properties pr = new Properties();
        pr.setProperty("profile", profile.name());
        pr.setProperty("led_distance", Integer.toString(ledDistance));
        pr.setProperty("video_height", Integer.toString(videoHeight));
        pr.setProperty("video_fps", Integer.toString(videoFps));
        pr.setProperty("active_cameras", Integer.toString(activeCameras));
        pr.setProperty("truss_detail", Float.toString(trussDetail));
        pr.setProperty("model_detail", Float.toString(modelDetail));
        pr.setProperty("memory_mb", Integer.toString(memoryBudgetMb));
        pr.setProperty("long_range", Boolean.toString(longRange));
        pr.setProperty("unlimited_distance", Boolean.toString(unlimited));
        pr.setProperty("feed_light", Boolean.toString(feedBeams));
        pr.setProperty("feed_full_light", Boolean.toString(feedFull));
        pr.setProperty("feed_particles", Boolean.toString(feedParticles));
        pr.setProperty("feed_other_mods", Boolean.toString(feedOtherMods));
        pr.setProperty("feed_impact", Integer.toString(feedImpact));
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) { pr.store(w, "CSC 1.2.0 LiveVision & Dynamic Truss - graphics settings of this player"); }
        } catch (Exception ignored) { }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
