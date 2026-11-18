package com.concertstage.item;

import com.concertstage.block.CurtainClothBlock;
import com.concertstage.block.CurtainRailBlock;
import com.concertstage.client.ClientHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Tarp Tool. Right click on a curtain rail, a tarp frame or any piece of their cloth: opens a screen to set the
 * length and the height / width (1 to 50 blocks), and for curtains the position of the cloth on the truss.
 */
public class CurtainToolItem extends Item {
    public CurtainToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.concertstage.tool_hint"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        Block block = level.getBlockState(ctx.getClickedPos()).getBlock();
        boolean ours = block instanceof CurtainRailBlock || block instanceof CurtainClothBlock
            || block instanceof com.concertstage.block.tarp.StageTarpRailBlock || block instanceof com.concertstage.block.tarp.StageTarpTopBlock
            || block instanceof com.concertstage.block.tarp.StageTarpClothBlock;
        if (player == null || !ours) return InteractionResult.PASS;
        if (level.isClientSide) ClientHooks.openTarpTool(ctx.getClickedPos());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
