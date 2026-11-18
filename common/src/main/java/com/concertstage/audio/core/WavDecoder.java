package com.concertstage.audio.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** WAV (PCM 8 / 16 / 24 / 32 bit, float, mono or stereo) through javax.sound.sampled, converted to 16-bit stereo. */
public final class WavDecoder implements TrackDecoder {
    private final AudioInputStream in;
    private final int channels, rate;
    private byte[] buf = new byte[0];

    public WavDecoder(Path file) throws IOException {
        AudioInputStream src;
        try {
            src = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16));
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("unsupported WAV file", e);
        }
        AudioFormat f = src.getFormat();
        int ch = f.getChannels();
        if (ch < 1 || ch > 8) { src.close(); throw new IOException("unsupported channel count " + ch); }
        rate = Math.round(f.getSampleRate());
        if (rate < 8000 || rate > 192000) { src.close(); throw new IOException("unsupported sample rate " + rate); }
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, ch, ch * 2, rate, false);
        AudioInputStream s = src;
        if (!sameFormat(f, target)) {
            try {
                s = AudioSystem.getAudioInputStream(target, src);
            } catch (IllegalArgumentException e) {
                src.close();
                throw new IOException("cannot convert " + f, e);
            }
        }
        this.in = s;
        this.channels = ch;
    }

    private static boolean sameFormat(AudioFormat a, AudioFormat b) {
        return a.getEncoding().equals(b.getEncoding()) && a.getSampleSizeInBits() == 16 && !a.isBigEndian();
    }

    @Override
    public int sampleRate() {
        return rate;
    }

    @Override
    public int read(short[] out) throws IOException {
        int frames = out.length / 2, bytes = frames * channels * 2;
        if (buf.length < bytes) buf = new byte[bytes];
        int n = 0;
        while (n < bytes) {
            int r = in.read(buf, n, bytes - n);
            if (r < 0) break;
            n += r;
        }
        int got = n / (channels * 2);
        if (got == 0) return n == 0 ? -1 : 0;
        for (int i = 0; i < got; i++) {
            int o = i * channels * 2;
            short l = (short) ((buf[o] & 0xFF) | (buf[o + 1] << 8));
            short r = channels == 1 ? l : (short) ((buf[o + 2] & 0xFF) | (buf[o + 3] << 8));
            out[2 * i] = l;
            out[2 * i + 1] = r;
        }
        return got;
    }

    @Override
    public long skip(long frames) throws IOException {
        long bytes = frames * channels * 2, done = 0;
        while (done < bytes) {
            long s = in.skip(bytes - done);
            if (s <= 0) {
                if (in.read() < 0) break;
                s = 1;
            }
            done += s;
        }
        return done / (channels * 2);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /** Length of a WAV file in milliseconds, from its header; -1 if it cannot be read. */
    public static long durationMs(Path file) {
        try (AudioInputStream s = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
            long frames = s.getFrameLength();
            float rate = s.getFormat().getFrameRate();
            if (frames <= 0 || rate <= 0) return -1;
            return (long) (frames * 1000 / rate);
        } catch (IOException | UnsupportedAudioFileException | RuntimeException e) {
            return -1;
        }
    }
}
