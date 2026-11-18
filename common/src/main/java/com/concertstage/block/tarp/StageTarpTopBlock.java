package com.concertstage.block.tarp;

import com.concertstage.block.StageProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Top row of a stage tarp after the rail cell (welded hem, eyelets, bungees to the truss or to the stage rim). No item. */
public class StageTarpTopBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public StageTarpTopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(StageProperties.SHIFT, 0)
            .setValue(TarpProperties.COLOR, TarpColor.BLACK)
            .setValue(TarpProperties.TOP, 0)
            .setValue(TarpProperties.END_NEG, false)
            .setValue(TarpProperties.END_POS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, StageProperties.SHIFT, TarpProperties.COLOR, TarpProperties.TOP, TarpProperties.END_NEG, TarpProperties.END_POS);
    }

    /** No collision for players, but a Theatrical light beam stops on the cloth (it casts a shadow). */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return com.concertstage.compat.LightCompat.isLightRay(ctx) ? getShape(state, level, pos, ctx) : super.getCollisionShape(state, level, pos, ctx);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return TarpShapes.get(state.getValue(AXIS), state.getValue(StageProperties.SHIFT));
    }

    /** The cell under a one-row tarp changed: fit again (the top row is then also the bottom one). */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (!level.isClientSide && fromPos.equals(pos.below())) StageTarpRailBlock.scheduleRefit(level, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return StageTarpClothBlock.useAny(level, pos, player, hand);
    }
}
