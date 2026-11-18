package com.concertstage.client.light;

import com.concertstage.client.GpuMesh;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * CSC's own coloured point lights (1.2.0 rev. 9), for players without Shimmer: the lights the fixtures put around them (lens, rig
 * above, centre and spread of the beam footprint) light every surface within their radius — the front and the sides of the truss,
 * tarps, roof, platforms, instruments, walls — the way Shimmer does on the reference pictures.
 *
 * <p>How: after the opaque scene is drawn (block entities included), the depth buffer is copied into a texture; then each light is
 * one screen quad whose fragment shader ({@code assets/minecraft/shaders/core/concertstage_light_volume.*}) rebuilds the position of
 * every pixel from that depth, measures its distance to the light and the angle of its surface, and multiplies the colour already
 * there by {@code 1 + light} (several passes for strong lights). No change to the game's lighting engine, no chunk rebuild, works
 * the same on Forge and Fabric. The shader is loaded on first use: if it does not compile (driver) or a shader pack is active, the
 * feature turns itself off and the projected light of the fixtures does the work alone.
 */
public final class VolumeLights {
    private static final int MAX = 384, PASSES = 3;
    /** Rev. 10: projected light of the fixtures (gobo / spot / wash on everything in the cone), per quality level. */
    // rev. 16d: budgets of the whole frame (the global quality, not the distance of each fixture: which fixtures got a place depended
    // on the order the game drew them in, so beams vanished or came back as the camera turned) and larger (9 November: 27 / 40 asked)
    // rev. 22: low quality projects its light too (24 places), with shadows: before, low had none — the stage was not lit where
    // the beams landed and the light of the fallback went under the deck
    // rev. 23: 48 / 64 at low / medium (24 / 32): a stage of 62 fixtures lost the light of the LUMORA on the floor as soon as the
    // VORTEX were switched on (author's video, 11 November, 22 h 18)
    private static final int[] MAX_PROJECTORS = {48, 64, 64, 96};
    private static final int PSTRIDE = 20;
    private static final float[] PDATA = new float[96 * PSTRIDE];
    private static final net.minecraft.resources.ResourceLocation[] PTEX = new net.minecraft.resources.ResourceLocation[96];
    /** Rev. 16: uniforms of the common optical mask (OpticalProfile#uniforms) of each projector / beam in the haze. */
    private static final int OPT = com.concertstage.light.core.OpticalProfile.UNIFORM_FLOATS;
    private static final float[] POPT = new float[96 * OPT], VOPT = new float[64 * OPT];
    private static final int TINT = com.concertstage.light.core.OpticalProfile.TINT_FLOATS;
    private static final float[] PTINT = new float[96 * TINT], VTINT = new float[64 * TINT];
    private static int pcount;
    private static ShaderInstance projector, projectorAdd, shaderAdd;
    /** Rev. 21: spill lights (light of a beam in the haze on what is beside it, light sent back up by a lit floor), pose space. */
    private static final int SSTRIDE = 23, MAX_SPILL = 128;
    private static final float[] SDATA = new float[MAX_SPILL * SSTRIDE];
    private static int scount;
    private static ShaderInstance spillShader;
    private static boolean spillFailed;
    static int spillLastFrame;
    /** Rev. 11: strength of the additive pass (light that shows on dark materials) for projected light and for point lights. */
    private static final float PROJ_ADD = 0.06f, POINT_ADD = 0.05f;      // rev. 14: 0.12 / 0.06 before (grey veil)
    private static final float[] DATA = new float[MAX * 8];     // pose-space centre x y z, r g b, strength, radius
    private static int count;
    private static ShaderInstance shader;
    private static boolean failed;
    private static TextureTarget depthCopy;
    static int drawnLastFrame;

    private VolumeLights() {}

    // rev. 3b: the cameras' own scratch targets and pending lights (LightTargets)
    private static TextureTarget fDepthCopy, fLightAcc, fSceneCopy, fHazeHalf;
    private static int fCount, fPcount, fScount, fVcount, fVquality, fFrameLights, fFrameProjected, fFrameRequested, fFrameVolumes, fFrameVolumesAsked;

    static void swapFeed() {
        TextureTarget t;
        t = depthCopy; depthCopy = fDepthCopy; fDepthCopy = t;
        t = lightAcc; lightAcc = fLightAcc; fLightAcc = t;
        t = sceneCopy; sceneCopy = fSceneCopy; fSceneCopy = t;
        t = hazeHalf; hazeHalf = fHazeHalf; fHazeHalf = t;
        int i;
        i = count; count = fCount; fCount = i;
        i = pcount; pcount = fPcount; fPcount = i;
        i = scount; scount = fScount; fScount = i;
        i = vcount; vcount = fVcount; fVcount = i;
        i = vquality; vquality = fVquality; fVquality = i;
        // the main view's counts (F3, report) stay its own
        i = frameLights; frameLights = fFrameLights; fFrameLights = i;
        i = frameProjected; frameProjected = fFrameProjected; fFrameProjected = i;
        i = frameRequested; frameRequested = fFrameRequested; fFrameRequested = i;
        i = frameVolumes; frameVolumes = fFrameVolumes; fFrameVolumes = i;
        i = frameVolumesAsked; frameVolumesAsked = fFrameVolumesAsked; fFrameVolumesAsked = i;
    }

    /** Rev. 19: places for projections this frame (quality level and setting), for FixtureRenderer.Perf.PROJECTIONS. */
    static int projectorCapacity() {
        return enabled() ? Math.min(MAX_PROJECTORS[FixtureRenderer.Perf.quality()], LightClient.budget(LightClient.maxProjections, 96)) : 0;
    }

    /** Rev. 19: places for beams in the haze this frame. */
    static int volumeCapacity() {
        return enabled() && !volumeFailed ? Math.min(MAX_VOLUMES[FixtureRenderer.Perf.quality()], LightClient.budget(LightClient.maxVolumes, 64)) : 0;
    }

    /** Usable this frame (setting on, no shader pack, the shader compiled or not tried yet). */
    static boolean enabled() {
        return LightClient.volumeLights && !failed && GpuMesh.enabled();
    }

    public static void clear() {
        count = 0;
        pcount = 0;
        scount = 0;
        // rev. 12: statistics of the frame that just ended (flush runs several times per frame: the totals are added up)
        lastLights = frameLights; lastProjected = frameProjected; lastRequested = frameRequested;
        frameLights = frameProjected = frameRequested = 0;
        lastVolumes = frameVolumes; lastVolumesAsked = frameVolumesAsked;      // rev. 15
        frameVolumes = frameVolumesAsked = 0;
        vcount = 0;
        BeamShadows.beginFrame();
    }

    private static int frameLights, frameProjected, frameRequested, lastLights, lastProjected, lastRequested;

    /**
     * Rev. 10: a fixture projecting its light on everything in its cone (pose space: {@code o} lens, {@code a} axis, {@code side}
     * first axis of the pattern). Returns false when it will not be drawn (feature off, budget of the quality level used up): the
     * caller then lights the surfaces its old way.
     */
    static boolean project(Vector3f o, Vector3f a, Vector3f side, float tanHalf, float rotDeg, float range, float r0, float r, float g, float b,
                           float flux, net.minecraft.resources.ResourceLocation pattern, float focusSpread, int quality, int shadow, Optics optics) {
        if (!enabled() || pcount >= MAX_PROJECTORS[FixtureRenderer.Perf.quality()] || flux < 0.01f) return false;
        if (pcount >= LightClient.budget(LightClient.maxProjections, 96)) return false;
        optics.write(POPT, pcount * OPT);
        System.arraycopy(optics.tint, 0, PTINT, pcount * TINT, TINT);
        int i = pcount * PSTRIDE;
        PDATA[i] = o.x; PDATA[i + 1] = o.y; PDATA[i + 2] = o.z;
        PDATA[i + 3] = a.x; PDATA[i + 4] = a.y; PDATA[i + 5] = a.z;
        PDATA[i + 6] = side.x; PDATA[i + 7] = side.y; PDATA[i + 8] = side.z;
        PDATA[i + 9] = tanHalf; PDATA[i + 10] = (float) Math.toRadians(rotDeg); PDATA[i + 11] = range; PDATA[i + 12] = r0;
        PDATA[i + 13] = r; PDATA[i + 14] = g; PDATA[i + 15] = b; PDATA[i + 16] = flux; PDATA[i + 17] = focusSpread;
        PDATA[i + 18] = shadow;                                  // rev. 15: shadow map of BeamShadows, -1 none
        PTEX[pcount++] = pattern;
        return true;
    }

    /**
     * Rev. 21: a spill light (pose space, as {@link #project}): the segment {@code a -> b} (a point when they are equal), colour
     * (brightest channel 1), {@code strength} on the scale of the projected light, {@code radius} where it ends, {@code core} the
     * distance over which it halves, {@code wrap} how much it lights the faces turned away (0..1). {@code plane}: normal (pose space)
     * and a point of the surface the beam meets — the light stays on its side (null: no plane).
     */
    static void spill(Vector3f a, Vector3f b, float r, float g, float bl, float strength, float radius, float core, float wrap,
                      Vector3f planeN, Vector3f planeP, float above) {
        spill(a, b, r, g, bl, strength, radius, core, wrap, planeN, planeP, above, null, null);
    }

