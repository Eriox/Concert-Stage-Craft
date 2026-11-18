package com.concertstage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Festival stage stairs (1 x 3 x 3). Two flights set side by side, facing the same way, make one wide staircase: the handrail between them
 * goes (on each of the two flights), the outer ones stay.
 */
public class FestivalStairsBlock extends MultiPartBlock {
    /** Handrail on the authored -x side (left when climbing) / +x side. */
    public static final BooleanProperty RAIL_L = BooleanProperty.create("rail_l"), RAIL_R = BooleanProperty.create("rail_r");
    private static final int STEPS = 9;
    private final VoxelShape[][][] shapes = new VoxelShape[9][4][4];

    public FestivalStairsBlock(Properties properties) {
        super(properties, 1, 3, 3, 0, 0, 0, Placement.FRONT, false, (lx, ly, lz) -> cell(lx, ly, lz, boxes(true, true)));
        registerDefaultState(defaultBlockState().setValue(RAIL_L, true).setValue(RAIL_R, true));
        for (int mask = 0; mask < 4; mask++) {
            double[][] b = boxes((mask & 1) == 0, (mask & 2) == 0);
            for (int part = 0; part < 9; part++) {
                VoxelShape base = cell(part % 1, part / 3, part % 3, b);
                for (int k = 0; k < 4; k++) shapes[part][k][mask] = rotate(base, k);
            }
        }
    }

    /** Steps (solid staircase) and the 0.9 m handrails: 10 risers of 20 cm to the 2.0 m deck, 9 treads of 33 cm. */
    static double[][] boxes(boolean left, boolean right) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < STEPS; i++) {
            double top = (i + 1) * 3.2, z0 = i * 48.0 / STEPS, z1 = (i + 1) * 48.0 / STEPS;
            out.add(new double[] {0, i * 3.2, z0, 16, top, 48});
            if (left) out.add(new double[] {0, top, z0, 1, top + 18.1, z1});
            if (right) out.add(new double[] {15, top, z0, 16, top + 18.1, z1});
        }
        return out.toArray(new double[0][]);
    }

    private static VoxelShape cell(int lx, int ly, int lz, double[][] boxes) {
        VoxelShape s = Shapes.empty();
        double ox = lx * 16, oy = ly * 16, oz = lz * 16;
        for (double[] b : boxes) {
            double x0 = Math.max(b[0], ox), y0 = Math.max(b[1], oy), z0 = Math.max(b[2], oz);
            double x1 = Math.min(b[3], ox + 16), y1 = Math.min(b[4], oy + 16), z1 = Math.min(b[5], oz + 16);
            if (x0 < x1 && y0 < y1 && z0 < z1) s = Shapes.or(s, Block.box(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz));
        }
        return s.optimize();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RAIL_L, RAIL_R);
    }

    private static int turns(Direction facing) { return (facing.get2DDataValue() + 2) % 4; }

    /** World side of the authored -x (left) or +x (right) handrail for this facing. */
    static Direction side(Direction facing, boolean left) {
        Direction d = left ? Direction.WEST : Direction.EAST;
        for (int i = 0; i < turns(facing); i++) d = d.getClockWise();
        return d;
    }

    private static boolean twin(BlockState self, BlockState other) {
        return other.getBlock() instanceof FestivalStairsBlock && other.getValue(FACING) == self.getValue(FACING)
            && other.getValue(PART).equals(self.getValue(PART));
    }

    private BlockState withRails(BlockState s, LevelAccessor level, BlockPos pos) {
        Direction f = s.getValue(FACING);
        return s.setValue(RAIL_L, !twin(s, level.getBlockState(pos.relative(side(f, true)))))
            .setValue(RAIL_R, !twin(s, level.getBlockState(pos.relative(side(f, false)))));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level.isClientSide || !state.is(this)) return;
        BlockState rails = withRails(state, level, pos);
        if (rails != state) level.setBlock(pos, rails, Block.UPDATE_CLIENTS);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos other) {
        BlockState s = super.updateShape(state, dir, neighbor, level, pos, other);
        if (!s.is(this) || dir.getAxis().isVertical()) return s;
        Direction f = s.getValue(FACING);
        if (dir == side(f, true)) return s.setValue(RAIL_L, !twin(s, neighbor));
        if (dir == side(f, false)) return s.setValue(RAIL_R, !twin(s, neighbor));
        return s;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int part = state.getValue(PART);
        if (part >= 9) return super.getShape(state, level, pos, ctx);
        int mask = (state.getValue(RAIL_L) ? 0 : 1) | (state.getValue(RAIL_R) ? 0 : 2);
        return shapes[part][turns(state.getValue(FACING))][mask];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }
}
