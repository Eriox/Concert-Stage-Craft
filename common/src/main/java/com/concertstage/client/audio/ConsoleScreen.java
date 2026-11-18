package com.concertstage.client.audio;

import com.concertstage.audio.AudioConsoleBlockEntity;
import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerBlockEntity;
import com.concertstage.audio.core.AudioFiles;
import com.concertstage.audio.net.AudioNet;
import com.concertstage.client.ui.Tr;
import com.concertstage.client.ui.Ui;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * CSC Audio Console screen (purple theme). Left: playlist, transport, progress (click to seek) and the import of MP3 / WAV files
 * (from the music folder, a file dialog, or dropped on the window). Middle: master and group faders with their real meters and
 * mutes. Right: 3-band EQ and the linked arrays (group, unlink). Every change is sent to the server, which syncs every listener.
 */
public class ConsoleScreen extends Screen {
    private static final int W = 530, H = 274, ACCENT = Ui.PURPLE;
    private static final int ROWS = 9, SPK_ROWS = 6;
    private final BlockPos pos;
    private int selected = -1, scroll, spkScroll;
    private boolean picker;
    private final List<Path> files = new ArrayList<>();
    /** 1.2.0 beta 2b: files ticked in the picker (imported together). */
    private final java.util.Set<Path> chosen = new java.util.LinkedHashSet<>();
    private int fileScroll;
    private static final int PICK_ROWS = 9;
    private final List<Fader> faders = new ArrayList<>();
    private Ui.Slider[] eq = new Ui.Slider[3];
    /** 1.1.0: the "?" overlay that explains each part of the console. */
    private boolean help;
    private boolean monitorBank;

    public ConsoleScreen(BlockPos pos) {
        super(Component.translatable("gui.concertstage.console_title"));
        this.pos = pos;
        ClientAudio.watch(pos);
        FriendlyInfo.ask(pos);
    }

    /** Asks the server what this player may import (permission, size limits). */
    private static final class FriendlyInfo {
        static void ask(BlockPos pos) {
            var b = AudioNet.message(AudioNet.C_INFO);
            b.writeBlockPos(pos);
            AudioNet.toServer(b);
        }
    }

    public BlockPos consolePos(){return pos;}

    private AudioConsoleBlockEntity console() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof AudioConsoleBlockEntity c ? c : null;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private void cmd(int action, int a, float f) {
        AudioNet.consoleCommand(pos, action, a, f);
    }

    @Override
    public void tick() {
        if (console() == null) onClose();
    }

    @Override
    public void removed() {
        ClientAudio.watch(null);
    }

