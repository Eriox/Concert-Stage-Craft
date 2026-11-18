package com.concertstage.block.roof;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Foot of a roof support frame (CSC RoofTruss), placed on top of a grid truss: the frame is drawn from here to the truss on the other side
 * ({@code RoofTrussRenderer}); invisible cells along it give it its collision. Right click it with the controller (CSC RingCommand) to set
 * the shape (arc, gable, single slope), span, rise, direction and finish.
 */
public class RoofTrussBlock extends Block implements EntityBlock {
    /** Broken by a player: the item goes straight into the inventory. */
    @Override
    public void playerDestroy(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.entity.BlockEntity be,
                              net.minecraft.world.item.ItemStack tool) {
        com.concertstage.block.Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }

    private static final VoxelShape SHAPE = Block.box(1.5, 0, 1.5, 14.5, 14.5, 14.5);

    private final int preset;

    public RoofTrussBlock(Properties properties) {
        this(properties, RoofTrussBlockEntity.PRESET_NONE);
    }

    /** {@code preset}: a roof kit (CSC SkyRoof / TitanRoof) that sets itself up as a whole roof when placed. */
    public RoofTrussBlock(Properties properties, int preset) {
        super(properties);
        this.preset = preset;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoofTrussBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                                        net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || type != com.concertstage.StageContent.ROOF_TRUSS_ENTITY) return null;
        return (l, p, s, be) -> { if (be instanceof RoofTrussBlockEntity roof) roof.serverTick(); };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RoofTrussBlockEntity be) {
            if (placer != null) be.init(placer.getDirection(), preset);
            be.rebuildCells();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RoofTrussBlockEntity be) be.clearCells();
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult hung = com.concertstage.compat.TheatricalCompat.tryHang(level, pos, player, hand, hit);
        return hung != null ? hung : super.use(state, level, pos, player, hand, hit);
    }
}
