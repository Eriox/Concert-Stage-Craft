package com.concertstage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a player breaks goes straight into the player's inventory (no item lying on the deck); only what does not fit falls on the
 * ground. Also for the pieces given back when a stage piece goes (deck attachments, angled trusses...).
 */
public final class Drops {
    private static Player breaker;
    private static long breakTick = Long.MIN_VALUE;

    private Drops() {}

    /** Called when a player starts breaking one of our blocks: the drops of this tick go to that player. */
    public static void breaking(Level level, Player player) {
        if (level.isClientSide) return;
        breaker = player;
        breakTick = level.getGameTime();
    }

    private static Player current(Level level) {
        if (breaker == null || level.isClientSide || level.getGameTime() != breakTick || breaker.level() != level) return null;
        return breaker;
    }

    /** One stack to give back: to the player breaking (nothing in creative), else dropped. */
    public static void give(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || level.isClientSide) return;
        Player p = current(level);
        if (p != null) {
            if (p.getAbilities().instabuild) return;
            if (p.getInventory().add(stack) && stack.isEmpty()) return;
        }
        Block.popResource(level, pos, stack);
    }

    /** Replaces {@link Block#playerDestroy}: same stats and drops, put in the inventory. */
    public static void playerDestroy(Block block, Level level, Player player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(block));
        player.causeFoodExhaustion(0.005F);
        if (!(level instanceof ServerLevel server)) return;
        breaking(level, player);
        for (ItemStack s : Block.getDrops(state, server, pos, be, player, tool)) give(level, pos, s);
        state.spawnAfterBreak(server, pos, tool, true);
    }
}
