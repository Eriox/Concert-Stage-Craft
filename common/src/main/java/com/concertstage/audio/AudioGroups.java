package com.concertstage.audio;

/** Audio groups of a CSC Audio Console: each linked speaker array plays one of them, each has its fader and mute on the console. */
public final class AudioGroups {
    public static final int MAIN_LEFT = 0, MAIN_RIGHT = 1, SUBS = 2, FRONT_FILL = 3, DELAY = 4, CUSTOM = 5, MONITOR_1 = 6, MONITOR_2 = 7, MONITOR_3 = 8, COUNT = 9;
    private static final String[] KEYS = {"main_left", "main_right", "subs", "front_fill", "delay", "custom", "monitor_1", "monitor_2", "monitor_3"};
    /** Accent colour of each group in the UIs and in the link lines. */
    public static final int[] COLORS = {0xFF5B8CFF, 0xFFE5484D, 0xFFB07CFF, 0xFF4CD08A, 0xFFF2B33D, 0xFF35C9E8, 0xFF73D5A1, 0xFFF5B76B, 0xFFAD9BFF};

    private AudioGroups() {}

    public static String key(int group) {
        return KEYS[Math.max(0, Math.min(COUNT - 1, group))];
    }

    /** Translation key of the group name. */
    public static String name(int group) {
        return "audio.concertstage.group." + key(group);
    }

    /** Which part of the stereo mix the group plays: -1 left, +1 right, 0 the mono sum. */
    public static int side(int group) {
        return group == MAIN_LEFT ? -1 : group == MAIN_RIGHT ? 1 : 0;
    }
}
