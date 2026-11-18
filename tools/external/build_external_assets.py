#!/usr/bin/env python3
"""Concert Stage Craft 1.2.0 - External Show Control: builds the files other lighting software reads, from the game's own data.

Inputs (nothing is invented, nothing is modified):
  - assets/concertstage/lightshow/<id>.json : the four fixture models exported from the validated Blender files (parts base / yoke /
    head, coloured triangles, pan and tilt pivots, beam origin and axis, emitter zones). The .blend files are not touched.
  - tools/external/modes.json : the DMX modes, dumped from the Java tables (DmxMode, the single source) by DumpModes.java.
  - assets/concertstage/textures/lightshow/gobo_<n>.png : the gobos of the VORTEX X1.

Outputs (assets/concertstage/external/, copied by the game to <game>/concertstage/export on request):
  gdtf/   Concert Stage Craft@CSC <MODEL>.gdtf     GDTF 1.2 (DIN SPEC 15800): attributes, wheels, geometries (Base, Yoke, Head, Beam,
          one Beam per LED cell / zone), DMX modes Full / Compact / Ultra Compact, glTF models (models/gltf) and 3DS (models/3ds).
  models/ <id>.glb                                  the fixture as one glTF 2.0 scene: Base > Yoke (pan) > Head (tilt) > Beam.
          <id>_<part>.obj + .mtl                    each part as OBJ (no hierarchy: OBJ cannot hold one; the pivots are in the .txt).
          <id>_pivots.txt                           pivots, axes, beam, scale.
          ma3d/<SHORT>.3ds + <SHORT>.import.png     MA 3D / dot2 3D model: 3DS with the MA naming (_XAP pan, _XAT tilt, _XB beam,
                                                    _XSX sunshade), one material per object, names of 8 characters at most.
  qlcplus/Concert-Stage-Craft-<MODEL>.qxf          QLC+ fixture definitions (Full / Compact / Ultra Compact, LED heads in Full).

Scale: as drawn in Minecraft (model x render scale of the game), so a rig exported from a world has the size it has in the world.
Coordinates: glTF Y up (as Minecraft); GDTF / MVR / 3DS Z up, X east, Y north (Minecraft (x, y, z) -> (x, -z, y)).

Usage: python3 -I build_external_assets.py <repo root>
"""
import io
import json
import math
import os
import struct
import sys
import uuid
import zipfile
from datetime import datetime, timezone

try:
    from PIL import Image, ImageDraw
except ImportError:                                   # thumbnails are optional
    Image = None

NS = uuid.UUID("6f1e0d3a-3c55-4b1a-9a4e-0c5c43534301")
MANUFACTURER = "Concert Stage Craft"
STAMP = "2026-11-09T12:00:00"                         # fixed: the same inputs give byte-identical files

# ------------------------------------------------------------------------------------------------------------------ model
class Model:
    def __init__(self, path, scale):
        d = json.load(open(path))
        q = d["quant"]
        self.id = d["id"]
        self.scale = scale
        self.parts = {}
        for name, part in d["parts"].items():
            groups = []
            for rgb, tri in part["static"].items():
                v = [c / q * scale for c in tri]
                groups.append((int(rgb, 16), v))
            groups.sort(key=lambda g: g[0])
            self.parts[name] = groups
        piv = d["pivots"]
        self.pan = None if piv["pan"] is None else [c * scale for c in piv["pan"]]
        self.tilt = [c * scale + (self.pan[i] if self.pan else 0) for i, c in enumerate(piv["tilt"])]
        b = d["beam"]
        self.beam_origin = [c * scale for c in b["origin"]]
        ax = b["axis"]
        n = math.sqrt(sum(c * c for c in ax))
        self.beam_axis = [c / n for c in ax]
        self.lens_radius = b["lens_radius"] * scale
        self.zones = []
        for i in range(len(d["zones"])):
            z = d["zones"][str(i)]
            if "centre" not in z:
                self.zones.append(None)
                continue
            self.zones.append({"name": z["name"], "kind": z["kind"], "centre": [c * scale for c in z["centre"]],
                               "half": [c * scale for c in z["half"]]})

    def rest_straight(self):
        """1.3.0 rev. 2: the moving heads are exported with their tilt centred on the straight position (beam along the base axis,
        up when standing), as in the game (FixtureProfile.tiltZero) and as GDTF / MA expect: the head, its beam and its emitters turn
        +90 degrees about X around the tilt pivot (head vertices, beam and zones are relative to that pivot). Nothing else moves."""
        def rx(v):
            return [v[0], -v[2], v[1]]
        head = []
        for rgb, v in self.parts["head"]:
            out = []
            for i in range(0, len(v), 3):
                out += rx(v[i:i + 3])
            head.append((rgb, out))
        self.parts["head"] = head
        self.beam_origin = rx(self.beam_origin)
        self.beam_axis = rx(self.beam_axis)
        for z in self.zones:
            if z is not None:
                z["centre"] = rx(z["centre"])

    def bbox(self, part):
        xs, ys, zs = [], [], []
        for _, v in self.parts[part]:
            xs += v[0::3]; ys += v[1::3]; zs += v[2::3]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def zup(p):
    """Minecraft / glTF (x, y, z), Y up -> GDTF / 3DS (x, -z, y), Z up."""
    return (p[0], -p[2], p[1])


def srgb_to_linear(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


# ------------------------------------------------------------------------------------------------------------------ glTF
def flat_normals(v):
    n = []
    for i in range(0, len(v), 9):
        ax, ay, az = v[i + 3] - v[i], v[i + 4] - v[i + 1], v[i + 5] - v[i + 2]
        bx, by, bz = v[i + 6] - v[i], v[i + 7] - v[i + 1], v[i + 8] - v[i + 2]
        nx, ny, nz = ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx
        l = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        n += [nx / l, ny / l, nz / l] * 3
    return n


class Gltf:
    def __init__(self):
        self.bin = bytearray()
        self.j = {"asset": {"version": "2.0", "generator": "Concert Stage Craft 1.2.0 build_external_assets.py"},
                  "scenes": [{"nodes": []}], "scene": 0, "nodes": [], "meshes": [], "materials": [], "accessors": [],
                  "bufferViews": [], "buffers": [{"byteLength": 0}]}
        self.mat_index = {}

    def material(self, rgb):
        if rgb in self.mat_index:
            return self.mat_index[rgb]
        r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
        self.j["materials"].append({"name": "csc_%06x" % rgb, "pbrMetallicRoughness": {
            "baseColorFactor": [srgb_to_linear(r), srgb_to_linear(g), srgb_to_linear(b), 1.0], "metallicFactor": 0.2, "roughnessFactor": 0.6},
            "doubleSided": True})
        self.mat_index[rgb] = len(self.j["materials"]) - 1
        return self.mat_index[rgb]

    def view(self, data, target):
        while len(self.bin) % 4:
            self.bin.append(0)
        off = len(self.bin)
        self.bin += data
        self.j["bufferViews"].append({"buffer": 0, "byteOffset": off, "byteLength": len(data), "target": target})
        return len(self.j["bufferViews"]) - 1

    def mesh(self, name, groups, offset=(0, 0, 0)):
        prims = []
        for rgb, v in groups:
            v = [v[i] - offset[i % 3] for i in range(len(v))]
            n = flat_normals(v)
            count = len(v) // 3
            pv = self.view(struct.pack("<%df" % len(v), *v), 34962)
            nv = self.view(struct.pack("<%df" % len(n), *n), 34962)
            mn = [min(v[k::3]) for k in range(3)]
            mx = [max(v[k::3]) for k in range(3)]
            self.j["accessors"].append({"bufferView": pv, "componentType": 5126, "count": count, "type": "VEC3", "min": mn, "max": mx})
            pa = len(self.j["accessors"]) - 1
            self.j["accessors"].append({"bufferView": nv, "componentType": 5126, "count": count, "type": "VEC3"})
            na = len(self.j["accessors"]) - 1
            prims.append({"attributes": {"POSITION": pa, "NORMAL": na}, "material": self.material(rgb), "mode": 4})
        self.j["meshes"].append({"name": name, "primitives": prims})
        return len(self.j["meshes"]) - 1

    def node(self, name, mesh=None, translation=None, rotation=None, children=None, extras=None):
        nd = {"name": name}
        if mesh is not None:
            nd["mesh"] = mesh
        if translation and any(abs(c) > 1e-9 for c in translation):
            nd["translation"] = list(translation)
        if rotation:
            nd["rotation"] = list(rotation)
        if children:
            nd["children"] = children
        if extras:
            nd["extras"] = extras
        self.j["nodes"].append(nd)
        return len(self.j["nodes"]) - 1

    def glb(self):
        self.j["buffers"][0]["byteLength"] = len(self.bin)
        js = json.dumps(self.j, separators=(",", ":"), sort_keys=True).encode()
        while len(js) % 4:
            js += b" "
        b = bytes(self.bin)
        while len(b) % 4:
            b += b"\0"
        total = 12 + 8 + len(js) + 8 + len(b)
        return struct.pack("<III", 0x46546C67, 2, total) + struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(b), 0x004E4942) + b


