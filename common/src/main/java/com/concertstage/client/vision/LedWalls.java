package com.concertstage.client.vision;

import com.concertstage.vision.OutputState;
import com.concertstage.vision.WallData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

/**
 * CSC 1.3.0 - draws every LED wall as ONE picture spread over its panels (an 8 x 5 wall shows one image, never forty copies), from the
 * wall registry the server sends: walls are drawn far beyond the loaded chunks (long range), the terrain that is drawn still hides them
 * (depth test), and walls beyond the far plane are kept at the far plane (depth clamp) instead of being cut.
 *
 * <p>Order: once per frame after the block entities and the fixture bodies, before the beams. A transition draws the two pictures
 * (DISSOLVE: the new one over the old with a growing opacity; FADE: old to black, then black to new).
 */
public final class LedWalls {
    /** Distance of the LED surface in front of the block centre, against the facing (the panel hangs at the back of its block). */
    static final float SCREEN = 0.3765f;
    private static long drawnFrame = -1;

    private LedWalls() {}

    /** Main view: once per frame (GpuMesh.flush runs twice). */
    public static void drawMain() {
        if (CameraFeeds.rendering) return;
        long f = com.concertstage.client.GpuMesh.frameSerial;
        if (f == drawnFrame) return;
        drawnFrame = f;
        var mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera();
        Matrix4f view = new Matrix4f().rotation(Axis.XP.rotationDegrees(cam.getXRot())).rotate(Axis.YP.rotationDegrees(cam.getYRot() + 180f));
        long t0 = System.nanoTime();
        // 1.2.0 beta 2b (perf): half the diagonal of the field of view, for the culling of the walls out of the screen
        double vfov = Math.toRadians(mc.options.fov().get() * 1.15);
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double halfDiag = Math.atan(Math.tan(vfov / 2) * Math.sqrt(1 + aspect * aspect));
        draw(cam.getPosition(), view, false, halfDiag);
        VisionStats.wallNanos += System.nanoTime() - t0;
    }

    /** Draws the walls for a view at {@code cam} (world) with the rotation {@code view} (camera frame). */
    public static void draw(Vec3 cam, Matrix4f view, boolean feed) {
        draw(cam, view, feed, Math.toRadians(70));
    }

