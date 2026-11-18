package com.concertstage.audio.core;

import java.io.Closeable;
import java.io.IOException;

/** Sequential decoder of one audio file to interleaved 16-bit stereo PCM at the file's own sample rate (pure Java, no Minecraft). */
public interface TrackDecoder extends Closeable {
    int sampleRate();

    /**
     * Fills {@code out} with up to {@code out.length / 2} stereo frames (L, R, L, R...).
     * @return the number of frames written, or -1 at the end of the file
     */
    int read(short[] out) throws IOException;

    /** Skips forward {@code frames} stereo frames (as fast as the format allows); returns how many were skipped. */
    long skip(long frames) throws IOException;
}
