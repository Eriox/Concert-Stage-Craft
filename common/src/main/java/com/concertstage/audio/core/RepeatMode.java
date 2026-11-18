package com.concertstage.audio.core;

/** Transport policy shared by server and forthcoming prepared PCM queue. No game dependencies. */
public final class RepeatMode {
    public static final int OFF = 0, ALL = 1, ONE = 2;
    private RepeatMode() {}
    public static int clamp(int mode) { return mode >= OFF && mode <= ONE ? mode : ALL; }
    public static int successor(int current, int count, int mode, boolean natural) {
        if (count <= 0) return -1;
        if (current < 0 || current >= count) return 0;
        mode = clamp(mode);
        if (natural && mode == ONE) return current;
        if (current + 1 < count) return current + 1;
        return mode == OFF ? -1 : 0;
    }
}
