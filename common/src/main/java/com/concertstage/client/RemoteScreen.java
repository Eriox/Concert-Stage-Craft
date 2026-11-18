package com.concertstage.client;

import com.concertstage.block.TowerBlockEntity;
import com.concertstage.client.ui.Ui;
import com.concertstage.item.RemoteItem;
import com.concertstage.net.StageNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/**
 * Lift remote (CSC LiftControl): two tabs (truss towers / chain hoists), thirteen groups A-M per remote (click a motor's group badge to move it),
 * the selected group (or all) is driven by the controls. Every control sends one small message; the server decides.
 */
public class RemoteScreen extends Screen {
    private static final int W = 300, H = 286, ROWS = 6, ACCENT = Ui.AMBER;
    private static final String[] LETTERS = {"", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M"};
    private static int tab = StageNetwork.GROUP_TOWERS;
    private static int group = 0;                       // 0 = every motor of the tab
    private final InteractionHand hand;
    private Ui.Slider slider;
    private int scroll;

    public RemoteScreen(InteractionHand hand) {
        super(Component.translatable("gui.concertstage.remote_title"));
        this.hand = hand;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private ItemStack stack() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getItemInHand(hand);
    }

    /** Linked motors of the current tab, with their index in the remote's full list. */
    private List<int[]> rows() {
        List<int[]> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(stack().getItem() instanceof RemoteItem)) return out;
        List<BlockPos> all = RemoteItem.read(stack(), mc.level);
        for (int i = 0; i < all.size(); i++) if (StageNetwork.inGroup(mc.level, all.get(i), tab)) out.add(new int[] {i});
        return out;
    }

