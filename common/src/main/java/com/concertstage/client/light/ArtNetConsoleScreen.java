package com.concertstage.client.light;

import com.concertstage.client.ui.Ui;
import com.concertstage.light.ExternalControl;
import com.concertstage.light.LumicoreBlockEntity;
import com.concertstage.light.core.Bytes;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.Live;
import com.concertstage.light.dmx.ArtNetSettings;
import com.concertstage.light.net.LightNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Rev. 1.3.0-2 — the screen of the CSC ART-NET NODE: what most people need to drive the fixtures from an external lighting application
 * (MA onPC, dot2, QLC+, Onyx...), on one page:
 * <ol>
 *   <li>the IP of the computer that runs the application (empty: any sender is accepted);</li>
 *   <li>the network interface of this computer that listens (0.0.0.0: all of them), the UDP port (6454);</li>
 *   <li>the Art-Net universes received (Port-Addresses, first and last; the CSC universe has the same number);</li>
 *   <li>the state of the reception, measured on the server: listening / receiving / filtered / bind error, packets, last sender.</li>
 * </ol>
 * The settings are the Art-Net settings of the world (the same as Settings → Network → Art-Net of the LUMICORE X1): one receiver, one
 * UDP port. "Full desk" opens the LUMICORE screen on this node (patch list, DMX monitor, recorder).
 */
public class ArtNetConsoleScreen extends Screen {
    private static final int W = 420, H = 262, ACCENT = Ui.CYAN;
    private static final int[] STATE_COLOR = {0xFF8A9099, 0xFFF2B33D, 0xFF4CD08A, 0xFFF2B33D, 0xFFE5484D, 0xFFE5484D, 0xFFFF8A3D};
    private static final String[] STATE_KEYS = {"disabled", "listening", "receiving", "no_data", "bind_error", "invalid", "filtered"};
    private final BlockPos pos;
    private EditBox appIp, bind, port, uniFirst, uniLast;
    /** 1.2.1: takes the refused sender as the application IP and applies (shown only when packets were refused). */
    private Ui.Button acceptSender;
    private boolean filled;
    private int ticks, ifIndex = -1;
    private Component error;

    public ArtNetConsoleScreen(BlockPos pos) {
        super(Component.translatable("gui.concertstage.artnet.title"));
        this.pos = pos;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private LumicoreBlockEntity desk() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof LumicoreBlockEntity d ? d : null;
    }

    private void ext(int op, java.util.function.Consumer<Bytes.Writer> args) {
        Bytes.Writer w = LightNet.action(pos, LightNet.A_EXT).varInt(op);
        if (args != null) args.accept(w);
        LightNet.toServer(w.toBytes());
    }

