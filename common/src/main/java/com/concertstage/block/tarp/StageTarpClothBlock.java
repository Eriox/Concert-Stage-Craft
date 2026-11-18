package com.concertstage.block.tarp;

import com.concertstage.block.StageProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
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

/**
 * A row of stage tarp below the top row (created and removed by the {@link StageTarpRailBlock}, no item).
 * The lowest piece of each column carries the bottom hem; when what is under it changes, the tarp is fitted again.
 */
public class StageTarpClothBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public StageTarpClothBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(StageProperties.SHIFT, 0)
            .setValue(TarpProperties.COLOR, TarpColor.BLACK)
            .setValue(TarpProperties.BOTTOM, 0)
            .setValue(TarpProperties.REACH, 0)
            .setValue(TarpProperties.END_NEG, false)
            .setValue(TarpProperties.END_POS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, StageProperties.SHIFT, TarpProperties.COLOR, TarpProperties.BOTTOM, TarpProperties.REACH,
            TarpProperties.END_NEG, TarpProperties.END_POS);
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

    /** The block under the bottom hem changed (a deck, a railing was placed or removed): fit the whole tarp again. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (level.isClientSide || !fromPos.equals(pos.below())) return;
        if (state.getValue(TarpProperties.BOTTOM) == 0 && !level.getBlockState(fromPos).isAir()) return;
        StageTarpRailBlock.scheduleRefit(level, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return useAny(level, pos, player, hand);
    }

    /** On any piece: a dye recolours the tarp, an empty hand gathers / spreads it. */
    static InteractionResult useAny(Level level, BlockPos pos, Player player, InteractionHand hand) {
        InteractionResult dyed = dye(level, pos, player, hand);
        if (dyed != InteractionResult.PASS || !player.getItemInHand(hand).isEmpty()) return dyed;
        if (!level.isClientSide) {
            BlockPos rail = StageTarpRailBlock.findRail(level, pos);
            if (rail != null && level.getBlockEntity(rail) instanceof StageTarpRailBlockEntity be) be.toggleOpen();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A dye on any piece recolours the whole tarp. */
    static InteractionResult dye(Level level, BlockPos pos, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof DyeItem dye)) return InteractionResult.PASS;
        BlockPos owner = StageTarpRailBlock.findRail(level, pos);
        if (owner != null && level.getBlockState(owner).getBlock() instanceof StageTarpRailBlock r && r.isRegie()) return InteractionResult.PASS;   // FOH tarp: black only
        TarpColor color = TarpColor.fromDye(dye.getDyeColor());
        if (color == null) return InteractionResult.PASS;
        if (!level.isClientSide) {
            BlockPos rail = StageTarpRailBlock.findRail(level, pos);
            if (rail != null && level.getBlockEntity(rail) instanceof StageTarpRailBlockEntity be && be.getColor() != color) {
                be.setColor(color);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
