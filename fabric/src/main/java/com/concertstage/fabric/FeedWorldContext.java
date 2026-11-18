package com.concertstage.fabric;

import com.concertstage.client.vision.CameraFeeds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;

/**
 * 1.2.1 — the world-render context of a camera image (Fabric): the lens's pose, projection, camera and frustum, handed to the mods
 * listening to {@link WorldRenderEvents} so their beams and effects are drawn into the image too.
 */
final class FeedWorldContext implements WorldRenderContext {
    private final PoseStack pose;
    private final Matrix4f projection;
    private final Camera camera;
    private final Frustum frustum;
    private final float partial;

    private FeedWorldContext(PoseStack pose, Matrix4f projection, Camera camera, Frustum frustum, float partial) {
        this.pose = pose; this.projection = projection; this.camera = camera; this.frustum = frustum; this.partial = partial;
    }

    static void fire(int stage, PoseStack pose, Matrix4f projection, Camera camera, Frustum frustum, float partial, int ticks) {
        FeedWorldContext c = new FeedWorldContext(pose, projection, camera, frustum, partial);
        switch (stage) {
            case CameraFeeds.AFTER_ENTITIES -> WorldRenderEvents.AFTER_ENTITIES.invoker().afterEntities(c);
            case CameraFeeds.AFTER_TRANSLUCENT -> WorldRenderEvents.AFTER_TRANSLUCENT.invoker().afterTranslucent(c);
            case CameraFeeds.AFTER_PARTICLES -> WorldRenderEvents.LAST.invoker().onLast(c);
            default -> { }
        }
    }

    @Override public LevelRenderer worldRenderer() { return Minecraft.getInstance().levelRenderer; }
    @Override public PoseStack matrixStack() { return pose; }
    @Override public float tickDelta() { return partial; }
    @Override public long limitTime() { return 0L; }
    @Override public boolean blockOutlines() { return false; }
    @Override public Camera camera() { return camera; }
    @Override public GameRenderer gameRenderer() { return Minecraft.getInstance().gameRenderer; }
    @Override public LightTexture lightmapTextureManager() { return Minecraft.getInstance().gameRenderer.lightTexture(); }
    @Override public Matrix4f projectionMatrix() { return projection; }
    @Override public ClientLevel world() { return Minecraft.getInstance().level; }
    @Override public ProfilerFiller profiler() { return Minecraft.getInstance().getProfiler(); }
    @Override public boolean advancedTranslucency() { return false; }
    @Override public MultiBufferSource consumers() { return Minecraft.getInstance().renderBuffers().bufferSource(); }
    @Override public Frustum frustum() { return frustum; }
}
