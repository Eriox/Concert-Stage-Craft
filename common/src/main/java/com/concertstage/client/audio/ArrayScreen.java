package com.concertstage.client.audio;

import com.concertstage.client.ui.Tr;
import com.concertstage.audio.SpeakerBlockEntity;
import com.concertstage.audio.SpeakerModels;
import com.concertstage.audio.net.AudioNet;
import com.concertstage.client.ui.Ui;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * CSC Array Tool screen (orange theme): a 3D view of the array (drag to turn it, scroll to zoom), a side view with the vertical
 * coverage of every cabinet, the list of cabinets with their splay angle, and the yaw / tilt of the whole array. Nothing changes in
 * the world until "Apply".
 */
public class ArrayScreen extends Screen {
    private static final int W = 470, H = 262, ACCENT = Ui.ORANGE;
    private final BlockPos pos;
    private final boolean hanging;
    private final double lift, base;
    private final String support;
    private final double drop;
    private final List<SpeakerBlockEntity.Cabinet> cabinets = new ArrayList<>();
    private final float[] splays;
    private final float[] original;
    private float yaw, tilt, yaw0, tilt0;
    private int selected = 1, scroll;
    private float rotX = 18, rotY = -32, zoom = 1;
    private float targetX=18,targetY=-32,targetZoom=1, visualScroll;
    private long lastFrame=System.nanoTime();
    private boolean dragging;
    private final List<float[]> pickBounds = new ArrayList<>();
    private Ui.Slider splaySlider, yawSlider, tiltSlider, commonSlider;
    private final String heading;
    private List<SpeakerBlockEntity.Pose> cachedPreview;
    private float cachedTilt = Float.NaN;
    private EditBox preciseSplay, preciseYaw, preciseTilt;
    private boolean syncing;

    /** Continuous local feedback, with the existing deferred Apply packet. */
    private static final class LiveSlider extends Ui.Slider {
        private IntConsumer live;
        LiveSlider(int x, int y, int w, int h, Component label, int min, int max, int value,
                   int accent, IntFunction<String> text, IntConsumer commit) {
            super(x, y, w, h, label, min, max, value, accent, text, commit);
            live = commit;
        }
        @Override protected void applyValue() { if (live != null) live.accept(get()); }
    }

