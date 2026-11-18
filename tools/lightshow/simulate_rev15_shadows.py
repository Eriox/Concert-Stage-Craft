"""Concert Stage Craft 1.2.0 rev. 15 - simulation of the beam shadows (CPU copy of the shader maths, not a Minecraft capture).

Scene: a fixture 6 blocks up throws a gobo through a box truss (chords + diagonal braces) onto the floor. Same maths as
concertstage_beam_volume.fsh / concertstage_light_projector.fsh: line of sight cut by the cone and the scene depth, pattern of the
gobo, light law (ramp, 1/r^0.85, absorption), visibility from the light (here an exact ray test, what the shadow map stores).
Left: rev. 14 (no occlusion), right: rev. 15.  Usage: python3 simulate_rev15_shadows.py <pattern.png> <out.png>
"""
import sys
import numpy as np
from PIL import Image

W, H, STEPS = 420, 300, 48
R_REF, ABS = 0.55, 0.018
LO, HI, LO2, HI2 = 0.25 / 16, 2.75 / 16, 13.25 / 16, 15.75 / 16


def norm(v):
    return v / np.linalg.norm(v)


def truss_boxes(x0, x1, y, z):
    """Chords along X of a 1x1 truss from x0 to x1 (bottom at y, side at z) and zig-zag braces on its four faces."""
    b = []
    for a in ((LO, HI), (LO2, HI2)):
        for c in ((LO, HI), (LO2, HI2)):
            b.append((x0, y + a[0], z + c[0], x1, y + a[1], z + c[1]))
    t = 1.2 / 16
    for cell in range(int(x0), int(x1)):
        for face in range(4):
            for k in range(6):                       # a diagonal as 6 small boxes
                u0, u1 = k / 6, (k + 1) / 6
                um = (u0 + u1) / 2
                v = HI + (LO2 - HI) * (um if cell % 2 == 0 else 1 - um)
                xs, xe = cell + u0, cell + u1
                if face == 0:   b.append((xs, y + v - t, z + LO, xe, y + v + t, z + HI))
                elif face == 1: b.append((xs, y + v - t, z + LO2, xe, y + v + t, z + HI2))
                elif face == 2: b.append((xs, y + LO, z + v - t, xe, y + HI, z + v + t))
                else:           b.append((xs, y + LO2, z + v - t, xe, y + HI2, z + v + t))
    return np.array(b, dtype=np.float32)


def hit_boxes(ro, rd, boxes, tmax):
    """Nearest hit (t) of rays (N,3) against boxes (M,6), inf when none before tmax."""
    best = np.full(ro.shape[0], np.inf, dtype=np.float32)
    inv = 1.0 / np.where(np.abs(rd) < 1e-9, 1e-9, rd)
    for bx in boxes:
        t0 = (bx[:3] - ro) * inv
        t1 = (bx[3:] - ro) * inv
        tn = np.minimum(t0, t1).max(axis=1)
        tf = np.maximum(t0, t1).min(axis=1)
        ok = (tf >= np.maximum(tn, 0)) & (tn < tmax)
        best = np.where(ok & (tn < best), np.maximum(tn, 0), best)
    return best


