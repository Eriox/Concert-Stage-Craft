package com.concertstage.forge.rackworks;

import com.concertstage.client.light.FixtureRenderer;
import com.concertstage.light.FixtureBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rackworks.client.CableRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 1.2.2 — the client half of the RackWorks integration: draw the cables plugged into a fixture with its body. Loaded only on the
 * client, and only when RackWorks is installed.
 */
final class RackworksClient {

    private RackworksClient() {}

    static void init() {
        FixtureRenderer.RENDER_HOOK = RackworksClient::drawCables;
    }

    private static void drawCables(FixtureBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        CableRenderer.draw(be, pose, buffers);
    }
}
