import com.concertstage.light.core.Attr;
import com.concertstage.light.core.FixtureProfile;
import com.concertstage.light.dmx.DmxMode;
import com.concertstage.light.dmx.VirtualMatrix;

/**
 * Writes the DMX modes of the CSC fixtures (the Java tables of DmxMode, the only source) as JSON for the exporter of the
 * external profiles (build_external_assets.py). Usage: see build.sh.
 */
public class DumpModes {
    /**
     * Rev. 21: a light zone attribute is written as the attribute it is (dimmer, red, green, blue, strobe) of its zone: the exporters
     * put it on the geometry / head of that zone (DmxMode#instances), as consoles expect for a multi-cell fixture.
     */
    static Attr base(Attr x) {
        return switch (x) {
            case TOP_DIM, MID_DIM, BOT_DIM -> Attr.DIMMER;
            case TOP_RED, BOT_RED -> Attr.RED;
            case TOP_GREEN, BOT_GREEN -> Attr.GREEN;
            case TOP_BLUE, BOT_BLUE -> Attr.BLUE;
            case TOP_STROBE, MID_STROBE, BOT_STROBE -> Attr.STROBE;
            default -> x;
        };
    }

    public static void main(String[] a) {
        StringBuilder s = new StringBuilder("{\n \"fixtures\": [\n");
        boolean firstP = true;
        for (FixtureProfile p : FixtureProfile.all()) {
            if (!firstP) s.append(",\n");
            firstP = false;
            s.append("  {\"id\": \"").append(p.id).append("\", \"name\": \"").append(p.name).append("\", \"function\": \"").append(p.function)
                .append("\", \"pan\": ").append(p.panRange).append(", \"tilt\": ").append(p.tiltRange)
                .append(", \"zoom\": [").append(p.zoomMin).append(", ").append(p.zoomMax).append("]")
                .append(", \"gobos\": ").append(p.gobos).append(", \"strobe_hz\": ").append(p.strobeMaxHz).append(", \"prism_facets\": ").append(p.prismFacets)
                .append(", \"zones\": ").append(p.zones).append(", \"rgb_zones\": ").append(p.rgbZones).append(", \"white_zones\": ").append(p.whiteZones)
                .append(", \"render_scale\": ").append(p.renderScale).append(", \"pan_speed\": ").append(p.panSpeed).append(", \"tilt_speed\": ").append(p.tiltSpeed)
                .append(", \"attrs\": [");
            boolean f = true;
            for (Attr at : p.attrs) { if (at.zoneAttr()) continue; s.append(f ? "" : ", ").append('"').append(at.name()).append('"'); f = false; }
            s.append("], \"home_zoom\": ").append(p.home(Attr.ZOOM)).append(", \"modes\": [");
            for (int m = 0; m < 4; m++) {
                DmxMode d = DmxMode.of(p, m);
                s.append(m == 0 ? "" : ", ").append("{\"name\": \"").append(d.name()).append("\", \"index\": ").append(m).append(", \"channels\": [");
                for (int i = 0; i < d.channels.length; i++) {
                    DmxMode.Channel c = d.channels[i];
                    s.append(i == 0 ? "" : ", ").append("{\"kind\": \"").append(c.kind()).append("\", \"attr\": ")
                        .append(c.attr() == null ? "null" : "\"" + base(c.attr()).name() + "\"").append(", \"zone\": ").append(c.zone())
                        .append(c.attr() != null && c.attr().zoneAttr() ? ", \"section\": \"" + FixtureProfile.SECTION_NAMES[DmxMode.zoneOf(c.attr())] + "\"" : "")
                        .append(", \"name\": \"").append(c.name()).append("\", \"default\": ").append(c.def());
                    // rev. 17: ranges of the packed Compact channels (hue, saturation / CTO, strobe / iris / focus, gobos / rotation)
                    java.util.List<DmxMode.Range> rs = DmxMode.ranges(p, c);
                    if (!rs.isEmpty()) {
                        s.append(", \"ranges\": [");
                        for (int k = 0; k < rs.size(); k++) {
                            DmxMode.Range r = rs.get(k);
                            s.append(k == 0 ? "" : ", ").append("{\"lo\": ").append(r.lo()).append(", \"hi\": ").append(r.hi())
                                .append(", \"name\": \"").append(r.name()).append("\", \"what\": \"").append(r.what())
                                .append("\", \"from\": ").append(r.from()).append(", \"to\": ").append(r.to()).append(", \"slot\": ").append(r.slot()).append("}");
                        }
                        s.append("]");
                    }
                    s.append("}");
                }
                s.append("], \"instances\": [");
                boolean ih = true;
                for (DmxMode.Instance in : d.instances()) {
                    s.append(ih ? "" : ", ").append("{\"name\": \"").append(in.name()).append("\", \"channels\": [");
                    for (int k = 0; k < in.channels().length; k++) s.append(k == 0 ? "" : ", ").append(in.channels()[k]);
                    s.append("]}");
                    ih = false;
                }
                s.append("], \"missing\": [");
                boolean g = true;
                for (String miss : d.missing()) { s.append(g ? "" : ", ").append('"').append(miss).append('"'); g = false; }
                s.append("]}");
            }
            s.append("]}");
        }
        s.append("\n ],\n \"macro\": [");
        for (int i = 0; i < DmxMode.MACRO_COLORS.length; i++)
            s.append(i == 0 ? "" : ", ").append("{\"name\": \"").append(DmxMode.MACRO_NAMES[i]).append("\", \"rgb\": \"").append(String.format("%06x", DmxMode.MACRO_COLORS[i])).append("\"}");
        s.append("],\n \"macro_step\": ").append(DmxMode.MACRO_STEP).append(", \"gobo_step\": ").append(DmxMode.GOBO_STEP)
            .append(", \"strobe_open\": ").append(DmxMode.STROBE_OPEN).append(", \"rot_stop\": [").append(DmxMode.ROT_STOP_LO).append(", ").append(DmxMode.ROT_STOP_HI)
            .append("], \"prism_in\": ").append(DmxMode.PRISM_IN).append(",\n \"matrix\": [");
        for (int i = 0; i < VirtualMatrix.CHANNEL_NAMES.length; i++) s.append(i == 0 ? "" : ", ").append('"').append(VirtualMatrix.CHANNEL_NAMES[i]).append('"');
        s.append("]\n}\n");
        System.out.print(s);
    }
}
