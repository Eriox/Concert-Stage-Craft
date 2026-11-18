package com.concertstage.client;

import com.concertstage.block.MotorTowerBlock;
import com.concertstage.block.MultiPartBlock;
import com.concertstage.block.TowerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws one cell of a motorised tower: its slice of the mast, the motor and the head, and the sliding joint when the joint is in
 * this cell. Every cell has its own block entity, so the tower is drawn as soon as any part of it is on screen.
 */
public class TowerRenderer implements BlockEntityRenderer<TowerBlockEntity> {
    private static final GpuMesh.Cache MAST = new GpuMesh.Cache(4096);

    public TowerRenderer(BlockEntityRendererProvider.Context context) {}

    public static void clear() {
        MAST.clear();
    }

    @Override
    public void render(TowerBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !(state.getBlock() instanceof MotorTowerBlock tower)) return;
        if (tower.isHoist()) {
            // 1.1.0: a hoist at rest (body when angled, chain, hook) is kept in the graphics card until it moves
            TowerBlockEntity ctl = be.controller();
            if (GpuMesh.enabled() && !(ctl != null ? ctl : be).isMoving()) {
                GpuMesh.noCull(TowerModel.HOIST_TEXTURE);
                String key = "hoist/" + state + "/" + be.angle() + "/" + be.offX() + "/" + be.offZ() + "/" + be.liftBlocks(level.getGameTime(), partial) + "/" + light
                    + "/" + topHookRise(be, level, state);
                GpuMesh m = MAST.get(key, b -> renderHoist(be, level, state, partial, new PoseStack(), b.source(), light));
                if (m != null) { m.draw(pose); return; }
            }
            renderHoist(be, level, state, partial, pose, buffers, light);
            return;
        }
        int cell = state.getValue(MultiPartBlock.PART);
        boolean refined = RiggingMeshes.available(tower.towerHeight());
        double dist2 = net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        boolean near = dist2 <= (double) RenderQuality.current().nearRadius * RenderQuality.current().nearRadius;
        // far away, the thin parts (chains, wheels, the bag of the motor) are not drawn (performance, 1.0.2)
        boolean thin = ClientCulling.trussDetail(Math.sqrt(dist2)) < 2;
        TowerBlockEntity controller = be.controller();
        double lift = controller == null ? 0.0 : controller.liftBlocks(level.getGameTime(), partial);
        int turns = (state.getValue(MultiPartBlock.FACING).get2DDataValue() + 2) % 4;
        // reference look of the rigging: the trusses copy this lighting (tools/match_truss_lighting.py)
        net.minecraft.resources.ResourceLocation tex = refined ? (tower.isBlack() ? RiggingMeshes.BLACK : RiggingMeshes.ALUMINIUM) : (tower.isBlack() ? TowerModel.TEXTURE_NOIR : TowerModel.TEXTURE);
        // 1.1.0: a tower at rest (the usual case) does not change at all: its whole cell (mast, chains, wheels, carriage) is kept in the
        // graphics card, rebuilt only when the tower has moved or the light / detail changed
        if (GpuMesh.enabled() && (controller == null || !controller.isMoving())) {
            double middle = 1.0 + lift + tower.carriage().cells / 2.0;
            boolean carriageHere = controller != null && (int) Math.floor(middle) == cell;
            int carriageLight = carriageHere ? LevelRenderer.getLightColor(level, controller.mainPos().above((int) Math.floor(middle))) : 0;
            String key = "all/" + tower.towerHeight() + "/" + cell + (near ? "n" : "f") + (thin ? "t" : "") + (refined ? "r" : "o") + tex + "/" + turns + "/"
                + be.angle() + "/" + light + "/" + lift + "/" + tower.carriage().id + "/" + tower.carriage().cells + (carriageHere ? "c" + carriageLight : "") + (controller == null ? "x" : "");
            GpuMesh m = MAST.get(key, b -> {
                PoseStack l = new PoseStack();
                l.translate(.5, 0, .5); l.mulPose(Axis.YP.rotationDegrees(-be.angle())); l.mulPose(Axis.YP.rotationDegrees(-90.0F * turns)); l.translate(-.5, 0, -.5);
                VertexConsumer c = b.consumer(tex, false, true);
                l.pushPose(); l.translate(0, -cell, 0);
                if (refined) {
                    RiggingMeshes.mast(tower.towerHeight(), cell, near, l, c, light);
                    if (thin) RiggingMeshes.mechanism(tower.towerHeight(), cell, tower.carriage().cells, lift, near, l, c, light);
                } else TowerModel.render(TowerModel.mastCell(tower.towerHeight(), cell), l, c, light);
                l.popPose();
                if (carriageHere) {
                    l.pushPose(); l.translate(0, 1 + lift - cell, 0);
                    if (refined) RiggingMeshes.carriage(tower.carriage().id, near, l, c, carriageLight);
                    else TowerModel.render(TowerModel.carriage(tower.carriage().id), l, c, carriageLight);
                    l.popPose();
                }
            });
            if (m != null) { m.draw(pose); return; }
        }
        // moving: the mast slice (it never moves) is kept in the graphics card; the chains, wheels and carriage are drawn every frame
        boolean mastDone = false;
        if (GpuMesh.enabled()) {
            String key = tower.towerHeight() + "/" + cell + (near ? "n" : "f") + (refined ? "r" : "o") + tex + "/" + turns + "/" + be.angle() + "/" + light;
            GpuMesh m = MAST.get(key, b -> {
                PoseStack l = new PoseStack();
                l.translate(.5, 0, .5); l.mulPose(Axis.YP.rotationDegrees(-be.angle())); l.mulPose(Axis.YP.rotationDegrees(-90.0F * turns)); l.translate(-.5, 0, -.5);
                l.translate(0, -cell, 0);
                VertexConsumer c = b.consumer(tex, false, true);
                if (refined) RiggingMeshes.mast(tower.towerHeight(), cell, near, l, c, light);
                else TowerModel.render(TowerModel.mastCell(tower.towerHeight(), cell), l, c, light);
            });
            if (m != null) { m.draw(pose); mastDone = true; }
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-be.angle()));pose.mulPose(Axis.YP.rotationDegrees(-90.0F * turns));pose.translate(-.5,0,-.5);
        pose.pushPose();pose.translate(0,-cell,0);
        if (refined) {
            if (!mastDone) RiggingMeshes.mast(tower.towerHeight(),cell,near,pose,vc,light);
            if (thin) RiggingMeshes.mechanism(tower.towerHeight(),cell,tower.carriage().cells,lift,near,pose,vc,light);
        } else if (!mastDone) TowerModel.render(TowerModel.mastCell(tower.towerHeight(),cell),pose,vc,light);
        pose.popPose();
        if (controller != null) {
            double middle = 1.0 + lift + tower.carriage().cells / 2.0;
            if ((int)Math.floor(middle) == cell) {
                pose.pushPose();pose.translate(0,1+lift-cell,0);
                BlockPos at=controller.mainPos().above((int)Math.floor(middle));
                if(refined) RiggingMeshes.carriage(tower.carriage().id,near,pose,vc,LevelRenderer.getLightColor(level,at));
                else TowerModel.render(TowerModel.carriage(tower.carriage().id),pose,vc,LevelRenderer.getLightColor(level,at));
                pose.popPose();
            }
        }
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(128);
    }

    /**
     * The lower part of a chain hoist: the body is the block model, the hook with its weight and the chain are drawn here because they
     * go down with the load. At rest the hook hangs inside the small truss under the hoist ({@code hookDy} px under the authored position);
     * for every block the load is lowered, the hook and the weight go one block lower and the chain gets longer.
     */
    /** Raise of the upper hook (px): live from the truss above, else the value saved by the server (hoist carried by a tower), else a box truss. */
    private static float topHookRise(TowerBlockEntity be, Level level, BlockState state) {
        float r = be.hookRise();
        if (Float.isNaN(r) && !state.getValue(com.concertstage.block.HoistBlock.ANGLED)) r = com.concertstage.block.HoistBlock.hookRise(level, be.getBlockPos(), state);
        return Float.isNaN(r) ? com.concertstage.block.HoistBlock.DEFAULT_HOOK_RISE : r;
    }

    private void renderHoist(TowerBlockEntity be, Level level, BlockState state, float partial, PoseStack pose,
                             MultiBufferSource buffers, int light) {
        TowerModel.HoistHook h = TowerModel.hoistHook();
        if (h.linkA == null) return;
        double lift = be.liftBlocks(level.getGameTime(), partial);
        double lowered = Math.max(0.0, com.concertstage.block.HoistBlock.RANGE - lift);          // blocks under the fully raised position
        double dyPx = h.hookDy - lowered * 16.0;                                                // px, hook assembly
        int turns = (state.getValue(MultiPartBlock.FACING).get2DDataValue() + 2) % 4;
        pose.pushPose();
        boolean angled = state.getValue(com.concertstage.block.HoistBlock.ANGLED);
        if (angled) {
            // under a beam placed at an angle: on the axis of the beam, turned to its angle (same transform as the angled trusses)
            pose.translate(0.5 + be.offX(), 0.0, 0.5 + be.offZ());
            pose.mulPose(Axis.YP.rotationDegrees(-be.angle()));
            pose.translate(-0.5, 0.0, -0.5);
            // the block is invisible in this state: draw the body here, in its authored orientation
            BlockState body = state.setValue(com.concertstage.block.HoistBlock.ANGLED, false)
                .setValue(MultiPartBlock.FACING, net.minecraft.core.Direction.NORTH);
            net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(body, pose, buffers, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } else {
            pose.translate(0.5, 0.0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F * turns));
            pose.translate(-0.5, 0.0, -0.5);
        }

        // renderSingleBlock above may switch/flush the active buffer. Acquire the
        // chain buffer afterwards so an angled moving hoist keeps its hook too.
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TowerModel.HOIST_TEXTURE));
        // chain links under the six of the block model, down to the weight
        java.util.List<float[]> links = new java.util.ArrayList<>();
        double limit = h.massTop + dyPx - 1.7;
        for (int n = h.firstLink; n < 400; n++) {
            float shift = -h.pitch * (n - (n % 2));
            float[] c = (n % 2 == 0 ? h.linkA : h.linkB).clone();
            if (c[1] + shift < limit) break;
            c[1] += shift;
            c[4] += shift;
            c[7] += shift;
            links.add(c);
        }
        TowerModel.render(links, pose, vc, light);

        // 1.1.0: upper hook inside the truss above: raised until its bight rests on the bar of that truss, turned across the beam so
        // the bar goes through it, and joined to the motor body by a shank
        if (!h.topHook.isEmpty()) {
            float rise = topHookRise(be, level, state);
            pose.pushPose();
            pose.translate(0.0, rise / 16.0, 0.0);
            pose.translate(0.5, 0.0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            pose.translate(-0.5, 0.0, -0.5);
            TowerModel.render(h.topHook, pose, vc, light);
            if (rise > 0.05f) {
                float bottom = h.topBase - rise, top = h.topBase + 0.15f;
                TowerModel.render(java.util.List.of(new float[] {6.625f, bottom, 7.65f, 7.325f, top, 8.35f,
                    6.975f, (bottom + top) / 2f, 8.0f, 0, 0, 0, 4, 4, 8, 8}), pose, vc, light);
            }
            pose.popPose();
        }

        pose.pushPose();
        pose.translate(0.0, dyPx / 16.0, 0.0);
        TowerModel.render(h.hook, pose, vc, light);
        pose.popPose();
        pose.popPose();
    }
}
