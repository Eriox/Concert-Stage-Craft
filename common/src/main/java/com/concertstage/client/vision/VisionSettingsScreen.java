package com.concertstage.client.vision;

import com.concertstage.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * CSC 1.3.0 - graphics profile of LiveVision and Dynamic Truss for this player: LOW / BALANCED / HIGH / ULTRA, then every value on its
 * own (changing one makes the profile CUSTOM). Saved in {@code config/concertstage-livevision.properties}, never sent to the server.
 */
public class VisionSettingsScreen extends Screen {
    private static final int W = 380, H = 284, ACCENT = Ui.PURPLE;
    private final Screen parent;

    public VisionSettingsScreen(Screen parent) {
        super(Component.translatable("gui.concertstage.lv.settings_title"));
        this.parent = parent;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private void custom() {
        VisionSettings.profile = VisionSettings.Profile.CUSTOM;
        VisionSettings.save();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = left() + 12, y = top() + 36, w = W - 24;
        VisionSettings.Profile[] ps = {VisionSettings.Profile.LOW, VisionSettings.Profile.BALANCED, VisionSettings.Profile.HIGH, VisionSettings.Profile.ULTRA};
        int pw = (w - 12) / 4;
        for (int i = 0; i < ps.length; i++) {
            VisionSettings.Profile p = ps[i];
            addRenderableWidget(new Ui.Button(x + i * (pw + 4), y, pw, 18, () -> Component.literal(p.name()), ACCENT, Ui.Style.NORMAL,
                () -> { VisionSettings.apply(p); VisionSettings.save(); rebuildWidgets(); }).selected(VisionSettings.profile == p));
        }
        int col = (w - 10) / 2, x2 = x + col + 10;
        int yy = y + 30;
        addRenderableWidget(new Ui.Slider(x, yy, col, 22, Component.translatable("gui.concertstage.lv.led_distance"), 64, 4096, VisionSettings.ledDistance, ACCENT,
            v -> v + " m", v -> { VisionSettings.ledDistance = v; custom(); }));
        addRenderableWidget(new Ui.Slider(x2, yy, col, 22, Component.translatable("gui.concertstage.lv.video_height"), 90, 1080, VisionSettings.videoHeight, ACCENT,
            v -> (v * 16 / 9) + "x" + v, v -> { VisionSettings.videoHeight = v; custom(); }));
        yy += 28;
        addRenderableWidget(new Ui.Slider(x, yy, col, 22, Component.translatable("gui.concertstage.lv.video_fps"), 1, 60, VisionSettings.videoFps, ACCENT,
            v -> v + " fps", v -> { VisionSettings.videoFps = v; custom(); }));
        addRenderableWidget(new Ui.Slider(x2, yy, col, 22, Component.translatable("gui.concertstage.lv.cameras"), 0, 8, VisionSettings.activeCameras, ACCENT,
            v -> Integer.toString(v), v -> { VisionSettings.activeCameras = v; custom(); }));
        yy += 28;
        addRenderableWidget(new Ui.Slider(x, yy, col, 22, Component.translatable("gui.concertstage.lv.truss_detail"), 3, 40, Math.round(VisionSettings.trussDetail * 10), ACCENT,
            v -> String.format("x%.1f", v / 10f), v -> { VisionSettings.trussDetail = v / 10f; custom(); }));
        addRenderableWidget(new Ui.Slider(x2, yy, col, 22, Component.translatable("gui.concertstage.lv.model_detail"), 3, 40, Math.round(VisionSettings.modelDetail * 10), ACCENT,
            v -> String.format("x%.1f", v / 10f), v -> { VisionSettings.modelDetail = v / 10f; custom(); }));
        yy += 28;
        addRenderableWidget(new Ui.Slider(x, yy, col, 22, Component.translatable("gui.concertstage.lv.memory"), 32, 2048, VisionSettings.memoryBudgetMb, ACCENT,
            v -> v + " MB", v -> { VisionSettings.memoryBudgetMb = v; custom(); }));
        addRenderableWidget(new Ui.Button(x2, yy + 4, col, 18, () -> Component.translatable(VisionSettings.longRange ? "gui.concertstage.lv.long_on" : "gui.concertstage.lv.long_off"),
            ACCENT, Ui.Style.NORMAL, () -> { VisionSettings.longRange = !VisionSettings.longRange; custom(); }).selected(VisionSettings.longRange));
        yy += 28;
        // rev. 3: the share of the frame the camera images may take (the frame limit / V-Sync idle time comes on top)
        addRenderableWidget(new Ui.Slider(x, yy - 4, col, 22, Component.translatable("gui.concertstage.lv.feed_impact"), 1, 15, VisionSettings.feedImpact, ACCENT,
            v -> v + " %", v -> { VisionSettings.feedImpact = v; VisionSettings.save(); }));
        addRenderableWidget(new Ui.Button(x2, yy, col, 18, () -> Component.translatable(VisionSettings.unlimited ? "gui.concertstage.lv.unlimited_on" : "gui.concertstage.lv.unlimited_off"),
            Ui.AMBER, Ui.Style.NORMAL, () -> { VisionSettings.unlimited = !VisionSettings.unlimited; custom(); }).selected(VisionSettings.unlimited));
        yy += 24;
        // 1.2.1: particles and the other mods' effects in the camera images
        addRenderableWidget(new Ui.Button(x, yy, col, 18, () -> com.concertstage.client.ui.Tr.state("Particles in cameras", VisionSettings.feedParticles),
            ACCENT, Ui.Style.NORMAL, () -> { VisionSettings.feedParticles = !VisionSettings.feedParticles; VisionSettings.save(); }).selected(VisionSettings.feedParticles));
        addRenderableWidget(new Ui.Button(x2, yy, col, 18, () -> com.concertstage.client.ui.Tr.state("Other mods' effects in cameras", VisionSettings.feedOtherMods),
            ACCENT, Ui.Style.NORMAL, () -> { VisionSettings.feedOtherMods = !VisionSettings.feedOtherMods; VisionSettings.save(); }).selected(VisionSettings.feedOtherMods));
        addRenderableWidget(new Ui.Button(left() + W - 12 - 90, top() + H - 26, 90, 18, () -> Component.translatable("gui.concertstage.done"),
            ACCENT, Ui.Style.ACCENT, this::onClose));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable("gui.concertstage.lv.settings_sub",
            Component.literal(VisionSettings.profile.name())));
        int y = top() + H - 52;
        for (String s : VisionStats.lines()) {
            for (String part : s.split(" \\| ")) {
                if (y > top() + H - 30) break;
                g.drawString(font, part, left() + 12, y, Ui.MUTED, false);
                y += 9;
            }
        }
        String st = CameraFeeds.status();
        if (!st.isEmpty()) g.drawString(font, Component.translatable("gui.concertstage.lv.feeds_" + st.split(" ")[0]), left() + 12, top() + H - 22, Ui.AMBER, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        VisionSettings.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
