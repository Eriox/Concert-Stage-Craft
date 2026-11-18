package com.concertstage.light;

import com.concertstage.light.core.Bytes;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.core.Show;
import com.concertstage.light.dmx.AutoPatch;
import com.concertstage.light.dmx.DmxMode;
import com.concertstage.light.dmx.VirtualMatrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Exports and imports of External Show Control (server side; files in {@code <game or server folder>/concertstage/export} and
 * {@code .../import}):
 * <ul>
 * <li>{@link #PATCH}: the patch as CSV (spreadsheet, MA Fixture Builder notes) and JSON;</li>
 * <li>{@link #GDTF}: the GDTF files of the fixture types used (built from the game data, in the mod);</li>
 * <li>{@link #MVR}: the rig as MVR (DIN SPEC 15801): every fixture with its position and orientation in the world, its GDTF, DMX mode
 * and address, the GDTF files inside;</li>
 * <li>{@link #MODELS}: the 3D models (glTF, OBJ, MA 3D / dot2 3D 3DS) and their pivots;</li>
 * <li>{@link #RIG}: all of it at once in one dated folder (EXPORT COMPLETE LIGHTING RIG);</li>
 * <li>{@link #QLC}: QLC+ fixture definitions and a QLC+ workspace with the patch;</li>
 * <li>EXPORT FIXTURE PROFILE, one exporter per software: {@link #PROFILE_MA3} (grandMA3: the GDTF files),
 * {@link #PROFILE_MA2} (grandMA2 fixture XML, gma2/library) and {@link #PROFILE_DOT2} (dot2 fixture XML, dot2/library). The MA2 / dot2
 * XML files are generated at build time from the DmxMode tables by tools/external/ma2_profiles.py (not converted from GDTF), one file
 * per type and DMX mode, with a README holding the channel correspondence tables.</li>
 * </ul>
 * Coordinates: Minecraft (x east, y up, z south, 1 block = 1 m) to MVR (X east, Y north, Z up, millimetres): (x, y, z) -> (x, -z, y).
 */
public final class ExternalExport {
    private ExternalExport() {}

    public static final int PATCH = 0, GDTF = 1, MVR = 2, MODELS = 3, RIG = 4, QLC = 5, PROFILE_MA3 = 6, PROFILE_MA2 = 7, PROFILE_DOT2 = 8,
        PROFILE_MAGICQ = 9;
    public static final String[] NAMES = {"Patch (CSV / JSON)", "GDTF profiles", "MVR rig", "3D models", "Complete rig", "QLC+",
        "grandMA3 GDTF", "grandMA2 XML", "dot2 XML", "MagicQ"};
    /** The EXPORT FIXTURE PROFILE choices, in the order of the console's software button. */
    public static final int[] PROFILE_KINDS = {PROFILE_MA3, PROFILE_MA2, PROFILE_DOT2, PROFILE_MAGICQ};
    /** Rev. 25b: the gobo pictures of the MA2 / dot2 wheels (Slot media_filename = user_gobos/CSC/<file>.png, see ma2_profiles.py). */
    static final String[] MA_GOBOS = {"gobo_1", "gobo_2", "gobo_3", "gobo_4", "gobo_5", "gobo_6", "gobo_7", "gobo_8",
        "gobo_phantom_1", "gobo_phantom_2", "gobo_phantom_3", "gobo_phantom_4", "gobo_phantom_5", "gobo_phantom_6", "gobo_phantom_7", "gobo_phantom_8",
        "gobo_2_1", "gobo_2_2", "gobo_2_3", "gobo_2_4", "gobo_2_5", "gobo_2_6", "gobo_2_7", "gobo_2_8",
        "gobo_profile_1", "gobo_profile_2", "gobo_profile_3", "gobo_profile_4", "gobo_profile_5", "gobo_profile_6", "gobo_profile_7", "gobo_profile_8",
        "gobo_razor_1", "gobo_razor_2", "gobo_razor_3", "gobo_razor_4", "gobo_razor_5", "gobo_razor_6", "gobo_razor_7", "gobo_razor_8"};
    /** Modes written as MA2 / dot2 XML (the Virtual Matrix mode has no channel). */
    static final int[] XML_MODES = {DmxMode.FULL, DmxMode.COMPACT, DmxMode.ULTRA};
    static final String[] XML_MODE_FILE = {"full", "compact", "ultra_compact"};
    static final String RES = "assets/concertstage/external/";
    static final String MANUFACTURER = "Concert Stage Craft";
    static final String[] IDS = FixtureProfile.all().stream().map(p -> p.id).toArray(String[]::new);
    static final String[] MA3D = {"VORTEX", "LUMW7", "LUMW19", "PULSEM", "PHANTOMS", "PROFILEP", "RAZORB1", "FLASHCOR", "PIXELBAR", "PIXELTUB", "BLINDERX", "BLINDERX", "BLINDERX", "FOLLOWSP", "PIXELWAS", "LASERCOR", "FLOODLIG"};

    static String gdtfName(FixtureProfile p) {
        return MANUFACTURER + "@" + p.name + ".gdtf";
    }

    /** File name of a MA2 / dot2 fixture type (same rule as tools/external/ma2_profiles.py: manufacturer@model@mode.xml). */
    static String maXmlName(FixtureProfile p, int mode) {
        return MANUFACTURER.toLowerCase(Locale.ROOT).replace(' ', '_') + "@" + p.name.toLowerCase(Locale.ROOT).replace(' ', '_') + "@" + XML_MODE_FILE[mode] + ".xml";
    }

    /** Modes and channel counts of the exported profiles, for the console ("VORTEX X1: Full 18, Compact 14, Ultra Compact 4"). */
    public static String profileSummary(FixtureProfile p) {
        StringBuilder s = new StringBuilder(p.name.replace("CSC ", "")).append(':');
        for (int m : XML_MODES) s.append(m == XML_MODES[0] ? " " : ", ").append(DmxMode.NAMES[m]).append(' ').append(DmxMode.of(p, m).footprint());
        return s.toString();
    }

    /** Writes the MA2 / dot2 fixture XML of {@code types} (every exported mode) and the README into {@code d}; returns the file count. */
    static int maXml(String sub, Set<FixtureProfile> types, Path d) throws IOException {
        Files.createDirectories(d);
        int n = 0;
        for (FixtureProfile p : types)
            for (int m : XML_MODES) {
                if (DmxMode.of(p, m).footprint() == 0) continue;
                copy(RES + sub + "/" + maXmlName(p, m), d.resolve(maXmlName(p, m)));
                n++;
            }
        // rev. 25b: the gobo pictures the wheels point to, in the "gobos" folder of the same software tree (USB:/dot2/gobos/user_gobos/CSC)
        Path gobos = d.getParent().resolve("gobos").resolve("user_gobos").resolve("CSC");
        Files.createDirectories(gobos);
        for (String gname : MA_GOBOS) copy(RES + "ma_gobos/" + gname + ".png", gobos.resolve(gname + ".png"));
        // rev. 16i: the README next to the USB tree, not inside the library folder the console reads
        Path out = d.getParent() != null && d.getParent().getParent() != null && d.getParent().getParent().getParent() != null ? d.getParent().getParent().getParent() : d;
        copy(RES + sub + "/README.md", out.resolve("README - " + sub + ".md"));
        return n;
    }

    static String qxfName(FixtureProfile p) {
        return "Concert-Stage-Craft-" + p.name.replace("CSC ", "").replace(' ', '-') + ".qxf";
    }

    /** Does the export, returns what was written (or why not). */
    static String export(Path base, Show show, int kind, Set<Integer> selection) {
        if (base == null) return "No server folder";
        List<Show.Patch> fx = new ArrayList<>();
        for (Show.Patch p : show.patch) if (selection.isEmpty() || selection.contains(p.fid)) fx.add(p);
        if (fx.isEmpty() && kind != GDTF && kind != MODELS && kind != QLC && kind < PROFILE_MA3) return "Nothing to export (no fixture)";
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String show_ = clean(show.name).replace(' ', '_');
        Path dir = base.resolve("export").resolve(kind == RIG ? show_ + "_rig_" + stamp : show_);
        try {
            Files.createDirectories(dir);
            List<String> written = new ArrayList<>();
            Set<FixtureProfile> types = new LinkedHashSet<>();
            for (Show.Patch p : (fx.isEmpty() ? show.patch : fx)) types.add(p.profile());
            if (types.isEmpty()) types.addAll(FixtureProfile.all());
            if (kind == PATCH || kind == RIG) {
                Files.writeString(dir.resolve("patch.csv"), csv(show, fx));
                Files.writeString(dir.resolve("patch.json"), json(show, fx));
                written.add("patch.csv"); written.add("patch.json");
            }
            // rev. 16i: the console profiles are written in the folder tree the software reads on a USB stick: copy the content of
            // "USB stick" to the root of the stick (dot2 only lists the fixture types of dot2/library: "fixtures not recognised")
            if (kind == PROFILE_MA2 || kind == RIG) {
                Path d = dir.resolve("USB stick").resolve("gma2").resolve("library");
                written.add("USB stick/gma2/library (" + maXml("gma2", types, d) + " grandMA2 XML)");
            }
            if (kind == PROFILE_DOT2 || kind == RIG) {
                Path d = dir.resolve("USB stick").resolve("dot2").resolve("library");
                written.add("USB stick/dot2/library (" + maXml("dot2", types, d) + " dot2 XML)");
            }
            if (kind == PROFILE_MA3 || kind == RIG) {
                Path d = dir.resolve("USB stick").resolve("grandMA3").resolve("gma3_library").resolve("fixturetypes").resolve("gdtf");
                Files.createDirectories(d);
                for (FixtureProfile p : types) copy(RES + "gdtf/" + gdtfName(p), d.resolve(gdtfName(p)));
                written.add("USB stick/grandMA3/gma3_library/fixturetypes/gdtf (" + types.size() + " GDTF)");
            }
            // rev. 25b: ChamSys MagicQ reads GDTF (1.8.4.4 and later: modes, wheels and gobo pictures) and MVR; the .hed format of its own
            // library is not published, so MagicQ gets the GDTF of the types used and a patch it imports (Patch > View Chans > Import Heads)
            if (kind == PROFILE_MAGICQ || kind == RIG) {
                Path d = dir.resolve("MagicQ").resolve("heads");
                Files.createDirectories(d);
                for (FixtureProfile p : types) copy(RES + "gdtf/" + gdtfName(p), d.resolve(gdtfName(p)));
                written.add("MagicQ/heads (" + types.size() + " GDTF)");
                if (!fx.isEmpty()) {
                    Files.writeString(dir.resolve("MagicQ").resolve("magicq_patch.csv"), magicqCsv(fx));
                    written.add("MagicQ/magicq_patch.csv");
                }
            }
            if (kind == GDTF || kind == RIG) {
                Path d = dir.resolve("gdtf");
                Files.createDirectories(d);
                for (FixtureProfile p : types) { copy(RES + "gdtf/" + gdtfName(p), d.resolve(gdtfName(p))); written.add("gdtf/" + gdtfName(p)); }
            }
            if (kind == PROFILE_MA2 || kind == PROFILE_DOT2 || kind == PROFILE_MA3 || kind == PROFILE_MAGICQ || kind == QLC || kind == RIG)
                Files.writeString(dir.resolve("INSTALL - consoles.txt"), INSTALL);
            if (kind == MVR || kind == RIG) {
                Path f = dir.resolve(show_ + ".mvr");
                Files.write(f, mvr(show, fx));
                written.add(f.getFileName().toString());
            }
            if (kind == MODELS || kind == RIG) {
                Path d = dir.resolve("models");
                Files.createDirectories(d.resolve("ma3d"));
                for (FixtureProfile p : types) {
                    int i = index(p);
                    String id = IDS[i];
                    copy(RES + "models/" + id + ".glb", d.resolve(id + ".glb"));
                    copy(RES + "models/" + id + "_pivots.txt", d.resolve(id + "_pivots.txt"));
                    for (String part : new String[] {"base", "yoke", "head"}) {
                        if (resource(RES + "models/" + id + "_" + part + ".obj") == null) continue;
                        copy(RES + "models/" + id + "_" + part + ".obj", d.resolve(id + "_" + part + ".obj"));
                        copy(RES + "models/" + id + "_" + part + ".mtl", d.resolve(id + "_" + part + ".mtl"));
                    }
                    copy(RES + "models/ma3d/" + MA3D[i] + ".3ds", d.resolve("ma3d").resolve(MA3D[i] + ".3ds"));
                    copy(RES + "models/ma3d/" + MA3D[i] + ".import.png", d.resolve("ma3d").resolve(MA3D[i] + ".import.png"));
                    written.add("models/" + id + " (glb, obj, 3ds)");
                }
            }
            if (kind == QLC || kind == RIG) {
                Path d = dir.resolve("qlcplus");
                Files.createDirectories(d);
                for (FixtureProfile p : types) copy(RES + "qlcplus/" + qxfName(p), d.resolve(qxfName(p)));
                // rev. 25c: the gobo pictures the .qxf point to (Res1="ConcertStageCraft/<name>.png"). QLC+ resolves a relative picture
                // path in its SYSTEM gobo folder only (QLCCapability::loadXML: QLCFile::systemDirectory(GOBODIR)), not in the user folder
                Path gd = d.resolve("Gobos").resolve("ConcertStageCraft");
                Files.createDirectories(gd);
                for (String gname : MA_GOBOS) copy(RES + "ma_gobos/" + gname + ".png", gd.resolve(gname + ".png"));
                if (!fx.isEmpty()) Files.writeString(d.resolve(show_ + ".qxw"), qxw(show, fx));
                written.add("qlcplus (" + types.size() + " .qxf" + (fx.isEmpty() ? "" : " + workspace") + ")");
            }
            return "Exported to " + base.getParent().relativize(dir) + ": " + String.join(", ", written);
        } catch (IOException e) {
            return "Export failed: " + e.getMessage();
        }
    }

    /** Rev. 16i: where each software looks for fixture types (written with the console exports). */
    static final String INSTALL = String.join("\n",
        "CONCERT STAGE CRAFT - installing the fixture types in a lighting console",
        "",
        "dot2 / dot2 onPC: copy the folder \"USB stick/dot2\" to the root of a USB stick (USB:/dot2/library/*.xml).",
        "  dot2 onPC: run it as administrator and join a session (Setup > Sessions). Setup > Patch & Fixture Schedule > Add New Fixtures >",
        "  Select Other > Drive (bottom left) = the USB stick > Concert Stage Craft > type > mode > OK.",
        "  Without a stick: C:\\ProgramData\\MA Lighting Technologies\\dot2\\<version>\\library (hidden folder), drive Internal.",
        "  dot2 onPC outputs one universe (512 channels) without MA hardware. dot2 universe 1 = Art-Net 0:0 = CSC universe 0.",
        "grandMA2 / grandMA2 onPC: copy \"USB stick/gma2\" to the root of the stick (USB:/gma2/library/*.xml), then Setup > Patch Only >",
        "  Add New Fixtures > Select Other > Import > drive = the stick. Written for grandMA2 3.9.60.",
        "grandMA3 / grandMA3 onPC: copy \"USB stick/grandMA3\" to the root of the stick (USB:/grandMA3/gma3_library/fixturetypes/gdtf/*.gdtf),",
        "  then Patch > Fixture Types > Import > drive = the stick. Older grandMA3 versions read USB:/grandMA3/shared/lib_fixture_types/gdtf.",
        "  The gobo pictures: copy \"USB stick/dot2/gobos\" (or gma2/gobos) with the library; without a stick, copy its user_gobos folder into",
        "  C:\\ProgramData\\MA Lighting Technologies\\dot2\\<version>\\gobos (grandMA2: ...\\grandma\\gma2_V_<version>\\gobos), then import the types.",
        "ChamSys MagicQ (1.8.4.4 or later): MagicQ imports GDTF. Copy the .gdtf files of \"MagicQ/heads\" into the heads folder of MagicQ",
        "  (File > Open show folder > heads, or a USB stick on a console) and import them as described in the MagicQ manual, Head Editor >",
        "  Importing GDTF Files. Then Patch > View Chans > Import Heads > magicq_patch.csv patches every fixture (head number = CSC fixture",
        "  number, universe, address, channel count of the mode). The MVR export (Complete rig) also carries the positions for MagicVis.",
        "  MagicQ universe 1 = Art-Net universe 0 by default = CSC universe 0.",
        "QLC+: copy the .qxf files of \"qlcplus\" into the user fixtures folder, then restart QLC+:",
        "  Windows C:\\Users\\<you>\\QLC+\\Fixtures   macOS ~/Library/Application Support/QLC+/Fixtures   Linux ~/.qlcplus/fixtures",
        "  Gobo pictures: copy the folder \"qlcplus/Gobos/ConcertStageCraft\" into the Gobos folder of the QLC+ INSTALLATION (QLC+ looks for",
        "  them there only): Windows C:\\QLC+\\Gobos   macOS QLC+.app/Contents/Resources/Gobos   Linux /usr/share/qlcplus/gobos",
        "  QLC+ universe 1 outputs Art-Net universe 0 by default = CSC universe 0.",
        "");

    /** A safe file name (letters, digits, space, _ . ( ) -), never empty, never a path. */
    static String clean(String s) {
        String c = s.replaceAll("[^A-Za-z0-9 _.()\\-]", "").trim();
        while (c.startsWith(".")) c = c.substring(1);
        return c.isEmpty() ? "show" : c.length() > 64 ? c.substring(0, 64) : c;
    }

    static int index(FixtureProfile p) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(p.id)) return i;
        return 0;
    }

    static InputStream resource(String path) {
        return ExternalExport.class.getClassLoader().getResourceAsStream(path);
    }

    static void copy(String res, Path to) throws IOException {
        try (InputStream in = resource(res)) {
            if (in == null) throw new IOException("missing in the mod: " + res);
            Files.copy(in, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static byte[] read(String res) throws IOException {
        try (InputStream in = resource(res)) {
            if (in == null) throw new IOException("missing in the mod: " + res);
            return in.readAllBytes();
        }
    }

    // ------------------------------------------------------------ patch
    static String csv(Show show, List<Show.Patch> fx) {
        StringBuilder s = new StringBuilder("fid;name;type;mode;universe;address;channels;last_channel;shared_with;x;y;z;yaw;mount;invert_pan;invert_tilt;pan_offset;tilt_offset;groups\n");
        for (Show.Patch p : fx) {
            int foot = p.footprint();
            StringBuilder shared = new StringBuilder();
            for (Show.Patch q : show.patch)
                if (q != p && q.universe == p.universe && q.address == p.address && q.type.equals(p.type) && q.dmxMode == p.dmxMode && foot > 0)
                    shared.append(shared.length() == 0 ? "" : ",").append(q.fid);
            StringBuilder groups = new StringBuilder();
            for (Show.Group g : show.groups) for (int f : g.fids) if (f == p.fid) groups.append(groups.length() == 0 ? "" : ",").append(g.name.replace(';', ','));
            s.append(String.format(Locale.ROOT, "%d;%s;%s;%s;%d;%d;%d;%d;%s;%d;%d;%d;%.1f;%s;%b;%b;%.2f;%.2f;%s\n", p.fid, p.name.replace(';', ','),
                p.profile().name, DmxMode.NAMES[p.dmxMode], p.universe, p.address, foot, foot == 0 ? 0 : p.address + foot - 1, shared,
                p.x, p.y, p.z, p.yaw, p.mount == 1 ? "hung" : "standing", p.invertPan, p.invertTilt, p.panOffset, p.tiltOffset, groups));
        }
        boolean matrix = fx.stream().anyMatch(p -> p.dmxMode == DmxMode.MATRIX);
        if (matrix) s.append(String.format(Locale.ROOT, "# Virtual Matrix: universe %d, address %d-%d (16 channels)\n", show.external.matrixUniverse,
            show.external.matrixAddress, show.external.matrixAddress + VirtualMatrix.CHANNELS - 1));
        for (AutoPatch.Conflict c : AutoPatch.conflicts(show)) s.append("# WARNING: ").append(c.text()).append('\n');
        return s.toString();
    }

    /**
     * Rev. 25b: the patch for MagicQ (Patch > View Chans > Import Heads): column headings from the MagicQ manual (Supported Column
     * Headings), universe 1-based as in MagicQ, the channel count to pick the mode of the imported GDTF.
     */
    static String magicqCsv(List<Show.Patch> fx) {
        StringBuilder s = new StringBuilder("Head No,Name,Manufacturer,Head Type,Mode,Universe,DMX,Num Of Chans\n");
        for (Show.Patch p : fx) {
            int foot = p.footprint();
            if (foot == 0) continue;                                       // Virtual Matrix mode: nothing to patch
            s.append(String.format(Locale.ROOT, "%d,%s,%s,%s,%s,%d,%d,%d\n", p.fid, p.name.replace(',', ' '), MANUFACTURER, p.profile().name,
                DmxMode.NAMES[p.dmxMode], p.universe + 1, p.address, foot));
        }
        return s.toString();
    }

    static String json(Show show, List<Show.Patch> fx) {
        StringBuilder s = new StringBuilder("{\n  \"show\": \"").append(esc(show.name)).append("\",\n  \"generator\": \"Concert Stage Craft 1.2.1\",\n");
        s.append("  \"matrix\": {\"universe\": ").append(show.external.matrixUniverse).append(", \"address\": ").append(show.external.matrixAddress)
            .append(", \"order\": ").append(show.external.matrixOrder).append("},\n  \"fixtures\": [\n");
        for (int i = 0; i < fx.size(); i++) {
            Show.Patch p = fx.get(i);
            s.append(String.format(Locale.ROOT, "    {\"fid\": %d, \"name\": \"%s\", \"type\": \"%s\", \"gdtf\": \"%s\", \"mode\": \"%s\", \"universe\": %d, \"address\": %d, \"channels\": %d, "
                    + "\"block\": [%d, %d, %d], \"yaw\": %.1f, \"mount\": \"%s\", \"invert_pan\": %b, \"invert_tilt\": %b, \"pan_offset\": %.2f, \"tilt_offset\": %.2f}%s\n",
                p.fid, esc(p.name), p.type, esc(gdtfName(p.profile())), DmxMode.NAMES[p.dmxMode], p.universe, p.address, p.footprint(), p.x, p.y, p.z, p.yaw,
                p.mount == 1 ? "hung" : "standing", p.invertPan, p.invertTilt, p.panOffset, p.tiltOffset, i + 1 < fx.size() ? "," : ""));
        }
        return s.append("  ]\n}\n").toString();
    }

    // ------------------------------------------------------------ MVR
    /** World matrix of a fixture in MVR space (rotation columns u, v, w; translation o in mm). */
    static double[][] mvrMatrix(Show.Patch p) {
        // Minecraft: world = translate * rotY(yaw) * rotX(pitch) * rotZ(roll) * (hung ? rotZ(180) : I) * local (1.2.1: free pose)
        double[][] m = com.concertstage.light.core.FixturePose.rotation(p);
        double[][] c = {{1, 0, 0}, {0, 0, -1}, {0, 1, 0}};           // Minecraft -> MVR
        double[][] ct = {{1, 0, 0}, {0, 0, 1}, {0, -1, 0}};
        double[][] r = mul(mul(c, m), ct);
        double[] org = com.concertstage.light.core.FixturePose.origin(p);
        double ox = org[0], oy = org[1], oz = org[2];
        double[] o = {ox * 1000, -oz * 1000, oy * 1000};
        return new double[][] {{r[0][0], r[1][0], r[2][0]}, {r[0][1], r[1][1], r[2][1]}, {r[0][2], r[1][2], r[2][2]}, o};
    }

    static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    static String uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().toUpperCase(Locale.ROOT);
    }

    static byte[] mvr(Show show, List<Show.Patch> fx) throws IOException {
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n");
        x.append("<GeneralSceneDescription verMajor=\"1\" verMinor=\"6\" provider=\"Concert Stage Craft\" providerVersion=\"1.2.1\">\n");
        x.append("  <Scene>\n    <Layers>\n");
        x.append("      <Layer name=\"").append(esc(show.name)).append(" - lighting\" uuid=\"").append(uuid("layer:" + show.id)).append("\">\n        <ChildList>\n");
        Set<FixtureProfile> types = new LinkedHashSet<>();
        for (Show.Patch p : fx) {
            types.add(p.profile());
            double[][] m = mvrMatrix(p);
            String mat = String.format(Locale.ROOT, "{%.6f,%.6f,%.6f}{%.6f,%.6f,%.6f}{%.6f,%.6f,%.6f}{%.3f,%.3f,%.3f}",
                m[0][0], m[0][1], m[0][2], m[1][0], m[1][1], m[1][2], m[2][0], m[2][1], m[2][2], m[3][0], m[3][1], m[3][2]);
            x.append("          <Fixture name=\"").append(esc(p.name)).append("\" uuid=\"").append(uuid("fixture:" + show.id + ":" + p.fid)).append("\">\n");
            x.append("            <Matrix>").append(mat).append("</Matrix>\n");
            x.append("            <GDTFSpec>").append(esc(gdtfName(p.profile()))).append("</GDTFSpec>\n");
            int mode = p.dmxMode == DmxMode.MATRIX ? DmxMode.ULTRA : p.dmxMode;     // a matrix member has no DMX mode of its own
            x.append("            <GDTFMode>").append(DmxMode.NAMES[mode]).append("</GDTFMode>\n");
            if (p.dmxMode != DmxMode.MATRIX) {
                // rev. 16h: the Art-Net Port-Address that feeds this CSC universe (direct by default: universe 0 = Port-Address 0); MVR
                // counts universes from 1, so Port-Address P is MVR universe P + 1 (what a console shows as universe P + 1)
                int abs = artnetPort(p.universe) * 512 + p.address;
                x.append("            <Addresses>\n              <Address break=\"0\">").append(abs).append("</Address>\n            </Addresses>\n");
            }
            x.append("            <FixtureID>").append(p.fid).append("</FixtureID>\n");
            x.append("            <FixtureIDNumeric>").append(p.fid).append("</FixtureIDNumeric>\n");
            x.append("            <UnitNumber>").append(p.fid).append("</UnitNumber>\n");
            if (p.invertPan) x.append("            <DMXInvertPan>true</DMXInvertPan>\n");
            if (p.invertTilt) x.append("            <DMXInvertTilt>true</DMXInvertTilt>\n");
            x.append("            <CastShadow>false</CastShadow>\n");
            x.append("          </Fixture>\n");
        }
        x.append("        </ChildList>\n      </Layer>\n    </Layers>\n  </Scene>\n</GeneralSceneDescription>\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("GeneralSceneDescription.xml"));
            z.write(x.toString().getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            for (FixtureProfile p : types) {
                z.putNextEntry(new ZipEntry(gdtfName(p)));
                z.write(read(RES + "gdtf/" + gdtfName(p)));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------ QLC+ workspace
    static String qxw(Show show, List<Show.Patch> fx) {
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE Workspace>\n<Workspace xmlns=\"http://www.qlcplus.org/Workspace\" CurrentWindow=\"FixtureManager\">\n");
        x.append(" <Creator>\n  <Name>Q Light Controller Plus</Name>\n  <Version>4.12.0</Version>\n  <Author>Concert Stage Craft 1.2.1</Author>\n </Creator>\n <Engine>\n  <InputOutputMap>\n");
        Set<Integer> us = new java.util.TreeSet<>();
        for (Show.Patch p : fx) if (p.dmxMode != DmxMode.MATRIX) us.add(p.universe);
        // rev. 16h: a QLC+ universe ID outputs Art-Net to the Port-Address of the same number by default: ID = the Port-Address of the CSC universe
        for (int u : us) x.append("   <Universe Name=\"CSC universe ").append(u).append("\" ID=\"").append(artnetPort(u)).append("\"/>\n");
        x.append("  </InputOutputMap>\n");
        int id = 0;
        for (Show.Patch p : fx) {
            if (p.dmxMode == DmxMode.MATRIX) continue;
            x.append("  <Fixture>\n   <Manufacturer>").append(MANUFACTURER).append("</Manufacturer>\n   <Model>").append(esc(p.profile().name.replace("CSC ", "")))
                .append("</Model>\n   <Mode>").append(DmxMode.NAMES[p.dmxMode]).append("</Mode>\n   <ID>").append(id++).append("</ID>\n   <Name>").append(esc(p.name))
                .append("</Name>\n   <Universe>").append(artnetPort(p.universe)).append("</Universe>\n   <Address>").append(p.address - 1).append("</Address>\n   <Channels>")
                .append(p.footprint()).append("</Channels>\n  </Fixture>\n");
        }
        x.append(" </Engine>\n</Workspace>\n");
        return x.toString();
    }

    /** Rev. 16h: Art-Net Port-Address feeding a CSC universe with the current settings (the same number when none is mapped to it). */
    static int artnetPort(int universe) {
        int pa = com.concertstage.light.dmx.ArtNetSettings.current.portAddressOf(universe);
        return pa == com.concertstage.light.dmx.ArtNetSettings.NONE ? Math.max(0, universe) : pa;
    }

    /** Rev. 16h: CSC universe fed by an Art-Net Port-Address with the current settings (the same number when it is not received). */
    static int cscOfPort(int pa) {
        int u = com.concertstage.light.dmx.ArtNetSettings.current.cscUniverse(pa);
        return u == com.concertstage.light.dmx.ArtNetSettings.NONE ? pa : u;
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------ imports
    /** Result of an import: the commands to apply (null if nothing), and what happened. */
    record Imported(byte[] command, String report) {}

    /** Patch CSV (as exported: fid;...;mode;universe;address;...) from {@code concertstage/import/<file>}. */
    static Imported importPatch(Path base, Show show, String file) {
        try {
            Path f = importFile(base, file, ".csv");
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return new Imported(null, "Empty file");
            String[] head = lines.get(0).split(";");
            int cFid = col(head, "fid"), cMode = col(head, "mode"), cU = col(head, "universe"), cA = col(head, "address");
            if (cFid < 0 || cU < 0 || cA < 0) return new Imported(null, "Not a CSC patch file (columns fid, universe, address needed)");
            List<int[]> plan = new ArrayList<>();
            int skipped = 0;
            for (String l : lines.subList(1, lines.size())) {
                if (l.isBlank() || l.startsWith("#")) continue;
                String[] c = l.split(";", -1);
                try {
                    int fid = Integer.parseInt(c[cFid].trim()), u = Integer.parseInt(c[cU].trim()), a = Integer.parseInt(c[cA].trim());
                    int mode = cMode < 0 ? 0 : modeIndex(c[cMode].trim());
                    if (show.fixture(fid) == null || !com.concertstage.light.dmx.ArtNetSettings.validUniverse(u) || a < 1 || a > 512 || mode < 0) { skipped++; continue; }
                    plan.add(new int[] {fid, u, a, mode});
                } catch (RuntimeException e) { skipped++; }
            }
            return bulk(plan, skipped, "patch file " + f.getFileName());
        } catch (IOException e) {
            return new Imported(null, "Could not read " + file + ": " + e.getMessage());
        }
    }

    /** MVR (from CSC or another program): fixtures matched by Fixture ID to the fixture numbers of the desk; address and mode taken. */
    static Imported importMvr(Path base, Show show, String file) {
        try {
            Path f = importFile(base, file, ".mvr");
            byte[] xml = null;
            try (ZipInputStream z = new ZipInputStream(Files.newInputStream(f))) {
                for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                    if (e.getName().equals("GeneralSceneDescription.xml")) { xml = z.readNBytes(32 << 20); break; }
                }
            }
            if (xml == null) return new Imported(null, "Not an MVR file (no GeneralSceneDescription.xml)");
            var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            var doc = dbf.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
            var list = doc.getElementsByTagName("Fixture");
            List<int[]> plan = new ArrayList<>();
            int skipped = 0;
            for (int i = 0; i < list.getLength(); i++) {
                var el = (org.w3c.dom.Element) list.item(i);
                String id = text(el, "FixtureIDNumeric");
                if (id.isEmpty()) id = text(el, "FixtureID");
                String mode = text(el, "GDTFMode"), addr = text(el, "Address"), spec = text(el, "GDTFSpec");
                try {
                    int fid = Integer.parseInt(id.replaceAll("[^0-9]", ""));
                    Show.Patch p = show.fixture(fid);
                    if (p == null || addr.isEmpty() || !spec.contains(p.profile().name)) { skipped++; continue; }
                    int abs = Integer.parseInt(addr.contains(".") ? String.valueOf((Integer.parseInt(addr.split("\\.")[0]) - 1) * 512 + Integer.parseInt(addr.split("\\.")[1])) : addr.trim());
                    int m = modeIndex(mode);
                    // rev. 16h: MVR universe N = Art-Net Port-Address N - 1, then the CSC universe of that Port-Address (direct by default)
                    plan.add(new int[] {fid, cscOfPort((abs - 1) / 512), (abs - 1) % 512 + 1, m < 0 ? p.dmxMode : m});
                } catch (RuntimeException e) { skipped++; }
            }
            return bulk(plan, skipped, "MVR " + f.getFileName());
        } catch (Exception e) {
            return new Imported(null, "Could not read " + file + ": " + e.getMessage());
        }
    }

    private static Imported bulk(List<int[]> plan, int skipped, String what) {
        if (plan.isEmpty()) return new Imported(null, "Nothing matched in " + what + " (" + skipped + " lines / fixtures skipped)");
        Bytes.Writer w = Commands.op(Commands.PATCH_BULK).varInt(plan.size());
        for (int[] e : plan) w.varInt(e[0]).varInt(e[1]).varInt(e[2]).varInt(e[3]);
        return new Imported(w.toBytes(), "Imported " + plan.size() + " fixtures from " + what + (skipped > 0 ? ", " + skipped + " skipped (unknown fixture or address)" : ""));
    }

    static Path importFile(Path base, String file, String ext) throws IOException {
        String n = clean(file);
        if (!n.toLowerCase(Locale.ROOT).endsWith(ext)) n += ext;
        Path dir = base.resolve("import");
        Files.createDirectories(dir);
        Path f = dir.resolve(n).normalize();
        if (!f.startsWith(dir) || !Files.isRegularFile(f)) throw new IOException("no file " + n + " in " + dir);
        return f;
    }

    static int col(String[] head, String name) {
        for (int i = 0; i < head.length; i++) if (head[i].trim().equalsIgnoreCase(name)) return i;
        return -1;
    }

    static int modeIndex(String name) {
        for (int i = 0; i < DmxMode.NAMES.length; i++) if (DmxMode.NAMES[i].equalsIgnoreCase(name)) return i;
        if (name.equalsIgnoreCase("ultra")) return DmxMode.ULTRA;
        return -1;
    }

    static String text(org.w3c.dom.Element el, String tag) {
        var l = el.getElementsByTagName(tag);
        return l.getLength() == 0 ? "" : l.item(0).getTextContent().trim();
    }
}
