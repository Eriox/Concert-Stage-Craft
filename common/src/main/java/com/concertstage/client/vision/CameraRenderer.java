package com.concertstage.client.vision;

import com.concertstage.client.ClientCulling;
import com.concertstage.client.RenderQuality;
import com.concertstage.vision.CameraBlockEntity;
import com.concertstage.vision.CameraKind;
import com.concertstage.vision.VisionBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * CSC 1.3.0 - the four cameras (PTZ P1, Broadcast B1, Crane C1, Remote R1) from their Blender models, every moving part turning about
 * its real pivot (pan, tilt, crane boom with a levelled head, head pan / tilt, optical block moving with the zoom). Hanging cameras
 * (PTZ, Remote under a truss) are drawn upside down from the top of their block, like a real inverted mount.
 */
public class CameraRenderer implements BlockEntityRenderer<CameraBlockEntity> {
    public CameraRenderer(BlockEntityRendererProvider.Context ctx) {}

    /** Turn of the model for a facing: the model looks to -Z (north). */
    public static float yaw(Direction d) {
        return switch (d) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    /** Model frame -> block-local frame (origin at the block's corner): centre of the floor, facing, hanging flip. */
    public static Matrix4f blockMatrix(CameraBlockEntity be) {
        BlockState s = be.getBlockState();
        Direction f = s.hasProperty(VisionBlock.FACING) ? s.getValue(VisionBlock.FACING) : Direction.NORTH;
        Matrix4f m = new Matrix4f().translate(0.5f, 0f, 0.5f).rotate(Axis.YP.rotationDegrees(yaw(f)));
        if (be.hanging()) m.translate(0f, 1f, 0f).rotate(Axis.ZP.rotationDegrees(180f));
        return m;
    }

    /** Pose of the moving parts at this game time. */
    public static VisionModel.Poser poser(CameraBlockEntity be, double time) {
        float pan = be.axis(0, time), tilt = be.axis(1, time), zoom = be.axis(2, time), boom = be.axis(3, time), head = be.axis(4, time);
        CameraKind k = be.kind();
        return switch (k) {
            case BROADCAST -> VisionModel.poser(0, 0, zoom, 0, pan, tilt);
            case CRANE -> VisionModel.poser(pan, 0, zoom, boom, head, tilt);
            default -> VisionModel.poser(pan, tilt, zoom, 0, 0, 0);
        };
    }

    @Override
    public void render(CameraBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        AABB box = be.getRenderBoundingBox();
        if (!ClientCulling.visible(be, box)) return;
        VisionModel m = VisionModel.get(be.kind().model);
        if (m == null) return;
        double dist = ClientCulling.distance(box);
        int lod = VisionSettings.modelLod(dist, m.size);
        double time = be.getLevel().getGameTime() + partial;
        pose.pushPose();
        pose.last().pose().mul(blockMatrix(be));
        pose.last().normal().mul(new org.joml.Matrix3f(blockMatrix(be)));
        m.draw(pose, buffers, lod, light, poser(be, time), false);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CameraBlockEntity be) {
        return be.kind() == CameraKind.CRANE;
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(128);
    }
}