    /** Same, with a second plane the light stays above (normal, point; null: none). */
    static void spill(Vector3f a, Vector3f b, float r, float g, float bl, float strength, float radius, float core, float wrap,
                      Vector3f planeN, Vector3f planeP, float above, Vector3f floorN, Vector3f floorP) {
        if (!enabled() || spillFailed || strength < 0.003f || radius < 0.5f) return;
        // rev. 21 (perf, seen in game: 62 fixtures, the frame time doubled): the light sent back by the same lit floor comes from many
        // fixtures at a few blocks from each other — one light for them (position and colour weighted by strength, strengths added)
        if (a.distanceSquared(b) < 1e-6f && planeN != null) {
            for (int k = 0; k < scount; k++) {
                int j = k * SSTRIDE;
                if (SDATA[j + 17] < 0.5f || Math.abs(SDATA[j] - SDATA[j + 3]) + Math.abs(SDATA[j + 1] - SDATA[j + 4]) + Math.abs(SDATA[j + 2] - SDATA[j + 5]) > 1e-4f) continue;
                if (planeN.x * SDATA[j + 13] + planeN.y * SDATA[j + 14] + planeN.z * SDATA[j + 15] < 0.95f) continue;
                if (Math.abs(SDATA[j + 16] + planeN.dot(planeP)) > 0.3f) continue;             // the same surface, not a deck above a floor
                if (Math.abs(SDATA[j + 18] - above) > 1e-3f) continue;                          // the same kind of light
                float dx = SDATA[j] - a.x, dy = SDATA[j + 1] - a.y, dz = SDATA[j + 2] - a.z;
                if (dx * dx + dy * dy + dz * dz > 16f) continue;
                float s0 = SDATA[j + 9], t = strength / (s0 + strength);
                for (int c = 0; c < 3; c++) { SDATA[j + c] += (a.get(c) - SDATA[j + c]) * t; SDATA[j + 3 + c] = SDATA[j + c]; }
                SDATA[j + 6] += (r - SDATA[j + 6]) * t; SDATA[j + 7] += (g - SDATA[j + 7]) * t; SDATA[j + 8] += (bl - SDATA[j + 8]) * t;
                SDATA[j + 9] = s0 + strength;
                SDATA[j + 10] = Math.max(SDATA[j + 10], Math.min(24f, radius)); SDATA[j + 11] = Math.max(SDATA[j + 11], core);
                return;
            }
        }
        if (scount >= MAX_SPILL) return;
        int i = scount++ * SSTRIDE;
        SDATA[i] = a.x; SDATA[i + 1] = a.y; SDATA[i + 2] = a.z; SDATA[i + 3] = b.x; SDATA[i + 4] = b.y; SDATA[i + 5] = b.z;
        SDATA[i + 6] = r; SDATA[i + 7] = g; SDATA[i + 8] = bl; SDATA[i + 9] = strength;
        SDATA[i + 10] = Math.min(24f, radius); SDATA[i + 11] = Math.max(0.2f, core); SDATA[i + 12] = wrap;
        if (planeN != null) {
            SDATA[i + 13] = planeN.x; SDATA[i + 14] = planeN.y; SDATA[i + 15] = planeN.z;
            SDATA[i + 16] = -(planeN.x * planeP.x + planeN.y * planeP.y + planeN.z * planeP.z); SDATA[i + 17] = 1f;
        } else SDATA[i + 17] = 0f;
        SDATA[i + 18] = above;
        if (floorN != null) {
            SDATA[i + 19] = floorN.x; SDATA[i + 20] = floorN.y; SDATA[i + 21] = floorN.z;
            SDATA[i + 22] = -(floorN.x * floorP.x + floorN.y * floorP.y + floorN.z * floorP.z);
        } else { SDATA[i + 19] = 0f; SDATA[i + 20] = 0f; SDATA[i + 21] = 0f; SDATA[i + 22] = 0f; }
    }

    /** A light at pose-space {@code c} (block entity pose applied), colour 0..1 (brightest channel 1), strength 0..1, radius in blocks. */
    static void add(Vector3f c, float r, float g, float b, float strength, float radius) {
        if (count >= MAX || strength < 0.02f || radius < 0.5f) return;
        int i = count++ * 8;
        DATA[i] = c.x; DATA[i + 1] = c.y; DATA[i + 2] = c.z;
        DATA[i + 3] = r; DATA[i + 4] = g; DATA[i + 5] = b; DATA[i + 6] = Math.min(1f, strength); DATA[i + 7] = Math.min(32f, radius);
    }

