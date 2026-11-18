package com.concertstage.block;

import com.concertstage.StageContent;
import com.concertstage.angle.AngleMath;
import com.concertstage.angle.AngledPiece;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Chain hoist (palan à chaîne D8+). It hooks under a 1 x 2 or 2 x 2 truss (any of the beams, small ones included) and carries what hangs
 * on its lower hook, which is the small 1 x 2 truss (10 px section) placed in the block under it, with the curtains and lights attached.
 *
 * It is a one cell motor that works like a tower for the lift remote and the lift code (it extends {@link MotorTowerBlock}):
 * the "height" is how far the load is above its lowest position, {@link #RANGE} blocks under the fully raised one. A new hoist is fully
 * raised (the load right under the hoist); the remote lowers it and raises it again. Unlike a tower it only takes small trusses
 * (and their curtains / attachments) along, never the big trusses it hangs from. If the big trusses are lifted by towers, the hoist and what
 * hangs on it go with them.
 */
public class HoistBlock extends MotorTowerBlock {
    /** How far the load can go down from the fully raised position, in blocks. */
    public static final int RANGE = 32;          // hook travel, blocks (8 before 1.16.2: older hoists are converted on load)
    /**
     * True when the hoist hangs under a truss placed at an angle: the block model is then not drawn by the world (the block is invisible) but
     * by the renderer, turned to the angle of the beam and moved onto its axis (angle and offset are in the block entity).
     */
    public static final BooleanProperty ANGLED = BooleanProperty.create("angled");

    public HoistBlock(Properties properties) {
        super(properties, 1, Carriage.CORNER3, false, (lx, ly, lz) -> Block.box(0, 0, 4, 14, 16, 12));
        this.registerDefaultState(this.defaultBlockState().setValue(ANGLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ANGLED);
    }

    @Override
    public boolean isHoist() {
        return true;
    }

    @Override
    public int maxLiftBlocks() {
        return RANGE;
    }

    @Override
    public int initialUnits() {
        return RANGE * TowerBlockEntity.UNITS;
    }

    /** The load starts at the cell under the lower hook: right under the hoist when raised, {@code RANGE - lift} blocks lower otherwise. */
    @Override
    public List<BlockPos> attachCells(BlockPos main, BlockState state, int liftBlocks) {
        List<BlockPos> out = new ArrayList<>(1);
        out.add(main.below(1 + RANGE - liftBlocks));
        return out;
    }

    /** An angled load: the cells around the point under the hoist, along its beam (the small trusses hang in the beam's direction). */
    @Override
    public List<BlockPos> attachCells(TowerBlockEntity be, int liftBlocks) {
        if (!be.getBlockState().getValue(ANGLED)) return attachCells(be.mainPos(), be.getBlockState(), liftBlocks);
        BlockPos main = be.mainPos();
        int y = main.below(1 + RANGE - liftBlocks).getY();
        double qx = main.getX() + 0.5 + be.offX(), qz = main.getZ() + 0.5 + be.offZ();
        double[] u = AngleMath.dir(be.angle());
        List<BlockPos> out = new ArrayList<>();
        for (double r : new double[] {0.0, -0.5, 0.5, -1.0, 1.0}) {
            out.add(new BlockPos(AngleMath.cellOf(qx + r * u[0]), y, AngleMath.cellOf(qz + r * u[1])));
        }
        return out;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(ANGLED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /** The beams a hoist can hook on: 1 x 1, 1 x 2 and 2 x 2 trusses, the small 1 x 2 and the X30D ones, aluminium or black. */
    public static boolean isBeam(BlockState state) {
        if (!(state.getBlock() instanceof MultiPartBlock)) return false;
        return isBeamName(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    /** Same test on the registry name of a truss (used for the trusses placed at an angle). */
    public static boolean isBeamName(String path) {
        if (path.endsWith("_noir")) path = path.substring(0, path.length() - 5);
        return path.equals("truss_ladder_l1") || path.equals("truss_ladder_l2") || path.equals("truss_h1_l1") || path.equals("truss_h1_l2") || path.equals("truss_h2_l2") || path.equals("truss_h2_l1") || path.equals("truss_h1_l2_petit") || path.equals("truss_h1_l1_petit")
            || path.equals("truss_x30d_l1") || path.equals("truss_x30d_l2") || path.equals("truss_h2_l2_x30d");
    }

    /** Only under a beam. The hoist turns to run along the beam (sneak = the other way round). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        BlockPos pos = ctx.getClickedPos();                 // where the hoist goes
        BlockState above = ctx.getLevel().getBlockState(pos.above());
        if (!isBeam(above)) {
            // under a truss placed at an angle: the hoist takes the angle of that beam (see setPlacedBy)
            double lookDeg = AngleMath.compassOfStep(ctx.getHorizontalDirection().getStepX(), ctx.getHorizontalDirection().getStepZ());
            return findAngledBeam(ctx.getLevel(), pos, lookDeg) != null ? state.setValue(ANGLED, true) : null;
        }

        // axis of the beam: the models are authored along x, a quarter turn count that is odd means the beam runs along z
        boolean beamAlongZ = ((above.getValue(FACING).get2DDataValue() + 2) % 4) % 2 == 1;
        Direction look = ctx.getHorizontalDirection();
        Direction facing = look;
        boolean alongZ = ((facing.get2DDataValue() + 2) % 4) % 2 == 1;
        if (alongZ != beamAlongZ) facing = look.getClockWise();
        if (ctx.isSecondaryUseActive()) facing = facing.getOpposite();
        return state.setValue(FACING, facing);
    }

    /** Fills in the angle and the position of a hoist that was placed under an angled beam. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TowerBlockEntity placed) placed.refreshHookRise();
        if (level.isClientSide || !state.getValue(ANGLED)) return;
        double lookDeg = placer == null ? 0.0 : AngleMath.compassOfStep(placer.getDirection().getStepX(), placer.getDirection().getStepZ());
        float[] beam = findAngledBeam(level, pos, lookDeg);
        if (beam == null || !(level.getBlockEntity(pos) instanceof TowerBlockEntity be)) return;
        float angle = beam[0];
        if (placer != null && placer.isShiftKeyDown()) angle = (float) AngleMath.norm360(angle + 180.0);     // sneak = the other way round, as for a straight beam
        be.setPlacement(angle, beam[1], beam[2]);
        be.refreshHookRise();
    }

    // ------------------------------------------------------------ 1.1.0: upper hook inside the truss
    /** Inner top of the bight of the upper hook as authored (px, hoist cell): the bar of the truss rests there. */
    public static final float HOOK_BIGHT = 16.456f;

    /**
     * Height (px, from the bottom of the truss cell) of the top of the lowest bar of the truss that crosses the hoist axis: the upper hook
     * of the hoist goes up into the truss and hangs on it. Box trusses (1 x 1, 1 x 2, 2 x 2, panel with X30D): bottom diagonal,
     * 0.75 - 2.25 px. Ladder, small BoxTruss 60 and TriTruss X30D: lower chord / diagonal, about 3.5 - 4.5 px.
     */
    public static float catchBarTop(String path) {
        if (path.endsWith("_noir")) path = path.substring(0, path.length() - 5);
        switch (path) {
            case "truss_ladder_l1": case "truss_ladder_l2":
            case "truss_h1_l1_petit": case "truss_h1_l2_petit":
            case "truss_x30d_l1": case "truss_x30d_l2":
                return 4.45f;
            default:
                return 2.25f;                       // truss_h1_l1, truss_h1_l2, truss_h2_l1, truss_h2_l2, truss_h2_l2_x30d and any other beam
        }
    }

    /** Raise of the upper hook (px) when nothing is known about the beam: the box trusses. */
    public static final float DEFAULT_HOOK_RISE = 16f + 2.25f - HOOK_BIGHT;

    /**
     * How far (px) the upper hook must go up so that its bight rests on the bar of the truss above, for any beam a hoist accepts,
     * straight or placed at an angle. NaN if there is no beam above (e.g. the truss is being moved).
     */
    public static float hookRise(Level level, BlockPos pos, BlockState state) {
        BlockPos above = pos.above();
        if (!state.getValue(ANGLED)) {
            BlockState beam = level.getBlockState(above);
            if (!isBeam(beam)) return Float.NaN;
            return 16f + catchBarTop(BuiltInRegistries.BLOCK.getKey(beam.getBlock()).getPath()) - HOOK_BIGHT;
        }
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        float best = Float.NaN;
        double bestD = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos holderPos = above.offset(dx, 0, dz);
                if (!(level.getBlockEntity(holderPos) instanceof AngledHolderBlockEntity holder)) continue;
                for (AngledPiece p : holder.pieces()) {
                    double y = p.absY(holderPos);
                    if (!isBeamName(p.block) || Math.round(y) != above.getY()) continue;
                    double[] hit = AngleMath.axisPointInCell(p.absX(holderPos), p.absZ(holderPos), p.angle, p.length(), cx, cz);
                    if (hit == null) continue;
                    double d = AngleMath.dist2(hit[0], hit[1], cx, cz);
                    if (d < bestD) {
                        bestD = d;
                        best = (float) (16.0 + (y - above.getY()) * 16.0 + catchBarTop(p.block) - HOOK_BIGHT);
                    }
                }
            }
        }
        return best;
    }

    /**
     * The angled beam right above the cell {@code pos} that crosses it: {angle, offsetX, offsetZ}, or null. The hoist sits on the axis of the
     * beam, at the point of the axis inside the cell that is closest to the centre of the cell, and runs along the beam (the direction of
     * the two that is closest to where the player looks).
     */
    public static float[] findAngledBeam(Level level, BlockPos pos, double lookDeg) {
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        BlockPos above = pos.above();
        float[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos holderPos = above.offset(dx, 0, dz);
                if (!(level.getBlockEntity(holderPos) instanceof AngledHolderBlockEntity holder)) continue;
                for (AngledPiece p : holder.pieces()) {
                    if (!isBeamName(p.block) || Math.round(p.absY(holderPos)) != above.getY()) continue;
                    double[] hit = AngleMath.axisPointInCell(p.absX(holderPos), p.absZ(holderPos), p.angle, p.length(), cx, cz);
                    if (hit == null) continue;
                    double d = AngleMath.dist2(hit[0], hit[1], cx, cz);
                    if (d < bestD) {
                        bestD = d;
                        best = new float[] {(float) AngleMath.alongBeam(p.angle, lookDeg), (float) (hit[0] - cx), (float) (hit[1] - cz)};
                    }
                }
            }
        }
        return best;
    }
}
