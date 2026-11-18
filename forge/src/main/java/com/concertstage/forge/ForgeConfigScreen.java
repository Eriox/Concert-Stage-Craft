package com.concertstage.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/** Client only: the Config button of the mods list opens the render settings of the mod. */
final class ForgeConfigScreen {
    private ForgeConfigScreen() {}

    static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new com.concertstage.client.RenderSettingsScreen(parent)));
    }
}
