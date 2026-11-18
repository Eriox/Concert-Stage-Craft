package com.concertstage.light;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Rev. 1.3.0-2 — CSC CONFIGURATION CARD (asked 12 November, 22 h 38, picture 4): links the Art-Net console (or a LUMICORE X1) to the
 * fixtures and gives each fixture its address from the patch chosen on the card.
 * <ul>
 *   <li>right click a console: the card is linked to it;</li>
 *   <li>right click in the air: the card screen (universe, DMX mode, start address, address auto increment, save);</li>
 *   <li>right click a fixture: it is patched on the linked console at the address of the card, in the mode of the card; with auto
 *   increment the card moves on to the next free address (start + footprint of the fixture in that mode).</li>
 * </ul>
 * See {@link QuickPatch}.
 */
public class ConfigCardItem extends Item {
    public ConfigCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) return InteractionResult.PASS;
        var be = level.getBlockEntity(pos);
        if (!(be instanceof LumicoreBlockEntity) && !(be instanceof FixtureBlockEntity)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (be instanceof LumicoreBlockEntity desk) {
            if (player instanceof ServerPlayer sp && !desk.mayOperate(sp)) {
                player.displayClientMessage(Component.translatable("message.concertstage.light_no_permission"), true);
                return InteractionResult.FAIL;
            }
            QuickPatch.link(stack, pos);
            player.displayClientMessage(Component.translatable("message.concertstage.card_linked", pos.getX(), pos.getY(), pos.getZ()), true);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) QuickPatch.cardOnFixture(sl, sp, stack, (FixtureBlockEntity) be);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) com.concertstage.client.ClientHooks.openConfigCard(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        QuickPatch.Card c = QuickPatch.card(stack);
        if (c.console() != null) lines.add(Component.translatable("tooltip.concertstage.card_console", c.console().getX(), c.console().getY(), c.console().getZ()).withStyle(ChatFormatting.AQUA));
        else lines.add(Component.translatable("tooltip.concertstage.card_unlinked").withStyle(ChatFormatting.RED));
        lines.add(Component.translatable("tooltip.concertstage.card_next", c.universe(), c.address(),
            c.mode() == QuickPatch.MODE_KEEP ? Component.translatable("gui.concertstage.card.mode_keep") : Component.literal(com.concertstage.light.dmx.DmxMode.NAMES[c.mode()]),
            Component.translatable(c.autoInc() ? "options.on" : "options.off")).withStyle(ChatFormatting.GRAY));
        for (int i = 1; i <= 3; i++) lines.add(Component.translatable("tooltip.concertstage.card_" + i).withStyle(ChatFormatting.DARK_GRAY));
    }
}
