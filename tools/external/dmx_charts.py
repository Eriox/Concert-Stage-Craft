#!/usr/bin/env python3
"""DMX charts of the CSC fixtures (markdown), from tools/external/modes.json (dumped from the Java tables). Usage: see build.sh."""
import json
import os
import sys

root = sys.argv[1] if len(sys.argv) > 1 else "."
m = json.load(open(os.path.join(root, "tools/external/modes.json")))


def values(f, c, md=None):
    a, k = c["attr"], c["kind"]
    if k == "FINE":
        return "fine byte of %s" % c["name"].replace(" fine", "")
    if c.get("ranges"):
        # rev. 17: packed Compact channel (hue, saturation / CTO, strobe / iris / focus, gobos with rotation)
        def phys(r):
            w = r["what"]
            if w == "HUE": return "0° to 359° (red, yellow, green, cyan, blue, magenta)"
            if w == "SATURATION": return ""
            if w == "CTO": return "6500 K to 2700 K"
            if w == "STROBE": return "1 Hz to %g Hz" % r["to"]
            if w.endswith("_SPIN"): return "%.0f to %.0f °/s" % (abs(r["from"]), abs(r["to"]))
            return ""
        return " · ".join("%d-%d %s%s" % (r["lo"], r["hi"], r["name"], (" (" + phys(r) + ")") if phys(r) else "") for r in c["ranges"])
    if k == "COLOR_MACRO":
        return " · ".join("%d-%d %s" % (i * m["macro_step"], 255 if i == len(m["macro"]) - 1 else i * m["macro_step"] + m["macro_step"] - 1, x["name"])
                          for i, x in enumerate(m["macro"]))
    if k.startswith("ZONE"):
        # 1.2.0 beta 2b: the Full of the pixel fixtures has no main colour any more: each cell is its own colour and intensity
        if md is not None and md["name"] == "Full" and any(x["kind"] == "ZONE_R" for x in md["channels"]) \
                and not any(x["kind"] == "ATTR" and x["attr"] in ("RED", "GREEN", "BLUE") and x["zone"] < 0 for x in md["channels"]):
            return "0-255 = 0-100 % (the cell itself: no master dimmer in this mode)" if k != "ZONE_LEVEL" else "0-255 = 0-100 % (the segment, times the white bar dimmer)"
        return "0-255 (merged highest-takes-precedence with the main colour)"
    if a == "PAN":
        return "0-255 = %g° to +%g° (128 = straight ahead)" % (-f["pan"] / 2, f["pan"] / 2)
    if a == "TILT":
        return "0-255 = %g° to +%g° (128 = straight, along the base axis)" % (-f["tilt"] / 2, f["tilt"] / 2)
    if a == "ZOOM":
        return "0-255 = %g° to %g°" % tuple(f["zoom"])
    if a == "STROBE":
        return "0-%d open · %d-255 strobe 1 Hz to %g Hz (capped by the desk safety)" % (m["strobe_open"] - 1, m["strobe_open"], f["strobe_hz"])
    if a in ("GOBO", "GOBO_2"):
        st = m["gobo_step"]
        return "0-%d open · " % (st - 1) + " · ".join("%d-%d gobo %d" % (g * st, 255 if g == f["gobos"] else g * st + st - 1, g) for g in range(1, f["gobos"] + 1))
    if a in ("GOBO_ROT", "GOBO_2_ROT", "PRISM_ROT", "PRISM_2_ROT"):
        lo, hi = m["rot_stop"]
        sp = 220 if a in ("GOBO_ROT", "GOBO_2_ROT") else 160
        return "0-%d one way %d°/s to slow · %d-%d stop · %d-255 other way slow to %d°/s" % (lo - 1, sp, lo, hi, hi + 1, sp)
    if a in ("PRISM", "PRISM_2"):
        return "0-%d out · %d-255 %d-facet prism in" % (m["prism_in"] - 1, m["prism_in"], 6 if a == "PRISM_2" else f.get("prism_facets",3))
    if a in ("SHUTTER", "DOUSER"):
        return "0 = closed, 255 = open (linear transmission)"
    if a == "LASER_PATTERN":
        return "0-50 dot · 51-101 fan · 102-152 circle · 153-203 cross · 204-255 square"
    if a == "FRAME_ROT":
        return "0-255 = -90° to +90°"
    if a == "CTO":
        return "0-255 = 6500 K to 2700 K"
    if a == "IRIS":
        return "0 = smallest, 255 = open"
    return "0-255 = 0-100 %"


out = ["# CSC fixtures — DMX charts", "",
       "Generated from the DMX tables of the mod (`DmxMode.java`, the same tables the game decodes with). Channel numbers are relative to the fixture address.",
       "Virtual Matrix: see `EXTERNAL_SHOW_CONTROL_FR.md`.", ""]
for f in m["fixtures"]:
    out.append("## %s — %s" % (f["name"], f["function"]))
    out.append("")
    for md in f["modes"][:3]:
        out.append("### %s — %d channels" % (md["name"], len(md["channels"])))
        out.append("")
        out.append("| Ch | Function | Default | Values |")
        out.append("|---:|---|---:|---|")
        for i, c in enumerate(md["channels"]):
            out.append("| %d | %s | %d | %s |" % (i + 1, c["name"], c["default"], values(f, c, md)))
        if md["missing"]:
            out.append("")
            out.append("Not reachable in this mode: " + "; ".join(md["missing"]) + ".")
        out.append("")
out.append("## CSC VIRTUAL MATRIX — 16 channels")
out.append("")
out.append("| Ch | Function |")
out.append("|---:|---|")
for i, n in enumerate(m["matrix"]):
    out.append("| %d | %s |" % (i + 1, n))
print("\n".join(out))
