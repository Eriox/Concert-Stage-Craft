package com.concertstage.client.vision;

import com.concertstage.client.ui.Ui;
import com.concertstage.vision.OutputState;
import com.concertstage.vision.VisionNet;
import com.concertstage.vision.VisionProcessorBlockEntity;
import com.concertstage.vision.WallData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * CSC 1.3.0 - configuration of an LED wall (right click on any of its panels): name, size and panels, brightness, the processor
 * output that feeds it, and a live view of what it shows.
 */
public class WallScreen extends Screen {
    private static final int W = 360, H = 300, ACCENT = Ui.ORANGE;
    private static final int[] QUALITY = {0, 270, 360, 540, 720, 1080}, FPS = {0, 5, 10, 15, 20, 30, 60};
    private final BlockPos clicked;
    private final long id;
    private EditBox name;
    private int procIndex;
    private String sig = "";

    public WallScreen(BlockPos clicked, long id) {
        super(Component.translatable("gui.concertstage.lv.wall_title"));
        this.clicked = clicked;
        this.id = id;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private WallData.Wall wall() { return VisionClient.WALLS.get(id); }

    /** Processors known to this client: those the server sent, and those loaded nearby. */
    private List<Long> processors() {
        List<Long> l = new ArrayList<>(VisionClient.OUTPUTS.keySet());
        var level = minecraft.level;
        if (level != null) {
            BlockPos c = clicked;
            for (int dx = -48; dx <= 48; dx += 16) for (int dz = -48; dz <= 48; dz += 16) {
                var ch = level.getChunkSource().getChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4, false);
                if (ch == null) continue;
                for (var be : ch.getBlockEntities().values())
                    if (be instanceof VisionProcessorBlockEntity && !l.contains(be.getBlockPos().asLong())) l.add(be.getBlockPos().asLong());
            }
        }
        l.sort(Long::compare);
        return l;
    }

    private String signature() {
        WallData.Wall w = wall();
        return w == null ? "-" : w.name + "|" + w.brightness + "|" + w.processor + "|" + w.output + "|" + w.count + "|" + w.angle + "|" + w.pivot + "|" + w.quality + "|" + w.fps;
    }

    @Override
    public void tick() {
        if (name != null) name.tick();
        String s = signature();
        if (!s.equals(sig) && !isDragging() && (name == null || !name.isFocused())) rebuildWidgets();
    }

