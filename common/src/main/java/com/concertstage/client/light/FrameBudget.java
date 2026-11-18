package com.concertstage.client.light;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rev. 19 — a per-frame budget (full beams, beams in the haze, projections, far ribbons) given by importance, not by drawing order.
 *
 * <p>Before, each budget was first come, first served in the order the game draws the block entities, an order that changes when
 * the camera turns or a chunk is rebuilt: with more lit fixtures than places, a fixture had its volumetric beam one frame and the
 * plain ribbon the next, its projection on the stage came and went — on screen, flashes and flicker that looked like a strobe
 * nobody had asked for (11 November report). Now every fixture asks with a score (how much it shows: intensity x output / distance);
 * the places of a frame are given to the best scores of the frame before, a fixture already in keeping its place until another one
 * is clearly better ({@link #KEEP}: 25 %). A fixture seen for the first time gets a free place if there is one. Pure Java (no game
 * class): tested by {@code tools/rev19/FrameBudgetTest}.
 */
public final class FrameBudget {
    /** A fixture in the budget keeps its place against newcomers up to this ratio of score. */
    public static final float KEEP = 1.25f;

    private final String name;
    private Map<Object, Float> asking = new HashMap<>(), previous = new HashMap<>();
    /** Rev. 23: family of each fixture asking (spots, washes, panels): over capacity, each family keeps its share of the places. */
    private Map<Object, Integer> groups = new HashMap<>();
    private Set<Object> admitted = new HashSet<>(), spare = new HashSet<>();
    private Object[] keys = new Object[64];
    private float[] ranks = new float[64];
    private int capacity, newcomers;

    public FrameBudget(String name) {
        this.name = name;
    }

    /** Start of a frame: the places of this frame from the scores of the last one. */
    public void frame(int capacity) {
        this.capacity = Math.max(0, capacity);
        Map<Object, Float> scores = asking;
        Set<Object> was = admitted, next = spare;
        next.clear();
        if (scores.size() <= this.capacity) next.addAll(scores.keySet());           // room for everybody: no ranking
        else if (this.capacity == 0) { /* rev. 22: no place this frame (quality lowered to "low" while fixtures were asking: the
            cut below read past the end of the array — game crash seen in the rev. 22 test, 11 November) */ }
        else {
            int n = scores.size(), i = 0;
            if (keys.length < n) { keys = new Object[n * 2]; ranks = new float[n * 2]; }
            for (Map.Entry<Object, Float> e : scores.entrySet()) {
                keys[i] = e.getKey();
                ranks[i++] = was.contains(e.getKey()) ? e.getValue() * KEEP : e.getValue();
            }
            // rev. 23: first each family its share of the places (in proportion to how many of it ask), best ranks first; then the
            // places left by score. Before, the places went to the best scores of all: a VORTEX outscores a LUMORA (output x size), and
            // switching 30 VORTEX on with 16 LUMORA lit took the LUMORA their light on the floor and their haze — they seemed to dim
            // (author's video, 11 November, 22 h 18)
            int[] count = new int[4];
            int[] grp = new int[n];
            for (int k = 0; k < n; k++) { Integer gg = groups.get(keys[k]); grp[k] = gg == null ? 0 : Math.max(0, Math.min(3, gg)); count[grp[k]]++; }
            Integer[] order = new Integer[n];
            for (int k = 0; k < n; k++) order[k] = k;
            final float[] rk = ranks;
            java.util.Arrays.sort(order, (x, y) -> Float.compare(rk[y], rk[x]));
            int[] quota = new int[4];
            for (int g = 0; g < 4; g++) quota[g] = count[g] == 0 ? 0 : Math.max(1, (int) Math.floor((double) this.capacity * count[g] / n));
            int[] taken = new int[4];
            boolean[] in = new boolean[n];
            for (int j = 0; j < n && next.size() < this.capacity; j++) {
                int k = order[j];
                if (taken[grp[k]] < quota[grp[k]]) { taken[grp[k]]++; in[k] = true; next.add(keys[k]); }
            }
            for (int j = 0; j < n && next.size() < this.capacity; j++) { int k = order[j]; if (!in[k]) { in[k] = true; next.add(keys[k]); } }
            java.util.Arrays.fill(keys, 0, n, null);
        }
        spare = was;
        admitted = next;
        Map<Object, Float> old = previous;
        previous = scores;
        old.clear();
        asking = old;
        if (groups.size() > 4 * Math.max(64, scores.size())) groups.keySet().retainAll(scores.keySet());
        newcomers = 0;
    }

    /** Whether {@code key} may use the budget this frame; records its score for the next frame. */
    public boolean admit(Object key, float score) {
        return admit(key, score, 0);
    }

    /** Rev. 23: with the family of the fixture (0 spot, 1 wash, 2 panel), see {@link #frame}. */
    public boolean admit(Object key, float score, int group) {
        groups.put(key, group);
        asking.put(key, Float.isFinite(score) ? score : 0f);
        if (admitted.contains(key)) return true;
        // not known last frame (just came into view, just lit): a free place if any, without taking one from a fixture already in
        if (!previous.containsKey(key) && admitted.size() + newcomers < capacity) { newcomers++; return true; }
        return false;
    }

    public int used() {
        return admitted.size() + newcomers;
    }

    public int capacity() {
        return capacity;
    }

    @Override
    public String toString() {
        return name + " " + used() + "/" + capacity;
    }
}
