package com.concertstage.client;

import com.concertstage.block.ring.RingTrussBlockEntity;
import com.concertstage.client.ui.Ui;
import com.concertstage.net.StageNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * CSC RingTruss controller (cyan theme, its own layout): a live 3/4 preview of the ring on the left (radius, tilt, rotation),
 * steppers and presets on the right. Each change is sent at once; the tilt moves at 30° per second in the world.
 */
public class RingScreen extends Screen {
    private static final int W = 330, H = 210, ACCENT = Ui.CYAN;
    private final BlockPos pos;
    private int radius2, tilt, yaw;
    private boolean black;
    private Ui.Slider tiltSlider;
    // 1.3.0: section (rings and curved pieces), arc angle and bend direction (curved pieces)
    private int section, sweep, bend;
    private final boolean curved;

    public RingScreen(BlockPos pos, RingTrussBlockEntity be) {
        super(Component.translatable("gui.concertstage.ring_title"));
        this.pos = pos;
        this.radius2 = be.radius2();
        this.tilt = be.targetTilt();
        this.yaw = be.yaw();
        this.black = be.isBlack();
        this.section = be.section();
        this.sweep = be.sweep();
        this.bend = be.bend();
        this.curved = be.isCurved();
        if (curved && be.getBlockState().getBlock() instanceof com.concertstage.block.ring.CurvedTrussBlock c) this.section = c.tri ? 1 : 0;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private void send() {
        StageNetwork.sendTarpSettings(pos, radius2, tilt, (yaw / 15) | (black ? 32 : 0) | (section << 6) | (bend << 7) | ((curved ? sweep : 0) << 9));
    }

    @Override
    protected void init() {
        if (curved) { initCurved(); return; }
        int cx = left() + 150, y = top() + 36, w = W - 162;
        // radius stepper
        addRenderableWidget(new Ui.Button(cx, y + 12, 22, 20, () -> Component.literal("−"), ACCENT, Ui.Style.NORMAL,
            () -> { radius2 = Math.max(RingTrussBlockEntity.MIN_RADIUS2, radius2 - 1); send(); }));
        addRenderableWidget(new Ui.Button(cx + w - 22, y + 12, 22, 20, () -> Component.literal("+"), ACCENT, Ui.Style.NORMAL,
            () -> { radius2 = Math.min(RingTrussBlockEntity.MAX_RADIUS2, radius2 + 1); send(); }));
        // tilt presets + fine slider
        int pw = (w - 9) / 4;
        int[] presets = {0, 30, 60, 90};
        for (int i = 0; i < 4; i++) {
            final int v = presets[i];
            addRenderableWidget(new Ui.Button(cx + i * (pw + 3), y + 52, pw, 16, () -> Component.literal(v == 90 ? "90° ↻" : v + "°"), ACCENT, Ui.Style.NORMAL,
                () -> { tilt = v; tiltSlider.set(v); send(); rebuildWidgets(); }).selected(tilt == v));
        }
        tiltSlider = addRenderableWidget(new Ui.Slider(cx, y + 72, w, 22, Component.translatable("gui.concertstage.ring_tilt"), 0, 90, tilt, ACCENT,
            v -> v + "°", v -> { tilt = v; send(); rebuildWidgets(); }));
        // rotation
        addRenderableWidget(new Ui.Button(cx, y + 108, 34, 18, () -> Component.literal("↺ 15°"), ACCENT, Ui.Style.NORMAL,
            () -> { yaw = (yaw + 345) % 360; send(); }));
        addRenderableWidget(new Ui.Button(cx + w - 34, y + 108, 34, 18, () -> Component.literal("15° ↻"), ACCENT, Ui.Style.NORMAL,
            () -> { yaw = (yaw + 15) % 360; send(); }));
        // finish + 1.3.0 section of the ring
        int q = (w - 9) / 4;
        addRenderableWidget(new Ui.Button(cx, y + 136, q, 16, () -> Component.translatable("gui.concertstage.ring_alu"), ACCENT, Ui.Style.NORMAL,
            () -> { black = false; send(); rebuildWidgets(); }).selected(!black));
        addRenderableWidget(new Ui.Button(cx + q + 3, y + 136, q, 16, () -> Component.translatable("gui.concertstage.ring_black"), ACCENT, Ui.Style.NORMAL,
            () -> { black = true; send(); rebuildWidgets(); }).selected(black));
        addRenderableWidget(new Ui.Button(cx + 2 * (q + 3), y + 136, q, 16, () -> Component.literal("QUAD"), ACCENT, Ui.Style.NORMAL,
            () -> { section = 0; send(); rebuildWidgets(); }).selected(section == 0));
        addRenderableWidget(new Ui.Button(cx + 3 * (q + 3), y + 136, q, 16, () -> Component.literal("TRI"), ACCENT, Ui.Style.NORMAL,
            () -> { section = 1; send(); rebuildWidgets(); }).selected(section == 1));
        addRenderableWidget(new Ui.Button(cx, y + 158, w, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    /** 1.3.0 - Curved TriTruss / QuadTruss: radius (0.5 m steps), arc angle (presets 30 / 45 / 90 / 180°, or any 5-355°), bend. */
    private void initCurved() {
        int cx = left() + 150, y = top() + 36, w = W - 162;
        addRenderableWidget(new Ui.Button(cx, y + 12, 22, 20, () -> Component.literal("−"), ACCENT, Ui.Style.NORMAL,
            () -> { radius2 = Math.max(RingTrussBlockEntity.MIN_CURVE_RADIUS2, radius2 - 1); send(); }));
        addRenderableWidget(new Ui.Button(cx + w - 22, y + 12, 22, 20, () -> Component.literal("+"), ACCENT, Ui.Style.NORMAL,
            () -> { radius2 = Math.min(RingTrussBlockEntity.MAX_CURVE_RADIUS2, radius2 + 1); send(); }));
        int[] presets = {30, 45, 90, 180};
        int pw = (w - 9) / 4;
        for (int i = 0; i < 4; i++) {
            final int v = presets[i];
            addRenderableWidget(new Ui.Button(cx + i * (pw + 3), y + 52, pw, 16, () -> Component.literal(v + "°"), ACCENT, Ui.Style.NORMAL,
                () -> { sweep = v; send(); rebuildWidgets(); }).selected(sweep == v));
        }
        addRenderableWidget(new Ui.Slider(cx, y + 72, w, 22, Component.translatable("gui.concertstage.ring_angle"), 5, 355, sweep, ACCENT,
            v -> v + "°", v -> { sweep = v; send(); rebuildWidgets(); }));
        String[] bn = {"←", "→", "↑", "↓"};
        int bw = (w - 9) / 4;
        for (int i = 0; i < 4; i++) {
            final int b = i;
            addRenderableWidget(new Ui.Button(cx + i * (bw + 3), y + 108, bw, 18, () -> Component.literal(bn[b]), ACCENT, Ui.Style.NORMAL,
                () -> { bend = b; send(); rebuildWidgets(); }).selected(bend == b));
        }
        int half = (w - 4) / 2;
        addRenderableWidget(new Ui.Button(cx, y + 136, half, 16, () -> Component.translatable("gui.concertstage.ring_alu"), ACCENT, Ui.Style.NORMAL,
            () -> { black = false; send(); rebuildWidgets(); }).selected(!black));
        addRenderableWidget(new Ui.Button(cx + half + 4, y + 136, half, 16, () -> Component.translatable("gui.concertstage.ring_black"), ACCENT, Ui.Style.NORMAL,
            () -> { black = true; send(); rebuildWidgets(); }).selected(black));
        addRenderableWidget(new Ui.Button(cx, y + 158, w, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    private void renderCurved(GuiGraphics g) {
        Ui.panel(g, left(), top(), W, H, ACCENT, Component.translatable(section == 1 ? "block.concertstage.curved_tritruss" : "block.concertstage.curved_quadtruss"),
            Component.translatable("gui.concertstage.curve_subtitle"));
        int cx = left() + 150, y = top() + 36, w = W - 162;
        Ui.section(g, cx, y, w, Component.translatable("gui.concertstage.ring_radius"));
        g.drawCenteredString(font, String.format("%.1f m", radius2 / 2.0), cx + w / 2, y + 18, Ui.TEXT);
        double arc = Math.toRadians(sweep) * radius2 / 2.0;
        g.drawCenteredString(font, Component.translatable("gui.concertstage.curve_length", String.format("%.2f", arc)), cx + w / 2, y + 28, Ui.MUTED);
        Ui.section(g, cx, y + 40, w, Component.translatable("gui.concertstage.ring_angle"));
        Ui.section(g, cx, y + 98, w, Component.translatable("gui.concertstage.curve_bend"));
        Ui.section(g, cx, y + 128, w, Component.translatable("gui.concertstage.ring_finish"));
        // plan view of the arc from the block (A) to its end (B)
        int px = left() + 10, py = top() + 34, pw = 128, ph = H - 44;
        g.fill(px, py, px + pw, py + ph, 0xFF0B0E12);
        Ui.frame(g, px, py, pw, ph, Ui.LINE);
        double r = radius2 / 2.0, sw = Math.toRadians(sweep);
        double ext = Math.max(2 * r * Math.sin(Math.min(sw, Math.PI) / 2), r * (1 - Math.cos(Math.min(sw, Math.PI)))) + 1;
        double scale = Math.min(9.0, (pw - 20) / Math.max(2, Math.max(ext, sweep > 180 ? 2 * r : ext)));
        int ax = px + pw / 2, ay = py + ph - 16;
        int side = bend == 1 ? -1 : 1;
        for (int i = 0; i <= 120; i++) {
            double a = sw * i / 120.0;
            double fx = r * Math.sin(a), lx = r * (1 - Math.cos(a));       // forward, sideways
            int sx = ax - side * (int) Math.round(lx * scale), sy = ay - (int) Math.round(fx * scale);
            if (bend >= 2) { sx = ax + (int) Math.round(fx * scale * 0.0); sy = ay - (int) Math.round(fx * scale); sx = ax + (int) Math.round((bend == 2 ? -1 : 1) * lx * scale * 0.6); }
            g.fill(sx, sy, sx + 2, sy + 2, black ? 0xFFB9C0C8 : ACCENT);
        }
        g.fill(ax - 2, ay - 2, ax + 3, ay + 3, Ui.AMBER);
        g.drawString(font, "A", ax + 4, ay - 4, Ui.AMBER, false);
        g.drawString(font, sweep + "°", px + 6, py + ph - 12, Ui.GREEN, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (curved) {
            renderBackground(g);
            renderCurved(g);
            super.render(g, mouseX, mouseY, partial);
            return;
        }
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable("gui.concertstage.ring_subtitle"));
        int cx = left() + 150, y = top() + 36, w = W - 162;
        Ui.section(g, cx, y, w, Component.translatable("gui.concertstage.ring_radius"));
        String r = String.format("%.1f m", radius2 / 2.0);
        g.drawCenteredString(font, r, cx + w / 2, y + 18, Ui.TEXT);
        g.drawCenteredString(font, Component.translatable("gui.concertstage.ring_diameter", String.format("%.1f", radius2 * 1.0)), cx + w / 2, y + 30 - 2, Ui.MUTED);
        Ui.section(g, cx, y + 40, w, Component.translatable("gui.concertstage.ring_tilt"));
        Ui.section(g, cx, y + 98, w, Component.translatable("gui.concertstage.ring_yaw"));
        g.drawCenteredString(font, yaw + "°", cx + w / 2, y + 113, Ui.TEXT);
        Ui.section(g, cx, y + 128, w, Component.translatable("gui.concertstage.ring_finish"));
        // live preview
        int px = left() + 10, py = top() + 34, pw = 128, ph = H - 44;
        g.fill(px, py, px + pw, py + ph, 0xFF0B0E12);
        Ui.frame(g, px, py, pw, ph, Ui.LINE);
        for (int gx = px + 8; gx < px + pw; gx += 12) g.fill(gx, py + 1, gx + 1, py + ph - 1, 0xFF11161C);
        for (int gy = py + 8; gy < py + ph; gy += 12) g.fill(px + 1, gy, px + pw - 1, gy + 1, 0xFF11161C);
        double shown = tilt;
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(pos) instanceof RingTrussBlockEntity be) shown = be.tiltAt(level.getGameTime(), partial);
        double t = Math.toRadians(shown), f = Math.toRadians(-yaw);
        int ox = px + pw / 2, top = py + 22;
        double rad = radius2 / 2.0;
        double scale = Math.min(10.0, 44.0 / rad);
        double topY = rad * Math.sin(t);
        g.fill(ox - 3, py + 10, ox + 4, py + 13, Ui.MUTED);                        // rigging point
        g.fill(ox, py + 13, ox + 1, top, Ui.MUTED);
        int n = 96;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double x = Math.cos(a) * rad, z = Math.sin(a) * rad;
            double yy = -z * Math.sin(t), zz = z * Math.cos(t);
            double x2 = x * Math.cos(f) + zz * Math.sin(f), z2 = -x * Math.sin(f) + zz * Math.cos(f);
            int sx = ox + (int) Math.round((x2 - z2 * 0.45) * scale);
            int sy = top + (int) Math.round((topY - yy + z2 * 0.25) * scale);
            boolean back = z2 > 0;
            g.fill(sx, sy, sx + 2, sy + 2, back ? Ui.alpha(black ? 0xFF8A9099 : ACCENT, 0x70) : (black ? 0xFFB9C0C8 : ACCENT));
        }
        g.drawString(font, String.format("%.0f°", shown), px + 6, py + ph - 12, Math.abs(shown - tilt) > 0.5 ? Ui.AMBER : Ui.GREEN, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
