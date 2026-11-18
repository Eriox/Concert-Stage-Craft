import com.concertstage.audio.core.ConsoleMixer;
public final class LimiterTest {
    private static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args) {
        int rate=48000;short[] src=new short[rate*2],out=new short[rate*2];float[] weights=new float[12];weights[0]=weights[1]=.7f;
        for(int i=0;i<rate;i++)src[2*i]=src[2*i+1]=(short)(1000*Math.sin(2*Math.PI*440*i/rate));
        new ConsoleMixer(rate).process(src,rate,weights,0,0,0,false,2.8f,out);
        double rms=0;for(int i=rate;i<rate*2;i++)rms+=out[i]*(double)out[i];rms=Math.sqrt(rms/rate);
        check(rms>1300,"quiet tracks are measurably louder after output gain");
        for(int i=0;i<rate;i++)src[2*i]=src[2*i+1]=(short)(30000*Math.sin(2*Math.PI*440*i/rate));
        new ConsoleMixer(rate).process(src,rate,weights,12,6,6,true,8f,out);
        int max=0;for(short v:out)max=Math.max(max,Math.abs((int)v));
        check(max<=32112,"boosted peaks remain below PCM full scale");
        check(max>10000,"limiter must not silence loud content");
        System.out.println("LimiterTest PASS: quiet RMS="+rms+", boosted peak="+max+", bounded stereo output");
    }
}
