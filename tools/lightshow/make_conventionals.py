#!/usr/bin/env python3
"""
CSC 1.2.1 - the conventional halogen fixtures (asked 15 November: "there are no conventionals in your mod"): CSC PAR P64, CSC FRESNEL
F1, CSC PC C1, CSC ELLIPSOIDAL E1. Fictional fixtures modelled here from simple solids (cylinders, cones, boxes), in the format of the
other game meshes (assets/concertstage/lightshow/<id>.json): triangles per colour quantised at 1/4096 m, parts "base" (the stirrup
yoke, in block coordinates from its foot) and "head" (around the tilt pivot), three levels of detail, the lens zone, the beam.
The model is built at 1 m = 1 block, beam towards -Z; the game draws it x2.0 (FixtureProfile.renderScale), like the blinders.
Every solid is convex: each triangle is turned to face away from the centre of its solid (outward normals, back faces culled).
Usage: python3 tools/lightshow/make_conventionals.py
"""
import json
import math
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/concertstage/lightshow")
Q = 4096

BODY, RIM, YOKE, KNOB, FRAME, SOCKET, HANDLE = "1c1d1f", "2c2d30", "232427", "3d3e42", "45474b", "6a6c70", "8e9196"
LENS, GLASS1, GLASS2, GLASS3 = "e9e6d6", "d6dce0", "c4ccd2", "b3bcc3"


class Mesh:
    def __init__(self):
        self.tris = {}                                   # colour -> flat list of floats

    def solid(self, colour, faces, centre):
        """faces: list of polygons (lists of 3d points), fanned into triangles turned away from centre."""
        out = self.tris.setdefault(colour, [])
        for poly in faces:
            for i in range(1, len(poly) - 1):
                a, b, c = poly[0], poly[i], poly[i + 1]
                n = cross(sub(b, a), sub(c, a))
                if length(n) < 1e-12:
                    continue
                g = [(a[k] + b[k] + c[k]) / 3 for k in range(3)]
                if dot(n, sub(g, centre)) < 0:
                    b, c = c, b
                out.extend(a + b + c)


def sub(a, b): return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def cross(a, b): return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
def length(a): return math.sqrt(dot(a, a))


def frustum_z(m, colour, r0, z0, r1, z1, segs, cx=0.0, cy=0.0, square=False):
    """Solid of revolution along z from radius r0 at z0 to r1 at z1 (a square section when square=True)."""
    n = 4 if square else segs
    ph = math.pi / 4 if square else 0.0
    k = math.sqrt(2) if square else 1.0
    ring0 = [[cx + r0 * k * math.cos(ph + 2 * math.pi * i / n), cy + r0 * k * math.sin(ph + 2 * math.pi * i / n), z0] for i in range(n)]
    ring1 = [[cx + r1 * k * math.cos(ph + 2 * math.pi * i / n), cy + r1 * k * math.sin(ph + 2 * math.pi * i / n), z1] for i in range(n)]
    faces = [ring0, ring1[::-1]]
    for i in range(n):
        j = (i + 1) % n
        faces.append([ring0[i], ring0[j], ring1[j], ring1[i]])
    m.solid(colour, faces, [cx, cy, (z0 + z1) / 2])


def cyl_x(m, colour, r, x0, x1, y, z, segs):
    ring0 = [[x0, y + r * math.cos(2 * math.pi * i / segs), z + r * math.sin(2 * math.pi * i / segs)] for i in range(segs)]
    ring1 = [[x1, p[1], p[2]] for p in ring0]
    faces = [ring0, ring1[::-1]]
    for i in range(segs):
        j = (i + 1) % segs
        faces.append([ring0[i], ring0[j], ring1[j], ring1[i]])
    m.solid(colour, faces, [(x0 + x1) / 2, y, z])


def box(m, colour, cx, cy, cz, hx, hy, hz, rx=0.0, hinge=None):
    """Box of half sizes hx, hy, hz at (cx, cy, cz), turned by rx (rad) about the x axis through hinge (y, z) when given."""
    pts = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            for sz in (-1, 1):
                p = [cx + sx * hx, cy + sy * hy, cz + sz * hz]
                if rx and hinge is not None:
                    y, z = p[1] - hinge[0], p[2] - hinge[1]
                    c, s = math.cos(rx), math.sin(rx)
                    p = [p[0], hinge[0] + y * c - z * s, hinge[1] + y * s + z * c]
                pts.append(p)
    idx = lambda sx, sy, sz: pts[(sx > 0) * 4 + (sy > 0) * 2 + (sz > 0)]
    faces = [
        [idx(-1, -1, -1), idx(-1, 1, -1), idx(-1, 1, 1), idx(-1, -1, 1)],
        [idx(1, -1, -1), idx(1, 1, -1), idx(1, 1, 1), idx(1, -1, 1)],
        [idx(-1, -1, -1), idx(1, -1, -1), idx(1, -1, 1), idx(-1, -1, 1)],
        [idx(-1, 1, -1), idx(1, 1, -1), idx(1, 1, 1), idx(-1, 1, 1)],
        [idx(-1, -1, -1), idx(1, -1, -1), idx(1, 1, -1), idx(-1, 1, -1)],
        [idx(-1, -1, 1), idx(1, -1, 1), idx(1, 1, 1), idx(-1, 1, 1)],
    ]
    centre = [sum(p[k] for p in pts) / 8 for k in range(3)]
    m.solid(colour, faces, centre)


