#!/usr/bin/env python3
"""Offline approximation of the CSC light (1.2.0 rev. 3 vs rev. 5) on a simple night stage: floor, backdrop, a truss, 6 LUMORA W19
washes in amber and 4 VORTEX beams. It draws the SAME geometry and formulas as BeamRenderer / FixtureRenderer / BeamOptics
(rev. 3: additive cones + additive pools; rev. 5: screen-blended soft ribbons and discs + light projected on surfaces as
dst x (1 + src)). It is NOT a Minecraft capture: no blocks, no textures of the game, no fog, no Shimmer, no shaders.
Usage: python3 -I simulate_rev5.py <beam_soft.png> <out_prefix>"""
import math, sys
import numpy as np
from PIL import Image

W, H = 960, 540
FOV = math.radians(70)
GLOW = None

def norm(v):
    return v / np.linalg.norm(v)

class Cam:
    def __init__(self, pos, look):
        self.p = np.array(pos, float)
        f = norm(np.array(look, float) - self.p)
        r = norm(np.cross(f, [0, 1, 0])); u = np.cross(r, f)
        self.f, self.r, self.u = f, r, u
        self.k = (H / 2) / math.tan(FOV / 2)

    def proj(self, pts):
        d = pts - self.p
        z = d @ self.f
        x = d @ self.r; y = d @ self.u
        return np.stack([W / 2 + x / np.maximum(z, 1e-3) * self.k, H / 2 - y / np.maximum(z, 1e-3) * self.k], -1), z

def tri(cam, img, P, C, UV, mode, tex=True):
    """One triangle: P 3x3 positions, C 3x3 colours, UV 3x2. mode: add / screen / modulate / opaque."""
    p, z = cam.proj(P)
    if (z <= 0.15).any(): return
    xs, ys = p[:, 0], p[:, 1]
    x0, x1 = int(max(0, xs.min())), int(min(W - 1, xs.max()) + 1)
    y0, y1 = int(max(0, ys.min())), int(min(H - 1, ys.max()) + 1)
    if x1 <= x0 or y1 <= y0: return
    gx, gy = np.meshgrid(np.arange(x0, x1) + .5, np.arange(y0, y1) + .5)
    (ax, bx, cx), (ay, by, cy) = xs, ys
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9: return
    l1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
    l2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
    l3 = 1 - l1 - l2
    m = (l1 >= 0) & (l2 >= 0) & (l3 >= 0)
    if not m.any(): return
    # perspective-correct interpolation
    iz = np.array([1 / z[0], 1 / z[1], 1 / z[2]])
    wsum = l1 * iz[0] + l2 * iz[1] + l3 * iz[2]
    def interp(a):
        return (l1[..., None] * a[0] * iz[0] + l2[..., None] * a[1] * iz[1] + l3[..., None] * a[2] * iz[2]) / wsum[..., None]
    col = interp(C)
    if tex:
        uv = interp(UV)
        tu = np.clip((uv[..., 0] * (GLOW.shape[1] - 1)).astype(int), 0, GLOW.shape[1] - 1)
        tv = np.clip((uv[..., 1] * (GLOW.shape[0] - 1)).astype(int), 0, GLOW.shape[0] - 1)
        col = col * GLOW[tv, tu][..., None]
    col = np.clip(col, 0, 1)
    region = img[y0:y1, x0:x1]
    s = col[m]; d = region[m]
    if mode == 'add': region[m] = np.clip(d + s, 0, 1)
    elif mode == 'screen': region[m] = s + d * (1 - s)
    elif mode == 'modulate': region[m] = np.clip(d * (1 + s), 0, 1)
    else: region[m] = s

def quad(cam, img, P, C, UV, mode, tex=True):
    tri(cam, img, np.array([P[0], P[1], P[2]]), np.array([C[0], C[1], C[2]]), np.array([UV[0], UV[1], UV[2]]), mode, tex)
    tri(cam, img, np.array([P[0], P[2], P[3]]), np.array([C[0], C[2], C[3]]), np.array([UV[0], UV[2], UV[3]]), mode, tex)

