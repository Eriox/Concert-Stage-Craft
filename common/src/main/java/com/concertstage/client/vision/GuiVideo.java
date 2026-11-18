package com.concertstage.client.vision;

import com.concertstage.client.ui.Ui;
import com.concertstage.vision.OutputState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** CSC 1.3.0 - video monitors of the screens (regie PREVIEW / PROGRAM, camera, wall): a live picture with its label and tally. */
final class GuiVideo {
    private GuiVideo() {}

    /** Draws a raw texture in the GUI. */
    static void blit(GuiGraphics g, int texture, int x, int y, int w, int h, boolean flipV, float alpha) {
        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float v0 = flipV ? 1 : 0, v1 = flipV ? 0 : 1;
        int a = Math.round(alpha * 255);
        bb.vertex(m, x, y, 0).uv(0, v0).color(255, 255, 255, a).endVertex();
        bb.vertex(m, x, y + h, 0).uv(0, v1).color(255, 255, 255, a).endVertex();
        bb.vertex(m, x + w, y + h, 0).uv(1, v1).color(255, 255, 255, a).endVertex();
        bb.vertex(m, x + w, y, 0).uv(1, v0).color(255, 255, 255, a).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.disableBlend();
    }

    /** A monitor showing {@code src} as the output would (media time of the output). */
    static void monitor(GuiGraphics g, int x, int y, int w, int h, String src, OutputState o, String label, int tally) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, tally);
        g.fill(x, y, x + w, y + h, 0xFF000000);
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            double time = mc.level.getGameTime() + mc.getFrameTime();
            MediaLibrary.Frame f = MediaLibrary.frame(src, o, time);
            if (f != null && f.texture() > 0) blit(g, f.texture(), x, y, w, h, f.flipV(), 1f);
            else if (src != null && src.startsWith("media:")) {
                String st = MediaLibrary.state(src.substring(6));
                if (!st.isEmpty()) g.drawCenteredString(mc.font, net.minecraft.network.chat.Component.translatable("gui.concertstage.lv.media_" + st), x + w / 2, y + h / 2 - 4, Ui.AMBER);
            } else if (src != null && src.startsWith("cam:")) {
                g.drawCenteredString(mc.font, net.minecraft.network.chat.Component.translatable("gui.concertstage.lv.no_signal"), x + w / 2, y + h / 2 - 4, Ui.MUTED);
            }
        }
        g.fill(x, y + h - 11, x + w, y + h, 0xA0000000);
        g.drawString(mc.font, label, x + 3, y + h - 10, Ui.TEXT, false);
    }
}
