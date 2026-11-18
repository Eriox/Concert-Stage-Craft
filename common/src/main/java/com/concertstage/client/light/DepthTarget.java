package com.concertstage.client.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Rev. 21: a square depth-only render target for a beam shadow map. The game's TextureTarget always carries a colour buffer the shadow
 * maps never use (as much memory again as the depth): with one map per projection (up to 96) and maps of 384² at ultra, a depth-only
 * target keeps the cost at 4 bytes per texel. Read with nearest filtering, no comparison mode (the shaders compare the linear
 * distances themselves, concertstage_light_projector / concertstage_beam_volume).
 */
final class DepthTarget {
    final int size;
    final int texture;
    private final int fbo;

    DepthTarget(int size) {
        RenderSystem.assertOnRenderThreadOrInit();
        this.size = size;
        texture = GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);      // GL_CLAMP_TO_EDGE
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, 34892, 0);                           // GL_TEXTURE_COMPARE_MODE: none
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, 33190, size, size, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null);  // GL_DEPTH_COMPONENT24
        fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            destroy();
            throw new IllegalStateException("shadow map target incomplete: " + status);
        }
    }

    /** Binds the target for drawing (viewport of the map) and clears its depth to the far plane. */
    void bindAndClear() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, size, size);
        RenderSystem.depthMask(true);
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);
    }

    void destroy() {
        try { GlStateManager._glDeleteFramebuffers(fbo); } catch (Throwable ignored) { }
        try { GlStateManager._deleteTexture(texture); } catch (Throwable ignored) { }
    }
}
