package com.concertstage.vision;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * CSC LED PANEL P1 (1.3.0): one 1 x 1 block LED video tile (model CSC_LED_Panel_P1: 1000 x 1027 x 120 mm, LED face, rear frame,
 * vented covers, powerCON / etherCON box, locks, pins). Adjacent panels facing the same way in the same plane form one LED wall
 * ({@link WallManager}): the wall shows one continuous image over all its panels, never one copy per panel.
 *
 * <p>Right click: wall settings (name, size, processor, source, brightness, state, diagnostics). With an accessory in hand (hang bar,
 * ground base, wall mount): fits or removes it on this panel. The panel body sits against the back of its block (LED face towards the
 * player who placed it), so a wall placed against a structure or a truss stands flush with it.
 */
public class LedPanelBlock extends VisionBlock {
    public LedPanelBlock(Properties props) {
        super(props, new double[] {0, 0, 13.9, 16, 16, 16}, null);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LedPanelBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel sl) WallManager.panelChanged(sl, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        boolean gone = !newState.is(state.getBlock());
        super.onRemove(state, level, pos, newState, moved);
        if (gone && level instanceof ServerLevel sl) WallManager.panelRemoved(sl, pos, state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LedAccessoryItem) return InteractionResult.PASS;          // the item handles it (useOn)
        if (level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof LedPanelBlockEntity be) VisionClientHooks.openWall(pos, be.wallId());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
