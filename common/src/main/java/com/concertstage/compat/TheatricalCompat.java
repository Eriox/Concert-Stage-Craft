package com.concertstage.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Hanging Theatrical / Theatrical: Extra Lights fixtures on Concert Stage trusses, towers, ring points and FOH girders, the way
 * Theatrical's own truss does it (click the side of the truss: the fixture hangs under it; click its top / bottom: it hangs
 * beside it, towards you). No compile dependency: a fixture is any block whose class extends Theatrical's
 * {@code dev.imabad.theatrical.blocks.HangableBlock}; its states are set through their property names.
 */
public final class TheatricalCompat {
    private static final String HANGABLE = "dev.imabad.theatrical.blocks.HangableBlock";

    private TheatricalCompat() {}

    public static boolean isFixture(Block block) {
        for (Class<?> c = block.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().equals(HANGABLE)) return true;
        }
        return false;
    }

    /** @return the result when a fixture was hung (or refused), null when the item in hand is not a Theatrical fixture. */
    public static InteractionResult tryHang(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem item) || !isFixture(item.getBlock())) return null;
        Direction face = hit.getDirection();
        BlockPos at;
        Direction hang;
        if (face.getAxis() == Direction.Axis.Y) {
            at = pos.relative(player.getDirection().getOpposite());
            hang = player.getDirection();
        } else {
            at = pos.below();
            hang = Direction.UP;
        }
        if (!level.getBlockState(at).isAir()) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState s = item.getBlock().defaultBlockState();
        s = with(s, "facing", player.getDirection().getName());
        s = with(s, "hanging", "true");
        s = with(s, "hang_direction", hang.getName());
        level.setBlock(at, s, Block.UPDATE_CLIENTS);
        item.getBlock().setPlacedBy(level, at, s, player, stack);       // Theatrical gives the fixture the player's DMX network here
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    private static BlockState with(BlockState s, String name, String value) {
        Property<?> p = s.getBlock().getStateDefinition().getProperty(name);
        return p == null ? s : set(s, p, value);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState s, Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }
}
