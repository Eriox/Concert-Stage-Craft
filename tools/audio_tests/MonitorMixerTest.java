import com.concertstage.audio.core.*;
/** Verifies monitor bus 9 really enters PCM, not merely a saved UI field. */
public class MonitorMixerTest {
 public static void main(String[] args) {
  short[] in=new short[4096],out=new short[4096];java.util.Arrays.fill(in,(short)1000);
  var mixer=new ConsoleMixer(48000,9);float[] gains=new float[18];gains[16]=gains[17]=.5f;
  mixer.groupDelays(new float[9]);float peak=0;
  for(int i=0;i<40;i++)peak=mixer.process(in,2048,gains,0,0,0,false,out);
  if(peak<=.001)throw new AssertionError("Monitor 3 produces no PCM");
  java.util.Arrays.fill(gains,0);for(int i=0;i<160;i++)mixer.process(in,2048,gains,0,0,0,false,out);
  int max=0;for(short v:out)max=Math.max(max,Math.abs(v));if(max>2)throw new AssertionError("muted monitor remains audible: "+max);
  boolean bad=false;try{new ConsoleMixer(48000,0);}catch(IllegalArgumentException e){bad=true;}if(!bad)throw new AssertionError("invalid groups accepted");
  System.out.println("MONITOR MIXER PASS: bus 9 PCM, mute decay, invalid group count");
 }
}
