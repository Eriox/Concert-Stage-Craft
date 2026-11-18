package com.concertstage.client.light;

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

/**
 * Rev. 21 — bloom of the light of the fixtures (asked on 11 November: "ajoute du bloom").
 *
 * <p>Only what the fixtures add to the picture blooms: the frame is copied just before the beams, the lenses, the cells and the flashes
 * are drawn (GpuMesh#drawBeams), and the source of the bloom is what they added (after − before). A white wall in daylight, the sky or
 * snow never glow; a beam core, a lit lens, a strobe flash do, in their colour. The source is taken at half size and blurred down a
 * chain of 5 smaller targets and back up (dual filter: 5 taps down, 8 taps up), then added to the frame. About 11 small full-screen
 * passes, independent of the number of fixtures.
 *
 * <p>Strength: LightClient#bloom (desk, "Bloom"; 0 = off). If the shader does not compile or a target cannot be made, bloom turns
 * itself off for the session and nothing else changes.
 */
public final class Bloom {
    private static final int LEVELS = 5;
    private static final TextureTarget[] MIPS = new TextureTarget[LEVELS];
    private static TextureTarget before;
    private static ShaderInstance shader, shaderAdd;
    private static boolean failed, armed;
    private static long frame = -1;
    static int lastPasses;

    private Bloom() {}

    // rev. 3b: the cameras' own targets (LightTargets)
    private static final TextureTarget[] F_MIPS = new TextureTarget[LEVELS];
    private static TextureTarget fBefore;
    private static boolean fArmed;
    private static long fFrame = -1;

    static void swapFeed() {
        for (int i = 0; i < LEVELS; i++) { TextureTarget t = MIPS[i]; MIPS[i] = F_MIPS[i]; F_MIPS[i] = t; }
        TextureTarget t = before; before = fBefore; fBefore = t;
        boolean a = armed; armed = fArmed; fArmed = a;
        long fr = frame; frame = fFrame; fFrame = fr;
    }

    /** Before the light of the fixtures is drawn: keeps the frame as it is. Returns true when {@link #apply()} must follow. */
    public static boolean begin() {
        armed = false;
        if (failed || LightClient.bloom < 0.01f || LightDiagnostics.mode != LightDiagnostics.OFF || !VolumeLights.enabled()) return false;
        // rev. 22: once per frame drawn. It used the lighting frame counter, which moved once per game tick: at 120 FPS the bloom was
        // on one frame in six and off the five others — every lit fixture seemed to strobe (author, 11 November, 21 h 49)
        long f = com.concertstage.client.GpuMesh.frameSerial;
        if (f == frame && !LightTargets.feed()) return false;          // rev. 3b: a camera image blooms on its own
        try {
            RenderTarget main = LightTargets.main();
            before = fit(before, main.width, main.height, false);
            GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, before.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
            GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
            main.bindWrite(false);
            frame = f;
            armed = true;
        } catch (Throwable t) {
            fail(t);
        }
        return armed;
    }

    /** After the light of the fixtures: blooms what it added. */
    public static void apply() {
        if (!armed || failed) return;
        armed = false;
        RenderTarget main = LightTargets.main();
        try {
            if (shader == null) shader = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_bloom", DefaultVertexFormat.POSITION);
            // the blending of a pass is the one of its shader's json (the game applies it when the shader is bound): one + one here
            if (shaderAdd == null) shaderAdd = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "concertstage_bloom_add", DefaultVertexFormat.POSITION);
            int w = Math.max(1, main.width / 2), h = Math.max(1, main.height / 2);
            for (int i = 0; i < LEVELS; i++) {
                MIPS[i] = fit(MIPS[i], w, h, true);
                w = Math.max(1, w / 2);
                h = Math.max(1, h / 2);
            }
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            int passes = 0;
            // 1. source: what the fixtures added, at half size
            pass(MIPS[0], 0, main.getColorTextureId(), main.width, main.height, false);
            passes++;
            // 2. down
            for (int i = 1; i < LEVELS; i++) { pass(MIPS[i], 1, MIPS[i - 1].getColorTextureId(), MIPS[i - 1].width, MIPS[i - 1].height, false); passes++; }
            // 3. up, each level added onto the one above
            for (int i = LEVELS - 2; i >= 0; i--) { pass(MIPS[i], 2, MIPS[i + 1].getColorTextureId(), MIPS[i + 1].width, MIPS[i + 1].height, true); passes++; }
            // 4. onto the frame
            main.bindWrite(true);
            shaderAdd.safeGetUniform("Strength").set(0.32f * LightClient.bloom);
            draw(3, MIPS[0].getColorTextureId(), MIPS[0].width, MIPS[0].height, main.width, main.height, true);
            passes++;
            lastPasses = passes;
        } catch (Throwable t) {
            fail(t);
        } finally {
            VolumeLights.resetBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            main.bindWrite(true);
        }
    }

    private static void pass(TextureTarget out, int mode, int src, int sw, int sh, boolean add) {
        out.bindWrite(true);
        draw(mode, src, sw, sh, out.width, out.height, add);
    }

    private static void draw(int mode, int src, int sw, int sh, int ow, int oh, boolean add) {
        ShaderInstance prog = add ? shaderAdd : shader;
        prog.setSampler("SrcSampler", src);
        prog.setSampler("BeforeSampler", before.getColorTextureId());
        prog.safeGetUniform("Mode").set((float) mode);
        prog.safeGetUniform("SrcTexel").set(1f / sw, 1f / sh);
        prog.safeGetUniform("OutSize").set((float) ow, (float) oh);
        RenderSystem.setShader(() -> prog);
        if (add) VolumeLights.forceBlend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        else RenderSystem.disableBlend();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1f, -1f, 0f).endVertex();
        bb.vertex(1f, -1f, 0f).endVertex();
        bb.vertex(1f, 1f, 0f).endVertex();
        bb.vertex(-1f, 1f, 0f).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static TextureTarget fit(TextureTarget t, int w, int h, boolean linear) {
        if (t == null) {
            t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            t.setClearColor(0f, 0f, 0f, 0f);
            if (linear) t.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        } else if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
            if (linear) t.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        }
        return t;
    }

    private static void fail(Throwable t) {
        failed = true;
        armed = false;
        com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: bloom turned off (shader or driver error)", t);
        try { LightTargets.main().bindWrite(true); } catch (Throwable ignored) { }
    }

    static String stats() {
        return failed ? "bloom off (error)" : LightClient.bloom < 0.01f ? "bloom off" : "bloom " + lastPasses + " passes";
    }
}
