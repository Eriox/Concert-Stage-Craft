package com.concertstage.client;

import com.concertstage.ConcertStage;
import com.concertstage.block.StageProperties;
import com.concertstage.block.tarp.StageTarpRailBlock;
import com.concertstage.block.tarp.StageTarpRailBlockEntity;
import com.concertstage.block.tarp.TarpColor;
import com.concertstage.block.tarp.TarpProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Stretched part of a stage tarp that leans down to a stage rim: one flat sheet per column, from the bottom of the top row
 * (on the tarp plane) to the deck edge of the stage, with the welded hem along the bottom and along the two ends of the tarp.
 */
public class TarpSheetRenderer implements BlockEntityRenderer<StageTarpRailBlockEntity> {
    private static final double HEM = 2.4 / 16.0;

    public TarpSheetRenderer(BlockEntityRendererProvider.Context context) {}

    /** Sprites looked up once (not two lookups and allocations per tarp and per frame); dropped at a resource reload. */
    private static final java.util.Map<String, TextureAtlasSprite> SPRITES = new java.util.concurrent.ConcurrentHashMap<>();

    private static TextureAtlasSprite sprite(TextureAtlas atlas, String path) {
        return SPRITES.computeIfAbsent(path, k -> atlas.getSprite(new ResourceLocation(ConcertStage.MOD_ID, k)));
    }

    public static void clear() {
        SPRITES.clear();
    }

    /** World height of the rail row while it travels with a lift (set by the lift renderer), NaN otherwise. */
    public static double liftY = Double.NaN;

