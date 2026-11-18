package com.concertstage.item;

import com.concertstage.block.roof.RoofTrussBlock;
import com.concertstage.client.ClientHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Roof controller (CSC RoofCommand): right click the foot of a roof support or a roof frame kit to set its shape, size, sheet and orientation,
 *  or a FOH tarp roof (its shape, pitch, overhang, valance and colour). */
public class RoofToolItem extends Item {
    public RoofToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof com.concertstage.block.regie.RegieRoofBlock) {
            if (ctx.getLevel().isClientSide) ClientHooks.openRegieRoof(ctx.getClickedPos());          // FOH tarp roof
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        if (!(ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof RoofTrussBlock)) {
            if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null)
                ctx.getPlayer().displayClientMessage(Component.translatable("message.concertstage.roof_hint"), true);
            return InteractionResult.PASS;
        }
        if (ctx.getLevel().isClientSide) ClientHooks.openRoof(ctx.getClickedPos());
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
}
