package com.concertstage.block.ring;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * CSC 1.3.0 - CSC CURVED TRITRUSS / CSC CURVED QUADTRUSS: a curved truss (radius 1 to 20 m, angle 5 to 355°, bending left, right, up or
 * down) that starts in this block, at the centre of its back face, along the direction the player looked when placing it — where a
 * straight truss of the same section ends. Same block entity as the circular truss ({@link RingTrussBlockEntity}: collision cells,
 * carried by the motorised structures, fixtures on the cells), settings with the CSC RingTruss controller.
 */
public class CurvedTrussBlock extends RingTrussBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Triangular section (TriTruss X30D) rather than square (QuadTruss H1). */
    public final boolean tri;

    public CurvedTrussBlock(Properties properties, boolean tri) {
        super(properties);
        this.tri = tri;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** New piece: its section, a 90° arc of 3.5 m to the left (ends on a block face, see {@link RingTrussBlockEntity#curveFrame()}). */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RingTrussBlockEntity be) {
            if (!stack.hasTag() || !stack.getTag().contains("BlockEntityTag")) be.apply(7, 0, (tri ? 64 : 0) | (90 << 9));
            else be.rebuildCells();
        }
    }
}
