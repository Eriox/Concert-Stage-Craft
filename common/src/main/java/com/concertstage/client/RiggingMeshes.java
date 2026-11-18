package com.concertstage.client;

import com.concertstage.ConcertStage;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Cached shared meshes for the moving towers. Static trusses use Forge's baked OBJ loader. */
public final class RiggingMeshes {
    public static final ResourceLocation ALUMINIUM=new ResourceLocation(ConcertStage.MOD_ID,"textures/block/rigging_detail.png");
    public static final ResourceLocation BLACK=new ResourceLocation(ConcertStage.MOD_ID,"textures/block/rigging_detail_noir.png");
    private static final Map<String,JsonObject> SOURCES=new HashMap<>();
    private static final Map<String,float[][]> QUADS=new HashMap<>();
    /** Mast slices by (height, cell, LOD) without building a String key every frame. */
    private static final Map<Integer,float[][]> MAST=new HashMap<>();
    private RiggingMeshes() {}
    private static final net.minecraft.client.renderer.texture.TextureAtlasSprite[] SPRITES=new net.minecraft.client.renderer.texture.TextureAtlasSprite[2];
    public static void clear() { SOURCES.clear();QUADS.clear();MAST.clear();SPRITES[0]=SPRITES[1]=null; }
    private static JsonObject source(String name) {
        if(SOURCES.containsKey(name))return SOURCES.get(name);
        JsonObject data=null;
        try {
            var resource=Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(ConcertStage.MOD_ID,"rigging/"+name+".json"));
            if(resource.isPresent())try(var reader=new BufferedReader(new InputStreamReader(resource.get().open(),StandardCharsets.UTF_8))) { data=JsonParser.parseReader(reader).getAsJsonObject(); }
        }catch(Exception ex) { LogUtils.getLogger().warn("Cannot load refined rigging model {}",name,ex); }
        SOURCES.put(name,data);return data;
    }
    public static boolean available(int height) { return source("mast_"+height)!=null; }
    private static float[][] parse(JsonArray rows) {
        float[][] out=new float[rows.size()][32];
        for(int i=0;i<out.length;i++) {
            JsonArray row=rows.get(i).getAsJsonArray();if(row.size()!=32)throw new IllegalArgumentException("Rigging quad requires 32 values");
            for(int j=0;j<32;j++)out[i][j]=row.get(j).getAsFloat();
        }
        return out;
    }
    private static float[][] quads(String name,String key) {
        return QUADS.computeIfAbsent(name+"/"+key,k->{ JsonObject d=source(name);return d==null?new float[0][]:parse(d.getAsJsonArray(key)); });
    }
    public static void mast(int height,int cell,boolean near,PoseStack pose,VertexConsumer vc,int light) {
        float[][] data=MAST.get((height*1024+cell)*2+(near?1:0));
        if(data==null) {
            String lod=near?"near":"far";JsonObject d=source("mast_"+height);
            data=d==null?new float[0][]:parse(d.getAsJsonArray("cells").get(cell).getAsJsonObject().getAsJsonArray(lod));
            MAST.put((height*1024+cell)*2+(near?1:0),data);
        }
        draw(data,pose,vc,light,0,0,0);
    }
    public static void carriage(String kind,boolean near,PoseStack pose,VertexConsumer vc,int light) { draw(quads("carriage_"+kind,near?"near":"far"),pose,vc,light,0,0,0); }
    private static void wheel(String name,double x,double y,double z,double degrees,boolean near,PoseStack pose,VertexConsumer vc,int light) {
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.mulPose(Axis.ZP.rotationDegrees((float)degrees));draw(quads(name,near?"near":"far"),pose,vc,light,0,0,0);pose.popPose();
    }
    public static void mechanism(int height,int cell,int carriageCells,double lift,boolean near,PoseStack pose,VertexConsumer vc,int light) {
        double head=TowerRiggingMath.head(height),phase=TowerRiggingMath.phase(lift),arcLength=Math.PI*TowerRiggingMath.HEAD_RADIUS;
        chain(cell,TowerRiggingMath.DRIVE_X,TowerRiggingMath.DRIVE_EXIT,head,arcLength-phase,near,pose,vc,light);
        chain(cell,TowerRiggingMath.LOAD_X,TowerRiggingMath.eye(carriageCells,lift),head,phase,near,pose,vc,light);
        if(cell==height-1) {
            wheel("head_wheel",8,head,TowerRiggingMath.CHAIN_Z,TowerRiggingMath.wheelDegrees(lift,TowerRiggingMath.HEAD_RADIUS),near,pose,vc,light);
            int first=(int)Math.floor((-phase-TowerRiggingMath.LINK_HALF_HEIGHT)/TowerRiggingMath.PITCH);
            int last=(int)Math.ceil((arcLength-phase+TowerRiggingMath.LINK_HALF_HEIGHT)/TowerRiggingMath.PITCH);
            float[] transformed=new float[32];
            for(int i=first;i<=last;i++) {
                double angle=(phase+i*TowerRiggingMath.PITCH)/TowerRiggingMath.HEAD_RADIUS,co=Math.cos(angle),si=Math.sin(angle);
                for(float[] original:quads("chain_link",Math.floorMod(i,2)==0?"a":"b")) {
                    System.arraycopy(original,0,transformed,0,32);
                    double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
                    for(int j=0;j<32;j+=8) {
                        transformed[j]=(float)(8+TowerRiggingMath.HEAD_RADIUS*co+original[j]*co-original[j+1]*si);
                        transformed[j+1]=(float)(head+TowerRiggingMath.HEAD_RADIUS*si+original[j]*si+original[j+1]*co);
                        transformed[j+2]+=(float)TowerRiggingMath.CHAIN_Z;
                        transformed[j+5]=(float)(original[j+5]*co-original[j+6]*si);
                        transformed[j+6]=(float)(original[j+5]*si+original[j+6]*co);
                        minY=Math.min(minY,transformed[j+1]);maxY=Math.max(maxY,transformed[j+1]);
                    }
                    if(minY>=head)quad(transformed,pose,vc,light,0,0,0);
                    else if(maxY>=head)clipped(transformed,0,0,0,head,head+TowerRiggingMath.HEAD_RADIUS+.5,pose,vc,light);
                }
            }
        }
        if(cell==0) {
            wheel("motor_wheel",8,7,-2.75,TowerRiggingMath.wheelDegrees(lift,.7),near,pose,vc,light);
            double max=Math.max(1,height-2-carriageCells),fill=.6+3*Math.max(0,Math.min(1,lift/max));
            pose.pushPose();pose.translate(0,4.1/16,0);pose.scale(1,(float)fill,1);draw(quads("bag_fill","near"),pose,vc,light,0,0,0);pose.popPose();
        }
    }
    private static void chain(int cell,double x,double minimum,double maximum,double phase,boolean near,PoseStack pose,VertexConsumer vc,int light) {
        if(minimum>=maximum)return;
        if(!near) {
            double a=Math.max(minimum,cell*16.),b=Math.min(maximum,(cell+1)*16.);
            if(b>a) { strip(x,a,b,cell,false,pose,vc,light);strip(x,a,b,cell,true,pose,vc,light); }return;
        }
        for(int k=TowerRiggingMath.firstLink(cell,maximum,phase);k<=TowerRiggingMath.lastLink(cell,maximum,phase);k++) {
            double y=maximum+phase-k*TowerRiggingMath.PITCH;
            if(y+TowerRiggingMath.LINK_HALF_HEIGHT<minimum||y-TowerRiggingMath.LINK_HALF_HEIGHT>maximum)continue;
            float[][] data=quads("chain_link",Math.floorMod(k,2)==0?"a":"b");
            if(y-TowerRiggingMath.LINK_HALF_HEIGHT>=minimum&&y+TowerRiggingMath.LINK_HALF_HEIGHT<=maximum)draw(data,pose,vc,light,x,y,TowerRiggingMath.CHAIN_Z);
            else for(float[] q:data)clipped(q,x,y,TowerRiggingMath.CHAIN_Z,minimum,maximum,pose,vc,light);
        }
    }
    private static void strip(double x,double low,double high,int cell,boolean turned,PoseStack pose,VertexConsumer vc,int light) {
        double z=TowerRiggingMath.CHAIN_Z,r=.28,u0=184./512,u1=200./512;
        double v0=(128+(1-(high-cell*16)/16)*128)/512,v1=(128+(1-(low-cell*16)/16)*128)/512;
        if(turned) {
            vertex(pose.last(),vc,light,x,low,z-r,u0,v1,1,0,0);vertex(pose.last(),vc,light,x,high,z-r,u0,v0,1,0,0);vertex(pose.last(),vc,light,x,high,z+r,u1,v0,1,0,0);vertex(pose.last(),vc,light,x,low,z+r,u1,v1,1,0,0);
        }else {
            vertex(pose.last(),vc,light,x-r,low,z,u0,v1,0,0,-1);vertex(pose.last(),vc,light,x-r,high,z,u0,v0,0,0,-1);vertex(pose.last(),vc,light,x+r,high,z,u1,v0,0,0,-1);vertex(pose.last(),vc,light,x+r,low,z,u1,v1,0,0,-1);
        }
    }
    private static void draw(float[][] data,PoseStack pose,VertexConsumer vc,int light,double x,double y,double z) { for(float[] q:data)quad(q,pose,vc,light,x,y,z); }
    private static void quad(float[] q,PoseStack pose,VertexConsumer vc,int light,double x,double y,double z) {
        for(int j=0;j<32;j+=8)vertex(pose.last(),vc,light,q[j]+x,q[j+1]+y,q[j+2]+z,q[j+3],q[j+4],q[j+5],q[j+6],q[j+7]);
    }
    private static List<double[]> clip(List<double[]> input,double y,boolean above) {
        List<double[]> result=new ArrayList<>();if(input.isEmpty())return result;double[] previous=input.get(input.size()-1);boolean was=above?previous[1]>=y:previous[1]<=y;
        for(double[] point:input) {
            boolean inside=above?point[1]>=y:point[1]<=y;
            if(inside!=was) { double t=(y-previous[1])/(point[1]-previous[1]);double[] v=new double[8];for(int j=0;j<8;j++)v[j]=previous[j]+t*(point[j]-previous[j]);result.add(v); }
            if(inside)result.add(point);previous=point;was=inside;
        }
        return result;
    }
    private static void clipped(float[] q,double x,double y,double z,double minimum,double maximum,PoseStack pose,VertexConsumer vc,int light) {
        List<double[]> points=new ArrayList<>(4);for(int j=0;j<32;j+=8)points.add(new double[]{q[j]+x,q[j+1]+y,q[j+2]+z,q[j+3],q[j+4],q[j+5],q[j+6],q[j+7]});points=clip(clip(points,minimum,true),maximum,false);
        for(int j=1;j+1<points.size();j++)for(int k:new int[]{0,j,j+1,j+1}) { double[] v=points.get(k);vertex(pose.last(),vc,light,v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7]); }
    }
    /** Block-style drawing (atlas sprite, block shading, block render type): set by {@link #begin}, null = legacy entity drawing. */
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite sprite;
    private static final org.joml.Vector3f NORMAL=new org.joml.Vector3f();

    /**
     * Starts drawing tower meshes exactly like the static trusses: same atlas texture, same render type (cutout, chunk shader:
     * shaders / Shimmer light it like the blocks) and the same directional shading as baked block quads, so a tower and the truss
     * beside it have the same grey or black.
     */
    public static VertexConsumer begin(net.minecraft.client.renderer.MultiBufferSource buffers,boolean black) {
        int k=black?1:0;
        if(SPRITES[k]==null)SPRITES[k]=Minecraft.getInstance().getModelManager().getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
            .getSprite(new ResourceLocation(ConcertStage.MOD_ID,"block/rigging_detail"+(black?"_noir":"")));
        sprite=SPRITES[k];
        return GpuMesh.cutout(buffers);
    }
    public static void end() { sprite=null; }

    /** Forge's diffuse light for a baked quad normal (the "shade_quads" of the truss OBJ models). */
    public static float blockShade(float nx,float ny,float nz) {
        return Math.min(1f,nx*nx*0.6f+nz*nz*0.8f+ny*ny*(ny>0?1f:0.5f));
    }

    private static void vertex(PoseStack.Pose pose,VertexConsumer vc,int light,double x,double y,double z,double u,double v,double nx,double ny,double nz) {
        if(sprite!=null) {
            NORMAL.set((float)nx,(float)ny,(float)nz);pose.normal().transform(NORMAL);
            int c=(int)(255*blockShade(NORMAL.x,NORMAL.y,NORMAL.z));
            vc.vertex(pose.pose(),(float)(x/16),(float)(y/16),(float)(z/16)).color(c,c,c,255).uv(sprite.getU(u*16),sprite.getV(v*16)).uv2(light).normal(pose.normal(),(float)nx,(float)ny,(float)nz).endVertex();
            return;
        }
        vc.vertex(pose.pose(),(float)(x/16),(float)(y/16),(float)(z/16)).color(255,255,255,255).uv((float)u,(float)v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(),(float)nx,(float)ny,(float)nz).endVertex();
    }
}
