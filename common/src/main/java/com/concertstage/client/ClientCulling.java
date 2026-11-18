package com.concertstage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Performance (1.0.2). The renderers of big pieces (roofs, rings, angled trusses, slanted tarps) are drawn off the chunk grid: on Fabric
 * (vanilla) nothing checks whether they are on screen, they were drawn every frame even behind the player. Fabric gives the view frustum
 * here every frame and these renderers skip what is off screen (Forge does the same check itself: no frustum is set there). Also the
 * distance used for the levels of detail.
 */
public final class ClientCulling {
    private static volatile Frustum frustum;

    private ClientCulling() {}

    public static Frustum frustum() {
        return frustum;
    }

    public static void setFrustum(Frustum f) {
        frustum = f;
    }

    /**
     * False when the block entity is in the world and its box is off screen. A copy drawn by something else (a structure travelling
     * with a lift) is never skipped: its box is not where it is drawn.
     */
    public static boolean visible(BlockEntity be, AABB box) {
        Frustum f = frustum;
        if (f == null || box == null || be.getLevel() == null) return true;
        if (be.getLevel().getBlockEntity(be.getBlockPos()) != be) return true;
        return f.isVisible(box);
    }

    /** Distance from the camera to the nearest point of the box (0 inside). */
    public static double distance(AABB box) {
        Vec3 c = com.concertstage.client.light.LightTargets.camera().getPosition();    // rev. 3b: a camera image's lens when one is drawn
        double dx = Math.max(0, Math.max(box.minX - c.x, c.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - c.y, c.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - c.z, c.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Level of detail of the truss meshes: 0 full, 1 no bracing diagonals, 2 chords only (from the render quality setting). */
    public static int trussDetail(double distance) {
        RenderQuality q = RenderQuality.current();
        double d1 = q == RenderQuality.HIGH ? 40 : q == RenderQuality.MEDIUM ? 28 : 16;
        double d2 = q == RenderQuality.HIGH ? 96 : q == RenderQuality.MEDIUM ? 64 : 40;
        return distance < d1 ? 0 : distance < d2 ? 1 : 2;
    }
}
