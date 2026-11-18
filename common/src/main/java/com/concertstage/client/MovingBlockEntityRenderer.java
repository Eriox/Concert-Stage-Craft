package com.concertstage.client;

import com.concertstage.lift.LiftStructureEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Client-only, unregistered block entities for models drawn by a BER instead of a baked block model. */
final class MovingBlockEntityRenderer {
    private final Map<LiftStructureEntity.Entry, Cached> cache = new WeakHashMap<>();

    private static final class Cached {
        BlockEntity blockEntity;
        boolean failed;
    }

    boolean render(LiftStructureEntity.Entry entry, Level level, BlockPos pos, float partial,
                PoseStack pose, MultiBufferSource buffers, int light) {
        if (entry.renderNbt == null) return false;
        Cached cached = cache.computeIfAbsent(entry, key -> new Cached());
        if (cached.failed) return false;
        pose.pushPose();
        try {
            BlockEntity be = cached.blockEntity;
            if (be == null || !be.getBlockPos().equals(pos) || be.getLevel() != level) {
                be = BlockEntity.loadStatic(pos, entry.state, entry.renderNbt.copy());
                cached.blockEntity = be;
                if (be == null) return false;
                be.setLevel(level);
            }
            BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
            if (renderer != null) {
                // The world cell is air during the lift. The dispatcher.render() path
                // checks that cell, so call the registered renderer with our saved state.
                // The caller already translated the pose to this moving block.
                renderer.render(be, partial, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                return true;
            }
            return false;
        } catch (RuntimeException ex) {
            // A third-party BER may require a real world block or a neighbour BE.
            // Keep drawing the rest of the rig, and log this incompatible renderer once.
            cached.failed = true;
            LogUtils.getLogger().warn("Cannot render lifted block entity {} at {}", entry.state, pos, ex);
            return false;
        } finally {
            pose.popPose();
        }
    }
}
