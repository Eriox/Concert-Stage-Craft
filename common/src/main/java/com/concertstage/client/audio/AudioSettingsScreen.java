package com.concertstage.client.audio;
import com.concertstage.client.ui.Tr;
import com.concertstage.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;

/**
 * Local listening controls (only for this player: the console mix stays shared). 1.1.0: every text translated (FR / EN), one line
 * under the title says what the open tab is for, and every control explains itself when the mouse rests on it.
 */
public class AudioSettingsScreen extends Screen {
 private static final int W=380,H=260,ACCENT=Ui.PURPLE;
 private final Screen parent;private int page;
 private static final String[] TABS={"Quality","System","EQ","EQ Hz","Dyn.","Space","Dist.","Adv.","Diag."};
 private static final String[] TAB_TIPS={"tab_quality","tab_system","tab_eq","tab_eq_hz","tab_dyn","tab_space","tab_dist","tab_adv","tab_diag"};
 private static final String[] HINTS={
  "Pick a profile, then fine-tune obstacles and reverb",
  "Your own volume and how the speakers reach your ears",
  "Tone of the whole system: + boosts, - cuts (0 = flat)",
  "Centre frequency of each EQ band (advanced)",
  "Evens out loud and quiet passages",
  "What the world does to the sound (outdoors, distance...)",
  "How the level falls with the distance to the speakers",
  "Streaming, cache and downloads (change only if needed)",
  "What the sound engine is doing right now"};
 public AudioSettingsScreen(Screen parent){super(Component.literal(Tr.t("Concert Stage - Sound")));this.parent=parent;}
 private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}
 private void slider(int row,String label,String tip,int min,int max,int value,java.util.function.IntFunction<String> text,IntConsumer action){
  Tr.help(addRenderableWidget(new Ui.Slider(left()+14,top()+69+row*27,W-28,23,Component.literal(Tr.t(label)),min,max,value,ACCENT,text,action){
   @Override protected void applyValue(){action.accept(get());}
  }),tip);
 }
 private void processing(int row,int id,String label,String tip,int min,int max,int scale,String unit){slider(row,label,tip,min,max,Math.round(AudioSettings.profile.value(id)*scale),v->String.format(java.util.Locale.ROOT,"%.1f%s",v/(float)scale,unit),v->{AudioSettings.profile=AudioSettings.profile.with(id,v/(float)scale);});}
 private void toggle(int row,int id,String label,String tip){
  Tr.help(addRenderableWidget(new Ui.Button(left()+14,top()+69+row*27,W-28,23,()->Tr.state(label,AudioSettings.profile.value(id)>=.5),ACCENT,Ui.Style.NORMAL,()->{AudioSettings.profile=AudioSettings.profile.with(id,AudioSettings.profile.value(id)>=.5?0:1);})),tip);
 }
 private void flag(int y,String label,String tip,java.util.function.BooleanSupplier get,Runnable flip){
  Tr.help(addRenderableWidget(new Ui.Button(left()+14,top()+y,W-28,22,()->Tr.state(label,get.getAsBoolean()),ACCENT,Ui.Style.NORMAL,flip)),tip);
 }
 private static String qualityName(String id){
  return switch(id){case "performance"->Tr.t("Performance");case "balanced"->Tr.t("Balanced");case "high"->Tr.t("High quality");default->Tr.t("Custom");};
 }
 @Override protected void init(){
  for(int i=0;i<TABS.length;i++){final int tab=i;Tr.help(addRenderableWidget(new Ui.Button(left()+14+i*39,top()+39,37,20,()->Component.literal(Tr.t(TABS[tab])),ACCENT,Ui.Style.NORMAL,()->{page=tab;rebuildWidgets();}).selected(page==i)),TAB_TIPS[i]);}
  if(page==0){
   String[] q={"performance","balanced","high"};int bw=(W-28-8)/3;
   for(int i=0;i<3;i++){final String id=q[i];Tr.help(addRenderableWidget(new Ui.Button(left()+14+i*(bw+4),top()+69,bw,22,()->Component.literal(qualityName(id)),ACCENT,Ui.Style.NORMAL,()->{AudioSettings.applyQuality(id);rebuildWidgets();}).selected(id.equals(AudioSettings.quality))),"quality_"+id);}
   flag(123,"Obstacles (walls muffle the sound)","occlusion",()->AudioSettings.occlusion,()->{AudioSettings.occlusion=!AudioSettings.occlusion;AudioSettings.quality="custom";rebuildWidgets();});
   flag(150,"Room reverb","reverb",()->AudioSettings.reverb,()->{AudioSettings.reverb=!AudioSettings.reverb;AudioSettings.quality="custom";rebuildWidgets();});
   slider(4,"Reverb amount","reverb_amount",0,200,Math.round(AudioSettings.reverbAmount*100),v->v+" %",v->AudioSettings.reverbAmount=v/100f);
  }else if(page==1){
   slider(0,"Listening volume","volume",0,100,Math.round(AudioSettings.volume*100),v->v+" %",v->AudioSettings.volume=v/100f);
   // 1.1.1: x0.25 to x4 (1 = the calibrated level, see AudioSettings.outputGain)
   slider(1,"Output gain","output_gain",25,400,Math.round(AudioSettings.outputGain*100),v->String.format(java.util.Locale.ROOT,"x%.2f (%+.1f dB)",v/100f,20*Math.log10(v/100f)),v->AudioSettings.outputGain=v/100f);
   // 1.1.1: mains | subwoofers | monitors on one row
   int tw=(W-28-8)/3,ty=top()+69+2*27;
   Tr.help(addRenderableWidget(new Ui.Slider(left()+14,ty,tw,23,Component.literal(Tr.t("Mains")),0,200,Math.round(AudioSettings.levelMains*100),ACCENT,v->v+" %",v->AudioSettings.levelMains=v/100f){
    @Override protected void applyValue(){AudioSettings.levelMains=get()/100f;}
   }),"level_mains");
   Tr.help(addRenderableWidget(new Ui.Slider(left()+14+tw+4,ty,tw,23,Component.literal(Tr.t("Subs")),0,200,Math.round(AudioSettings.profile.subLevel()*100),ACCENT,v->v+" %",v->AudioSettings.profile=AudioSettings.profile.with(12,v/100f)){
    @Override protected void applyValue(){AudioSettings.profile=AudioSettings.profile.with(12,get()/100f);}
   }),"sub_level");
   Tr.help(addRenderableWidget(new Ui.Slider(left()+14+2*(tw+4),ty,W-28-2*(tw+4),23,Component.literal(Tr.t("Monitors")),0,200,Math.round(AudioSettings.levelMonitors*100),ACCENT,v->v+" %",v->AudioSettings.levelMonitors=v/100f){
    @Override protected void applyValue(){AudioSettings.levelMonitors=get()/100f;}
   }),"level_monitors");
   // 1.1.1 audio fix: the crossover follows the voicing of the subwoofer model (80-100 Hz) until the slider is moved
   int xw=(W-28-4)*2/3;
   slider(3,"LR24 crossover","crossover",50,200,Math.round(AudioSettings.profile.crossover()),v->AudioSettings.subCrossoverAuto?Tr.t("auto (sub model)"):v+" Hz",v->{AudioSettings.profile=AudioSettings.profile.with(11,v);AudioSettings.subCrossoverAuto=false;});
   Tr.help(addRenderableWidget(new Ui.Button(left()+14+xw+4,top()+69+3*27,W-28-xw-4,23,()->Tr.state("Auto",AudioSettings.subCrossoverAuto),ACCENT,Ui.Style.NORMAL,()->{AudioSettings.subCrossoverAuto=!AudioSettings.subCrossoverAuto;rebuildWidgets();})),"crossover_auto");
   processing(4,9,"Common listening delay","listen_delay",0,500,1," ms");toggle(5,10,"Common polarity inversion","polarity");
  }else if(page==2){
   String[] names={"Bass / 90 Hz","Low mids / 300 Hz","Mids / 1 kHz","High mids / 3 kHz","Treble / 8 kHz"};
   for(int i=0;i<5;i++)processing(i,i,names[i],"eq_band_"+i,-24,24,2," dB");
  }else if(page==3){
   String[] names={"Bass shelf","Low-mid bell","Mid bell","High-mid bell","Treble shelf"};int[][] r={{40,250},{150,800},{500,3000},{1500,8000},{4000,16000}};
   for(int b=0;b<5;b++){final int band=b;slider(b,names[b],"eq_hz",r[b][0],r[b][1],Math.round(AudioSettings.eqHz[b]),v->v>=1000?String.format(java.util.Locale.ROOT,"%.1f kHz",v/1000f):v+" Hz",v->{float[] h=AudioSettings.eqHz.clone();h[band]=v;AudioSettings.eqHz=h;});}
   slider(5,"Bell width (Q)","eq_q",3,40,Math.round(AudioSettings.eqQ*10),v->String.format(java.util.Locale.ROOT,"%.1f",v/10f),v->AudioSettings.eqQ=v/10f);
  }else if(page==4){toggle(0,5,"Stereo-linked compressor","compressor");processing(1,6,"Threshold","threshold",-40,0,1," dBFS");processing(2,7,"Ratio","ratio",10,100,10,":1");toggle(3,8,"Output limiter","limiter");}
  else if(page==5){
   flag(69,"CSC Outdoor Concert Mode","outdoor",()->AudioSettings.outdoor,()->AudioSettings.outdoor=!AudioSettings.outdoor);
   flag(96,"Distance attenuation","distance",()->AudioSettings.distanceAttenuation,()->AudioSettings.distanceAttenuation=!AudioSettings.distanceAttenuation);
   flag(123,"Speaker directionality","directionality",()->AudioSettings.directionality,()->AudioSettings.directionality=!AudioSettings.directionality);
   flag(150,"High-frequency air absorption","air",()->AudioSettings.airAbsorption,()->AudioSettings.airAbsorption=!AudioSettings.airAbsorption);
   // aurex.2: concert realism (echo of the place, glue of the mix, concert bass, wind far away)
   flag(177,"Concert realism (echo, glue, bass, wind)","concert",()->AudioSettings.concert,()->{AudioSettings.concert=!AudioSettings.concert;rebuildWidgets();});
   int hw=(W-28-4)/2;
   Tr.help(addRenderableWidget(new Ui.Slider(left()+14,top()+204,hw,23,Component.literal(Tr.t("Glue")),0,100,Math.round(AudioSettings.concertGlue*100),ACCENT,v->v+" %",v->AudioSettings.concertGlue=v/100f){
    @Override protected void applyValue(){AudioSettings.concertGlue=get()/100f;}
   }),"concert_glue");
   Tr.help(addRenderableWidget(new Ui.Slider(left()+14+hw+4,top()+204,W-28-hw-4,23,Component.literal(Tr.t("Concert bass")),0,100,Math.round(AudioSettings.concertBass*100),ACCENT,v->v+" %",v->AudioSettings.concertBass=v/100f){
    @Override protected void applyValue(){AudioSettings.concertBass=get()/100f;}
   }),"concert_bass");
  }else if(page==6){
   // 1.1.1 audio fix: the curve of each kind of speaker (gentle / realistic / strong) or the old single curve
   String[] curves={"gentle","realistic","strong","legacy"};String[] names={"Gentle","Realistic","Strong","Legacy"};int cw=(W-28-12)/4;
   for(int i=0;i<4;i++){final String id=curves[i];Tr.help(addRenderableWidget(new Ui.Button(left()+14+i*(cw+4),top()+69,cw,22,()->Component.literal(Tr.t(names[java.util.Arrays.asList(curves).indexOf(id)])),ACCENT,Ui.Style.NORMAL,()->{AudioSettings.attenuationCurve=id;rebuildWidgets();}).selected(id.equals(AudioSettings.attenuationCurve))),"curve_"+id);}
   slider(1,"Diffusion distance","max_range",16,512,(int)AudioSettings.distanceCurve.maximum(),v->Math.round(v/1.92f)+" %",v->curve(1,v));
   if("legacy".equals(AudioSettings.attenuationCurve)){
    int hw=(W-28-4)/2;
    Tr.help(addRenderableWidget(new Ui.Slider(left()+14,top()+69+2*27,hw,23,Component.literal(Tr.t("Reference distance")),1,100,(int)AudioSettings.distanceCurve.reference(),ACCENT,v->v+" m",v->curve(0,v)){@Override protected void applyValue(){curve(0,get());}}),"reference");
    Tr.help(addRenderableWidget(new Ui.Slider(left()+14+hw+4,top()+69+2*27,W-28-hw-4,23,Component.literal(Tr.t("Rolloff")),10,300,(int)(AudioSettings.distanceCurve.rolloff()*100),ACCENT,v->String.format(java.util.Locale.ROOT,"%.2f",v/100f),v->curve(2,v/100.0)){@Override protected void applyValue(){curve(2,get()/100.0);}}),"rolloff");
   }else slider(2,"Spatialisation","spatial",0,100,Math.round(AudioSettings.spatialIntensity*100),v->v+" %",v->AudioSettings.spatialIntensity=v/100f);
   slider(3,"Gain smoothing","smoothing",5,500,Math.round(AudioSettings.smoothingMs),v->v+" ms",v->AudioSettings.smoothingMs=v);
   // rev. 4: delay towers as in 1.1.0 (Delay group and console delays, CSC Delay Planner): no detection, trim, automatic delay or
   // propagation settings any more
  }else if(page==7){
   slider(0,"Simultaneous consoles","max_streams",1,6,AudioSettings.maxStreams,String::valueOf,v->AudioSettings.maxStreams=v);
   slider(1,"Cache","cache",1,32,Math.max(1,AudioSettings.cacheMb/256),v->v*256+" MB",v->AudioSettings.cacheMb=v*256);
   flag(123,"Server audio downloads","downloads",()->AudioSettings.downloads,()->AudioSettings.downloads=!AudioSettings.downloads);
   Tr.help(addRenderableWidget(new Ui.Button(left()+14,top()+154,W-28,20,()->Component.literal(Tr.t("Open audio cache")),ACCENT,Ui.Style.GHOST,()->{try{java.nio.file.Files.createDirectories(AudioCache.dir());}catch(java.io.IOException ignored){}net.minecraft.Util.getPlatform().openFile(AudioCache.dir().toFile());})),"open_cache");
   slider(4,"OpenAL PCM block (four queued)","stream_buffer",40,500,AudioSettings.streamBufferMs,v->v+" ms",v->AudioSettings.streamBufferMs=v);
   // 1.1.1 audio fix: anti-echo of the delay towers and their advice in the chat
   int hw=(W-28-4)/2;
   Tr.help(addRenderableWidget(new Ui.Button(left()+14,top()+204,hw,22,()->Tr.state("Anti-echo",AudioSettings.antiEcho),ACCENT,Ui.Style.NORMAL,()->AudioSettings.antiEcho=!AudioSettings.antiEcho)),"anti_echo");
   Tr.help(addRenderableWidget(new Ui.Button(left()+14+hw+4,top()+204,W-28-hw-4,22,()->Tr.state("Tower advice in chat",AudioSettings.towerHints),ACCENT,Ui.Style.NORMAL,()->AudioSettings.towerHints=!AudioSettings.towerHints)),"tower_hints");
  }
  if(page==8)Tr.help(addRenderableWidget(new Ui.Button(left()+14,top()+H-48,200,18,()->Tr.state("Diagnostic logging",AudioSettings.diagnosticsLog),ACCENT,Ui.Style.NORMAL,()->AudioSettings.diagnosticsLog=!AudioSettings.diagnosticsLog)),"diag_log");
  if(parent instanceof ConsoleScreen c)Tr.help(addRenderableWidget(new Ui.Button(left()+14,top()+H-23,120,17,()->Component.literal(Tr.t("Delay Planner")),ACCENT,Ui.Style.ACCENT,()->Minecraft.getInstance().setScreen(new DelayPlannerScreen(this,c.consolePos(),new net.minecraft.nbt.CompoundTag())))),"open_planner");
  Tr.help(addRenderableWidget(new Ui.Button(left()+W-110,top()+H-23,96,17,()->Component.literal(Tr.t("Done")),ACCENT,Ui.Style.ACCENT,this::onClose)),"done_sound");
 }
 private void curve(int i,double value){var c=AudioSettings.distanceCurve;AudioSettings.distanceCurve=new com.concertstage.audio.core.DistanceCurve(i==0?value:c.reference(),i==1?value:c.maximum(),i==2?value:c.rolloff(),i==3?value:c.output());}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  renderBackground(g);
  Ui.panel(g,left(),top(),W,H,ACCENT,title,Component.literal(font.plainSubstrByWidth(Tr.t(HINTS[page]),W-20)));
  super.render(g,mx,my,partial);
  if(page==8){int row=0;for(String line:ClientAudio.diagnostics())g.drawString(font,font.plainSubstrByWidth(line,W-28),left()+14,top()+69+11*row++,Ui.MUTED,false);}
  if(page==0){
   g.drawString(font,font.plainSubstrByWidth(Tr.t("Profile: ")+qualityName(AudioSettings.quality)+Tr.t(" · sets consoles, obstacle checks, reverb and render quality"),W-28),left()+14,top()+97,Ui.MUTED,false);
   g.drawString(font,Tr.f("Here now: reverb wet %.2f, room size %.2f",SoundSpace.wet(),SoundSpace.room()),left()+14,top()+206,Ui.MUTED,false);
   g.drawString(font,font.plainSubstrByWidth(Tr.t("Local listening profile — console mix remains shared"),W-28),left()+14,top()+218,0xFF7E858E,false);
  }
  if(page==4)g.drawString(font,Tr.f("Compressor reduction: %.1f dB",ClientAudio.gainReductionDb()),left()+14,top()+190,Ui.MUTED,false);
 }
 @Override public void onClose(){AudioSettings.save();AudioCache.trim();Minecraft.getInstance().setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
