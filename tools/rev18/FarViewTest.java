import com.concertstage.client.light.FarView;

/** Rev. 18: the far-view rules (levels of detail, beam quality, light distance, far ribbons). Usage: java FarViewTest */
public class FarViewTest {
    static int fail;
    static void check(boolean ok, String w) { if (!ok) { fail++; System.out.println("FAIL " + w); } }

    public static void main(String[] a) {
        // GPU cache, high quality: a VORTEX keeps its middle model to 64 blocks (was 28), a FLASHCORE (2.6) further
        check(FarView.lod(5, 1.95f, 2, true) == FarView.NEAR, "close: full model");
        check(FarView.lod(40, 1.95f, 2, true) == FarView.FAR, "40 blocks: middle model (was the lowest from 28)");
        check(FarView.lod(70, 2.6f, 2, true) == FarView.FAR, "big fixture keeps its middle model further");
        check(FarView.lod(70, 1.95f, 2, true) == FarView.LOW, "70 blocks: lowest model");
        check(FarView.lod(40, 1.95f, 0, true) == FarView.LOW, "low quality: lowest model sooner");
        // without the cache (shader pack): never the full model, the middle one only close up
        check(FarView.lod(3, 1.95f, 2, false) == FarView.FAR, "no cache, close: middle model");
        check(FarView.lod(20, 1.95f, 2, false) == FarView.LOW, "no cache, 20 blocks: lowest model");
        // beams
        check(FarView.beamQuality(10, 3) == 3 && FarView.beamQuality(50, 3) == 2 && FarView.beamQuality(100, 3) == 1 && FarView.beamQuality(200, 3) == 1 && FarView.beamQuality(300, 3) == 1 && FarView.beamQuality(400, 3) == 0, "beam quality by distance (rev. 24: medium up to 384 blocks)");
        check(FarView.beamQuality(10, 1) == 1, "frame quality caps it");
        // light distance: auto follows the world, the old saved 160 is auto, a set value stays
        check(FarView.lightDistance(0, 384) == 384, "auto = render distance");
        check(FarView.lightDistance(160, 512) == 512, "old 160 = auto");
        check(FarView.lightDistance(0, 96) == 160, "auto never below 160");
        check(FarView.lightDistance(0, 1024) == 1024, "auto follows the world, no ceiling (rev. 24)");
        check(FarView.lightDistance(96, 512) == 96, "a chosen distance is kept");
        check(FarView.farRibbons(0) > 0 && FarView.farRibbons(3) >= FarView.farRibbons(0), "far ribbons budget");
        check(FarView.haloGrowth(0) == 1f && FarView.haloGrowth(400) <= 3.21f && FarView.haloGrowth(80) > FarView.haloGrowth(40), "halo growth");
        System.out.println(fail == 0 ? "FarViewTest PASS" : "FarViewTest FAIL " + fail);
        if (fail > 0) System.exit(1);
    }
}