    @Override
    protected void init() {
        sig = signature();
        WallData.Wall w = wall();
        int x = left() + 12, y = top() + 36, col = 160;
        if (w == null) {
            addRenderableWidget(new Ui.Button(left() + W - 82, top() + H - 24, 70, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
            return;
        }
        name = new EditBox(font, x, y + 10, col - 40, 16, Component.translatable("gui.concertstage.lv.wall_name"));
        name.setMaxLength(48);
        name.setValue(w.name);
        addRenderableWidget(name);
        addRenderableWidget(new Ui.Button(x + col - 36, y + 10, 36, 16, () -> Component.literal("OK"), ACCENT, Ui.Style.NORMAL,
            () -> VisionClient.send(VisionClient.wall(id, VisionNet.W_NAME).str(name.getValue()))));
        addRenderableWidget(new Ui.Slider(x, y + 74, col, 22, Component.translatable("gui.concertstage.lv.brightness"), 0, 100, w.brightness, ACCENT,
            v -> v + " %", v -> VisionClient.send(VisionClient.wall(id, VisionNet.W_BRIGHTNESS).varInt(v))));
        // source: processor + output
        List<Long> procs = processors();
        int cur = procs.indexOf(w.processor);
        if (cur >= 0) procIndex = cur;
        procIndex = procs.isEmpty() ? 0 : Math.min(procIndex, procs.size() - 1);
        int py = y + 112;
        addRenderableWidget(new Ui.Button(x, py, col, 16, () -> {
            if (procs.isEmpty()) return Component.translatable("gui.concertstage.lv.no_processor");
            BlockPos p = BlockPos.of(procs.get(procIndex));
            return Component.literal("VP1  " + p.getX() + " " + p.getY() + " " + p.getZ());
        }, ACCENT, Ui.Style.NORMAL, () -> { if (!procs.isEmpty()) { procIndex = (procIndex + 1) % procs.size(); } }));
        for (int i = 0; i < VisionProcessorBlockEntity.OUTPUTS; i++) {
            final int k = i;
            boolean on = !procs.isEmpty() && w.processor == procs.get(procIndex) && w.output == k;
            addRenderableWidget(new Ui.Button(x + i * 41, py + 20, 37, 16, () -> Component.literal("OUT " + (k + 1)), ACCENT, Ui.Style.NORMAL, () -> {
                if (procs.isEmpty()) return;
                VisionClient.send(VisionClient.proc(BlockPos.of(procs.get(procIndex)), VisionNet.P_ASSIGN, k).sLong(id));
            }).selected(on));
        }
        addRenderableWidget(new Ui.Button(x, py + 40, 78, 16, () -> Component.translatable("gui.concertstage.lv.disconnect"), Ui.RED, Ui.Style.NORMAL, () -> {
            if (w.processor != 0) VisionClient.send(VisionClient.proc(BlockPos.of(w.processor), VisionNet.P_UNASSIGN, 0).sLong(id));
        }));
        addRenderableWidget(new Ui.Button(x + 82, py + 40, 78, 16, () -> Component.translatable("gui.concertstage.lv.open_regie"), ACCENT, Ui.Style.NORMAL, () -> {
            if (w.processor != 0) minecraft.setScreen(new RegieScreen(BlockPos.of(w.processor)));
        }));
        // rev. 2: the source of the wall chosen here, under its picture: each click puts the next camera (then BARS, BLACK) on the
        // processor output that feeds the wall, without opening the regie
        if (w.processor != 0) {
            addRenderableWidget(new Ui.Button(left() + 190, top() + 166, W - 202, 16, () -> {
                OutputState o = VisionClient.output(wall());
                return Component.translatable("gui.concertstage.lv.next_source", VisionClient.label(o == null ? OutputState.BLACK : o.program));
            }, ACCENT, Ui.Style.ACCENT, this::nextSource));
        }
        // rev. 3: angle of the wall (and its axis), quality and images per second of the camera image it shows
        int ay = top() + 222;
        addRenderableWidget(new Ui.Slider(x, ay, col, 22, Component.translatable("gui.concertstage.lv.angle"), -90, 90, Math.round(w.angle), ACCENT,
            v -> v + "°", v -> VisionClient.send(VisionClient.wall(id, VisionNet.W_ANGLE).f32(v).varInt(wall().pivot))));
        addRenderableWidget(new Ui.Button(x, ay + 26, col, 16, () -> Component.translatable("gui.concertstage.lv.pivot_" + wall().pivot), ACCENT, Ui.Style.NORMAL,
            () -> VisionClient.send(VisionClient.wall(id, VisionNet.W_ANGLE).f32(wall().angle).varInt((wall().pivot + 1) % 3))));
        int rx = left() + 190, rw = W - 202;
        addRenderableWidget(new Ui.Button(rx, ay + 4, rw, 16, () -> wall().quality == 0 ? Component.translatable("gui.concertstage.lv.quality_auto")
            : Component.translatable("gui.concertstage.lv.quality", wall().quality * 16 / 9, wall().quality), ACCENT, Ui.Style.NORMAL,
            () -> VisionClient.send(VisionClient.wall(id, VisionNet.W_QUALITY).varInt(next(QUALITY, wall().quality)).varInt(wall().fps))));
        addRenderableWidget(new Ui.Button(rx, ay + 26, rw, 16, () -> wall().fps == 0 ? Component.translatable("gui.concertstage.lv.fps_auto")
            : Component.translatable("gui.concertstage.lv.fps", wall().fps), ACCENT, Ui.Style.NORMAL,
            () -> VisionClient.send(VisionClient.wall(id, VisionNet.W_QUALITY).varInt(wall().quality).varInt(next(FPS, wall().fps)))));
        addRenderableWidget(new Ui.Button(left() + W - 82, top() + H - 24, 70, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    private static int next(int[] list, int cur) {
        for (int i = 0; i < list.length; i++) if (list[i] == cur) return list[(i + 1) % list.length];
        return list[0];
    }

    /** Rev. 2: the next source on the output feeding this wall: the cameras by number, then BARS and BLACK. */
    private void nextSource() {
        WallData.Wall w = wall();
        if (w == null || w.processor == 0) return;
        List<String> list = new ArrayList<>();
        VisionClient.CAMERAS.values().stream().sorted((a, b) -> Integer.compare(a.number(), b.number()))
            .forEach(c -> list.add(OutputState.cam(BlockPos.of(c.pos()))));
        list.add(OutputState.BARS);
        list.add(OutputState.BLACK);
        OutputState o = VisionClient.output(w);
        int i = o == null ? -1 : list.indexOf(o.program);
        String next = list.get((i + 1) % list.size());
        VisionClient.send(VisionClient.proc(BlockPos.of(w.processor), VisionNet.P_PROGRAM, w.output).str(next));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        WallData.Wall w = wall();
        Ui.panel(g, left(), top(), W, H, ACCENT, title, w == null ? Component.translatable("gui.concertstage.lv.wall_unknown")
            : Component.translatable("gui.concertstage.lv.wall_sub", w.width(), w.height(), w.count));
        int x = left() + 12, y = top() + 36;
        Ui.section(g, x, y, 160, Component.translatable("gui.concertstage.lv.wall_name"));
        if (w != null) {
            g.drawString(font, Component.translatable("gui.concertstage.lv.wall_info", w.width(), w.height(), w.count), x, y + 32, Ui.MUTED, false);
            g.drawString(font, Component.translatable("gui.concertstage.lv.wall_res", w.width() * 64, w.height() * 64,
                Component.translatable("gui.concertstage.lv.facing_" + w.facing.getName())), x, y + 43, Ui.MUTED, false);
            Ui.section(g, x, y + 62, 160, Component.translatable("gui.concertstage.lv.brightness"));
            Ui.section(g, x, y + 100, 160, Component.translatable("gui.concertstage.lv.source"));
            Ui.section(g, x, top() + 212, 160, Component.translatable("gui.concertstage.lv.position"));
            Ui.section(g, left() + 190, top() + 212, W - 202, Component.translatable("gui.concertstage.lv.camera_image"));
            // measured on this computer: what the camera image of the wall really gets (the frame budget can lower it)
            OutputState oo = VisionClient.output(w);
            if (oo != null && oo.program != null && oo.program.startsWith("cam:")) {
                int[] a = CameraFeeds.actual(OutputState.camPos(oo.program));
                g.drawString(font, a[0] == 0 ? Component.translatable("gui.concertstage.lv.actual_none")
                    : Component.translatable("gui.concertstage.lv.actual", a[0], a[1]), left() + 190, top() + 270, Ui.MUTED, false);
            }
            // preview: the wall's picture in its proportions
            int px = left() + 190, py = top() + 40, pw = W - 202, ph = 120;
            float aspect = w.width() / (float) w.height();
            int vw = pw, vh = Math.round(pw / aspect);
            if (vh > ph) { vh = ph; vw = Math.round(ph * aspect); }
            int vx = px + (pw - vw) / 2, vy = py + (ph - vh) / 2;
            OutputState o = VisionClient.output(w);
            GuiVideo.monitor(g, vx, vy, vw, vh, o == null ? OutputState.BLACK : o.program, o,
                o == null ? "—" : VisionClient.label(o.program), o == null ? Ui.LINE : Ui.RED);
            // panels missing in the rectangle
            if (w.count < w.width() * w.height()) {
                float cw = vw / (float) w.width(), chh = vh / (float) w.height();
                boolean rev = w.facing == net.minecraft.core.Direction.NORTH || w.facing == net.minecraft.core.Direction.EAST;
                for (int v = w.v0; v <= w.v1; v++) for (int u = w.u0; u <= w.u1; u++) {
                    if (w.has(u, v)) continue;
                    int col = rev ? w.u1 - u : u - w.u0, row = w.v1 - v;
                    g.fill(vx + Math.round(col * cw), vy + Math.round(row * chh), vx + Math.round((col + 1) * cw), vy + Math.round((row + 1) * chh), 0xFF15171A);
                }
            }
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
