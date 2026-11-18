package com.concertstage.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Keys of the mod: K opens the render settings (can be changed in Controls). */
public final class ClientKeys {
    private ClientKeys() {}

    public static final KeyMapping RENDER_SETTINGS = new KeyMapping("key.concertstage.render_settings", GLFW.GLFW_KEY_K, "key.categories.concertstage");

    /** Every client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (RENDER_SETTINGS.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new RenderSettingsScreen(null));
        }
    }
}
