package com.concertstage.audio.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One track being played, decoded once on its own background thread and shared by every speaker that plays it (each one reads it
 * with its own {@link Reader}, at its own pace). Only a few seconds around the readers are kept in memory (blocks of one second),
 * the decoder never runs more than {@link #AHEAD} blocks ahead of the furthest reader. Nothing here runs on the game thread.
 */
public final class TrackPcm implements AutoCloseable {
    public static final int AHEAD = 6, BEHIND = 8;
    private final Path file;
    private final String type;
    private final Object lock = new Object();
    private final Map<Long, short[]> blocks = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Reader> readers = new CopyOnWriteArrayList<>();
    private final Thread thread;
    private volatile int rate = 44100;
    private volatile long blockFrames = 44100;
    /** First and one-past-last decoded frame; end of the file once known. */
    private volatile long first = -1, until = -1, end = -1;
    private volatile boolean closed, failed, ready;
    private final long startMs;

    /** @param startMs where the first reader will start (the decoder jumps there first) */
    public TrackPcm(Path file, String type, long startMs) {
        this.file = file;
        this.type = type;
        this.startMs = Math.max(0, startMs);
        thread = new Thread(this::run, "CSC audio decoder");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    public int sampleRate() { return rate; }
    public boolean ready() { return ready; }
    public boolean failed() { return failed; }
    /** Frame where the file ends, -1 while unknown. */
    public long endFrame() { return end; }
    public long frameAt(long ms) { return ms * rate / 1000; }

    /** Waits (on the calling, non-game thread) until the decoder knows the sample rate; false if the file cannot be read. */
    public boolean awaitReady(long timeoutMs) {
        long until = System.currentTimeMillis() + timeoutMs;
        synchronized (lock) {
            while (!ready && !failed && !closed) {
                long left = until - System.currentTimeMillis();
                if (left <= 0) break;
                try { lock.wait(left); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        return ready && !failed;
    }

    public Reader reader(long startFrame) {
        Reader r = new Reader(startFrame);
        readers.add(r);
        synchronized (lock) { lock.notifyAll(); }
        return r;
    }

    public int readerCount() {
        return readers.size();
    }

    private void run() {
        try (TrackDecoder d = AudioFiles.open(file, type)) {
            rate = d.sampleRate();
            blockFrames = rate;
            long startBlock = (startMs * rate / 1000) / blockFrames;
            long pos = d.skip(startBlock * blockFrames);                   // short of it when the file is shorter
            first = pos;
            until = pos;
            synchronized (lock) { ready = true; lock.notifyAll(); }
            short[] tmp = new short[(int) blockFrames * 2];
            short[] part = new short[4096 * 2];                            // aurex.2: one read buffer for the whole track
            while (!closed) {
                long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
                for (Reader r : readers) { if (r.closed) continue; min = Math.min(min, r.pos); max = Math.max(max, r.pos); }
                if (min == Long.MAX_VALUE) { min = until; max = until; }
                // drop what every reader has passed
                long keepFrom = min - BEHIND * blockFrames;
                blocks.keySet().removeIf(k -> (k + 1) * blockFrames <= keepFrom);
                if (first < keepFrom) first = Math.max(first, (keepFrom / blockFrames) * blockFrames);
                // every reader far ahead of what is decoded: jump
                if (min > until + 3 * blockFrames && end < 0) {
                    long target = (min / blockFrames) * blockFrames;
                    long s = d.skip(target - until);
                    until += s;
                    blocks.clear();
                    first = until;
                    if (until < target) { end = until; signal(); }
                    continue;
                }
                if (end >= 0 || until >= max + AHEAD * blockFrames) {
                    synchronized (lock) { if (!closed) lock.wait(60); }
                    continue;
                }
                // one block (the decoder may stop short at the end of the file)
                int got = 0;
                while (got < blockFrames) {
                    int want = (int) Math.min(4096, blockFrames - got) * 2;
                    short[] buf = want == part.length ? part : new short[want];   // only the last, shorter read of a block allocates
                    int n = d.read(buf);
                    if (n < 0) break;
                    System.arraycopy(buf, 0, tmp, got * 2, n * 2);
                    got += n;
                }
                if (got > 0) {
                    short[] b = new short[got * 2];
                    System.arraycopy(tmp, 0, b, 0, got * 2);
                    blocks.put(until / blockFrames, b);
                    until += got;
                }
                if (got < blockFrames) end = until;
                signal();
            }
        } catch (IOException | RuntimeException e) {
            failed = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!ready) failed = true;
            signal();
        }
    }

    private void signal() {
        synchronized (lock) { lock.notifyAll(); }
    }

    /** Copies frames [frame, frame + n) into {@code out} if they are decoded; frames before the start of the buffer are silence. */
    private boolean copy(long frame, short[] out, int offset, int n) {
        long f = first;
        int o = offset;
        long p = frame;
        long stop = frame + n;
        while (p < stop) {
            if (p < 0 || f >= 0 && p < f) {                                      // before what is decoded: silence
                long gap = Math.min(stop, f < 0 ? stop : f) - p;
                java.util.Arrays.fill(out, o * 2, (int) (o + gap) * 2, (short) 0);
                o += gap; p += gap;
                continue;
            }
            short[] b = blocks.get(p / blockFrames);
            if (b == null) return false;
            int in = (int) (p - (p / blockFrames) * blockFrames);
            int c = (int) Math.min(stop - p, b.length / 2 - in);
            if (c <= 0) return false;
            System.arraycopy(b, in * 2, out, o * 2, c * 2);
            o += c; p += c;
        }
        return true;
    }

    @Override
    public void close() {
        closed = true;
        signal();
        blocks.clear();
    }

    /** One speaker's position in the track. */
    public final class Reader implements AutoCloseable {
        private volatile long pos;
        private volatile boolean closed;

        private Reader(long pos) {
            this.pos = pos;
        }

        public long position() { return pos; }

        /**
         * Next {@code frames} stereo frames into {@code out}, waiting (at most {@code timeoutMs}, on the sound thread) for the decoder.
         * @return frames written (silence if the decoder is late), or -1 at the end of the track
         */
        public int read(short[] out, int frames, long timeoutMs) {
            long e = end;
            if (e >= 0 && pos >= e) return -1;
            int n = e >= 0 ? (int) Math.min(frames, e - pos) : frames;
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (lock) { lock.notifyAll(); }
            while (!copy(pos, out, 0, n)) {
                if (failed || TrackPcm.this.closed || closed) { java.util.Arrays.fill(out, 0, n * 2, (short) 0); break; }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) { java.util.Arrays.fill(out, 0, n * 2, (short) 0); break; }      // late: a gap of silence, never a stall
                synchronized (lock) {
                    try { lock.wait(Math.min(left, 20)); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
                }
                e = end;
                if (e >= 0) n = (int) Math.max(0, Math.min(n, e - pos));
            }
            pos += n;
            return n;
        }

        /** Non-blocking look at frames around a position (VU meters): false if they are not in memory. */
        public boolean peek(long frame, short[] out, int frames) {
            return copy(frame, out, 0, frames);
        }

        /** Moves the reader (VU meter following the playhead); never blocks. */
        public void seek(long frame) {
            pos = Math.max(0, frame);
            synchronized (lock) { lock.notifyAll(); }
        }

        @Override
        public void close() {
            closed = true;
            readers.remove(this);
        }
    }
}
