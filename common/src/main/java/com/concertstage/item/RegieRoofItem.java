package com.concertstage.item;

import com.concertstage.StageContent;
import com.concertstage.block.regie.RegieRoofBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * FOH tarp roof: right click any FOH element (post, node, ledger, panel, girder, rail, flight case…) and the whole FOH structure it belongs
 * to gets a black tarp roof, from the top of its highest element, with an overhang and a valance. Right click the roof (its anchor, over
 * the structure) with this item to change its shape; break the anchor to take it back.
 */
public class RegieRoofItem extends Item {
    public RegieRoofItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        if (!RegieRoofBlockEntity.isRegie(level.getBlockState(clicked))) return fail(ctx, "regie_roof_needs_regie");
        double[] b = RegieRoofBlockEntity.measure(level, clicked);
        if (b == null) return fail(ctx, "regie_roof_needs_regie");
        int y = (int) Math.ceil(b[4] - 1e-4);
        BlockPos anchor = null;
        BlockPos first = new BlockPos(clicked.getX(), y, clicked.getZ());
        if (level.getBlockState(first).canBeReplaced()) anchor = first;
        for (int x = (int) Math.floor(b[0]); anchor == null && x < Math.ceil(b[2]); x++)
            for (int z = (int) Math.floor(b[1]); anchor == null && z < Math.ceil(b[3]); z++) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getBlockState(p).canBeReplaced()) anchor = p;
            }
        // aurex.2: sneak + right click on the FOH structure takes its roof back
        for (int x = (int) Math.floor(b[0]); x < Math.ceil(b[2]); x++)
            for (int z = (int) Math.floor(b[1]); z < Math.ceil(b[3]); z++)
                if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof RegieRoofBlockEntity roof) {
                    if (ctx.getPlayer() == null || !ctx.getPlayer().isShiftKeyDown()) return fail(ctx, "regie_roof_already");
                    if (!level.isClientSide) {
                        roof.takeBack(ctx.getPlayer());
                        ctx.getPlayer().displayClientMessage(Component.translatable("message.concertstage.regie_roof_removed"), true);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
        if (anchor == null) return fail(ctx, "regie_roof_blocked");
        if (!level.isClientSide) {
            level.setBlock(anchor, StageContent.REGIE_ROOF.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(anchor) instanceof RegieRoofBlockEntity roof) roof.fit(clicked);
            level.playSound(null, anchor, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1, 0.8F);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static InteractionResult fail(UseOnContext ctx, String key) {
        if (ctx.getPlayer() != null && !ctx.getLevel().isClientSide) ctx.getPlayer().displayClientMessage(Component.translatable("message.concertstage." + key), true);
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.concertstage.regie_roof_tarp.tip1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.concertstage.regie_roof_tarp.tip2").withStyle(ChatFormatting.GRAY));
    }
}