    @Override
    protected void init() {
        faders.clear();
        ClientAudio.watch(pos);
        AudioConsoleBlockEntity c = console();
        if (c == null) return;
        int x = left() + 10, y = top() + 34;
        if (picker) { initPicker(); return; }
        // playlist editing
        int py = y + 14 + ROWS * 12 + 3;
        Tr.help(addRenderableWidget(new Ui.Button(x, py, 22, 14, () -> Component.literal("▲"), ACCENT, Ui.Style.GHOST, () -> { if (selected > 0) { cmd(AudioNet.MOVE, selected, -1); selected--; } })), "track_up");
        Tr.help(addRenderableWidget(new Ui.Button(x + 24, py, 22, 14, () -> Component.literal("▼"), ACCENT, Ui.Style.GHOST, () -> {
            AudioConsoleBlockEntity cc = console();
            if (cc != null && selected >= 0 && selected < cc.playlist().size() - 1) { cmd(AudioNet.MOVE, selected, 1); selected++; } })), "track_down");
        Tr.help(addRenderableWidget(new Ui.Button(x + 48, py, 22, 14, () -> Component.literal("✕"), Ui.RED, Ui.Style.GHOST, () -> { if (selected >= 0) { cmd(AudioNet.REMOVE, selected, 0); selected = -1; } })), "track_remove");
        Tr.help(addRenderableWidget(new Ui.Button(x + 76, py, 114, 14, () -> Component.translatable("gui.concertstage.console_import"), ACCENT, Ui.Style.ACCENT, this::openPicker)), "import");
        // transport
        int ty = py + 18, bw = 30;
        Tr.help(addRenderableWidget(new Ui.Button(x, ty, bw, 18, () -> Component.literal("⏮"), ACCENT, Ui.Style.NORMAL, () -> cmd(AudioNet.PREV, 0, 0))), "prev");
        Tr.help(addRenderableWidget(new Ui.Button(x + 32, ty, bw + 10, 18, () -> Component.literal("▶"), Ui.GREEN, Ui.Style.NORMAL, () -> {
            AudioConsoleBlockEntity cc = console();
            if (cc != null && selected >= 0 && selected != cc.current()) cmd(AudioNet.SELECT, selected, 0); else cmd(AudioNet.PLAY, 0, 0); })), "play");
        Tr.help(addRenderableWidget(new Ui.Button(x + 74, ty, bw, 18, () -> Component.literal("⏸"), ACCENT, Ui.Style.NORMAL, () -> cmd(AudioNet.PAUSE, 0, 0))), "pause");
        Tr.help(addRenderableWidget(new Ui.Button(x + 106, ty, bw, 18, () -> Component.literal("⏹"), Ui.RED, Ui.Style.NORMAL, () -> cmd(AudioNet.STOP, 0, 0))), "stop");
        Tr.help(addRenderableWidget(new Ui.Button(x + 138, ty, bw, 18, () -> Component.literal("⏭"), ACCENT, Ui.Style.NORMAL, () -> cmd(AudioNet.NEXT, 0, 0))), "next");
        addRenderableWidget(new Ui.Button(x + 170, ty, 20, 18, () -> Component.literal(console() != null && console().repeatMode() == AudioConsoleBlockEntity.REPEAT_ONE ? "↻1" : "↻"), ACCENT, Ui.Style.NORMAL, () -> cmd(AudioNet.REPEAT, 0, 0)) {
            @Override public void renderWidget(GuiGraphics g, int mx, int my, float p) {
                AudioConsoleBlockEntity cc = console();
                selected(cc != null && cc.repeat());
                if (cc != null) setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.concertstage.repeat_mode_" + cc.repeatMode()).append("\n").append(Tr.tip("repeat"))));
                super.renderWidget(g, mx, my, p);
            }
        });
        // faders: master + groups
        int fx = left() + 214, fy = y + 14;
        faders.add(addRenderableWidget(new Fader(fx, fy, -1)));
        addRenderableWidget(new Ui.Button(fx, y - 3, 186, 14, () -> Component.translatable(monitorBank ? "gui.concertstage.monitor_bank" : "gui.concertstage.main_bank"), ACCENT, Ui.Style.GHOST, () -> { monitorBank = !monitorBank; rebuildWidgets(); }));
        for (int slot = 0; slot < (monitorBank ? 3 : 6); slot++) {
            int g = monitorBank ? AudioGroups.MONITOR_1 + slot : slot;
            final int gg = g;
            Fader f = addRenderableWidget(new Fader(fx + 34 + slot * 26, fy, g));
            faders.add(f);
            addRenderableWidget(new Ui.Button(fx + 34 + slot * 26, fy + 148, 22, 13, () -> Component.literal("M"), Ui.RED, Ui.Style.GHOST, () -> cmd(AudioNet.MUTE, gg, 0)) {
                @Override public void renderWidget(GuiGraphics gr, int mx, int my, float p) {
                    AudioConsoleBlockEntity cc = console();
                    selected(cc != null && cc.muted(gg));
                    if (cc != null) setTooltip(net.minecraft.client.gui.components.Tooltip.create(Tr.tip(cc.muted(gg) ? "unmute" : "mute", groupName(cc, gg))));
                    super.renderWidget(gr, mx, my, p);
                }
            });
        }
        // EQ
        int ex = left() + W - 124, ew = 114;
        String[] bands = {"low", "mid", "high"};
        for (int b = 0; b < 3; b++) {
            final int bb = b;
            eq[b] = addRenderableWidget(new Ui.Slider(ex, y + 14 + b * 22, ew, 20, Component.translatable("gui.concertstage.eq_" + bands[b]),
                -24, 24, Math.round(c.eq(b) * 2), ACCENT, v -> String.format(java.util.Locale.ROOT, "%+.1f dB", v / 2f), v -> cmd(AudioNet.EQ, bb, v / 2f)));
            Tr.help(eq[b], "console_eq");
        }
        // linked arrays
        int sy = y + 98;
        Tr.help(addRenderableWidget(new Ui.Button(ex + ew - 42, sy - 2, 20, 11, () -> Component.literal("▲"), ACCENT, Ui.Style.GHOST, () -> spkScroll = Math.max(0, spkScroll - 1))), "arrays_scroll");
        Tr.help(addRenderableWidget(new Ui.Button(ex + ew - 20, sy - 2, 20, 11, () -> Component.literal("▼"), ACCENT, Ui.Style.GHOST, () -> {
            AudioConsoleBlockEntity cc = console();
            if (cc != null) spkScroll = Math.max(0, Math.min(cc.speakers().size() - SPK_ROWS, spkScroll + 1)); })), "arrays_scroll");
        Tr.help(addRenderableWidget(new Ui.Button(ex, top() + H - 24, ew, 16, () -> Component.translatable("gui.concertstage.audio_settings"), ACCENT, Ui.Style.GHOST,
            () -> Minecraft.getInstance().setScreen(new AudioSettingsScreen(this)))), "sound_settings");
        // help overlay toggle, in the title bar
        Tr.help(addRenderableWidget(new Ui.Button(left() + W - 26, top() + 5, 18, 16, () -> Component.literal("?"), ACCENT, Ui.Style.NORMAL, () -> { help = !help; rebuildWidgets(); })
            .selected(help)), "console_help");
    }

