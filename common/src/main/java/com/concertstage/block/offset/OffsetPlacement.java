package com.concertstage.block.offset;

import com.concertstage.ConcertStage;
import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Placing any block (another mod's or vanilla) on the floor of a stage piece whose floor is not at a block boundary (a deck at
 * 2.2 m, a 40 cm deck, a riser...): instead of the next whole cell, with a gap, the block is held in an {@link OffsetBlock} and moved down onto
 * the floor. Also on the top or the side of such a block, so rows and stacks stay level. Blocks with a block entity (speakers, mixers, lights...) need to be real blocks to work (link a
 * speaker, open a menu): they are placed as usual, unless the player sneaks, which lowers them as decorations (drawn by their own
 * renderer, no function). Sneaking with a plain block places it as usual. Doors and beds are placed as usual.
 */
public final class OffsetPlacement {
    private OffsetPlacement() {}

    /** The block entity a block of another mod would have, with the data carried by the item (its name, its settings), saved; null if none. */
    private static net.minecraft.nbt.CompoundTag heldData(BlockState state, BlockPos pos, ItemStack stack) {
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb)) return null;
        try {
            net.minecraft.world.level.block.entity.BlockEntity be = eb.newBlockEntity(pos, state);
            if (be == null) return null;
            net.minecraft.nbt.CompoundTag fromItem = BlockItem.getBlockEntityData(stack);
            if (fromItem != null) be.load(fromItem);
            return be.saveWithFullMetadata();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return PASS when it does not apply (normal placement goes on). */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem item) || player.isSpectator()) return InteractionResult.PASS;
        Block block = item.getBlock();
        if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(ConcertStage.MOD_ID)) return InteractionResult.PASS;   // ours place themselves
        BlockPos clicked = hit.getBlockPos();
        BlockState cs = level.getBlockState(clicked);
        boolean holder = cs.getBlock() instanceof OffsetBlock;
        if (!holder && !BuiltInRegistries.BLOCK.getKey(cs.getBlock()).getNamespace().equals(ConcertStage.MOD_ID)) return InteractionResult.PASS;
        Direction face = hit.getDirection();
        BlockPos target;
        double drop;
        if (face == Direction.UP) {
            double top = hit.getLocation().y - clicked.getY();          // height of the floor clicked, in its cell
            if (top <= 0.02 || top >= 0.98) return InteractionResult.PASS;
            target = clicked.above();
            drop = 1 - top;
        } else if (holder && face.getAxis().isHorizontal() && level.getBlockEntity(clicked) instanceof OffsetBlockEntity be) {
            target = clicked.relative(face);                             // next to a lowered block: same level
            drop = be.drop();
        } else {
            return InteractionResult.PASS;
        }
        if (drop < 0.02) return InteractionResult.PASS;
        if (!level.getBlockState(target).canBeReplaced()) return InteractionResult.PASS;
        BlockState state = block.getStateForPlacement(new BlockPlaceContext(player, hand, stack, hit));
        if (state == null || state.isAir() || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            || state.hasProperty(BlockStateProperties.BED_PART)) return InteractionResult.PASS;
        // only plain Minecraft blocks are lowered by default: a block of another mod may have a function (a S.E.F speaker linked with the
        // wrench, a mixer, a light...) that needs the real block, so it is placed as usual; sneaking lowers it as a decoration (no function).
        // Sneaking with a Minecraft block places it as usual.
        boolean plain = !state.hasBlockEntity() && BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
        if (plain == player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!player.mayUseItemAt(target, face, stack)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(target, StageContent.OFFSET_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(target) instanceof OffsetBlockEntity be) be.set(state, drop, heldData(state, target, stack));
            SoundType sound = state.getSoundType();
            level.playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
