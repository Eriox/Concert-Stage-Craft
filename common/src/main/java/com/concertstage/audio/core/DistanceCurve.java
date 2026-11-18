package com.concertstage.audio.core;
/** Continuous, monotone gameplay attenuation. Not a measured sound pressure model. */
public record DistanceCurve(double reference,double maximum,double rolloff,double output) {
 public DistanceCurve {reference=clamp(reference,1,100,12);maximum=clamp(maximum,16,512,192);rolloff=clamp(rolloff,.1,3,.65);output=clamp(output,0,4,1);}
 private static double clamp(double v,double lo,double hi,double fallback){return Double.isFinite(v)?Math.max(lo,Math.min(hi,v)):fallback;}
 public double gain(double distance){
  if(!Double.isFinite(distance))return 0;
  double d=Math.max(0,distance);
  return output*Math.pow(1+d/reference,-rolloff)*(1-Acoustics.smooth((d-maximum*.75)/(maximum*.25)));
 }
}
