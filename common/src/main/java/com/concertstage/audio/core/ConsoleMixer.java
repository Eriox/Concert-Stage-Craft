package com.concertstage.audio.core;
import java.util.Arrays;
/** Sound-thread-owned coherent stereo mixer. All groups consume identical PCM frames. */
public final class ConsoleMixer {
    public static final int GROUPS = 6;
    private final int groups;
    private final GroupDelayLine[] delays;
    private volatile float[] groupDelayMs;
    public void groupDelays(float[] values){if(values.length==groups)groupDelayMs=values.clone();}
    private final SpeakerDsp[] filters;
    private final double[] gains;
    private double attack, release;
    private final int rate;
    private float smoothing=-1;
    public void smoothing(float ms){if(!Float.isFinite(ms))ms=60;ms=Math.max(5,Math.min(500,ms));if(ms==smoothing)return;smoothing=ms;attack=1-Math.exp(-1000.0/(rate*ms));release=1-Math.exp(-1000.0/(rate*ms*1.5));}
    private final OutputLimiter limiter;
    private final SystemProcessor processor;
    /** Room reverb of the listener's surroundings (1.1.0), set by the client every block; off = not computed. */
    private final Reverb reverb;
    private volatile float reverbWet, reverbRoom = .5f, reverbDamp = .5f;
    public void environment(float wet, float room, float damp) { reverbWet = wet; reverbRoom = room; reverbDamp = damp; }
    /** Frequencies of the five listening EQ bands and the Q of the middle ones (preview.9). */
    public void eqTuning(float[] hz, float q) { processor.tuning(hz, q); }
    public float reductionDb(){return processor.reductionDb();}
    /** 1.1.1: gain reduction of the output limiter in the last callback (dB). */
    public float limiterDb(){return limiter.reductionDb();}
    /** Concert realism (aurex.2): echo of the place, glue, concert bass, wind. See ConcertAcoustics. */
    private final ConcertAcoustics acoustics;
    public void concert(boolean on,float echoMs,float echoGain,float glue,float bass,float wind){acoustics.enabled(on);acoustics.echo(echoMs,echoGain);acoustics.style(glue,bass);acoustics.wind(wind);}
    private float[] mono = new float[0];
    /** 1.1.1: groups not heard (both gains at zero) are not filtered at all; they start again from silence. */
    private final boolean[] idle;
    private int activeGroups;
    /** Groups filtered in the last callback (diagnostics, tests). */
    public int activeGroups() { return activeGroups; }
    private float[] sum = new float[0];
    /**
     * 1.1.1 audio fix: per slot, the side of the program it plays (-1 left, +1 right, 0 mono sum) and whether it is a subwoofer slot
     * (low pass, its own level limits and protection). The default layout is the console groups: 0 left, 1 right, 2 subs, others mono.
     */
    private final int[] side;
    private final boolean[] sub;
    /** Crossover of the subs and the mains (Hz); NaN = the one of the listening profile. */
    private volatile float crossoverHz = Float.NaN;
    public void crossover(float hz) { crossoverHz = Float.isFinite(hz) ? Math.max(40, Math.min(200, hz)) : Float.NaN; }
    /** 1.1.1 audio fix: the subwoofer slots are summed apart and limited at {@link #SUB_CEILING} before joining the mains. */
    private float[] subSum = new float[0];
    private double subGain = 1;
    private int subHold;
    private double subLeast = 1;
    /** -6 dBFS: the low band never takes the whole headroom (it would push the output limiter on the mains). */
    public static final double SUB_CEILING = 16384;
    /** Gain reduction of the subwoofer protection in the last callback (dB). */
    public float subLimiterDb() { return (float) (-20 * Math.log10(Math.max(1e-6, subLeast))); }
    public ConsoleMixer(int rate) { this(rate, GROUPS); }
    public ConsoleMixer(int rate, int groups) { this(rate, groups, null, null); }
    public ConsoleMixer(int rate, int groups, int[] sides, boolean[] subs) {
        if(groups < 1 || groups > 32) throw new IllegalArgumentException("groups");
        side = new int[groups]; sub = new boolean[groups];
        for (int g = 0; g < groups; g++) {
            side[g] = sides != null && g < sides.length ? Integer.signum(sides[g]) : g == 0 ? -1 : g == 1 ? 1 : 0;
            sub[g] = subs != null && g < subs.length ? subs[g] : g == 2;
        }
        this.groups=groups; delays=new GroupDelayLine[groups]; groupDelayMs=new float[groups]; filters=new SpeakerDsp[groups]; gains=new double[groups*2];idle=new boolean[groups];java.util.Arrays.fill(idle,true);
        if (rate <= 0) throw new IllegalArgumentException("sample rate");
        this.rate=rate;
        limiter = new OutputLimiter(rate);
        processor = new SystemProcessor(rate);
        reverb = new Reverb(rate);
        acoustics = new ConcertAcoustics(rate);
        attack = 1 - Math.exp(-1.0 / (rate * .035));
        release = 1 - Math.exp(-1.0 / (rate * .090));
        for (int g = 0; g < groups; g++){filters[g] = new SpeakerDsp(rate);delays[g]=new GroupDelayLine(rate);}
    }
    public float process(short[] input, int frames, float[] targets,
                         float low, float mid, float high, boolean subs, short[] output) {
        return process(input, frames, targets, low, mid, high, subs, 1, output);
    }
    public float process(short[] input, int frames, float[] targets,
                         float low, float mid, float high, boolean subs, float boost, short[] output) {
        return process(input,frames,targets,low,mid,high,subs,boost,boost==1?SoundProfile.DEFAULT.with(8,0):SoundProfile.DEFAULT,output);
    }
    public float process(short[] input,int frames,float[] targets,float low,float mid,float high,boolean subs,float boost,SoundProfile profile,short[] output) {
        return process(input,frames,targets,low,mid,high,subs,boost,profile,20000,output);
    }
    public float process(short[] input,int frames,float[] targets,float low,float mid,float high,boolean subs,float boost,SoundProfile profile,float airHz,short[] output) {
        if (frames < 0 || input.length < frames * 2 || output.length < frames * 2 || targets.length != groups * 2)
            throw new IllegalArgumentException("buffer size");
        if (mono.length < frames) mono = new float[frames];
        if (sum.length < frames * 2) sum = new float[frames * 2];
        Arrays.fill(sum, 0, frames * 2, 0);
        if (subSum.length < frames * 2) subSum = new float[frames * 2];
        boolean anySub = false;
        float xo = Float.isFinite(crossoverHz) ? crossoverHz : profile.crossover();
        float[] delaySnapshot=groupDelayMs;
        int active = 0;
        for (int g = 0; g < groups; g++) {
            // 1.1.1 audio fix: up to +6 dB (mains) / +12 dB (subs with their voicing) instead of 1: next to the PA several systems add
            // up above 1 and the level must still rise as the listener gets closer (the clamp at 1 flattened it); the subwoofer
            // protection and the output limiter keep the peaks
            double cap = sub[g] ? 4 : 2;
            double tl = Math.max(0, Math.min(cap, finite(targets[g * 2]))), tr = Math.max(0, Math.min(cap, finite(targets[g * 2 + 1])));
            // 1.1.1: a group nobody hears (target and current gain at zero on both sides) costs nothing; when it comes back its filters
            // and its delay start from silence (its gain rises from zero over the attack time anyway)
            if (tl == 0 && tr == 0 && gains[g * 2] < 1e-6 && gains[g * 2 + 1] < 1e-6) {
                if (!idle[g]) { idle[g] = true; gains[g * 2] = gains[g * 2 + 1] = 0; }
                continue;
            }
            if (idle[g]) { idle[g] = false; filters[g].reset(); delays[g].clear(); }
            active++;
            filters[g].process(input, frames, side[g],
                finite(low), finite(mid), finite(high), sub[g] ? 1 : subs ? 2 : 0, xo, mono);
            delays[g].process(mono,frames,delaySnapshot[g]);
            for (int ch = 0; ch < 2; ch++) {
                int k = g * 2 + ch;
                double target = (ch == 0 ? tl : tr) * (sub[g]?profile.subLevel():1), gain = gains[k];
                float[] into = sub[g] ? subSum : sum;
                if (sub[g] && !anySub) { Arrays.fill(subSum, 0, frames * 2, 0); anySub = true; }
                for (int i = 0; i < frames; i++) {
                    gain += (target - gain) * (target > gain ? attack : release);
                    into[i * 2 + ch] += mono[i] * gain;
                }
                gains[k] = gain;
            }
        }
        activeGroups = active;
        // 1.1.1 audio fix: subwoofer protection (stereo-linked peak limiter on the low band only: instant attack, the gain held 40 ms
        // — longer than one period of the lowest notes, so it does not ripple inside a wave — then 200 ms release)
        subLeast = 1;
        if (anySub) {
            double rel = 1 - Math.exp(-1.0 / (rate * .200));
            int hold = rate / 25;
            for (int i = 0; i < frames; i++) {
                double a = Math.max(Math.abs(subSum[2 * i]), Math.abs(subSum[2 * i + 1]));
                double target = a > SUB_CEILING ? SUB_CEILING / a : 1;
                if (target <= subGain) { subGain = target; subHold = hold; }
                else if (subHold > 0) subHold--;
                else subGain += (target - subGain) * rel;
                if (subGain < subLeast) subLeast = subGain;
                sum[2 * i] += (float) (subSum[2 * i] * subGain);
                sum[2 * i + 1] += (float) (subSum[2 * i + 1] * subGain);
            }
        } else subGain = 1;
        processor.key(input);
        acoustics.key(input);
        processor.process(sum,frames,profile,airHz);
        acoustics.process(sum,frames);
        reverb.process(sum,frames,reverbWet,reverbRoom,reverbDamp);
        if (!profile.limiter()) {
            float peak = 0;
            for (int i = 0; i < frames * 2; i++) {
                output[i] = (short)Math.round(Math.max(-32768, Math.min(32767, sum[i]*boost)));
                peak = Math.max(peak, Math.abs(output[i] / 32768f));
            }
            return peak;
        }
        return limiter.process(sum, frames, boost, output);
    }
    private static float finite(float v) { return Float.isFinite(v) ? v : 0; }
}
