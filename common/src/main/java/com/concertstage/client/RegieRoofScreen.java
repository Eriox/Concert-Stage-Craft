package com.concertstage.client;

import com.concertstage.block.regie.RegieRoofBlockEntity;
import com.concertstage.client.ui.Ui;
import com.concertstage.net.StageNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * CSC RoofCommand on a FOH tarp roof (purple, like the roof frames): shape, pitch, overhang, valance and tarp colour, with a live front
 * view on the left. Each change is sent at once; the roof fits itself to the FOH structure again.
 */
public class RegieRoofScreen extends Screen {
    private static final int W = 316, H = 236, ACCENT = Ui.PURPLE;
    private static final int[] SWATCH = {0xFF1A1B1F, 0xFF6E7278, 0xFFE9EAEC, 0xFF2F57B8, 0xFFB8302F};
    private final BlockPos pos;
    private int shape, pitch, overhang10, valance20, color;

    public RegieRoofScreen(BlockPos pos, RegieRoofBlockEntity be) {
        super(Component.translatable("gui.concertstage.foh_roof_title"));
        this.pos = pos;
        this.shape = be.shape();
        this.pitch = be.pitch();
        this.overhang10 = be.overhang10();
        this.valance20 = be.valance20();
        this.color = be.color();
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private void send() {
        StageNetwork.sendTarpSettings(pos, overhang10, valance20, shape | (pitch << 2) | (color << 5));
    }

    @Override
    protected void init() {
        int cx = left() + 140, y = top() + 34, w = W - 152, third = (w - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int s = i;
            addRenderableWidget(new Ui.Button(cx + i * (third + 4), y + 12, third, 16, () -> Component.translatable("gui.concertstage.foh_roof_shape_" + s),
                ACCENT, Ui.Style.NORMAL, () -> { shape = s; send(); rebuildWidgets(); }).selected(shape == s));
        }
        stepper(cx, y + 46, w, () -> { pitch = Math.max(0, pitch - 1); send(); }, () -> { pitch = Math.min(4, pitch + 1); send(); }, shape != RegieRoofBlockEntity.FLAT);
        stepper(cx, y + 80, w, () -> { overhang10 = Math.max(0, overhang10 - 1); send(); }, () -> { overhang10 = Math.min(10, overhang10 + 1); send(); }, true);
        stepper(cx, y + 114, w, () -> { valance20 = Math.max(0, valance20 - 1); send(); }, () -> { valance20 = Math.min(12, valance20 + 1); send(); }, true);
        int sw = (w - 4 * 4) / 5;
        for (int i = 0; i < 5; i++) {
            final int c = i;
            addRenderableWidget(new Ui.Button(cx + i * (sw + 4), y + 150, sw, 16, () -> Component.literal(""), SWATCH[c] | 0xFF000000,
                Ui.Style.NORMAL, () -> { color = c; send(); rebuildWidgets(); }).selected(color == c));
        }
        // aurex.2: take the roof back from here (the anchor is hard to find under the tarp)
        int half = (w - 4) / 2;
        addRenderableWidget(new Ui.Button(cx, y + 176, half, 16, () -> Component.translatable("gui.concertstage.foh_roof_remove"), Ui.RED, Ui.Style.DANGER, () -> {
            StageNetwork.sendTarpSettings(pos, overhang10, valance20, RegieRoofBlockEntity.REMOVE);
            onClose();
        }));
        addRenderableWidget(new Ui.Button(cx + half + 4, y + 176, w - half - 4, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    private void stepper(int x, int y, int w, Runnable minus, Runnable plus, boolean on) {
        Ui.Button m = new Ui.Button(x, y, 22, 18, () -> Component.literal("−"), ACCENT, Ui.Style.NORMAL, minus);
        Ui.Button p = new Ui.Button(x + w - 22, y, 22, 18, () -> Component.literal("+"), ACCENT, Ui.Style.NORMAL, plus);
        m.active = on; p.active = on;
        addRenderableWidget(m);
        addRenderableWidget(p);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable("gui.concertstage.foh_roof_subtitle"));
        int cx = left() + 140, y = top() + 34, w = W - 152;
        Ui.section(g, cx, y, w, Component.translatable("gui.concertstage.foh_roof_shape"));
        Ui.section(g, cx, y + 34, w, Component.translatable("gui.concertstage.foh_roof_pitch"));
        g.drawCenteredString(font, Component.translatable("gui.concertstage.foh_roof_pitch_" + pitch), cx + w / 2, y + 51, shape == RegieRoofBlockEntity.FLAT ? Ui.MUTED : Ui.TEXT);
        Ui.section(g, cx, y + 68, w, Component.translatable("gui.concertstage.foh_roof_overhang"));
        g.drawCenteredString(font, (overhang10 * 10) + " cm", cx + w / 2, y + 85, Ui.TEXT);
        Ui.section(g, cx, y + 102, w, Component.translatable("gui.concertstage.foh_roof_valance"));
        g.drawCenteredString(font, (valance20 * 5) + " cm", cx + w / 2, y + 119, Ui.TEXT);
        Ui.section(g, cx, y + 138, w, Component.translatable("gui.concertstage.foh_roof_color"));
        // front view: posts, the roof outline in the chosen colour
        int px = left() + 12, py = top() + 34, pw = 118, ph = 150;
        g.fill(px, py, px + pw, py + ph, 0xFF15171B);
        Ui.frame(g, px, py, pw, ph, Ui.LINE);
        int base = py + ph - 16, eave = py + 70, l = px + 14, r = px + pw - 14;
        int ov = Math.round(overhang10 * 1.6f), val = Math.round(valance20 * 2.2f);
        int col = SWATCH[color] | 0xFF000000;
        g.fill(px + 22, eave, px + 25, base, 0xFF9AA0A8);
        g.fill(px + pw - 25, eave, px + pw - 22, base, 0xFF9AA0A8);
        g.fill(px + 6, base, px + pw - 6, base + 2, Ui.MUTED);
        double[] f = {0.12, 0.2, 0.28, 0.38, 0.5};
        int rise = shape == RegieRoofBlockEntity.FLAT ? 0 : (int) Math.round((r - l) * f[pitch] * (shape == RegieRoofBlockEntity.MONO ? 0.65 : 1));
        int x0 = l - ov, x1 = r + ov;
        for (int x = x0; x <= x1; x++) {
            double t = (x - x0) / (double) (x1 - x0);
            double h = shape == RegieRoofBlockEntity.MONO ? rise * t : shape == RegieRoofBlockEntity.GABLE ? rise * (1 - Math.abs(2 * t - 1)) : 0;
            int top = eave - (int) Math.round(h);
            g.fill(x, top - 2, x + 1, eave, col);
        }
        if (val > 0) g.fill(x0, eave, x1 + 1, eave + val, col);
        g.fill(x0, eave - 1, x1 + 1, eave, Ui.alpha(0xFFFFFFFF, 0x30));
        super.render(g, mouseX, mouseY, partial);
        int sw = (w - 4 * 4) / 5;
        for (int i = 0; i < 5; i++) {                                                   // colour swatches on their buttons
            int bx = cx + i * (sw + 4);
            g.fill(bx + 4, y + 154, bx + sw - 4, y + 162, SWATCH[i]);
            Ui.frame(g, bx + 4, y + 154, sw - 8, 8, 0x60FFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
