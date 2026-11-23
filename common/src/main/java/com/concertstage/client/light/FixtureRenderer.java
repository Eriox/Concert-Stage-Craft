package com.concertstage.client.light;

import com.concertstage.ConcertStage;
import com.concertstage.light.FixtureBlock;
import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.core.Attr;
import com.concertstage.light.core.BeamOptics;
import com.concertstage.light.core.Engine;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.LampModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a CSC fixture: the exported model (base, yoke turned by pan, head turned by tilt), its emitters lit with their live colours, and
 * the light itself — a soft beam drawn as camera-facing additive layers (no volumetric simulation: cheap and readable), cut where it hits
 * a block, with the spot on that block (gobo pattern for the VORTEX), the prism splitting it in three, and a flare when you look into it.
 *
 * <p>The heads move like real motors: they follow the value of the desk at the speed of the profile (slower when the desk limits the
 * movement). Performance: the body is cached in the graphics card per level of detail, beams are limited per frame by the beam quality,
 * which the automatic mode lowers when the frame time gets high (it never changes what the desk programmed, only how much is drawn).
 */
public class FixtureRenderer implements BlockEntityRenderer<FixtureBlockEntity> {
    /** Rev. 4c: throw of a fixture with the beam-range setting of the configuration screen. */
    public static float throwOf(com.concertstage.light.core.FixtureProfile pr) {
        return (float) (pr.throwBlocks * com.concertstage.client.RenderQuality.beamRange());
    }

    public static final ResourceLocation BEAM = tex("beam"), BEAM_GOBO = tex("beam_gobo"), GLOW = tex("glow");
    /** Rev. 5: smooth radial profile (128 px) of the soft beams, the projected light and the halos. */
    static final ResourceLocation SOFT = tex("beam_soft");
    /** Rev. 8: the open spot of the VORTEX on a surface. */
    static final ResourceLocation SPOT_OPEN = tex("spot_open");
    /** Rev. 14b: VORTEX beams in the air 40 % less bright than in rev. 14 (they dominated the picture). Rev. 17: 0.8 (11 November
     *  pictures: the spots read as lit lenses with almost no shaft in the light haze; still below rev. 14). */
    private static final float VORTEX_AIR = 0.8f;
    /**
     * Rev. 17 — calibration after the 11 November pictures (a lit rig, almost no beam in the air, the stage barely lit):
     * <ul>
     * <li>{@link #WASH_AIR}: washes in the haze x1.7 (they were the least visible family from the audience), and their light carries
     *     further from the lens ({@code BeamOptics.SPREAD_EXP});</li>
     * <li>{@link #STAGE_GAIN}: light projected on the blocks x1.45 (stage, truss, set);</li>
     * <li>the forward scattering of the haze is wider in the beam shader, so a beam aimed at the audience shows a cone of lit haze
     *     around the lens instead of a lens alone.</li>
     * </ul>
     * The three brightness settings of the desk (beams, stage light, lens glow) multiply these, on each player's screen.
     */
    private static final float WASH_AIR = 1.7f, STAGE_GAIN = 1.45f;
    /** rev. 1: strength of the laser lines in the haze (LaserBeams), on top of the haze and brightness settings. */
    static final float LASER_AIR = 1.0f;
    /** 1.3.0 rev. 1, visual tests only: the laser drawn as before (a 30° cone of haze with the figure as its mask), for comparison. */
    public static boolean legacyLaser;
    /** Rev. 16: optical profile of each model (built once from its emitter cells) and the optical state reused for every fixture. */
    private static final java.util.Map<FixtureMeshes.Model, com.concertstage.light.core.OpticalProfile> OPTICS = new java.util.WeakHashMap<>();
    private static final VolumeLights.Optics OPTICS_STATE = new VolumeLights.Optics();

    static com.concertstage.light.core.OpticalProfile optics(FixtureMeshes.Model m, FixtureProfile pr) {
        return OPTICS.computeIfAbsent(m, mm -> {
            float[][] cells = new float[mm.zones.size()][];
            for (int i = 0; i < cells.length; i++) {
                FixtureMeshes.Zone z = mm.zones.get(i);
                cells[i] = new float[] {z.cx() - mm.beamOrigin[0], z.cy() - mm.beamOrigin[1], z.hx(), z.hy(), z.kind()};
            }
            return com.concertstage.light.core.OpticalProfile.of(pr, cells, pr.renderScale);
        });
    }
    private static final ResourceLocation[] GOBOS = new ResourceLocation[9];
    static {
        for (int i = 0; i < GOBOS.length; i++) GOBOS[i] = tex("gobo_" + i);
    }

    /** Rev. 22: textures of the gobo wheels, by file name (GoboWheels). */
    private static final java.util.Map<String, ResourceLocation> WHEEL_TEX = new java.util.HashMap<>();

    /** Rev. 22: the gobo in the light now (wheel 1, or wheel 2 when wheel 1 is open), null when both are open. */
    static ResourceLocation goboTexture(FixtureProfile pr, Engine.Output o) {
        String file = null;
        if (pr.has(Attr.GOBO)) {
            com.concertstage.light.core.GoboWheels.Wheel w = com.concertstage.light.core.GoboWheels.first(pr);
            int k = o.get(Attr.GOBO) > 0.01f ? Math.max(1, com.concertstage.light.core.GoboWheels.slot(pr, o.get(Attr.GOBO))) : 0;
            if (w != null && k > 0) file = w.file(Math.min(k, w.size()));
        }
        if (file == null && pr.has(Attr.GOBO_2) && o.get(Attr.GOBO_2) > 0.01f) {
            com.concertstage.light.core.GoboWheels.Wheel w2 = com.concertstage.light.core.GoboWheels.second(pr);
            if (w2 != null) file = w2.file(Math.max(1, Math.min(w2.size(), Math.round(o.get(Attr.GOBO_2) * w2.size()))));
        }
        return file == null ? null : WHEEL_TEX.computeIfAbsent(file, FixtureRenderer::tex);
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(ConcertStage.MOD_ID, "textures/lightshow/" + name + ".png");
    }

    public FixtureRenderer(BlockEntityRendererProvider.Context ctx) {}

    /** Score of the fixture being drawn (render thread only; set in render, read by beam). */
    private static float SCORE;
    private static final PoseStack LOCAL = new PoseStack();
    private static final PoseStack[] PAIR = new PoseStack[2];
    private static final PoseStack FEED_LOCAL = new PoseStack();
    private static final PoseStack[] FEED_PAIR = new PoseStack[2];
    /**
     * 1.2.0 (15 November, pictures of the PIXELBAR hung straight down: "covered by a panel at 90°"): the side of the optical
     * mask (cells of a bar, lens array, gobo) is the fixture's own side, model -X carried by its pan and tilt. It was built from the
     * world (up × axis, or x × axis once the beam is within 25° of vertical): a bar aimed straight down had its row of cells turned
     * 90° on the floor and in the haze (a sheet across the bar), and every pattern jumped there. For a beam off the vertical it is
     * the same side as before on a standing fixture.
     */
    private static final Vector3f SIDE = new Vector3f(-1, 0, 0);

    /** The side of the mask of a fixture: model -X in the frame {@code lm}, square to the beam axis. */
    static Vector3f sideOf(Matrix4f lm, Vector3f axis, Vector3f out) {
        lm.transformDirection(out.set(-1, 0, 0));
        out.sub(new Vector3f(axis).mul(out.dot(axis)));
        if (out.lengthSquared() < 1e-6f) out.set(Math.abs(axis.y) < 0.9f ? 0 : 1, Math.abs(axis.y) < 0.9f ? 1 : 0, 0).cross(axis);
        return out.normalize();
    }

    // ------------------------------------------------------------ frame budget and automatic performance
    static final class Perf {
        static long frameKey = Long.MIN_VALUE, lastFrameNanos;
        static double avgMs = 16;
        /** Rev. 19: when the automatic level last went down (it does not go up again within {@link #DWELL_MS}: no pumping). */
        static long lastDrop;
        static final long DWELL_MS = 20000;
        /** Rev. 19: budgets given by importance (FrameBudget), not by the order the game draws the fixtures in. */
        static FrameBudget BEAMS = new FrameBudget("beams"), FAR = new FrameBudget("far beams"),
            VOLUMES = new FrameBudget("beams in the haze"), PROJECTIONS = new FrameBudget("projections");
        static int beamsThisFrame, raysThisFrame, farThisFrame, autoLevel = 3;
        // rev. 3b: the budgets of the LiveVision camera images, apart from the main view's (LightTargets)
        private static FrameBudget fBeams = new FrameBudget("camera beams"), fFar = new FrameBudget("camera far beams"),
            fVolumes = new FrameBudget("camera beams in the haze"), fProjections = new FrameBudget("camera projections");
        private static int fBeamsThisFrame, fFarThisFrame;
        private static boolean inFeed;

        static void swapFeed() {
            FrameBudget b;
            b = BEAMS; BEAMS = fBeams; fBeams = b;
            b = FAR; FAR = fFar; fFar = b;
            b = VOLUMES; VOLUMES = fVolumes; fVolumes = b;
            b = PROJECTIONS; PROJECTIONS = fProjections; fProjections = b;
            int i;
            i = beamsThisFrame; beamsThisFrame = fBeamsThisFrame; fBeamsThisFrame = i;
            i = farThisFrame; farThisFrame = fFarThisFrame; fFarThisFrame = i;
            inFeed = !inFeed;
            if (inFeed) {
                // each camera image is a frame of its own budgets
                beamsThisFrame = 0;
                farThisFrame = 0;
                BEAMS.frame(maxBeams());
                FAR.frame(FarView.farRibbons(quality()));
                VOLUMES.frame(VolumeLights.volumeCapacity());
                PROJECTIONS.frame(VolumeLights.projectorCapacity());
            }
        }
        static long frameCount, rayTick, lastRayTick = Long.MIN_VALUE;
        static long slowSince, fastSince;
        static double capMs;                                                    // 1.2.1: frame time of the frame rate cap, 0 = none

        static void frame(long key) {
            if (key == frameKey) return;
            frameKey = key;
            long now = System.nanoTime();
            if (lastFrameNanos != 0) {
                double ms = Math.min(250, (now - lastFrameNanos) / 1e6);
                avgMs = avgMs * 0.95 + ms * 0.05;
            }
            lastFrameNanos = now;
            // rev. 20: the client never updates the darkness of the sky it computed when the world was joined (only the server level
            // does, every tick): a world joined by day kept the daylight value all night — the stage light 8 x too weak, the dense
            // haze a light grey screen (seen in game, 11 November) — and one joined at night kept the night value by day
            var lvl = net.minecraft.client.Minecraft.getInstance().level;
            if (lvl != null) lvl.updateSkyBrightness();
            if (frameCount % 120 == 0) LightBlockers.prune();                     // rev. 20: roof sheets of removed roofs
            beamsThisFrame = 0;
            farThisFrame = 0;
            // rev. 22: the rays (what is under each beam) keep the cadence of the game ticks, as measured in rev. 21: the frame key
            // now changes at every frame drawn (bloom, budgets, frame time), which would have cast up to six times more rays a second
            var lv = net.minecraft.client.Minecraft.getInstance().level;
            long tick = lv == null ? 0 : lv.getGameTime();
            if (tick != lastRayTick) { lastRayTick = tick; raysThisFrame = 0; rayTick++; }
            LightDiagnostics.endFrame();
            ShimmerLights.frame(++frameCount);
            if (!LightClient.autoPerformance) autoLevel = 3;
            else {
                long t = System.currentTimeMillis();
                // 1.2.1: measured against the frame rate cap of the game (a game held to 20 images a second, 50 ms a frame, is not
                // slow: before, it put every beam on its lowest detail)
                if ((frameCount & 63) == 0) capMs = com.concertstage.client.RenderQuality.capPeriodMs();
                double slowMs = Math.max(22, capMs * 1.15), fastMs = Math.max(14, capMs * 1.06);
                // rev. 19: down after 2 s slow, up after 5 s fast but never within 20 s of a drop (before: down / up / down every few
                // seconds near the limit, the whole rig changing its look each time — a slow pulsing of the beams)
                if (avgMs > slowMs) { fastSince = 0; if (slowSince == 0) slowSince = t; else if (t - slowSince > 2000 && autoLevel > 0) { autoLevel--; slowSince = t; lastDrop = t; } }
                else if (avgMs < fastMs) { slowSince = 0; if (fastSince == 0) fastSince = t; else if (t - fastSince > 5000 && t - lastDrop > DWELL_MS && autoLevel < 3) { autoLevel++; fastSince = t; } }
                else { slowSince = 0; fastSince = 0; }
            }
            BEAMS.frame(maxBeams());
            FAR.frame(FarView.farRibbons(quality()));
            VOLUMES.frame(VolumeLights.volumeCapacity());
            PROJECTIONS.frame(VolumeLights.projectorCapacity());
        }

        /** How much a lit fixture shows from the camera (the order of the budgets). */
        static float score(FixtureProfile pr, float intensity, double dist) {
            return intensity * pr.output * pr.renderScale / (float) Math.max(4.0, dist);
        }

        static int quality() {
            return Math.min(LightClient.beamQuality, autoLevel);
        }

        static int maxBeams() {
            return BeamOptics.maxBeams(quality());
        }
    }

    @Override
    public void render(FixtureBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        GpuTimers.beginCpu(GpuTimers.FIXTURES);                 // rev. 25: processor time and memory of the fixtures (F3, tests)
        try {
            renderFixture(be, partial, pose, buffers, light, overlay);
        } finally {
            GpuTimers.endCpu(GpuTimers.FIXTURES);
        }
    }

    private void renderFixture(FixtureBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !(be.getBlockState().getBlock() instanceof FixtureBlock block)) return;
        FixtureProfile pr = block.profile();
        FixtureMeshes.Model m = FixtureMeshes.get(pr.id);
        if (!com.concertstage.client.ClientCulling.visible(be, be.getRenderBoundingBox())) return;   // off screen (Fabric; Forge checks it itself)
        PointLights.context(be.getBlockPos(), pose.last().pose());
        double gameTime = level.getGameTime() + partial;
        // rev. 22: one key per frame drawn. The game time + partial tick stood still between two ticks in the rev. 22 test: the frame
        // budgets and the frame time were counted once per tick ("frame 50 ms" at 119 FPS), and the automatic quality, reading a
        // slow frame time, could step the lighting down to low on a fast PC
        // rev. 3b: a LiveVision camera image (LightTargets) uses the cameras' budgets, the desk output as it is, and leaves the motors
        boolean feed = LightTargets.feed();
        if (!feed) Perf.frame(com.concertstage.client.GpuMesh.frameSerial > 0 ? com.concertstage.client.GpuMesh.frameSerial : Double.doubleToLongBits(gameTime));
        Engine.Output o = feed ? LightClient.feedOutput(level, be, partial) : LightClient.output(level, be, partial);
        // 1.2.2 (RackWorks, Forge): a fixture with no mains is dark, whatever the desk sends; its halogen cools out through the lamp.
        if (o != null && LampModel.tungsten(pr)) o = lamp(be, pr, o, feed, !be.mainsPowered);
        else if (o != null && !be.mainsPowered) {
            o = o.copy();
            o.v[Attr.DIMMER.ordinal()] = 0f;
            o.strobeHz = 0f;
        }
        // 1.2.2: a tungsten lamp (a conventional or a blinder) lags its dimmer and warms with it. Everything below (beam, pool on the
        // floor, lens, cells) reads the lamp's level and colour, never the raw desk value.
        boolean hung = be.getBlockState().getValue(FixtureBlock.HUNG);
        Direction facing = be.getBlockState().getValue(FixtureBlock.FACING);
        float yaw = 180f - facing.toYRot();

