package com.concertstage.audio;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** CSC Array Tool: right click a line array or a sub stack to open its rigging screen (yaw, tilt, splay of each cabinet). */
public class ArrayToolItem extends Item {
    public ArrayToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        BlockPos array = SpeakerItem.arrayAt(ctx.getLevel(), ctx.getClickedPos());
        if (array == null) {
            if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null)
                ctx.getPlayer().displayClientMessage(Component.translatable("message.concertstage.array_hint"), true);
            return InteractionResult.PASS;
        }
        if (ctx.getLevel().isClientSide) com.concertstage.client.ClientHooks.openArray(array);
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.concertstage.array_tool").withStyle(ChatFormatting.GRAY));
    }
}
