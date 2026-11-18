package com.concertstage.item;

import com.concertstage.block.ring.RingTrussBlock;
import com.concertstage.client.ClientHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Ring controller: right click a circular truss to open its control screen (radius, wheel tilt, rotation, finish). */
public class RingToolItem extends Item {
    public RingToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof RingTrussBlock)) {
            if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null)
                ctx.getPlayer().displayClientMessage(Component.translatable("message.concertstage.ring_hint"), true);
            return InteractionResult.PASS;
        }
        if (ctx.getLevel().isClientSide) ClientHooks.openRing(ctx.getClickedPos());
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
}
