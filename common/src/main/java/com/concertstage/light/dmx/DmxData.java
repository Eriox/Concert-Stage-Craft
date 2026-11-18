package com.concertstage.light.dmx;

import java.util.Map;
import java.util.TreeMap;

/**
 * The DMX a desk is driven by (External or Playback), universe by universe, on the server and on every client: the server fills it
 * from the receiver or the show player and sends the changes to the clients, which fill theirs; the engine reads it. Each universe is
 * 512 slots (unsigned bytes). Not thread safe: game threads only.
 */
public final class DmxData {
    private final Map<Integer, byte[]> universes = new TreeMap<>();
    /** Changes with every update (the engine and the sender use it). */
    public long version;
    /** Monotonic time (ms) of the last update, and whether the source has gone quiet (signal lost). */
    public long updatedMs;
    public boolean lost = true;

    /** The 512 slots of a universe, or null if nothing was received for it. Do not keep or modify. */
    public byte[] get(int universe) {
        return universes.get(universe);
    }

    public void set(int universe, byte[] data512) {
        byte[] d = universes.computeIfAbsent(universe, k -> new byte[512]);
        System.arraycopy(data512, 0, d, 0, 512);
        version++;
    }

    /** Applies a run of slots (delta from the network or a recording). */
    public void patch(int universe, int start, byte[] values, int off, int len) {
        if (start < 0 || len <= 0 || start + len > 512) return;
        byte[] d = universes.computeIfAbsent(universe, k -> new byte[512]);
        System.arraycopy(values, off, d, start, len);
        version++;
    }

    public java.util.Set<Integer> universes() {
        return universes.keySet();
    }

    public void clear() {
        universes.clear();
        version++;
        lost = true;
    }

    /** Value of a slot (1-based address), 0 when absent. */
    public int value(int universe, int address) {
        byte[] d = universes.get(universe);
        return d == null || address < 1 || address > 512 ? 0 : d[address - 1] & 255;
    }
}
