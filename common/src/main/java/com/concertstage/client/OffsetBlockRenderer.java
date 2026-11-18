package com.concertstage.client;

import com.concertstage.block.offset.OffsetBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Draws the block held by an offset cell, moved down onto the stage floor. */
public class OffsetBlockRenderer implements BlockEntityRenderer<OffsetBlockEntity> {
    private static final java.util.Set<OffsetBlockEntity> failed = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public OffsetBlockRenderer(BlockEntityRendererProvider.Context context) {}

    /** A lowered block stays inside its cell and the one under it: normal culling (no off-screen rendering cost). */
    @Override
    public boolean shouldRenderOffScreen(OffsetBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return Math.max(32, RenderQuality.distance() / 2);           // small pieces: half the render distance
    }

    @Override
    public void render(OffsetBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.stored().isAir()) return;
        pose.pushPose();
        pose.translate(0, -be.drop(), 0);
        if (be.stored().getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL)
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(be.stored(), pose, buffers, light, overlay);
        net.minecraft.world.level.block.entity.BlockEntity held = be.heldView();
        if (held != null && !failed.contains(be)) {
            try {
                var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(held);
                if (renderer != null) renderer.render(held, partial, pose, buffers, light, overlay);
            } catch (RuntimeException e) {
                failed.add(be);                     // a renderer that needs the real block in the world: the baked model only
            }
        }
        pose.popPose();
    }
}
