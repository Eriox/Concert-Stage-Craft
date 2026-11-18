package com.concertstage.forge;

import com.concertstage.light.net.LightNet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Both directions: one message of the lighting channel (see {@link LightNet}), carried as bytes (1.2.0). */
public final class LightPacket {
    private final byte[] data;

    public LightPacket(byte[] data) {
        this.data = data;
    }

    public static void encode(LightPacket p, FriendlyByteBuf buf) {
        buf.writeByteArray(p.data);
    }

    public static LightPacket decode(FriendlyByteBuf buf) {
        return new LightPacket(buf.readByteArray(LightNet.MAX_MESSAGE));
    }

    public static void handle(LightPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isServer()) {
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null) LightNet.receiveOnServer(player, p.data);
            });
        } else {
            c.enqueueWork(() -> LightNet.receiveOnClient(p.data));
        }
        c.setPacketHandled(true);
    }
}
