#!/usr/bin/env python3
"""Rev. 22 — the gobo wheels of the CSC fixtures that have one, drawn here from geometry (no picture taken from anywhere).

    python3 -I tools/gobos/make_gobos.py <repo root>

Writes common/src/main/resources/assets/concertstage/textures/lightshow/gobo_<wheel>_<n>.png (256 x 256, white = light) and
tools/gobos/wheels.json (names of the slots, read by the exports). The VORTEX X1 keeps its historical wheel (gobo_<n>.png) and the
PHANTOM S1 its second wheel (gobo_2_<n>.png): existing shows look the same.
"""
import json, math, os, sys
import numpy as np
from PIL import Image

N = 256
SS = 4                      # supersampling (anti-aliased edges)
R = 0.965                   # radius of the aperture, as the historical gobos


def grid():
    n = N * SS
    v = (np.arange(n) + 0.5) / n * 2 - 1
    x, y = np.meshgrid(v, -v)
    return x, y


X, Y = grid()
RAD = np.hypot(X, Y)
ANG = np.arctan2(Y, X)


def disc(cx, cy, r):
    return np.hypot(X - cx, Y - cy) <= r


def ring(r0, r1, cx=0.0, cy=0.0):
    d = np.hypot(X - cx, Y - cy)
    return (d >= r0) & (d <= r1)


def poly(pts):
    """Inside a convex or concave polygon (even-odd rule)."""
    inside = np.zeros_like(X, dtype=bool)
    n = len(pts)
    for i in range(n):
        x0, y0 = pts[i]; x1, y1 = pts[(i + 1) % n]
        cond = ((y0 > Y) != (y1 > Y))
        xint = (x1 - x0) * (Y - y0) / ((y1 - y0) if y1 != y0 else 1e-9) + x0
        inside ^= cond & (X < xint)
    return inside


def star(points, r_out, r_in, rot=0.0, cx=0.0, cy=0.0):
    pts = []
    for k in range(points * 2):
        r = r_out if k % 2 == 0 else r_in
        a = rot + math.pi / 2 + k * math.pi / points
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return poly(pts)


def regular(n, r, rot=0.0, cx=0.0, cy=0.0):
    return poly([(cx + r * math.cos(rot + math.pi / 2 + k * 2 * math.pi / n), cy + r * math.sin(rot + math.pi / 2 + k * 2 * math.pi / n)) for k in range(n)])


def noise(seed, scale):
    """Smooth value noise (sum of a few octaves), in [0, 1]."""
    rng = np.random.default_rng(seed)
    acc = np.zeros_like(X)
    amp, tot = 1.0, 0.0
    for o in range(4):
        cells = int(scale * 2 ** o) + 1
        g = rng.random((cells + 1, cells + 1))
        u = (X + 1) / 2 * cells; v = (Y + 1) / 2 * cells
        i = np.clip(u.astype(int), 0, cells - 1); j = np.clip(v.astype(int), 0, cells - 1)
        fu = u - i; fv = v - j
        fu = fu * fu * (3 - 2 * fu); fv = fv * fv * (3 - 2 * fv)
        a = g[j, i]; b = g[j, i + 1]; c = g[j + 1, i]; d = g[j + 1, i + 1]
        acc += amp * (a * (1 - fu) * (1 - fv) + b * fu * (1 - fv) + c * (1 - fu) * fv + d * fu * fv)
        tot += amp; amp *= 0.5
    return acc / tot


def save(mask, path):
    m = (mask & (RAD <= R)).astype(np.float32)
    img = Image.fromarray((m * 255).astype(np.uint8), "L").resize((N, N), Image.LANCZOS)
    rgba = Image.merge("RGBA", (img, img, img, Image.new("L", (N, N), 255)))
    rgba.save(path, optimize=True)


