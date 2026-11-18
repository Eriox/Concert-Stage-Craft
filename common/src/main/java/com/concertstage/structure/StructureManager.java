package com.concertstage.structure;

import com.concertstage.StageContent;
import com.concertstage.block.Drops;
import com.concertstage.block.roof.RoofTrussBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the CSC Structure Manager: removes a whole stage roof in one action once the player confirmed it on the screen.
 * Everything is checked again here (the tool in hand, distance, building rights on every foot, and that the roof is still the one
 * the player saw); the feet go back to the player (nothing in creative), every cell goes, left-over cells too.
 */
public final class StructureManager {
    public static final int ACTION_DELETE = 0;

    private StructureManager() {}

    public static void handle(ServerPlayer player, BlockPos clicked, int action, int mode, int signature) {
        if (action != ACTION_DELETE) return;
        ServerLevel level = player.serverLevel();
        if (!(player.getMainHandItem().is(StageContent.STRUCTURE_TOOL) || player.getOffhandItem().is(StageContent.STRUCTURE_TOOL))) return;
        if (!level.isLoaded(clicked) || player.distanceToSqr(Vec3.atCenterOf(clicked)) > 48 * 48) return;
        if (!player.mayBuild()) { player.displayClientMessage(Component.translatable("message.concertstage.structure_denied"), true); return; }
        RoofStructure s = RoofStructure.find(level, clicked, RoofStructure.SINGLE);           // one frame / kit at a time
        if (s == null || s.signature() != signature) {
            player.displayClientMessage(Component.translatable("message.concertstage.structure_changed").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        for (BlockPos f : s.feet) {
            if (!level.mayInteract(player, f)) { player.displayClientMessage(Component.translatable("message.concertstage.structure_denied"), true); return; }
        }
        Drops.breaking(level, player);
        int feet = 0, cells = s.cells.size() + s.orphans.size();
        for (BlockPos f : s.feet) {
            if (!(level.getBlockEntity(f) instanceof RoofTrussBlockEntity roof)) continue;
            ItemStack back = new ItemStack(level.getBlockState(f).getBlock().asItem());
            roof.clearCells();
            level.setBlock(f, Blocks.AIR.defaultBlockState(), 3);
            Drops.give(level, f, back);
            feet++;
        }
        for (BlockPos c : s.cells) if (level.getBlockState(c).getBlock() instanceof com.concertstage.block.ring.RingTrussCellBlock) level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos c : s.orphans) if (level.getBlockState(c).getBlock() instanceof com.concertstage.block.ring.RingTrussCellBlock) level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, clicked, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.6f);
        player.displayClientMessage(s.orphanOnly
            ? Component.translatable("message.concertstage.structure_cleaned", cells).withStyle(ChatFormatting.GREEN)
            : Component.translatable("message.concertstage.structure_removed", feet, cells).withStyle(ChatFormatting.GREEN), true);
    }
}
