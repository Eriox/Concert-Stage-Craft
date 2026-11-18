package com.concertstage.forge;

import com.concertstage.audio.net.AudioNet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Both directions: one message of the audio channel (see {@link AudioNet}), carried as bytes. */
public final class AudioPacket {
    private final byte[] data;

    public AudioPacket(byte[] data) {
        this.data = data;
    }

    public static void encode(AudioPacket p, FriendlyByteBuf buf) {
        buf.writeByteArray(p.data);
    }

    public static AudioPacket decode(FriendlyByteBuf buf) {
        return new AudioPacket(buf.readByteArray(AudioNet.MAX_MESSAGE));
    }

    public static void handle(AudioPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isServer()) {
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null) AudioNet.receiveOnServer(player, p.data);
            });
        } else {
            c.enqueueWork(() -> AudioNet.receiveOnClient(p.data));
        }
        c.setPacketHandled(true);
    }
}
