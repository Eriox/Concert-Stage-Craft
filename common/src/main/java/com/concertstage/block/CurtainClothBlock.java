package com.concertstage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One piece of pleated cloth. Created and removed by the Curtain Rail (not obtainable as an item).
 * The first row (TOP) has a rail bar and clips that reach up to the truss, so the curtain has no gap.
 */
public class CurtainClothBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public CurtainClothBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(COLOR, DyeColor.BLACK)
            .setValue(TOP, false)
            .setValue(StageProperties.DROP, 0)
            .setValue(StageProperties.SHIFT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, COLOR, TOP, StageProperties.DROP, StageProperties.SHIFT);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.UP && state.getValue(TOP)) {
            return state.setValue(StageProperties.DROP, TrussGap.gapUnder(neighbor));
        }
        return state;
    }

    /** No collision for players, but a Theatrical light beam stops on the cloth (it casts a shadow). */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return com.concertstage.compat.LightCompat.isLightRay(ctx) ? getShape(state, level, pos, ctx) : super.getCollisionShape(state, level, pos, ctx);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return CurtainShapes.get(state.getValue(AXIS), state.getValue(StageProperties.SHIFT));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof DyeItem dye) {
            DyeColor c = dye.getDyeColor();
            if (!level.isClientSide && c != state.getValue(COLOR)) {
                level.setBlock(pos, state.setValue(COLOR, c), Block.UPDATE_ALL);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
