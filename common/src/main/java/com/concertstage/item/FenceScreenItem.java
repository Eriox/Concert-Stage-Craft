package com.concertstage.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Black knitted HDPE fence screen 1.76 × 3.41 m, eyelets every 50 cm: fitted on a CSC SiteFence by right-clicking it. */
public class FenceScreenItem extends Item {
    public FenceScreenItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.concertstage.fence_screen_black"));
    }
}
