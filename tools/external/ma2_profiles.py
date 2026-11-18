"""
CSC - grandMA2 / dot2 fixture type exporters (MA2 fixture XML, the format the MA Fixture Builder writes and the grandMA2 / dot2
"library" folders hold).

Built from tools/external/modes.json, which is dumped from the Java DmxMode tables (the single source of the DMX layouts). Nothing is
copied from the GDTF files: every channel, range, default and channel set comes from the mode tables and the fixture data.

One file per fixture type and per DMX mode (Full, Compact, Ultra Compact). The Virtual Matrix mode has no channel and is not exported.

Two exporters, one per software, sharing the writer:
  * GrandMa2Exporter  -> gma2/library/<file>.xml
  * Dot2Exporter      -> dot2/library/<file>.xml
MA documents that one Fixture Builder file imports into both systems, so the XML body is the same; what differs is the target folder,
the info item, and the target checks (dot2: everything must fit the first universe that dot2 onPC outputs without hardware).

STATUS: the structure follows the MA documentation and published grandMA2 fixture XML. It has NOT been imported into a real dot2 onPC
or grandMA2 onPC yet. The names marked UNCONFIRMED below are the ones to check first if an import fails or looks wrong.

Usage: python3 -I tools/external/ma2_profiles.py <repo root>
Adding a fixture: add it to DmxMode / FixtureProfile, rebuild modes.json (tools/external/build.sh), run this again. Nothing here is
per fixture except the main module class / beam type table (MAIN_MODULE), which falls back to a generic head mover.
"""
import json
import os
import sys
from xml.sax.saxutils import quoteattr

MANUFACTURER = "Concert Stage Craft"
SHORT_MANUFACTURER = "CSC"
STAMP = "2026-11-09T12:00:00"                     # fixed: same inputs give byte-identical files
def ns_header(major, minor, stream):
    """Root element. The version is the version of the software the file is written for: an MA desk refuses a fixture type written by
    a newer version than its own (rev. 16i: dot2 1.x refused the files tagged grandMA2 3.9.60 - "the fixtures are not recognised")."""
    return ('<MA xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://schemas.malighting.de/grandma2/xml/MA" '
            'xsi:schemaLocation="http://schemas.malighting.de/grandma2/xml/MA http://schemas.malighting.de/grandma2/xml/%d.%d.%d/MA.xsd" '
            'major_vers="%d" minor_vers="%d" stream_vers="%d">') % (major, minor, stream, major, minor, stream)
EXPORTED_MODES = 3                                # Full, Compact, Ultra Compact (Virtual Matrix has no channel)
FILE_MODE = {"Full": "full", "Compact": "compact", "Ultra Compact": "ultra_compact"}

# Main module of each type: (class, beamtype). Fixture function, not a measurement. Unknown type -> generic head mover / wash.
MAIN_MODULE = {"csc_vortex_x1": ("Headmover", "Spot"), "csc_lumora_w7": ("Headmover", "Wash"),
               "csc_lumora_w19": ("Headmover", "Wash"), "csc_pulsestrike_m": ("Headmover", "Wash")}
# MA 3D only draws a beam when beam_intensity > 0. The game has no photometric data: this is a NOMINAL visualisation value, not a
# measurement (documented as such; change it in the fixture type editor if the 3D beam is too strong / weak).
NOMINAL_BEAM_INTENSITY = 10000

# Rotation speeds the game renders at the end of the rotation channel (FixtureRenderer: 220 deg/s gobo, 160 deg/s prism).
ROT_DEG_S = {"GOBO_ROT": 220.0, "PRISM_ROT": 160.0}

# MA attribute naming: CSC attribute -> (preset, feature, attribute). Sources: grandMA2 3.9 / dot2 1.9 manuals ("Fixture Types" table
# of MA 3D / dot2 3D, Attribute & Encoder Grouping, ColorMix vs MixColor).
MA_ATTR = {
    "PAN": ("POSITION", "POSITION", "PAN"),
    "TILT": ("POSITION", "POSITION", "TILT"),
    "DIMMER": ("DIMMER", "DIMMER", "DIM"),
    "STROBE": ("BEAM", "SHUTTER", "SHUTTER"),
    "RED": ("COLOR", "COLORRGB", "COLORRGB1"),
    "GREEN": ("COLOR", "COLORRGB", "COLORRGB2"),
    "BLUE": ("COLOR", "COLORRGB", "COLORRGB3"),
    "WHITE": ("COLOR", "COLORRGB", "COLORRGB5"),
    "CTO": ("COLOR", "CTO", "CTO"),                 # UNCONFIRMED attribute / feature name
    "ZOOM": ("FOCUS", "FOCUS", "ZOOM"),
    "FOCUS": ("FOCUS", "FOCUS", "FOCUS"),
    "IRIS": ("BEAM", "BEAM1", "IRIS"),
    "GOBO": ("GOBO", "GOBO1", "GOBO1"),
    "GOBO_ROT": ("GOBO", "GOBO1", "GOBO1_POS"),
    "PRISM": ("BEAM", "BEAM1", "PRISMA1"),
    "PRISM_ROT": ("BEAM", "BEAM1", "PRISMA1_POS"),
}
# Extended attributes: standard MA names for CMY / shapers; laser controls use
# gobo/zoom encoders because they select a virtual graphical pattern, never real laser hardware.
MA_ATTR.update({
    "CYAN": ("COLOR", "COLORMIX", "COLOR1"),
    "MAGENTA": ("COLOR", "COLORMIX", "COLOR2"),
    "YELLOW": ("COLOR", "COLORMIX", "COLOR3"),
    "COLOR_WHEEL": ("COLOR", "COLOR1", "COLOR1"),
    "FROST": ("BEAM", "BEAM1", "FROST"),
    "DOUSER": ("BEAM", "SHUTTER", "SHUTTER2"),
    "FRAME_ROT": ("SHAPERS", "SHAPER", "SHAPER_ROT"),
    "MOVE_SPEED": ("POSITION", "POSITION", "SPEED_PAN_TILT"),
    "LASER_PATTERN": ("GOBO", "GOBO1", "GOBO1"),
    "LASER_SIZE": ("FOCUS", "FOCUS", "ZOOM"),
    "LASER_SPEED": ("GOBO", "GOBO1", "GOBO1_POS"),
})
for i in range(1,5): MA_ATTR["BLADE_"+str(i)]=( "SHAPERS", "SHAPER", "BLADE"+str(i)+"A")

