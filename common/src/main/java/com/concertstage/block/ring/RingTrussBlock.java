package com.concertstage.block.ring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Circular truss (CSC RingTruss). The block is its rigging point (centre of the ring); the ring itself is drawn around it
 * ({@code RingTrussRenderer}) with its radius, its tilt (0 = flat, 90 = standing like a wheel; it always hangs under the point, raised like a wheel) and its rotation, set with the
 * ring controller. Invisible cells ({@link RingTrussCellBlock}) along the ring give it its collision.
 */
public class RingTrussBlock extends Block implements EntityBlock {
    /** Broken by a player: the item goes straight into the inventory. */
    @Override
    public void playerDestroy(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.entity.BlockEntity be,
                              net.minecraft.world.item.ItemStack tool) {
        com.concertstage.block.Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }

    private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 16, 11);

    public RingTrussBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RingTrussBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RingTrussBlockEntity be) be.rebuildCells();
    }

    /** Broken by a player (not moved by a lift: the lift carries the cells with it): the cells go too. */
    @Override
    public void playerWillDestroy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RingTrussBlockEntity be) be.clearCells();
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Theatrical / Extra Lights fixtures also hang on the rigging point. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                     net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                     net.minecraft.world.phys.BlockHitResult hit) {
        net.minecraft.world.InteractionResult hung = com.concertstage.compat.TheatricalCompat.tryHang(level, pos, player, hand, hit);
        return hung != null ? hung : super.use(state, level, pos, player, hand, hit);
    }
}
