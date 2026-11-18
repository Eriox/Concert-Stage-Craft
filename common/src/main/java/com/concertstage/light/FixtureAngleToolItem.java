package com.concertstage.light;

import com.concertstage.StageContent;
import com.concertstage.light.core.Attr;
import java.util.List;
import net.minecraft.network.chat.Component;
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
 * CSC Fixture Angle Tool (rev. 24, screen in rev. 25): sets the angle of a fixture that has no tilt motor (BLINDER X2 / X4 / X8,
 * FLOODLIGHT F8, PIXELBAR, PIXELTUBE...), the way a technician tilts a blinder on its bracket.
 * <ul>
 * <li>Right click the fixture: the angle screen (protractor, -15 ... +15 buttons, preset angles, Copy, Paste).</li>
 * <li>Sneak + right click a fixture: pastes the copied angle at once (to set a whole row of blinders quickly).</li>
 * <li>The copied angle is kept in the tool.</li>
 * </ul>
 */
public class FixtureAngleToolItem extends Item {
    public static final String COPIED = "CopiedTilt", COPIED_POSE = "CopiedPose";
    /** Messages of the screen to the server (StageNetwork.handleTarpSettings): set the tilt, copy it into the tool. */
    public static final int OP_SET = 0, OP_COPY = 1;
    /** 1.2.1, free placement (FixturePoseScreen): offset, turn, copy / paste / reset of the pose. */
    public static final int OP_POSE_POS = 2, OP_POSE_ROT = 3, OP_POSE_COPY = 4, OP_POSE_PASTE = 5, OP_POSE_RESET = 6;
    /** 1.2.1: the gel of a conventional (value = RGB). */
    public static final int OP_GEL = 7;

    /** Offsets in centimetres (±200), 10 bits each. */
    public static int packPos(float x, float y, float z) {
        return cm(x) | cm(y) << 10 | cm(z) << 20;
    }

    private static int cm(float v) {
        return Math.max(0, Math.min(400, Math.round(v * 100f) + 200));
    }

    private static float uncm(int v) {
        return ((v & 1023) - 200) / 100f;
    }

    /** Yaw and pitch in half degrees. */
    public static int packRot(float yaw, float pitch) {
        return Math.max(0, Math.min(720, Math.round(yaw * 2f) + 360)) | Math.max(0, Math.min(360, Math.round(pitch * 2f) + 180)) << 10;
    }

    public static int packRoll(float roll) {
        return Math.max(0, Math.min(720, Math.round(roll * 2f) + 360));
    }

    public FixtureAngleToolItem(Properties properties) {
        super(properties);
    }

    /** The new tilt after one click (FixtureTilt, pure and tested in tools/rev24). */
    public static float next(float tilt, int step, boolean down) {
        return com.concertstage.light.core.FixtureTilt.next(tilt, step, down);
    }

    /** The angle copied in the tool, or NaN. */
    public static float copied(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(COPIED) ? stack.getTag().getFloat(COPIED) : Float.NaN;
    }

