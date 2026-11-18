package com.concertstage.client;

import com.concertstage.angle.AngleMath;
import com.concertstage.client.ui.Ui;
import com.concertstage.item.AngleToolItem;
import com.concertstage.net.StageNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Angle tool (blue theme): a protractor showing the bend and a grid of the preset angles. */
public class AngleScreen extends Screen {
    private static final int W = 300, H = 196;
    private final boolean platform;
    private final int ACCENT;
    private int bend;

    public AngleScreen() {
        this(false);
    }

    /** {@code platform}: the platform protractor (orange), which turns decks and platforms; otherwise the angle square (trusses, towers). */
    public AngleScreen(boolean platform) {
        super(Component.translatable(platform ? "gui.concertstage.platform_angle_title" : "gui.concertstage.angle_title"));
        this.platform = platform;
        this.ACCENT = platform ? Ui.ORANGE : Ui.BLUE;
        this.bend = platform ? com.concertstage.item.PlatformAngleToolItem.angleOf(Minecraft.getInstance().player)
            : AngleToolItem.bendOf(Minecraft.getInstance().player);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        int x = left() + 12, y = top() + 116, w = W - 24;
        int n = AngleMath.PRESETS.length, cols = 8, cw = (w - (cols - 1) * 3) / cols;
        for (int i = 0; i < n; i++) {
            final int v = AngleMath.PRESETS[i];
            int r = i / cols, c = i % cols;
            addRenderableWidget(new Ui.Button(x + c * (cw + 3), y + r * 20, cw, 17, () -> Component.literal((v > 0 ? "+" : "") + v + "°"), ACCENT, Ui.Style.NORMAL,
                () -> { bend = v; StageNetwork.sendRemote(platform ? StageNetwork.REMOTE_PLATFORM_ANGLE : StageNetwork.REMOTE_ANGLE, v + 180); rebuildWidgets(); }).selected(bend == v));
        }
        addRenderableWidget(new Ui.Button(x + (cols - 1) * (cw + 3), y + 20, cw, 17, () -> Component.translatable("gui.concertstage.done_short"), ACCENT, Ui.Style.ACCENT, this::onClose));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable(platform ? "gui.concertstage.platform_angle_subtitle" : "gui.concertstage.angle_subtitle"));
        // protractor: half circle of ticks, the current bend as a needle
        int cx = left() + W / 2, cy = top() + 104, r = 62;
        for (int a = -90; a <= 90; a += 5) {
            double t = Math.toRadians(a);
            int len = a % 45 == 0 ? 9 : a % 15 == 0 ? 6 : 3;
            for (int k = 0; k < len; k++) {
                int px = cx + (int) Math.round(Math.sin(t) * (r - k)), py = cy - (int) Math.round(Math.cos(t) * (r - k));
                g.fill(px, py, px + 1, py + 1, a == 0 ? Ui.TEXT : Ui.MUTED);
            }
        }
        double t = Math.toRadians(bend);
        for (int k = 0; k < r - 10; k++) {
            int px = cx + (int) Math.round(Math.sin(t) * k), py = cy - (int) Math.round(Math.cos(t) * k);
            g.fill(px - (k < 6 ? 1 : 0), py, px + 1, py + 1, ACCENT);
        }
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, ACCENT);
        String v = (bend > 0 ? "+" : "") + bend + "°";
        g.pose().pushPose();
        g.pose().translate(cx, cy - 30, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(font, v, 0, 0, Ui.TEXT);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
