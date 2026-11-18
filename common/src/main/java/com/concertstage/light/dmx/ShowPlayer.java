package com.concertstage.light.dmx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSC SHOW PLAYER: plays a {@link CscShowFile} back into a {@link DmxData}, exactly as recorded (same values at the same times).
 * Play, pause, stop, restart, seek, loop and speed; the position can follow an external clock (the linked music, a timecode) instead of
 * its own: {@link #follow}. Seeking starts from the last key frame of each universe, so it costs at most
 * {@link CscShowFile#KEY_INTERVAL_MS} of frames.
 */
public final class ShowPlayer {
    public final CscShowFile show;
    private final long[] times;
    private final Map<Integer, List<Integer>> keys = new HashMap<>();
    private final Map<Integer, byte[]> state = new HashMap<>();
    /** Next frame to apply, and the show time it corresponds to. */
    private int cursor;
    private long positionMs;
    public boolean playing, loop;
    public float speed = 1f;
    private long lastClockMs = Long.MIN_VALUE;

    public ShowPlayer(CscShowFile show) {
        this.show = show;
        times = new long[show.frames.size()];
        for (int i = 0; i < times.length; i++) {
            CscShowFile.Frame f = show.frames.get(i);
            times[i] = f.timeMs;
            if (f.key) keys.computeIfAbsent(f.universe, k -> new ArrayList<>()).add(i);
        }
        for (int u : show.universes) state.put(u, new byte[512]);
    }

    public long duration() {
        return show.durationMs;
    }

    public long position() {
        return positionMs;
    }

    public void play(long clockMs) {
        playing = true;
        lastClockMs = clockMs;
    }

    public void pause() {
        playing = false;
    }

    /** Stop: back to the start, not playing. */
    public void stop(DmxData out) {
        playing = false;
        seek(0, out);
    }

    public void restart(long clockMs, DmxData out) {
        seek(0, out);
        play(clockMs);
    }

    /** Own clock: advances by (clock delta × speed) while playing, wraps when looping. Returns false at the end (not looping). */
    public boolean tick(long clockMs, DmxData out) {
        if (!playing) { lastClockMs = clockMs; return true; }
        long dt = lastClockMs == Long.MIN_VALUE ? 0 : clockMs - lastClockMs;
        lastClockMs = clockMs;
        long target = positionMs + Math.round(Math.max(0, dt) * (double) speed);
        if (target > show.durationMs) {
            if (loop && show.durationMs > 0) { seek(target % Math.max(1, show.durationMs), out); return true; }
            advance(show.durationMs, out);
            playing = false;
            return false;
        }
        advance(target, out);
        return true;
    }

    /**
     * External clock: the show position is {@code showMs} (for example the music position minus the music position at the start of
     * the recording). Small differences are followed frame by frame, a jump (seek of the music, drift over 250 ms) seeks.
     */
    public void follow(long showMs, DmxData out) {
        if (showMs < positionMs - 250 || showMs > positionMs + 1000) seek(showMs, out);
        else if (showMs > positionMs) advance(showMs, out);
    }

    /** Jumps to a time: state rebuilt from the last key frame of each universe before it. */
    public void seek(long ms, DmxData out) {
        ms = Math.max(0, Math.min(show.durationMs, ms));
        int end = upper(ms);                                           // first frame after ms
        for (int u : show.universes) {
            byte[] s = state.get(u);
            java.util.Arrays.fill(s, (byte) 0);
            List<Integer> k = keys.get(u);
            int from = 0;
            if (k != null) for (int idx : k) { if (idx < end) from = idx; else break; }
            for (int i = from; i < end; i++) {
                CscShowFile.Frame f = show.frames.get(i);
                if (f.universe != u) continue;
                if (f.key) System.arraycopy(f.data, 0, s, 0, 512); else CscShowFile.apply(f.data, s);
            }
            out.set(u, s);
        }
        cursor = end;
        positionMs = ms;
    }

    /** Applies the frames up to {@code ms} (forward only). */
    private void advance(long ms, DmxData out) {
        boolean[] touched = new boolean[show.universes.length];
        while (cursor < times.length && times[cursor] <= ms) {
            CscShowFile.Frame f = show.frames.get(cursor++);
            byte[] s = state.get(f.universe);
            if (s == null) continue;
            if (f.key) System.arraycopy(f.data, 0, s, 0, 512); else CscShowFile.apply(f.data, s);
            for (int i = 0; i < show.universes.length; i++) if (show.universes[i] == f.universe) touched[i] = true;
        }
        for (int i = 0; i < touched.length; i++) if (touched[i]) out.set(show.universes[i], state.get(show.universes[i]));
        positionMs = ms;
    }

    private int upper(long ms) {
        int lo = 0, hi = times.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= ms) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    /** Current values of a universe (for tests and the monitor). */
    public byte[] universe(int u) {
        return state.get(u);
    }
}
