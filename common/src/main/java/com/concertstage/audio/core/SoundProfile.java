package com.concertstage.audio.core;
/** Immutable snapshot shared between UI and sound thread. Values are sanitised at construction. */
public record SoundProfile(float bass, float lowMid, float mid, float highMid, float treble,
                           boolean compressor, float threshold, float ratio, boolean limiter,
                           float delayMs, boolean invert, float crossover, float subLevel) {
 public static final SoundProfile DEFAULT=new SoundProfile(0,0,0,0,0,false,-12,3,true,0,false,100,1);
 public SoundProfile {
  bass=bound(bass,-12,12,0);lowMid=bound(lowMid,-12,12,0);mid=bound(mid,-12,12,0);
  highMid=bound(highMid,-12,12,0);treble=bound(treble,-12,12,0);
  threshold=bound(threshold,-40,0,-12);ratio=bound(ratio,1,10,3);delayMs=bound(delayMs,0,500,0);
  crossover=bound(crossover,50,200,100);subLevel=bound(subLevel,0,2,1);
 }
 public float value(int i){return switch(i){case 0->bass;case 1->lowMid;case 2->mid;case 3->highMid;case 4->treble;case 5->compressor?1:0;case 6->threshold;case 7->ratio;case 8->limiter?1:0;case 9->delayMs;case 10->invert?1:0;case 11->crossover;case 12->subLevel;default->throw new IllegalArgumentException("profile index");};}
 public SoundProfile with(int index,float value){if(!Float.isFinite(value))return this;float[] v=new float[13];for(int i=0;i<v.length;i++)v[i]=i==index?value:value(i);return new SoundProfile(v[0],v[1],v[2],v[3],v[4],v[5]>=.5,v[6],v[7],v[8]>=.5,v[9],v[10]>=.5,v[11],v[12]);}
 private static float bound(float v,float min,float max,float fallback){return Float.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
}
