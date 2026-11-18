#!/usr/bin/env python3
"""
CSC 1.2.0 rev. 25b - inventory / creative tab icons of the lighting fixtures, rendered from the game meshes (lightshow/<id>.json).

Before: flat triangles painted back to front at 128 px (no anti-aliasing, one shade per face, lenses as dark as the body, every
fixture framed differently, thin fixtures lost in the slot). Now:
- z-buffer at 4 x 128 px, reduced with a Lanczos filter (smooth edges);
- three lights (key from the upper left, fill, rim from behind) on the face normals, a floor of brightness so a black body still
  reads on the grey slot of the creative tab;
- the head of a moving head (and of every fixture with a tilt pivot) raised towards the viewer, the same three-quarter view for all;
- lit lenses: every lens cell / zone of the mesh drawn emissive with a soft glow (halogen yellow lamps on the blinders, a rainbow
  on the pixel bars and tubes, green on the laser, cool white elsewhere), so each icon shows what kind of light it is;
- framed to fill the icon (6 px margin), with a 1 px dark outline.
Usage: python3 tools/lightshow/render_icons.py [fixture ids...]   (writes textures/item/<id>.png and models/item/<id>.json)
"""
import colorsys
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "external"))
from build_external_assets import Model  # noqa: E402

ASSETS = os.path.join(ROOT, "common/src/main/resources/assets/concertstage")
SIZE, SS, MARGIN = 128, 4, 6
YAW, PITCH = math.radians(208), math.radians(-24)       # three-quarter front view from above

LIGHTS = [(np.array([-0.55, 0.65, 0.55]), 0.80),          # key, upper left, towards the camera side
          (np.array([0.70, 0.15, 0.45]), 0.30),            # fill, right
          (np.array([0.10, 0.45, -0.90]), 0.35)]           # rim, behind
AMBIENT, FLOOR = 0.34, 46                                    # FLOOR: darkest albedo channel kept (black bodies stay readable)


def rot_x(p, a):
    c, s = math.cos(a), math.sin(a)
    return np.stack([p[:, 0], p[:, 1] * c - p[:, 2] * s, p[:, 1] * s + p[:, 2] * c], 1)


def camera(p):
    c, s = math.cos(YAW), math.sin(YAW)
    x, z = p[:, 0] * c - p[:, 2] * s, p[:, 0] * s + p[:, 2] * c
    cp, sp = math.cos(PITCH), math.sin(PITCH)
    y, z2 = p[:, 1] * cp - z * sp, p[:, 1] * sp + z * cp
    return np.stack([x, y, z2], 1)


def lens_colour(fid, i, n, kind):
    if fid.startswith("csc_blinder") or fid in ("csc_par_p64", "csc_fresnel_f1", "csc_pc_c1", "csc_ellipsoidal_e1"):
        return (255, 205, 120), (255, 244, 214)                                       # halogen lamp, pale core
    if fid in ("csc_pixelbar_b16", "csc_pixeltube_t1"):
        r, g, b = colorsys.hsv_to_rgb(i / max(1, n), 0.85, 1.0)
        return (int(r * 255), int(g * 255), int(b * 255)), (255, 255, 255)
    if fid == "csc_lasercore_l1":
        return (40, 255, 90), (220, 255, 225)
    if kind == "white":
        return (255, 236, 200), (255, 252, 240)
    return (200, 225, 255), (250, 252, 255)                                         # LED / discharge lens, cool white


def tilt_for(m, fid):
    if m.pan is not None:
        return math.radians(28)                     # moving heads: head raised towards the viewer
    if fid in ("csc_pixeltube_t1", "csc_pixelbar_b16"):
        return math.radians(18)
    return math.radians(12)


