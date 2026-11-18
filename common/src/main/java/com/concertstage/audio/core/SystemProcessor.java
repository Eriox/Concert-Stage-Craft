package com.concertstage.audio.core;
/** Stereo-linked compressor, five-band EQ, polarity and common output delay. Owned by the sound thread. */
public final class SystemProcessor {
 private final int rate;
 private final SpeakerDsp.Biquad[] air={new SpeakerDsp.Biquad(),new SpeakerDsp.Biquad()};
 private float airHz=Float.NaN;
 private final SpeakerDsp.Biquad[][] eq=new SpeakerDsp.Biquad[2][5];
 private final float[] delay;
 private SoundProfile previous;
 private int cursor;
 private double envelope;
 private volatile float reductionDb;
 public SystemProcessor(int rate){this.rate=rate;delay=new float[(rate/2+1)*2];for(int c=0;c<2;c++)for(int b=0;b<5;b++)eq[c][b]=new SpeakerDsp.Biquad();}
 public float reductionDb(){return reductionDb;}
 /** 1.1.1 audio fix: the program the compressor listens to (stereo 16-bit), null = its own input. */
 private short[] key;
 public void key(short[] program){key=program;}
 /** Centre / corner frequencies of the five EQ bands and the width (Q) of the three middle ones (preview.9; defaults = before). */
 public static final float[] DEFAULT_HZ={90,300,1000,3000,8000};
 private volatile float[] tuningHz=DEFAULT_HZ.clone();private volatile float tuningQ=.8f;
 private float[] appliedHz;private float appliedQ=Float.NaN;
 public void tuning(float[] hz,float q){
  if(hz==null||hz.length!=5)return;float[] c=new float[5];
  for(int b=0;b<5;b++)c[b]=Float.isFinite(hz[b])?Math.max(20,Math.min(rate*.45f,hz[b])):DEFAULT_HZ[b];
  tuningHz=c;tuningQ=Float.isFinite(q)?Math.max(.3f,Math.min(4f,q)):.8f;
 }
 public void process(float[] data,int frames,SoundProfile p){
  process(data,frames,p,20000);
 }
 public void process(float[] data,int frames,SoundProfile p,float cutoff){
  cutoff=Float.isFinite(cutoff)?Math.max(1000,Math.min(20000,cutoff)):20000;
  if(cutoff!=airHz){airHz=cutoff;for(var filter:air)filter.lowPass(rate,cutoff,.707);}
  float[] hzNow=tuningHz;float qNow=tuningQ;
  if(!p.equals(previous)||!java.util.Arrays.equals(hzNow,appliedHz)||qNow!=appliedQ){
   double[] gains={p.bass(),p.lowMid(),p.mid(),p.highMid(),p.treble()};
   for(int c=0;c<2;c++)for(int b=0;b<5;b++){
    if(b==0)eq[c][b].lowShelf(rate,hzNow[b],gains[b]);else if(b==4)eq[c][b].highShelf(rate,hzNow[b],gains[b]);else eq[c][b].peaking(rate,hzNow[b],qNow,gains[b]);
   }
   previous=p;appliedHz=hzNow;appliedQ=qNow;
  }
  double attack=1-Math.exp(-1.0/(rate*.01)),release=1-Math.exp(-1.0/(rate*.15));
  int delayFrames=Math.round(p.delayMs()*rate/1000);float reduction=0;
  boolean compress=p.compressor();double threshold=Math.pow(10,p.threshold()/20),slope=1-1/p.ratio();
  for(int i=0;i<frames;i++){
   double l=data[i*2],r=data[i*2+1];for(int b=0;b<5;b++){l=eq[0][b].run(l);r=eq[1][b].run(r);}
   if(cutoff<20000){l=air[0].run(l);r=air[1].run(r);}
   // 1.1.1 audio fix: the compressor listens to the program (key), not to the level at the listener's place (it undid the distance)
   double peak=(key!=null&&2*i+1<key.length?Math.max(Math.abs(key[2*i]),Math.abs(key[2*i+1])):Math.max(Math.abs(l),Math.abs(r)))/32768;
   envelope+=(peak-envelope)*(peak>envelope?attack:release);
   double gain=p.invert()?-1:1;
   // 1.1.1: the logarithm and the power are only worked out when the compressor is on and above its threshold (before: two per
   // sample at all times, the compressor being off by default)
   if(compress&&envelope>threshold){
    double db=20*Math.log10(envelope);
    double gr=(db-p.threshold())*slope;
    reduction=Math.max(reduction,(float)gr);gain*=Math.pow(10,-gr/20);
   }
   delay[cursor*2]=(float)(l*gain);delay[cursor*2+1]=(float)(r*gain);
   int read=(cursor-delayFrames+delay.length/2)%(delay.length/2);
   data[i*2]=delay[read*2];data[i*2+1]=delay[read*2+1];cursor=(cursor+1)%(delay.length/2);
  }
  reductionDb=reduction;
 }
}
