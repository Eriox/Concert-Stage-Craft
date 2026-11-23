package com.concertstage.light.core;

/**
 * 1.2.2 — a real tungsten lamp on a dimmer (Conventionals, Blinders).
 *
 * <p>A halogen filament is not a linear light: the dimmer sets the voltage, the filament heats, and the light follows roughly the
 * square of the voltage, so a lamp at half a fader is a fifth of its full output, not half. More than that, the filament takes time
 * to heat and cools slowly: a lamp switched on comes up over about a tenth of a second, and one switched off glows on for half a
 * second, through amber, before it is dark.
 *
 * <p>Without this, a conventional's pool on the floor was flat from 100 % down to about 15 % of the fader and then dropped: the
 * light on the surfaces is tone mapped (a soft ceiling, {@code 1 + 2.2 tanh(L / 1.83)}), and a linear dimmer sat at the ceiling for
 * most of its travel. The dimmer law below gives the tone map room to show the level, and the heat and cool times put the lamp's
 * own inertia in front of it. The lamp's colour is worked out from the same lagging level ({@link Engine#halogen}), so it warms up
 * through amber and cools down through amber as a real lamp does.
 *
 * <p>Pure Java (no Minecraft class): tested by {@code tools/lightshow_tests/TungstenTest.java}. The renderer keeps one
 * {@link #step} state per fixture; the server and the desk output are untouched, this is a look only.
 */
public final class LampModel {
    /** Light of a filament against its dimmer (voltage squared; a halogen on a triac dimmer). */
    public static final float LIGHT_LAW = 2.2f;
    /** Time constants of the filament (seconds): it heats faster than it cools. */
    public static final float HEAT = 0.075f, COOL = 0.26f;
    /** The "slow" setting of the desk multiplies both times by this (a big 2 kW lamp). */
    public static final float SLOW = 2.4f;

    private LampModel() {}

    /** A fixture with a real tungsten lamp: the 1.2.1 conventionals and the halogen blinders. */
    public static boolean tungsten(FixtureProfile profile) {
        return profile != null && (profile.conventional || com.concertstage.light.dmx.DmxMode.tungsten(profile));
    }

    /** The light the lamp gives at a dimmer (0..1), with no inertia yet. */
    public static float drive(float dimmer) {
        return (float) Math.pow(clamp(dimmer), LIGHT_LAW);
    }

    /**
     * One step of the filament towards {@code target}: asymptotic, frame-rate independent (the same curve at 20 and at 240 images
     * a second) and asymmetric (heating is quicker than cooling). {@code speed} is the desk's lamp-fade setting: 0 = no inertia
     * (the target at once), 1 = realistic, {@link #SLOW} = slow.
     */
    public static float step(float level, float target, float dt, float speed) {
        if (!(dt > 0f)) return clamp(level);
        if (!(speed > 0f)) return clamp(target);
        float tau = (target > level ? HEAT : COOL) * speed;
        float a = 1f - (float) Math.exp(-dt / tau);
        float next = level + (target - level) * a;
        if (Math.abs(target - next) < 5.0e-4f) next = target;              // settle: no endless last per-mille
        return clamp(next);
    }

    /**
     * CPU twin of the surface composite for one fixture ({@code 1 + 2.2 tanh(L / 1.83)}, rev. 24): the multiplier the scene
     * gets from a light value {@code L}. Tests compare a linear dimmer with the tungsten law through it.
     */
    public static float composite(float light) {
        return 1f + 2.2f * (float) Math.tanh(light / 1.83f);
    }

    private static float clamp(float v) {
        return Float.isNaN(v) ? 0f : v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
