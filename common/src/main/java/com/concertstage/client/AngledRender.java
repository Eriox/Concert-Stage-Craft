package com.concertstage.client;

import com.concertstage.angle.AngledPiece;
import com.concertstage.block.MultiPartBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws trusses placed at an angle: the normal block model of the truss (authored orientation, main cell), moved to the pivot of the piece
 * and turned about the vertical axis. The pose stack is at the corner of the cell that holds the pieces. See AngleMath for the conventions
 * (the same maths is checked in tools/AngleCheck.java).
 */
public final class AngledRender {
    private AngledRender() {}

    public static void renderPieces(List<AngledPiece> pieces, PoseStack pose, MultiBufferSource buffers, int light) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (AngledPiece piece : pieces) {
            MultiPartBlock truss = piece.truss();
            if (truss == null) continue;
            BlockState state = truss.defaultBlockState();          // facing north = authored orientation, main cell
            pose.pushPose();
            pose.translate(piece.x, piece.y, piece.z);               // the pivot: centre of the first cell
            pose.mulPose(Axis.YP.rotationDegrees(-piece.angle));      // clockwise seen from above
            pose.translate(-0.5, 0.0, -0.5);                         // the model starts at the corner of its first cell
            dispatcher.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            // 1.2.0 beta 2b: a barrier whose model is split over its cells (AluGuard corner, site fence): every other cell too
            if (com.concertstage.angle.AnglePlacement.barrier(truss)) {
                int sx = truss.sizeX(), sy = truss.sizeY(), n = truss.cellCount(), sz = Math.max(1, n / Math.max(1, sx * sy));
                for (int idx = 1; idx < n; idx++) {
                    int lx = idx % sx, ly = idx / (sx * sz), lz = (idx / sx) % sz;
                    pose.pushPose();
                    pose.translate(lx, ly, lz);
                    dispatcher.renderSingleBlock(state.setValue(MultiPartBlock.PART, idx), pose, buffers, light, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
            pose.popPose();
        }
    }
}