        // ---- motors
        float tPan = (o == null ? 0f : pr.panDegrees(o.get(Attr.PAN))) + be.manualPan;
        float tTilt = (o == null ? pr.tiltZero : pr.tiltDegrees(o.get(Attr.TILT))) + be.manualTilt;
        // rev. 19: the manual offsets never take a head past its mechanical stops
        if (pr.panRange > 0) tPan = Math.max(-pr.panRange / 2, Math.min(pr.panRange / 2, tPan));
        if (pr.tiltRange > 0) tTilt = Math.max(pr.tiltMin(), Math.min(pr.tiltMax(), tTilt));
        long nowN = System.nanoTime();
        float dt = feed ? 0f : be.lastFrameNanos == 0 ? 0f : Math.min(0.1f, (nowN - be.lastFrameNanos) / 1e9f);
        if (!feed) be.lastFrameNanos = nowN;
        float slow = o != null && o.slow ? 0.3f : 1f;
        if (o != null && pr.has(Attr.MOVE_SPEED)) slow *= 1f - 0.95f * o.get(Attr.MOVE_SPEED);
        // rev. 1.3.0-2 (suggested 13 November: "smoothing of the pan and tilt"): the motors accelerate and brake (critically damped,
        // within the top speed of the profile) instead of starting and stopping at full speed; the 30-44 steps per second of Art-Net
        // become a continuous movement
        if (Float.isNaN(be.curPan)) { be.curPan = tPan; be.panVel = 0f; }
        else if (pr.hasPan()) { float[] r = motor(be.curPan, be.panVel, tPan, pr.panSpeed * slow, dt); be.curPan = r[0]; be.panVel = r[1]; }
        else be.curPan = tPan;
        if (Float.isNaN(be.curTilt)) { be.curTilt = tTilt; be.tiltVel = 0f; }
        else if (pr.has(Attr.TILT)) { float[] r = motor(be.curTilt, be.tiltVel, tTilt, pr.tiltSpeed * slow, dt); be.curTilt = r[0]; be.tiltVel = r[1]; }
        else be.curTilt = approach(be.curTilt, tTilt, 100000f, dt);
        if (o != null) {
            be.wheel1Angle = (be.wheel1Angle + (o.get(Attr.GOBO_ROT) - 0.5f) * 2f * 220f * dt) % 360f;
            // rev. 1.3.0-2: the second wheel (PHANTOM S1) turns with its own rotation channel when its gobo is the one in the light
            if (pr.has(Attr.GOBO_2_ROT)) be.wheel2Angle = (be.wheel2Angle + (o.get(Attr.GOBO_2_ROT) - 0.5f) * 2f * 220f * dt) % 360f;
            boolean second = pr.has(Attr.GOBO_2) && !(pr.has(Attr.GOBO) && o.get(Attr.GOBO) > 0.01f) && o.get(Attr.GOBO_2) > 0.01f;
            be.goboAngle = second ? be.wheel2Angle : be.wheel1Angle;
            be.prismAngle = (be.prismAngle + (o.get(Attr.PRISM_ROT) - 0.5f) * 2f * 160f * dt) % 360f;
        }

        Vec3 cam = LightTargets.camera().getPosition();
        BlockPos bp = be.getBlockPos();
        double dist = Math.sqrt(cam.distanceToSqr(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5));
        // rev. 18: levels of detail by the size of the fixture and the render quality (FarView), no longer a box at 28 blocks
        boolean gpu = com.concertstage.client.GpuMesh.enabled();
        int lod = FarView.lod(dist, pr.renderScale, com.concertstage.client.RenderQuality.current().ordinal(), gpu);

        // ---- rev. 18: hung, the fixture hangs from its hardware (half couplers and bar across two chords, omega and hook clamps on
        // a tube, or a plate under a flat face), drawn in block coordinates; the fixture starts under it, moved under the tube it
        // hangs from when there is only one
        // 1.2.1: a fixture moved or tilted off its standard place (free pose) is drawn without the truss hardware
        MountGeometry.Layout mount = hung && !be.posed() ? FixtureMount.of(be, level, pr.renderScale) : null;
        float top = mount != null ? mount.top() : 1f, mx = mount != null ? mount.ox() : 0f, mz = mount != null ? mount.oz() : 0f;
        if (mount != null && mount.kind() != MountGeometry.NONE && dist < (gpu ? FarView.MOUNT_GPU : FarView.MOUNT_DIRECT))
            FixtureMeshes.drawMount(m, mount, pose, buffers, light);

        // ---- body: the BER pose and a local copy of the same transforms (block-local coordinates, for the beam)
        // 1.2.0: one local pose stack and one pair for every fixture (they were made for each fixture each frame)
        PoseStack local = LOCAL;
        while (!local.clear()) local.popPose();
        local.setIdentity();
        PAIR[0] = pose; PAIR[1] = local;
        pose.pushPose();
        for (PoseStack p : PAIR) {
            basePose(p, be, hung, top, mx, mz, yaw, pr.renderScale);          // rev. 2: drawn larger, Blender model unchanged
        }
        FixtureMeshes.draw(m, "base", lod, pose, buffers, light);
        if (m.hasYoke() && m.panPivot != null) {
            for (PoseStack p : PAIR) {
                p.translate(m.panPivot[0], m.panPivot[1], m.panPivot[2]);
                p.mulPose(Axis.YP.rotationDegrees(be.curPan));
            }
            FixtureMeshes.draw(m, "yoke", lod, pose, buffers, light);
        }
        if (m.tiltPivot != null) {
            for (PoseStack p : PAIR) {
                p.translate(m.tiltPivot[0], m.tiltPivot[1], m.tiltPivot[2]);
                p.mulPose(Axis.XP.rotationDegrees(be.curTilt));
            }
        }
        FixtureMeshes.draw(m, "head", lod, pose, buffers, light);

