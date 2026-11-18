package com.concertstage.audio.core;

import com.concertstage.lib.jlayer.Bitstream;
import com.concertstage.lib.jlayer.BitstreamException;
import com.concertstage.lib.jlayer.Decoder;
import com.concertstage.lib.jlayer.DecoderException;
import com.concertstage.lib.jlayer.Header;
import com.concertstage.lib.jlayer.OBuffer;
import com.concertstage.lib.jlayer.SampleBuffer;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** MP3 (MPEG 1 / 2 / 2.5 layer III, also I and II) through the bundled JLayer decoder (LGPL 2.1). */
public final class Mp3Decoder implements TrackDecoder {
    private final InputStream in;
    private final Bitstream bits;
    private final Decoder decoder = new Decoder();
    private final int rate;
    private Header pending;
    /** Decoded frames not handed out yet (interleaved stereo). */
    private short[] left = new short[0];
    private int leftPos, leftLen;
    private boolean eof, decodedAny, afterJump;

    public Mp3Decoder(Path file) throws IOException {
        this.in = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
        this.bits = new Bitstream(in);
        try {
            pending = bits.readFrame();
        } catch (BitstreamException e) {
            close();
            throw new IOException("not an MP3 file", e);
        }
        if (pending == null) {
            close();
            throw new IOException("no MP3 frame");
        }
        rate = pending.frequency();
        gapless(pending);
    }

    @Override
    public int sampleRate() {
        return rate;
    }

    static int samplesPerFrame(Header h) {
        if (h.layer() == 1) return 384;
        if (h.layer() == 2) return 1152;
        return h.version() == Header.MPEG1 ? 1152 : 576;
    }

    private Header next() throws IOException {
        if (pending != null) { Header h = pending; pending = null; return h; }
        try {
            return bits.readFrame();
        } catch (BitstreamException e) {
            return null;                                          // a damaged end of file: stop there
        }
    }

    /** Decodes the next frame into {@link #left}; false at the end. */
    private boolean decodeNext() throws IOException {
        while (true) {
            Header h = next();
            if (h == null) { eof = true; return false; }
            try {
                OBuffer o = decoder.decodeFrame(h, bits);
                bits.closeFrame();
                if (!(o instanceof SampleBuffer sb)) continue;
                int n = sb.getBufferLength(), ch = Math.max(1, sb.getChannelCount());
                short[] src = sb.getBuffer();
                int frames = n / ch;
                if (frames == 0) {
                    if (!afterJump) continue;                     // the "Info" frame at the start of the file: no sound, no time
                    // just after a jump the decoder cannot rebuild the frame (its data starts in frames it did not read): the frame
                    // still lasts its time, as silence (it is dropped by skip() anyway)
                    frames = samplesPerFrame(h);
                    if (left.length < frames * 2) left = new short[frames * 2];
                    java.util.Arrays.fill(left, 0, frames * 2, (short) 0);
                    leftPos = 0;
                    leftLen = frames;
                    return true;
                }
                afterJump = false;
                if (left.length < frames * 2) left = new short[frames * 2];
                if (ch == 1) for (int i = 0; i < frames; i++) { left[2 * i] = src[i]; left[2 * i + 1] = src[i]; }
                else System.arraycopy(src, 0, left, 0, frames * 2);
                leftPos = 0;
                leftLen = frames;
                decodedAny = true;
                return true;
            } catch (DecoderException | RuntimeException e) {
                bits.closeFrame();                                // one bad frame: skipped
            }
        }
    }

    // ------------------------------------------------------------------ gapless (LAME / Xing tag)
    /** Encoder delay + decoder delay to drop at the start, frames of real sound in the file (-1 unknown), as the LAME tag says. */
    private int startTrim;
    private long limit = -1, pos;
    private boolean started;

    private void gapless(Header first) {
        if (first.encoderDelay() < 0) return;
        startTrim = first.samplesToSkipAtStart();
        if (first.vbrFrames() > 0) limit = Math.max(0, (long) first.vbrFrames() * samplesPerFrame(first) - startTrim - first.samplesToSkipAtEnd());
    }

