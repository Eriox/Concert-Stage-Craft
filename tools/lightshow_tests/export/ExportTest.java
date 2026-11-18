package com.concertstage.light;

import com.concertstage.light.core.*;
import com.concertstage.light.dmx.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Off-game tests of the External Show Control exports and imports (ExternalExport): patch CSV / JSON, GDTF copy, MVR (XML, matrices,
 * addresses, GDTF inside), QLC+ workspace, 3D models, and the imports of the patch CSV and of the MVR (round trip).
 * Usage: sh tools/lightshow_tests/run_external.sh (runs it after ExternalTest)
 */
public class ExportTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  ok   " + what); } else { failed++; System.out.println("  FAIL " + what); }
    }

    public static void main(String[] a) throws Exception {
        System.out.println("Exports / imports");
        Show s = new Show();
        s.id = "export-test";
        s.name = "Festival";
        Live l = new Live();
        String[] types = {FixtureProfile.VORTEX_X1.id, FixtureProfile.LUMORA_W7.id, FixtureProfile.LUMORA_W19.id, FixtureProfile.PULSESTRIKE_M.id};
        for (int i = 0; i < 8; i++)
            Commands.apply(s, l, Commands.op(Commands.PATCH_ADD).varInt(i + 1).str(types[i % 4]).str("").sInt(i * 3).sInt(70).sInt(-20).f32(i % 2 == 0 ? 0 : 90).varInt(i < 4 ? 1 : 0).toBytes(), 0, null);
        AutoPatch.Plan p = AutoPatch.plan(s, 1, DmxMode.FULL, AutoPatch.SHARE_NONE);
        for (AutoPatch.Entry e : p.entries()) { Show.Patch f = s.fixture(e.fid()); f.universe = e.universe(); f.address = e.address(); f.dmxMode = e.mode(); }
        s.fixture(2).invertPan = true;
        Path base = Files.createTempDirectory("cscexport").resolve("concertstage");
        Files.createDirectories(base);
        String r = ExternalExport.export(base, s, ExternalExport.RIG, Set.of());
        System.out.println("  " + r);
        check(r.startsWith("Exported"), "complete rig exported");
        Path dir;
        try (var st = Files.list(base.resolve("export"))) { dir = st.filter(Files::isDirectory).findFirst().orElseThrow(); }
        for (String f : new String[] {"patch.csv", "patch.json", "Festival.mvr", "gdtf/Concert Stage Craft@CSC VORTEX X1.gdtf", "models/csc_lumora_w19.glb",
            "models/ma3d/PULSEM.3ds", "models/csc_vortex_x1_head.obj", "qlcplus/Festival.qxw", "qlcplus/Concert-Stage-Craft-LUMORA-W7.qxf",
            "USB stick/dot2/library/concert_stage_craft@csc_pulsestrike_m@full.xml", "USB stick/gma2/library/concert_stage_craft@csc_vortex_x1@ultra_compact.xml",
            "README - dot2.md", "INSTALL - consoles.txt", "USB stick/grandMA3/gma3_library/fixturetypes/gdtf/Concert Stage Craft@CSC VORTEX X1.gdtf"})
            check(Files.isRegularFile(dir.resolve(f)), "file " + f);
        // MVR
        Map<String, byte[]> z = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(dir.resolve("Festival.mvr")))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) z.put(e.getName(), in.readAllBytes());
        }
        check(z.containsKey("GeneralSceneDescription.xml") && z.keySet().stream().filter(n -> n.endsWith(".gdtf")).count() == 4, "MVR: scene + the 4 GDTF");
        var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(z.get("GeneralSceneDescription.xml")));
        var fx = doc.getElementsByTagName("Fixture");
        check(fx.getLength() == 8, "MVR: 8 fixtures");
        var f1 = (org.w3c.dom.Element) fx.item(0);
        String m1 = f1.getElementsByTagName("Matrix").item(0).getTextContent();
        // fixture 1: block (0, 70, -20), hung, yaw 0: origin (0.5, 71, -19.5) m -> MVR (500, 19500, 71000) mm, rotated 180 about MVR Y
        check(m1.equals("{-1.000000,0.000000,0.000000}{0.000000,1.000000,0.000000}{0.000000,0.000000,-1.000000}{500.000,19500.000,71000.000}"),
            "MVR matrix of a hung fixture: " + m1);
        var f6 = (org.w3c.dom.Element) fx.item(5);
        String m6 = f6.getElementsByTagName("Matrix").item(0).getTextContent();
        // yaw 90: the front of the model (Minecraft -Z, north) turns to the west (Minecraft -X) = MVR -X; MVR Y (north) goes to -X
        check(m6.equals("{0.000000,1.000000,0.000000}{-1.000000,0.000000,0.000000}{0.000000,0.000000,1.000000}{15500.000,19500.000,70000.000}"),
            "MVR matrix of a standing fixture turned 90 degrees (front to the west): " + m6);
        String ad = ((org.w3c.dom.Element) fx.item(1)).getElementsByTagName("Address").item(0).getTextContent();
        check(Integer.parseInt(ad) == s.fixture(2).universe * 512 + s.fixture(2).address, "MVR absolute address (rev. 16h: CSC universe U = Art-Net Port-Address U = MVR universe U + 1)");
        check(((org.w3c.dom.Element) fx.item(1)).getElementsByTagName("DMXInvertPan").getLength() == 1, "MVR: inverted pan kept");
        // imports: change the patch, then read the exports back
        Map<Integer, int[]> before = new HashMap<>();
        for (Show.Patch q : s.patch) before.put(q.fid, new int[] {q.universe, q.address, q.dmxMode});
        Files.createDirectories(base.resolve("import"));
        Files.copy(dir.resolve("Festival.mvr"), base.resolve("import/Festival.mvr"));
        Files.copy(dir.resolve("patch.csv"), base.resolve("import/patch.csv"));
        for (Show.Patch q : s.patch) { q.universe = 9; q.address = 1; q.dmxMode = DmxMode.ULTRA; }
        ExternalExport.Imported im = ExternalExport.importMvr(base, s, "Festival");
        System.out.println("  " + im.report());
        check(im.command() != null && Commands.apply(s, l, im.command(), 0, null) == Commands.SHOW, "MVR import applies");
        boolean same = true;
        for (Show.Patch q : s.patch) same &= Arrays.equals(before.get(q.fid), new int[] {q.universe, q.address, q.dmxMode});
        check(same, "MVR export -> import gives the same patch");
        for (Show.Patch q : s.patch) { q.universe = 9; q.address = 1; q.dmxMode = DmxMode.ULTRA; }
        im = ExternalExport.importPatch(base, s, "patch");
        check(im.command() != null && Commands.apply(s, l, im.command(), 0, null) == Commands.SHOW, "patch CSV import applies");
        same = true;
        for (Show.Patch q : s.patch) same &= Arrays.equals(before.get(q.fid), new int[] {q.universe, q.address, q.dmxMode});
        check(same, "patch CSV export -> import gives the same patch");
        check(ExternalExport.importPatch(base, s, "../../etc/passwd").command() == null, "import outside the import folder refused");
        String qxw = Files.readString(dir.resolve("qlcplus/Festival.qxw"));
        check(qxw.contains("<Universe>" + s.fixture(1).universe + "</Universe>") && qxw.contains("ID=\"" + s.fixture(1).universe + "\"") && qxw.contains("<Mode>" + DmxMode.NAMES[s.fixture(1).dmxMode] + "</Mode>"), "QLC+ workspace: universe ID = Art-Net Port-Address (CSC universe 0 = ID 0), mode name");
        System.out.println();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }
}
