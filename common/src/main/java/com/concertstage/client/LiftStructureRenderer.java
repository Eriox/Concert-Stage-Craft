package com.concertstage.client;

import com.concertstage.StageContent;
import com.concertstage.block.AngledHolderBlockEntity;
import com.concertstage.lift.LiftStructureEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;

/**
 * Draws the blocks of the structure that is being lifted, each one with its own block model, at the entity's (interpolated) position.
 * 1.1.0: the block models and the angled trusses of the structure are built once into a mesh kept in the graphics card (see GpuMesh)
 * instead of being sent again at every frame (a stage roof of thousands of blocks used to cost a large part of the frame); the mesh is
 * rebuilt when the structure changes and every few blocks of travel (for the light). Blocks with their own renderer (lights, tarps,
 * speakers...) and translucent blocks are still drawn every frame.
 */
public class LiftStructureRenderer extends EntityRenderer<LiftStructureEntity> {
    private final MovingBlockEntityRenderer blockEntities = new MovingBlockEntityRenderer();

    /** Mesh of one travelling structure: built for this revision of its blocks at this height. */
    private static final class Baked {
        int revision = Integer.MIN_VALUE, blockY = Integer.MIN_VALUE;
        GpuMesh mesh;
        boolean uncacheable;
        /** Indices drawn every frame (own renderer, translucent). */
        int[] live = new int[0];
    }

    private static final Map<LiftStructureEntity, Baked> BAKED = new WeakHashMap<>();

    public LiftStructureRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LiftStructureEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        long t0 = System.nanoTime();
        try {
            renderTimed(entity, partial, pose, buffers);
        } finally {
            RenderStats.add("Lifted structure", System.nanoTime() - t0);
        }
    }

    private void renderTimed(LiftStructureEntity entity, float partial, PoseStack pose, MultiBufferSource buffers) {
        var entries = entity.entries();
        if (entries.isEmpty()) return;
        if (GpuMesh.enabled()) {
            Baked b = BAKED.computeIfAbsent(entity, e -> new Baked());
            int y = (int) Math.floor(entity.getY());
            int step = entries.size() > 30000 ? 16 : entries.size() > 8000 ? 8 : 4;           // the light is taken again every few blocks
            if (b.revision != entity.revision() || !b.uncacheable && (Math.abs(y - b.blockY) >= step || !GpuMesh.usable(b.mesh))) bake(entity, b, y);
            if (!b.uncacheable && b.mesh != null) {
                b.mesh.draw(pose);
                for (int i : b.live) drawOne(entity, i, partial, pose, buffers);
                return;
            }
        }
        for (int i = 0; i < entries.size(); i++) drawOne(entity, i, partial, pose, buffers);
    }

    /** Builds the mesh of everything that does not need drawing every frame. */
    private static void bake(LiftStructureEntity entity, Baked b, int y) {
        if (b.mesh != null) b.mesh.free();
        b.mesh = null;
        b.revision = entity.revision();
        b.blockY = y;
        var entries = entity.entries();
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        Level level = entity.level();
        GpuMesh.Builder builder = GpuMesh.builder();
        MultiBufferSource source = builder.source();
        List<Integer> live = new ArrayList<>();
        PoseStack local = new PoseStack();
        for (int i = 0; i < entries.size(); i++) {
            LiftStructureEntity.Entry e = entries.get(i);
            if (e.renderNbt != null || e.state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED
                || (e.state.getBlock() != StageContent.ANGLED_HOLDER && ItemBlockRenderTypes.getChunkRenderType(e.state) == RenderType.translucent())) {
                live.add(i);
                continue;
            }
            if (entity.isHidden(i)) continue;
            local.pushPose();
            local.translate(e.rel.getX(), e.rel.getY(), e.rel.getZ());
            int blockLight = LevelRenderer.getLightColor(level, at(entity, e));
            if (e.state.getBlock() == StageContent.ANGLED_HOLDER) {
                if (e.pieces == null) e.pieces = e.nbt != null ? AngledHolderBlockEntity.readPieces(e.nbt) : List.of();
                AngledRender.renderPieces(e.pieces, local, source, blockLight);
            } else if (e.state.getRenderShape() == RenderShape.MODEL) {
                dispatcher.renderSingleBlock(e.state, local, source, blockLight, OverlayTexture.NO_OVERLAY);
            }
            local.popPose();
        }
        b.live = live.stream().mapToInt(Integer::intValue).toArray();
        b.mesh = builder.build();
        b.uncacheable = b.mesh == null;
    }

    private static BlockPos at(LiftStructureEntity entity, LiftStructureEntity.Entry e) {
        return BlockPos.containing(entity.getX() + e.rel.getX() + 0.5, entity.getY() + e.rel.getY() + 0.5, entity.getZ() + e.rel.getZ() + 0.5);
    }

    /** One block, the old way (every frame). */
    private void drawOne(LiftStructureEntity entity, int i, float partial, PoseStack pose, MultiBufferSource buffers) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var level = entity.level();
        LiftStructureEntity.Entry e = entity.entries().get(i);
        if (entity.isHidden(i) && e.renderNbt == null) return; // keep special renderers even if the base cell is opaque
        pose.pushPose();
        try {
            pose.translate(e.rel.getX(), e.rel.getY(), e.rel.getZ());
            BlockPos at = at(entity, e);
            int blockLight = LevelRenderer.getLightColor(level, at);
            if (e.state.getBlock() == StageContent.ANGLED_HOLDER) {
                // trusses placed at an angle: the cell is invisible, its pieces are in the saved data of the block entity
                if (e.pieces == null) e.pieces = e.nbt != null ? AngledHolderBlockEntity.readPieces(e.nbt) : java.util.List.of();
                AngledRender.renderPieces(e.pieces, pose, buffers, blockLight);
            } else {
                // ENTITYBLOCK_ANIMATED would use the item's default renderer here
                // (e.g. a chest), duplicating the saved block entity rendered below.
                if (e.state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                    if (!blockEntities.render(e, level, at, partial, pose, buffers, blockLight)) {
                        dispatcher.renderSingleBlock(e.state, pose, buffers, blockLight, OverlayTexture.NO_OVERLAY);
                    }
                } else {
                    dispatcher.renderSingleBlock(e.state, pose, buffers, blockLight, OverlayTexture.NO_OVERLAY);
                    // a lifted tarp draws its sheet down to the tie points that stay in the world: it needs its exact height
                    TarpSheetRenderer.liftY = net.minecraft.util.Mth.lerp(partial, entity.yo, entity.getY()) + e.rel.getY();
                    try {
                        blockEntities.render(e, level, at, partial, pose, buffers, blockLight);
                    } finally {
                        TarpSheetRenderer.liftY = Double.NaN;
                    }
                }
            }
        } finally {
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(LiftStructureEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
