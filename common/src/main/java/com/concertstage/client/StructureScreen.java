package com.concertstage.client;

import com.concertstage.client.ui.Ui;
import com.concertstage.net.StageNetwork;
import com.concertstage.structure.RoofStructure;
import com.concertstage.structure.StructureManager;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * CSC Structure Manager screen (red theme): what the selected roof frame is made of (the clicked frame or kit with all its cells),
 * its size, what comes back to the player, and the removal in two clicks (the second within 5 s, the button says how many
 * blocks go). While the screen is open the roof is outlined in the world.
 */
public class StructureScreen extends Screen {
    private static final int W = 300, H = 204, ACCENT = Ui.RED;
    private final BlockPos clicked;
    /** 1.0.2: one frame (or one kit) at a time. */
    private final int mode = RoofStructure.SINGLE;
    private RoofStructure structure;
    private int armed;                       // ticks left to confirm, 0 = not armed
    private int age;

    public StructureScreen(BlockPos clicked) {
        super(Component.translatable("gui.concertstage.structure_title"));
        this.clicked = clicked;
        scan();
    }

    private void scan() {
        var level = Minecraft.getInstance().level;
        structure = level == null ? null : RoofStructure.find(level, clicked, mode);
        armed = 0;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        if (structure == null) return;
        int x = left() + 12, w = W - 24, y = top() + H - 26;
        int bw = (w - 6) / 2;
        addRenderableWidget(new Ui.Button(x, y, bw, 18, () -> Component.translatable("gui.concertstage.cancel"), ACCENT, Ui.Style.GHOST, this::onClose));
        addRenderableWidget(new Ui.Button(x + bw + 6, y, bw, 18, () -> armed > 0
            ? Component.translatable("gui.concertstage.structure_confirm", structure.blockCount())
            : Component.translatable(structure.orphanOnly ? "gui.concertstage.structure_clean" : "gui.concertstage.structure_delete_frame"), ACCENT, Ui.Style.DANGER, this::press));
    }

    private void press() {
        if (structure == null) return;
        if (armed <= 0) { armed = 100; return; }                                       // first click: arm for 5 s
        StageNetwork.sendStructure(clicked, StructureManager.ACTION_DELETE, mode, structure.signature());
        onClose();
    }

    @Override
    public void tick() {
        age++;
        if (armed > 0 && --armed == 0) rebuildWidgets();
        if (structure == null) return;
        if (age % 8 == 1) outline();
    }

    /** The edges of the selection and a mark on each foot, in red dust. */
    private void outline() {
        var level = Minecraft.getInstance().level;
        AABB b = structure.bounds();
        if (level == null || b == null) return;
        DustParticleOptions red = new DustParticleOptions(new Vector3f(0.95f, 0.25f, 0.25f), 1.0f);
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            double len = axis == 0 ? b.maxX - b.minX : axis == 1 ? b.maxY - b.minY : b.maxZ - b.minZ;
            int n = (int) Math.min(64, Math.max(2, len * 1.5));
            for (double u : axis == 0 ? ys : xs)
                for (double v : axis == 2 ? ys : zs)
                    for (int i = 0; i <= n; i++) {
                        double t = i / (double) n;
                        double px = axis == 0 ? b.minX + (b.maxX - b.minX) * t : u;
                        double py = axis == 1 ? b.minY + (b.maxY - b.minY) * t : axis == 0 ? u : v;
                        double pz = axis == 2 ? b.minZ + (b.maxZ - b.minZ) * t : v;
                        level.addParticle(red, px, py, pz, 0, 0, 0);
                    }
        }
        for (BlockPos f : structure.feet) level.addParticle(red, f.getX() + 0.5, f.getY() + 1.2, f.getZ() + 0.5, 0, 0, 0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Component sub = structure == null ? Component.empty() : Component.translatable(structure.orphanOnly ? "gui.concertstage.structure_kind_orphans" : "gui.concertstage.structure_kind_frame");
        Ui.panel(g, left(), top(), W, H, ACCENT, title, sub);
        int x = left() + 12, y = top() + 36, w = W - 24;
        if (structure == null) {
            g.drawString(font, Component.translatable("gui.concertstage.structure_none"), x, y, Ui.MUTED, false);
            super.render(g, mouseX, mouseY, partial);
            return;
        }
        Ui.section(g, x, y, w, Component.translatable("gui.concertstage.structure_contents"));
        int ly = y + 14;
        if (!structure.orphanOnly) {
            g.drawString(font, Component.translatable("gui.concertstage.structure_feet", structure.feet.size()), x, ly, Ui.TEXT, false);
            ly += 11;
            g.drawString(font, Component.translatable("gui.concertstage.structure_cells", structure.cells.size()), x, ly, Ui.MUTED, false);
            ly += 11;
        }
        if (!structure.orphans.isEmpty()) {
            g.drawString(font, Component.translatable("gui.concertstage.structure_orphans", structure.orphans.size()), x, ly, Ui.AMBER, false);
            ly += 11;
        }
        AABB b = structure.bounds();
        if (b != null) {
            g.drawString(font, Component.translatable("gui.concertstage.structure_size", (int) Math.round(b.getXsize()), (int) Math.round(b.getZsize()),
                (int) Math.round(b.getYsize())), x, ly, Ui.MUTED, false);
            ly += 11;
        }
        // what comes back
        var level = Minecraft.getInstance().level;
        if (level != null && !structure.orphanOnly) {
            int ix = x;
            for (Map.Entry<Item, Integer> e : structure.items(level).entrySet()) {
                if (ix > x + w - 40) break;
                g.renderItem(new ItemStack(e.getKey()), ix, ly + 2);
                g.drawString(font, "×" + e.getValue(), ix + 18, ly + 6, Ui.TEXT, false);
                ix += 46;
            }
        }
        if (structure.truncated) g.drawString(font, Component.translatable("gui.concertstage.structure_truncated"), x, top() + 126, Ui.AMBER, false);
        int wy = top() + H - 44;
        g.drawString(font, Component.translatable(armed > 0 ? "gui.concertstage.structure_warning_armed" : "gui.concertstage.structure_warning"), x, wy, armed > 0 ? Ui.RED : 0xFF8A9099, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
