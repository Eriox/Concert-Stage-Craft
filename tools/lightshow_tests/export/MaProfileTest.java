package com.concertstage.light;

import com.concertstage.light.core.*;
import com.concertstage.light.dmx.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Off-game checks of the grandMA2 / dot2 fixture XML (EXPORT FIXTURE PROFILE). They check the files AGAINST THE GAME TABLES
 * (DmxMode), not against dot2: well-formed XML, MA root and namespace, one file per type and exported mode with the right file name,
 * every DMX channel of the mode described exactly once (coarse / fine offsets of the main module, sub-fixture patches of the LED cells),
 * defaults, 24-bit ranges contiguous from 0 to 16777215, channel sets inside their function, the export of the console.
 * A pass here does NOT mean dot2 accepts the files: that needs the import test in dot2 onPC.
 * Usage: sh tools/lightshow_tests/run_external.sh
 */
public class MaProfileTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  ok   " + what); } else { failed++; System.out.println("  FAIL " + what); }
    }

    static List<Element> kids(Element e, String tag) {
        List<Element> out = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element c && c.getTagName().equals(tag)) out.add(c);
        return out;
    }

    static int num(Element e, String a) {
        return Integer.parseInt(e.getAttribute(a));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("grandMA2 / dot2 fixture XML");
        Path res = Paths.get("common/src/main/resources/assets/concertstage/external");
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        for (String sub : new String[] {"gma2", "dot2"}) {
            int files = 0;
            for (FixtureProfile p : FixtureProfile.all()) {
                for (int mode : ExternalExport.XML_MODES) {
                    DmxMode m = DmxMode.of(p, mode);
                    Path f = res.resolve(sub).resolve(ExternalExport.maXmlName(p, mode));
                    String tag = sub + " " + p.name + " " + m.name();
                    if (!Files.isRegularFile(f)) { check(false, tag + ": file " + f.getFileName()); continue; }
                    files++;
                    Document d = dbf.newDocumentBuilder().parse(f.toFile());
                    Element root = d.getDocumentElement();
                    boolean ok = root.getLocalName().equals("MA") && "http://schemas.malighting.de/grandma2/xml/MA".equals(root.getNamespaceURI())
                        && f.getFileName().toString().contains("@");
                    Element ft = (Element) root.getElementsByTagNameNS("*", "FixtureType").item(0);
                    ok &= ft != null && ft.getAttribute("name").equals(p.name) && ft.getAttribute("mode").equals(m.name());
                    // channels described: main module coarse / fine, sub-fixture modules at their instance patch
                    Element modules = kids(ft, "Modules").get(0);
                    List<Element> mods = kids(modules, "Module");
                    List<Element> inst = kids(kids(ft, "Instances").get(0), "Instance");
                    int[] owner = new int[m.footprint() + 1];
                    boolean ranges = true, defaults = true, sets = true;
                    for (Element in : inst) {
                        int mi = num(in, "module_index"), patch = num(in, "patch");
                        for (Element ct : kids(mods.get(mi), "ChannelType")) {
                            int coarse = patch - 1 + num(ct, "coarse");
                            if (coarse >= owner.length) { ok = false; continue; }
                            owner[coarse]++;
                            boolean wide = ct.hasAttribute("fine");
                            if (wide) { int fine = patch - 1 + num(ct, "fine"); if (fine < owner.length) owner[fine]++; else ok = false; }
                            DmxMode.Channel ch = m.channels[coarse - 1];
                            int def = num(ct, "default");
                            defaults &= def == (wide ? ch.def() * 256 : ch.def());
                            if (wide) ok &= coarse + 1 == num(ct, "fine") + patch - 1 && m.channels[coarse].kind() == DmxMode.Kind.FINE;
                            // 24-bit function ranges: contiguous, 0 .. 16777215, sets inside the function (in the channel's resolution)
                            long next = 0;
                            for (Element cf : kids(ct, "ChannelFunction")) {
                                long lo = Long.parseLong(cf.getAttribute("min_dmx_24")), hi = Long.parseLong(cf.getAttribute("max_dmx_24"));
                                ranges &= lo == next && hi >= lo;
                                next = hi + 1;
                                int shift = wide ? 8 : 16;
                                for (Element cs : kids(cf, "ChannelSet"))
                                    sets &= (num(cs, "from_dmx") >= (lo >> shift)) && (num(cs, "to_dmx") <= (hi >> shift)) && num(cs, "from_dmx") <= num(cs, "to_dmx");
                            }
                            ranges &= next == 16777216L;
                        }
                    }
                    boolean exact = true;
                    for (int c = 1; c <= m.footprint(); c++) exact &= owner[c] == 1;
                    // rev. 16i: the version of the file is the version of the software it is written for (dot2 refuses a newer one)
                    boolean ver = sub.equals("dot2") ? root.getAttribute("major_vers").equals("1") : root.getAttribute("major_vers").equals("3");
                    check(ver, tag + ": written for " + (sub.equals("dot2") ? "dot2 1.x" : "grandMA2 3.9") + " (major_vers " + root.getAttribute("major_vers") + "." + root.getAttribute("minor_vers") + ")");
                    check(ok, tag + ": MA root, FixtureType name / mode, offsets");
                    check(exact, tag + ": each of the " + m.footprint() + " channels described exactly once");
                    check(defaults, tag + ": defaults = DmxMode defaults");
                    check(ranges, tag + ": function ranges contiguous 0..16777215");
                    check(sets, tag + ": channel sets inside their function");
                    // what the game reads on each attribute channel is what the XML says (attribute -> coarse channel)
                    boolean zoned = p.hasSections() && (mode == DmxMode.FULL || mode == DmxMode.COMPACT);
                    if (zoned) {
                        // rev. 21: the light zones are sub-fixtures (Top bar, Centre strobe, Bottom bar): their DIM / COLORRGB1 / SHUTTER
                        // are the channels the game decodes for that zone
                        List<String> names = new ArrayList<>();
                        boolean same = true;
                        for (Element in : inst) {
                            if (num(in, "index") == 0) continue;
                            Element mod = mods.get(num(in, "module_index"));
                            String n = mod.getAttribute("name");
                            // rev. 24: the two-instance Compact: "LED bars" carries the top zone attributes (copied to the bottom bar by the
                            // game), "White bar" the centre
                            int k = n.startsWith("Top") || n.startsWith("LED bars") ? 0 : n.startsWith("Centre") || n.startsWith("White bar") ? 1 : n.startsWith("Bottom") ? 2 : -1;
                            if (k < 0) continue;
                            names.add(n + "@" + num(in, "patch"));
                            Map<String, Integer> byAttr = new HashMap<>();
                            for (Element ct : kids(mod, "ChannelType")) byAttr.putIfAbsent(ct.getAttribute("attribute"), num(in, "patch") - 1 + num(ct, "coarse"));
                            same &= Objects.equals(byAttr.get("DIM"), m.channelsOf(FixtureProfile.sectionDim(k))[0]);
                            if (FixtureProfile.sectionRed(k) != null) same &= Objects.equals(byAttr.get("COLORRGB1"), m.channelsOf(FixtureProfile.sectionRed(k))[0]);
                            if (mode == DmxMode.FULL) same &= Objects.equals(byAttr.get("SHUTTER"), m.channelsOf(FixtureProfile.sectionStrobe(k))[0]);
                        }
                        check(same, tag + ": DIM / COLORRGB1 / SHUTTER of each light zone on the channels the game decodes");
                        List<String> want = new ArrayList<>();
                        for (DmxMode.Instance x : m.instances()) want.add(x.name() + "@" + (x.channels()[0] + 1));
                        check(names.equals(want), tag + ": zone instances " + want);
                    } else if (mode != DmxMode.ULTRA) {
                        // rev. 16g: over the fixture and its named instances (absolute channel = instance patch - 1 + coarse); the
                        // per-zone sub-fixtures of the Full mode are left out (their RGB is the cell, not the fixture)
                        Map<String, Integer> byAttr = new HashMap<>();
                        for (Element in : inst) {
                            Element mod = mods.get(num(in, "module_index"));
                            if (num(in, "index") != 0 && !(mod.getAttribute("name").equals("LED bars") || mod.getAttribute("name").equals("White bar"))) continue;
                            for (Element ct : kids(mod, "ChannelType")) byAttr.putIfAbsent(ct.getAttribute("attribute"), num(in, "patch") - 1 + num(ct, "coarse"));
                        }
                        boolean same = Objects.equals(byAttr.get("DIM"), m.channelsOf(Attr.DIMMER)[0])
                            && Objects.equals(byAttr.get("SHUTTER"), m.channelsOf(Attr.STROBE)[0])
                            ;
                        int hue = -1;
                        for (int i = 0; i < m.channels.length; i++) if (m.channels[i].kind() == DmxMode.Kind.HUE) hue = i + 1;
                        if (hue > 0) same &= Objects.equals(byAttr.get("HUE"), hue) && !byAttr.containsKey("COLORRGB1");      // rev. 17 Compact: hue + saturation
                        else if (p.has(Attr.RED)) same &= Objects.equals(byAttr.get("COLORRGB1"), m.channelsOf(Attr.RED)[0]);
                        else same &= !byAttr.containsKey("COLORRGB1");
                        if (p.has(Attr.TILT)) same &= Objects.equals(byAttr.get("TILT"), m.channelsOf(Attr.TILT)[0]);
                        else same &= !byAttr.containsKey("TILT");
                        if (p.hasPan()) same &= Objects.equals(byAttr.get("PAN"), m.channelsOf(Attr.PAN)[0]);
                        check(same, tag + ": PAN / TILT / DIM / SHUTTER / COLORRGB1 or HUE on the channels the game decodes");
                        // rev. 16g: the named instances of the mode (PULSESTRIKE M Compact: LED bars, then the white bar)
                        List<String> names = new ArrayList<>();
                        for (Element in : inst) {
                            if (num(in, "index") == 0) continue;
                            String n = mods.get(num(in, "module_index")).getAttribute("name");
                            if (n.equals("LED bars") || n.equals("White bar")) names.add(n + "@" + num(in, "patch"));
                        }
                        List<String> want = new ArrayList<>();
                        for (DmxMode.Instance x : m.instances()) want.add(x.name() + "@" + (x.channels()[0] + 1));
                        check(names.equals(want), tag + ": instances " + want);
                    }
                }
            }
            check(files == FixtureProfile.all().size() * 3, sub + ": one file per registered type and conventional mode");
            check(Files.isRegularFile(res.resolve(sub).resolve("README.md")), sub + ": README with the channel tables");
        }
        // the console export (EXPORT FIXTURE PROFILE)
        Path base = Files.createTempDirectory("cscprofile").resolve("concertstage");
        Files.createDirectories(base);
        Show s = new Show();
        s.id = "profile-test";
        s.name = "Profiles";
        String r = ExternalExport.export(base, s, ExternalExport.PROFILE_DOT2, Set.of());
        System.out.println("  " + r);
        Path dot2 = base.resolve("export/Profiles/USB stick/dot2/library");
        long n;
        try (var st = Files.list(dot2)) { n = st.filter(x -> x.toString().endsWith(".xml")).count(); }
        check(r.startsWith("Exported") && n == FixtureProfile.all().size() * 3, "EXPORT FIXTURE PROFILE dot2 with no fixture patched: all registered types");
        r = ExternalExport.export(base, s, ExternalExport.PROFILE_MA2, Set.of());
        check(r.startsWith("Exported") && Files.isRegularFile(base.resolve("export/Profiles/README - gma2.md")), "EXPORT FIXTURE PROFILE grandMA2");
        r = ExternalExport.export(base, s, ExternalExport.PROFILE_MA3, Set.of());
        check(r.startsWith("Exported") && Files.isRegularFile(base.resolve("export/Profiles/USB stick/grandMA3/gma3_library/fixturetypes/gdtf/Concert Stage Craft@CSC VORTEX X1.gdtf")), "EXPORT FIXTURE PROFILE grandMA3 (GDTF)");
        check(ExternalExport.profileSummary(FixtureProfile.LUMORA_W19).equals("LUMORA W19: Full 124, Compact 7, Ultra Compact 4"), "channel counts shown: " + ExternalExport.profileSummary(FixtureProfile.LUMORA_W19));
        System.out.println();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }
}
