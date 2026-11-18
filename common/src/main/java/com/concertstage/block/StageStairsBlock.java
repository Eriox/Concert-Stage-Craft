package com.concertstage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stage stairs: 4 open treads (25 cm rise each), side stringers and hand rails.
 * FACING = direction you walk to go up. Stringers and rails disappear between two neighbouring stairs,
 * so you can build wide stairs by placing several side by side.
 */
public class StageStairsBlock extends Block {
    @Override
    public void playerWillDestroy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                  net.minecraft.world.entity.player.Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Broken by a player: the item goes straight into the inventory. */
    @Override
    public void playerDestroy(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.entity.BlockEntity be,
                              net.minecraft.world.item.ItemStack tool) {
        com.concertstage.block.Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");    // a stair is next to the left side
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");  // a stair is next to the right side

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES[d.get2DDataValue()] = makeShape(d);
        }
    }

    private static VoxelShape makeShape(Direction d) {
        VoxelShape shape = Shapes.empty();
        for (int i = 1; i <= 4; i++) {
            double z0 = 16 - 4 * i, z1 = 20 - 4 * i, h = 4 * i;
            VoxelShape part;
            switch (d) {
                case EAST: part = Block.box(16 - z1, 0, 0, 16 - z0, h, 16); break;
                case SOUTH: part = Block.box(0, 0, 16 - z1, 16, h, 16 - z0); break;
                case WEST: part = Block.box(z0, 0, 0, z1, h, 16); break;
                default: part = Block.box(0, 0, z0, 16, h, z1); break;
            }
            shape = Shapes.or(shape, part);
        }
        return shape;
    }

    public StageStairsBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
        return withNeighbors(ctx.getLevel(), ctx.getClickedPos(), state);
    }

    public static BlockState withNeighbors(BlockGetter level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        return state
            .setValue(LEFT, same(level.getBlockState(pos.relative(f.getCounterClockWise())), f))
            .setValue(RIGHT, same(level.getBlockState(pos.relative(f.getClockWise())), f));
    }

    private static boolean same(BlockState s, Direction f) {
        return s.getBlock() instanceof StageStairsBlock && s.getValue(FACING) == f;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        Direction f = state.getValue(FACING);
        if (dir == f.getCounterClockWise()) return state.setValue(LEFT, same(neighbor, f));
        if (dir == f.getClockWise()) return state.setValue(RIGHT, same(neighbor, f));
        return state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }
}
