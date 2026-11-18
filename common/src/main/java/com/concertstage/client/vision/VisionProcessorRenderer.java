package com.concertstage.client.vision;

import com.concertstage.client.ClientCulling;
import com.concertstage.client.RenderQuality;
import com.concertstage.vision.VisionBlock;
import com.concertstage.vision.VisionProcessorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** CSC 1.3.0 - VISION PROCESSOR VP1 (half-rack 1U unit, front panel with its status screen and LEDs) from the Blender file. */
public class VisionProcessorRenderer implements BlockEntityRenderer<VisionProcessorBlockEntity> {
    public VisionProcessorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(VisionProcessorBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        AABB box = new AABB(be.getBlockPos());
        if (!ClientCulling.visible(be, box)) return;
        VisionModel m = VisionModel.get("vision_processor_vp1");
        if (m == null) return;
        int lod = VisionSettings.modelLod(ClientCulling.distance(box), 0.5);
        Direction f = be.getBlockState().hasProperty(VisionBlock.FACING) ? be.getBlockState().getValue(VisionBlock.FACING) : Direction.NORTH;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(CameraRenderer.yaw(f)));
        m.draw(pose, buffers, lod, light, VisionModel.REST, false);
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(64);
    }
}
