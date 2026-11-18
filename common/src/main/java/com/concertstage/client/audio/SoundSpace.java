package com.concertstage.client.audio;

import com.concertstage.block.ring.RingTrussCellBlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the world does to the sound on its way to the listener (1.1.0), on the game thread, cheaply:
 * <ul>
 * <li><b>obstacles</b>: the blocks on the straight line from an array to the listener. A full opaque block (stone, wood, a wall)
 * counts as one wall, a block with holes (glass, leaves, fences, slabs) as a fifth of a wall; the trusses, collision cells and
 * speakers of the mod do not count (sound goes through a lattice). Each wall takes about 9 dB off and dulls the highs (low pass
 * down to 1 kHz). Recomputed every few ticks per array (quality profile), or at once when the listener or the array moves;</li>
 * <li><b>the room</b> around the listener for the reverb: 26 rays (up to 32 blocks) say how closed the place is and how big;
 * open air gives almost none, a hall or a cave a long tail. Once a second.</li>
 * </ul>
 * Game settings tuned by ear, not acoustic measurements.
 */
final class SoundSpace {
    /** Gain per wall (about -9 dB) and cut-off factor per wall. */
    private static final double WALL_GAIN = 0.355, WALL_CUTOFF = 0.3;
    private static final int MAX_SAMPLES = 160;

    private record Cached(long tick, BlockPos ear, Vec3 from, double walls) {}

    private static final Map<BlockPos, Cached> CACHE = new HashMap<>();
    private static long probeTick = Long.MIN_VALUE;
    private static BlockPos probeEar;
    private static float wet, room = .5f, damp = .5f;
    /** aurex.2: echo of the place for the concert realism (ms, level) and whether the listener is under the open sky. */
    private static float echoMs, echoGain;
    private static boolean outdoors = true;

    private SoundSpace() {}

    static void reset() {
        CACHE.clear();
        probeTick = Long.MIN_VALUE;
        probeEar = null;
        wet = 0;
    }

    /** Forget arrays that are no longer heard (called every second). */
    static void trim(long tick) {
        CACHE.values().removeIf(c -> tick - c.tick > 200);
    }

    /** Walls (0..5, fractional) between an array and the listener. */
    static double walls(Level level, BlockPos array, Vec3 from, Vec3 ear, long tick) {
        if (!AudioSettings.occlusion) return 0;
        BlockPos earBlock = BlockPos.containing(ear);
        Cached c = CACHE.get(array);
        if (c != null && tick - c.tick < AudioSettings.occlusionInterval && c.ear.equals(earBlock) && c.from.distanceToSqr(from) < 0.25) return c.walls;
        double w = trace(level, from, ear, array);
        CACHE.put(array, new Cached(tick, earBlock, from, w));
        return w;
    }

    static double gain(double walls) {
        return walls <= 0 ? 1 : Math.pow(WALL_GAIN, walls);
    }

    static float cutoff(double walls) {
        return walls <= 0 ? 20000 : (float) Math.max(1000, 20000 * Math.pow(WALL_CUTOFF, walls));
    }

    private static double trace(Level level, Vec3 from, Vec3 to, BlockPos self) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1) return 0;
        int n = (int) Math.min(MAX_SAMPLES, Math.ceil(len / 0.5));
        double walls = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), last = new BlockPos.MutableBlockPos(Integer.MIN_VALUE, 0, 0);
        BlockPos earBlock = BlockPos.containing(to);
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            p.set(Math.floor(from.x + d.x * t), Math.floor(from.y + d.y * t), Math.floor(from.z + d.z * t));
            if (p.equals(last) || p.equals(earBlock) || p.equals(self)) continue;
            last.set(p);
            if (!level.isLoaded(p)) continue;
            walls += weight(level, p);
            if (walls >= 5) return 5;
        }
        return walls;
    }

    /** How much one block stops the sound: 1 a full opaque block, 0.2 a block with holes, 0 air and the mod's rigging. */
    private static double weight(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return 0;
        if (s.getBlock() instanceof RingTrussCellBlock || s.getBlock() instanceof com.concertstage.audio.SpeakerBlock) return 0;
        if (s.getCollisionShape(level, p).isEmpty()) return 0;
        if (s.isSolidRender(level, p)) return 1;
        boolean mod = "concertstage".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace());
        return mod ? 0 : 0.2;                                            // trusses, decks, barriers of the mod: open structures
    }

    // ------------------------------------------------------------------ room (reverb)
    static float wet() { return wet; }
    static float room() { return room; }
    static float damp() { return damp; }
    static float echoMs() { return echoMs; }
    static float echoGain() { return echoGain; }
    static boolean outdoors() { return outdoors; }

    /** Looks at the place around the listener once a second (or when the listener moved 4 blocks). */
    static void probe(Level level, Vec3 ear, long tick) {
        if (!AudioSettings.reverb && !AudioSettings.concert) { wet = 0; echoGain = 0; return; }
        BlockPos e = BlockPos.containing(ear);
        if (tick - probeTick < 20 && probeEar != null && probeEar.distSqr(e) < 16) return;
        probeTick = tick;
        probeEar = e;
        int hits = 0, rays = 0;
        double dist = 0;
        double[] far = new double[8];                                   // horizontal rays: distant buildings, slopes (echo)
        int farHits = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    rays++;
                    double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    int reach = dy == 0 ? 96 : 32;                      // aurex.2: horizontally, far enough to hear the buildings answer
                    for (int k = 1; k <= reach; k++) {
                        p.set(Math.floor(ear.x + dx / l * k), Math.floor(ear.y + dy / l * k), Math.floor(ear.z + dz / l * k));
                        if (!level.isLoaded(p)) break;
                        BlockState s = level.getBlockState(p);
                        if (!s.isAir() && !(s.getBlock() instanceof RingTrussCellBlock) && s.isSolidRender(level, p)) {
                            if (k <= 32) { hits++; dist += k; }
                            if (dy == 0 && k >= 12) far[farHits++] = k;
                            break;
                        }
                    }
                }
        double enclosure = hits / (double) rays;
        boolean sky = level.canSeeSky(e.above());
        outdoors = sky;
        if (sky) {
            // open air: the slap-back of what stands around the audience (median of the walls met at 12..96 m), -10 to -20 dB
            if (farHits > 0) {
                java.util.Arrays.sort(far, 0, farHits);
                double d = far[farHits / 2];
                echoMs = (float) (2 * d / 343.0 * 1000);
                echoGain = (float) (Math.min(.3, .08 + .03 * farHits) * Math.sqrt(24 / (24 + d)));
            } else {
                echoGain = 0;
            }
        } else {
            // inside: the first reflections of the walls, just before the reverb tail
            double mean0 = hits == 0 ? 16 : dist / hits;
            echoMs = (float) Math.min(120, 2 * mean0 / 343.0 * 1000);
            echoGain = (float) (.14 * enclosure);
        }
        if (!AudioSettings.reverb) { wet = 0; return; }
        double target = Math.max(0, enclosure - 0.3) / 0.7 * 0.30 * (sky ? 0.5 : 1);     // open air: nearly dry; hall / cave: long tail
        target *= AudioSettings.reverbAmount;
        double mean = hits == 0 ? 32 : dist / hits;
        wet = (float) Math.min(0.6, target);
        room = (float) Math.max(0.2, Math.min(0.95, mean / 24));
        damp = (float) (0.3 + 0.4 * (1 - room));                         // small rooms: dull tail, big halls: brighter
    }
}
