package com.concertstage.client.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Translation of the texts of the CSC Relay screens (1.1.0): the English text written in the code is the reference, its key is
 * {@code gui.concertstage.ui.<text in lower case, words joined by _>} in the language files (FR / EN). Spaces at both ends are kept
 * (labels followed by a value). A text without a translation is shown as written, never as a raw key. Results are cached per
 * language (the console LCD asks every frame).
 */
public final class Tr {
    private Tr() {}

    private static final Map<String, String> CACHE = new HashMap<>();
    private static String cacheLang = "";

    public static String key(String english) {
        String text = english.strip();
        boolean label = text.endsWith(":");                              // "Model: " and "model" are two different texts
        String s = text.toLowerCase(Locale.ROOT).replace("+", " plus ").replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.length() > 60) s = s.substring(0, 60);
        return "gui.concertstage.ui." + s + (label ? "_label" : "");
    }

    public static String t(String english) {
        if (english == null || english.isBlank()) return english;
        String lang = language();
        synchronized (CACHE) {
            if (!lang.equals(cacheLang)) { CACHE.clear(); cacheLang = lang; }
            String hit = CACHE.get(english);
            if (hit != null) return hit;
        }
        String out = english;
        String k = key(english);
        Language lang0 = Language.getInstance();
        if (lang0.has(k)) {
            int a = 0, b = english.length();
            while (a < b && english.charAt(a) == ' ') a++;
            while (b > a && english.charAt(b - 1) == ' ') b--;
            out = english.substring(0, a) + lang0.getOrDefault(k) + english.substring(b);   // raw: "%" patterns are formatted by f()
        }
        synchronized (CACHE) { CACHE.put(english, out); }
        return out;
    }

    /** Translated format string (the English pattern is the key), numbers written the same in every language. */
    public static String f(String englishPattern, Object... args) {
        return String.format(Locale.ROOT, t(englishPattern), args);
    }

    public static Component c(String english) {
        return Component.literal(t(english));
    }

    /** "ON" / "OFF" in the player's language. */
    public static String onOff(boolean on) {
        return t(on ? "ON" : "OFF");
    }

    /** "Label: ON" with both parts translated. */
    public static Component state(String englishLabel, boolean on) {
        return Component.literal(t(englishLabel) + " : ".substring(language().startsWith("fr") ? 0 : 1) + onOff(on));
    }

    /** Help text of a control, key {@code gui.concertstage.tip.<key>}. */
    public static MutableComponent tip(String key, Object... args) {
        return Component.translatable("gui.concertstage.tip." + key, args);
    }

    /** Puts the help text {@code gui.concertstage.tip.<key>} on a widget (shown when the mouse rests on it). */
    public static <T extends AbstractWidget> T help(T widget, String key) {
        widget.setTooltip(Tooltip.create(tip(key)));
        return widget;
    }

    private static String language() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