    public static ItemStack held(Player player) {
        if (player.getMainHandItem().is(StageContent.FIXTURE_ANGLE_TOOL)) return player.getMainHandItem();
        if (player.getOffhandItem().is(StageContent.FIXTURE_ANGLE_TOOL)) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** Server side of the screen: {@code value} = tilt x 10 + 900 (tenths of a degree). Checked here, never trusted. */
    public static void handle(ServerPlayer player, FixtureBlockEntity be, int op, int value) {
        handle(player, be, op, value, 0);
    }

    public static void handle(ServerPlayer player, FixtureBlockEntity be, int op, int value, int extra) {
        ItemStack tool = held(player);
        if (tool.isEmpty() || be.block() == null) return;
        // 1.2.1: the pose of any fixture (moving heads too)
        switch (op) {
            case OP_POSE_POS -> { be.setPose(uncm(value), uncm(value >> 10), uncm(value >> 20), be.rotYaw, be.rotPitch, be.rotRoll); return; }
            case OP_POSE_ROT -> {
                be.setPose(be.offX, be.offY, be.offZ, ((value & 1023) - 360) / 2f, (((value >> 10) & 1023) - 180) / 2f, ((extra & 1023) - 360) / 2f);
                return;
            }
            case OP_POSE_RESET -> { be.setPose(0, 0, 0, 0, 0, 0); return; }
            case OP_POSE_COPY -> {
                tool.getOrCreateTag().putIntArray(COPIED_POSE, new int[] {packPos(be.offX, be.offY, be.offZ), packRot(be.rotYaw, be.rotPitch), packRoll(be.rotRoll)});
                player.displayClientMessage(Component.translatable("message.concertstage.pose_copied"), true);
                return;
            }
            case OP_POSE_PASTE -> { pastePose(player, tool, be); return; }
            case OP_GEL -> { if (be.block().profile().conventional) be.setGel(value & 0xFFFFFF); return; }
            default -> { }
        }
        if (be.block().profile().has(Attr.TILT)) return;
        float tilt = Math.max(-90f, Math.min(90f, (value - 900) / 10f));
        if (op == OP_SET) be.setManualTilt(upSign(be) * tilt);
        else if (op == OP_COPY) {
            tool.getOrCreateTag().putFloat(COPIED, tilt);
            player.displayClientMessage(Component.translatable("message.concertstage.angle_tool_copied", String.format(java.util.Locale.ROOT, "%+.1f", tilt)), true);
        }
    }

    /**
     * 1.2.1: the angle of the screen is "+ = up" for a hung fixture too (its head is turned upside down, so its own tilt runs the other
     * way: a hung PAR set to -40° pointed up). The stored tilt keeps its meaning (blinders hung before keep their direction).
     */
    static float upSign(FixtureBlockEntity be) {
        return be.getBlockState().getValue(FixtureBlock.HUNG) ? -1f : 1f;
    }

    /** 1.2.1: the pose copied in the tool on this fixture. False when the tool has none. */
    static boolean pastePose(Player player, ItemStack tool, FixtureBlockEntity be) {
        if (!tool.hasTag() || !tool.getTag().contains(COPIED_POSE)) return false;
        int[] p = tool.getTag().getIntArray(COPIED_POSE);
        if (p.length < 3) return false;
        be.setPose(uncm(p[0]), uncm(p[0] >> 10), uncm(p[0] >> 20), ((p[1] & 1023) - 360) / 2f, (((p[1] >> 10) & 1023) - 180) / 2f, ((p[2] & 1023) - 360) / 2f);
        player.displayClientMessage(Component.translatable("message.concertstage.pose_pasted", be.block().profile().name), true);
        return true;
    }

    static boolean hasPose(ItemStack tool) {
        return tool.hasTag() && tool.getTag().contains(COPIED_POSE);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof FixtureBlockEntity be) || be.block() == null || player == null) return InteractionResult.PASS;
        var profile = be.block().profile();
        // 1.2.1: a fixture with a tilt motor gets the position screen (free placement); sneaking pastes the copied pose
        if (profile.has(Attr.TILT)) {
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide && !pastePose(player, ctx.getItemInHand(), be))
                    player.displayClientMessage(Component.translatable("message.concertstage.angle_tool_nothing"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (level.isClientSide) FixtureAngleClient.openPose(ctx.getClickedPos(), profile.name, hasPose(ctx.getItemInHand()));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {                                       // paste the copied angle (and the copied pose, 1.2.1)
            if (!level.isClientSide) {
                float c = copied(ctx.getItemInHand());
                boolean pose = pastePose(player, ctx.getItemInHand(), be);
                if (Float.isNaN(c)) { if (!pose) player.displayClientMessage(Component.translatable("message.concertstage.angle_tool_nothing"), true); }
                else {
                    be.setManualTilt(upSign(be) * c);
                    player.displayClientMessage(Component.translatable("message.concertstage.angle_tool_pasted", profile.name, String.format(java.util.Locale.ROOT, "%+.1f", c)), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) FixtureAngleClient.open(ctx.getClickedPos(), upSign(be) * be.manualTilt, profile.name, copied(ctx.getItemInHand()));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            float c = copied(stack);
            player.displayClientMessage(Float.isNaN(c) ? Component.translatable("message.concertstage.angle_tool_nothing")
                : Component.translatable("message.concertstage.angle_tool_step", String.format(java.util.Locale.ROOT, "%+.1f", c)), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        float c = copied(stack);
        tooltip.add(Component.translatable("tooltip.concertstage.angle_tool", Float.isNaN(c) ? "-" : String.format(java.util.Locale.ROOT, "%+.1f°", c)));
    }
}

/** Keeps the client screen class out of the common item class on a dedicated server. */
final class FixtureAngleClient {
    private FixtureAngleClient() {}

    static void open(net.minecraft.core.BlockPos pos, float tilt, String name, float copied) {
        com.concertstage.client.light.FixtureAngleScreen.open(pos, tilt, name, copied);
    }

    static void openPose(net.minecraft.core.BlockPos pos, String name, boolean copied) {
        com.concertstage.client.light.FixturePoseScreen.open(pos, name, copied);
    }
}
