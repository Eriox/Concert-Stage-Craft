package com.concertstage.client.light;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Rev. 25 (perf) — many vertex buffers with one shader: the program, the projection and the texture are set once, then each buffer
 * only uploads its model-view matrix and draws. VertexBuffer#drawWithShader sets every uniform and sampler of the shader for each
 * call; for the shadow maps (hundreds of sections and structure meshes a frame) that was most of the processor time of the mod.
 */
public final class FastDraw {
    private static ShaderInstance current;

    private FastDraw() {}

    public static void begin(ShaderInstance shader, Matrix4f proj, ResourceLocation texture) {
        current = shader;
        RenderSystem.setShaderTexture(0, texture);
        shader.setSampler("Sampler0", RenderSystem.getShaderTexture(0));
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(proj);
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(new Matrix4f());
        shader.apply();
    }

    /** Binds another texture on unit 0 (the shader's Sampler0) without setting the shader up again. */
    public static void texture(ResourceLocation texture) {
        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(texture).getId());
    }

    public static void draw(VertexBuffer vb, Matrix4f mv) {
        if (current.MODEL_VIEW_MATRIX != null) {
            current.MODEL_VIEW_MATRIX.set(mv);
            current.MODEL_VIEW_MATRIX.upload();
        }
        vb.bind();
        vb.draw();
    }

    public static void end() {
        if (current != null) current.clear();
        current = null;
    }
}