MA_ATTR.update({
    "GOBO_2": ("GOBO","GOBO2","GOBO2"),
    "GOBO_2_ROT": ("GOBO","GOBO2","GOBO2_POS"),
    "ANIMATION": ("BEAM","BEAM1","ANIMATION1"),
    "PRISM_2": ("BEAM","BEAM1","PRISMA2"),
    "PRISM_2_ROT": ("BEAM","BEAM1","PRISMA2_POS"),
    "SHUTTER": ("BEAM","SHUTTER","SHUTTER1"),
})
ROT_DEG_S.update({"GOBO_2_ROT":220,"PRISM_2_ROT":160})

# rev. 17: packed Compact channels. Attribute of the channel, then the sub attribute of each range (DmxMode.ranges).
# UNCONFIRMED: HUE / SATURATION as MA2 channel attributes (COLORHSB feature); the other names are the ones used above.
# 1.2.0 beta 2b: hue + saturation of the main channels as a separate "Colour" sub-fixture (second instance) — off: one instance.
SPLIT_COLOUR = False
# 1.2.0 beta 2b: pixel fixtures — no RGB on the base module, a virtual dimmer (no DMX address) on every RGB cell
PIXEL_VDIM = True
# 1.2.0 beta 2b (14 November, dot2: "the Position tab cannot be reached" on the PULSESTRIKE M / FLASHCORE FX): a fixture with a tilt
# and no pan gets a virtual PAN (no DMX channel) in its main module, as the tilt-only fixtures of the MA library (GLP JDC1, Chauvet
# Color Strike M): dot2 / grandMA2 only open the position encoders on a pan + tilt pair.
VIRTUAL_PAN = True
PACKED_ATTR = {"HUE": ("COLOR", "COLORHSB", "HUE"), "SATURATION": ("COLOR", "COLORHSB", "SATURATION"),
               "SHUTTER_FX": MA_ATTR["STROBE"], "GOBO_FX": MA_ATTR["GOBO"]}
RANGE_SUB = {"HUE": "HUE", "SATURATION": "SATURATION", "CTO": "CTO", "SHUTTER_OPEN": "SHUTTER", "STROBE": "STROBE", "IRIS": "IRIS",
             "FOCUS": "FOCUS", "GOBO": "GOBO1", "GOBO_SPIN": "GOBO1", "GOBO_2": "GOBO2", "GOBO_2_SPIN": "GOBO2"}

# The colour macro (17 fixed colours) behaves as a colour wheel: COLOR1 ("C1"), one channel set per colour.
MACRO_ATTR = ("COLOR", "COLOR1", "COLOR1")          # UNCONFIRMED that dot2 accepts COLOR1 without a <Wheels> block
ZONE_ATTR = {"ZONE_R": "RED", "ZONE_G": "GREEN", "ZONE_B": "BLUE", "ZONE_W": "WHITE"}


def dmx24(v8, top=False):
    """8-bit DMX value -> MA 24-bit range bound (the top of a range covers the whole 8-bit step)."""
    return (v8 << 16) | (0xFFFF if top else 0)


def a(**kw):
    return "".join(" %s=%s" % (k, quoteattr(str(v))) for k, v in kw.items() if v is not None)


def g(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s



# rev. 22: slot names of each fixture's own gobo wheel (tools/gobos/wheels.json, as GoboWheels.java)
_WHEELS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "gobos", "wheels.json")))


def GOBO_NAMES(f, second=False):
    w = next((v for k, v in _WHEELS.items() if f["id"] in v["fixtures"] and k.endswith("_2") == second), None)
    n = 8 if second else f["gobos"]
    return ["Gobo %d %s" % (k, w["names"][k - 1]) if w and k <= len(w["names"]) else "Gobo %d" % k for k in range(1, n + 1)]

def WHEEL_FILES(f, second=False):
    """rev. 25b: the texture files of a fixture's gobo wheel (tools/gobos/wheels.json), None when it has no wheel of its own."""
    w = next((v for k, v in _WHEELS.items() if f["id"] in v["fixtures"] and k.endswith("_2") == second), None)
    return w["files"] if w else None


# rev. 25b: the gobo pictures for dot2 / grandMA2. An MA fixture type shows the gobos of a wheel (encoder view, gobo pool, presets)
# only through a <Wheels> block: one <Slot media_filename=...> per gobo, the picture being a file of the desk's "gobos" folder.
# Without it dot2 printed "No matching fixture selected" in the gobo view for every mode (12 November, picture of the user).
# Format taken from the MA library of dot2 1.9 (robe@robin_pointe@mode_1): ChannelFunction wheel="n" (1-based), ChannelSet
# slot_index (0 = open), Wheels after Instances, Slot media_name / media_filename relative to the gobos folder.
MA_GOBO_DIR = "user_gobos/CSC"


