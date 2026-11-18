package com.concertstage.light.core;

/** Rev. 24 — the tilt a fixture without tilt motor gets from the fixture angle tool (pure Java, tested in tools/rev24). */
public final class FixtureTilt {
    private FixtureTilt() {}

    /** One click: up (or down) by {@code step} degrees, back on the grid of the step, held between -90 and +90. */
    public static float next(float tilt, int step, boolean down) {
        float t = Float.isFinite(tilt) ? tilt : 0f;
        int s = Math.max(1, step);
        t += down ? -s : s;
        t = Math.round(t / (float) s) * (float) s;
        return Math.max(-90f, Math.min(90f, t));
    }
}
