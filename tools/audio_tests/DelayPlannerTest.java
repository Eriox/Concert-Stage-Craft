import com.concertstage.audio.core.*;
import java.util.*;
public final class DelayPlannerTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static DelayCoverage.Source source(double x,double y,double z){return new DelayCoverage.Source(x,y,z,0,12,12,110,12,.2,new DistanceCurve(12,90,.7,1),.64,true,true);}
 static DelayCoverage.Grid grid(int dx,int dz,boolean unknown){double[] floor=new double[21*21];if(unknown)Arrays.fill(floor,Double.NaN);float[][] pass=new float[1][floor.length];Arrays.fill(pass[0],1);return new DelayCoverage.Grid(-50+dx,20+dz,5,21,21,floor,pass,1);}
 public static void main(String[] args){
  var mains=List.of(source(0,0,0));double d=DelayCoverage.alignment(mains,0,0,100,0,0,150);check(Math.abs(d-291.5451895043732)<1e-6,"100 m path difference");
  double side=DelayCoverage.alignment(mains,30,0,100,0,0,150);check(Math.abs(side-d)>20,"alignment uses listening reference, not source separation");
  check(DelayCoverage.alignment(mains,0,0,-20,0,0,150)<0,"negative delay is preserved for UI rejection");
  short[] pulse=new short[1500];pulse[0]=20000;new GroupDelayLine(48000).process(pulse,pulse.length,10);for(int i=0;i<pulse.length;i++)check(pulse[i]==(i==480?20000:0),"exact delay tap "+i);
  var line=new GroupDelayLine(48000);short[] first=new short[200];first[0]=20000;line.process(first,200,10);short[] second=new short[700];line.process(second,700,10);check(second[280]==20000,"delay persists across callbacks");
  var mix=new ConsoleMixer(48000);mix.groupDelays(new float[]{0,0,0,0,10,0});short[] input=new short[2000],out=new short[2000];input[0]=input[1]=30000;float[] weights=new float[12];weights[8]=1;mix.process(input,1000,weights,0,0,0,false,out);for(int i=0;i<480;i++)check(out[i*2]==0,"group delay really affects mixer");check(out[960]>1000,"delayed group receives shared PCM");for(int i=0;i<1000;i++)check(out[i*2+1]==0,"group routing remains independent");
  var c=new DistanceCurve(12,140,.65,1);var template=new DelayCoverage.Source(0,0,0,0,0,0,110,12,.2,c,.64,false,true);var g=grid(0,0,false);var r=DelayCoverage.analyze(g,List.of(source(0,8,0)),.2,c,"csc_noryx_a1",template,.32);check(!r.towers().isEmpty(),"weak areas produce recommendations");
  var mutedTemplate=new DelayCoverage.Source(0,0,0,0,0,0,110,12,.2,c,0,false,true);var distinct=DelayCoverage.analyze(g,List.of(source(0,8,0)),.2,c,"csc_noryx_a1",List.of(template,mutedTemplate,template),.32);for(var t:distinct.towers())check(t.template().level()==(t.group()==4?0:.64),"each proposed bus preserves its own gain/mute");
  var moved=DelayCoverage.analyze(grid(10,30,false),List.of(source(10,8,30)),.2,c,"csc_noryx_a1",template,.32);check(r.towers().size()==moved.towers().size(),"translation count");for(int i=0;i<r.towers().size();i++){var a=r.towers().get(i);var b=moved.towers().get(i);check(Math.abs(a.x()+10-b.x())<1e-5&&Math.abs(a.z()+30-b.z())<1e-5,"proposals depend on real geometry");check(Double.isFinite(a.delayMs()),"finite recommendation");}
  var disabled=r.towers().stream().map(t->new DelayCoverage.Tower(t.x(),t.ground(),t.z(),t.height(),t.yaw(),t.modules(),t.model(),t.group(),t.refX(),t.refY(),t.refZ(),t.delayMs(),false,t.template(),t.cabinetHeight())).toList();var off=DelayCoverage.update(r,disabled,c);for(int i=0;i<r.baseline().length;i++)check(Math.abs(r.baseline()[i]-off.predicted()[i])<1e-6,"ignored towers do not contribute");
  var none=DelayCoverage.analyze(grid(0,0,true),List.of(source(0,8,0)),.2,c,"csc_noryx_a1",template,.32);check(none.towers().isEmpty(),"unknown terrain cannot recommend towers");check(Float.isNaN(none.baseline()[0]),"unknown remains unknown");
  System.out.println("DelayPlannerTest PASS: geometric alignment, actual group DSP delay, streaming continuity, responsive proposals and unknown data");
 }
}