        float intensity = 0f;
        boolean flash = false;
        if (o != null) {
            intensity = o.get(Attr.DIMMER);
            intensity = BeamOptics.strobeIntensity(intensity, o.strobeHz, gameTime / 20.0);
            flash = o.strobeHz > 0.05f && intensity > 0f;
        }
        // rev. 21: a zone fixture draws its three light zones at this instant (each with its own strobe), the light it throws from
        // what its cells emit now. The output is drawn with those values and given back as it was at the end of this method.
        float[] keptZones = null;
        float keptR = 0f, keptG = 0f, keptB = 0f;
        if (o != null && pr.hasSections() && o.zones.length == pr.zones * 4) {
            if (be.frameZones == null || be.frameZones.length != o.zones.length) be.frameZones = new float[o.zones.length];
            intensity = com.concertstage.light.core.ZoneStrobe.frame(pr, o.get(Attr.DIMMER), o.zones, o.sectionHz, o.alternate, gameTime / 20.0,
                be.frameZones, ZONE_BEAM, ZONE_FLASH);
            flash = ZONE_BEAM[3] > 0.5f && intensity > 0f;
            keptZones = o.zones; keptR = o.beamR; keptG = o.beamG; keptB = o.beamB;
            o.zones = be.frameZones; o.beamR = ZONE_BEAM[0]; o.beamG = ZONE_BEAM[1]; o.beamB = ZONE_BEAM[2];
        }
        try {
        // ---- emitters
        if (intensity > 0.003f && LightDiagnostics.emitters()) glows(m, pr, o, intensity, flash, pose, buffers, cam, local, bp);
        pose.popPose();
        Matrix4f lm = local.last().pose();
        Vector3f origin = lm.transformPosition(new Vector3f(m.beamOrigin[0], m.beamOrigin[1], m.beamOrigin[2]));
        Vector3f axis = lm.transformDirection(new Vector3f(m.beamAxis[0], m.beamAxis[1], m.beamAxis[2])).normalize();
        sideOf(lm, axis, SIDE);
        boolean lit = o != null && intensity > 0.01f;
        if (!lit || !LightClient.beams) { PointLights.dark(be); for (Object k : be.spreadLights) PointLights.dark(k); }

        // ---- halo around the lens and the light it throws on what is around the fixture (truss, rig, stage)
        if (lit) {
            Vector3f lensFront = lm.transformPosition(new Vector3f(m.beamOrigin[0], m.beamOrigin[1], m.lensFrontZ - 0.01f));
            if (LightDiagnostics.emitters()) halo(be, pr, m, o, intensity, flash, lensFront, axis, pose, buffers, bp, dist);
            else PointLights.dark(be.lensLight);
            // rev. 20: no more spill light around the fixtures (light on the floor around the foot of a standing fixture, a streak on
            // the truss above a hung one): seen in game in close-up (diagnostic "surfaces only"), it was the "strange light that comes
            // when the fixtures are on" of the author's report. A moving head lets no light out sideways; what it lights is where its
            // beam lands (projection). Kept only with the real Shimmer mod, which lights the rig in its own way.
            if (LightClient.beams && dist < 96 && LightDiagnostics.pointLights() && ShimmerLights.available()) spill(be, level, pr, o, intensity, flash, pose, buffers, bp);
            else PointLights.dark(be.rigLight);
        }
        else { PointLights.dark(be.lensLight); PointLights.dark(be.rigLight); }

        // ---- the beam
        int lightDist = LightClient.lightDistance();
        // rev. 19: the score of the fixture for the budgets (a strobing fixture asks with its level, not with its flash of the moment:
        // its place does not come and go with each flash)
        float lvl = o == null ? 0f : o.get(Attr.DIMMER);
        float score = Perf.score(pr, lvl, dist);
        SCORE = score;
        if (lit && LightClient.beams && dist < lightDist && Perf.BEAMS.admit(be, score, pr.autoRole())) {
            Perf.beamsThisFrame++;
            // rev. 16d: a far fixture keeps its beam in the haze, its light on the surfaces and its shadows (medium quality up to the
            // light distance). Before, beyond 40 blocks it fell back to the old ribbons with no shadow (beams through the roof seen
            // from the back of the audience, a different look at every step of distance, 9 November video)
            // rev. 18: low (ribbons and a few rays, no shadow map) beyond 128 blocks, so the light distance can follow the world
            int q = FarView.beamQuality(dist, Perf.quality());
            beam(be, level, pr, m, o, intensity, flash, q, origin, axis, pose, buffers, cam, bp);
        } else if (lit && LightClient.beams && dist < Math.max(lightDist, com.concertstage.client.RenderQuality.worldDistance() + 64)
            && LightDiagnostics.air() && Perf.FAR.admit(be, score, pr.autoRole())) {
            // rev. 24: a far ribbon at any distance the world is drawn at (was the light distance, at most 512 blocks)
            // rev. 18: over the budget of full beams (or past the light distance), a far beam keeps its shaft in the haze
            Perf.farThisFrame++;
            farBeam(be, pr, m, o, intensity, flash, origin, axis, pose, buffers, cam, bp);
        }
        } finally {
            if (keptZones != null) { o.zones = keptZones; o.beamR = keptR; o.beamG = keptG; o.beamB = keptB; }
        }
        // 1.2.2 (RackWorks, Forge): the cables plugged into the fixture (power) are drawn with its body, by RackWorks.
        if (RENDER_HOOK != null) RENDER_HOOK.drawCables(be, pose, buffers);
    }

    /** 1.2.2: a loader's client-only drawing after a fixture (RackWorks cables); null in a build without it. */
    public static FixtureRenderHook RENDER_HOOK;

    /**
     * 1.2.2 — the light of a tungsten lamp this frame: the desk's dimmer through the filament law, held by the filament's inertia
     * (heat quicker than cool), and the colour of the lamp at that temperature ({@link Engine#halogen}). A copy of the desk output is
     * changed, never the output itself: it is reused by the next frame and by the cameras.
     *
     * @param feed a LiveVision camera image, drawn between two frames: the lamp does not advance twice, the current level is used
     */
    private static Engine.Output lamp(FixtureBlockEntity be, FixtureProfile pr, Engine.Output o, boolean feed, boolean unpowered) {
        float target = unpowered ? 0f : LampModel.drive(o.get(Attr.DIMMER));
        long now = System.nanoTime();
        if (Float.isNaN(be.lampLevel)) {
            be.lampLevel = target;
            be.lampNanos = now;
        } else if (!feed) {
            float dt = Math.min(0.1f, Math.max(0f, (now - be.lampNanos) / 1.0e9f));
            be.lampNanos = now;
            be.lampLevel = LampModel.step(be.lampLevel, target, dt, LightClient.lampFadeSpeed());
        }
        Engine.Output c = o.copy();
        float level = be.lampLevel;
        c.v[Attr.DIMMER.ordinal()] = level;
        if (unpowered) c.strobeHz = 0f;                                  // no mains, no flash either
        if (pr.conventional) {
            // the gel sits in front of the lamp: the colour of the filament through it (Engine.finishZones, same formula)
            float[] h = Engine.halogen(level, 0f);
            int gel = o.gel;
            c.beamR = h[0] * ((gel >> 16) & 255) / 255f;
            c.beamG = h[1] * ((gel >> 8) & 255) / 255f;
            c.beamB = h[2] * (gel & 255) / 255f;
        } else {
            // blinders: the halogen colour of each white lamp (Engine.finishZones), its CTO channel still in front of it
            float[] h = Engine.halogen(level, o.get(Attr.CTO));
            c.beamR = h[0];
            c.beamG = h[1];
            c.beamB = h[2];
            for (int z = 0; z < pr.zones && z * 4 + 3 < c.zones.length; z++) {
                if (pr.zoneKind(z) != 1) continue;
                c.zones[z * 4] = h[0];
                c.zones[z * 4 + 1] = h[1];
                c.zones[z * 4 + 2] = h[2];
            }
        }
        return c;
    }

    /**
     * Rev. 22: the centre ray of a far ribbon. It kept the length measured the last time the fixture had a full beam, or its whole
     * throw when it never had one: with more fixtures than full beams (low quality: 24 for 62 on the author's stage), the ribbons
     * of the others went through the festival deck, the risers and the floor (picture 1 of 11 November). One ray when the head has
     * turned or every 20 frames, within the ray budget of the frame.
     */
    private static void farCentre(FixtureBlockEntity be, Level level, Vector3f origin, Vector3f axis, float throwLen, BlockPos bp) {
        if (level == null) return;
        int frameNo = (int) Perf.rayTick;
        float moved = (float) Math.sqrt(sq(axis.x - be.farAx) + sq(axis.y - be.farAy) + sq(axis.z - be.farAz));
        if (be.beamLength >= 0 && moved < 0.01f && frameNo - be.farRayFrame < 20) return;
        if (be.beamLength >= 0 && Perf.raysThisFrame >= BeamOptics.rayBudget(Perf.quality()) + 32) return;
        Perf.raysThisFrame++;
        be.farRayFrame = frameNo; be.farAx = axis.x; be.farAy = axis.y; be.farAz = axis.z;
        Vec3 start = new Vec3(bp.getX() + origin.x + axis.x * 0.3, bp.getY() + origin.y + axis.y * 0.3, bp.getZ() + origin.z + axis.z * 0.3);
        Vec3 stop = start.add(axis.x * throwLen, axis.y * throwLen, axis.z * throwLen);
        BlockHitResult hit = clipRig(level, start, stop, bp);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) { be.beamLength = (float) hit.getLocation().distanceTo(start) + 0.3f; be.beamHitFace = hit.getDirection(); }
        else { be.beamLength = throwLen; be.beamHitFace = null; }
    }

    /**
     * Rev. 23 — balance of each family at 100 %, measured in game (CraftCity copy, FOH, medium haze, 16 W19 / 30 VORTEX / 16
     * PULSESTRIKE alone at full). Before: 16 W19 in white covered 30 % of the screen above 200/255 with a burnt white cloud, 30 VORTEX made
     * a white wall over the stage. The PULSESTRIKE M was right. Haze part, then surfaces part. Families not in that stage
     * (PHANTOM, PROFILE, RAZOR, W7, W37, ...) follow the nearest measured one.
     */
    /** Rev. 24 — every fixture a little dimmer (asked 11 November, 23 h): haze and surfaces, on top of the family balance below. */
    static final float MASTER_AIR = 0.8f, MASTER_STAGE = 0.8f;

    static float airBalance(FixtureProfile pr) { return MASTER_AIR * familyAir(pr); }

    /** 1.2.0 beta 2b: the strobes and LED bars (PULSESTRIKE M, FLASHCORE FX, PIXELBAR B16, PIXELTUBE T1). */
    static boolean barOrStrobe(FixtureProfile pr) {
        return pr == FixtureProfile.PULSESTRIKE_M || pr == FixtureProfile.FLASHCORE_FX || pr == FixtureProfile.PIXELBAR_B16 || pr == FixtureProfile.PIXELTUBE_T1;
    }

    static float stageBalance(FixtureProfile pr) { return MASTER_STAGE * familyStage(pr); }

    private static float familyAir(FixtureProfile pr) {
        if (pr == FixtureProfile.LUMORA_W19 || pr == FixtureProfile.PIXELWASH_W37) return 0.38f;
        if (pr == FixtureProfile.LUMORA_W7) return 0.48f;
        if (pr == FixtureProfile.VORTEX_X1 || pr == FixtureProfile.PHANTOM_S1 || pr == FixtureProfile.PROFILE_P1) return 0.5f;
        if (pr == FixtureProfile.RAZOR_B1) return 0.65f;
        return 1f;
    }

    private static float familyStage(FixtureProfile pr) {
        if (pr == FixtureProfile.LUMORA_W19 || pr == FixtureProfile.PIXELWASH_W37) return 0.62f;
        if (pr == FixtureProfile.LUMORA_W7) return 0.75f;
        if (pr == FixtureProfile.VORTEX_X1 || pr == FixtureProfile.PHANTOM_S1 || pr == FixtureProfile.PROFILE_P1 || pr == FixtureProfile.RAZOR_B1) return 0.8f;
        // 1.2.2: a conventional's pool on the floor sat at the ceiling of the light composite from full down to a fifth of the fader
        // (the tone map is a soft ceiling, 1 + 2.2 tanh(L / 1.83)): at full the pool is unchanged, below it the level now shows.
        // The blinders keep their own 0.15 on the projection (barOrStrobe above).
        if (pr.conventional) return 0.5f;
        return 1f;
    }

    /** Rev. 21: r, g, b, flash of the zone fixture being drawn (render thread). */
    private static final float[] ZONE_BEAM = new float[4];
    /** Rev. 21: the zones of the zone fixture being drawn that are in a flash now (only their cells get the flash look). */
    private static final boolean[] ZONE_FLASH = new boolean[3];

    /** Rev. 21: a cell of a zone fixture flashes only with its own zone; the other fixtures flash as a whole. */
    private static boolean cellFlash(FixtureProfile pr, int cell, boolean flash) {
        if (!pr.hasSections()) return flash;
        int k = pr.section(cell);
        return k >= 0 && ZONE_FLASH[k];
    }

    /**
     * Rev. 18: a far beam over the budget of full beams — one soft ribbon in the haze at low quality, as long as the last beam measured
     * (or its throw), no ray, no shadow, no projection: a few quads. Same strengths as the low-quality ribbons of {@link #beam}.
     */
    private void farBeam(FixtureBlockEntity be, FixtureProfile pr, FixtureMeshes.Model m, Engine.Output o, float intensity, boolean flash,
                         Vector3f origin, Vector3f axis, PoseStack pose, MultiBufferSource buffers, Vec3 cam, BlockPos bp) {
        boolean spotType = pr.isSpot(), strobeType = pr.isPanel();
        if (strobeType && !flash) return;                                   // a blinder has no beam between flashes
        float angle = pr.beamAngle(o.get(Attr.ZOOM));
        if (pr.has(Attr.FROST)) angle += o.get(Attr.FROST) * 22f;
        if (pr == FixtureProfile.LASERCORE_L1) angle = 30f;
        float tanHalf = (float) Math.tan(Math.toRadians(Math.max(0.6f, angle) / 2));
        float r0 = m.lensRadius * 0.9f * pr.renderScale;
        farCentre(be, be.getLevel(), origin, axis, throwOf(pr), bp);
        float len = be.beamLength > 0 ? be.beamLength : throwOf(pr);
        boolean hits = be.beamHitFace != null && be.beamLength < throwOf(pr) - 0.01f;
        if (strobeType) len = Math.min(len, 9f);
        float air = intensity * pr.output * LightClient.haze * BeamOptics.AIR_DENSITY * LightClient.beamBrightness * airBalance(pr);
        if (air < 0.001f) return;
        if (pr == FixtureProfile.LASERCORE_L1 && !legacyLaser) {
            // rev. 1: a far laser keeps its lines (a few quads each), not a 30° cone
            Vector3f camF = new Vector3f((float) (cam.x - bp.getX()), (float) (cam.y - bp.getY()), (float) (cam.z - bp.getZ()));
            float pk = Math.max(1e-3f, Math.max(o.beamR, Math.max(o.beamG, o.beamB)));
            Level lv = be.getLevel();
            LaserBeams.draw(LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM),
                LightBatch.buffer(buffers, LightRenderTypes.of(GLOW), LightBatch.PASS_EMIT), pose.last().pose(), pose.last().normal(), be, lv, o,
                (lv.getGameTime() + Minecraft.getInstance().getFrameTime()) / 20.0, origin, axis, r0, tanHalf, throwOf(pr), camF,
                o.beamR / pk, o.beamG / pk, o.beamB / pk, LASER_AIR * air, true, lv.getGameTime());
            return;
        }
        float k = spotType ? VORTEX_AIR * 0.5f * air
            : strobeType ? 0.45f * air
            : (pr == FixtureProfile.LUMORA_W19 ? 0.75f : 0.65f) * WASH_AIR * BeamOptics.washAirScale(tanHalf) * air;
        float rb = spotType ? Math.max(r0, 0.09f) : r0;
        Vector3f camL = new Vector3f((float) (cam.x - bp.getX()), (float) (cam.y - bp.getY()), (float) (cam.z - bp.getZ()));
        VertexConsumer vc = LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM);
        BeamRenderer.softBeam(vc, pose.last().pose(), pose.last().normal(), origin, axis, len, rb, tanHalf, camL, o.beamR * k, o.beamG * k,
            o.beamB * k, hits, BeamOptics.LOW, 1f, false);
    }

    /**
     * Rev. 4: the glowing lens seen from the audience (a soft camera-facing halo, larger when the fixture looks at the camera and on a
     * strobe flash) and a small Shimmer light just in front of the lens, so the truss and the structure around a fixture take its colour
     * — the coloured rig of the ExtraLights pictures. The beam's own Shimmer light (where it hits) is kept first in the budget.
     */
    private void halo(FixtureBlockEntity be, FixtureProfile pr, FixtureMeshes.Model m, Engine.Output o, float intensity, boolean flash,
                      Vector3f origin, Vector3f axis, PoseStack pose, MultiBufferSource buffers, BlockPos bp, double dist) {
        boolean strobeType = pr.isPanel();
        var camera = LightTargets.camera();
        Vec3 cp = camera.getPosition();
        Vector3f toCam = new Vector3f((float) (cp.x - bp.getX()) - origin.x, (float) (cp.y - bp.getY()) - origin.y, (float) (cp.z - bp.getZ()) - origin.z);
        float d = toCam.length();
        if (d > 0.6f) {
            float align = Math.max(0f, axis.dot(toCam.div(d)));
            float a2 = align * align;
            float lens = m.lensRadius * pr.renderScale;
            float size, k;
            if (com.concertstage.light.dmx.DmxMode.tungsten(pr)) {
                // rev. 25 — a halogen blinder: a moderate glow around the face, so its lamps stay visible one by one (the picture of a
                // lit 4-lite blinder: four yellow lamps with a pale core, not one white blob)
                size = lens * (flash ? 1.7f + 1.0f * a2 : 1.25f + 0.6f * a2);
                k = intensity * (flash ? 0.2f + 0.15f * a2 : 0.07f + 0.09f * a2) * LightClient.lensGlow;
                // rev. 1.3.0-2 (reported 13 November: "very, very shiny at moody brightness, not much better on bright"): the glow
                // follows the lamp level more closely (intensity^1.5: at 25 % it is an eighth of the full glow, was a quarter) and is a
                // little lower at full
                k = (float) Math.pow(intensity, 1.5) * (flash ? 0.17f + 0.12f * a2 : 0.055f + 0.07f * a2) * LightClient.lensGlow;
            } else if (strobeType) {
                // a blinder face: the whole face blooms. Rev. 25d: a flash no longer grows the glow (3.2 to 5.7 lens radii, times up to
                // 3.2 with distance): a row of PULSESTRIKE / FLASHCORE strobing covered the whole stage with one white blob (picture of
                // 12 November, 15 h 34). The flash is brighter, not bigger; the light on the stage, truss and surroundings is unchanged
                size = lens * (flash ? 2.0f + 1.3f * a2 : 1.8f + 1.2f * a2);
                k = intensity * (flash ? 0.36f + 0.34f * a2 : 0.25f + 0.35f * a2) * LightClient.lensGlow;
            } else {
                // rev. 16e: a tighter bloom (1.6 to 3.4 lens radii, was 4.0)
                size = lens * (1.6f + 1.8f * a2 * a2);
                k = intensity * (0.12f + 0.45f * a2 * a2) * pr.output * LightClient.lensGlow;
            }
            // far away the halo keeps a visible size on screen (a lit fixture still reads as a point of light)
            size *= FarView.haloGrowth(dist);                 // rev. 18: up to x3.2 (was x2.5), a lit lens stays a point of light far away
            // rev. 25d: a panel face is already large: its glow grows at most x1.4 with distance (x3.2 made each one several blocks wide)
            if (strobeType && !com.concertstage.light.dmx.DmxMode.tungsten(pr)) size = Math.min(size, lens * (flash ? 2.0f + 1.3f * a2 : 1.8f + 1.2f * a2) * 1.4f);
            float hr = o.beamR, hg = o.beamG, hb = o.beamB;
            // rev. 25: a flash is brighter in the colour of the light, never whiter (a violet LUMORA strobing stayed violet in the
            // haze but its lens flashed white, and the whole picture turned pink — 12 November). Only a white source flashes white.
            if (flash) { float pk = Math.max(1e-3f, Math.max(hr, Math.max(hg, hb))); hr /= pk; hg /= pk; hb /= pk; }
            Vector3f c = new Vector3f(axis).mul(0.02f).add(origin);
            BeamRenderer.halo(LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM), pose.last().pose(), pose.last().normal(), c,
                camera.getLeftVector(), camera.getUpVector(), size, hr * k, hg * k, hb * k);
            // rev. 16e: looking into a spot (VORTEX) from close to its axis, the eye / a camera sees fine rays around the source
            // (diffraction of the eye's lashes and aperture): a faint star, four thin rays, longer the more exactly it is in line
            if (pr.isSpot() && align > 0.82f) {
                float t = (align - 0.82f) / 0.18f;
                float sk = intensity * t * t * 0.32f * pr.output;
                float len = lens * (5f + 9f * t) * (1f + (float) Math.min(1.5, dist / 40.0)), thick = lens * 0.16f;
                Vector3f l = camera.getLeftVector(), u = camera.getUpVector();
                Vector3f d1 = new Vector3f(l).add(u).normalize(), d2 = new Vector3f(l).sub(u).normalize();
                VertexConsumer vs = LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM);
                streak(vs, pose.last().pose(), pose.last().normal(), c, d1, d2, len, thick, hr * sk, hg * sk, hb * sk);
                streak(vs, pose.last().pose(), pose.last().normal(), c, d2, d1, len, thick, hr * sk, hg * sk, hb * sk);
                streak(vs, pose.last().pose(), pose.last().normal(), c, new Vector3f(l), new Vector3f(u), len * 0.6f, thick, hr * sk * 0.6f, hg * sk * 0.6f, hb * sk * 0.6f);
            }
            // rev. 10: haze lit around the fixture — a large, faint glow a little in front of the lens (the volume of light in the
            // smoke right at the source, very visible on the reference pictures)
            // rev. 14c: only on a blinder flash. Always on, this camera-facing glow lay over the fixtures next to it and greyed
            // their bodies near the camera
            if (flash && !strobeType) {                    // rev. 25d: not on the panels (their flash lights the haze through the beam volume)
                float hk = 0.07f * intensity * pr.output * LightClient.haze * (flash ? 2f : 1f);
                Vector3f gc = new Vector3f(axis).mul(1.6f + lens * 2f).add(origin);
                BeamRenderer.halo(LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM), pose.last().pose(), pose.last().normal(), gc,
                    camera.getLeftVector(), camera.getUpVector(), 2.2f + lens * 4f, o.beamR * hk, o.beamG * hk, o.beamB * hk);
            }
        }
        boolean spotType = pr.isSpot();
        if (spotType) {
            // rev. 14c: a beam / spot fixture spills almost nothing around its lens (its light leaves through a narrow optic, and it
            // already lights where its beam lands): no lens light. It washed the bodies of the fixtures next to it in white.
            PointLights.dark(be.lensLight);
        } else if (!(LightClient.shimmer && LightDiagnostics.pointLights() && ShimmerLights.available())) {
            PointLights.dark(be.lensLight);                                       // rev. 20: CSC's own lights: no glow around the lens either
        } else {
            // rev. 9: two blocks in front of the lens: it lights the rig and the stage around without burning the fixture's own body
            // rev. 14b: 3.5 blocks out (2 before): the neighbouring fixture bodies are no longer bathed in it
            Vector3f c = new Vector3f(axis).mul(strobeType ? 3.5f : 3.5f).add(origin);
            float sum = Math.max(1e-3f, Math.max(o.beamR, Math.max(o.beamG, o.beamB)));
            float strength = Math.min(1f, intensity * pr.output * (flash ? 0.9f : 0.5f));
            // rev. 6: wider, so the truss, the rig and the structure around a lit fixture take its colour (images 1 to 5)
            // rev. 8: wider again — with the rig light above, the truss and the structure around a lit fixture take its colour
            float radius = flash ? 12f : strobeType ? 7f : pr == FixtureProfile.LUMORA_W19 ? 7.5f : 6.5f;   // rev. 14c: a little smaller
            // rev. 1.3.0-2: a halogen blinder lights a wider area in front of it (its blinding radius)
            if (com.concertstage.light.dmx.DmxMode.tungsten(pr)) radius = flash ? 16f : 11f;
            PointLights.put(be.lensLight, bp.getX() + c.x, bp.getY() + c.y, bp.getZ() + c.z, o.beamR / sum, o.beamG / sum, o.beamB / sum,
                strength, radius, true);
        }
    }

    /**
     * Rev. 16g — lenses that vanished with some colours (deep blue, deep red, saturated green on LUMORA and PULSESTRIKE, 9 November
     * 22 h 37): an additive emitter of a saturated colour adds very little brightness (pure blue is 7 % of the luminance of white),
     * so a lit lens read as dark on a dark body. A lit LED seen directly saturates the eye or a camera: its core burns towards white
     * while the glow around keeps the colour. The core of an emitter is lifted towards white until its luminance reaches about half
     * of its brightest channel (the colour stays readable: blue gives a light blue core, red a pink-white core).
     */
    /** Rev. 25 (memory): the result is a scratch array of the render thread, valid until the next call (used at once by callers). */
    private static final float[] HOT = new float[3];

    static float[] hotCore(float r, float g, float b) {
        float m = Math.max(r, Math.max(g, b));
        float[] o = HOT;
        if (m <= 1e-4f) { o[0] = o[1] = o[2] = 0f; return o; }
        float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float target = 0.5f * m;
        if (lum >= target) { o[0] = r; o[1] = g; o[2] = b; return o; }
        float w = Math.min(0.6f, (target - lum) / Math.max(1e-4f, m - lum));
        o[0] = r + (m - r) * w; o[1] = g + (m - g) * w; o[2] = b + (m - b) * w;
        return o;
    }

    // rev. 25 (memory): scratch vectors of the cell loops (render thread only): ~1000 small objects a frame fewer
    private static final Vector3f S_C = new Vector3f(), S_TO = new Vector3f(), S_PA = new Vector3f(), S_PB = new Vector3f();

    /** Rev. 16e: a thin soft ray facing the camera, {@code len} along {@code a}, {@code thick} along {@code b} (the soft round texture stretched). */
    /**
     * 1.2.1: the base of a fixture in its block: the truss hardware place (hung), then the free pose of the player (offset, fine yaw,
     * pitch, roll), then the hung flip and the size. Same order as the desk's aiming (FixturePose).
     */
    static void basePose(PoseStack p, FixtureBlockEntity be, boolean hung, float top, float mx, float mz, float yaw, float scale) {
        p.translate(0.5 + mx + be.offX, (hung ? top : 0.0) + be.offY, 0.5 + mz + be.offZ);
        p.mulPose(Axis.YP.rotationDegrees(yaw + be.rotYaw));
        if (be.rotPitch != 0) p.mulPose(Axis.XP.rotationDegrees(be.rotPitch));
        if (be.rotRoll != 0) p.mulPose(Axis.ZP.rotationDegrees(be.rotRoll));
        if (hung) p.mulPose(Axis.ZP.rotationDegrees(180f));
        p.scale(scale, scale, scale);                                            // rev. 2: drawn larger, Blender model unchanged
    }

    private static final float[][] STREAK = {{-1, -1, 0, 0}, {1, -1, 1, 0}, {1, 1, 1, 1}, {-1, 1, 0, 1}};

    private static void streak(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f c, Vector3f a, Vector3f b, float len, float thick,
                               float r, float g, float bl) {
        int ri = Math.min(255, Math.max(0, Math.round(r * 255))), gi = Math.min(255, Math.max(0, Math.round(g * 255))), bi = Math.min(255, Math.max(0, Math.round(bl * 255)));
        if ((ri | gi | bi) == 0) return;
        for (float[] e : STREAK) {
            float px = c.x + a.x * e[0] * len + b.x * e[1] * thick, py = c.y + a.y * e[0] * len + b.y * e[1] * thick, pz = c.z + a.z * e[0] * len + b.z * e[1] * thick;
            vc.vertex(mat, px, py, pz).color(ri, gi, bi, 255).uv(e[2], e[3]).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).normal(nm, 0, 1, 0).endVertex();
        }
    }

    // ------------------------------------------------------------ spill light on the rig (rev. 8)
    private static final float[] SPILL_DIR = new float[3];

    /**
     * Light a fixture throws on what surrounds it — the truss it hangs from, the roof and tarps above, the platform below, the
     * structure next to it — besides its beam: the coloured rig of the reference pictures. A few rays from the fixture find those
     * surfaces (refreshed every few seconds: a rig does not move), then each one gets the light projected on it (multiplied onto its
     * texture, so truss, tarps and platforms keep their look and take the colour) and, with Shimmer, a real coloured light on the
     * rig above the fixture.
     */
    private void spill(FixtureBlockEntity be, Level level, FixtureProfile pr, Engine.Output o, float intensity, boolean flash,
                       PoseStack pose, MultiBufferSource buffers, BlockPos bp) {
        int q = Perf.quality();
        int frameNo = (int) Perf.rayTick;
        int rays = BeamOptics.spillRays(q);
        // refresh: every ~6 s, staggered between fixtures, within the ray budget of the frame
        int every = 360 + Math.floorMod(bp.hashCode(), 60);
        if ((frameNo - be.spillFrame >= every || be.spillCount != rays) && Perf.raysThisFrame + rays <= BeamOptics.rayBudget(q) + 16) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                Perf.raysThisFrame += rays;
                double cx = bp.getX() + 0.5, cy = bp.getY() + 0.5, cz = bp.getZ() + 0.5;
                for (int i = 0; i < rays; i++) {
                    BeamOptics.spillDir(i, SPILL_DIR);
                    Vec3 start = new Vec3(cx + SPILL_DIR[0] * 0.75, cy + SPILL_DIR[1] * 0.75, cz + SPILL_DIR[2] * 0.75);
                    Vec3 stop = start.add(SPILL_DIR[0] * BeamOptics.SPILL_RANGE, SPILL_DIR[1] * BeamOptics.SPILL_RANGE, SPILL_DIR[2] * BeamOptics.SPILL_RANGE);
                    // rev. 9: from the centre of the fixture, through its own block and the fixtures next to it
                    BlockHitResult hit = clipRig(level, new Vec3(cx, cy, cz), stop, bp);
                    int b = i * FixtureBlockEntity.HIT_STRIDE;
                    // another fixture next to this one: its block is an invisible box, light projected on it would float in the air
                    if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                        Vec3 h = hit.getLocation();
                        Direction f = hit.getDirection();
                        be.spillHits[b] = (float) (h.x - bp.getX()); be.spillHits[b + 1] = (float) (h.y - bp.getY()); be.spillHits[b + 2] = (float) (h.z - bp.getZ());
                        be.spillHits[b + 3] = f.getStepX(); be.spillHits[b + 4] = f.getStepY(); be.spillHits[b + 5] = f.getStepZ();
                        be.spillHits[b + 6] = (float) h.distanceTo(new Vec3(cx, cy, cz));
                        be.spillHits[b + 7] = Math.abs(SPILL_DIR[0] * f.getStepX() + SPILL_DIR[1] * f.getStepY() + SPILL_DIR[2] * f.getStepZ());
                        BlockPos lp = hit.getBlockPos().relative(f);
                        int bl = level.getBrightness(LightLayer.BLOCK, lp), sl = level.getBrightness(LightLayer.SKY, lp) - level.getSkyDarken();
                        be.spillHits[b + 8] = Math.max(0, Math.max(bl, sl)) / 15f;
                    } else be.spillHits[b + 6] = -1f;
                }
                be.spillCount = rays;
                be.spillFrame = frameNo;
            }
        }
        float peak = Math.max(1e-3f, Math.max(o.beamR, Math.max(o.beamG, o.beamB)));
        float nr = o.beamR / peak, ng = o.beamG / peak, nb = o.beamB / peak;
        boolean wash = !pr.isSpot();
        boolean shimmer = PointLights.active();   // rev. 9: Shimmer, or CSC's own coloured lights
        float k = intensity * (flash ? 1.8f : 1f) * (shimmer ? 0.6f : 1f);
        VertexConsumer sv = LightBatch.buffer(buffers, LightRenderTypes.modulate(SOFT), LightBatch.PASS_SURFACE);
        Matrix4f mat = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        Vector3f c = new Vector3f(), n = new Vector3f();
        int rig = -1;
        float rigBest = 0f;
        for (int i = 0; i < be.spillCount; i++) {
            int b = i * FixtureBlockEntity.HIT_STRIDE;
            float d = be.spillHits[b + 6];
            if (d < 0f) continue;
            float e = BeamOptics.spill(d, be.spillHits[b + 7], pr.output, wash) * k;
            if (e < 0.01f) continue;
            if (be.spillHits[b + 4] < -0.5f && e > rigBest) { rigBest = e; rig = i; }      // a face looking down: the rig / roof above
            n.set(be.spillHits[b + 3], be.spillHits[b + 4], be.spillHits[b + 5]);
            c.set(be.spillHits[b], be.spillHits[b + 1], be.spillHits[b + 2]).add(n.x * 0.02f, n.y * 0.02f, n.z * 0.02f);
            relight(sv, mat, nm, c, n, null, 1f, BeamOptics.spillRadius(d), BeamOptics.relight(e, be.spillHits[b + 8]), nr, ng, nb, false, 0f);
        }
        // Shimmer: a coloured light on the rig above the fixture (truss, roof), which lights the whole structure around it
        if (shimmer && rig >= 0) {
            int b = rig * FixtureBlockEntity.HIT_STRIDE;
            float d = be.spillHits[b + 6];
            // rev. 9: on the face of the rig itself (0.3 off it), not down among the fixtures, and a little softer
            // rev. 14b: half a block INTO the rig (behind the face the ray met), softer: it colours the truss and the roof without
            // bathing the fixtures hanging right under it
            PointLights.put(be.rigLight, bp.getX() + be.spillHits[b] - be.spillHits[b + 3] * 0.5, bp.getY() + be.spillHits[b + 1] - be.spillHits[b + 4] * 0.5,
                bp.getZ() + be.spillHits[b + 2] - be.spillHits[b + 5] * 0.5,
                nr, ng, nb, Math.min(1f, intensity * pr.output * (flash ? 0.8f : wash ? 0.38f : 0.25f)), Math.max(6f, 10f - d * 0.5f), true);
        } else PointLights.dark(be.rigLight);
    }

    /** Critically damped motor toward {@code target} (degrees), top speed {@code speed} degrees / s: {position, velocity}. */
    static float[] motor(float cur, float vel, float target, float speed, float dt) {
        if (dt <= 0f) return new float[] {cur, vel};
        final float w = 14f;                                   // about 0.3 s to settle a small move; large moves run at top speed
        float t = 0f;
        while (t < dt) {                                       // sub-steps keep it stable at low frame rates
            float h = Math.min(1f / 120f, dt - t);
            float acc = w * w * (target - cur) - 2f * w * vel, amax = speed * 5f;   // top speed reached in 0.2 s
            if (acc > amax) acc = amax; else if (acc < -amax) acc = -amax;
            vel += acc * h;
            if (vel > speed) vel = speed; else if (vel < -speed) vel = -speed;
            cur += vel * h;
            t += h;
        }
        if (Math.abs(target - cur) < 0.02f && Math.abs(vel) < 0.2f) { cur = target; vel = 0f; }
        return new float[] {cur, vel};
    }

    private static float approach(float cur, float target, float speed, float dt) {
        float d = target - cur;
        float max = speed * dt;
        if (Math.abs(d) <= max) return cur + d * Math.min(1f, 0.5f + dt * 10f);
        return cur + Math.signum(d) * max;
    }

    // ------------------------------------------------------------ emitters
    private void glows(FixtureMeshes.Model m, FixtureProfile pr, Engine.Output o, float intensity, boolean flash, PoseStack pose,
                       MultiBufferSource buffers, Vec3 cam, PoseStack local, BlockPos bp) {
        VertexConsumer vc = LightBatch.buffer(buffers, LightRenderTypes.of(GLOW), LightBatch.PASS_EMIT);
        // emitters are light sources: a lit cell reads bright even at a low level, and a strobe flash saturates the face (rev. 4)
        float boost = flash ? 1.8f : 1.25f;
        Matrix4f mat = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int nz = Math.min(m.zones.size(), Math.max(pr.zones, 1));
        // rev. 6: each lit cell / LED segment also gets a soft bloom twice its size (screen-blended) up to 32 blocks away, so a wash
        // reads as a matrix of lit lenses and a blinder as a wall of light, not as a dark body with small coloured dots
        double cd2 = (cam.x - bp.getX()) * (cam.x - bp.getX()) + (cam.y - bp.getY()) * (cam.y - bp.getY()) + (cam.z - bp.getZ()) * (cam.z - bp.getZ());
        boolean panel = pr.isPanel();
        // rev. 16b: a PULSESTRIKE face reads from the back of the audience (64 blocks), its cells larger and blooming more (picture 5
        // of 9 November: small dots on a dark body)
        double bloomReach = panel ? 64 : 32;
        VertexConsumer bloom = cd2 < bloomReach * bloomReach ? LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM) : null;
        boolean lamp = com.concertstage.light.dmx.DmxMode.tungsten(pr);   // rev. 25: halogen blinder lamps, each one visible
        float bloomK = (lamp ? (flash ? 0.24f : 0.12f) : panel ? (flash ? 0.62f : 0.55f) : 0.3f) * Math.min(1.5f, LightClient.lensGlow);
        float cellScale = lamp ? 1.0f : panel ? 1.6f : 1.35f, bloomScale = lamp ? 1.55f : panel ? 3.0f : 2.2f;
        float faceR = 0, faceG = 0, faceB = 0;
        for (int i = 0; i < nz; i++) {
            FixtureMeshes.Zone z = m.zones.get(i);
            float r, g, b;
            boolean cf = cellFlash(pr, i, flash);
            float cb = cf ? 1.8f : 1.25f;
            if (pr.zones > 0 && i * 4 + 3 < o.zones.length) {
                float zl = o.zones[i * 4 + 3] * intensity;
                float lv = Math.min(1f, (lamp ? (float) Math.pow(zl, 0.85) * 1.1f : (float) Math.sqrt(zl)) * cb);
                r = o.zones[i * 4] * lv; g = o.zones[i * 4 + 1] * lv; b = o.zones[i * 4 + 2] * lv;
            } else {
                // rev. 1.3.0-2: a halogen lamp at a low level is a dim glow, not a bright face (the square root lit a 25 % blinder at 62 %)
                float lv = Math.min(1f, (lamp ? (float) Math.pow(intensity, 0.85) * 1.1f : (float) Math.sqrt(intensity)) * cb);
                r = o.beamR * lv; g = o.beamG * lv; b = o.beamB * lv;
            }
            if (r + g + b < 0.004f) continue;
            // rev. 25: a flash brightens the cell in its own colour (it added white: a violet wash flashed pink)
            if (cf) { float pk = Math.max(1e-3f, Math.max(r, Math.max(g, b))), up = Math.min(1.35f, 1f / pk); r *= up; g *= up; b *= up; }
            if (panel) { faceR += r; faceG += g; faceB += b; }
            float hx = Math.max(z.hx(), 0.004f) * cellScale, hy = Math.max(z.hy(), 0.004f) * cellScale, cz = z.cz() - 0.003f;
            if (lamp) {
                // rev. 25: the lamp in its reflector — the yellow of the lamp over the whole cell, a pale hot core in its middle
                float rr = Math.min(1f, r * 0.92f), rg = Math.min(1f, g * 0.92f), rb = Math.min(1f, b * 0.92f);
                quad(vc, mat, nm, z.cx() - hx, z.cy() - hy, cz, z.cx() + hx, z.cy() - hy, cz, z.cx() + hx, z.cy() + hy, cz, z.cx() - hx, z.cy() + hy, cz,
                    rr, rg, rb, rr, rg, rb);
                float lv = Math.max(r, Math.max(g, b)), ch = 0.5f, cz2 = cz - 0.002f;
                float pr_ = Math.min(1f, lv * 1.0f), pg = Math.min(1f, lv * 0.95f), pb = Math.min(1f, lv * (0.62f + 0.2f * b / Math.max(lv, 1e-3f)));
                quad(vc, mat, nm, z.cx() - hx * ch, z.cy() - hy * ch, cz2, z.cx() + hx * ch, z.cy() - hy * ch, cz2, z.cx() + hx * ch, z.cy() + hy * ch, cz2,
                    z.cx() - hx * ch, z.cy() + hy * ch, cz2, pr_, pg, pb, pr_, pg, pb);
            } else {
                float[] hc = hotCore(r, g, b);
                quad(vc, mat, nm, z.cx() - hx, z.cy() - hy, cz, z.cx() + hx, z.cy() - hy, cz, z.cx() + hx, z.cy() + hy, cz, z.cx() - hx, z.cy() + hy, cz,
                    hc[0], hc[1], hc[2], hc[0], hc[1], hc[2]);
            }
            if (bloom != null) {
                float bx = hx * bloomScale / cellScale * 1.35f, by = hy * bloomScale / cellScale * 1.35f, bz = cz - 0.004f, br = r * bloomK, bg = g * bloomK, bb = b * bloomK;
                quad(bloom, mat, nm, z.cx() - bx, z.cy() - by, bz, z.cx() + bx, z.cy() - by, bz, z.cx() + bx, z.cy() + by, bz, z.cx() - bx, z.cy() + by, bz,
                    br, bg, bb, br, bg, bb);
            }
        }
        // rev. 16b: the whole PULSESTRIKE face glows as one panel of light (its mean colour), behind the cells, as a blinder does
        if (panel && !lamp && bloom != null && nz > 0 && faceR + faceG + faceB > 0.01f) {
            float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, fz = 0f;
            for (int i = 0; i < nz; i++) {
                FixtureMeshes.Zone z = m.zones.get(i);
                x0 = Math.min(x0, z.cx() - z.hx()); x1 = Math.max(x1, z.cx() + z.hx()); y0 = Math.min(y0, z.cy() - z.hy()); y1 = Math.max(y1, z.cy() + z.hy());
                fz = z.cz();
            }
            float k = (flash ? 0.48f : 0.4f) / nz, mx = 0.25f * (x1 - x0), my = 0.6f * (y1 - y0), bz = fz - 0.008f;
            float fr = Math.min(1f, faceR * k), fg = Math.min(1f, faceG * k), fb = Math.min(1f, faceB * k);
            quad(bloom, mat, nm, x0 - mx, y0 - my, bz, x1 + mx, y0 - my, bz, x1 + mx, y1 + my, bz, x0 - mx, y1 + my, bz, fr, fg, fb, fr, fg, fb);
        }
        // rev. 16e: the lenses of a LUMORA seen from the side. The glow of a cell lies flat on the lens: from 30-60° off the axis it
        // shrank to a line and the lit fixture showed dark lenses (picture 6, 9 November 21 h). A real LED wash lens is a dome that
        // shows its source over a wide angle: each lit cell also gets a small sprite facing the camera, just in front of the lens,
        // bright on the axis and fading towards the side (gone behind the face).
        boolean lensArray = pr == FixtureProfile.LUMORA_W7 || pr == FixtureProfile.LUMORA_W19 || pr == FixtureProfile.PIXELWASH_W37;
        Matrix4f lmDome = local.last().pose();
        if ((lensArray || panel) && nz > 0) {
            boolean round = lamp;                                          // rev. 25: blinder lamps are round domes, not strips
            Matrix4f inv = new Matrix4f(lmDome).invert();
            Vector3f camL = inv.transformPosition(new Vector3f((float) (cam.x - bp.getX()), (float) (cam.y - bp.getY()), (float) (cam.z - bp.getZ())));
            var camera = LightTargets.camera();
            Vector3f left = inv.transformDirection(new Vector3f(camera.getLeftVector())).normalize();
            Vector3f up = inv.transformDirection(new Vector3f(camera.getUpVector())).normalize();
            Vector3f ax = new Vector3f(m.beamAxis[0], m.beamAxis[1], m.beamAxis[2]).normalize();
            for (int i = 0; i < nz; i++) {
                FixtureMeshes.Zone z = m.zones.get(i);
                if (z.hx() <= 0f) continue;
                float lv = 1f, r, g, b;
                float cb = cellFlash(pr, i, flash) ? 1.8f : 1.25f;
                if (pr.zones > 0 && i * 4 + 3 < o.zones.length) {
                    lv = Math.min(1f, (float) Math.sqrt(o.zones[i * 4 + 3] * intensity) * cb);
                    r = o.zones[i * 4] * lv; g = o.zones[i * 4 + 1] * lv; b = o.zones[i * 4 + 2] * lv;
                } else {
                    lv = Math.min(1f, (float) Math.sqrt(intensity) * cb);
                    r = o.beamR * lv; g = o.beamG * lv; b = o.beamB * lv;
                }
                if (r + g + b < 0.004f) continue;
                if (panel && !round) {
                    // rev. 16e — PULSESTRIKE M: each lit cell / strip is a tube of light, seen over almost the half space in front
                    // of the face (a strobe tube or LED strip behind a clear cover, not a flat sticker): a ribbon along the strip that
                    // always faces the camera, a hot core (additive) and a wider glow of the strip in the air (screen)
                    boolean alongX = z.hx() >= z.hy();
                    float lg = alongX ? z.hx() : z.hy(), sh = alongX ? z.hy() : z.hx();
                    Vector3f pa = alongX ? S_PA.set(1, 0, 0) : S_PA.set(0, 1, 0);
                    float off = sh * 0.6f + 0.004f;
                    Vector3f c = S_C.set(z.cx() + ax.x * off, z.cy() + ax.y * off, z.cz() + ax.z * off);
                    Vector3f to = S_TO.set(camL).sub(c);
                    float dd = to.length();
                    if (dd < 0.05f) continue;
                    float cos = ax.dot(to.div(dd));
                    if (cos < -0.1f) continue;
                    float side = Math.min(1f, (cos + 0.1f) / 0.35f);
                    Vector3f pb = S_PB.set(pa).cross(to);
                    if (pb.lengthSquared() < 1e-6f) continue;
                    pb.normalize();
                    boolean whiteCell = z.kind() == 1;
                    float ck = side * (whiteCell ? 0.95f : 0.75f);
                    float hot = whiteCell ? 0.35f : 0.12f;                         // the centre of a lit tube burns towards white
                    float[] hs = hotCore(Math.min(1f, r * ck + hot * lv * side), Math.min(1f, g * ck + hot * lv * side), Math.min(1f, b * ck + hot * lv * side));
                    streak(vc, mat, nm, c, pa, pb, lg * 1.02f, sh * 0.85f, hs[0], hs[1], hs[2]);
                    if (bloom != null) {
                        float gk = side * (cellFlash(pr, i, flash) ? 0.55f : 0.3f) * (whiteCell ? 1.2f : 1f);
                        streak(bloom, mat, nm, c, pa, pb, lg * 1.3f + sh * 2.5f, sh * 4.5f + 0.02f, r * gk, g * gk, b * gk);
                    }
                    continue;
                }
                float rad = Math.max(z.hx(), z.hy());
                float off = rad * 0.45f + 0.004f;
                Vector3f c = S_C.set(z.cx() + ax.x * off, z.cy() + ax.y * off, z.cz() + ax.z * off);
                Vector3f to = S_TO.set(camL).sub(c);
                float dd = to.length();
                if (dd < 0.05f) continue;
                float cos = ax.dot(to.div(dd));
                if (cos < -0.05f) continue;                                       // behind the face
                float side = Math.min(1f, (cos + 0.05f) / 0.35f);                  // fades in over the last ~20° before the face plane
                float k = side * side * (0.55f + 0.45f * cos) * (round ? 0.28f : 0.9f);
                float size = rad * (round ? 0.85f + 0.2f * cos : 1.25f + 0.35f * cos);
                float[] hd = hotCore(r * k, g * k, b * k);
                BeamRenderer.halo(vc, mat, nm, c, left, up, size, hd[0], hd[1], hd[2]);
            }
        }
        // flare: looking into the lens
        Matrix4f lm = lmDome;
        Vector3f o3 = lm.transformPosition(new Vector3f(m.beamOrigin[0], m.beamOrigin[1], m.beamOrigin[2]));
        Vector3f a3 = lm.transformDirection(new Vector3f(m.beamAxis[0], m.beamAxis[1], m.beamAxis[2])).normalize();
        Vector3f toCam = new Vector3f((float) (cam.x - bp.getX()) - o3.x, (float) (cam.y - bp.getY()) - o3.y, (float) (cam.z - bp.getZ()) - o3.z);
        float d = toCam.length();
        if (d < 0.5f) return;
        float align = Math.max(0f, a3.dot(toCam.div(d)));
        // rev. 16e: the lens itself, seen into: a bright disc the size of the lens (the exit of the optic, uniformly lit when the
        // eye is in the beam), barely larger on the axis. It was up to 6.4 lens radii: the large white disc of picture 7. The glow
        // around it is the halo and the haze of the beam.
        float a4 = align * align * align * align;
        float s = m.lensRadius * (1.05f + 0.45f * a4);
        float k = intensity * (0.3f + 0.7f * align * align) * (0.6f + 0.4f * a4);
        float fr = o.beamR * k, fg = o.beamG * k, fb = o.beamB * k;
        float cz = m.lensFrontZ - 0.006f;                        // rev. 6: in front of the lens, not inside it
        quad(vc, mat, nm, m.beamOrigin[0] - s, m.beamOrigin[1] - s, cz, m.beamOrigin[0] + s, m.beamOrigin[1] - s, cz,
            m.beamOrigin[0] + s, m.beamOrigin[1] + s, cz, m.beamOrigin[0] - s, m.beamOrigin[1] + s, cz, fr, fg, fb, fr, fg, fb);
    }

    /**
     * Rev. 21: the spill lights of one beam (VolumeLights#spill). Haze: a segment light along the beam (to the surface it meets, at most
     * 32 blocks), a few blocks wide, its strength following the haze. Bounce: a point light just above the footprint, as wide as the
     * footprint and some 12 blocks more, about a quarter of the light the footprint gets (a stage floor sends back a good part of it).
     */
    private static void spillAround(FixtureBlockEntity be, Vector3f origin, Vector3f axis, Matrix4f mat, float r0, float tan, float len, boolean hits,
                                    float throwLen, float nr, float ng, float nb, float stage, boolean spot, boolean strobe) {
        if (stage < 0.01f) return;
        Vector3f nP = null, pP = null;
        float L = hits ? len : Math.min(throwLen, 32f);
        Vector3f hit = new Vector3f(axis).mul(L).add(origin);
        if (hits && be.beamHitFace != null) {
            var nf = be.beamHitFace.getNormal();
            nP = mat.transformDirection(new Vector3f(nf.getX(), nf.getY(), nf.getZ())).normalize();
            pP = mat.transformPosition(new Vector3f(hit));
        }
        float foot = r0 + L * tan;
        float haze = LightClient.haze;
        if (haze > 0.01f && !strobe) {
            // a beam is a thin line of bright haze: its light reaches a few blocks; a wash is a wide and dimmer volume
            float width = r0 + 0.5f * L * tan;
            float k = (spot ? 0.16f : 0.10f) * haze * stage;
            Vector3f a = mat.transformPosition(new Vector3f(axis).mul(0.6f).add(origin));
            Vector3f b = mat.transformPosition(new Vector3f(axis).mul(Math.max(0.7f, L - 0.3f)).add(origin));
            // not on the surface it lands on (that is the projection's light): from 0.2 block above it
            // and never below the lowest point of the beam (the haze of a beam on a deck does not light the scaffolding under it)
            Vector3f up = mat.transformDirection(new Vector3f(0f, 1f, 0f)).normalize();
            Vector3f low = mat.transformPosition(new Vector3f(axis).mul(axis.y < 0f ? Math.max(0.7f, L - 0.3f) : 0.6f).add(origin).add(0f, -0.3f, 0f));
            VolumeLights.spill(a, b, nr, ng, nb, k, Math.min(width + 3f, 6f), width + 1.2f, 0.45f, nP, pP, 0.2f, up, low);
        }
        if (hits && nP != null) {
            Vector3f c = new Vector3f(hit);
            var nf = be.beamHitFace.getNormal();
            c.add(nf.getX() * 0.4f, nf.getY() * 0.4f, nf.getZ() * 0.4f);
            // about a tenth of what the footprint gets, spread over the half space above it; never on the surface it comes from
            float k = 0.09f * stage / (1f + 0.08f * foot);
            Vector3f cp = mat.transformPosition(new Vector3f(c));
            VolumeLights.spill(cp, cp, nr, ng, nb, k, Math.min(foot + 10f, 20f), foot + 1.5f, 0.15f, nP, pP, 0.35f);
        }
    }

    // ------------------------------------------------------------ beam and lit surfaces (rev. 5)
    private static final float[] OFF = new float[2];

    /**
     * The light of a fixture, in two separate parts (see {@link BeamOptics}):
     * <ol>
     *   <li>the beam in the air: soft ribbon + discs ({@link BeamRenderer#softBeam}), screen-blended, brightness from the 1/r law, so a
     *   tight VORTEX beam carries across the stage while a wide wash glows near the fixture and fades, and a blinder shows no beam
     *   except a short burst of haze on a flash;</li>
     *   <li>the light on surfaces: a few rays sample what is under the beam (floor, platform, instruments, truss, backdrop) and keep it for
     *   several frames; each surface reached gets the light projected on it ({@link LightRenderTypes#modulate}: its texture stays, it
     *   takes the colour), with Shimmer lights over the footprint when Shimmer is installed (real coloured lighting of the blocks around).
     *   The VORTEX projects its gobo.</li>
     * </ol>
     */
    private void beam(FixtureBlockEntity be, Level level, FixtureProfile pr, FixtureMeshes.Model m, Engine.Output o, float intensity,
                      boolean flash, int q, Vector3f origin, Vector3f axis, PoseStack pose, MultiBufferSource buffers, Vec3 cam, BlockPos bp) {
        float throwLen = throwOf(pr);
        boolean spotType = pr.isSpot(), strobeType = pr.isPanel();
        float angle = pr.beamAngle(o.get(Attr.ZOOM));
        if (pr.has(Attr.FROST)) angle += o.get(Attr.FROST) * 22f;
        if (pr == FixtureProfile.LASERCORE_L1) angle = 30f;
        float iris = pr.has(Attr.IRIS) ? o.get(Attr.IRIS) : 1f;
        if (pr.has(Attr.IRIS)) angle *= 0.25f + 0.75f * iris;
        // 1.2.0 External Show Control: the PULSESTRIKE M has a real zoom (20–70°, profile beamAngle); its focus only sets the edge
        float tanHalf = (float) Math.tan(Math.toRadians(Math.max(0.6f, angle) / 2));
        float r0 = m.lensRadius * 0.9f * pr.renderScale;
        // rev. 9: the iris also narrows the exit of the beam and holds back part of the light
        if (pr.has(Attr.IRIS)) { r0 *= BeamOptics.irisExit(iris); intensity *= BeamOptics.irisLight(iris); }
        // rev. 22: each gobo fixture has its own wheel (GoboWheels); the PHANTOM S1 also shows its second wheel when the first is open
        ResourceLocation goboTex = goboTexture(pr, o);
        boolean gobo = goboTex != null;
        boolean prism = (pr.has(Attr.PRISM) && o.get(Attr.PRISM) > 0.5f || pr.has(Attr.PRISM_2) && o.get(Attr.PRISM_2) > 0.5f) && q > 0;
        Vector3f[] axes = prism ? prismAxes(axis, be.prismAngle, pr.prismFacets) : new Vector3f[] {axis};
        float split = prism ? 0.5f : 1f;
        // rev. 16: the optical state of the fixture for this frame (Optical Simulation System): the profile of its family, built once
        // from its model, and what it does now. The beam in the haze and the light on surfaces read the same mask; with the prism the
        // mask splits the light into its three images inside one wider cone (no more three separate cones in the light passes)
        float sharpO = pr.has(Attr.FOCUS) ? BeamOptics.focusSharpness(o.get(Attr.FOCUS)) : pr.fixedSharpness;
        if (pr.has(Attr.FROST)) sharpO *= 1f - o.get(Attr.FROST);
        VolumeLights.Optics optics = OPTICS_STATE.set(optics(m, pr), gobo, prism, (float) Math.toRadians(be.prismAngle), angle, sharpO, o.zones).effects(o, (level.getGameTime() + Minecraft.getInstance().getFrameTime()) / 20.0);
        float tanO = prism ? tanHalf * com.concertstage.light.core.OpticalProfile.PRISM_WIDEN + 0.02f : tanHalf;
        LightDiagnostics.fixtures++;
        if (gobo) LightDiagnostics.gobos++;
        if (prism) LightDiagnostics.prisms++;

        // ---- what is under the beam: sampled rays, kept until the head moves or after a while
        sampleSurfaces(be, level, origin, axis, tanHalf, r0, throwLen, q, bp);
        float len = Math.max(0.2f, be.beamLength);
        boolean hits = be.beamHitFace != null && be.beamLength < throwLen - 0.01f;

        float flux = intensity * pr.output;
        float cr = o.beamR, cg = o.beamG, cb = o.beamB;
        float peak = Math.max(1e-3f, Math.max(cr, Math.max(cg, cb)));
        float nr = cr / peak, ng = cg / peak, nb = cb / peak;           // hue of the light, brightest channel = 1
        Vector3f camL = new Vector3f((float) (cam.x - bp.getX()), (float) (cam.y - bp.getY()), (float) (cam.z - bp.getZ()));
        Matrix4f mat = pose.last().pose();
        Matrix3f nm = pose.last().normal();

        // ---- 1. the beam in the air (rev. 8: core + soft body + subtle halo for every fixture type, faded near the camera and just
        // before the surface it meets, washes thinner in the air the wider their zoom)
        float air = flux * LightClient.haze * BeamOptics.AIR_DENSITY * LightClient.beamBrightness * airBalance(pr);
        float stage = flux * STAGE_GAIN * LightClient.stageBrightness * stageBalance(pr);   // rev. 17: light on the blocks
        VertexConsumer vc = LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM);
        // rev. 1: the laser draws the lines of its figure (LaserBeams) instead of a 30° cone of haze; its projection stays
        boolean laser = pr == FixtureProfile.LASERCORE_L1 && !legacyLaser;
        if (laser && air > 0.001f && LightDiagnostics.air()) {
            VertexConsumer dots = LightBatch.buffer(buffers, LightRenderTypes.of(GLOW), LightBatch.PASS_EMIT);
            LaserBeams.draw(vc, dots, mat, nm, be, level, o, (level.getGameTime() + Minecraft.getInstance().getFrameTime()) / 20.0, origin, axis, r0,
                tanHalf, throwLen, camL, nr, ng, nb, LASER_AIR * air, true, level.getGameTime());
            LightDiagnostics.beams++;
        }

        // ---- rev. 15: shadows of the structures and the beam computed in the haze
        // The fixture asks for its shadow map (what its optic sees: truss tubes, braces, blocks — BeamShadows). The projected light
        // then stops behind every tube, and the beam in the air is computed along each line of sight (VolumeLights.volume): its
        // pattern turns with the gobo, opens with the zoom, and the shadow of each tube runs through the haze. When it cannot (low
        // quality, prism, shader pack, budget used), the ribbons below are drawn as before.
        Vector3f sideL = new Vector3f(SIDE);                         // 1.2.0: the fixture's own side (see SIDE)
        // rev. 21: the places of the frame are decided first, and only a fixture that got a projection or a beam in the haze asks for a
        // shadow map. Before, every beam asked in the order the game drew them: with fewer maps than fixtures (medium quality: 32), a
        // fixture could get its projection without its map — its light went through the deck (seen in game, 11 November)
        // rev. 22: low quality uses the same light (haze beams, projections, shadow maps) with smaller budgets, instead of a second
        // renderer of its own (flat ribbons, decals with no shadow): the look no longer changes with the quality, only how many
        // fixtures get it. Beyond the budgets the ribbons stay.
        boolean wantVolume = !laser && air > 0.001f && LightDiagnostics.air() && Perf.VOLUMES.admit(be, SCORE, pr.autoRole());
        boolean wantProjection = LightDiagnostics.surfaces() && Perf.PROJECTIONS.admit(be, SCORE, pr.autoRole());
        int shadow = (wantVolume || wantProjection)
            ? BeamShadows.request(be, level, bp, mat, origin, axis, sideL, r0, tanO * 1.05f, throwLen, q) : -1;
        float range = hits ? len + 1.5f : throwLen;
        boolean shadowed = shadow >= 0 && BeamShadows.texture(shadow) >= 0;
        // with shadows the light goes on past the surface the centre of the beam meets (the parts that miss it, the gaps of a truss),
        // as far as the shadow map reaches
        if (shadowed) range = hits && len + 1.5f > BeamShadows.far(q) ? len + 1.5f : Math.min(throwLen, BeamShadows.far(q));
        float projRange = range;
        if (hits) {
            // rev. 20: a beam grazing the surface it meets (a wash aimed at the audience floor) lights an ellipse that reaches much
            // further along the axis than the centre: the range covers that far edge (it ended on a hard straight line across the floor)
            var nf = be.beamHitFace.getNormal();
            float cosI = Math.abs(axis.x * nf.getX() + axis.y * nf.getY() + axis.z * nf.getZ());
            float inc = (float) Math.acos(Math.min(1f, cosI)), half = (float) Math.atan(tanO);
            float far = inc + half < 1.45f ? len * cosI / (float) Math.cos(inc + half) * (float) Math.cos(half) + (r0 + 0.5f) : throwLen;
            projRange = Math.max(projRange, Math.min(throwLen, far + 1.0f));
        }
        boolean volumetric = false;
        if (wantVolume) {
            // rev. 16: every family in the haze through the common optical mask (OpticalProfile): VORTEX beam / spot / gobo / prism,
            // W7 and W19 lens arrays, PULSESTRIKE face (blinder, and the haze lit by each flash). Strengths: the ones of the ribbons
            // they replace, the mean of the masks being about 1 (OpticsTest)
            float kv, rV = spotType ? Math.max(r0, 0.09f) : r0;
            if (spotType) kv = VORTEX_AIR * air * (gobo ? 0.9f * (0.6f + 0.4f * sharpO) : 0.65f);
            // rev. 16b (pictures of 9 November): washes almost invisible in the air from the audience -> about twice their haze; the
            // PULSESTRIKE row built a white wall over the stage front -> a short, gentle glow of haze in front of the face, brighter on
            // a flash but never across the audience
            else if (strobeType) kv = (flash ? 0.32f : 0.1f) * BeamOptics.washAirScale(tanHalf) * air;
            // rev. 16d: back to the rev. 16 strength — washes were invisible from the audience because far fixtures had no beam in
            // the haze at all (distance tiers, fixed above), not because of their strength; x1.9 made walls of colour up close
            else kv = (pr == FixtureProfile.LUMORA_W19 || pr == FixtureProfile.PIXELWASH_W37 ? 0.85f : 0.75f) * WASH_AIR * BeamOptics.washAirScale(tanHalf) * air;
            // with the colours of the cells (OpticalProfile#tints) the colour is in the mask: the haze strength stays, grey
            float pk = Math.max(cr, Math.max(cg, cb));
            float vr = optics.tinted ? pk : cr, vg = optics.tinted ? pk : cg, vb = optics.tinted ? pk : cb;
            float vRange = shadowed ? range : (hits ? len + 0.3f : throwLen);
            if (strobeType) vRange = Math.min(vRange, flash ? 9f : 5f);       // a blinder lights the haze in front of it, not across the arena
            float endFade = vRange >= throwLen - 0.01f || strobeType ? Math.max(2f, vRange * 0.4f) : (hits && !shadowed ? 0f : 2f);
            Vector3f oV = mat.transformPosition(new Vector3f(origin));
            Vector3f aV = mat.transformDirection(new Vector3f(axis)).normalize();
            Vector3f sV = mat.transformDirection(new Vector3f(sideL)).normalize();
            volumetric = VolumeLights.volume(oV, aV, sV, tanO, gobo ? be.goboAngle : 0f, vRange, rV, vr * kv, vg * kv, vb * kv,
                BeamOptics.ABSORPTION * (0.6f + 0.4f * LightClient.haze), spotType ? (1f - sharpO) * 0.09f : 0f, endFade,
                (gobo ? goboTex : GOBOS[0]), q, shadow, optics);
            if (volumetric) LightDiagnostics.beams++;
        }
        for (Vector3f ax : volumetric || laser || !LightDiagnostics.air() ? new Vector3f[0] : axes) {
            if (spotType) {
                // beam / spot: a precise core whose width follows the zoom, never thinner than the exit of the optic (a beam in haze
                // is a shaft, not a laser line), a soft body, and a subtle halo of scattered light; the core fades as the zoom opens
                float tight = Math.max(0f, Math.min(1f, (14f - angle) / 12f));
                float rb = Math.max(r0, 0.09f);
                // rev. 9: an open iris with a sharp focus shows the clean edge of the optic in the haze (a spot), a closed iris and a
                // soft focus a soft shaft (a beam). The soft body gives way to a body with a defined edge as the iris opens.
                float sharpF = pr.has(Attr.FOCUS) ? BeamOptics.focusSharpness(o.get(Attr.FOCUS)) : pr.fixedSharpness;
                float edge = BeamOptics.edgeDefinition(iris, sharpF);
                // rev. 13: with a gobo in, the beam IS the gobo: a bundle of shafts shaped like the pattern (see GoboShafts); the
                // full body stays only as a faint fill of haze between them, the defined edge and the tight core are not drawn
                boolean shafts = gobo && q >= BeamOptics.MEDIUM;
                if (shafts) {
                    goboShafts(vc, mat, nm, origin, ax, axis, len, rb, tanHalf, camL, cr, cg, cb, VORTEX_AIR * 1.1f * air * split * (0.6f + 0.4f * sharpF), hits,
                        goboTex, be.goboAngle, q, (1f - sharpF));
                    edge = 0f;
                    tight = 0f;
                }
                float k = VORTEX_AIR * (shafts ? 0.12f : 0.5f) * air * split;
                BeamRenderer.softBeam(vc, mat, nm, origin, ax, len, rb, tanHalf, camL, cr * k * (1f - 0.65f * edge), cg * k * (1f - 0.65f * edge),
                    cb * k * (1f - 0.65f * edge), hits, q, 1f, true);
                if (edge > 0.02f) {
                    VertexConsumer ev = LightBatch.buffer(buffers, LightRenderTypes.screen(SPOT_OPEN), LightBatch.PASS_BEAM);
                    float ke = VORTEX_AIR * 0.5f * edge * air * split;
                    BeamRenderer.softBeam(ev, mat, nm, origin, ax, len, rb, tanHalf, camL, cr * ke, cg * ke, cb * ke, hits, q, 0.95f, false);
                }
                if (tight > 0.02f) {
                    float kc = VORTEX_AIR * 0.6f * tight * air * split;
                    BeamRenderer.softBeam(vc, mat, nm, origin, ax, len, rb * 0.6f, tanHalf * 0.6f, camL, cr * kc, cg * kc, cb * kc, hits, q, 0.75f, false);
                }
                float kh = VORTEX_AIR * 0.2f * (1f - 0.5f * edge) * air * split;
                BeamRenderer.softBeam(vc, mat, nm, origin, ax, len, rb * 2.2f, tanHalf * 1.8f + 0.015f, camL, cr * kh, cg * kh, cb * kh, hits, q, 1f, false);
            } else if (strobeType) {
                // blinder: no beam in normal use; a flash lights the haze in front of it for a few blocks. Rev. 9: with the focus
                // tightened (a punch), the light carries further and a faint shaft stays visible between flashes
                float focus = pr.has(Attr.FOCUS) ? o.get(Attr.FOCUS) : 0f;
                float reach = 9f + 16f * focus;
                if (flash) {
                    float k = (0.45f + 0.25f * focus) * air;
                    BeamRenderer.softBeam(vc, mat, nm, origin, ax, Math.min(len, reach), r0 * 1.4f, tanHalf, camL, cr * k, cg * k, cb * k, false, q, 1f, true);
                } else if (focus > 0.3f) {
                    float k = 0.25f * (focus - 0.3f) / 0.7f * air;
                    BeamRenderer.softBeam(vc, mat, nm, origin, ax, Math.min(len, reach), r0 * 1.4f, tanHalf, camL, cr * k, cg * k, cb * k, false, q, 1f, true);
                }
            } else {
                // wash: soft, wide body; the wider the zoom, the thinner its light in the air (same light in a larger volume), so a row
                // of washes stays a soft glow instead of a wall of colour. A denser core on the axis; the W19 wider and stronger.
                boolean w19 = pr == FixtureProfile.LUMORA_W19;
                float wide = BeamOptics.washAirScale(tanHalf);
                float k = (w19 ? 0.75f : 0.65f) * WASH_AIR * wide * air * split;
                BeamRenderer.softBeam(vc, mat, nm, origin, ax, len, r0, tanHalf * (w19 ? 1.05f : 1f), camL, cr * k, cg * k, cb * k, hits, q, 1f, true);
                if (q >= BeamOptics.MEDIUM) {
                    float kc = 0.28f * WASH_AIR * wide * air * split;
                    BeamRenderer.softBeam(vc, mat, nm, origin, ax, len, r0 * 0.8f, tanHalf * 0.55f, camL, cr * kc, cg * kc, cb * kc, hits, q, 1f, false);
                }
            }
        }
        // rev. 13: the old gobo strands texture on the cone is replaced by the gobo shafts (goboShafts)

        // ---- 2. the light on surfaces
        // rev. 10: the fixture projects its light (gobo / open spot / soft wash) on everything drawn in its cone — truss bars, the
        // gaps between them and what is behind, floor, instruments, tarps — with CSC's projector shader. When it can (no shader
        // pack, budget of the quality level), the sampled surfaces below are not needed any more.
        boolean projected = false;
        if (wantProjection) {
            // rev. 16: one projection through the common optical mask (gobo, prism images, lens array, blinder face included)
            float sharp = pr.has(Attr.FOCUS) && (spotType || strobeType) ? sharpO : pr.conventional ? pr.fixedSharpness : 1f;
            Vector3f sideA = new Vector3f(sideL);
            Vector3f oP = mat.transformPosition(new Vector3f(origin));
            Vector3f aP = mat.transformDirection(new Vector3f(axis)).normalize();
            Vector3f sP = mat.transformDirection(sideA).normalize();
            float pr0 = optics.tinted ? 1f : nr, pg0 = optics.tinted ? 1f : ng, pb0 = optics.tinted ? 1f : nb;
            // 1.2.0 beta 2b: the strobes and the bars put so much light on the floor that their whole patch sat at the ceiling of the
            // light composite (measured: flat at 164 / 255 from edge to edge, picture of 13 November) — their footprint now has a hot
            // centre (concertstage_optics.glsl), and their light on surfaces is lowered so that it shows: the axis still reaches the
            // ceiling, the light falls off towards the edge, and a flash (x1.5) stands out from the steady light
            float floorK = barOrStrobe(pr) ? 0.15f : 1f;
            projected = VolumeLights.project(oP, aP, sP, tanO, gobo ? be.goboAngle : 0f, projRange, r0, pr0, pg0, pb0,
                stage * floorK * (strobeType && flash ? 1.5f : 1f), (gobo ? goboTex : GOBOS[0]), spotType ? (1f - sharp) * 0.09f : 0f, q, shadow, optics);
            if (projected) LightDiagnostics.projections++;
        }
        // rev. 21: the light around the beam — the haze it lights lights what is beside it (every side of a truss next to a beam), and
        // the surface it lands on sends some back up (the underside of the truss and the roof over a lit deck). Both stay on the side
        // of the surface the beam meets (nothing under a lit deck). Pictures 3 and 4 of 11 November: a truss lit on one side only.
        if (projected && LightClient.rigLight > 0.01f && LightDiagnostics.surfaces()) spillAround(be, origin, axis, mat, r0, tanO, len, hits, throwLen,
            nr, ng, nb, stage * (strobeType && flash ? 1.5f : 1f) * LightClient.rigLight, spotType, strobeType);
        if (!LightDiagnostics.pointLights()) {
            // diagnostic views: nothing but the selected contribution
            PointLights.dark(be); for (Object k : be.spreadLights) PointLights.dark(k);
            Vector3f end = new Vector3f(axis).mul(len).add(origin);
            be.setBeamEnd(bp.getX() + end.x, bp.getY() + end.y, bp.getZ() + end.z, BeamOptics.radius(r0, tanHalf, len));
            return;
        }
        boolean shimmer = PointLights.active();   // rev. 9: Shimmer, or CSC's own coloured lights
        float decalScale = shimmer ? 0.45f : 1f;                     // with Shimmer the blocks are really lit: the projection only shapes it
        VertexConsumer sv = LightBatch.buffer(buffers, LightRenderTypes.modulate(SOFT), LightBatch.PASS_SURFACE);
        // rev. 21: the flat light decals of the sampled hits are kept only when CSC's own surface light is off (shader pack, setting):
        // a decal is a flat quad on the surface a ray met, and where it overhangs the edge of a deck it floats in front of what is under
        // the deck and multiplies it (seen in game at medium quality: the scaffolding under the stage front lit through the planks).
        // Otherwise a fixture without a projection lights its footprint with spill lights (footprintSpill, read from the depth)
        int count = projected || VolumeLights.enabled() ? 0 : be.surfaceCount;
        Vector3f c = new Vector3f(), n = new Vector3f();
        for (int i = 0; i < count; i++) {
            int b = i * FixtureBlockEntity.HIT_STRIDE;
            float dist = be.surfaceHits[b + 6];
            if (dist < 0f) continue;
            float foot = BeamOptics.radius(r0, tanHalf, dist);
            float e = BeamOptics.surface(stage, dist, foot, be.surfaceHits[b + 7]) * BeamOptics.rayWeight(i, count) * decalScale
                * (strobeType && flash ? 1.5f : 1f);
            if (e < 0.01f) continue;
            n.set(be.surfaceHits[b + 3], be.surfaceHits[b + 4], be.surfaceHits[b + 5]);
            c.set(be.surfaceHits[b], be.surfaceHits[b + 1], be.surfaceHits[b + 2]).add(n.x * 0.02f, n.y * 0.02f, n.z * 0.02f);
            float rad = BeamOptics.decalRadius(i, count, foot) * (spotType && !gobo ? 1.15f : 1f);
            // rev. 8: stretched along the slope of the beam (1 / cos incidence) like a real projection, soft profile to its edge
            relight(sv, mat, nm, c, n, axis, be.surfaceHits[b + 7], rad, BeamOptics.relight(e * split, be.surfaceHits[b + 8]), nr, ng, nb, true, 0f);
        }
        // the gobo (or the hard edge of the spot) projected where the centre ray lands
        if (spotType && count > 0 && be.surfaceHits[6] >= 0f && q > 0) {
            // rev. 8: an open spot uses a profile with a defined but soft edge and a gentle hot centre (no flat uniform disc)
            VertexConsumer gv = LightBatch.buffer(buffers, LightRenderTypes.modulate(gobo ? goboTex : SPOT_OPEN), LightBatch.PASS_SURFACE);
            float dist = be.surfaceHits[6];
            float foot = BeamOptics.radius(r0, tanHalf, dist);
            n.set(be.surfaceHits[3], be.surfaceHits[4], be.surfaceHits[5]);
            float cosI = Math.max(0.25f, be.surfaceHits[7]);
            float e = BeamOptics.surface(stage, dist, foot, cosI) * 0.9f;
            // rev. 6: focus. Sharp at the middle of the focus range, softened on both sides: the pattern is drawn three times at
            // slightly different sizes, which blurs its edges like an optic out of focus
            float sharp = pr.has(Attr.FOCUS) ? BeamOptics.focusSharpness(o.get(Attr.FOCUS)) : pr.fixedSharpness;
            float spread = (1f - sharp) * 0.09f;
            float factor = BeamOptics.relight(e * split, be.surfaceHits[8]);
            VertexConsumer glowGobo = LightBatch.buffer(buffers, LightRenderTypes.of(gobo ? goboTex : GOBOS[0]), LightBatch.PASS_EMIT);
            for (Vector3f ax : axes) {
                c.set(ax).mul(dist).add(origin).add(n.x * 0.025f, n.y * 0.025f, n.z * 0.025f);
                if (spread < 0.005f) relight(gv, mat, nm, c, n, ax, cosI, foot, factor, nr, ng, nb, true, gobo ? be.goboAngle : 0f);
                else {
                    float f3 = (float) Math.cbrt(factor);              // three layers multiply back to the same light
                    for (int k = -1; k <= 1; k++)
                        relight(gv, mat, nm, c, n, ax, cosI, foot * (1f + spread * k), f3, nr, ng, nb, true, gobo ? be.goboAngle : 0f);
                }
                // a faint additive layer: the pattern stays readable on very dark materials (images 1 and 5), never in the air
                // since it is only drawn where the centre ray met a surface
                float add = gobo ? Math.min(0.15f, 0.05f * e * split) : 0f;
                if (add > 0.01f) spot(glowGobo, mat, nm, c, n, ax, cosI, foot, gobo ? be.goboAngle : 0f, nr * add, ng * add, nb * add);
            }
        }
        // Shimmer: real coloured light on the blocks — at the centre of the footprint, and spread over it for a wide beam
        // rev. 21: without the Shimmer mod, a fixture with no projection this frame (budget) no longer puts CSC point lights around its
        // footprint: they had no shadow and lit what is under a lit deck (the scaffolding under the stage front, seen in game). Its
        // footprint gets spill lights that stay on the side of the surfaces its rays met (VolumeLights#spill with their plane)
        if (shimmer && !projected && !(LightClient.shimmer && ShimmerLights.available())) {
            PointLights.dark(be); for (Object k : be.spreadLights) PointLights.dark(k);
            footprintSpill(be, mat, stage * (strobeType && flash ? 1.5f : 1f), r0, tanHalf, nr, ng, nb, spotType);
        }
        else if (shimmer && !projected) shimmerLights(be, pr, o, stage, flash, strobeType, spotType, r0, tanHalf, q, bp);
        else if (projected && LightClient.shimmer && ShimmerLights.available()) {
            // with Shimmer: keep one softer light at the centre of the footprint (Shimmer also lights entities and particles)
            // rev. 19: a Shimmer light has no shadow; with the shadowed projection it is kept faint, otherwise the shadow of a truss
            // or a set piece takes the colour of the fixture instead of being a place with less light
            shimmerLights(be, pr, o, stage * (shadowed ? 0.2f : 0.5f), flash, strobeType, spotType, r0, tanHalf, BeamOptics.MEDIUM, bp);
        }
        else { PointLights.dark(be); for (Object k : be.spreadLights) PointLights.dark(k); }

        // render box: from the fixture to the end of the beam (frustum culling of fixtures off screen)
        Vector3f end = new Vector3f(axis).mul(len).add(origin);
        be.setBeamEnd(bp.getX() + end.x, bp.getY() + end.y, bp.getZ() + end.z, BeamOptics.radius(r0, tanHalf, len));
    }

    /**
     * Samples the surfaces under the beam: the centre ray (it also gives the length of the beam) and, for a wide footprint, a ring of
     * rays ({@link BeamOptics#rays}). Resampled when the head has turned by more than a tenth of the cone, or every few frames, within
     * a ray budget per frame; between two samples the last result is kept.
     */
    private static void sampleSurfaces(FixtureBlockEntity be, Level level, Vector3f origin, Vector3f axis, float tanHalf, float r0,
                                       float throwLen, int q, BlockPos bp) {
        int frameNo = (int) Perf.rayTick;
        int every = q >= BeamOptics.HIGH ? 8 : q == BeamOptics.MEDIUM ? 16 : 30;
        float moved = be.surfTan < 0f ? 9f
            : (float) Math.sqrt(sq(axis.x - be.surfAx) + sq(axis.y - be.surfAy) + sq(axis.z - be.surfAz)) / Math.max(0.01f, tanHalf);
        boolean stale = frameNo - be.surfaceFrame >= every || Math.abs(tanHalf - be.surfTan) > be.surfTan * 0.1f + 0.002f;
        boolean need = be.beamLength < 0 || moved > 0.1f || stale;
        if (!need) return;
        float footGuess = BeamOptics.radius(r0, tanHalf, be.beamLength > 0 ? be.beamLength : throwLen * 0.5f);
        int rays = BeamOptics.rays(q, footGuess);
        // budget: a beam with no length yet always gets its centre ray
        if (be.beamLength >= 0 && Perf.raysThisFrame + rays > BeamOptics.rayBudget(q)) {
            if (moved > 0.6f && Perf.raysThisFrame < BeamOptics.rayBudget(q) + 32) rays = 1;   // keep at least the centre following the head
            else return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        Perf.raysThisFrame += rays;
        Vector3f u = Math.abs(axis.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        u.cross(axis).normalize();
        Vector3f v = new Vector3f(axis).cross(u).normalize();
        Vector3f dir = new Vector3f();
        double sx = bp.getX() + origin.x + axis.x * 0.3, sy = bp.getY() + origin.y + axis.y * 0.3, sz = bp.getZ() + origin.z + axis.z * 0.3;
        for (int i = 0; i < rays; i++) {
            BeamOptics.rayOffset(i, rays, OFF);
            dir.set(axis).add(u.x * OFF[0] * tanHalf + v.x * OFF[1] * tanHalf, u.y * OFF[0] * tanHalf + v.y * OFF[1] * tanHalf,
                u.z * OFF[0] * tanHalf + v.z * OFF[1] * tanHalf).normalize();
            Vec3 start = new Vec3(sx, sy, sz);
            Vec3 stop = start.add(dir.x * throwLen, dir.y * throwLen, dir.z * throwLen);
            BlockHitResult hit = clipRig(level, start, stop, bp);
            int b = i * FixtureBlockEntity.HIT_STRIDE;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                Vec3 h = hit.getLocation();
                Direction f = hit.getDirection();
                float d = (float) h.distanceTo(start) + 0.3f;
                be.surfaceHits[b] = (float) (h.x - bp.getX()); be.surfaceHits[b + 1] = (float) (h.y - bp.getY()); be.surfaceHits[b + 2] = (float) (h.z - bp.getZ());
                be.surfaceHits[b + 3] = f.getStepX(); be.surfaceHits[b + 4] = f.getStepY(); be.surfaceHits[b + 5] = f.getStepZ();
                be.surfaceHits[b + 6] = d;
                be.surfaceHits[b + 7] = Math.abs(dir.x * f.getStepX() + dir.y * f.getStepY() + dir.z * f.getStepZ());
                // how lit the surface already is (block light, or sky light minus the night): the projected light is relative to it
                BlockPos lp = hit.getBlockPos().relative(f);
                int bl = level.getBrightness(LightLayer.BLOCK, lp), sl = level.getBrightness(LightLayer.SKY, lp) - level.getSkyDarken();
                be.surfaceHits[b + 8] = Math.max(0, Math.max(bl, sl)) / 15f;
                if (i == 0) { be.beamLength = d; be.beamHitFace = f; }
            } else {
                be.surfaceHits[b + 6] = -1f;
                if (i == 0) { be.beamLength = throwLen; be.beamHitFace = null; }
            }
        }
        // a centre-only update (ray budget used up while the head moves) drops the ring until the next full sample
        be.surfaceCount = rays;
        be.surfaceFrame = frameNo;
        be.beamCheckFrame = frameNo;
        be.surfAx = axis.x; be.surfAy = axis.y; be.surfAz = axis.z; be.surfTan = tanHalf;
    }

    /**
     * Multiplies the surface by {@code factor} (coloured by the light) with as many dst × (1 + src) passes as needed: one pass can at
     * most double what is drawn, a beam on a dark stage at night needs several.
     */
    private static void relight(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f c, Vector3f n, Vector3f beamAxis, float cosI, float rad,
                                float factor, float nr, float ng, float nb, boolean goboSpot, float goboAngle) {
        for (int pass = 0; pass < 4 && factor > 1.01f; pass++) {
            float s1 = Math.min(1f, factor - 1f);
            if (goboSpot) spot(vc, mat, nm, c, n, beamAxis, cosI, rad, goboAngle, nr * s1, ng * s1, nb * s1);
            else BeamRenderer.pool(vc, mat, nm, c, n, rad, nr * s1, ng * s1, nb * s1);
            factor /= 1f + s1;
        }
    }

    /**
     * Rev. 9: the surface a ray of light meets, ignoring the fixture's own block and the other fixtures (their blocks are invisible
     * boxes: light stopped or projected on them floated in the air — and when a ray started inside the truss above, its light was
     * projected inside the truss, through the fixtures hanging under it). A ray starting inside a block skips that block.
     */
    static BlockHitResult clipRig(Level level, Vec3 from, Vec3 to, BlockPos self) {
        return net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, level, (lvl, pos) -> {
            if (pos.equals(self)) return null;
            // rev. 20: the sheets drawn by CSC renderers with no block where they are (stage roofs, FOH roofs) stop the light too
            BlockHitResult sheet = LightBlockers.clip(pos, from, to);
            net.minecraft.world.level.block.state.BlockState st = lvl.getBlockState(pos);
            if (st.isAir() || st.getBlock() instanceof FixtureBlock) return sheet;
            // rev. 20: tarps, curtains and FOH roof plates answer with their light shape (their outline is empty: a beam aimed at a
            // tarp went through it — seen in game on the author's stage)
            net.minecraft.world.phys.shapes.VoxelShape ls = BeamShadows.sheetShape(lvl, pos, st);
            if (ls != null && !ls.isEmpty()) {
                BlockHitResult h = ls.clip(from, to, pos.immutable());
                if (h != null && !h.isInside()) return nearer(from, h, sheet);
                return sheet;
            }
            if (sheet != null) return sheet;
            // rev. 11: a truss / tower cell answers with its tubes only (the same lattice as for Theatrical beams): light goes through
            // the gaps, lands on the tubes it really meets and on what is behind — a gobo breaks up across the truss
            net.minecraft.world.phys.shapes.VoxelShape sh = com.concertstage.compat.LightCompat.lattice(st.getBlock(), st);
            // rev. 16d: a shape with no large face (a truss cell, a rail...) never stops a ray: known per block state, without
            // clipping it (the rays now go through the trusses; testing every cell crossed cost 4 to 7 ms a frame for 40 fixtures)
            if (sh != null || !LARGE.computeIfAbsent(st, s0 -> hasLargeBox(s0.getVisualShape(lvl, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()))
                    || hasLargeBox(s0.getShape(lvl, pos)))) return null;
            sh = st.getVisualShape(lvl, pos, net.minecraft.world.phys.shapes.CollisionContext.empty());
            if (sh.isEmpty()) sh = st.getShape(lvl, pos);
            if (sh.isEmpty()) return null;
            BlockHitResult h = sh.clip(from, to, pos.immutable());
            if (h == null || h.isInside()) return null;
            // rev. 16c: only a real surface stops the ray — the face it meets must be large (a floor, a wall, a slab, a step, a tarp).
            // A truss tube, a diagonal, a rail, the small parts of a model of another mod let it through: the ray goes on to what is
            // behind. Before, a ray stopped on a tube: a flat projection as wide as the beam was laid at that point (it floated in
            // the openings of the truss, as on an invisible surface) and the beam ended there. The light the tubes really receive
            // comes from the projection on the depth buffer, their shadows from the shadow maps.
            return largeFace(sh, h, pos) ? h : null;
        }, lvl -> null);
    }

    private static BlockHitResult nearer(Vec3 from, BlockHitResult a, BlockHitResult b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getLocation().distanceToSqr(from) <= b.getLocation().distanceToSqr(from) ? a : b;
    }

    /** Rev. 16d: per block state, whether its shape has a box with a face of at least half a block (states are shared, the map stays small). */
    private static final java.util.Map<net.minecraft.world.level.block.state.BlockState, Boolean> LARGE = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean hasLargeBox(net.minecraft.world.phys.shapes.VoxelShape sh) {
        for (net.minecraft.world.phys.AABB b : sh.toAabbs()) {
            double sx = b.maxX - b.minX, sy = b.maxY - b.minY, sz = b.maxZ - b.minZ;
            int big = (sx >= 0.5 ? 1 : 0) + (sy >= 0.5 ? 1 : 0) + (sz >= 0.5 ? 1 : 0);
            if (big >= 2) return true;
        }
        return false;
    }

    /** Rev. 16c: the box of {@code sh} the hit lies on spans at least half a block in both directions of its face. */
    static boolean largeFace(net.minecraft.world.phys.shapes.VoxelShape sh, BlockHitResult h, BlockPos pos) {
        Vec3 l = h.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction.Axis n = h.getDirection().getAxis();
        double e = 1e-3;
        for (net.minecraft.world.phys.AABB b : sh.toAabbs()) {
            if (l.x < b.minX - e || l.x > b.maxX + e || l.y < b.minY - e || l.y > b.maxY + e || l.z < b.minZ - e || l.z > b.maxZ + e) continue;
            double sx = b.maxX - b.minX, sy = b.maxY - b.minY, sz = b.maxZ - b.minZ;
            boolean big = switch (n) {
                case X -> sy >= 0.5 && sz >= 0.5;
                case Y -> sx >= 0.5 && sz >= 0.5;
                default -> sx >= 0.5 && sy >= 0.5;
            };
            if (big) return true;
        }
        return false;
    }

    /**
     * Rev. 13: the beam of a gobo as a bundle of shafts. Each shaft is the light through one part of the pattern (GoboShafts): it
     * leaves the lens at that point of the optic, opens with the beam (same angle, scaled to its part), turns with the gobo wheel
     * like the projection on the surfaces, and is as bright as the light that part lets through. {@code blur} 0..1: out of focus,
     * the shafts are wider and softer.
     */
    private static void goboShafts(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f origin, Vector3f ax, Vector3f mainAxis, float len,
                                   float r0, float tanHalf, Vector3f cam, float cr, float cg, float cb, float k, boolean hits,
                                   ResourceLocation gobo, float rotDeg, int q, float blur) {
        java.util.List<GoboShafts.Shaft> list = GoboShafts.of(gobo);
        if (list.isEmpty()) return;
        // same frame as the projected pattern (VolumeLights.project): side = the fixture's side (SIDE), up = axis × side
        Vector3f side = new Vector3f(SIDE);                          // 1.2.0: the fixture's own side (see SIDE)
        side.sub(new Vector3f(ax).mul(side.dot(ax)));
        if (side.lengthSquared() < 1e-6f) side.set(Math.abs(ax.y) < 0.9f ? 0 : 1, Math.abs(ax.y) < 0.9f ? 1 : 0, 0).cross(ax);
        side.normalize();
        Vector3f up = new Vector3f(ax).cross(side).normalize();
        double rot = Math.toRadians(rotDeg);
        float c = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float cell = GoboShafts.CELL_RADIUS * (1f + 0.6f * blur);
        int step = q >= BeamOptics.HIGH ? 1 : 2;                       // medium quality: every other shaft
        // rev. 14: the light of the beam is shared between its shafts: seen from the side about GRID·0.6 of them overlap on a line
        // of sight, so each one carries about that fraction (rev. 13 gave each the full light: the sky burnt to white)
        float per = k * (step == 2 ? 1.6f : 1f) / (GoboShafts.GRID * 0.6f);
        Vector3f o = new Vector3f(), d = new Vector3f();
        for (int i = 0; i < list.size(); i += step) {
            GoboShafts.Shaft sh = list.get(i);
            // the projector turns the pattern by rot (texture = R(rot) world): back to the beam's frame with R(−rot)
            float wx = sh.qx() * c + sh.qy() * sn, wy = -sh.qx() * sn + sh.qy() * c;
            o.set(side).mul(wx * r0).add(up.x * wy * r0, up.y * wy * r0, up.z * wy * r0).add(origin);
            d.set(side).mul(wx * tanHalf).add(up.x * wy * tanHalf, up.y * wy * tanHalf, up.z * wy * tanHalf).add(ax);
            float dl = d.length();
            d.div(dl);
            float kk = per * sh.weight();
            // rev. 14c: 6 segments (4 before): a long shaft into the sky turned towards the camera in too few steps and showed as
            // large pale flat shards
            BeamRenderer.softBeam(vc, mat, nm, o, d, len, r0 * cell, tanHalf * cell / dl, cam, cr * kk, cg * kk, cb * kk, hits,
                BeamOptics.MEDIUM, 1f, false, r0, tanHalf);
        }
    }

    private static float sq(float x) {
        return x * x;
    }

    /** Shimmer lights of a beam: one at the centre of the footprint, and for a wide footprint some more spread over it. */
    private static void shimmerLights(FixtureBlockEntity be, FixtureProfile pr, Engine.Output o, float flux, boolean flash, boolean strobeType,
                                      boolean spotType, float r0, float tanHalf, int q, BlockPos bp) {
        float peak = Math.max(1e-3f, Math.max(o.beamR, Math.max(o.beamG, o.beamB)));
        float lr = o.beamR / peak, lg = o.beamG / peak, lb = o.beamB / peak;
        int count = be.surfaceCount;
        if (count == 0 || be.surfaceHits[6] < 0f) {
            PointLights.dark(be);
            for (Object k : be.spreadLights) PointLights.dark(k);
            return;
        }
        float dist = be.surfaceHits[6];
        float foot = BeamOptics.radius(r0, tanHalf, dist);
        float e = BeamOptics.surface(flux, dist, foot, be.surfaceHits[7]);
        float off = 0.6f + Math.min(1.5f, foot * 0.25f);            // a little in front of the surface, so it lights around the spot too
        float rad = Math.max(2.5f, Math.min(16f, foot * 1.3f + 1.5f)) * (strobeType && flash ? 1.3f : 1f);
        PointLights.put(be, bp.getX() + be.surfaceHits[0] + be.surfaceHits[3] * off, bp.getY() + be.surfaceHits[1] + be.surfaceHits[4] * off,
            bp.getZ() + be.surfaceHits[2] + be.surfaceHits[5] * off, lr, lg, lb, Math.min(1f, e * (spotType ? 0.45f : 0.55f)), rad, false);
        int extra = foot > 2.5f && count > 1 ? Math.min(BeamOptics.shimmerExtra(q), count - 1) : 0;
        for (int k = 0; k < be.spreadLights.length; k++) {
            if (k >= extra) { PointLights.dark(be.spreadLights[k]); continue; }
            int i = 1 + k * (count - 1) / extra;                     // spread evenly over the ring
            int b = i * FixtureBlockEntity.HIT_STRIDE;
            float d = be.surfaceHits[b + 6];
            if (d < 0f) { PointLights.dark(be.spreadLights[k]); continue; }
            float f2 = BeamOptics.radius(r0, tanHalf, d);
            float e2 = BeamOptics.surface(flux, d, f2, be.surfaceHits[b + 7]) * 0.7f;
            PointLights.put(be.spreadLights[k], bp.getX() + be.surfaceHits[b] + be.surfaceHits[b + 3] * off,
                bp.getY() + be.surfaceHits[b + 1] + be.surfaceHits[b + 4] * off, bp.getZ() + be.surfaceHits[b + 2] + be.surfaceHits[b + 5] * off,
                lr, lg, lb, Math.min(1f, e2 * 0.5f), Math.max(2.5f, Math.min(12f, f2 * 0.8f + 1.5f)), true);
        }
    }

    /**
     * Rev. 21: the light of a fixture without a projection this frame, on the surfaces its rays met (the centre and up to three of the
     * ring): one spill light per hit, as wide as the footprint there, kept on the outer side of the surface it lit.
     */
    private static void footprintSpill(FixtureBlockEntity be, Matrix4f mat, float flux, float r0, float tanHalf, float nr, float ng, float nb, boolean spot) {
        int count = be.surfaceCount;
        if (count == 0 || flux < 0.01f) return;
        int step = Math.max(1, (count - 1) / 3);
        for (int i = 0; i < count; i += (i == 0 ? 1 : step)) {
            int b = i * FixtureBlockEntity.HIT_STRIDE;
            float d = be.surfaceHits[b + 6];
            if (d < 0f) continue;
            float foot = BeamOptics.radius(r0, tanHalf, d);
            float e = BeamOptics.surface(flux, d, foot, be.surfaceHits[b + 7]) * (i == 0 ? 0.8f : 0.45f) * (spot ? 0.8f : 1f);
            Vector3f n = new Vector3f(be.surfaceHits[b + 3], be.surfaceHits[b + 4], be.surfaceHits[b + 5]);
            Vector3f hit = new Vector3f(be.surfaceHits[b], be.surfaceHits[b + 1], be.surfaceHits[b + 2]);
            Vector3f c = mat.transformPosition(new Vector3f(n).mul(0.3f).add(hit));
            Vector3f nP = mat.transformDirection(new Vector3f(n)).normalize(), pP = mat.transformPosition(new Vector3f(hit));
            // and never below the level the ray met (seen in game at medium quality: the 30 fixtures over the projection budget lit the
            // scaffolding under the stage front through the deck): world up through the hit, a little under it
            Vector3f up = mat.transformDirection(new Vector3f(0f, 1f, 0f)).normalize();
            Vector3f low = mat.transformPosition(new Vector3f(hit).add(0f, -0.35f, 0f));
            VolumeLights.spill(c, new Vector3f(c), nr, ng, nb, e, Math.max(2f, Math.min(10f, foot * 1.1f + 1f)), Math.max(0.8f, foot * 0.7f), 0.2f, nP, pP, -0.3f,
                n.y > -0.5f ? up : null, low);
            if (i == 0 && count == 1) break;
        }
    }

    /** Three axes around the beam, 5 degrees apart, turning with the prism. */
    private static Vector3f[] prismAxes(Vector3f a, float angleDeg, int facets) {
        Vector3f any = Math.abs(a.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(a).cross(any).normalize();
        Vector3f v = new Vector3f(a).cross(u).normalize();
        Vector3f[] out = new Vector3f[facets];
        float t = (float) Math.tan(Math.toRadians(5));
        for (int k = 0; k < facets; k++) {
            double ang = Math.toRadians(angleDeg + k * 360f / facets);
            out[k] = new Vector3f(a).add(new Vector3f(u).mul((float) Math.cos(ang) * t)).add(new Vector3f(v).mul((float) Math.sin(ang) * t)).normalize();
        }
        return out;
    }

    /** The lit spot (gobo projection) on the face the beam hits, turned with the gobo wheel. */
    /**
     * The lit spot / gobo on the face the beam hits, turned with the gobo wheel (rev. 6: stretched along the slope of the beam by
     * 1 / cos(incidence), as a real projection on a surface it does not face squarely).
     */
    private static void spot(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f c, Vector3f n, Vector3f beamAxis, float cosI, float rad,
                             float rotDeg, float r, float g, float b) {
        // t: direction of the beam's slope on the surface; s: across it
        Vector3f t = beamAxis == null ? new Vector3f() : new Vector3f(n).mul(-beamAxis.dot(n)).add(beamAxis);
        if (t.lengthSquared() < 1e-6f) t.set(Math.abs(n.y) < 0.9f ? 0 : 1, Math.abs(n.y) < 0.9f ? 1 : 0, 0).cross(n);
        t.normalize();
        Vector3f sv = new Vector3f(n).cross(t).normalize();
        float stretch = 1f / Math.max(0.3f, Math.min(1f, cosI));
        double a = Math.toRadians(rotDeg);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        Vector3f uu = new Vector3f(t).mul(ca * rad * stretch).add(new Vector3f(sv).mul(sa * rad));
        Vector3f vv = new Vector3f(sv).mul(ca * rad).sub(new Vector3f(t).mul(sa * rad * stretch));
        // once: the render type does not cull, the face is seen from either side
        vertex(vc, mat, nm, c.x - uu.x - vv.x, c.y - uu.y - vv.y, c.z - uu.z - vv.z, 0, 0, r, g, b);
        vertex(vc, mat, nm, c.x + uu.x - vv.x, c.y + uu.y - vv.y, c.z + uu.z - vv.z, 1, 0, r, g, b);
        vertex(vc, mat, nm, c.x + uu.x + vv.x, c.y + uu.y + vv.y, c.z + uu.z + vv.z, 1, 1, r, g, b);
        vertex(vc, mat, nm, c.x - uu.x + vv.x, c.y - uu.y + vv.y, c.z - uu.z + vv.z, 0, 1, r, g, b);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Matrix3f nm, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float r2, float g2, float b2) {
        // once: the light render type does not cull
        vertex(vc, mat, nm, ax, ay, az, 0, 1, r, g, b);
        vertex(vc, mat, nm, bx, by, bz, 1, 1, r2, g2, b2);
        vertex(vc, mat, nm, cx, cy, cz, 1, 0, r2, g2, b2);
        vertex(vc, mat, nm, dx, dy, dz, 0, 0, r, g, b);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f nm, float x, float y, float z, float u, float v, float r, float g, float b) {
        int ri = (int) Math.min(255, Math.max(0, r * 255)), gi = (int) Math.min(255, Math.max(0, g * 255)), bi = (int) Math.min(255, Math.max(0, b * 255));
        vc.vertex(mat, x, y, z).color(ri, gi, bi, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
            .normal(nm, 0, 1, 0).endVertex();
    }

    /**
     * CSC 1.3.0 rev. 1 — a fixture in a LiveVision camera image (CameraFeeds): its body (the same GPU meshes as the main view, at the lowest
     * level of detail that suits the distance), the glow of its lens, its beam in the haze as a few soft ribbons, the laser lines, and
     * a soft spot where the beam lands. Nothing of the expensive passes of the main view (haze volumes, shadow maps, projections, bloom):
     * a camera image costs the bodies plus a few quads per lit fixture, and it reads the lengths of the beams the main view measured
     * (one ray of its own only for a beam never measured). The heads are shown where the main view turned them; a fixture the main
     * view has not drawn for a while is shown at its target.
     */
    public static void renderFeed(FixtureBlockEntity be, float partial, PoseStack pose, Vec3 cam, int light, boolean beams) {
        Level level = be.getLevel();
        if (level == null || !(be.getBlockState().getBlock() instanceof FixtureBlock block)) return;
        FixtureProfile pr = block.profile();
        FixtureMeshes.Model m = FixtureMeshes.get(pr.id);
        if (m == null) return;
        MultiBufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        long t0 = System.nanoTime();
        Engine.Output o = LightClient.feedOutput(level, be, partial);
        long t1 = System.nanoTime();
        FEED_NANOS[0] += t1 - t0;
        boolean hung = be.getBlockState().getValue(FixtureBlock.HUNG);
        float yaw = 180f - be.getBlockState().getValue(FixtureBlock.FACING).toYRot();
        float pan = be.curPan, tilt = be.curTilt;
        if (Float.isNaN(pan) || Float.isNaN(tilt) || System.nanoTime() - be.lastFrameNanos > 250_000_000L) {
            pan = (o == null ? 0f : pr.panDegrees(o.get(Attr.PAN))) + be.manualPan;
            tilt = (o == null ? pr.tiltZero : pr.tiltDegrees(o.get(Attr.TILT))) + be.manualTilt;
            if (pr.panRange > 0) pan = Math.max(-pr.panRange / 2, Math.min(pr.panRange / 2, pan));
            if (pr.tiltRange > 0) tilt = Math.max(pr.tiltMin(), Math.min(pr.tiltMax(), tilt));
        }
        BlockPos bp = be.getBlockPos();
        double dist = Math.sqrt(cam.distanceToSqr(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5));
        boolean gpu = com.concertstage.client.GpuMesh.enabled();
        int lod = FarView.lod(dist, pr.renderScale, 0, gpu);
        // 1.2.1: a fixture moved or tilted off its standard place (free pose) is drawn without the truss hardware
        MountGeometry.Layout mount = hung && !be.posed() ? FixtureMount.of(be, level, pr.renderScale) : null;
        float top = mount != null ? mount.top() : 1f, mx = mount != null ? mount.ox() : 0f, mz = mount != null ? mount.oz() : 0f;
        if (mount != null && mount.kind() != MountGeometry.NONE && dist < (gpu ? FarView.MOUNT_GPU : FarView.MOUNT_DIRECT))
            FixtureMeshes.drawMount(m, mount, pose, buffers, light);
        long t2 = System.nanoTime();
        FEED_NANOS[1] += t2 - t1;
        PoseStack local = FEED_LOCAL;                                           // 1.2.1: no allocation per fixture
        while (!local.clear()) local.popPose();
        local.setIdentity();
        FEED_PAIR[0] = pose; FEED_PAIR[1] = local;
        pose.pushPose();
        for (PoseStack p : FEED_PAIR) {
            basePose(p, be, hung, top, mx, mz, yaw, pr.renderScale);
        }
        FixtureMeshes.draw(m, "base", lod, pose, buffers, light);
        if (m.hasYoke() && m.panPivot != null) {
            for (PoseStack p : FEED_PAIR) {
                p.translate(m.panPivot[0], m.panPivot[1], m.panPivot[2]);
                p.mulPose(Axis.YP.rotationDegrees(pan));
            }
            FixtureMeshes.draw(m, "yoke", lod, pose, buffers, light);
        }
        if (m.tiltPivot != null) {
            for (PoseStack p : FEED_PAIR) {
                p.translate(m.tiltPivot[0], m.tiltPivot[1], m.tiltPivot[2]);
                p.mulPose(Axis.XP.rotationDegrees(tilt));
            }
        }
        FixtureMeshes.draw(m, "head", lod, pose, buffers, light);
        pose.popPose();
        long t3 = System.nanoTime();
        FEED_NANOS[2] += t3 - t2;
        try {
            feedLight(be, level, pr, m, o, pose, cam, local, partial, beams, buffers);
        } finally {
            FEED_NANOS[3] += System.nanoTime() - t3;
        }
    }

    /** Test figures of renderFeed (ns): the output, the hanging hardware, the body, the light. */
    public static final long[] FEED_NANOS = new long[4];

    private static void feedLight(FixtureBlockEntity be, Level level, FixtureProfile pr, FixtureMeshes.Model m, Engine.Output o, PoseStack pose,
                                  Vec3 cam, PoseStack local, float partial, boolean beams, MultiBufferSource buffers) {
        BlockPos bp = be.getBlockPos();
        if (!beams || o == null || !LightClient.beams) return;
        if (!be.mainsPowered && !LampModel.tungsten(pr)) { o = o.copy(); o.v[Attr.DIMMER.ordinal()] = 0f; o.strobeHz = 0f; }
        if (LampModel.tungsten(pr)) o = lamp(be, pr, o, true, !be.mainsPowered);   // 1.2.2: the camera sees the lamp, not the desk
        double gameTime = level.getGameTime() + partial;
        float intensity = BeamOptics.strobeIntensity(o.get(Attr.DIMMER), o.strobeHz, gameTime / 20.0);
        boolean flash = o.strobeHz > 0.05f && intensity > 0f;
        if (intensity <= 0.01f) return;
        Matrix4f lm = local.last().pose();
        Vector3f origin = lm.transformPosition(new Vector3f(m.beamOrigin[0], m.beamOrigin[1], m.beamOrigin[2]));
        Vector3f axis = lm.transformDirection(new Vector3f(m.beamAxis[0], m.beamAxis[1], m.beamAxis[2])).normalize();
        Vector3f lens = lm.transformPosition(new Vector3f(m.beamOrigin[0], m.beamOrigin[1], m.lensFrontZ + 0.01f));
        Vector3f camL = new Vector3f((float) (cam.x - bp.getX()), (float) (cam.y - bp.getY()), (float) (cam.z - bp.getZ()));
        Matrix4f mat = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float peak = Math.max(1e-3f, Math.max(o.beamR, Math.max(o.beamG, o.beamB)));
        float nr = o.beamR / peak, ng = o.beamG / peak, nb = o.beamB / peak;
        float r0 = m.lensRadius * 0.9f * pr.renderScale;
        // the lens: a glow facing the camera, stronger when the fixture looks at the camera
        Vector3f toCam = new Vector3f(camL).sub(lens);
        float facing = Math.max(0f, toCam.normalize().dot(axis));
        float g = Math.min(1.2f, intensity * (0.35f + 0.9f * facing * facing) * LightClient.lensGlow);
        LaserBeams.sprite(LightBatch.buffer(buffers, LightRenderTypes.of(GLOW), LightBatch.PASS_EMIT), mat, nm, lens, camL,
            Math.max(0.06f, r0 * (1.4f + 1.2f * facing)), 0.02f, nr * g, ng * g, nb * g);
        boolean spotType = pr.isSpot(), strobeType = pr.isPanel();
        if (strobeType && !flash) return;
        float air = intensity * pr.output * LightClient.haze * BeamOptics.AIR_DENSITY * LightClient.beamBrightness * airBalance(pr);
        float angle = pr.beamAngle(o.get(Attr.ZOOM));
        if (pr.has(Attr.FROST)) angle += o.get(Attr.FROST) * 22f;
        if (pr == FixtureProfile.LASERCORE_L1) angle = 30f;
        if (pr.has(Attr.IRIS)) angle *= 0.25f + 0.75f * o.get(Attr.IRIS);
        float tanHalf = (float) Math.tan(Math.toRadians(Math.max(0.6f, angle) / 2));
        if (pr == FixtureProfile.LASERCORE_L1 && !legacyLaser) {
            if (air > 0.001f) LaserBeams.draw(LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM),
                LightBatch.buffer(buffers, LightRenderTypes.of(GLOW), LightBatch.PASS_EMIT), mat, nm, be, level, o, gameTime / 20.0, origin, axis,
                r0, tanHalf, throwOf(pr), camL, nr, ng, nb, LASER_AIR * air, false, level.getGameTime());
            return;
        }
        if (be.beamLength < 0) farCentre(be, level, origin, axis, throwOf(pr), bp);
        float len = be.beamLength > 0 ? be.beamLength : throwOf(pr);
        boolean hits = be.beamHitFace != null && be.beamLength < throwOf(pr) - 0.01f;
        if (strobeType) len = Math.min(len, 9f);
        if (air > 0.001f) {
            float k = spotType ? VORTEX_AIR * 0.5f * air
                : strobeType ? 0.45f * air
                : (pr == FixtureProfile.LUMORA_W19 ? 0.75f : 0.65f) * WASH_AIR * BeamOptics.washAirScale(tanHalf) * air;
            float rb = spotType ? Math.max(r0, 0.09f) : r0;
            BeamRenderer.softBeam(LightBatch.buffer(buffers, LightRenderTypes.screen(SOFT), LightBatch.PASS_BEAM), mat, nm, origin, axis, len, rb,
                tanHalf, camL, nr * peak * k, ng * peak * k, nb * peak * k, hits, BeamOptics.MEDIUM, 1f, spotType);
        }
        // where it lands: a soft spot of the colour of the light (the main view projects it with shadows; here one quad)
        if (hits && be.beamHitFace != null && LightDiagnostics.surfaces()) {
            var nf = be.beamHitFace.getNormal();
            Vector3f n = new Vector3f(nf.getX(), nf.getY(), nf.getZ());
            float cosI = Math.abs(axis.dot(n));
            Vector3f c = new Vector3f(axis).mul(len - 0.3f).add(origin).add(n.x * 0.03f, n.y * 0.03f, n.z * 0.03f);
            float foot = BeamOptics.radius(r0, tanHalf, len);
            float e = Math.min(1.0f, 0.45f * intensity * pr.output * STAGE_GAIN * LightClient.stageBrightness * stageBalance(pr)
                / Math.max(1f, foot * 0.35f));
            if (e > 0.01f) spot(LightBatch.buffer(buffers, LightRenderTypes.modulate(spotType ? SPOT_OPEN : SOFT), LightBatch.PASS_SURFACE), mat, nm,
                c, n, axis, Math.max(0.3f, cosI), foot, 0f, nr * e, ng * e, nb * e);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FixtureBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        // rev. 18: as far as the world (was cut to 75 / 50 % at medium / low quality): far away the body is its lowest level, one
        // draw call in the graphics card, and the beams follow their own budgets
        return com.concertstage.client.RenderQuality.worldDistance();
    }
}
