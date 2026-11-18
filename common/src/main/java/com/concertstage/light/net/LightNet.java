package com.concertstage.light.net;

import com.concertstage.ConcertStage;
import com.concertstage.light.core.Bytes;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lighting channel (1.2.0), both directions; Forge and Fabric only carry the bytes. Messages:
 * <ul>
 * <li>client → server: {@link #C_CMD} a desk command (see {@code Commands}), {@link #C_SYNC} ask for the full state, {@link #C_ACTION}
 * an action only the server can do (AutoShow analysis, show library, undo, permissions, show import...).</li>
 * <li>server → clients: {@link #S_CMD} a command applied by the server (revision, server clock, bytes) — each client applies the same
 * bytes to its copy; {@link #S_FULL} the whole show and live state; {@link #S_STATUS} a message for the desk operator.</li>
 * </ul>
 */
public final class LightNet {
    public static final ResourceLocation CHANNEL = new ResourceLocation(ConcertStage.MOD_ID, "light");
    public static final int MAX_MESSAGE = 4 << 20;
    /** Import of a show file: chunks (a client to server packet is limited to 32 kB). */
    public static final int CHUNK = 28000;

    public static final int C_CMD = 1, C_SYNC = 2, C_ACTION = 3;
    /** Rev. 1.3.0-2: {@link #C_CARD} saves the configuration card in hand; {@link #C_FIXTURE_PATCH} patches the fixture at the position (QuickPatch). */
    public static final int C_CARD = 4, C_FIXTURE_PATCH = 5;
    public static final int S_CMD = 10, S_FULL = 11, S_STATUS = 12;
    /** External Show Control: {@link #S_DMX} DMX driving a desk (changed slots), {@link #S_EXT} receiver / recorder / player status. */
    public static final int S_DMX = 13, S_EXT = 14;

    /** Server actions (C_ACTION). */
    public static final int A_AUTOSHOW = 1, A_LIVE_START = 2, A_LIVE_STOP = 3, A_LIB_SAVE = 4, A_LIB_LOAD = 5, A_LIB_DELETE = 6,
        A_LIB_DUPLICATE = 7, A_LIB_NEW = 8, A_UNDO = 9, A_REDO = 10, A_AUTOPATCH = 11, A_PUBLIC = 12, A_OPERATOR_ADD = 13,
        A_OPERATOR_REMOVE = 14, A_IMPORT_BEGIN = 15, A_IMPORT_CHUNK = 16, A_LINK_AUDIO = 17, A_AUTOSAVE = 18, A_POSITIONS = 19,
        A_UNPATCH_MISSING = 20, A_EXT = 21;

    public interface Sender {
        void toServer(byte[] data);

        void toClient(ServerPlayer player, byte[] data);
    }

    private static Sender sender;
    private static Consumer<byte[]> clientHandler;

    private LightNet() {}

    public static void setSender(Sender s) {
        sender = s;
    }

    public static void setClientHandler(Consumer<byte[]> h) {
        clientHandler = h;
    }

    public static void toServer(byte[] data) {
        if (sender != null) sender.toServer(data);
    }

    public static void toClient(ServerPlayer p, byte[] data) {
        if (sender != null) sender.toClient(p, data);
    }

    /** On the server thread. */
    public static void receiveOnServer(ServerPlayer player, byte[] data) {
        try {
            if (com.concertstage.vision.VisionNet.isVision(data)) { com.concertstage.vision.VisionNet.receiveOnServer(player, data); return; }
            com.concertstage.light.LightServer.receive(player, data);
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().debug("Concert Stage lighting: bad message from {}", player.getGameProfile().getName(), e);
        }
    }

    /** On the client thread. */
    public static void receiveOnClient(byte[] data) {
        try {
            if (com.concertstage.vision.VisionNet.isVision(data)) { com.concertstage.vision.VisionNet.receiveOnClient(data); return; }
            if (clientHandler == null) return;
            clientHandler.accept(data);
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage lighting: bad message from the server", e);
        }
    }

    // ------------------------------------------------------------ helpers
    public static Bytes.Writer message(int type, BlockPos pos) {
        return new Bytes.Writer().varInt(type).sInt(pos.getX()).sInt(pos.getY()).sInt(pos.getZ());
    }

    public static BlockPos readPos(Bytes.Reader r) {
        return new BlockPos(r.sInt(), r.sInt(), r.sInt());
    }

    /** A desk command from this client. */
    public static void command(BlockPos console, byte[] cmd) {
        toServer(message(C_CMD, console).bytes(cmd).toBytes());
    }

    public static void sync(BlockPos console) {
        toServer(message(C_SYNC, console).toBytes());
    }

    /** A server action: the writer already holds the type, position and action, the caller adds the arguments. */
    public static Bytes.Writer action(BlockPos console, int action) {
        return message(C_ACTION, console).varInt(action);
    }
}
