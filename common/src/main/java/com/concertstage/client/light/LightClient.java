package com.concertstage.client.light;

import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.LightServer;
import com.concertstage.light.LumicoreBlockEntity;
import com.concertstage.light.core.Bytes;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.Engine;
import com.concertstage.light.core.Live;
import com.concertstage.light.core.Show;
import com.concertstage.light.net.LightNet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Client side of the lighting: applies the commands the server sends to the local copy of each desk (asking for the full state when
 * one was missed), gives the fixture renderers their values for the frame (one evaluation per desk per frame, shared by all its
 * fixtures), and keeps the player's lighting settings ({@code config/concertstage-lighting.properties}): delay of the lights against
 * the music, beam quality, automatic performance mode.
 */
public final class LightClient {
    private LightClient() {}

    /** Lights later than the music by this many ms (the sound of the clients is buffered before it is heard). */
    public static int offsetMs = 120;
    /** 0 low, 1 medium, 2 high, 3 ultra (rev. 5): the player's choice; the automatic mode may draw less (never more). */
    public static int beamQuality = 2;
    public static boolean autoPerformance = true;
    public static boolean beams = true;
    /** Rev. 2: how much haze the beams show; rev. 16: 0 = no haze (beams almost invisible, surfaces still lit) .. 2.5 thick smoke. */
    public static float haze = 1f;
    /** Rev. 16: atmosphere presets of the desk (Atmospheric System): none, light (recommended for a concert), medium, dense. */
    public static final float[] HAZE_PRESETS = {0f, 1f, 1.5f, 2.25f};   // 1 = the calibration of the beams so far
    public static final String[] HAZE_NAMES = {"None", "Light", "Medium", "Dense"};

    public static String hazeName() {
        for (int i = 0; i < HAZE_PRESETS.length; i++) if (Math.abs(haze - HAZE_PRESETS[i]) < 0.01f) return HAZE_NAMES[i];
        return String.format(java.util.Locale.ROOT, "%.2f", haze);
    }

    public static void nextHaze() {
        int next = 0;
        for (int i = 0; i < HAZE_PRESETS.length; i++) if (haze >= HAZE_PRESETS[i] - 0.01f) next = (i + 1) % HAZE_PRESETS.length;
        haze = HAZE_PRESETS[next];
    }
    /** Rev. 2: coloured light of the beams on the blocks through Shimmer (when installed), and how many such lights at most. */
    /**
     * Rev. 17 (11 November pictures: lenses lit, almost no beam and little light on the stage): three brightness settings of the
     * light itself, on your screen only, saved with the others. 1 = the rev. 17 calibration. Beams: the light in the haze; stage
     * light: the light the fixtures project on the blocks; lens glow: the halo around a lit lens.
     */
    public static float beamBrightness = 1f, stageBrightness = 1f, lensGlow = 1f;
    /**
     * Rev. 21: light around the beams (the haze a beam lights on the truss beside it, the light a lit floor sends back up on the rig;
     * VolumeLights#spill) and bloom (the glow of bright beams and lenses, Bloom). 1 = the rev. 21 calibration, 0 = off. Properties
     * rig_light, bloom.
     */
    public static float rigLight = 1f, bloom = 1f;
    public static final float[] EFFECT_PRESETS = {0f, 0.6f, 1f, 1.5f};
    /**
     * 1.2.2: the inertia of the tungsten lamps (the conventionals and the blinders): 0 = off (the old instant dimmer), 1 =
     * realistic (a filament heats in about a tenth of a second and cools in about half a second), 2 = slow (a big 2 kW lamp).
     * A look only, on your screen; the DMX and the show are unchanged. Saved as lamp_fade.
     */
    public static int lampFade = 1;

    /** Translation key of the current {@link #lampFade} (the render settings and the desk settings read it). */
    public static String lampFadeKey() {
        return "gui.concertstage.lamp_fade_" + (lampFade <= 0 ? "off" : lampFade == 1 ? "realistic" : "slow");
    }

