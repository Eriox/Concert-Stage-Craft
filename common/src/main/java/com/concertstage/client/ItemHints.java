package com.concertstage.client;

import com.concertstage.ConcertStage;
import com.concertstage.block.MotorTowerBlock;
import com.concertstage.block.MultiPartBlock;
import com.concertstage.stage.StageEquipmentItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Short technical hints for the items of the mod in the inventory (Forge: ItemTooltipEvent, Fabric: ItemTooltipCallback). */
public final class ItemHints {
    private ItemHints() {}

    public static void add(ItemStack stack, List<Component> lines) {
        var item = stack.getItem();
        var id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getNamespace().equals(ConcertStage.MOD_ID) || item instanceof StageEquipmentItem) return;
        String key = id.getPath();
        String kind = key.startsWith("truss_") ? "truss" : key.startsWith("motor_tower_") ? "tower" : key.equals("palan_chaine") ? "hoist"
            : key.startsWith("curtain_") && !key.endsWith("_tool") ? "curtain" : "tools";
        if (key.equals("stage_riser") || key.equals("stage_stairs")) kind = "compatibility";
        lines.add(Component.translatable("tooltip.concertstage.existing." + kind).withStyle(ChatFormatting.DARK_AQUA));
        if (item instanceof BlockItem bi && bi.getBlock() instanceof MultiPartBlock mp) {
            if (kind.equals("truss")) lines.add(Component.translatable("tooltip.concertstage.truss_size", mp.sizeX(), mp.sizeY()).withStyle(ChatFormatting.GRAY));
            if (kind.equals("tower") && mp instanceof MotorTowerBlock tower)
                lines.add(Component.translatable("tooltip.concertstage.tower_size", tower.towerHeight(), tower.carriage().cells).withStyle(ChatFormatting.GRAY));
        }
    }
}
