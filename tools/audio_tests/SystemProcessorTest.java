import com.concertstage.audio.core.*;
public class SystemProcessorTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  var p=new SystemProcessor(48000);float[] dry={1000,-2000,3000,-4000};p.process(dry,2,SoundProfile.DEFAULT);check(Math.abs(dry[0]-1000)<.01&&Math.abs(dry[3]+4000)<.01,"flat bypass");
  var delay=new SystemProcessor(48000);float[] impulse=new float[200];impulse[0]=1234;impulse[1]=1234;
  delay.process(impulse,100,SoundProfile.DEFAULT.with(9,1).with(10,1));
  for(int i=0;i<48;i++)check(Math.abs(impulse[i*2])<.01,"exact delay before impulse");check(Math.abs(impulse[96]+1234)<.01,"48 frame delay and polarity");
  var compressor=new SystemProcessor(48000);float[] loud=new float[48000*2];java.util.Arrays.fill(loud,24000);
  compressor.process(loud,48000,SoundProfile.DEFAULT.with(5,1).with(6,-20).with(7,4));
  check(compressor.reductionDb()>10,"compression meter");check(loud[loud.length-1]<10000,"compression reduces output");
  check(SoundProfile.DEFAULT.with(9,Float.NaN).delayMs()==0,"NaN sanitised");check(SoundProfile.DEFAULT.with(11,5000).crossover()==200,"crossover clamp");
  for(float v:loud)check(Float.isFinite(v),"finite DSP");
  float[] high=new float[9600];double raw=0;
  for(int i=0;i<4800;i++){high[i*2]=high[i*2+1]=(float)(10000*Math.sin(2*Math.PI*10000*i/48000));if(i>1000)raw+=high[i*2]*high[i*2];}
  new SystemProcessor(48000).process(high,4800,SoundProfile.DEFAULT,2000);double filtered=0;for(int i=1001;i<4800;i++)filtered+=high[i*2]*high[i*2];
  check(filtered<raw*.04,"air absorption attenuates high frequencies");
  short[] tone=new short[96000],low=new short[48000],wide=new short[48000];for(int i=0;i<48000;i++)tone[i*2]=tone[i*2+1]=(short)(10000*Math.sin(2*Math.PI*100*i/48000));
  new SpeakerDsp(48000).process(tone,48000,0,0,0,0,1,50,low);new SpeakerDsp(48000).process(tone,48000,0,0,0,0,1,200,wide);
  double lo=0,hi=0;for(int i=24000;i<48000;i++){lo+=(double)low[i]*low[i];hi+=(double)wide[i]*wide[i];}check(lo<hi*.04,"adjustable crossover changes sub response");
  System.out.println("SystemProcessorTest PASS: flat signal, exact delay, polarity, compressor reduction, finite bounds");
 }
}
