package com.concertstage.client.light;

import com.concertstage.StageContent;
import com.concertstage.client.ui.Ui;
import com.concertstage.light.QuickPatch;
import com.concertstage.light.core.Bytes;
import com.concertstage.light.dmx.ArtNetSettings;
import com.concertstage.light.dmx.DmxMode;
import com.concertstage.light.net.LightNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Rev. 1.3.0-2 — the CSC CONFIGURATION CARD screen (picture 4 of 12 November): network universe, DMX mode, start address, address auto
 * increment, save. The card then patches each fixture it is used on (see {@link QuickPatch}).
 */
public class ConfigCardScreen extends Screen {
    private static final int W = 300, H = 196, ACCENT = Ui.AMBER;
    private final InteractionHand hand;
    private EditBox universe, address;
    private int mode;
    private boolean autoInc;
    private Component error;

    public ConfigCardScreen(InteractionHand hand) {
        super(Component.translatable("item.concertstage.csc_config_card"));
        this.hand = hand;
    }

    private ItemStack stack() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        QuickPatch.Card c = QuickPatch.card(stack());
        if (universe == null) { mode = c.mode(); autoInc = c.autoInc(); }
        int x = left() + 12, y = top() + 36, bx = x + 150;
        String u = universe == null ? Integer.toString(c.universe()) : universe.getValue();
        String a = address == null ? Integer.toString(c.address()) : address.getValue();
        universe = new EditBox(font, bx, y + 4, 60, 16, Component.translatable("gui.concertstage.card.universe"));
        universe.setValue(u);
        universe.setMaxLength(5);
        addRenderableWidget(universe);
        addRenderableWidget(new Ui.Button(bx, y + 28, W - 24 - 150, 16, () -> mode == QuickPatch.MODE_KEEP
            ? Component.translatable("gui.concertstage.card.mode_keep") : Component.literal(DmxMode.NAMES[mode]), ACCENT, Ui.Style.NORMAL,
            () -> mode = mode >= DmxMode.ULTRA ? QuickPatch.MODE_KEEP : mode + 1));
        address = new EditBox(font, bx, y + 52, 60, 16, Component.translatable("gui.concertstage.card.address"));
        address.setValue(a);
        address.setMaxLength(3);
        addRenderableWidget(address);
        addRenderableWidget(new Ui.Button(bx, y + 76, 60, 16, () -> Component.translatable(autoInc ? "options.on" : "options.off"), ACCENT, Ui.Style.NORMAL,
            () -> autoInc = !autoInc).selected(autoInc));
        addRenderableWidget(new Ui.Button(left() + 12, top() + H - 26, 110, 18, () -> Component.translatable("gui.concertstage.card.save"), Ui.GREEN, Ui.Style.ACCENT, this::save));
        addRenderableWidget(new Ui.Button(left() + W - 84, top() + H - 26, 72, 18, () -> Component.translatable("gui.concertstage.done"), ACCENT, Ui.Style.GHOST, this::onClose));
    }

    private void save() {
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
        Bytes.Writer w = LightNet.message(LightNet.C_CARD, BlockPos.ZERO).varInt(hand == InteractionHand.OFF_HAND ? 1 : 0).varInt(u).varInt(a).sInt(mode).bool(autoInc);
        LightNet.toServer(w.toBytes());
        onClose();
    }

    @Override
    public void tick() {
        if (universe != null) universe.tick();
        if (address != null) address.tick();
        if (!stack().is(StageContent.CONFIG_CARD)) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        int x = left(), y = top();
        QuickPatch.Card c = QuickPatch.card(stack());
        Component sub = c.console() == null ? Component.translatable("tooltip.concertstage.card_unlinked")
            : Component.translatable("tooltip.concertstage.card_console", c.console().getX(), c.console().getY(), c.console().getZ());
        Ui.panel(g, x, y, W, H, ACCENT, title, sub);
        int lx = x + 12, ly = y + 36;
        g.drawString(font, Component.translatable("gui.concertstage.card.universe"), lx, ly + 8, Ui.TEXT, false);
        g.drawString(font, Component.translatable("gui.concertstage.card.mode"), lx, ly + 32, Ui.TEXT, false);
        g.drawString(font, Component.translatable("gui.concertstage.card.address"), lx, ly + 56, Ui.TEXT, false);
        g.drawString(font, Component.translatable("gui.concertstage.card.auto_inc"), lx, ly + 80, Ui.TEXT, false);
        Ui.centeredFit(g, Component.translatable("gui.concertstage.card.hint"), x + W / 2, ly + 102, W - 20, Ui.MUTED);
        if (error != null) Ui.centeredFit(g, error, x + W / 2, ly + 114, W - 20, Ui.RED);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
