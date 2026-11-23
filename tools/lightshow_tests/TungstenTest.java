import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.LampModel;

/**
 * Off-game tests of the 1.2.2 tungsten lamp of the conventionals and the blinders:
 * <ul>
 * <li>which fixtures have a real filament;</li>
 * <li>the dimmer law (a lamp at half a fader is a fifth of its light, not half);</li>
 * <li>the filament's inertia (asymmetric, frame-rate independent);</li>
 * <li>the light the surface composite shows, before and after: the floor pool of a conventional was flat from 100 % down to about
 *     15 % of the fader (the report of the ground not dimming), and the law below gives the tone map room to show the level.</li>
 * </ul>
 * Pure Java, no Minecraft: run with {@code sh tools/lightshow_tests/run_tungsten.sh}.
 */
public class TungstenTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  ok   " + what); }
        else { failed++; System.out.println("  FAIL " + what); }
    }

    static void near(float got, float want, float tol, String what) {
        check(Math.abs(got - want) <= tol, what + " (" + got + ", want " + want + " +/- " + tol + ")");
    }

    public static void main(String[] args) {
        System.out.println("Tungsten lamp 1.2.2");

        // ---- the fixtures with a real lamp
        for (FixtureProfile p : new FixtureProfile[] {FixtureProfile.PAR_P64, FixtureProfile.FRESNEL_F1, FixtureProfile.PC_C1,
                FixtureProfile.ELLIPSOIDAL_E1, FixtureProfile.BLINDER_X2, FixtureProfile.BLINDER_X4, FixtureProfile.BLINDER_X8})
            check(LampModel.tungsten(p), p.id + " has a tungsten lamp");
        for (FixtureProfile p : new FixtureProfile[] {FixtureProfile.VORTEX_X1, FixtureProfile.LUMORA_W7, FixtureProfile.LUMORA_W19,
                FixtureProfile.PULSESTRIKE_M, FixtureProfile.PIXELBAR_B16, FixtureProfile.FOLLOWSPOT_F1, FixtureProfile.FLOODLIGHT_F8})
            check(!LampModel.tungsten(p), p.id + " is not a tungsten lamp");

        // ---- the dimmer law
        near(LampModel.drive(0f), 0f, 1e-6f, "no dimmer, no light");
        near(LampModel.drive(1f), 1f, 1e-6f, "full dimmer, full light");
        near(LampModel.drive(0.5f), 0.2176f, 0.002f, "half a fader is a fifth of the light");
        check(LampModel.drive(0.5f) < 0.25f, "the law is not linear (the old one was 0.5)");

        // ---- inertia: heating is quicker than cooling, and the curve does not depend on the frame rate
        float up = simulate(0f, 1f, 0.5f, 1f, 1f / 240f), up20 = simulate(0f, 1f, 0.5f, 1f, 1f / 20f);
        near(up, up20, 0.01f, "heat-up at 240 and at 20 images a second");
        check(up > 0.97f, "a lamp reaches its level in half a second (" + up + ")");
        float down = simulate(1f, 0f, 0.5f, 1f, 1f / 240f);
        check(down < 0.35f, "a lamp cools slower: a third is still lit after half a second (" + down + ")");
        check(LampModel.COOL > LampModel.HEAT * 2f, "the cooling time constant is the slower one");
        float off = simulate(0f, 1f, 0.02f, 0f, 1f / 60f);
        near(off, 1f, 1e-4f, "lamp fade off: the target at once");

        // ---- the floor pool: the reason for the lamp law
        // A PAR about 5 blocks over the floor, at night: L ~ 9 x light (the old balance, 0.8) or 4.5 x (the 1.2.2 tungsten balance).
        System.out.println("  floor pool of a conventional, as the composite shows it:");
        System.out.println("    dimmer   before (linear)   after (tungsten law)");
        float fullBefore = 0f, fullAfter = 0f, halfBefore = 0f, halfAfter = 0f;
        for (float d : new float[] {1f, 0.75f, 0.5f, 0.25f, 0.1f, 0.05f}) {
            float before = LampModel.composite(9f * d) - 1f;
            float after = LampModel.composite(4.5f * LampModel.drive(d)) - 1f;
            if (d == 1f) { fullBefore = before; fullAfter = after; }
            if (d == 0.5f) { halfBefore = before; halfAfter = after; }
            System.out.printf("    %5.0f %%   %12.3f   %19.3f%n", d * 100f, before, after);
        }
        check(halfBefore / fullBefore > 0.9f, "before: at half a fader the pool was still 90 % of full (the report)");
        check(halfAfter / fullAfter > 0.35f && halfAfter / fullAfter < 0.65f,
            "after: at half a fader the pool is about half of full (" + (halfAfter / fullAfter) + ")");
        check(fullAfter > 0.95f * fullBefore, "after: full is as bright as before");

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** Run the filament from {@code from} towards {@code to} for {@code seconds}, one {@code dt}. */
    static float simulate(float from, float to, float seconds, float speed, float dt) {
        float level = from;
        for (float t = 0; t < seconds; t += dt) level = LampModel.step(level, to, dt, speed);
        return level;
    }
}
