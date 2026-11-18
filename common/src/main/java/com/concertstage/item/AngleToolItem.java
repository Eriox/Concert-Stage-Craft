package com.concertstage.item;

import com.concertstage.angle.AngleMath;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Angle tool (set square). Hold it in either hand while placing a truss or a motorised tower: the piece is turned by the angle shown on
 * the tool. To change the angle click in the air (not on a block), with the tool or with the truss you are about to place (the tool can stay
 * in the other hand): click = next value (10, 20, 30, 45, 60, 75, 90, then -90 ... -10, 0), sneaking + click = previous value. 0 = straight, as before.
 * A truss placed against another truss turns away from that truss by the angle (positive = clockwise seen from above); a truss that continues
 * an angled line, or that is placed on the side of a turned tower, goes the way of the line / of the tower by itself (sneak while placing to
 * add the angle as a bend); a truss on its own turns away from the direction you look at; a tower turns about its own axis. A line has no
 * length limit and is remembered until its last piece is broken.
 */
public class AngleToolItem extends Item {
    public static final String TAG = "Bend";

    public AngleToolItem(Properties properties) {
        super(properties);
    }

    /** Angle written on a tool, in degrees (0 for anything else). */
    public static int bendOf(ItemStack stack) {
        if (!(stack.getItem() instanceof AngleToolItem) || !stack.hasTag()) return 0;
        return AngleMath.clampBend(stack.getTag().getInt(TAG));
    }

    /** The tool the player holds (main hand first, then off hand), or an empty stack. */
    public static ItemStack heldTool(Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (player.getMainHandItem().getItem() instanceof AngleToolItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof AngleToolItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static boolean holding(Player player) {
        return !heldTool(player).isEmpty();
    }

    /** Angle of the tool the player holds; 0 if there is none or it is at 0. */
    public static int bendOf(Player player) {
        return bendOf(heldTool(player));
    }

    /** True if the player is pointing at a block (a click is then a placement, not a change of angle). */
    public static boolean aimingAtBlock(Level level, Player player) {
        return getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() == HitResult.Type.BLOCK;
    }

    /** One click: the next angle (the previous one when sneaking), shown in the action bar. */
    public static void cycle(Player player) {
        ItemStack tool = heldTool(player);
        if (tool.isEmpty()) return;
        int next = AngleMath.stepBend(bendOf(tool), !player.isShiftKeyDown());
        tool.getOrCreateTag().putInt(TAG, next);
        player.displayClientMessage(Component.translatable("message.concertstage.angle_set", next), true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (aimingAtBlock(level, player)) return InteractionResultHolder.pass(stack);   // let the truss in the other hand be placed
        if (player.isShiftKeyDown()) {                                                   // sneak: quick step back, as before
            if (!level.isClientSide) cycle(player);
        } else if (level.isClientSide) {
            ClientHooksAccess.openAngle();                                                 // the angle screen
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.concertstage.angle_tool.bend", bendOf(stack)));
        tooltip.add(Component.translatable("item.concertstage.angle_tool.tip1"));
        tooltip.add(Component.translatable("item.concertstage.angle_tool.tip2"));
        tooltip.add(Component.translatable("item.concertstage.angle_tool.tip3"));
    }
}

/** Keeps the client screen class out of the common item class on a dedicated server. */
final class ClientHooksAccess {
    private ClientHooksAccess() {}

    static void openAngle() {
        com.concertstage.client.ClientHooks.openAngle();
    }
}
