package com.concertstage.vision;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * CSC LED accessories (1.3.0, model CSC_LED_Accessories): HANG BAR (suspension bar with two clamps for a Ø 119 mm truss tube, on top
 * of a panel), GROUND BASE (beam, stringers, jacks and braces, under a panel standing on the floor), WALL MOUNT (300 mm plate on the
 * M10 points at the back of a panel). Used on a panel: fits it, or removes it when it is already there. Fitted on the panel itself,
 * they never break a wall and they travel with the panel when a motorised structure carries it.
 */
public class LedAccessoryItem extends Item {
    public final int bit;
    public final String key;

    public LedAccessoryItem(Properties props, int bit, String key) {
        super(props);
        this.bit = bit;
        this.key = key;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof LedPanelBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() != null && !ctx.getPlayer().mayBuild()) return InteractionResult.FAIL;
        boolean on = be.toggleMount(bit);
        if (ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(Component.translatable(on ? "message.concertstage.led_mount_on" : "message.concertstage.led_mount_off",
                Component.translatable("item.concertstage." + key)), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.concertstage.led_accessory"));
    }
}