    /** The heat/cool time scale of {@link #lampFade}: 0 = no inertia. */
    public static float lampFadeSpeed() {
        return lampFade <= 0 ? 0f : lampFade == 1 ? 1f : com.concertstage.light.core.LampModel.SLOW;
    }

    /** Next preset of rig light / bloom (wraps, 0 = off). */
    public static float nextEffect(float v) {
        for (int i = 0; i < EFFECT_PRESETS.length; i++) if (v < EFFECT_PRESETS[i] - 0.01f) return EFFECT_PRESETS[i];
        return EFFECT_PRESETS[0];
    }
    public static final float[] BRIGHTNESS_PRESETS = {0.6f, 1f, 1.4f, 2f};

    /** Next preset of a brightness setting (wraps). */
    public static float nextBrightness(float v) {
        for (int i = 0; i < BRIGHTNESS_PRESETS.length; i++) if (v < BRIGHTNESS_PRESETS[i] - 0.01f) return BRIGHTNESS_PRESETS[i];
        return BRIGHTNESS_PRESETS[0];
    }

    public static String percent(float v) {
        return Math.round(v * 100) + " %";
    }

    public static boolean shimmer = true;
    /** Rev. 9: CSC's own coloured point lights on the stage when Shimmer is not there (see VolumeLights). */
    public static boolean volumeLights = true;
    public static int shimmerMax = 160;
    /**
     * Rev. 16b: individual settings (config/concertstage-lighting.properties, -1 = from the beam quality): projections and beams in the
     * haze drawn at most per frame, shadow maps, steps of the beams in the haze (x 0.5 .. 2), distance of the light of the fixtures.
     */
    public static int maxProjections = -1, maxVolumes = -1, maxShadows = -1;
    public static float volumeSteps = 1f;
    /** Rev. 24: resolution of the beams in the haze: 0 auto (half in low / medium quality), 1 full, 2 half. Property haze_resolution. */
    public static int hazeResolution = 0;
    /** Rev. 18: 0 = auto (the render distance, 160 to 512 blocks, see FarView); the old default 160 also reads as auto. */
    public static int lightDistance = 0;
    /** Rev. 19: the light of the fixtures on the blocks is tone mapped (no clipped white under several fixtures). Property tone_mapping. */
    public static boolean toneMapping = true;

    /** Distance (blocks) up to which the fixtures draw their beam and their light. */
    public static int lightDistance() {
        return FarView.lightDistance(lightDistance, com.concertstage.client.RenderQuality.worldDistance());
    }

    /** A budget of the quality level, lowered by its individual setting when there is one. */
    public static int budget(int setting, int levelMax) {
        return setting < 0 ? levelMax : Math.min(setting, levelMax);
    }          // rev. 8: lens + rig + beam lights for ~50 fixtures (Shimmer holds 2048)
    private static boolean loaded;
    private static final Map<BlockPos, Long> SYNC_ASKED = new HashMap<>();
    /** Last message of the server for a desk (shown in the desk screen). */
    public static String lastMessage = "";
    public static long lastMessageTime;
    /** Timeline preview of a desk (local only): music time forced while the operator scrubs the timeline. */
    public static BlockPos previewDesk;
    public static long previewMs = -1;

    public static void init() {
        LightNet.setClientHandler(LightClient::receive);
        load();
    }

    public static void reset() {
        SYNC_ASKED.clear();
        REMOTE.clear();
        REMOTE_ASKED.clear();
        ShimmerLights.clear();
        previewDesk = null;
        previewMs = -1;
    }

