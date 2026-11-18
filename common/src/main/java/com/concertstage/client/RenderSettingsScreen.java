package com.concertstage.client;

import com.concertstage.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Render settings of the mod (key K, or Config in the mods list): three cards, Low / Medium / High, with what each one changes. The
 * choice applies at once and is saved.
 */
public class RenderSettingsScreen extends Screen {
    private static final int W = 360, H = 238, ACCENT = Ui.CYAN;
    private final Screen parent;

    public RenderSettingsScreen(Screen parent) {
        super(Component.translatable("gui.concertstage.render_title"));
        this.parent = parent;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        int x = left() + 12, y = top() + 40, cw = (W - 24 - 16) / 3;
        RenderQuality[] all = RenderQuality.values();
        for (int i = 0; i < all.length; i++) addRenderableWidget(new Card(x + i * (cw + 8), y, cw, 118, all[i]));
        // rev. 4c: reach of the beams (and of their light on the blocks), distance at which the mod's blocks are drawn
        int sw = (W - 24 - 8) / 2, sy = top() + 166;
        com.concertstage.client.ui.Tr.help(addRenderableWidget(new Ui.Slider(left() + 12, sy, sw, 18, Component.empty(), RenderQuality.RANGE_MIN,
            RenderQuality.RANGE_MAX, RenderQuality.beamPercent(), ACCENT,
            v -> Component.translatable("gui.concertstage.render_beam_range", v).getString(), RenderQuality::setBeamPercent)), "render_beam_range");
        com.concertstage.client.ui.Tr.help(addRenderableWidget(new Ui.Slider(left() + 20 + sw, sy, sw, 18, Component.empty(), RenderQuality.RANGE_MIN,
            RenderQuality.BLOCK_MAX, RenderQuality.blockPercent(), ACCENT,
            v -> Component.translatable("gui.concertstage.render_block_range", v).getString(), RenderQuality::setBlockPercent)), "render_block_range");
        addRenderableWidget(new Ui.Button(left() + W - 12 - 90, top() + H - 28, 90, 18, () -> Component.translatable("gui.concertstage.done"),
            ACCENT, Ui.Style.ACCENT, this::onClose));
        // 1.1.1: AUTO — the quality follows the frame rate (one step at a time, at most one change every 20 s)
        com.concertstage.client.ui.Tr.help(addRenderableWidget(new Ui.Button(left() + 12, top() + H - 28, 150, 18,
            () -> Component.translatable(RenderQuality.auto() ? "gui.concertstage.render_auto_on" : "gui.concertstage.render_auto_off"),
            ACCENT, Ui.Style.NORMAL, () -> RenderQuality.setAuto(!RenderQuality.auto()))), "render_auto");
        // 1.2.1: the tutorial of the mod
        addRenderableWidget(new Ui.Button(left() + W - 12 - 80, top() + 6, 80, 16, () -> Component.translatable("gui.concertstage.guide_button"),
            Ui.AMBER, Ui.Style.NORMAL, () -> minecraft.setScreen(new com.concertstage.client.ui.GuideScreen(this))));
        // 1.3.0: LiveVision & Dynamic Truss profiles
        addRenderableWidget(new Ui.Button(left() + 168, top() + H - 28, 96, 18, () -> Component.translatable("gui.concertstage.lv.settings"),
            Ui.PURPLE, Ui.Style.NORMAL, () -> minecraft.setScreen(new com.concertstage.client.vision.VisionSettingsScreen(this))));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable("gui.concertstage.render_subtitle"));
        g.drawString(font, Component.translatable("gui.concertstage.render_hint"), left() + 12, top() + H - 23, Ui.MUTED, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null;
    }

    /** One quality: name, signal bars, what it does; outlined in the accent colour when chosen. */
    private final class Card extends AbstractButton {
        private final RenderQuality q;

        Card(int x, int y, int w, int h, RenderQuality q) {
            super(x, y, w, h, Component.translatable("gui.concertstage.render_" + q.name().toLowerCase(java.util.Locale.ROOT)));
            this.q = q;
        }

        @Override
        public void onPress() {
            RenderQuality.set(q);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean on = RenderQuality.current() == q, hover = isHoveredOrFocused();
            int x = getX(), y = getY(), w = width, h = height;
            g.fill(x, y, x + w, y + h, on ? Ui.mix(0xFF1C1F24, ACCENT, 0.18f) : hover ? 0xFF262A31 : 0xFF1C1F24);
            Ui.frame(g, x, y, w, h, on ? ACCENT : hover ? Ui.alpha(ACCENT, 0x90) : Ui.LINE);
            if (on) g.fill(x + 1, y + 1, x + w - 1, y + 3, ACCENT);
            // signal bars: 1, 2 or 3 lit
            int level = q.ordinal() + 1, bx = x + w / 2 - 13, by = y + 30;
            for (int i = 0; i < 3; i++) {
                int bh = 6 + i * 5;
                g.fill(bx + i * 9, by - bh, bx + i * 9 + 7, by, i < level ? (on ? ACCENT : Ui.TEXT) : 0xFF3A3F47);
            }
            Ui.centeredFit(g, getMessage(), x + w / 2, y + 38, w - 8, on ? 0xFFFFFFFF : Ui.TEXT);
            String k = "gui.concertstage.render_" + q.name().toLowerCase(java.util.Locale.ROOT);
            Component[] lines = {
                Component.translatable("gui.concertstage.render_distance", Math.round(q.distance * 100)),
                Component.translatable(q.nearRadius > 0 ? "gui.concertstage.render_detail_near" : "gui.concertstage.render_detail_far", q.nearRadius),
                Component.translatable(q.hardware ? "gui.concertstage.render_hardware_on" : "gui.concertstage.render_hardware_off"),
                Component.translatable(k + "_use")};
            int ly = y + 56;
            for (int i = 0; i < lines.length; i++) {
                g.pose().pushPose();
                g.pose().translate(x + w / 2f, ly + i * 13, 0);
                g.pose().scale(0.8f, 0.8f, 1);
                Ui.centeredFit(g, lines[i], 0, 0, (int) ((w - 6) / 0.8f), i == 3 ? (on ? ACCENT : Ui.MUTED) : Ui.MUTED);
                g.pose().popPose();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
