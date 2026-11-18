package com.concertstage.client.light;

import com.concertstage.client.ui.Tr;
import com.concertstage.client.ui.Ui;
import com.concertstage.light.LumicoreBlockEntity;
import com.concertstage.light.analysis.AutoShow;
import com.concertstage.light.core.Attr;
import com.concertstage.light.core.Bytes;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.Engine;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Live;
import com.concertstage.light.core.Look;
import com.concertstage.light.core.Movement;
import com.concertstage.light.core.Show;
import com.concertstage.light.net.LightNet;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * CSC LUMICORE X1 — the desk screen. Work spaces (tabs) like a professional desk: Fixtures, Groups, Program (intensity, position, beam,
 * pixels), Color (wheel, RGB, CTO, palettes, saved colours, gradient), Position (pad, palettes, standard positions), Effects, Cues,
 * Playback (executors, grand master), Timeline (music, sections, events, preview), AutoShow, Shows (library, import / export,
 * operators) and Help (first-show tutorial, shortcuts). The safety strip at the bottom is always visible: BLACKOUT, STOP SHOW,
 * NO STROBE, MOVE LIMIT, HOME, STOP AUTO, grand master, undo / redo.
 *
 * <p>Everything the screen does is a command to the server (see {@code Commands}); it never computes the light itself. The fixture
 * selection is local to each player. The screen is drawn in immediate mode: each frame registers its clickable areas.
 */
public class LumicoreScreen extends Screen {
    enum Tab { FIXTURES, GROUPS, PROGRAM, COLOR, POSITION, EFFECTS, CUES, PLAYBACK, TIMELINE, AUTO, SHOWS, EXTERNAL, SETTINGS, HELP }

    private static final String[] TAB_NAMES = {"Fixtures", "Groups", "Program", "Color", "Position", "Effects", "Cues", "Playback", "Timeline",
        "AutoShow", "Shows", "External", "Settings", "Help"};
    private static final int ACCENT = 0xFFB07CFF, CYAN = Ui.CYAN, GREEN = Ui.GREEN, RED = Ui.RED, AMBER = Ui.AMBER;
    private static final Map<BlockPos, Set<Integer>> SELECTIONS = new HashMap<>();
    private static Tab lastTab = Tab.FIXTURES;
    private static ResourceLocation wheel;

    private final BlockPos pos;
    private Tab tab = lastTab;
    private int x0, y0, w, h;
    private final List<Hit> hits = new ArrayList<>();
    private Hit dragging;
    private final Map<String, Long> throttle = new HashMap<>();
    private EditBox nameBox, numberBox, searchBox;
    // rev. 16h: Settings > Network > Art-Net
    private EditBox ipBox, portBox, srcBox, mapBox;

    // per tab state
    private int selList = -1, selCue = -1, selGroup = -1, selPreset = -1, fixtureScroll, cueScroll;
    private int recordMask = 0;
    private int opticsPage = 0;
    private int fixtureTypePage = 0;
    private float recordFade = 2f;
    private final Show.Effect fx = new Show.Effect();
    private float colorH = 0f, colorS = 0f, colorV = 1f, white = 0f;
    /** Rev. 1.3.0-3: the White slider was moved in this screen (before, a colour sent white 0: blinders and white bars went dark). */
    private boolean whiteTouched;
    private int gradientA = 0xFF0000, gradientB = 0x0000FF;
    private long tlStart = 0, tlSpan = 120000;
    private int tlEvent = -1;
    private int padLastX, padLastY;
    private String importFile = "";
    private Component tooltip;

    private record Hit(int x, int y, int w, int h, IntConsumer click, Drag drag, IntConsumer scroll, Component tip) {
        boolean in(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private interface Drag {
        void at(double mx, double my, boolean release);
    }

    public LumicoreScreen(BlockPos pos) {
        super(Component.literal("CSC LUMICORE X1"));
        this.pos = pos.immutable();
        fx.type = Show.Effect.CIRCLE;
        fx.rate = 0.5f;
        fx.amplitude = 0.4f;
        fx.fade = 1f;
        LightClient.askSync(pos);
    }

    private LumicoreBlockEntity desk() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof LumicoreBlockEntity d ? d : null;
    }

    private Set<Integer> selection() {
        return SELECTIONS.computeIfAbsent(pos, k -> new LinkedHashSet<>());
    }

    private int[] sel() {
        LumicoreBlockEntity d = desk();
        return selection().stream().filter(f -> d != null && d.show.fixture(f) != null).mapToInt(Integer::intValue).toArray();
    }

    private void cmd(byte[] b) {
        LightNet.command(pos, b);
    }

    private void action(int a, java.util.function.Consumer<Bytes.Writer> args) {
        Bytes.Writer w = LightNet.action(pos, a);
        if (args != null) args.accept(w);
        LightNet.toServer(w.toBytes());
    }

    private boolean throttled(String key, boolean force) {
        long now = System.currentTimeMillis();
        Long last = throttle.get(key);
        if (!force && last != null && now - last < 60) return true;
        throttle.put(key, now);
        return false;
    }

    private static String t(String s) {
        return Tr.t(s);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        lastTab = tab;
        if (LightClient.previewDesk != null && LightClient.previewDesk.equals(pos)) { LightClient.previewDesk = null; LightClient.previewMs = -1; }
    }

    @Override
    public void tick() {
        if (desk() == null) onClose();
        if (nameBox != null) nameBox.tick();
        if (numberBox != null) numberBox.tick();
        if (searchBox != null) searchBox.tick();
        for (EditBox b : new EditBox[] {ipBox, portBox, srcBox, mapBox}) if (b != null) b.tick();
        // rev. 16h: the Settings tab asks the server for the network status (interfaces, monitors) every 2 s while it is open
        if (tab == Tab.SETTINGS && ++netTicks % 40 == 0) {
            ext(com.concertstage.light.ExternalControl.X_NET_HELLO, null);
            if (netPage == 2) ext(com.concertstage.light.ExternalControl.X_DMX_MON, w2 -> w2.varInt(monUniverse));
        }
    }

    // ------------------------------------------------------------ layout and widgets
    @Override
    protected void init() {
        w = Math.min(width - 8, 620);
        h = Math.min(height - 8, 340);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        nameBox = numberBox = searchBox = null;
        ipBox = portBox = srcBox = mapBox = null;
        int bx = x0 + w - 170, by = y0 + 30;
        switch (tab) {
            case FIXTURES -> {
                searchBox = box(x0 + 8, y0 + 46, 150, "Search…");
                nameBox = box(bx, by + 132, 160, "New name");
            }
            case GROUPS -> nameBox = box(bx, by + 16, 160, "Group name");
            case COLOR, POSITION -> nameBox = box(bx, by + 184, 160, "Preset name");
            case CUES -> {
                nameBox = box(bx, by + 140, 160, "Cue name");
                numberBox = box(bx, by + 172, 70, "Cue no.");
            }
            case SHOWS -> {
                nameBox = box(x0 + 8, y0 + 46, 160, "Show name");
                numberBox = box(showsAccessX(), y0 + h - 146, 110, "Player");
            }
            case TIMELINE -> nameBox = box(x0 + w - 178, y0 + h - 88, 110, "Marker");
            case SETTINGS -> {
                ext(com.concertstage.light.ExternalControl.X_NET_HELLO, null);
                if (netPage == 0) {
                    int ax = x0 + 104, ay = y0 + 46;
                    ipBox = netBox(ax + 62, ay + 29, 136, "0.0.0.0");
                    portBox = netBox(ax + 62, ay + 125, 56, "6454");
                    srcBox = netBox(ax + 102, ay + 141, 96, "192.168.1.100");
                    mapBox = netBox(x0 + 312 + 64, ay + 174, 44, "16");
                    if (draft != null) fillBoxes();
                }
            }
            case EXTERNAL -> {
                ext(com.concertstage.light.ExternalControl.X_HELLO, null);
                nameBox = box(x0 + 416, y0 + 46 + 150, 120, "Recording name");
                numberBox = box(x0 + 416, y0 + 46 + 104, 120, "File in import/");
            }
            default -> { }
        }
    }

    private EditBox box(int x, int y, int bw, String hint) {
        EditBox b = new EditBox(font, x, y, bw, 14, Component.literal(t(hint)));
        b.setMaxLength(Show.MAX_NAME);
        b.setHint(Component.literal(t(hint)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        addRenderableWidget(b);
        return b;
    }

    private void switchTab(Tab t) {
        if (tab == t) return;
        tab = t;
        lastTab = t;
        rebuildWidgets();
    }

    // ------------------------------------------------------------ immediate mode helpers
    private void hit(int x, int y, int bw, int bh, IntConsumer click, Drag drag, IntConsumer scroll, String tip) {
        hits.add(new Hit(x, y, bw, bh, click, drag, scroll, tip == null ? null : Component.literal(t(tip))));
    }

    private boolean button(GuiGraphics g, int x, int y, int bw, int bh, String label, int color, boolean selected, Runnable action) {
        return button(g, x, y, bw, bh, label, color, selected, action, null);
    }

    private boolean button(GuiGraphics g, int x, int y, int bw, int bh, String label, int color, boolean selected, Runnable action, String tip) {
        int mx = mouseX, my = mouseY;
        boolean hover = mx >= x && my >= y && mx < x + bw && my < y + bh;
        int base = selected ? Ui.mix(0xFF1C1F24, color, hover ? 0.6f : 0.48f) : hover ? 0xFF2E333B : 0xFF22262C;
        g.fill(x, y, x + bw, y + bh, base);
        Ui.frame(g, x, y, bw, bh, selected || hover ? color : Ui.LINE);
        Ui.centeredFit(g, Component.literal(label), x + bw / 2, y + (bh - 8) / 2, bw - 4, Ui.TEXT);
        hit(x, y, bw, bh, b -> { if (b == 0 && action != null) action.run(); }, null, null, tip);
        return hover;
    }

    /** Horizontal slider 0..1 (NaN = not set: drawn hollow). Left drag sets, right click releases (NaN). */
    private void slider(GuiGraphics g, int x, int y, int sw, String label, float value, int color, DoubleConsumer live, DoubleConsumer release) {
        g.drawString(font, t(label), x, y, Ui.MUTED, false);
        String v = Float.isNaN(value) ? "—" : Math.round(value * 100) + "%";
        g.drawString(font, v, x + sw - font.width(v), y, Ui.TEXT, false);
        int ty = y + 11;
        g.fill(x, ty, x + sw, ty + 5, 0xFF0D0F12);
        if (!Float.isNaN(value)) g.fill(x, ty, x + Math.round(sw * value), ty + 5, color);
        Ui.frame(g, x, ty - 1, sw, 7, Ui.LINE);
        hits.add(new Hit(x, y, sw, 18, b -> { if (b == 1 && release != null) release.accept(Double.NaN); },
            (mx, my, rel) -> {
                double val = Math.max(0, Math.min(1, (mx - x) / (double) sw));
                if (rel) { if (release != null) release.accept(val); } else live.accept(val);
            }, s -> {
                double val = Math.max(0, Math.min(1, (Float.isNaN(value) ? 0.5 : value) + s * 0.01));
                if (release != null) release.accept(val);
            }, null));
    }

    /** Vertical fader (playback, grand master). */
    private void fader(GuiGraphics g, int x, int y, int fw, int fh, float value, int color, DoubleConsumer live, DoubleConsumer release) {
        g.fill(x + fw / 2 - 2, y, x + fw / 2 + 2, y + fh, 0xFF0D0F12);
        int fy = y + Math.round((1 - value) * (fh - 8));
        g.fill(x + fw / 2 - 2, fy + 4, x + fw / 2 + 2, y + fh, color);
        g.fill(x, fy, x + fw, fy + 8, 0xFFCDD1D6);
        g.fill(x, fy + 3, x + fw, fy + 5, 0xFF2A2E35);
        hits.add(new Hit(x, y, fw, fh, null, (mx, my, rel) -> {
            double val = Math.max(0, Math.min(1, 1 - (my - y - 4) / (double) (fh - 8)));
            if (rel) release.accept(val); else live.accept(val);
        }, s -> release.accept(Math.max(0, Math.min(1, value + s * 0.02))), null));
    }

    private void label(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, t(s), x, y, color, false);
    }

    private void raw(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    private int mouseX, mouseY;

    // ------------------------------------------------------------ rendering
    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        mouseX = mx;
        mouseY = my;
        tooltip = null;
        hits.clear();
        renderBackground(g);
        LumicoreBlockEntity d = desk();
        if (d == null) return;
        Show s = d.show;
        Live l = d.live;
        Ui.panel(g, x0, y0, w, h, ACCENT, Component.literal("CSC LUMICORE X1  ·  " + s.name), Component.literal(subtitle(d)));
        // tabs
        int tx = x0 + 6, ty = y0 + 30;
        int tw = (w - 12) / TAB_NAMES.length;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final Tab t = Tab.values()[i];
            button(g, tx + i * tw, ty, tw - 2, 13, t(TAB_NAMES[i]), ACCENT, tab == t, () -> switchTab(t));
        }
        int cy = y0 + 46, ch = h - 46 - 34;
        switch (tab) {
            case FIXTURES -> fixtures(g, d, cy, ch);
            case GROUPS -> groups(g, d, cy);
            case PROGRAM -> program(g, d, cy);
            case COLOR -> color(g, d, cy);
            case POSITION -> position(g, d, cy);
            case EFFECTS -> effects(g, d, cy);
            case CUES -> cues(g, d, cy, ch);
            case PLAYBACK -> playback(g, d, cy, ch);
            case TIMELINE -> timeline(g, d, cy, ch, partial);
            case AUTO -> auto(g, d, cy);
            case SHOWS -> shows(g, d, cy);
            case EXTERNAL -> external(g, d, cy, ch);
            case SETTINGS -> settings(g, d, cy, ch);
            case HELP -> help(g, d, cy);
        }
        safety(g, d, y0 + h - 30);
        super.render(g, mx, my, partial);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hh = hits.get(i);
            if (hh.tip() != null && hh.in(mx, my)) { g.renderTooltip(font, font.split(hh.tip(), 240), mx, my); break; }
        }
        if (!LightClient.lastMessage.isEmpty() && System.currentTimeMillis() - LightClient.lastMessageTime < 6000) {
            int mw = font.width(LightClient.lastMessage) + 8;
            g.fill(x0 + w - mw - 6, y0 + 4, x0 + w - 6, y0 + 16, 0xE0202328);
            raw(g, LightClient.lastMessage, x0 + w - mw - 2, y0 + 6, AMBER);
        }
    }

    private String subtitle(LumicoreBlockEntity d) {
        int[] s = sel();
        String audio = d.live.hasAudio ? t("audio linked") : t("no audio");
        return Tr.f("%d fixtures · %d selected · %s", d.show.patch.size(), s.length, audio) + (d.status.isEmpty() ? "" : " · " + d.status);
    }

    // ------------------------------------------------------------ safety strip
    private void safety(GuiGraphics g, LumicoreBlockEntity d, int y) {
        Live l = d.live;
        int x = x0 + 6;
        g.fill(x0 + 1, y - 3, x0 + w - 1, y0 + h - 1, 0xFF15171B);
        button(g, x, y, 70, 22, "BLACKOUT", RED, l.blackout, () -> cmd(Commands.op(Commands.BLACKOUT).bool(!desk().live.blackout).toBytes()), "Everything dark at once (B)");
        button(g, x + 72, y, 66, 22, t("STOP SHOW"), RED, false, () -> cmd(Commands.op(Commands.STOP_ALL).toBytes()), "Stops every playback and the timeline");
        button(g, x + 140, y, 64, 22, t("NO STROBE"), AMBER, l.noStrobe, () -> cmd(Commands.op(Commands.NO_STROBE).bool(!desk().live.noStrobe).toBytes()), "Strobes held open (no flashing)");
        button(g, x + 206, y, 64, 22, t("MOVE LIMIT"), AMBER, l.moveLimit, () -> cmd(Commands.op(Commands.MOVE_LIMIT).bool(!desk().live.moveLimit).toBytes()), "Small, slow movements only");
        button(g, x + 272, y, 44, 22, t("HOME"), CYAN, false, () -> cmd(Commands.op(Commands.HOME_ALL).toBytes()), "Every fixture back to its rest position, lamps at zero");
        button(g, x + 318, y, 60, 22, t("STOP AUTO"), RED, !l.autoEnabled, () -> {
            boolean en = desk().live.autoEnabled;
            cmd(Commands.op(Commands.AUTO_ENABLE).bool(!en).toBytes());
            if (en) action(LightNet.A_LIVE_STOP, null);
        }, "Stops the AutoShow at once (click again to allow it)");
        int gx = x + 382;
        label(g, "GM", gx, y + 1, Ui.MUTED);
        int gmw = 70;
        g.fill(gx, y + 12, gx + gmw, y + 18, 0xFF0D0F12);
        g.fill(gx, y + 12, gx + Math.round(gmw * l.grandMaster), y + 18, ACCENT);
        raw(g, Math.round(l.grandMaster * 100) + "%", gx + 22, y + 1, Ui.TEXT);
        hits.add(new Hit(gx, y, gmw, 22, null, (mx, my, rel) -> {
            float v = (float) Math.max(0, Math.min(1, (mx - gx) / gmw));
            if (rel || !throttled("gm", false)) cmd(Commands.op(Commands.GRAND_MASTER).unit(v).toBytes());
        }, s -> cmd(Commands.op(Commands.GRAND_MASTER).unit(Math.max(0, Math.min(1, desk().live.grandMaster + s * 0.05f))).toBytes()), Component.literal(t("Grand Master"))));
        button(g, x0 + w - 98, y, 44, 22, t("Undo"), Ui.BLUE, false, () -> action(LightNet.A_UNDO, null), "Ctrl+Z");
        button(g, x0 + w - 52, y, 44, 22, t("Redo"), Ui.BLUE, false, () -> action(LightNet.A_REDO, null), "Ctrl+Y");
    }

