package com.concertstage.client.vision;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

/**
 * CSC 1.3.0 - a plain RGBA texture of LiveVision (test patterns, images, video frames), uploaded on the render thread. Binding goes
 * through {@link RenderSystem#bindTexture} so the game's record of the bound texture stays right; the upload itself is plain GL.
 */
public final class VTex {
    public final int w, h;
    private int id = -1;

    public VTex(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public int id() { return id; }

    /** Uploads {@code rgba} (w*h*4 bytes, first row = top of the image). */
    public void upload(ByteBuffer rgba) {
        RenderSystem.assertOnRenderThread();
        boolean create = id < 0;
        if (create) id = TextureUtil.generateTextureId();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(id);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        if (create) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
        }
        RenderSystem.bindTexture(0);
    }

    public void upload(byte[] rgba) {
        ByteBuffer b = MemoryUtil.memAlloc(rgba.length);
        try {
            b.put(rgba).flip();
            upload(b);
        } finally {
            MemoryUtil.memFree(b);
        }
    }

    public void free() {
        if (id >= 0) {
            int i = id;
            id = -1;
            RenderSystem.recordRenderCall(() -> TextureUtil.releaseTextureId(i));
        }
    }

    public long bytes() { return (long) w * h * 4; }
}
