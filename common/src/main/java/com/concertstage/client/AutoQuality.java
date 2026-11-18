package com.concertstage.client;

/**
 * 1.1.1: the AUTO render quality (pure logic, tested off game by AutoQualityTest). Fed once per client tick with Minecraft's
 * frames-per-second counter, it moves the mod's render quality one step at a time:
 * <ul>
 * <li>down one level after {@link #DOWN_AFTER_MS} under {@link #LOW_FPS};</li>
 * <li>up one level after {@link #UP_AFTER_MS} above {@link #HIGH_FPS};</li>
 * <li>never twice within {@link #DWELL_MS} (each change rebuilds the cached meshes: no back-and-forth), never above the quality the
 *     player allows ({@code ceiling}).</li>
 * </ul>
 * Between the two thresholds nothing moves. It only changes how the blocks of the mod are drawn, never the state of a device.
 */
public final class AutoQuality {
    public static final int LOW_FPS = 40, HIGH_FPS = 75;
    public static final long DOWN_AFTER_MS = 3000, UP_AFTER_MS = 15000, DWELL_MS = 20000;

    private int level;
    private long lowSince = -1, highSince = -1, lastChange = Long.MIN_VALUE / 2;

    public AutoQuality(int startLevel) { level = startLevel; }

    public int level() { return level; }

    /** @return the level to use now (0 low .. ceiling) */
    public int update(int fps, long nowMs, int ceiling) {
        return update(fps, nowMs, ceiling, 0);
    }

    /**
     * 1.2.1: {@code capFps} = the frame rate Minecraft is held to (0 = none). Under a low cap (20, 30, 60 images a second) the
     * thresholds follow the cap: a game running at its cap is fast, it only goes down when it falls clearly below it.
     */
    public int update(int fps, long nowMs, int ceiling, int capFps) {
        if (level > ceiling) { level = ceiling; lastChange = nowMs; }
        if (fps <= 0) return level;                                   // no measure yet (loading)
        int low = LOW_FPS, high = HIGH_FPS;
        if (capFps > 0 && capFps * 0.95 < HIGH_FPS) { high = (int) Math.floor(capFps * 0.9); low = Math.min(LOW_FPS, (int) Math.floor(capFps * 0.75)); }
        if (fps < low) { if (lowSince < 0) lowSince = nowMs; } else lowSince = -1;
        if (fps > high) { if (highSince < 0) highSince = nowMs; } else highSince = -1;
        if (nowMs - lastChange < DWELL_MS) return level;
        if (lowSince >= 0 && nowMs - lowSince >= DOWN_AFTER_MS && level > 0) { level--; lastChange = nowMs; lowSince = highSince = -1; }
        else if (highSince >= 0 && nowMs - highSince >= UP_AFTER_MS && level < ceiling) { level++; lastChange = nowMs; lowSince = highSince = -1; }
        return level;
    }
}
