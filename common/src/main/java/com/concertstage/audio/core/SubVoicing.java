package com.concertstage.audio.core;

import com.concertstage.audio.SpeakerModels;

/**
 * 1.1.1 audio fix — voicing of each subwoofer model (game settings chosen for each cabinet, not measurements): the crossover with the
 * mains and the level of the subs above a flat sum.
 *
 * <p>Why the subs were weak: the Subs group played the track's low band at exactly the level of the mains (a flat sum), which sounds
 * thin on headphones and computer speakers at game volumes, and a real PA is never tuned flat — its subs run several dB above (the
 * "house curve"); the subs also fell silent before the mains at distance and were muffled by walls as much as the highs. The level
 * here is added on the Subs feed only, and that feed has its own protection limiter (ConsoleMixer): the general level of the mod does
 * not rise, the low band does.
 */
public record SubVoicing(double crossoverHz, double gainDb) {
    /** Unknown or modded subwoofer. */
    public static final SubVoicing DEFAULT = new SubVoicing(90, 4.5);

    public static SubVoicing of(SpeakerModels.Model m) {
        if (m == null || !m.isSub()) return DEFAULT;
        return switch (m.id()) {
            case "csc_aurex_s218_f" -> new SubVoicing(80, 6);    // 2 x 18": lowest crossover, most level
            case "csc_orvane_sb28" -> new SubVoicing(80, 6);     // 2 x 18"
            case "csc_veyra_b2" -> new SubVoicing(90, 4.5);      // compact double sub
            case "csc_noryx_b1" -> new SubVoicing(100, 3);       // small single sub: crosses higher, less level
            case "csc_aurex_b18p" -> new SubVoicing(100, 4);     // rev. 4: portable single 18" under two small C6
            default -> DEFAULT;
        };
    }

    /** Linear level of the voicing. */
    public double gain() {
        return Math.pow(10, gainDb / 20);
    }
}
