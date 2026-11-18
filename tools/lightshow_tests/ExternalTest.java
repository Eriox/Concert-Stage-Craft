import com.concertstage.light.core.*;
import com.concertstage.light.dmx.*;
import java.util.*;

/**
 * Off-game tests of CSC External Show Control (1.2.0): DMX modes, Art-Net / sACN decoding and validation, merging of sources, DMX to
 * fixture values through the engine (16-bit, transforms, shared addresses, colour macro, cells), control modes and their cross-fade,
 * AUTO PATCH 1 UNIVERSE, Virtual Matrix determinism, .cscshow recording / file / player (seek, loop, damage), MIDI timecode, OSC,
 * show / live formats and timings.
 * Usage: see tools/lightshow_tests/run_external.sh
 */
public class ExternalTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  ok   " + what); }
        else { failed++; System.out.println("  FAIL " + what); }
    }

    static void near(double a, double b, double tol, String what) {
        check(Math.abs(a - b) <= tol, what + String.format(Locale.ROOT, " (%.4f ~ %.4f)", a, b));
    }

    public static void main(String[] args) throws Exception {
        modes();
        packets();
        merge();
        decodeEngine();
        controlModes();
        autoPatch();
        compact8();
        vortexModes();
        matrix();
        recording();
        timecode();
        formats();
        commands();
        universeZero();
        udp();
        perf();
        System.out.println();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static final FixtureProfile[] ALL = {FixtureProfile.VORTEX_X1, FixtureProfile.LUMORA_W7, FixtureProfile.LUMORA_W19, FixtureProfile.PULSESTRIKE_M};

    static Show rig(int vortex, int w7, int w19, int pulse) {
        Show s = new Show();
        s.id = "ext";
        Live l = new Live();
        int fid = 1;
        int[] counts = {vortex, w7, w19, pulse};
        for (int t = 0; t < 4; t++) for (int i = 0; i < counts[t]; i++) {
            byte[] c = Commands.op(Commands.PATCH_ADD).varInt(fid).str(ALL[t].id).str("").sInt(fid * 2).sInt(70).sInt(-20).f32(0).varInt(1).toBytes();
            Commands.apply(s, l, c, 0, null);
            fid++;
        }
        return s;
    }

    // ------------------------------------------------------------------------------------------------------------------ modes
    static void modes() {
        System.out.println("DMX modes");
        // rev. 17: Compact in 8 channels at most; rev. 19: VORTEX Ultra + strobe + gobo; rev. 21: PULSESTRIKE M in three light zones (Full
        // 87, Compact 12), its former layouts kept as "before rev. 21" (82 / 8) for the shows saved before
        // rev. 24: PULSESTRIKE M Compact in two instances (LED bars, white bar): 9 channels; the 12-channel one kept as COMPACT_Z3
        int[][] expect = {{18, 8, 6}, {70, 7, 4}, {124, 7, 4}, {87, 9, 4}};
        check(DmxMode.of(FixtureProfile.PULSESTRIKE_M, DmxMode.COMPACT_Z3).footprint() == 12, "PULSESTRIKE M Compact of rev. 21 to 23 kept (12 channels)");
        check(DmxMode.of(FixtureProfile.PULSESTRIKE_M, DmxMode.FULL_V1).footprint() == 82 && DmxMode.of(FixtureProfile.PULSESTRIKE_M, DmxMode.COMPACT_V1).footprint() == 8,
            "PULSESTRIKE M layouts before rev. 21 unchanged (82 / 8 channels)");
        for (int t = 0; t < 4; t++) {
            FixtureProfile p = ALL[t];
            for (int m = 0; m < 3; m++) {
                DmxMode d = DmxMode.of(p, m);
                check(d.footprint() == expect[t][m], p.name + " " + d.name() + ": " + d.footprint() + " channels");
                Set<String> names = new HashSet<>();
                for (DmxMode.Channel c : d.channels) names.add(c.name());
                check(names.size() == d.channels.length, p.name + " " + d.name() + ": channel names unique");
                for (DmxMode.Channel c : d.channels) if (c.attr() != null && !p.has(c.attr())) check(false, p.name + " " + d.name() + " claims " + c.attr());
            }
            check(DmxMode.of(p, DmxMode.FULL).missing().isEmpty(), p.name + " Full reaches every function");
            check(!DmxMode.of(p, DmxMode.ULTRA).missing().isEmpty(), p.name + " Ultra Compact lists what it cannot reach");
            check(DmxMode.of(p, DmxMode.MATRIX).footprint() == 0, p.name + " Virtual Matrix mode uses no channel");
        }
        check(FixtureProfile.PULSESTRIKE_M.has(Attr.ZOOM) && FixtureProfile.PULSESTRIKE_M.beamAngle(0) == 20f && FixtureProfile.PULSESTRIKE_M.beamAngle(1) == 70f,
            "PULSESTRIKE M has a zoom 20-70 degrees");
        check(FixtureProfile.PULSESTRIKE_M.home(Attr.ZOOM) == 1f, "PULSESTRIKE M rests at its widest zoom (70 degrees, as before)");
        int worst = 0;
        for (FixtureProfile p : ALL) for (Attr a : p.attrs) for (int v = 0; v <= 255; v++) {
            float x = DmxMode.decode(p, a, v, false);
            int back = DmxMode.encode(p, a, x, false);
            float x2 = DmxMode.decode(p, a, back, false);
            if (Math.abs(x - x2) > 1e-6) worst++;
        }
        check(worst == 0, "8-bit decode(encode(decode(v))) is stable for every attribute and value");
        near(DmxMode.decode(FixtureProfile.VORTEX_X1, Attr.PAN, 32768, true), 0.5, 1e-4, "pan 16-bit 32768 = centre");
        check(DmxMode.decode(FixtureProfile.VORTEX_X1, Attr.GOBO, 3 * 28 + 5, false) == 3f / 8, "gobo slot 3 from 84..111");
        check(DmxMode.decode(FixtureProfile.VORTEX_X1, Attr.STROBE, 7, false) == 0f && DmxMode.decode(FixtureProfile.VORTEX_X1, Attr.STROBE, 255, false) == 1f, "strobe 0-7 open, 255 fastest");
        check(DmxMode.decode(FixtureProfile.VORTEX_X1, Attr.GOBO_ROT, 128, false) == 0.5f, "gobo rotation 124-131 stopped");
        check(DmxMode.macroColor(0) == 0xFFFFFF && DmxMode.macroColor(16) == 0xFF0000 && DmxMode.macroColor(255) == DmxMode.MACRO_COLORS[16], "colour macro steps of 15");
    }

    // ------------------------------------------------------------------------------------------------------------------ packets
    static void packets() {
        System.out.println("Art-Net / sACN");
        byte[] d = new byte[512];
        for (int i = 0; i < 512; i++) d[i] = (byte) i;
        byte[] a = DmxPackets.artDmx(0x0103, 7, d, 512);
        DmxPackets.Frame f = DmxPackets.parse(a, a.length, "10.0.0.5");
        check(f != null && f.protocol() == DmxPackets.PROTO_ARTNET && f.universe() == 0x0103 && f.length() == 512 && f.data()[300] == (byte) 300 && f.sequence() == 7,
            "ArtDmx: port-address, length, data, sequence");
        byte[] bad = a.clone(); bad[16] = 0x7F;
        check(DmxPackets.parse(bad, bad.length, "x") == null, "ArtDmx with a length past the packet rejected");
        byte[] old = a.clone(); old[11] = 9;
        check(DmxPackets.parse(old, old.length, "x") == null, "Art-Net protocol version < 14 rejected");
        byte[] cid = new byte[16]; cid[3] = 42;
        byte[] s = DmxPackets.sacnData(5, 150, 9, false, cid, "grandMA3", d, 512);
        DmxPackets.Frame g = DmxPackets.parse(s, s.length, "x");
        check(g != null && g.protocol() == DmxPackets.PROTO_SACN && g.universe() == 5 && g.priority() == 150 && "grandMA3".equals(g.sourceName())
            && g.data()[511] == (byte) 511, "sACN: universe, priority, source name, data");
        byte[] s2 = s.clone(); s2[112] = (byte) 0x80;
        check(DmxPackets.parse(s2, s2.length, "x") == null, "sACN preview data not used for output");
        byte[] s3 = s.clone(); s3[108] = (byte) 201;
        check(DmxPackets.parse(s3, s3.length, "x") == null, "sACN priority over 200 rejected");
        byte[] s4 = s.clone(); s4[21] = 5;
        check(DmxPackets.parse(s4, s4.length, "x") == null, "sACN wrong root vector rejected");
        check(DmxPackets.parse(s, s.length - 10, "x") == null, "sACN truncated packet rejected");
        byte[] s5 = DmxPackets.sacnData(5, 100, 10, true, cid, "grandMA3", d, 512);
        DmxPackets.Frame t = DmxPackets.parse(s5, s5.length, "x");
        check(t != null && t.terminated(), "sACN stream terminated read");
        check(DmxPackets.parse(new byte[40], 40, "x") == null, "garbage rejected");
        check(DmxPackets.sacnGroup(300).equals("239.255.1.44"), "sACN multicast group of universe 300");
    }

    // ------------------------------------------------------------------------------------------------------------------ merge
    static void merge() {
        System.out.println("Merging sources");
        DmxMerge m = new DmxMerge();
        byte[] cidA = new byte[16], cidB = new byte[16];
        cidA[0] = 1; cidB[0] = 2;
        byte[] a = new byte[512], b = new byte[512];
        a[0] = 100; a[1] = 10; b[0] = 50; b[1] = (byte) 200;
        m.receive(DmxPackets.parse(DmxPackets.sacnData(1, 100, 1, false, cidA, "A", a, 512), 638, "x"), 0);
        m.receive(DmxPackets.parse(DmxPackets.sacnData(1, 100, 1, false, cidB, "B", b, 512), 638, "x"), 0);
        byte[] out = new byte[512];
        m.snapshot(1, out);
        check((out[0] & 255) == 100 && (out[1] & 255) == 200, "same priority: highest value per channel (HTP)");
        m.receive(DmxPackets.parse(DmxPackets.sacnData(1, 120, 2, false, cidB, "B", b, 512), 638, "x"), 10);
        m.snapshot(1, out);
        check((out[0] & 255) == 50 && (out[1] & 255) == 200, "higher priority source wins alone");
        m.receive(DmxPackets.parse(DmxPackets.sacnData(1, 120, 1, false, cidB, "B", a, 512), 638, "x"), 20);
        m.snapshot(1, out);
        check((out[0] & 255) == 50, "late (out of order) packet dropped");
        m.expire(10 + DmxMerge.TIMEOUT_MS + 1);
        check(m.lost(1), "sources gone after the timeout: universe lost");
        m.snapshot(1, out);
        check((out[0] & 255) == 50, "lost universe keeps its last values (hold)");
        DmxMerge n = new DmxMerge();
        int u = n.receive(DmxPackets.parse(DmxPackets.artDmx(0, 1, a, 512), 530, "1.2.3.4"), 0);
        check(u == 0, "Art-Net Port-Address 0 is CSC universe 0 (rev. 16h: no +1)");
        n.receive(DmxPackets.parse(DmxPackets.artDmx(1, 1, a, 512), 530, "1.2.3.4"), 0);
        n.receive(DmxPackets.parse(DmxPackets.sacnData(1, 100, 1, true, cidA, "A", a, 512), 638, "x"), 5);
        check(!n.lost(1), "terminating an unrelated sACN source keeps the Art-Net one");
    }

    // ------------------------------------------------------------------------------------------------------------------ decode
    static DmxData data(int universe, int[] addrValue) {
        DmxData d = new DmxData();
        byte[] u = new byte[512];
        for (int i = 0; i + 1 < addrValue.length; i += 2) u[addrValue[i] - 1] = (byte) addrValue[i + 1];
        d.set(universe, u);
        return d;
    }

    static void decodeEngine() {
        System.out.println("DMX to fixtures (engine)");
        Show s = rig(2, 1, 0, 1);
        Live l = new Live();
        Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(Live.EXTERNAL).toBytes(), 0, null);
        Show.Patch v1 = s.fixture(1), v2 = s.fixture(2), w7 = s.fixture(3), ps = s.fixture(4);
        v1.universe = 1; v1.address = 1; v1.dmxMode = DmxMode.FULL;
        v2.universe = 1; v2.address = 1; v2.dmxMode = DmxMode.FULL;               // shares with v1
        w7.universe = 1; w7.address = 101; w7.dmxMode = DmxMode.FULL;
        ps.universe = 1; ps.address = 301; ps.dmxMode = DmxMode.ULTRA;
        v2.invertPan = true;
        DmxMode vm = DmxMode.of(FixtureProfile.VORTEX_X1, DmxMode.FULL);
        int pan = vm.channelsOf(Attr.PAN)[0], panF = vm.channelsOf(Attr.PAN)[1], dim = vm.channelsOf(Attr.DIMMER)[0], dimF = vm.channelsOf(Attr.DIMMER)[1], gobo = vm.channelsOf(Attr.GOBO)[0];
        DmxMode wm = DmxMode.of(FixtureProfile.LUMORA_W7, DmxMode.FULL);
        int cell5r = -1;
        for (int i = 0; i < wm.channels.length; i++) if (wm.channels[i].kind() == DmxMode.Kind.ZONE_R && wm.channels[i].zone() == 4) cell5r = i + 1;
        int wRed = wm.channelsOf(Attr.RED)[0], wDim = wm.channelsOf(Attr.DIMMER)[0];
        DmxMode pm = DmxMode.of(FixtureProfile.PULSESTRIKE_M, DmxMode.ULTRA);
        check(pm.channels[2].kind() == DmxMode.Kind.COLOR_MACRO, "PULSESTRIKE Ultra: tilt, dimmer, colour macro, strobe");
        DmxData d = data(1, new int[] {pan, 0xC0, panF, 0x00, dim, 255, dimF, 255, gobo, 2 * 28 + 1,
            100 + wDim, 255, 100 + wRed, 0, 100 + wm.channelsOf(Attr.GREEN)[0], 0, 100 + wm.channelsOf(Attr.BLUE)[0], 0, 100 + cell5r, 255,
            300 + pm.channelsOf(Attr.DIMMER)[0], 255, 300 + 3, 7 * 15 + 3});
        Engine e = new Engine();
        Map<Integer, Engine.Output> o = e.evaluate(s, l, 5000, Engine.Music.NONE, d);
        near(o.get(1).get(Attr.PAN), 0xC000 / 65535.0, 1e-4, "16-bit pan");
        near(o.get(1).get(Attr.DIMMER), 1.0, 1e-6, "dimmer full");
        near(o.get(1).get(Attr.GOBO), 2 / 8.0, 1e-6, "gobo 2");
        near(o.get(2).get(Attr.PAN), 1 - 0xC000 / 65535.0, 1e-4, "shared address, pan inverted locally");
        check(o.get(2).get(Attr.GOBO) == o.get(1).get(Attr.GOBO), "shared address: same values");
        float[] z = o.get(3).zones;
        check(z[4 * 4] > 0.99f && z[4 * 4 + 1] < 0.01f && z[0] < 0.01f, "W7 cell 5 red by its own channel, the others dark (colour 0)");
        check(o.get(4).beamG > 0.2f && o.get(4).beamR < 0.01f && o.get(4).beamB < 0.01f, "PULSESTRIKE Ultra Compact: colour macro 7 = green");
        v1.panOffset = 27f;
        o = e.evaluate(s, l, 5000, Engine.Music.NONE, d);
        near(o.get(1).get(Attr.PAN), 0xC000 / 65535.0 + 27f / 540f, 1e-4, "pan offset 27 degrees");
        l.blackout = true;
        o = e.evaluate(s, l, 5000, Engine.Music.NONE, d);
        check(o.get(1).get(Attr.DIMMER) == 0f, "blackout still applies to DMX control");
        l.blackout = false;
        l.noStrobe = true;
        d.set(1, bytesWith(d.get(1), vm.channelsOf(Attr.STROBE)[0], 255));
        o = e.evaluate(s, l, 5000, Engine.Music.NONE, d);
        check(o.get(1).strobeHz == 0f, "NO STROBE still applies to DMX control");
        l.noStrobe = false;
        // a fixture across the end of the universe: what is missing stays home, nothing breaks
        v1.address = 505;
        o = e.evaluate(s, l, 5000, Engine.Music.NONE, d);
        check(o.get(1) != null, "fixture patched past channel 512 does not break the engine");
    }

    static byte[] bytesWith(byte[] src, int addr, int val) {
        byte[] b = src.clone();
        b[addr - 1] = (byte) val;
        return b;
    }

    static void controlModes() {
        System.out.println("Control modes");
        Show s = rig(1, 0, 0, 0);
        Live l = new Live();
        Engine e = new Engine();
        Commands.apply(s, l, Commands.op(Commands.P_SET).varInt(1).varInt(1).varInt(Attr.DIMMER.ordinal()).unit(0.2f).toBytes(), 0, null);
        DmxMode vm = DmxMode.of(FixtureProfile.VORTEX_X1, 0);
        DmxData d = data(s.fixture(1).universe, new int[] {vm.channelsOf(Attr.DIMMER)[0], 255, vm.channelsOf(Attr.DIMMER)[1], 255});   // rev. 16h: patched on universe 0
        near(e.evaluate(s, l, 0, Engine.Music.NONE, d).get(1).get(Attr.DIMMER), 0.2, 1e-3, "AutoShow / internal: programmer drives (DMX ignored)");
        Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(Live.EXTERNAL).toBytes(), 1000, null);
        near(e.evaluate(s, l, 1500, Engine.Music.NONE, d).get(1).get(Attr.DIMMER), 0.6, 1e-3, "switch to External: half way after 0.5 s");
        near(e.evaluate(s, l, 2100, Engine.Music.NONE, d).get(1).get(Attr.DIMMER), 1.0, 1e-3, "External after the fade: DMX only");
        Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(Live.PLAYBACK).toBytes(), 3000, null);
        near(e.evaluate(s, l, 3100, Engine.Music.NONE, d).get(1).get(Attr.DIMMER), 1.0, 1e-3, "External to Playback: both DMX, no fade");
        Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(Live.INTERNAL).toBytes(), 4000, null);
        near(e.evaluate(s, l, 5100, Engine.Music.NONE, d).get(1).get(Attr.DIMMER), 0.2, 1e-3, "back to Internal: desk values");
        check(!l.autoEnabled, "Internal mode keeps the AutoShow off");
        check(Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(9).toBytes(), 0, null) == Commands.REJECTED, "unknown mode rejected");
        check(Commands.playbackOnly(Commands.CONTROL_MODE), "mode switch allowed to playback operators");
    }

    // ------------------------------------------------------------------------------------------------------------------ rev. 17
    /** Rev. 17: Compact in 8 channels at most (64 fixtures per universe), every function of the former Compact kept, every gobo added. */
    static void compact8() {
        System.out.println("Compact 8 channels (rev. 17)");
        for (FixtureProfile p : FixtureProfile.all()) {
            DmxMode c = DmxMode.of(p, DmxMode.COMPACT);
            if (p.hasSections()) {
                // rev. 21: the zone fixtures — Compact carries the three zones (12 channels), checked in ZoneTest (tools/rev21); their
                // Compact before rev. 21 keeps the rev. 17 contract
                // rev. 24: two instances (LED bars, white bar), tools/rev24; the three-zone Compact of rev. 21 is COMPACT_Z3
                check(c.footprint() == (p.has(Attr.ZOOM) ? 9 : 8), p.name + " Compact in two instances: " + c.footprint() + " channels");
                check(DmxMode.of(p, DmxMode.COMPACT_Z3).footprint() == (p.has(Attr.ZOOM) ? 12 : 11), p.name + " Compact of rev. 21 to 23 kept");
                c = DmxMode.of(p, DmxMode.COMPACT_V1);
            }
            check(c.footprint() <= DmxMode.COMPACT_MAX, p.name + " Compact " + c.footprint() + " <= 8 channels");
            check(512 / c.footprint() >= 64, p.name + ": " + 512 / c.footprint() + " fixtures in one universe in Compact");
            // what the former Compact reached is still reached
            java.util.EnumSet<Attr> reached = java.util.EnumSet.noneOf(Attr.class);
            for (DmxMode.Channel ch : c.channels) reached.addAll(DmxMode.covers(p, ch));
            for (Attr a : new Attr[] {Attr.PAN, Attr.TILT, Attr.DIMMER, Attr.STROBE, Attr.RED, Attr.GREEN, Attr.BLUE, Attr.WHITE, Attr.CTO, Attr.ZOOM, Attr.IRIS})
                if (p.has(a) && !(p.whiteZones > 0 && p.rgbZones == 0 && a != Attr.DIMMER && a != Attr.STROBE && a != Attr.WHITE && a != Attr.CTO))
                    check(reached.contains(a), p.name + " Compact still reaches " + a);
            for (Attr a : new Attr[] {Attr.GOBO, Attr.GOBO_ROT, Attr.GOBO_2, Attr.GOBO_2_ROT})
                if (p.has(a)) check(reached.contains(a), p.name + " Compact reaches " + a + " (every gobo)");
            if (p == FixtureProfile.VORTEX_X1) check(reached.contains(Attr.FOCUS), "VORTEX Compact keeps its focus");
            // the ranges of a packed channel cover 0..255 without a gap
            for (DmxMode.Channel ch : c.channels) {
                List<DmxMode.Range> rs = DmxMode.ranges(p, ch);
                if (rs.isEmpty()) continue;
                int next = 0;
                boolean ok = true;
                for (DmxMode.Range r : rs) { if (r.lo() != next || r.hi() < r.lo()) ok = false; next = r.hi() + 1; }
                check(ok && next == 256, p.name + " " + ch.name() + ": ranges cover 0-255");
            }
        }
        // 64 VORTEX in Compact on one universe
        Show s = rig(64, 0, 0, 0);
        AutoPatch.Plan p = AutoPatch.plan(s, 1, DmxMode.COMPACT, AutoPatch.SHARE_NONE);
        boolean allCompact = p.entries().stream().allMatch(e -> e.mode() == DmxMode.COMPACT);
        check(p.fits() && allCompact && p.used() == 512, "64 VORTEX X1 in Compact in one universe: " + p.report());
        // the decoders of the packed channels
        FixtureProfile v = FixtureProfile.VORTEX_X1;
        DmxMode vc = DmxMode.of(v, DmxMode.COMPACT);
        float[] o = home(v);
        DmxMode.hueSat(v, 0, 255, o);
        check(o[Attr.RED.ordinal()] == 1f && o[Attr.GREEN.ordinal()] == 0f && o[Attr.BLUE.ordinal()] == 0f, "hue 0, saturation 255 = red");
        DmxMode.hueSat(v, 171, 255, o);
        check(o[Attr.BLUE.ordinal()] > 0.95f && o[Attr.RED.ordinal()] < 0.05f && o[Attr.GREEN.ordinal()] < 0.05f, "hue 171 = blue");
        DmxMode.hueSat(v, 85, 0, o);
        check(o[Attr.RED.ordinal()] == 1f && o[Attr.GREEN.ordinal()] == 1f && o[Attr.BLUE.ordinal()] == 1f, "saturation 0 = white whatever the hue");
        FixtureProfile w = FixtureProfile.LUMORA_W7;
        float[] ow = home(w);
        DmxMode.hueSat(w, 0, 0, ow);
        check(ow[Attr.WHITE.ordinal()] == 1f && ow[Attr.RED.ordinal()] == 0f, "RGBW wash: saturation 0 = its white LEDs");
        DmxMode.hueSat(w, 0, 255, ow);
        check(ow[Attr.CTO.ordinal()] == 1f && ow[Attr.WHITE.ordinal()] == 1f, "RGBW wash: end of the saturation channel = warm white (CTO)");
        DmxMode.hueSat(w, 0, DmxMode.SAT_END, ow);
        check(ow[Attr.RED.ordinal()] == 1f && ow[Attr.WHITE.ordinal()] == 0f && ow[Attr.CTO.ordinal()] == 0f, "RGBW wash: full saturation = pure colour");
        DmxMode.shutterFx(v, 200, o);
        check(o[Attr.STROBE.ordinal()] == 0f && o[Attr.IRIS.ordinal()] == 1f && o[Attr.FOCUS.ordinal()] > 0.1f, "VORTEX strobe channel 200 = focus, iris open, no strobe");
        DmxMode.shutterFx(v, 160, o);
        check(o[Attr.STROBE.ordinal()] == 0f && o[Attr.IRIS.ordinal()] < 0.6f && o[Attr.IRIS.ordinal()] > 0.4f, "VORTEX strobe channel 160 = iris half closed");
        DmxMode.shutterFx(v, 127, o);
        check(o[Attr.STROBE.ordinal()] == 1f && o[Attr.IRIS.ordinal()] == 1f, "VORTEX strobe channel 127 = fastest strobe");
        for (FixtureProfile g : new FixtureProfile[] {v, FixtureProfile.PHANTOM_S1, FixtureProfile.PROFILE_P1, FixtureProfile.RAZOR_B1}) {
            float[] og = home(g);
            for (int wheel = 1; wheel <= (g.has(Attr.GOBO_2) ? 2 : 1); wheel++)
                for (int k = 0; k <= g.gobos; k++)
                    for (float sp : new float[] {0f, 0.5f, 1f, -0.5f, -1f}) {
                        int val = DmxMode.goboValue(g, wheel, k, sp);
                        DmxMode.goboFx(g, val, og);
                        Attr ga = wheel == 2 && k > 0 ? Attr.GOBO_2 : Attr.GOBO;
                        Attr ra = wheel == 2 && k > 0 ? Attr.GOBO_2_ROT : Attr.GOBO_ROT;
                        boolean ok = Math.abs(og[ga.ordinal()] - k / (float) g.gobos) < 1e-5f;
                        if (k > 0 && g.has(ra)) ok &= sp == 0f ? og[ra.ordinal()] == 0.5f : Math.signum(og[ra.ordinal()] - 0.5f) == Math.signum(sp)
                            && Math.abs(Math.abs(og[ra.ordinal()] - 0.5f) * 2f - Math.abs(sp)) < 0.2f;
                        if (!ok) { check(false, g.name + " gobo channel: wheel " + wheel + " gobo " + k + " speed " + sp + " -> DMX " + val); return; }
                    }
            check(true, g.name + " gobo channel: every gobo of every wheel, still and turning both ways");
        }
        // through the whole decoder: Compact VORTEX, gobo 3 turning, blue, half iris
        Show t = rig(1, 0, 0, 0);
        Show.Patch f = t.fixture(1);
        f.universe = 1; f.address = 1; f.dmxMode = DmxMode.COMPACT;
        int[] slots = new int[vc.channels.length * 2];
        for (int i = 0; i < vc.channels.length; i++) {
            DmxMode.Channel ch = vc.channels[i];
            int val = switch (ch.kind()) {
                case HUE -> 171; case SATURATION -> 255; case SHUTTER_FX -> 160; case GOBO_FX -> DmxMode.goboValue(v, 1, 3, 1f);
                default -> ch.attr() == Attr.DIMMER ? 255 : ch.def();
            };
            slots[2 * i] = i + 1; slots[2 * i + 1] = val;
        }
        float[] out = home(v);
        DmxDecode.decode(f, data(1, slots), out, null);
        check(Math.abs(out[Attr.GOBO.ordinal()] - 3 / 8f) < 1e-5 && out[Attr.GOBO_ROT.ordinal()] > 0.9f && out[Attr.BLUE.ordinal()] > 0.95f
            && out[Attr.RED.ordinal()] < 0.05f && out[Attr.DIMMER.ordinal()] == 1f && out[Attr.IRIS.ordinal()] < 0.6f, "Compact VORTEX decoded: blue, gobo 3 turning, iris half");
    }

    /** Rev. 17: the VORTEX is no longer sent to Ultra Compact first, and a mode change re-addresses instead of being refused. */
    static void vortexModes() {
        System.out.println("VORTEX modes (rev. 17)");
        // the rig of the 11 November screenshot: 54 fixtures, many VORTEX
        Show s = rig(22, 12, 10, 10);
        AutoPatch.Plan p = AutoPatch.plan(s, 1, DmxMode.FULL, AutoPatch.SHARE_NONE);
        System.out.println("  " + p.report());
        boolean vortexUltra = p.entries().stream().anyMatch(e -> s.fixture(e.fid()).profile() == FixtureProfile.VORTEX_X1 && e.mode() == DmxMode.ULTRA);
        check(p.fits() && !vortexUltra, "54 fixtures with 22 VORTEX: one universe, no VORTEX in Ultra Compact");
        Show big = rig(30, 15, 10, 10);
        AutoPatch.Plan q = AutoPatch.plan(big, 1, DmxMode.FULL, AutoPatch.SHARE_NONE);
        System.out.println("  " + q.report());
        // rev. 21: the 10 PULSESTRIKE M take 12 channels each in Compact (light zones): the plan may send them to Ultra Compact; the
        // moving heads and washes must not go there
        check(q.fits() && q.entries().stream().noneMatch(e -> e.mode() == DmxMode.ULTRA && !big.fixture(e.fid()).profile().hasSections()),
            "65 fixtures: one universe, every moving head and wash in Compact or better");
        // Ultra -> Compact for every VORTEX, packed at 4 channels each: nothing refused, no overlap, nothing past 512
        Show u = rig(40, 0, 0, 0);
        int addr = 1;
        for (Show.Patch f : u.patch) { f.universe = 1; f.address = addr; f.dmxMode = DmxMode.ULTRA; addr += 4; }
        int[] fids = u.patch.stream().mapToInt(f -> f.fid).toArray();
        List<int[]> plan = AutoPatch.changeMode(u, fids, DmxMode.COMPACT);
        Bytes.Writer bw = Commands.op(Commands.PATCH_BULK).varInt(plan.size());
        for (int[] e : plan) bw.varInt(e[0]).varInt(e[1]).varInt(e[2]).varInt(e[3]);
        int res = Commands.apply(u, new Live(), bw.toBytes(), 0, null);
        check(res != Commands.REJECTED, "40 VORTEX Ultra -> Compact accepted");
        check(u.patch.stream().allMatch(f -> f.dmxMode == DmxMode.COMPACT) && AutoPatch.conflicts(u).isEmpty(), "40 VORTEX in Compact, no overlap, nothing past 512");
        check(u.patch.stream().allMatch(f -> f.universe == 1), "40 VORTEX in Compact stay in universe 1 (320 channels)");
        // and back to Full: those that do not fit go to the next universe instead of being refused
        plan = AutoPatch.changeMode(u, fids, DmxMode.FULL);
        bw = Commands.op(Commands.PATCH_BULK).varInt(plan.size());
        for (int[] e : plan) bw.varInt(e[0]).varInt(e[1]).varInt(e[2]).varInt(e[3]);
        check(Commands.apply(u, new Live(), bw.toBytes(), 0, null) != Commands.REJECTED && AutoPatch.conflicts(u).isEmpty()
            && u.patch.stream().allMatch(f -> f.dmxMode == DmxMode.FULL), "40 VORTEX Compact -> Full: re-addressed over two universes, no conflict");
    }

    static float[] home(FixtureProfile p) {
        float[] v = new float[Attr.COUNT];
        for (Attr a : Attr.ALL) v[a.ordinal()] = p.home(a);
        return v;
    }

    // ------------------------------------------------------------------------------------------------------------------ auto patch
    static void autoPatch() {
        System.out.println("AUTO PATCH 1 UNIVERSE");
        Show s = rig(10, 12, 8, 10);
        AutoPatch.Plan p = AutoPatch.plan(s, 1, DmxMode.FULL, AutoPatch.SHARE_NONE);
        System.out.println("  " + p.report());
        check(p.fits() && p.used() <= 512, "40 fixtures individually in one universe (modes reduced)");
        apply(s, p);
        check(AutoPatch.conflicts(s).isEmpty(), "no overlapping addresses");
        check(AutoPatch.usage(s).get(1) == p.used(), "usage = plan");
        Show big = rig(40, 40, 40, 40);
        AutoPatch.Plan q = AutoPatch.plan(big, 1, DmxMode.FULL, AutoPatch.SHARE_NONE);
        System.out.println("  " + q.report());
        check(!q.fits() && q.used() == 40 * 6 + 120 * 4, "160 fixtures individually do not fit even in Ultra Compact, said so: " + q.used() + " channels (rev. 19: VORTEX Ultra 6)");
        boolean spill = false;
        for (AutoPatch.Entry e : q.entries()) if (e.universe() == 2) spill = true;
        check(spill, "what does not fit goes on to universe 2 (no overlap)");
        AutoPatch.Plan r = AutoPatch.plan(big, 1, DmxMode.FULL, AutoPatch.SHARE_BY_TYPE);
        System.out.println("  " + r.report());
        check(r.fits() && r.used() == 18 + 70 + 124 + 87, "shared by type (asked): 4 addresses, full modes kept (" + r.used() + ")");
        apply(big, r);
        check(AutoPatch.conflicts(big).isEmpty(), "shares are not conflicts");
        Show.Patch a = big.fixture(1), b = big.fixture(31);
        b.address = a.address + 3; b.universe = a.universe;
        check(!AutoPatch.conflicts(big).isEmpty(), "a real overlap is reported");
        AutoPatch.Plan m = AutoPatch.plan(big, 1, DmxMode.FULL, AutoPatch.MATRIX);
        check(m.fits() && m.used() == 16, "Virtual Matrix plan: 16 channels for 160 fixtures");
        AutoPatch.Plan again = AutoPatch.plan(rig(10, 12, 8, 10), 1, DmxMode.FULL, AutoPatch.SHARE_NONE);
        check(again.report().equals(p.report()), "deterministic plan");
    }

    static void apply(Show s, AutoPatch.Plan p) {
        for (AutoPatch.Entry e : p.entries()) {
            Show.Patch f = s.fixture(e.fid());
            f.universe = e.universe(); f.address = e.address(); f.dmxMode = e.mode();
        }
    }

    static void matrix() {
        System.out.println("Virtual Matrix");
        Show s = rig(4, 0, 0, 0);
        for (Show.Patch p : s.patch) p.dmxMode = DmxMode.MATRIX;
        s.external.matrixUniverse = 1; s.external.matrixAddress = 1;
        int[] ch = {255, 255, 0, 0, 0, 0, 255, 128, 40, 200, 0, 70, 255, 0, 128, 128};
        DmxData d = new DmxData();
        byte[] u = new byte[512];
        for (int i = 0; i < 16; i++) u[i] = (byte) ch[i];
        d.set(1, u);
        Live l = new Live();
        Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(Live.EXTERNAL).toBytes(), -5000, null);
        Engine e1 = new Engine(), e2 = new Engine();
        Map<Integer, Engine.Output> a = e1.evaluate(s, l, 12345, Engine.Music.NONE, d), b = e2.evaluate(s, l, 12345, Engine.Music.NONE, d);
        boolean same = true;
        for (int f = 1; f <= 4; f++) same &= Arrays.equals(a.get(f).v, b.get(f).v);
        check(same, "same channels, same time: same light (two engines)");
        Map<Integer, Engine.Output> c = e1.evaluate(s, l, 12345, Engine.Music.NONE, d);
        float p1 = c.get(1).get(Attr.PAN), p2 = c.get(2).get(Attr.PAN), p4 = c.get(4).get(Attr.PAN);
        check(Math.abs(p1 - p2) > 0.01f, "phase spread: fixtures at different places of the circle");
        near(p1, p4, 1e-4, "spread 255 = one full cycle across the line (first and last together)");
        float before = c.get(1).get(Attr.PAN);
        float after = e1.evaluate(s, l, 12845, Engine.Music.NONE, d).get(1).get(Attr.PAN);
        check(Math.abs(before - after) > 0.001f, "the movement runs with the clock");
        check(VirtualMatrix.members(s).size() == 4, "4 members");
    }

    // ------------------------------------------------------------------------------------------------------------------ recording
    static void recording() throws Exception {
        System.out.println("Recording, .cscshow, player");
        Show s = rig(2, 0, 0, 0);
        ShowRecorder rec = new ShowRecorder(1000, Set.of(1, 2), s, "Test show", "unit test");
        rec.music("abc", "track.ogg", 30000);
        Random rnd = new Random(7);
        Map<Long, byte[]> truth = new TreeMap<>();
        byte[] u1 = new byte[512], u2 = new byte[512];
        for (int i = 0; i < 1500; i++) {                                  // 60 s at 25 fps
            long t = 1000 + i * 40L;
            for (int k = 0; k < 20; k++) u1[rnd.nextInt(64)] = (byte) rnd.nextInt(256);
            if (i % 5 == 0) u2[rnd.nextInt(512)] = (byte) rnd.nextInt(256);
            rec.capture(1, u1, t);
            rec.capture(2, u2, t);
            rec.capture(3, u2, t);                                          // not recorded
            truth.put(t - 1000, u1.clone());
        }
        CscShowFile f = rec.finish(1000 + 1500 * 40L);
        byte[] bytes = f.toBytes();
        System.out.println("  60 s, 2 universes: " + f.frames.size() + " frames, file " + bytes.length + " bytes");
        CscShowFile g = CscShowFile.fromBytes(bytes);
        check(g.frames.size() == f.frames.size() && g.universes.length == 2 && "abc".equals(g.trackSha1) && g.musicStartMs == 30000
            && g.patch.size() == 2, "file round trip: frames, universes, music link, patch");
        check(Arrays.equals(g.toBytes(), bytes), "re-encoding is byte identical");
        ShowPlayer pl = new ShowPlayer(g);
        DmxData out = new DmxData();
        boolean ok = true;
        long[] probes = {0, 40, 1999, 2000, 2001, 33333, 59960, 12000, 400};
        for (long t : probes) {
            pl.seek(t, out);
            byte[] want = null;
            for (Map.Entry<Long, byte[]> e : truth.entrySet()) if (e.getKey() <= t) want = e.getValue();
            if (!Arrays.equals(out.get(1), want)) { ok = false; System.out.println("    seek " + t + " differs"); }
        }
        check(ok, "seek anywhere (forwards and backwards) gives the recorded values");
        pl.seek(0, out);
        pl.play(0);
        boolean ok2 = true;
        for (long c = 0; c <= 59960; c += 17) {
            pl.tick(c, out);
            byte[] want = null;
            for (Map.Entry<Long, byte[]> e : truth.entrySet()) if (e.getKey() <= c) want = e.getValue(); else break;
            if (!Arrays.equals(out.get(1), want)) { ok2 = false; break; }
        }
        check(ok2, "playing at 1x gives the recorded values at every moment");
        pl.speed = 2f;
        pl.restart(100000, out);
        pl.tick(110000, out);
        check(Math.abs(pl.position() - 20000) <= 1, "speed 2x: 10 s of clock = 20 s of show");
        pl.loop = true;
        pl.speed = 1f;
        pl.tick(160000, out);
        check(pl.playing && pl.position() < 20000, "loop: wraps at the end");
        pl.follow(45000, out);
        check(pl.position() == 45000, "follows an external clock (jump = seek)");
        byte[] dam = bytes.clone();
        dam[dam.length / 2] ^= 0x5A;
        boolean refused = false;
        try { CscShowFile.fromBytes(dam); } catch (CscShowFile.BadFile e) { refused = true; System.out.println("    damaged: " + e.getMessage()); }
        check(refused, "damaged file refused");
        refused = false;
        try { CscShowFile.fromBytes(Arrays.copyOf(bytes, bytes.length - 50)); } catch (CscShowFile.BadFile e) { refused = true; }
        check(refused, "truncated file refused");
        refused = false;
        try { CscShowFile.fromBytes("hello world, not a show".getBytes()); } catch (CscShowFile.BadFile e) { refused = true; }
        check(refused, "other file refused");
        byte[] a = new byte[512], b = new byte[512];
        b[0] = 1; b[3] = 2; b[200] = 9; b[511] = 4;
        byte[] r = CscShowFile.runs(a, b);
        byte[] c = new byte[512];
        CscShowFile.apply(r, c);
        check(Arrays.equals(b, c), "delta runs round trip (" + r.length + " bytes)");
    }

    // ------------------------------------------------------------------------------------------------------------------ timecode
    static void timecode() {
        System.out.println("MIDI timecode, OSC");
        Timecode.Mtc m = new Timecode.Mtc();
        // 01:02:03:10 at 25 fps (rate 1): pieces
        int h = 1, mi = 2, se = 3, fr = 10, rate = 1;
        int[] pcs = {fr & 15, fr >> 4, se & 15, se >> 4, mi & 15, mi >> 4, h & 15, (h >> 4) | rate << 1};
        boolean done = false;
        for (int i = 0; i < 8; i++) done = m.message(new byte[] {(byte) 0xF1, (byte) (i << 4 | pcs[i])}, 2, 0);
        long want = ((1 * 60 + 2) * 60 + 3) * 1000L + Math.round(12 * 40.0);
        check(done && m.ms() == want, "quarter frames: 01:02:03:10 at 25 fps (+2 frames) = " + m.ms() + " ms");
        check(m.fps() == 25, "rate 25 fps");
        Timecode.Mtc f = new Timecode.Mtc();
        f.message(new byte[] {(byte) 0xF0, 0x7F, 0x7F, 0x01, 0x01, (byte) (3 << 5 | 0), 0, 10, 15, (byte) 0xF7}, 10, 0);
        check(f.ms() == 10 * 1000 + 500 && f.fps() == 30, "full frame 00:00:10:15 at 30 fps");
        check(f.ms(100_000_000L) == 10600 && f.ms(500_000_000L) == 10500, "running extrapolation, held when it stops");
        byte[] osc = Timecode.oscMessage("/csc/seek", 12.5f);
        List<Timecode.OscMessage> msgs = Timecode.osc(osc, osc.length);
        check(msgs != null && msgs.size() == 1 && msgs.get(0).address().equals("/csc/seek") && msgs.get(0).number(0, 0) == 12.5f, "OSC message with a float");
        byte[] s2 = Timecode.oscMessage("/csc/mode", "playback");
        check(Timecode.osc(s2, s2.length).get(0).text(0).equals("playback"), "OSC message with a string");
        check(Timecode.osc(new byte[] {'x', 'y', 0, 0}, 4) == null, "malformed OSC refused");
    }

    // ------------------------------------------------------------------------------------------------------------------ formats
    static void formats() {
        System.out.println("Show and live formats");
        Show s = rig(2, 1, 0, 0);
        s.fixture(1).dmxMode = DmxMode.COMPACT; s.fixture(1).invertPan = true; s.fixture(1).panOffset = -12.5f;
        s.fixture(2).dmxMode = DmxMode.MATRIX;
        s.external.matrixAddress = 400; s.external.matrixOrder = Show.External.ORDER_Z;
        byte[] b = s.toBytes();
        Show r = Show.fromBytes(b);
        check(r != null && r.fixture(1).dmxMode == DmxMode.COMPACT && r.fixture(1).invertPan && r.fixture(1).panOffset == -12.5f
            && r.fixture(2).dmxMode == DmxMode.MATRIX && r.external.matrixAddress == 400 && r.external.matrixOrder == Show.External.ORDER_Z,
            "format 2 keeps DMX modes, transforms and matrix settings");
        // a format 1 file (1.2.0 before External Show Control): same bytes without the tail, version 1
        Bytes.Writer tail = new Bytes.Writer();
        tail.varInt(s.patch.size());
        for (Show.Patch p : s.patch) tail.varInt(p.fid).varInt(p.dmxMode).bool(p.invertPan).bool(p.invertTilt).f32(p.panOffset).f32(p.tiltOffset);
        tail.varInt(s.external.matrixUniverse).varInt(s.external.matrixAddress).varInt(s.external.matrixOrder);
        byte[] v1 = Arrays.copyOf(b, b.length - tail.size());
        v1[4] = 1;                                                          // varInt format right after the 4-byte magic
        Show o = Show.fromBytes(v1);
        check(o != null && o.patch.size() == 3 && o.fixture(1).dmxMode == DmxMode.FULL && !o.fixture(1).invertPan, "format 1 shows still load (Full mode, no transform)");
        Live l = new Live();
        Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(Live.PLAYBACK).toBytes(), 777, null);
        Live lr = Live.fromBytes(l.toBytes());
        check(lr.control == Live.PLAYBACK && lr.previousControl == Live.AUTOSHOW && lr.controlMs == 777, "live state keeps the control mode");
    }

    static void commands() {
        System.out.println("Commands");
        Show s = rig(2, 0, 0, 0);
        Live l = new Live();
        byte[] c = Commands.op(Commands.PATCH_DMX).varInt(1).varInt(2).varInt(100).varInt(DmxMode.ULTRA).bool(true).bool(false).f32(5).f32(-5).toBytes();
        check(Commands.apply(s, l, c, 0, null) == Commands.SHOW && s.fixture(1).universe == 2 && s.fixture(1).address == 100
            && s.fixture(1).dmxMode == DmxMode.ULTRA && s.fixture(1).invertPan, "PATCH_DMX");
        check(Commands.apply(s, l, Commands.op(Commands.PATCH_DMX).varInt(1).varInt(2).varInt(600).varInt(0).bool(false).bool(false).f32(0).f32(0).toBytes(), 0, null)
            == Commands.REJECTED, "address 600 rejected");
        Bytes.Writer w = Commands.op(Commands.PATCH_BULK).varInt(2);
        w.varInt(1).varInt(1).varInt(1).varInt(0).varInt(99).varInt(1).varInt(19).varInt(0);
        check(Commands.apply(s, l, w.toBytes(), 0, null) == Commands.REJECTED && s.fixture(1).universe == 2, "bulk patch with an unknown fixture: nothing applied");
        check(Commands.editsShow(Commands.PATCH_DMX) && Commands.editsShow(Commands.PATCH_BULK) && Commands.editsShow(Commands.MATRIX_SETTINGS), "patch edits are undoable");
    }

    /** Rev. 16h: Art-Net universe 0 and the Art-Net settings (tests A, B, C, I, K of the specification, off-game parts). */
    static void universeZero() {
        System.out.println("Art-Net universe 0 and network settings");
        // C: Port-Address = Net:Sub-Net:Universe, as the packets carry it
        int[][] nsu = {{0, 0, 0, 0}, {0, 0, 1, 1}, {0, 0, 15, 15}, {0, 1, 0, 16}, {1, 0, 0, 256}};
        boolean ok = true;
        for (int[] r : nsu) {
            int pa = ArtNetSettings.portAddress(r[0], r[1], r[2]);
            byte[] b = new byte[512];
            DmxPackets.Frame f = DmxPackets.parse(DmxPackets.artDmx(pa, 1, b, 512), 530, "10.0.0.2");
            ok &= pa == r[3] && f != null && f.universe() == r[3] && ArtNetSettings.netSubUni(pa).equals(r[0] + ":" + r[1] + ":" + r[2]);
        }
        check(ok, "Port-Address 0, 1, 15, 16, 256 = Net:Sub-Net:Universe 0:0:0, 0:0:1, 0:0:15, 0:1:0, 1:0:0, decoded unchanged");
        ArtNetSettings a = new ArtNetSettings();
        a.range(0, 300);
        check(a.cscUniverse(0) == 0 && a.cscUniverse(1) == 1 && a.cscUniverse(15) == 15 && a.cscUniverse(16) == 16 && a.cscUniverse(64) == 64,
            "default mapping is direct: 0 -> 0, 1 -> 1, 15 -> 15, 16 -> 16 (no +1)");
        check(a.cscUniverse(256) == 256 && ArtNetSettings.NONE != 0, "selected Port-Address 256 maps directly to CSC 256; NONE remains -1");
        a.mapping.put(256, 3);
        check(a.cscUniverse(256) == 3 && a.portAddressOf(3) == 256, "mapping 256 -> CSC 3 (and back: CSC 3 is fed by 256)");
        // A: universe 0 alone
        ArtNetSettings only0 = new ArtNetSettings();
        only0.range(0, 0);
        DmxMerge m = new DmxMerge();
        m.artnetMap = only0::cscUniverse;
        byte[] d0 = new byte[512], d1 = new byte[512];
        for (int i = 0; i < 512; i++) { d0[i] = (byte) i; d1[i] = (byte) (255 - i); }
        int u0 = m.receive(DmxPackets.parse(DmxPackets.artDmx(0, 1, d0, 512), 530, "10.0.0.2"), 0);
        int u1 = m.receive(DmxPackets.parse(DmxPackets.artDmx(1, 1, d1, 512), 530, "10.0.0.2"), 0);
        byte[] out = new byte[512];
        long v = m.snapshot(0, out);
        check(u0 == 0 && v >= 0 && Arrays.equals(out, d0) && !m.lost(0), "universe 0 alone: the 512 channels received (channel 1 = slot 0 ... channel 512)");
        check(u1 == DmxMerge.NOT_RECEIVED && m.snapshot(1, out) < 0 && m.ignored() == 1 && m.rejected() == 0, "Port-Address 1 not selected: ignored, not an error");
        // B: universes 0 and 1 together, no shift
        ArtNetSettings both = new ArtNetSettings();
        both.range(0, 1);
        m = new DmxMerge();
        m.artnetMap = both::cscUniverse;
        m.receive(DmxPackets.parse(DmxPackets.artDmx(0, 1, d0, 512), 530, "10.0.0.2"), 0);
        m.receive(DmxPackets.parse(DmxPackets.artDmx(1, 1, d1, 512), 530, "10.0.0.2"), 0);
        byte[] o0 = new byte[512], o1 = new byte[512];
        m.snapshot(0, o0);
        m.snapshot(1, o1);
        check(Arrays.equals(o0, d0) && Arrays.equals(o1, d1), "universes 0 and 1 at the same time, each with its own data (no shift)");
        // non consecutive universes
        ArtNetSettings sparse = new ArtNetSettings();
        sparse.selected.clear();
        sparse.selected.set(0); sparse.selected.set(2); sparse.selected.set(7);
        check(sparse.cscUniverse(0) == 0 && sparse.cscUniverse(1) == ArtNetSettings.NONE && sparse.cscUniverse(2) == 2 && sparse.cscUniverse(7) == 7
            && ArtNetSettings.list(sparse.selected).equals("0,2,7"), "non consecutive universes 0, 2, 7");
        // a fixture patched on universe 0 is driven by it (and by nothing else)
        Show s = new Show();
        Live l = new Live();
        Commands.apply(s, l, Commands.op(Commands.PATCH_ADD).varInt(1).str(FixtureProfile.VORTEX_X1.id).str("").sInt(0).sInt(70).sInt(0).f32(0).varInt(1).toBytes(), 0, null);
        Commands.apply(s, l, Commands.op(Commands.PATCH_ADD).varInt(2).str(FixtureProfile.LUMORA_W19.id).str("").sInt(2).sInt(70).sInt(0).f32(0).varInt(1).toBytes(), 0, null);
        check(s.fixture(1).universe == 0 && s.fixture(1).address == 1, "a new show patches its first fixture on universe 0, address 1");
        check(Commands.apply(s, l, Commands.op(Commands.PATCH_DMX).varInt(2).varInt(1).varInt(1).varInt(DmxMode.FULL).bool(false).bool(false).f32(0).f32(0).toBytes(), 0, null) == Commands.SHOW
            && Commands.apply(s, l, Commands.op(Commands.PATCH_DMX).varInt(1).varInt(0).varInt(1).varInt(DmxMode.FULL).bool(false).bool(false).f32(0).f32(0).toBytes(), 0, null) == Commands.SHOW
            && s.fixture(1).universe == 0, "patch on universe 0 accepted (VORTEX 0.001, W19 1.001)");
        check(Commands.apply(s, l, Commands.op(Commands.PATCH_DMX).varInt(1).varInt(-1).varInt(1).varInt(DmxMode.FULL).bool(false).bool(false).f32(0).f32(0).toBytes(), 0, null) == Commands.REJECTED
            && Commands.apply(s, l, Commands.op(Commands.PATCH_DMX).varInt(1).varInt(64000).varInt(1).varInt(DmxMode.FULL).bool(false).bool(false).f32(0).f32(0).toBytes(), 0, null) == Commands.REJECTED,
            "universe -1 and 64000 rejected, 0 accepted");
        Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(Live.EXTERNAL).toBytes(), 0, null);
        DmxMode vm = DmxMode.of(FixtureProfile.VORTEX_X1, DmxMode.FULL), wm = DmxMode.of(FixtureProfile.LUMORA_W19, DmxMode.FULL);
        DmxData dd = new DmxData();
        byte[] uz = new byte[512], uo = new byte[512];
        uz[vm.channelsOf(Attr.DIMMER)[0] - 1] = (byte) 255; uz[vm.channelsOf(Attr.DIMMER)[1] - 1] = (byte) 255;
        uz[vm.channelsOf(Attr.PAN)[0] - 1] = (byte) 0x40;
        uo[wm.channelsOf(Attr.DIMMER)[0] - 1] = (byte) 128;
        dd.set(0, uz);
        dd.set(1, uo);
        Map<Integer, Engine.Output> o = new Engine().evaluate(s, l, 5000, Engine.Music.NONE, dd);
        near(o.get(1).get(Attr.DIMMER), 1.0, 1e-6, "VORTEX on universe 0: dimmer from universe 0");
        near(o.get(1).get(Attr.PAN), 0x40 / 255.0, 0.01, "VORTEX on universe 0: pan from universe 0");
        near(o.get(2).get(Attr.DIMMER), 128 * 257 / 65535.0, 0.01, "W19 on universe 1: dimmer from universe 1 (not from universe 0)");
        // K: saved and restored (show codec, recording, settings file, console message)
        Show r = Show.fromBytes(s.toBytes());
        check(r.fixture(1).universe == 0 && r.fixture(2).universe == 1, "show saved and reloaded: universe 0 kept (not turned into 1)");
        java.util.Properties pr = new java.util.Properties();
        ArtNetSettings w = new ArtNetSettings();
        w.range(0, 3); w.selected.set(9); w.mapping.put(16, 0); w.bind = "192.168.1.50"; w.port = 6455; w.anySource = false; w.sourceIp = "192.168.1.100";
        w.loss = ArtNetSettings.LOSS_RETURN; w.priority = ArtNetSettings.PRIORITY_ARTNET;
        w.store(pr);
        ArtNetSettings back = ArtNetSettings.load(pr);
        check(back.selected.equals(w.selected) && back.mapping.equals(w.mapping) && back.bind.equals(w.bind) && back.port == 6455 && !back.anySource
            && back.sourceIp.equals("192.168.1.100") && back.loss == ArtNetSettings.LOSS_RETURN && back.priority == ArtNetSettings.PRIORITY_ARTNET
            && back.cscUniverse(0) == 0 && back.cscUniverse(16) == 0, "settings file: universes 0-3 and 9, mapping 16 -> 0, IPs, port, policy kept");
        check("0-3,9".equals(pr.getProperty("artnet_universes")) && !pr.containsKey("artnet_universe_offset"), "settings file: artnet_universes=0-3,9, old +1 key removed");
        java.util.Properties old = new java.util.Properties();
        old.setProperty("artnet_universe_offset", "1");
        check(ArtNetSettings.load(old).cscUniverse(0) == 0, "an old file with artnet_universe_offset=1: no automatic +1 any more (direct 0 -> 0)");
        Bytes.Writer bw = new Bytes.Writer();
        w.write(bw);
        ArtNetSettings net = new ArtNetSettings();
        check(net.read(new Bytes.Reader(bw.toBytes())) && net.selected.equals(w.selected) && net.mapping.equals(w.mapping) && net.port == 6455,
            "console <-> server message of the settings");
        // validation
        ArtNetSettings bad = new ArtNetSettings();
        bad.bind = "192.168.1"; String e1 = bad.validate();
        bad.bind = "0.0.0.0"; bad.port = 70000; String e2 = bad.validate();
        bad.port = 6454; bad.mapping.put(5, 64000); String e3 = bad.validate();
        bad.mapping.clear(); bad.mapping.put(5, 0); String e4 = bad.validate();
        bad.selected.clear(); bad.mapping.clear(); String e5 = bad.validate();
        check(e1 != null && e2 != null && e3 != null && e4 == null && e5 != null, "validation: bad IP, port, mapping target rejected; mapping to universe 0 accepted; nothing selected rejected");
        check(ArtNetSettings.parseList("0-3,7, 16-31") != null && ArtNetSettings.parseList("0-3,7,16-31").cardinality() == 21 && ArtNetSettings.parseList("3-1") == null
            && ArtNetSettings.parseList("0-40000") == null, "universe list parsing (0-3,7,16-31)");
        // recording of universe 0
        Set<Integer> us = new TreeSet<>(); us.add(0); us.add(1);
        ShowRecorder rec = new ShowRecorder(0, us, s, "u0", "test");
        rec.capture(0, d0, 0);
        rec.capture(1, d1, 10);
        rec.capture(0, d1, 500);
        CscShowFile f = rec.finish(1000);
        try {
            CscShowFile g = CscShowFile.fromBytes(f.toBytes());
            ShowPlayer pl = new ShowPlayer(g);
            DmxData pd = new DmxData();
            pl.seek(600, pd);
            check(g.universes[0] == 0 && Arrays.equals(pd.get(0), d1) && Arrays.equals(pd.get(1), d1), "recording / playback of universe 0");
        } catch (CscShowFile.BadFile ex) {
            check(false, "recording of universe 0: " + ex.getMessage());
        }
        // auto patch on universe 0
        Show big = rig(4, 2, 0, 1);
        AutoPatch.Plan plan = AutoPatch.plan(big, 0, DmxMode.FULL, AutoPatch.SHARE_NONE);
        boolean all0 = plan.fits();
        for (AutoPatch.Entry en : plan.entries()) all0 &= en.universe() == 0;
        check(all0, "AUTO PATCH on universe 0");
    }

    /** The receiver path over a real socket (this machine, loopback): bind as the game does, send ArtDmx and sACN unicast, merge. */
    static void udp() throws Exception {
        System.out.println("UDP loopback (sockets as the receiver opens them)");
        java.net.DatagramSocket in;
        try {
            in = new java.net.DatagramSocket(null);
            in.setReuseAddress(true);
            in.setSoTimeout(2000);
            in.bind(new java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), DmxPackets.ARTNET_PORT));
        } catch (java.io.IOException e) {
            System.out.println("  skipped: port " + DmxPackets.ARTNET_PORT + " not available here (" + e.getMessage() + ")");
            return;
        }
        DmxMerge m = new DmxMerge();
        byte[] d = new byte[512];
        d[0] = (byte) 200; d[511] = 7;
        try (java.net.DatagramSocket out = new java.net.DatagramSocket()) {
            byte[] pk = DmxPackets.artDmx(0, 1, d, 512);
            out.send(new java.net.DatagramPacket(pk, pk.length, java.net.InetAddress.getLoopbackAddress(), DmxPackets.ARTNET_PORT));
            byte[] cid = new byte[16];
            byte[] sp = DmxPackets.sacnData(1, 100, 1, false, cid, "test", d, 512);
            out.send(new java.net.DatagramPacket(sp, sp.length, java.net.InetAddress.getLoopbackAddress(), DmxPackets.ARTNET_PORT));
        }
        byte[] buf = new byte[1500];
        int got = 0;
        for (int i = 0; i < 2; i++) {
            java.net.DatagramPacket p = new java.net.DatagramPacket(buf, buf.length);
            in.receive(p);
            DmxPackets.Frame f = DmxPackets.parse(buf, p.getLength(), p.getAddress().getHostAddress());
            if (f != null && m.receive(f, 0) == (f.protocol() == DmxPackets.PROTO_ARTNET ? 0 : 1)) got++;
        }
        in.close();
        byte[] o = new byte[512], o0 = new byte[512];
        m.snapshot(1, o);
        m.snapshot(0, o0);
        check(got == 2 && (o[0] & 255) == 200 && o[511] == 7 && (o0[0] & 255) == 200 && o0[511] == 7,
            "Art-Net Port-Address 0 (universe 0) and sACN universe 1 received over UDP on this machine, each in its own CSC universe");
    }

    static void perf() {
        System.out.println("Timings (this machine, indicative)");
        Show s = rig(25, 25, 25, 25);
        AutoPatch.Plan p = AutoPatch.plan(s, 1, DmxMode.FULL, AutoPatch.SHARE_NONE);
        apply(s, p);
        Live l = new Live();
        Commands.apply(s, l, Commands.op(Commands.CONTROL_MODE).varInt(Live.EXTERNAL).toBytes(), -5000, null);
        DmxData d = new DmxData();
        byte[] u = new byte[512];
        new Random(1).nextBytes(u);
        for (int k = 1; k <= 4; k++) d.set(k, u);
        Engine e = new Engine();
        for (int i = 0; i < 2000; i++) e.evaluate(s, l, i, Engine.Music.NONE, d);
        long t0 = System.nanoTime();
        int n = 5000;
        for (int i = 0; i < n; i++) e.evaluate(s, l, i, Engine.Music.NONE, d);
        double us = (System.nanoTime() - t0) / 1000.0 / n;
        System.out.println(String.format(Locale.ROOT, "  engine, 100 fixtures from DMX: %.1f us per frame", us));
        check(us < 2000, "100 fixtures decoded well under 2 ms per frame");
        ShowRecorder rec = new ShowRecorder(0, Set.of(1, 2, 3, 4), s, "perf", "");
        Random r = new Random(3);
        for (int i = 0; i < 10 * 60 * 30; i++) {                          // 10 min at 30 fps, 4 universes
            for (int k = 0; k < 30; k++) u[r.nextInt(512)] = (byte) r.nextInt(256);
            for (int k = 1; k <= 4; k++) rec.capture(k, u, i * 33L);
        }
        long t1 = System.nanoTime();
        byte[] file = rec.finish(10 * 60 * 1000).toBytes();
        double encMs = (System.nanoTime() - t1) / 1e6;
        System.out.println(String.format(Locale.ROOT, "  10 min, 4 universes, 30 changes per frame: %d kB file, written in %.0f ms", file.length / 1024, encMs));
        try {
            long t2 = System.nanoTime();
            ShowPlayer pl = new ShowPlayer(CscShowFile.fromBytes(file));
            double loadMs = (System.nanoTime() - t2) / 1e6;
            DmxData o = new DmxData();
            long t3 = System.nanoTime();
            for (int i = 0; i < 200; i++) pl.seek((i * 7919L) % 600000, o);
            double seekUs = (System.nanoTime() - t3) / 1000.0 / 200;
            System.out.println(String.format(Locale.ROOT, "  loaded in %.0f ms, seek %.0f us", loadMs, seekUs));
            check(seekUs < 20000, "seek under 20 ms");
        } catch (CscShowFile.BadFile ex) {
            check(false, "perf file reads back: " + ex.getMessage());
        }
    }
}
