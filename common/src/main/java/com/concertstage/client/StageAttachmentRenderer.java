package com.concertstage.client;

import com.concertstage.stage.*;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Only mounted parts are dynamic. Full bases and their item previews are baked OBJ models. */
public final class StageAttachmentRenderer implements BlockEntityRenderer<StageAttachmentBlockEntity> {
    private static final ResourceLocation[] TEXTURES={new ResourceLocation("concertstage","textures/block/stage/metals.png"),new ResourceLocation("concertstage","textures/block/stage/anti_slip.png"),new ResourceLocation("concertstage","textures/block/stage/hardware.png"),new ResourceLocation("concertstage","textures/block/rigging_detail.png"),new ResourceLocation("concertstage","textures/block/rigging_detail_noir.png"),new ResourceLocation("concertstage","textures/block/stage/csc_v3_atlas.png")};
    private static final Map<String,float[][][]> SOURCES=new HashMap<>();
    private static final Map<StageAttachmentBlockEntity,Cached> CACHE=new WeakHashMap<>();
    private static int generation;
    private record Cached(int revision,int generation,String signature,float[][][] quads){}
    /** 1.1.0: the baked pieces in the graphics card, by pieces + light + hardware (see GpuMesh). */
    private static final GpuMesh.Cache GPU=new GpuMesh.Cache(1024);
    /** Baked meshes shared by every cell with the same pieces at the same places (railings repeated along a stage...), at most 512 (1.0.2). */
    private static final Map<String,float[][][]> SHARED=new LinkedHashMap<>(256,0.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,float[][][]> e){return size()>512;}};
    private static String signature(StageAttachmentBlockEntity be){StringBuilder b=new StringBuilder();for(var e:be.entries()){b.append(e.key()).append('@').append(e.x()).append(',').append(e.y()).append(',').append(e.z()).append(',').append(e.degrees());for(double c:e.clip())b.append(',').append(c);b.append(';');}return b.toString();}
    private static final double HARDWARE_DISTANCE=24;
    public StageAttachmentRenderer(BlockEntityRendererProvider.Context context){}
    public static void clear(){SOURCES.clear();CACHE.clear();SHARED.clear();GPU.clear();generation++;}
    private static float[][][] source(String key){return SOURCES.computeIfAbsent(key,k->{
        float[][][] out=new float[TEXTURES.length][][];
        try(var resource=Minecraft.getInstance().getResourceManager().getResourceOrThrow(new ResourceLocation("concertstage","stage_meshes/"+key+".json")).open();var reader=new InputStreamReader(resource,StandardCharsets.UTF_8)){
            JsonObject data=JsonParser.parseReader(reader).getAsJsonObject();for(int slot=0;slot<TEXTURES.length;slot++){JsonArray rows=data.has(Integer.toString(slot))?data.getAsJsonArray(Integer.toString(slot)):new JsonArray();out[slot]=new float[rows.size()][32];for(int i=0;i<rows.size();i++){JsonArray q=rows.get(i).getAsJsonArray();if(q.size()!=32)throw new IllegalArgumentException("Invalid stage quad");for(int j=0;j<32;j++)out[slot][i][j]=q.get(j).getAsFloat();}}
        }catch(IOException|RuntimeException e){for(int slot=0;slot<out.length;slot++)if(out[slot]==null)out[slot]=new float[0][];}return out;   // no mesh: nothing drawn rather than a crash
    });}
    private static float[][][] bake(StageAttachmentBlockEntity be){
        List<List<float[]>> out=List.of(new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
        for(var e:be.entries()){float[][][] source=source(e.key());double[] bounds=StagePose.box(StageSpec.get(e.key()).raw_bounds,e.x(),e.y(),e.z(),e.degrees());for(int slot=0;slot<TEXTURES.length;slot++)for(float[] q:source[slot])out.get(slot).addAll(StagePose.clipped(StagePose.quad(q,e.x(),e.y(),e.z(),e.degrees()),e.clip(),bounds));}
        float[][][] arrays=new float[TEXTURES.length][][];for(int slot=0;slot<TEXTURES.length;slot++)arrays[slot]=out.get(slot).toArray(float[][]::new);return arrays;
    }
    public void render(StageAttachmentBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(be.getBlockState().hasProperty(StageEquipmentBlock.ATTACHED)&&!be.getBlockState().getValue(StageEquipmentBlock.ATTACHED)){CACHE.remove(be);be.clearLocal();return;}
        // Captured moving cells have a synthetic BE: their baked base must travel as well.
        if(be.getBlockState().getBlock()instanceof StageEquipmentBlock&&be.getLevel()!=null&&be.getLevel().getBlockEntity(be.getBlockPos())!=be)Minecraft.getInstance().getBlockRenderer().renderSingleBlock(be.getBlockState(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        if(be.entries().isEmpty())return;Cached cached=CACHE.get(be);
        if(cached==null||cached.revision!=be.revision()||cached.generation!=generation){String sig=signature(be);cached=new Cached(be.revision(),generation,sig,SHARED.computeIfAbsent(sig,k->bake(be)));CACHE.put(be,cached);}
        boolean hardware=RenderQuality.current().hardware
            // bolts, clamps, pins: too small to see from afar (performance, 1.0.2)
            &&be.getBlockPos().distToCenterSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())<HARDWARE_DISTANCE*HARDWARE_DISTANCE;
        if(GpuMesh.enabled()){final Cached c=cached;final boolean hw=hardware;
            GpuMesh m=GPU.get(c.signature+"#"+light+(hw?"h":"")+generation,b->{PoseStack.Pose id=new PoseStack().last();for(int slot=0;slot<TEXTURES.length;slot++){if(c.quads[slot].length==0||slot==2&&!hw)continue;emit(b.consumer(TEXTURES[slot],false,true),id,c.quads[slot],light);}});
            if(m!=null){m.draw(pose);return;}}
        for(int slot=0;slot<TEXTURES.length;slot++){if(cached.quads[slot].length==0||slot==2&&!hardware)continue;emit(buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[slot])),pose.last(),cached.quads[slot],light);}
    }
    private static void emit(VertexConsumer vc,PoseStack.Pose p,float[][] quads,int light){for(float[] q:quads)for(int j=0;j<32;j+=8)vc.vertex(p.pose(),q[j],q[j+1],q[j+2]).color(255,255,255,255).uv(q[j+3],q[j+4]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(),q[j+5],q[j+6],q[j+7]).endVertex();}
    public int getViewDistance(){return RenderQuality.distance(128);}
}