    @Override
    protected void init() {
        ext(ExternalControl.X_NET_HELLO, null);
        LightNet.sync(pos);
        int x = left() + 12, y = top() + 34, lw = 150, bx = x + lw;
        appIp = box(bx, y + 10, 120, "192.168.1.20");
        bind = box(bx, y + 38, 120, "0.0.0.0");
        port = box(bx, y + 58, 56, "6454");
        uniFirst = box(bx, y + 86, 40, "0");
        uniLast = box(bx + 64, y + 86, 40, "15");
        addRenderableWidget(new Ui.Button(bx + 124, y + 38, 120, 16, () -> Component.translatable("gui.concertstage.artnet.next_interface"), ACCENT, Ui.Style.GHOST, this::nextInterface));
        addRenderableWidget(new Ui.Button(bx + 124, y + 10, 120, 16, () -> Component.translatable("gui.concertstage.artnet.any_source"), ACCENT, Ui.Style.GHOST, () -> appIp.setValue("")));
        acceptSender = new Ui.Button(left() + W - 146, y + 112 + 13 + 12 + 11 + 11 - 3, 134, 14,
            () -> Component.translatable("gui.concertstage.artnet.accept_sender"), Ui.ORANGE, Ui.Style.NORMAL, this::acceptRefused);
        acceptSender.visible = false;
        addRenderableWidget(acceptSender);
        int by = top() + H - 26;
        addRenderableWidget(new Ui.Button(x, by, 128, 18, () -> Component.translatable("gui.concertstage.artnet.apply"), Ui.GREEN, Ui.Style.ACCENT, this::apply));
        addRenderableWidget(new Ui.Button(x + 132, by, 80, 18, () -> Component.translatable("gui.concertstage.artnet.test"), ACCENT, Ui.Style.NORMAL,
            () -> ext(ExternalControl.X_NET_TEST, null)));
        addRenderableWidget(new Ui.Button(x + 216, by, 96, 18, () -> Component.translatable("gui.concertstage.artnet.full_desk"), ACCENT, Ui.Style.NORMAL,
            () -> minecraft.setScreen(new LumicoreScreen(pos))));
        addRenderableWidget(new Ui.Button(left() + W - 84, by, 72, 18, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
        // External control of the fixtures of this node: on (the application drives them) / off (the node holds them dark)
        addRenderableWidget(new Ui.Button(left() + W - 136, top() + 5, 124, 16, () -> {
            LumicoreBlockEntity d = desk();
            boolean on = d != null && d.live.control == Live.EXTERNAL;
            return Component.translatable(on ? "gui.concertstage.artnet.control_on" : "gui.concertstage.artnet.control_off");
        }, Ui.GREEN, Ui.Style.NORMAL, () -> {
            LumicoreBlockEntity d = desk();
            if (d == null) return;
            int next = d.live.control == Live.EXTERNAL ? Live.INTERNAL : Live.EXTERNAL;
            LightNet.command(pos, Commands.op(Commands.CONTROL_MODE).varInt(next).toBytes());
        }));
        filled = false;
        fill();
    }

    private EditBox box(int x, int y, int w, String hint) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.literal(hint));
        b.setMaxLength(40);
        b.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        addRenderableWidget(b);
        return b;
    }

    /** The boxes take the settings the server sent (once, so typing is not overwritten). */
    private void fill() {
        ExternalStatus es = ExternalStatus.of(pos);
        if (filled || !es.hasArt) return;
        ArtNetSettings a = es.art;
        appIp.setValue(a.anySource ? "" : a.sourceIp);
        bind.setValue(a.bind);
        port.setValue(Integer.toString(a.port));
        int s = a.rangeStart(), e = a.rangeEnd();
        uniFirst.setValue(Integer.toString(s < 0 ? 0 : s));
        uniLast.setValue(Integer.toString(e < 0 ? 15 : e));
        filled = true;
    }

    private void nextInterface() {
        ExternalStatus es = ExternalStatus.of(pos);
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add("0.0.0.0");
        for (ExternalStatus.NetIf n : es.interfaces) if (n.up() && ArtNetSettings.ipv4(n.address()) && !list.contains(n.address())) list.add(n.address());
        ifIndex = (list.indexOf(bind.getValue().trim()) + 1) % list.size();
        bind.setValue(list.get(ifIndex));
    }

    private void apply() {
        error = null;
        ExternalStatus es = ExternalStatus.of(pos);
        ArtNetSettings a = es.hasArt ? es.art.copy() : new ArtNetSettings();
        String ip = appIp.getValue().trim();
        a.anySource = ip.isEmpty();
        a.sourceIp = ip;
        a.bind = bind.getValue().trim().isEmpty() ? "0.0.0.0" : bind.getValue().trim();
        a.enabled = true;
        try {
            a.port = Integer.parseInt(port.getValue().trim());
            int s = Integer.parseInt(uniFirst.getValue().trim()), e = Integer.parseInt(uniLast.getValue().trim());
            if (s < 0 || e < 0 || s > ArtNetSettings.MAX_PORT_ADDRESS || e > ArtNetSettings.MAX_PORT_ADDRESS) throw new NumberFormatException();
            a.range(s, e);
        } catch (NumberFormatException ex) {
            error = Component.translatable("gui.concertstage.artnet.bad_number");
            return;
        }
        String bad = a.validate();
        if (bad != null) { error = Component.literal(bad); return; }
        ext(ExternalControl.X_NET_SAVE, a::write);
        filled = false;
    }

    private void acceptRefused() {
        ExternalStatus es = ExternalStatus.of(pos);
        if (es.lastFiltered.isEmpty()) return;
        appIp.setValue(es.lastFiltered);
        apply();
    }

