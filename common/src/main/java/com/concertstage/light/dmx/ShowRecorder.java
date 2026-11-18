package com.concertstage.light.dmx;

import com.concertstage.light.core.Show;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CSC RECORD EXTERNAL SHOW: captures the merged DMX of chosen universes with their arrival time (network thread), only what changes,
 * with a key frame of each universe every {@link CscShowFile#KEY_INTERVAL_MS}. Stops by itself at {@link #MAX_MS} or
 * {@link #MAX_FRAMES} (memory bound).
 */
public final class ShowRecorder {
    public static final long MAX_MS = 4L * 3600 * 1000;
    public static final int MAX_FRAMES = 3_000_000;

    private final CscShowFile file = new CscShowFile();
    private final Set<Integer> universes;
    private final Map<Integer, byte[]> last = new HashMap<>();
    private final Map<Integer, Long> lastKey = new HashMap<>();
    private final long startMs;
    private boolean stopped;
    private long bytes;

    /** {@code startMs}: monotonic clock at the start; {@code universes}: CSC universes to record. */
    public ShowRecorder(long startMs, Set<Integer> universes, Show show, String name, String source) {
        this.startMs = startMs;
        this.universes = Set.copyOf(universes);
        file.name = name;
        file.source = source;
        file.created = System.currentTimeMillis();
        file.universes = universes.stream().mapToInt(Integer::intValue).sorted().toArray();
        for (Show.Patch p : show.patch) {
            Show.Patch c = new Show.Patch();
            c.fid = p.fid; c.type = p.type; c.name = p.name; c.universe = p.universe; c.address = p.address; c.dmxMode = p.dmxMode;
            c.x = p.x; c.y = p.y; c.z = p.z; c.yaw = p.yaw; c.mount = p.mount;
            file.patch.add(c);
        }
        file.matrixUniverse = show.external.matrixUniverse;
        file.matrixAddress = show.external.matrixAddress;
        file.matrixOrder = show.external.matrixOrder;
    }

    /** Music linked to the desk when the recording started (to replay in sync with it). */
    public synchronized void music(String sha1, String trackName, long positionMs) {
        file.trackSha1 = sha1 == null ? "" : sha1;
        file.trackName = trackName == null ? "" : trackName;
        file.musicStartMs = positionMs;
    }

    /** A new state of a universe ({@code nowMs}: same monotonic clock as the start). */
    public synchronized void capture(int universe, byte[] data, long nowMs) {
        if (stopped || !universes.contains(universe)) return;
        long t = nowMs - startMs;
        if (t < 0) t = 0;
        if (t > MAX_MS || file.frames.size() >= MAX_FRAMES) { stopped = true; return; }
        byte[] prev = last.get(universe);
        Long lk = lastKey.get(universe);
        CscShowFile.Frame f = new CscShowFile.Frame();
        f.timeMs = t;
        f.universe = universe;
        if (prev == null || lk == null || t - lk >= CscShowFile.KEY_INTERVAL_MS) {
            f.key = true;
            f.data = data.clone();
            lastKey.put(universe, t);
        } else {
            byte[] runs = CscShowFile.runs(prev, data);
            if (runs.length == 0) return;
            f.data = runs;
        }
        last.put(universe, data.clone());
        file.frames.add(f);
        file.durationMs = Math.max(file.durationMs, t);
        bytes += f.data.length + 4;
    }

    /** Ends the recording; the file can be saved. */
    public synchronized CscShowFile finish(long nowMs) {
        stopped = true;
        file.durationMs = Math.max(file.durationMs, Math.max(0, nowMs - startMs));
        return file;
    }

    public synchronized boolean stopped() {
        return stopped;
    }

    public synchronized int frames() {
        return file.frames.size();
    }

    /** Raw size of the captured data so far (before compression). */
    public synchronized long bytes() {
        return bytes;
    }

    public synchronized long duration(long nowMs) {
        return Math.max(0, nowMs - startMs);
    }
}
