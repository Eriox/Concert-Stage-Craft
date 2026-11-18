package com.concertstage.fabric;

import com.concertstage.StageContent;
import com.concertstage.client.LiftStructureRenderer;
import com.concertstage.client.AngledHolderRenderer;
import com.concertstage.client.TowerRenderer;
import com.concertstage.net.StageNetwork;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/** Client side of the Tarp Tool message on Fabric. */
public class ConcertStageFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // the block / item models are OBJ files declared for Forge's OBJ loader: read and baked here (same files)
        com.concertstage.fabric.client.FabricObjModels.register();
        // every block of the mod has holes (lattice, tubes): cutout layer, like "render_type": "cutout" on Forge
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(net.minecraft.client.renderer.RenderType.cutout(),
            StageContent.BLOCKS.values().toArray(new net.minecraft.world.level.block.Block[0]));
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, flag, lines) -> com.concertstage.client.ItemHints.add(stack, lines));
        // meshes cached by the renderers are dropped when the resource packs are reloaded (F3+T)
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(
            new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override public net.minecraft.resources.ResourceLocation getFabricId() { return new net.minecraft.resources.ResourceLocation("concertstage", "mesh_caches"); }
                @Override public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                    com.concertstage.client.TowerModel.clear();
                    com.concertstage.client.RiggingMeshes.clear();
                    com.concertstage.client.StageAttachmentRenderer.clear();
                    com.concertstage.client.RingTrussRenderer.clear();
                    com.concertstage.client.RoofTrussRenderer.clear();
                    com.concertstage.client.audio.SpeakerMeshes.clear();
                    com.concertstage.client.light.FixtureMeshes.clear();
                    com.concertstage.client.RegieRoofRenderer.clear();
                    com.concertstage.client.TarpSheetRenderer.clear();
                    com.concertstage.client.GpuMesh.clearAll();
                }
            });
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.TOWER_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Towers", new TowerRenderer(ctx)));
        // render settings: key K (Controls), opens the Concert Stage render quality screen
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(com.concertstage.client.ClientKeys.RENDER_SETTINGS);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> com.concertstage.client.ClientKeys.tick());
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.RING_TRUSS_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Ring trusses", new com.concertstage.client.RingTrussRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.ROOF_TRUSS_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Roof frames", new com.concertstage.client.RoofTrussRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.REGIE_ROOF_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("FOH roofs", new com.concertstage.client.RegieRoofRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.STAGE_ATTACHMENT_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Stage pieces", new com.concertstage.client.StageAttachmentRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.OFFSET_BLOCK_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Offset blocks", new com.concertstage.client.OffsetBlockRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.STAGE_TARP_RAIL_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Tarps", new com.concertstage.client.TarpSheetRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.ANGLED_HOLDER_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Angled trusses", new AngledHolderRenderer(ctx)));
        EntityRendererRegistry.register(StageContent.LIFT_STRUCTURE, LiftStructureRenderer::new);
        // sound system (1.1.0): the audio channel in both directions, the client side of the sound engine
        com.concertstage.audio.net.AudioNet.setSender(new com.concertstage.audio.net.AudioNet.Sender() {
            @Override public void toServer(byte[] data) {
                ClientPlayNetworking.send(com.concertstage.audio.net.AudioNet.CHANNEL, new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data)));
            }
            @Override public void toClient(net.minecraft.server.level.ServerPlayer player, byte[] data) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, com.concertstage.audio.net.AudioNet.CHANNEL,
                    new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data)));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(com.concertstage.audio.net.AudioNet.CHANNEL, (client, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            client.execute(() -> com.concertstage.audio.net.AudioNet.receiveOnClient(data));
        });
        com.concertstage.client.audio.ClientAudio.init();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> com.concertstage.client.audio.ClientAudio.tick());
        // lighting (1.2.0): channel, settings, renderers
        com.concertstage.light.net.LightNet.setSender(new com.concertstage.light.net.LightNet.Sender() {
            @Override public void toServer(byte[] data) {
                ClientPlayNetworking.send(com.concertstage.light.net.LightNet.CHANNEL, new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data)));
            }
            @Override public void toClient(net.minecraft.server.level.ServerPlayer player, byte[] data) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, com.concertstage.light.net.LightNet.CHANNEL,
                    new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data)));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(com.concertstage.light.net.LightNet.CHANNEL, (client, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            client.execute(() -> com.concertstage.light.net.LightNet.receiveOnClient(data));
        });
        com.concertstage.client.light.LightClient.init();
        // LiveVision (1.3.0)
        com.concertstage.client.vision.VisionClient.init();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> com.concertstage.client.vision.VisionClient.tick());
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.LED_PANEL_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("LED panels", new com.concertstage.client.vision.LedPanelRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.VISION_PROCESSOR_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Vision processors", new com.concertstage.client.vision.VisionProcessorRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.CAMERA_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Cameras", new com.concertstage.client.vision.CameraRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.FIXTURE_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Fixtures", new com.concertstage.client.light.FixtureRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.LUMICORE_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Light desks", new com.concertstage.client.light.LumicoreRenderer(ctx)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> { com.concertstage.client.audio.ClientAudio.reset(); com.concertstage.client.light.LightClient.reset(); com.concertstage.client.vision.VisionClient.reset(); com.concertstage.client.GpuMesh.clearAll(); }));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.AUDIO_CONSOLE_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Consoles", new com.concertstage.client.audio.AudioConsoleRenderer(ctx)));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(StageContent.SPEAKER_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Speakers", new com.concertstage.client.audio.SpeakerRenderer(ctx)));
        StageNetwork.setStructureSender((pos, action, mode, signature) -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeVarInt(action);
            buf.writeVarInt(mode);
            buf.writeInt(signature);
            ClientPlayNetworking.send(StageNetwork.STRUCTURE, buf);
        });
        // performance: the view frustum, so the renderers drawn off the chunk grid (roofs, rings, angled trusses) skip what is
        // off screen (Forge does it itself)
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_SETUP.register(ctx -> com.concertstage.client.ClientCulling.setFrustum(ctx.frustum()));
        // performance figures of the mod in the F3 screen (1.1.0), bottom right
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((g, delta) -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!mc.options.renderDebug) return;
            java.util.List<String> lines = com.concertstage.client.RenderStats.lines();
            int y = g.guiHeight() - 2 - lines.size() * 10;
            for (String l : lines) {
                int w = mc.font.width(l);
                g.fill(g.guiWidth() - w - 3, y - 1, g.guiWidth() - 1, y + 9, 0x90505050);
                g.drawString(mc.font, l, g.guiWidth() - w - 2, y, 0xE0E0E0, false);
                y += 10;
            }
        });
        // performance (1.1.0): the mod's meshes kept in the graphics card are drawn together once the block entities are done
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(ctx -> { if (!com.concertstage.client.vision.CameraFeeds.rendering) com.concertstage.client.GpuMesh.beginFrame(); });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, hit) -> { if (!com.concertstage.client.vision.CameraFeeds.rendering) com.concertstage.client.GpuMesh.flush(); return true; });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> { if (!com.concertstage.client.vision.CameraFeeds.rendering) com.concertstage.client.GpuMesh.flush(); });
        // 1.2.1: the camera images fire the world-render events again, from the lens (other mods' beams and effects in the images)
        com.concertstage.client.vision.CameraFeeds.setWorldHooks(FeedWorldContext::fire);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(ctx -> { if (!com.concertstage.client.vision.CameraFeeds.rendering) com.concertstage.client.GpuMesh.endFrame(); });
        StageNetwork.setRemoteSender((action, value) -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(action);
            buf.writeVarInt(value);
            ClientPlayNetworking.send(StageNetwork.REMOTE_COMMAND, buf);
        });
        StageNetwork.setSender((pos, length, second, shift) -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeVarInt(length);
            buf.writeVarInt(second);
            buf.writeVarInt(shift);
            ClientPlayNetworking.send(StageNetwork.TARP_SETTINGS, buf);
        });
    }
}