def gel_frame(m, half, z, t=0.006, w=0.018):
    """Square gel frame in front of the lens (four bars)."""
    box(m, FRAME, 0, half - w / 2, z, half, w / 2, t)
    box(m, FRAME, 0, -half + w / 2, z, half, w / 2, t)
    box(m, FRAME, half - w / 2, 0, z, w / 2, half - w, t)
    box(m, FRAME, -half + w / 2, 0, z, w / 2, half - w, t)


def barn_doors(m, half_w, half_h, z, depth, open_deg=32):
    """Four barn doors hinged around a front opening of half sizes half_w x half_h at z, opened outwards."""
    a = math.radians(open_deg)
    # top / bottom: plates in x-z, hinged on the y edges, opened by a rotation about x
    box(m, FRAME, 0, half_h, z - depth / 2, half_w, 0.003, depth / 2, rx=-a, hinge=(half_h, z))
    box(m, FRAME, 0, -half_h, z - depth / 2, half_w, 0.003, depth / 2, rx=a, hinge=(-half_h, z))
    # sides: shorter plates, turned about y (built in x-z then mirrored by hand)
    for s in (-1, 1):
        d = depth * 0.8
        x0, z0 = s * half_w, z
        x1, z1 = x0 + s * d * math.sin(a), z0 - d * math.cos(a)
        t = 0.003
        nx, nz = (z1 - z0), -(x1 - x0)
        ln = math.hypot(nx, nz)
        nx, nz = nx / ln * t, nz / ln * t
        hy = half_h * 0.9
        quad = [[x0 + nx, -hy, z0 + nz], [x1 + nx, -hy, z1 + nz], [x1 - nx, -hy, z1 - nz], [x0 - nx, -hy, z0 - nz]]
        top = [[p[0], hy, p[2]] for p in quad]
        faces = [quad, top] + [[quad[i], quad[(i + 1) % 4], top[(i + 1) % 4], top[i]] for i in range(4)]
        m.solid(FRAME, faces, [(x0 + x1) / 2, 0, (z0 + z1) / 2])


def yoke(m, arm_x, pivot_y, segs, knobs=True):
    """Stirrup yoke: foot bar, two arms up to the pivot, the tilt knobs."""
    box(m, YOKE, 0, 0.007, 0, arm_x + 0.008, 0.007, 0.026)
    for s in (-1, 1):
        box(m, YOKE, s * arm_x, (pivot_y + 0.03) / 2, 0, 0.008, (pivot_y + 0.03) / 2, 0.024)
        if knobs:
            cyl_x(m, KNOB, 0.022, s * (arm_x + 0.008), s * (arm_x + 0.03), pivot_y, 0, segs)


def par(m, lod):
    s = (24, 12, 8)[lod]
    frustum_z(m, BODY, 0.12, -0.19, 0.12, 0.13, s)
    frustum_z(m, BODY, 0.12, 0.13, 0.075, 0.19, s)
    if lod < 2:
        frustum_z(m, RIM, 0.128, -0.205, 0.128, -0.19, s)
        box(m, SOCKET, 0, 0, 0.205, 0.028, 0.028, 0.016)
    if lod == 0:
        gel_frame(m, 0.135, -0.213)
        for y in (-0.06, 0.0, 0.06):                     # ribs of the can
            frustum_z(m, RIM, 0.123, y - 0.004, 0.123, y + 0.004, s)
    frustum_z(m, LENS, 0.105, -0.207, 0.105, -0.199, s)
    return {"lens_z": -0.207, "lens_r": 0.105}


def fresnel(m, lod):
    s = (24, 12, 8)[lod]
    box(m, BODY, 0, 0, 0, 0.13, 0.14, 0.17)
    box(m, RIM, 0, 0, -0.178, 0.135, 0.145, 0.008)
    if lod < 2:
        box(m, SOCKET, 0, -0.03, 0.185, 0.05, 0.05, 0.015)
        for i in range(5):                               # cooling fins on the top
            box(m, RIM, 0, 0.152, -0.12 + i * 0.06, 0.11, 0.012, 0.004)
    if lod == 0:
        barn_doors(m, 0.135, 0.145, -0.186, 0.12)
        cyl_x(m, HANDLE, 0.012, -0.02, 0.02, -0.15, 0.12, 8)        # focus knob under the body
    # the stepped Fresnel lens: concentric rings stepping forward
    frustum_z(m, GLASS3, 0.098, -0.191, 0.098, -0.186, s)
    if lod < 2:
        frustum_z(m, GLASS2, 0.068, -0.194, 0.068, -0.191, s)
        frustum_z(m, GLASS1, 0.038, -0.197, 0.038, -0.194, s)
    return {"lens_z": -0.197, "lens_r": 0.098}


