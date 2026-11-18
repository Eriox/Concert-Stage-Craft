package com.concertstage.client;

import com.concertstage.block.CurtainRailBlock;
import com.concertstage.block.CurtainRailBlockEntity;
import com.concertstage.block.StageProperties;
import com.concertstage.client.ui.Ui;
import com.concertstage.net.StageNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tarp Tool (green theme): length and height steppers with sliders, the position across the cell as a segmented control
 * (side / centre / side, named after the real directions), and for the stage tarp the "stretched to the stage rim" switch.
 * Every change is sent at once, so the curtain or tarp follows while the screen is open.
 */
public class TarpToolScreen extends Screen {
    private static final int MAX = CurtainRailBlockEntity.MAX, W = 280, ACCENT = Ui.GREEN;
    private final BlockPos pos;
    private final boolean curtain;
    private final Direction.Axis axis;
    private int length, second, shift;
    private Boolean slant;
    private Ui.Slider lengthSlider, secondSlider;

    public TarpToolScreen(BlockPos pos, boolean curtain, int length, int second, int shift, BlockState state) {
        this(pos, curtain, length, second, shift, state, curtain ? "gui.concertstage.title_curtain" : "gui.concertstage.title_tarp");
    }

    public TarpToolScreen(BlockPos pos, boolean curtain, int length, int second, int shift, BlockState state, String titleKey) {
        super(Component.translatable(titleKey));
        this.pos = pos;
        this.curtain = curtain;
        this.length = length;
        this.second = second;
        this.shift = shift;
        this.axis = state.getBlock() instanceof CurtainRailBlock ? state.getValue(CurtainRailBlock.FACING).getAxis()
            : state.getBlock() instanceof com.concertstage.block.tarp.StageTarpRailBlock
            ? state.getValue(com.concertstage.block.tarp.StageTarpRailBlock.FACING).getAxis() : Direction.Axis.X;
    }

    /** Stage tarp: shows the "stretched to the stage rim" switch. */
    public TarpToolScreen withSlant(boolean slant) {
        this.slant = slant;
        return this;
    }

    private int h() { return 196 + (slant != null ? 30 : 0); }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - h()) / 2; }

    private void send() {
        StageNetwork.sendTarpSettings(pos, length, second, shift | (slant != null && !slant ? 8 : 0));
    }

    private void stepper(int x, int y, int w, Component label, int value, java.util.function.IntConsumer set, boolean first) {
        Ui.Slider s = addRenderableWidget(new Ui.Slider(x + 26, y, w - 52, 22, label, 1, MAX, value, ACCENT, v -> v + " m", v -> { set.accept(v); send(); }));
        if (first) lengthSlider = s; else secondSlider = s;
        addRenderableWidget(new Ui.Button(x, y + 4, 22, 18, () -> Component.literal("−"), ACCENT, Ui.Style.NORMAL, () -> { s.set(s.get() - 1); set.accept(s.get()); send(); }));
        addRenderableWidget(new Ui.Button(x + w - 22, y + 4, 22, 18, () -> Component.literal("+"), ACCENT, Ui.Style.NORMAL, () -> { s.set(s.get() + 1); set.accept(s.get()); send(); }));
    }

    @Override
    protected void init() {
        int x = left() + 12, y = top() + 38, w = W - 24;
        stepper(x, y, w, Component.translatable("gui.concertstage.length"), length, v -> length = v, true);
        stepper(x, y + 32, w, Component.translatable(curtain ? "gui.concertstage.height" : "gui.concertstage.width"), second, v -> second = v, false);
        if (curtain) {
            int third = (w - 8) / 3;
            int[] order = {2, 0, 1};
            for (int i = 0; i < 3; i++) {
                final int sh = order[i];
                addRenderableWidget(new Ui.Button(x + i * (third + 4), y + 82, third, 18, () -> positionName(sh), ACCENT, Ui.Style.NORMAL,
                    () -> { shift = sh; send(); rebuildWidgets(); }).selected(shift == sh));
            }
        }
        int next = y + 112;
        if (slant != null) {
            addRenderableWidget(new Ui.Button(x, next, w, 20, () -> Component.translatable(slant ? "gui.concertstage.tarp_slant_on" : "gui.concertstage.tarp_slant_off"),
                ACCENT, Ui.Style.NORMAL, () -> { slant = !slant; send(); rebuildWidgets(); }).selected(slant));
            next += 30;
        }
        addRenderableWidget(new Ui.Button(x, next + 8, w, 18, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.ACCENT, this::onClose));
    }

    private Component positionName(int sh) {
        if (sh == 0) return Component.translatable("gui.concertstage.pos.center");
        return Component.translatable("gui.concertstage.pos." + StageProperties.sideOf(axis, sh).getName());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, h(), ACCENT, title, Component.translatable("gui.concertstage.tarp_subtitle", length, second));
        int x = left() + 12, w = W - 24;
        if (curtain) Ui.section(g, x, top() + 108, w, Component.translatable("gui.concertstage.position_label"));
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