    // ------------------------------------------------------------ fixtures
    private void fixtures(GuiGraphics g, LumicoreBlockEntity d, int y, int ch) {
        Show s = d.show;
        int x = x0 + 8;
        String q = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        List<Show.Patch> list = new ArrayList<>();
        for (Show.Patch p : s.patch)
            if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q) || p.profile().name.toLowerCase(Locale.ROOT).contains(q) || String.valueOf(p.fid).equals(q)) list.add(p);
        int listY = y + 18, rows = (ch - 22) / 12;
        Ui.section(g, x + 160, y + 3, w - 350, Component.literal(t("Patch") + "  (" + list.size() + ")"));
        raw(g, "§8" + t("No.   Name                 Type              U.Addr   Mount"), x, listY, Ui.MUTED);
        fixtureScroll = Math.max(0, Math.min(fixtureScroll, Math.max(0, list.size() - rows + 1)));
        Set<Integer> selSet = selection();
        Map<Integer, Engine.Output> outs = Minecraft.getInstance().level == null ? Map.of() : LightClient.outputs(Minecraft.getInstance().level, d, 0f);
        for (int i = 0; i < rows - 1 && i + fixtureScroll < list.size(); i++) {
            Show.Patch p = list.get(i + fixtureScroll);
            int ry = listY + 12 + i * 12;
            boolean on = selSet.contains(p.fid);
            if (on) g.fill(x - 2, ry - 2, x + w - 190, ry + 10, 0x60B07CFF);
            Engine.Output o = outs.get(p.fid);
            int dot = o == null ? 0xFF333333 : 0xFF000000 | (int) (o.beamR * 255 * Math.max(0.2f, o.get(Attr.DIMMER))) << 16
                | (int) (o.beamG * 255 * Math.max(0.2f, o.get(Attr.DIMMER))) << 8 | (int) (o.beamB * 255 * Math.max(0.2f, o.get(Attr.DIMMER)));
            g.fill(x + w - 200, ry, x + w - 194, ry + 6, dot);
            raw(g, String.format(Locale.ROOT, "%-5d %-20s %-17s %2d.%03d   %s", p.fid, clip(p.name, 20), p.profile().name.replace("CSC ", ""),
                p.universe, p.address, p.mount == 1 ? t("hung") : t("standing")), x, ry, on ? Ui.TEXT : Ui.MUTED);
            final int fid = p.fid;
            hits.add(new Hit(x - 2, ry - 2, w - 190, 12, b -> {
                boolean shift = hasShiftDown(), ctrl = hasControlDown();
                if (!shift && !ctrl) { selSet.clear(); selSet.add(fid); }
                else if (selSet.contains(fid)) selSet.remove(fid); else selSet.add(fid);
            }, null, sc -> fixtureScroll = Math.max(0, fixtureScroll - sc), null));
        }
        hits.add(new Hit(x - 2, listY, w - 190, ch - 22, null, null, sc -> fixtureScroll = Math.max(0, fixtureScroll - sc), null));
        // right column
        int bx = x0 + w - 170, by = y0 + 30 + 16;
        button(g, bx, by, 78, 16, t("All"), ACCENT, false, () -> { selSet.clear(); for (Show.Patch p : desk().show.patch) selSet.add(p.fid); });
        button(g, bx + 82, by, 78, 16, t("None"), ACCENT, false, selSet::clear);
        button(g, bx, by + 18, 60, 16, t("Odd"), ACCENT, false, () -> { List<Integer> c = new ArrayList<>(selSet); selSet.clear(); for (int i = 0; i < c.size(); i += 2) selSet.add(c.get(i)); });
        button(g, bx + 62, by + 18, 60, 16, t("Even"), ACCENT, false, () -> { List<Integer> c = new ArrayList<>(selSet); selSet.clear(); for (int i = 1; i < c.size(); i += 2) selSet.add(c.get(i)); });
        int typePages = (FixtureProfile.all().size() + 3) / 4;
        button(g, bx + 124, by + 18, 16, 16, "‹", CYAN, false,
            () -> fixtureTypePage = (fixtureTypePage + typePages - 1) % typePages, "Previous fixture types");
        button(g, bx + 142, by + 18, 18, 16, "›", CYAN, false,
            () -> fixtureTypePage = (fixtureTypePage + 1) % typePages, "Next fixture types");
        int k = 0;
        for (FixtureProfile pr : FixtureProfile.all().stream().skip(fixtureTypePage * 4L).limit(4).toList()) {
            final String type = pr.id;
            button(g, bx + (k % 2) * 82, by + 38 + (k / 2) * 18, 78, 16, pr.name.replace("CSC ", ""), CYAN, false, () -> {
                selSet.clear();
                for (Show.Patch p : desk().show.patch) if (p.type.equals(type)) selSet.add(p.fid);
            }, "Select every fixture of this type");
            k++;
        }
        button(g, bx, by + 78, 160, 16, t("Auto-patch (48 blocks)"), GREEN, false, () -> action(LightNet.A_AUTOPATCH, w2 -> w2.varInt(48)),
            "Patches every CSC fixture around the desk that has no desk yet");
        button(g, bx, by + 96, 78, 16, t("Highlight"), AMBER, false, () -> {
            Bytes.Writer w2 = Commands.op(Commands.P_RGB);
            Commands.fids(w2, sel());
            cmd(w2.unit(1).unit(1).unit(1).unit(1).toBytes());
            Bytes.Writer w3 = Commands.op(Commands.P_SET);
            Commands.fids(w3, sel());
            cmd(w3.varInt(Attr.DIMMER.ordinal()).unit(1).toBytes());
        }, "Full white on the selection, to find them on stage");
        button(g, bx + 82, by + 96, 78, 16, t("Unpatch"), RED, false, () -> { for (int f : sel()) cmd(Commands.op(Commands.PATCH_REMOVE).varInt(f).toBytes()); });
        button(g, bx, by + 132 + 18, 78, 16, t("Rename"), ACCENT, false, () -> {
            int[] f = sel();
            String n = nameBox.getValue().strip();
            if (n.isEmpty()) return;
            for (int i = 0; i < f.length; i++) {
                Show.Patch p = desk().show.fixture(f[i]);
                cmd(Commands.op(Commands.PATCH_EDIT).varInt(f[i]).str(f.length == 1 ? n : n + " " + (i + 1)).varInt(p.universe).varInt(p.address).toBytes());
            }
        });
        button(g, bx + 82, by + 132 + 18, 78, 16, t("Clean up"), ACCENT, false, () -> action(LightNet.A_UNPATCH_MISSING, null),
            "Unpatches the fixtures that are no longer in the world");
        button(g, bx, by + 172, 160, 16, d.live.hasAudio ? t("Audio console linked ✓") : t("Link nearest audio console"), GREEN, d.live.hasAudio,
            () -> action(LightNet.A_LINK_AUDIO, null), "The CSC Relay that plays the music the lights follow");
        if (selSet.size() == 1) {
            Show.Patch p = s.fixture(selSet.iterator().next());
            if (p != null) {
                int ay = by + 194;
                raw(g, t("Universe") + " " + p.universe + "  ·  " + t("Address") + " " + p.address, bx, ay, Ui.MUTED);
                button(g, bx, ay + 12, 38, 14, "U-", ACCENT, false, () -> cmd(Commands.op(Commands.PATCH_EDIT).varInt(p.fid).str("").varInt(Math.max(0, p.universe - 1)).varInt(p.address).toBytes()));
                button(g, bx + 40, ay + 12, 38, 14, "U+", ACCENT, false, () -> cmd(Commands.op(Commands.PATCH_EDIT).varInt(p.fid).str("").varInt(Math.min(com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE, p.universe + 1)).varInt(p.address).toBytes()));
                button(g, bx + 82, ay + 12, 38, 14, "A-", ACCENT, false, () -> cmd(Commands.op(Commands.PATCH_EDIT).varInt(p.fid).str("").varInt(p.universe).varInt(Math.max(1, p.address - 1)).toBytes()));
                button(g, bx + 122, ay + 12, 38, 14, "A+", ACCENT, false, () -> cmd(Commands.op(Commands.PATCH_EDIT).varInt(p.fid).str("").varInt(p.universe).varInt(Math.min(512, p.address + 1)).toBytes()));
                raw(g, "§8" + p.profile().function + " · " + p.x + " " + p.y + " " + p.z, bx, ay + 30, Ui.MUTED);
            }
        }
    }

    // ------------------------------------------------------------ groups
    private static final String[] GROUP_NAMES = {"Front Wash", "Back Wash", "Beams", "Strobes", "Stage Left", "Stage Right", "Audience"};

    private void groups(GuiGraphics g, LumicoreBlockEntity d, int y) {
        Show s = d.show;
        int x = x0 + 8, cols = 4, gw = (w - 200) / cols;
        Ui.section(g, x, y + 3, w - 200, Component.literal(t("Groups") + " (" + s.groups.size() + ")  ·  " + t("click = select, Shift = add")));
        for (int i = 0; i < s.groups.size(); i++) {
            Show.Group gr = s.groups.get(i);
            int gx = x + (i % cols) * gw, gy = y + 16 + (i / cols) * 26;
            final int id = gr.id;
            boolean selected = selGroup == id;
            g.fill(gx, gy, gx + gw - 4, gy + 24, selected ? Ui.mix(0xFF1C1F24, gr.color, 0.45f) : 0xFF22262C);
            g.fill(gx, gy, gx + 3, gy + 24, gr.color);
            raw(g, clip(gr.name, 16), gx + 6, gy + 3, Ui.TEXT);
            raw(g, "§8" + gr.fids.length + " " + t("fixtures") + "  " + (i < 9 ? "[" + (i + 1) + "]" : ""), gx + 6, gy + 13, Ui.MUTED);
            hits.add(new Hit(gx, gy, gw - 4, 24, b -> {
                selGroup = id;
                Show.Group gg = desk().show.group(id);
                if (gg == null) return;
                if (!hasShiftDown()) selection().clear();
                for (int f : gg.fids) selection().add(f);
            }, null, null, null));
        }
        int bx = x0 + w - 170, by = y0 + 30 + 16;
        raw(g, t("Store the selection as:"), bx, by + 34, Ui.MUTED);
        button(g, bx, by + 46, 160, 16, t("Store (name above)"), GREEN, false, () -> storeGroup(nameBox.getValue()));
        for (int i = 0; i < GROUP_NAMES.length; i++) {
            final String n = GROUP_NAMES[i];
            button(g, bx + (i % 2) * 82, by + 66 + (i / 2) * 18, 78, 16, t(n), CYAN, false, () -> storeGroup(t(n)));
        }
        if (selGroup >= 0 && s.group(selGroup) != null) {
            int ay = by + 66 + 4 * 18 + 6;
            raw(g, t("Selected group:") + " " + s.group(selGroup).name, bx, ay, Ui.TEXT);
            button(g, bx, ay + 12, 78, 16, t("Update"), ACCENT, false, () -> {
                Bytes.Writer w2 = Commands.op(Commands.GROUP_SET).varInt(selGroup);
                Commands.fids(w2, sel());
                cmd(w2.toBytes());
            }, "Replace the fixtures of this group by the selection");
            button(g, bx + 82, ay + 12, 78, 16, t("Rename"), ACCENT, false, () -> { if (!nameBox.getValue().isBlank()) cmd(Commands.op(Commands.GROUP_RENAME).varInt(selGroup).str(nameBox.getValue()).toBytes()); });
            button(g, bx, ay + 30, 160, 16, t("Delete group"), RED, false, () -> { cmd(Commands.op(Commands.GROUP_DELETE).varInt(selGroup).toBytes()); selGroup = -1; });
        }
    }

    private void storeGroup(String name) {
        int[] f = sel();
        if (f.length == 0) { LightClient.lastMessage = t("Select fixtures first"); LightClient.lastMessageTime = System.currentTimeMillis(); return; }
        int[] colors = {0xFFB07CFF, 0xFF35C9E8, 0xFFF2B33D, 0xFF4CD08A, 0xFFE5484D, 0xFF5B8CFF};
        Bytes.Writer w2 = Commands.op(Commands.GROUP_STORE).str(name == null ? "" : name);
        Commands.fids(w2, f);
        cmd(w2.i32(colors[desk().show.groups.size() % colors.length]).toBytes());
    }

    // ------------------------------------------------------------ program
    /** Programmer value of the selection (the first selected fixture that has it), or NaN. */
    private float progValue(LumicoreBlockEntity d, Attr a) {
        for (int f : sel()) {
            Look l = d.live.programmer.get(f);
            if (l != null && l.has(a)) return l.get(a);
        }
        return Float.NaN;
    }

    private void setAttr(Attr a, double v, boolean force) {
        if (throttled("attr" + a.ordinal(), force)) return;
        Bytes.Writer w2 = Commands.op(Commands.P_SET);
        Commands.fids(w2, sel());
        cmd(w2.varInt(a.ordinal()).unit((float) v).toBytes());
    }

    private Set<Attr> available(LumicoreBlockEntity d) {
        Set<Attr> out = new LinkedHashSet<>();
        for (int f : sel()) out.addAll(d.show.fixture(f).profile().attrs);
        return out;
    }

    private void attrSlider(GuiGraphics g, LumicoreBlockEntity d, Attr a, int x, int y, int sw, int color) {
        slider(g, x, y, sw, a.label, progValue(d, a), color, v -> setAttr(a, v, false), v -> setAttr(a, v, true));
    }

    private void program(GuiGraphics g, LumicoreBlockEntity d, int y) {
        int[] f = sel();
        int x = x0 + 8;
        if (f.length == 0) { label(g, "Select fixtures (Fixtures or Groups tab) to program them.", x, y + 8, Ui.MUTED); return; }
        Set<Attr> av = available(d);
        int col = (w - 24) / 3;
        int cx = x;
        Ui.section(g, cx, y + 3, col - 8, Component.literal(t("Intensity")));
        int yy = y + 16;
        for (Attr a : new Attr[] {Attr.DIMMER, Attr.STROBE}) if (av.contains(a)) { attrSlider(g, d, a, cx, yy, col - 12, AMBER); yy += 22; }
        button(g, cx, yy, 40, 14, "0%", AMBER, false, () -> setAttr(Attr.DIMMER, 0, true));
        button(g, cx + 42, yy, 40, 14, "50%", AMBER, false, () -> setAttr(Attr.DIMMER, 0.5, true));
        button(g, cx + 84, yy, 40, 14, "Full", AMBER, false, () -> setAttr(Attr.DIMMER, 1, true));
        yy += 22;
        Ui.section(g, cx, yy, col - 8, Component.literal(t("Position")));
        yy += 13;
        for (Attr a : new Attr[] {Attr.PAN, Attr.TILT}) if (av.contains(a)) { attrSlider(g, d, a, cx, yy, col - 12, CYAN); yy += 22; }
        button(g, cx, yy, col - 12, 14, t("Home position"), CYAN, false, () -> { Bytes.Writer w2 = Commands.op(Commands.P_HOME); Commands.fids(w2, sel()); cmd(w2.toBytes()); });
        // beam
        cx = x + col;
        Ui.section(g, cx, y + 3, col - 8, Component.literal(t("Beam")));
        List<Attr> extraOptics = new ArrayList<>(av.stream().filter(a -> a.ordinal() >= Attr.CYAN.ordinal() && !a.zoneAttr()).toList());
        // rev. 21: the light zones of the PULSESTRIKE M / FLASHCORE FX on their own pages: intensities and strobes, then bar colours
        if (av.contains(Attr.TOP_DIM)) {
            while (extraOptics.size() % 6 != 0) extraOptics.add(null);
            java.util.Collections.addAll(extraOptics, Attr.TOP_DIM, Attr.TOP_STROBE, Attr.MID_DIM, Attr.MID_STROBE, Attr.BOT_DIM, Attr.BOT_STROBE,
                Attr.TOP_RED, Attr.TOP_GREEN, Attr.TOP_BLUE, Attr.BOT_RED, Attr.BOT_GREEN, Attr.BOT_BLUE);
        }
        int opticPages = 1 + (extraOptics.size() + 5) / 6;
        opticsPage = Math.min(opticsPage, opticPages - 1);
        if (opticPages > 1) button(g, cx + col - 90, y, 78, 14, (av.contains(Attr.TOP_DIM) ? t("Zones") + " / " : "") + "Optics " + (opticsPage + 1) + "/" + opticPages,
            Ui.BLUE, false, () -> opticsPage = (opticsPage + 1) % opticPages);
        yy = y + 16;
        if (opticsPage > 0) {
            int start = (opticsPage - 1) * 6;
            for (int i = start; i < Math.min(start + 6, extraOptics.size()); i++) {
                Attr ea = extraOptics.get(i);
                if (ea == null) continue;
                attrSlider(g, d, ea, cx, yy, col - 12, ea.zoneAttr() ? AMBER : Ui.BLUE); yy += 22;
            }
        } else {
        for (Attr a : new Attr[] {Attr.ZOOM, Attr.FOCUS, Attr.IRIS, Attr.GOBO_ROT, Attr.PRISM_ROT}) if (av.contains(a)) { attrSlider(g, d, a, cx, yy, col - 12, Ui.BLUE); yy += 22; }
        if (av.contains(Attr.GOBO)) {
            label(g, "Gobo", cx, yy, Ui.MUTED);
            yy += 10;
            int gobos = 1;
            // rev. 22: the name of each slot when the selected fixtures share one wheel (each gobo fixture has its own, GoboWheels)
            com.concertstage.light.core.GoboWheels.Wheel wheel = null;
            boolean oneWheel = true;
            for (int selected : f) {
                var sp = d.show.fixture(selected).profile();
                gobos = Math.max(gobos, sp.gobos);
                var w = com.concertstage.light.core.GoboWheels.first(sp);
                if (w == null) continue;
                if (wheel == null) wheel = w; else if (wheel != w) oneWheel = false;
            }
            final com.concertstage.light.core.GoboWheels.Wheel names = oneWheel ? wheel : null;
            for (int i = 0; i <= gobos; i++) {
                final float v = i / (float) gobos;
                final int slotNo = i;
                button(g, cx + i * 19, yy, 18, 14, i == 0 ? "○" : String.valueOf(i), Ui.BLUE, Math.abs(progValue(d, Attr.GOBO) - v) < 0.01f, () -> setAttr(Attr.GOBO, v, true),
                    names == null ? null : t("Gobo") + " " + slotNo + " : " + t(names.name(slotNo)));
            }
            yy += 18;
        }
        if (av.contains(Attr.PRISM)) {
            boolean in = progValue(d, Attr.PRISM) > 0.5f;
            button(g, cx, yy, 70, 14, t("Prism") + " " + (in ? "IN" : "OUT"), Ui.BLUE, in, () -> setAttr(Attr.PRISM, in ? 0 : 1, true));
            yy += 18;
        }
        if (av.contains(Attr.IRIS)) {
            button(g, cx, yy, 60, 14, "Beam", Ui.BLUE, false, () -> { setAttr(Attr.ZOOM, 0, true); setAttr(Attr.IRIS, 0.2, true); setAttr(Attr.FOCUS, 0.5, true); }, "Tight shaft: zoom closed, iris small");
            button(g, cx + 62, yy, 60, 14, "Spot", Ui.BLUE, false, () -> { setAttr(Attr.ZOOM, 0.45, true); setAttr(Attr.IRIS, 1, true); setAttr(Attr.FOCUS, 0.6, true); }, "Wider spot, iris open, for gobos");
        }
        }
        // pixels
        cx = x + 2 * col;
        Ui.section(g, cx, y + 3, col - 8, Component.literal(t("Pixels / LED zones")));
        Show.Patch first = null;
        for (int fid : f) { Show.Patch p = d.show.fixture(fid); if (p.profile().zones > 0) { first = p; break; } }
        if (first == null) { label(g, "No LED zones on the selection.", cx, y + 16, Ui.MUTED); }
        else {
            FixtureProfile pr = first.profile();
            Look l = d.live.programmer.get(first.fid);
            int per = pr.zones > 30 ? 10 : 7, sz = 13;
            for (int z = 0; z < pr.zones; z++) {
                int zx = cx + (z % per) * (sz + 2), zy = y + 16 + (z / per) * (sz + 2);
                float lv = l != null && l.zones != null && z * 4 + 3 < l.zones.length && !Float.isNaN(l.zones[z * 4 + 3]) ? l.zones[z * 4 + 3] : -1f;
                int c = pr.zoneKind(z) == 1 ? 0xFFF4E6C8 : 0xFF6CA0FF;
                g.fill(zx, zy, zx + sz, zy + sz, lv < 0 ? 0xFF2A2E35 : Ui.mix(0xFF101214, c, Math.max(0.15f, lv)));
                final int zz = z;
                final Show.Patch fp = first;
                final float cur = lv;
                hits.add(new Hit(zx, zy, sz, sz, b -> {
                    float nl = b == 1 ? Float.NaN : cur >= 0.5f ? 0f : 1f;
                    for (int fid : sel()) {
                        Show.Patch p = desk().show.fixture(fid);
                        if (p.profile().zones <= zz) continue;
                        cmd(Commands.op(Commands.P_ZONE).varInt(fid).varInt(zz).unit(Float.NaN).unit(Float.NaN).unit(Float.NaN).unit(nl).toBytes());
                    }
                }, null, null, Component.literal(t("Click: zone on / off · right click: release"))));
            }
            int py = y + 16 + ((pr.zones + per - 1) / per) * (sz + 2) + 4;
            String[] modes = {"All", "Odd/Even", "Gradient", "Clear"};
            for (int i = 0; i < modes.length; i++) {
                final int mode = i;
                button(g, cx + (i % 2) * 82, py + (i / 2) * 18, 78, 16, t(modes[i]), Ui.BLUE, false, () -> {
                    float[] rgb = hsv(colorH, colorS, colorV);
                    Bytes.Writer w2 = Commands.op(Commands.P_ZONES_FILL);
                    Commands.fids(w2, sel());
                    cmd(w2.varInt(mode).unit(rgb[0]).unit(rgb[1]).unit(rgb[2]).toBytes());
                }, "Pattern on the zones of the selection, in the colour of the Color tab");
            }
        }
        // clear
        int by = y0 + h - 56;
        button(g, x, by, 120, 16, t("Clear selection values"), RED, false, () -> { Bytes.Writer w2 = Commands.op(Commands.P_CLEAR); Commands.fids(w2, sel()); cmd(w2.bool(false).toBytes()); });
        button(g, x + 124, by, 120, 16, t("Clear programmer"), RED, false, () -> { Bytes.Writer w2 = Commands.op(Commands.P_CLEAR); Commands.fids(w2, new int[0]); cmd(w2.bool(true).toBytes()); }, "Delete key");
    }

    // ------------------------------------------------------------ color
    private void ensureWheel() {
        if (wheel != null) return;
        int n = 96;
        NativeImage img = new NativeImage(n, n, true);
        for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) {
            double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1, r = Math.sqrt(dx * dx + dy * dy);
            if (r > 1) { img.setPixelRGBA(x, y, 0); continue; }
            float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            float[] c = hsv(hue, (float) r, 1f);
            int abgr = 0xFF000000 | (int) (c[2] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[0] * 255);
            img.setPixelRGBA(x, y, abgr);
        }
        wheel = Minecraft.getInstance().getTextureManager().register("concertstage_color_wheel", new DynamicTexture(img));
    }

    static float[] hsv(float h, float s, float v) {
        int c = java.awt.Color.HSBtoRGB(h, s, v);
        return new float[] {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    private void sendColor(boolean force, float r, float gg, float b) {
        if (throttled("color", force)) return;
        Bytes.Writer w2 = Commands.op(Commands.P_RGB);
        Commands.fids(w2, sel());
        cmd(w2.unit(r).unit(gg).unit(b).unit(whiteTouched ? white : Float.NaN).toBytes());
    }

    private void color(GuiGraphics g, LumicoreBlockEntity d, int y) {
        int x = x0 + 8;
        ensureWheel();
        int n = 96;
        g.blit(wheel, x, y + 6, 0, 0, n, n, n, n);
        double ang = colorH * 2 * Math.PI;
        int px = x + n / 2 + (int) (Math.cos(ang) * colorS * n / 2), py = y + 6 + n / 2 + (int) (Math.sin(ang) * colorS * n / 2);
        g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
        hits.add(new Hit(x, y + 6, n, n, null, (mx, my, rel) -> {
            double dx = (mx - x) / (double) n * 2 - 1, dy = (my - y - 6) / (double) n * 2 - 1;
            colorH = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            colorS = (float) Math.min(1, Math.sqrt(dx * dx + dy * dy));
            float[] c = hsv(colorH, colorS, colorV);
            sendColor(rel, c[0], c[1], c[2]);
        }, null, Component.literal(t("Colour wheel: drag to choose hue and saturation"))));
        float[] rgb = hsv(colorH, colorS, colorV);
        g.fill(x, y + 108, x + n, y + 124, 0xFF000000 | (int) (rgb[0] * 255) << 16 | (int) (rgb[1] * 255) << 8 | (int) (rgb[2] * 255));
        // sliders
        int sx = x + n + 12, sw = 150;
        slider(g, sx, y + 6, sw, "Brightness", colorV, Ui.TEXT, v -> { colorV = (float) v; float[] c = hsv(colorH, colorS, colorV); sendColor(false, c[0], c[1], c[2]); },
            v -> { colorV = (float) (Double.isNaN(v) ? 1 : v); float[] c = hsv(colorH, colorS, colorV); sendColor(true, c[0], c[1], c[2]); });
        String[] names = {"Red", "Green", "Blue"};
        int[] cols = {0xFFE5484D, 0xFF4CD08A, 0xFF5B8CFF};
        for (int i = 0; i < 3; i++) {
            final int ci = i;
            slider(g, sx, y + 30 + i * 22, sw, names[i], rgb[i], cols[i], v -> setRgb(ci, (float) v, false), v -> setRgb(ci, (float) (Double.isNaN(v) ? 0 : v), true));
        }
        Set<Attr> av = available(d);
        if (av.contains(Attr.WHITE)) slider(g, sx, y + 96, sw, "White", white, 0xFFF4E6C8, v -> { white = (float) v; whiteTouched = true; float[] c = hsv(colorH, colorS, colorV); sendColor(false, c[0], c[1], c[2]); },
            v -> { white = (float) (Double.isNaN(v) ? 0 : v); whiteTouched = true; float[] c = hsv(colorH, colorS, colorV); sendColor(true, c[0], c[1], c[2]); });
        if (av.contains(Attr.CTO)) attrSlider(g, d, Attr.CTO, sx, y + 118, sw, 0xFFFFC27A);
        // quick colours
        int qx = sx + sw + 14, qy = y + 6;
        int[] quick = {0xFF0000, 0xFF7F00, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0040FF, 0x8000FF, 0xFF00C0, 0xFFFFFF, 0xFFC27A};
        label(g, "Quick colours", qx, qy, Ui.MUTED);
        for (int i = 0; i < quick.length; i++) swatch(g, qx + (i % 5) * 18, qy + 11 + (i / 5) * 18, quick[i]);
        label(g, "Saved colours", qx, qy + 52, Ui.MUTED);
        List<Integer> saved = d.show.savedColors;
        for (int i = 0; i < saved.size(); i++) {
            final int idx = i;
            swatch(g, qx + (i % 8) * 18, qy + 63 + (i / 8) * 18, saved.get(i));
            hits.add(new Hit(qx + (i % 8) * 18, qy + 63 + (i / 8) * 18, 16, 16, b -> { if (b == 1) cmd(Commands.op(Commands.DELETE_COLOR).varInt(idx).toBytes()); }, null, null, null));
        }
        button(g, qx, qy + 63 + ((saved.size() + 7) / 8) * 18 + 2, 100, 14, t("Save colour"), GREEN, false, () -> {
            float[] c = hsv(colorH, colorS, colorV);
            cmd(Commands.op(Commands.SAVE_COLOR).i32((int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[2] * 255)).toBytes());
        }, "Right click a saved colour to delete it");
        // gradient over the selection
        int gy = y + 146;
        Ui.section(g, x, gy, 260, Component.literal(t("Gradient over the selection")));
        swatch(g, x, gy + 12, gradientA);
        hits.add(new Hit(x, gy + 12, 16, 16, b -> { float[] c = hsv(colorH, colorS, colorV); gradientA = rgbInt(c); }, null, null, Component.literal(t("Click: take the current colour"))));
        swatch(g, x + 20, gy + 12, gradientB);
        hits.add(new Hit(x + 20, gy + 12, 16, 16, b -> { float[] c = hsv(colorH, colorS, colorV); gradientB = rgbInt(c); }, null, null, Component.literal(t("Click: take the current colour"))));
        button(g, x + 42, gy + 12, 100, 16, t("Apply gradient"), ACCENT, false, () -> {
            int[] f = sel();
            for (int i = 0; i < f.length; i++) {
                float tt = f.length <= 1 ? 0 : i / (float) (f.length - 1);
                float r = lerp((gradientA >> 16 & 255) / 255f, (gradientB >> 16 & 255) / 255f, tt);
                float gg = lerp((gradientA >> 8 & 255) / 255f, (gradientB >> 8 & 255) / 255f, tt);
                float b = lerp((gradientA & 255) / 255f, (gradientB & 255) / 255f, tt);
                Bytes.Writer w2 = Commands.op(Commands.P_RGB);
                Commands.fids(w2, new int[] {f[i]});
                cmd(w2.unit(r).unit(gg).unit(b).unit(0).toBytes());
            }
        });
        // colour presets
        presets(g, d, Show.Preset.COLOR, gy + 34);
    }

    private void setRgb(int channel, float v, boolean force) {
        float[] c = hsv(colorH, colorS, colorV);
        c[channel] = v;
        float[] hsb = java.awt.Color.RGBtoHSB((int) (c[0] * 255), (int) (c[1] * 255), (int) (c[2] * 255), null);
        colorH = hsb[0]; colorS = hsb[1]; colorV = hsb[2];
        sendColor(force, c[0], c[1], c[2]);
    }

    private static int rgbInt(float[] c) {
        return (int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[2] * 255);
    }

    private void swatch(GuiGraphics g, int x, int y, int rgb) {
        g.fill(x, y, x + 16, y + 16, 0xFF000000 | rgb);
        Ui.frame(g, x, y, 16, 16, Ui.LINE);
        hits.add(new Hit(x, y, 16, 16, b -> {
            if (b != 0) return;
            float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
            colorH = hsb[0]; colorS = hsb[1]; colorV = hsb[2];
            sendColor(true, (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
        }, null, null, null));
    }

    /** Presets of one kind: apply (click), store the selection (button), delete (right click). */
    private void presets(GuiGraphics g, LumicoreBlockEntity d, int kind, int y) {
        int x = x0 + 8;
        Ui.section(g, x, y, w - 200, Component.literal(t(kind == Show.Preset.COLOR ? "Colour presets" : kind == Show.Preset.POSITION ? "Position presets" : "Presets")
            + "  ·  " + t("click = apply, right click = delete")));
        int i = 0;
        for (Show.Preset p : d.show.presets) {
            if (p.kind != kind) continue;
            int px = x + (i % 5) * 84, py = y + 12 + (i / 5) * 18;
            if (py > y0 + h - 60) break;
            final int id = p.id;
            g.fill(px, py, px + 4, py + 16, p.swatch);
            button(g, px + 5, py, 76, 16, p.name, kind == Show.Preset.COLOR ? ACCENT : CYAN, selPreset == id, () -> {
                selPreset = id;
                Bytes.Writer w2 = Commands.op(Commands.P_PRESET);
                Commands.fids(w2, sel());
                cmd(w2.varInt(id).toBytes());
            });
            hits.add(new Hit(px + 5, py, 76, 16, b -> { if (b == 1) cmd(Commands.op(Commands.PRESET_DELETE).varInt(id).toBytes()); }, null, null, null));
            i++;
        }
        int bx = x0 + w - 170, by = y0 + 30 + 16 + 184 + 18;
        button(g, bx, by, 160, 16, t("Store preset (selection)"), GREEN, false, () -> {
            Bytes.Writer w2 = Commands.op(Commands.STORE_PRESET).varInt(kind).str(nameBox == null ? "" : nameBox.getValue());
            Commands.fids(w2, sel());
            cmd(w2.sInt(-1).toBytes());
        });
    }

    // ------------------------------------------------------------ position
    private void position(GuiGraphics g, LumicoreBlockEntity d, int y) {
        int x = x0 + 8, pad = 120;
        Ui.section(g, x, y + 3, pad, Component.literal(t("Pan / Tilt pad")));
        int py = y + 16;
        g.fill(x, py, x + pad, py + pad, 0xFF0D0F12);
        Ui.frame(g, x, py, pad, pad, Ui.LINE);
        g.fill(x + pad / 2, py, x + pad / 2 + 1, py + pad, Ui.LINE);
        g.fill(x, py + pad / 2, x + pad, py + pad / 2 + 1, Ui.LINE);
        float pan = progValue(d, Attr.PAN), tilt = progValue(d, Attr.TILT);
        if (!Float.isNaN(pan) || !Float.isNaN(tilt)) {
            int dx = x + Math.round((Float.isNaN(pan) ? 0.5f : pan) * pad), dy = py + Math.round((1 - (Float.isNaN(tilt) ? 0.5f : tilt)) * pad);
            g.fill(dx - 3, dy - 3, dx + 4, dy + 4, CYAN);
        }
        hits.add(new Hit(x, py, pad, pad, null, (mx, my, rel) -> {
            float pv = (float) Math.max(0, Math.min(1, (mx - x) / pad)), tv = (float) Math.max(0, Math.min(1, 1 - (my - py) / pad));
            setAttr(Attr.PAN, pv, rel);
            setAttr(Attr.TILT, tv, rel);
        }, null, Component.literal(t("Drag: absolute pan (horizontal) and tilt (vertical) of the selection"))));
        int sx = x + pad + 12;
        Set<Attr> av = available(d);
        if (av.contains(Attr.PAN)) attrSlider(g, d, Attr.PAN, sx, py, 150, CYAN);
        if (av.contains(Attr.TILT)) attrSlider(g, d, Attr.TILT, sx, py + 22, 150, CYAN);
        button(g, sx, py + 48, 150, 16, t("Home / rest position"), CYAN, false, () -> { Bytes.Writer w2 = Commands.op(Commands.P_HOME); Commands.fids(w2, sel()); cmd(w2.toBytes()); });
        button(g, sx, py + 66, 150, 16, t("Build standard positions"), GREEN, false, () -> action(LightNet.A_POSITIONS, null),
            "Stage centre, audience, upstage, cross, vertical beams, fan out, rest — computed from where each fixture really is");
        raw(g, "§8" + t("Positions are aimed per fixture from the rig geometry."), sx, py + 88, Ui.MUTED);
        presets(g, d, Show.Preset.POSITION, py + pad + 6);
    }

    // ------------------------------------------------------------ effects
    private void effects(GuiGraphics g, LumicoreBlockEntity d, int y) {
        int x = x0 + 8;
        Ui.section(g, x, y + 3, w - 16, Component.literal(t("Effect engine — runs on the selection, recorded into the next cue")));
        // rev. 24: 17 effects on two rows
        for (int i = 0; i < Show.Effect.NAMES.length; i++) {
            final int type = i;
            button(g, x + (i % 9) * 66, y + 16 + (i / 9) * 17, 64, 15, t(Show.Effect.NAMES[i]), ACCENT, fx.type == i, () -> fx.type = type);
        }
        int sy = y + 40 + 17 * ((Show.Effect.NAMES.length - 1) / 9), sw = 170;
        slider(g, x, sy, sw, fx.bpmSync ? "Beats per cycle (/16)" : "Speed (cycles/s, /4)", fx.bpmSync ? fx.rate / 16f : fx.rate / 4f, ACCENT,
            v -> fx.rate = (float) Math.max(0.0625, v * (fx.bpmSync ? 16 : 4)), v -> { if (!Double.isNaN(v)) fx.rate = (float) Math.max(0.0625, v * (fx.bpmSync ? 16 : 4)); });
        raw(g, String.format(Locale.ROOT, "%.2f %s", fx.rate, fx.bpmSync ? t("beats") : "Hz"), x + sw + 6, sy + 10, Ui.TEXT);
        slider(g, x, sy + 22, sw, "Amplitude", fx.amplitude, ACCENT, v -> fx.amplitude = (float) v, v -> { if (!Double.isNaN(v)) fx.amplitude = (float) v; });
        slider(g, x, sy + 44, sw, "Phase spread", fx.spread, ACCENT, v -> fx.spread = (float) v, v -> { if (!Double.isNaN(v)) fx.spread = (float) v; });
        slider(g, x, sy + 66, sw, "Duty / width", fx.duty, ACCENT, v -> fx.duty = (float) v, v -> { if (!Double.isNaN(v)) fx.duty = (float) v; });
        slider(g, x, sy + 88, sw, "Fade in (/10 s)", fx.fade / 10f, ACCENT, v -> fx.fade = (float) v * 10, v -> { if (!Double.isNaN(v)) fx.fade = (float) v * 10; });
        int bx = x + sw + 70;
        button(g, bx, sy, 110, 16, t("BPM sync") + ": " + Tr.onOff(fx.bpmSync), ACCENT, fx.bpmSync, () -> { fx.bpmSync = !fx.bpmSync; fx.rate = fx.bpmSync ? 4 : 0.5f; },
            "Locks the effect to the beat of the analysed track (or the show tempo)");
        button(g, bx, sy + 18, 110, 16, t("Direction") + (fx.direction > 0 ? " →" : " ←"), ACCENT, false, () -> fx.direction = -fx.direction);
        button(g, bx, sy + 36, 110, 16, t("Symmetric") + ": " + Tr.onOff(fx.symmetric), ACCENT, fx.symmetric, () -> fx.symmetric = !fx.symmetric,
            "Phase mirrored from the middle of the selection");
        if (fx.type == Show.Effect.COLOR_CHASE) {
            label(g, "Chase colours (saved colours):", bx, sy + 58, Ui.MUTED);
            for (int i = 0; i < d.show.savedColors.size() && i < 16; i++) {
                int c = d.show.savedColors.get(i);
                boolean in = false;
                for (int k : fx.colors) if (k == c) in = true;
                int cx = bx + (i % 8) * 14, cyy = sy + 70 + (i / 8) * 14;
                g.fill(cx, cyy, cx + 12, cyy + 12, 0xFF000000 | c);
                if (in) Ui.frame(g, cx - 1, cyy - 1, 14, 14, 0xFFFFFFFF);
                final boolean was = in;
                hits.add(new Hit(cx, cyy, 12, 12, b -> {
                    List<Integer> l = new ArrayList<>();
                    for (int k : fx.colors) if (k != c) l.add(k);
                    if (!was && l.size() < 8) l.add(c);
                    fx.colors = l.stream().mapToInt(Integer::intValue).toArray();
                }, null, null, null));
            }
        }
        int ay = y0 + h - 56;
        button(g, x, ay, 150, 16, t("Run on selection"), GREEN, false, () -> {
            int[] f = sel();
            if (f.length == 0) return;
            Show.Effect e = fx.copy();
            e.fids = f;
            Bytes.Writer w2 = Commands.op(Commands.P_EFFECT_ADD);
            writeEffect(w2, e);
            cmd(w2.toBytes());
        });
        button(g, x + 154, ay, 150, 16, t("Stop programmer effects"), RED, false, () -> cmd(Commands.op(Commands.P_EFFECT_CLEAR).toBytes()));
        int ly = sy + 112;
        label(g, "Running in the programmer:", x, ly, Ui.MUTED);
        int i = 0;
        for (Show.Effect e : d.live.programmerEffects) {
            raw(g, "• " + t(Show.Effect.NAMES[Math.min(Show.Effect.NAMES.length - 1, e.type)]) + "  " + e.fids.length + " " + t("fixtures") + String.format(Locale.ROOT, "  %.2f %s", e.rate, e.bpmSync ? t("beats") : "Hz"),
                x + 4, ly + 11 + i * 10, Ui.TEXT);
            if (++i > 4) break;
        }
    }

    static void writeEffect(Bytes.Writer w, Show.Effect e) {
        w.varInt(e.type).varInt(e.fids.length);
        for (int f : e.fids) w.varInt(f);
        w.f32(e.rate).bool(e.bpmSync).unit(e.amplitude).unit(e.spread).sInt(e.direction).bool(e.symmetric).unit(e.duty).f32(e.fade);
        w.varInt(e.colors.length);
        for (int c : e.colors) w.varInt(c);
    }

    // ------------------------------------------------------------ cues
    private void cues(GuiGraphics g, LumicoreBlockEntity d, int y, int ch) {
        Show s = d.show;
        int x = x0 + 8;
        if (selList < 0 || s.list(selList) == null) selList = s.lists.isEmpty() ? -1 : s.lists.get(0).id;
        int lx = x;
        for (Show.CueList l : s.lists) {
            final int id = l.id;
            int lw = Math.min(110, font.width(l.name) + 12);
            if (lx + lw > x0 + w - 180) break;
            button(g, lx, y + 2, lw, 14, l.name, ACCENT, selList == id, () -> { selList = id; selCue = -1; });
            lx += lw + 2;
        }
        button(g, lx, y + 2, 20, 14, "+", GREEN, false, () -> cmd(Commands.op(Commands.LIST_NEW).str("").toBytes()), "New cue list");
        Show.CueList list = s.list(selList);
        int ty = y + 20;
        raw(g, "§8" + t("Cue    Name                      In     Out    Delay  Follow  Trig.  Prio"), x, ty, Ui.MUTED);
        int rows = (ch - 30) / 11;
        if (list != null) {
            Live.Playback pb = d.live.playbacks.get(list.id);
            cueScroll = Math.max(0, Math.min(cueScroll, Math.max(0, list.cues.size() - rows + 1)));
            for (int i = 0; i < rows - 1 && i + cueScroll < list.cues.size(); i++) {
                int idx = i + cueScroll;
                Show.Cue c = list.cues.get(idx);
                int ry = ty + 11 + i * 11;
                boolean active = pb != null && pb.index == idx;
                if (idx == selCue) g.fill(x - 2, ry - 1, x0 + w - 182, ry + 10, 0x60B07CFF);
                if (active) g.fill(x - 6, ry, x - 3, ry + 8, GREEN);
                String trig = c.trigger == Show.Cue.TRIGGER_FOLLOW ? "FLW" : c.trigger == Show.Cue.TRIGGER_TIME ? "TC" : "GO";
                raw(g, String.format(Locale.ROOT, "%-6s %-25s %-6s %-6s %-6s %-7s %-6s %s", LumicoreRenderer.fmt(c.number), clip(c.name, 25), sec(c.fadeIn), sec(c.fadeOut),
                    sec(c.delay), c.follow < 0 ? "-" : sec(c.follow), trig, c.priority < 0 ? "-" : String.valueOf(c.priority))
                    + (c.effects.isEmpty() ? "" : " §d" + c.effects.size() + "fx"), x, ry, active ? GREEN : Ui.TEXT);
                final int ci = idx;
                hits.add(new Hit(x - 2, ry - 1, w - 190, 11, b -> {
                    selCue = ci;
                    Show.CueList ll = desk().show.list(selList);
                    if (ll != null && ci < ll.cues.size() && nameBox != null) { nameBox.setValue(ll.cues.get(ci).name); numberBox.setValue(LumicoreRenderer.fmt(ll.cues.get(ci).number)); }
                }, null, sc -> cueScroll = Math.max(0, cueScroll - sc), null));
            }
        } else label(g, "No cue list yet: Record creates one.", x, ty + 14, Ui.MUTED);
        // right column: record and edit
        int bx = x0 + w - 170, by = y0 + 30 + 16;
        String[] fam = {"I", "P", "C", "B"};
        label(g, "Record mask:", bx, by, Ui.MUTED);
        for (int i = 0; i < 4; i++) {
            final int bit = 1 << i;
            button(g, bx + 70 + i * 22, by - 2, 20, 12, fam[i], AMBER, recordMask == 0 || (recordMask & bit) != 0, () -> recordMask ^= bit, "Intensity / Position / Color / Beam (none lit = all)");
        }
        slider(g, bx, by + 12, 160, "Fade (s, /10)", recordFade / 10f, AMBER, v -> recordFade = Math.round(v * 100) / 10f, v -> { if (!Double.isNaN(v)) recordFade = Math.round(v * 100) / 10f; });
        raw(g, sec(recordFade) + " s", bx + 110, by + 12, Ui.TEXT);
        button(g, bx, by + 34, 78, 18, t("Record"), RED, false, this::recordNext, "Stores the programmer as the next cue (R)");
        button(g, bx + 82, by + 34, 78, 18, t("Update"), AMBER, false, () -> { if (selList >= 0 && selCue >= 0) cmd(Commands.op(Commands.UPDATE_CUE).varInt(selList).varInt(selCue).toBytes()); },
            "Merges the programmer into the selected cue");
        if (list != null && selCue >= 0 && selCue < list.cues.size()) {
            Show.Cue c = list.cues.get(selCue);
            int ey = by + 58;
            raw(g, t("Cue") + " " + LumicoreRenderer.fmt(c.number), bx, ey, Ui.TEXT);
            ey += 12;
            ey = stepper(g, bx, ey, "Fade in", c.fadeIn, 0.5f, v -> editCue(list, c, v, c.fadeOut, c.delay, c.follow, c.trigger, c.priority));
            ey = stepper(g, bx, ey, "Fade out", c.fadeOut, 0.5f, v -> editCue(list, c, c.fadeIn, v, c.delay, c.follow, c.trigger, c.priority));
            ey = stepper(g, bx, ey, "Delay", c.delay, 0.5f, v -> editCue(list, c, c.fadeIn, c.fadeOut, v, c.follow, c.trigger, c.priority));
            ey = stepper(g, bx, ey, "Follow", c.follow, 1f, v -> editCue(list, c, c.fadeIn, c.fadeOut, c.delay, v < 0 ? -1 : v, c.trigger, c.priority));
            String[] trig = {"GO", "Follow", "Timecode"};
            button(g, bx, ey, 160, 14, t("Trigger") + ": " + t(trig[c.trigger]), AMBER, false, () -> editCue(list, c, c.fadeIn, c.fadeOut, c.delay, c.follow, (c.trigger + 1) % 3, c.priority));
            ey += 16;
            ey = stepper(g, bx, ey, "Priority", c.priority, 10f, v -> editCue(list, c, c.fadeIn, c.fadeOut, c.delay, c.follow, c.trigger, (int) Math.max(-1, Math.min(100, v))));
            // name / number boxes are placed by init(); their buttons:
            button(g, bx + 74, by + 172, 86, 14, t("Apply name/no."), ACCENT, false, () -> {
                float num = parse(numberBox.getValue(), c.number);
                cmd(Commands.op(Commands.CUE_EDIT).varInt(list.id).varInt(selCue).f32(num).str(nameBox.getValue()).f32(c.fadeIn).f32(c.fadeOut).f32(c.delay).f32(c.follow)
                    .varInt(c.trigger).sInt(c.priority).toBytes());
            });
            button(g, bx, by + 190, 52, 14, t("Go to"), GREEN, false, () -> cmd(Commands.op(Commands.GOTO).varInt(list.id).varInt(selCue).toBytes()));
            button(g, bx + 54, by + 190, 52, 14, t("Copy"), ACCENT, false, () -> cmd(Commands.op(Commands.CUE_COPY).varInt(list.id).varInt(selCue).f32(nextNumber(list)).toBytes()));
            button(g, bx + 108, by + 190, 52, 14, t("Delete"), RED, false, () -> { cmd(Commands.op(Commands.CUE_DELETE).varInt(list.id).varInt(selCue).toBytes()); selCue = -1; });
            for (int i = 0; i < c.effects.size() && i < 3; i++) {
                final int ei = i;
                Show.Effect e = c.effects.get(i);
                button(g, bx, by + 208 + i * 15, 160, 13, "✕ " + t(Show.Effect.NAMES[Math.min(Show.Effect.NAMES.length - 1, e.type)]) + " (" + e.fids.length + ")", RED, false,
                    () -> cmd(Commands.op(Commands.EFFECT_DELETE).varInt(list.id).varInt(selCue).varInt(ei).toBytes()));
            }
        }
        if (list != null) {
            int ly = y0 + h - 56;
            button(g, x, ly, 110, 16, t("Priority") + " " + list.priority, ACCENT, false,
                () -> cmd(Commands.op(Commands.LIST_EDIT).varInt(list.id).str("").varInt((list.priority + 10) % 110).bool(list.loop).toBytes()), "Higher priority lists win the positions and colours");
            button(g, x + 114, ly, 80, 16, t("Loop") + ": " + Tr.onOff(list.loop), ACCENT, list.loop,
                () -> cmd(Commands.op(Commands.LIST_EDIT).varInt(list.id).str("").varInt(list.priority).bool(!list.loop).toBytes()));
            button(g, x + 198, ly, 110, 16, t("Rename list"), ACCENT, false,
                () -> { if (!nameBox.getValue().isBlank()) cmd(Commands.op(Commands.LIST_EDIT).varInt(list.id).str(nameBox.getValue()).varInt(list.priority).bool(list.loop).toBytes()); });
            button(g, x + 312, ly, 90, 16, t("Delete list"), RED, false, () -> { cmd(Commands.op(Commands.LIST_DELETE).varInt(list.id).toBytes()); selList = -1; });
        }
    }

    private interface FloatSet {
        void set(float v);
    }

    private int stepper(GuiGraphics g, int x, int y, String label, float value, float step, FloatSet set) {
        raw(g, t(label), x, y + 3, Ui.MUTED);
        raw(g, value < 0 ? "-" : sec(value), x + 84, y + 3, Ui.TEXT);
        button(g, x + 116, y, 20, 13, "-", ACCENT, false, () -> set.set(Math.max(-1, value - step)));
        button(g, x + 140, y, 20, 13, "+", ACCENT, false, () -> set.set(value < 0 ? 0 : value + step));
        return y + 15;
    }

    private void editCue(Show.CueList list, Show.Cue c, float in, float out, float delay, float follow, int trigger, int prio) {
        cmd(Commands.op(Commands.CUE_EDIT).varInt(list.id).varInt(list.cues.indexOf(c)).f32(c.number).str(c.name).f32(Math.max(0, in)).f32(Math.max(0, out))
            .f32(Math.max(0, delay)).f32(follow).varInt(trigger).sInt(prio).toBytes());
    }

    private float nextNumber(Show.CueList l) {
        float max = 0;
        for (Show.Cue c : l.cues) max = Math.max(max, c.number);
        return (float) Math.floor(max) + 1;
    }

    private void recordNext() {
        LumicoreBlockEntity d = desk();
        if (d == null) return;
        Show.CueList l = d.show.list(selList);
        float number = l == null ? 1 : nextNumber(l);
        if (numberBox != null && !numberBox.getValue().isBlank() && hasShiftDown()) number = parse(numberBox.getValue(), number);
        int listId = l == null ? d.show.nextListId() : l.id;
        if (l == null) selList = listId;
        cmd(Commands.op(Commands.STORE_CUE).varInt(listId).f32(number).str(nameBox == null ? "" : nameBox.getValue()).f32(recordFade).varInt(0).varInt(recordMask).toBytes());
    }

    // ------------------------------------------------------------ playback
    private void playback(GuiGraphics g, LumicoreBlockEntity d, int y, int ch) {
        Show s = d.show;
        int x = x0 + 8;
        int ew = 54, n = Math.min(s.lists.size(), (w - 90) / (ew + 4));
        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() * 50L;
        for (int i = 0; i < n; i++) {
            Show.CueList l = s.lists.get(i);
            Live.Playback pb = d.live.playbacks.get(l.id);
            int ex = x + i * (ew + 4);
            g.fill(ex, y + 2, ex + ew, y + ch - 4, 0xFF181B20);
            Ui.frame(g, ex, y + 2, ew, ch - 6, selList == l.id ? ACCENT : Ui.LINE);
            raw(g, clip(l.name, 9), ex + 3, y + 5, Ui.TEXT);
            String cur = "-", next = "-";
            if (pb != null && pb.running() && pb.index < l.cues.size()) {
                cur = LumicoreRenderer.fmt(l.cues.get(pb.index).number);
                int ni = pb.index + 1 < l.cues.size() ? pb.index + 1 : l.loop ? 0 : -1;
                if (ni >= 0) next = LumicoreRenderer.fmt(l.cues.get(ni).number);
                Show.Cue c = l.cues.get(pb.index);
                float prog = c.duration() <= 0 ? 1 : Math.min(1, pb.elapsed(now) / 1000f / Math.max(0.01f, c.duration()));
                g.fill(ex + 3, y + 26, ex + 3 + Math.round((ew - 6) * prog), y + 28, pb.paused ? AMBER : GREEN);
            } else if (!l.cues.isEmpty()) next = LumicoreRenderer.fmt(l.cues.get(0).number);
            raw(g, "§a" + cur + " §8> §f" + next, ex + 3, y + 15, Ui.TEXT);
            final int id = l.id;
            float level = pb == null ? 1 : pb.level;
            int fh = ch - 128;
            fader(g, ex + ew / 2 - 8, y + 32, 16, fh, level, ACCENT,
                v -> { if (!throttled("fader" + id, false)) cmd(Commands.op(Commands.FADER).varInt(id).unit((float) v).toBytes()); },
                v -> cmd(Commands.op(Commands.FADER).varInt(id).unit((float) v).toBytes()));
            int by = y + 34 + fh;
            raw(g, Math.round(level * 100) + "%", ex + 3, by, Ui.MUTED);
            button(g, ex + 2, by + 10, ew - 4, 16, "GO", GREEN, false, () -> { selList = id; cmd(Commands.op(Commands.GO).varInt(id).toBytes()); });
            button(g, ex + 2, by + 28, (ew - 6) / 2, 14, "◀", ACCENT, false, () -> cmd(Commands.op(Commands.BACK).varInt(id).toBytes()), "Back");
            button(g, ex + 4 + (ew - 6) / 2, by + 28, (ew - 6) / 2, 14, "❚❚", AMBER, pb != null && pb.paused, () -> cmd(Commands.op(Commands.PAUSE).varInt(id).toBytes()), "Pause");
            button(g, ex + 2, by + 44, ew - 4, 14, "■", RED, false, () -> cmd(Commands.op(Commands.STOP).varInt(id).toBytes()), "Stop (releases with the fade out)");
            // flash: held while the mouse button is down
            boolean flash = pb != null && pb.flash;
            int fy = by + 60;
            g.fill(ex + 2, fy, ex + ew - 2, fy + 16, flash ? 0xFFF2B33D : 0xFF2A2E35);
            Ui.centeredFit(g, Component.literal("FLASH"), ex + ew / 2, fy + 4, ew - 6, flash ? 0xFF000000 : Ui.TEXT);
            hits.add(new Hit(ex + 2, fy, ew - 4, 16, b -> cmd(Commands.op(Commands.FLASH).varInt(id).bool(true).toBytes()),
                (mx, my, rel) -> { if (rel) cmd(Commands.op(Commands.FLASH).varInt(id).bool(false).toBytes()); }, null, Component.literal(t("Held: full level"))));
        }
        if (s.lists.isEmpty()) label(g, "Record cues (Cues tab) or generate an AutoShow to get playbacks.", x, y + 10, Ui.MUTED);
        // grand master
        int gx = x0 + w - 60;
        raw(g, "GM", gx + 12, y + 4, Ui.TEXT);
        fader(g, gx + 10, y + 16, 20, ch - 60, d.live.grandMaster, ACCENT,
            v -> { if (!throttled("gm", false)) cmd(Commands.op(Commands.GRAND_MASTER).unit((float) v).toBytes()); },
            v -> cmd(Commands.op(Commands.GRAND_MASTER).unit((float) v).toBytes()));
        raw(g, Math.round(d.live.grandMaster * 100) + "%", gx + 8, y + ch - 40, Ui.TEXT);
        label(g, "Space = GO of the selected list", gx - 120, y + ch - 12, Ui.MUTED);
    }

    // ------------------------------------------------------------ timeline
    private void timeline(GuiGraphics g, LumicoreBlockEntity d, int y, int ch, float partial) {
        Show s = d.show;
        Show.Timeline tl = s.timeline;
        int x = x0 + 8, tw = w - 16;
        var level = Minecraft.getInstance().level;
        Engine.Music m = level == null ? Engine.Music.NONE : com.concertstage.light.LightServer.music(level, d.live, level.getGameTime() + partial, 0);
        long dur = 0;
        for (Show.Section sc : tl.sections) dur = Math.max(dur, sc.endMs);
        for (Show.Event e : tl.events) dur = Math.max(dur, e.ms + 2000);
        if (m.valid()) dur = Math.max(dur, m.positionMs() + 10000);
        dur = Math.max(dur, 60000);
        tlSpan = Math.max(5000, Math.min(tlSpan, dur));
        tlStart = Math.max(0, Math.min(tlStart, dur - tlSpan));
        raw(g, t("Track") + ": §f" + (tl.trackName.isEmpty() ? t("(none: use the current track)") : tl.trackName)
            + (tl.bpm > 0 ? String.format(Locale.ROOT, "   §7BPM §f%.1f", tl.bpm) : "") + "   §7" + t("Offset") + " §f" + tl.offsetMs + " ms", x, y + 2, Ui.MUTED);
        int top = y + 16, rowH = 12;
        List<Integer> listIds = new ArrayList<>();
        for (Show.Event e : tl.events) if (e.kind != Show.Event.ACCENT && !listIds.contains(e.list)) listIds.add(e.list);
        int rows = 2 + listIds.size();
        int th = Math.min(ch - 70, 16 + rows * rowH + 10);
        g.fill(x, top, x + tw, top + th, 0xFF0D0F12);
        Ui.frame(g, x, top, tw, th, Ui.LINE);
        // sections
        for (Show.Section sc : tl.sections) {
            int a = tx(sc.startMs, x, tw), b = tx(sc.endMs, x, tw);
            if (b < x || a > x + tw) continue;
            a = Math.max(a, x); b = Math.min(b, x + tw);
            int col = switch (sc.kind) { case 3, 4 -> 0xFF8A2A30; case 2 -> 0xFF7A5A20; case 5 -> 0xFF234A6A; case 7 -> 0xFF202020; default -> 0xFF2A3A55; };
            g.fill(a, top + 1, b, top + rowH, col);
            if (b - a > 30) raw(g, clip(sc.label, (b - a) / 6), a + 2, top + 2, Ui.TEXT);
        }
        // beat grid (bars)
        if (tl.bpm > 0) {
            double barMs = 60000.0 / tl.bpm * Math.max(1, tl.beatsPerBar);
            if (tlSpan / barMs < tw / 4.0) {
                for (double t = tl.beatOffsetMs; t < tlStart + tlSpan; t += barMs) {
                    if (t < tlStart) continue;
                    int bx = tx((long) t, x, tw);
                    g.fill(bx, top + rowH, bx + 1, top + th, 0x30FFFFFF);
                }
            }
        }
        // markers
        for (int i = 0; i < tl.markers.size(); i++) {
            Show.Marker mk = tl.markers.get(i);
            int mx = tx(mk.ms, x, tw);
            if (mx < x || mx > x + tw) continue;
            g.fill(mx, top + rowH, mx + 1, top + th, mk.color);
        }
        // events: one row per list, accents in the last row
        for (int i = 0; i < tl.events.size(); i++) {
            Show.Event e = tl.events.get(i);
            int ex = tx(e.ms, x, tw);
            if (ex < x - 3 || ex > x + tw + 3) continue;
            int row = e.kind == Show.Event.ACCENT ? rows - 1 : 1 + listIds.indexOf(e.list);
            int ey = top + row * rowH + 6;
            int col = e.kind == Show.Event.GO ? GREEN : e.kind == Show.Event.STOP ? RED : AMBER;
            g.fill(ex - 2, ey - 2, ex + 3, ey + 3, i == tlEvent ? 0xFFFFFFFF : col);
            final int idx = i;
            hits.add(new Hit(ex - 3, ey - 3, 7, 7, b -> {
                if (b == 1) { cmd(Commands.op(Commands.TL_EVENT_DELETE).varInt(idx).toBytes()); tlEvent = -1; }
                else tlEvent = idx;
            }, (mx2, my2, rel) -> {
                if (rel && tlEvent == idx) cmd(Commands.op(Commands.TL_EVENT_MOVE).varInt(idx).varLong(Math.max(0, msAt(mx2, x, tw))).toBytes());
            }, null, Component.literal((e.kind == Show.Event.GO ? "GO " + LumicoreRenderer.fmt(e.cue) : e.kind == Show.Event.STOP ? "STOP" : t("Accent")) + " @ " + time(e.ms)
                + " · " + t("drag = move, right click = delete"))));
        }
        for (int r = 0; r < listIds.size(); r++) {
            Show.CueList l = s.list(listIds.get(r));
            if (l != null) raw(g, "§8" + clip(l.name, 12), x + 2, top + (1 + r) * rowH + 2, Ui.MUTED);
        }
        // playhead and preview cursor
        if (m.valid() && m.positionMs() >= tlStart && m.positionMs() <= tlStart + tlSpan) {
            int px = tx(m.positionMs(), x, tw);
            g.fill(px, top, px + 1, top + th, 0xFFFF3040);
        }
        boolean previewing = pos.equals(LightClient.previewDesk) && LightClient.previewMs >= 0;
        if (previewing) {
            int px = tx(LightClient.previewMs, x, tw);
            g.fill(px, top, px + 1, top + th, 0xFF35C9E8);
        }
        hits.add(new Hit(x, top, tw, th, b -> {
            long ms = msAt(mouseX, x, tw);
            if (b == 0 && hasShiftDown() && selList >= 0 && selCue >= 0 && d.show.list(selList) != null && selCue < d.show.list(selList).cues.size()) {
                cmd(Commands.op(Commands.TL_EVENT_ADD).varLong(ms).varInt(Show.Event.GO).varInt(selList).f32(d.show.list(selList).cues.get(selCue).number).sInt(-1).unit(1).varInt(150).toBytes());
            } else if (b == 0) {
                LightClient.previewDesk = pos;
                LightClient.previewMs = ms;
            } else if (b == 1) {
                LightClient.previewDesk = null;
                LightClient.previewMs = -1;
            }
        }, (mx2, my2, rel) -> {
            if (pos.equals(LightClient.previewDesk)) LightClient.previewMs = Math.max(0, msAt(mx2, x, tw));
        }, sc -> {
            if (hasShiftDown()) tlStart = Math.max(0, tlStart - (long) (sc * tlSpan / 10));
            else {
                long center = msAt(mouseX, x, tw);
                tlSpan = Math.max(5000, (long) (tlSpan * (sc > 0 ? 0.8 : 1.25)));
                tlStart = Math.max(0, center - tlSpan / 2);
            }
        }, Component.literal(t("Click: preview the light at that time · Shift+click: add GO (cue selected in Cues) · wheel: zoom · Shift+wheel: scroll · right click: end preview"))));
        raw(g, time(tlStart), x, top + th + 2, Ui.MUTED);
        raw(g, time(tlStart + tlSpan), x + tw - font.width(time(tlStart + tlSpan)), top + th + 2, Ui.MUTED);
        if (previewing) raw(g, "§b" + t("Preview") + " " + time(LightClient.previewMs), x + tw / 2 - 30, top + th + 2, CYAN);
        // controls
        int cyy = top + th + 14;
        boolean armed = d.live.timelineArmed;
        button(g, x, cyy, 110, 16, t("Timeline") + ": " + (armed ? t("ARMED") : t("OFF")), GREEN, armed, () -> cmd(Commands.op(Commands.TIMELINE_ARM).bool(!desk().live.timelineArmed).toBytes()),
            "Armed: the events fire with the music of the linked audio console");
        button(g, x + 114, cyy, 130, 16, t("Use current track"), CYAN, false, () -> {
            var lv = Minecraft.getInstance().level;
            var a = lv == null ? null : com.concertstage.light.LightServer.audio(lv, desk().live);
            var trk = a == null ? null : a.currentTrack();
            if (trk != null) cmd(Commands.op(Commands.TL_TRACK).str(trk.sha1()).str(trk.name()).toBytes());
        });
        button(g, x + 248, cyy, 24, 16, "-", ACCENT, false, () -> cmd(Commands.op(Commands.TL_OFFSET).sInt(desk().show.timeline.offsetMs - 20).toBytes()), "Lights earlier (20 ms)");
        button(g, x + 274, cyy, 24, 16, "+", ACCENT, false, () -> cmd(Commands.op(Commands.TL_OFFSET).sInt(desk().show.timeline.offsetMs + 20).toBytes()), "Lights later (20 ms)");
        button(g, x + 302, cyy, 120, 16, t("Clear events"), RED, false, () -> cmd(Commands.op(Commands.TL_CLEAR).toBytes()));
        int cy2 = cyy + 20;
        button(g, x, cy2, 110, 16, t("Accent here"), AMBER, false, () -> {
            long ms = previewing ? LightClient.previewMs : m.valid() ? m.positionMs() : -1;
            if (ms >= 0) cmd(Commands.op(Commands.TL_EVENT_ADD).varLong(ms).varInt(Show.Event.ACCENT).varInt(0).f32(0).sInt(selGroup).unit(1).varInt(150).toBytes());
        }, "A short flash of the selected group (Groups tab) at the preview cursor or the playhead");
        button(g, x + 114, cy2, 130, 16, t("STOP list here"), RED, false, () -> {
            long ms = previewing ? LightClient.previewMs : m.valid() ? m.positionMs() : -1;
            if (ms >= 0 && selList >= 0) cmd(Commands.op(Commands.TL_EVENT_ADD).varLong(ms).varInt(Show.Event.STOP).varInt(selList).f32(0).sInt(-1).unit(1).varInt(150).toBytes());
        });
        int bx = x0 + w - 178;
        button(g, bx + 114, y0 + h - 88, 56, 14, t("Marker"), AMBER, false, () -> {
            long ms = previewing ? LightClient.previewMs : m.valid() ? m.positionMs() : -1;
            if (ms >= 0) cmd(Commands.op(Commands.TL_MARKER_ADD).varLong(ms).str(nameBox.getValue()).toBytes());
        });
        raw(g, "§8" + t("Your light delay") + ": " + LightClient.offsetMs + " ms", x + 250, cy2 + 4, Ui.MUTED);
        button(g, x + 400, cy2, 20, 16, "-", ACCENT, false, () -> { LightClient.offsetMs -= 20; LightClient.save(); }, "Only for you: lights earlier (your sound buffering)");
        button(g, x + 422, cy2, 20, 16, "+", ACCENT, false, () -> { LightClient.offsetMs += 20; LightClient.save(); }, "Only for you: lights later");
    }

    private int tx(long ms, int x, int tw) {
        return x + (int) Math.round((ms - tlStart) / (double) tlSpan * tw);
    }

    private long msAt(double mx, int x, int tw) {
        return tlStart + Math.round((mx - x) / tw * tlSpan);
    }

    // ------------------------------------------------------------ AutoShow
    /** Rev. 24: the advanced settings of the AutoShow tab (effects used, favourite colours, analysis mode) are shown on demand. */
    private boolean autoAdvanced;

    /**
     * Rev. 24 — AutoShow tab made simple: three steps on top (music, style, generate), the big buttons, four sliders and the strobe
     * safety; the effect list and the favourite colours behind "Advanced".
     */
    private void auto(GuiGraphics g, LumicoreBlockEntity d, int y) {
        Show.AutoSettings a = d.show.auto;
        int x = x0 + 8;
        Ui.section(g, x, y + 3, w - 16, Component.literal("CSC AUTOshow Engine"));
        boolean audio = d.live.hasAudio;
        boolean made = !d.show.timeline.sections.isEmpty();
        // the three steps
        String ok = "§a✔ ", todo = "§e➜ ";
        raw(g, (audio ? ok : todo) + t("1. Music playing"), x + 150, y + 3, Ui.TEXT);
        raw(g, ok + t("2. Style"), x + 290, y + 3, Ui.TEXT);
        raw(g, (made ? ok : todo) + t("3. Generate"), x + 380, y + 3, Ui.TEXT);
        for (int i = 0; i < Show.AutoSettings.STYLES.length; i++) {
            final int st = i;
            button(g, x + i * 92, y + 16, 90, 16, t(Show.AutoSettings.STYLES[i]), ACCENT, a.style == i, () -> sendAuto(desk().show.auto, st, -1));
        }
        // the big buttons
        int ay = y + 38;
        button(g, x, ay, 240, 22, "▶ " + t("GENERATE THE AUTOSHOW"), GREEN, false, () -> action(LightNet.A_AUTOSHOW, null),
            "Analyses the current track of the linked audio console (tempo, beats, sections, repetitions) and writes the AutoShow cue list and timeline");
        button(g, x + 244, ay, 150, 22, t("Live (follows the music)"), CYAN, false, () -> action(LightNet.A_LIVE_START, null),
            "Follows the music while it plays, with stabilised changes");
        button(g, x + 398, ay, 90, 22, t("Stop live"), RED, false, () -> action(LightNet.A_LIVE_STOP, null));
        raw(g, "§7" + (d.status.isEmpty() ? (audio ? t("Ready.") : t("Link the CSC audio console first (Fixtures tab), then play a track.")) : d.status), x, ay + 27, Ui.MUTED);
        // the four sliders and the strobe safety
        int sy = ay + 42, sw = 180;
        slider(g, x, sy, sw, "Intensity", a.intensity, AMBER, v -> {}, v -> { if (!Double.isNaN(v)) { a.intensity = (float) v; sendAuto(a, -1, -1); } });
        slider(g, x, sy + 22, sw, "Movement", a.movement, CYAN, v -> {}, v -> { if (!Double.isNaN(v)) { a.movement = (float) v; sendAuto(a, -1, -1); } });
        slider(g, x + sw + 14, sy, sw, "Colour variety", a.colorVariety, ACCENT, v -> {}, v -> { if (!Double.isNaN(v)) { a.colorVariety = (float) v; sendAuto(a, -1, -1); } });
        slider(g, x + sw + 14, sy + 22, sw, "Effects", a.effectAmount, GREEN, v -> {}, v -> { if (!Double.isNaN(v)) { a.effectAmount = (float) v; sendAuto(a, -1, -1); } });
        int bx = x + 2 * (sw + 14);
        button(g, bx, sy, 120, 16, t("Strobes") + ": " + Tr.onOff(a.strobes), AMBER, a.strobes, () -> { a.strobes = !a.strobes; sendAuto(a, -1, -1); },
            "Off: no strobe and no flash accent (photosensitive audience)");
        raw(g, t("Max strobe") + String.format(Locale.ROOT, " %.0f Hz", a.strobeMaxHz), bx, sy + 24, Ui.MUTED);
        button(g, bx + 78, sy + 21, 20, 14, "-", AMBER, false, () -> { a.strobeMaxHz = Math.max(1, a.strobeMaxHz - 1); sendAuto(a, -1, -1); });
        button(g, bx + 100, sy + 21, 20, 14, "+", AMBER, false, () -> { a.strobeMaxHz = Math.min(15, a.strobeMaxHz + 1); sendAuto(a, -1, -1); });
        int ny = sy + 46;
        button(g, x, ny, 120, 14, (autoAdvanced ? "▾ " : "▸ ") + t("Advanced"), ACCENT, autoAdvanced, () -> autoAdvanced = !autoAdvanced,
            "Effects used, favourite colours, analysis mode");
        raw(g, "§8" + t("The result is a normal show: edit its cues (Cues) and events (Timeline). STOP AUTO stops it at once."), x + 126, ny + 3, Ui.MUTED);
        if (!autoAdvanced) {
            int sections = d.show.timeline.sections.size();
            if (sections > 0) {
                StringBuilder sb = new StringBuilder(t("Sections") + ": ");
                for (Show.Section sc : d.show.timeline.sections) { if (sb.length() > 170) { sb.append("…"); break; } sb.append(sc.label).append(" · "); }
                raw(g, "§8" + sb, x, ny + 20, Ui.MUTED);
            }
            return;
        }
        int vy = ny + 20;
        label(g, "Effects used", x, vy, Ui.MUTED);
        for (int i = 0; i < Show.Effect.NAMES.length; i++) {
            final int bit = 1 << i;
            boolean on = (a.effectMask & bit) != 0;
            button(g, x + (i % 6) * 64, vy + 11 + (i / 6) * 15, 62, 13, t(Show.Effect.NAMES[i]), GREEN, on, () -> { a.effectMask ^= bit; sendAuto(a, -1, -1); });
        }
        int fx2 = x + 6 * 64 + 10;
        label(g, "Favourite colours (saved colours)", fx2, vy, Ui.MUTED);
        for (int i = 0; i < d.show.savedColors.size() && i < 16; i++) {
            int c = d.show.savedColors.get(i);
            boolean in = a.favoriteColors.contains(c);
            int cx = fx2 + (i % 8) * 16, cyy = vy + 12 + (i / 8) * 16;
            g.fill(cx, cyy, cx + 14, cyy + 14, 0xFF000000 | c);
            if (in) Ui.frame(g, cx - 1, cyy - 1, 16, 16, 0xFFFFFFFF);
            hits.add(new Hit(cx, cyy, 14, 14, b -> { if (in) a.favoriteColors.remove((Integer) c); else a.favoriteColors.add(c); sendAuto(a, -1, -1); }, null, null, null));
        }
        if (d.show.savedColors.isEmpty()) raw(g, "§8" + t("Save colours in the Color tab first."), fx2, vy + 14, Ui.MUTED);
        label(g, "Mode", fx2, vy + 48, Ui.MUTED);
        button(g, fx2 + 30, vy + 45, 90, 14, t("Pre-analysis"), CYAN, a.mode == 0, () -> sendAuto(desk().show.auto, -1, 0));
        button(g, fx2 + 122, vy + 45, 60, 14, t("Live"), CYAN, a.mode == 1, () -> sendAuto(desk().show.auto, -1, 1));
    }

    private void sendAuto(Show.AutoSettings a, int style, int mode) {
        Bytes.Writer w2 = Commands.op(Commands.AUTO_SETTINGS).varInt(style >= 0 ? style : a.style).varInt(mode >= 0 ? mode : a.mode)
            .unit(a.intensity).unit(a.movement).unit(a.colorVariety).unit(a.effectAmount).bool(a.strobes).f32(a.strobeMaxHz).varInt(a.effectMask)
            .varInt(a.favoriteColors.size());
        for (int c : a.favoriteColors) w2.varInt(c);
        cmd(w2.toBytes());
    }

    // ------------------------------------------------------------ shows
    private Path showsDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("concertstage").resolve("shows");
    }

    // ------------------------------------------------------------ External Show Control
    private int extUniverse = 0, extMode = com.concertstage.light.dmx.DmxMode.FULL, extShare = com.concertstage.light.dmx.AutoPatch.SHARE_NONE;
    private boolean extSelectionOnly;
    /** EXPORT FIXTURE PROFILE: index in ExternalExport.PROFILE_KINDS (grandMA3 GDTF, grandMA2 XML, dot2 XML). */
    private int extProfile = 2;

    /** Modes and channel counts of the exported profiles (tooltip of the software button). */
    private static String profileTip() {
        StringBuilder s = new StringBuilder(t("Click: change the software. Channels Full / Compact / Ultra Compact:")).append(' ');
        boolean first = true;
        for (com.concertstage.light.core.FixtureProfile p : com.concertstage.light.core.FixtureProfile.all()) {
            if (!first) s.append(" · ");
            first = false;
            s.append(p.name.replace("CSC ", "")).append(' ');
            int[] modes = {com.concertstage.light.dmx.DmxMode.FULL, com.concertstage.light.dmx.DmxMode.COMPACT, com.concertstage.light.dmx.DmxMode.ULTRA};
            for (int i = 0; i < modes.length; i++) s.append(i == 0 ? "" : "/").append(com.concertstage.light.dmx.DmxMode.of(p, modes[i]).footprint());
        }
        return s.toString();
    }

    private void ext(int op, java.util.function.Consumer<Bytes.Writer> args) {
        action(LightNet.A_EXT, w2 -> { w2.varInt(op); if (args != null) args.accept(w2); });
    }

    /** CSC EXTERNAL CONTROL MANAGER: control mode, connection, patch, export / import, recording, playback. */
    private void external(GuiGraphics g, LumicoreBlockEntity d, int y, int ch) {
        ExternalStatus es = ExternalStatus.of(pos);
        Show s = d.show;
        Live l = d.live;
        int x = x0 + 8;
        // ---- control mode
        Ui.section(g, x, y + 2, 190, Component.literal(t("Control")));
        for (int m = 0; m < 4; m++) {
            final int mode = m;
            int col = m >= Live.EXTERNAL ? CYAN : ACCENT;
            button(g, x + (m % 2) * 96, y + 14 + (m / 2) * 18, 94, 16, t(Live.CONTROL_NAMES[m]), col, l.control == m,
                () -> cmd(Commands.op(Commands.CONTROL_MODE).varInt(mode).toBytes()),
                m == Live.INTERNAL ? "The desk drives the fixtures (cue lists, programmer, timeline), AutoShow off"
                    : m == Live.AUTOSHOW ? "The desk with the AutoShow allowed (as before)"
                    : m == Live.EXTERNAL ? "A third-party desk drives the fixtures over Art-Net / sACN" : "A recorded show drives the fixtures");
        }
        // ---- connection
        int cy2 = y + 54;
        Ui.section(g, x, cy2, 190, Component.literal(t("Connection")));
        raw(g, "§7" + clip(es.receiver, 34), x, cy2 + 12, Ui.MUTED);
        button(g, x, cy2 + 24, 94, 14, t("Start receiver"), GREEN, false, () -> ext(com.concertstage.light.ExternalControl.X_MONITOR, null),
            "Opens Art-Net (UDP 6454) and sACN (UDP 5568) on this computer or server");
        button(g, x + 96, cy2 + 24, 94, 14, t("Test signal"), AMBER, es.testing, () -> ext(com.concertstage.light.ExternalControl.X_TEST, null),
            "10 s of Art-Net to 127.0.0.1 through the receiver, to check the whole chain without another program");
        button(g, x, cy2 + 40, 94, 14, "OSC: " + Tr.onOff(es.osc), ACCENT, es.osc, () -> ext(com.concertstage.light.ExternalControl.X_OSC, w2 -> w2.bool(!es.osc)),
            "Accept OSC commands for this desk (port in config/concertstage-external.properties)");
        raw(g, "§8" + clip(es.midi, 18), x + 98, cy2 + 43, Ui.MUTED);
        raw(g, Tr.f("%d packets · %d rejected", es.packets, es.rejected), x, cy2 + 58, Ui.MUTED);
        int uy = cy2 + 70;
        if (es.universes.isEmpty()) raw(g, "§8" + t("No DMX received yet"), x, uy, Ui.MUTED);
        for (int i = 0; i < Math.min(6, es.universes.size()); i++) {
            ExternalStatus.Universe u = es.universes.get(i);
            int c = u.lost() ? RED : GREEN;
            g.fill(x, uy + i * 11 + 1, x + 6, uy + i * 11 + 7, 0xFF000000 | c);
            raw(g, String.format(Locale.ROOT, "U%-2d %s %s", u.universe(), u.lost() ? t("lost") : u.sources() + " src", clip(u.names(), 24)), x + 9, uy + i * 11, u.lost() ? Ui.MUTED : Ui.TEXT);
        }
        if (l.control == Live.EXTERNAL && d.dmx.lost) raw(g, "§c" + t("No signal: holding the last values"), x, y + ch - 12, RED);
        // ---- patch
        int px = x0 + 206;
        Ui.section(g, px, y + 2, 200, Component.literal(t("Patch")));
        int[] sl = sel();
        raw(g, sl.length == 0 ? "§8" + t("Select fixtures (Fixtures tab)") : Tr.f("%d selected", sl.length), px, y + 14, Ui.MUTED);
        // 1.2.1: fixtures still in a layout from before 1.2.0 (hue / saturation, colour macro...): one click moves them to the current
        // layout of the same mode (RGB), at their address when it still fits, else at the next free one
        int legacyN = 0;
        for (Show.Patch p : s.patch) if (com.concertstage.light.dmx.DmxMode.legacy(p.dmxMode)) legacyN++;
        if (legacyN > 0) button(g, px + 100, y + 11, 100, 13, Tr.f("Old modes → RGB (%d)", legacyN), Ui.ORANGE, false, () -> {
            Show sh = Show.fromBytes(desk().show.toBytes());          // a copy: each group is planned after the one before
            if (sh == null) return;
            List<int[]> all = new java.util.ArrayList<>();
            java.util.Map<Integer, List<Integer>> byMode = new java.util.TreeMap<>();
            for (Show.Patch p : sh.patch) if (com.concertstage.light.dmx.DmxMode.legacy(p.dmxMode))
                byMode.computeIfAbsent(com.concertstage.light.dmx.DmxMode.current(p.dmxMode), k -> new java.util.ArrayList<>()).add(p.fid);
            for (var e : byMode.entrySet()) {
                List<int[]> plan = com.concertstage.light.dmx.AutoPatch.changeMode(sh, e.getValue().stream().mapToInt(Integer::intValue).toArray(), e.getKey());
                for (int[] pe : plan) { Show.Patch q = sh.fixture(pe[0]); if (q != null) { q.universe = pe[1]; q.address = pe[2]; q.dmxMode = pe[3]; } }
                all.addAll(plan);
            }
            if (all.isEmpty()) return;
            Bytes.Writer w2 = Commands.op(Commands.PATCH_BULK).varInt(all.size());
            for (int[] pe : all) w2.varInt(pe[0]).varInt(pe[1]).varInt(pe[2]).varInt(pe[3]);
            cmd(w2.toBytes());
        }, "These fixtures still read DMX with a layout from before 1.2.0 (hue / saturation, colour macro): they move to the current RGB layout. Check the new addresses on your console");
        for (int m = 0; m < 4; m++) {
            final int mode = m;
            button(g, px + m * 50, y + 26, 48, 14, t(new String[] {"Full", "Compact", "Ultra", "Matrix"}[m]), CYAN, false, () -> {
                // rev. 17: one patch plan for the whole selection; a fixture that no longer fits at its address moves to the next free
                // place (before, the change was refused at the end of a universe and the auto patch then put the old mode back)
                int[] fids = sel();
                if (fids.length == 0) return;
                List<int[]> plan = com.concertstage.light.dmx.AutoPatch.changeMode(desk().show, fids, mode);
                if (plan.isEmpty()) return;
                Bytes.Writer w2 = Commands.op(Commands.PATCH_BULK).varInt(plan.size());
                for (int[] e : plan) w2.varInt(e[0]).varInt(e[1]).varInt(e[2]).varInt(e[3]);
                cmd(w2.toBytes());
            }, "DMX mode of the selected fixtures (a fixture that no longer fits at its address moves to the next free address)");
        }
        button(g, px, y + 42, 98, 14, t("Invert pan"), ACCENT, false, () -> { for (int f : sel()) { Show.Patch p = desk().show.fixture(f); if (p != null) cmd(patchDmx(p, p.universe, p.address, p.dmxMode, !p.invertPan, p.invertTilt)); } },
            "Local transform: the same DMX turns these fixtures the other way (no extra DMX control)");
        button(g, px + 102, y + 42, 98, 14, t("Invert tilt"), ACCENT, false, () -> { for (int f : sel()) { Show.Patch p = desk().show.fixture(f); if (p != null) cmd(patchDmx(p, p.universe, p.address, p.dmxMode, p.invertPan, !p.invertTilt)); } });
        button(g, px, y + 58, 98, 14, t("Mirror (right side)"), ACCENT, false, () -> {
            int[] f = sel();
            if (f.length < 2) return;
            double cx = 0;
            for (int i : f) cx += desk().show.fixture(i).x;
            cx /= f.length;
            for (int i : f) { Show.Patch p = desk().show.fixture(i); cmd(patchDmx(p, p.universe, p.address, p.dmxMode, p.x + 0.5 > cx + 0.5, p.invertTilt)); }
        }, "Symmetry: pan inverted for the selected fixtures east of their centre, so shared values open fans");
        button(g, px + 102, y + 58, 98, 14, t("Clear transforms"), ACCENT, false, () -> { for (int f : sel()) { Show.Patch p = desk().show.fixture(f); if (p != null) cmd(Commands.op(Commands.PATCH_DMX).varInt(p.fid).varInt(p.universe).varInt(p.address).varInt(p.dmxMode).bool(false).bool(false).f32(0).f32(0).toBytes()); } });
        // auto patch
        Ui.section(g, px, y + 78, 200, Component.literal(t("AUTO PATCH — 1 UNIVERSE")));
        button(g, px, y + 90, 14, 14, "-", ACCENT, false, () -> extUniverse = Math.max(0, extUniverse - 1));
        raw(g, Tr.f("Universe %d", extUniverse), px + 18, y + 93, Ui.TEXT);
        button(g, px + 80, y + 90, 14, 14, "+", ACCENT, false, () -> extUniverse = Math.min(com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE, extUniverse + 1));
        button(g, px + 100, y + 90, 100, 14, t("Prefer") + ": " + t(com.concertstage.light.dmx.DmxMode.NAMES[extMode]), CYAN, false, () -> extMode = (extMode + 1) % 3,
            "Mode the auto patch starts from; it reduces it type by type only if needed");
        button(g, px, y + 106, 200, 14, t(com.concertstage.light.dmx.AutoPatch.SHARE_NAMES[extShare]), CYAN, extShare > 0, () -> extShare = (extShare + 1) % 4,
            "Individual: one address each · Share by type / group: fixtures get the same values · Virtual Matrix: 16 channels for all");
        button(g, px, y + 122, 200, 16, t("AUTO PATCH — 1 UNIVERSE"), GREEN, false,
            () -> ext(com.concertstage.light.ExternalControl.X_AUTOPATCH, w2 -> w2.varInt(extUniverse).varInt(extMode).varInt(extShare)),
            "Fits the whole patch in this universe (512 channels): modes, sharing, addresses. Undo with the desk undo");
        Map<Integer, Integer> use = com.concertstage.light.dmx.AutoPatch.usage(s);
        StringBuilder us = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : use.entrySet()) { if (us.length() > 0) us.append("  "); us.append("U").append(e.getKey()).append(": ").append(e.getValue()).append("/512"); }
        raw(g, clip(us.length() == 0 ? t("Nothing patched") : us.toString(), 34), px, y + 142, use.values().stream().anyMatch(v -> v > 512) ? RED : Ui.MUTED);
        var conflicts = com.concertstage.light.dmx.AutoPatch.conflicts(s);
        raw(g, conflicts.isEmpty() ? "§a" + t("No address conflict") : "§c" + clip(conflicts.get(0).text(), 34) + (conflicts.size() > 1 ? " (+" + (conflicts.size() - 1) + ")" : ""), px, y + 153, Ui.MUTED);
        // virtual matrix
        Ui.section(g, px, y + 166, 200, Component.literal(t("CSC VIRTUAL MATRIX")));
        Show.External ex = s.external;
        raw(g, Tr.f("U%d.%d-%d", ex.matrixUniverse, ex.matrixAddress, ex.matrixAddress + 15), px, y + 181, Ui.TEXT);
        button(g, px + 70, y + 178, 14, 14, "-", ACCENT, false, () -> cmd(Commands.op(Commands.MATRIX_SETTINGS).varInt(desk().show.external.matrixUniverse).varInt(Math.max(1, desk().show.external.matrixAddress - 16)).varInt(desk().show.external.matrixOrder).toBytes()));
        button(g, px + 86, y + 178, 14, 14, "+", ACCENT, false, () -> cmd(Commands.op(Commands.MATRIX_SETTINGS).varInt(desk().show.external.matrixUniverse).varInt(Math.min(497, desk().show.external.matrixAddress + 16)).varInt(desk().show.external.matrixOrder).toBytes()));
        String[] orders = {"by number", "east-west", "north-south"};
        button(g, px + 104, y + 178, 96, 14, t("Order") + ": " + t(orders[ex.matrixOrder]), CYAN, false, () -> cmd(Commands.op(Commands.MATRIX_SETTINGS).varInt(desk().show.external.matrixUniverse).varInt(desk().show.external.matrixAddress).varInt((desk().show.external.matrixOrder + 1) % 3).toBytes()),
            "Order of the fixtures along the matrix line (phase, chases, gradients)");
        raw(g, "§8" + Tr.f("%d fixtures follow it", com.concertstage.light.dmx.VirtualMatrix.members(s).size()), px, y + 196, Ui.MUTED);
        // ---- export / import
        int ex2 = x0 + 414;
        Ui.section(g, ex2, y + 2, 196, Component.literal(t("Export")));
        for (int k = 0; k < 6; k++) {
            final int kind = k;
            if (k == 1) {   // EXPORT FIXTURE PROFILE: software choice (grandMA3 GDTF / grandMA2 XML / dot2 XML) + export
                final int pk = com.concertstage.light.ExternalExport.PROFILE_KINDS[extProfile];
                int bx = ex2 + 98, by = y + 14;
                button(g, bx, by, 62, 14, t(com.concertstage.light.ExternalExport.NAMES[pk]), CYAN, true,
                    () -> extProfile = (extProfile + 1) % com.concertstage.light.ExternalExport.PROFILE_KINDS.length, profileTip());
                button(g, bx + 64, by, 32, 14, t("Export"), CYAN, false,
                    () -> ext(com.concertstage.light.ExternalControl.X_EXPORT, w2 -> { w2.varInt(pk); Commands.fids(w2, extSelectionOnly ? sel() : new int[0]); }),
                    t("EXPORT FIXTURE PROFILE: every DMX mode of the fixture types, to concertstage/export"));
                continue;
            }
            button(g, ex2 + (k % 2) * 98, y + 14 + (k / 2) * 16, 96, 14, t(com.concertstage.light.ExternalExport.NAMES[k]), k == 4 ? GREEN : CYAN, false,
                () -> ext(com.concertstage.light.ExternalControl.X_EXPORT, w2 -> { w2.varInt(kind); Commands.fids(w2, extSelectionOnly ? sel() : new int[0]); }),
                k == 4 ? "EXPORT COMPLETE LIGHTING RIG: patch, GDTF, grandMA2 / dot2 XML, MVR, 3D models and QLC+ in one folder" : "Writes to concertstage/export");
        }
        button(g, ex2, y + 64, 196, 14, t("Selected fixtures only") + ": " + Tr.onOff(extSelectionOnly), ACCENT, extSelectionOnly, () -> extSelectionOnly = !extSelectionOnly);
        if (!es.lastExport.isEmpty()) raw(g, "§8" + clip(es.lastExport, 34), ex2, y + 81, Ui.MUTED);
        Ui.section(g, ex2, y + 92, 196, Component.literal(t("Import")));
        button(g, ex2 + 124, y + 104, 72, 14, t("Patch CSV"), ACCENT, false, () -> ext(com.concertstage.light.ExternalControl.X_IMPORT_PATCH, w2 -> w2.str(numberBox.getValue())),
            "Reads concertstage/import/<name>.csv (as exported): universe, address and mode by fixture number");
        button(g, ex2 + 124, y + 120, 72, 14, t("MVR"), ACCENT, false, () -> ext(com.concertstage.light.ExternalControl.X_IMPORT_MVR, w2 -> w2.str(numberBox.getValue())),
            "Reads concertstage/import/<name>.mvr: address and mode of the fixtures with the same Fixture ID");
        // ---- recording
        Ui.section(g, ex2, y + 138, 196, Component.literal(t("Recording")));
        if (es.recording) {
            button(g, ex2 + 124, y + 150, 72, 14, "■ " + t("Stop"), RED, true, () -> ext(com.concertstage.light.ExternalControl.X_REC_STOP, null));
            raw(g, String.format(Locale.ROOT, "§c● %s  %s  %d fr  %d kB", clip(es.recordName, 10), time(es.recordMs), es.recordFrames, es.recordBytes / 1024), ex2, y + 167, RED);
        } else {
            button(g, ex2 + 124, y + 150, 72, 14, "● " + t("Record"), RED, false, () -> ext(com.concertstage.light.ExternalControl.X_REC_START,
                w2 -> w2.str(nameBox.getValue().isBlank() ? "Recording" : nameBox.getValue())),
                "CSC RECORD EXTERNAL SHOW: captures the DMX the desk receives (its universes), with the time, to concertstage/recordings");
        }
        // ---- playback
        Ui.section(g, ex2, y + 178, 196, Component.literal(t("Playback")));
        int ly = y + 190;
        for (int i = 0; i < Math.min(es.loaded ? 2 : 3, es.library.size()); i++) {
            String[] e = es.library.get(i);
            boolean on = es.loaded && e[0].equals(es.playerName);
            button(g, ex2, ly + i * 14, 150, 13, clip(e[0], 20) + "  §8" + e[1], CYAN, on, () -> ext(com.concertstage.light.ExternalControl.X_LOAD, w2 -> w2.str(e[0])));
            button(g, ex2 + 152, ly + i * 14, 44, 13, t("Delete"), RED, false, () -> ext(com.concertstage.light.ExternalControl.X_DELETE, w2 -> w2.str(e[0])));
        }
        if (es.library.isEmpty()) raw(g, "§8" + t("No recording yet"), ex2, ly + 2, Ui.MUTED);
        int ty = y + ch - 30;
        if (es.loaded) {
            int bw = 30;
            button(g, ex2, ty, bw, 14, "▶", GREEN, es.playing, () -> ext(com.concertstage.light.ExternalControl.X_PLAY, null), "Play (switches the desk to Playback)");
            button(g, ex2 + 32, ty, bw, 14, "❚❚", AMBER, !es.playing, () -> ext(com.concertstage.light.ExternalControl.X_PAUSE, null));
            button(g, ex2 + 64, ty, bw, 14, "■", RED, false, () -> ext(com.concertstage.light.ExternalControl.X_STOP, null));
            button(g, ex2 + 96, ty, bw, 14, "⟲", ACCENT, false, () -> ext(com.concertstage.light.ExternalControl.X_RESTART, null), "Restart");
            button(g, ex2 + 128, ty, 32, 14, t("Loop"), ACCENT, es.loop, () -> ext(com.concertstage.light.ExternalControl.X_LOOP, w2 -> w2.bool(!es.loop)));
            button(g, ex2 + 162, ty, 34, 14, String.format(Locale.ROOT, "%.2gx", es.speed), ACCENT, false,
                () -> ext(com.concertstage.light.ExternalControl.X_SPEED, w2 -> w2.f32(es.speed >= 2f ? 0.5f : es.speed * 2f)), "Playback speed 0.5x / 1x / 2x");
            int by2 = ty + 17;
            double f = es.durationMs <= 0 ? 0 : es.positionMs / (double) es.durationMs;
            Ui.gauge(g, ex2, by2, 196, 5, f, CYAN, -1);
            hits.add(new Hit(ex2, by2 - 3, 196, 11, b -> { }, (mx, my, rel) -> {
                if (rel) ext(com.concertstage.light.ExternalControl.X_SEEK, w2 -> w2.varLong((long) (Math.max(0, Math.min(1, (mx - ex2) / 196.0)) * es.durationMs)));
            }, null, Component.literal(t("Seek"))));
            raw(g, time(es.positionMs) + " / " + time(es.durationMs), ex2, by2 + 8, Ui.MUTED);
            button(g, ex2 + 96, by2 + 6, 48, 12, t("Music") + " " + Tr.onOff(es.syncMusic), ACCENT, es.syncMusic, () -> ext(com.concertstage.light.ExternalControl.X_SYNC, w2 -> w2.bool(!es.syncMusic)),
                es.trackName.isEmpty() ? "Recorded without music" : "Follow the linked music (" + es.trackName + "): no drift");
            button(g, ex2 + 146, by2 + 6, 50, 12, "MTC " + Tr.onOff(es.syncMtc), ACCENT, es.syncMtc, () -> ext(com.concertstage.light.ExternalControl.X_SYNC_MTC, w2 -> w2.bool(!es.syncMtc)),
                "Follow the MIDI timecode (device in config/concertstage-external.properties)");
        }
    }

    private static byte[] patchDmx(Show.Patch p, int u, int a, int mode, boolean ip, boolean it) {
        return Commands.op(Commands.PATCH_DMX).varInt(p.fid).varInt(u).varInt(a).varInt(mode).bool(ip).bool(it).f32(p.panOffset).f32(p.tiltOffset).toBytes();
    }

    // ------------------------------------------------------------ Settings > Network > Art-Net (rev. 16h)
    private static int netPage = 0;                       // 0 Art-Net settings, 1 Art-Net monitor, 2 DMX monitor
    private static int monUniverse = 0, monPage = 0;
    private int netTicks;
    /** The settings being edited (from the server when the page opens; sent with APPLY / SAVE). */
    private com.concertstage.light.dmx.ArtNetSettings draft;
    private int mapTarget = 0, rangeStart = 0, rangeEnd = 15;

    private EditBox netBox(int x, int y, int bw, String hint) {
        EditBox b = new EditBox(font, x, y, bw, 14, Component.literal(hint));
        b.setMaxLength(15);
        b.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        addRenderableWidget(b);
        return b;
    }

    private void fillBoxes() {
        if (ipBox != null) ipBox.setValue(draft.bind);
        if (portBox != null) portBox.setValue(String.valueOf(draft.port));
        if (srcBox != null) srcBox.setValue(draft.sourceIp);
        int a = draft.rangeStart(), b = draft.rangeEnd();
        if (a != com.concertstage.light.dmx.ArtNetSettings.NONE) { rangeStart = a; rangeEnd = b; }
    }

    /** The draft with the text boxes read in (null and a message when a box is wrong). */
    private com.concertstage.light.dmx.ArtNetSettings fromBoxes() {
        com.concertstage.light.dmx.ArtNetSettings a = draft.copy();
        if (ipBox != null) a.bind = ipBox.getValue().trim();
        if (portBox != null) {
            try { a.port = Integer.parseInt(portBox.getValue().trim()); } catch (NumberFormatException e) { netLocal = t("UDP port must be a number"); return null; }
        }
        if (srcBox != null) a.sourceIp = srcBox.getValue().trim();
        String bad = a.validate();
        if (bad != null) { netLocal = bad; return null; }
        netLocal = "";
        return a;
    }

    private String netLocal = "";

    private void sendNet(int op) {
        com.concertstage.light.dmx.ArtNetSettings a = fromBoxes();
        if (a == null) return;
        draft = a;
        ext(op, a::write);
    }

    private static final int[] STATE_COLOR = {0xFF8A9099, 0xFFF2B33D, 0xFF4CD08A, 0xFFF2B33D, 0xFFE5484D, 0xFFE5484D, 0xFFFF8A3D};

    private void settings(GuiGraphics g, LumicoreBlockEntity d, int y, int ch) {
        ExternalStatus es = ExternalStatus.of(pos);
        int x = x0 + 8;
        Ui.section(g, x, y + 2, 88, Component.literal(t("Settings")));
        raw(g, "§8" + t("NETWORK"), x, y + 15, Ui.MUTED);
        String[] pages = {"ART-NET", "ART-NET MONITOR", "DMX MONITOR"};
        for (int i = 0; i < pages.length; i++) {
            final int pg = i;
            button(g, x, y + 26 + i * 18, 88, 16, t(pages[i]), CYAN, netPage == i, () -> { if (netPage != pg) { netPage = pg; rebuildWidgets(); if (pg == 2) ext(com.concertstage.light.ExternalControl.X_DMX_MON, w2 -> w2.varInt(monUniverse)); } });
        }
        // state chip
        int stc = es.hasArt ? STATE_COLOR[Math.max(0, Math.min(6, es.artState))] : Ui.MUTED;
        String stn = es.hasArt ? t(new String[] {"DISABLED", "LISTENING", "RECEIVING", "NO DATA", "BIND ERROR", "INVALID PACKET", "SOURCE FILTERED"}[Math.max(0, Math.min(6, es.artState))]) : t("waiting for the server");
        g.fill(x, y + 86, x + 6, y + 92, 0xFF000000 | stc);
        raw(g, clip(stn, 14), x + 9, y + 85, stc);
        if (es.hasArt && !es.listening.isEmpty()) raw(g, "§8" + clip(es.listening, 15), x, y + 97, Ui.MUTED);
        if (!es.hasArt) { raw(g, "§8" + t("No answer from the server yet"), x0 + 104, y + 4, Ui.MUTED); return; }
        if (draft == null) { draft = es.art.copy(); fillBoxes(); }
        switch (netPage) {
            case 0 -> artnetSettings(g, d, es, y, ch);
            case 1 -> artnetMonitor(g, d, es, y, ch);
            default -> dmxMonitor(g, d, es, y, ch);
        }
    }

    private void artnetSettings(GuiGraphics g, LumicoreBlockEntity d, ExternalStatus es, int y, int ch) {
        int ax = x0 + 104, bx = x0 + 312;
        com.concertstage.light.dmx.ArtNetSettings a = draft;
        // ---- column A: reception, addresses, policy
        Ui.section(g, ax, y + 2, 200, Component.literal(t("ART-NET NETWORK SETTINGS")));
        button(g, ax, y + 13, 98, 14, "Art-Net: " + Tr.onOff(a.enabled), GREEN, a.enabled, () -> draft.enabled = !draft.enabled, "Receive Art-Net on this computer or server");
        raw(g, t("Local IP"), ax, y + 33, Ui.MUTED);
        raw(g, "§8" + t("Interfaces (click to use)"), ax, y + 47, Ui.MUTED);
        List<ExternalStatus.NetIf> ifs = new ArrayList<>(es.interfaces);
        int iy = y + 57;
        button(g, ax, iy, 200, 12, clip(t("All interfaces") + "  0.0.0.0", 34), ACCENT, "0.0.0.0".equals(ipBox == null ? a.bind : ipBox.getValue().trim()),
            () -> { if (ipBox != null) ipBox.setValue("0.0.0.0"); }, "Listen on every IPv4 interface of this computer");
        for (int i = 0; i < Math.min(4, ifs.size()); i++) {
            ExternalStatus.NetIf n = ifs.get(i);
            String lab = (n.up() ? "§a● §r" : "§7○ §r") + clip(n.name(), 18) + "  " + n.address();
            boolean on = n.address().equals(ipBox == null ? a.bind : ipBox.getValue().trim());
            button(g, ax, iy + 13 + i * 13, 200, 12, lab, ACCENT, on, () -> { if (ipBox != null) ipBox.setValue(n.address()); },
                n.up() ? (n.loopback() ? "Loopback: this computer only" : "Active interface") : "Interface down");
        }
        if (ifs.isEmpty()) raw(g, "§8" + t("REFRESH to list the interfaces"), ax, iy + 15, Ui.MUTED);
        raw(g, t("UDP port"), ax, y + 129, Ui.MUTED);
        button(g, ax + 122, y + 125, 78, 14, t("Default 6454"), ACCENT, false, () -> { if (portBox != null) portBox.setValue("6454"); });
        button(g, ax, y + 141, 98, 14, a.anySource ? t("ANY SOURCE") : t("SPECIFIC SOURCE"), AMBER, !a.anySource, () -> draft.anySource = !draft.anySource,
            "Source filter: every sender, or only the console IP on the right (the others are counted as filtered)");
        if (srcBox != null) srcBox.setEditable(!a.anySource);
        raw(g, "§8" + t("Console IP (optional)"), ax + 102, y + 157, Ui.MUTED);
        button(g, ax, y + 168, 200, 14, t("Priority") + ": " + t(com.concertstage.light.dmx.ArtNetSettings.PRIORITY_NAMES[a.priority]), CYAN, a.priority == 1,
            () -> draft.priority = 1 - draft.priority,
            "Desk mode decides: Art-Net drives a desk only in External. Art-Net takes control: a desk goes to External as soon as Art-Net arrives on its universes");
        button(g, ax, y + 184, 200, 14, t("Signal lost") + ": " + t(com.concertstage.light.dmx.ArtNetSettings.LOSS_NAMES[a.loss]), CYAN, a.loss != 0,
            () -> draft.loss = (draft.loss + 1) % 3, "What a desk in External does after 3 s without Art-Net on its universes");
        if (es.dedicated) button(g, ax, y + 200, 200, 14, t("Allowed on this dedicated server") + ": " + Tr.onOff(a.allowDedicated), RED, a.allowDedicated,
            () -> draft.allowDedicated = !draft.allowDedicated, "Opens the UDP port on the server machine (server operators of level 4)");
        // ---- column B: universes
        Ui.section(g, bx, y + 2, 300, Component.literal(t("ART-NET UNIVERSES (Port-Address)")));
        raw(g, t("Start"), bx, y + 16, Ui.MUTED);
        button(g, bx + 30, y + 13, 14, 14, "-", ACCENT, false, () -> rangeStart = Math.max(0, rangeStart - 1));
        raw(g, String.valueOf(rangeStart), bx + 48, y + 16, Ui.TEXT);
        button(g, bx + 68, y + 13, 14, 14, "+", ACCENT, false, () -> rangeStart = Math.min(rangeEnd, rangeStart + 1));
        raw(g, t("End"), bx + 88, y + 16, Ui.MUTED);
        button(g, bx + 110, y + 13, 14, 14, "-", ACCENT, false, () -> rangeEnd = Math.max(rangeStart, rangeEnd - 1));
        raw(g, String.valueOf(rangeEnd), bx + 128, y + 16, Ui.TEXT);
        button(g, bx + 148, y + 13, 14, 14, "+", ACCENT, false, () -> rangeEnd = Math.min(32767, rangeEnd + 1));
        button(g, bx + 166, y + 13, 70, 14, t("Select range"), GREEN, false, () -> draft.range(rangeStart, rangeEnd),
            "Universe Start / Universe End: receive exactly these Port-Addresses (0 is a normal universe)");
        button(g, bx + 238, y + 13, 62, 14, t("Clear"), RED, false, () -> draft.selected.clear());
        // grid of Port-Addresses 0..63
        java.util.Map<Integer, Long> age = new java.util.HashMap<>();
        for (ExternalStatus.Port pt : es.ports) age.put(pt.portAddress(), pt.ageMs());
        for (int pa = 0; pa < 64; pa++) {
            final int fpa = pa;
            int gx = bx + (pa % 16) * 18 + 2, gy = y + 31 + (pa / 16) * 14;
            boolean on = a.selected.get(pa);
            Long ag = age.get(pa);
            boolean live = ag != null && ag >= 0 && ag < 3000;
            int col = live ? GREEN : CYAN;
            button(g, gx, gy, 17, 13, String.valueOf(pa), col, on, () -> { if (draft.selected.get(fpa)) draft.selected.clear(fpa); else draft.selected.set(fpa); },
                Tr.f("Universe %d (Art-Net %s)", pa, com.concertstage.light.dmx.ArtNetSettings.netSubUni(pa)) + " → CSC " + csc(pa) + (live ? " · " + t("receiving") : ""));
        }
        int above = a.selected.cardinality() - a.selected.get(0, 64).cardinality();
        if (above > 0) raw(g, "§8" + Tr.f("+%d selected above 63", above), bx + 2, y + 88, Ui.MUTED);
        // mapping
        Ui.section(g, bx, y + 98, 300, Component.literal(t("UNIVERSE MAPPING (Port-Address → CSC universe)")));
        int my = y + 110, row = 0;
        if (a.mapping.isEmpty()) raw(g, "§8" + t("Direct: Port-Address N drives CSC universe N (0 → 0, 1 → 1, no +1)"), bx + 2, my + 2, Ui.MUTED);
        for (java.util.Map.Entry<Integer, Integer> e : new ArrayList<>(a.mapping.entrySet())) {
            if (row >= 4) { raw(g, "§8" + Tr.f("+%d more", a.mapping.size() - 4), bx + 230, my + 4 * 14 + 2, Ui.MUTED); break; }
            int ry = my + row * 14;
            final int pa = e.getKey();
            raw(g, String.format(Locale.ROOT, "Art-Net %d (%s)  →  CSC %d", pa, com.concertstage.light.dmx.ArtNetSettings.netSubUni(pa), e.getValue()), bx + 2, ry + 3, Ui.TEXT);
            button(g, bx + 210, ry, 14, 12, "-", ACCENT, false, () -> draft.mapping.computeIfPresent(pa, (k, v) -> Math.max(0, v - 1)));
            button(g, bx + 226, ry, 14, 12, "+", ACCENT, false, () -> draft.mapping.computeIfPresent(pa, (k, v) -> Math.min(com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE, v + 1)));
            button(g, bx + 244, ry, 56, 12, t("Remove"), RED, false, () -> draft.mapping.remove(pa));
            row++;
        }
        raw(g, t("Port-Address"), bx, my + 4 * 14 + 8, Ui.MUTED);
        raw(g, "→ CSC", bx + 112, my + 4 * 14 + 8, Ui.MUTED);
        button(g, bx + 144, my + 4 * 14 + 4, 14, 14, "-", ACCENT, false, () -> mapTarget = Math.max(0, mapTarget - 1));
        raw(g, String.valueOf(mapTarget), bx + 162, my + 4 * 14 + 8, Ui.TEXT);
        button(g, bx + 180, my + 4 * 14 + 4, 14, 14, "+", ACCENT, false, () -> mapTarget = Math.min(com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE, mapTarget + 1));
        button(g, bx + 198, my + 4 * 14 + 4, 102, 14, t("Add mapping"), GREEN, false, () -> {
            try {
                int pa = Integer.parseInt(mapBox.getValue().trim());
                if (pa >= 0 && pa <= 32767) draft.mapping.put(pa, mapTarget);
                else netLocal = t("Port-Address must be 0 to 32767");
            } catch (RuntimeException e) { netLocal = t("Port-Address must be a number"); }
        });
        button(g, bx, my + 4 * 14 + 22, 148, 14, t("Direct (0 → 0)"), CYAN, a.mapping.isEmpty(), () -> draft.mapping.clear(),
            "Default: the Art-Net Port-Address is the CSC universe number");
        button(g, bx + 152, my + 4 * 14 + 22, 148, 14, t("Port-Address N → CSC N+1"), CYAN, false, () -> {
            draft.mapping.clear();
            for (int pa = draft.selected.nextSetBit(0); pa >= 0 && pa < 64; pa = draft.selected.nextSetBit(pa + 1)) draft.mapping.put(pa, pa + 1);
        }, "For shows patched with the old rule (Art-Net 0 = CSC universe 1)");
        // ---- buttons
        int by2 = y + ch - 18;
        String[] labels = {"APPLY", "SAVE", "RESET", "REFRESH", "TEST CONNECTION"};
        int[] ops = {com.concertstage.light.ExternalControl.X_NET_APPLY, com.concertstage.light.ExternalControl.X_NET_SAVE, com.concertstage.light.ExternalControl.X_NET_RESET,
            com.concertstage.light.ExternalControl.X_NET_REFRESH, com.concertstage.light.ExternalControl.X_NET_TEST};
        String[] tips = {"Use these settings now (no restart); the previous ones come back if the address cannot be opened",
            "Apply and keep these settings (config/concertstage-external.properties)", "Back to the defaults (0.0.0.0:6454, universes 0-15, direct mapping) and save",
            "Reload the settings and the interface list from the server", "Sends an ArtPoll (no DMX) to the receiver and checks that it arrives"};
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            int bw = i == 4 ? 112 : 94;
            int bxx = ax + i * 96;
            button(g, bxx, by2, bw, 16, t(labels[i]), i == 0 || i == 1 ? GREEN : i == 2 ? RED : CYAN, false, () -> {
                switch (k) {
                    case 0, 1 -> sendNet(ops[k]);
                    case 2 -> { ext(ops[k], null); draft = null; }
                    case 3 -> { ext(ops[k], null); draft = null; rebuildWidgets(); }
                    default -> ext(ops[k], null);
                }
            }, tips[i]);
        }
        String report = !netLocal.isEmpty() ? "§c" + netLocal : es.netReport.isEmpty() ? (es.bindError.isEmpty() ? "" : "§c" + es.bindError) : es.netReport;
        raw(g, clip(report, 84), ax, by2 - 12, es.netReport.startsWith("BIND") || es.netReport.startsWith("Not") ? RED : Ui.MUTED);
    }

    /** CSC universe a Port-Address goes to with the draft settings ("—" when it is not received). */
    private String csc(int pa) {
        int u = draft.cscUniverse(pa);
        return u == com.concertstage.light.dmx.ArtNetSettings.NONE ? "—" : String.valueOf(u);
    }

    private void artnetMonitor(GuiGraphics g, LumicoreBlockEntity d, ExternalStatus es, int y, int ch) {
        int ax = x0 + 104;
        com.concertstage.light.dmx.ArtNetSettings a = es.art;
        Ui.section(g, ax, y + 2, 504, Component.literal(t("CSC ART-NET MONITOR")));
        String[] states = {"DISABLED", "LISTENING", "RECEIVING", "NO DATA", "BIND ERROR", "INVALID PACKET", "SOURCE FILTERED"};
        int st = Math.max(0, Math.min(6, es.artState));
        String[][] rows = {
            {"ART-NET RECEIVER", t(states[st])},
            {"Local IP", a.bind + (es.listening.isEmpty() ? "" : "  (" + es.listening + ")")},
            {"Source Filter", a.anySource ? "ANY" : a.sourceIp},
            {"UDP Port", String.valueOf(a.port)},
            {"Detected Source", es.lastSource.isEmpty() ? "—" : es.lastSource},
            {"Protocol", "Art-Net (ArtDmx, Port-Address 15 bits)"},
            {"Packets Received", String.valueOf(es.artPackets)},
            {"Packet Rate", es.artRate + " packets/s"},
            {"DMX packets used", String.valueOf(es.artAccepted)},
            {"Invalid packets", es.artInvalid + (es.lastInvalid.isEmpty() ? "" : "  · " + es.lastInvalid)},
            {"Filtered (source)", es.artFiltered + (es.lastFiltered.isEmpty() ? "" : "  · " + es.lastFiltered)},
            {"Not selected", es.artNotSelected + "  (" + t("valid packets of Port-Addresses not received") + ")"},
            {"Last DMX", es.lastAcceptedAgo < 0 ? "—" : es.lastAcceptedAgo + " ms ago"},
        };
        for (int i = 0; i < rows.length; i++) {
            raw(g, t(rows[i][0]), ax, y + 15 + i * 11, Ui.MUTED);
            raw(g, clip(rows[i][1], 40), ax + 110, y + 15 + i * 11, i == 0 ? STATE_COLOR[st] : Ui.TEXT);
        }
        if (!es.bindError.isEmpty()) raw(g, "§c" + clip(es.bindError, 70), ax, y + 15 + rows.length * 11 + 2, RED);
        // universes: the selected / mapped Port-Addresses and every one seen
        int ux = ax + 312;
        raw(g, "§7" + t("Universe") + "       CSC   " + t("State") + "     " + t("Packets"), ux, y + 15, Ui.MUTED);
        java.util.TreeSet<Integer> list = new java.util.TreeSet<>();
        for (int pa = a.selected.nextSetBit(0); pa >= 0 && list.size() < 64; pa = a.selected.nextSetBit(pa + 1)) list.add(pa);
        list.addAll(a.mapping.keySet());
        java.util.Map<Integer, ExternalStatus.Port> seen = new java.util.HashMap<>();
        for (ExternalStatus.Port pt : es.ports) { seen.put(pt.portAddress(), pt); list.add(pt.portAddress()); }
        int active = 0;
        for (ExternalStatus.Port pt : es.ports) if (pt.ageMs() >= 0 && pt.ageMs() < 3000) active++;
        raw(g, Tr.f("Active Universes %d", active), ux, y + 4 - 2 + 0, Ui.MUTED);
        // prefer the active ones first
        List<Integer> order = new ArrayList<>(list);
        order.sort((p1, p2) -> {
            ExternalStatus.Port s1 = seen.get(p1), s2 = seen.get(p2);
            boolean l1 = s1 != null && s1.ageMs() >= 0 && s1.ageMs() < 3000, l2 = s2 != null && s2.ageMs() >= 0 && s2.ageMs() < 3000;
            return l1 != l2 ? (l1 ? -1 : 1) : Integer.compare(p1, p2);
        });
        int maxRows = (ch - 30) / 11;
        for (int i = 0; i < Math.min(maxRows, order.size()); i++) {
            int pa = order.get(i);
            ExternalStatus.Port pt = seen.get(pa);
            boolean live = pt != null && pt.ageMs() >= 0 && pt.ageMs() < 3000;
            int u = a.cscUniverse(pa);
            String stt = u == com.concertstage.light.dmx.ArtNetSettings.NONE ? (pt != null ? t("IGNORED") : "—") : live ? t("ACTIVE") : t("NO DATA");
            int col = u == com.concertstage.light.dmx.ArtNetSettings.NONE ? Ui.MUTED : live ? GREEN : AMBER;
            raw(g, String.format(Locale.ROOT, "%-5d %-7s %-5s %-9s %d", pa, com.concertstage.light.dmx.ArtNetSettings.netSubUni(pa),
                u == com.concertstage.light.dmx.ArtNetSettings.NONE ? "—" : String.valueOf(u), stt, pt == null ? 0 : pt.packets()), ux, y + 27 + i * 11, col);
        }
        // sACN universes (merge status) under the Art-Net block
        if (!es.universes.isEmpty()) raw(g, "§8" + Tr.f("CSC universes with data: %s", universesText(es)), ax, y + ch - 12, Ui.MUTED);
    }

    private static String universesText(ExternalStatus es) {
        StringBuilder b = new StringBuilder();
        for (ExternalStatus.Universe u : es.universes) b.append(b.length() == 0 ? "" : ", ").append(u.universe()).append(u.lost() ? "×" : "");
        return b.toString();
    }

    private void dmxMonitor(GuiGraphics g, LumicoreBlockEntity d, ExternalStatus es, int y, int ch) {
        int ax = x0 + 104;
        Ui.section(g, ax, y + 2, 504, Component.literal(Tr.f("DMX MONITOR — UNIVERSE %d", monUniverse)));
        button(g, ax, y + 13, 14, 14, "-", ACCENT, false, () -> { monUniverse = Math.max(0, monUniverse - 1); ext(com.concertstage.light.ExternalControl.X_DMX_MON, w2 -> w2.varInt(monUniverse)); });
        raw(g, Tr.f("CSC universe %d", monUniverse), ax + 18, y + 16, Ui.TEXT);
        button(g, ax + 100, y + 13, 14, 14, "+", ACCENT, false, () -> { monUniverse = Math.min(com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE, monUniverse + 1); ext(com.concertstage.light.ExternalControl.X_DMX_MON, w2 -> w2.varInt(monUniverse)); });
        int pa = es.art.portAddressOf(monUniverse);
        raw(g, "§8" + (pa == com.concertstage.light.dmx.ArtNetSettings.NONE ? t("no Art-Net Port-Address feeds it") : Tr.f("from Art-Net Port-Address %d (%s)", pa, com.concertstage.light.dmx.ArtNetSettings.netSubUni(pa))), ax + 120, y + 16, Ui.MUTED);
        boolean mine = es.hasMonitor || es.monUniverse == monUniverse;
        String state = !mine || !es.monData ? t("NO DATA") : es.monLost ? t("LOST (last values)") : t("RECEIVING");
        raw(g, t("Packets") + ": " + state + "   " + t("Last Update") + ": " + (es.monAgeMs < 0 || !mine ? "—" : es.monAgeMs + " ms ago"), ax + 300, y + 16, !mine || !es.monData ? AMBER : es.monLost ? RED : GREEN);
        for (int p = 0; p < 4; p++) {
            final int pp = p;
            button(g, ax + p * 64, y + 30, 62, 13, (p * 128 + 1) + "-" + (p * 128 + 128), ACCENT, monPage == p, () -> monPage = pp);
        }
        raw(g, "§8" + t("amber: changed · hover: fixture"), ax + 262, y + 33, Ui.MUTED);
        byte[] v = es.monValues, pv = es.prevUniverse == es.monUniverse ? es.prevValues : es.monValues;
        int cw = 31, chh = 24;
        for (int i = 0; i < 128; i++) {
            int c = monPage * 128 + i;                 // 0-based slot
            int cx = ax + (i % 16) * cw, cy = y + 47 + (i / 16) * chh;
            int val = mine && es.monData ? v[c] & 255 : 0;
            boolean changed = mine && es.monData && (pv[c] & 255) != val;
            g.fill(cx, cy, cx + cw - 2, cy + chh - 2, 0xFF15181C);
            int bh = Math.round((chh - 4) * val / 255f);
            g.fill(cx + 1, cy + chh - 3 - bh, cx + cw - 3, cy + chh - 3, changed ? 0x66F2B33D : 0x5535C9E8);
            raw(g, "§8" + String.format(Locale.ROOT, "%03d", c + 1), cx + 2, cy + 2, Ui.MUTED);
            raw(g, String.format(Locale.ROOT, "%3d", val), cx + 8, cy + 12, changed ? AMBER : Ui.TEXT);
            String who = fixtureAt(d, monUniverse, c + 1);
            hit(cx, cy, cw - 2, chh - 2, null, null, null, Tr.f("Channel %03d = %d", c + 1, val) + (who.isEmpty() ? "" : " · " + who));
        }
    }

    /** Fixture and channel name at a slot of a universe, from the patch of the desk ("" when none). */
    private static String fixtureAt(LumicoreBlockEntity d, int universe, int address) {
        StringBuilder out = new StringBuilder();
        for (Show.Patch p : d.show.patch) {
            if (p.dmxMode == com.concertstage.light.dmx.DmxMode.MATRIX) {
                if (d.show.external.matrixUniverse == universe && address >= d.show.external.matrixAddress && address < d.show.external.matrixAddress + 16) {
                    out.append(out.length() == 0 ? "" : ", ").append("Virtual Matrix: ").append(com.concertstage.light.dmx.VirtualMatrix.CHANNEL_NAMES[address - d.show.external.matrixAddress]);
                    break;
                }
                continue;
            }
            if (p.universe != universe) continue;
            com.concertstage.light.dmx.DmxMode m = com.concertstage.light.dmx.DmxMode.of(p.profile(), p.dmxMode);
            int k = address - p.address;
            if (k >= 0 && k < m.channels.length) {
                if (out.length() > 0) out.append(", ");
                out.append(p.name).append(": ").append(m.channels[k].name());
                if (out.length() > 120) break;
            }
        }
        return out.toString();
    }

    /** rev. 1: left edge of the Access column of the Shows page (the Player box of init() uses it too). */
    private int showsAccessX() {
        return x0 + w - 166;
    }

    private void shows(GuiGraphics g, LumicoreBlockEntity d, int y) {
        int x = x0 + 8;
        raw(g, t("Current show") + ": §f" + d.show.name, x + 170, y + 3, Ui.MUTED);
        button(g, x, y + 18, 78, 16, t("Rename"), ACCENT, false, () -> { if (!nameBox.getValue().isBlank()) cmd(Commands.op(Commands.SHOW_NAME).str(nameBox.getValue()).toBytes()); });
        button(g, x + 82, y + 18, 78, 16, t("Save"), GREEN, false, () -> action(LightNet.A_LIB_SAVE, w2 -> w2.str(nameBox.getValue().isBlank() ? desk().show.name : nameBox.getValue())),
            "Saves the current show in the desk library under the name above");
        button(g, x + 164, y + 18, 90, 16, t("New show"), AMBER, false, () -> action(LightNet.A_LIB_NEW, null), "Empty show, same patch");
        // rev. 1: three columns that no longer overlap, placed from the right (access 158, files 150, the library takes the rest: 280
        // in a 620 wide desk, less at a large GUI scale), see showsAccessX
        int ix = showsAccessX() - 158, lw = ix - 8 - x;
        Ui.section(g, x, y + 42, lw, Component.literal(t("Desk library") + " (" + d.library.size() + "/" + LumicoreBlockEntity.MAX_LIBRARY + ")"));
        int i = 0;
        for (String n : d.library.keySet()) {
            int ry = y + 56 + i * 18;
            raw(g, clip(n, Math.max(4, (lw - 154) / 6)), x, ry + 4, Ui.TEXT);
            button(g, x + lw - 152, ry, 46, 16, t("Load"), GREEN, false, () -> action(LightNet.A_LIB_LOAD, w2 -> w2.str(n)));
            button(g, x + lw - 104, ry, 50, 16, t("Copy"), ACCENT, false, () -> action(LightNet.A_LIB_DUPLICATE, w2 -> w2.str(n)));
            button(g, x + lw - 52, ry, 50, 16, t("Delete"), RED, false, () -> action(LightNet.A_LIB_DELETE, w2 -> w2.str(n)));
            // rev. 1: in a narrow desk (large GUI scale) the list stops above the settings moved to the left (see narrow below)
            if (++i >= (w < 612 ? Math.max(1, Math.min(12, (h - 90 - 4 - 84) / 18)) : 12)) break;
        }
        // import / export
        Ui.section(g, ix, y + 42, 150, Component.literal(t("Files")));
        button(g, ix, y + 56, 150, 16, t("Export current show"), CYAN, false, this::exportShow, "Writes .minecraft/concertstage/shows/<name>.cscshow");
        List<Path> files = new ArrayList<>();
        try (var st = Files.list(showsDir())) { st.filter(p -> p.toString().endsWith(".cscshow")).limit(8).forEach(files::add); } catch (Exception ignored) { }
        for (int k = 0; k < files.size(); k++) {
            if (y + 76 + k * 17 + 15 > y0 + h - 92) break;          // rev. 1: never over the brightness buttons (narrow desk)
            Path p = files.get(k);
            String n = p.getFileName().toString().replace(".cscshow", "");
            button(g, ix, y + 76 + k * 17, 150, 15, "⇪ " + clip(n, 19), ACCENT, false, () -> importShow(p), "Imports this file into the desk library");
        }
        if (files.isEmpty()) {
            // rev. 1: wrapped in the column (it ran under the Access column, picture of 12 November)
            int ly = y + 78;
            for (var line : font.split(Component.literal(t("No .cscshow file in the shows folder")), 150)) {
                g.drawString(font, line, ix, ly, 0xFF6E6E78, false);
                ly += 10;
            }
        }
        // permissions and autosave
        int px = showsAccessX();
        Ui.section(g, px, y + 42, 158, Component.literal(t("Access")));
        raw(g, t("Owner") + ": §f" + (d.ownerName.isEmpty() ? "-" : d.ownerName), px, y + 56, Ui.MUTED);
        button(g, px, y + 68, 158, 16, t("Public desk") + ": " + Tr.onOff(d.publicAccess), AMBER, d.publicAccess, () -> action(LightNet.A_PUBLIC, w2 -> w2.bool(!desk().publicAccess)),
            "Public: every player may use it. Otherwise the owner, the operators and the server operators");
        int oy = y + 88;
        for (Map.Entry<java.util.UUID, String> e : d.operators.entrySet()) {
            raw(g, clip(e.getValue(), 20), px, oy + 3, Ui.TEXT);
            button(g, px + 138, oy, 20, 14, "✕", RED, false, () -> action(LightNet.A_OPERATOR_REMOVE, w2 -> w2.str(e.getValue())));
            oy += 16;
            if (oy > y0 + h - 166) break;                          // rev. 1: stops above the Player box
        }
        // rev. 1: player box, Add and autosave moved up: the autosave buttons were drawn over the Beams / Stage light / Lens glow
        // buttons and its label under Rig light (picture of 12 November)
        button(g, px + 114, y0 + h - 146, 44, 14, t("Add"), GREEN, false, () -> { if (!numberBox.getValue().isBlank()) action(LightNet.A_OPERATOR_ADD, w2 -> w2.str(numberBox.getValue())); },
            "Gives a player the right to use this desk");
        raw(g, t("Autosave") + ": §f" + (d.autosaveMinutes == 0 ? Tr.onOff(false) : d.autosaveMinutes + " min"), px, y0 + h - 124, Ui.MUTED);
        int[] opts = {0, 5, 10, 30};
        for (int k = 0; k < opts.length; k++) {
            final int mins = opts[k];
            button(g, px + k * 40, y0 + h - 113, 38, 14, mins == 0 ? t("OFF") : mins + "m", ACCENT, d.autosaveMinutes == mins, () -> action(LightNet.A_AUTOSAVE, w2 -> w2.varInt(mins)));
        }
        // rev. 17: brightness of the light itself (your screen only): beams in the haze, light on the stage, glow of the lenses
        int bx = x0 + w - 302, by = y0 + h - 73;              // rev. 1: from the right edge (x + 310 in a 620 wide desk, as before)
        button(g, bx, by, 98, 14, t("Beams") + ": " + LightClient.percent(LightClient.beamBrightness), CYAN, false,
            () -> { LightClient.beamBrightness = LightClient.nextBrightness(LightClient.beamBrightness); LightClient.save(); },
            "Brightness of the beams in the haze: 60, 100, 140, 200 % (only on your screen)");
        button(g, bx + 102, by, 98, 14, t("Stage light") + ": " + LightClient.percent(LightClient.stageBrightness), CYAN, false,
            () -> { LightClient.stageBrightness = LightClient.nextBrightness(LightClient.stageBrightness); LightClient.save(); },
            "Brightness of the light the fixtures project on the stage, the truss and the set: 60, 100, 140, 200 % (only on your screen)");
        button(g, bx + 204, by, 90, 14, t("Lens glow") + ": " + LightClient.percent(LightClient.lensGlow), CYAN, false,
            () -> { LightClient.lensGlow = LightClient.nextBrightness(LightClient.lensGlow); LightClient.save(); },
            "Glow around the lit lenses: 60, 100, 140, 200 % (only on your screen)");
        // rev. 21: light around the beams on the rig and bloom (your screen only)
        button(g, bx, by - 17, 98, 14, t("Rig light") + ": " + (LightClient.rigLight < 0.01f ? t("OFF") : LightClient.percent(LightClient.rigLight)), CYAN, LightClient.rigLight > 0.01f,
            () -> { LightClient.rigLight = LightClient.nextEffect(LightClient.rigLight); LightClient.save(); },
            "Light around the beams: the haze a beam lights falls on the truss beside it, a lit floor lights the rig above. Off, 60, 100, 150 % (only on your screen)");
        button(g, bx + 102, by - 17, 98, 14, t("Bloom") + ": " + (LightClient.bloom < 0.01f ? t("OFF") : LightClient.percent(LightClient.bloom)), CYAN, LightClient.bloom > 0.01f,
            () -> { LightClient.bloom = LightClient.nextEffect(LightClient.bloom); LightClient.save(); },
            "Glow of the bright beams, lenses and flashes on the screen. Off, 60, 100, 150 % (only on your screen)");
        // rev. 24: resolution of the beams in the haze (the most expensive pass)
        String[] hr = {"Auto", "Full", "Half"};
        button(g, bx + 204, by - 17, 90, 14, t("Haze res.") + ": " + t(hr[LightClient.hazeResolution]), CYAN, LightClient.hazeResolution != 1,
            () -> { LightClient.hazeResolution = (LightClient.hazeResolution + 1) % 3; LightClient.save(); },
            "Resolution of the beams in the haze: Auto (half in low / medium quality), Full, Half (about 4x cheaper, for small graphics cards). Only on your screen");
        // client settings
        raw(g, t("Beams") + ":", x, y0 + h - 52, Ui.MUTED);
        String[] q = {"Low", "Medium", "High", "Ultra"};
        for (int k = 0; k < q.length; k++) {
            final int qq = k;
            button(g, x + 40 + k * 42, y0 + h - 55, 40, 14, t(q[k]), CYAN, LightClient.beamQuality == k, () -> { LightClient.beamQuality = qq; LightClient.save(); }, "Your own beam quality (only on your screen)");
        }
        button(g, x + 212, y0 + h - 55, 96, 14, t("Auto performance") + ": " + Tr.onOff(LightClient.autoPerformance), CYAN, LightClient.autoPerformance,
            () -> { LightClient.autoPerformance = !LightClient.autoPerformance; LightClient.save(); }, "Draws fewer beam details when the frame rate drops (never changes the show)");
        // rev. 16: atmosphere presets (Atmospheric System): none, light (recommended for a concert), medium, dense
        // rev. 1: a narrow desk (large GUI scale) has no room for this row: haze and Shimmer go to the left of the Beams brightness
        // row, Stage light and Diagnostic to the left of the Rig light row (they ran out of the window, picture at GUI scale 3)
        boolean narrow = w < 612;
        int hx = narrow ? x : x + 312, hy = narrow ? y0 + h - 73 : y0 + h - 55;
        int sx = narrow ? x : x + 488, sy = narrow ? y0 + h - 90 : y0 + h - 55;
        button(g, hx, hy, 76, 14, t("Haze") + ": " + t(LightClient.hazeName()), CYAN, false,
            () -> { LightClient.nextHaze(); LightClient.save(); },
            "Haze in the air: none (beams almost invisible, surfaces still lit), light, medium, dense — only on your screen");
        button(g, hx + 80, hy, 92, 14, t("Shimmer light") + ": " + Tr.onOff(LightClient.shimmer), CYAN, LightClient.shimmer,
            () -> { LightClient.shimmer = !LightClient.shimmer; if (!LightClient.shimmer) ShimmerLights.clear(); LightClient.save(); },
            "Not used since rev. 20: CSC lights the blocks itself, the same with or without Shimmer (start the game with -Dconcertstage.useShimmer=true to use it again)");
        button(g, sx + 62, sy, 54, 14, LightDiagnostics.mode == LightDiagnostics.OFF ? t("Diagnostic") : t(LightDiagnostics.NAMES[LightDiagnostics.mode]),
            AMBER, LightDiagnostics.mode != LightDiagnostics.OFF, LightDiagnostics::next,
            "Diagnostic views (your screen only, not saved): beams only, surfaces only, emitters only, occlusions (green lit / red shadow), optical masks, depth, normals. Counters on the F3 screen");
        button(g, sx, sy, 58, 14, t("Stage light"), CYAN, LightClient.volumeLights,
            () -> { LightClient.volumeLights = !LightClient.volumeLights; LightClient.save(); },
            "Without Shimmer: CSC's own coloured lights on the truss, roof, tarps and platforms around the fixtures");
    }

    private void exportShow() {
        LumicoreBlockEntity d = desk();
        if (d == null) return;
        try {
            Files.createDirectories(showsDir());
            String name = d.show.name.replaceAll("[^A-Za-z0-9 _.-]", "_").strip();
            Path p = showsDir().resolve((name.isEmpty() ? "show" : name) + ".cscshow");
            Files.write(p, d.show.toBytes());
            LightClient.lastMessage = t("Exported") + ": " + p.getFileName();
        } catch (Exception e) {
            LightClient.lastMessage = t("Export failed") + ": " + e.getMessage();
        }
        LightClient.lastMessageTime = System.currentTimeMillis();
    }

    private void importShow(Path p) {
        try {
            byte[] data = Files.readAllBytes(p);
            if (data.length > 2 << 20 || Show.fromBytes(data) == null) { LightClient.lastMessage = t("Not a valid show file"); LightClient.lastMessageTime = System.currentTimeMillis(); return; }
            String name = p.getFileName().toString().replace(".cscshow", "");
            action(LightNet.A_IMPORT_BEGIN, w2 -> w2.str(name).varInt(data.length));
            for (int off = 0; off < data.length; off += LightNet.CHUNK) {
                byte[] chunk = java.util.Arrays.copyOfRange(data, off, Math.min(data.length, off + LightNet.CHUNK));
                action(LightNet.A_IMPORT_CHUNK, w2 -> w2.bytes(chunk));
            }
        } catch (Exception e) {
            LightClient.lastMessage = t("Import failed") + ": " + e.getMessage();
            LightClient.lastMessageTime = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------ help / tutorial
    private void help(GuiGraphics g, LumicoreBlockEntity d, int y) {
        int x = x0 + 8;
        Ui.section(g, x, y + 3, w - 16, Component.literal(t("Your first show, step by step")));
        boolean anyList = false, running = false;
        for (Show.CueList l : d.show.lists) if (!l.cues.isEmpty()) anyList = true;
        for (Live.Playback pb : d.live.playbacks.values()) if (pb.running()) running = true;
        boolean prog = false;
        for (Look l : d.live.programmer.values()) if (l.has(Attr.DIMMER) && l.get(Attr.DIMMER) > 0) prog = true;
        String[] steps = {
            "Place CSC fixtures (on the floor, or under a truss), then patch them: Fixtures tab > Auto-patch, or the CSC Light Patch Tool.",
            "Select fixtures: Fixtures tab (click, Shift / Ctrl for several) or Groups tab.",
            "Give them intensity and a colour: Program tab (Dimmer) and Color tab (wheel, quick colours).",
            "Record cue 1: Cues tab > Record. Change something, record cue 2.",
            "Play it: Playback tab > GO (or Space). Faders scale the intensity; Back, Pause, Stop, Flash.",
            "Music: link the CSC audio console (Fixtures tab), play a track, then AutoShow tab > Analyse and generate."};
        boolean[] done = {!d.show.patch.isEmpty(), sel().length > 0, prog, anyList, running, d.live.hasAudio && !d.show.timeline.events.isEmpty()};
        for (int i = 0; i < steps.length; i++) {
            int ry = y + 18 + i * 22;
            g.fill(x, ry, x + 14, ry + 14, done[i] ? GREEN : 0xFF2A2E35);
            raw(g, done[i] ? "✓" : String.valueOf(i + 1), x + 4, ry + 3, done[i] ? 0xFF000000 : Ui.TEXT);
            for (var line : font.split(Component.literal(t(steps[i])), w - 40)) { g.drawString(font, line, x + 20, ry + 3, done[i] ? Ui.MUTED : Ui.TEXT, false); ry += 10; }
        }
        int ky = y + 18 + steps.length * 22 + 4;
        Ui.section(g, x, ky, w - 16, Component.literal(t("Keyboard")));
        String keys = t("Space GO · B blackout · R record · Delete clear programmer · Ctrl+A select all · 1-9 groups · Ctrl+Z / Ctrl+Y undo / redo · Tab next work space");
        int ly = ky + 12;
        for (var line : font.split(Component.literal(keys), w - 24)) { g.drawString(font, line, x, ly, Ui.MUTED, false); ly += 10; }
        String safety = t("Safety: BLACKOUT, STOP SHOW, NO STROBE (flashes held open), MOVE LIMIT, HOME (rest), STOP AUTO are always at the bottom. Strobes are capped at 10 Hz by default.");
        for (var line : font.split(Component.literal(safety), w - 24)) { g.drawString(font, line, x, ly + 4, AMBER, false); ly += 10; }
        // 1.2.1: the whole tutorial of the mod, in the game
        button(g, x + w - 16 - 150, y + 3, 150, 14, t("Open the full guide"), Ui.CYAN, false, com.concertstage.client.ui.GuideScreen::open,
            "Tutorial of the mod: desk, music, Art-Net, LED walls, settings, common problems");
    }

    // ------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hh = hits.get(i);
            if (!hh.in(mx, my)) continue;
            if (hh.click() != null) hh.click().accept(button);
            if (hh.drag() != null && button == 0) { dragging = hh; hh.drag().at(mx, my, false); }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { dragging.drag().at(mx, my, false); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            Hit d = dragging;
            dragging = null;
            d.drag().at(mx, my, true);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hh = hits.get(i);
            if (hh.in(mx, my) && hh.scroll() != null) { hh.scroll().accept((int) Math.signum(delta)); return true; }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean typing = (nameBox != null && nameBox.isFocused()) || (numberBox != null && numberBox.isFocused()) || (searchBox != null && searchBox.isFocused())
            || (ipBox != null && ipBox.isFocused()) || (portBox != null && portBox.isFocused()) || (srcBox != null && srcBox.isFocused()) || (mapBox != null && mapBox.isFocused());
        if (typing) return super.keyPressed(key, scan, mods);
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        LumicoreBlockEntity d = desk();
        if (d == null) return super.keyPressed(key, scan, mods);
        switch (key) {
            case GLFW.GLFW_KEY_SPACE -> {
                int id = selList >= 0 && d.show.list(selList) != null ? selList : d.show.lists.isEmpty() ? -1 : d.show.lists.get(0).id;
                if (id >= 0) cmd(Commands.op(Commands.GO).varInt(id).toBytes());
                return true;
            }
            case GLFW.GLFW_KEY_B -> { cmd(Commands.op(Commands.BLACKOUT).bool(!d.live.blackout).toBytes()); return true; }
            case GLFW.GLFW_KEY_R -> { recordNext(); return true; }
            case GLFW.GLFW_KEY_DELETE -> { Bytes.Writer w2 = Commands.op(Commands.P_CLEAR); Commands.fids(w2, new int[0]); cmd(w2.bool(true).toBytes()); return true; }
            case GLFW.GLFW_KEY_Z -> { if (ctrl) { action(LightNet.A_UNDO, null); return true; } }
            case GLFW.GLFW_KEY_Y -> { if (ctrl) { action(LightNet.A_REDO, null); return true; } }
            case GLFW.GLFW_KEY_A -> { if (ctrl) { selection().clear(); for (Show.Patch p : d.show.patch) selection().add(p.fid); return true; } }
            case GLFW.GLFW_KEY_TAB -> { switchTab(Tab.values()[(tab.ordinal() + 1) % Tab.values().length]); return true; }
            default -> { }
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int gi = key - GLFW.GLFW_KEY_1;
            if (gi < d.show.groups.size()) {
                Show.Group gr = d.show.groups.get(gi);
                if (!hasShiftDown()) selection().clear();
                for (int f : gr.fids) selection().add(f);
                selGroup = gr.id;
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------ small helpers
    private static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, Math.max(1, n - 1)) + "…";
    }

    private static String sec(float s) {
        return s == Math.rint(s) ? String.valueOf((int) s) : String.format(Locale.ROOT, "%.1f", s);
    }

    private static String time(long ms) {
        return LumicoreRenderer.time(ms);
    }

    private static float parse(String s, float def) {
        try { return Float.parseFloat(s.strip().replace(',', '.')); } catch (Exception e) { return def; }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
