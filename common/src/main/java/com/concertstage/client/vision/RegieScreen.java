package com.concertstage.client.vision;

import com.concertstage.client.ui.Ui;
import com.concertstage.vision.CameraData;
import com.concertstage.vision.OutputState;
import com.concertstage.vision.VisionNet;
import com.concertstage.vision.VisionProcessorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * CSC 1.3.0 - the regie of a VISION PROCESSOR VP1: for each of its four outputs, PREVIEW and PROGRAM monitors, the sources (black,
 * test patterns, cameras, media), TAKE (with the chosen transition) and CUT, the automatic camera switcher with its countdown, media
 * transport, brightness and output mirroring. Click a source: PREVIEW; Shift + click: straight to PROGRAM (cut).
 */
public class RegieScreen extends Screen {
    private static final int W = 448, H = 272, ACCENT = Ui.RED, MW = 150, MH = 84, PAGE = 12;
    private final BlockPos pos;
    private int out, page;
    private String sig = "";

    public RegieScreen(BlockPos pos) {
        super(Component.translatable("gui.concertstage.lv.regie_title"));
        this.pos = pos;
        VisionClient.requestMediaList();
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private OutputState state() {
        OutputState[] o = VisionClient.outputs(pos);
        return o == null ? new OutputState() : o[Math.min(out, o.length - 1)];
    }

    private void send(int op, java.util.function.Consumer<com.concertstage.light.core.Bytes.Writer> args) {
        var w = VisionClient.proc(pos, op, out);
        if (args != null) args.accept(w);
        VisionClient.send(w);
    }

    private List<String> sources() {
        List<String> l = new ArrayList<>(List.of(OutputState.BLACK, OutputState.BARS, OutputState.GRID));
        List<CameraData.Cam> cams = new ArrayList<>(VisionClient.CAMERAS.values());
        cams.sort((a, b) -> Integer.compare(a.number(), b.number()));
        for (CameraData.Cam c : cams) l.add(OutputState.cam(BlockPos.of(c.pos())));
        for (String m : VisionClient.MEDIA) l.add("media:" + m);
        return l;
    }

    private String signature() {
        OutputState o = state();
        return out + "|" + o.program + "|" + o.preview + "|" + o.transition + "|" + o.duration + "|" + o.auto + o.autoPaused + o.random + o.interval
            + "|" + o.brightness + "|" + o.mirrorOf + "|" + o.loop + (o.pausedAt >= 0) + "|" + VisionClient.CAMERAS.size() + "|" + VisionClient.MEDIA.size() + "|" + page;
    }

    @Override
    public void tick() {
        String s = signature();
        if (!s.equals(sig) && !isDragging()) rebuildWidgets();
    }

    @Override
    protected void init() {
        sig = signature();
        OutputState o = state();
        int x = left() + 12, y = top() + 30;
        for (int i = 0; i < VisionProcessorBlockEntity.OUTPUTS; i++) {
            final int k = i;
            addRenderableWidget(new Ui.Button(x + i * 52, y, 48, 14, () -> Component.literal("OUT " + (k + 1)), ACCENT, Ui.Style.NORMAL,
                () -> { out = k; rebuildWidgets(); }).selected(out == k));
        }
        // centre column: TAKE / CUT / transition
        int cx = left() + 12 + MW + 8, cw = W - 24 - 2 * MW - 16, my = top() + 50;
        addRenderableWidget(new Ui.Button(cx, my, cw, 26, () -> Component.literal("TAKE"), ACCENT, Ui.Style.ACCENT, () -> send(VisionNet.P_TAKE, null)));
        addRenderableWidget(new Ui.Button(cx, my + 30, cw, 16, () -> Component.literal("CUT"), ACCENT, Ui.Style.NORMAL,
            () -> send(VisionNet.P_PROGRAM, w -> w.str(state().preview))));
        String[] tn = {"CUT", "DISS", "FADE"};
        int tw = (cw - 4) / 3;
        for (int i = 0; i < 3; i++) {
            final int t = i;
            addRenderableWidget(new Ui.Button(cx + i * (tw + 2), my + 50, tw, 14, () -> Component.literal(tn[t]), ACCENT, Ui.Style.NORMAL,
                () -> send(VisionNet.P_TRANSITION, w -> w.varInt(t).varInt(state().duration))).selected(o.transition == t));
        }
        addRenderableWidget(new Ui.Slider(cx, my + 68, cw, 22, Component.translatable("gui.concertstage.lv.duration"), 4, 60, o.duration, ACCENT,
            v -> String.format("%.1f s", v / 20f), v -> send(VisionNet.P_TRANSITION, w -> w.varInt(state().transition).varInt(v))));
        // sources
        List<String> src = sources();
        int pages = Math.max(1, (src.size() + PAGE - 1) / PAGE);
        page = Math.min(page, pages - 1);
        int sy = top() + 158, bw = (W - 24 - 5 * 4 - 30) / 6;
        for (int i = 0; i < PAGE; i++) {
            int idx = page * PAGE + i;
            if (idx >= src.size()) break;
            String s = src.get(idx);
            int col = i % 6, row = i / 6;
            Ui.Button b = new Ui.Button(x + col * (bw + 4), sy + row * 18, bw, 16, () -> Component.literal(short_(VisionClient.label(s))), ACCENT, Ui.Style.NORMAL,
                () -> {
                    if (Screen.hasShiftDown()) send(VisionNet.P_PROGRAM, w -> w.str(s));
                    else send(VisionNet.P_PREVIEW, w -> w.str(s));
                });
            addRenderableWidget(b.selected(s.equals(o.preview) || s.equals(o.program)));
        }
        addRenderableWidget(new Ui.Button(left() + W - 12 - 26, sy, 26, 16, () -> Component.literal("▲"), ACCENT, Ui.Style.GHOST,
            () -> { page = Math.max(0, page - 1); rebuildWidgets(); }));
        addRenderableWidget(new Ui.Button(left() + W - 12 - 26, sy + 18, 26, 16, () -> Component.literal("▼"), ACCENT, Ui.Style.GHOST,
            () -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); }));
        // auto switcher
        int ay = top() + 206;
        addRenderableWidget(new Ui.Button(x, ay, 58, 16, () -> Component.literal(o.auto ? "AUTO ON" : "AUTO OFF"), Ui.GREEN, Ui.Style.NORMAL,
            () -> sendAuto(!state().auto, state().random, state().interval)).selected(o.auto));
        addRenderableWidget(new Ui.Button(x + 62, ay, 46, 16, () -> Component.literal(o.random ? "RANDOM" : "SEQ"), Ui.GREEN, Ui.Style.NORMAL,
            () -> sendAuto(state().auto, !state().random, state().interval)));
        addRenderableWidget(new Ui.Button(x + 112, ay, 46, 16, () -> Component.literal(o.autoPaused ? "▶" : "❚❚"), Ui.GREEN, Ui.Style.NORMAL,
            () -> send(VisionNet.P_AUTO_PAUSE, w -> w.bool(!state().autoPaused))).selected(o.autoPaused));
        addRenderableWidget(new Ui.Slider(x + 164, ay - 6, 110, 22, Component.translatable("gui.concertstage.lv.interval"), 2, 120, o.interval, Ui.GREEN,
            v -> v + " s", v -> sendAuto(state().auto, state().random, v)));
        // media transport
        int ty = top() + 230;
        String[] mt = {"▶", "❚❚", "⟲"};
        for (int i = 0; i < 3; i++) {
            final int a = i;
            addRenderableWidget(new Ui.Button(x + i * 26, ty, 24, 16, () -> Component.literal(mt[a]), Ui.BLUE, Ui.Style.NORMAL,
                () -> send(VisionNet.P_MEDIA, w -> w.varInt(a))));
        }
        addRenderableWidget(new Ui.Button(x + 80, ty, 46, 16, () -> Component.literal("LOOP"), Ui.BLUE, Ui.Style.NORMAL,
            () -> send(VisionNet.P_MEDIA, w -> w.varInt(state().loop ? 4 : 3))).selected(o.loop));
        addRenderableWidget(new Ui.Slider(x + 132, ty - 6, 110, 22, Component.translatable("gui.concertstage.lv.brightness"), 0, 100, o.brightness, Ui.AMBER,
            v -> v + " %", v -> send(VisionNet.P_BRIGHTNESS, w -> w.varInt(v))));
        addRenderableWidget(new Ui.Button(x + 250, ty, 92, 16,
            () -> o.mirrorOf < 0 ? Component.translatable("gui.concertstage.lv.mirror_none") : Component.translatable("gui.concertstage.lv.mirror", o.mirrorOf + 1),
            Ui.AMBER, Ui.Style.NORMAL, () -> {
                int m = state().mirrorOf + 1;
                if (m == out) m++;
                if (m >= VisionProcessorBlockEntity.OUTPUTS) m = -1;
                final int mm = m;
                send(VisionNet.P_MIRROR, w -> w.sInt(mm));
            }));
        addRenderableWidget(new Ui.Button(left() + W - 12 - 70, top() + H - 24, 70, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    private void sendAuto(boolean on, boolean random, int interval) {
        List<Long> cams = new ArrayList<>(VisionClient.CAMERAS.keySet());
        cams.sort((a, b) -> Integer.compare(VisionClient.CAMERAS.get(a).number(), VisionClient.CAMERAS.get(b).number()));
        List<Long> use = cams.subList(0, Math.min(32, cams.size()));
        send(VisionNet.P_AUTO, w -> {
            w.bool(on).bool(random).varInt(interval).varInt(use.size());
            for (long c : use) w.sLong(c);
        });
    }

    private static String short_(String s) {
        return s.length() > 11 ? s.substring(0, 10) + "…" : s;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable("gui.concertstage.lv.regie_sub", pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        OutputState o = state();
        int my = top() + 50;
        GuiVideo.monitor(g, left() + 12, my, MW, MH, o.preview, o, "PVW  " + VisionClient.label(o.preview), Ui.GREEN);
        GuiVideo.monitor(g, left() + W - 12 - MW, my, MW, MH, o.program, o, "PGM  " + VisionClient.label(o.program), Ui.RED);
        var level = minecraft.level;
        int x = left() + 12;
        Ui.section(g, x, top() + 150, W - 24, Component.translatable("gui.concertstage.lv.sources"));
        Ui.section(g, x, top() + 196, W - 24, Component.translatable("gui.concertstage.lv.auto"));
        if (o.auto && level != null) {
            long left = Math.max(0, o.nextSwitch - level.getGameTime());
            String txt = o.autoPaused ? "PAUSE" : String.format("%.1f s", left / 20f);
            g.drawString(font, txt, x + 284, top() + 210, o.autoPaused ? Ui.AMBER : Ui.GREEN, false);
        }
        if (VisionClient.CAMERAS.isEmpty()) g.drawString(font, Component.translatable("gui.concertstage.lv.no_cameras"), x + 330, top() + 210, Ui.MUTED, false);
        String feeds = CameraFeeds.status();
        if (!feeds.isEmpty()) g.drawString(font, Component.translatable("gui.concertstage.lv.feeds_" + feeds.split(" ")[0]), x, top() + H - 20, Ui.AMBER, false);
        int walls = 0;
        for (var w : VisionClient.WALLS.values()) if (w.processor == pos.asLong() && w.output == out) walls++;
        g.drawString(font, Component.translatable("gui.concertstage.lv.walls_on_output", walls), x + 220, top() + 31, Ui.MUTED, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
