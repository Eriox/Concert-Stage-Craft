package com.concertstage.client.vision;

import com.concertstage.client.ClientCulling;
import com.concertstage.client.RenderQuality;
import com.concertstage.vision.LedPanelBlockEntity;
import com.concertstage.vision.VisionBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * CSC 1.3.0 - LED PANEL P1 (1 x 1 block, the back of the cabinet against the back of its block) and its accessories, from the Blender
 * files: the hanging bar on the top mounting points, the ground base under the bottom ones, the wall bracket on the four M10 inserts
 * of the back. The LED surface itself is drawn by {@link LedWalls} as part of the wall's picture; a panel the client knows no wall
 * for (registry not received yet) shows the modelled LED face instead.
 */
public class LedPanelRenderer implements BlockEntityRenderer<LedPanelBlockEntity> {
    /** Model origin (cabinet centre line) from the block centre, towards the back (+Z when facing north). */
    static final float BACK = 0.422f;

    public LedPanelRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LedPanelBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        AABB box = new AABB(be.getBlockPos()).inflate(be.mounts() == 0 ? 0.1 : 1.2);
        com.concertstage.vision.WallData.Wall ww = be.wallId() == 0 ? null : VisionClient.WALLS.get(be.wallId());
        if (ww != null && ww.angle != 0f)
            box = box.inflate(Math.max(ww.width(), 2) + 1, 0, Math.max(ww.width(), 2) + 1);   // it may be drawn away from its block
        if (!ClientCulling.visible(be, box)) return;
        VisionModel m = VisionModel.get("led_panel_p1");
        if (m == null) return;
        double dist = ClientCulling.distance(box);
        int lod = VisionSettings.modelLod(dist, 1.0);
        Direction f = be.getBlockState().hasProperty(VisionBlock.FACING) ? be.getBlockState().getValue(VisionBlock.FACING) : Direction.NORTH;
        boolean wallKnown = be.wallId() != 0 && VisionClient.WALLS.containsKey(be.wallId());
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // rev. 1.3.0-3: a wall at an angle — the panel moves and turns with its wall, around the wall's axis
        com.concertstage.vision.WallData.Wall wall = wallKnown ? VisionClient.WALLS.get(be.wallId()) : null;
        if (wall != null && wall.angle != 0f) {
            var bp = be.getBlockPos();
            double[] t = wall.turn(bp.getX() + 0.5, bp.getZ() + 0.5);
            pose.translate(t[0] - (bp.getX() + 0.5), 0, t[1] - (bp.getZ() + 0.5));
            pose.mulPose(Axis.YP.rotationDegrees(wall.angle));
        }
        pose.mulPose(Axis.YP.rotationDegrees(CameraRenderer.yaw(f)));
        pose.translate(0, 0, BACK);
        m.draw(pose, buffers, lod, light, VisionModel.REST, wallKnown);
        int mounts = be.mounts();
        if (mounts != 0) {
            VisionModel acc = VisionModel.get("led_accessories");
            if (acc != null) {
                int alod = VisionSettings.modelLod(dist, 2.0);
                if ((mounts & LedPanelBlockEntity.MOUNT_HANG) != 0) {
                    pose.pushPose();
                    pose.translate(0, 1.0, 0);                         // bar mounting points (y 0) on the panel's top points (y 1.0)
                    acc.drawPart(pose, buffers, "HANG_BAR", alod, light);
                    pose.popPose();
                }
                if ((mounts & LedPanelBlockEntity.MOUNT_GROUND) != 0) {
                    pose.pushPose();
                    pose.translate(0, -0.08, 0);                       // base points (y 0.08) under the panel's bottom points (y 0)
                    acc.drawPart(pose, buffers, "GROUND_BASE", alod, light);
                    pose.popPose();
                }
                if ((mounts & LedPanelBlockEntity.MOUNT_WALL) != 0) {
                    pose.pushPose();
                    pose.translate(0, 0, 0.042);                       // bracket face (z -0.006) on the M10 inserts (z 0.036)
                    acc.drawPart(pose, buffers, "WALL_MOUNT", alod, light);
                    pose.popPose();
                }
            }
        }
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(160);
    }
}
