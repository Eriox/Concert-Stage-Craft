package com.rackworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Compile-only shape of RackWorks' CableRenderer. */
public final class CableRenderer {
    private CableRenderer() {}
    public static void draw(BlockEntity be, PoseStack pose, MultiBufferSource buffers) {}
}