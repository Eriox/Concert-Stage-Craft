package com.concertstage.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * CSC Structure Manager (1.0.2): right click any block of a roof frame (its foot, the frame or its sheet): the frame (or the whole kit)
 * with all its blocks is selected (outlined) and a screen shows what it is made of; it is removed only after a confirmation. Also cleans the invisible cells
 * an older version left behind.
 */
public class StructureManagerItem extends Item {
    public StructureManagerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        com.concertstage.structure.RoofStructure s = com.concertstage.structure.RoofStructure.find(level, ctx.getClickedPos(), com.concertstage.structure.RoofStructure.SINGLE);
        if (s == null) {
            if (!level.isClientSide && ctx.getPlayer() != null)
                ctx.getPlayer().displayClientMessage(Component.translatable("message.concertstage.structure_hint"), true);
            return InteractionResult.PASS;
        }
        if (level.isClientSide) com.concertstage.client.ClientHooks.openStructure(ctx.getClickedPos());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.concertstage.structure_tool").withStyle(ChatFormatting.GRAY));
    }
}