def geometry(m, fid):
    """Triangles (n, 3, 3) in the model frame, their colours, and the lens quads (emissive) as polygons in the model frame."""
    t = tilt_for(m, fid)
    tris, cols = [], []
    for part, groups in m.parts.items():
        for rgb, v in groups:
            p = np.asarray(v, dtype=np.float64).reshape(-1, 3)
            if part == "head":
                p = rot_x(p, t) + np.asarray(m.tilt)
            elif part == "yoke" and m.pan is not None:
                p = p + np.asarray(m.pan)
            tris.append(p.reshape(-1, 3, 3))
            cols.append(np.tile(np.array([(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255], dtype=np.float64), (len(p) // 3, 1)))
    lenses = []
    zones = [z for z in m.zones if z]
    if not zones:
        zones = [{"centre": m.beam_origin, "half": [m.lens_radius, m.lens_radius], "kind": "cell"}]
    for i, z in enumerate(zones):
        cx, cy, cz = z["centre"]
        hx, hy = max(z["half"][0], 0.012), max(z["half"][1], 0.012)
        round_ = fid.startswith("csc_blinder") or abs(hx - hy) < 1e-3
        k = 28 if round_ else 4
        pts = []
        for j in range(k):
            a = 2 * math.pi * (j + (0 if round_ else 0.5)) / k
            if round_:
                pts.append((cx + hx * 0.92 * math.cos(a), cy + hy * 0.92 * math.sin(a), cz - 0.004))
            else:
                pts.append((cx + hx * 0.9 * math.copysign(1, math.cos(a)), cy + hy * 0.9 * math.copysign(1, math.sin(a)), cz - 0.004))
        p = rot_x(np.asarray(pts), t) + np.asarray(m.tilt)
        lenses.append((p, lens_colour(fid, i, len(zones), z.get("kind", "cell"))))
    return np.concatenate(tris), np.concatenate(cols), lenses


def raster(img, zbuf, tri2, depth, colour, mask=None):
    """One screen triangle (3 x 2 pixels, 3 depths) into the z-buffer (larger depth = nearer)."""
    h, w = zbuf.shape
    x0, x1 = int(max(0, math.floor(tri2[:, 0].min()))), int(min(w - 1, math.ceil(tri2[:, 0].max())))
    y0, y1 = int(max(0, math.floor(tri2[:, 1].min()))), int(min(h - 1, math.ceil(tri2[:, 1].max())))
    if x1 < x0 or y1 < y0:
        return
    (ax, ay), (bx, by), (cx, cy) = tri2
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9:
        return
    xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    l0 = ((by - cy) * (xs - cx) + (cx - bx) * (ys - cy)) / den
    l1 = ((cy - ay) * (xs - cx) + (ax - cx) * (ys - cy)) / den
    l2 = 1 - l0 - l1
    inside = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
    if not inside.any():
        return
    d = l0 * depth[0] + l1 * depth[1] + l2 * depth[2]
    sub = zbuf[y0:y1 + 1, x0:x1 + 1]
    win = inside & (d > sub)
    sub[win] = d[win]
    img[y0:y1 + 1, x0:x1 + 1][win] = colour
    if mask is not None:
        mask[y0:y1 + 1, x0:x1 + 1][win] = 1


def render(fid):
    m = Model(os.path.join(ASSETS, "lightshow", fid + ".json"), 1)
    tris, cols, lenses = geometry(m, fid)
    cam = camera(tris.reshape(-1, 3)).reshape(-1, 3, 3)
    lens_cam = [(camera(p), c) for p, c in lenses]
    allpts = np.concatenate([cam.reshape(-1, 3)] + [p for p, _ in lens_cam])
    lo, hi = allpts[:, :2].min(0), allpts[:, :2].max(0)
    big = SIZE * SS
    scale = (big - 2 * MARGIN * SS) / max(hi - lo)
    centre = (lo + hi) / 2

    def screen(p):
        return np.stack([(p[..., 0] - centre[0]) * scale + big / 2, big / 2 - (p[..., 1] - centre[1]) * scale], -1)

    img = np.zeros((big, big, 3))
    zbuf = np.full((big, big), -1e9)
    emis = np.zeros((big, big, 3))
    emask = np.zeros((big, big))
    # shading: face normals in camera space (the camera looks along -z: depth = z)
    e1, e2 = cam[:, 1] - cam[:, 0], cam[:, 2] - cam[:, 0]
    n = np.cross(e1, e2)
    n /= np.maximum(1e-12, np.linalg.norm(n, axis=1, keepdims=True))
    n[n[:, 2] < 0] *= -1                                     # two-sided faces: the side facing the camera
    light = np.full(len(n), AMBIENT)
    for d, k in LIGHTS:
        dc = camera(d[None, :])[0]
        dc /= np.linalg.norm(dc)
        light += k * np.clip(n @ dc, 0, None)
    albedo = np.maximum(cols, FLOOR)
    shade = np.clip(albedo * light[:, None], 0, 255)
    s2 = screen(cam)
    for i in range(len(cam)):
        raster(img, zbuf, s2[i], cam[i, :, 2], shade[i])
    # lenses: emissive polygons (fan of triangles) just in front of the lens face, kept only where visible
    for p, (rim, core) in lens_cam:
        sp = screen(p)
        c2, cz = sp.mean(0), p[:, 2].mean() + 0.003
        for j in range(len(p)):
            tri2 = np.stack([c2, sp[j], sp[(j + 1) % len(p)]])
            raster(img, zbuf, tri2, np.array([cz, p[j, 2] + 0.003, p[(j + 1) % len(p), 2] + 0.003]), np.array(rim, float), emask)
        # pale core at 45 % of the size
        inner = c2 + (sp - c2) * 0.45
        for j in range(len(p)):
            tri2 = np.stack([c2, inner[j], inner[(j + 1) % len(p)]])
            raster(img, zbuf, tri2, np.array([cz + 0.001] * 3), np.array(core, float))
    alpha = (zbuf > -1e8).astype(np.float64)
    emis = img * emask[..., None]
    # anti-aliasing: premultiplied reduction to 128 px
    rgba = np.dstack([img * alpha[..., None], alpha * 255]).clip(0, 255).astype(np.uint8)
    small = Image.fromarray(rgba, "RGBA").resize((SIZE, SIZE), Image.LANCZOS)
    a = np.asarray(small).astype(np.float64)
    al = a[..., 3:4] / 255.0
    rgb = np.where(al > 0, a[..., :3] / np.maximum(al, 1e-6), 0)
    # glow of the lenses (added light, inside and just around the fixture)
    glow = Image.fromarray(emis.clip(0, 255).astype(np.uint8), "RGB").resize((SIZE, SIZE), Image.BILINEAR).filter(ImageFilter.GaussianBlur(2.2))
    g = np.asarray(glow).astype(np.float64) * 0.9
    glow_a = np.clip(g.max(2, keepdims=True) / 255.0 * 0.9, 0, 1)
    out_rgb = rgb * al + g * (1 - al) + g * 0.35 * al
    out_a = np.maximum(al, glow_a * (1 - al) * 0.85)
    out_rgb = np.where(out_a > 0, out_rgb / np.maximum(out_a, 1e-6), 0)
    # 1 px dark outline around the opaque silhouette
    solid = (al[..., 0] > 0.5).astype(np.uint8) * 255
    ring = np.asarray(Image.fromarray(solid).filter(ImageFilter.MaxFilter(3))).astype(np.float64) / 255.0
    ring = np.clip(ring - al[..., 0], 0, 1)[..., None] * 0.75
    out_rgb = out_rgb * (1 - ring * (1 - out_a)) + np.array([18, 18, 22]) * ring * (1 - out_a)
    out_a = np.maximum(out_a, ring)
    final = np.dstack([out_rgb.clip(0, 255), out_a[..., 0] * 255]).astype(np.uint8)
    return Image.fromarray(final, "RGBA")


def fixture_ids():
    return sorted(json.load(open(os.path.join(ROOT, "tools/external/modes.json")))["fixtures"], key=lambda f: 0)


def main():
    ids = sys.argv[1:] or [f["id"] for f in json.load(open(os.path.join(ROOT, "tools/external/modes.json")))["fixtures"]]
    for fid in ids:
        im = render(fid)
        im.save(os.path.join(ASSETS, "textures/item", fid + ".png"), optimize=True)
        # one flat item model for every fixture: the same icon on Forge and Fabric, no OBJ loader needed for the inventory
        with open(os.path.join(ASSETS, "models/item", fid + ".json"), "w", newline="\n") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "concertstage:item/" + fid}}, f, indent=1)
        print("icon", fid)


if __name__ == "__main__":
    main()
