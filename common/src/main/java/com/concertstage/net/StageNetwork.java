package com.concertstage.net;

import com.concertstage.ConcertStage;
import com.concertstage.StageContent;
import com.concertstage.block.CurtainRailBlockEntity;
import com.concertstage.block.TowerBlockEntity;
import com.concertstage.item.RemoteItem;
import com.concertstage.lift.LiftControl;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-agnostic part of the Tarp Tool network message (client -> server).
 * Forge and Fabric only have to carry the four values and call {@link #handleTarpSettings}.
 */
public final class StageNetwork {
    public static final ResourceLocation TARP_SETTINGS = new ResourceLocation(ConcertStage.MOD_ID, "tarp_settings");
    public static final ResourceLocation REMOTE_COMMAND = new ResourceLocation(ConcertStage.MOD_ID, "remote_command");
    /** CSC Structure Manager (1.0.2): client -> server, remove the confirmed roof (pos, action, mode, signature). */
    public static final ResourceLocation STRUCTURE = new ResourceLocation(ConcertStage.MOD_ID, "structure");

    /** Implemented by each loader on the client side (Structure Manager). */
    public interface StructureSender {
        void send(BlockPos pos, int action, int mode, int signature);
    }

    private static StructureSender structureSender;

    public static void setStructureSender(StructureSender s) {
        structureSender = s;
    }

    public static void sendStructure(BlockPos pos, int action, int mode, int signature) {
        if (structureSender != null) structureSender.send(pos, action, mode, signature);
    }

    public static void handleStructure(ServerPlayer player, BlockPos pos, int action, int mode, int signature) {
        com.concertstage.structure.StructureManager.handle(player, pos, action, mode, signature);
    }

    public static final int REMOTE_GOTO = 0;
    public static final int REMOTE_UP = 1;
    public static final int REMOTE_DOWN = 2;
    public static final int REMOTE_STOP = 3;
    public static final int REMOTE_CLEAR = 4;
    /** Group of a remote command, in bits 16-17 of the value: 0 = everything linked, 1 = truss towers only, 2 = chain hoists only. */
    public static final int GROUP_ALL = 0, GROUP_TOWERS = 1, GROUP_HOISTS = 2;
    /** Set the group of one linked motor: value = index in the remote's list (bits 0-11) | group 1..13 (bits 12-15). */
    public static final int REMOTE_SET_GROUP = 5;
    /** Angle tool: value = angle + 180. */
    public static final int REMOTE_ANGLE = 6;
    public static final int REMOTE_PLATFORM_ANGLE = 7;

    /** Remote value with the tab (bits 16-17) and the motor group 0 = all, 1..12 (bits 18-22). */
    public static int withGroup(int value, int tab, int motorGroup) {
        return (value & 0xFFFF) | (tab << 16) | (motorGroup << 18);
    }

    public static int withGroup(int value, int group) {
        return (value & 0xFFFF) | (group << 16);
    }

    /** True when the motor at {@code p} belongs to {@code group}. Unloaded positions belong to every group. */
    public static boolean inGroup(net.minecraft.world.level.BlockGetter level, BlockPos p, int group) {
        if (group == GROUP_ALL) return true;
        boolean hoist = level.getBlockState(p).getBlock() instanceof com.concertstage.block.HoistBlock;
        if (!(level.getBlockState(p).getBlock() instanceof com.concertstage.block.MotorTowerBlock)) return true;
        return group == GROUP_HOISTS ? hoist : !hoist;
    }

    /** Implemented by each loader on the client side (lift remote). */
    public interface RemoteSender {
        void send(int action, int value);
    }

    private static RemoteSender remoteSender;

    /** Implemented by each loader on the client side. */
    public interface Sender {
        void send(BlockPos pos, int length, int second, int shift);
    }

    private static Sender sender;

    private StageNetwork() {}

    public static void setSender(Sender s) {
        sender = s;
    }

    public static void setRemoteSender(RemoteSender s) {
        remoteSender = s;
    }

    /** Client side: a button of the lift remote screen. {@code value} is the height in blocks for REMOTE_GOTO. */
    public static void sendRemote(int action, int value) {
        if (remoteSender != null) remoteSender.send(action, value);
    }

    /** Client side: asks the server to resize a curtain / tarp. {@code second} = height (curtain) or width (tarp). */
    public static void sendTarpSettings(BlockPos pos, int length, int second, int shift) {
        if (sender != null) sender.send(pos, length, second, shift);
    }

    /** Server side. Checks everything the client says before touching the world. */
    public static void handleTarpSettings(ServerPlayer player, BlockPos pos, int length, int second, int shift) {
        Level level = player.level();
        // rev. 25: the fixture angle screen uses the same small message: length = operation, second = tilt x 10 + 900
        if (level.hasChunkAt(pos) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0 * 64.0
            && level.getBlockEntity(pos) instanceof com.concertstage.light.FixtureBlockEntity fx) {
            com.concertstage.light.FixtureAngleToolItem.handle(player, fx, length, second, shift);
            return;
        }
        if (level.hasChunkAt(pos) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0 * 64.0
            && level.getBlockEntity(pos) instanceof com.concertstage.block.ring.RingTrussBlockEntity ring) {
            // the ring controller uses the same small message: length = radius (half metres), second = tilt, shift = rotation | finish
            if (player.getMainHandItem().is(StageContent.RING_TOOL) || player.getOffhandItem().is(StageContent.RING_TOOL)) ring.apply(length, second, shift);
            return;
        }
        if (level.hasChunkAt(pos) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0 * 64.0
            && level.getBlockEntity(pos) instanceof com.concertstage.block.roof.RoofTrussBlockEntity roof) {
            // roof support: length = span, second = rise (half metres), shift = shape | direction << 2 | finish
            if (player.getMainHandItem().is(StageContent.ROOF_TOOL) || player.getOffhandItem().is(StageContent.ROOF_TOOL)) roof.apply(length, second, shift);
            return;
        }
        if (level.hasChunkAt(pos) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0 * 64.0
            && level.getBlockEntity(pos) instanceof com.concertstage.block.regie.RegieRoofBlockEntity foh) {
            // FOH tarp roof: length = overhang (tenths of a metre), second = valance (5 cm), shift = shape | pitch << 2 | colour << 5
            if (player.getMainHandItem().is(StageContent.ROOF_TOOL) || player.getOffhandItem().is(StageContent.ROOF_TOOL)) foh.apply(length, second, shift);
            return;
        }
        if (!player.getMainHandItem().is(StageContent.CURTAIN_TOOL) && !player.getOffhandItem().is(StageContent.CURTAIN_TOOL)) return;
        if (!level.hasChunkAt(pos)) return;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0 * 64.0) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.concertstage.block.tarp.StageTarpRailBlockEntity tarp) {
            tarp.applySettings(length, second, shift);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.concertstage.tarp_size", tarp.getLength(), tarp.getHeight()), true);
            return;
        }
        if (be instanceof CurtainRailBlockEntity rail) {
            rail.applySettings(length, second, shift);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.concertstage.curtain_size", rail.getLength(), rail.getHeight()), true);
        }
    }

    /** Server side of the lift remote. The linked towers come from the remote the player holds, never from the packet. */
    public static void handleRemote(ServerPlayer player, int action, int value) {
        if (action == REMOTE_PLATFORM_ANGLE) {                        // platform protractor screen
            com.concertstage.item.PlatformAngleToolItem.set(player, (value & 0xFFFF) - 180);
            return;
        }
        if (action == REMOTE_ANGLE) {                                 // angle tool screen
            ItemStack tool = com.concertstage.item.AngleToolItem.heldTool(player);
            if (!tool.isEmpty()) {
                int angle = com.concertstage.angle.AngleMath.clampBend((value & 0xFFFF) - 180);
                tool.getOrCreateTag().putInt(com.concertstage.item.AngleToolItem.TAG, angle);
                player.displayClientMessage(Component.translatable("message.concertstage.angle_set", angle), true);
            }
            return;
        }
        ItemStack stack = player.getMainHandItem().getItem() instanceof RemoteItem ? player.getMainHandItem()
            : player.getOffhandItem().getItem() instanceof RemoteItem ? player.getOffhandItem() : ItemStack.EMPTY;
        if (stack.isEmpty()) return;
        ServerLevel level = player.serverLevel();

        int group = (value >>> 16) & 3;
        int motorGroup = (value >>> 18) & 31;                            // 1.1.0: 13 groups (A ... M)
        value &= 0xFFFF;
        if (action == REMOTE_SET_GROUP) {
            List<BlockPos> all = RemoteItem.read(stack, level);
            int index = value & 0xFFF, g = (value >>> 12) & 15;
            if (index < all.size()) RemoteItem.setGroup(stack, level, all.get(index), g);
            return;
        }
        if (action == REMOTE_CLEAR && group != GROUP_ALL) {
            List<BlockPos> keep = new ArrayList<>();
            for (BlockPos p : RemoteItem.read(stack, level))
                if (level.hasChunkAt(p) && !(inGroup(level, p, group) && (motorGroup == 0 || RemoteItem.groupOf(stack, p) == motorGroup))) keep.add(p);
            RemoteItem.write(stack, level, keep);
            player.displayClientMessage(Component.translatable("message.concertstage.lift_cleared"), true);
            return;
        }
        if (action == REMOTE_CLEAR) {
            RemoteItem.clear(stack);
            player.displayClientMessage(Component.translatable("message.concertstage.lift_cleared"), true);
            return;
        }

        List<BlockPos> list = RemoteItem.read(stack, level);
        List<TowerBlockEntity> towers = new ArrayList<>();
        boolean unloaded = false;
        boolean pruned = false;
        for (BlockPos p : new ArrayList<>(list)) {
            if (!level.hasChunkAt(p)) {
                unloaded = true;
                continue;
            }
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof TowerBlockEntity tb && tb.isController()) {
                if (inGroup(level, p, group) && (motorGroup == 0 || RemoteItem.groupOf(stack, p) == motorGroup)) towers.add(tb);   // other tab / group: left alone
            } else {
                list.remove(p);
                pruned = true;
            }
        }
        if (pruned) {
            RemoteItem.write(stack, level, list);
            player.displayClientMessage(Component.translatable("message.concertstage.lift_missing"), true);
        }
        if (unloaded) {
            player.displayClientMessage(Component.translatable("message.concertstage.lift_unloaded"), true);
            return;
        }
        if (towers.isEmpty()) {
            if (!pruned) player.displayClientMessage(Component.translatable("message.concertstage.lift_none"), true);
            return;
        }

        if (action == REMOTE_STOP) {
            boolean any = LiftControl.stop(level, towers);
            player.displayClientMessage(Component.translatable(any ? "message.concertstage.lift_stopping"
                : "message.concertstage.lift_idle"), true);
            return;
        }

        int current = towers.get(0).restUnits() / TowerBlockEntity.UNITS;
        int target = action == REMOTE_UP ? current + 1 : action == REMOTE_DOWN ? current - 1 : value;
        LiftControl.Result result = LiftControl.start(level, towers, target, player);
        String key = null;
        switch (result) {
            case STARTED:
                player.displayClientMessage(Component.translatable("message.concertstage.lift_start", Math.max(0, target)), true);
                break;
            case ALREADY: key = "message.concertstage.lift_already"; break;
            case MOVING: key = "message.concertstage.lift_moving"; break;
            case NOT_ALIGNED: key = "message.concertstage.lift_not_aligned"; break;
            case UNLOADED: key = "message.concertstage.lift_unloaded"; break;
            case TOO_BIG: player.displayClientMessage(Component.translatable("message.concertstage.lift_too_big", LiftControl.MAX_SCAN), true); break;
            case BLOCKED: key = "message.concertstage.lift_blocked"; break;
            default: break;   // GROUNDED: the message was sent with the position
        }
        if (key != null) player.displayClientMessage(Component.translatable(key), true);
    }
}