# ---------------------------------------------------------------- RAZOR B1 — beam: small graphic apertures, read far away in the haze
def razor():
    out = []
    out.append(("Pinhole", disc(0, 0, 0.22)))
    out.append(("Ring", ring(0.42, 0.62)))
    out.append(("Triangle", regular(3, 0.62)))
    out.append(("Three dots", disc(0, 0.42, 0.2) | disc(-0.364, -0.21, 0.2) | disc(0.364, -0.21, 0.2)))
    out.append(("Star", star(5, 0.72, 0.3)))
    out.append(("Slit", (np.abs(X) <= 0.11) & (np.abs(Y) <= 0.8)))
    out.append(("Double ring", ring(0.18, 0.32) | ring(0.58, 0.72)))
    petals = np.zeros_like(X, dtype=bool)
    for k in range(6):
        a = k * math.pi / 3
        petals |= disc(0.46 * math.cos(a), 0.46 * math.sin(a), 0.17)
    out.append(("Six dots", petals | disc(0, 0, 0.17)))
    return out


# ---------------------------------------------------------------- PROFILE P1 — theatre profile: windows, breakups, textures
def profile():
    out = []
    # 1 window with four panes and a cross frame
    pane = (np.abs(X) <= 0.6) & (np.abs(Y) <= 0.7) & (np.abs(X) >= 0.05) & (np.abs(Y) >= 0.05)
    out.append(("Window", pane))
    # 2 foliage breakup (leaves): thresholded noise with holes
    n = noise(22, 6.0)
    out.append(("Leaves", n > np.percentile(n[RAD <= R], 48)))
    # 3 clouds: softer, larger cells
    n2 = noise(7, 3.2)
    out.append(("Clouds", n2 > np.percentile(n2[RAD <= R], 52)))
    # 4 venetian blinds
    out.append(("Blinds", (np.mod(Y * 5.0 + 10, 1.0) < 0.55)))
    # 5 gothic arch window
    arch = ((np.abs(X) <= 0.45) & (Y <= 0.15) & (Y >= -0.75)) | (disc(-0.45 + 0.9 * 0.5, 0.15, 0.0)) 
    pointed = (np.hypot(X + 0.45, Y - 0.15) <= 0.9) & (np.hypot(X - 0.45, Y - 0.15) <= 0.9) & (Y >= 0.15)
    frame = (np.abs(X) <= 0.03) | (np.abs(Y + 0.3) <= 0.03)
    out.append(("Arch window", (arch | pointed) & ~frame))
    # 6 star field: scattered dots of several sizes
    rng = np.random.default_rng(5)
    field = np.zeros_like(X, dtype=bool)
    for _ in range(46):
        r = rng.uniform(0.0, 0.92); a = rng.uniform(0, 2 * math.pi)
        field |= disc(r * math.cos(a), r * math.sin(a), rng.choice([0.025, 0.035, 0.05, 0.07]))
    out.append(("Star field", field))
    # 7 water ripples: wavy concentric bands
    w = np.sin(RAD * 22 + 0.6 * np.sin(ANG * 5) + 1.2 * noise(11, 2.0)) > 0.15
    out.append(("Water", w))
    # 8 cells (voronoi breakup): the gaps between irregular cells are dark
    pts = np.random.default_rng(31).uniform(-1, 1, (26, 2))
    d = np.full(X.shape, 9.0); d2 = np.full(X.shape, 9.0)
    for px, py in pts:
        dd = np.hypot(X - px, Y - py)
        d2 = np.where(dd < d, d, np.minimum(d2, dd)); d = np.minimum(d, dd)
    out.append(("Cells", (d2 - d) > 0.06))
    return out


