package com.concertstage.client.audio;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class AudioRenderGeometry {
    private static final Map<Integer,float[]> CUBES=new HashMap<>();
    static void box(PoseStack p,VertexConsumer vc,double x,double y,double z,double w,double h,double d,int colour,int light) {
        float[] mesh=CUBES.computeIfAbsent(colour,k->{
            java.util.List<Float> out=new java.util.ArrayList<>();float u=(808+16*k)/1024f,v=328/1024f;float[] uv={u,v,u,v,u,v,u,v};
            SpeakerMeshes.quad(out,new float[]{0,0,1,1,0,1,1,1,1,0,1,1},uv,0,0,1);
            SpeakerMeshes.quad(out,new float[]{1,0,0,0,0,0,0,1,0,1,1,0},uv,0,0,-1);
            SpeakerMeshes.quad(out,new float[]{0,1,1,1,1,1,1,1,0,0,1,0},uv,0,1,0);
            SpeakerMeshes.quad(out,new float[]{0,0,0,1,0,0,1,0,1,0,0,1},uv,0,-1,0);
            SpeakerMeshes.quad(out,new float[]{1,0,1,1,0,0,1,1,0,1,1,1},uv,1,0,0);
            SpeakerMeshes.quad(out,new float[]{0,0,0,0,0,1,0,1,1,0,1,0},uv,-1,0,0);
            float[] a=new float[out.size()];for(int i=0;i<a.length;i++)a[i]=out.get(i);return a;
        });
        p.pushPose();p.translate(x,y,z);p.scale((float)w,(float)h,(float)d);SpeakerRenderer.emit(p,vc,mesh,light);p.popPose();
    }
    static void chain(PoseStack pose,VertexConsumer vc,Vec3 a,Vec3 b,int light) {
        Vec3 d=b.subtract(a);double len=d.length();if(len<.005)return;int count=Math.max(1,(int)Math.ceil(len/.024));
        var rotation=new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)(d.x/len),(float)(d.y/len),(float)(d.z/len)));
        for(int i=0;i<count;i++) {
            Vec3 at=a.add(d.scale((i+.5)/count));pose.pushPose();pose.translate(at.x,at.y,at.z);pose.mulPose(rotation);
            if((i&1)==1)pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
            pose.scale(1,(float)(len/count/.030),1);SpeakerRenderer.emit(pose,vc,SpeakerMeshes.get("csc_chain_link").near(),light);pose.popPose();
        }
    }
    static void link(PoseStack pose,VertexConsumer vc,Vec3 a,Vec3 b,int light) {
        Vec3 delta=b.subtract(a);double len=delta.length();if(len<.001)return;
        pose.pushPose();pose.translate(a.x,a.y,a.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)(delta.x/len),(float)(delta.y/len),(float)(delta.z/len))));
        pose.scale(1,(float)len,1);SpeakerRenderer.emit(pose,vc,SpeakerMeshes.get("csc_rig_link").near(),light);pose.popPose();
    }
}
