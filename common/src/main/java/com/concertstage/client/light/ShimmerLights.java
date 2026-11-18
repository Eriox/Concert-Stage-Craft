package com.concertstage.client.light;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Coloured light on the blocks a beam hits, through Shimmer when it is installed (1.2.0 rev. 2) — the same way Theatrical: Extra Lights
 * lights a stage. No compile dependency: Shimmer's public API ({@code LightManager.INSTANCE.addLight(Vector3f pos, int argb, float radius)},
 * {@code ColorPointLight.update()} / {@code remove()}) is found by reflection the first time; if it is missing or behaves differently the
 * bridge switches itself off and the beams keep their own light pools.
 *
 * <p>One light per beam (the brightest beams first, up to {@link LightClient#shimmerMax}), updated only when its colour, position or
 * radius really changed, and removed when its fixture has not been drawn for a second (off screen, unloaded, unpatched).
 * Shimmer's light is a visual effect only (no block light level).
 */
final class ShimmerLights {
    private static final class Slot {
        Object light;
        float x, y, z, radius;
        int color;
        long seenFrame;
    }

    private static Boolean available;
    private static Object manager;
    private static Method addLight, update, remove, setColor, setRadius;
    private static Class<?> vecClass;
    private static java.lang.reflect.Constructor<?> vecCtor;
    private static Field fx, fy, fz, fr, fg, fb, fa, fRadius;
    private static final Map<Object, Slot> SLOTS = new IdentityHashMap<>();
    private static long frame;
    private static int usedThisFrame, usedLastFrame;

    private ShimmerLights() {}

    /** Rev. 8: one line for the F3 screen — whether Shimmer was found and how many CSC lights are really active in it. */
    static String stats() {
        if (!USE) return "Shimmer: not used — CSC lights the blocks itself, the same with or without Shimmer (rev. 20)";
        if (!LightClient.shimmer) return "Shimmer light: off (desk setting)";
        if (available == null) return "Shimmer: not checked yet";
        if (!available) return "Shimmer: not installed (or its API changed): surfaces lit by CSC projection only";
        String colored = coloredLightEnabled();
        return "Shimmer: " + SLOTS.size() + " CSC lights held, " + usedLastFrame + " lit last frame (budget " + LightClient.shimmerMax + ")" + colored;
    }

    /** Shimmer's own "colored light" switch (its config): when it is off, Shimmer draws none of the lights, CSC's included. */
    private static String coloredLightEnabled() {
        try {
            Object platform = Class.forName("com.lowdragmc.shimmer.platform.Services").getField("PLATFORM").get(null);
            Object on = platform.getClass().getMethod("isColoredLightEnable").invoke(platform);
            return Boolean.FALSE.equals(on) ? " — Shimmer coloured light is OFF in Shimmer's config" : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Rev. 20: CSC no longer puts its lights into Shimmer. Seen in the author's game (Shimmer 0.3.0 with its bloom, AutoShow drop): the
     * Shimmer lights came on top of CSC's own projected light, without shadows, and with Shimmer's bloom the audience, the trees and the
     * stage burnt to white with a speckle of light on the leaves. The look must not depend on another mod: the light on the blocks is
     * CSC's (projection, tone mapped) with or without Shimmer installed. Shimmer itself keeps working for the other mods.
     */
    private static final boolean USE = Boolean.getBoolean("concertstage.useShimmer");

    static boolean available() {
        if (!USE) return false;
        if (available == null) available = init();
        return available && LightClient.shimmer;
    }

    private static boolean init() {
        try {
            Class<?> lm = Class.forName("com.lowdragmc.shimmer.client.light.LightManager");
            manager = lm.getField("INSTANCE").get(null);
            for (Method m : lm.getMethods()) {
                if (m.getName().equals("addLight") && m.getParameterCount() == 3 && m.getParameterTypes()[1] == int.class
                    && m.getParameterTypes()[2] == float.class) {
                    addLight = m;
                    vecClass = m.getParameterTypes()[0];
                }
            }
            if (addLight == null) return false;
            vecCtor = vecClass.getConstructor(float.class, float.class, float.class);
            Class<?> light = addLight.getReturnType();
            update = light.getMethod("update");
            remove = light.getMethod("remove");
            try { setColor = light.getMethod("setColor", int.class); } catch (NoSuchMethodException ignored) { }
            try { setRadius = light.getMethod("setRadius", float.class); } catch (NoSuchMethodException ignored) { }
            fx = field(light, "x"); fy = field(light, "y"); fz = field(light, "z");
            fr = field(light, "r"); fg = field(light, "g"); fb = field(light, "b"); fa = field(light, "a"); fRadius = field(light, "radius");
            com.mojang.logging.LogUtils.getLogger().info("Concert Stage: Shimmer found and enabled by -Dconcertstage.useShimmer, the CSC beams also feed its block lights");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Field field(Class<?> c, String name) {
        try { return c.getField(name); } catch (Throwable t) { return null; }
    }

    /** Start of a frame: removes the lights of the fixtures that were not drawn for 20 frames. */
    static void frame(long frameNo) {
        frame = frameNo;
        usedLastFrame = usedThisFrame;
        usedThisFrame = 0;
        if (SLOTS.isEmpty()) return;
        for (Iterator<Slot> it = SLOTS.values().iterator(); it.hasNext(); ) {
            Slot s = it.next();
            if (frame - s.seenFrame > 20) { kill(s); it.remove(); }
        }
    }

    /** Light of one fixture's beam this frame (world coordinates; colour 0..1; strength 0..1 = alpha). */
    static void put(Object owner, double x, double y, double z, float r, float g, float b, float strength, float radius) {
        put(owner, x, y, z, r, g, b, strength, radius, false);
    }

    /**
     * {@code secondary}: a light around a fixture (rev. 4), not the light of a beam where it hits. It only takes a new slot while less
     * than 75 % of the budget is used (rev. 8; 60 % before), so the beams keep theirs.
     */
    static void put(Object owner, double x, double y, double z, float r, float g, float b, float strength, float radius, boolean secondary) {
        if (!available()) return;
        Slot existing = SLOTS.get(owner);
        int cap = secondary ? (int) (LightClient.shimmerMax * 0.75f) : LightClient.shimmerMax;
        if (existing == null && (strength < 0.02f || usedThisFrame >= cap)) return;
        if (strength < 0.02f) strength = 0f;          // a strobe between two flashes: the light stays, dark (no remove / add 25 times a second)
        else usedThisFrame++;
        int argb = (Math.round(Math.min(1f, strength) * 255) << 24) | (Math.round(Math.min(1f, r) * 255) << 16)
            | (Math.round(Math.min(1f, g) * 255) << 8) | Math.round(Math.min(1f, b) * 255);
        Slot s = SLOTS.get(owner);
        try {
            if (s == null || s.light == null) {
                s = new Slot();
                s.light = addLight.invoke(manager, vecCtor.newInstance((float) x, (float) y, (float) z), argb, radius);
                if (s.light == null) return;                // Shimmer full: nothing this frame
                SLOTS.put(owner, s);
                s.x = (float) x; s.y = (float) y; s.z = (float) z; s.color = argb; s.radius = radius;
            } else if (Math.abs(s.x - x) + Math.abs(s.y - y) + Math.abs(s.z - z) > 0.05 || s.color != argb || Math.abs(s.radius - radius) > 0.1f) {
                boolean moved = false;
                if (fx != null && fy != null && fz != null) { fx.setFloat(s.light, (float) x); fy.setFloat(s.light, (float) y); fz.setFloat(s.light, (float) z); moved = true; }
                if (setColor != null) setColor.invoke(s.light, argb);
                else if (fr != null && fg != null && fb != null && fa != null) {
                    fr.setFloat(s.light, ((argb >> 16) & 255) / 255f); fg.setFloat(s.light, ((argb >> 8) & 255) / 255f);
                    fb.setFloat(s.light, (argb & 255) / 255f); fa.setFloat(s.light, ((argb >>> 24) & 255) / 255f);
                }
                if (setRadius != null) setRadius.invoke(s.light, radius); else if (fRadius != null) fRadius.setFloat(s.light, radius);
                if (moved) update.invoke(s.light);
                else { kill(s); s.light = addLight.invoke(manager, vecCtor.newInstance((float) x, (float) y, (float) z), argb, radius); }
                s.x = (float) x; s.y = (float) y; s.z = (float) z; s.color = argb; s.radius = radius;
            }
            s.seenFrame = frame;
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: Shimmer bridge disabled ({})", t.toString());
            available = false;
            clear();
        }
    }

    /** The fixture is drawn but its beam is dark this frame: its light goes dark, and stays reserved. */
    static void dark(Object owner) {
        Slot s = SLOTS.get(owner);
        if (s != null) put(owner, s.x, s.y, s.z, 0, 0, 0, 0, s.radius, false);
    }

    static void release(Object owner) {
        Slot s = SLOTS.remove(owner);
        if (s != null) kill(s);
    }

    private static void kill(Slot s) {
        if (s.light == null) return;
        try { remove.invoke(s.light); } catch (Throwable ignored) { }
        s.light = null;
    }

    /** Leaving the world / turning the option off. */
    static void clear() {
        for (Slot s : SLOTS.values()) kill(s);
        SLOTS.clear();
    }
}
