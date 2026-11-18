package com.concertstage.forge;

import com.concertstage.ConcertStage;
import com.concertstage.StageContent;
import com.concertstage.client.LiftStructureRenderer;
import com.concertstage.client.AngledHolderRenderer;
import com.concertstage.client.TowerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registrations (renderers of the motorised towers and of the lifted structure). */
@Mod.EventBusSubscriber(modid = ConcertStage.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeClientEvents {
    private ForgeClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(StageContent.STAGE_ATTACHMENT_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Stage pieces", new com.concertstage.client.StageAttachmentRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.TOWER_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Towers", new TowerRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.RING_TRUSS_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Ring trusses", new com.concertstage.client.RingTrussRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.ROOF_TRUSS_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Roof frames", new com.concertstage.client.RoofTrussRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.REGIE_ROOF_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("FOH roofs", new com.concertstage.client.RegieRoofRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.OFFSET_BLOCK_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Offset blocks", new com.concertstage.client.OffsetBlockRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.STAGE_TARP_RAIL_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Tarps", new com.concertstage.client.TarpSheetRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.ANGLED_HOLDER_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Angled trusses", new AngledHolderRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.AUDIO_CONSOLE_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Consoles", new com.concertstage.client.audio.AudioConsoleRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.SPEAKER_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Speakers", new com.concertstage.client.audio.SpeakerRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.FIXTURE_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Fixtures", new com.concertstage.client.light.FixtureRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.LUMICORE_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Light desks", new com.concertstage.client.light.LumicoreRenderer(ctx)));
        // 1.3.0 LiveVision
        event.registerBlockEntityRenderer(StageContent.LED_PANEL_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("LED panels", new com.concertstage.client.vision.LedPanelRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.VISION_PROCESSOR_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Vision processors", new com.concertstage.client.vision.VisionProcessorRenderer(ctx)));
        event.registerBlockEntityRenderer(StageContent.CAMERA_ENTITY, ctx -> com.concertstage.client.RenderStats.timed("Cameras", new com.concertstage.client.vision.CameraRenderer(ctx)));
        event.registerEntityRenderer(StageContent.LIFT_STRUCTURE, LiftStructureRenderer::new);
    }
    @SubscribeEvent
    public static void registerKeys(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.concertstage.client.ClientKeys.RENDER_SETTINGS);
    }

    /** Render settings: the Config button of the mods list, and the key (every client tick). */
    @SubscribeEvent
    public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // 1.3.0 rev. 2: the blocks of the camera images with Embeddium / Sodium, drawn with each model's own render types and data
        com.concertstage.client.vision.FeedTerrain.drawer = (state, pos, level, pose, layer, random) -> {
            var br = net.minecraft.client.Minecraft.getInstance().getBlockRenderer();
            var model = br.getBlockModel(state);
            net.minecraftforge.client.model.data.ModelData data = level.getModelDataManager().getAt(pos);
            if (data == null) data = net.minecraftforge.client.model.data.ModelData.EMPTY;
            data = model.getModelData(level, pos, state, data);
            long seed = state.getSeed(pos);
            random.setSeed(seed);
            for (net.minecraft.client.renderer.RenderType rt : model.getRenderTypes(state, random, data)) {
                random.setSeed(seed);
                br.renderBatched(state, pos, level, pose, layer.apply(rt), true, random, data, rt);
            }
        };
        // rev. 1.3.0-3: the LiveVision camera images follow the frames (one at most per frame, within a frame-time budget)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.RenderTickEvent e) -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.START) com.concertstage.client.vision.CameraFeeds.perFrame();
            else com.concertstage.client.vision.CameraFeeds.frameEnd();
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent e) -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                com.concertstage.client.ClientKeys.tick();
                com.concertstage.client.audio.ClientAudio.tick();
                com.concertstage.client.vision.VisionClient.tick();
            }
        });
        // sound system (1.1.0): client side of the sound engine, stopped when leaving a world
        com.concertstage.client.audio.ClientAudio.init();
        com.concertstage.client.light.LightClient.init();                              // lighting (1.2.0)
        com.concertstage.client.vision.VisionClient.init();                            // LiveVision (1.3.0)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
            { com.concertstage.client.audio.ClientAudio.reset(); com.concertstage.client.light.LightClient.reset(); com.concertstage.client.vision.VisionClient.reset(); net.minecraft.client.Minecraft.getInstance().execute(com.concertstage.client.GpuMesh::clearAll); });
        // performance figures of the mod in the F3 screen (1.1.0)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.CustomizeGuiOverlayEvent.DebugText e) -> {
            if (net.minecraft.client.Minecraft.getInstance().options.renderDebug) e.getRight().addAll(com.concertstage.client.RenderStats.lines());
        });
        // performance (1.1.0): the mod's meshes kept in the graphics card are drawn together once the block entities are done
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderLevelStageEvent e) -> {
            if (com.concertstage.client.vision.CameraFeeds.rendering) return;          // 1.2.1: a camera image (fired again below)
            var st = e.getStage();
            if (st == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) com.concertstage.client.GpuMesh.beginFrame();
            else if (st == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                || st == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) com.concertstage.client.GpuMesh.flush();
            else if (st == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) com.concertstage.client.GpuMesh.endFrame();
        });
        // 1.2.1: the camera images fire the world-render stages again, from the lens, so the mods that draw their beams, effects or
        // fixtures there (Theatrical ExtraLights and the like) draw them into the image too
        com.concertstage.client.vision.CameraFeeds.setWorldHooks((stage, pose, proj, camera, frustum, partial, ticks) -> {
            net.minecraftforge.client.event.RenderLevelStageEvent.Stage st = switch (stage) {
                case com.concertstage.client.vision.CameraFeeds.AFTER_ENTITIES -> net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES;
                case com.concertstage.client.vision.CameraFeeds.AFTER_BLOCK_ENTITIES -> net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
                case com.concertstage.client.vision.CameraFeeds.AFTER_TRANSLUCENT -> net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
                default -> net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES;
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.RenderLevelStageEvent(st,
                net.minecraft.client.Minecraft.getInstance().levelRenderer, pose, proj, ticks, partial, camera, frustum));
        });
    }

    @SubscribeEvent
    public static void registerReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
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
        });
    }
}
