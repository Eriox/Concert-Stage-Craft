package com.concertstage.block.offset;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Any block set on a stage deck, a platform or a riser whose floor is not at a block boundary: it is held in the cell above and drawn,
 * shaped and broken as that block, moved down so it rests on the floor with no gap (OffsetPlacement). Stacks the same way.
 */
public class OffsetBlock extends Block implements EntityBlock {
    public OffsetBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OffsetBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;              // drawn by OffsetBlockRenderer, moved down
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return level.getBlockEntity(pos) instanceof OffsetBlockEntity be ? be.shape(level, pos) : Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return level.getBlockEntity(pos) instanceof OffsetBlockEntity be ? be.collision(level, pos) : Shapes.block();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof OffsetBlockEntity off && !off.stored().isAir()) {
            builder.withParameter(LootContextParams.BLOCK_STATE, off.stored());
            BlockEntity held = off.heldView();
            if (held != null) builder.withParameter(LootContextParams.BLOCK_ENTITY, held);       // its loot keeps its data (name, settings...)
            return off.stored().getDrops(builder);
        }
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof OffsetBlockEntity be && !be.stored().isAir() ? new ItemStack(be.stored().getBlock()) : ItemStack.EMPTY;
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
