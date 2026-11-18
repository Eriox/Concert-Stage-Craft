package com.concertstage.client.audio;

import com.concertstage.client.ui.Tr;import com.concertstage.audio.AudioConsoleBlock;
import com.concertstage.audio.AudioConsoleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Original CSC digital desk. Faders, mutes, VU and LCD content use real console state. */
public final class AudioConsoleRenderer implements BlockEntityRenderer<AudioConsoleBlockEntity> {
    private static final class Animation { float[] values=new float[14];long time=System.nanoTime();boolean ready; }
    private final WeakHashMap<AudioConsoleBlockEntity,Animation> animations=new WeakHashMap<>();
    public AudioConsoleRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(AudioConsoleBlockEntity c,float partial,PoseStack p,MultiBufferSource buffers,int light,int overlay) {
        if(!com.concertstage.client.ClientCulling.visible(c,c.getRenderBoundingBox()))return;   // off screen (Fabric; Forge checks it itself)
        var mc=Minecraft.getInstance();double distance=mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(c.getBlockPos().getCenter());
        var state=animations.computeIfAbsent(c,k->new Animation());long now=System.nanoTime();float alpha=(float)(1-Math.exp(-Math.min(.1,(now-state.time)/1e9)/.08));state.time=now;
        var pb=ClientAudio.playback(c.getBlockPos());
        for(int i=0;i<14;i++) {
            float target=i==6?c.master():i<6?c.fader(i):i==13?(pb==null?0:Math.max(pb.vuL,pb.vuR)):(pb==null?0:pb.vuGroup[i-7]);
            state.values[i]=state.ready?state.values[i]+(target-state.values[i])*alpha:target;
        }
        state.ready=true;
        var vc=buffers.getBuffer(RenderType.entityCutoutNoCull(SpeakerMeshes.ATLAS));
        float yaw=-c.getBlockState().getValue(AudioConsoleBlock.FACING).toYRot();
        boolean far=distance>24*24,near=distance<40*40;
        // aurex.2: the desk (thousands of triangles) and, when they are at rest, its fader caps and mute keys are kept in the graphics
        // card (see GpuMesh): 5 ms per frame for one console before, a draw call now. VU meters and screens stay live.
        boolean body=false,caps=false;
        if(com.concertstage.client.GpuMesh.enabled()){
            com.concertstage.client.GpuMesh m=GPU.get("desk/"+(far?"f":"n")+"/"+yaw+"/"+light,b->{PoseStack l=new PoseStack();l.translate(.5,0,.5);l.mulPose(Axis.YP.rotationDegrees(yaw));
                var mesh=SpeakerMeshes.get("csc_audio_console_mesh");SpeakerRenderer.emit(l,b.consumer(SpeakerMeshes.ATLAS,false,true),far?mesh.far():mesh.near(),light);});
            if(m!=null){m.draw(p);body=true;}
            boolean resting=true;for(int i=0;i<7;i++)if(Math.abs(state.values[i]-(i==6?c.master():c.fader(i)))>.002f)resting=false;
            if(near&&resting){
                StringBuilder key=new StringBuilder("caps/").append(yaw).append('/').append(light);
                for(int i=0;i<7;i++)key.append('/').append(Math.round(state.values[i]*500));
                for(int g=0;g<6;g++)key.append(c.muted(g)?'m':'-');
                final float[] v=state.values.clone();
                com.concertstage.client.GpuMesh cm=GPU.get(key.toString(),b->{PoseStack l=new PoseStack();l.translate(.5,0,.5);l.mulPose(Axis.YP.rotationDegrees(yaw));caps(l,b.consumer(SpeakerMeshes.ATLAS,false,true),v,c,light);});
                if(cm!=null){cm.draw(p);caps=true;}
            }
        }
        p.pushPose();p.translate(.5,0,.5);p.mulPose(Axis.YP.rotationDegrees(yaw));
        if(!body){var mesh=SpeakerMeshes.get("csc_audio_console_mesh");SpeakerRenderer.emit(p,vc,far?mesh.far():mesh.near(),light);}
        if(near) {
            if(!caps)caps(p,vc,state.values,c,light);
            for(int group=0;group<7;group++) {
                double x=group<6?-.82+group*4*.065:.84;
                for(int seg=0;seg<12;seg++) {
                    boolean on=state.values[7+group]>(float)Math.pow(10,(-42+seg*3.8)/20);
                    double z=.42-seg*.019;
                    AudioRenderGeometry.box(p,vc,x,.98-.18*z+.009,z,.010,.004,.012,on?(seg>9?8:seg>7?10:9):6,on?LightTexture.FULL_BRIGHT:light);
                }
            }
            if(distance<12*12){
            p.pushPose();p.translate(0,1.09,-.385);p.mulPose(Axis.XP.rotationDegrees(-12));p.translate(0,-1.09,.385);
            String track=c.currentTrack()==null?Tr.t("NO TRACK"):c.currentTrack().name();
            lcd(p,buffers,Tr.t("CSC  |  PLAYBACK"),-.695,1.320,0xFF70CBFF);
            lcd(p,buffers,track,-.695,1.283,0xFFE4F1F5);
            String mode=c.state()==AudioConsoleBlockEntity.PLAYING?Tr.t("PLAY"):c.state()==AudioConsoleBlockEntity.PAUSED?Tr.t("PAUSE"):Tr.t("STOP");
            lcd(p,buffers,mode+Tr.t("  /  REPEAT ")+new String[]{Tr.t("OFF"),Tr.t("ALL"),Tr.t("ONE")}[c.repeatMode()],-.695,1.246,0xFFAFD3DD);
            long seconds=c.positionMs(c.getLevel()==null?0:c.getLevel().getGameTime())/1000;
            long length=c.currentTrack()==null?0:c.currentTrack().durationMs()/1000;
            lcd(p,buffers,String.format(java.util.Locale.ROOT,"%02d:%02d / %02d:%02d",seconds/60,seconds%60,length/60,length%60),-.695,1.209,0xFFE4F1F5);
            lcd(p,buffers,Tr.t("CSC  |  SYSTEM"),.145,1.320,0xFF70CBFF);
            lcd(p,buffers,Tr.t("MASTER ")+Math.round(c.master()*100)+"%  / "+c.speakers().size()+Tr.t(" LINKS"),.145,1.283,0xFFE4F1F5);
            int bus=4;for(int g=3;g<6;g++)if(c.groupDelay(g)>0){bus=g;break;}
            String label=c.groupLabel(bus).isEmpty()?Tr.t("BUS ")+(bus+1):c.groupLabel(bus);
            lcd(p,buffers,label,.145,1.246,0xFFAFD3DD);
            lcd(p,buffers,String.format(java.util.Locale.ROOT,"DELAY %.2f ms",c.groupDelay(bus)),.145,1.209,0xFFE4F1F5);
            for(int channel=0;channel<2;channel++)for(int seg=0;seg<18;seg++){
                float peak=pb==null?0:channel==0?pb.vuL:pb.vuR;boolean on=peak>Math.pow(10,(-48+seg*3)/20.0);
                AudioRenderGeometry.box(p,vc,-.695+seg*.031,1.14+channel*.021,-.306,.024,.013,.001,on?(seg>15?8:seg>12?10:9):6,LightTexture.FULL_BRIGHT);
            }
            for(int g=0;g<6;g++){double x=.145+g*.088;float peak=pb==null?0:pb.vuGroup[g];for(int n=0;n<4;n++)AudioRenderGeometry.box(p,vc,x,1.139+n*.011,-.306,.063,.007,.001,peak>Math.pow(10,(-36+n*10)/20.0)?9:6,LightTexture.FULL_BRIGHT);}
            p.popPose();
            }
        }
        p.popPose();
    }
    private static final com.concertstage.client.GpuMesh.Cache GPU=new com.concertstage.client.GpuMesh.Cache(256);
    /** The 25 fader caps (24 channels + master) and the 24 mute keys. */
    private static void caps(PoseStack p,com.mojang.blaze3d.vertex.VertexConsumer vc,float[] values,AudioConsoleBlockEntity c,int light){
        for(int i=0;i<25;i++) {
            int group=i<24?i/4:6;float value=values[group];double x=i<24?-.84+i*.065:.8,z=.4-.23*value,y=.98-.18*z+.012;
            p.pushPose();p.translate(x,y,z);p.mulPose(Axis.XP.rotationDegrees((float)Math.toDegrees(Math.atan(.18))));
            SpeakerRenderer.emit(p,vc,SpeakerMeshes.get("csc_fader_cap").near(),light);p.popPose();
            if(i<24)AudioRenderGeometry.box(p,vc,x-.013,.98-.18*.075+.012,.063,.026,.006,.024,c.muted(group)?8:5,LightTexture.FULL_BRIGHT);
        }
    }
    private static void lcd(PoseStack p,MultiBufferSource b,String text,double x,double y,int color) {
        var font=Minecraft.getInstance().font;
        p.pushPose();p.translate(x,y,-.306);p.scale(.0024f,-.0024f,.0024f);
        font.drawInBatch(font.plainSubstrByWidth(text,240),0,0,color,false,p.last().pose(),b,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        p.popPose();
    }
    @Override public boolean shouldRenderOffScreen(AudioConsoleBlockEntity c) { return true; }
    @Override public int getViewDistance() { return Math.min(96, com.concertstage.client.RenderQuality.distance()); }
}
