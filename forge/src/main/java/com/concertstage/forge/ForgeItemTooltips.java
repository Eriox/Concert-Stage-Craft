package com.concertstage.forge;

import com.concertstage.ConcertStage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Short technical hints for preserved equipment, in the standard inventory (shared with Fabric: ItemHints). */
@Mod.EventBusSubscriber(modid = ConcertStage.MOD_ID, value = Dist.CLIENT)
public final class ForgeItemTooltips {
    private ForgeItemTooltips() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        com.concertstage.client.ItemHints.add(event.getItemStack(), event.getToolTip());
    }
}
