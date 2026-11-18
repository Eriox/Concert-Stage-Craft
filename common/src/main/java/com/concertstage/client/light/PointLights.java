package com.concertstage.client.light;

import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where the fixtures put their coloured point lights (rev. 9): into Shimmer when it is installed and on, otherwise into CSC's own
 * {@link VolumeLights}. The fixture renderer gives world coordinates; {@link #context} tells which block entity pose is current so
 * the CSC lights can be placed in the space the frame is drawn in.
 */
final class PointLights {
    private static BlockPos bp = BlockPos.ZERO;
    private static final Matrix4f POSE = new Matrix4f();
    private static final Vector3f TMP = new Vector3f();

    private PointLights() {}

    /** Point lights are drawn this frame by one of the two systems. */
    static boolean active() {
        return shimmer() || VolumeLights.enabled();
    }

    private static boolean shimmer() {
        return LightClient.shimmer && ShimmerLights.available();
    }

    /** The block entity being drawn and its pose (before any push). */
    static void context(BlockPos pos, Matrix4f blockPose) {
        bp = pos;
        POSE.set(blockPose);
    }

    static void put(Object owner, double x, double y, double z, float r, float g, float b, float strength, float radius) {
        put(owner, x, y, z, r, g, b, strength, radius, false);
    }

    static void put(Object owner, double x, double y, double z, float r, float g, float b, float strength, float radius, boolean secondary) {
        if (shimmer()) { ShimmerLights.put(owner, x, y, z, r, g, b, strength, radius, secondary); return; }
        if (!VolumeLights.enabled()) return;
        TMP.set((float) (x - bp.getX()), (float) (y - bp.getY()), (float) (z - bp.getZ()));
        POSE.transformPosition(TMP);
        VolumeLights.add(TMP, r, g, b, strength, radius);
    }

    static void dark(Object owner) {
        ShimmerLights.dark(owner);       // the CSC lights only live for the frame they are given in
    }
}
