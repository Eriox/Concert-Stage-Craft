package com.concertstage.vision;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * CSC VISION PROCESSOR VP1 (1.3.0, model CSC_Vision_Processor_VP1: 19" 2U rack, front screen, encoder, buttons, LEDs; rear powerCON,
 * etherCON, BNC, fan). Right click: the LiveVision regie (cameras, media, PREVIEW / PROGRAM, TAKE, transitions, outputs, automatic
 * switcher). Its front screen shows the state of output 1.
 */
public class VisionProcessorBlock extends VisionBlock {
    public VisionProcessorBlock(Properties props) {
        super(props, new double[] {4, 0, 4.2, 12, 1.6, 11.8}, null);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VisionProcessorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StageContent.VISION_PROCESSOR_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> t = (BlockEntityTicker<T>) (BlockEntityTicker<VisionProcessorBlockEntity>) VisionProcessorBlockEntity::serverTick;
        return t;
    }

    /** Rev. 2: placed, it takes the walls and the camera around it (VisionAutoLink). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl && level.getBlockEntity(pos) instanceof VisionProcessorBlockEntity be)
            VisionAutoLink.processorPlaced(sl, be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            VisionClientHooks.openRegie(pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
