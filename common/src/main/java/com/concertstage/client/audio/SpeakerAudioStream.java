package com.concertstage.client.audio;

import com.concertstage.audio.core.ConsoleMixer;
import com.concertstage.audio.core.TrackPcm;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

/** Shared stereo feed. Four vanilla OpenAL buffers need enough wall-clock reserve to survive game stalls. */
final class SpeakerAudioStream implements AudioStream {
    private final com.concertstage.audio.core.PlaylistPcm reader;
    private final ClientAudio.Voice voice;
    private final AudioFormat format;
    private final ConsoleMixer mixer;
    private final int rate;
    private short[] stereo = new short[0], mono = new short[0];
    private final ByteBuffer[] buffers = new ByteBuffer[2];
    private int flip;
    /** 1.1.1: level of what is really sent to Minecraft (Diag. tab). */
    private final com.concertstage.audio.core.LevelMeter meter = new com.concertstage.audio.core.LevelMeter(com.concertstage.audio.core.PlaylistPcm.RATE, 32768);

    SpeakerAudioStream(com.concertstage.audio.core.PlaylistPcm reader, ClientAudio.Voice voice) {
        this.reader = reader;
        this.voice = voice;
        int rate = com.concertstage.audio.core.PlaylistPcm.RATE;
        this.rate = rate;
        this.format = new AudioFormat(rate, 16, 2, true, false);
        // 1.1.1 audio fix: the console groups plus the slots of the delay towers (each with its own delay, see ListenerMix)
        this.mixer = new ConsoleMixer(rate, com.concertstage.audio.core.ListenerMix.SLOTS, com.concertstage.audio.core.ListenerMix.sides(),
            com.concertstage.audio.core.ListenerMix.subs());
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) {
        int frames = Math.max(1, Math.min(size / 4, rate * Math.max(40,Math.min(500,AudioSettings.streamBufferMs)) / 1000));
        if (stereo.length < frames * 2) { stereo = new short[frames * 2]; mono = new short[frames * 2]; }
        int n = reader.read(stereo, frames);
        if (n <= 0) {
            if(AudioSettings.diagnosticsLog)com.mojang.logging.LogUtils.getLogger().info("CSC_AUDIO stream EOF frames={} state={} failure={} dead={}",reader.outputFrames(),reader.decoderState(),reader.failure(),voice.dead);
            return null;
        }                                          // end of the track: the channel stops
        mixer.groupDelays(voice.groupDelays);
        mixer.crossover(voice.crossoverHz);
        mixer.smoothing(AudioSettings.smoothingMs);
        mixer.environment(voice.reverbWet, voice.reverbRoom, voice.reverbDamp);
        mixer.eqTuning(AudioSettings.eqHz, AudioSettings.eqQ);
        mixer.concert(AudioSettings.concert, voice.echoMs, voice.echoGain, AudioSettings.concertGlue, AudioSettings.concertBass, voice.wind);
        float peak = mixer.process(stereo, n, voice.weights, voice.eqLow, voice.eqMid, voice.eqHigh, voice.playback.subs, AudioSettings.outputGain, AudioSettings.profile, voice.airCutoff, mono);
        voice.reductionDb = mixer.reductionDb();
        voice.peak = Math.max(voice.peak * 0.5f, peak);
        meter.add(mono, n);
        voice.loudness = (float) meter.shortTerm(); voice.limiterDb = mixer.limiterDb(); voice.groups = mixer.activeGroups();
        ByteBuffer b = buffers[flip];
        if (b == null || b.capacity() < n * 4) b = buffers[flip] = BufferUtils.createByteBuffer(Math.max(n * 4, frames * 4));
        flip ^= 1;
        b.clear();
        for (int i = 0; i < n * 2; i++) b.putShort(mono[i]);
        b.flip();
        return b;
    }

    @Override
    public void close() {
        if(AudioSettings.diagnosticsLog)com.mojang.logging.LogUtils.getLogger().info("CSC_AUDIO stream close frames={} state={} failure={} dead={}",reader.outputFrames(),reader.decoderState(),reader.failure(),voice.dead);
        reader.close();
    }
}