# ---------------------------------------------------------------- PHANTOM S1 — spot: concert graphics, rotate well
def phantom():
    out = []
    # 1 tunnel: spiral bands
    out.append(("Tunnel", np.mod(ANG / (2 * math.pi) * 3 + RAD * 3.2, 1.0) < 0.5))
    # 2 sun: disc + rays
    rays = (np.mod(ANG / (2 * math.pi) * 16, 1.0) < 0.45) & (RAD >= 0.42)
    out.append(("Sun", disc(0, 0, 0.3) | rays))
    # 3 honeycomb: hexagonal holes, thin dark walls (distance to the nearest centre of a hexagonal lattice)
    sz = 0.17
    best = np.full(X.shape, 9.0); hexd = np.full(X.shape, 9.0)
    for i in range(-8, 9):
        for j in range(-8, 9):
            cx = (i + 0.5 * (j % 2)) * sz * math.sqrt(3); cy = j * sz * 1.5
            if math.hypot(cx, cy) > 1.2: continue
            lx = np.abs(X - cx); ly = np.abs(Y - cy)
            hd = np.maximum(lx * 2 / math.sqrt(3), lx / math.sqrt(3) + ly)   # pointy-top hexagon (circumradius units)
            closer = hd < hexd
            hexd = np.where(closer, hd, hexd)
    out.append(("Honeycomb", hexd < sz * 0.86))
    # 4 orbit: dots on two circles
    orb = np.zeros_like(X, dtype=bool)
    for k in range(10):
        a = k * 2 * math.pi / 10; orb |= disc(0.72 * math.cos(a), 0.72 * math.sin(a), 0.1)
    for k in range(5):
        a = k * 2 * math.pi / 5 + 0.3; orb |= disc(0.36 * math.cos(a), 0.36 * math.sin(a), 0.08)
    out.append(("Orbit", orb))
    # 5 flames: wavy radial tongues
    out.append(("Flames", (np.mod(ANG / (2 * math.pi) * 7 + 0.18 * np.sin(RAD * 9), 1.0) < 0.42) & (RAD > 0.12)))
    # 6 half moon
    out.append(("Half moon", disc(0, 0, 0.8) & ~disc(0.32, 0.12, 0.66)))
    # 7 propeller: four curved blades
    blades = np.mod(ANG / (2 * math.pi) * 4 - RAD * 0.55, 1.0) < 0.3
    out.append(("Propeller", (blades & (RAD > 0.14)) | disc(0, 0, 0.1)))
    # 8 triangle mesh: a lattice of small triangles
    t = np.zeros_like(X, dtype=bool)
    step = 0.3
    for i in range(-4, 5):
        for j in range(-4, 5):
            cx = i * step + (j % 2) * step / 2; cy = j * step * 0.87
            if math.hypot(cx, cy) < 0.95:
                t |= regular(3, 0.11, rot=(0 if (i + j) % 2 == 0 else math.pi), cx=cx, cy=cy)
    out.append(("Triangles", t))
    return out


WHEELS = {
    # key: (fixture ids, slot names, generator or None for the historical files)
    "vortex": (["csc_vortex_x1"], ["Flower", "Dots", "Swirl", "Target", "Starburst", "Bars", "Waves", "Clover"], None),
    "phantom": (["csc_phantom_s1"], None, phantom),
    "profile": (["csc_profile_p1"], None, profile),
    "razor": (["csc_razor_b1"], None, razor),
}


def main(root):
    tex = os.path.join(root, "common/src/main/resources/assets/concertstage/textures/lightshow")
    meta = {}
    for key, (ids, names, gen) in WHEELS.items():
        if gen is None:
            meta[key] = {"fixtures": ids, "files": ["gobo_%d" % k for k in range(1, 9)], "names": names}
            continue
        slots = gen()
        files = []
        for k, (name, mask) in enumerate(slots, 1):
            fn = "gobo_%s_%d" % (key, k)
            save(mask, os.path.join(tex, fn + ".png"))
            files.append(fn)
        meta[key] = {"fixtures": ids, "files": files, "names": [n for n, _ in slots]}
    meta["phantom_2"] = {"fixtures": ["csc_phantom_s1"], "files": ["gobo_2_%d" % k for k in range(1, 9)],
                         "names": ["Six rays", "Ring", "Cross", "Bars", "Fine star", "Square", "Diagonals", "Rings"]}
    with open(os.path.join(root, "tools/gobos/wheels.json"), "w") as f:
        json.dump(meta, f, indent=1)
    print("ok", sum(len(v["files"]) for v in meta.values()), "slots")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