    @Override
    public void render(StageTarpRailBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        if (be.isLiftSheet()) {
            renderLift(be, pose, buffers, light);
            return;
        }
        if (!be.hasSlant()) { com.concertstage.client.light.LightBlockers.remove(be); return; }
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof StageTarpRailBlock)) return;
        blockLight(be, st);
        if (!ClientCulling.visible(be, be.getRenderBoundingBox())) return;                  // off screen (Fabric; Forge checks it itself)
        Direction dir = st.getValue(StageTarpRailBlock.FACING);
        int shift = st.getValue(StageProperties.SHIFT);
        TarpColor color = st.getValue(TarpProperties.COLOR);
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite cloth = sprite(atlas, "block/stage_tarp_" + color.getSerializedName());
        TextureAtlasSprite hem = sprite(atlas, "block/stage_tarp_hem_" + color.getSerializedName());
        VertexConsumer vc = GpuMesh.cutout(buffers);   // like the tarp blocks (shaders, Shimmer, same shading)
        double plane = StageTarpRailBlockEntity.planeOffset(shift);
        int len = be.builtLength();
        BlockPos pos = be.getBlockPos();
        for (int k = 0; k < len; k++) {
            double drop = be.getSlantDrop(k);
            if (drop <= 0.02) continue;
            double rim = be.getSlantAcross(k);
            BlockPos colPos = pos.relative(dir, k);
            int lit = LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(colPos.getX() + 0.5 + (dir.getAxis() == Direction.Axis.X ? 0 : (plane + rim) / 2),
                pos.getY() - drop / 2, colPos.getZ() + 0.5 + (dir.getAxis() == Direction.Axis.X ? (plane + rim) / 2 : 0)));
            Column c = new Column(dir, k, plane, rim, drop);
            double slope = Math.hypot(drop, rim - plane);
            double hemF = Math.min(1.0, HEM / slope);
            Matrix4f m = pose.last().pose();
            Matrix3f nm = pose.last().normal();
            // sheet, in strips of one block of slope so the texture keeps its scale
            int strips = (int) Math.ceil(slope * (1 - hemF));
            for (int i = 0; i < strips; i++) {
                double f0 = i / slope, f1 = Math.min(1 - hemF, (i + 1) / slope);
                if (f1 <= f0) break;
                c.quad(vc, m, nm, cloth, 0, 1, f0, f1, (float) ((f1 - f0) * slope * 16), lit, 0);
            }
            c.quad(vc, m, nm, hem, 0, 1, 1 - hemF, 1, (float) Math.min(16, HEM * 16), lit, 0.004);   // bottom hem
            boolean startNeg = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            if (k == 0) c.endHem(vc, m, nm, hem, startNeg ? 0 : 1 - HEM, startNeg ? HEM : 1, slope, lit);
            if (k == len - 1) c.endHem(vc, m, nm, hem, startNeg ? 1 - HEM : 0, startNeg ? 1 : HEM, slope, lit);
        }
    }

    /**
     * Rev. 20: the slanted / stretched sheet has no blocks of its own (only its top row): it stops the light of the fixtures through
     * LightBlockers (seen in game on the author's stage: beams aimed at the side tarps went through them). One quad per column.
     */
    /** Rev. 1.3.0-3: game tick of the last light-blocker refresh of each sheet (it was rebuilt every frame, strings and arrays included). */
    private static final java.util.Map<StageTarpRailBlockEntity, Long> BLOCKER_TICK = new java.util.WeakHashMap<>();

    private static void blockLight(StageTarpRailBlockEntity be, BlockState st) {
        long tick = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        Long last = BLOCKER_TICK.get(be);
        if (last != null && tick - last < 10 && tick >= last) return;   // a sheet changes when it is edited: twice a second is enough
        BLOCKER_TICK.put(be, tick);
        Direction dir = st.getValue(StageTarpRailBlock.FACING);
        double plane = StageTarpRailBlockEntity.planeOffset(st.getValue(StageProperties.SHIFT));
        int len = be.builtLength();
        java.util.List<double[]> quads = new java.util.ArrayList<>();
        StringBuilder key = new StringBuilder().append(dir).append(plane).append(':').append(len);
        for (int k = 0; k < len; k++) {
            double drop = be.getSlantDrop(k);
            if (drop <= 0.02) continue;
            double rim = be.getSlantAcross(k);
            key.append(',').append(Math.round(drop * 64)).append('/').append(Math.round(rim * 64));
            Column c = new Column(dir, k, plane, rim, drop);
            double[] a = c.at(0, 0, 0), b = c.at(1, 0, 0), d = c.at(1, 1, 0), e = c.at(0, 1, 0);
            quads.add(new double[] {a[0], a[1], a[2], b[0], b[1], b[2], d[0], d[1], d[2], e[0], e[1], e[2]});
        }
        com.concertstage.client.light.LightBlockers.put(be, be.getBlockPos(), key.toString(), quads.toArray(new double[0][]));
    }

    /** Lifted tarp: every column stretches from the moving top row to its tie point, fixed in the world. */
    private void renderLift(StageTarpRailBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof StageTarpRailBlock)) return;
        Direction dir = st.getValue(StageTarpRailBlock.FACING);
        double plane = StageTarpRailBlockEntity.planeOffset(st.getValue(StageProperties.SHIFT));
        TarpColor color = st.getValue(TarpProperties.COLOR);
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite cloth = sprite(atlas, "block/stage_tarp_" + color.getSerializedName());
        TextureAtlasSprite hem = sprite(atlas, "block/stage_tarp_hem_" + color.getSerializedName());
        VertexConsumer vc = GpuMesh.cutout(buffers);
        double railY = Double.isNaN(liftY) ? be.getBlockPos().getY() : liftY;
        int n = be.sheetColumns();
        for (int k = 0; k < n; k++) {
            double drop = be.sheetDrop(k, railY);
            if (drop <= 0.02) continue;
            Column c = new Column(dir, k, plane, be.sheetAcross(k), drop);
            double slope = Math.hypot(drop, be.sheetAcross(k) - plane);
            double hemF = Math.min(1.0, HEM / slope);
            Matrix4f m = pose.last().pose();
            Matrix3f nm = pose.last().normal();
            int strips = (int) Math.ceil(slope * (1 - hemF));
            for (int i = 0; i < strips; i++) {
                double f0 = i / slope, f1 = Math.min(1 - hemF, (i + 1) / slope);
                if (f1 <= f0) break;
                c.quad(vc, m, nm, cloth, 0, 1, f0, f1, (float) ((f1 - f0) * slope * 16), light, 0);
            }
            c.quad(vc, m, nm, hem, 0, 1, 1 - hemF, 1, (float) Math.min(16, HEM * 16), light, 0.004);
            boolean startNeg = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            if (k == 0) c.endHem(vc, m, nm, hem, startNeg ? 0 : 1 - HEM, startNeg ? HEM : 1, slope, light);
            if (k == n - 1) c.endHem(vc, m, nm, hem, startNeg ? 1 - HEM : 0, startNeg ? 1 : HEM, slope, light);
        }
    }

    /** Geometry of one column: u = 0..1 along the cell (block axis order), f = 0 (top) .. 1 (rim). */
    private record Column(Direction dir, int k, double plane, double rim, double drop) {
        double[] at(double u, double f, double lift) {
            boolean alongX = dir.getAxis() == Direction.Axis.X;
            double base = alongX ? dir.getStepX() * k : dir.getStepZ() * k;
            double along = base + u;
            double across = 0.5 + plane + (rim - plane) * f;
            double y = -drop * f;
            // a little off the sheet (hem in front of the cloth), towards the normal
            double len = Math.hypot(drop, rim - plane);
            double na = drop / len, ny = (rim - plane) / len;
            across += na * lift; y += ny * lift;
            return alongX ? new double[] {along, y, across} : new double[] {across, y, along};
        }

        float[] normal() {
            double len = Math.hypot(drop, rim - plane);
            float na = (float) (drop / len), ny = (float) ((rim - plane) / len);
            return dir.getAxis() == Direction.Axis.X ? new float[] {0, ny, na} : new float[] {na, ny, 0};
        }

        void quad(VertexConsumer vc, Matrix4f m, Matrix3f nm, TextureAtlasSprite sp, double u0, double u1, double f0, double f1,
                  float vLen, int light, double lift) {
            float[] n = normal();
            float uu0 = sp.getU(u0 * 16), uu1 = sp.getU(u1 * 16), v0 = sp.getV(0), v1 = sp.getV(Math.min(16, vLen));
            put(vc, m, nm, at(u0, f0, lift), uu0, v0, light, n);
            put(vc, m, nm, at(u1, f0, lift), uu1, v0, light, n);
            put(vc, m, nm, at(u1, f1, lift), uu1, v1, light, n);
            put(vc, m, nm, at(u0, f1, lift), uu0, v1, light, n);
            float[] back = {-n[0], -n[1], -n[2]};                                  // the other face (block render types cull)
            put(vc, m, nm, at(u0, f1, -lift), uu0, v1, light, back);
            put(vc, m, nm, at(u1, f1, -lift), uu1, v1, light, back);
            put(vc, m, nm, at(u1, f0, -lift), uu1, v0, light, back);
            put(vc, m, nm, at(u0, f0, -lift), uu0, v0, light, back);
        }

        void endHem(VertexConsumer vc, Matrix4f m, Matrix3f nm, TextureAtlasSprite sp, double u0, double u1, double slope, int light) {
            int strips = (int) Math.ceil(slope);
            for (int i = 0; i < strips; i++) {
                double f0 = i / slope, f1 = Math.min(1, (i + 1) / slope);
                quad(vc, m, nm, sp, u0, u1, f0, f1, (float) ((f1 - f0) * slope * 16), light, 0.004);
            }
        }

        private static void put(VertexConsumer vc, Matrix4f m, Matrix3f nm, double[] p, float u, float v, int light, float[] n) {
            int c = (int) (255 * RiggingMeshes.blockShade(n[0], n[1], n[2]));
            FastVertex.put(vc, m, nm, (float) p[0], (float) p[1], (float) p[2], c, c, c, 255, u, v,
                -1, light, n[0], n[1], n[2]);   // 1.2.0
        }
    }

    @Override
    public boolean shouldRenderOffScreen(StageTarpRailBlockEntity be) {
        return be.hasSlant() || be.isLiftSheet();
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(128);
    }
}