    // ------------------------------------------------------------ messages
    static void receive(byte[] data) {
        Bytes.Reader r = new Bytes.Reader(data);
        int type = r.varInt();
        BlockPos pos = LightNet.readPos(r);
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        LumicoreBlockEntity desk = desk(level, pos);
        if (desk == null && type == LightNet.S_FULL) {                // 1.2.0 beta 2: a desk out of view, followed for its fixtures
            desk = new LumicoreBlockEntity(pos, com.concertstage.StageContent.LUMICORE.defaultBlockState());
            desk.setLevel(level);
            REMOTE.put(pos.immutable(), desk);
        }
        if (desk == null) return;
        switch (type) {
            case LightNet.S_CMD -> {
                long rev = r.varLong(), now = r.varLong();
                byte[] cmd = r.bytes(LightNet.MAX_MESSAGE);
                if (!r.ok()) return;
                if (desk.live.revision != rev - 1) { askSync(pos); return; }
                int res = Commands.apply(desk.show, desk.live, cmd, now, LightServer.stage(desk));
                if (res == Commands.REJECTED) askSync(pos);
            }
            case LightNet.S_FULL -> {
                Show s = Show.fromBytes(r.bytes(LightNet.MAX_MESSAGE));
                Live l = Live.fromBytes(r.bytes(LightNet.MAX_MESSAGE));
                int n = r.count(LumicoreBlockEntity.MAX_LIBRARY * 4);
                java.util.List<String> names = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) names.add(r.str(1024));
                String status = r.str(4096);
                if (!r.ok() || s == null) return;
                s.edits = desk.show.edits + 1;
                desk.show = s;
                desk.live = l;
                desk.library.clear();
                for (String nm : names) desk.library.put(nm, new byte[0]);
                desk.status = status;
                // 1.2.0: the id of the desk (its fixtures name it): a copy made here for a desk out of view had a new one, and
                // patched none of them (visual test v139: "followed copy true, output NONE")
                long id = r.varLong();
                if (r.ok() && id != 0) desk.deskId = id;
            }
            case LightNet.S_DMX -> com.concertstage.light.ExternalControl.receiveDmx(desk, r);
            case LightNet.S_EXT -> ExternalStatus.receive(pos, r);
            case LightNet.S_STATUS -> {
                String key = r.str(256);
                int n = r.count(8);
                Object[] args = new Object[n];
                for (int i = 0; i < n; i++) args[i] = r.str(256);
                Component c = Component.translatable(key, args);
                lastMessage = c.getString();
                lastMessageTime = System.currentTimeMillis();
                var mc = Minecraft.getInstance();
                if (mc.gui != null) mc.gui.setOverlayMessage(c, false);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------ 1.2.0 beta 2: desks out of view (multiplayer)
    /**
     * Copies of the desks this game does not have loaded (out of its view distance) while their fixtures are in view: kept up to date by
     * the server like a loaded desk (LightServer.subscribe), asked again every {@link #REMOTE_RENEW_MS}. Without them, in multiplayer, a
     * player far from the desk saw no light at all.
     */
    static final Map<BlockPos, LumicoreBlockEntity> REMOTE = new HashMap<>();
    static final Map<BlockPos, Long> REMOTE_ASKED = new HashMap<>();
    static final long REMOTE_RENEW_MS = 5000;

    /** The desk at {@code pos}: the loaded one, else the followed copy (null if neither). */
    public static LumicoreBlockEntity desk(Level level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LumicoreBlockEntity d) {
            if (!REMOTE.isEmpty() && REMOTE.remove(pos) != null) REMOTE_ASKED.remove(pos);
            return d;
        }
        LumicoreBlockEntity d = REMOTE.get(pos);
        return d != null && d.getLevel() == level ? d : null;
    }

    /** A fixture needs a desk that is not loaded here: follow it (asked now, then renewed every 5 s while needed). */
    static LumicoreBlockEntity follow(Level level, BlockPos pos) {
        long now = System.currentTimeMillis();
        Long last = REMOTE_ASKED.get(pos);
        if (last == null || now - last >= (REMOTE.containsKey(pos) ? REMOTE_RENEW_MS : 1000)) {
            REMOTE_ASKED.put(pos.immutable(), now);
            LightNet.sync(pos);
        }
        LumicoreBlockEntity d = REMOTE.get(pos);
        if (d != null && d.getLevel() != level) { REMOTE.remove(pos); return null; }
        return d;
    }

    public static void askSync(BlockPos pos) {
        long now = System.currentTimeMillis();
        Long last = SYNC_ASKED.get(pos);
        if (last != null && now - last < 1000) return;
        SYNC_ASKED.put(pos.immutable(), now);
        LightNet.sync(pos);
    }

    // ------------------------------------------------------------ values for the renderers
    /** Values of a fixture this frame, or null if its desk is not loaded here. */
    public static Engine.Output output(Level level, FixtureBlockEntity fe, float partial) {
        BlockPos c = fe.console();
        if (c == null) return null;
        LumicoreBlockEntity desk = level.isLoaded(c) && level.getBlockEntity(c) instanceof LumicoreBlockEntity d ? d : follow(level, c);
        if (desk == null || !desk.patches(fe)) return null;
        return outputs(level, desk, partial).get(fe.fid());
    }

    /**
     * rev. 1: the output of a fixture for a LiveVision camera image: the one the main view computed for its last frame (the desk
     * evaluates its whole show once per frame; a camera image drawn between two frames with its own time would evaluate it again,
     * about 0.25 ms per fixture in the author's show). Computed only when the main view has not done it for 100 ms.
     */
    public static Engine.Output feedOutput(Level level, FixtureBlockEntity fe, float partial) {
        BlockPos c = fe.console();
        if (c == null) return null;
        LumicoreBlockEntity desk = level.isLoaded(c) && level.getBlockEntity(c) instanceof LumicoreBlockEntity d ? d : follow(level, c);
        if (desk == null || !desk.patches(fe)) return null;
        if (System.nanoTime() - desk.evalNanos < 100_000_000L) return desk.lastOutputs().get(fe.fid());
        return outputs(level, desk, partial).get(fe.fid());
    }

    public static Map<Integer, Engine.Output> outputs(Level level, LumicoreBlockEntity desk, float partial) {
        double gameTime = level.getGameTime() + partial;
        long frame = Double.doubleToLongBits(gameTime);
        Engine.Music music = desk.getBlockPos().equals(previewDesk) && previewMs >= 0
            ? new Engine.Music(true, desk.show.timeline.trackSha1, false, previewMs)
            : LightServer.music(level, desk.live, gameTime, offsetMs);
        return desk.outputs(frame, (long) (gameTime * 50), music);
    }

    // ------------------------------------------------------------ settings
    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("concertstage-lighting.properties");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            Properties p = new Properties();
            try (var rd = Files.newBufferedReader(f)) { p.load(rd); }
            offsetMs = Math.max(-1000, Math.min(1000, Integer.parseInt(p.getProperty("light_delay_ms", "120").trim())));
            beamQuality = Math.max(0, Math.min(3, Integer.parseInt(p.getProperty("beam_quality", "2").trim())));
            autoPerformance = !"false".equalsIgnoreCase(p.getProperty("auto_performance", "true").trim());
            beams = !"false".equalsIgnoreCase(p.getProperty("beams", "true").trim());
            haze = Math.max(0f, Math.min(2.5f, Float.parseFloat(p.getProperty("beam_haze", "1").trim())));
            shimmer = !"false".equalsIgnoreCase(p.getProperty("shimmer_lights", "true").trim());
            shimmerMax = Math.max(0, Math.min(512, Integer.parseInt(p.getProperty("shimmer_max_lights", "160").trim())));
            if (shimmerMax == 48 || shimmerMax == 64) shimmerMax = 160;
            volumeLights = Boolean.parseBoolean(p.getProperty("stage_lights", "true").trim());
            maxProjections = Integer.parseInt(p.getProperty("max_projections", "-1").trim());
            maxVolumes = Integer.parseInt(p.getProperty("max_beams_in_haze", "-1").trim());
            maxShadows = Integer.parseInt(p.getProperty("max_shadow_maps", "-1").trim());
            volumeSteps = Math.max(0.5f, Math.min(2f, Float.parseFloat(p.getProperty("beam_haze_steps", "1").trim())));
            toneMapping = !"false".equalsIgnoreCase(p.getProperty("tone_mapping", "true").trim());
            String hr = p.getProperty("haze_resolution", "auto").trim().toLowerCase(java.util.Locale.ROOT);
            hazeResolution = hr.equals("full") ? 1 : hr.equals("half") ? 2 : 0;
            String ld = p.getProperty("light_distance", "auto").trim();
            lightDistance = ld.equalsIgnoreCase("auto") ? 0 : Math.max(32, Math.min(512, Integer.parseInt(ld)));
            if (lightDistance == 160) lightDistance = 0;                 // rev. 18: the old default becomes auto
            beamBrightness = clampBrightness(p.getProperty("beam_brightness", "1"));
            stageBrightness = clampBrightness(p.getProperty("stage_light_brightness", "1"));
            lensGlow = clampBrightness(p.getProperty("lens_glow", "1"));   // old defaults saved by earlier versions: move to the new one
            rigLight = clampEffect(p.getProperty("rig_light", "1"));
            bloom = clampEffect(p.getProperty("bloom", "1"));
            try {
                lampFade = Math.max(0, Math.min(2, Integer.parseInt(p.getProperty("lamp_fade", "1").trim())));
            } catch (NumberFormatException e) {
                lampFade = 1;
            }
        } catch (Exception ignored) {
            // defaults
        }
    }

    private static float clampEffect(String v) {
        try {
            float f = Float.parseFloat(v.trim());
            return Float.isFinite(f) ? Math.max(0f, Math.min(2f, f)) : 1f;
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    private static float clampBrightness(String v) {
        try {
            float f = Float.parseFloat(v.trim());
            return Float.isFinite(f) ? Math.max(0.25f, Math.min(3f, f)) : 1f;
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Properties p = new Properties();
            p.setProperty("light_delay_ms", String.valueOf(offsetMs));
            p.setProperty("beam_quality", String.valueOf(beamQuality));
            p.setProperty("auto_performance", String.valueOf(autoPerformance));
            p.setProperty("beams", String.valueOf(beams));
            p.setProperty("beam_haze", String.valueOf(haze));
            p.setProperty("shimmer_lights", String.valueOf(shimmer));
            p.setProperty("shimmer_max_lights", String.valueOf(shimmerMax));
            p.setProperty("stage_lights", String.valueOf(volumeLights));
            p.setProperty("max_projections", String.valueOf(maxProjections));
            p.setProperty("max_beams_in_haze", String.valueOf(maxVolumes));
            p.setProperty("max_shadow_maps", String.valueOf(maxShadows));
            p.setProperty("beam_haze_steps", String.valueOf(volumeSteps));
            p.setProperty("light_distance", lightDistance <= 0 ? "auto" : String.valueOf(lightDistance));
            p.setProperty("tone_mapping", String.valueOf(toneMapping));
            p.setProperty("haze_resolution", hazeResolution == 1 ? "full" : hazeResolution == 2 ? "half" : "auto");
            p.setProperty("beam_brightness", String.valueOf(beamBrightness));
            p.setProperty("stage_light_brightness", String.valueOf(stageBrightness));
            p.setProperty("lens_glow", String.valueOf(lensGlow));
            p.setProperty("rig_light", String.valueOf(rigLight));
            p.setProperty("bloom", String.valueOf(bloom));
            p.setProperty("lamp_fade", String.valueOf(lampFade));
            try (var w = Files.newBufferedWriter(f)) { p.store(w, "Concert Stage Craft - lighting (client)"); }
        } catch (Exception ignored) {
            // read-only folder: settings kept for this session
        }
    }
}