    private List<BlockPos> all() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || !(stack().getItem() instanceof RemoteItem) ? List.of() : RemoteItem.read(stack(), mc.level);
    }

    private boolean selected(BlockPos p, Map<BlockPos, Integer> groups) {
        return group == 0 || groups.getOrDefault(p, 1) == group;
    }

    private int commonMax() {
        Level level = Minecraft.getInstance().level;
        int max = Integer.MAX_VALUE;
        Map<BlockPos, Integer> groups = RemoteItem.groups(stack());
        for (int[] r : rows()) {
            BlockPos p = all().get(r[0]);
            if (selected(p, groups) && level != null && level.getBlockEntity(p) instanceof TowerBlockEntity tb && tb.isController())
                max = Math.min(max, tb.tower().maxLiftBlocks());
        }
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    private void send(int action, int value) {
        StageNetwork.sendRemote(action, StageNetwork.withGroup(value, tab, group));
    }

    @Override
    protected void init() {
        int x = left() + 12, y = top() + 34, w = W - 24, half = (w - 6) / 2;
        for (int t : new int[] {StageNetwork.GROUP_TOWERS, StageNetwork.GROUP_HOISTS}) {
            final int tt = t;
            addRenderableWidget(new Ui.Button(x + (t == StageNetwork.GROUP_TOWERS ? 0 : half + 6), y, half, 18,
                () -> Component.translatable(tt == StageNetwork.GROUP_HOISTS ? "gui.concertstage.remote_tab_hoists" : "gui.concertstage.remote_tab_towers", count(tt)),
                ACCENT, Ui.Style.NORMAL, () -> { tab = tt; scroll = 0; rebuildWidgets(); }).selected(tab == t));
        }
        // 1.1.0: "All" then the 13 group letters A ... M in one row (the full name in the tooltip)
        int allW = 36, cw = (w - allW - 2 * RemoteItem.GROUPS) / RemoteItem.GROUPS;
        for (int gI = 0; gI <= RemoteItem.GROUPS; gI++) {
            final int gg = gI;
            int bx = gI == 0 ? x : x + allW + 2 + (gI - 1) * (cw + 2), bw = gI == 0 ? allW : cw;
            var b = addRenderableWidget(new Ui.Button(bx, y + 24, bw, 16,
                () -> gg == 0 ? Component.translatable("gui.concertstage.remote_group_all") : Component.literal(LETTERS[gg]),
                ACCENT, Ui.Style.NORMAL, () -> { group = gg; rebuildWidgets(); }).selected(group == gI));
            if (gI > 0) b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.concertstage.remote_group", LETTERS[gg])));
        }
        int max = commonMax();
        slider = addRenderableWidget(new Ui.Slider(x, y + 152, w, 22, Component.translatable("gui.concertstage.remote_target_label"),
            0, Math.max(1, max), Math.min(currentBlocks(), max), ACCENT, v -> v + " / " + max + " m", v -> {}));
        int third = (w - 12) / 3;
        addRenderableWidget(new Ui.Button(x, y + 180, third, 20, () -> Component.translatable("gui.concertstage.remote_up"), ACCENT, Ui.Style.NORMAL,
            () -> send(StageNetwork.REMOTE_UP, 0)));
        addRenderableWidget(new Ui.Button(x + third + 6, y + 180, third, 20, () -> Component.translatable("gui.concertstage.remote_down"), ACCENT, Ui.Style.NORMAL,
            () -> send(StageNetwork.REMOTE_DOWN, 0)));
        addRenderableWidget(new Ui.Button(x + 2 * (third + 6), y + 180, third, 20, () -> Component.translatable("gui.concertstage.remote_go"), ACCENT, Ui.Style.ACCENT,
            () -> send(StageNetwork.REMOTE_GOTO, slider.get())));
        addRenderableWidget(new Ui.Button(x, y + 206, w, 22, () -> Component.translatable("gui.concertstage.remote_stop"), ACCENT, Ui.Style.DANGER,
            () -> send(StageNetwork.REMOTE_STOP, 0)));
        addRenderableWidget(new Ui.Button(x, y + 234, half, 16, () -> Component.translatable(group == 0 ? "gui.concertstage.remote_clear" : "gui.concertstage.remote_clear_group"),
            ACCENT, Ui.Style.GHOST, () -> send(StageNetwork.REMOTE_CLEAR, 0)));
        addRenderableWidget(new Ui.Button(x + half + 6, y + 234, half, 16, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    private int count(int t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        int n = 0;
        for (BlockPos p : all()) if (StageNetwork.inGroup(mc.level, p, t)) n++;
        return n;
    }

    private int currentBlocks() {
        Level level = Minecraft.getInstance().level;
        Map<BlockPos, Integer> groups = RemoteItem.groups(stack());
        for (int[] r : rows()) {
            BlockPos p = all().get(r[0]);
            if (selected(p, groups) && level != null && level.getBlockEntity(p) instanceof TowerBlockEntity tb && tb.isController())
                return Math.round((float) tb.liftBlocks(level.getGameTime(), 0.0F));
        }
        return 0;
    }

    private int stateColor(Level level, BlockPos p) {
        if (level == null || !level.hasChunkAt(p) || !(level.getBlockEntity(p) instanceof TowerBlockEntity tb) || !tb.isController()) return 0xFF6B7178;
        if (tb.isMoving()) return Ui.AMBER;
        if (tb.fault() == TowerBlockEntity.FAULT_BLOCKED) return Ui.RED;
        return Ui.GREEN;
    }

    private Component stateText(Level level, BlockPos p) {
        if (level == null || !level.hasChunkAt(p) || !(level.getBlockEntity(p) instanceof TowerBlockEntity tb) || !tb.isController())
            return Component.translatable("gui.concertstage.state_unloaded");
        if (tb.isMoving()) return Component.translatable("gui.concertstage.state_moving");
        if (tb.fault() == TowerBlockEntity.FAULT_BLOCKED) return Component.translatable("gui.concertstage.state_blocked");
        return Component.translatable("gui.concertstage.state_idle");
    }

    private int listY() { return top() + 34 + 46; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable("gui.concertstage.remote_subtitle"));
        int x = left() + 12, w = W - 24, y = listY();
        g.fill(x, y, x + w, y + ROWS * 16 + 6, 0xFF0F1114);
        Ui.frame(g, x, y, w, ROWS * 16 + 6, Ui.LINE);
        Level level = Minecraft.getInstance().level;
        List<int[]> rows = rows();
        List<BlockPos> all = all();
        Map<BlockPos, Integer> groups = RemoteItem.groups(stack());
        if (rows.isEmpty()) {
            g.drawWordWrap(font, Component.translatable(tab == StageNetwork.GROUP_HOISTS ? "gui.concertstage.remote_no_hoists" : "gui.concertstage.remote_no_towers"),
                x + 8, y + 10, w - 16, Ui.MUTED);
        }
        int target = slider == null ? 0 : slider.get();
        for (int i = 0; i < Math.min(ROWS, rows.size() - scroll); i++) {
            int index = rows.get(i + scroll)[0];
            BlockPos p = all.get(index);
            int ry = y + 4 + i * 16;
            boolean sel = selected(p, groups);
            int gI = groups.getOrDefault(p, 1);
            int tint = sel ? Ui.TEXT : 0xFF5E646C;
            g.drawString(font, (tab == StageNetwork.GROUP_HOISTS ? "P" : "T") + (i + scroll + 1), x + 6, ry + 3, tint, false);
            // group badge (click: next group)
            int bx = x + 28;
            g.fill(bx, ry + 1, bx + 14, ry + 13, sel ? Ui.mix(0xFF1C1F24, ACCENT, 0.5f) : 0xFF2A2E35);
            Ui.frame(g, bx, ry + 1, 14, 12, sel ? ACCENT : Ui.LINE);
            g.drawCenteredString(font, LETTERS[gI], bx + 7, ry + 3, sel ? Ui.TEXT : Ui.MUTED);
            double lift = 0; int max = 0;
            if (level != null && level.getBlockEntity(p) instanceof TowerBlockEntity tb && tb.isController()) {
                lift = tb.liftBlocks(level.getGameTime(), partial);
                max = tb.tower().maxLiftBlocks();
            }
            int col = stateColor(level, p);
            Ui.gauge(g, x + 48, ry + 4, 104, 6, max > 0 ? lift / max : 0, sel ? col : Ui.alpha(col, 0x66), sel && max > 0 ? target / (double) max : -1);
            g.drawString(font, max > 0 ? String.format("%.1f/%d", lift, max) : "?", x + 158, ry + 3, tint, false);
            Component st = stateText(level, p);
            g.drawString(font, st, x + w - 6 - font.width(st), ry + 3, sel ? col : 0xFF5E646C, false);
        }
        if (rows.size() > ROWS) g.drawString(font, (scroll + 1) + "-" + Math.min(rows.size(), scroll + ROWS) + " / " + rows.size(), x + w - 50, y + ROWS * 16 + 8, Ui.MUTED, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left() + 12, y = listY();
        List<int[]> rows = rows();
        for (int i = 0; i < Math.min(ROWS, rows.size() - scroll); i++) {
            int ry = y + 4 + i * 16, bx = x + 28;
            if (mx >= bx && mx < bx + 14 && my >= ry + 1 && my < ry + 13) {
                int index = rows.get(i + scroll)[0];
                int cur = RemoteItem.groups(stack()).getOrDefault(all().get(index), 1);
                int next = button == 1 ? (cur + RemoteItem.GROUPS - 2) % RemoteItem.GROUPS + 1 : cur % RemoteItem.GROUPS + 1;
                StageNetwork.sendRemote(StageNetwork.REMOTE_SET_GROUP, (index & 0xFFF) | (next << 12));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int n = rows().size();
        if (my >= listY() && my <= listY() + ROWS * 16 + 6 && n > ROWS) {
            scroll = Math.max(0, Math.min(n - ROWS, scroll - (int) Math.signum(delta)));
            return true;
        }
        if (slider != null) { slider.set(slider.get() + (int) Math.signum(delta)); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_UP) { send(StageNetwork.REMOTE_UP, 0); return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { send(StageNetwork.REMOTE_DOWN, 0); return true; }
        if (key == GLFW.GLFW_KEY_SPACE) { send(StageNetwork.REMOTE_STOP, 0); return true; }
        if (key == GLFW.GLFW_KEY_ENTER && slider != null) { send(StageNetwork.REMOTE_GOTO, slider.get()); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
