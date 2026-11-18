package com.concertstage.client;

import com.concertstage.angle.AngledPiece;
import com.concertstage.block.AngledHolderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Draws the trusses placed at an angle that a cell holds (1.1.0: kept in the graphics card, see GpuMesh). */
public class AngledHolderRenderer implements BlockEntityRenderer<AngledHolderBlockEntity> {
    private static final GpuMesh.Cache GPU = new GpuMesh.Cache(2048);

    private record Memo(int ids, int light, String key) {}

    private static final java.util.Map<AngledHolderBlockEntity, Memo> MEMO = new java.util.WeakHashMap<>();

    public AngledHolderRenderer(BlockEntityRendererProvider.Context context) {}

    public static void clear() {
        GPU.clear();
    }

    @Override
    public void render(AngledHolderBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!ClientCulling.visible(be, be.getRenderBoundingBox())) return;                  // off screen (Fabric; Forge checks it itself)
        var pieces = be.pieces();
        if (pieces.isEmpty()) return;
        if (GpuMesh.enabled()) {
            Memo memo = MEMO.get(be);                                     // aurex.2: the key is built again only when something changed
            int ids = pieces.size();                                     // the pieces are immutable objects: same objects, same mesh
            for (AngledPiece p : pieces) ids = ids * 31 + System.identityHashCode(p);
            if (memo == null || memo.ids != ids || memo.light != light) MEMO.put(be, memo = new Memo(ids, light, signature(pieces, light)));
            GpuMesh m = GPU.get(memo.key, b -> AngledRender.renderPieces(pieces, new PoseStack(), b.source(), light));
            if (m != null) {
                m.draw(pose);
                return;
            }
        }
        AngledRender.renderPieces(pieces, pose, buffers, light);
    }

    /** Same pieces at the same places with the same light: same mesh (cells along a line of angled trusses often share it). */
    static String signature(java.util.List<AngledPiece> pieces, int light) {
        StringBuilder sb = new StringBuilder(32 * pieces.size() + 12).append(light);
        for (AngledPiece p : pieces) {
            sb.append(';').append(p.block)
                .append(',').append(p.x).append(',').append(p.y).append(',').append(p.z).append(',').append(p.angle);
        }
        return sb.toString();
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(128);
    }

    @Override
    public boolean shouldRenderOffScreen(AngledHolderBlockEntity be) {
        return true;
    }
}