def quat_from_to(a, b):
    """Rotation taking unit vector a to unit vector b, as glTF quaternion (x, y, z, w)."""
    c = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
    d = sum(a[i] * b[i] for i in range(3))
    if d < -0.999999:
        return [1.0, 0.0, 0.0, 0.0]
    w = 1 + d
    n = math.sqrt(c[0] ** 2 + c[1] ** 2 + c[2] ** 2 + w * w)
    return [c[0] / n, c[1] / n, c[2] / n, w / n]


def fixture_glb(m, title):
    g = Gltf()
    pan = m.pan or [0, 0, 0]
    beam = g.node("Beam", translation=m.beam_origin, rotation=quat_from_to([0, 0, -1], m.beam_axis),
                  extras={"lens_radius_m": m.lens_radius, "note": "the light leaves along -Z of this node"})
    head = g.node("Head", g.mesh("Head", m.parts["head"]), translation=[m.tilt[i] - pan[i] for i in range(3)], children=[beam],
                  extras={"axis": "tilt: rotation about +X of this node, positive raises the beam"})
    if "yoke" in m.parts:
        yoke = g.node("Yoke", g.mesh("Yoke", m.parts["yoke"]), translation=pan, children=[head],
                      extras={"axis": "pan: rotation about +Y of this node"})
        base = g.node("Base", g.mesh("Base", m.parts["base"]), children=[yoke])
    else:
        head_abs = g.j["nodes"][head]
        head_abs["translation"] = list(m.tilt)
        base = g.node("Base", g.mesh("Base", m.parts["base"]), children=[head])
    root = g.node(title, children=[base], extras={"scale": "as drawn in Minecraft, 1 block = 1 m", "front": "-Z (north)"})
    g.j["scenes"][0]["nodes"] = [root]
    return g.glb()


def part_glb(m, part, offset):
    g = Gltf()
    n = g.node(part.capitalize(), g.mesh(part.capitalize(), m.parts[part], offset))
    g.j["scenes"][0]["nodes"] = [n]
    return g.glb()


