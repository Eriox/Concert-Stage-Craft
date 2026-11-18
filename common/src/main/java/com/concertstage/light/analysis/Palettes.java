package com.concertstage.light.analysis;

import com.concertstage.light.core.Show;
import java.util.ArrayList;
import java.util.List;

/**
 * Colour palette engine of the AutoShow. A palette is three colours (primary for the washes, secondary for the beams, accent for the
 * pixels and the accents). Palettes are chosen per musical motif — a returning chorus gets its palette back — with the style, the energy
 * of the section and the player's favourite colours. Colours never change at random: a palette only changes with the section.
 */
public final class Palettes {
    private Palettes() {}

    public record Palette(int primary, int secondary, int accent) {
        public Palette swapped() {
            return new Palette(secondary, primary, accent);
        }
    }

    private static final int WHITE = 0xFFF4E6, WARM = 0xFFC27A, AMBER = 0xFF9A1F, RED = 0xFF1A10, MAGENTA = 0xFF1FA0, PINK = 0xFF6FB6,
        VIOLET = 0x8A3BFF, DEEP_BLUE = 0x1530FF, BLUE = 0x2F7BFF, CYAN = 0x18E0FF, TEAL = 0x00C8A0, LIME = 0x9CFF2E, GOLD = 0xFFC21A,
        LAVENDER = 0xB49CFF, CONGO = 0x4B1AFF;

    /** Calm palettes (intro, verse, break, outro) and energetic palettes (chorus, drop) for each style. */
    private static final Palette[][] CALM = {
        {p(BLUE, LAVENDER, WHITE), p(TEAL, BLUE, WHITE), p(PINK, LAVENDER, WARM), p(DEEP_BLUE, VIOLET, CYAN)},          // pop
        {p(DEEP_BLUE, CYAN, WHITE), p(CONGO, VIOLET, CYAN), p(TEAL, BLUE, LIME), p(VIOLET, DEEP_BLUE, MAGENTA)},        // edm
        {p(AMBER, WARM, WHITE), p(DEEP_BLUE, WHITE, WARM), p(RED, AMBER, WARM), p(WARM, GOLD, WHITE)},                  // rock
        {p(DEEP_BLUE, VIOLET, LAVENDER), p(TEAL, DEEP_BLUE, CYAN), p(LAVENDER, PINK, WHITE), p(CONGO, BLUE, TEAL)},     // ambient
    };
    private static final Palette[][] HOT = {
        {p(MAGENTA, CYAN, WHITE), p(AMBER, PINK, WHITE), p(VIOLET, GOLD, WHITE), p(RED, WHITE, AMBER)},
        {p(MAGENTA, CYAN, WHITE), p(RED, WHITE, AMBER), p(LIME, BLUE, WHITE), p(CYAN, VIOLET, MAGENTA), p(ORANGE_RED(), DEEP_BLUE, WHITE)},
        {p(RED, WHITE, AMBER), p(WHITE, AMBER, RED), p(GOLD, RED, WHITE), p(DEEP_BLUE, WHITE, AMBER)},
        {p(VIOLET, CYAN, LAVENDER), p(BLUE, PINK, WHITE), p(TEAL, LAVENDER, WHITE), p(DEEP_BLUE, MAGENTA, CYAN)},
    };

    private static int ORANGE_RED() {
        return 0xFF4A12;
    }

    private static Palette p(int a, int b, int c) {
        return new Palette(a, b, c);
    }

    /** Whether a section kind is an energetic one (chorus, drop). */
    public static boolean hot(int kind) {
        return kind == Analysis.CHORUS || kind == Analysis.DROP;
    }

    /**
     * The palette of a section: chosen once per (motif, hot) and reused each time the motif comes back; {@code variety} 0..1 limits how
     * many different palettes the show uses; the favourite colours replace the primary / secondary colours when given.
     */
    public static Palette choose(Show.AutoSettings s, int motif, int kind, int occurrence, List<Integer> used) {
        int style = Math.min(3, s.style);
        Palette[] set = (hot(kind) ? HOT : CALM)[style == Show.AutoSettings.CUSTOM ? 0 : style];
        int distinct = 1 + Math.round(Math.max(0, Math.min(1, s.colorVariety)) * (set.length - 1));
        int index = Math.floorMod(motif * 7 + (hot(kind) ? 3 : 0), distinct);
        Palette pal = set[index];
        if (s.style == Show.AutoSettings.CUSTOM || !s.favoriteColors.isEmpty()) pal = withFavorites(pal, s.favoriteColors, motif);
        if (used != null) used.add(index);
        // repeated sections: same palette, beams and washes swap every other time for a small variation
        if (occurrence % 2 == 1 && s.colorVariety > 0.3f) pal = pal.swapped();
        return pal;
    }

    private static Palette withFavorites(Palette base, List<Integer> fav, int motif) {
        if (fav.isEmpty()) return base;
        int a = fav.get(Math.floorMod(motif, fav.size()));
        int b = fav.size() > 1 ? fav.get(Math.floorMod(motif + 1, fav.size())) : base.secondary();
        int c = fav.size() > 2 ? fav.get(Math.floorMod(motif + 2, fav.size())) : base.accent();
        return new Palette(a, b, c);
    }

    /** Colour of a build: from the calm palette towards white as it rises (progress 0..1). */
    public static int towardsWhite(int rgb, float t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += Math.round((255 - r) * t * 0.6f);
        g += Math.round((255 - g) * t * 0.6f);
        b += Math.round((255 - b) * t * 0.6f);
        return r << 16 | g << 8 | b;
    }

    /** The colours of a colour chase in a palette. */
    public static int[] chase(Palette p) {
        List<Integer> l = new ArrayList<>(List.of(p.primary(), p.secondary(), p.accent()));
        return l.stream().mapToInt(Integer::intValue).toArray();
    }
}