    private void start() throws IOException {
        if (started) return;
        started = true;
        if (startTrim > 0) rawSkip(startTrim);
    }

    /**
     * Next frames of the sound, without the silence the MP3 encoder adds at the start and at the end (read from the LAME tag, so two
     * tracks follow each other without a gap; a file without the tag is read whole, as before).
     */
    @Override
    public int read(short[] out) throws IOException {
        start();
        int want = out.length / 2;
        if (limit >= 0) {
            long left = limit - pos;
            if (left <= 0) return -1;
            want = (int) Math.min(want, left);
        }
        int n = rawRead(out, want);
        if (n > 0) pos += n;
        return n;
    }

    @Override
    public long skip(long frames) throws IOException {
        start();
        if (limit >= 0) frames = Math.max(0, Math.min(frames, limit - pos));
        long n = rawSkip(frames);
        pos += n;
        return n;
    }

    private int rawRead(short[] out, int want) throws IOException {
        int got = 0;
        while (got < want) {
            if (leftPos >= leftLen && !decodeNext()) break;
            int n = Math.min(want - got, leftLen - leftPos);
            System.arraycopy(left, leftPos * 2, out, got * 2, n * 2);
            leftPos += n;
            got += n;
        }
        return got == 0 && eof ? -1 : got;
    }

    private long rawSkip(long frames) throws IOException {
        long done = 0;
        int buffered = leftLen - leftPos;
        if (buffered > 0) {
            int n = (int) Math.min(buffered, frames);
            leftPos += n;
            done += n;
        }
        // the start of the file is always decoded: its first frame may be a Xing / LAME "Info" frame that holds no sound (counting
        // it as 1152 samples would put every later position one frame off)
        if (!decodedAny) {
            if (!decodeNext()) return done;
            int n = (int) Math.min(frames - done, leftLen - leftPos);
            leftPos += n;
            done += n;
        }
        // whole frames without decoding them (fast), keeping about 10 frames to decode: after a jump the first decoded frames are
        // wrong (a low bit rate VBR frame takes its data from up to 511 bytes back, measured: 3 wrong frames at 20000 samples)
        boolean skipped = false;
        while (frames - done > 10 * 1152) {
            skipped = true;
            Header h = next();
            if (h == null) { eof = true; return done; }
            done += samplesPerFrame(h);
            bits.closeFrame();
        }
        if (skipped) { decoder.seekNotify(); afterJump = true; }                       // nothing of the last decoded frame carried into the next one
        // the rest decoded and dropped, to the exact frame
        while (done < frames) {
            if (leftPos >= leftLen && !decodeNext()) return done;
            int n = (int) Math.min(frames - done, leftLen - leftPos);
            leftPos += n;
            done += n;
        }
        return done;
    }

    @Override
    public void close() throws IOException {
        try {
            bits.close();
        } catch (BitstreamException ignored) {
            // closing anyway
        }
        in.close();
    }

    /** Length of an MP3 file in milliseconds, read from the frame headers (nothing is decoded); -1 if it is not an MP3 file. */
    public static long durationMs(Path file) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            Bitstream b = new Bitstream(in);
            long samples = 0, trim = 0;
            int rate = 0, frames = 0;
            Header h;
            while ((h = b.readFrame()) != null) {
                if (rate == 0) {
                    rate = h.frequency();
                    if (h.encoderDelay() >= 0) trim = h.samplesToSkipAtStart() + (h.vbrFrames() > 0 ? h.samplesToSkipAtEnd() : 0);
                }
                samples += samplesPerFrame(h);
                frames++;
                b.closeFrame();
            }
            return frames < 2 || rate <= 0 ? -1 : Math.max(0, samples - trim) * 1000 / rate;     // the sound itself, as read()
        } catch (IOException | BitstreamException | RuntimeException e) {
            return -1;
        }
    }
}