# ---------------------------------------------------------------- scene
def scene(cam):
    img = np.zeros((H, W, 3))
    img[:] = np.array([0.02, 0.025, 0.05])                                  # night sky
    ambient = 0.16
    planks = np.array([0.55, 0.42, 0.28]) * ambient
    wall = np.array([0.22, 0.22, 0.25]) * ambient
    grass = np.array([0.25, 0.35, 0.2]) * ambient
    W0 = [[0, 0], [1, 0], [1, 1], [0, 1]]
    quad(cam, img, [[-30, 0, -30], [30, 0, -30], [30, 0, 0], [-30, 0, 0]], [grass] * 4, W0, 'opaque', False)
    for i in range(-10, 10):                                                 # stage floor in planks (2 tones)
        c = planks * (1.0 if i % 2 else 0.85)
        quad(cam, img, [[i, 1, 0], [i + 1, 1, 0], [i + 1, 1, 12], [i, 1, 12]], [c] * 4, W0, 'opaque', False)
    quad(cam, img, [[-10, 0, 0], [10, 0, 0], [10, 1, 0], [-10, 1, 0]], [planks * 0.6] * 4, W0, 'opaque', False)
    quad(cam, img, [[-10, 1, 12], [10, 1, 12], [10, 14, 12], [-10, 14, 12]], [wall] * 4, W0, 'opaque', False)
    t = np.array([0.12, 0.12, 0.13]) * ambient * 2
    for z in (2, 6):                                                         # two truss bars
        quad(cam, img, [[-10, 10.8, z - .3], [10, 10.8, z - .3], [10, 11.4, z - .3], [-10, 11.4, z - .3]], [t] * 4, W0, 'opaque', False)
    for x in (-3, 3):                                                        # two "instruments" (boxes)
        quad(cam, img, [[x - .6, 1, 7], [x + .6, 1, 7], [x + .6, 2.6, 7], [x - .6, 2.6, 7]], [np.array([.6, .5, .4]) * ambient] * 4, W0, 'opaque', False)
    return img

AMBER = np.array([1.0, 0.62, 0.25])
WHITE = np.array([1.0, 0.95, 0.9])

def fixtures():
    out = []
    for i in range(6):                                                       # W19 washes, 30 deg, aimed at the stage
        x = -7.5 + i * 3
        o = np.array([x, 10.3, 2.0]); a = norm(np.array([0, -1, 0.35]))
        out.append(('wash', o, a, math.tan(math.radians(15)), 0.45, AMBER))
    for i in range(4):                                                       # VORTEX beams, 4 deg, fanned towards the audience / sky
        x = -6 + i * 4
        o = np.array([x, 10.3, 6.0]); a = norm(np.array([(i - 1.5) * 0.25, 0.55, -1]))
        out.append(('spot', o, a, math.tan(math.radians(2)), 0.3, WHITE))
    return out

def hit(o, a, max_len=40):
    """Ray against the floor / stage / backdrop of the scene."""
    best = (max_len, None, None)
    if a[1] < -1e-6:
        for y, zr in ((1, (0, 12)), (0, (-30, 0))):
            t = (y - o[1]) / a[1]
            p = o + a * t
            if 0 < t < best[0] and zr[0] <= p[2] <= zr[1] and -30 <= p[0] <= 30: best = (t, p, np.array([0, 1.0, 0]))
    if a[2] > 1e-6:
        t = (12 - o[2]) / a[2]; p = o + a * t
        if 0 < t < best[0] and 1 <= p[1] <= 14 and -10 <= p[0] <= 10: best = (t, p, np.array([0, 0, -1.0]))
    return best

def basis(a):
    u = np.array([0, 1.0, 0]) if abs(a[1]) < 0.9 else np.array([1.0, 0, 0])
    u = norm(np.cross(u, a)); v = norm(np.cross(a, u))
    return u, v