def pc(m, lod):
    s = (24, 12, 8)[lod]
    frustum_z(m, BODY, 0.12, -0.15, 0.12, 0.14, s)
    box(m, BODY, 0, 0, 0.18, 0.1, 0.1, 0.04)
    frustum_z(m, RIM, 0.126, -0.165, 0.126, -0.15, s)
    if lod < 2:
        box(m, SOCKET, 0, 0, 0.23, 0.03, 0.03, 0.012)
        for i in range(3):
            box(m, RIM, 0, 0.105, 0.155 + i * 0.02, 0.08, 0.004, 0.003)
    if lod == 0:
        barn_doors(m, 0.115, 0.115, -0.17, 0.1)
        cyl_x(m, HANDLE, 0.011, -0.02, 0.02, -0.13, 0.08, 8)
    frustum_z(m, LENS, 0.1, -0.168, 0.1, -0.162, s)
    return {"lens_z": -0.168, "lens_r": 0.1}


def ellipsoidal(m, lod):
    s = (24, 12, 8)[lod]
    frustum_z(m, BODY, 0.13, 0.02, 0.13, 0.09, s)          # reflector housing
    frustum_z(m, BODY, 0.13, 0.09, 0.075, 0.24, s)
    box(m, BODY, 0, 0, -0.01, 0.1, 0.1, 0.035)             # shutter gate
    frustum_z(m, BODY, 0.075, -0.33, 0.075, -0.045, s)     # lens tube
    frustum_z(m, RIM, 0.086, -0.355, 0.086, -0.33, s)
    if lod < 2:
        box(m, SOCKET, 0, 0, 0.255, 0.03, 0.03, 0.014)
        frustum_z(m, RIM, 0.082, -0.2, 0.082, -0.185, s)   # second lens ring (zoom-less barrel joint)
    if lod == 0:
        for (x, y, z) in ((0, 0.12, -0.022), (0, -0.12, -0.002), (0.12, 0, -0.012), (-0.12, 0, 0.008)):   # the four shutter handles
            box(m, HANDLE, x, y, z, 0.014 if x == 0 else 0.022, 0.022 if x == 0 else 0.014, 0.004)
        gel_frame(m, 0.1, -0.362, w=0.014)
        cyl_x(m, HANDLE, 0.01, -0.012, 0.012, -0.085, -0.25, 8)    # lens tube knob
    frustum_z(m, LENS, 0.068, -0.357, 0.068, -0.35, s)
    return {"lens_z": -0.357, "lens_r": 0.068}


FIXTURES = {
    # id: (builder, pivot height, yoke arm x)
    "csc_par_p64": (par, 0.17, 0.142),
    "csc_fresnel_f1": (fresnel, 0.19, 0.152),
    "csc_pc_c1": (pc, 0.19, 0.142),
    "csc_ellipsoidal_e1": (ellipsoidal, 0.2, 0.147),
}


def quant(tris):
    return {c: [int(round(v * Q)) for v in t] for c, t in tris.items() if t}


def build(fid):
    builder, pivot, arm = FIXTURES[fid]
    doc = {"id": fid, "quant": Q}
    info = None
    for key, lod in (("parts", 0), ("lod_far", 1), ("lod_low", 2)):
        head, base = Mesh(), Mesh()
        i = builder(head, lod)
        info = info or i
        yoke(base, arm, pivot, (12, 8, 6)[lod], knobs=lod < 2)
        doc[key] = {"base": {"static": quant(base.tris)}, "head": {"static": quant(head.tris)}}
    doc["pivots"] = {"pan": None, "tilt": [0.0, pivot, 0.0]}
    doc["zones"] = {"0": {"name": "Lens", "kind": "cell", "centre": [0.0, 0.0, info["lens_z"]], "half": [info["lens_r"], info["lens_r"]]}}
    doc["palette_white_uv"] = [0.03125, 0.03125]
    doc["beam"] = {"origin": [0.0, 0.0, info["lens_z"]], "axis": [0, 0, -1], "lens_radius": info["lens_r"]}
    with open(os.path.join(OUT, fid + ".json"), "w") as f:
        json.dump(doc, f, separators=(",", ":"))
    ys = [v for c in doc["parts"]["head"]["static"].values() for v in c[1::3]]
    xs = [v for c in doc["parts"]["base"]["static"].values() for v in c[0::3]] + [v for c in doc["parts"]["head"]["static"].values() for v in c[0::3]]
    tri = sum(len(v) for p in ("head", "base") for v in doc["parts"][p]["static"].values()) // 9
    print(f"{fid}: {tri} triangles, half width {max(abs(x) for x in xs) / Q:.3f}, height {pivot + max(ys) / Q:.3f}")


if __name__ == "__main__":
    for fid in FIXTURES:
        build(fid)
