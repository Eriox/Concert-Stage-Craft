package com.concertstage.audio.core;

import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerModels;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.1.1 audio fix — what the arrays of one console form in the world (pure maths, no Minecraft; tested off game): the main system,
 * the delay towers ("tours de rappel"), the other installations. Computed once when the arrays change (added, removed, moved, turned,
 * regrouped), never per tick.
 *
 * <p>A delay tower is recognised only when every clue agrees:
 * <ol>
 * <li>same musical system: linked to the same console (two consoles are never mixed together);</li>
 * <li>a secondary position: an installation (arrays within {@link #CLUSTER} blocks of each other) clearly in front of the main system,
 *     at least {@link #MIN_FORWARD} blocks along the direction the mains fire, and within 60° of that direction;</li>
 * <li>aimed at a far listening zone: its own aim within about 53° of the aim of the mains (it fires away from the stage);</li>
 * <li>an installation a tower can be: at least one full-range array (line array or main), no monitor, no front fill.</li>
 * </ol>
 * The groups already set on the console are clues too: arrays put in the Delay group need less distance ({@link #MIN_FORWARD_DELAY})
 * and a looser aim. A far installation that misses a clue (aimed sideways, aimed back at the stage, monitors, fills) is not a tower:
 * it keeps its group exactly as before. Without left / right mains there is no reference, so nothing is classified (no guess).
 *
 * <p>The main system: the left / right / subwoofer arrays nearest the stage (the most upstream along the aim of the mains) and those
 * standing in the same stage plane (within {@link #STAGE_DEPTH} blocks along the aim: the other hang, the sub stacks).
 */
public final class SoundSystems {
    public static final double CLUSTER = 12, CLUSTER_Y = 16, STAGE_DEPTH = 25, MIN_FORWARD = 20, MIN_FORWARD_DELAY = 12;
    /** cos of the largest angle between the aim of a tower and the aim of the mains (53°), and with the Delay group (72°). */
    public static final double AIM_COS = .6, AIM_COS_DELAY = .3;
    /** Towers mixed with their own delay (beyond, they share the last one). */
    public static final int MAX_TOWERS = 5;
    /** A tower is aligned on the mains this far in front of it (blocks): the middle of the audience it covers, at ear height. */
    public static final double ALIGN_AHEAD = 15;
    /** Speed of sound (m/s = blocks/s). */
    public static final double SPEED = 343;

    public enum Mode { AUTO, DELAY_GROUP, OFF }
    public enum Role { MAIN, TOWER, OTHER }

    /** One array, as the classification and the listener mix see it. {@code yaw}: degrees, the game's convention. */
    public record Source(long id, int group, SpeakerModels.Model model, int cabinets, double lengthM, double x, double y, double z,
                         double yaw, double pitchTop, double pitchBottom) {
        public double fx() { return -Math.sin(Math.toRadians(yaw)); }
        public double fz() { return Math.cos(Math.toRadians(yaw)); }
        boolean fullRange() { return model != null && !model.isSub() && !model.isMonitor(); }
    }

    /** One installation: its role, its centre, its delay (ms, towers only) and why it was classified so. */
    public record Installation(int index, Role role, double x, double y, double z, double delayMs, int tower, String reason, int arrays) {}

    /** The result: the installation of every source (same order), the installations, the aim of the mains (unit, horizontal). */
    /**
     * The result: the installation of every source (same order), the installations, the aim of the mains (unit, horizontal), the
     * acoustic centre of the mains (the left / right full-range arrays: the point every delay is measured from; NaN without mains) and
     * the estimated ear height of the audience (the lowest array + 0.5, at most 6 blocks under the mains).
     */
    public record Layout(int[] installationOf, List<Installation> installations, double aimX, double aimZ, double mainX, double mainY,
                         double mainZ, double earY) {
        public static final Layout EMPTY = new Layout(new int[0], List.of(), 0, 1, Double.NaN, Double.NaN, Double.NaN, 0);
        public boolean hasMain() { return !Double.isNaN(mainX); }
        public Installation of(int source) {
            return source >= 0 && source < installationOf.length && installationOf[source] >= 0 ? installations.get(installationOf[source]) : null;
        }
        public int towers() {
            int n = 0;
            for (Installation i : installations) if (i.role() == Role.TOWER) n++;
            return n;
        }
    }

    private SoundSystems() {}

    /**
     * Classifies the arrays of one console.
     * @param trimMs added to the automatic delay of a tower (Haas offset: the main stays the image a little ahead; 0..30 ms)
     */
    public static Layout classify(List<Source> sources, Mode mode, double trimMs) {
        int n = sources.size();
        if (n == 0) return Layout.EMPTY;
        // ---- installations: single-link clusters
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            Source a = sources.get(i);
            for (int j = i + 1; j < n; j++) {
                Source b = sources.get(j);
                double dx = a.x() - b.x(), dz = a.z() - b.z();
                if (dx * dx + dz * dz <= CLUSTER * CLUSTER && Math.abs(a.y() - b.y()) <= CLUSTER_Y) union(parent, i, j);
            }
        }
        int[] clusterOf = new int[n];
        List<List<Integer>> clusters = new ArrayList<>();
        int[] map = new int[n];
        java.util.Arrays.fill(map, -1);
        for (int i = 0; i < n; i++) {
            int r = find(parent, i);
            if (map[r] < 0) { map[r] = clusters.size(); clusters.add(new ArrayList<>()); }
            clusterOf[i] = map[r];
            clusters.get(map[r]).add(i);
        }
        int c = clusters.size();
        double[][] centre = new double[c][3];
        double[] aimX = new double[c], aimZ = new double[c];
        boolean[] stereo = new boolean[c], hasFull = new boolean[c], hasMonitor = new boolean[c], hasFill = new boolean[c], allDelay = new boolean[c];
        for (int k = 0; k < c; k++) {
            allDelay[k] = true;
            int full = 0;
            for (int i : clusters.get(k)) {
                Source s = sources.get(i);
                centre[k][0] += s.x(); centre[k][1] += s.y(); centre[k][2] += s.z();
                int g = s.group();
                if (g == AudioGroups.MAIN_LEFT || g == AudioGroups.MAIN_RIGHT) stereo[k] = true;
                if (s.model() != null && s.model().isMonitor() || g >= AudioGroups.MONITOR_1) hasMonitor[k] = true;
                if (g == AudioGroups.FRONT_FILL) hasFill[k] = true;
                if (s.fullRange()) {
                    hasFull[k] = true; full++;
                    aimX[k] += s.fx(); aimZ[k] += s.fz();
                    if (g != AudioGroups.DELAY) allDelay[k] = false;
                }
            }
            int m = clusters.get(k).size();
            centre[k][0] /= m; centre[k][1] /= m; centre[k][2] /= m;
            // the acoustic centre of an installation: its full-range arrays (subs and fills around a hang do not move it)
            if (full > 0) {
                double fx = 0, fy = 0, fz = 0;
                for (int i : clusters.get(k)) { Source s = sources.get(i); if (s.fullRange()) { fx += s.x(); fy += s.y(); fz += s.z(); } }
                centre[k][0] = fx / full; centre[k][1] = fy / full; centre[k][2] = fz / full;
            }
            double l = Math.hypot(aimX[k], aimZ[k]);
            if (l > 1e-6) { aimX[k] /= l; aimZ[k] /= l; }
            if (full == 0) allDelay[k] = false;
        }
        // ---- the aim of the mains: mean of the left / right full-range arrays
        double ax = 0, az = 0;
        for (Source s : sources)
            if (s.fullRange() && (s.group() == AudioGroups.MAIN_LEFT || s.group() == AudioGroups.MAIN_RIGHT)) { ax += s.fx(); az += s.fz(); }
        double al = Math.hypot(ax, az);
        Role[] role = new Role[c];
        java.util.Arrays.fill(role, Role.OTHER);
        String[] reason = new String[c];
        java.util.Arrays.fill(reason, "no left / right mains: nothing classified");
        double mx = 0, my = 0, mz = 0;
        if (al > 1e-6) {
            ax /= al; az /= al;
            // ---- the main system: the most upstream stereo cluster, then the stereo / sub clusters in the same stage plane
            double first = Double.MAX_VALUE;
            for (int k = 0; k < c; k++) if (stereo[k] && hasFull[k]) first = Math.min(first, centre[k][0] * ax + centre[k][2] * az);
            int mains = 0;
            for (int k = 0; k < c; k++) {
                boolean candidate = stereo[k] || clusterOnlySubs(clusters.get(k), sources);
                double along = centre[k][0] * ax + centre[k][2] * az;
                if (candidate && along <= first + STAGE_DEPTH) {
                    role[k] = Role.MAIN; reason[k] = "main system (stage plane)";
                }
            }
            // the reference of every delay: the acoustic centre of the left / right full-range arrays of the main system
            for (int i = 0; i < n; i++) {
                Source s = sources.get(i);
                if (role[clusterOf[i]] == Role.MAIN && s.fullRange() && (s.group() == AudioGroups.MAIN_LEFT || s.group() == AudioGroups.MAIN_RIGHT)) {
                    mx += s.x(); my += s.y(); mz += s.z(); mains++;
                }
            }
            mx /= mains; my /= mains; mz /= mains;
            // ---- the other installations
            for (int k = 0; k < c; k++) {
                if (role[k] == Role.MAIN) continue;
                if (mode == Mode.OFF) { reason[k] = "detection off"; continue; }
                double rx = centre[k][0] - mx, rz = centre[k][2] - mz, flat = Math.hypot(rx, rz), fwd = rx * ax + rz * az;
                double aim = aimX[k] * ax + aimZ[k] * az;
                boolean delayHint = allDelay[k];
                if (mode == Mode.DELAY_GROUP && !delayHint) { reason[k] = "not in the Delay group (mode: Delay group only)"; continue; }
                if (!hasFull[k]) { reason[k] = "no full-range array (subs only)"; continue; }
                if (hasMonitor[k]) { reason[k] = "stage monitors are never delay towers"; continue; }
                if (hasFill[k]) { reason[k] = "front fills are never delay towers"; continue; }
                double minFwd = delayHint ? MIN_FORWARD_DELAY : MIN_FORWARD, minAim = delayHint ? AIM_COS_DELAY : AIM_COS;
                if (fwd < minFwd) { reason[k] = String.format(java.util.Locale.ROOT, "too close to the mains (%.0f blocks in front, needs %.0f)", fwd, minFwd); continue; }
                if (fwd < .5 * flat) { reason[k] = "beside the mains, not in front of them"; continue; }
                if (aim < 0) { reason[k] = "aimed back at the stage"; continue; }
                if (aim < minAim) { reason[k] = "aim ambiguous (sideways): left as it is"; continue; }
                role[k] = Role.TOWER;
                reason[k] = delayHint ? "delay tower (Delay group, in front, aimed away)" : "delay tower (in front of the mains, aimed away)";
            }
        }
        // ---- installations, towers numbered from the stage
        List<Integer> towerOrder = new ArrayList<>();
        for (int k = 0; k < c; k++) if (role[k] == Role.TOWER) towerOrder.add(k);
        final double fax = ax, faz = az;
        towerOrder.sort(java.util.Comparator.comparingDouble(k -> centre[k][0] * fax + centre[k][2] * faz));
        int[] towerIndex = new int[c];
        java.util.Arrays.fill(towerIndex, -1);
        for (int t = 0; t < towerOrder.size(); t++) towerIndex[towerOrder.get(t)] = Math.min(t, MAX_TOWERS - 1);
        // ear height of the audience: the lowest array (a sub, a monitor, a fill stands on the ground) + 0.5, at most 6 under the mains
        double low = Double.MAX_VALUE;
        for (Source s : sources) low = Math.min(low, s.y());
        double earY = al > 1e-6 ? Math.min(low + .5, my - 6) : low + .5;
        List<Installation> out = new ArrayList<>();
        for (int k = 0; k < c; k++) {
            double delay = 0;
            if (role[k] == Role.TOWER)
                delay = towerDelay(mx, my, mz, centre[k][0], centre[k][1], centre[k][2], aimX[k], aimZ[k], earY, trimMs);
            out.add(new Installation(k, role[k], centre[k][0], centre[k][1], centre[k][2], delay, towerIndex[k], reason[k], clusters.get(k).size()));
        }
        boolean main = al > 1e-6;
        return new Layout(clusterOf, List.copyOf(out), main ? ax : 0, main ? az : 1, main ? mx : Double.NaN, main ? my : Double.NaN,
            main ? mz : Double.NaN, earY);
    }

    /**
     * The exact delay of a tower (ms): at the point it is aligned on ({@link #ALIGN_AHEAD} blocks in front of it, at ear height, the
     * middle of the audience it serves), its sound arrives {@code trimMs} after the mains' — the precedence (Haas) effect keeps the image
     * on the stage, and the two never form an echo. {@code (|P - mains| - |P - tower|) / 343 + trim}. Before: the straight distance
     * from the mains to the tower, which is right only on the tower itself (under it, where nobody listens).
     */
    public static double towerDelay(double mx, double my, double mz, double tx, double ty, double tz, double aimX, double aimZ, double earY, double trimMs) {
        double l = Math.hypot(aimX, aimZ);
        double ax = l > 1e-6 ? aimX / l : 0, az = l > 1e-6 ? aimZ / l : 1;
        double px = tx + ax * ALIGN_AHEAD, py = earY, pz = tz + az * ALIGN_AHEAD;
        double toMain = Math.sqrt((px - mx) * (px - mx) + (py - my) * (py - my) + (pz - mz) * (pz - mz));
        double toTower = Math.sqrt((px - tx) * (px - tx) + (py - ty) * (py - ty) + (pz - tz) * (pz - tz));
        return Math.max(0, (toMain - toTower) / SPEED * 1000 + Math.max(0, Math.min(30, trimMs)));
    }

    /** The acoustic centre of the mains (the reference of the delays), or null. */
    public static double[] mainCentre(Layout l) {
        return l.hasMain() ? new double[] {l.mainX(), l.mainY(), l.mainZ()} : null;
    }

    private static boolean clusterOnlySubs(List<Integer> members, List<Source> sources) {
        for (int i : members) { Source s = sources.get(i); if (s.model() == null || !s.model().isSub()) return false; }
        return !members.isEmpty();
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; }
        return i;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) p[Math.max(ra, rb)] = Math.min(ra, rb);
    }
}
