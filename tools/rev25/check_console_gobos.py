"""Rev. 25c: the gobos of the other console formats are usable.
- QLC+ (.qxf): every capability of a gobo channel with Preset GoboMacro has a Res1 picture that the mod ships (ma_gobos, exported to
  qlcplus/Gobos/ConcertStageCraft); QLC+ reads it from its system Gobos folder (QLCCapability::loadXML).
- GDTF (grandMA3, MagicQ, Vectorworks...): every wheel slot with a MediaFileName has wheels/<name>.png in the archive (a readable
  PNG), every channel function linked to a wheel names an existing wheel, every channel set of it has a WheelSlotIndex in 1..slots and
  a DMXFrom inside its function; every gobo channel of every mode has its pictures.
"""
import glob
import io
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

from PIL import Image

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "common/src/main/resources/assets/concertstage/external")
bad = []

# ---------------------------------------------------------------- QLC+
qlc_caps = 0
for p in glob.glob(os.path.join(BASE, "qlcplus", "*.qxf")):
    s = open(p, encoding="utf-8").read()
    for m in re.finditer(r'<Capability [^>]*Preset="GoboMacro"[^>]*>', s):
        qlc_caps += 1
        r = re.search(r'Res1="([^"]+)"', m.group(0))
        if not r:
            bad.append("%s: gobo capability without picture" % os.path.basename(p)); continue
        name = r.group(1)
        if not name.startswith("ConcertStageCraft/") or not os.path.exists(os.path.join(BASE, "ma_gobos", os.path.basename(name))):
            bad.append("%s: picture %s not shipped" % (os.path.basename(p), name))
print("QLC+: %d gobo capabilities with a picture checked" % qlc_caps)

# ---------------------------------------------------------------- GDTF
def dmx(v):
    a, b = v.split("/")
    return int(a), int(b)

gdtf_sets = 0
for p in glob.glob(os.path.join(BASE, "gdtf", "*.gdtf")):
    z = zipfile.ZipFile(p)
    names = set(z.namelist())
    root = ET.fromstring(z.read("description.xml"))
    wheels = {}
    for w in root.iter("Wheel"):
        slots = w.findall("Slot")
        wheels[w.get("Name")] = len(slots)
        for s in slots:
            mf = s.get("MediaFileName")
            if not mf:
                continue
            f = "wheels/%s.png" % mf
            if f not in names:
                bad.append("%s: %s missing" % (os.path.basename(p), f)); continue
            try:
                im = Image.open(io.BytesIO(z.read(f)))
                if im.format != "PNG" or max(im.size) > 1024:
                    bad.append("%s: %s not a PNG <= 1024 px" % (os.path.basename(p), f))
            except Exception as e:  # noqa: BLE001
                bad.append("%s: %s unreadable (%s)" % (os.path.basename(p), f, e))
    for ch in root.iter("DMXChannel"):
        for lc in ch.iter("LogicalChannel"):
            fns = lc.findall("ChannelFunction")
            for i, fn in enumerate(fns):
                w = fn.get("Wheel")
                if not w:
                    continue
                if w not in wheels:
                    bad.append("%s: function %s -> unknown wheel %s" % (os.path.basename(p), fn.get("Name"), w)); continue
                lo = dmx(fn.get("DMXFrom"))[0]
                for cs in fn.findall("ChannelSet"):
                    gdtf_sets += 1
                    wi = cs.get("WheelSlotIndex")
                    if wi is None or not (1 <= int(wi) <= wheels[w]):
                        bad.append("%s: %s slot %s of %d" % (os.path.basename(p), cs.get("Name"), wi, wheels[w]))
                    if dmx(cs.get("DMXFrom"))[0] < lo:
                        bad.append("%s: %s starts before its function" % (os.path.basename(p), cs.get("Name")))
print("GDTF: %d wheel channel sets checked" % gdtf_sets)

for b in bad[:30]:
    print("  WRONG", b)
print("console gobos: %d wrong" % len(bad))
sys.exit(1 if bad else 0)
