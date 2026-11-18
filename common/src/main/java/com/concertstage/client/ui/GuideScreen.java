package com.concertstage.client.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * 1.2.1 — the tutorial of the mod in the game (asked 15 November: "integrate the tutorials in the game"). Reads
 * {@code assets/concertstage/guide/<language>.md} (English when the language has none): its "##" parts are listed on the left, the
 * text scrolls on the right (mouse wheel, or a click on a part). Markdown kept to what the guide uses: headings, paragraphs,
 * numbered and bulleted lists, tables (one line per row), **bold** and `code`.
 */
public class GuideScreen extends Screen {
    private static final int W = 420, H = 250, SIDE = 118, ACCENT = Ui.CYAN;
    private final Screen parent;
    private final List<String> source = new ArrayList<>();
    private final List<String> parts = new ArrayList<>();
    private final List<Integer> partLine = new ArrayList<>();
    /** Laid-out lines: text, x indent, gap before (pixels), heading level (0 = text). */
    private final List<Object[]> lines = new ArrayList<>();
    private int laidWidth = -1, contentHeight, scroll;

    public GuideScreen(Screen parent) {
        super(Component.translatable("gui.concertstage.guide_title"));
        this.parent = parent;
        load();
    }

    /** Opens the guide over the current screen (Done goes back to it). */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new GuideScreen(mc.screen));
    }

    private void load() {
        Minecraft mc = Minecraft.getInstance();
        String lang = mc.getLanguageManager().getSelected();
        for (String l : new String[] {lang, "en_us"}) {
            var res = mc.getResourceManager().getResource(new ResourceLocation("concertstage", "guide/" + l + ".md"));
            if (res.isEmpty()) continue;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                String s;
                while ((s = r.readLine()) != null) source.add(s);
                return;
            } catch (Exception e) {
                source.clear();
            }
        }
        source.add("# Concert Stage Craft");
        source.add("The guide could not be read.");
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private int textX() { return left() + SIDE + 18; }
    private int textW() { return W - SIDE - 30; }
    private int textTop() { return top() + 30; }
    private int textBottom() { return top() + H - 32; }

    @Override
    protected void init() {
        layout(textW());
        int y = top() + 32;
        for (int i = 0; i < parts.size(); i++) {
            final int k = i;
            String label = parts.get(i);
            if (font.width(label) > SIDE - 8) label = font.plainSubstrByWidth(label, SIDE - 14) + "…";
            final String shown = label;
            addRenderableWidget(new Ui.Button(left() + 8, y + i * 19, SIDE, 17, () -> Component.literal(shown), ACCENT, Ui.Style.GHOST,
                () -> scroll = clampScroll(lineY(partLine.get(k)))));
        }
        addRenderableWidget(new Ui.Button(left() + W - 12 - 80, top() + H - 26, 80, 18, () -> Component.translatable("gui.concertstage.done"),
            ACCENT, Ui.Style.ACCENT, this::onClose));
    }

    /** Builds the lines for a text width. */
    private void layout(int w) {
        if (w == laidWidth) return;
        laidWidth = w;
        lines.clear();
        parts.clear();
        partLine.clear();
        boolean first = true;
        for (String raw : source) {
            String s = raw.stripTrailing();
            if (s.isBlank()) continue;
            if (s.startsWith("|")) {
                if (s.replace("|", "").replace("-", "").replace(" ", "").isEmpty()) continue;       // | --- | --- |
                String[] cells = s.substring(1, s.endsWith("|") ? s.length() - 1 : s.length()).split("\\|");
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < cells.length; i++) b.append(i == 0 ? "" : " — ").append(cells[i].trim());
                add(inline("• " + b), 4, 2, 0, w);
                continue;
            }
            int level = s.startsWith("### ") ? 3 : s.startsWith("## ") ? 2 : s.startsWith("# ") ? 1 : 0;
            if (level > 0) {
                String t = s.substring(level + 1).trim();
                if (level == 2) { parts.add(t); partLine.add(lines.size()); }
                MutableComponent c = Component.literal(t).withStyle(Style.EMPTY.withBold(true)
                    .withColor(level == 1 ? Ui.AMBER & 0xFFFFFF : level == 2 ? ACCENT & 0xFFFFFF : Ui.TEXT & 0xFFFFFF));
                add(c, 0, first ? 0 : level == 3 ? 7 : 12, level, w);
                first = false;
                continue;
            }
            int indent = 0;
            String t = s.trim();
            if (t.startsWith("- ") || t.startsWith("* ")) { t = "• " + t.substring(2); indent = 4 + (s.length() - s.stripLeading().length()) * 2; }
            else if (t.matches("\\d+\\.\\s.*")) indent = 4 + (s.length() - s.stripLeading().length()) * 2;
            add(inline(t), indent, 3, 0, w);
            first = false;
        }
        int y = 0;
        for (Object[] l : lines) { y += (int) l[2] + 10; l[4] = y - 10; }
        contentHeight = y;
    }

    private void add(Component c, int indent, int gap, int level, int w) {
        List<FormattedCharSequence> wrapped = font.split(c, Math.max(40, w - indent - (indent > 0 ? 8 : 0)));
        for (int i = 0; i < wrapped.size(); i++)
            lines.add(new Object[] {wrapped.get(i), i == 0 ? indent : indent + (indent > 0 ? 8 : 0), i == 0 ? gap : 0, i == wrapped.size() - 1 ? level : 0, 0});
    }

    /** **bold** and `code` of a line. */
    private static MutableComponent inline(String s) {
        MutableComponent out = Component.empty();
        int i = 0;
        while (i < s.length()) {
            int b = s.indexOf("**", i), c = s.indexOf('`', i);
            int next = b < 0 ? c : c < 0 ? b : Math.min(b, c);
            if (next < 0) { out.append(Component.literal(s.substring(i)).withStyle(ChatFormatting.WHITE)); break; }
            if (next > i) out.append(Component.literal(s.substring(i, next)).withStyle(ChatFormatting.WHITE));
            boolean bold = next == b;
            int end = s.indexOf(bold ? "**" : "`", next + (bold ? 2 : 1));
            if (end < 0) { out.append(Component.literal(s.substring(next)).withStyle(ChatFormatting.WHITE)); break; }
            String inner = s.substring(next + (bold ? 2 : 1), end);
            out.append(Component.literal(inner).withStyle(bold ? Style.EMPTY.withBold(true).withColor(Ui.AMBER & 0xFFFFFF)
                : Style.EMPTY.withColor(Ui.MUTED & 0xFFFFFF)));
            i = end + (bold ? 2 : 1);
        }
        return out;
    }

    private int lineY(int index) {
        return index < lines.size() ? (int) lines.get(index)[4] : 0;
    }

    private int clampScroll(int s) {
        return Math.max(0, Math.min(Math.max(0, contentHeight - (textBottom() - textTop())), s));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = clampScroll(scroll - (int) Math.round(delta * 24));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 264 || key == 267) { scroll = clampScroll(scroll + (key == 267 ? textBottom() - textTop() - 20 : 24)); return true; }   // down, page down
        if (key == 265 || key == 266) { scroll = clampScroll(scroll - (key == 266 ? textBottom() - textTop() - 20 : 24)); return true; }   // up, page up
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        layout(textW());
        Ui.panel(g, left(), top(), W, H, ACCENT, title, Component.translatable("gui.concertstage.guide_subtitle"));
        g.fill(left() + SIDE + 12, top() + 30, left() + SIDE + 13, top() + H - 32, Ui.LINE);
        int x = textX(), y0 = textTop(), y1 = textBottom();
        g.enableScissor(x - 2, y0, x + textW() + 2, y1);
        for (Object[] l : lines) {
            int y = y0 + (int) l[4] - scroll;
            if (y < y0 - 10 || y > y1) continue;
            g.drawString(font, (FormattedCharSequence) l[0], x + (int) l[1], y, Ui.TEXT, false);
            if ((int) l[3] == 2) g.fill(x, y + 10, x + textW(), y + 11, Ui.LINE);
        }
        g.disableScissor();
        // scroll bar
        int view = y1 - y0;
        if (contentHeight > view) {
            int bh = Math.max(16, view * view / contentHeight), by = y0 + (view - bh) * scroll / Math.max(1, contentHeight - view);
            g.fill(left() + W - 8, by, left() + W - 6, by + bh, ACCENT);
        }
        g.drawString(font, Component.translatable("gui.concertstage.guide_hint"), left() + 12, top() + H - 21, Ui.MUTED, false);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
