package com.concertstage.block;

import com.concertstage.ConcertStage;
import com.concertstage.angle.AngledPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Holds the trusses that are placed at an angle. A truss at an angle does not fit the block grid, so it is not a block of its own:
 * the cell that contains its pivot holds a list of pieces (several can share a cell), and a block entity renderer draws each one with the
 * model of its truss, turned about the vertical axis. The cell has an outline so it can be aimed at, but no collision (the pieces
 * stick out of it and cannot be walked on). Breaking the cell gives back every piece it holds.
 */
public class AngledHolderBlock extends Block implements EntityBlock {
    /** Broken by a player: the item goes straight into the inventory. */
    @Override
    public void playerDestroy(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.entity.BlockEntity be,
                              net.minecraft.world.item.ItemStack tool) {
        com.concertstage.block.Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }

    public AngledHolderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AngledHolderBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** In creative mode nothing is given back, like any other block. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        if (player.isCreative() && level.getBlockEntity(pos) instanceof AngledHolderBlockEntity holder) holder.noDrops = true;
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Gives back every truss of the cell (unless a lift is taking the cell away, or the player is in creative mode). */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof AngledHolderBlockEntity holder
            && !holder.noDrops) {
            for (AngledPiece piece : holder.pieces()) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(ConcertStage.MOD_ID, piece.block));
                if (item != net.minecraft.world.item.Items.AIR) Drops.give(level, pos, new ItemStack(item));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Middle click: the first truss of the cell. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof AngledHolderBlockEntity holder && !holder.pieces().isEmpty()) {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(ConcertStage.MOD_ID, holder.pieces().get(0).block));
            if (item != net.minecraft.world.item.Items.AIR) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }
}
