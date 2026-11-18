package com.concertstage.forge;

import com.concertstage.ConcertStage;
import com.concertstage.StageContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

@Mod(ConcertStage.MOD_ID)
public class ConcertStageForge {
    public ConcertStageForge() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::onRegister);
        // any block set on a stage floor that is not at a block boundary goes down onto the floor
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) -> {
            net.minecraft.world.InteractionResult r = com.concertstage.block.offset.OffsetPlacement.onUseBlock(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec());
            if (r != net.minecraft.world.InteractionResult.PASS) { e.setCanceled(true); e.setCancellationResult(r); }
        });
        ForgeNetwork.init();
        // sound system (1.1.0): paced sending of the tracks to the players, files closed when the server stops
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e) -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.END) com.concertstage.audio.net.AudioServer.tick(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent e) ->
            { com.concertstage.audio.net.AudioServer.stopped(); com.concertstage.light.LightServer.stopped(); });
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) ForgeConfigScreen.register();
    }

    private void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper ->
            StageContent.BLOCKS.forEach((name, block) -> helper.register(new ResourceLocation(ConcertStage.MOD_ID, name), block)));

        event.register(Registries.BLOCK_ENTITY_TYPE, helper ->
            StageContent.BLOCK_ENTITIES.forEach((name, type) -> helper.register(new ResourceLocation(ConcertStage.MOD_ID, name), type)));

        event.register(Registries.ENTITY_TYPE, helper ->
            StageContent.ENTITY_TYPES.forEach((name, type) -> helper.register(new ResourceLocation(ConcertStage.MOD_ID, name), type)));

        event.register(Registries.SOUND_EVENT, helper ->
            StageContent.SOUND_EVENTS.forEach((name, sound) -> helper.register(new ResourceLocation(ConcertStage.MOD_ID, name), sound)));

        event.register(Registries.ITEM, helper ->
            StageContent.ITEMS.forEach((name, item) -> helper.register(new ResourceLocation(ConcertStage.MOD_ID, name), item)));

        // one creative tab per family: trusses, towers and rigging, platforms, CSC V3 scaffolding, curtains, tools
        // aurex.2: the CSC tabs follow each other right after the vanilla tabs, so they share one page of the creative inventory
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            ResourceLocation previous = net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS.location();
            for (com.concertstage.stage.StageCatalog.Tab tab : com.concertstage.stage.StageCatalog.Tab.values()) {
                ResourceLocation id = new ResourceLocation(ConcertStage.MOD_ID, tab.id);
                helper.register(id, CreativeModeTab.builder()
                    .withTabsBefore(previous)
                    .title(Component.translatable("itemGroup.concertstage." + tab.id))
                    .icon(() -> new ItemStack(com.concertstage.stage.StageCatalog.icon(tab)))
                    .displayItems((params, output) -> com.concertstage.stage.StageCatalog.items(tab).forEach(output::accept))
                    .build());
                previous = id;
            }
        });
    }
}