def ma_gobo_file(name):
    return "%s/%s.png" % (MA_GOBO_DIR, name)


class Func:
    """One ChannelFunction: DMX range (8-bit, inclusive), display range, physical range, subattribute, channel sets."""
    def __init__(self, lo, hi, frm, to, pfrom, pto, sub, name=None, sets=()):
        self.lo, self.hi, self.frm, self.to, self.pfrom, self.pto, self.sub, self.name, self.sets = lo, hi, frm, to, pfrom, pto, sub, name, list(sets)
        self.slot = None            # rev. 25b: slot of the gobo wheel this range selects (0 = open), None when not a wheel position
        self.spin = False           # a "gobo n rotating" range: same slot, not offered again in the gobo pool (auto_create false)


class MaXmlExporter:
    software = "MA2"
    folder = "library"
    version = (3, 9, 60)            # grandMA2 3.9.60 (current grandMA2 onPC); older grandMA2 versions refuse it
    highlight = True

    def __init__(self, modes):
        self.modes = modes
        self.issues = []

    # ------------------------------------------------------------------ names
    @staticmethod
    def file_name(f, mode_name):
        model = f["name"].lower().replace(" ", "_")
        return "%s@%s@%s.xml" % (MANUFACTURER.lower().replace(" ", "_"), model, FILE_MODE[mode_name])

    @staticmethod
    def short_name(f):
        return f["name"].replace("CSC ", "").replace(" ", "")[:16]

    # ------------------------------------------------------------------ channel functions (from the DmxMode value mappings)
    def functions(self, f, attr):
        m = self.modes
        if attr in ("GOBO_2","GOBO_2_ROT","PRISM_2","PRISM_2_ROT"):
            primary={"GOBO_2":"GOBO","GOBO_2_ROT":"GOBO_ROT","PRISM_2":"PRISM","PRISM_2_ROT":"PRISM_ROT"}[attr]
            result=self.functions(dict(f,prism_facets=6,gobo_slot_names=GOBO_NAMES(f,True) if attr=="GOBO_2" else None,gobos=8 if attr=="GOBO_2" else f["gobos"]),primary)
            for fn in result: fn.sub=fn.sub.replace("GOBO1","GOBO2").replace("PRISMA1","PRISMA2")
            return result
        if attr == "PAN":
            h = f["pan"] / 2
            return [Func(0, 255, -h, h, -h, h, "PAN")]
        if attr == "TILT":
            h = f["tilt"] / 2
            return [Func(0, 255, -h, h, -h, h, "TILT")]
        if attr == "STROBE":
            so = m["strobe_open"]
            return [Func(0, so - 1, 1, 1, 1, 1, "SHUTTER", "Open", [("Open", 0, so - 1)]),
                    Func(so, 255, 1, f["strobe_hz"], 1, f["strobe_hz"], "STROBE", "Strobe")]
        if attr == "ZOOM":
            return [Func(0, 255, f["zoom"][0], f["zoom"][1], f["zoom"][0], f["zoom"][1], "ZOOM")]
        if attr == "CTO":
            return [Func(0, 255, 0, 100, 0, 1, "CTO", "CTO 6500 K - 2700 K")]
        if attr == "GOBO":
            st = m["gobo_step"]
            # one function per slot (DmxMode.GOBO_STEP wide, the last slot to 255), display / physical value = slot number
            out = []
            for k in range(0, f["gobos"] + 1):
                lo, hi = k * st, 255 if k == f["gobos"] else k * st + st - 1
                n = "Open" if k == 0 else (f.get("gobo_slot_names") or GOBO_NAMES(f))[k - 1]
                fn = Func(lo, hi, k, k, k, k, "GOBO1", n, [(n, lo, hi)])
                fn.slot = k
                out.append(fn)
            return out
        if attr in ROT_DEG_S:
            lo, hi = m["rot_stop"]
            rpm = ROT_DEG_S[attr] * 60.0 / 360.0
            sub = "GOBO1_ROT" if attr == "GOBO_ROT" else "PRISMA1_ROT"
            # DmxMode.decode: 0 = full speed one way, linear to 0 at the stop band, then 0 to full speed the other way at 255
            slow = rpm * (1.0 - (lo - 1) / float(lo))
            return [Func(0, lo - 1, -rpm, -slow, -rpm, -slow, sub, "Rotate CCW"),
                    Func(lo, hi, 0, 0, 0, 0, sub, "Stop", [("Stop", lo, hi)]),
                    Func(hi + 1, 255, rpm / (255 - hi), rpm, rpm / (255 - hi), rpm, sub, "Rotate CW")]
        if attr == "COLOR_WHEEL":
            return self.macro_functions()
        if attr == "LASER_PATTERN":
            names=["Dot", "Fan", "Circle", "Cross", "Square"]
            return [Func(i*51, 255 if i==4 else (i+1)*51-1, i, i, i, i, "GOBO1", n) for i,n in enumerate(names)]
        if attr == "FRAME_ROT":
            return [Func(0,255,-90,90,-90,90,"SHAPER_ROT")]
        if attr == "PRISM":
            pi = m["prism_in"]
            return [Func(0, pi - 1, 0, 0, 0, 0, "PRISMA1", "Open", [("Open", 0, pi - 1)]),
                    Func(pi, 255, 1, 1, 1, 1, "PRISMA1", "%d-Facet" % f.get("prism_facets",3), [("%d-Facet" % f.get("prism_facets",3), pi, 255)])]
        # linear 0..100 % (dimmer, colour mix, focus, iris)
        return [Func(0, 255, 0, 100, 0, 1, MA_ATTR[attr][2])]

    def packed_functions(self, c):
        """rev. 17: one ChannelFunction per range of a packed Compact channel (DmxMode.ranges), a channel set on each fixed position."""
        out = []
        for r in c["ranges"]:
            w = r["what"]
            if w == "SATURATION":
                frm, to = 0, 100
            elif w == "IRIS":
                frm, to = 100, 0
            elif w == "FOCUS":
                frm, to = 0, 100
            elif w in ("GOBO_SPIN", "GOBO_2_SPIN"):
                # rev. 25: a "gobo n rotating" range of the packed Compact channel is still gobo n for the console: its physical
                # value is the slot (as the fixed range), so dot2 / grandMA2 keep the gobo in their gobo view and pool. Before, the
                # rotation speed was given as the physical value of the GOBO1 select function (3.3 to 36.7): dot2 read it as slot
                # numbers and showed no gobo for the Compact patch (12 November).
                frm = to = int(r["slot"])
            elif w.endswith("_SPIN"):
                frm, to = r["from"] * 60.0 / 360.0, r["to"] * 60.0 / 360.0
            else:
                frm, to = r["from"], r["to"]
            fixed = w in ("GOBO", "GOBO_2", "SHUTTER_OPEN", "GOBO_SPIN", "GOBO_2_SPIN")
            sets = [(r["name"], r["lo"], r["hi"])] if fixed else []
            if w == "HUE":
                # rev. 3 (13 November): dot2's colour picker does not turn its colour into these hue / saturation channels right; named
                # hues on the hue channel (DmxMode.hueRgb: raw = degrees * 256 / 360) give buttons that set the exact value
                sets = [(n, v, v) for n, v in (("Red", 0), ("Orange", 21), ("Yellow", 43), ("Green", 85), ("Cyan", 128),
                                                  ("Blue", 171), ("Magenta", 213))]
            f_ = Func(r["lo"], r["hi"], frm, to, frm, to, RANGE_SUB[w], r["name"], sets)
            if w in ("GOBO_2", "GOBO_2_SPIN"): f_.attr = ("GOBO", "GOBO2", "GOBO2")      # rev. 25: wheel 2 gobos keep their own wheel
            # rev. 3 (13 November, dot2: a wash in Compact showed "Saturation 2" scaled 6500 K, the colour picker went wrong and pulled the
            # dimmer): the white CTO range of the saturation channel is its own attribute, CTO, so SATURATION has one function only
            if w == "CTO": f_.attr = MA_ATTR["CTO"]
            if w in ("GOBO", "GOBO_2", "GOBO_SPIN", "GOBO_2_SPIN"):
                f_.slot = int(r.get("slot", frm))
                f_.spin = w.endswith("_SPIN")
            out.append(f_)
        return out

    def macro_functions(self):
        m = self.modes
        st = m["macro_step"]
        n = len(m["macro"])
        out = []
        for i, mc in enumerate(m["macro"]):
            lo, hi = i * st, 255 if i == n - 1 else i * st + st - 1
            out.append(Func(lo, hi, i, i, i, i, "COLOR1", mc["name"], [(mc["name"], lo, hi)]))
        return out

    @staticmethod
    def merge_wheel_functions(funcs, wheels):
        """rev. 25c: consecutive wheel positions of the same wheel (open, gobos, gobos rotating) become one select function."""
        out = []
        for fn in funcs:
            if fn.slot is None or fn.sub not in wheels:
                out.append(fn)
                continue
            prev = out[-1] if out else None
            sets = [(sn, lo, hi, fn.slot, fn.spin) for (sn, lo, hi) in fn.sets] or [(fn.name, fn.lo, fn.hi, fn.slot, fn.spin)]
            if prev is not None and getattr(prev, "merged", False) and prev.sub == fn.sub and prev.hi + 1 == fn.lo \
                    and getattr(prev, "attr", None) == getattr(fn, "attr", None):
                prev.hi = fn.hi
                prev.sets += sets
                continue
            m = Func(fn.lo, fn.hi, 0, 0, 0, 0, fn.sub, "Gobo wheel %d" % (2 if fn.sub == "GOBO2" else 1), sets)
            m.slot, m.merged = fn.slot, True
            if getattr(fn, "attr", None):
                m.attr = fn.attr
            out.append(m)
        return out

    # ------------------------------------------------------------------ one channel type
    def channel_type(self, w, idx, coarse, fine, names, default8, wide, funcs, ind):
        preset, feature, attribute = names
        wheels = getattr(self, "_wheels", {})
        default = default8 * 256 if wide else default8
        w('%s<ChannelType%s>\n' % (ind, a(index=idx, attribute=attribute, feature=feature, preset=preset, coarse=coarse, fine=fine,
                                           default=default, highlight_value=(65535 if wide else 255) if attribute == "DIM" and self.highlight else None)))
        funcs = self.merge_wheel_functions(funcs, wheels)
        for fi, fn in enumerate(funcs):
            fp, ff, fa = getattr(fn, "attr", None) or (preset, feature, attribute)
            wheel = wheels.get(fn.sub) if fn.slot is not None else None
            if getattr(fn, "merged", False):
                # rev. 25c: the wheel select function as in the MA library (robe@robin_pointe): no physical range, display range in
                # percent of the channel, one channel set per wheel position with its slot_index. dot2 lists the gobos of a wheel from
                # the channel sets of this function only: with one function per gobo (each with a physical value) it showed "Open" alone
                w('%s  <ChannelFunction%s>\n' % (ind, a(index=fi, name=fn.name, **{"from": g(fn.lo / 2.56)}, to=g((fn.hi + 1) / 2.56),
                                                        min_dmx_24=dmx24(fn.lo), max_dmx_24=dmx24(fn.hi, True),
                                                        subattribute=fn.sub, attribute=fa, feature=ff, preset=fp, wheel=wheel)))
                k = 256 if wide else 1
                for si, (sn, lo, hi, slot, spin) in enumerate(fn.sets):
                    # rev. 3 (seen 13 November in dot2: each gobo shown 3 times): dot2 makes a gobo tile, with the slot's name and picture,
                    # of every channel set that names a slot, auto_create or not. The rotation ranges keep their own names, no slot.
                    sl = {} if spin else dict(slot_index=slot, slot_from=0, slot_to=0)
                    w('%s    <ChannelSet%s/>\n' % (ind, a(index=si, name=sn, from_dmx=lo * k, to_dmx=hi * k + (k - 1), **sl)))
                w('%s  </ChannelFunction>\n' % ind)
                continue
            w('%s  <ChannelFunction%s>\n' % (ind, a(index=fi, name=fn.name, **{"from": g(fn.frm)}, to=g(fn.to),
                                                    min_dmx_24=dmx24(fn.lo), max_dmx_24=dmx24(fn.hi, True), physfrom=g(fn.pfrom), physto=g(fn.pto),
                                                    subattribute=fn.sub, attribute=fa, feature=ff, preset=fp, wheel=wheel)))
            for si, (sn, lo, hi) in enumerate(fn.sets):
                k = 256 if wide else 1
                slot = dict(slot_index=fn.slot, slot_from=0, slot_to=0) if wheel and not fn.spin else {}
                w('%s    <ChannelSet%s/>\n' % (ind, a(index=si, name=sn, from_dmx=lo * k, to_dmx=hi * k + (k - 1), **slot)))
            w('%s  </ChannelFunction>\n' % ind)
        w('%s</ChannelType>\n' % ind)

    # ------------------------------------------------------------------ one fixture type / mode
    def fixture_xml(self, f, md):
        chans = md["channels"]
        main = [c for c in chans if c["zone"] < 0]
        zones = [c for c in chans if c["zone"] >= 0]
        # rev. 16g: named instances of the mode (DmxMode.instances): their channels leave the main module for a sub module each
        named = list(md.get("instances", []))
        # rev. 3 (13 November, dot2: picker and HSB faders gave wrong colours on Compact washes / VORTEX and pulled the dimmer; the
        # PULSESTRIKE M LED bars, whose hue / saturation are a sub-fixture without dimmer, were right): hue + saturation of the main
        # channels become a "Colour" sub-fixture the same way. Same DMX, the console description only.
        taken = {ch for ins in named for ch in ins["channels"]}
        ks = [c["kind"] for c in chans]
        # 1.2.0 beta 2b (13 November, 23 h 28): the "Colour" sub-fixture is withdrawn at the author's request — a Compact wash or gobo
        # fixture is ONE instance on the console again (hue and saturation back in the main module, same DMX).
        if SPLIT_COLOUR and "HUE" in ks and "SATURATION" in ks:
            h, sa = ks.index("HUE"), ks.index("SATURATION")
            if sa == h + 1 and h not in taken and sa not in taken and chans[h]["zone"] < 0:
                named.append({"name": "Colour", "channels": [h, sa]})
        in_instance = {ch for ins in named for ch in ins["channels"]}
        # the main module is the leading channels; zones (FULL only) follow, each zone contiguous
        assert all(c["zone"] < 0 for c in chans[:len(main)]), "zone channels must follow the main channels"
        offsets = {}
        for i, c in enumerate(chans):
            key = ("F" if c["kind"] == "FINE" else "C", c["attr"]) if c["zone"] < 0 else None
            if key:
                offsets[key] = i + 1
        cls, beamtype = MAIN_MODULE.get(f["id"], ("Headmover", "Wash"))
        # rev. 25b: the gobo wheels of the fixture (1-based numbers used by ChannelFunction wheel="n")
        wheel_list = []
        for sub, second in (("GOBO1", False), ("GOBO2", True)):
            files = WHEEL_FILES(f, second)
            uses = any((c["attr"] == ("GOBO_2" if second else "GOBO")) or (c["kind"] == "GOBO_FX" and any(
                r["what"] in (("GOBO_2", "GOBO_2_SPIN") if second else ("GOBO", "GOBO_SPIN")) for r in c.get("ranges", []))) for c in chans)
            if files and uses:
                wheel_list.append((sub, files, GOBO_NAMES(f, second)))
        self._wheels = {sub: i + 1 for i, (sub, _, _) in enumerate(wheel_list)}
        out = []
        w = out.append
        w('<?xml version="1.0" encoding="utf-8"?>\n')
        w(ns_header(*self.version) + '\n')
        w('  <Info%s/>\n' % a(datetime=STAMP, showfile="Concert Stage Craft export"))
        w('  <FixtureType%s>\n' % a(name=f["name"], mode=md["name"]))
        w('    <InfoItems>\n')
        w('      <Info%s>%s</Info>\n' % (a(type="Description", date=STAMP), self.description(f, md)))
        w('    </InfoItems>\n')
        w('    <short_name>%s</short_name>\n' % self.short_name(f))
        w('    <manufacturer>%s</manufacturer>\n' % MANUFACTURER)
        w('    <short_manufacturer>%s</short_manufacturer>\n' % SHORT_MANUFACTURER)
        w('    <Modules index="0">\n')
        w('      <Module%s>\n' % a(index=0, name="Main", **{"class": cls}, beamtype=beamtype,
                                    beam_angle=g(f["zoom"][0] + f.get("home_zoom", 0.35) * (f["zoom"][1] - f["zoom"][0])),
                                    beam_intensity=NOMINAL_BEAM_INTENSITY))
        # 1.2.0 beta 2b (14 November, advice of a console operator): no RGB on the base of a pixel fixture — it disturbs a lot (picker,
        # effects, presets fight with the cells). The colour channels the RGB cells also have are left out of the main module (same DMX:
        # those base channels are simply not driven by the console, and the game takes the brighter of base and cell), and every RGB
        # cell gets a virtual dimmer (VDIM: a dimmer of the console with no DMX address, scaling the cell's RGB).
        cell_attrs = set()
        if PIXEL_VDIM and zones:
            for c in zones:
                at = ZONE_ATTR.get(c["kind"])
                if at in ("RED", "GREEN", "BLUE", "WHITE"):
                    cell_attrs.add(at)
        idx = 0
        main_attrs = {c["attr"] for i, c in enumerate(main) if c["kind"] != "FINE" and i not in in_instance}
        if VIRTUAL_PAN and "TILT" in main_attrs and "PAN" not in main_attrs:
            w('        <ChannelType%s>\n' % a(index=idx, attribute="PAN", feature="POSITION", preset="POSITION"))
            w('          <ChannelFunction%s>\n' % a(index=0, **{"from": 0}, to=100, min_dmx_24=0, max_dmx_24=16777215, physfrom=0, physto=0,
                                                      subattribute="PAN", attribute="PAN", feature="POSITION", preset="POSITION"))
            w('            <ChannelSet%s/>\n' % a(index=0, name="no feature", from_dmx=0, to_dmx=16777215))
            w('          </ChannelFunction>\n')
            w('        </ChannelType>\n')
            idx += 1
        for i, c in enumerate(main):
            if c["kind"] == "FINE" or i in in_instance:
                continue
            if c["kind"] != "COLOR_MACRO" and not c.get("ranges") and c["attr"] in cell_attrs:
                continue
            ind = "        "
            if c["kind"] == "COLOR_MACRO":
                self.channel_type(w, idx, i + 1, None, MACRO_ATTR, c["default"], False, self.macro_functions(), ind)
            elif c.get("ranges"):
                self.channel_type(w, idx, i + 1, None, PACKED_ATTR[c["kind"]], c["default"], False, self.packed_functions(c), ind)
            else:
                fine = offsets.get(("F", c["attr"]))
                self.channel_type(w, idx, i + 1, fine, MA_ATTR[c["attr"]], c["default"], fine is not None,
                                  self.functions(f, c["attr"]), ind)
            idx += 1
        w('      </Module>\n')
        # LED sub modules: one module per zone layout (RGB cell, white segment), one instance per zone
        instances = []
        layouts = []
        if zones:
            by_zone = {}
            for i, c in enumerate(chans):
                if c["zone"] >= 0:
                    by_zone.setdefault(c["zone"], []).append((i + 1, c))
            for z in sorted(by_zone):
                layout = tuple(c["kind"] for _, c in by_zone[z])
                if layout not in layouts:
                    layouts.append(layout)
                instances.append((layouts.index(layout) + 1, by_zone[z][0][0], by_zone[z][0][1]["name"].rsplit(" ", 1)[0] if layout != ("ZONE_LEVEL",) else by_zone[z][0][1]["name"]))
            for li, layout in enumerate(layouts):
                rgb = layout != ("ZONE_LEVEL",)
                w('      <Module%s>\n' % a(index=li + 1, name="RGB cell" if rgb else "White segment", **{"class": "LED"}, beamtype="Wash",
                                            beam_angle=g(f["zoom"][1]), beam_intensity=NOMINAL_BEAM_INTENSITY))
                for ci, k in enumerate(layout):
                    attr = ZONE_ATTR.get(k, "DIMMER")
                    self.channel_type(w, ci, ci + 1, None, MA_ATTR[attr], 0, False, self.functions(f, attr), "        ")
                if rgb and PIXEL_VDIM:
                    # virtual dimmer of the cell: no coarse channel (as robe / chauvet pixel fixtures of the MA library)
                    w('        <ChannelType%s>\n' % a(index=len(layout), attribute="DIM", feature="DIMMER", preset="DIMMER", highlight_value=100))
                    w('          <ChannelFunction%s>\n' % a(index=0, **{"from": 0}, to=100, min_dmx_24=0, max_dmx_24=16777215, physfrom=0, physto=1,
                                                              subattribute="DIM", attribute="DIM", feature="DIMMER", preset="DIMMER"))
                    w('          </ChannelFunction>\n')
                    w('        </ChannelType>\n')
                w('      </Module>\n')
        # rev. 16g: one sub module per named instance (PULSESTRIKE M Compact: LED bars, white bar), patched at its first channel
        for ni, ins in enumerate(named):
            mi = len(layouts) + 1 + ni
            chs = ins["channels"]
            assert chs == list(range(chs[0], chs[0] + len(chs))), "an instance must be a run of contiguous channels"
            w('      <Module%s>\n' % a(index=mi, name=ins["name"], **{"class": "LED"}, beamtype="Wash",
                                        beam_angle=g(f["zoom"][1]), beam_intensity=NOMINAL_BEAM_INTENSITY))
            for ci, ch in enumerate(chs):
                c = chans[ch]
                if c.get("ranges"):
                    self.channel_type(w, ci, ci + 1, None, PACKED_ATTR[c["kind"]], c["default"], False, self.packed_functions(c), "        ")
                else:
                    self.channel_type(w, ci, ci + 1, None, MA_ATTR[c["attr"]], c["default"], False, self.functions(f, c["attr"]), "        ")
            w('      </Module>\n')
            instances.append((mi, chs[0] + 1, ins["name"]))
        w('    </Modules>\n')
        w('    <Instances index="1">\n')
        w('      <Instance%s/>\n' % a(index=0, module_index=0, patch=1))
        for ii, (mi, patch, name) in enumerate(instances):
            w('      <Instance%s/>\n' % a(index=ii + 1, module_index=mi, patch=patch, parent_instance=0))
        w('    </Instances>\n')
        if wheel_list:
            w('    <Wheels index="2">\n')
            for wi, (sub, files, names) in enumerate(wheel_list):
                w('      <Wheel%s>\n' % a(index=wi, name="Gobo wheel %d" % (wi + 1), subattribute=sub, attribute=sub, feature=sub, preset="GOBO"))
                w('        <Slot%s/>\n' % a(index=0, media_name="Open"))
                for si, (fl, nm) in enumerate(zip(files, names)):
                    w('        <Slot%s/>\n' % a(index=si + 1, media_name=nm, media_filename=ma_gobo_file(fl)))
                w('      </Wheel>\n')
            w('    </Wheels>\n')
        self._wheels = {}
        w('  </FixtureType>\n')
        w('</MA>\n')
        self.check(f, md)
        return "".join(out)

    def description(self, f, md):
        return "%s - %s mode, %d DMX channels. Generated by Concert Stage Craft 1.2.0 from the game DMX tables (%s)." % (
            f["name"], md["name"], len(md["channels"]), self.software)

    def check(self, f, md):
        pass

    # ------------------------------------------------------------------ channel correspondence table (README in the export folder)
    def table(self, f, md):
        chans = md["channels"]
        rows = ["| Ch | CSC channel | Preset | Feature | Attribute | Res. | Default | Ranges |", "|---|---|---|---|---|---|---|---|"]
        fine_of = {}
        for i, c in enumerate(chans):
            if c["kind"] == "FINE":
                fine_of[c["attr"]] = i + 1
        for i, c in enumerate(chans):
            k = c["kind"]
            if k == "FINE":
                names, res, rng = MA_ATTR[c["attr"]], "fine", "fine byte of ch %d" % next(j + 1 for j, cc in enumerate(chans) if cc["kind"] == "ATTR" and cc["attr"] == c["attr"])
            elif k == "COLOR_MACRO":
                names, res = MACRO_ATTR, "8 bit"
                rng = ", ".join("%d-%d %s" % (fn.lo, fn.hi, fn.name) for fn in self.macro_functions())
            elif c.get("ranges"):
                names, res = PACKED_ATTR[k], "8 bit"
                rng = ", ".join("%d-%d %s" % (fn.lo, fn.hi, fn.name) for fn in self.packed_functions(c))
            elif c["zone"] >= 0:
                attr = ZONE_ATTR.get(k, "DIMMER")
                names, res, rng = MA_ATTR[attr], "8 bit", "0-255 = 0-100 %% (sub-fixture, zone %d)" % (c["zone"] + 1)
            else:
                names = MA_ATTR[c["attr"]]
                res = "16 bit" if c["attr"] in fine_of else "8 bit"
                rng = ", ".join("%d-%d %s" % (fn.lo, fn.hi, fn.name or ("%s to %s" % (g(fn.pfrom), g(fn.pto)))) for fn in self.functions(f, c["attr"]))
            ins = next((x["name"] for x in md.get("instances", []) if i in x["channels"]), None)
            if ins:
                rng += " (instance: %s)" % ins
            rows.append("| %d | %s | %s | %s | %s | %s | %d | %s |" % (i + 1, c["name"], names[0], names[1], names[2], res, c["default"], rng))
        return "\n".join(rows)

    def readme(self, written):
        t = []
        w = t.append
        w("CONCERT STAGE CRAFT - %s fixture types (MA2 fixture XML)\n" % self.software)
        w("STATUS: generated from the game DMX tables (DmxMode). NOT yet imported into a real %s: test before using on a show.\n" % self.software)
        w("Install: copy the .xml files to the %s folder of a USB stick (or of the %s onPC data folder), then in %s:" % (self.folder, self.software, self.software))
        w("Setup > Patch > Select Fixture Type > Import (title bar icon) > choose the drive > pick the type and mode.")
        w("Files written for %s %d.%d.%d (major_vers / minor_vers / stream_vers): an MA desk refuses a fixture type written by a newer version than its own." % ((self.software,) + self.version))
        if self.software == "dot2":
            w("dot2 onPC: run it as administrator, join a session (Setup > Sessions), then Setup > Patch & Fixture Schedule > Add New Fixtures >")
            w("Select Other > Drive (bottom left: drag up to the USB stick) > CSC type > mode > OK. Without a USB stick, the files can go to the")
            w("library folder of dot2 onPC: C:\\ProgramData\\MA Lighting Technologies\\dot2\\<dot2 version>\\library (hidden folder).")
            w("If a type still does not appear or OK does nothing: open the .xml in the free MA Fixture Builder (File > Import) and Export to USB")
            w("Drive: it writes the file for its own dot2 version. Please send the result (or one fixture exported by your dot2) to fix the generator.")
        w("")
        w("Files:")
        for n, fx, mode, count in written:
            w("  %-62s %-18s %-14s %3d channels" % (n, fx, mode, count))
        w("Gobo pictures (rev. 25b): the gobo wheels of the VORTEX X1, PHANTOM S1 (2 wheels), PROFILE P1 and RAZOR B1 are <Wheels> blocks whose")
        w("slots point to gobos/user_gobos/CSC/<name>.png. Copy the folder gobos/user_gobos/CSC of the export into the gobos folder of the desk")
        w("(dot2 onPC: C:\\ProgramData\\MA Lighting Technologies\\dot2\\<version>\\gobos) BEFORE importing the types: the gobo view, pool and presets")
        w("then show the pictures.")
        w("\nVirtual Matrix mode: no channel of its own, nothing to import (the CSC Virtual Matrix is patched as its own 16 channels, see EXTERNAL_SHOW_CONTROL_FR.md).")
        w("\nUnconfirmed names (check first if an import fails): CTO (COLOR / CTO / CTO), colour macro as COLOR1 without a colour <Wheels> entry,")
        w("ChannelSet attributes from_dmx / to_dmx, beam_intensity = %d is a nominal 3D value (no photometric data in the game)." % NOMINAL_BEAM_INTENSITY)
        w("LED cells / zones (Full mode) are sub-fixtures (class LED, parent instance 0) without 3D offsets. Each RGB cell has a virtual")
        w("dimmer (DIM with no DMX address, as the pixel fixtures of the MA library). Full of the pixel fixtures (1.2.0 beta 2b) = a pixel mode:")
        w("no master dimmer and no base colour the cells already have (in the game DMX itself), each cell is its own colour and intensity.")
        w("PULSESTRIKE M / FLASHCORE FX Full: one main fixture (tilt, zoom, focus, CTO, white bar dimmer, strobe), then the RGB cells and the")
        w("white segments; no LED bar sub-fixtures. A fixture with a tilt and no pan gets a virtual pan (the dot2 Position tab needs both).")
        w("PULSESTRIKE M Compact: two sub-fixtures, 1 = LED bars (hue, saturation), 2 = white bar (white, CTO); tilt, dimmer, strobe and zoom on the fixture.")
        w("Compact (rev. 17): 8 channels at most per fixture (64 fixtures per universe). Colour = hue + saturation (white at saturation 0, CTO at")
        w("the end of the saturation channel when the fixture has one); strobe channel with iris / focus ranges; one gobo channel for every gobo")
        w("of every wheel, each with a still range and two rotation ranges. HUE / SATURATION attribute names are UNCONFIRMED in MA2.\n")
        for f in self.modes["fixtures"]:
            for md in f["modes"][:EXPORTED_MODES]:
                if md["channels"]:
                    w("\n%s - %s (%d channels)\n" % (f["name"], md["name"], len(md["channels"])))
                    w(self.table(f, md))
        return "\n".join(t) + "\n"

    def export_all(self, out_dir):
        os.makedirs(out_dir, exist_ok=True)
        written = []
        for f in self.modes["fixtures"]:
            for md in f["modes"][:EXPORTED_MODES]:
                if not md["channels"]:
                    continue
                name = self.file_name(f, md["name"])
                open(os.path.join(out_dir, name), "w", newline="\n").write(self.fixture_xml(f, md))
                written.append((name, f["name"], md["name"], len(md["channels"])))
        open(os.path.join(out_dir, "README.md"), "w", newline="\n").write(self.readme(written))
        return written


