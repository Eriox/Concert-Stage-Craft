package com.concertstage.forge;

import com.concertstage.ConcertStage;
import com.concertstage.net.StageNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge side of the Tarp Tool message. */
public final class ForgeNetwork {
    private static final String PROTOCOL = "4";   // 1.2.0: lighting channel
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(ConcertStage.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ForgeNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(0, TarpSettingsPacket.class, TarpSettingsPacket::encode, TarpSettingsPacket::decode, TarpSettingsPacket::handle);
        CHANNEL.registerMessage(1, RemoteCommandPacket.class, RemoteCommandPacket::encode, RemoteCommandPacket::decode, RemoteCommandPacket::handle);
        CHANNEL.registerMessage(2, AudioPacket.class, AudioPacket::encode, AudioPacket::decode, AudioPacket::handle);
        com.concertstage.audio.net.AudioNet.setSender(new com.concertstage.audio.net.AudioNet.Sender() {
            @Override public void toServer(byte[] data) { CHANNEL.sendToServer(new AudioPacket(data)); }
            @Override public void toClient(net.minecraft.server.level.ServerPlayer player, byte[] data) {
                CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new AudioPacket(data));
            }
        });
        CHANNEL.registerMessage(4, LightPacket.class, LightPacket::encode, LightPacket::decode, LightPacket::handle);
        com.concertstage.light.net.LightNet.setSender(new com.concertstage.light.net.LightNet.Sender() {
            @Override public void toServer(byte[] data) { CHANNEL.sendToServer(new LightPacket(data)); }
            @Override public void toClient(net.minecraft.server.level.ServerPlayer player, byte[] data) {
                CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new LightPacket(data));
            }
        });
        CHANNEL.registerMessage(3, StructurePacket.class, StructurePacket::encode, StructurePacket::decode, StructurePacket::handle);
        StageNetwork.setStructureSender((pos, action, mode, signature) -> CHANNEL.sendToServer(new StructurePacket(pos, action, mode, signature)));
        StageNetwork.setRemoteSender((action, value) -> CHANNEL.sendToServer(new RemoteCommandPacket(action, value)));
        StageNetwork.setSender((pos, length, second, shift) -> CHANNEL.sendToServer(new TarpSettingsPacket(pos, length, second, shift)));
    }
}
