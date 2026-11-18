package com.concertstage.client.light;

import com.concertstage.client.ui.Ui;
import com.concertstage.light.FixtureAngleToolItem;
import com.concertstage.net.StageNetwork;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Rev. 25 — the screen of the fixture angle tool (asked 12 November): a protractor with the fixture seen from the side, the angle in
 * large figures, -15 / -5 / -1 / +1 / +5 / +15 buttons, preset angles, Copy (keeps the angle in the tool) and Paste (the copied angle
 * on this fixture). Sneak + right click another fixture pastes without opening the screen.
 */
public class FixtureAngleScreen extends Screen {
    private static final int W = 312, H = 230;
    /** 1.2.1: the gels of the conventionals (colours of the mod, not of a real gel range): open, straw, amber, red, pink, lavender,
     * light blue, deep blue, green, cyan. */
    public static final int[] GELS = {0xFFFFFF, 0xFFE2A8, 0xFFB347, 0xFF2A1A, 0xFF7A9C, 0xB896FF, 0x8CB8FF, 0x2B4BFF, 0x3DD65A, 0x45D8E6};
    public static final String[] GEL_KEYS = {"open", "straw", "amber", "red", "pink", "lavender", "light_blue", "deep_blue", "green", "cyan"};
    private static final int[] STEPS = {-15, -5, -1, 1, 5, 15};
    private static final int[] PRESETS = {-45, -30, -15, 0, 15, 30, 45, 60};
    private final BlockPos pos;
    private final String fixture;
    private float tilt, copied;