    /** Draws the lights of this frame (render thread, after the opaque meshes, before the beams). */
    public static void flush() {
        // rev. 12: what happened this frame, for the F3 line (read later, outside the world rendering)
        if (!LightClient.volumeLights) status = "off (desk setting)";
        else if (failed) status = "turned off after a shader error (see the log)";
        else if (!GpuMesh.enabled()) status = "off (shader pack or GPU meshes off)";
        else status = null;
        frameRequested += pcount;
        // rev. 15: the shadow maps of the beams (drawn in their own small targets, before anything reads them)
        if (enabled()) {
            GpuTimers.begin(GpuTimers.SHADOWS);
            try {
                BeamShadows.render();
            } finally {
                LightTargets.main().bindWrite(true);
                GpuTimers.end(GpuTimers.SHADOWS);
            }
        }
        if (enabled() && LightDiagnostics.bufferView() && !debugFailed) {
            try {
                drawBufferView();
            } catch (Throwable t) {
                debugFailed = true;
                com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: diagnostic view turned off (shader or driver error)", t);
                try { LightTargets.main().bindWrite(false); } catch (Throwable ignored) { }
            }
        }
        if (count == 0 && pcount == 0 && scount == 0) return;
        drawnLastFrame = 0;
        projectedLastFrame = 0;
        if (!enabled()) { count = 0; pcount = 0; scount = 0; return; }
        try {
            if (shader == null) shader = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_light_volume",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            if (projector == null) projector = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_light_projector",
                DefaultVertexFormat.POSITION);
            if (projectorAdd == null) projectorAdd = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_light_projector_add",
                DefaultVertexFormat.POSITION);
            if (shaderAdd == null) shaderAdd = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_light_volume_add",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            GpuTimers.begin(GpuTimers.STAGE);
            try { draw(); } finally { GpuTimers.end(GpuTimers.STAGE); }
        } catch (Throwable t) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: coloured stage lights turned off (shader or driver error)", t);
            try { LightTargets.main().bindWrite(false); } catch (Throwable ignored) { }
        } finally {
            count = 0;
            pcount = 0;
            scount = 0;
        }
    }

    private static void draw() throws java.io.IOException {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = LightTargets.main();
        if (depthCopy == null) depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        else if (depthCopy.width != main.width || depthCopy.height != main.height) depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);

        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f mvInv = new Matrix4f(mv).invert();
        Vector3f camPose = mvInv.transformPosition(new Vector3f());
        Vector3f right = mvInv.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f up = mvInv.transformDirection(new Vector3f(0, 1, 0)).normalize();
        Vector3f fwd = mvInv.transformDirection(new Vector3f(0, 0, -1)).normalize();

        // brighter at night: the same light shows much more on a dark stage than in daylight (as BeamOptics.relight)
        float dark = 1f;
        if (mc.level != null) dark = Math.min(1f, Math.max(0f, mc.level.getSkyDarken() / 11f));
        float gain = 0.6f + 4.4f * dark * dark;

        float[] ambient = ambientTint();
        for (ShaderInstance sh : new ShaderInstance[] {shader, shaderAdd}) {
            tint(sh, ambient);
            sh.setSampler("DepthSampler", depthCopy.getDepthTextureId());
            sh.safeGetUniform("InvProjMat").set(new Matrix4f(proj).invert());
            sh.safeGetUniform("TargetSize").set((float) main.width, (float) main.height);
            sh.safeGetUniform("Gain").set(gain);
            sh.safeGetUniform("PassRoot").set(1f / PASSES);
            sh.safeGetUniform("AddK").set(POINT_ADD);
        }

        RenderSystem.setShader(() -> shader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        forceBlend(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE);
        Vector3f c = new Vector3f(), p = new Vector3f();
        // which lights can be seen at all (behind the camera and out of reach: skipped)
        boolean[] show = new boolean[count];
        int visible = 0;
        for (int k = 0; k < count; k++) {
            int i = k * 8;
            c.set(DATA[i], DATA[i + 1], DATA[i + 2]);
            float rad = DATA[i + 7];
            float d = c.distance(camPose);
            show[k] = !(d > rad && new Vector3f(c).sub(camPose).dot(fwd) < -rad)
                && Math.abs(c.z) * 100f < 32000f && Math.abs(c.x) < 300f && Math.abs(c.y) < 300f;
            if (show[k]) visible++;
        }
        // rev. 20: the point lights go into the same accumulation as the projected light and are applied once, tone mapped. Seen in
        // game on a real stage (CraftCity, 62 fixtures): each light multiplied the screen in its own passes, so the lights of a row
        // of fixtures under one truss multiplied each other and the truss came out as a white neon tube.
        final boolean[] shown = show;
        final int nVisible = visible;
        boolean accumulated = drawProjectors(mv, proj, camPose, right, up, fwd, gain,
            nVisible > 0 && !pointAccumFailed ? () -> pointQuads(shown, camPose, right, up, fwd) : null);
        if (accumulated || visible == 0) {
            drawnLastFrame = visible;
            frameLights += visible;
            restore();
            return;
        }
        RenderSystem.setShader(() -> shader);
        forceBlend(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        if (visible == 0) { restore(); return; }
        for (int pass = 0; pass <= PASSES; pass++) {
            boolean add = pass == PASSES;                     // rev. 11: last pass adds a little light (dark materials)
            RenderSystem.setShader(add ? () -> shaderAdd : () -> shader);
            forceBlend(add ? GlStateManager.SourceFactor.ONE : GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            for (int k = 0; k < count; k++) {
                if (!show[k]) continue;
                int i = k * 8;
                c.set(DATA[i], DATA[i + 1], DATA[i + 2]);
                float rad = DATA[i + 7];
                float d = c.distance(camPose);
                int cr = Math.round(DATA[i + 3] * 255), cg = Math.round(DATA[i + 4] * 255), cb = Math.round(DATA[i + 5] * 255);
                int ca = Math.round(DATA[i + 6] * 255);
                int iz = Math.round(c.z * 100f), ir = Math.round(rad * 100f);
                // a quad covering the light on screen: around its centre, facing the camera; when the camera is in or near the
                // light, a large quad just in front of the camera (the shader only lights what is within the radius anyway)
                Vector3f q = d > rad * 1.25f + 0.5f ? new Vector3f(c) : new Vector3f(fwd).mul(0.3f).add(camPose);
                // half-size at the centre's depth that covers the whole sphere as seen from the camera: d·r / sqrt(d² − r²)
                float s = d > rad * 1.25f + 0.5f ? 1.05f * d * rad / (float) Math.sqrt(d * d - rad * rad) : 2f;
                for (int corner = 0; corner < 4; corner++) {
                    float sx = corner == 0 || corner == 3 ? -s : s, sy = corner < 2 ? -s : s;
                    p.set(q).add(right.x * sx + up.x * sy, right.y * sx + up.y * sy, right.z * sx + up.z * sy);
                    bb.vertex(p.x, p.y, p.z).color(cr, cg, cb, ca).uv(c.x, c.y).uv2(iz, ir).endVertex();
                }
            }
            BufferUploader.drawWithShader(bb.end());
        }
        drawnLastFrame = visible;
        frameLights += visible;
        restore();
    }

    /** Rev. 10: the projected light of the fixtures, one quad (3 passes) per fixture over the screen area of its cone. */
    private static boolean pointAccumFailed;

    /** Rev. 20: the point lights as quads into the accumulation target (concertstage_light_volume_add, AccumMode 1, blending one + one). */
    private static void pointQuads(boolean[] show, Vector3f camPose, Vector3f right, Vector3f up, Vector3f fwd) {
        shaderAdd.safeGetUniform("AccumMode").set(1f);
        RenderSystem.setShader(() -> shaderAdd);
        forceBlend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Vector3f c = new Vector3f(), p = new Vector3f();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        for (int k = 0; k < count; k++) {
            if (!show[k]) continue;
            int i = k * 8;
            c.set(DATA[i], DATA[i + 1], DATA[i + 2]);
            float rad = DATA[i + 7];
            float d = c.distance(camPose);
            int cr = Math.round(DATA[i + 3] * 255), cg = Math.round(DATA[i + 4] * 255), cb = Math.round(DATA[i + 5] * 255);
            int ca = Math.round(DATA[i + 6] * 255);
            int iz = Math.round(c.z * 100f), ir = Math.round(rad * 100f);
            Vector3f q = d > rad * 1.25f + 0.5f ? new Vector3f(c) : new Vector3f(fwd).mul(0.3f).add(camPose);
            float sz = d > rad * 1.25f + 0.5f ? 1.05f * d * rad / (float) Math.sqrt(d * d - rad * rad) : 2f;
            for (int corner = 0; corner < 4; corner++) {
                float sx = corner == 0 || corner == 3 ? -sz : sz, sy = corner < 2 ? -sz : sz;
                p.set(q).add(right.x * sx + up.x * sy, right.y * sx + up.y * sy, right.z * sx + up.z * sy);
                bb.vertex(p.x, p.y, p.z).color(cr, cg, cb, ca).uv(c.x, c.y).uv2(iz, ir).endVertex();
            }
        }
        BufferUploader.drawWithShader(bb.end());
        shaderAdd.safeGetUniform("AccumMode").set(0f);
    }

    /** Returns true when {@code points} (the point lights) were added into the accumulation and applied with the projected light. */
    private static boolean drawProjectors(Matrix4f mv, Matrix4f proj, Vector3f camPose, Vector3f right, Vector3f up, Vector3f fwd, float gain,
                                          Runnable points) throws java.io.IOException {
        if (pcount == 0 && points == null && scount == 0) return false;
        // rev. 20: camera frame -> world rotation (the block entities are drawn with the camera rotation X then Y + 180 in their pose)
        var cam = LightTargets.camera();
        Matrix4f viewToWorld = new Matrix4f().rotationX((float) Math.toRadians(cam.getXRot())).rotateY((float) Math.toRadians(cam.getYRot() + 180f)).invert();
        for (ShaderInstance sh : new ShaderInstance[] {projector, projectorAdd}) {
            sh.safeGetUniform("ViewToWorld").set(viewToWorld);
            sh.setSampler("DepthSampler", depthCopy.getDepthTextureId());
            sh.safeGetUniform("InvProjMat").set(new Matrix4f(proj).invert());
            sh.safeGetUniform("TargetSize").set((float) LightTargets.main().width,
                (float) LightTargets.main().height);
            sh.safeGetUniform("Gain").set(gain);
            sh.safeGetUniform("PassRoot").set(1f / PASSES);
            sh.safeGetUniform("AddK").set(PROJ_ADD);
        }
        var textures = Minecraft.getInstance().getTextureManager();
        Vector3f o = new Vector3f(), a = new Vector3f(), sd = new Vector3f(), c = new Vector3f(), p = new Vector3f();
        Matrix4f viewProj = new Matrix4f(proj).mul(mv), invViewProj = new Matrix4f(viewProj).invert();
        int drawn = 0;
        // rev. 16b: the light of every projector is added into one target, applied once by compositeLight
        RenderTarget main = LightTargets.main();
        if (lightAcc == null) lightAcc = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        else if (lightAcc.width != main.width || lightAcc.height != main.height) lightAcc.resize(main.width, main.height, Minecraft.ON_OSX);
        floatColour(lightAcc);
        lightAcc.setClearColor(0f, 0f, 0f, 0f);
        lightAcc.clear(Minecraft.ON_OSX);
        lightAcc.bindWrite(true);
        projectorAdd.safeGetUniform("AccumMode").set(1f);
        for (int k = 0; k < pcount; k++) {
            int i = k * PSTRIDE;
            o.set(PDATA[i], PDATA[i + 1], PDATA[i + 2]);
            a.set(PDATA[i + 3], PDATA[i + 4], PDATA[i + 5]);
            sd.set(PDATA[i + 6], PDATA[i + 7], PDATA[i + 8]);
            float tan = PDATA[i + 9], rot = PDATA[i + 10], range = PDATA[i + 11], r0 = PDATA[i + 12];
            // bounding sphere of the cone
            float rEnd = r0 + range * tan;
            c.set(a).mul(range * 0.5f).add(o);
            float rad = (float) Math.sqrt(range * range * 0.25f + rEnd * rEnd);
            float d = c.distance(camPose);
            if (d > rad && new Vector3f(c).sub(camPose).dot(fwd) < -rad) continue;   // behind the camera
            var tex = textures.getTexture(PTEX[k]);
            goboMips(tex);                                                        // smooth edges, mipmaps (rev. 16g)
            Vector3f ov = mv.transformPosition(new Vector3f(o));
            Vector3f av = mv.transformDirection(new Vector3f(a)).normalize();
            Vector3f sv = mv.transformDirection(new Vector3f(sd)).normalize();
            for (ShaderInstance sh : new ShaderInstance[] {projector, projectorAdd}) {
                sh.setSampler("PatternSampler", tex.getId());
            bindShadow(sh, (int) PDATA[i + 18], mv);
                sh.safeGetUniform("ProjOrigin").set(ov.x, ov.y, ov.z);
                sh.safeGetUniform("ProjAxis").set(av.x, av.y, av.z);
                sh.safeGetUniform("ProjSide").set(sv.x, sv.y, sv.z);
                sh.safeGetUniform("ProjShape").set(tan, (float) Math.cos(rot), (float) Math.sin(rot), range);
                sh.safeGetUniform("ProjLight").set(PDATA[i + 13], PDATA[i + 14], PDATA[i + 15], PDATA[i + 16]);
                sh.safeGetUniform("ProjOptics").set(r0, PDATA[i + 17], 0f, 0f);
                setOptics(sh, POPT, k * OPT);
                setTints(sh, PTINT, k * TINT);
            }
            Vector3f q = d > rad * 1.25f + 0.5f ? new Vector3f(c) : new Vector3f(fwd).mul(0.3f).add(camPose);
            float s = d > rad * 1.25f + 0.5f ? 1.05f * d * rad / (float) Math.sqrt(d * d - rad * rad) : 2f;
            // rev. 16b: one additive pass into the accumulation target (was 3 multiply passes + 1 additive pass on the screen per fixture)
            RenderSystem.setShader(() -> projectorAdd);
            forceBlend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);   // rev. 16e
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            // rev. 21: the screen rectangle of the cone (lens square and end square), not the square around its bounding sphere
            Vector3f u2 = new Vector3f(a).cross(sd).normalize();
            int nq = 0;
            for (int end = 0; end < 2; end++) {
                float along = end == 0 ? 0f : range, hw = (end == 0 ? r0 : r0 + range * tan) * 1.05f + 0.05f;
                for (int corner = 0; corner < 4; corner++) {
                    float sx = corner == 0 || corner == 3 ? -hw : hw, sy = corner < 2 ? -hw : hw;
                    QPTS[nq * 3] = o.x + a.x * along + sd.x * sx + u2.x * sy;
                    QPTS[nq * 3 + 1] = o.y + a.y * along + sd.y * sx + u2.y * sy;
                    QPTS[nq * 3 + 2] = o.z + a.z * along + sd.z * sx + u2.z * sy;
                    nq++;
                }
            }
            int sq = screenQuad(bb, viewProj, invViewProj, QPTS, nq);
            if (sq == 2) { bb.end().release(); continue; }
            if (sq == 0) {
                for (int corner = 0; corner < 4; corner++) {
                    float sx = corner == 0 || corner == 3 ? -s : s, sy = corner < 2 ? -s : s;
                    p.set(q).add(right.x * sx + up.x * sy, right.y * sx + up.y * sy, right.z * sx + up.z * sy);
                    bb.vertex(p.x, p.y, p.z).endVertex();
                }
            }
            BufferUploader.drawWithShader(bb.end());
            drawn++;
        }
        spillLastFrame = 0;
        if (scount > 0 && !spillFailed) {
            try {
                spillLastFrame = drawSpills(mv, proj, camPose, right, up, fwd, gain);
            } catch (RuntimeException | java.io.IOException e) {
                spillFailed = true;                        // the projected light goes on without them
                com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: spill light turned off ({})", e.toString());
            }
            lightAcc.bindWrite(true);
        }
        boolean accumulated = false;
        if (points != null) {
            try {
                points.run();
                accumulated = true;
            } catch (RuntimeException e) {
                pointAccumFailed = true;                   // the lights go back to their own passes, for good
                com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: point lights back to separate passes ({})", e.toString());
            }
            lightAcc.bindWrite(true);
        }
        compositeLight(gain);
        projectedLastFrame = drawn;
        frameProjected += drawn;
        return accumulated;
    }

    /** Rev. 21: the spill lights into the accumulation target (one quad each over the sphere around their segment). */
    private static int drawSpills(Matrix4f mv, Matrix4f proj, Vector3f camPose, Vector3f right, Vector3f up, Vector3f fwd, float gain) throws java.io.IOException {
        if (spillShader == null) spillShader = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_light_spill", DefaultVertexFormat.POSITION);
        ShaderInstance sh = spillShader;
        sh.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        sh.safeGetUniform("InvProjMat").set(new Matrix4f(proj).invert());
        sh.safeGetUniform("TargetSize").set((float) lightAcc.width, (float) lightAcc.height);
        sh.safeGetUniform("Gain").set(gain);
        sh.safeGetUniform("DebugMode").set((float) LightDiagnostics.shaderMode());
        RenderSystem.setShader(() -> sh);
        forceBlend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f(), p = new Vector3f(), n = new Vector3f(), q0 = new Vector3f();
        Matrix4f viewProj = new Matrix4f(proj).mul(mv), invViewProj = new Matrix4f(viewProj).invert();
        int drawn = 0;
        for (int k = 0; k < scount; k++) {
            int i = k * SSTRIDE;
            a.set(SDATA[i], SDATA[i + 1], SDATA[i + 2]);
            b.set(SDATA[i + 3], SDATA[i + 4], SDATA[i + 5]);
            float R = SDATA[i + 10];
            c.set(a).add(b).mul(0.5f);
            float rad = a.distance(b) * 0.5f + R;
            float d = c.distance(camPose);
            if (d > rad && new Vector3f(c).sub(camPose).dot(fwd) < -rad) continue;
            Vector3f av = mv.transformPosition(new Vector3f(a)), bv = mv.transformPosition(new Vector3f(b));
            sh.safeGetUniform("SpillA").set(av.x, av.y, av.z);
            sh.safeGetUniform("SpillB").set(bv.x, bv.y, bv.z);
            sh.safeGetUniform("SpillLight").set(SDATA[i + 6], SDATA[i + 7], SDATA[i + 8], SDATA[i + 9]);
            sh.safeGetUniform("SpillShape").set(R, SDATA[i + 11], SDATA[i + 12], SDATA[i + 18]);
            if (SDATA[i + 17] > 0.5f) {
                n.set(SDATA[i + 13], SDATA[i + 14], SDATA[i + 15]);
                // a point of the plane, from n.x + d = 0, into view space with the normal
                q0.set(n).mul(-SDATA[i + 16]);
                Vector3f nv = mv.transformDirection(new Vector3f(n)).normalize();
                Vector3f pv = mv.transformPosition(new Vector3f(q0));
                sh.safeGetUniform("SpillPlane").set(nv.x, nv.y, nv.z, -nv.dot(pv));
            } else sh.safeGetUniform("SpillPlane").set(0f, 0f, 0f, 0f);
            if (Math.abs(SDATA[i + 19]) + Math.abs(SDATA[i + 20]) + Math.abs(SDATA[i + 21]) > 0.5f) {
                n.set(SDATA[i + 19], SDATA[i + 20], SDATA[i + 21]);
                q0.set(n).mul(-SDATA[i + 22]);
                Vector3f nv = mv.transformDirection(new Vector3f(n)).normalize();
                Vector3f pv = mv.transformPosition(new Vector3f(q0));
                sh.safeGetUniform("SpillFloor").set(nv.x, nv.y, nv.z, -nv.dot(pv));
            } else sh.safeGetUniform("SpillFloor").set(0f, 0f, 0f, 0f);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            // rev. 21 (perf): a beam segment gets a quad hugging it (its length plus the radius, as wide as the radius), not a square
            // around the sphere of its whole length — a beam across the stage covered the screen once per fixture
            Vector3f ab = new Vector3f(b).sub(a);
            float len = ab.length();
            float dSeg = segmentDistance(camPose, a, b);
            Vector3f side = len > 0.5f ? new Vector3f(ab).cross(new Vector3f(c).sub(camPose)) : new Vector3f();
            int nq = 0;
            for (int k2 = 0; k2 < 8; k2++) {
                QPTS[nq * 3] = ((k2 & 1) == 0 ? Math.min(a.x, b.x) - R : Math.max(a.x, b.x) + R);
                QPTS[nq * 3 + 1] = ((k2 & 2) == 0 ? Math.min(a.y, b.y) - R : Math.max(a.y, b.y) + R);
                QPTS[nq * 3 + 2] = ((k2 & 4) == 0 ? Math.min(a.z, b.z) - R : Math.max(a.z, b.z) + R);
                nq++;
            }
            int sq = screenQuad(bb, viewProj, invViewProj, QPTS, nq);
            if (sq == 2) { bb.end().release(); continue; }
            if (sq == 1) { /* the screen rectangle of its box */ }
            else if (len > 0.5f && dSeg > R * 1.3f + 0.5f && side.length() > 1e-3f * len * d) {
                side.normalize().mul(R * 1.5f);
                ab.normalize().mul(R * 1.2f);
                Vector3f a0 = new Vector3f(a).sub(ab), b0 = new Vector3f(b).add(ab);
                bb.vertex(a0.x - side.x, a0.y - side.y, a0.z - side.z).endVertex();
                bb.vertex(b0.x - side.x, b0.y - side.y, b0.z - side.z).endVertex();
                bb.vertex(b0.x + side.x, b0.y + side.y, b0.z + side.z).endVertex();
                bb.vertex(a0.x + side.x, a0.y + side.y, a0.z + side.z).endVertex();
            } else {
                Vector3f q = d > rad * 1.25f + 0.5f ? new Vector3f(c) : new Vector3f(fwd).mul(0.3f).add(camPose);
                float s = d > rad * 1.25f + 0.5f ? 1.05f * d * rad / (float) Math.sqrt(d * d - rad * rad) : 2f;
                for (int corner = 0; corner < 4; corner++) {
                    float sx = corner == 0 || corner == 3 ? -s : s, sy = corner < 2 ? -s : s;
                    p.set(q).add(right.x * sx + up.x * sy, right.y * sx + up.y * sy, right.z * sx + up.z * sy);
                    bb.vertex(p.x, p.y, p.z).endVertex();
                }
            }
            BufferUploader.drawWithShader(bb.end());
            drawn++;
        }
        return drawn;
    }

    /**
     * Rev. 21 (perf): a quad in pose space that covers, on screen, the rectangle around the given pose-space points (x y z triples).
     * Returns 1 when written into {@code bb}, 2 when the points are all off screen (nothing to draw), 0 when a point is behind or
     * too near the camera (the caller then uses its own larger quad). The light passes only shade the pixels of that rectangle: a
     * fixture whose cone covers a corner of the screen no longer runs its shader over the whole screen.
     */
    private static int screenQuad(BufferBuilder bb, Matrix4f viewProj, Matrix4f invViewProj, float[] pts, int n) {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        org.joml.Vector4f v = new org.joml.Vector4f();
        for (int i = 0; i < n; i++) {
            v.set(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2], 1f);
            viewProj.transform(v);
            if (v.w < 0.3f) return 0;
            float x = v.x / v.w, y = v.y / v.w;
            x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
        }
        x0 = Math.max(-1f, x0 - 0.01f); y0 = Math.max(-1f, y0 - 0.01f); x1 = Math.min(1f, x1 + 0.01f); y1 = Math.min(1f, y1 + 0.01f);
        if (x0 >= x1 || y0 >= y1) return 2;
        float[][] c = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
        for (float[] q : c) {
            v.set(q[0], q[1], 0.5f, 1f);
            invViewProj.transform(v);
            bb.vertex(v.x / v.w, v.y / v.w, v.z / v.w).endVertex();
        }
        return 1;
    }

    private static final float[] QPTS = new float[8 * 3];

    private static float segmentDistance(Vector3f p, Vector3f a, Vector3f b) {
        Vector3f ab = new Vector3f(b).sub(a);
        float l2 = ab.lengthSquared();
        float t = l2 < 1e-6f ? 0f : Math.max(0f, Math.min(1f, new Vector3f(p).sub(a).dot(ab) / l2));
        return new Vector3f(a).add(ab.mul(t)).distance(p);
    }

    static int projectedLastFrame;
    private static TextureTarget lightAcc;
    private static int floatTex = -1, floatW, floatH;
    private static boolean floatFailed;

    /**
     * Rev. 24 — the accumulation target in half floats (RGBA16F) instead of 8 bits. With 8 bits the sum was clipped at L = 4 per
     * channel: under a row of washes the channels of the wash were already at the ceiling, so a gobo added on top could only raise the
     * channels the wash did not use, and its pattern came out in the colour of the floor (seen in game, 11 November, 23 h). In half
     * floats nothing is clipped before the tone mapping, which keeps the hue. Done again after each resize (the game re-creates the
     * texture in 8 bits). If the driver refuses it, the 8-bit target stays (as in rev. 23).
     */
    private static void floatColour(TextureTarget t) {
        if (floatFailed) return;
        int id = t.getColorTextureId();
        if (id == floatTex && t.width == floatW && t.height == floatH) return;
        while (org.lwjgl.opengl.GL11.glGetError() != 0) { }
        GlStateManager._bindTexture(id);
        org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_RGBA16F, t.width, t.height, 0,
            org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL30.GL_HALF_FLOAT, (java.nio.ByteBuffer) null);
        GlStateManager._bindTexture(0);
        int err = org.lwjgl.opengl.GL11.glGetError();
        if (err != 0) {
            floatFailed = true;
            GlStateManager._bindTexture(id);
            org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA8, t.width, t.height, 0,
                org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GlStateManager._bindTexture(0);
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: half-float light target refused (GL error {}), 8 bits kept", err);
            return;
        }
        floatTex = id; floatW = t.width; floatH = t.height;
    }

    /** Rev. 24: true when the light is accumulated in half floats (statistics, tests in game). */
    static boolean floatAccumulation() { return floatTex >= 0 && !floatFailed; }
    private static ShaderInstance composite, compositeAdd;

    /**
     * Rev. 16b: applies the accumulated light of the projectors to the scene: two multiply passes (dst x f, f = 1 + 3 tanh(L / 2.5))
     * and the additive pass for dark materials (concertstage_light_composite). Many fixtures on one surface saturate softly instead of
     * multiplying each other (the white floor in front of a row of blinders, 9 November).
     */
    private static void compositeLight(float gain) throws java.io.IOException {
        RenderTarget main = LightTargets.main();
        if (LightClient.toneMapping && !tonemapFailed) {
            try {
                if (tonemapped(main, gain)) return;
            } catch (RuntimeException | java.io.IOException e) {
                tonemapFailed = true;                      // driver / shader trouble: the two multiply passes as before, for good
                com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: light tone mapping disabled ({})", e.toString());
            }
        }
        main.bindWrite(false);
        if (composite == null) composite = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_light_composite", DefaultVertexFormat.POSITION);
        if (compositeAdd == null) compositeAdd = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_light_composite_add", DefaultVertexFormat.POSITION);
        float[] ambient = ambientTint();
        for (ShaderInstance sh : new ShaderInstance[] {composite, compositeAdd}) {
            tint(sh, ambient);
            sh.setSampler("LightSampler", lightAcc.getColorTextureId());
            sh.safeGetUniform("TargetSize").set((float) main.width, (float) main.height);
            sh.safeGetUniform("PassRoot").set(0.5f);
            sh.safeGetUniform("AddK").set(PROJ_ADD);
            sh.safeGetUniform("Gain").set(gain);
            sh.safeGetUniform("DebugMode").set((float) LightDiagnostics.shaderMode());
        }
        for (int pass = 0; pass < 3; pass++) {
            boolean add = pass == 2;
            RenderSystem.setShader(add ? () -> compositeAdd : () -> composite);
            forceBlend(add ? GlStateManager.SourceFactor.ONE : GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            bb.vertex(-1f, -1f, 0f).endVertex();
            bb.vertex(1f, -1f, 0f).endVertex();
            bb.vertex(1f, 1f, 0f).endVertex();
            bb.vertex(-1f, 1f, 0f).endVertex();
            BufferUploader.drawWithShader(bb.end());
        }
    }

    private static TextureTarget sceneCopy;
    private static ShaderInstance tonemap;
    private static boolean tonemapFailed;

    /**
     * Rev. 19: the light of the projectors applied with tone mapping (concertstage_light_tonemap): the frame is copied, then one pass
     * writes scene x light + the light of dark materials, its highlights compressed instead of clipped (the white floors and trusses
     * under several fixtures). False if it cannot run (the caller uses the blended passes).
     */
    private static boolean tonemapped(RenderTarget main, float gain) throws java.io.IOException {
        if (sceneCopy == null) sceneCopy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        else if (sceneCopy.width != main.width || sceneCopy.height != main.height) sceneCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        if (tonemap == null) tonemap = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_light_tonemap", DefaultVertexFormat.POSITION);
        // the frame as it is, into the copy (a blit of the colour buffer, as RenderTarget#copyDepthFrom does for the depth)
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, sceneCopy.width, sceneCopy.height,
            org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
        main.bindWrite(false);
        tonemap.setSampler("LightSampler", lightAcc.getColorTextureId());
        tonemap.setSampler("SceneSampler", sceneCopy.getColorTextureId());
        tonemap.safeGetUniform("TargetSize").set((float) main.width, (float) main.height);
        tonemap.safeGetUniform("AddK").set(PROJ_ADD);
        tonemap.safeGetUniform("Gain").set(gain);
        tonemap.safeGetUniform("Knee").set(.65f);
        tint(tonemap, ambientTint());
        tonemap.safeGetUniform("DebugMode").set((float) LightDiagnostics.shaderMode());
        RenderSystem.setShader(() -> tonemap);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1f, -1f, 0f).endVertex();
        bb.vertex(1f, -1f, 0f).endVertex();
        bb.vertex(1f, 1f, 0f).endVertex();
        bb.vertex(-1f, 1f, 0f).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableBlend();
        return true;
    }

    private static final java.util.Map<Object, Boolean> MIPPED = new java.util.WeakHashMap<>();

    /**
     * Rev. 16g: the gobo pictures get their mipmaps once (the game loads them with a single level) and are read with trilinear
     * filtering at the level the shaders choose (cscGoboLod): far away the pattern keeps its shape instead of breaking into noise.
     * If the mipmaps cannot be made, the picture keeps its single level and plain linear filtering (never an incomplete texture).
     */
    private static void goboMips(net.minecraft.client.renderer.texture.AbstractTexture tex) {
        Boolean ok = MIPPED.get(tex);
        if (ok == null) {
            ok = false;
            try {
                for (int e = 0; e < 8 && org.lwjgl.opengl.GL11.glGetError() != 0; e++) { }   // errors left by others
                RenderSystem.bindTexture(tex.getId());
                com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 33085, 8);      // GL_TEXTURE_MAX_LEVEL
                com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 33083, 8f);     // GL_TEXTURE_MAX_LOD
                com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 33082, 0f);     // GL_TEXTURE_MIN_LOD
                org.lwjgl.opengl.GL30.glGenerateMipmap(3553);
                ok = org.lwjgl.opengl.GL11.glGetError() == 0;
            } catch (Throwable t) {
                ok = false;
            }
            if (!ok) {
                try {
                    RenderSystem.bindTexture(tex.getId());
                    com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 33085, 0);
                } catch (Throwable ignored) { }
            }
            MIPPED.put(tex, ok);
        }
        tex.setFilter(true, ok);
    }

    /** Rev. 16: the uniforms of the common optical mask (concertstage_optics.glsl) and the diagnostic view. */
    private static void setOptics(ShaderInstance sh, float[] u, int at) {
        sh.safeGetUniform("OptKind").set(u[at], u[at + 1], u[at + 2], u[at + 3]);
        sh.safeGetUniform("OptShape").set(u[at + 4], u[at + 5], u[at + 6], u[at + 7]);
        for (int r = 0; r < com.concertstage.light.core.OpticalProfile.MAX_RINGS; r++)
            sh.safeGetUniform("OptRing" + RING_INDEX[r]).set(u[at + 8 + r * 4], u[at + 9 + r * 4], u[at + 10 + r * 4], u[at + 11 + r * 4]);
        sh.safeGetUniform("DebugMode").set((float) LightDiagnostics.shaderMode());
    }

    private static final String[] RING_INDEX = {"0", "1", "2", "3", "4", "5", "6", "7"};

    /** Rev. 16b: colours of the cells (OpticalProfile#tints) for the common optical mask. */
    private static void setTints(ShaderInstance sh, float[] t, int at) {
        sh.safeGetUniform("OptTintInfo").set(t[at], t[at + 1], t[at + 2], t[at + 3]);
        for (int g = 0; g < com.concertstage.light.core.OpticalProfile.MAX_TINTS; g++)
            sh.safeGetUniform("OptTint" + RING_INDEX[g]).set(t[at + 4 + g * 4], t[at + 5 + g * 4], t[at + 6 + g * 4], 0f);
    }

    /**
     * Rev. 16: the optical state of one light for the common optical mask: the profile of its family (OpticalProfile, from its model)
     * and what the fixture does this frame (gobo, prism, beam angle, focus). Filled by the renderer, copied into the light's slot.
     */
    static final class Optics {
        com.concertstage.light.core.OpticalProfile profile;
        boolean gobo, prism;
        float prismRad, angleDeg, sharpness = 1f;

        /** Rev. 16b: colours of the cells, computed once per fixture and frame; {@link #tinted}: the shader uses them. */
        final float[] tint = new float[com.concertstage.light.core.OpticalProfile.TINT_FLOATS];
        boolean tinted;
        com.concertstage.light.core.FixtureProfile fixture;
        float[] controls;
        double seconds;

        Optics effects(com.concertstage.light.core.Engine.Output output, double time) {
            fixture = output.profile; controls = output.v; seconds = time;
            return this;
        }

        Optics set(com.concertstage.light.core.OpticalProfile p, boolean gobo, boolean prism, float prismRad, float angleDeg, float sharpness,
                   float[] zones) {
            this.fixture = null; this.controls = null;
            this.profile = p; this.gobo = gobo; this.prism = prism; this.prismRad = prismRad; this.angleDeg = angleDeg; this.sharpness = sharpness;
            this.tinted = p.tints(zones, tint, 0);
            return this;
        }

        void write(float[] out, int at) {
            profile.uniforms(gobo, prism, prismRad, angleDeg, sharpness, out, at);
            if (fixture != null) com.concertstage.light.core.ProfessionalOptics.write(fixture, controls, seconds, out, at);
        }
    }

    /** Rev. 15: the shadow map of a beam for a shader (or none: the shader then ignores it). */
    private static void bindShadow(ShaderInstance sh, int h, Matrix4f mv) {
        int tex = BeamShadows.texture(h);
        if (tex < 0) {
            sh.setSampler("ShadowSampler", depthCopy.getDepthTextureId());
            sh.safeGetUniform("ShadowNF").set(0.1f, 1f, 0f);
            return;
        }
        sh.setSampler("ShadowSampler", tex);
        sh.safeGetUniform("ShadowMat").set(BeamShadows.shadowMat(h, mv));
        sh.safeGetUniform("ShadowNF").set(BeamShadows.nearOf(h), BeamShadows.farOf(h), 1f);
    }

    // ---- rev. 15: the beams in the haze, computed along each line of sight (concertstage_beam_volume)

    // rev. 22: 16 beams in the haze at low quality (the washes first: their wide ribbons showed their edges at full zoom)
    private static final int[] MAX_VOLUMES = {32, 48, 48, 64};         // rev. 23: low 16 -> 32, medium 24 -> 48
    private static final int[] VOLUME_STEPS = {0, 14, 22, 30};
    private static final int VSTRIDE = 22;
    private static final float[] VDATA = new float[64 * VSTRIDE];
    private static final net.minecraft.resources.ResourceLocation[] VTEX = new net.minecraft.resources.ResourceLocation[64];
    private static int vcount, vquality, frameVolumes, frameVolumesAsked, lastVolumes, lastVolumesAsked;
    private static ShaderInstance volume;

    /**
     * Rev. 15: a beam in the haze (pose space, as {@link #project}). {@code r g b}: colour times strength on the scale of the beam
     * ribbons; {@code absorb} per block; {@code endFade} blocks over which it fades before the range (0: it ends on a surface).
     * Returns false when it will not be drawn (feature off, budget): the caller draws its ribbons.
     */
    static boolean volume(Vector3f o, Vector3f a, Vector3f side, float tanHalf, float rotDeg, float range, float r0, float r, float g, float b,
                          float absorb, float focusSpread, float endFade, net.minecraft.resources.ResourceLocation pattern, int quality, int shadow,
                          Optics optics) {
        int q = Math.max(0, Math.min(3, quality));
        frameVolumesAsked++;
        if (!enabled() || volumeFailed || vcount >= MAX_VOLUMES[FixtureRenderer.Perf.quality()] || Math.max(r, Math.max(g, b)) < 0.002f) return false;
        if (vcount >= LightClient.budget(LightClient.maxVolumes, 64)) return false;
        optics.write(VOPT, vcount * OPT);
        System.arraycopy(optics.tint, 0, VTINT, vcount * TINT, TINT);
        int i = vcount * VSTRIDE;
        VDATA[i] = o.x; VDATA[i + 1] = o.y; VDATA[i + 2] = o.z;
        VDATA[i + 3] = a.x; VDATA[i + 4] = a.y; VDATA[i + 5] = a.z;
        VDATA[i + 6] = side.x; VDATA[i + 7] = side.y; VDATA[i + 8] = side.z;
        VDATA[i + 9] = tanHalf; VDATA[i + 10] = (float) Math.toRadians(rotDeg); VDATA[i + 11] = range; VDATA[i + 12] = r0;
        VDATA[i + 13] = r; VDATA[i + 14] = g; VDATA[i + 15] = b; VDATA[i + 16] = absorb; VDATA[i + 17] = focusSpread;
        VDATA[i + 18] = endFade; VDATA[i + 19] = shadow;
        VTEX[vcount++] = pattern;
        vquality = Math.max(vquality, q);
        return true;
    }

    private static boolean volumeFailed, veilFailed, debugFailed;
    private static ShaderInstance debugView;

    /** Rev. 16c: diagnostic views of the depth buffer and of its normals, as the light engine reads them (LightDiagnostics). */
    private static void drawBufferView() throws java.io.IOException {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = LightTargets.main();
        if (depthCopy == null) depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        else if (depthCopy.width != main.width || depthCopy.height != main.height) depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);
        if (debugView == null) debugView = new ShaderInstance(mc.getResourceManager(), "concertstage_light_debug", DefaultVertexFormat.POSITION);
        debugView.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        debugView.safeGetUniform("InvProjMat").set(new Matrix4f(RenderSystem.getProjectionMatrix()).invert());
        debugView.safeGetUniform("TargetSize").set((float) main.width, (float) main.height);
        debugView.safeGetUniform("DebugMode").set((float) LightDiagnostics.shaderMode());
        RenderSystem.setShader(() -> debugView);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1f, -1f, 0f).endVertex();
        bb.vertex(1f, -1f, 0f).endVertex();
        bb.vertex(1f, 1f, 0f).endVertex();
        bb.vertex(-1f, 1f, 0f).endVertex();
        BufferUploader.drawWithShader(bb.end());
        restore();
    }
    private static ShaderInstance veil;

    /**
     * Rev. 16b — Atmospheric System: with a medium or dense haze the scenery fades with the distance into the colour of the haze
     * (dark at night, the light grey of the fog by day), so that the beams stand out of the smoke instead of being drawn over a clear
     * night. Density per block: 0 up to the light haze, then growing with it (medium 0.006, dense 0.016).
     */
    private static void drawVeil() throws java.io.IOException {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = LightTargets.main();
        if (depthCopy == null) depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        else if (depthCopy.width != main.width || depthCopy.height != main.height) depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);
        if (veil == null) veil = new ShaderInstance(mc.getResourceManager(), "concertstage_haze", DefaultVertexFormat.POSITION);
        float dark = mc.level == null ? 1f : Math.min(1f, Math.max(0f, mc.level.getSkyDarken() / 11f));
        float density = hazeDensity(LightClient.haze);
        float grey = 0.04f + 0.56f * (1f - dark);
        veil.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        veil.safeGetUniform("InvProjMat").set(new Matrix4f(RenderSystem.getProjectionMatrix()).invert());
        veil.safeGetUniform("TargetSize").set((float) main.width, (float) main.height);
        veil.safeGetUniform("HazeVeil").set(grey, grey * 1.04f, grey * 1.1f, density);
        RenderSystem.setShader(() -> veil);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        forceBlend(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1f, -1f, 0f).endVertex();
        bb.vertex(1f, -1f, 0f).endVertex();
        bb.vertex(1f, 1f, 0f).endVertex();
        bb.vertex(-1f, 1f, 0f).endVertex();
        BufferUploader.drawWithShader(bb.end());
        restore();
    }

    /** Density of the haze veil per block for a haze setting (0 up to the light haze, 1). */
    static float hazeDensity(float haze) {
        return haze <= 1f ? 0f : 0.0128f * (float) Math.pow(haze - 1f, 1.3);
    }

    /**
     * Rev. 20: luminance / colour of the sky light the game draws the scene with (its light map: (s, s, 1) brought 35 % towards white,
     * s the sky brightness), mixed 60 % (block-lit surfaces are not blue): the multiplied light is divided by the moonlight's tint,
     * so a white fixture gives a white pool at night instead of a lavender one. (1, 1, 1) by day.
     */
    static float[] ambientTint() {
        var lvl = Minecraft.getInstance().level;
        float sky = lvl == null ? 1f : lvl.getSkyDarken(1f) * 0.95f + 0.05f;
        float r = sky * 0.65f + 0.35f, b = 1f;
        float lum = (0.2126f + 0.7152f) * r + 0.0722f * b;
        float tr = 1f + 0.6f * (lum / r - 1f), tb = 1f + 0.6f * (lum / b - 1f);
        return new float[]{tr, tr, tb};
    }

    private static void tint(ShaderInstance sh, float[] t) {
        sh.safeGetUniform("AmbientTint").set(t[0], t[1], t[2]);
    }

    /** Rev. 20: beams in the haze or the haze veil to draw this frame. */
    public static boolean beamsPending() {
        return vcount > 0 || enabled() && !veilFailed && LightClient.haze > 1.01f && LightDiagnostics.mode == LightDiagnostics.OFF;
    }

    /** Rev. 15: draws the beams in the haze (render thread, after the fixture bodies so they hide the beam behind them, before the ribbons). */
    public static void flushBeams() {
        if (enabled() && !veilFailed && LightClient.haze > 1.01f && LightDiagnostics.mode == LightDiagnostics.OFF) {
            try {
                drawVeil();
            } catch (Throwable t) {
                veilFailed = true;
                com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: haze veil turned off (shader or driver error)", t);
                try { LightTargets.main().bindWrite(false); } catch (Throwable ignored) { }
            }
        }
        if (vcount == 0) return;
        try {
            if (!enabled() || volumeFailed) return;
            if (volume == null) volume = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_beam_volume",
                DefaultVertexFormat.POSITION);
            drawVolumes();
        } catch (Throwable t) {
            volumeFailed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: beams in the haze turned off (shader or driver error)", t);
            try { LightTargets.main().bindWrite(false); } catch (Throwable ignored) { }
        } finally {
            vcount = 0;
            vquality = 0;
        }
    }

    private static void drawVolumes() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = LightTargets.main();
        if (depthCopy == null) depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        else if (depthCopy.width != main.width || depthCopy.height != main.height) depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        depthCopy.copyDepthFrom(main);                              // now with the fixture bodies
        main.bindWrite(false);
        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f pv = new Matrix4f(proj).mul(mv);
        volume.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        volume.safeGetUniform("InvProjMat").set(new Matrix4f(proj).invert());
        // rev. 24: the beams in the haze at half resolution (a quarter of the pixels) in low / medium quality, then spread onto the
        // frame with the same screen blending: the haze is soft, the picture is the same, the most expensive pass costs ~4x less
        boolean half = halfRes(main);
        if (half) {
            int hw = Math.max(1, (main.width + 1) / 2), hh = Math.max(1, (main.height + 1) / 2);
            if (hazeHalf == null) hazeHalf = new TextureTarget(hw, hh, false, Minecraft.ON_OSX);
            else if (hazeHalf.width != hw || hazeHalf.height != hh) hazeHalf.resize(hw, hh, Minecraft.ON_OSX);
            hazeHalf.setClearColor(0f, 0f, 0f, 0f);
            hazeHalf.clear(Minecraft.ON_OSX);
            hazeHalf.bindWrite(true);
            volume.safeGetUniform("TargetSize").set((float) hw, (float) hh);
        } else volume.safeGetUniform("TargetSize").set((float) main.width, (float) main.height);
        RenderSystem.setShader(() -> volume);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        var textures = mc.getTextureManager();
        Vector3f o = new Vector3f(), a = new Vector3f(), sd = new Vector3f(), up = new Vector3f(), p = new Vector3f();
        org.joml.Vector4f c = new org.joml.Vector4f();
        int drawn = 0;
        for (int k = 0; k < vcount; k++) {
            int i = k * VSTRIDE;
            o.set(VDATA[i], VDATA[i + 1], VDATA[i + 2]);
            a.set(VDATA[i + 3], VDATA[i + 4], VDATA[i + 5]);
            sd.set(VDATA[i + 6], VDATA[i + 7], VDATA[i + 8]);
            float tan = VDATA[i + 9], rot = VDATA[i + 10], range = VDATA[i + 11], r0 = VDATA[i + 12];
            up.set(a).cross(sd).normalize();
            // screen rectangle of the box around the cone (the whole screen when part of it is behind the camera)
            // rev. 20: the box is taken wider than the cone (soft edge to 1.08 of the radius, out-of-focus spread, the skirt of the
            // washes, prism images): seen in game, the light of the beam ended on a straight vertical and horizontal edge, the side of
            // the screen rectangle — most visible with the focus turned up (the blur spreads the beam past its radius)
            float rEnd = (r0 + range * tan) * 1.75f + 0.4f;
            float rStart = r0 * 1.75f + 0.25f;
            float x0 = 1f, y0 = 1f, x1 = -1f, y1 = -1f;
            boolean full = false;
            for (int corner = 0; corner < 8 && !full; corner++) {
                float rr = corner < 4 ? rStart : rEnd, along = corner < 4 ? -0.3f : range + 0.5f;
                float sx = (corner & 1) == 0 ? -rr : rr, sy = (corner & 2) == 0 ? -rr : rr;
                p.set(a).mul(along).add(o).add(sd.x * sx + up.x * sy, sd.y * sx + up.y * sy, sd.z * sx + up.z * sy);
                c.set(p.x, p.y, p.z, 1f).mul(pv);
                if (c.w < 0.05f) { full = true; break; }
                float nx = c.x / c.w, ny = c.y / c.w;
                x0 = Math.min(x0, nx); y0 = Math.min(y0, ny); x1 = Math.max(x1, nx); y1 = Math.max(y1, ny);
            }
            if (full) { x0 = -1f; y0 = -1f; x1 = 1f; y1 = 1f; }
            x0 = Math.max(-1f, x0); y0 = Math.max(-1f, y0); x1 = Math.min(1f, x1); y1 = Math.min(1f, y1);
            if (x0 >= x1 || y0 >= y1) continue;
            var tex = textures.getTexture(VTEX[k]);
            goboMips(tex);
            Vector3f ov = mv.transformPosition(new Vector3f(o));
            Vector3f av = mv.transformDirection(new Vector3f(a)).normalize();
            Vector3f sv = mv.transformDirection(new Vector3f(sd)).normalize();
            volume.setSampler("PatternSampler", tex.getId());
            bindShadow(volume, (int) VDATA[i + 19], mv);
            volume.safeGetUniform("BeamOrigin").set(ov.x, ov.y, ov.z);
            volume.safeGetUniform("BeamAxis").set(av.x, av.y, av.z);
            volume.safeGetUniform("BeamSide").set(sv.x, sv.y, sv.z);
            volume.safeGetUniform("BeamShape").set(tan, (float) Math.cos(rot), (float) Math.sin(rot), range);
            volume.safeGetUniform("BeamLight").set(VDATA[i + 13], VDATA[i + 14], VDATA[i + 15], VDATA[i + 16]);
            // rev. 16g: fewer steps for the families whose light in the air is smooth (wash: lens shafts merge within a few blocks;
            // PULSESTRIKE: a flat flood). The VORTEX keeps every step for its gobo. Washes near the camera cover the whole screen:
            // they were the most expensive beams (58 fps, GPU 100 %, red washes picture of 22 h 41)
            float fam = VOPT[k * OPT];
            float stepK = fam < 0.5f ? 1f : fam < 1.5f ? 0.6f : 0.5f;
            volume.safeGetUniform("BeamOptics").set(r0, VDATA[i + 17], (float) Math.max(6, Math.min(64, Math.round(VOLUME_STEPS[Math.max(1, vquality)] * LightClient.volumeSteps * stepK))), VDATA[i + 18]);
            setOptics(volume, VOPT, k * OPT);
            setTints(volume, VTINT, k * TINT);
            forceBlend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);   // rev. 16e, see forceBlend
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            bb.vertex(x0, y0, 0f).endVertex();
            bb.vertex(x1, y0, 0f).endVertex();
            bb.vertex(x1, y1, 0f).endVertex();
            bb.vertex(x0, y1, 0f).endVertex();
            BufferUploader.drawWithShader(bb.end());
            drawn++;
        }
        frameVolumes += drawn;
        if (half) {
            main.bindWrite(true);
            if (drawn > 0) spreadHalf(main);
        }
        restore();
    }

    private static TextureTarget hazeHalf;
    private static boolean adaptiveHalf;
    private static int adaptiveWindow = -1;
    private static int adaptiveOver, adaptiveCount, adaptiveWait = 8;
    private static boolean adaptiveProbe;
    private static ShaderInstance spread;
    private static boolean halfFailed;
    static int halfFrames;

    /**
     * Rev. 24: beams in the haze at half resolution? Setting {@code haze_resolution}: auto (low and medium quality, or a frame larger
     * than 1.5 x 1080p in high), full, half.
     */
    static boolean halfRes(RenderTarget main) {
        if (halfFailed || LightClient.hazeResolution == 1) return false;
        if (LightClient.hazeResolution == 2) return true;
        if (FixtureRenderer.Perf.quality() <= 1 || (FixtureRenderer.Perf.quality() == 2 && (long) main.width * main.height > 3_110_400L)) return true;
        // rev. 25 — Auto in high / ultra: full resolution while the haze is cheap; when the beams in the haze cost the card more than
        // 3 ms a frame (16 W19 at full zoom over the camera: 12.9 ms measured), half resolution, with a full-resolution window tried now and then
        // to come back when the scene got cheaper. The picture is the same to the eye (rev. 24 captures), the frame rate stays.
        int w = GpuTimers.window;
        if (w != adaptiveWindow) {
            adaptiveWindow = w;
            double ms = GpuTimers.gpuMs(GpuTimers.HAZE);
            if (ms >= 0) {
                // rev. 25: two windows in a row above 3 ms (a single slow frame — loading, a capture — does not count) switch to half;
                // in half, a window at full resolution is tried now and then (after 8, 16 … 64 windows): kept if it costs < 3 ms
                if (!adaptiveHalf) {
                    adaptiveOver = ms > 3.0 ? adaptiveOver + 1 : 0;
                    if (adaptiveOver >= 2 || (adaptiveProbe && ms > 3.0)) {
                        adaptiveHalf = true; adaptiveOver = 0; adaptiveCount = 0;
                        adaptiveWait = adaptiveProbe ? Math.min(64, adaptiveWait * 2) : 8;
                    } else if (adaptiveProbe) adaptiveWait = 8;
                    adaptiveProbe = false;
                } else if (++adaptiveCount >= adaptiveWait) {
                    adaptiveHalf = false; adaptiveProbe = true;
                }
            }
        }
        return adaptiveHalf;
    }

    /** The half-resolution haze onto the frame (concertstage_bloom, mode 3: a tent filter), screen blending as the beams themselves. */
    private static void spreadHalf(RenderTarget main) {
        try {
            if (spread == null) spread = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_bloom", DefaultVertexFormat.POSITION);
            spread.setSampler("SrcSampler", hazeHalf.getColorTextureId());
            spread.setSampler("BeforeSampler", hazeHalf.getColorTextureId());
            spread.safeGetUniform("Mode").set(3f);
            spread.safeGetUniform("SrcTexel").set(1f / hazeHalf.width, 1f / hazeHalf.height);
            spread.safeGetUniform("OutSize").set((float) main.width, (float) main.height);
            spread.safeGetUniform("Strength").set(1f);
            RenderSystem.setShader(() -> spread);
            forceBlend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            bb.vertex(-1f, -1f, 0f).endVertex();
            bb.vertex(1f, -1f, 0f).endVertex();
            bb.vertex(1f, 1f, 0f).endVertex();
            bb.vertex(-1f, 1f, 0f).endVertex();
            BufferUploader.drawWithShader(bb.end());
            halfFrames++;
        } catch (Throwable t) {
            halfFailed = true;                                   // driver / shader trouble: full resolution from the next frame
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: half-resolution haze turned off ({})", t.toString());
        }
    }

    private static final com.mojang.blaze3d.shaders.BlendMode OPAQUE = new com.mojang.blaze3d.shaders.BlendMode();

    /**
     * Rev. 15b: puts the game's blend tracking back in the state the vanilla code expects. A core shader applies the blending of its
     * json and the game remembers the last one; vanilla shaders are all "opaque", so it only ever changes when a mod shader with its
     * own blending is applied. CSC's light shaders have one (multiply, add, screen): the next vanilla shader applied then switched
     * blending OFF, whatever the code drawing with it had asked for. When that next shader was the one of the vignette of the HUD
     * (it relies on blending being on), the vignette was drawn as an opaque grey picture over the whole screen — the grey screen
     * at ground level of the 9 November video (the vignette is stronger where the player stands in less light). Called after each
     * CSC light pass, it re-applies an opaque blend once, while nothing is drawing.
     */
    /**
     * Rev. 16e — the black areas of the 9 November (21 h) captures: rectangles and squares the size of a beam in the haze, of the
     * light on a surface or of a lens halo, black with the light inside. They are what these passes give when they are drawn WITHOUT
     * blending: the pass replaces the picture instead of adding to it. The game keeps a copy of the blend state and skips a call that
     * matches it; when the real state was changed behind its back (by another renderer, a driver path, the chunk renderer of a
     * performance mod) "enable blending" was skipped. Here the state is set for real before each pass: disable then enable (the copy
     * cannot skip both), the equation and the factors sent straight to OpenGL with the same values as the copy, and the json blend
     * tracking of the shaders put back to opaque so that the shader of the pass cannot switch blending off when it is applied.
     */
    public static void forceBlend(GlStateManager.SourceFactor src, GlStateManager.DestFactor dst) {
        resetBlend();
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(32774);   // GL_FUNC_ADD
        RenderSystem.blendFunc(src, dst);
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(src.value, dst.value, src.value, dst.value);
        org.lwjgl.opengl.GL11.glColorMask(true, true, true, true);
    }

    /**
     * Rev. 16f — the real cause of the black squares (9 November, 21 h and 22 h): the light render types of the fixtures (halos,
     * emitters, blooms, streaks) draw with the game's "eyes" shader, whose json carries its own blending (src alpha, one minus src
     * alpha). The game applies a shader's json blending only when it differs from the last one it applied; then it REPLACES the
     * blending the render type has just set. The halo texture is opaque (alpha 1 everywhere, black outside the glow): drawn with
     * src alpha / one minus src alpha, every halo replaced the picture by its black square. It happened on the first light draw
     * after an opaque shader (rev. 16d: the surface pass became empty, a halo came first; rev. 16e made it every draw).
     * Here the game's record is set to the eyes blending first (so the eyes shader skips its own), then the blending the light
     * needs is set.
     */
    private static final com.mojang.blaze3d.shaders.BlendMode EYES = new com.mojang.blaze3d.shaders.BlendMode(770, 771, 32774);

    public static void lightTypeBlend(GlStateManager.SourceFactor src, GlStateManager.DestFactor dst) {
        OPAQUE.apply();          // the record: opaque (whatever it was)
        EYES.apply();            // then the eyes blending: recorded, so the eyes shader will not apply it again
        RenderSystem.disableBlend();
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(32774);   // GL_FUNC_ADD
        RenderSystem.blendFunc(src, dst);
    }

    public static void resetBlend() {
        OPAQUE.apply();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void restore() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** For the F3 screen. */
    static String stats() {
        if (failed) return "CSC stage lights: turned off after a shader error (see the log)";
        if (status != null) return "CSC stage lights: " + status;
        return "CSC stage lights: " + lastLights + " lights, projectors " + lastProjected + " drawn / " + lastRequested + " asked, beams in haze "
            + (volumeFailed ? "off (shader error)" : lastVolumes + " / " + lastVolumesAsked) + ", " + BeamShadows.stats()
            + ", spill " + (spillFailed ? "off (error)" : spillLastFrame) + ", " + Bloom.stats()
            + ", haze " + (halfFailed ? "full (half off: error)" : halfRes(LightTargets.main()) ? "half res" : "full res")
            + ", light target " + (floatAccumulation() ? "16F" : "8 bit");
    }

    private static String status = "not started";
}
