package com.concertstage.client.light;

/**
 * Rev. 18 — how the fixtures are drawn far away (pure rules, no game classes: tested by {@code tools/rev18/FarViewTest}).
 *
 * <p>Before rev. 18 a fixture switched to its lowest model at 28 blocks whatever its size (a 2.6x FLASHCORE became a box from the middle
 * of the audience), its body was drawn only up to 75 % / 50 % of the render distance at medium / low quality, and its beam stopped at
 * a fixed 160 blocks then vanished once 24 to 256 beams were drawn. Now:
 * <ul>
 * <li>levels of detail by the size of the fixture on screen (its render scale) and the render quality: in the graphics card a body is
 *     one draw call whatever its number of triangles, so the middle level is kept much further;</li>
 * <li>bodies drawn as far as the world (Minecraft's render distance, not cut by the quality);</li>
 * <li>light distance "auto" (0): the render distance, between 160 and 512 blocks;</li>
 * <li>beyond the budget of full beams, far beams keep a light ribbon in the haze (no ray, no shadow, no projection: a few quads), up to
 *     their own budget — a rig seen from the back of the field still shows all its beams.</li>
 * </ul>
 */
public final class FarView {
    private FarView() {}

    public static final int NEAR = 0, FAR = 1, LOW = 2;
    /** Extra far ribbons per frame, per beam quality (LOW .. ULTRA), after the full beams. */
    private static final int[] FAR_RIBBONS = {96, 192, 384, 640};      // rev. 24: x2 (a ribbon is a few quads)
    /** Hanging hardware: drawn up to this distance (blocks) with the GPU cache / without it (it is ~0.2 block high). */
    public static final float MOUNT_GPU = 64f, MOUNT_DIRECT = 24f;

    /** Size factor of a fixture: 1 for a VORTEX (render scale 1.95), between 0.8 and 1.4. */
    static float size(float renderScale) {
        return Math.max(0.8f, Math.min(1.4f, renderScale / 1.95f));
    }

    /**
     * Level of detail. {@code quality}: 0 low, 1 medium, 2 high (RenderQuality). With the GPU cache: full model up to 14 blocks (x size,
     * x quality share), middle model up to 64; without it (shader pack, every triangle sent each frame): middle model up to 12 blocks,
     * the lowest beyond.
     */
    public static int lod(double dist, float renderScale, int quality, boolean gpu) {
        float k = size(renderScale) * (quality <= 0 ? 0.6f : quality == 1 ? 0.8f : 1f);
        if (!gpu) return dist < 12 * k ? FAR : LOW;
        return dist < 14 * k ? NEAR : dist < 64 * k ? FAR : LOW;
    }

    /**
     * Beam quality by distance (capped by the frame quality): ultra < 32, high < 64, medium < 384 (rev. 24), low beyond. Rev. 21: medium up to 256
     * blocks (was 128): beyond 128 a stage lost its projected light, its shadows and its beams in the haze, and the whole rig went dark
     * seen from the back of a large audience (picture 4 of 11 November). The frame budgets still choose which fixtures get them.
     */
    public static int beamQuality(double dist, int frameQuality) {
        int q = dist < 32 ? 3 : dist < 64 ? 2 : dist < 384 ? 1 : 0;      // rev. 24: medium up to 384 blocks (was 256)
        return Math.min(frameQuality, q);
    }

    public static int farRibbons(int quality) {
        return FAR_RIBBONS[Math.max(0, Math.min(3, quality))];
    }

    /** Light distance: the setting, or with "auto" (0, and the old default 160) the render distance between 160 and 512 blocks. */
    public static int lightDistance(int setting, int renderBlocks) {
        if (setting > 0 && setting != 160) return setting;
        return Math.max(160, renderBlocks);                                // rev. 24: no 512-block ceiling: as far as the world
    }

    /** Halo growth with distance: a lit lens keeps a few pixels on screen (x1 close, up to x3.2 at 200 blocks and more). */
    public static float haloGrowth(double dist) {
        return 1f + (float) Math.min(2.2, Math.max(0, dist) / 40.0);
    }
}
