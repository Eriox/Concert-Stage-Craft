package com.concertstage.client.vision;

import com.concertstage.client.ui.Ui;
import com.concertstage.vision.CameraBlockEntity;
import com.concertstage.vision.CameraKind;
import com.concertstage.vision.OutputState;
import com.concertstage.vision.VisionNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * CSC 1.3.0 - control of a camera: number and name (as shown in the regie), pan / tilt / zoom (and boom / head pan for the crane),
 * eight positions (click: recall, Shift + click: store), and its live picture.
 */
public class CameraScreen extends Screen {
    private static final int W = 400, H = 236, ACCENT = Ui.BLUE;
    private final BlockPos pos;
    private EditBox name;
    private Ui.Slider number;

    public CameraScreen(BlockPos pos) {
        super(Component.translatable("gui.concertstage.lv.camera_title"));
        this.pos = pos;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private CameraBlockEntity be() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof CameraBlockEntity c ? c : null;
    }

    private void move(int axis, float v) {
        VisionClient.send(VisionClient.cam(pos, VisionNet.K_MOVE).varInt(1 << axis).f32(v));
    }

    @Override
    protected void init() {
        CameraBlockEntity be = be();
        if (be == null) return;
        CameraKind k = be.kind();
        int x = left() + 12, y = top() + 36, col = 170;
        name = new EditBox(font, x, y + 10, col - 40, 16, Component.translatable("gui.concertstage.lv.camera_name"));
        name.setMaxLength(32);
        name.setValue(be.name);
        addRenderableWidget(name);
        number = addRenderableWidget(new Ui.Slider(x, y + 30, col, 22, Component.literal("N°"), 1, 99, be.number, ACCENT, v -> Integer.toString(v), v -> { }));
        addRenderableWidget(new Ui.Button(x + col - 36, y + 10, 36, 16, () -> Component.literal("OK"), ACCENT, Ui.Style.NORMAL,
            () -> VisionClient.send(VisionClient.cam(pos, VisionNet.K_NAME).varInt(number.get()).str(name.getValue()))));
        int sy = y + 58;
        addRenderableWidget(new Ui.Slider(x, sy, col, 22, Component.translatable("gui.concertstage.lv.pan"), Math.round(-k.panMax), Math.round(k.panMax),
            Math.round(be.target(0)), ACCENT, v -> v + "°", v -> move(0, v)));
        addRenderableWidget(new Ui.Slider(x, sy + 26, col, 22, Component.translatable("gui.concertstage.lv.tilt"), Math.round(k.tiltMin), Math.round(k.tiltMax),
            Math.round(be.target(1)), ACCENT, v -> v + "°", v -> move(1, v)));
        addRenderableWidget(new Ui.Slider(x, sy + 52, col, 22, Component.translatable("gui.concertstage.lv.zoom"), 0, 100,
            Math.round(be.target(2) * 100), ACCENT, v -> String.format("%.1fx", Math.pow(60.0 / 7.0, v / 100.0)), v -> move(2, v / 100f)));
        int ny = sy + 78;
        if (k == CameraKind.CRANE) {
            addRenderableWidget(new Ui.Slider(x, ny, col, 22, Component.translatable("gui.concertstage.lv.boom"), Math.round(k.boomMin), Math.round(k.boomMax),
                Math.round(be.target(3)), ACCENT, v -> v + "°", v -> move(3, v)));
            addRenderableWidget(new Ui.Slider(x, ny + 26, col, 22, Component.translatable("gui.concertstage.lv.head_pan"), Math.round(-k.headPanMax), Math.round(k.headPanMax),
                Math.round(be.target(4)), ACCENT, v -> v + "°", v -> move(4, v)));
            ny += 52;
        }
        // presets
        int px = left() + W - 12 - 200, py = top() + 158, pw = 47;
        for (int i = 0; i < CameraBlockEntity.PRESETS; i++) {
            final int p = i;
            boolean stored = be.presets[i] != null;
            addRenderableWidget(new Ui.Button(px + (i % 4) * (pw + 4), py + (i / 4) * 18, pw, 16, () -> Component.literal("POS " + (p + 1)), ACCENT,
                Ui.Style.NORMAL, () -> {
                    if (Screen.hasShiftDown()) VisionClient.send(VisionClient.cam(pos, VisionNet.K_STORE).varInt(p));
                    else VisionClient.send(VisionClient.cam(pos, VisionNet.K_RECALL).varInt(p));
                    minecraft.tell(this::rebuildWidgets);
                }).selected(stored));
        }
        // rev. 4c: quality and images per second of this camera, kept on every screen (Auto = what the screens ask)
        int vx = left() + W - 12 - 200;
        com.concertstage.client.ui.Tr.help(addRenderableWidget(new Ui.Button(vx, top() + H - 24, 62, 16,
            () -> Component.translatable("gui.concertstage.lv.cam_quality", be() == null || be().videoHeight == 0
                ? Component.translatable("gui.concertstage.lv.cam_auto") : Component.literal(be().videoHeight + "p")),
            ACCENT, Ui.Style.NORMAL, () -> video(1, 0))), "lv.cam_quality");
        com.concertstage.client.ui.Tr.help(addRenderableWidget(new Ui.Button(vx + 66, top() + H - 24, 62, 16,
            () -> Component.translatable("gui.concertstage.lv.cam_fps", be() == null || be().videoFps == 0
                ? Component.translatable("gui.concertstage.lv.cam_auto") : Component.literal(Integer.toString(be().videoFps))),
            ACCENT, Ui.Style.NORMAL, () -> video(0, 1))), "lv.cam_fps");
        addRenderableWidget(new Ui.Button(left() + W - 82, top() + H - 24, 70, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    /** Rev. 4c: next quality / images per second of the camera (shown at once, then confirmed by the server). */
    private void video(int dh, int df) {
        CameraBlockEntity be = be();
        if (be == null) return;
        be.videoHeight = next(CameraBlockEntity.HEIGHTS, be.videoHeight, dh);
        be.videoFps = next(CameraBlockEntity.FPS, be.videoFps, df);
        VisionClient.send(VisionClient.cam(pos, VisionNet.K_VIDEO).varInt(be.videoHeight).varInt(be.videoFps));
    }

    private static int next(int[] values, int v, int step) {
        if (step == 0) return v;
        int i = 0;
        while (i < values.length && values[i] != v) i++;
        return values[(i + step) % values.length];
    }

    @Override
    public void tick() {
        if (name != null) name.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        CameraBlockEntity be = be();
        Ui.panel(g, left(), top(), W, H, ACCENT, title, be == null ? Component.translatable("gui.concertstage.lv.camera_gone")
            : Component.translatable("gui.concertstage.lv.camera_sub", Component.translatable("gui.concertstage.lv.kind_" + be.kind().name().toLowerCase(java.util.Locale.ROOT)), be.number));
        int x = left() + 12, y = top() + 36;
        Ui.section(g, x, y, 170, Component.translatable("gui.concertstage.lv.camera_name"));
        int mw = 200, mh = 104, mx = left() + W - 12 - mw, my = top() + 38;
        GuiVideo.monitor(g, mx, my, mw, mh, OutputState.cam(pos), null, be == null ? "" : be.label(), Ui.BLUE);
        Ui.section(g, mx, top() + 152, mw, Component.translatable("gui.concertstage.lv.presets"));
        g.drawString(font, Component.translatable("gui.concertstage.lv.presets_hint"), mx, top() + 196, Ui.MUTED, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
