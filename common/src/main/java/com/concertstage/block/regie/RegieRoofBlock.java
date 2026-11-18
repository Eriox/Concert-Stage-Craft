package com.concertstage.block.regie;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Anchor of a FOH tarp roof (one cell over the FOH structure; the roof itself is drawn by RegieRoofRenderer). Break it to take the roof back.
 * aurex.2: it breaks in one hit and the whole cell can be aimed at; the roof can also be taken back without finding it: sneak + right click
 * on the FOH structure with a FOH roof tarp, or "Remove the roof" in the CSC RoofCommand screen.
 */
public class RegieRoofBlock extends BaseEntityBlock {
    /** aurex.2: the whole cell can be aimed at (it was a 1 px plate, hard to hit under the tarp). */
    private static final VoxelShape PLATE = Block.box(0, 0, 0, 16, 16, 16);

    public RegieRoofBlock(Properties properties) {
        super(properties);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return PLATE; }
    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RegieRoofBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StageContent.REGIE_ROOF_ENTITY) return null;
        return (l, p, s, be) -> { if (be instanceof RegieRoofBlockEntity roof) roof.serverTick(); };
    }

    /** Right click with the roof item: gable roof, flat roof, single slope. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(StageContent.REGIE_ROOF_TARP)) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RegieRoofBlockEntity roof) {
            roof.cycleShape();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.concertstage.regie_roof_shape_" + roof.shape()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool) {
        com.concertstage.block.Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }
}
