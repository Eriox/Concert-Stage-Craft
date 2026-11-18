import com.concertstage.client.light.FrameBudget;

/** Rev. 23: over capacity, each family of fixtures keeps its share of the places (VORTEX switched on must not take the LUMORA theirs). */
public final class BudgetGroupsTest {
    static int fail, pass;
    static void ok(boolean c, String m) { if (c) pass++; else { fail++; System.out.println("FAIL " + m); } System.out.println((c ? "  ok   " : "  FAIL ") + m); }

    public static void main(String[] a) {
        FrameBudget b = new FrameBudget("test");
        Object[] wash = new Object[16], spot = new Object[30], panel = new Object[16];
        for (int i = 0; i < 16; i++) { wash[i] = "w" + i; panel[i] = "p" + i; }
        for (int i = 0; i < 30; i++) spot[i] = "s" + i;
        // the washes alone: all of them in
        for (int f = 0; f < 5; f++) { b.frame(16); for (Object w : wash) b.admit(w, 2f, 1); }
        int in = 0; b.frame(16); for (Object w : wash) if (b.admit(w, 2f, 1)) in++;
        ok(in == 16, "16 washes alone, 16 places: all lit (" + in + ")");
        // 30 spots switched on, scoring 5 x more
        int washIn = 0, spotIn = 0;
        for (int f = 0; f < 10; f++) {
            b.frame(16); washIn = 0; spotIn = 0;
            for (Object w : wash) if (b.admit(w, 2f, 1)) washIn++;
            for (Object s : spot) if (b.admit(s, 10f, 0)) spotIn++;
        }
        ok(washIn >= 5 && washIn <= 7, "with 30 spots: the washes keep their share (" + washIn + " of 16 places, expected ~5)");
        ok(washIn + spotIn <= 16, "never more than the capacity (" + (washIn + spotIn) + ")");
        // stable from frame to frame
        int again = 0; b.frame(16); for (Object w : wash) if (b.admit(w, 2f, 1)) again++; for (Object s : spot) b.admit(s, 10f, 0);
        ok(again == washIn, "same places the next frame (" + again + ")");
        // capacity 0 (quality lowered): nobody, no crash
        b.frame(0); int z = 0; for (Object w : wash) if (b.admit(w, 2f, 1)) z++;
        b.frame(0); z = 0; for (Object w : wash) if (b.admit(w, 2f, 1)) z++;
        ok(z == 0, "capacity 0: no place, no crash");
        System.out.println("\npassed " + pass + ", failed " + fail);
        if (fail > 0) System.exit(1);
    }
}
