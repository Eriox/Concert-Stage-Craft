package com.concertstage.client.light;

import com.concertstage.client.ui.Ui;
import com.concertstage.light.FixtureAngleToolItem;
import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.net.StageNetwork;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 1.2.1 — free placement of a fixture (angle tool, right click): offset of the base from the centre of its block (X, Y, Z in
 * centimetres, 2 blocks each way at most) and its turn (yaw, pitch, roll in degrees). Each click is applied at once (the fixture,
 * its beam and the desk's aiming follow). Copy keeps the pose in the tool; sneak + right click another fixture pastes it.
 */
public class FixturePoseScreen extends Screen {
    private static final int W = 330, H = 236;
    private static final String[] ROWS = {"x", "y", "z", "yaw", "pitch", "roll"};
    private static final int[] STEP_CM = {-10, -1, 1, 10}, STEP_DEG = {-15, -1, 1, 15};
    private final BlockPos pos;
    private final String fixture;
    /** x, y, z (blocks), yaw, pitch, roll (degrees). */
    private final float[] v = new float[6];
    private boolean copiedPose;

    private FixturePoseScreen(BlockPos pos, String fixture, boolean copiedPose) {
        super(Component.translatable("gui.concertstage.pose_title"));
        this.pos = pos;
        this.fixture = fixture;
        this.copiedPose = copiedPose;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(pos) instanceof FixtureBlockEntity be) {
            v[0] = be.offX; v[1] = be.offY; v[2] = be.offZ; v[3] = be.rotYaw; v[4] = be.rotPitch; v[5] = be.rotRoll;
        }
    }

    public static void open(BlockPos pos, String fixture, boolean copiedPose) {
        Minecraft.getInstance().setScreen(new FixturePoseScreen(pos, fixture, copiedPose));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private static float limit(int row) {
        return row < 3 ? FixtureBlockEntity.MAX_OFFSET : row == 4 ? 90f : 180f;
    }

    private void change(int row, float delta) {
        float n = v[row] + delta;
        v[row] = Math.max(-limit(row), Math.min(limit(row), row < 3 ? Math.round(n * 100f) / 100f : Math.round(n * 2f) / 2f));
        send(row < 3);
    }

    private void send(boolean position) {
        if (position)
            StageNetwork.sendTarpSettings(pos, FixtureAngleToolItem.OP_POSE_POS, FixtureAngleToolItem.packPos(v[0], v[1], v[2]), 0);
        else
            StageNetwork.sendTarpSettings(pos, FixtureAngleToolItem.OP_POSE_ROT, FixtureAngleToolItem.packRot(v[3], v[4]), FixtureAngleToolItem.packRoll(v[5]));
    }

    private static String value(int row, float x) {
        return row < 3 ? String.format(Locale.ROOT, "%+.0f cm", x * 100f) : String.format(Locale.ROOT, x == Math.round(x) ? "%+.0f°" : "%+.1f°", x);
    }

    @Override
    protected void init() {
        int x = left() + 12, y = top() + 48;
        for (int r = 0; r < ROWS.length; r++) {
            final int row = r;
            int ry = y + r * 21 + (r >= 3 ? 12 : 0);
            int[] steps = r < 3 ? STEP_CM : STEP_DEG;
            for (int i = 0; i < steps.length; i++) {
                final int s = steps[i];
                int bx = i < 2 ? x + 100 + i * 38 : x + 230 + (i - 2) * 38;   // value shown between the two pairs
                addRenderableWidget(new Ui.Button(bx, ry, 36, 17, () -> Component.literal((s > 0 ? "+" : "") + s + (row < 3 ? "" : "°")),
                    Ui.AMBER, Ui.Style.NORMAL, () -> change(row, row < 3 ? s / 100f : s)));
            }
        }
        int by = top() + H - 26, bw = (W - 24 - 3 * 3) / 4;
        addRenderableWidget(new Ui.Button(x, by, bw, 18, () -> Component.translatable("gui.concertstage.pose_reset"), Ui.ORANGE, Ui.Style.NORMAL, () -> {
            java.util.Arrays.fill(v, 0f);
            StageNetwork.sendTarpSettings(pos, FixtureAngleToolItem.OP_POSE_RESET, 0, 0);
        }));
        addRenderableWidget(new Ui.Button(x + bw + 3, by, bw, 18, () -> Component.translatable("gui.concertstage.pose_copy"), Ui.CYAN, Ui.Style.NORMAL, () -> {
            copiedPose = true;
            StageNetwork.sendTarpSettings(pos, FixtureAngleToolItem.OP_POSE_COPY, 0, 0);
        }));
        addRenderableWidget(new Ui.Button(x + 2 * (bw + 3), by, bw, 18,
            () -> Component.translatable(copiedPose ? "gui.concertstage.pose_paste" : "gui.concertstage.angle_paste_none"), Ui.CYAN, Ui.Style.NORMAL, () -> {
                if (copiedPose) StageNetwork.sendTarpSettings(pos, FixtureAngleToolItem.OP_POSE_PASTE, 0, 0);
            }));
        addRenderableWidget(new Ui.Button(x + 3 * (bw + 3), by, bw, 18, () -> Component.translatable("gui.concertstage.done_short"), Ui.AMBER, Ui.Style.ACCENT, this::onClose));
    }

    @Override
    public void tick() {
        // the server's values (after a paste, a reset, or a limit)
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof FixtureBlockEntity be) {
            v[0] = be.offX; v[1] = be.offY; v[2] = be.offZ; v[3] = be.rotYaw; v[4] = be.rotPitch; v[5] = be.rotRoll;
        } else onClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int y = top() + 48;
        for (int r = 0; r < ROWS.length; r++) {
            int ry = y + r * 21 + (r >= 3 ? 12 : 0);
            if (my >= ry - 2 && my < ry + 19) {
                float step = r < 3 ? (hasShiftDown() ? 0.10f : 0.01f) : (hasShiftDown() ? 5f : 1f);
                change(r, delta > 0 ? step : -step);
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, Ui.AMBER, title, Component.literal(fixture));
        int x = left() + 12, y = top() + 48;
        Ui.section(g, x, y - 13, W - 24, Component.translatable("gui.concertstage.pose_position"));
        Ui.section(g, x, y + 3 * 21, W - 24, Component.translatable("gui.concertstage.pose_rotation"));
        for (int r = 0; r < ROWS.length; r++) {
            int ry = y + r * 21 + (r >= 3 ? 12 : 0);
            g.drawString(font, Component.translatable("gui.concertstage.pose_" + ROWS[r]), x, ry + 5, Ui.TEXT, false);
            g.drawCenteredString(font, value(r, v[r]), x + 202, ry + 5, v[r] == 0 ? Ui.MUTED : Ui.AMBER);
        }
        g.drawCenteredString(font, Component.translatable("gui.concertstage.pose_hint"), left() + W / 2, top() + H - 40, Ui.MUTED);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