    /** Same, culling the walls outside a cone of {@code halfDiag} radians around the view direction. */
    public static void draw(Vec3 cam, Matrix4f view, boolean feed, double halfDiag) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || VisionClient.WALLS.isEmpty()) return;
        double time = mc.level.getGameTime() + mc.getFrameTime();
        double max = VisionSettings.maxWallDistance();
        Matrix4f look = new Matrix4f(view).invert();
        Vec3 fwd = new Vec3(look.transformDirection(new org.joml.Vector3f(0, 0, -1)));
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            GL11.glEnable(GL32.GL_DEPTH_CLAMP);
            for (WallData.Wall w : VisionClient.WALLS.values()) {
                if (w.count == 0) continue;
                double cx, cy = (w.v0 + w.v1 + 1) / 2.0, cz;
                if (w.facing.getAxis() == Direction.Axis.Z) { cx = (w.u0 + w.u1 + 1) / 2.0; cz = w.plane + 0.5; }
                else { cx = w.plane + 0.5; cz = (w.u0 + w.u1 + 1) / 2.0; }
                if (w.angle != 0f) { double[] t = w.turn(cx, cz); cx = t[0]; cz = t[1]; }     // rev. 3: walls at an angle
                double rad = Math.max(w.width(), w.height()) * 0.75;
                Vec3 d = new Vec3(cx - cam.x, cy - cam.y, cz - cam.z);
                double dist = d.length();
                if (dist - rad > max) continue;
                if (dist > rad && d.dot(fwd) < -rad) continue;                       // behind the view
                // 1.2.0 beta 2b (perf, 14 November): out of the field of view. Before, a wall off the screen was still drawn and, worse,
                // kept its camera image rendered (a second view of the whole stage, 13 ms a frame in the author's scene)
                if (dist > rad && Math.acos(Math.max(-1, Math.min(1, d.dot(fwd) / dist))) > halfDiag + Math.asin(Math.min(1, rad / dist)) + 0.05) continue;
                // a wall seen from behind shows its black back (the panel model)
                var n = w.facing.getNormal();
                double nx = n.getX(), nz = n.getZ();
                if (w.angle != 0f) { double a = Math.toRadians(w.angle), c = Math.cos(a), sn = Math.sin(a); double tx = nx * c + nz * sn; nz = -nx * sn + nz * c; nx = tx; }
                if ((cam.x - cx) * nx + (cam.z - cz) * nz < -0.6) continue;
                if (!VisionSettings.longRange && !loaded(mc, w.pos(w.u0, w.v0))) continue;
                int tier = VisionSettings.tier(dist);
                drawWall(w, cam, view, time, tier, feed);
                VisionStats.wall(tier >= 2, w.count);
            }
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage LiveVision: wall drawing error", t);
        } finally {
            GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private static void drawWall(WallData.Wall w, Vec3 cam, Matrix4f view, double time, int tier, boolean feed) {
        OutputState o = VisionClient.output(w);
        float bright = w.brightness / 100f * (o == null ? 1f : o.brightness / 100f);
        // base: the panels switched off / black (opaque, writes depth)
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        layer(w, cam, view, 0, false, 0.02f, 0.02f, 0.025f, 1f);
        if (o == null || bright <= 0.001f) return;
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float t = o.progress(time);
        if (t >= 1f) source(w, cam, view, o.program, o, time, bright, 1f, feed);
        else if (o.transition == OutputState.FADE) {
            if (t < 0.5f) source(w, cam, view, o.from, o, time, bright, 1f - 2f * t, feed);
            else source(w, cam, view, o.program, o, time, bright, 2f * t - 1f, feed);
        } else {
            source(w, cam, view, o.from, o, time, bright, 1f, feed);
            source(w, cam, view, o.program, o, time, bright, t, feed);
        }
    }

    private static void source(WallData.Wall w, Vec3 cam, Matrix4f view, String src, OutputState o, double time, float bright, float alpha, boolean feed) {
        if (alpha <= 0.002f) return;
        // rev. 3: the quality and images per second this wall asks for its camera (the feed takes the largest of its walls)
        MediaLibrary.Frame f;
        if (src != null && src.startsWith("cam:")) {
            // 1.2.0 beta 2b (perf): inside a camera image, a wall shows the last picture of its camera but does not ask for it — a camera
            // seen only through another camera's image (or a wall seen only there) no longer keeps its image rendered
            if (feed) f = CameraFeeds.peek(OutputState.camPos(src));
            else {
                CameraFeeds.want(OutputState.camPos(src), w.quality, w.fps);
                f = MediaLibrary.frame(src, o, time);
            }
        } else f = MediaLibrary.frame(src, o, time);
        if (f == null || f.texture() <= 0) return;
        layer(w, cam, view, f.texture(), f.flipV(), bright, bright, bright, alpha);
    }

    /** One picture over the whole wall: a quad per run of panels in each row, texture coordinates of the wall rectangle. */
    private static void layer(WallData.Wall w, Vec3 cam, Matrix4f view, int texture, boolean flipV, float r, float g, float b, float a) {
        RenderSystem.setShaderTexture(0, texture <= 0 ? whiteTexture() : texture);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int ir = clamp(r), ig = clamp(g), ib = clamp(b), ia = clamp(a);
        int width = w.width(), height = w.height();
        var n = w.facing.getNormal();
        boolean zAxis = w.facing.getAxis() == Direction.Axis.Z;
        double plane = w.plane + 0.5 - SCREEN * (zAxis ? n.getZ() : n.getX());
        boolean reversed = w.facing == Direction.NORTH || w.facing == Direction.EAST;   // image left at the high u
        for (int v = w.v0; v <= w.v1; v++) {
            int u = w.u0;
            while (u <= w.u1) {
                if (!w.has(u, v)) { u++; continue; }
                int start = u;
                while (u <= w.u1 && w.has(u, v)) u++;
                double ua = start, ub = u;                       // [ua, ub) along u, [v, v+1) up
                float sa = (float) (reversed ? (w.u1 + 1 - ua) / width : (ua - w.u0) / width);
                float sb = (float) (reversed ? (w.u1 + 1 - ub) / width : (ub - w.u0) / width);
                float tt = (float) ((w.v1 + 1 - (v + 1)) / (double) height), tb = (float) ((w.v1 + 1 - v) / (double) height);
                if (flipV) { tt = 1 - tt; tb = 1 - tb; }
                vertex(bb, w, view, cam, zAxis, plane, ua, v, sa, tb, ir, ig, ib, ia);
                vertex(bb, w, view, cam, zAxis, plane, ub, v, sb, tb, ir, ig, ib, ia);
                vertex(bb, w, view, cam, zAxis, plane, ub, v + 1, sb, tt, ir, ig, ib, ia);
                vertex(bb, w, view, cam, zAxis, plane, ua, v + 1, sa, tt, ir, ig, ib, ia);
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void vertex(BufferBuilder bb, WallData.Wall w, Matrix4f view, Vec3 cam, boolean zAxis, double plane, double u, double y, float s, float t,
                               int r, int g, int b, int a) {
        double x = zAxis ? u : plane, z = zAxis ? plane : u;
        if (w.angle != 0f) { double[] p = w.turn(x, z); x = p[0]; z = p[1]; }
        bb.vertex(view, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).uv(s, t).color(r, g, b, a).endVertex();
    }

    /** Whether this client holds the chunk (ClientLevel.hasChunkAt always answers true). */
    public static boolean loaded(Minecraft mc, net.minecraft.core.BlockPos p) {
        return mc.level != null && mc.level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, false) != null;
    }

    private static int clamp(float f) { return Math.max(0, Math.min(255, Math.round(f * 255))); }

    private static VTex white;

    static int whiteTexture() {
        if (white == null || white.id() < 0) {
            white = new VTex(1, 1);
            white.upload(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        }
        return white.id();
    }
}
