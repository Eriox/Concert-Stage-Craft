package com.concertstage.client;

import com.concertstage.block.roof.RoofTrussBlockEntity;
import com.concertstage.client.ui.Ui;
import com.concertstage.net.StageNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * Roof controller CSC RoofCommand (purple): shape (arc / gable / single slope), span, rise, direction and finish,
 * with a live side view of the frame on the left. Each change is sent at once.
 */
public class RoofScreen extends Screen {
    private static final int W = 330, H = 304, ACCENT = Ui.PURPLE;
    private static final String[] SHAPES = {"gui.concertstage.roof_arc", "gui.concertstage.roof_gable", "gui.concertstage.roof_slope", "gui.concertstage.roof_bow"};
    /** Order of the shape buttons: arc, arc on posts / gable, slope. */
    private static final int[] ORDER = {RoofTrussBlockEntity.ARC, RoofTrussBlockEntity.BOW, RoofTrussBlockEntity.GABLE, RoofTrussBlockEntity.SLOPE};
    private final BlockPos pos;
    private int span, rise2, shape, turn, cover;
    private boolean black, full, heavy;
    private int overhang, skirtMode;
    private boolean flaps;
    /** Gable on a middle truss (truss columns under the ridge). */
    private boolean kingPost, kingSmall;

    public RoofScreen(BlockPos pos, RoofTrussBlockEntity be) {
        super(Component.translatable("gui.concertstage.roof_title"));
        this.pos = pos;
        this.span = be.span();
        this.rise2 = be.rise2();
        this.shape = be.shape();
        this.turn = be.turn();
        this.black = be.isBlack();
        this.cover = be.cover();
        this.full = be.isFull();
        this.heavy = be.isHeavy();
        this.overhang = be.overhang();
        this.skirtMode = be.skirtMode();
        this.flaps = be.hasFlaps();
        this.kingPost = be.hasKingPost();
        this.kingSmall = be.isKingSmall();
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private void send() {
        StageNetwork.sendTarpSettings(pos, span, rise2, shape | (turn << 2) | (black ? 32 : 0) | ((cover + 128) << 20)
            | (full ? 1 << 12 : 0) | (heavy ? 1 << 13 : 0) | (overhang << 14)
            | (skirtMode << 16) | (flaps ? 1 << 18 : 0) | (kingPost && shape == RoofTrussBlockEntity.GABLE ? 1 << 19 : 0) | (kingPost && kingSmall ? 1 << 6 : 0));
    }

    @Override
    protected void init() {
        int cx = left() + 150, y = top() + 36, w = W - 162;
        int half = (w - 4) / 2;
        for (int i = 0; i < 4; i++) {
            final int s = ORDER[i];
            addRenderableWidget(new Ui.Button(cx + (i % 2) * (half + 4), y + 12 + (i / 2) * 18, half, 16, () -> Component.translatable(SHAPES[s]), ACCENT,
                Ui.Style.NORMAL, () -> { shape = s; kingPost = false; if (s == RoofTrussBlockEntity.BOW && rise2 > 6) rise2 = 3; send(); rebuildWidgets(); })
                .selected(shape == s && !(s == RoofTrussBlockEntity.GABLE && kingPost)));
        }
        int kw = (w - 4) / 2;                                                                                  // gable on a middle truss: frame truss / small truss
        addRenderableWidget(new Ui.Button(cx, y + 48, kw, 16, () -> Component.translatable("gui.concertstage.roof_gable_king"), ACCENT,
            Ui.Style.NORMAL, () -> { shape = RoofTrussBlockEntity.GABLE; kingPost = true; kingSmall = false; send(); rebuildWidgets(); })
            .selected(shape == RoofTrussBlockEntity.GABLE && kingPost && !kingSmall));
        addRenderableWidget(new Ui.Button(cx + kw + 4, y + 48, kw, 16, () -> Component.translatable("gui.concertstage.roof_gable_king_small"), ACCENT,
            Ui.Style.NORMAL, () -> { shape = RoofTrussBlockEntity.GABLE; kingPost = true; kingSmall = true; send(); rebuildWidgets(); })
            .selected(shape == RoofTrussBlockEntity.GABLE && kingPost && kingSmall));
        stepper(cx, y + 78, w, () -> { span = Math.max(RoofTrussBlockEntity.MIN_SPAN, span - step()); send(); },
            () -> { span = Math.min(RoofTrussBlockEntity.MAX_SPAN, span + step()); send(); });
        stepper(cx, y + 110, w, () -> { rise2 = Math.max(0, rise2 - 1); send(); },
            () -> { rise2 = Math.min(RoofTrussBlockEntity.MAX_RISE2, rise2 + 1); send(); });
        stepper(cx, y + 142, w, () -> { cover = Math.max(-RoofTrussBlockEntity.MAX_COVER, cover - step()); send(); },
            () -> { cover = Math.min(RoofTrussBlockEntity.MAX_COVER, cover + step()); send(); });
        addRenderableWidget(new Ui.Button(cx, y + 174, 26, 18, () -> Component.literal("↺"), ACCENT, Ui.Style.NORMAL,
            () -> { turn = (turn + 3) & 3; send(); }));
        addRenderableWidget(new Ui.Button(cx + 30, y + 174, 26, 18, () -> Component.literal("↻"), ACCENT, Ui.Style.NORMAL,
            () -> { turn = (turn + 1) & 3; send(); }));
        int fw = (w - 64) / 2;
        addRenderableWidget(new Ui.Button(cx + 60, y + 174, fw, 18, () -> Component.translatable("gui.concertstage.roof_alu"), ACCENT, Ui.Style.NORMAL,
            () -> { black = false; send(); rebuildWidgets(); }).selected(!black));
        addRenderableWidget(new Ui.Button(cx + 64 + fw, y + 174, fw, 18, () -> Component.translatable("gui.concertstage.roof_black"), ACCENT, Ui.Style.NORMAL,
            () -> { black = true; send(); rebuildWidgets(); }).selected(black));
        int fh = full ? (w - 4) / 2 : w;
        addRenderableWidget(new Ui.Button(cx, y + 200, fh, 18, () -> Component.translatable(full ? "gui.concertstage.roof_full_on" : "gui.concertstage.roof_full_off"),
            ACCENT, Ui.Style.NORMAL, () -> { full = !full; if (full && cover == 0) cover = 6; send(); rebuildWidgets(); }).selected(full));
        if (full) addRenderableWidget(new Ui.Button(cx + fh + 4, y + 200, fh, 18, () -> Component.translatable("gui.concertstage.roof_rotate"),
            ACCENT, Ui.Style.NORMAL, this::rotateRoof));
        // sheet down to the trusses, to meet the stage tarps: under the edge frames (none / start / opposite / both), at the frame ends
        int sh = (w - 4) / 2;
        addRenderableWidget(new Ui.Button(cx, y + 224, sh, 18, () -> Component.translatable("gui.concertstage.roof_skirt_" + skirtMode),
            ACCENT, Ui.Style.NORMAL, () -> { skirtMode = (skirtMode + 1) & 3; send(); rebuildWidgets(); }).selected(skirtMode != 0));
        addRenderableWidget(new Ui.Button(cx + sh + 4, y + 224, sh, 18, () -> Component.translatable(flaps ? "gui.concertstage.roof_flaps_on" : "gui.concertstage.roof_flaps_off"),
            ACCENT, Ui.Style.NORMAL, () -> { flaps = !flaps; send(); rebuildWidgets(); }).selected(flaps));
        addRenderableWidget(new Ui.Button(cx, y + 248, w, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    /**
     * Whole roof turned by 90 degrees on the same footprint: the frames take the other side of the rectangle (the ridge goes from front-back
     * to left-right or back), the slope angle is kept (the rise follows the new span).
     */
    private void rotateRoof() {
        if (cover == 0) return;
        Direction dir = Direction.from2DDataValue(turn);
        Direction next = cover > 0 ? dir.getClockWise() : dir.getCounterClockWise();
        int newSpan = Math.max(RoofTrussBlockEntity.MIN_SPAN, Math.min(RoofTrussBlockEntity.MAX_SPAN, Math.abs(cover)));
        int newCover = Math.max(-RoofTrussBlockEntity.MAX_COVER, Math.min(RoofTrussBlockEntity.MAX_COVER, cover > 0 ? -span : span));
        rise2 = Math.max(0, Math.min(RoofTrussBlockEntity.MAX_RISE2, (int) Math.round(rise2 * newSpan / (double) span)));
        span = newSpan;
        cover = newCover;
        turn = next.get2DDataValue();
        send();
        rebuildWidgets();
    }

    /** Step of the − / + buttons: 1 block, 8 blocks with Shift held (long roofs). */
    private static int step() {
        return hasShiftDown() ? 8 : 1;
    }

    private void stepper(int x, int y, int w, Runnable minus, Runnable plus) {
        addRenderableWidget(new Ui.Button(x, y, 22, 18, () -> Component.literal("−"), ACCENT, Ui.Style.NORMAL, minus));
        addRenderableWidget(new Ui.Button(x + w - 22, y, 22, 18, () -> Component.literal("+"), ACCENT, Ui.Style.NORMAL, plus));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable("gui.concertstage.roof_subtitle"));
        int cx = left() + 150, y = top() + 36, w = W - 162;
        Ui.section(g, cx, y, w, Component.translatable("gui.concertstage.roof_shape"));
        Ui.section(g, cx, y + 68, w, Component.translatable("gui.concertstage.roof_span"));
        g.drawCenteredString(font, span + " m", cx + w / 2, y + 83, Ui.TEXT);
        Ui.section(g, cx, y + 100, w, Component.translatable("gui.concertstage.roof_rise"));
        g.drawCenteredString(font, String.format("%.1f m", rise2 / 2.0), cx + w / 2, y + 115, Ui.TEXT);
        Ui.section(g, cx, y + 132, w, Component.translatable("gui.concertstage.roof_cover"));
        g.drawCenteredString(font, cover == 0 ? Component.translatable("gui.concertstage.roof_cover_none")
            : Component.translatable(cover > 0 ? "gui.concertstage.roof_cover_right" : "gui.concertstage.roof_cover_left", Math.abs(cover)), cx + w / 2, y + 147, Ui.TEXT);
        Ui.section(g, cx, y + 164, w, Component.translatable("gui.concertstage.roof_dir_finish",
            Component.translatable("gui.concertstage.pos." + Direction.from2DDataValue(turn).getName())));
        // side view of the frame, the grid trusses under its two ends
        int px = left() + 10, py = top() + 34, pw = 128, ph = H - 44;
        g.fill(px, py, px + pw, py + ph, 0xFF0B0E12);
        Ui.frame(g, px, py, pw, ph, Ui.LINE);
        for (int gx = px + 8; gx < px + pw; gx += 12) g.fill(gx, py + 1, gx + 1, py + ph - 1, 0xFF11161C);
        for (int gy = py + 8; gy < py + ph; gy += 12) g.fill(px + 1, gy, px + pw - 1, gy + 1, 0xFF11161C);
        double rise = rise2 / 2.0;
        double scale = Math.min((pw - 24) / (double) span, (ph - 60) / Math.max(1.0, rise + 1));
        int x0 = px + 12, base = py + ph - 30;
        int line = black ? 0xFFB9C0C8 : ACCENT;
        int n = Math.max(24, span * 4);
        for (int i = 0; i <= n; i++) {
            double along = span * (double) i / n;
            double hgt = RoofTrussBlockEntity.height(shape, span, rise, along);
            int sx = x0 + (int) Math.round(along * scale), sy = base - (int) Math.round(hgt * scale);
            g.fill(sx, sy - 2, sx + 2, sy + 1, line);
        }
        if (shape == RoofTrussBlockEntity.BOW) {                                                                   // posts down to the truss
            for (int i = 1; i * 1.5 < span; i++) {
                double along = i * 1.5, hgt = RoofTrussBlockEntity.height(shape, span, rise, along);
                int sx = x0 + (int) Math.round(along * scale);
                g.fill(sx, base - (int) Math.round(hgt * scale), sx + 1, base + 2, Ui.alpha(line, 0xB0));
            }
            g.fill(x0, base + 1, x0 + (int) Math.round(span * scale), base + 3, Ui.MUTED);
        }
        if (shape == RoofTrussBlockEntity.GABLE && kingPost) {                                                  // column down to the middle truss
            int sx = x0 + (int) Math.round(span / 2.0 * scale), top = base - (int) Math.round(rise * scale);
            int kwid = kingSmall ? 1 : 2;
            g.fill(sx - kwid, top, sx + kwid + 1, base + 2, Ui.alpha(line, 0xB0));
            g.fill(sx - 4, base + 2, sx + 5, base + 2 + Math.max(4, (int) Math.round(scale)), Ui.MUTED);
        }
        int tw = Math.max(4, (int) Math.round(scale));
        g.fill(x0 - tw / 2, base + 2, x0 + tw / 2 + 1, base + 2 + tw, Ui.MUTED);                                    // grid trusses
        int ex = x0 + (int) Math.round(span * scale);
        g.fill(ex - tw / 2, base + 2, ex + tw / 2 + 1, base + 2 + tw, Ui.MUTED);
        g.drawString(font, span + " m", px + 6, py + ph - 12, Ui.MUTED, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
