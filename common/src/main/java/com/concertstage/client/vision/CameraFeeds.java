package com.concertstage.client.vision;

import com.concertstage.StageContent;
import com.concertstage.client.ClientCulling;
import com.concertstage.vision.CameraBlockEntity;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * CSC 1.3.0 - the camera images: a real second view of the world from each camera's lens (position and direction from the Blender
 * LENS_AXIS point, moved by pan / tilt / boom / zoom), drawn into its own frame buffer at the profile's video size and rate.
 *
 * <p>What a camera image contains: the sky, the terrain of the chunks loaded by this client (solid, cutout, translucent), entities
 * (players, mobs) within 96 blocks, the block entities of the mod and of the game within 64 blocks (trusses, panels, speakers...),
 * the LED walls (showing their previous image: no feedback loop). rev. 1: the stage fixtures within 128 blocks, with their lens glow,
 * their beams in the haze (soft ribbons, the laser lines) and a soft spot where each beam lands ({@code FixtureRenderer.renderFeed}:
 * a few quads per fixture, none of the volumetric passes of the main view). Not drawn in camera images: the haze volumes, shadow maps
 * and projections of the main view, clouds, weather, particles.
 *
 * <p>Images are rendered in the client tick (at most 20 per second), only for cameras someone looks at (a wall's PROGRAM or a regie
 * monitor), at most {@link VisionSettings#activeCameras} at a time, the most recently asked first. A shader pack turns this off.
 */
public final class CameraFeeds {
    public static volatile boolean rendering;
    /** rev. 1: most fixtures drawn in one camera image (a cap for very large rigs). */
    private static final int MAX_FIXTURES = 160;
    /** 1.3.0 rev. 1, visual tests only: the camera images without the fixtures (as in 1.3.0), to measure what they cost. */
    public static boolean testNoFixtures;
    private static final Map<Long, Feed> FEEDS = new HashMap<>();
    private static final FeedCamera CAMERA = new FeedCamera();
    /** Rev. 3b: this image is drawn with the full stage lights; they failed once (simplified light for the session). */
    private static boolean full, fullFailed;
    private static Field viewAreaField;
    private static boolean failed, searched, skyFailed, logged;
    private static long failedAt;
    private static Boolean replaced;

    /** Rev. 2: Embeddium, Sodium (or a port) replace the game's chunk meshes. */
    private static boolean replacedChunkRenderer() {
        if (Boolean.getBoolean("csc.feed.ownTerrain")) return true;     // the visual test checks this path without Embeddium
        if (replaced == null) {
            boolean r = false;
            for (String c : new String[] {"me.jellysquid.mods.sodium.client.SodiumClientMod", "org.embeddedt.embeddium.impl.Embeddium",
                "org.embeddedt.embeddium.api.EmbeddiumApi", "net.caffeinemc.mods.sodium.client.SodiumClientMod"}) {
                try { Class.forName(c, false, CameraFeeds.class.getClassLoader()); r = true; break; } catch (Throwable ignored) { }
            }
            replaced = r || Boolean.getBoolean("csc.feed.ownTerrain");      // the visual test checks this path without Embeddium
        }
        return replaced;
    }
    private static String status = "";

    /**
     * 1.2.1 (asked 15 November: "the fixtures, fireworks... of Theatrical ExtraLights and of any mod must show in the cameras"): the
     * loader's world-render events (Forge RenderLevelStageEvent, Fabric WorldRenderEvents) fired again for a camera image, so the
     * mods that draw their beams or effects there draw them into the image too, from the lens. Set by the Forge / Fabric client.
     */
    public interface WorldHooks {
        /** stage: {@link #AFTER_ENTITIES}, {@link #AFTER_BLOCK_ENTITIES}, {@link #AFTER_TRANSLUCENT}, {@link #AFTER_PARTICLES}. */
        void stage(int stage, PoseStack pose, Matrix4f projection, Camera camera, Frustum frustum, float partial, int ticks);
    }
    public static final int AFTER_ENTITIES = 0, AFTER_BLOCK_ENTITIES = 1, AFTER_TRANSLUCENT = 2, AFTER_PARTICLES = 3;
    private static WorldHooks hooks;
    /** A hook or the particles failed once: left out of the images for the session (logged once). */
    private static boolean hooksFailed, particlesFailed;

    public static void setWorldHooks(WorldHooks h) { hooks = h; }

    private static void hook(int stage, PoseStack pose, Matrix4f proj, Frustum frustum, float partial, ClientLevel level) {
        if (hooks == null || hooksFailed || !VisionSettings.feedOtherMods) return;
        try {
            pose.pushPose();
            hooks.stage(stage, pose, proj, CAMERA, frustum, partial, (int) level.getGameTime());
            pose.popPose();
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } catch (Throwable t) {
            hooksFailed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage LiveVision: the other mods' world rendering left out of the camera images", t);
        }
    }

    private CameraFeeds() {}

    private static final class Feed {
        final BlockPos pos;
        TextureTarget target;
        long wanted, rendered;
        /** Rev. 3: what the walls showing it ask (largest this second; 0 = the LiveVision settings) and what it gets now. */
        int askHeight, askFps, nextHeight, nextFps, height = 270;
        long askedAt;
        /** Cost of one image (ns, average of the CPU time and of the frame time it added) and images of the last second. */
        double costNs = 3_000_000;
        int count, achieved;
        long countFrom, adaptAt;
        /** Rev. 3: the height before the last reduction and the cost measured there (0 = none pending); lowest height worth lowering to. */
        int prevHeight, floor = 144, lastWant;
        double prevCost;
        Feed(BlockPos pos) { this.pos = pos; }

        /** Rev. 4c: quality / images per second set on the camera itself (0 = the screens decide); set = kept whatever it costs. */
        int camHeight, camFps;
        boolean fixed() { return camHeight > 0 || camFps > 0; }
        int wantHeight() { return Math.max(144, Math.min(1080, camHeight > 0 ? camHeight : askHeight > 0 ? askHeight : VisionSettings.videoHeight)); }
        int wantFps() { return Math.max(1, Math.min(60, camFps > 0 ? camFps : askFps > 0 ? askFps : VisionSettings.videoFps)); }
    }

    // ------------------------------------------------------------------ rev. 3: frame budget
    /**
     * Rev. 3 (asked 13 November: "the quality and the frame rate of the image on a screen, without any FPS drop, on every computer"):
     * the images are rendered one per frame at most, from a budget of frame time: {@link VisionSettings#feedImpact} % of the frame time
     * measured without camera images, plus most of the idle time when the game is held by its frame limit or V-Sync. A feed that does
     * not fit gets fewer images per second first, then a lower resolution; it goes back up when there is room. The cost of an image is
     * measured (its CPU time and the frame time it added, which includes the GPU), on this computer.
     */
    private static long lastFrameNs, frameStartNs;
    private static double baseMs = 16, withMs = 16, tokensNs;
    /** 1.2.0 beta 2b: share of the frame time all camera images may take at most, even for a camera with its own rate. */
    private static final double FIXED_SHARE = 0.30;
    private static double fixedTokensNs;
    /**
     * Rev. 3b: time the game really works on a frame without camera image (render tick start to end, before the buffer swap and the
     * wait of the frame limit / V-Sync). The frame time itself includes that wait: under a limit of 60 fps it read 16.7 ms, no room
     * was seen and a wall asking 60 images/s got 0 to 2 (13 November, the author's scene). -1 = not measured (Fabric, tick-driven).
     */
    private static double workMs = -1;
    private static boolean frameHadFeed;
    private static boolean lastHadFeed, perFrame;
    /** Measured: milliseconds per frame without images, extra ms of a frame with one, budget of a frame (ms). For the screens. */
    public static double statBaseMs, statExtraMs, statBudgetMs, statWorkMs;

    /** Rev. 3: a wall shows this camera with these settings (0 = automatic). Called every frame by the walls. */
    public static void want(BlockPos pos, int height, int fps) {
        if (pos == null) return;
        Feed f = FEEDS.computeIfAbsent(pos.asLong(), k -> new Feed(pos));
        long now = System.currentTimeMillis();
        // largest asked over one second, then that second's value is the one used (a wall changed to a lower quality is followed)
        if (now - f.askedAt > 1000) { f.askHeight = f.nextHeight; f.askFps = f.nextFps; f.nextHeight = 0; f.nextFps = 0; f.askedAt = now; }
        f.nextHeight = Math.max(f.nextHeight, height);
        f.nextFps = Math.max(f.nextFps, fps);
        if (f.askHeight == 0 && f.askFps == 0) { f.askHeight = f.nextHeight; f.askFps = f.nextFps; }
    }

    /** Rev. 3b, tests: the lens of a camera (x, y, z, yaw, pitch, zoom) or null. */
    public static double[] lensOf(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && pos != null && mc.level.getBlockEntity(pos) instanceof CameraBlockEntity be ? lens(be, mc.getFrameTime()) : null;
    }

    /** Rev. 3b, tests: writes the last image of a camera to a PNG file (render thread). Returns false when there is none. */
    public static boolean saveImage(BlockPos pos, java.nio.file.Path file) {
        Feed f = pos == null ? null : FEEDS.get(pos.asLong());
        if (f == null || f.target == null || f.rendered == 0) return false;
        try (com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(f.target.width, f.target.height, false)) {
            RenderSystem.bindTexture(f.target.getColorTextureId());
            img.downloadTexture(0, false);
            img.flipY();
            java.nio.file.Files.createDirectories(file.getParent());
            img.writeToFile(file);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Height and images per second a camera image really has now ({0, 0} when it is not rendered). */
    public static int[] actual(BlockPos pos) {
        Feed f = pos == null ? null : FEEDS.get(pos.asLong());
        return f == null || f.rendered == 0 ? new int[] {0, 0} : new int[] {f.height, f.achieved};
    }

    /** Rev. 3, Forge: every frame before the world (RenderTickEvent START). The images then follow the frames, not the 20 ticks. */
    public static void perFrame() {
        perFrame = true;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (lastFrameNs != 0) {
            double dt = Math.min(250, (now - lastFrameNs) / 1e6);
            if (lastHadFeed) withMs += (dt - withMs) * 0.15;
            else baseMs += (dt - baseMs) * 0.05;
        }
        lastFrameNs = now;
        frameStartNs = now;
        lastHadFeed = false;
        if (mc.level == null || mc.isPaused()) return;
        schedule(mc);
    }

    /** Rev. 3b, Forge: end of the frame's rendering (RenderTickEvent END), before the swap and the frame-limit wait. */
    public static void frameEnd() {
        if (frameStartNs == 0) return;
        double ms = Math.min(250, (System.nanoTime() - frameStartNs) / 1e6);
        if (!lastHadFeed) workMs = workMs < 0 ? ms : workMs + (ms - workMs) * 0.05;
    }

    private static void schedule(Minecraft mc) {
        if (failed || rendering || mc.level == null) return;
        if (com.concertstage.client.GpuMesh.shaderPackInUse()) { status = "shaderpack"; return; }
        // budget of this frame: a share of the frame, plus the idle time below the frame limit / V-Sync
        int limit = mc.options.framerateLimit().get();
        double period = mc.options.enableVsync().get() ? 1000.0 / Math.max(30, refreshRate(mc)) : limit >= 260 ? 0 : 1000.0 / Math.max(10, limit);
        // rev. 3b: the room under the limit is measured on the work of a frame, not on its length (which the limit stretches)
        double work = workMs >= 0 && perFrame ? Math.min(workMs, baseMs) : baseMs;
        double headroom = Math.max(0, period - work) * 0.7;
        double budgetMs = VisionSettings.feedImpact / 100.0 * baseMs + headroom;
        double extraMs = Math.max(0, withMs - baseMs);
        statBaseMs = baseMs; statExtraMs = extraMs; statBudgetMs = budgetMs; statWorkMs = work;
        double perFrameNs = budgetMs * 1e6 * (perFrame ? 1 : 3);          // tick-driven (Fabric): a tick spans about 3 frames
        tokensNs = Math.min(tokensNs + perFrameNs, perFrameNs + 2 * maxCost());
        long nowMs = System.currentTimeMillis();
        List<Feed> live = new ArrayList<>();
        for (Feed f : FEEDS.values()) if (nowMs - f.wanted < 1500) live.add(f);
        live.sort((a, b) -> Long.compare(b.wanted, a.wanted));
        int max = VisionSettings.activeCameras;
        status = live.size() > max ? "limited " + max + "/" + live.size() : "";
        Feed pick = null;
        double late = 0;
        for (int i = 0; i < Math.min(max, live.size()); i++) {
            Feed f = live.get(i);
            if (nowMs - f.countFrom >= 1000) { f.achieved = f.count; f.count = 0; f.countFrom = nowMs; adapt(f, nowMs); }
            // rev. 3b: half a frame of tolerance: an image asked every frame (60 i/s at 60 fps) was due 16.7 ms after the last one, the frame
            // came 16 or 17 ms later and every other one was skipped (40 i/s measured)
            if (mc.level.getBlockEntity(f.pos) instanceof CameraBlockEntity cb) { f.camHeight = cb.videoHeight; f.camFps = cb.videoFps; }
            double period1 = 1000.0 / f.wantFps(), over = (nowMs - f.rendered + 0.5 * Math.min(baseMs, period1)) / period1;
            // rev. 4c: a camera with its own quality / images per second comes first and is not held by the budget
            if (over >= 1 && f.fixed()) over += 1000;
            if (over >= 1 && over > late) { late = over; pick = f; }
        }
        // 1.2.0 beta 2b (perf, 14 November: a camera fixed at its own rate took 13 ms of a 22 ms frame, the game fell to 45 fps): a camera
        // with its own quality / rate keeps its priority, but all the images together never take more than FIXED_SHARE of the frame
        // time (measured without images); above it the camera gives fewer images per second instead of the game losing frames
        fixedTokensNs = Math.min(fixedTokensNs + FIXED_SHARE * baseMs * 1e6 * (perFrame ? 1 : 3), FIXED_SHARE * baseMs * 1e6 * (perFrame ? 1 : 3) + 2 * maxCost());
        if (pick != null && pick.fixed() && fixedTokensNs < pick.costNs) return;
        if (pick == null || (!pick.fixed() && tokensNs < pick.costNs)) return;
        if (!(mc.level.getBlockEntity(pick.pos) instanceof CameraBlockEntity be)) return;   // camera not loaded here
        long t0 = System.nanoTime();
        render(mc, pick, be);
        long cpu = System.nanoTime() - t0;
        VisionStats.feedNanos += cpu;
        VisionStats.feedsRendered++;
        double cost = Math.max(cpu, extraMs * 1e6);
        pick.costNs += (cost - pick.costNs) * 0.25;
        tokensNs -= Math.max(cpu, pick.costNs);
        if (pick.fixed()) tokensNs = Math.max(tokensNs, 0);         // rev. 4c: a forced image does not starve the other cameras
        fixedTokensNs -= Math.max(cpu, pick.costNs);
        pick.rendered = nowMs;
        pick.count++;
        lastHadFeed = true;
    }

    /** Once a second: a feed far below the images per second asked loses resolution, one with room gets it back. */
    private static void adapt(Feed f, long nowMs) {
        int want = f.wantHeight();
        if (f.fixed()) { f.height = want; f.lastWant = want; f.prevHeight = 0; return; }   // rev. 4c: the camera's own quality is kept
        // a new height asked (a wall's quality, the LiveVision settings): it is taken as is, then lowered only if that really saves time
        if (want != f.lastWant) { f.lastWant = want; f.height = want; f.floor = 144; f.prevHeight = 0; f.adaptAt = nowMs; return; }
        if (nowMs - f.adaptAt < 2500) return;
        // the last reduction did not make an image cheaper (the cost is the scene, not the pixels): the height asked comes back, and stays
        if (f.prevHeight > 0) {
            if (f.costNs > f.prevCost * 0.85) { f.floor = f.prevHeight; f.height = f.prevHeight; f.prevHeight = 0; f.adaptAt = nowMs; return; }
            f.prevHeight = 0;
        }
        int fps = f.wantFps();
        int low = Math.max(180, f.floor);
        if (f.achieved < fps * 0.6 && f.height > low) {
            f.prevHeight = f.height; f.prevCost = f.costNs;
            f.height = Math.max(low, (int) (f.height * 0.75) & ~1); f.adaptAt = nowMs;
        }
        else if (f.achieved >= fps * 0.95 && f.height < want && tokensNs > f.costNs * 1.6) { f.height = Math.min(want, (int) (f.height / 0.75) & ~1); f.adaptAt = nowMs; }
        if (f.height >= want) f.floor = Math.min(f.floor, want);
    }

    private static double maxCost() {
        double m = 2_000_000;
        for (Feed f : FEEDS.values()) m = Math.max(m, f.costNs);
        return m;
    }

    private static int refreshRate(Minecraft mc) {
        try {
            var v = mc.getWindow().getPreferredFullscreenVideoMode();
            if (v.isPresent()) return v.get().getRefreshRate();
            var mon = mc.getWindow().findBestMonitor();
            if (mon != null) return mon.getCurrentMode().getRefreshRate();
        } catch (Throwable ignored) { }
        return 60;
    }

    /** The Camera of an image: position and angles set directly. */
    static final class FeedCamera extends Camera {
        void set(Vec3 p, float yaw, float pitch, float partial) {
            // Forge's fog hook reads the camera's entity: the image is "seen by" the local player, then moved to the lens
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) setup(mc.level, mc.player, false, false, partial);
            setPosition(p);
            setRotation(yaw, pitch);
        }
    }

    public static String status() { return failed ? "error" : status; }

    /** 1.2.0 beta 2b: last image of the camera at {@code pos} without asking for it to be kept up to date (walls inside an image). */
    public static MediaLibrary.Frame peek(BlockPos pos) {
        Feed f = pos == null || failed ? null : FEEDS.get(pos.asLong());
        if (f == null || f.target == null || f.rendered == 0) return null;
        return new MediaLibrary.Frame(f.target.getColorTextureId(), true, f.target.width / (float) f.target.height);
    }

    /** Last image of the camera at {@code pos} (asks for it to be kept up to date), or null. */
    public static MediaLibrary.Frame frame(BlockPos pos) {
        if (pos == null || failed) return null;
        Feed f = FEEDS.computeIfAbsent(pos.asLong(), k -> new Feed(pos));
        f.wanted = System.currentTimeMillis();
        if (f.target == null || f.rendered == 0) return null;
        return new MediaLibrary.Frame(f.target.getColorTextureId(), true, f.target.width / (float) f.target.height);
    }

    public static void clear() {
        FeedTerrain.clear();
        for (Feed f : FEEDS.values()) if (f.target != null) f.target.destroyBuffers();
        FEEDS.clear();
    }

    /** Client tick: renders the images that are due. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (failed && System.currentTimeMillis() - failedAt > 10_000) failed = false;
        if (failed || mc.level == null || rendering) return;
        if (com.concertstage.client.GpuMesh.shaderPackInUse()) { status = "shaderpack"; return; }
        long now = System.currentTimeMillis();
        // forget images nobody looked at for 10 s
        for (Iterator<Feed> it = FEEDS.values().iterator(); it.hasNext(); ) {
            Feed f = it.next();
            if (now - f.wanted > 10_000) {
                if (f.target != null) f.target.destroyBuffers();
                it.remove();
            }
        }
        if (!perFrame) schedule(mc);                               // Fabric: from the client tick, with the same budget
    }

    /** rev. 1: how far the block entities other than the fixtures are drawn in a camera image (blocks). */
    private static final double OTHERS_RANGE = 80;
    private static Field globalsField;
    private static boolean globalsSearched;
    private static final Map<Class<?>, java.lang.reflect.Method> BOX = new HashMap<>();

    /** The game's set of block entities drawn whatever their section (LevelRenderer#globalBlockEntities, found by its type). */
    @SuppressWarnings("unchecked")
    private static java.util.Set<BlockEntity> globals(LevelRenderer lr) {
        if (!globalsSearched) {
            globalsSearched = true;
            for (Field fl : LevelRenderer.class.getDeclaredFields()) {
                if (java.util.Set.class.isAssignableFrom(fl.getType()) && fl.getGenericType() instanceof java.lang.reflect.ParameterizedType pt
                    && pt.getActualTypeArguments().length == 1 && pt.getActualTypeArguments()[0] == BlockEntity.class) {
                    fl.setAccessible(true);
                    globalsField = fl;
                    break;
                }
            }
        }
        try {
            return globalsField == null ? null : (java.util.Set<BlockEntity>) globalsField.get(lr);
        } catch (Exception e) {
            return null;
        }
    }

    /** The render box of a block entity (Forge's getRenderBoundingBox, or the mod's own on Fabric), or null when it has none. */
    private static net.minecraft.world.phys.AABB renderBox(BlockEntity be) {
        java.lang.reflect.Method m;
        Class<?> c = be.getClass();
        if (BOX.containsKey(c)) m = BOX.get(c);
        else {
            try {
                m = c.getMethod("getRenderBoundingBox");
                if (m.getReturnType() != net.minecraft.world.phys.AABB.class) m = null;
            } catch (NoSuchMethodException e) {
                m = null;
            }
            BOX.put(c, m);
        }
        if (m == null) return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
        try {
            return (net.minecraft.world.phys.AABB) m.invoke(be);
        } catch (Exception e) {
            return null;
        }
    }

    private static ViewArea viewArea(LevelRenderer lr) {
        if (!searched) {
            searched = true;
            for (Field fl : LevelRenderer.class.getDeclaredFields()) {
                if (fl.getType() == ViewArea.class) { fl.setAccessible(true); viewAreaField = fl; break; }
            }
        }
        try {
            return viewAreaField == null ? null : (ViewArea) viewAreaField.get(lr);
        } catch (Exception e) {
            return null;
        }
    }

    /** Lens position and angles of the camera: {x, y, z, yaw, pitch, zoom 0..1}. */
    public static double[] lens(CameraBlockEntity be, float partial) {
        VisionModel m = VisionModel.get(be.kind().model);
        double time = be.getLevel().getGameTime() + partial;
        Matrix4f world = CameraRenderer.blockMatrix(be);
        float[] p = m == null ? null : m.point("LENS_AXIS", CameraRenderer.poser(be, time));
        org.joml.Vector4f pos, dir;
        if (p == null) { pos = new org.joml.Vector4f(0, 0.5f, -0.3f, 1); dir = new org.joml.Vector4f(0, 0, -1, 0); }
        else { pos = new org.joml.Vector4f(p[0], p[1], p[2], 1); dir = new org.joml.Vector4f(p[3], p[4], p[5], 0); }
        world.transform(pos);
        world.transform(dir);
        double len = Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
        double dx = dir.x / len, dy = dir.y / len, dz = dir.z / len;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.asin(Mth.clamp(dy, -1, 1)));
        BlockPos b = be.getBlockPos();
        // a little in front of the glass, so the camera does not see its own lens
        return new double[] {b.getX() + pos.x + dx * 0.03, b.getY() + pos.y + dy * 0.03, b.getZ() + pos.z + dz * 0.03, yaw, pitch, be.axis(2, time)};
    }

    private static void render(Minecraft mc, Feed f, CameraBlockEntity be) {
        if (f.height > f.wantHeight() || f.target == null) f.height = Math.min(f.height, f.wantHeight());
        int h = Math.max(144, f.height) & ~1, w = (h * 16 / 9) & ~1;
        if (f.target == null || f.target.width != w || f.target.height != h) {
            if (f.target != null) f.target.destroyBuffers();
            f.target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            f.target.setFilterMode(GL11.GL_LINEAR);
        }
        float partial = mc.getFrameTime();
        double[] l = lens(be, partial);
        Vec3 cam = new Vec3(l[0], l[1], l[2]);
        float yaw = (float) l[3], pitch = (float) l[4];
        float fov = (float) (60.0 * Math.pow(7.0 / 60.0, l[5]));
        float far = Math.max(64f, Math.min(mc.options.getEffectiveRenderDistance() * 16f, 320f));
        CAMERA.set(cam, yaw, pitch, partial);
        ClientLevel level = mc.level;
        LevelRenderer lr = mc.levelRenderer;
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(fov), w / (float) h, 0.05f, far * 1.5f);
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180f));
        Matrix4f view = new Matrix4f(pose.last().pose());

        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSort = RenderSystem.getVertexSorting();
        var mv = RenderSystem.getModelViewStack();
        Frustum oldCull = ClientCulling.frustum();
        Camera main = mc.gameRenderer.getMainCamera();
        rendering = true;
        boolean wasInWorld = com.concertstage.client.GpuMesh.beginFeed();   // rev. 1: the cached meshes, as in the main view
        try {
            mv.pushPose();
            mv.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
            f.target.bindWrite(true);
            FogRenderer.setupColor(CAMERA, partial, level, mc.options.getEffectiveRenderDistance(), mc.gameRenderer.getDarkenWorldAmount(partial));
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            long ph = System.nanoTime();
            // sky
            FogRenderer.setupFog(CAMERA, FogRenderer.FogMode.FOG_SKY, far, false, partial);
            // rev. 2: the game sets the position shader before drawing the sky (renderSky draws with the current shader). Without it,
            // the shader left by the last draw was used, null with ImmediatelyFast: NullPointerException, camera images turned off
            // ("NO SIGNAL", author's CraftCity profile, 12 November)
            if (!skyFailed) {
                try {
                    RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionShader);
                    lr.renderSky(pose, proj, partial, CAMERA, false, () -> FogRenderer.setupFog(CAMERA, FogRenderer.FogMode.FOG_SKY, far, false, partial));
                } catch (Throwable t) {
                    skyFailed = true;                       // the clear colour (fog colour) stands for the sky
                    com.mojang.logging.LogUtils.getLogger().warn("Concert Stage LiveVision: sky left out of the camera images", t);
                }
            }
            FogRenderer.setupFog(CAMERA, FogRenderer.FogMode.FOG_TERRAIN, Math.max(far, 32f), false, partial);
            // terrain
            Frustum frustum = new Frustum(view, proj);
            frustum.prepare(cam.x, cam.y, cam.z);
            List<ChunkRenderDispatcher.RenderChunk> chunks = new ArrayList<>();
            ViewArea va = viewArea(lr);
            if (va != null && va.chunks != null) {
                double far2 = (far + 16) * (far + 16);
                for (ChunkRenderDispatcher.RenderChunk rc : va.chunks) {
                    if (rc == null || rc.getCompiledChunk() == ChunkRenderDispatcher.CompiledChunk.UNCOMPILED) continue;
                    BlockPos o = rc.getOrigin();
                    double dx = o.getX() + 8 - cam.x, dy = o.getY() + 8 - cam.y, dz = o.getZ() + 8 - cam.z;
                    if (dx * dx + dy * dy + dz * dz > far2) continue;
                    if (!frustum.isVisible(rc.getBoundingBox())) continue;
                    chunks.add(rc);
                }
            }
            // rev. 2: Embeddium / Sodium leave the game's chunk sections empty — CSC builds the sections around the camera itself
            boolean own = chunks.isEmpty() || replacedChunkRenderer();
            List<FeedTerrain.Section> ownSections = own ? FeedTerrain.sections(level, cam, frustum, Math.min(far, 160), 4_000_000L) : List.of();
            if (own) chunks.clear();
            ph = VisionStats.phase(0, ph);
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            layer(RenderType.solid(), chunks, view, proj, cam);
            layer(RenderType.cutoutMipped(), chunks, view, proj, cam);
            layer(RenderType.cutout(), chunks, view, proj, cam);
            for (int i = 0; i < 3; i++) FeedTerrain.draw(i, ownSections, view, proj, cam);
            ph = VisionStats.phase(1, ph);
            VisionStats.feedChunks = chunks.size();
            // entities and block entities
            ClientCulling.setFrustum(frustum);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            var erd = mc.getEntityRenderDispatcher();
            erd.prepare(level, CAMERA, mc.crosshairPickEntity);
            for (Entity e : level.entitiesForRendering()) {
                if (e.distanceToSqr(cam) > 96 * 96 || !erd.shouldRender(e, frustum, cam.x, cam.y, cam.z)) continue;
                double ex = Mth.lerp(partial, e.xOld, e.getX()), ey = Mth.lerp(partial, e.yOld, e.getY()), ez = Mth.lerp(partial, e.zOld, e.getZ());
                float ry = Mth.lerp(partial, e.yRotO, e.getYRot());
                erd.render(e, ex - cam.x, ey - cam.y, ez - cam.z, ry, partial, pose, buffers, erd.getPackedLightCoords(e, partial));
            }
            buffers.endBatch();
            hook(AFTER_ENTITIES, pose, proj, frustum, partial, level);
            ph = VisionStats.phase(2, ph);
            long fixtureNanos = 0;
            int others = 0;
            var bed = mc.getBlockEntityRenderDispatcher();
            bed.prepare(level, CAMERA, mc.hitResult);
            // rev. 1: the block entities the game itself would draw for this view — those of the sections in the camera's field, plus
            // the "global" ones (big renderers: roofs, rings, tarps, the fixtures), each checked against its render box. 1.3.0 drew
            // every block entity of the 9 x 9 chunks around the camera, seen or not: 2128 of them, 60 ms per image, in the author's
            // world (CSC_Scene, 12 November).
            List<BlockEntity> visible = new ArrayList<>(256);
            if (own) {
                // rev. 2: no game sections to read them from: the block entities of the loaded chunks around the camera
                int cx = Mth.floor(cam.x) >> 4, cz = Mth.floor(cam.z) >> 4;
                for (int x = cx - 8; x <= cx + 8; x++) {
                    for (int z = cz - 8; z <= cz + 8; z++) {
                        net.minecraft.world.level.chunk.LevelChunk ch = level.getChunkSource().getChunk(x, z, false);
                        if (ch != null) visible.addAll(ch.getBlockEntities().values());
                    }
                }
            } else {
                for (ChunkRenderDispatcher.RenderChunk rc : chunks) visible.addAll(rc.getCompiledChunk().getRenderableBlockEntities());
                java.util.Set<BlockEntity> global = globals(lr);
                if (global != null) synchronized (global) { visible.addAll(global); }
            }
            int fixtures = 0;
            // rev. 3b (asked 13 November: "the camera image must look like what we see"): the fixtures drawn by their own renderer and
            // the stage lights of the main view (projections, haze, bloom) run into this image, from this lens
            full = VisionSettings.feedFull && !fullFailed && com.concertstage.client.GpuMesh.enabled();
            if (full) com.concertstage.client.light.LightTargets.beginFeed(f.target, CAMERA);
            {
                {
                    for (BlockEntity bent : visible) {
                        if (bent.isRemoved() || bent.getLevel() != level || bent.getType() == StageContent.LUMICORE_ENTITY) continue;
                        BlockPos p = bent.getBlockPos();
                        if (bent.getType() == StageContent.FIXTURE_ENTITY) {
                            // rev. 1: the fixtures and their light, up to 128 blocks, in the field of the camera (beam included)
                            if (testNoFixtures || fixtures >= MAX_FIXTURES || p.distToCenterSqr(cam) > 128 * 128
                                || !(bent instanceof com.concertstage.light.FixtureBlockEntity fe) || !frustum.isVisible(fe.getRenderBoundingBox())) continue;
                            fixtures++;
                            long f0 = System.nanoTime();
                            pose.pushPose();
                            pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
                            if (full) bed.render(bent, partial, pose, buffers);
                            else com.concertstage.client.light.FixtureRenderer.renderFeed(fe, partial, pose, cam, LevelRenderer.getLightColor(level, p),
                                VisionSettings.feedBeams);
                            pose.popPose();
                            fixtureNanos += System.nanoTime() - f0;
                            continue;
                        }
                        // 1.2.1: as far as the block entity's own renderer is drawn in the main view (other mods' beams reach far)
                        double range = OTHERS_RANGE;
                        try {
                            var ber = bed.getRenderer(bent);
                            if (ber == null) continue;
                            range = Math.max(OTHERS_RANGE, Math.min(far, ber.getViewDistance()));
                        } catch (Throwable ignored) { }
                        if (p.distToCenterSqr(cam) > range * range) continue;
                        net.minecraft.world.phys.AABB box = renderBox(bent);
                        if (box != null && !frustum.isVisible(box)) continue;
                        others++;
                        long b0 = System.nanoTime();
                        pose.pushPose();
                        pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
                        bed.render(bent, partial, pose, buffers);
                        pose.popPose();
                        VisionStats.byType(bent, System.nanoTime() - b0);
                    }
                }
            }
            buffers.endBatch();
            ph = VisionStats.phase(3, ph + fixtureNanos);       // the fixtures are counted apart
            VisionStats.phaseNanos[4] += fixtureNanos;
            VisionStats.feedOthers = others;
            if (full) com.concertstage.client.GpuMesh.flushFeedLit();
            else com.concertstage.client.GpuMesh.flushFeed();
            ph = VisionStats.phase(5, ph);
            hook(AFTER_BLOCK_ENTITIES, pose, proj, frustum, partial, level);
            layer(RenderType.translucent(), chunks, view, proj, cam);
            FeedTerrain.draw(3, ownSections, view, proj, cam);
            layer(RenderType.tripwire(), chunks, view, proj, cam);              // 1.2.1: the fifth chunk layer (tripwire, some mods' blocks)
            hook(AFTER_TRANSLUCENT, pose, proj, frustum, partial, level);
            // 1.2.1: the particles (fireworks, smoke, sparks, the particle effects of other mods), as the main view draws them
            if (VisionSettings.feedParticles && !particlesFailed) {
                try {
                    mc.particleEngine.render(pose, buffers, mc.gameRenderer.lightTexture(), CAMERA, partial);
                } catch (Throwable t) {
                    particlesFailed = true;
                    com.mojang.logging.LogUtils.getLogger().warn("Concert Stage LiveVision: particles left out of the camera images", t);
                }
                RenderSystem.applyModelViewMatrix();
            }
            hook(AFTER_PARTICLES, pose, proj, frustum, partial, level);
            VisionStats.feedChunks = own ? -ownSections.size() : chunks.size();
            ph = VisionStats.phase(6, ph);
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            LedWalls.draw(cam, view, true, Math.atan(Math.tan(Math.toRadians(fov) / 2) * Math.sqrt(1 + (16.0 / 9.0) * (16.0 / 9.0))));
            // rev. 1: the light of the fixtures (recorded by renderFeed), after the opaque scene and the walls, as in the main view
            ph = VisionStats.phase(7, ph);
            if (full) com.concertstage.client.GpuMesh.feedBeams();
            else com.concertstage.client.light.LightBatch.flush();
            buffers.endBatch();
            VisionStats.phase(8, ph);
            VisionStats.feedFixtures = fixtures;
            // the picture is opaque: the sky and the clear colour leave alpha 0, which the walls would blend away
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0f, 0f, 0f, 1f);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.colorMask(true, true, true, true);
        } catch (Throwable t) {
            if (full) {
                // rev. 3b: the full stage lights failed in a camera image: the simplified light from now on, the images go on
                fullFailed = true;
                com.mojang.logging.LogUtils.getLogger().warn("Concert Stage LiveVision: camera images back to the simplified light after an error", t);
                return;
            }
            // rev. 2: tried again after 10 s (it was off until the game was restarted); the error is logged once
            failed = true;
            failedAt = System.currentTimeMillis();
            if (!logged) com.mojang.logging.LogUtils.getLogger().warn("Concert Stage LiveVision: camera images paused after a rendering error", t);
            logged = true;
        } finally {
            com.concertstage.client.light.LightTargets.endFeed();
            full = false;
            com.concertstage.client.GpuMesh.endFeed(wasInWorld);
            rendering = false;
            ClientCulling.setFrustum(oldCull);
            try {
                mc.getEntityRenderDispatcher().prepare(mc.level, main, mc.crosshairPickEntity);
                mc.getBlockEntityRenderDispatcher().prepare(mc.level, main, mc.hitResult);
            } catch (Throwable ignored) { }
            RenderSystem.setProjectionMatrix(oldProj, oldSort);
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    /** One terrain layer of the chunks (as the game draws it: shader of the layer, chunk offset per section). */
    private static void layer(RenderType type, List<ChunkRenderDispatcher.RenderChunk> chunks, Matrix4f view, Matrix4f proj, Vec3 cam) {
        type.setupRenderState();
        ShaderInstance sh = RenderSystem.getShader();
        if (sh == null) { type.clearRenderState(); return; }
        for (int i = 0; i < 12; i++) sh.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        if (sh.MODEL_VIEW_MATRIX != null) sh.MODEL_VIEW_MATRIX.set(view);
        if (sh.PROJECTION_MATRIX != null) sh.PROJECTION_MATRIX.set(proj);
        if (sh.COLOR_MODULATOR != null) sh.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (sh.GLINT_ALPHA != null) sh.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        if (sh.FOG_START != null) sh.FOG_START.set(RenderSystem.getShaderFogStart());
        if (sh.FOG_END != null) sh.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (sh.FOG_COLOR != null) sh.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (sh.FOG_SHAPE != null) sh.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (sh.TEXTURE_MATRIX != null) sh.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (sh.GAME_TIME != null) sh.GAME_TIME.set(RenderSystem.getShaderGameTime());
        RenderSystem.setupShaderLights(sh);
        sh.apply();
        for (ChunkRenderDispatcher.RenderChunk rc : chunks) {
            if (rc.getCompiledChunk().isEmpty(type)) continue;
            VertexBuffer vb = rc.getBuffer(type);
            BlockPos o = rc.getOrigin();
            if (sh.CHUNK_OFFSET != null) {
                sh.CHUNK_OFFSET.set((float) (o.getX() - cam.x), (float) (o.getY() - cam.y), (float) (o.getZ() - cam.z));
                sh.CHUNK_OFFSET.upload();
            }
            vb.bind();
            vb.draw();
        }
        if (sh.CHUNK_OFFSET != null) sh.CHUNK_OFFSET.set(0f, 0f, 0f);
        sh.clear();
        VertexBuffer.unbind();
        type.clearRenderState();
    }
}
