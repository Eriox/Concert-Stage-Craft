package com.concertstage.block;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Temporary fence panel (CSC SiteFence) with or without its black knitted screen: right-click the bare fence with a fence screen to
 * fit it (eyelets tied to the frame), right-click the screened fence with shears to take the screen off (it drops).
 */
public class FenceScreenBlock extends MultiPartBlock {
    private final boolean screened;

    public FenceScreenBlock(Properties properties, int sx, int sy, int sz, boolean screened, ShapeProvider shape) {
        super(properties, sx, sy, sz, 0, 0, 0, Placement.FRONT, false, shape);
        this.screened = screened;
    }

    public boolean isScreened() {
        return screened;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!screened && held.is(StageContent.FENCE_SCREEN)) {
            if (!level.isClientSide) {
                swapStructure(level, pos, state, (MultiPartBlock) StageContent.FENCE_SITE_SCREEN);
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (screened && held.is(Items.SHEARS)) {
            if (!level.isClientSide) {
                swapStructure(level, pos, state, (MultiPartBlock) StageContent.FENCE_SITE);
                if (!player.getAbilities().instabuild) held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                Block.popResourceFromFace(level, pos, hit.getDirection(), new ItemStack(StageContent.FENCE_SCREEN));
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