def main():
    gobo = np.asarray(Image.open(sys.argv[1]).convert('L'), dtype=np.float32) / 255.0
    gh, gw = gobo.shape

    def pattern(q):
        inside = (np.abs(q[..., 0]) <= 1) & (np.abs(q[..., 1]) <= 1)
        u = np.clip(((q[..., 0] * 0.5 + 0.5) * (gw - 1)).astype(int), 0, gw - 1)
        v = np.clip(((q[..., 1] * 0.5 + 0.5) * (gh - 1)).astype(int), 0, gh - 1)
        return np.where(inside, gobo[v, u], 0.0)

    boxes = truss_boxes(-5, 5, 3.0, -0.1)
    O = np.array([0.0, 6.2, -1.6], np.float32)
    a = norm(np.array([0.0, -1.0, 0.75], np.float32))
    side = norm(np.cross(np.array([0, 1, 0], np.float32), a))
    up = np.cross(a, side)
    tan, r0, rng = np.tan(np.radians(15)), 0.12, 14.0
    apex = O - a * (r0 / tan)
    cam = np.array([9.0, 3.4, 2.4], np.float32)
    fwd = norm(np.array([-1.0, -0.12, -0.18], np.float32))
    right = norm(np.cross(fwd, [0, 1, 0]))
    cup = np.cross(right, fwd)
    ys, xs = np.mgrid[0:H, 0:W]
    px = (xs + 0.5) / W * 2 - 1
    py = 1 - (ys + 0.5) / H * 2
    f = 1 / np.tan(np.radians(35))
    rd = fwd[None] + (px.reshape(-1, 1) * right[None] * (W / H) + py.reshape(-1, 1) * cup[None]) / f
    rd /= np.linalg.norm(rd, axis=1, keepdims=True)
    ro = np.repeat(cam[None], rd.shape[0], axis=0)
    # scene depth: floor y = 0 and the truss
    tfloor = np.where(rd[:, 1] < -1e-6, -cam[1] / rd[:, 1], np.inf)
    ttruss = hit_boxes(ro, rd, boxes, 100.0)
    tscene = np.minimum(tfloor, ttruss)

    def visible(p):                                   # what the shadow map answers: nothing between the light and p
        d = p - apex
        L = np.linalg.norm(d, axis=1)
        h = hit_boxes(np.repeat(apex[None], p.shape[0], 0), d / L[:, None], boxes, 100.0)
        return (h >= L - (0.05 + 0.006 * L)).astype(np.float32)

    def beam(shadows):
        t0, t1 = np.full(rd.shape[0], 0.05, np.float32), np.minimum(tscene, 40.0)
        dt = (t1 - t0) / STEPS
        jit = np.random.default_rng(3).random(rd.shape[0]).astype(np.float32)
        acc = np.zeros(rd.shape[0], np.float32)
        for i in range(STEPS):
            t = t0 + (i + jit) * dt
            p = ro + rd * t[:, None]
            rel = p - O
            al = rel @ a
            hw = r0 + np.maximum(al, 0) * tan
            q = np.stack([rel @ side, rel @ up], -1) / hw[:, None]
            k = pattern(q) * ((al > 0.05) & (al < rng))
            m = k > 0.004
            if shadows and m.any():
                k[m] *= visible(p[m])
            dens = k * np.minimum(1, 0.35 + al / 0.8) * np.minimum(1, R_REF / hw) ** 0.85 * np.exp(-ABS * al) / (2 * hw)
            acc += dens * dt
        # the light on the floor (projected), shadowed by the tubes
        fin = np.isfinite(tscene)
        pf = ro + rd * np.where(fin, tscene, 0.0)[:, None]
        rel = pf - O
        al = rel @ a
        hw = r0 + np.maximum(al, 0) * tan
        q = np.stack([rel @ side, rel @ up], -1) / hw[:, None]
        k = pattern(q) * ((al > 0.05) & fin)
        if shadows:
            m = k > 0.004
            k[m] *= visible(pf[m])
        e = 2.4 * 1.0 / (1 + 0.22 * hw) / (1 + 0.035 * np.linalg.norm(rel, axis=1))
        return acc, k * np.minimum(3, e * 2.5)

    base = np.where(np.isfinite(ttruss) & (ttruss <= tfloor), 0.10, np.where(np.isfinite(tfloor), 0.05, 0.015))
    col = np.array([0.35, 0.45, 1.0], np.float32)
    out = []
    for shadows in (False, True):
        acc, surf = beam(shadows)
        img = base[:, None] * (1 + surf[:, None] * col[None])
        light = np.clip(acc[:, None] * col[None] * 0.9, 0, 1)
        img = 1 - (1 - np.clip(img, 0, 1)) * (1 - light)
        out.append((np.clip(img, 0, 1).reshape(H, W, 3) ** (1 / 1.6) * 255).astype(np.uint8))
    Image.fromarray(np.concatenate([out[0], np.full((H, 6, 3), 255, np.uint8), out[1]], 1)).save(sys.argv[2])


if __name__ == '__main__':
    main()
