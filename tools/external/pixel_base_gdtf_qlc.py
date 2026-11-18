#!/usr/bin/env python3
"""1.2.0 beta 2b (14 November): the advice of a console operator applied to the GDTF and QLC+ profiles, as ma2_profiles.py does for
grandMA2 / dot2 (PIXEL_VDIM): no RGB on the base of a pixel fixture, and a virtual dimmer for every RGB pixel.

Full modes only (the only modes with pixels). Since 1.2.0 beta 2b (13 h 30) the Full of these fixtures is a pixel mode in the game
itself (DmxMode.pixelLayout: no master dimmer, no base colour the cells have), so build_external_assets.py no longer writes those base
channels and the part of this script that removes / renames them finds nothing to do; what it still does is the virtual dimmer of
every RGB cell in the GDTF files. Run it after build_external_assets.py (see build.sh).

* GDTF (grandMA3, ChamSys MagicQ, Capture, Vectorworks, the MVR export): the base ("Beam" geometry) colour channels that the RGB
  cells also have are removed from the Full mode (gap in the offsets, the footprint does not change), and every RGB cell gets a
  virtual Dimmer channel (DMXChannel Offset="None": no DMX address) that multiplies the cell's colour channels (one <Relation
  Type="Multiply"> per colour, as the GDTF Builder makes them for a virtual dimmer).
* QLC+: a .qxf cannot hold a channel without address (QLC+ has no virtual dimmer), so the base colour channels of the Full mode
  only stop being colour channels (group Maintenance, "Base red (not driven)"...): the colour tools and the RGB Matrix only see the
  pixels. The channels and their numbers are the same.

The rule is the one of ma2_profiles.py: a base colour is left out when an RGB cell (a cell with red) has the same colour; a white
base stays when the cells have no white (LUMORA W7 / W19). White-only cells (blinder segments, PULSESTRIKE white segments) get no
virtual dimmer, they are dimmers themselves.

Idempotent: run it again on its own output and nothing changes. Usage: pixel_base_gdtf_qlc.py <external folder>
"""
import io
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

CELL = re.compile(r"(Cell|Zone)\d+$")
COLOURS = ("ColorAdd_R", "ColorAdd_G", "ColorAdd_B", "ColorAdd_W")
BASE = "Beam"


def gdtf_full(desc):
    """Rewrites the Full mode of a GDTF description.xml (text in, text out)."""
    m = re.search(r'(<DMXMode Name="Full"[^>]*>.*?</DMXMode>)', desc, re.S)
    if not m:
        return desc, 0, 0
    mode = m.group(1)
    chans = re.findall(r'[ \t]*<DMXChannel\b.*?</DMXChannel>\n', mode, re.S)
    geo = lambda c: re.search(r'Geometry="([^"]*)"', c).group(1)
    attr = lambda c: re.search(r'<LogicalChannel Attribute="([^"]*)"', c).group(1)
    virtual = lambda c: re.search(r'Offset="(None)?"', c) is not None
    cells = {}
    for c in chans:
        if CELL.match(geo(c)):
            cells.setdefault(geo(c), []).append(c)
    rgb_cells = [g for g, cs in cells.items() if "ColorAdd_R" in {attr(c) for c in cs}]
    cell_colours = {attr(c) for g in rgb_cells for c in cells[g] if attr(c) in COLOURS}
    removed = added = 0
    out, relations = [], []
    for i, c in enumerate(chans):
        if geo(c) == BASE and attr(c) in cell_colours:
            removed += 1
            continue
        out.append(c)
        g = geo(c)
        last_of_cell = g in rgb_cells and (i + 1 == len(chans) or geo(chans[i + 1]) != g)
        has_vdim = g in rgb_cells and any(virtual(x) and attr(x) == "Dimmer" for x in cells[g])
        if last_of_cell and not has_vdim:
            ind = re.match(r'[ \t]*', c).group(0)
            out.append(
                f'{ind}<DMXChannel DMXBreak="1" Offset="None" InitialFunction="{g}_Dimmer.Dimmer.Dimmer" Highlight="255/1" Geometry="{g}">\n'
                f'{ind}  <LogicalChannel Attribute="Dimmer" Snap="No" Master="Grand" MibFade="0.000000" DMXChangeTimeLimit="0.000000">\n'
                f'{ind}    <ChannelFunction Name="Dimmer" Attribute="Dimmer" OriginalAttribute="" DMXFrom="0/1" Default="0/1" PhysicalFrom="0.000000" PhysicalTo="1.000000" RealFade="0.000000" RealAcceleration="0.000000"/>\n'
                f'{ind}  </LogicalChannel>\n'
                f'{ind}</DMXChannel>\n')
            for x in cells[g]:
                if attr(x) in COLOURS:
                    fn = re.search(r'InitialFunction="([^"]*)"', x).group(1)
                    relations.append('<Relation Name="VDim_%s_%s" Master="%s_Dimmer" Follower="%s" Type="Multiply"/>' % (g, attr(x), g, fn))
            added += 1
    if not removed and not added:
        return desc, 0, 0
    head = mode[:mode.index(chans[0])]
    tail = mode[mode.index(chans[-1]) + len(chans[-1]):]
    if relations:
        rind = re.search(r'\n([ \t]*)<Relations', tail).group(1)
        rel = "<Relations>\n" + "".join(rind + "  " + r + "\n" for r in relations) + rind + "</Relations>"
        tail = re.sub(r'<Relations/>', lambda _: rel, tail, count=1) if "<Relations/>" in tail else tail
    return desc.replace(mode, head + "".join(out) + tail), removed, added


