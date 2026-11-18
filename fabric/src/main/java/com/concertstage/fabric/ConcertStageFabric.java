package com.concertstage.fabric;

import com.concertstage.ConcertStage;
import com.concertstage.StageContent;
import com.concertstage.net.StageNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ConcertStageFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // any block set on a stage floor that is not at a block boundary goes down onto the floor
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
            com.concertstage.block.offset.OffsetPlacement.onUseBlock(player, world, hand, hit));
        StageContent.BLOCKS.forEach((name, block) ->
            Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(ConcertStage.MOD_ID, name), block));

        StageContent.BLOCK_ENTITIES.forEach((name, type) ->
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, new ResourceLocation(ConcertStage.MOD_ID, name), type));

        StageContent.ENTITY_TYPES.forEach((name, type) ->
            Registry.register(BuiltInRegistries.ENTITY_TYPE, new ResourceLocation(ConcertStage.MOD_ID, name), type));

        StageContent.SOUND_EVENTS.forEach((name, sound) ->
            Registry.register(BuiltInRegistries.SOUND_EVENT, new ResourceLocation(ConcertStage.MOD_ID, name), sound));

        StageContent.ITEMS.forEach((name, item) ->
            Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ConcertStage.MOD_ID, name), item));

        // one creative tab per family: trusses, towers and rigging, platforms, CSC V3 scaffolding, curtains, tools
        for (com.concertstage.stage.StageCatalog.Tab tab : com.concertstage.stage.StageCatalog.Tab.values()) {
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(ConcertStage.MOD_ID, tab.id),
                FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.concertstage." + tab.id))
                    .icon(() -> new ItemStack(com.concertstage.stage.StageCatalog.icon(tab)))
                    .displayItems((params, output) -> com.concertstage.stage.StageCatalog.items(tab).forEach(output::accept))
                    .build());
        }

        // sound system (1.1.0): one channel, both directions (see AudioNet); paced sending of the tracks every server tick
        com.concertstage.audio.net.AudioNet.setSender(new com.concertstage.audio.net.AudioNet.Sender() {
            @Override public void toServer(byte[] data) { }                        // replaced on the client (ConcertStageFabricClient)
            @Override public void toClient(net.minecraft.server.level.ServerPlayer player, byte[] data) {
                ServerPlayNetworking.send(player, com.concertstage.audio.net.AudioNet.CHANNEL, new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data)));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(com.concertstage.audio.net.AudioNet.CHANNEL, (server, player, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            server.execute(() -> com.concertstage.audio.net.AudioNet.receiveOnServer(player, data));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(com.concertstage.audio.net.AudioServer::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> { com.concertstage.audio.net.AudioServer.stopped(); com.concertstage.light.LightServer.stopped(); });
        // lighting (1.2.0): its own channel, both directions (see LightNet)
        com.concertstage.light.net.LightNet.setSender(new com.concertstage.light.net.LightNet.Sender() {
            @Override public void toServer(byte[] data) { }                        // replaced on the client (ConcertStageFabricClient)
            @Override public void toClient(net.minecraft.server.level.ServerPlayer player, byte[] data) {
                ServerPlayNetworking.send(player, com.concertstage.light.net.LightNet.CHANNEL, new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data)));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(com.concertstage.light.net.LightNet.CHANNEL, (server, player, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            server.execute(() -> com.concertstage.light.net.LightNet.receiveOnServer(player, data));
        });

        ServerPlayNetworking.registerGlobalReceiver(StageNetwork.STRUCTURE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int action = buf.readVarInt();
            int mode = buf.readVarInt();
            int signature = buf.readInt();
            server.execute(() -> StageNetwork.handleStructure(player, pos, action, mode, signature));
        });

        ServerPlayNetworking.registerGlobalReceiver(StageNetwork.REMOTE_COMMAND, (server, player, handler, buf, responseSender) -> {
            int action = buf.readVarInt();
            int value = buf.readVarInt();
            server.execute(() -> StageNetwork.handleRemote(player, action, value));
        });

        ServerPlayNetworking.registerGlobalReceiver(StageNetwork.TARP_SETTINGS, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int length = buf.readVarInt();
            int second = buf.readVarInt();
            int shift = buf.readVarInt();
            server.execute(() -> StageNetwork.handleTarpSettings(player, pos, length, second, shift));
        });
    }
}