# ------------------------------------------------------------------------------------------------------------------ OBJ
def obj_files(m, part, offset, stem):
    o = io.StringIO()
    mtl = io.StringIO()
    o.write("# Concert Stage Craft 1.2.0 - %s, part %s (as drawn in Minecraft, metres, Y up). Pivot at the origin.\n" % (m.id, part))
    o.write("mtllib %s.mtl\n" % stem)
    idx = 1
    for rgb, v in m.parts[part]:
        name = "csc_%06x" % rgb
        mtl.write("newmtl %s\nKd %.4f %.4f %.4f\nKa 0 0 0\nKs 0.2 0.2 0.2\nNs 40\nd 1\nillum 2\n\n" % (
            name, ((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0))
        o.write("o %s_%s\nusemtl %s\n" % (part, name, name))
        for i in range(0, len(v), 3):
            o.write("v %.5f %.5f %.5f\n" % (v[i] - offset[0], v[i + 1] - offset[1], v[i + 2] - offset[2]))
        for i in range(len(v) // 9):
            o.write("f %d %d %d\n" % (idx + i * 3, idx + i * 3 + 1, idx + i * 3 + 2))
        idx += len(v) // 3
    return o.getvalue(), mtl.getvalue()


# ------------------------------------------------------------------------------------------------------------------ 3DS (MA 3D / dot2 3D)
def chunk(cid, payload):
    return struct.pack("<HI", cid, 6 + len(payload)) + payload


def cstr(s):
    return s.encode("ascii") + b"\0"


def three_ds(m, short):
    """MA 3D / dot2 3D model: objects named with the MA parameters (pan anchor _XAP, tilt anchor _XAT, beam _XB, sunshade _XSX)."""
    objects = []                                     # (name, verts (z-up), faces, material, parent name, pivot)
    mats = {}

    def matname(rgb):
        if rgb not in mats:
            mats[rgb] = "C%02d%06x" % (len(mats), rgb)
        return mats[rgb]

    pan = m.pan or [0, 0, 0]
    tilt_abs = list(m.tilt)

    def add_part(prefix, part, offset_abs, parent, pivot):
        for k, (rgb, v) in enumerate(m.parts[part]):
            verts = []
            for i in range(0, len(v), 3):
                verts.append(zup((v[i] + offset_abs[0], v[i + 1] + offset_abs[1], v[i + 2] + offset_abs[2])))
            faces = [(i * 3, i * 3 + 1, i * 3 + 2) for i in range(len(v) // 9)]
            objects.append(("%s%02d_XSX" % (prefix, k), verts, faces, matname(rgb), parent, pivot))

    def square(name, centre, normal, size, parent):
        n = normal
        a = [1, 0, 0] if abs(n[0]) < 0.9 else [0, 1, 0]
        u = [n[1] * a[2] - n[2] * a[1], n[2] * a[0] - n[0] * a[2], n[0] * a[1] - n[1] * a[0]]
        l = math.sqrt(sum(c * c for c in u)); u = [c / l for c in u]
        w = [n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]]
        h = size / 2
        pts = [[centre[i] + (sx * u[i] + sy * w[i]) * h for i in range(3)] for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
        # both triangles wound so that their normal is n
        objects.append((name, pts, [(0, 1, 2), (0, 2, 3)], matname(0x000000), parent, centre))

    root = None
    base_zero = [0, 0, 0]
    add_part("B", "base", base_zero, None, zup(base_zero))
    root = objects[0][0]
    for i in range(1, len(objects)):
        objects[i] = objects[i][:4] + (root,) + objects[i][5:]
    beam_parent = None
    if "yoke" in m.parts:
        square("_XAP", zup(pan), zup((0, 1, 0)), 0.2 * m.scale, root)
        add_part("Y", "yoke", [0, 0, 0], "_XAP", zup(pan))
        square("_XAT", zup(tilt_abs), zup((1, 0, 0)), 0.2 * m.scale, "_XAP")
    else:
        square("_XAT", zup(tilt_abs), zup((1, 0, 0)), 0.2 * m.scale, root)
    add_part("H", "head", tilt_abs, "_XAT", zup(tilt_abs))
    bo = [m.beam_origin[i] + tilt_abs[i] for i in range(3)]
    square("_XB", zup(bo), zup(m.beam_axis), 2 * m.lens_radius, "_XAT")
    for o in objects:
        if len(o[0]) > 8:
            raise SystemExit("3DS object name over 8 characters: " + o[0])
    names = [o[0] for o in objects]
    if len(set(names)) != len(names):
        raise SystemExit("3DS object names not unique")
    # materials
    edit = chunk(0x3D3E, struct.pack("<I", 3))
    for rgb, name in mats.items():
        col = chunk(0x0011, bytes(((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255)))
        body = chunk(0xA000, cstr(name)) + chunk(0xA010, col) + chunk(0xA020, col) + chunk(0xA030, chunk(0x0011, bytes((40, 40, 40))))
        edit += chunk(0xAFFF, body)
    edit += chunk(0x0100, struct.pack("<f", 1.0))
    for name, verts, faces, mat, parent, pivot in objects:
        if len(verts) > 65535:
            raise SystemExit("3DS object too large: " + name)
        pts = chunk(0x4110, struct.pack("<H", len(verts)) + b"".join(struct.pack("<fff", *p) for p in verts))
        fl = struct.pack("<H", len(faces)) + b"".join(struct.pack("<HHHH", a, b, c, 7) for a, b, c in faces)
        fl += chunk(0x4130, cstr(mat) + struct.pack("<H", len(faces)) + b"".join(struct.pack("<H", i) for i in range(len(faces))))
        mat_chunk = chunk(0x4160, struct.pack("<12f", 1, 0, 0, 0, 1, 0, 0, 0, 1, *pivot))
        tri = chunk(0x4100, pts + chunk(0x4120, fl) + mat_chunk)
        edit += chunk(0x4000, cstr(name) + tri)
    # keyframer: the hierarchy (parent of each object), pivots at the axes
    index = {o[0]: i for i, o in enumerate(objects)}
    kf = chunk(0xB00A, struct.pack("<H", 5) + cstr("MAXSCENE") + struct.pack("<I", 100))
    kf += chunk(0xB008, struct.pack("<II", 0, 100)) + chunk(0xB009, struct.pack("<I", 0))
    for i, (name, verts, faces, mat, parent, pivot) in enumerate(objects):
        par = 0xFFFF if parent is None else index[parent]
        ppiv = (0, 0, 0) if parent is None else objects[index[parent]][5]
        pos = [pivot[k] - ppiv[k] for k in range(3)]
        node = chunk(0xB030, struct.pack("<H", i))
        node += chunk(0xB010, cstr(name) + struct.pack("<HHH", 0x4000, 0, par))
        node += chunk(0xB013, struct.pack("<fff", 0, 0, 0))
        track = lambda body: struct.pack("<H", 0) + b"\0" * 8 + struct.pack("<I", 1) + body
        node += chunk(0xB020, track(struct.pack("<IH", 0, 0) + struct.pack("<fff", *pos)))
        node += chunk(0xB021, track(struct.pack("<IH", 0, 0) + struct.pack("<ffff", 0, 0, 0, 1)))
        node += chunk(0xB022, track(struct.pack("<IH", 0, 0) + struct.pack("<fff", 1, 1, 1)))
        kf += chunk(0xB002, node)
    return chunk(0x4D4D, chunk(0x0002, struct.pack("<I", 3)) + chunk(0x3D3D, edit) + chunk(0xB000, kf)), len(objects)


# ------------------------------------------------------------------------------------------------------------------ thumbnail
def thumbnail(m, size):
    if Image is None:
        return None
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    tris = []
    pan = m.pan or [0, 0, 0]
    yaw, pitch = math.radians(215), math.radians(-20)          # three-quarter front view
    for part in m.parts:
        off = m.tilt if part == "head" else [0, 0, 0]
        for rgb, v in m.parts[part]:
            for i in range(0, len(v), 9):
                pts = []
                for k in range(3):
                    x, y, z = v[i + k * 3] + off[0], v[i + k * 3 + 1] + off[1], v[i + k * 3 + 2] + off[2]
                    x, z = x * math.cos(yaw) - z * math.sin(yaw), x * math.sin(yaw) + z * math.cos(yaw)
                    y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
                    pts.append((x, y, z))
                ax, ay = pts[1][0] - pts[0][0], pts[1][1] - pts[0][1]
                bx, by = pts[2][0] - pts[0][0], pts[2][1] - pts[0][1]
                nz = ax * by - ay * bx
                tris.append((sum(p[2] for p in pts) / 3, pts, rgb, nz))
    xs = [p[0] for t in tris for p in t[1]]
    ys = [p[1] for t in tris for p in t[1]]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    span = max(max(xs) - min(xs), max(ys) - min(ys)) * 1.1
    tris.sort(key=lambda t: t[0])
    for _, pts, rgb, nz in tris:
        shade = 0.55 + 0.45 * min(1.0, abs(nz) * 400)
        col = tuple(int(((rgb >> s) & 255) * shade) for s in (16, 8, 0)) + (255,)
        dr.polygon([((p[0] - cx) / span * size + size / 2, size / 2 - (p[1] - cy) / span * size) for p in pts], fill=col)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


# ------------------------------------------------------------------------------------------------------------------ GDTF
GDTF_ATTR = {
    "DIMMER": ("Dimmer", "Dim", "Dimmer.Dimmer", "LuminousIntensity", None),
    "PAN": ("Pan", "P", "Position.PanTilt", "Angle", "PanTilt"),
    "TILT": ("Tilt", "T", "Position.PanTilt", "Angle", "PanTilt"),
    "RED": ("ColorAdd_R", "R", "Color.RGB", "ColorComponent", "ColorRGB"),
    "GREEN": ("ColorAdd_G", "G", "Color.RGB", "ColorComponent", "ColorRGB"),
    "BLUE": ("ColorAdd_B", "B", "Color.RGB", "ColorComponent", "ColorRGB"),
    "WHITE": ("ColorAdd_W", "W", "Color.RGB", "ColorComponent", "ColorRGB"),
    "CTO": ("CTO", "CTO", "Color.Color", "Temperature", None),
    "ZOOM": ("Zoom", "Zoom", "Focus.Focus", "Angle", None),
    "FOCUS": ("Focus1", "Focus1", "Focus.Focus", "None", None),
    "IRIS": ("Iris", "Iris", "Beam.Beam", "None", None),
    "GOBO": ("Gobo1", "G1", "Gobo.Gobo", "None", "Gobo1"),
    "GOBO_ROT": ("Gobo1PosRotate", "G1 <>", "Gobo.Gobo", "AngularSpeed", "Gobo1"),
    "PRISM": ("Prism1", "Prism1", "Beam.Beam", "None", "Prism1"),
    "PRISM_ROT": ("Prism1PosRotate", "Prism1 <>", "Beam.Beam", "AngularSpeed", "Prism1"),
    "STROBE": ("Shutter1", "Sh1", "Beam.Beam", "None", None),
}
# GDTF 1.2 Annex attribute names. CSC laser controls are custom attributes.
GDTF_ATTR.update({
    "CYAN": ("ColorSub_C", "C", "Color.RGB", "ColorComponent", "ColorRGB"),
    "MAGENTA": ("ColorSub_M", "M", "Color.RGB", "ColorComponent", "ColorRGB"),
    "YELLOW": ("ColorSub_Y", "Y", "Color.RGB", "ColorComponent", "ColorRGB"),
    "COLOR_WHEEL": ("ColorMacro2", "Filter", "Color.Color", "None", None),
    "FROST": ("Frost1", "Frost", "Beam.Beam", "None", None),
    "DOUSER": ("Shutter2", "Douser", "Beam.Beam", "None", None),
    "FRAME_ROT": ("ShaperRot", "Frame", "Shapers.Shapers", "Angle", None),
    "MOVE_SPEED": ("PanTiltSpeed", "Speed", "Position.PanTilt", "None", None),
    "LASER_PATTERN": ("CSCLaserPattern", "Pattern", "Beam.Beam", "None", None),
    "LASER_SIZE": ("CSCLaserSize", "Size", "Beam.Beam", "None", None),
    "LASER_SPEED": ("CSCLaserSpeed", "Speed", "Beam.Beam", "Frequency", None),
})
for i in range(1,5): GDTF_ATTR["BLADE_"+str(i)]=("Blade"+str(i)+"A", "Blade "+str(i), "Shapers.Shapers", "None", None)

GDTF_ATTR.update({
    "GOBO_2": ("Gobo2","G2","Gobo.Gobo","None","Gobo2"),
    "GOBO_2_ROT": ("Gobo2PosRotate","G2 rotate","Gobo.Gobo","AngularSpeed","Gobo2"),
    "ANIMATION": ("AnimationWheel1","Animation","Beam.Beam","None",None),
    "PRISM_2": ("Prism2","Prism2","Beam.Beam","None","Prism2"),
    "PRISM_2_ROT": ("Prism2PosRotate","Prism2 rotate","Beam.Beam","AngularSpeed","Prism2"),
    "SHUTTER": ("Shutter3","Shutter","Beam.Beam","None",None),
})

# rev. 17: attributes of the ranges of the packed Compact channels (GDTF 1.2 annex names: HSB_Hue, HSB_Saturation, Gobo(n)SelectSpin)
GDTF_ATTR.update({
    "HUE": ("HSB_Hue", "Hue", "Color.HSB", "Angle", "ColorHSB"),
    "SATURATION": ("HSB_Saturation", "Sat", "Color.HSB", "ColorComponent", "ColorHSB"),
    "GOBO_SPIN": ("Gobo1SelectSpin", "G1 spin", "Gobo.Gobo", "AngularSpeed", "Gobo1"),
    "GOBO_2_SPIN": ("Gobo2SelectSpin", "G2 spin", "Gobo.Gobo", "AngularSpeed", "Gobo2"),
})
# what of a range (DmxMode.Range) -> GDTF_ATTR key
RANGE_ATTR = {"HUE": "HUE", "SATURATION": "SATURATION", "CTO": "CTO", "SHUTTER_OPEN": "STROBE", "STROBE": "STROBE", "IRIS": "IRIS",
              "FOCUS": "FOCUS", "GOBO": "GOBO", "GOBO_SPIN": "GOBO_SPIN", "GOBO_2": "GOBO_2", "GOBO_2_SPIN": "GOBO_2_SPIN"}
# logical channel of a packed channel
PACKED_LOGICAL = {"HUE": "HUE", "SATURATION": "SATURATION", "SHUTTER_FX": "STROBE", "GOBO_FX": "GOBO"}

EXTRA_ATTR = {
    "Shutter1Strobe": ("Strobe1", "Beam.Beam", "Frequency", None, "Shutter1"),
    "ColorMacro1": ("Color Macro1", "Color.Color", "None", None, None),
}


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def mat4(rot=None, t=(0, 0, 0)):
    """GDTF / MVR matrix string: rows of the 4x4 (row-major storage, rotation in the first three columns, translation in the 4th)."""
    r = rot or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    rows = [[r[0][0], r[0][1], r[0][2], t[0]], [r[1][0], r[1][1], r[1][2], t[1]], [r[2][0], r[2][1], r[2][2], t[2]], [0, 0, 0, 1]]
    return "".join("{" + ",".join(fmt(c) for c in row) + "}" for row in rows)


def fmt(c):
    s = "%.6f" % c
    return "0.000000" if s == "-0.000000" else s


def rot_from_to(a, b):
    """3x3 rotation taking unit vector a to b."""
    v = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
    c = sum(a[i] * b[i] for i in range(3))
    if c < -0.999999:
        return [[1, 0, 0], [0, -1, 0], [0, 0, -1]]
    k = 1 / (1 + c)
    vx = [[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]]
    m = [[(1 if i == j else 0) + vx[i][j] + k * sum(vx[i][n] * vx[n][j] for n in range(3)) for j in range(3)] for i in range(3)]
    return m


# rev. 22: the gobo wheel of each fixture (tools/gobos/make_gobos.py -> tools/gobos/wheels.json; the same table as GoboWheels.java)
_WHEELS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "gobos", "wheels.json")))


def wheel_of(fid, second=False):
    for key, w in _WHEELS.items():
        if fid in w["fixtures"] and key.endswith("_2") == second:
            return w
    return None


def gobo_names(f, second=False):
    w = wheel_of(f["id"], second)
    n = 8 if second else f["gobos"]
    return ["Gobo %d %s" % (k, w["names"][k - 1]) if w and k <= len(w["names"]) else "Gobo %d" % k for k in range(1, n + 1)]


def gdtf(m, f, modes_json, gobo_pngs, thumb):
    """Description.xml and the archive of one fixture type."""
    name = f["name"]
    ftid = str(uuid.uuid5(NS, f["id"])).upper()
    has_pan = f["pan"] > 0
    used = set()
    for md in f["modes"][:3]:
        for c in md["channels"]:
            if c["attr"]:
                used.add(c["attr"])
            if c["kind"] in ("ZONE_R", "ZONE_G", "ZONE_B", "ZONE_W"):
                used.update(("RED", "GREEN", "BLUE"))
                if c["kind"] == "ZONE_W": used.add("WHITE")
            if c["kind"] == "ZONE_LEVEL":
                used.add("WHITE")
            for r in c.get("ranges", []):
                used.add(RANGE_ATTR[r["what"]])
    macro = any(c["kind"] == "COLOR_MACRO" for md in f["modes"] for c in md["channels"])
    x = io.StringIO()
    w = x.write
    w('<?xml version="1.0" encoding="UTF-8" standalone="no" ?>\n')
    w('<GDTF DataVersion="1.2">\n')
    w('  <FixtureType Name="%s" ShortName="%s" LongName="%s" Manufacturer="%s" Description="%s" FixtureTypeID="%s" Thumbnail="thumbnail" RefFT="" CanHaveChildren="Yes">\n' % (
        esc(name.replace("CSC ", "")), esc(name.replace("CSC ", "").replace("LUMORA ", "")), esc(name), MANUFACTURER,
        esc("%s - %s. Concert Stage Craft (Minecraft mod) fixture, generated from the game data; scale as drawn in the game." % (name, f["function"])), ftid))
    w('    <AttributeDefinitions>\n      <ActivationGroups>\n')
    groups = sorted({GDTF_ATTR[a][4] for a in used if GDTF_ATTR[a][4]})
    for gname in groups:
        w('        <ActivationGroup Name="%s"/>\n' % gname)
    w('      </ActivationGroups>\n      <FeatureGroups>\n')
    feats = {}
    for a in used:
        fg, fe = GDTF_ATTR[a][2].split(".")
        feats.setdefault(fg, set()).add(fe)
    if macro:
        feats.setdefault("Color", set()).add("Color")
    if "STROBE" in used:
        feats.setdefault("Beam", set()).add("Beam")
    for fg in sorted(feats):
        w('        <FeatureGroup Name="%s" Pretty="%s">\n' % (fg, fg))
        for fe in sorted(feats[fg]):
            w('          <Feature Name="%s"/>\n' % fe)
        w('        </FeatureGroup>\n')
    w('      </FeatureGroups>\n      <Attributes>\n')
    for a in sorted(used, key=lambda k: list(GDTF_ATTR).index(k)):
        n, pretty, feat, unit, ag = GDTF_ATTR[a]
        w('        <Attribute Name="%s" Pretty="%s"%s Feature="%s" PhysicalUnit="%s"/>\n' % (
            n, esc(pretty), ' ActivationGroup="%s"' % ag if ag else "", feat, unit))
        if a == "STROBE":
            p, fe, u, _, main = EXTRA_ATTR["Shutter1Strobe"]
            w('        <Attribute Name="Shutter1Strobe" Pretty="%s" MainAttribute="Shutter1" Feature="%s" PhysicalUnit="%s"/>\n' % (p, fe, u))
    if macro:
        w('        <Attribute Name="ColorMacro1" Pretty="Color Macro1" Feature="Color.Color" PhysicalUnit="None"/>\n')
    w('      </Attributes>\n    </AttributeDefinitions>\n')
    # wheels
    w('    <Wheels>\n')
    if f["gobos"] > 0:
        w('      <Wheel Name="Gobo1">\n        <Slot Name="Open" Color="0.312700,0.329000,100.000000"/>\n')
        wh = wheel_of(f["id"])
        for k, name in enumerate(gobo_names(f), 1):
            w('        <Slot Name="%s" Color="0.312700,0.329000,100.000000" MediaFileName="%s"/>\n' % (esc(name), wh["files"][k - 1] if wh else "gobo_%d" % k))
        w('      </Wheel>\n')
    if "GOBO_2" in used:
        w('      <Wheel Name="Gobo2">\n        <Slot Name="Open" Color="0.312700,0.329000,100.000000"/>\n')
        wh2 = wheel_of(f["id"], True)
        for k, name in enumerate(gobo_names(f, True), 1):
            w('        <Slot Name="%s" Color="0.312700,0.329000,100.000000" MediaFileName="%s"/>\n' % (esc(name), wh2["files"][k - 1] if wh2 else "gobo_2_%d" % k))
        w('      </Wheel>\n')
    if "PRISM_2" in used:
        w('      <Wheel Name="Prism2">\n        <Slot Name="Open" Color="0.312700,0.329000,100.000000"/>\n        <Slot Name="6-Facet" Color="0.312700,0.329000,100.000000">\n')
        for k in range(6):
            a=2*math.pi*k/6
            w('          <Facet Color="0.312700,0.329000,100.000000" Rotation="{%s,%s,0.000000}{%s,%s,0.000000}{0.000000,0.000000,1.000000}"/>\n' % (fmt(math.cos(a)),fmt(-math.sin(a)),fmt(math.sin(a)),fmt(math.cos(a))))
        w('        </Slot>\n      </Wheel>\n')
    if "PRISM" in used:
        w('      <Wheel Name="Prism1">\n        <Slot Name="Open" Color="0.312700,0.329000,100.000000"/>\n')
        w('        <Slot Name="%d-Facet" Color="0.312700,0.329000,100.000000">\n' % f.get("prism_facets",3))
        for k in range(f.get("prism_facets",3)):
            a = 2 * math.pi * k / f.get("prism_facets",3)
            w('          <Facet Color="0.312700,0.329000,100.000000" Rotation="{%s,%s,0.000000}{%s,%s,0.000000}{0.000000,0.000000,1.000000}"/>\n' % (
                fmt(math.cos(a)), fmt(-math.sin(a)), fmt(math.sin(a)), fmt(math.cos(a))))
        w('        </Slot>\n      </Wheel>\n')
    w('    </Wheels>\n    <PhysicalDescriptions/>\n')
    # models
    w('    <Models>\n')
    parts = ["base"] + (["yoke"] if "yoke" in m.parts else []) + ["head"]
    pan = m.pan or [0, 0, 0]
    offsets = {"base": [0, 0, 0], "yoke": pan, "head": m.tilt}
    for p in parts:
        lo, hi = m.bbox(p)
        o = offsets[p] if p != "head" else [0, 0, 0]          # head vertices are already relative to the tilt pivot
        lo = [lo[i] - o[i] for i in range(3)]; hi = [hi[i] - o[i] for i in range(3)]
        # GDTF: Length along X, Width along Y, Height along Z (Z up): from Minecraft x, z, y
        w('      <Model Name="%s" Length="%s" Width="%s" Height="%s" PrimitiveType="Undefined" File="%s_%s"/>\n' % (
            p.capitalize(), fmt(hi[0] - lo[0]), fmt(hi[2] - lo[2]), fmt(hi[1] - lo[1]), f["id"], p))
    w('    </Models>\n')
    # geometries
    w('    <Geometries>\n')
    w('      <Geometry Name="Base" Model="Base" Position="%s">\n' % mat4())
    head_pos = [m.tilt[i] - (pan[i] if "yoke" in m.parts else 0) for i in range(3)]
    ind = "        "
    if "yoke" in m.parts:
        w('%s<Axis Name="Yoke" Model="Yoke" Position="%s">\n' % (ind, mat4(t=zup(pan))))
        ind += "  "
    w('%s<Axis Name="Head" Model="Head" Position="%s">\n' % (ind, mat4(t=zup(head_pos))))
    ax = zup(m.beam_axis)
    r = rot_from_to((0, 0, -1), ax)
    beam_type = "Spot" if f["id"] == "csc_vortex_x1" else "Wash"
    angle = f["zoom"][1]
    w('%s  <Beam Name="Beam" Position="%s" LampType="LED" PowerConsumption="0" LuminousFlux="0" ColorTemperature="6500" BeamAngle="%s" FieldAngle="%s" BeamRadius="%s" BeamType="%s" ColorRenderingIndex="100"/>\n' % (
        ind, mat4(r, zup(m.beam_origin)), fmt(angle), fmt(angle), fmt(m.lens_radius), beam_type))
    zone_geo = {}
    for zi, z in enumerate(m.zones):
        if z is None:
            continue
        kind = "White" if z["kind"] == "white" else "Zone" if z["kind"] == "rgb" else "Cell"
        count = sum(1 for zz in m.zones[:zi] if zz is not None and (("White" if zz["kind"] == "white" else "Zone" if zz["kind"] == "rgb" else "Cell") == kind)) + 1
        gname = "%s%d" % (kind, count)
        zone_geo[zi] = gname
        w('%s  <Beam Name="%s" Position="%s" LampType="LED" PowerConsumption="0" LuminousFlux="0" ColorTemperature="6500" BeamAngle="%s" FieldAngle="%s" BeamRadius="%s" BeamType="%s" ColorRenderingIndex="100"/>\n' % (
            ind, gname, mat4(r, zup(z["centre"])), fmt(angle), fmt(angle), fmt(max(z["half"])), "Wash"))
    # rev. 16g: the named instances of the modes (DmxMode.instances, PULSESTRIKE M Compact: LED bars, white bar), each a beam geometry
    # at the centre of the zones it drives: grandMA3 makes a sub-fixture of each geometry that carries channels
    inst_geo = {}
    for md in f["modes"][:3]:
        for ins in md.get("instances", []):
            gname = ins["name"].replace(" ", "_")
            if gname in inst_geo.values():
                continue
            white = "WHITE" in {md["channels"][k]["attr"] for k in ins["channels"]}
            zs = [z for z in m.zones if z is not None and ((z["kind"] == "white") == white)]
            if zs:
                cx = [sum(z["centre"][i] for z in zs) / len(zs) for i in range(3)]
                half = max(max(abs(z["centre"][i] - cx[i]) + z["half"][i] for z in zs) for i in range(2))
            else:
                cx, half = m.beam_origin, m.lens_radius
            w('%s  <Beam Name="%s" Position="%s" LampType="LED" PowerConsumption="0" LuminousFlux="0" ColorTemperature="6500" BeamAngle="%s" FieldAngle="%s" BeamRadius="%s" BeamType="%s" ColorRenderingIndex="100"/>\n' % (
                ind, gname, mat4(r, zup(cx)), fmt(angle), fmt(angle), fmt(half), "Wash"))
            for k in ins["channels"]:
                inst_geo[(md["name"], k)] = gname
    w('%s</Axis>\n' % ind)
    if "yoke" in m.parts:
        w('        </Axis>\n')
    w('      </Geometry>\n    </Geometries>\n')
    # DMX modes
    w('    <DMXModes>\n')
    geo_of = {"PAN": "Yoke", "TILT": "Head"}
    for md in f["modes"][:3]:
        w('      <DMXMode Name="%s" Geometry="Base">\n        <DMXChannels>\n' % md["name"])
        ch = md["channels"]
        i = 0
        while i < len(ch):
            c = ch[i]
            fine = i + 1 < len(ch) and ch[i + 1]["kind"] == "FINE" and ch[i + 1]["attr"] == c["attr"]
            offs = "%d,%d" % (i + 1, i + 2) if fine else "%d" % (i + 1)
            res = 2 if fine else 1
            if c["kind"] == "COLOR_MACRO":
                attr, geo = "ColorMacro1", "Beam"
            elif c.get("ranges"):
                attr, geo = GDTF_ATTR[PACKED_LOGICAL[c["kind"]]][0], inst_geo.get((md["name"], i), "Beam")
            elif c["kind"] in ("ZONE_R", "ZONE_G", "ZONE_B", "ZONE_W", "ZONE_LEVEL"):
                attr = {"ZONE_R": "ColorAdd_R", "ZONE_G": "ColorAdd_G", "ZONE_B": "ColorAdd_B", "ZONE_W": "ColorAdd_W", "ZONE_LEVEL": "ColorAdd_W"}[c["kind"]]
                geo = zone_geo.get(c["zone"], "Beam")
            else:
                attr, geo = GDTF_ATTR[c["attr"]][0], inst_geo.get((md["name"], i), geo_of.get(c["attr"], "Beam"))
            default = c["default"] * (257 if fine else 1) if c["attr"] not in ("PAN", "TILT") or c.get("ranges") else (32768 if fine else 128)
            funcs = functions(f, c, attr, res, modes_json)
            first = funcs[0][0]
            w('          <DMXChannel DMXBreak="1" Offset="%s" InitialFunction="%s_%s.%s.%s" Highlight="None" Geometry="%s">\n' % (
                offs, geo, attr, attr, first, geo))
            w('            <LogicalChannel Attribute="%s" Snap="%s" Master="%s" MibFade="0.000000" DMXChangeTimeLimit="0.000000">\n' % (
                attr, "Yes" if attr in ("Gobo1", "Prism1", "ColorMacro1") else "No", "Grand" if attr == "Dimmer" else "None"))
            for fn, fattr, frm, pfrom, pto, extra, sets in funcs:
                w('              <ChannelFunction Name="%s" Attribute="%s" OriginalAttribute="" DMXFrom="%d/%d" Default="%d/%d" PhysicalFrom="%s" PhysicalTo="%s" RealFade="0.000000" RealAcceleration="0.000000"%s%s\n' % (
                    fn, fattr, frm * (257 if res == 2 else 1), res, default if frm == 0 else frm * (257 if res == 2 else 1), res, fmt(pfrom), fmt(pto), extra,
                    ">" if sets else "/>"))
                if sets:
                    for sn, sfrom, slot in sets:
                        w('                <ChannelSet Name="%s" DMXFrom="%d/1"%s/>\n' % (esc(sn), sfrom, ' WheelSlotIndex="%d"' % slot if slot else ""))
                    w('              </ChannelFunction>\n')
            w('            </LogicalChannel>\n          </DMXChannel>\n')
            i += 2 if fine else 1
        w('        </DMXChannels>\n        <Relations/>\n        <FTMacros/>\n      </DMXMode>\n')
    w('    </DMXModes>\n')
    w('    <Revisions>\n      <Revision Text="Generated by Concert Stage Craft 1.2.0 from the game data (DmxMode tables, validated models). Not yet checked in a console." Date="%s" UserID="0" ModifiedBy="Concert Stage Craft"/>\n    </Revisions>\n' % STAMP)
    w('    <FTPresets/>\n    <Protocols/>\n  </FixtureType>\n</GDTF>\n')
    xml = x.getvalue()
    # archive
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        info = lambda n: zipfile.ZipInfo(n, (2026, 11, 9, 12, 0, 0))
        z.writestr(info("description.xml"), xml, zipfile.ZIP_DEFLATED)
        if thumb:
            z.writestr(info("thumbnail.png"), thumb, zipfile.ZIP_DEFLATED)
        for p in parts:
            off = offsets[p] if p != "head" else [0, 0, 0]
            z.writestr(info("models/gltf/%s_%s.glb" % (f["id"], p)), part_glb_zup(m, p, off), zipfile.ZIP_DEFLATED)
        for k, png in gobo_pngs.items():
            z.writestr(info("wheels/%s.png" % k), png, zipfile.ZIP_DEFLATED)
    return xml, buf.getvalue()


def part_glb_zup(m, part, offset):
    """A part for GDTF: glTF (Y up, as glTF defines it); GDTF readers turn glTF to their Z up frame."""
    return part_glb(m, part, offset)


def functions(f, c, attr, res, modes):
    """(name, attribute, dmxFrom (8-bit), physicalFrom, physicalTo, extra xml, channel sets) for one channel."""
    a = c["attr"]
    if a in ("GOBO_2","GOBO_2_ROT","PRISM_2","PRISM_2_ROT"):
        primary={"GOBO_2":"GOBO","GOBO_2_ROT":"GOBO_ROT","PRISM_2":"PRISM","PRISM_2_ROT":"PRISM_ROT"}[a]
        result=functions(dict(f,prism_facets=6,gobo_slot_names=gobo_names(f,True) if a=="GOBO_2" else None,gobos=8 if a=="GOBO_2" else f["gobos"]),dict(c,attr=primary),attr,res,modes)
        return [(n.replace("Gobo1","Gobo2").replace("Prism1","Prism2"),at.replace("Gobo1","Gobo2").replace("Prism1","Prism2"),fr,pf,pt,ex.replace("Gobo1","Gobo2").replace("Prism1","Prism2"),sets) for n,at,fr,pf,pt,ex,sets in result]
    if c["kind"] == "COLOR_MACRO":
        sets = [(mc["name"], i * modes["macro_step"], 0) for i, mc in enumerate(modes["macro"])]
        return [("ColorMacro", "ColorMacro1", 0, 0, 1, "", sets)]
    if c.get("ranges"):
        # rev. 17: one channel function per range of a packed Compact channel (DmxMode.ranges)
        out, seen = [], {}
        for r in c["ranges"]:
            w = r["what"]
            ga = GDTF_ATTR[RANGE_ATTR[w]][0]
            if w == "STROBE":
                ga = "Shutter1Strobe"
            name = r["name"].replace("/", "-")
            seen[name] = seen.get(name, 0) + 1
            if seen[name] > 1:
                name += " %d" % seen[name]
            wheel = ""
            sets = []
            if w in ("GOBO", "GOBO_SPIN", "GOBO_2", "GOBO_2_SPIN"):
                wheel = ' Wheel="%s"' % ("Gobo2" if w.startswith("GOBO_2") else "Gobo1")
                sets = [(r["name"], r["lo"], int(r["slot"]) + 1)]
            pf, pt = r["from"], r["to"]
            if w in ("GOBO", "GOBO_2"):
                pf = pt = 0
            out.append((name, ga, r["lo"], pf, pt, wheel, sets))
        return out
    if c["kind"].startswith("ZONE"):
        return [(attr, attr, 0, 0, 1, "", [])]
    if a == "FRAME_ROT":
        return [("Frame", "ShaperRot", 0, -90, 90, "", [])]
    if a == "COLOR_WHEEL":
        sets = [(mc["name"], i * modes["macro_step"], 0) for i, mc in enumerate(modes["macro"])]
        return [("Filters", "ColorMacro2", 0, 0, 1, "", sets)]
    if a == "LASER_PATTERN":
        return [("Patterns", "CSCLaserPattern", 0, 0, 4, "", [(n,i*51,0) for i,n in enumerate(["Dot","Fan","Circle","Cross","Square"])])]
    if a == "PAN":
        return [("Pan", "Pan", 0, -f["pan"] / 2, f["pan"] / 2, "", [])]
    if a == "TILT":
        return [("Tilt", "Tilt", 0, -f["tilt"] / 2, f["tilt"] / 2, "", [])]
    if a == "ZOOM":
        return [("Zoom", "Zoom", 0, f["zoom"][0], f["zoom"][1], "", [])]
    if a == "CTO":
        return [("CTO", "CTO", 0, 6500, 2700, "", [])]
    if a == "STROBE":
        so = modes["strobe_open"]
        return [("Open", "Shutter1", 0, 0, 0, "", []), ("Strobe", "Shutter1Strobe", so, 1, f["strobe_hz"], "", [])]
    if a == "GOBO":
        step = modes["gobo_step"]
        names = f.get("gobo_slot_names") or gobo_names(f)
        sets = [("Open", 0, 1)] + [(names[k - 1], k * step, k + 1) for k in range(1, f["gobos"] + 1)]
        return [("Gobo", "Gobo1", 0, 0, 1, ' Wheel="Gobo1"', sets)]
    if a in ("GOBO_ROT", "PRISM_ROT"):
        lo, hi = modes["rot_stop"]
        sp = 220.0 if a == "GOBO_ROT" else 160.0
        n = "Gobo1PosRotate" if a == "GOBO_ROT" else "Prism1PosRotate"
        return [("Rotate CCW", n, 0, -sp, -sp * (1 - (lo - 1) / lo), "", []), ("Stop", n, lo, 0, 0, "", []),
                ("Rotate CW", n, hi + 1, sp / (255 - hi), sp, "", [])]
    if a == "PRISM":
        return [("Prism", "Prism1", 0, 0, 1, ' Wheel="Prism1"', [("Open", 0, 1), ("%d-Facet" % f.get("prism_facets",3), modes["prism_in"], 2)])]
    return [(attr, attr, 0, 0, 1, "", [])]


# ------------------------------------------------------------------------------------------------------------------ QLC+
def qlc_capabilities(text):
    """1.2.0 (15 November, QLC+ 4.13 fixture editor: "No capability provided in channel 'Pan'"): QLC+ wants at least one capability
    in every channel. A channel without any (pan, tilt, dimmer, colour, zoom, focus, iris, their fine bytes) gets one over its whole
    range, named after the channel. The channels and their values do not change."""
    import re
    def fix(m):
        block = m.group(0)
        if "<Capability" in block:
            return block
        name = m.group(1)
        return block.replace(" </Channel>", '  <Capability Min="0" Max="255">%s</Capability>\n </Channel>' % name)
    return re.sub(r' <Channel Name="([^"]*)">\n.*? </Channel>', fix, text, flags=re.S)


def qxf(f, m, modes):
    name = f["name"].replace("CSC ", "")
    x = io.StringIO()
    w = x.write
    w('<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE FixtureDefinition>\n<FixtureDefinition xmlns="http://www.qlcplus.org/FixtureDefinition">\n')
    w(' <Creator>\n  <Name>Concert Stage Craft</Name>\n  <Version>1.2.0</Version>\n  <Author>Concert Stage Craft (generated from the game data)</Author>\n </Creator>\n')
    w(' <Manufacturer>%s</Manufacturer>\n <Model>%s</Model>\n <Type>%s</Type>\n' % (MANUFACTURER, esc(name), "Dimmer" if "halogen" in f["function"] else "Moving Head"))   # 1.2.1: conventionals
    defined = {}
    order = []
    for md in f["modes"][:3]:
        for c in md["channels"]:
            if c["name"] not in defined:
                defined[c["name"]] = c
                order.append(c["name"])
    for n in order:
        c = defined[n]
        w(' <Channel Name="%s">\n' % esc(n))
        k, a = c["kind"], c["attr"]
        if k == "FINE":
            grp = "Pan" if a == "PAN" else "Tilt" if a == "TILT" else "Intensity" if a == "DIMMER" else "Beam"
            w('  <Group Byte="1">%s</Group>\n' % grp)
        elif k in ("ZONE_R", "ZONE_G", "ZONE_B", "ZONE_W"):
            w('  <Group Byte="0">Intensity</Group>\n  <Colour>%s</Colour>\n' % {"ZONE_R": "Red", "ZONE_G": "Green", "ZONE_B": "Blue", "ZONE_W": "White"}[k])
        elif k == "ZONE_LEVEL":
            w('  <Group Byte="0">Intensity</Group>\n  <Colour>White</Colour>\n')
        elif c.get("ranges"):
            grp = {"HUE": "Colour", "SATURATION": "Colour", "SHUTTER_FX": "Shutter", "GOBO_FX": "Gobo"}[k]
            w('  <Group Byte="0">%s</Group>\n' % grp)
            for r in c["ranges"]:
                # rev. 24: the gobo ranges of the packed Compact / Ultra Compact channel get the picture of their slot, as the Full mode
                res_ = ""
                if r["what"] in ("GOBO", "GOBO_SPIN", "GOBO_2", "GOBO_2_SPIN") and int(r["slot"]) > 0:
                    wh = wheel_of(f["id"], r["what"].startswith("GOBO_2"))
                    if wh and int(r["slot"]) <= len(wh["files"]):
                        res_ = ' Preset="%s" Res1="ConcertStageCraft/%s.png"' % ("GoboMacro", wh["files"][int(r["slot"]) - 1])
                w('  <Capability Min="%d" Max="%d"%s>%s</Capability>\n' % (r["lo"], r["hi"], res_, esc(r["name"])))
        elif k == "COLOR_MACRO":
            w('  <Group Byte="0">Colour</Group>\n')
            for i, mc in enumerate(modes["macro"]):
                lo = i * modes["macro_step"]
                hi = 255 if i == len(modes["macro"]) - 1 else lo + modes["macro_step"] - 1
                w('  <Capability Min="%d" Max="%d" Color="#%s">%s</Capability>\n' % (lo, hi, mc["rgb"], esc(mc["name"])))
        elif a == "PAN":
            w('  <Group Byte="0">Pan</Group>\n')
        elif a == "TILT":
            w('  <Group Byte="0">Tilt</Group>\n')
        elif a == "DIMMER":
            w('  <Group Byte="0">Intensity</Group>\n')
        elif a in ("RED", "GREEN", "BLUE", "WHITE"):
            w('  <Group Byte="0">Intensity</Group>\n  <Colour>%s</Colour>\n' % a.capitalize())
        elif a == "CTO":
            w('  <Group Byte="0">Colour</Group>\n  <Capability Min="0" Max="255">CTO 6500 K to 2700 K</Capability>\n')
        elif a == "STROBE":
            so = modes["strobe_open"]
            w('  <Group Byte="0">Shutter</Group>\n  <Capability Min="0" Max="%d">Open</Capability>\n  <Capability Min="%d" Max="255">Strobe 1 to %g Hz</Capability>\n' % (so - 1, so, f["strobe_hz"]))
        elif a in ("GOBO", "GOBO_2"):
            st = modes["gobo_step"]
            w('  <Group Byte="0">Gobo</Group>\n  <Capability Min="0" Max="%d">Open</Capability>\n' % (st - 1))
            # rev. 22: the slot names and pictures of the fixture's own wheel (QLC+ gobos folder: ConcertStageCraft/<file>.png)
            wh = wheel_of(f["id"], a == "GOBO_2")
            names = gobo_names(f, a == "GOBO_2")
            for g in range(1, len(names) + 1):
                res_ = ' Preset="GoboMacro" Res1="ConcertStageCraft/%s.png"' % wh["files"][g - 1] if wh else ""
                w('  <Capability Min="%d" Max="%d"%s>%s</Capability>\n' % (g * st, 255 if g == len(names) else g * st + st - 1, res_, esc(names[g - 1])))
        elif a in ("GOBO_ROT", "GOBO_2_ROT", "PRISM_ROT", "PRISM_2_ROT"):
            lo, hi = modes["rot_stop"]
            w('  <Group Byte="0">%s</Group>\n' % ("Gobo" if a in ("GOBO_ROT", "GOBO_2_ROT") else "Prism"))
            w('  <Capability Min="0" Max="%d">Rotation one way, fast to slow</Capability>\n  <Capability Min="%d" Max="%d">Stop</Capability>\n  <Capability Min="%d" Max="255">Rotation other way, slow to fast</Capability>\n' % (lo - 1, lo, hi, hi + 1))
        elif a in ("PRISM", "PRISM_2"):
            pi = modes["prism_in"]
            w('  <Group Byte="0">Prism</Group>\n  <Capability Min="0" Max="%d">Open</Capability>\n  <Capability Min="%d" Max="255">%d-facet prism</Capability>\n' % (pi - 1, pi, 6 if a == "PRISM_2" else f.get("prism_facets",3)))
        else:
            w('  <Group Byte="0">Beam</Group>\n')
        w(' </Channel>\n')
    lo, hi = m.bbox("base")
    width = int(round((max(m.bbox(p)[1][0] for p in m.parts) - min(m.bbox(p)[0][0] for p in m.parts)) * 1000))
    for md in f["modes"][:3]:
        w(' <Mode Name="%s">\n' % md["name"])
        bulb = ("Halogen", 3200) if "halogen" in f["function"] else ("LED", 6500)    # 1.2.1: conventionals
        w('  <Physical>\n   <Bulb Type="%s" Lumens="0" ColourTemperature="%d"/>\n   <Dimensions Weight="0" Width="%d" Height="%d" Depth="%d"/>\n' % (bulb[0], bulb[1], width, width, width))
        w('   <Lens Name="Other" DegreesMin="%g" DegreesMax="%g"/>\n   <Focus Type="%s" PanMax="%d" TiltMax="%d"/>\n   <Technical PowerConsumption="0" DmxConnector="Other"/>\n  </Physical>\n' % (
            f["zoom"][0], f["zoom"][1], "Head", int(f["pan"]), int(f["tilt"])))
        for i, c in enumerate(md["channels"]):
            w('  <Channel Number="%d">%s</Channel>\n' % (i, esc(c["name"])))
        heads = {}
        for i, c in enumerate(md["channels"]):
            if c["zone"] >= 0:
                heads.setdefault(c["zone"], []).append(i)
        # rev. 16g: the named instances of the mode (PULSESTRIKE M Compact: head 1 = LED bars, head 2 = white bar)
        for k, ins in enumerate(md.get("instances", [])):
            heads[1000 + k] = list(ins["channels"])
        for z in sorted(heads):
            w('  <Head>\n' + "".join('   <Channel>%d</Channel>\n' % i for i in heads[z]) + '  </Head>\n')
        w(' </Mode>\n')
    w('</FixtureDefinition>\n')
    return qlc_capabilities(x.getvalue())


# ------------------------------------------------------------------------------------------------------------------ main
def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    res = os.path.join(root, "common/src/main/resources/assets/concertstage")
    out = os.path.join(res, "external")
    modes = json.load(open(os.path.join(root, "tools/external/modes.json")))
    for d in ("gdtf", "models", "models/ma3d", "qlcplus"):
        os.makedirs(os.path.join(out, d), exist_ok=True)
    gobos = {}
    for k in range(1, 9):
        p = os.path.join(res, "textures/lightshow/gobo_%d.png" % k)
        if os.path.isfile(p):
            gobos[k] = open(p, "rb").read()
    secondary_gobos={"2_"+str(k):open(os.path.join(res,"textures/lightshow/gobo_2_%d.png" % k),"rb").read() for k in range(1,9)}
    short = {"csc_vortex_x1": "VORTEX", "csc_lumora_w7": "LUMW7", "csc_lumora_w19": "LUMW19", "csc_pulsestrike_m": "PULSEM"}
    report = []
    for f in modes["fixtures"]:
        short.setdefault(f["id"], f["id"][4:].replace("_", "").upper()[:8])
        m = Model(os.path.join(res, "lightshow/%s.json" % f["id"]), f["render_scale"])
        if f["tilt"] > 0:                 # rev. 4c: the motorised bars and strobes too (FixtureProfile.tiltZero)
            m.rest_straight()
        thumb = thumbnail(m, 256)
        pngs = {}
        for second in (False, True):
            wh = wheel_of(f["id"], second) if (f["gobos"] if not second else "GOBO_2" in f["attrs"]) else None
            if wh:
                for fn in wh["files"]:
                    pngs[fn] = open(os.path.join(res, "textures/lightshow/%s.png" % fn), "rb").read()
        xml, arc = gdtf(m, f, modes, pngs, thumb)
        gname = "%s@%s.gdtf" % (MANUFACTURER, f["name"])
        open(os.path.join(out, "gdtf", gname), "wb").write(arc)
        open(os.path.join(out, "models", f["id"] + ".glb"), "wb").write(fixture_glb(m, f["name"]))
        pan = m.pan or [0, 0, 0]
        offs = {"base": [0, 0, 0], "yoke": [0, 0, 0], "head": [0, 0, 0]}
        for p in m.parts:
            o, mtl = obj_files(m, p, offs[p], "%s_%s" % (f["id"], p))
            open(os.path.join(out, "models", "%s_%s.obj" % (f["id"], p)), "w").write(o)
            open(os.path.join(out, "models", "%s_%s.mtl" % (f["id"], p)), "w").write(mtl)
        tx = io.StringIO()
        tx.write("%s - pivots and axes (metres, as drawn in Minecraft = model x %.2f; Minecraft / glTF frame: Y up, front -Z)\n" % (f["name"], m.scale))
        tx.write("base:  origin = bottom centre of the base (standing fixture); hung under a truss the whole fixture is turned 180 degrees about Z\n")
        if m.pan:
            tx.write("yoke:  pan pivot %s, pan = rotation about +Y, %g degrees in total (DMX centre = straight ahead)\n" % (fmt3(pan), f["pan"]))
        tx.write("head:  tilt pivot %s (in the base frame), tilt = rotation about +X, %g degrees in total, positive raises the beam; head OBJ vertices are relative to this pivot\n" % (fmt3(m.tilt), f["tilt"]))
        tx.write("beam:  origin %s (head frame), axis %s, lens radius %.4f, zoom %g-%g degrees\n" % (fmt3(m.beam_origin), fmt3(m.beam_axis), m.lens_radius, f["zoom"][0], f["zoom"][1]))
        open(os.path.join(out, "models", f["id"] + "_pivots.txt"), "w").write(tx.getvalue())
        tds, nobj = three_ds(m, short[f["id"]])
        open(os.path.join(out, "models", "ma3d", short[f["id"]] + ".3ds"), "wb").write(tds)
        small = thumbnail(m, 32)
        if small:
            open(os.path.join(out, "models", "ma3d", short[f["id"]] + ".import.png"), "wb").write(small)
        open(os.path.join(out, "qlcplus", "Concert-Stage-Craft-%s.qxf" % f["name"].replace("CSC ", "").replace(" ", "-")), "w").write(qxf(f, m, modes))
        tris = sum(len(v) // 9 for p in m.parts.values() for _, v in p)
        report.append("%-18s GDTF %7d B  glb %8d B  3DS %8d B (%d objects)  %6d triangles" % (
            f["name"], len(arc), os.path.getsize(os.path.join(out, "models", f["id"] + ".glb")), len(tds), nobj, tris))
    print("\n".join(report))


def fmt3(v):
    return "(%.4f, %.4f, %.4f)" % tuple(v)


if __name__ == "__main__":
    main()
