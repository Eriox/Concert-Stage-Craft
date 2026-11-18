package com.concertstage.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Small flat UI kit shared by the Concert Stage screens (lift remote, ring controller, tarp tool, angle tool): dark glass panels,
 * one accent colour per tool, flat buttons, chips, segmented controls and flat sliders. Everything is drawn with fills, no textures.
 */
public final class Ui {
    public static final int BG = 0xF0141619, BG2 = 0xFF1C1F24, LINE = 0xFF2C3139, TEXT = 0xFFE8EAED, MUTED = 0xFFB8BEC6;
    public static final int AMBER = 0xFFF2B33D, CYAN = 0xFF35C9E8, GREEN = 0xFF4CD08A, BLUE = 0xFF5B8CFF, RED = 0xFFE5484D, ORANGE = 0xFFFF8A3D, PURPLE = 0xFFB07CFF;

    private Ui() {}

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Opaque mix of two colours ({@code t} = share of {@code b}). */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    public static int alpha(int color, int a) {
        return (color & 0xFFFFFF) | (a << 24);
    }

    /** Panel with a soft shadow, a thin border, a title bar and an accent line. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent, Component title, Component subtitle) {
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x40000000);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x60000000);
        g.fill(x, y, x + w, y + h, BG);
        frame(g, x, y, w, h, LINE);
        g.fill(x, y, x + w, y + 26, BG2);
        g.fill(x, y + 26, x + w, y + 27, LINE);
        g.fill(x, y, x + 3, y + 26, accent);
        g.drawString(font(), title, x + 10, y + 5, TEXT, false);
        if (subtitle != null) g.drawString(font(), subtitle, x + 10, y + 15, MUTED, false);
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Small caps-like section label with a hairline. */
    public static void section(GuiGraphics g, int x, int y, int w, Component label) {
        g.drawString(font(), label, x, y, MUTED, false);
        int lw = font().width(label) + 6;
        g.fill(x + lw, y + 4, x + w, y + 5, LINE);
    }

    /** Horizontal bar gauge. */
    public static void gauge(GuiGraphics g, int x, int y, int w, int h, double fraction, int color, double marker) {
        g.fill(x, y, x + w, y + h, 0xFF0D0F12);
        g.fill(x, y, x + (int) Math.round(w * Math.max(0, Math.min(1, fraction))), y + h, color);
        if (marker >= 0) {
            int mx = x + (int) Math.round(w * Math.max(0, Math.min(1, marker)));
            g.fill(mx, y - 2, mx + 1, y + h + 2, TEXT);
        }
    }

    // ------------------------------------------------------------ widgets
    public enum Style { NORMAL, ACCENT, DANGER, GHOST }

    /** Flat button; {@code selected} draws it filled with the accent (chips, tabs, segmented controls). */
    public static class Button extends AbstractButton {
        private final Runnable action;
        private final Supplier<Component> label;
        private final int accent;
        private Style style;
        public boolean selected;

        public Button(int x, int y, int w, int h, Supplier<Component> label, int accent, Style style, Runnable action) {
            super(x, y, w, h, label.get());
            this.label = label;
            this.accent = accent;
            this.style = style;
            this.action = action;
        }

        public Button selected(boolean s) {
            this.selected = s;
            return this;
        }

        @Override
        public void onPress() {
            action.run();
            setMessage(label.get());
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hover = isHoveredOrFocused() && active;
            int x = getX(), y = getY();
            int base, border, text = TEXT;
            if (!active) { base = 0xFF17191D; border = LINE; text = 0xFF6B7178; }
            else if (selected) { base = mix(0xFF1C1F24, accent, hover ? 0.55f : 0.45f); border = accent; }
            else switch (style) {
                case ACCENT: base = mix(0xFF1C1F24, accent, hover ? 0.75f : 0.62f); border = accent; break;
                case DANGER: base = hover ? 0xFFD63B40 : 0xFFB8292E; border = 0xFFFF6B70; break;
                case GHOST: base = hover ? 0xFF262A31 : 0xFF181B20; border = hover ? accent : LINE; break;
                default: base = hover ? 0xFF2E333B : 0xFF24282E; border = hover ? accent : LINE; break;
            }
            g.fill(x, y, x + width, y + height, base);
            frame(g, x, y, width, height, border);
            if (selected) g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, accent);    // underline of the chosen chip / tab
            // light text everywhere (readable on every fill), with the vanilla shadow
            centeredFit(g, getMessage(), x + width / 2, y + (height - 8) / 2, width - 6, text);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** Flat slider: label on the left, value on the right, accent fill. The value is committed on release / keys. */
    public static class Slider extends AbstractSliderButton {
        private final Component label;
        private final int min, max, accent;
        private final IntFunction<String> text;
        private final IntConsumer commit;

        public Slider(int x, int y, int w, int h, Component label, int min, int max, int value, int accent, IntFunction<String> text, IntConsumer commit) {
            super(x, y, w, h, Component.empty(), max == min ? 0 : (value - min) / (double) (max - min));
            this.label = label; this.min = min; this.max = max; this.accent = accent; this.text = text; this.commit = commit;
            updateMessage();
        }

        public int get() {
            return min + (int) Math.round(value * (max - min));
        }

        public void set(int v) {
            value = max == min ? 0 : (Math.max(min, Math.min(max, v)) - min) / (double) (max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(text.apply(get())));
        }

        @Override
        protected void applyValue() {}

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            commit.accept(get());
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            boolean handled = super.keyPressed(key, scan, mods);
            if (handled) commit.accept(get());
            return handled;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int x = getX(), y = getY();
            g.drawString(font(), label, x, y, MUTED, false);
            String v = getMessage().getString();
            g.drawString(font(), v, x + width - font().width(v), y, TEXT, false);
            int ty = y + height - 6;
            g.fill(x, ty, x + width, ty + 3, 0xFF2A2E35);
            int fx = x + (int) Math.round((width - 6) * value);
            g.fill(x, ty, fx + 3, ty + 3, accent);
            boolean hover = isHoveredOrFocused();
            g.fill(fx, ty - 3, fx + 6, ty + 6, hover ? TEXT : 0xFFCDD1D6);
        }
    }

    /**
     * Text centred on {@code cx} that never runs out of its {@code maxWidth}: drawn smaller when it is a little too long (down to 70 %),
     * then shortened with "…".
     */
    public static void centeredFit(GuiGraphics g, net.minecraft.network.chat.Component text, int cx, int y, int maxWidth, int color) {
        net.minecraft.client.gui.Font f = font();
        int w = f.width(text);
        if (w <= maxWidth) { g.drawCenteredString(f, text, cx, y, color); return; }
        float scale = Math.max(0.7f, maxWidth / (float) w);
        String s = text.getString();
        if (w * scale > maxWidth) {
            while (s.length() > 1 && f.width(s + "…") * scale > maxWidth) s = s.substring(0, s.length() - 1);
            s = s + "…";
        }
        g.pose().pushPose();
        g.pose().translate(cx, y + 4 * (1 - scale), 0);
        g.pose().scale(scale, scale, 1);
        g.drawCenteredString(f, s, 0, 0, color);
        g.pose().popPose();
    }
}