def gdtf_file(path):
    zin = zipfile.ZipFile(path)
    desc = zin.read("description.xml").decode("utf-8")
    new, removed, added = gdtf_full(desc)
    if new == desc:
        return 0, 0
    ET.fromstring(new.encode("utf-8"))                     # still well-formed
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():                            # same entries, same order, same dates
            data = new.encode("utf-8") if info.filename == "description.xml" else zin.read(info.filename)
            zout.writestr(info, data)
    zin.close()
    open(path, "wb").write(buf.getvalue())
    return removed, added


QLC_COLOUR = {"Red": "red", "Green": "green", "Blue": "blue", "White": "white"}


def qxf_file(path):
    t = open(path, encoding="utf-8").read()
    m = re.search(r'( <Mode Name="Full">.*?</Mode>\n)', t, re.S)
    if not m:
        return 0
    mode = m.group(1)
    nums = re.findall(r'<Channel Number="(\d+)">([^<]*)</Channel>', mode)
    heads = re.findall(r'<Head>(.*?)</Head>', mode, re.S)
    head_chs = [[int(x) for x in re.findall(r'<Channel>(\d+)</Channel>', h)] for h in heads]
    name_of = {int(n): nm for n, nm in nums}
    ns = "{http://www.qlcplus.org/FixtureDefinition}"
    colour_of = {ch.get("Name"): ch.findtext(ns + "Colour") for ch in ET.fromstring(t.encode("utf-8")).findall(ns + "Channel")}
    in_head = {c for h in head_chs for c in h}
    rgb_heads = [h for h in head_chs if any(colour_of.get(name_of[c]) == "Red" for c in h)]
    cell_colours = {colour_of.get(name_of[c]) for h in rgb_heads for c in h} & set(QLC_COLOUR)
    changed = 0
    for n, nm in nums:
        n = int(n)
        if n in in_head or colour_of.get(nm) not in cell_colours:
            continue
        new = "Base %s (not driven)" % QLC_COLOUR[colour_of[nm]]
        mode = mode.replace('<Channel Number="%d">%s</Channel>' % (n, nm), '<Channel Number="%d">%s</Channel>' % (n, new))
        if ' <Channel Name="%s">' % new not in t:
            # a channel of its own (the Compact mode keeps the real "Red"...), not a colour channel for QLC+
            defn = (' <Channel Name="%s">\n  <Group Byte="0">Maintenance</Group>\n'
                    '  <Capability Min="0" Max="255">Base colour of the fixture, keep at 0: the colour comes from the cells</Capability>\n'
                    ' </Channel>\n') % new
            first_mode = t.index(' <Mode ')
            t = t[:first_mode] + defn + t[first_mode:]
        changed += 1
    if not changed:
        return 0
    t = t.replace(m.group(1), mode)
    ET.fromstring(t.encode("utf-8"))
    open(path, "w", encoding="utf-8").write(t)
    return changed


def main():
    root = sys.argv[1]
    for f in sorted(os.listdir(os.path.join(root, "gdtf"))):
        if f.endswith(".gdtf"):
            r, a = gdtf_file(os.path.join(root, "gdtf", f))
            if r or a:
                print("GDTF %-45s base colours removed %d, virtual dimmers %d" % (f, r, a))
    for f in sorted(os.listdir(os.path.join(root, "qlcplus"))):
        if f.endswith(".qxf"):
            n = qxf_file(os.path.join(root, "qlcplus", f))
            if n:
                print("QLC+ %-45s base colours not driven %d" % (f, n))


if __name__ == "__main__":
    main()
