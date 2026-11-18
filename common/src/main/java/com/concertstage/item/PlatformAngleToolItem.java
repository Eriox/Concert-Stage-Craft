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
 * Platform protractor (CSC DeckAngle). Hold it in either hand while placing a deck, a riser or a platform: the platform is turned by the
 * angle shown on the tool (positive = clockwise seen from above), around the cell clicked. Click in the air to open its screen,
 * sneak + click in the air for the next angle. 0 = straight, as before. Separate from the angle square, which only turns trusses and towers.
 */
public class PlatformAngleToolItem extends Item {
    public static final String TAG = "PlatformAngle";

    public PlatformAngleToolItem(Properties properties) {
        super(properties);
    }

    public static int angleOf(ItemStack stack) {
        if (!(stack.getItem() instanceof PlatformAngleToolItem) || !stack.hasTag()) return 0;
        return AngleMath.clampBend(stack.getTag().getInt(TAG));
    }

    public static ItemStack heldTool(Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (player.getMainHandItem().getItem() instanceof PlatformAngleToolItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof PlatformAngleToolItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static int angleOf(Player player) {
        return angleOf(heldTool(player));
    }

    public static void set(Player player, int angle) {
        ItemStack tool = heldTool(player);
        if (tool.isEmpty()) return;
        int a = AngleMath.clampBend(angle);
        tool.getOrCreateTag().putInt(TAG, a);
        player.displayClientMessage(Component.translatable("message.concertstage.platform_angle_set", a), true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() == HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) set(player, AngleMath.stepBend(angleOf(stack), true));
        } else if (level.isClientSide) {
            PlatformAngleClient.open();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.concertstage.platform_angle_tool.angle", angleOf(stack)));
        tooltip.add(Component.translatable("item.concertstage.platform_angle_tool.tip1"));
        tooltip.add(Component.translatable("item.concertstage.platform_angle_tool.tip2"));
    }
}

/** Keeps the client screen class out of the common item class on a dedicated server. */
final class PlatformAngleClient {
    private PlatformAngleClient() {}

    static void open() {
        com.concertstage.client.ClientHooks.openPlatformAngle();
    }
}
