import com.concertstage.client.AutoQuality;

/** 1.1.1: AUTO render quality — steps, thresholds, dwell time, no oscillation, ceiling. Off game. */
public class AutoQualityTest {
    static int fail;
    static void check(boolean ok, String w) { if (!ok) { fail++; System.out.println("FAIL " + w); } }
    public static void main(String[] a) {
        AutoQuality q = new AutoQuality(2);
        long t = 0;
        for (; t < 60000; t += 50) q.update(120, t, 2);
        check(q.level() == 2, "fast machine stays HIGH");
        // 30 FPS: down one step after 3 s (dwell already passed), the next not before 20 s
        long start = t; int changes = 0, last = q.level();
        for (; t < start + 19000; t += 50) { q.update(30, t, 2); if (q.level() != last) { changes++; last = q.level(); } }
        check(q.level() == 1 && changes == 1, "30 FPS: one step down within 19 s (" + changes + ")");
        for (; t < start + 30000; t += 50) q.update(30, t, 2);
        check(q.level() == 0, "still 30 FPS: LOW after the dwell time");
        // FPS hovering around the thresholds (35..85, alternating every 2 s): at most one change per 20 s
        AutoQuality h = new AutoQuality(1); long t2 = 0; int ch = 0, lv = 1;
        for (; t2 < 600000; t2 += 50) { int fps = ((t2 / 2000) % 2 == 0) ? 35 : 85; h.update(fps, t2, 2); if (h.level() != lv) { ch++; lv = h.level(); } }
        check(ch == 0, "alternating 35 / 85 FPS every 2 s: no change at all (" + ch + ")");
        // between the thresholds: nothing moves
        AutoQuality m = new AutoQuality(1);
        for (long t3 = 0; t3 < 120000; t3 += 50) m.update(55, t3, 2);
        check(m.level() == 1, "55 FPS: stays");
        // recovery and ceiling
        AutoQuality r = new AutoQuality(0); long t4 = 0;
        for (; t4 < 200000; t4 += 50) r.update(140, t4, 1);
        check(r.level() == 1, "fast again: up to the ceiling only (" + r.level() + ")");
        check(new AutoQuality(2).update(0, 0, 2) == 2, "no FPS measure yet: unchanged");
        System.out.println(fail == 0 ? "AutoQualityTest PASS" : "AutoQualityTest FAIL " + fail);
        if (fail > 0) System.exit(1);
    }
}