class GrandMa2Exporter(MaXmlExporter):
    software = "grandMA2"
    folder = "gma2/library"


class Dot2Exporter(MaXmlExporter):
    software = "dot2"
    folder = "dot2/library"
    UNIVERSE = 512
    # rev. 16i: dot2 has its own version numbers (1.x) and refuses a fixture type written by a newer software: the MA Fixture Builder
    # writes the version of the dot2 it targets. 1.2.2 = the first dot2 onPC that outputs a universe without MA hardware; every later
    # dot2 accepts it. Only attributes of the oldest MA2 fixture format are written.
    version = (1, 2, 2)
    highlight = False

    def check(self, f, md):
        # dot2 onPC without MA hardware outputs one universe: a mode wider than 512 channels could not be patched at all
        if len(md["channels"]) > self.UNIVERSE:
            self.issues.append("%s %s: %d channels > one universe" % (f["name"], md["name"], len(md["channels"])))


EXPORTERS = {"gma2": GrandMa2Exporter, "dot2": Dot2Exporter}


def export_ma_gobos(root, out_dir):
    """rev. 25b: the wheel pictures for dot2 / grandMA2, as the MA library ones: 128 x 128 RGB PNG, white = light."""
    from PIL import Image
    os.makedirs(out_dir, exist_ok=True)
    tex = os.path.join(root, "common/src/main/resources/assets/concertstage/textures/lightshow")
    n = 0
    for w in _WHEELS.values():
        for fl in w["files"]:
            im = Image.open(os.path.join(tex, fl + ".png")).convert("RGBA")
            bg = Image.new("RGBA", im.size, (0, 0, 0, 255))
            bg.alpha_composite(im)
            bg.convert("RGB").resize((128, 128), Image.LANCZOS).save(os.path.join(out_dir, fl + ".png"), optimize=True)
            n += 1
    return n


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    modes = json.load(open(os.path.join(root, "tools/external/modes.json")))
    base = os.path.join(root, "common/src/main/resources/assets/concertstage/external")
    print("MA gobo pictures:", export_ma_gobos(root, os.path.join(base, "ma_gobos")))
    for key, cls in EXPORTERS.items():
        ex = cls(modes)
        written = ex.export_all(os.path.join(base, key))
        for n, fx, mode, count in written:
            print("%-8s %-70s %3d ch" % (ex.software, n, count))
        for i in ex.issues:
            print("ISSUE", ex.software, i)


if __name__ == "__main__":
    main()
