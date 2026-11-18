package com.concertstage.client.light;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/**
 * 1.3.0 rev. 3b — where CSC's stage lights draw (asked 13 November: "the camera image must look like what we see").
 *
 * <p>The stage-light passes (projections with shadows and gobos, beams in the haze, light on the structures, bloom) were written for
 * the main view only: they read the game's main target and camera. A LiveVision camera image now runs the same passes into its own
 * target, seen from its own lens: for the time of that image {@link #main()} and {@link #camera()} answer the camera's, and the
 * scratch targets and per-frame budgets of the passes are swapped with a set kept for the cameras, so the main view finds its own
 * untouched (no resize back and forth, no budget taken from it).
 */
public final class LightTargets {
    private static RenderTarget target;
    private static Camera camera;

    private LightTargets() {}

    /** The target the stage lights draw into: a camera image's while one is drawn, the game's main target otherwise. */
    public static RenderTarget main() {
        RenderTarget t = target;
        return t != null ? t : Minecraft.getInstance().getMainRenderTarget();
    }

    /** The point of view of the stage lights (see {@link #main()}). */
    public static Camera camera() {
        Camera c = camera;
        return c != null ? c : Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    /** True while a camera image is drawn with the full stage lights. */
    public static boolean feed() {
        return target != null;
    }

    /** Start of a camera image with the full stage lights: its target and camera, the cameras' scratch targets and budgets. */
    public static void beginFeed(RenderTarget t, Camera c) {
        if (target != null) return;
        VolumeLights.swapFeed();
        Bloom.swapFeed();
        com.concertstage.client.GpuMesh.swapFeed();
        FixtureRenderer.Perf.swapFeed();
        LightDiagnostics.swapFeed();
        target = t;
        camera = c;
    }

    /** End of that image: everything back to the main view's. */
    public static void endFeed() {
        if (target == null) return;
        target = null;
        camera = null;
        LightDiagnostics.swapFeed();
        FixtureRenderer.Perf.swapFeed();
        com.concertstage.client.GpuMesh.swapFeed();
        Bloom.swapFeed();
        VolumeLights.swapFeed();
    }
}
