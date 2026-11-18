package com.concertstage.light;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * CSC Light Patch Tool: right click a LUMICORE X1 to choose it, right click a fixture to patch it into that desk (it gets the next
 * fixture number and a free address), sneak + right click a fixture to unpatch it, right click in the air to patch every fixture within
 * 48 blocks of the chosen desk.
 */
public class LightPatchToolItem extends Item {
    public LightPatchToolItem(Properties properties) {
        super(properties);
    }

    public static BlockPos desk(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains("DeskX") ? new BlockPos(t.getInt("DeskX"), t.getInt("DeskY"), t.getInt("DeskZ")) : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (level.isClientSide || player == null) return InteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof LumicoreBlockEntity desk) {
            if (player instanceof ServerPlayer sp && !desk.mayOperate(sp)) {
                player.displayClientMessage(Component.translatable("message.concertstage.light_no_permission"), true);
                return InteractionResult.FAIL;
            }
            CompoundTag t = stack.getOrCreateTag();
            t.putInt("DeskX", pos.getX()); t.putInt("DeskY", pos.getY()); t.putInt("DeskZ", pos.getZ());
            player.displayClientMessage(Component.translatable("message.concertstage.patch_desk_selected", pos.getX(), pos.getY(), pos.getZ()), true);
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FixtureBlockEntity fe && level instanceof ServerLevel sl) {
            if (player.isShiftKeyDown()) {
                LightServer.unpatch(sl, fe);
                player.displayClientMessage(Component.translatable("message.concertstage.patch_removed"), true);
                return InteractionResult.SUCCESS;
            }
            BlockPos d = desk(stack);
            if (d == null || !level.isLoaded(d) || !(level.getBlockEntity(d) instanceof LumicoreBlockEntity desk)) {
                player.displayClientMessage(Component.translatable("message.concertstage.patch_no_desk"), true);
                return InteractionResult.FAIL;
            }
            if (player instanceof ServerPlayer sp && !desk.mayOperate(sp)) {
                player.displayClientMessage(Component.translatable("message.concertstage.light_no_permission"), true);
                return InteractionResult.FAIL;
            }
            if (LightServer.patch(sl, desk, fe))
                player.displayClientMessage(Component.translatable("message.concertstage.patch_added", fe.fid(), desk.show.patch.size()), true);
            else player.displayClientMessage(Component.translatable("message.concertstage.patch_already", fe.fid()), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        BlockPos d = desk(stack);
        if (d == null || !level.isLoaded(d) || !(level.getBlockEntity(d) instanceof LumicoreBlockEntity)) {
            player.displayClientMessage(Component.translatable("message.concertstage.patch_no_desk"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer sp)
            LightServer.receive(sp, com.concertstage.light.net.LightNet.action(d, com.concertstage.light.net.LightNet.A_AUTOPATCH).varInt(48).toBytes());
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        BlockPos d = desk(stack);
        if (d != null) lines.add(Component.translatable("tooltip.concertstage.patch_desk", d.getX(), d.getY(), d.getZ()).withStyle(net.minecraft.ChatFormatting.AQUA));
        for (int i = 1; i <= 3; i++) lines.add(Component.translatable("tooltip.concertstage.patch_tool_" + i).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
