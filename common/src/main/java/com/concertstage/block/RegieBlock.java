package com.concertstage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * FOH (régie) kit piece. It sits on the real floor under it: on a deck whose surface is lower than the top of its cell (a 40 cm
 * deck: surface 6.4 px into its cell, so 9.6 px below the cell the piece is placed in) the whole piece goes {@link #DROP} px down,
 * model and shapes alike. A piece set on (or clicked against) another FOH piece takes its drop, so towers and ledgers stay aligned.
 */
public class RegieBlock extends MultiPartBlock {
    public static final IntegerProperty DROP = IntegerProperty.create("drop", 0, 15);
    private final VoxelShape[][][] moved;
    private final VoxelShape[][][] selection;

    public RegieBlock(Properties properties, int sx, int sy, int sz, Placement placement, boolean chain, ShapeProvider shape) {
        super(properties, sx, sy, sz, 0, 0, 0, placement, chain, shape);
        this.moved = new VoxelShape[sx * sy * sz][4][16];
        this.selection = new VoxelShape[sx * sy * sz][4][16];
        registerDefaultState(defaultBlockState().setValue(DROP, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DROP);
    }

    /** Drop for a piece at {@code pos}: the drop of a FOH piece it is attached to, else the gap between the floor under it and its cell. */
    public static int dropFor(BlockGetter level, BlockPos pos, BlockPos clicked) {
        if (clicked != null) {
            BlockState c = level.getBlockState(clicked);
            if (c.getBlock() instanceof RegieBlock) return c.getValue(DROP);
        }
        BlockPos below = pos.below();
        BlockState b = level.getBlockState(below);
        if (b.getBlock() instanceof RegieBlock) return b.getValue(DROP);
        if (b.isAir()) return 0;
        VoxelShape shape = b.getCollisionShape(level, below);
        if (shape.isEmpty()) return 0;
        double top = shape.max(Direction.Axis.Y);
        if (top >= 0.999) return 0;
        return Math.max(0, Math.min(15, (int) Math.round((1.0 - top) * 16)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        if (s == null) return null;
        BlockPos clicked = ctx.replacingClickedOnBlock() ? null : ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite());
        // the bottom row decides: the placed cell is in row localY, so look under the bottom row
        int row = rowOf(s);
        return s.setValue(DROP, dropFor(ctx.getLevel(), ctx.getClickedPos().below(row), clicked));
    }

    /** Exact shape (collision, light beams, tarps): the authored tubes, moved down by the drop. */
    public VoxelShape precise(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int drop = state.getValue(DROP);
        VoxelShape base = super.getShape(state, level, pos, ctx);
        if (drop == 0) return base;
        int part = state.getValue(PART);
        int turn = state.getValue(FACING).get2DDataValue();
        if (part >= moved.length) return base.move(0, -drop / 16.0, 0);
        VoxelShape m = moved[part][turn][drop];
        if (m == null) moved[part][turn][drop] = m = base.move(0, -drop / 16.0, 0);
        return m;
    }

    /**
     * Outline used to aim at the piece (placing against it, breaking it): every tube made at least 6 px thick (a post 3.2 px, a ledger
     * 1.6 px are hard to hit), kept inside the cell. The collision stays the exact shape.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int drop = state.getValue(DROP);
        int part = state.getValue(PART);
        int turn = state.getValue(FACING).get2DDataValue();
        boolean cache = part < selection.length;
        if (cache && selection[part][turn][drop] != null) return selection[part][turn][drop];
        VoxelShape exact = precise(state, level, pos, ctx);
        VoxelShape out = net.minecraft.world.phys.shapes.Shapes.empty();
        double min = 6.0 / 16;
        for (net.minecraft.world.phys.AABB b : exact.toAabbs()) {
            double[] lo = {b.minX, b.minY, b.minZ}, hi = {b.maxX, b.maxY, b.maxZ};
            for (int i = 0; i < 3; i++) {
                double size = hi[i] - lo[i];
                if (size < min) { double c = (lo[i] + hi[i]) / 2; lo[i] = c - min / 2; hi[i] = c + min / 2; }
                double floor = i == 1 ? -drop / 16.0 : 0.0;
                lo[i] = Math.max(floor, lo[i]); hi[i] = Math.min(1.0, hi[i]);
            }
            if (hi[0] > lo[0] && hi[1] > lo[1] && hi[2] > lo[2])
                out = net.minecraft.world.phys.shapes.Shapes.or(out, net.minecraft.world.phys.shapes.Shapes.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
        }
        out = out.isEmpty() ? exact : out.optimize();
        if (cache) selection[part][turn][drop] = out;
        return out;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return precise(state, level, pos, ctx);
    }
}