    private FixtureAngleScreen(BlockPos pos, float tilt, String fixture, float copied) {
        super(Component.translatable("gui.concertstage.fixture_angle_title"));
        this.pos = pos;
        this.tilt = Float.isFinite(tilt) ? tilt : 0f;
        this.fixture = fixture;
        this.copied = copied;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(pos) instanceof com.concertstage.light.FixtureBlockEntity be) gel = be.gel;
    }

    private int gel = 0xFFFFFF;

    private boolean conventional() {
        return Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(pos) instanceof com.concertstage.light.FixtureBlockEntity be
            && be.block() != null && be.block().profile().conventional;
    }

    public static void open(BlockPos pos, float tilt, String fixture, float copied) {
        Minecraft.getInstance().setScreen(new FixtureAngleScreen(pos, tilt, fixture, copied));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private void set(float t) {
        tilt = Math.max(-90f, Math.min(90f, Math.round(t * 10f) / 10f));
        StageNetwork.sendTarpSettings(pos, FixtureAngleToolItem.OP_SET, Math.round(tilt * 10f) + 900, 0);
        rebuildWidgets();
    }

    private static String deg(float v) {
        return String.format(Locale.ROOT, v == Math.round(v) ? "%+.0f°" : "%+.1f°", v);
    }

    @Override
    protected void init() {
        int x = left() + 12, w = W - 24, y = top() + 128;
        int cw = (w - 5 * 3) / 6;
        for (int i = 0; i < STEPS.length; i++) {
            final int s = STEPS[i];
            addRenderableWidget(new Ui.Button(x + i * (cw + 3), y, cw, 17, () -> Component.literal((s > 0 ? "+" : "") + s + "°"), Ui.AMBER, Ui.Style.NORMAL,
                () -> set(tilt + s)));
        }
        int pw = (w - 7 * 3) / 8;
        for (int i = 0; i < PRESETS.length; i++) {
            final int v = PRESETS[i];
            addRenderableWidget(new Ui.Button(x + i * (pw + 3), y + 21, pw, 17, () -> Component.literal((v > 0 ? "+" : "") + v + "°"), Ui.AMBER, Ui.Style.NORMAL,
                () -> set(v)).selected(Math.abs(tilt - v) < 0.05f));
        }
        int bw = (w - 2 * 3) / 3;
        addRenderableWidget(new Ui.Button(x, y + 46, bw, 18, () -> Component.translatable("gui.concertstage.angle_copy"), Ui.CYAN, Ui.Style.NORMAL, () -> {
            copied = tilt;
            StageNetwork.sendTarpSettings(pos, FixtureAngleToolItem.OP_COPY, Math.round(tilt * 10f) + 900, 0);
            rebuildWidgets();
        }));
        Ui.Button paste = new Ui.Button(x + bw + 3, y + 46, bw, 18,
            () -> Float.isNaN(copied) ? Component.translatable("gui.concertstage.angle_paste_none") : Component.translatable("gui.concertstage.angle_paste", deg(copied)),
            Ui.CYAN, Ui.Style.NORMAL, () -> { if (!Float.isNaN(copied)) set(copied); });
        addRenderableWidget(paste);
        addRenderableWidget(new Ui.Button(x + 2 * (bw + 3), y + 46, bw, 18, () -> Component.translatable("gui.concertstage.done_short"), Ui.AMBER, Ui.Style.ACCENT, this::onClose));
        // 1.2.1: the gel of a conventional (one row under the screen's buttons)
        if (conventional()) {
            int gw = (w - 9 * 2) / 10;
            for (int i = 0; i < GELS.length; i++) {
                final int c = GELS[i];
                Ui.Button b = new Ui.Button(x + i * (gw + 2), y + 72, gw, 16, () -> Component.empty(), 0xFF000000 | c, Ui.Style.ACCENT, () -> {
                    gel = c;
                    StageNetwork.sendTarpSettings(pos, FixtureAngleToolItem.OP_GEL, c, 0);
                    rebuildWidgets();
                }).selected(gel == c);
                com.concertstage.client.ui.Tr.help(addRenderableWidget(b), "gel_" + GEL_KEYS[i]);
            }
        }
        // 1.2.1: free placement of this fixture (offset, yaw, pitch, roll)
        addRenderableWidget(new Ui.Button(left() + W - 112, top() + 5, 100, 16, () -> Component.translatable("gui.concertstage.pose_open"), Ui.CYAN, Ui.Style.GHOST,
            () -> FixturePoseScreen.open(pos, fixture, false)));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        set(tilt + (delta > 0 ? 1 : -1) * (hasShiftDown() ? 5 : 1));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, Ui.AMBER, title, Component.literal(fixture));
        // protractor seen from the side: 0° = horizontal, + = up
        int cx = left() + 70, cy = top() + 78, r = 46;
        for (int a = -90; a <= 90; a += 5) {
            double t = Math.toRadians(a);
            int len = a % 45 == 0 ? 8 : a % 15 == 0 ? 5 : 2;
            for (int k = 0; k < len; k++) {
                int px = cx + (int) Math.round(Math.cos(t) * (r - k)), py = cy - (int) Math.round(Math.sin(t) * (r - k));
                g.fill(px, py, px + 1, py + 1, a == 0 ? Ui.TEXT : Ui.MUTED);
            }
        }
        // the fixture body and its beam
        double t = Math.toRadians(tilt);
        float dx = (float) Math.cos(t), dy = (float) -Math.sin(t);
        for (int k = -6; k < r + 22; k++) {
            int px = cx + Math.round(dx * k), py = cy + Math.round(dy * k);
            int col = k < 6 ? 0xFF5A5F66 : k < r - 6 ? 0xC0FFC870 : 0x60FFC870;
            int half = k < 6 ? 4 : 1 + Math.max(0, (k - 6) / 14);
            for (int o = -half; o <= half; o++) g.fill(px - Math.round(dy * o), py + Math.round(dx * o), px - Math.round(dy * o) + 1, py + Math.round(dx * o) + 1, col);
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, Ui.AMBER);
        g.pose().pushPose();
        g.pose().translate(left() + 214, top() + 58, 0);
        g.pose().scale(2.4f, 2.4f, 1f);
        g.drawCenteredString(font, deg(tilt), 0, 0, Ui.TEXT);
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("gui.concertstage.angle_hint"), left() + 214, top() + 90, Ui.MUTED);
        if (!Float.isNaN(copied)) g.drawCenteredString(font, Component.translatable("gui.concertstage.angle_copied", deg(copied)), left() + 214, top() + 104, Ui.CYAN);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