    public ArrayScreen(BlockPos pos, SpeakerBlockEntity be) {
        super(Component.translatable("gui.concertstage.array_title"));
        this.pos = pos;
        this.hanging = be.hanging();
        this.lift = be.groundLift();
        this.base = be.base();
        this.support = be.support();
        this.drop = be.supportDrop();
        cabinets.addAll(be.cabinets());
        splays = new float[cabinets.size()];
        for (int i = 0; i < splays.length; i++) splays[i] = cabinets.get(i).splay();
        original = splays.clone();
        yaw = yaw0 = be.yaw();
        tilt = tilt0 = be.tilt();
        SpeakerModels.Model m = be.firstModel();
        heading = (m == null ? "" : Component.translatable("item.concertstage." + m.id()).getString()) + "  ×" + cabinets.size()
            + (hanging ? "" : "  · " + Component.translatable("gui.concertstage.array_stack").getString());
        if (cabinets.size() < 2) selected = 0;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private boolean isSubs() {
        SpeakerModels.Model m = cabinets.isEmpty() ? null : SpeakerModels.get(cabinets.get(0).model());
        return m != null && m.isSub();
    }
    private float splayMax(int i) {
        SpeakerModels.Model m = SpeakerModels.get(cabinets.get(i).model());
        return m == null ? 0 : (float) m.splayMax();
    }

    private List<SpeakerBlockEntity.Pose> preview() {
        if (cachedPreview != null && cachedTilt == tilt) return cachedPreview;
        List<SpeakerBlockEntity.Cabinet> list = new ArrayList<>();
        for (int i = 0; i < cabinets.size(); i++) list.add(new SpeakerBlockEntity.Cabinet(cabinets.get(i).id(), cabinets.get(i).model(), i == 0 ? 0 : splays[i]));
        cachedTilt = tilt;
        return cachedPreview = SpeakerBlockEntity.poses(list, tilt, hanging, lift, drop, base);
    }

    @Override
    protected void init() {
        int x = left() + W - 150, y = top() + 34, w = 140;
        boolean subs = isSubs();
        // cabinet list arrows
        addRenderableWidget(new Ui.Button(x, y + 92, 20, 14, () -> Component.literal("▲"), ACCENT, Ui.Style.GHOST, () -> { scroll = Math.max(0, scroll - 1); }));
        addRenderableWidget(new Ui.Button(x + 22, y + 92, 20, 14, () -> Component.literal("▼"), ACCENT, Ui.Style.GHOST,
            () -> { scroll = Math.max(0, Math.min(cabinets.size() - 6, scroll + 1)); }));
        // splay of the selected cabinet
        addRenderableWidget(new Ui.Button(x + 48, y + 92, 20, 14, () -> Component.literal("−"), ACCENT, Ui.Style.NORMAL, () -> step(-0.1f))).active = !subs;
        addRenderableWidget(new Ui.Button(x + 120, y + 92, 20, 14, () -> Component.literal("+"), ACCENT, Ui.Style.NORMAL, () -> step(0.1f))).active = !subs;
        splaySlider = addRenderableWidget(new LiveSlider(x, y + 110, w, 20, Component.translatable("gui.concertstage.array_splay"), 0, 1000,
            Math.round(splays[Math.min(selected, splays.length - 1)] * 100), ACCENT, v -> String.format("%.2f°", v / 100f), v -> setSplay(selected, v / 100f)));
        splaySlider.active = !subs && selected > 0;
        commonSlider = addRenderableWidget(new LiveSlider(x, y + 134, w, 20, Component.translatable("gui.concertstage.array_common"), 0, 1000,
            Math.round(common() * 100), ACCENT, v -> String.format("%.2f°", v / 100f), v -> { for (int i = 1; i < splays.length; i++) setSplay(i, v / 100f); syncSliders(); }));
        commonSlider.active = !subs && cabinets.size() > 1;
        yawSlider = addRenderableWidget(new LiveSlider(x, y + 158, w, 20, Component.translatable("gui.concertstage.array_yaw"), -18000, 18000, Math.round(yaw * 100), ACCENT,
            v -> String.format("%.2f°", v / 100f), v -> setYaw(v / 100f)));
        tiltSlider = addRenderableWidget(new LiveSlider(x, y + 182, w, 20, Component.translatable("gui.concertstage.array_tilt"), -1500, 1500, Math.round(tilt * 100), ACCENT,
            v -> String.format("%+.2f°", v / 100f), v -> setTilt(v / 100f)));
        preciseSplay = addRenderableWidget(new EditBox(font, x + 71, y + 92, 45, 14, Component.literal(Tr.t("Splay (degrees)"))));
        preciseSplay.setMaxLength(7);
        preciseSplay.setValue(String.format(java.util.Locale.ROOT, "%.2f", splays[selected]));
        preciseSplay.setResponder(text -> {
            if (syncing) return;
            try { setSplay(selected, Float.parseFloat(text.replace(',', '.'))); }
            catch (NumberFormatException ignored) { }
        });
        preciseSplay.setEditable(!subs && selected > 0);
        preciseYaw = angleField(x + 95, y + 158, Tr.t("Yaw (degrees)"), yaw, this::setYaw);
        preciseTilt = angleField(x + 95, y + 182, Tr.t("Tilt (degrees)"), tilt, this::setTilt);
        int bw = (w - 6) / 3;
        addRenderableWidget(new Ui.Button(x, y + 208, bw, 16, () -> Component.translatable("gui.concertstage.array_reset"), ACCENT, Ui.Style.GHOST, this::reset));
        addRenderableWidget(new Ui.Button(x + bw + 3, y + 208, bw, 16, () -> Component.translatable("gui.concertstage.cancel"), ACCENT, Ui.Style.GHOST, this::onClose));
        addRenderableWidget(new Ui.Button(x + 2 * (bw + 3), y + 208, bw, 16, () -> Component.translatable("gui.concertstage.array_apply"), ACCENT, Ui.Style.ACCENT, this::apply));
    }

    private EditBox angleField(int x, int y, String label, float initial, java.util.function.Consumer<Float> apply) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, 45, 12, Component.literal(label)));
        box.setMaxLength(8);
        box.setValue(String.format(java.util.Locale.ROOT, "%.2f", initial));
        box.setResponder(text -> {
            if (syncing) return;
            try { apply.accept(Float.parseFloat(text.replace(',', '.'))); }
            catch (NumberFormatException ignored) { }
        });
        return box;
    }

    private void mirror(EditBox box, float value) {
        if (box == null || box.isFocused()) return;
        syncing = true;
        box.setValue(String.format(java.util.Locale.ROOT, "%.2f", value));
        syncing = false;
    }

    private void setYaw(float value) {
        if (!Float.isFinite(value)) return;
        yaw = Math.max(-180, Math.min(180, value));
        if (yawSlider != null) yawSlider.set(Math.round(yaw * 100));
        mirror(preciseYaw, yaw);
    }

    private void setTilt(float value) {
        if (!Float.isFinite(value)) return;
        tilt = Math.max(-15, Math.min(15, value));
        if (tiltSlider != null) tiltSlider.set(Math.round(tilt * 100));
        mirror(preciseTilt, tilt);
    }

    private float common() {
        if (splays.length < 2) return 0;
        float s = 0;
        for (int i = 1; i < splays.length; i++) s += splays[i];
        return s / (splays.length - 1);
    }

    private void step(float d) {
        if (selected <= 0) return;
        setSplay(selected, splays[selected] + d);
        syncSliders();
    }

    private void setSplay(int i, float v) {
        if (i <= 0 || i >= splays.length) return;
        if (!Float.isFinite(v)) return;
        cachedPreview = null;
        splays[i] = Math.max(0, Math.min(splayMax(i), Math.round(v * 100) / 100f));
        if (splaySlider != null && i == selected) splaySlider.set(Math.round(splays[i] * 100));
        if (commonSlider != null) commonSlider.set(Math.round(common() * 100));
        if (preciseSplay != null && !preciseSplay.isFocused() && i == selected) {
            syncing = true;
            preciseSplay.setValue(String.format(java.util.Locale.ROOT, "%.2f", splays[i]));
            syncing = false;
        }
    }

    private void syncSliders() {
        if (preciseSplay != null) {
            syncing = true;
            preciseSplay.setValue(String.format(java.util.Locale.ROOT, "%.2f", splays[selected]));
            preciseSplay.setEditable(!isSubs() && selected > 0);
            syncing = false;
        }
        if (splaySlider != null) splaySlider.set(Math.round(splays[Math.min(Math.max(selected, 0), splays.length - 1)] * 100));
        if (commonSlider != null) commonSlider.set(Math.round(common() * 100));
    }

    private void reset() {
        cachedPreview = null;
        for (int i = 0; i < splays.length; i++) splays[i] = original[i];
        setYaw(yaw0);
        setTilt(tilt0);
        yawSlider.set(Math.round(yaw * 100));
        tiltSlider.set(Math.round(tilt * 100));
        syncSliders();
    }

    private void apply() {
        AudioNet.arrayAngles(pos, yaw, tilt, splays);
        onClose();
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int vx = left() + 10, vy = top() + 34;
        if (button == 0 && mx >= vx && mx < vx + 170 && my >= vy && my < vy + H - 44) {
            float nearest = -Float.MAX_VALUE;
            for (int i = 0; i < pickBounds.size(); i++) {
                float[] b = pickBounds.get(i);
                if (mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3] && b[4] > nearest) {
                    selected = i; nearest = b[4];
                }
            }
            scroll = Math.max(0, Math.min(scroll, selected));
            if (selected >= scroll + 6) scroll = selected - 5;
            syncSliders();
            splaySlider.active = !isSubs() && selected > 0;
            dragging = true; return true;
        }
        // Pick against the interpolated list positions, not its target scroll index.
        int x=left()+W-150,y=top()+46;
        if(mx>=x&&mx<x+140&&my>=y&&my<y+72) {
            int i=(int)Math.floor((my-y)/12+visualScroll);
            if(i>=0&&i<cabinets.size()) {selected=i;syncSliders();splaySlider.active=!isSubs()&&selected>0;return true;}
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            targetY += dx * 1.2f;
            targetX = Math.max(-80, Math.min(80, targetX + (float) dy));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int vx = left() + 10, vy = top() + 34;
        if (mx >= vx && mx < vx + 170 && my >= vy && my < vy + H - 44) { targetZoom = Math.max(0.4f, Math.min(4f, targetZoom * (delta > 0 ? 1.15f : 1 / 1.15f))); return true; }
        if (mx >= left() + W - 150) { scroll = Math.max(0, Math.min(Math.max(0, cabinets.size() - 6), scroll - (int) Math.signum(delta))); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now=System.nanoTime();float alpha=(float)(1-Math.exp(-Math.min(.1,(now-lastFrame)/1e9)/.075));lastFrame=now;
        rotX+=(targetX-rotX)*alpha;rotY+=(targetY-rotY)*alpha;zoom+=(targetZoom-zoom)*alpha;
        visualScroll+=(scroll-visualScroll)*alpha;
        if(Math.abs(visualScroll-scroll)<.001)visualScroll=scroll;
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, Component.literal(heading), Component.translatable(hanging ? "gui.concertstage.array_subtitle_flown" : "gui.concertstage.array_subtitle_stack"));
        List<SpeakerBlockEntity.Pose> poses = preview();
        view3d(g, poses);
        sideView(g, poses);
        int x = left() + W - 150, y = top() + 34;
        Ui.section(g, x, y, 140, Component.translatable("gui.concertstage.array_cabinets"));
        g.enableScissor(x,y+12,x+140,y+84);
        for (int i=(int)Math.floor(visualScroll);i<(int)Math.floor(visualScroll)+7;i++) {
            if (i >= cabinets.size()) break;
            int ry = y + 12 + Math.round((i-visualScroll)*12);
            boolean sel = i == selected;
            if (sel) g.fill(x, ry, x + 140, ry + 12, Ui.alpha(ACCENT, 0x55));
            SpeakerModels.Model m = SpeakerModels.get(cabinets.get(i).model());
            String name = m == null ? "?" : m.id().replace("csc_", "").replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
            g.drawString(font, (i + 1) + "  " + name, x + 3, ry + 2, sel ? Ui.TEXT : Ui.MUTED, false);
            String a = i == 0 ? "—" : String.format("%.2f°", splays[i]);
            g.drawString(font, a, x + 137 - font.width(a), ry + 2, Ui.TEXT, false);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partial);
    }

    private void view3d(GuiGraphics g, List<SpeakerBlockEntity.Pose> poses) {
        int vx = left() + 10, vy = top() + 34, vw = 170, vh = H - 44;
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF0B0E12);
        Ui.frame(g, vx, vy, vw, vh, Ui.LINE);
        pickBounds.clear();
        if (poses.isEmpty()) return;
        double minY = 1e9, maxY = -1e9, maxW = 0;
        for (SpeakerBlockEntity.Pose p : poses) {
            minY = Math.min(minY, Math.min(p.y(), p.y() + p.model().height()));
            maxY = Math.max(maxY, Math.max(p.y(), p.y() + p.model().height()));
            maxW = Math.max(maxW, Math.max(p.model().width(), p.model().depth()));
        }
        if (hanging) maxY = Math.max(maxY, 1);
        double size = Math.max(maxY - minY, maxW) + 0.4;
        float s = (float) (Math.min(vw, vh) * 0.78 / size) * zoom;
        g.enableScissor(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(vx + vw / 2f, vy + vh / 2f, 150);
        ps.scale(s, -s, s);
        ps.mulPose(Axis.XP.rotationDegrees(rotX));
        ps.mulPose(Axis.YP.rotationDegrees(rotY));
        ps.translate(-0.5, -(minY + maxY) / 2, -0.5);
        // Project the same cabinet bounds as the renderer, retaining frontmost depth for picking.
        double yr = Math.toRadians(yaw), cy = Math.cos(yr), sy = Math.sin(yr);
        for (SpeakerBlockEntity.Pose p : poses) {
            float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            double pr = Math.toRadians(p.pitch()), cp = Math.cos(pr), sp = Math.sin(pr);
            for (int k = 0; k < 8; k++) {
                double px = ((k & 1) == 0 ? -.5 : .5) * p.model().width() + p.x();
                double py = (k & 2) == 0 ? 0 : p.model().height();
                double pz = (k & 4) == 0 ? 0 : -p.model().depth();
                double y2 = py * cp - pz * sp + p.y(), z2 = py * sp + pz * cp + p.z();
                org.joml.Vector3f point = new org.joml.Vector3f((float)(.5 + px * cy - z2 * sy),
                    (float)y2, (float)(.5 + px * sy + z2 * cy));
                ps.last().pose().transformPosition(point);
                bounds[0] = Math.min(bounds[0], point.x); bounds[1] = Math.min(bounds[1], point.y);
                bounds[2] = Math.max(bounds[2], point.x); bounds[3] = Math.max(bounds[3], point.y);
                bounds[4] = Math.max(bounds[4], point.z);
            }
            pickBounds.add(bounds);
        }
        RenderSystem.enableDepthTest();
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buf = g.bufferSource();
        SpeakerRenderer.draw(ps, t -> buf.getBuffer(RenderType.entityCutoutNoCull(t)), poses, yaw, hanging, null, LightTexture.FULL_BRIGHT,
            poses.size() > 10 ? SpeakerRenderer.LOD_FAR : SpeakerRenderer.LOD_NEAR, support, base);
        if(selected>=0&&selected<poses.size()) {
            var selectedPose=poses.get(selected);var m=selectedPose.model();
            ps.pushPose();ps.translate(.5,0,.5);ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(selectedPose.x(),selectedPose.y(),selectedPose.z());ps.mulPose(Axis.XP.rotationDegrees((float)selectedPose.pitch()));
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(ps,buf.getBuffer(RenderType.lines()),-m.width()/2,0,-m.depth(),m.width()/2,m.height(),0,1f,.55f,.1f,1f);
            ps.popPose();
        }
        buf.endBatch();
        Lighting.setupFor3DItems();
        ps.popPose();
        g.disableScissor();
        g.drawString(font, Component.translatable("gui.concertstage.array_drag"), vx + 4, vy + vh - 11, 0xFF6B7178, false);
    }

    /** Side view: the profile of each cabinet, its aim and its vertical coverage (half the vertical dispersion each side), to 30 m. */
    private void sideView(GuiGraphics g, List<SpeakerBlockEntity.Pose> poses) {
        int vx = left() + 186, vy = top() + 34, vw = 126, vh = H - 44;
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF0B0E12);
        Ui.frame(g, vx, vy, vw, vh, Ui.LINE);
        g.enableScissor(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1);
        double top = hanging ? 1 : 0, bottom = 0;
        for (SpeakerBlockEntity.Pose p : poses) { bottom = Math.min(bottom, p.y()); top = Math.max(top, p.y() + p.model().height()); }
        double span = Math.max(2.5, top - bottom + 0.6);
        double sc = (vh - 30) / span;
        int ox = vx + 26;
        int oy = vy + 16 + (int) (top * sc);                                        // the highest point near the top of the view
        java.util.function.DoubleUnaryOperator sx = z -> ox + z * sc, sy = y -> oy - y * sc;
        // coverage rays first (behind)
        double reach = 30;
        for (int i = 0; i < poses.size(); i++) {
            SpeakerBlockEntity.Pose p = poses.get(i);
            double r = Math.toRadians(p.pitch()), hy = p.model().height() / 2;
            double cz = p.z() + hy * Math.sin(r), cy = p.y() + hy * Math.cos(r);       // middle of the front face
            double half = p.model().vDispersion() / 2;
            int col = Ui.alpha(i == selected ? ACCENT : 0xFF5B8CFF, i == selected ? 0x90 : 0x40);
            for (double a : new double[] {p.pitch() - half, p.pitch(), p.pitch() + half}) {
                double ar = Math.toRadians(a);
                line(g, sx.applyAsDouble(cz), sy.applyAsDouble(cy), sx.applyAsDouble(cz + reach * Math.cos(ar)), sy.applyAsDouble(cy - reach * Math.sin(ar)),
                    a == p.pitch() ? Ui.alpha(col, 0xC0) : col);
            }
        }
        // truss / floor
        if (hanging) g.fill(vx + 4, (int) sy.applyAsDouble(1) - 3, vx + 50, (int) sy.applyAsDouble(1), 0xFF8A9099);
        else g.fill(vx + 4, (int) sy.applyAsDouble(0), vx + vw - 4, (int) sy.applyAsDouble(0) + 1, 0xFF8A9099);
        // cabinets: their side outline (front at +z)
        for (int i = 0; i < poses.size(); i++) {
            SpeakerBlockEntity.Pose p = poses.get(i);
            double r = Math.toRadians(p.pitch()), c = Math.cos(r), s = Math.sin(r);
            double h = p.model().height(), d = p.model().depth();
            double[][] pts = {{0, 0}, {h, 0}, {h, -d}, {0, -d}};          // (y, z) in the cabinet frame
            double[] px = new double[4], py = new double[4];
            for (int k = 0; k < 4; k++) {
                double yy = pts[k][0] * c - pts[k][1] * s, zz = pts[k][0] * s + pts[k][1] * c;
                px[k] = sx.applyAsDouble(p.z() + zz);
                py[k] = sy.applyAsDouble(p.y() + yy);
            }
            int col = i == selected ? ACCENT : 0xFFCDD1D6;
            for (int k = 0; k < 4; k++) line(g, px[k], py[k], px[(k + 1) % 4], py[(k + 1) % 4], col);
        }
        g.disableScissor();
        g.drawString(font, Component.translatable("gui.concertstage.array_side"), vx + 4, vy + 4, Ui.MUTED, false);
        if (!poses.isEmpty()) {
            double spread = Math.abs(poses.get(poses.size() - 1).pitch() - poses.get(0).pitch());
            g.drawString(font, String.format("%.1f° → %.1f°", poses.get(0).pitch(), poses.get(poses.size() - 1).pitch()), vx + 4, vy + vh - 22, Ui.TEXT, false);
            g.drawString(font, Component.translatable("gui.concertstage.array_spread", String.format("%.1f", spread)), vx + 4, vy + vh - 11, Ui.MUTED, false);
        }
        // compass: the aim of the array seen from above (north up)
        int cx = vx + vw - 16, cy = vy + 16;
        g.fill(cx - 10, cy - 10, cx + 11, cy + 11, 0xFF11161C);
        g.drawCenteredString(font, "N", cx, cy - 9, 0xFF6B7178);
        double yr = Math.toRadians(yaw);
        line(g, cx, cy, cx - Math.sin(yr) * 9, cy + Math.cos(yr) * 9, ACCENT);
    }

    /** A 1-pixel line made of small fills. */
    private static void line(GuiGraphics g, double x0, double y0, double x1, double y1, int color) {
        double len = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int n = (int) Math.min(400, Math.max(1, len));
        for (int i = 0; i <= n; i++) {
            int x = (int) Math.round(x0 + (x1 - x0) * i / n), y = (int) Math.round(y0 + (y1 - y0) * i / n);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
