"""Rev. 25: in the grandMA2 / dot2 profiles, every function of a gobo wheel (attribute GOBO1 / GOBO2, subattribute = the wheel) has
one select function per wheel (rev. 25c) whose channel sets carry the slots, in every mode: dot2 builds its gobo view from them (12 November: the Compact
patch showed no gobo, its 'rotating' ranges had the rotation speed as physical value)."""
import glob, os, re, sys
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "common", "src", "main", "resources", "assets", "concertstage", "external")
fails = checks = 0
for folder in ("gma2", "dot2"):
    for f in sorted(g for g in glob.glob(os.path.join(root, folder, "*.xml")) if any(k in g for k in ("vortex", "phantom", "profile_p1", "razor"))):
        s = open(f, encoding="utf-8").read()
        for m in re.finditer(r'<ChannelFunction ([^>]*)>(.*?)</ChannelFunction>', s, re.S):
            at = dict(re.findall(r'(\w+)="([^"]*)"', m.group(1)))
            if at.get("subattribute") not in ("GOBO1", "GOBO2"):
                continue
            checks += 1
            # rev. 25c: the select function of a wheel, as in the MA library: wheel="n", no physical range, a channel set with a
            # slot_index for each position (dot2 lists the gobos from these sets)
            sets = re.findall(r'<ChannelSet ([^>]*)/>', m.group(2))
            ok = "wheel" in at and "physfrom" not in at and sets and all("slot_index=" in x for x in sets) and at["attribute"] == at["subattribute"]
            if not ok:
                fails += 1
                print("FAIL", os.path.basename(f), at.get("name"))
print("MA gobo select functions: %d checked, %d wrong" % (checks, fails))
_first_fails = fails


# rev. 25b: every gobo fixture type has a <Wheels> block, every GOBO1 / GOBO2 select function points to an existing wheel, every
# channel set of it has a slot of that wheel, and every slot picture exists in assets/.../external/ma_gobos
def check_wheels():
    import glob, os, xml.etree.ElementTree as ET
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "common/src/main/resources/assets/concertstage/external")
    ns = "{http://schemas.malighting.de/grandma2/xml/MA}"
    bad, n = [], 0
    for sw in ("dot2", "gma2"):
        for p in glob.glob(os.path.join(base, sw, "*.xml")):
            if not any(k in p for k in ("vortex_x1", "phantom_s1", "profile_p1", "razor_b1")):
                continue
            t = ET.parse(p).getroot()
            wheels = t.findall(".//%sWheel" % ns)
            if not wheels:
                bad.append("%s: no <Wheels>" % os.path.basename(p)); continue
            for w in wheels:
                for s in w.findall("%sSlot" % ns):
                    f = s.get("media_filename")
                    if f and not os.path.exists(os.path.join(base, "ma_gobos", os.path.basename(f))):
                        bad.append("%s: missing picture %s" % (os.path.basename(p), f))
            for fn in t.findall(".//%sChannelFunction" % ns):
                if fn.get("subattribute") not in ("GOBO1", "GOBO2"):
                    continue
                n += 1
                wi = fn.get("wheel")
                if wi is None or not (1 <= int(wi) <= len(wheels)):
                    bad.append("%s: %s without wheel" % (os.path.basename(p), fn.get("name"))); continue
                slots = len(wheels[int(wi) - 1].findall("%sSlot" % ns))
                for cs in fn.findall("%sChannelSet" % ns):
                    si = cs.get("slot_index")
                    if si is None or not (0 <= int(si) < slots):
                        bad.append("%s: %s slot %s" % (os.path.basename(p), cs.get("name"), si))
    for b in bad[:20]:
        print("  WRONG", b)
    print("MA gobo wheels: %d select functions linked, %d wrong" % (n, len(bad)))
    return not bad


if __name__ == "__main__":
    ok = check_wheels()
    sys.exit(1 if (_first_fails or not ok) else 0)
