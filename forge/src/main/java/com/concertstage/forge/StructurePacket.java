package com.concertstage.forge;

import com.concertstage.net.StageNetwork;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: the CSC Structure Manager removes the roof the player confirmed. */
public final class StructurePacket {
    private final BlockPos pos;
    private final int action, mode, signature;

    public StructurePacket(BlockPos pos, int action, int mode, int signature) {
        this.pos = pos;
        this.action = action;
        this.mode = mode;
        this.signature = signature;
    }

    public static void encode(StructurePacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.mode);
        buf.writeInt(p.signature);
    }

    public static StructurePacket decode(FriendlyByteBuf buf) {
        return new StructurePacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readInt());
    }

    public static void handle(StructurePacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null) StageNetwork.handleStructure(player, p.pos, p.action, p.mode, p.signature);
        });
        c.setPacketHandled(true);
    }
}