    // ------------------------------------------------------------------ import picker
    private void openPicker() {
        picker = true;
        files.clear();
        chosen.clear();
        fileScroll = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(AudioCache.musicDir())) {
            for (Path p : ds) if (Files.isRegularFile(p) && AudioFiles.extension(p.getFileName().toString()) != null) files.add(p);
        } catch (IOException ignored) {
            // empty list
        }
        files.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        rebuildWidgets();
    }

    private void initPicker() {
        int x = left() + 10, y = top() + 34, w = W - 20;
        for (int r = 0; r < PICK_ROWS; r++) {
            int i = fileScroll + r;
            if (i >= files.size()) break;
            Path p = files.get(i);
            String size = String.format(java.util.Locale.ROOT, "%.1f MB", sizeOf(p) / 1048576.0);
            Tr.help(addRenderableWidget(new Ui.Button(x, y + 14 + r * 17, w, 15,
                () -> Component.literal((chosen.contains(p) ? "☑  " : "☐  ") + p.getFileName().toString() + "   ·   " + size), ACCENT, Ui.Style.NORMAL,
                () -> { if (!chosen.remove(p)) chosen.add(p); rebuildWidgets(); }).selected(chosen.contains(p))), "files_pick");
        }
        // selection: all / none, import the ticked files, import every file of the folder
        int sy = top() + 206, sw = (w - 8) / 3;
        boolean all = !files.isEmpty() && chosen.size() == files.size();
        Tr.help(addRenderableWidget(new Ui.Button(x, sy, sw, 16,
            () -> Component.translatable(all ? "gui.concertstage.console_select_none" : "gui.concertstage.console_select_all"), ACCENT, Ui.Style.GHOST,
            () -> { if (all) chosen.clear(); else chosen.addAll(files); rebuildWidgets(); })), "files_select_all");
        Tr.help(addRenderableWidget(new Ui.Button(x + sw + 4, sy, sw, 16,
            () -> Component.translatable("gui.concertstage.console_import_selected", chosen.size()), ACCENT, Ui.Style.ACCENT,
            () -> { if (chosen.isEmpty()) return; Transfers.importFiles(pos, new ArrayList<>(chosen)); chosen.clear(); picker = false; rebuildWidgets(); })
            .selected(!chosen.isEmpty())), "files_import_selected");
        Tr.help(addRenderableWidget(new Ui.Button(x + 2 * (sw + 4), sy, sw, 16,
            () -> Component.translatable("gui.concertstage.console_import_all", files.size()), ACCENT, Ui.Style.NORMAL,
            () -> { if (files.isEmpty()) return; Transfers.importFiles(pos, new ArrayList<>(files)); chosen.clear(); picker = false; rebuildWidgets(); })),
            "files_import_all");
        int by = top() + H - 26, bw = (w - 12) / 4;
        Tr.help(addRenderableWidget(new Ui.Button(x, by, bw, 16, () -> Component.literal("▲ ▼"), ACCENT, Ui.Style.GHOST,
            () -> { fileScroll = fileScroll + PICK_ROWS >= files.size() ? 0 : fileScroll + PICK_ROWS; rebuildWidgets(); })), "files_page");
        Tr.help(addRenderableWidget(new Ui.Button(x + bw + 4, by, bw, 16, () -> Component.translatable("gui.concertstage.console_folder"), ACCENT, Ui.Style.GHOST,
            () -> Util.getPlatform().openFile(AudioCache.musicDir().toFile()))), "files_folder");
        Tr.help(addRenderableWidget(new Ui.Button(x + 2 * (bw + 4), by, bw, 16, () -> Component.translatable("gui.concertstage.console_browse"), ACCENT, Ui.Style.GHOST, this::browse)), "files_browse");
        addRenderableWidget(new Ui.Button(x + 3 * (bw + 4), by, bw, 16, () -> Component.translatable("gui.concertstage.back"), ACCENT, Ui.Style.ACCENT,
            () -> { picker = false; rebuildWidgets(); }));
    }

    private static long sizeOf(Path p) {
        try { return Files.size(p); } catch (IOException e) { return 0; }
    }

    /** System file dialog, on its own thread (the game keeps running). */
    private void browse() {
        Thread t = new Thread(() -> {
            String chosen;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(2);
                filters.put(stack.UTF8("*.mp3"));
                filters.put(stack.UTF8("*.wav"));
                filters.flip();
                chosen = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(Component.translatable("gui.concertstage.console_browse").getString(),
                    AudioCache.musicDir().toAbsolutePath() + java.io.File.separator, filters, "MP3 / WAV", true);
            } catch (Throwable e) {
                chosen = null;
            }
            final String c = chosen;
            if (c != null) {
                // several files chosen: tinyfd separates them with '|'
                List<Path> list = new ArrayList<>();
                for (String one : c.split("\\|")) if (!one.isBlank()) list.add(Path.of(one));
                Minecraft.getInstance().execute(() -> { Transfers.importFiles(pos, list); picker = false; rebuildWidgets(); });
            }
        }, "CSC file dialog");
        t.setDaemon(true);
        t.start();
    }

    /** Files dropped on the game window. */
    @Override
    public void onFilesDrop(List<Path> paths) {
        List<Path> ok = new ArrayList<>();
        for (Path p : paths) if (AudioFiles.extension(p.getFileName().toString()) != null) ok.add(p);
        Transfers.importFiles(pos, ok);
        picker = false;
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ mouse
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (help) {
            boolean onToggle = mx >= left() + W - 26 && mx < left() + W - 8 && my >= top() + 5 && my < top() + 21;
            if (!onToggle) { help = false; rebuildWidgets(); return true; }
        }
        AudioConsoleBlockEntity c = console();
        if (c != null && !picker) {
            int x = left() + 10, y = top() + 34;
            // playlist rows
            for (int r = 0; r < ROWS; r++) {
                int i = scroll + r;
                if (i >= c.playlist().size()) break;
                int ry = y + 14 + r * 12;
                if (mx >= x && mx < x + 190 && my >= ry && my < ry + 12) {
                    if (selected == i && button == 0) cmd(AudioNet.SELECT, i, 0);     // second click: play it
                    selected = i;
                    return true;
                }
            }
            // progress bar: seek
            int by = progressY();
            AudioConsoleBlockEntity.Track t = c.currentTrack();
            if (t != null && t.durationMs() > 0 && mx >= x && mx < x + 190 && my >= by - 2 && my < by + 6) {
                cmd(AudioNet.SEEK, (int) Math.min(Integer.MAX_VALUE, (long) ((mx - x) / 190.0 * t.durationMs())), 0);
                return true;
            }
            // linked arrays: group chip (cycle) and unlink
            int ex = left() + W - 124, sy = top() + 34 + 98 + 12;
            for (int r = 0; r < SPK_ROWS; r++) {
                int i = spkScroll + r;
                if (i >= c.speakers().size()) break;
                int ry = sy + r * 13;
                if (my >= ry && my < ry + 12) {
                    var level = Minecraft.getInstance().level;
                    if (mx >= ex + 98 && mx < ex + 114) { cmd(AudioNet.UNLINK, i, 0); return true; }
                    if (mx >= ex && mx < ex + 40 && level != null && level.getBlockEntity(c.speakers().get(i)) instanceof SpeakerBlockEntity s) {
                        cmd(AudioNet.SET_GROUP, i, (s.group() + (button == 1 ? AudioGroups.COUNT - 1 : 1)) % AudioGroups.COUNT);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        AudioConsoleBlockEntity c = console();
        if (c != null && !picker && mx < left() + 202) {
            scroll = Math.max(0, Math.min(Math.max(0, c.playlist().size() - ROWS), scroll - (int) Math.signum(delta)));
            return true;
        }
        if (picker) {
            fileScroll = Math.max(0, Math.min(Math.max(0, files.size() - PICK_ROWS), fileScroll - (int) Math.signum(delta)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private int progressY() {
        // under the transport buttons (they end 18 + 18 px under the playlist edit row), after the "now playing" line
        return top() + 34 + 14 + ROWS * 12 + 3 + 18 + 18 + 16;
    }

    // ------------------------------------------------------------------ drawing
    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        AudioConsoleBlockEntity c = console();
        Component sub = Component.translatable("gui.concertstage.console_subtitle", pos.getX(), pos.getY(), pos.getZ(),
            c == null ? 0 : c.speakers().size(), ClientAudio.activeVoices());
        Ui.panel(g, left(), top(), W, H, ACCENT, title, sub);
        if (c == null) { super.render(g, mouseX, mouseY, partial); return; }
        int x = left() + 10, y = top() + 34;
        if (picker) {
            Ui.section(g, x, y, W - 20, Component.translatable("gui.concertstage.console_files", AudioCache.musicDir().toString()));
            if (files.isEmpty()) {
                g.drawString(font, Component.translatable("gui.concertstage.console_no_files"), x + 4, y + 20, Ui.MUTED, false);
                g.drawString(font, Component.translatable("gui.concertstage.console_drop"), x + 4, y + 34, Ui.MUTED, false);
            }
            g.drawString(font, Component.translatable("gui.concertstage.console_rights"), x, top() + H - 40, 0xFF8A9099, false);
            if (files.size() > PICK_ROWS) g.drawString(font, (fileScroll + 1) + "–" + Math.min(files.size(), fileScroll + PICK_ROWS) + " / " + files.size(),
                left() + W - 80, y, Ui.MUTED, false);
            super.render(g, mouseX, mouseY, partial);
            return;
        }
        // playlist
        Ui.section(g, x, y, 190, Component.translatable("gui.concertstage.console_playlist", c.playlist().size(), AudioConsoleBlockEntity.MAX_TRACKS));
        g.fill(x, y + 13, x + 190, y + 14 + ROWS * 12, 0xFF0D0F12);
        if (c.playlist().isEmpty()) g.drawString(font, Component.translatable("gui.concertstage.console_empty"), x + 4, y + 18, Ui.MUTED, false);
        for (int r = 0; r < ROWS; r++) {
            int i = scroll + r;
            if (i >= c.playlist().size()) break;
            AudioConsoleBlockEntity.Track t = c.playlist().get(i);
            int ry = y + 14 + r * 12;
            boolean cur = i == c.current(), sel = i == selected;
            if (sel) g.fill(x, ry, x + 190, ry + 12, Ui.alpha(ACCENT, 0x50));
            String mark = cur ? (c.state() == AudioConsoleBlockEntity.PLAYING ? "▶ " : c.state() == AudioConsoleBlockEntity.PAUSED ? "⏸ " : "• ") : "   ";
            String d = time(t.durationMs());
            double dl = Transfers.downloadProgress(t.sha1());
            if (dl >= 0) d = (int) (dl * 100) + "%";
            String name = font.plainSubstrByWidth(mark + t.name(), 186 - font.width(d) - 6);
            g.drawString(font, name, x + 3, ry + 2, cur ? Ui.TEXT : Ui.MUTED, false);
            g.drawString(font, d, x + 187 - font.width(d), ry + 2, dl >= 0 ? Ui.CYAN : 0xFF8A9099, false);
        }
        // now playing + progress
        AudioConsoleBlockEntity.Track t = c.currentTrack();
        int by = progressY();
        var level = Minecraft.getInstance().level;
        long ms = level == null ? 0 : c.positionMs((double) level.getGameTime() + partial);
        if (t != null) {
            ClientAudio.Playback pb = ClientAudio.playback(pos);
            String state = pb != null && pb.waitingForFile ? Component.translatable(AudioSettings.downloads ? "gui.concertstage.console_receiving" : "gui.concertstage.console_no_download").getString()
                : time(ms) + " / " + (t.durationMs() > 0 ? time(t.durationMs()) : "-:--");
            // "now playing" strip, clear of the transport buttons: state icon, title, time
            boolean playing = c.state() == AudioConsoleBlockEntity.PLAYING, paused = c.state() == AudioConsoleBlockEntity.PAUSED;
            g.fill(x, by - 14, x + 190, by - 2, playing ? Ui.mix(0xFF0D0F12, Ui.GREEN, 0.18f) : 0xFF0D0F12);
            String icon = playing ? "▶" : paused ? "⏸" : "■";
            g.drawString(font, icon, x + 3, by - 12, playing ? Ui.GREEN : paused ? Ui.AMBER : Ui.MUTED, false);
            int tx = x + 4 + font.width(icon) + 4;
            g.drawString(font, font.plainSubstrByWidth(t.name(), x + 190 - tx - font.width(state) - 6), tx, by - 12, playing ? Ui.TEXT : Ui.MUTED, false);
            g.drawString(font, state, x + 188 - font.width(state), by - 12, Ui.MUTED, false);
            Ui.gauge(g, x, by, 190, 4, t.durationMs() > 0 ? ms / (double) t.durationMs() : 0, ACCENT, -1);
        } else {
            g.fill(x, by - 14, x + 190, by - 2, 0xFF0D0F12);
            g.drawString(font, Component.translatable("gui.concertstage.console_no_track"), x + 4, by - 12, Ui.MUTED, false);
        }
        // import status
        Component st = Transfers.status();
        double up = Transfers.uploadProgress();
        if (up >= 0) Ui.gauge(g, x, by + 12, 190, 3, up, Ui.CYAN, -1);
        String stText = st.getString();
        if (Transfers.queued() > 0) stText = Component.translatable("audio.concertstage.import_queue", Transfers.queued()).getString() + "  " + stText;
        if (!stText.isEmpty()) g.drawString(font, font.plainSubstrByWidth(stText, 190), x, by + 18, Transfers.statusColor(), false);
        if (!Transfers.canUpload) g.drawString(font, Component.translatable("audio.concertstage.err_permission"), x, by + 30, 0xFF8A9099, false);
        // mixer
        int fx = left() + 214, fy = y;
        // The bank selector labels the mixer section.
        ClientAudio.Playback pb = ClientAudio.playback(pos);
        for (int slot = 0; slot <= (monitorBank ? 3 : 6); slot++) {
            int k = slot == 0 ? 0 : (monitorBank ? AudioGroups.MONITOR_1 + slot : slot);
            int cx = slot == 0 ? fx : fx + 34 + (slot - 1) * 26;
            String label = k == 0 ? Component.translatable("gui.concertstage.group_short.master").getString()
                : groupShort(c,k-1);
            int col = k == 0 ? Ui.TEXT : AudioGroups.COLORS[k - 1];
            g.drawCenteredString(font, label, cx + 11, fy + 178, col);
            if (k == 0) {
                vu(g, cx + 24, fy + 14, 3, 132, pb == null ? 0 : pb.vuL);
                vu(g, cx + 28, fy + 14, 3, 132, pb == null ? 0 : pb.vuR);
            } else {
                vu(g, cx + 22, fy + 14, 2, 132, pb == null ? 0 : pb.vuGroup[k - 1]);
            }
        }
        // EQ and arrays
        int ex = left() + W - 124;
        Ui.section(g, ex, y, 114, Component.translatable("gui.concertstage.console_eq"));
        Ui.section(g, ex, y + 86, 114, Component.translatable("gui.concertstage.console_arrays", c.speakers().size()));
        int sy = y + 98 + 12;
        List<Component> arrayTip = null;
        for (int r = 0; r < SPK_ROWS; r++) {
            int i = spkScroll + r;
            if (i >= c.speakers().size()) break;
            BlockPos p = c.speakers().get(i);
            int ry = sy + r * 13;
            SpeakerBlockEntity s = level != null && level.getBlockEntity(p) instanceof SpeakerBlockEntity x2 ? x2 : null;
            if (s != null && mouseX >= ex && mouseX < ex+114 && mouseY >= ry && mouseY < ry+12) {
                var model=s.firstModel();var listener=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                arrayTip=new ArrayList<>();
                if(model!=null) {
                    arrayTip.add(Component.translatable("item.concertstage."+model.id()));
                    arrayTip.add(Component.literal(String.format(java.util.Locale.ROOT,"%.2f × %.2f × %.2f m",model.width(),model.height(),model.depth())));
                }
                arrayTip.add(Component.literal(Tr.t("Modules: ")+s.cabinets().size()+" | "+Tr.t(s.hanging()?"FLY":"GROUND")));
                arrayTip.add(Component.literal(s.support().isEmpty()?Tr.t("Legacy support"):s.support()));
                int group=s.group();arrayTip.add(Component.literal(groupName(c,group)+" • "+Tr.f("delay %.2f ms",c.groupDelay(group))));
                arrayTip.add(Component.literal(Tr.f("Yaw %.2f° | Tilt %.2f° | %.1f m from you",s.yaw(),s.tilt(),s.acousticAxis()[0].distanceTo(listener))));
                arrayTip.add(Component.literal(Tr.f("Output +%.1f dB | Link %s",20*Math.log10(AudioSettings.outputGain),Tr.t(pos.equals(s.console())?"OK":"lost"))));
                arrayTip.add(Tr.tip("array_chip").withStyle(net.minecraft.ChatFormatting.GRAY));
                if(mouseX>=ex+98)arrayTip.add(Tr.tip("array_unlink").withStyle(net.minecraft.ChatFormatting.RED));
            }
            int grp = s == null ? -1 : s.group();
            g.fill(ex, ry, ex + 40, ry + 12, grp < 0 ? 0xFF24282E : Ui.mix(0xFF1C1F24, AudioGroups.COLORS[grp], 0.55f));
            String gl = grp < 0 ? "?" : groupShort(c,grp);
            g.drawCenteredString(font, gl, ex + 20, ry + 2, Ui.TEXT);
            String info = s == null ? Component.translatable("gui.concertstage.console_far").getString() : s.cabinets().size() + "× " + p.getY();
            g.drawString(font, font.plainSubstrByWidth(info, 54), ex + 44, ry + 2, s == null ? 0xFF8A9099 : Ui.MUTED, false);
            g.fill(ex + 98, ry, ex + 114, ry + 12, 0xFF2A1C1E);
            g.drawCenteredString(font, "✕", ex + 106, ry + 2, Ui.RED);
        }
        if (c.speakers().isEmpty()) g.drawString(font, Component.translatable("gui.concertstage.console_no_arrays"), ex, sy + 2, 0xFF8A9099, false);
        super.render(g, mouseX, mouseY, partial);
        if (help) { renderHelp(g); return; }
        if (arrayTip != null) g.renderComponentTooltip(font,arrayTip,mouseX,mouseY);
        else if (mouseX >= x && mouseX < x + 190 && mouseY >= y + 14 && mouseY < y + 14 + ROWS * 12 && !c.playlist().isEmpty())
            g.renderTooltip(font, font.split(Tr.tip("playlist"), 220), mouseX, mouseY);
        else if (t != null && t.durationMs() > 0 && mouseX >= x && mouseX < x + 190 && mouseY >= by - 2 && mouseY < by + 6)
            g.renderTooltip(font, font.split(Tr.tip("seek", time((long) ((mouseX - x) / 190.0 * t.durationMs()))), 220), mouseX, mouseY);
    }

    /** The "?" overlay: one short line per part of the console, in the player's language. */
    private void renderHelp(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(left(), top() + 27, left() + W, top() + H, 0xE80D0F12);
        int x = left() + 18, y = top() + 38, w = W - 36;
        Ui.section(g, x, y, w, Component.translatable("gui.concertstage.console_help_title"));
        y += 16;
        for (int i = 1; i <= 9; i++) {
            Component line = Component.translatable("gui.concertstage.console_help_" + i);
            for (var part : font.split(line, w)) { g.drawString(font, part, x, y, i % 2 == 1 ? Ui.TEXT : Ui.MUTED, false); y += 10; }
            y += 3;
        }
        g.drawString(font, Component.translatable("gui.concertstage.console_help_close"), x, top() + H - 16, 0xFF8A9099, false);
        g.pose().popPose();
    }

    /** Vertical meter: green, then amber over -6 dB, red over -1 dB. */
    private static void vu(GuiGraphics g, int x, int y, int w, int h, float level) {
        g.fill(x, y, x + w, y + h, 0xFF0D0F12);
        double db = level <= 1e-5 ? -60 : 20 * Math.log10(level);
        double f = Math.max(0, Math.min(1, (db + 48) / 48));
        int fill = (int) Math.round(h * f);
        for (int i = 0; i < fill; i += 2) {
            double at = i / (double) h * 48 - 48;
            int col = at > -1 ? Ui.RED : at > -6 ? Ui.AMBER : Ui.GREEN;
            g.fill(x, y + h - i - 1, x + w, y + h - i, col);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Level of a fader in dB, without "-0" (0 dB at the top). */
    private static String dbShort(float v) {
        if (v <= 0.001f) return "-∞";
        long d = Math.round(20 * Math.log10(v));
        return d == 0 ? "0" : Long.toString(d);
    }

    private static String dbText(float v) {
        if (v <= 0.001f) return "-∞ dB";
        double d = 20 * Math.log10(v);
        return String.format(java.util.Locale.ROOT, "%.1f dB", Math.abs(d) < 0.05 ? 0.0 : d);
    }

    /** Full name of a group: the name given in the Delay Planner, otherwise the translated group name. */
    private static String groupName(AudioConsoleBlockEntity c, int group) {
        String alias = c.groupLabel(group);
        return alias.isEmpty() ? Component.translatable(AudioGroups.name(group)).getString() : alias;
    }

    private static String groupShort(AudioConsoleBlockEntity c,int group){
        String alias=c.groupLabel(group);
        if(alias.isEmpty())return Component.translatable("gui.concertstage.group_short."+AudioGroups.key(group)).getString();
        if(alias.matches("Delay Tower [1-3]"))return "DT"+alias.charAt(alias.length()-1);
        return "G"+(group+1);
    }
    /** Vertical fader (master = group -1): drag the cap; the value is sent on release. */
    private final class Fader extends AbstractWidget {
        private final int group;
        private boolean held;
        private float value;

        Fader(int x, int y, int group) {
            super(x, y, 22, 134, Component.empty());
            this.group = group;
        }

        private float current() {
            AudioConsoleBlockEntity c = console();
            if (held || c == null) return value;
            return group < 0 ? c.master() : c.fader(group);
        }

        private void setFrom(double my) {
            value = (float) Math.max(0, Math.min(1, 1 - (my - getY() - 4) / (height - 8)));
        }

        @Override
        public void onClick(double mx, double my) {
            held = true;
            setFrom(my);
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            setFrom(my);
        }

        @Override
        public void onRelease(double mx, double my) {
            if (!held) return;
            held = false;
            if (group < 0) cmd(AudioNet.MASTER, 0, value); else cmd(AudioNet.FADER, group, value);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int x = getX(), y = getY();
            float v = current();
            var console=RemoteConsoles.get(pos);
            if(group<0)setTooltip(net.minecraft.client.gui.components.Tooltip.create(Tr.tip("fader_master",dbText(v))));
            else if(console!=null)setTooltip(net.minecraft.client.gui.components.Tooltip.create(Tr.tip("fader_group",groupName(console,group),dbText(v),String.format(java.util.Locale.ROOT,"%.2f",console.groupDelay(group)))));
            int col = group < 0 ? Ui.TEXT : AudioGroups.COLORS[group];
            g.fill(x + 9, y + 4, x + 13, y + height - 4, 0xFF0D0F12);
            int cy = y + 4 + (int) Math.round((1 - v) * (height - 8));
            g.fill(x + 10, cy, x + 12, y + height - 4, Ui.alpha(col, 0xA0));
            for (int t = 0; t <= 4; t++) g.fill(x + 2, y + 4 + t * (height - 8) / 4, x + 6, y + 5 + t * (height - 8) / 4, Ui.LINE);
            boolean hover = isHoveredOrFocused() || held;
            g.fill(x + 1, cy - 4, x + 21, cy + 4, hover ? 0xFFE8EAED : 0xFFB9C0C8);
            g.fill(x + 1, cy, x + 21, cy + 1, col);
            String db = dbShort(v);
            g.drawCenteredString(Minecraft.getInstance().font, db, x + 11, y + height + 2, Ui.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