    @Override
    public void tick() {
        if (acceptSender != null) {
            ExternalStatus st = ExternalStatus.of(pos);
            acceptSender.visible = st.hasArt && !st.lastFiltered.isEmpty() && st.artFiltered > 0;
        }
        for (EditBox b : new EditBox[] {appIp, bind, port, uniFirst, uniLast}) if (b != null) b.tick();
        if (desk() == null) { onClose(); return; }
        if (++ticks % 40 == 0) ext(ExternalControl.X_NET_HELLO, null);
        fill();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        int x = left(), y = top();
        Ui.panel(g, x, y, W, H, ACCENT, title, Component.translatable("gui.concertstage.artnet.subtitle"));
        int lx = x + 12, ly = y + 34;
        Ui.section(g, lx, ly, 136, Component.translatable("gui.concertstage.artnet.connection"));
        g.drawString(font, Component.translatable("gui.concertstage.artnet.app_ip"), lx, ly + 14, Ui.TEXT, false);
        g.drawString(font, Component.translatable("gui.concertstage.artnet.bind"), lx, ly + 42, Ui.TEXT, false);
        g.drawString(font, Component.translatable("gui.concertstage.artnet.port"), lx, ly + 62, Ui.TEXT, false);
        g.drawString(font, Component.translatable("gui.concertstage.artnet.universes"), lx, ly + 90, Ui.TEXT, false);
        g.drawString(font, "→", lx + 150 + 46, ly + 90, Ui.MUTED, false);
        // ---- status (measured on the server)
        ExternalStatus es = ExternalStatus.of(pos);
        int sy = ly + 112;
        Ui.section(g, lx, sy, W - 24, Component.translatable("gui.concertstage.artnet.status"));
        sy += 13;
        if (!es.hasArt) {
            g.drawString(font, Component.translatable("gui.concertstage.artnet.waiting"), lx, sy, Ui.MUTED, false);
        } else {
            int st = Math.max(0, Math.min(6, es.artState));
            g.fill(lx, sy, lx + 8, sy + 8, STATE_COLOR[st]);
            g.drawString(font, Component.translatable("gui.concertstage.artnet.state." + STATE_KEYS[st]), lx + 12, sy, STATE_COLOR[st], false);
            if (!es.listening.isEmpty()) g.drawString(font, Component.translatable("gui.concertstage.artnet.listening", es.listening), lx + 150, sy, Ui.MUTED, false);
            sy += 12;
            g.drawString(font, Component.translatable("gui.concertstage.artnet.packets", es.artAccepted, es.artRate, es.artFiltered), lx, sy, Ui.TEXT, false);
            sy += 11;
            String last = es.lastSource.isEmpty() ? "-" : es.lastSource;
            String ago = es.lastAcceptedAgo < 0 ? "-" : String.format(java.util.Locale.ROOT, "%.1f s", es.lastAcceptedAgo / 1000.0);
            g.drawString(font, Component.translatable("gui.concertstage.artnet.last_source", last, ago), lx, sy, Ui.TEXT, false);
            sy += 11;
            if (!es.lastFiltered.isEmpty() && es.artFiltered > 0)
                { g.drawString(font, Component.translatable("gui.concertstage.artnet.filtered_from", es.lastFiltered), lx, sy, Ui.ORANGE, false); sy += 11; }
            if (!es.bindError.isEmpty() && st == 4) { g.drawString(font, Component.literal(es.bindError), lx, sy, Ui.RED, false); sy += 11; }
            if (!es.receiverAllowed) { g.drawString(font, Component.translatable("gui.concertstage.artnet.not_allowed"), lx, sy, Ui.RED, false); sy += 11; }
            if (!es.netReport.isEmpty()) { Ui.centeredFit(g, Component.literal(es.netReport), x + W / 2, sy, W - 24, Ui.MUTED); sy += 11; }
        }
        LumicoreBlockEntity d = desk();
        if (d != null) {
            int n = d.show.patch.size();
            g.drawString(font, Component.translatable("gui.concertstage.artnet.fixtures", n), lx, y + H - 44, n > 0 ? Ui.TEXT : Ui.ORANGE, false);
            if (n == 0) g.drawString(font, Component.translatable("gui.concertstage.artnet.fixtures_hint"), lx + 110, y + H - 44, Ui.MUTED, false);
        }
        if (error != null) g.drawString(font, error, lx, y + H - 56, Ui.RED, false);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
