package com.concertstage.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Terrain shaders (including Shimmer) need Position + ChunkOffset before the view rotation.
 * Split the renderer's affine pose without moving the projected geometry or rebuilding its VBO.
 * CSC caches object rotation in the mesh; the queued pose carries the camera rotation and translation.
 */
public final class GpuLightSpace {
    private GpuLightSpace() {}
    public static void split(Matrix4f modelView, Vector3f chunkOffset) {
        float x = modelView.m30(), y = modelView.m31(), z = modelView.m32();
        new Matrix4f(modelView).invert().transformDirection(x, y, z, chunkOffset);
        modelView.setTranslation(0, 0, 0);
    }
}