# ---------------------------------------------------------------- rev. 3 (additive cones + additive pools)
def rev3(cam):
    img = scene(cam)
    for kind, o, a, tan, r0, col in fixtures():
        L, p, n = hit(o, a)
        hits = p is not None
        u, v = basis(a)
        seg, rings = 18, 8
        layers = [(0.5, r0 * .7, tan * .55, .012, 3, .03), (0.2, r0, tan, .02, 3, .03)] if kind == 'spot' else \
                 [(0.8, r0, tan, .025, 1.3, .1), (0.22, r0 * 1.1, tan * 1.3, .04, 1.2, .05)]
        density = math.sqrt(max(.35, min(1.8, 10 / max(2, math.degrees(math.atan(tan)) * 2))))
        for k0, rr0, tn, fade, ep, floor in layers:
            k = k0 * (density if kind == 'spot' else max(density, .8))
            D = [L * (j / rings) ** 1.35 for j in range(rings + 1)]
            for j in range(rings):
                for i in range(seg):
                    P, C = [], []
                    for (jj, ii) in ((j, i), (j, i + 1), (j + 1, i + 1), (j + 1, i)):
                        d = D[jj]; ang = 2 * math.pi * ii / seg
                        nrm = u * math.cos(ang) + v * math.sin(ang)
                        q = o + a * d + nrm * (rr0 + d * tn)
                        view = q - cam.p; facing = abs(nrm @ view) / np.linalg.norm(view)
                        ramp = min(1, .25 + d / .6); att = 1 / (1 + fade * d); tail = 1 if hits else max(0, 1 - (jj / rings) ** 2)
                        kk = ramp * att * tail * (floor + (1 - floor) * facing ** ep)
                        P.append(q); C.append(col * k * kk)
                    for _ in range(2):                                       # both windings
                        quad(cam, img, P, C, [[.5, .5]] * 4, 'add')
        if hits:
            rad = (r0 + L * tan) / max(.25, abs(a @ n)) * (1.8 if kind == 'spot' else 1.5)
            pk = (0.35 if kind == 'spot' else 0.7) / (1 + L * 0.015)
            uu, vv = basis(n)
            c = p + n * 0.01
            P = [c - uu * rad - vv * rad, c + uu * rad - vv * rad, c + uu * rad + vv * rad, c - uu * rad + vv * rad]
            for _ in range(2): quad(cam, img, P, [col * pk] * 4, [[0, 0], [1, 0], [1, 1], [0, 1]], 'add')
    return img

# ---------------------------------------------------------------- rev. 5 (BeamOptics)
R_REF, ABS = 0.55, 0.018

def air(d, L, hits, r0, tan):
    if d < 0 or d > L: return 0
    ramp = min(1, .35 + d / .8)
    spread = min(1, R_REF / (r0 + d * tan)) ** 0.85
    tail = 1
    if not hits:
        t = d / L
        if t > .6: x = (1 - t) / .4; tail = x * x * (3 - 2 * x)
    return ramp * spread * math.exp(-ABS * d) * tail

def surface(flux, dist, foot, cosI):
    return min(3, 2.4 * flux / (1 + .22 * foot) / (1 + .012 * dist) * (.25 + .75 * cosI))

def soft_beam(cam, img, o, a, L, r0, tan, col, hits, width=1.0, segs=9, nd=7, discs=True):
    mid = o + a * L * .5; vd = mid - cam.p; cosv = (a @ vd) / np.linalg.norm(vd)
    side = .3 + .7 * math.sqrt(max(0, 1 - cosv * cosv))
    rings = []
    for k in range(segs + 1):
        t = k / segs; d = L * t * (.45 + .55 * t)
        c = o + a * d; r = (r0 + d * tan) * width
        kk = air(d, L, hits, r0, tan) * side
        dc = np.linalg.norm(c - cam.p)
        if dc < 2: kk *= dc / 2
        nrm = np.cross(a, c - cam.p); nrm /= max(np.linalg.norm(nrm), 1e-6)
        rings.append((c - nrm * r * 1.15, c + nrm * r * 1.15, kk))
    for k in range(segs):
        (a0, b0, k0), (a1, b1, k1) = rings[k], rings[k + 1]
        if k0 < .002 and k1 < .002: continue
        quad(cam, img, [a0, b0, b1, a1], [col * k0, col * k0, col * k1, col * k1], [[0, .5], [1, .5], [1, .5], [0, .5]], 'screen')
    aw = 0 if abs(cosv) < .12 else (abs(cosv) - .12) / .88
    if not discs or aw <= .01: return
    u, v = basis(a); per = aw * .55 * 6 / nd
    for j in range(nd):
        d = L * (j + .6) / (nd + .4); kk = air(d, L, hits, r0, tan) * per
        if kk < .003: continue
        rad = (r0 + d * tan) * 1.1 * width; c = o + a * d
        quad(cam, img, [c - u * rad - v * rad, c + u * rad - v * rad, c + u * rad + v * rad, c - u * rad + v * rad], [col * kk] * 4,
             [[0, 0], [1, 0], [1, 1], [0, 1]], 'screen')

