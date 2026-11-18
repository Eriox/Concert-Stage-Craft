package com.concertstage.client.light;

import com.concertstage.client.ui.Ui;
import com.concertstage.light.FixtureBlock;
import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.LumicoreBlockEntity;
import com.concertstage.light.QuickPatch;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Show;
import com.concertstage.light.dmx.ArtNetSettings;
import com.concertstage.light.dmx.DmxMode;
import com.concertstage.light.net.LightNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Rev. 1.3.0-2 — the patch of a fixture, opened by a right click on it with an empty hand (asked 12 November, 22 h 38): universe, start
 * address, DMX mode (with its number of channels and the last address it takes), "next free address" of that universe, apply. A
 * fixture that is not patched yet goes to the nearest console ({@value QuickPatch#REACH} blocks). Sent to the server
 * (LightNet.C_FIXTURE_PATCH, see {@link QuickPatch#fixturePatch}).
 */
public class FixturePatchScreen extends Screen {
    private static final int W = 300, H = 200, ACCENT = Ui.BLUE;
    private final BlockPos pos;
    private EditBox universe, address;
    private int mode = DmxMode.FULL;
    private boolean read;
    private Component error;

    public FixturePatchScreen(BlockPos pos) {
        super(Component.translatable("gui.concertstage.fixture_patch.title"));
        this.pos = pos;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private FixtureBlockEntity fixture() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof FixtureBlockEntity f ? f : null;
    }

    private FixtureProfile profile() {
        FixtureBlockEntity f = fixture();
        FixtureBlock b = f == null ? null : f.block();
        return b == null ? null : b.profile();
    }

    private LumicoreBlockEntity desk() {
        FixtureBlockEntity f = fixture();
        if (f == null || f.console() == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(f.console()) instanceof LumicoreBlockEntity d && d.patches(f) ? d : null;
    }

    private Show.Patch patch() {
        LumicoreBlockEntity d = desk();
        FixtureBlockEntity f = fixture();
        return d == null || f == null ? null : d.show.fixture(f.fid());
    }

    @Override
    protected void init() {
        Show.Patch p = patch();
        String u = universe != null ? universe.getValue() : p != null ? Integer.toString(p.universe) : "0";
        String a = address != null ? address.getValue() : p != null ? Integer.toString(p.address) : "1";
        if (!read) { if (p != null && p.dmxMode <= DmxMode.ULTRA) mode = p.dmxMode; read = true; }
        int x = left() + 12, y = top() + 36, bx = x + 110;
        universe = new EditBox(font, bx, y + 4, 60, 16, Component.translatable("gui.concertstage.card.universe"));
        universe.setMaxLength(5);
        universe.setValue(u);
        addRenderableWidget(universe);
        address = new EditBox(font, bx, y + 28, 60, 16, Component.translatable("gui.concertstage.card.address"));
        address.setMaxLength(3);
        address.setValue(a);
        addRenderableWidget(address);
        addRenderableWidget(new Ui.Button(bx + 66, y + 28, W - 24 - 110 - 66, 16, () -> Component.translatable("gui.concertstage.fixture_patch.next_free"), ACCENT,
            Ui.Style.GHOST, this::nextFree));
        addRenderableWidget(new Ui.Button(bx, y + 52, W - 24 - 110, 16, () -> Component.literal(DmxMode.NAMES[mode]), ACCENT, Ui.Style.NORMAL,
            () -> mode = (mode + 1) % (DmxMode.ULTRA + 1)));
        addRenderableWidget(new Ui.Button(left() + 12, top() + H - 26, 110, 18, () -> Component.translatable("gui.concertstage.fixture_patch.apply"), Ui.GREEN, Ui.Style.ACCENT, this::apply));
        addRenderableWidget(new Ui.Button(left() + W - 84, top() + H - 26, 72, 18, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    private int footprint() {
        FixtureProfile pr = profile();
        return pr == null ? 0 : DmxMode.of(pr, mode).footprint();
    }

    /** First address of the universe where this fixture fits without overlapping the other fixtures of its desk (client copy). */
    private void nextFree() {
        LumicoreBlockEntity d = desk();
        int u;
        try { u = Integer.parseInt(universe.getValue().trim()); } catch (NumberFormatException e) { return; }
        int foot = Math.max(1, footprint());
        boolean[] used = new boolean[513];
        if (d != null) {
            Show.Patch self = patch();
            for (Show.Patch o : d.show.patch) {
                if (o == self || o.universe != u || o.dmxMode == DmxMode.MATRIX || o.profile() == null) continue;
                int n = DmxMode.of(o.profile(), o.dmxMode).footprint();
                for (int k = o.address; k < o.address + n && k <= 512; k++) used[k] = true;
            }
        }
        for (int a = 1; a + foot - 1 <= 512; a++) {
            boolean free = true;
            for (int k = a; k < a + foot; k++) if (used[k]) { free = false; a = k; break; }
            if (free) { address.setValue(Integer.toString(a)); return; }
        }
        error = Component.translatable("message.concertstage.card_full");
    }

    private void apply() {
        error = null;
        int u, a;
        try {
            u = Integer.parseInt(universe.getValue().trim());
            a = Integer.parseInt(address.getValue().trim());
        } catch (NumberFormatException e) {
            error = Component.translatable("gui.concertstage.artnet.bad_number");
            return;
        }
        if (!ArtNetSettings.validUniverse(u) || a < 1 || a > 512) { error = Component.translatable("message.concertstage.card_invalid"); return; }
        if (a + footprint() - 1 > 512) { error = Component.translatable("message.concertstage.patch_past_512", footprint(), 513 - footprint()); return; }
        LightNet.toServer(LightNet.message(LightNet.C_FIXTURE_PATCH, pos).varInt(u).varInt(a).varInt(mode).toBytes());
        onClose();
    }

    @Override
    public void tick() {
        if (universe != null) universe.tick();
        if (address != null) address.tick();
        if (fixture() == null) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        int x = left(), y = top();
        FixtureProfile pr = profile();
        FixtureBlockEntity f = fixture();
        Component sub;
        if (f != null && desk() != null)
            sub = Component.translatable("gui.concertstage.fixture_patch.on_console", f.fid(), f.console().getX(), f.console().getY(), f.console().getZ());
        else sub = Component.translatable("gui.concertstage.fixture_patch.unpatched", QuickPatch.REACH);
        Ui.panel(g, x, y, W, H, ACCENT, Component.literal(pr == null ? "?" : pr.name).append(" · ").append(title), sub);
        int lx = x + 12, ly = y + 36;
        g.drawString(font, Component.translatable("gui.concertstage.card.universe"), lx, ly + 8, Ui.TEXT, false);
        g.drawString(font, Component.translatable("gui.concertstage.card.address"), lx, ly + 32, Ui.TEXT, false);
        g.drawString(font, Component.translatable("gui.concertstage.card.mode"), lx, ly + 56, Ui.TEXT, false);
        int foot = footprint();
        String last;
        try { int a = Integer.parseInt(address.getValue().trim()); last = Integer.toString(a + foot - 1); } catch (RuntimeException e) { last = "?"; }
        g.drawString(font, Component.translatable("gui.concertstage.fixture_patch.channels", foot, last), lx, ly + 80, Ui.MUTED, false);
        Show.Patch p = patch();
        if (p != null) g.drawString(font, Component.translatable("gui.concertstage.fixture_patch.current", p.universe, p.address,
            p.dmxMode < DmxMode.NAMES.length ? DmxMode.NAMES[p.dmxMode] : "?"), lx, ly + 94, Ui.MUTED, false);
        if (error != null) Ui.centeredFit(g, error, x + W / 2, ly + 112, W - 20, Ui.RED);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
