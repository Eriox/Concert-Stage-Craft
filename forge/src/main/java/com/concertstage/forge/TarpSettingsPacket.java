package com.concertstage.forge;

import com.concertstage.net.StageNetwork;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: new size / position of a curtain or tarp. */
public final class TarpSettingsPacket {
    private final BlockPos pos;
    private final int length;
    private final int second;
    private final int shift;

    public TarpSettingsPacket(BlockPos pos, int length, int second, int shift) {
        this.pos = pos;
        this.length = length;
        this.second = second;
        this.shift = shift;
    }

    public static void encode(TarpSettingsPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.length);
        buf.writeVarInt(p.second);
        buf.writeVarInt(p.shift);
    }

    public static TarpSettingsPacket decode(FriendlyByteBuf buf) {
        return new TarpSettingsPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TarpSettingsPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null) StageNetwork.handleTarpSettings(player, p.pos, p.length, p.second, p.shift);
        });
        c.setPacketHandled(true);
    }
}
