import com.concertstage.audio.core.ConsoleMixer;
import java.util.Arrays;
public final class MixerTest {
    static void check(boolean v, String s) { if (!v) throw new AssertionError(s); }
    public static void main(String[] args) {
        int rate=48000; short[] input=new short[rate*2], out=new short[rate*2];
        Arrays.fill(input,(short)12000); float[] target=new float[12]; target[0]=1;
        ConsoleMixer mixer=new ConsoleMixer(rate);
        mixer.process(input,rate,target,0,0,0,false,out);
        check(out[0]>0 && out[0]<20,"start near silence");
        check(out[out.length-2]>11990,"target convergence");
        for(int i=1;i<rate;i++) {
            check(Math.abs(out[i*2]-out[(i-1)*2])<20,"continuous attack");
            check(out[i*2+1]==0,"channel routing");
        }
        Arrays.fill(target,0); mixer.process(input,rate,target,0,0,0,false,out);
        check(out[0]>11990 && out[out.length-2]<2,"continuous release");
        target[0]=Float.NaN;target[1]=Float.POSITIVE_INFINITY;
        mixer.process(input,rate,target,Float.NaN,0,0,false,out);
        check(Math.abs(out[out.length-2])<2,"non-finite controls silent");
        ConsoleMixer whole=new ConsoleMixer(rate),split=new ConsoleMixer(rate);
        target[0]=.5f;target[1]=.5f;
        short[] expected=new short[2000],actual=new short[2000],chunk=new short[200];
        whole.process(input,1000,target,0,0,0,false,expected);
        for(int i=0;i<10;i++) {
            split.process(input,100,target,0,0,0,false,chunk);
            System.arraycopy(chunk,0,actual,i*200,200);
        }
        check(Arrays.equals(expected,actual),"callback independence");
        System.out.println("MixerTest PASS: routing, attack, release, finite controls, callback independence");
    }
}
