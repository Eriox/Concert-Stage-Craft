package com.concertstage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Curtain rail. Place it under a truss (clips reach up to the truss, no gap):
 * the curtain extends to the RIGHT of where you look.
 * - Tarp Tool: opens a screen to set the length (1-50), the height (1-50) and the position of the cloth.
 * - Dye: repaint the whole curtain.
 * - Empty hand or redstone: gather / spread the curtain.
 */
public class CurtainRailBlock extends Block implements EntityBlock {
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
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public CurtainRailBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.EAST)
            .setValue(OPEN, false)
            .setValue(POWERED, false)
            .setValue(COLOR, DyeColor.BLACK)
            .setValue(StageProperties.DROP, 0)
            .setValue(StageProperties.SHIFT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED, COLOR, StageProperties.DROP, StageProperties.SHIFT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState above = ctx.getLevel().getBlockState(ctx.getClickedPos().above());
        return this.defaultBlockState()
            .setValue(FACING, ctx.getHorizontalDirection().getClockWise())
            .setValue(StageProperties.DROP, TrussGap.gapUnder(above))
            // under a truss the cloth hangs on the line of the tubes, on the side the player stands on
            .setValue(StageProperties.SHIFT, TrussGap.curtainShift(above, ctx.getHorizontalDirection().getOpposite()));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.UP) return state.setValue(StageProperties.DROP, TrussGap.gapUnder(neighbor));
        return state;
    }

    /** No collision for players, but a Theatrical light beam stops on the cloth (it casts a shadow). */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return com.concertstage.compat.LightCompat.isLightRay(ctx) ? getShape(state, level, pos, ctx) : super.getCollisionShape(state, level, pos, ctx);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return CurtainShapes.get(state.getValue(FACING).getAxis(), state.getValue(StageProperties.SHIFT));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CurtainRailBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CurtainRailBlockEntity be) {
            be.rebuild();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof CurtainRailBlockEntity be) {
            be.clearAll();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (level.isClientSide) return;
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, signal).setValue(OPEN, signal), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof CurtainRailBlockEntity be) be.rebuild();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level.getBlockEntity(pos) instanceof CurtainRailBlockEntity be)) return InteractionResult.PASS;

        if (stack.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide) {
                be.setColor(dye.getDyeColor());
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.isEmpty()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(OPEN, !state.getValue(OPEN)), Block.UPDATE_ALL);
                be.rebuild();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS; // other items (the ruler, blocks...) handle the click themselves
    }
}
