package com.concertstage.client.audio;

import com.concertstage.audio.SpeakerBlockEntity;
import com.concertstage.audio.SpeakerModels;
import com.concertstage.client.RenderQuality;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a CSC speaker array: each cabinet at its pose (yaw of the array, splay angles), with its own light; detailed mesh close by,
 * simplified mesh further, a plain box far away (distances from the render quality setting). A flown array also gets its flying
 * frame (bumper) and the two shackles up to the truss.
 */
public class SpeakerRenderer implements BlockEntityRenderer<SpeakerBlockEntity> {
    public SpeakerRenderer(BlockEntityRendererProvider.Context context) {}

    public static final int LOD_NEAR = 0, LOD_FAR = 1, LOD_BOX = 2;

    public static int lod(double distance) {
        RenderQuality q = RenderQuality.current();
        double near = q == RenderQuality.HIGH ? 18 : q == RenderQuality.MEDIUM ? 10 : 0;
        double far = q == RenderQuality.HIGH ? 72 : q == RenderQuality.MEDIUM ? 48 : 32;
        return distance < near ? LOD_NEAR : distance < far ? LOD_FAR : LOD_BOX;
    }

    @Override
    public void render(SpeakerBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        if (!com.concertstage.client.ClientCulling.visible(be, be.getRenderBoundingBox())) return;    // off screen (Fabric; Forge checks it itself)
        if (be.cabinets().isEmpty()) {
            var m=be.supportModel();if(m==null)return;
            pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-be.yaw()));
            support(pose,t->buffers.getBuffer(RenderType.entityCutoutNoCull(t)),com.concertstage.audio.SpeakerFrames.drawn(be.support(),m,be.hanging()),
                m,be.hanging(),be.tilt(),light,be.supportDrop(),be.base());
            pose.popPose();return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int lod = lod(Math.sqrt(be.getBlockPos().distToCenterSqr(cam)));
        List<SpeakerBlockEntity.Pose> poses = be.poses();
        String support = be.support();
        double base = be.base();
        // aurex.2: the light of each cabinet and the cache key are kept per array and worked out again only when the array changed or
        // every half second (they were rebuilt at every frame: thousands of small objects per second for a festival PA)
        long now = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        Memo memo = MEMO.get(be);
        if (memo == null || memo.poses != poses || memo.yaw != be.yaw() || memo.hanging != be.hanging() || memo.lod != lod || memo.light != light
            || now - memo.time >= 10 || now < memo.time) {
            int[] lights = new int[poses.size()];
            for (int i = 0; i < poses.size(); i++) {
                SpeakerBlockEntity.Pose p = poses.get(i);
                Vec3 c = be.toWorld(p, 0, p.model().height() / 2, -p.model().depth() / 2);
                lights[i] = LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(c));
            }
            String key = null;
            if (memo != null && memo.poses == poses && memo.yaw == be.yaw() && memo.hanging == be.hanging() && memo.lod == lod && memo.light == light
                && java.util.Arrays.equals(memo.lights, lights)) key = memo.key;
            if (key == null) {
                StringBuilder k = new StringBuilder(64 + poses.size() * 40).append(be.yaw()).append(be.hanging()).append(lod).append('/').append(light)
                    .append('/').append(support).append('/').append(base);
                for (int i = 0; i < poses.size(); i++) {
                    SpeakerBlockEntity.Pose p = poses.get(i);
                    k.append(';').append(p.model().id()).append(',').append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(',').append(p.pitch()).append(',').append(lights[i]);
                }
                key = k.toString();
            }
            memo = new Memo(poses, be.yaw(), be.hanging(), lod, light, lights, key, now);
            MEMO.put(be, memo);
        }
        int[] lights = memo.lights;
        // 1.1.0: the array (cabinets, frame, links) kept in the graphics card until it changes (angles, lights, detail): see GpuMesh
        if (com.concertstage.client.GpuMesh.enabled()) {
            float yaw = be.yaw();
            boolean hanging = be.hanging();
            com.concertstage.client.GpuMesh m = GPU.get(memo.key, b -> draw(new PoseStack(),
                t -> b.consumer(t, false, true), poses, yaw, hanging, lights, light, lod, support, base));
            if (m != null) {
                m.draw(pose);
                return;
            }
        }
        draw(pose, t -> buffers.getBuffer(RenderType.entityCutoutNoCull(t)), poses, be.yaw(), be.hanging(), lights, light, lod, support, base);
    }

    private static final com.concertstage.client.GpuMesh.Cache GPU = new com.concertstage.client.GpuMesh.Cache(1024);

    private record Memo(List<SpeakerBlockEntity.Pose> poses, float yaw, boolean hanging, int lod, int light, int[] lights, String key, long time) {}

    private static final java.util.Map<SpeakerBlockEntity, Memo> MEMO = new java.util.WeakHashMap<>();

    public static void clearGpu() {
        GPU.clear();
    }

    /** Also used by the Array Tool screen (with full-bright light). Legacy form: one buffer for everything, the family's support. */
    public static void draw(PoseStack pose, VertexConsumer vc, List<SpeakerBlockEntity.Pose> poses, float yaw, boolean hanging, int[] lights, int headLight, int lod) {
        draw(pose, t -> vc, poses, yaw, hanging, lights, headLight, lod, "", 0);
    }

    /**
     * Rev. 4: {@code out} gives the buffer of each texture (a stack adapter is on the new atlas, its Orvane / Veyra / Noryx cabinets on
     * theirs), {@code support} the array's own support (empty: the family's implicit one), {@code base} where the stack starts in its
     * block (on top of its subs).
     */
    public static void draw(PoseStack pose, java.util.function.Function<net.minecraft.resources.ResourceLocation, VertexConsumer> out,
                            List<SpeakerBlockEntity.Pose> poses, float yaw, boolean hanging, int[] lights, int headLight, int lod, String support, double base) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        var firstModel = poses.isEmpty() ? null : poses.get(0).model();
        var frame = com.concertstage.audio.SpeakerFrames.drawn(support, firstModel, hanging);
        double drop=!hanging||poses.isEmpty()?0:1-SpeakerBlockEntity.BUMPER-(poses.get(0).y()+poses.get(0).model().height()*Math.cos(Math.toRadians(poses.get(0).pitch())));
        boolean framed = !poses.isEmpty() && !poses.get(0).model().isMonitor() && (hanging || poses.get(0).y() > base + .1);
        VertexConsumer vc = firstModel == null ? null : out.apply(SpeakerMeshes.texture(firstModel.id()));
        if (framed)
            support(pose,out,frame,poses.get(0).model(),hanging,(float)poses.get(0).pitch(),headLight,drop,base);
        // the links drawn between legacy cabinets; the new pieces carry their own hinges, pins and splay links in their meshes
        boolean ownHardware = firstModel != null && (firstModel.family().equals("aurex_la") || firstModel.family().equals("aurex_c6"))
            || frame != null && frame.centred();
        if (framed && !ownHardware) {
            var first=poses.get(0);
            var b0=new SpeakerBlockEntity.Pose(0,hanging?1-SpeakerBlockEntity.BUMPER-drop:base,first.model().depth()/2,hanging?first.pitch():0,first.model());
            for(int side:new int[]{-1,1}) {
                Vec3 a=local(b0,side*(first.model().width()/2-.018),hanging?.025:.10,-.025);
                Vec3 b=local(first,side*(first.model().width()/2-.018),hanging?first.model().height()-.025:.025,-.025);
                AudioRenderGeometry.link(pose,vc,a,b,headLight);
                if(hanging) {
                    Vec3 ra=local(b0,side*first.model().width()/2,.025,-first.model().depth()+.025);
                    Vec3 rb=local(first,side*(first.model().width()*(first.model().family().equals("orvane")?.445:.5)-.018),rearTop(first.model(),true)-.025,-first.model().depth()+.025);
                    AudioRenderGeometry.link(pose,vc,ra,rb,headLight);
                }
            }
        }
        if (!ownHardware) for (int i=1;i<poses.size();i++) {
            var a=poses.get(i-1);var b=poses.get(i);
            for(int side:new int[]{-1,1}) for(boolean rear:new boolean[]{false,true}) {
                double za=rear?-a.model().depth()+.025:-.025,zb=rear?-b.model().depth()+.025:-.025;
                Vec3 pa=local(a,side*(a.model().width()*(rear&&a.model().family().equals("orvane")?.445:.5)-.018),hanging?.025:rearTop(a.model(),rear)-.025,za);
                Vec3 pb=local(b,side*(b.model().width()*(rear&&b.model().family().equals("orvane")?.445:.5)-.018),hanging?rearTop(b.model(),rear)-.025:.025,zb);
                AudioRenderGeometry.link(pose,vc,pa,pb,headLight);
            }
        }
        for (int i = 0; i < poses.size(); i++) {
            SpeakerBlockEntity.Pose p = poses.get(i);
            SpeakerMeshes.Mesh mesh = SpeakerMeshes.get(p.model().id());
            float[] tris = lod == LOD_NEAR ? mesh.near() : lod == LOD_FAR ? mesh.far() : mesh.box();
            pose.pushPose();
            pose.translate(p.x(), p.y(), p.z());
            pose.mulPose(Axis.XP.rotationDegrees((float) p.pitch()));
            emit(pose, out.apply(SpeakerMeshes.texture(p.model().id())), tris, lights == null ? headLight : lights[i]);
            pose.popPose();
        }
        pose.popPose();
    }

    private static double rearTop(SpeakerModels.Model m,boolean rear) { return rear&&m.family().equals("orvane")&&!m.isSub()?m.height()*.88:m.height(); }
    private static Vec3 local(SpeakerBlockEntity.Pose p,double x,double y,double z) {
        double angle=Math.toRadians(p.pitch());return new Vec3(p.x()+x,p.y()+y*Math.cos(angle)-z*Math.sin(angle),p.z()+y*Math.sin(angle)+z*Math.cos(angle));
    }
    /**
     * The support of an array: legacy frames start at the front edge of the cabinet and are stretched to its size; the rev. 4 pieces
     * (AUREX LA frames, stack adapters, C6 pole) are centred on it as delivered, an adapter on top of its family's own ground frame.
     */
    private static void support(PoseStack pose, java.util.function.Function<net.minecraft.resources.ResourceLocation, VertexConsumer> out,
                                com.concertstage.audio.SpeakerFrames.Frame frame, SpeakerModels.Model model, boolean flying, float tilt, int light,
                                double drop, double base) {
        if (frame == null) return;
        double y = flying ? 1 - SpeakerBlockEntity.BUMPER - drop : base;
        if (!flying && frame.under() != null) {
            var under = com.concertstage.audio.SpeakerFrames.get(frame.under());
            if (under != null) { legacyFrame(pose, out, under, model, false, tilt, light, y); y += under.height(); }
        }
        if (frame.centred()) {
            pose.pushPose();
            pose.translate(0, y, 0);
            if (flying) { pose.translate(0, 0, model.depth() / 2); pose.mulPose(Axis.XP.rotationDegrees(tilt)); pose.translate(0, 0, -model.depth() / 2); }
            emit(pose, out.apply(SpeakerMeshes.texture(frame.id())), SpeakerMeshes.get(frame.id()).near(), light);
            pose.popPose();
        } else legacyFrame(pose, out, frame, model, flying, tilt, light, y);
        if(flying && drop>.01) {
            var b0=new SpeakerBlockEntity.Pose(0,1-SpeakerBlockEntity.BUMPER-drop,model.depth()/2,tilt,model);
            VertexConsumer vc = out.apply(SpeakerMeshes.texture(model.id()));
            for(int side:new int[]{-1,1})AudioRenderGeometry.chain(pose,vc,local(b0,side*model.width()*.22,SpeakerBlockEntity.BUMPER,-model.depth()/2),new Vec3(0,1,0),light);
        }
    }

    private static void legacyFrame(PoseStack pose, java.util.function.Function<net.minecraft.resources.ResourceLocation, VertexConsumer> out,
                                    com.concertstage.audio.SpeakerFrames.Frame frame, SpeakerModels.Model model, boolean flying, float tilt, int light, double y) {
        var ref=SpeakerModels.get(frame.model());
        if (ref == null) return;
        pose.pushPose();pose.translate(0,y,model.depth()/2);
        if(flying)pose.mulPose(Axis.XP.rotationDegrees(tilt));
        double k=SpeakerModels.scale(ref.id());                     // rev. 4b: the legacy frame mesh is at the delivered size
        pose.scale((float)(model.width()*k/ref.width()),1,(float)(model.depth()*k/ref.depth()));
        emit(pose,out.apply(SpeakerMeshes.texture(frame.id())),SpeakerMeshes.get(frame.id()).near(),light);pose.popPose();
    }

    /** Triangles sent as quads (the entity render types are quads): the third corner is doubled. */
    static void emit(PoseStack pose, VertexConsumer vc, float[] t, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int i = 0; i + 23 < t.length; i += 24) {
            for (int k = 0; k < 4; k++) {
                int o = i + Math.min(k, 2) * 8;
                com.concertstage.client.FastVertex.put(vc, m, n, t[o], t[o + 1], t[o + 2], 255, 255, 255, 255, t[o + 3], t[o + 4],
                    OverlayTexture.NO_OVERLAY, light, t[o + 5], t[o + 6], t[o + 7]);                   // 1.2.0
            }
        }
    }

    private static final java.util.Map<String, float[]> BUMPERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Flying frame on top of the first cabinet, and two shackles up to the top of the block (the truss). */
    private static void bumper(PoseStack pose, VertexConsumer vc, SpeakerModels.Model m, int light) {
        float[] t = BUMPERS.computeIfAbsent(m.id(), k -> {
            java.util.List<Float> out = new java.util.ArrayList<>();
            double y0 = 1 - SpeakerBlockEntity.BUMPER, zf = m.depth() / 2, zb = -m.depth() / 2;
            double w = m.width() / 2 + 0.03;
            box(out, -w, y0, zb - 0.02, w, y0 + 0.035, zf + 0.02);                       // frame
            box(out, -w, y0 + 0.035, zf - 0.06, -w + 0.05, 1 - 0.03, zf);                // side plates
            box(out, w - 0.05, y0 + 0.035, zf - 0.06, w, 1 - 0.03, zf);
            box(out, -0.04, y0 + 0.035, zb + 0.1, 0.04, 1, zb + 0.16);                    // rear pick point
            box(out, -m.width() / 4 - 0.02, y0 + 0.035, -0.02, -m.width() / 4 + 0.02, 1, 0.02);   // shackles
            box(out, m.width() / 4 - 0.02, y0 + 0.035, -0.02, m.width() / 4 + 0.02, 1, 0.02);
            float[] f = new float[out.size()];
            for (int i = 0; i < f.length; i++) f[i] = out.get(i);
            return f;
        });
        emit(pose, vc, t, light);
    }

    private static void box(java.util.List<Float> out, double x0, double y0, double z0, double x1, double y1, double z1) {
        float a = (float) x0, b = (float) y0, c = (float) z0, d = (float) x1, e = (float) y1, f = (float) z1;
        float u = SpeakerMeshes.DARK_U, v = SpeakerMeshes.DARK_V;
        float[] uv = {u, v, u, v, u, v, u, v};
        SpeakerMeshes.quad(out, new float[] {a, b, f, d, b, f, d, e, f, a, e, f}, uv, 0, 0, 1);
        SpeakerMeshes.quad(out, new float[] {d, b, c, a, b, c, a, e, c, d, e, c}, uv, 0, 0, -1);
        SpeakerMeshes.quad(out, new float[] {a, e, f, d, e, f, d, e, c, a, e, c}, uv, 0, 1, 0);
        SpeakerMeshes.quad(out, new float[] {a, b, c, d, b, c, d, b, f, a, b, f}, uv, 0, -1, 0);
        SpeakerMeshes.quad(out, new float[] {d, b, f, d, b, c, d, e, c, d, e, f}, uv, 1, 0, 0);
        SpeakerMeshes.quad(out, new float[] {a, b, c, a, b, f, a, e, f, a, e, c}, uv, -1, 0, 0);
    }

    public static void clearBumpers() {
        BUMPERS.clear();
    }

    @Override
    public boolean shouldRenderOffScreen(SpeakerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return RenderQuality.distance(192);
    }
}
