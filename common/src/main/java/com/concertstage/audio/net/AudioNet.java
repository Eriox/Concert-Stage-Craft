package com.concertstage.audio.net;

import com.concertstage.ConcertStage;
import io.netty.buffer.Unpooled;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The audio channel of the mod (both directions): console commands, array angles, the upload of an imported track to the server
 * and the download of a track by the clients that hear it. Each message is a type number followed by its fields; Forge and Fabric
 * only carry the bytes. Big files go in chunks of {@link #CHUNK} bytes (a client to server packet is limited to 32 kB).
 */
public final class AudioNet {
    public static final ResourceLocation CHANNEL = new ResourceLocation(ConcertStage.MOD_ID, "audio");
    public static final int CHUNK = 30000;
    /** Largest message (decoding side). */
    public static final int MAX_MESSAGE = 1 << 20;

    // client -> server
    public static final int C_CONSOLE = 1, C_UPLOAD_BEGIN = 2, C_UPLOAD_CHUNK = 3, C_DOWNLOAD = 4, C_ARRAY = 5, C_INFO = 6, C_UPLOAD_CANCEL = 7, C_STATE = 8, C_GROUP_CONFIG = 9, C_DELAY_PLAN = 10;
    // server -> client
    public static final int S_UPLOAD = 20, S_FILE_BEGIN = 21, S_FILE_CHUNK = 22, S_FILE_ERROR = 23, S_INFO = 24, S_STATE = 25;
    /** Blocks of a travelling structure (1.1.0, see LiftPayload): not audio, but this channel already carries big data both ways. */
    public static final int S_LIFT = 30;

    /** Console commands (C_CONSOLE: pos, action, int a, float f). */
    public static final int PLAY = 0, PAUSE = 1, STOP = 2, NEXT = 3, PREV = 4, SELECT = 5, REMOVE = 6, MOVE = 7, MASTER = 8, FADER = 9,
        MUTE = 10, EQ = 11, REPEAT = 12, SEEK = 13, UNLINK = 14, SET_GROUP = 15;

    /** Upload status (S_UPLOAD: sha1, status, long value, message key). */
    public static final int UP_SEND = 0, UP_DONE = 1, UP_ERROR = 2, UP_CHECKING = 3;

    public interface Sender {
        void toServer(byte[] data);

        void toClient(ServerPlayer player, byte[] data);
    }

    private static Sender sender;
    private static Consumer<FriendlyByteBuf> clientHandler;

    private AudioNet() {}

    public static void setSender(Sender s) {
        sender = s;
    }

    /** Set by the client initialisation (a dedicated server never has one). */
    public static void setClientHandler(Consumer<FriendlyByteBuf> h) {
        clientHandler = h;
    }

    public static FriendlyByteBuf message(int type) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        b.writeVarInt(type);
        return b;
    }

    public static byte[] bytes(FriendlyByteBuf b) {
        byte[] out = new byte[b.readableBytes()];
        b.getBytes(b.readerIndex(), out);
        b.release();
        return out;
    }

    public static void toServer(FriendlyByteBuf b) {
        byte[] data = bytes(b);
        if (sender != null) sender.toServer(data);
    }

    public static void toClient(ServerPlayer player, FriendlyByteBuf b) {
        byte[] data = bytes(b);
        if (sender != null) sender.toClient(player, data);
    }

    /** On the server thread. */
    public static void receiveOnServer(ServerPlayer player, byte[] data) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            AudioServer.receive(player, b);
        } catch (RuntimeException e) {
            // malformed message from that client: ignored
            com.mojang.logging.LogUtils.getLogger().debug("Concert Stage audio: bad message from {}", player.getGameProfile().getName(), e);
        }
    }

    /** On the client thread. */
    public static void receiveOnClient(byte[] data) {
        if (clientHandler == null) return;
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            clientHandler.accept(b);
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage audio: bad message from the server", e);
        }
    }

    // ------------------------------------------------------------------ client side helpers
    public static void consoleCommand(net.minecraft.core.BlockPos pos, int action, int a, float f) {
        FriendlyByteBuf b = message(C_CONSOLE);
        b.writeBlockPos(pos);
        b.writeVarInt(action);
        b.writeVarInt(a);
        b.writeFloat(f);
        toServer(b);
    }

    public static void arrayAngles(net.minecraft.core.BlockPos pos, float yaw, float tilt, float[] splays) {
        FriendlyByteBuf b = message(C_ARRAY);
        b.writeBlockPos(pos);
        b.writeFloat(yaw);
        b.writeFloat(tilt);
        b.writeVarInt(splays.length);
        for (float s : splays) b.writeFloat(s);
        toServer(b);
    }
}