AMBIENT_LIGHT = 4 / 15        # outdoors at night: sky light 15 - darkening 11, no block light

def relight(e, ambient):
    if e <= 0: return 1
    dark = 1 - max(0, min(1, ambient))
    return min(12, 1 + e * (0.5 + 6 * dark * dark))

def ray_offsets(count):
    out = [(0, 0)]
    ring = count - 1
    for i in range(1, count):
        if ring > 6:
            inner = ring // 2
            frac, k, n = (.45, i - 1, inner) if i <= inner else (.85, i - 1 - inner, ring - inner)
        else:
            frac, k, n = .62, i - 1, ring
        ang = 2 * math.pi * k / n + (math.pi / n if frac > .8 else 0)
        out.append((math.cos(ang) * frac, math.sin(ang) * frac))
    return out

def rev5(cam, haze=1.0):
    img = scene(cam)
    fx = fixtures()
    # 1. light on surfaces first (projected, dst x (1 + src))
    for kind, o, a, tan, r0, col in fx:
        L, p, n = hit(o, a)
        foot0 = r0 + L * tan
        count = 1 if foot0 < 1.2 else 7
        u, v = basis(a)
        for i, (ox, oy) in enumerate(ray_offsets(count)):
            d = norm(a + (u * ox + v * oy) * tan)
            Li, pi_, ni = hit(o, d)
            if pi_ is None: continue
            foot = r0 + Li * tan
            e = surface(1.0, Li, foot, abs(d @ ni)) * (1 if i == 0 else .75)
            rad = max(.35, foot * (1.05 if count == 1 else .7 if i == 0 else .55)) * (1.15 if kind == 'spot' else 1)
            uu, vv = basis(ni); c = pi_ + ni * .02
            P = [c - uu * rad - vv * rad, c + uu * rad - vv * rad, c + uu * rad + vv * rad, c - uu * rad + vv * rad]
            F = relight(e, AMBIENT_LIGHT)
            for _ in range(4):
                if F <= 1.01: break
                s1 = min(1, F - 1)
                quad(cam, img, P, [col * s1] * 4, [[0, 0], [1, 0], [1, 1], [0, 1]], 'modulate')
                F /= 1 + s1
    # 2. beams in the air (screen)
    for kind, o, a, tan, r0, col in fx:
        L, p, n = hit(o, a); hits = p is not None
        if kind == 'spot':
            soft_beam(cam, img, o, a, L, r0, tan, col * .55 * haze, hits)
            soft_beam(cam, img, o, a, L, r0 * .6, tan * .45, col * .6 * haze, hits, width=.5, discs=False)
        else:
            soft_beam(cam, img, o, a, L, r0, tan, col * 1.1 * haze, hits)
    return img

def save(img, path):
    Image.fromarray((np.clip(img, 0, 1) ** (1 / 1.1) * 255).astype(np.uint8)).save(path)

if __name__ == '__main__':
    g = np.asarray(Image.open(sys.argv[1]).convert('L'), float) / 255.0
    GLOW = g
    prefix = sys.argv[2]
    views = {'front': Cam([0, 4, -20], [0, 5, 6]), 'side': Cam([-24, 6, 4], [0, 5, 5]), 'back': Cam([0, 12, 22], [0, 4, 0])}
    for name, cam in views.items():
        a, b = rev3(cam), rev5(cam)
        both = np.concatenate([a, np.ones((H, 6, 3)), b], 1)
        save(both, f"{prefix}_{name}.png")
        print(name, "mean rev3 %.3f rev5 %.3f, pixels > 0.97: rev3 %.1f %% rev5 %.1f %%" % (
            a.mean(), b.mean(), (a.max(2) > .97).mean() * 100, (b.max(2) > .97).mean() * 100))
