"""Concert Stage Craft 1.2.0 rev. 15 - checks of the maths of concertstage_beam_volume.fsh / the shadow maps, on the CPU.

1. The segment of a line of sight inside the beam (slab + double cone, as in the shader) against a brute-force march, on 3000
   random beams and rays (narrow 0.3° to wide 90°, scene depth or sky).
2. The distance rebuilt from a depth of the shadow map (shadowLinear) against the real distance, with an OpenGL perspective.
Usage: python3 check_rev15_cone.py
"""
import numpy as np


def norm(v):
    return v / np.linalg.norm(v)


def interval(dirv, O, a, tan, r0, rng_, tScene):
    back = r0 / tan; apex = O - a * back; cos2 = 1 / (1 + tan * tan); co = -apex
    da = dirv @ a; ca = co @ a; hLo = back + 0.05; hHi = back + rng_
    meet = lambda x, y: (max(x[0], y[0]), min(x[1], y[1]))
    if abs(da) < 1e-5:
        if ca < hLo or ca > hHi: return None
        slab = (-1e6, 1e6)
    else:
        t0 = (hLo - ca) / da; t1 = (hHi - ca) / da; slab = (min(t0, t1), max(t0, t1))
    slab = meet(slab, (0.05, tScene))
    if slab[0] >= slab[1]: return None
    qa = da * da - cos2; qb = 2 * (da * ca - cos2 * (dirv @ co)); qc = ca * ca - cos2 * (co @ co)
    seg = (1, 0)
    if abs(qa) < 1e-6:
        if abs(qb) > 1e-6:
            tr = -qc / qb; seg = meet(slab, (tr, 1e6) if qb > 0 else (-1e6, tr))
    else:
        disc = qb * qb - 4 * qa * qc
        if disc <= 0:
            if qa > 0: seg = slab
        else:
            sd = np.sqrt(disc); ra = (-qb - sd) / (2 * qa); rb = (-qb + sd) / (2 * qa); r1, r2 = min(ra, rb), max(ra, rb)
            if qa < 0: seg = meet(slab, (r1, r2))
            else:
                s1 = meet(slab, (-1e6, r1)); s2 = meet(slab, (r2, 1e6))
                seg = s1 if (s1[1] - s1[0]) > (s2[1] - s2[0]) else s2
    return None if seg[0] >= seg[1] else seg


def inside(p, O, a, tan, r0, rng_):
    rel = p - O; al = rel @ a
    if al < 0.05 or al > rng_: return False
    return np.linalg.norm(rel - a * al) <= r0 + al * tan


def main():
    rng = np.random.default_rng(1)
    bad = tot = 0
    for _ in range(3000):
        O = rng.uniform(-10, 10, 3); a = norm(rng.normal(size=3)); tan = float(rng.choice([0.005, 0.05, 0.3, 1.0]))
        r0 = float(rng.uniform(0.05, 0.4)); R = float(rng.uniform(5, 40))
        tgt = O + a * rng.uniform(0, R) + rng.normal(size=3) * 2
        d = norm(tgt + rng.normal(size=3) * 0.5); tS = float(rng.choice([1e5, rng.uniform(1, 40)]))
        seg = interval(d, O, a, tan, r0, R, tS)
        ts = np.linspace(0.05, min(tS, 80), 4000)
        ins = np.array([inside(d * t, O, a, tan, r0, R) for t in ts])
        if ins.any():
            tot += 1
            lo, hi = ts[ins].min(), ts[ins].max()
            if seg is None or abs(seg[0] - lo) > 0.05 or abs(seg[1] - hi) > 0.05: bad += 1
        elif seg is not None and seg[1] - seg[0] > 0.05:
            tot += 1; bad += 1
    print(f"cone segment: {tot - bad} / {tot} rays right")
    n, f = 2.3, 40.0
    t = 1 / np.tan(0.4)
    P = np.array([[t, 0, 0, 0], [0, t, 0, 0], [0, 0, (f + n) / (n - f), 2 * f * n / (n - f)], [0, 0, -1, 0]])
    worst = 0.0
    for dist in np.linspace(n + 0.01, f - 0.01, 200):
        c = P @ np.array([0.3, 0.1, -dist, 1]); depth = c[2] / c[3] * 0.5 + 0.5
        lin = 2 * n * f / (f + n - (depth * 2 - 1) * (f - n)); worst = max(worst, abs(lin - dist))
    print(f"shadow depth -> distance: worst error {worst:.2e} blocks")
    assert bad == 0 and worst < 1e-6


if __name__ == '__main__':
    main()
