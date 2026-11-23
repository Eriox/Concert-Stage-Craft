package com.concertstage.client.light;

import com.concertstage.light.FixtureBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 1.2.2 — the client-only extra drawing on a fixture after its body and light: today the RackWorks cables plugged into it. Set
 * by a loader's client setup (Forge, when RackWorks is installed); never loaded on a dedicated server.
 */
public interface FixtureRenderHook {

    void drawCables(FixtureBlockEntity be, PoseStack pose, MultiBufferSource buffers);
}
