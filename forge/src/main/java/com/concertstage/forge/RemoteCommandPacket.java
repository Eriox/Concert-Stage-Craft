package com.concertstage.forge;

import com.concertstage.net.StageNetwork;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: a button of the lift remote screen. */
public final class RemoteCommandPacket {
    private final int action;
    private final int value;

    public RemoteCommandPacket(int action, int value) {
        this.action = action;
        this.value = value;
    }

    public static void encode(RemoteCommandPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.value);
    }

    public static RemoteCommandPacket decode(FriendlyByteBuf buf) {
        return new RemoteCommandPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(RemoteCommandPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null) StageNetwork.handleRemote(player, p.action, p.value);
        });
        c.setPacketHandled(true);
    }
}
