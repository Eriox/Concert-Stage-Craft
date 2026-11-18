"""
CSC shader harness — test scenes rendered with the REAL shaders of the mod (concertstage_light_projector, concertstage_beam_volume and
the common optical mask concertstage_optics.glsl), executed on Mesa llvmpipe by harness.c.

What is real: the GLSL the mod ships, its uniforms (computed by DumpOptics.java with the formulas of FixtureRenderer#beam), the gobo
textures of the mod. What is NOT Minecraft: the scene (a floor, a back wall, a truss bar, stairs, a slab — ray cast here into a depth
buffer and a shadow map, flat grey materials), the composition of the passes (done here as the blend modes of the json files say),
no Minecraft lighting, no textures of blocks, no Shimmer, no other light of the fixture (halo, emitters, point lights, ribbons).
Every picture is labelled "shader harness — not Minecraft".

Usage: python3 -I tools/shader_harness/scene.py <repo root> <out dir>   (needs /tmp/harness, numpy, Pillow, javac)
"""
import math
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
OUT = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else "validation/rev16_harness")
SH = os.path.join(ROOT, "common/src/main/resources/assets/minecraft/shaders")
TEX = os.path.join(ROOT, "common/src/main/resources/assets/concertstage/textures/lightshow")
HARNESS = "/tmp/harness"
W, H = 480, 300
TMP = tempfile.mkdtemp()


# ------------------------------------------------------------------ matrices (JOML / OpenGL conventions)
def perspective(fovy, aspect, n, f):
    t = 1.0 / math.tan(fovy / 2)
    m = np.zeros((4, 4))
    m[0, 0] = t / aspect; m[1, 1] = t
    m[2, 2] = (f + n) / (n - f); m[2, 3] = 2 * f * n / (n - f); m[3, 2] = -1
    return m


def look_at(eye, target, up):
    eye, target, up = map(np.asarray, (eye, target, up))
    f = target - eye; f = f / np.linalg.norm(f)
    s = np.cross(f, up); s = s / np.linalg.norm(s)
    u = np.cross(s, f)
    m = np.identity(4)
    m[0, :3] = s; m[1, :3] = u; m[2, :3] = -f
    m[:3, 3] = -m[:3, :3] @ eye
    return m


def mat_str(m):
    return " ".join("%.9g" % v for v in m.T.flatten())   # column major


# ------------------------------------------------------------------ scene: planes and boxes, ray cast
class Scene:
    def __init__(self):
        self.boxes = []            # (lo, hi, albedo)
        self.floor = -3.0
        self.wall = -30.0

    def box(self, lo, hi, albedo):
        self.boxes.append((np.array(lo, float), np.array(hi, float), albedo))

    def cast(self, o, d):
        """o (3,), d (N, 3) -> t (N,), albedo (N,), normal (N, 3)"""
        n = d.shape[0]
        t = np.full(n, np.inf); alb = np.zeros(n); nor = np.zeros((n, 3))
        with np.errstate(divide="ignore", invalid="ignore"):
            tf = (self.floor - o[1]) / d[:, 1]
            hit = (tf > 1e-4) & (tf < t)
            t[hit] = tf[hit]; alb[hit] = 0.42; nor[hit] = (0, 1, 0)
            tw = (self.wall - o[2]) / d[:, 2]
            hit = (tw > 1e-4) & (tw < t)
            t[hit] = tw[hit]; alb[hit] = 0.36; nor[hit] = (0, 0, 1)
            for lo, hi, a in self.boxes:
                t1 = (lo - o) / d; t2 = (hi - o) / d
                tmin = np.minimum(t1, t2); tmax = np.maximum(t1, t2)
                tn = tmin.max(axis=1); tx = tmax.min(axis=1)
                hit = (tn <= tx) & (tn > 1e-4) & (tn < t)
                t[hit] = tn[hit]; alb[hit] = a
                ax = tmin.argmax(axis=1)
                for k in range(3):
                    m = hit & (ax == k)
                    nv = np.zeros(3); nv[k] = 1
                    nor[m] = -np.sign(d[m, k])[:, None] * nv
        return t, alb, nor


def depth_buffer(scene, eye, view, proj):
    inv = np.linalg.inv(proj @ view)
    ys, xs = np.mgrid[0:H, 0:W]
    ndc = np.stack([(xs + 0.5) / W * 2 - 1, (ys + 0.5) / H * 2 - 1, np.ones_like(xs, float), np.ones_like(xs, float)], -1).reshape(-1, 4)
    p = ndc @ inv.T
    p = p[:, :3] / p[:, 3:4]
    d = p - eye; d /= np.linalg.norm(d, axis=1, keepdims=True)
    t, alb, nor = scene.cast(np.asarray(eye, float), d)
    world = eye + d * np.where(np.isfinite(t), t, 1e4)[:, None]
    vp = np.c_[world, np.ones(len(world))] @ (proj @ view).T
    z = vp[:, 2] / vp[:, 3] * 0.5 + 0.5
    z[~np.isfinite(t)] = 1.0
    # base colour: albedo x a dim ambient + a little sky light from above (a stage at night, before the fixtures)
    shade = alb * (0.22 + 0.08 * np.clip(nor[:, 1], 0, 1))
    shade[~np.isfinite(t)] = 0.02
    return z.reshape(H, W).astype(np.float32), shade.reshape(H, W)


def shadow_map(scene, apex, axis, side, tan, near, far, size=256):
    up = np.cross(axis, side); up /= np.linalg.norm(up)
    view = look_at(apex, apex + axis, up)
    fov = 2 * math.atan(tan * 1.04 + 0.01)
    proj = perspective(fov, 1.0, near, far)
    inv = np.linalg.inv(proj @ view)
    ys, xs = np.mgrid[0:size, 0:size]
    ndc = np.stack([(xs + 0.5) / size * 2 - 1, (ys + 0.5) / size * 2 - 1, np.ones_like(xs, float), np.ones_like(xs, float)], -1).reshape(-1, 4)
    p = ndc @ inv.T
    p = p[:, :3] / p[:, 3:4]
    d = p - apex; d /= np.linalg.norm(d, axis=1, keepdims=True)
    t, _, _ = scene.cast(np.asarray(apex, float), d)
    world = apex + d * np.where(np.isfinite(t), t, 1e4)[:, None]
    cp = np.c_[world, np.ones(len(world))] @ (proj @ view).T
    z = cp[:, 2] / cp[:, 3] * 0.5 + 0.5
    z[~np.isfinite(t) | (z > 1)] = 1.0
    return z.reshape(size, size).astype(np.float32), proj @ view


# ------------------------------------------------------------------ harness calls
def raw(name, arr):
    path = os.path.join(TMP, name)
    np.ascontiguousarray(arr).tofile(path)
    return path


GOBO_RAW = {}


def gobo_raw(slot):
    if slot not in GOBO_RAW:
        im = Image.open(os.path.join(TEX, "gobo_%d.png" % slot)).convert("RGBA")
        GOBO_RAW[slot] = (raw("gobo%d.raw" % slot, np.asarray(im, np.uint8)), im.size)
    return GOBO_RAW[slot]


def run(prog, lines):
    uf = os.path.join(TMP, "u.txt")
    open(uf, "w").write("\n".join(lines) + "\n")
    out = os.path.join(TMP, "o.raw")
    env = dict(os.environ, EGL_PLATFORM="surfaceless")
    r = subprocess.run([HARNESS, os.path.join(SH, "core", prog + ".vsh"), os.path.join(SH, "core", prog + ".fsh"), os.path.join(SH, "include"),
                        uf, str(W), str(H), out], env=env, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr)
    LOG.append(r.stderr.strip().splitlines()[-1].replace(ROOT + "/", ""))
    a = np.fromfile(out, np.float32).reshape(H, W, 4)
    return a


LOG = []


def dump_optics(cases):
    cls = os.path.join(TMP, "cls")
    if not os.path.isdir(cls):
        os.makedirs(cls)
        compile_java(cls)
    r = subprocess.run(["java", "-Dcsc.calib=" + os.environ.get("CSC_CALIB", "17")] + os.environ.get("CSC_JOPTS", "").split() + ["-cp", cls, "DumpOptics"], input="\n".join(cases) + "\n", capture_output=True, text=True, cwd=ROOT, check=True)
    return parse_optics(r.stdout)


def compile_java(cls):
    src = [os.path.join(ROOT, "tools/shader_harness/DumpOptics.java")]
    for d in ("common/src/main/java/com/concertstage/light/core", "common/src/main/java/com/concertstage/light/dmx"):
        src += [os.path.join(ROOT, d, f) for f in os.listdir(os.path.join(ROOT, d)) if f.endswith(".java")]
    subprocess.run(["javac", "-nowarn", "-d", cls] + src, check=True, capture_output=True)


def parse_optics(stdout):
    out = []
    for line in stdout.strip().splitlines():
        kv = dict(x.split("=", 1) for x in line.split())
        d = {k: float(v) for k, v in kv.items() if k not in ("opt", "tint")}
        d["opt"] = [float(v) for v in kv["opt"].split(",")]
        d["tint"] = [float(v) for v in kv["tint"].split(",")]
        out.append(d)
    return out


# ------------------------------------------------------------------ one picture
def light_passes(scene, eye, view, proj, depth, fix, aim, state, colour, gobo_deg, debug, shadow_scene=None):
    """projector (multiply, add) and volume outputs of one fixture"""
    p = dump_optics([" ".join(str(v) for v in state)])[0]
    fix = np.asarray(fix, float)
    axis = np.asarray(aim, float) - fix; axis /= np.linalg.norm(axis)
    side = np.cross((0, 1, 0) if abs(axis[1]) < 0.9 else (1, 0, 0), axis); side /= np.linalg.norm(side)
    tan, r0 = p["tanO"], p["r0"]
    rng = min(p["throw"], 40.0)
    back = min(60.0, r0 / max(tan, 0.004))
    apex = fix - axis * back
    near, far = back + 0.3, back + rng
    smap, smat_world = shadow_map(shadow_scene or scene, apex, axis, side, tan, near, far)
    smat = smat_world @ np.linalg.inv(view)
    to_v = lambda v: (view @ np.r_[v, 1])[:3]
    dir_v = lambda v: view[:3, :3] @ v
    ov, av, sv = to_v(fix), dir_v(axis), dir_v(side)
    opt, tint = p["opt"], p["tint"]
    tinted = p["tinted"] > 0.5
    common = ["f TargetSize %d %d" % (W, H), "m InvProjMat " + mat_str(np.linalg.inv(proj)),
              "t DepthSampler 0 %s %d %d r32f" % (raw("depth.raw", depth), W, H),
              "t ShadowSampler 2 %s 256 256 r32f" % raw("shadow.raw", smap), "m ShadowMat " + mat_str(smat),
              "f ShadowNF %f %f 1" % (near, far),
              "f OptKind %f %f %f %f" % tuple(opt[0:4]), "f OptShape %f %f %f %f" % tuple(opt[4:8]), "f DebugMode %d" % debug,
              "f OptTintInfo %f %f 0 0" % (tint[0], tint[1])]
    common += ["f OptRing%d %f %f %f %f" % ((i,) + tuple(opt[8 + 4 * i:12 + 4 * i])) for i in range(6)]
    common += ["f OptTint%d %f %f %f 0" % ((g,) + tuple(tint[4 + 4 * g:7 + 4 * g])) for g in range(8)]
    gp, gs = gobo_raw(int(state[3]))
    common.append("t PatternSampler 1 %s %d %d %s" % (gp, gs[0], gs[1], os.environ.get("CSC_GOBO_FMT", "rgba8mip")))
    rot = math.radians(gobo_deg)
    peak = max(colour)
    hue = [1.0, 1.0, 1.0] if tinted else [c / peak for c in colour]
    vcol = [peak] * 3 if tinted else list(colour)
    pl = common + ["m ModelViewMat " + mat_str(np.identity(4)), "m ProjMat " + mat_str(np.identity(4)),
                   "f Gain 5.0", "f PassRoot 1.0", "f AddK 0.06",
                   "f ProjOrigin %f %f %f" % tuple(ov), "f ProjAxis %f %f %f" % tuple(av), "f ProjSide %f %f %f" % tuple(sv),
                   "f ProjShape %f %f %f %f" % (tan, math.cos(rot), math.sin(rot), rng),
                   "f ProjLight %f %f %f %f" % (hue[0], hue[1], hue[2], p["flux"]), "f ProjOptics %f %f 0 0" % (r0, p["pspread"])]
    # rev. 16b: the light of the fixture added into the accumulation target (AccumMode), applied once for all by the composite
    acc = run("concertstage_light_projector", pl + ["f AddMode 1", "f AccumMode 1"])
    mul = add = None
    vol = None
    if p["kv"] > 0 and debug != 5:
        vr = min(rng, p["vrange"])
        vl = common + ["f BeamOrigin %f %f %f" % tuple(ov), "f BeamAxis %f %f %f" % tuple(av), "f BeamSide %f %f %f" % tuple(sv),
                       "f BeamShape %f %f %f %f" % (tan, math.cos(rot), math.sin(rot), vr),
                       "f BeamLight %f %f %f %f" % (vcol[0] * p["kv"], vcol[1] * p["kv"], vcol[2] * p["kv"], p["absorb"]),
                       "f BeamOptics %f %f %f %f" % (p["rV"], p["vspread"], 30.0, max(2.0, vr * 0.4) if p["strobe"] else 2.0)]
        vol = run("concertstage_beam_volume", vl)
    return acc, None, vol


def veil(depth, proj, haze, dark=1.0):
    dens = 0.0 if haze <= 1 else 0.0128 * (haze - 1) ** 1.3
    if dens <= 0:
        return None
    grey = 0.04 + 0.56 * (1 - dark)
    lines = ["f TargetSize %d %d" % (W, H), "m InvProjMat " + mat_str(np.linalg.inv(proj)),
             "t DepthSampler 0 %s %d %d r32f" % (raw("depth.raw", depth), W, H), "f HazeVeil %f %f %f %f" % (grey, grey * 1.04, grey * 1.1, dens)]
    return run("concertstage_haze", lines)


def render(scene, eye, target, fix, aim, state, colour=(1.0, 1.0, 1.0), gobo_deg=25.0, title="", debug=0, more=(), shadow_scene=None):
    """state: (id, zoom, focus, gobo, prism, dimmer, iris, haze, flash[, zones]); more: other fixtures (fix, aim, state, colour)"""
    eye = np.asarray(eye, float)
    view = look_at(eye, target, (0, 1, 0))
    proj = perspective(math.radians(70), W / H, 0.05, 256)
    depth, base = depth_buffer(scene, eye, view, proj)
    img = base[..., None] * np.array([1.0, 1.0, 1.0])
    fixtures = [(fix, aim, state, colour)] + list(more)
    if debug in (6, 7):
        # rev. 16c: depth / normals as the engine reads them (concertstage_light_debug)
        dv = run("concertstage_light_debug", ["f TargetSize %d %d" % (W, H), "m InvProjMat " + mat_str(np.linalg.inv(proj)),
                                              "t DepthSampler 0 %s %d %d r32f" % (raw("depth.raw", depth), W, H), "f DebugMode %d" % debug])
        return finish(dv[..., :3], title)
    outs = [light_passes(scene, eye, view, proj, depth, f, a, st, c, gobo_deg, debug, shadow_scene) for f, a, st, c in fixtures]
    # light on surfaces (rev. 16b): sum of the fixtures in the 8-bit accumulation target, then the composite (multiply + additive)
    acc = np.zeros((H, W, 4), np.float32)
    for a, _, _ in outs:
        acc += a * (a[..., 3:4] >= 0)          # rgb: light, alpha: reflected light (rev. 16d); discarded pixels are 0
    acc = np.clip(np.round(np.clip(acc, 0, 1) * 255) / 255, 0, 1)
    accq = (acc * 255 + 0.5).astype(np.uint8)
    cl = ["f TargetSize %d %d" % (W, H), "t LightSampler 0 %s %d %d rgba8" % (raw("acc.raw", accq[::1]), W, H), "f Gain 5.0", "f AddK 0.06",
          "f DebugMode %d" % (4 if debug == 4 else 5 if debug == 5 else 0)]
    mul = run("concertstage_light_composite", cl + ["f PassRoot 1.0", "f AddMode 0"])
    add = run("concertstage_light_composite", cl + ["f PassRoot 1.0", "f AddMode 1"])
    if debug:
        img = img * 0.5 + add[..., :3] * (add[..., 3:4] > 0)
    else:
        img = img * (1 + mul[..., :3] * (mul[..., 3:4] > 0)) + add[..., :3] * (add[..., 3:4] > 0)
    # haze veil (rev. 16b), before the beams
    v = veil(depth, proj, float(state[7]))
    if v is not None:
        a = v[..., 3:4] * (v[..., 3:4] > 0)
        img = v[..., :3] * a + img * (1 - a)
    for _, _, vol in outs:
        if vol is None:
            continue
        src = vol[..., :3] * (vol[..., 3:4] > 0)
        img = src + img * (1 - src)
    return finish(img, title)


def finish(img, title):
    img = np.clip(img, 0, 1)[::-1]
    im = Image.fromarray((img * 255 + 0.5).astype(np.uint8))
    dr = ImageDraw.Draw(im)
    dr.text((6, 4), title, fill=(255, 255, 255))
    dr.text((6, H - 14), "shader harness (real CSC GLSL on Mesa) - not Minecraft", fill=(150, 150, 150))
    return im


def truss(s, axis, lo, length, albedo=0.16, diagonals=True):
    """a square truss of the mod: 1 block wide, 4 chords (1/16 .. 2.75/16 and 13.25/16 .. 15.75/16) and zig-zag diagonals on each side"""
    lo = np.array(lo, float)
    a, b = [i for i in range(3) if i != axis]
    c0, c1 = 0.25 / 16, 2.75 / 16
    for ua in (c0, 13.25 / 16):
        for ub in (c0, 13.25 / 16):
            p0 = lo.copy(); p1 = lo.copy()
            p0[a] += ua; p1[a] += ua + (c1 - c0); p0[b] += ub; p1[b] += ub + (c1 - c0); p1[axis] += length
            s.box(p0, p1, albedo)
    if not diagonals:
        return
    t = 0.07                                              # diagonal tubes approximated by small cubes along the zig-zag
    for side in range(4):
        fixed, moving = (a, b) if side < 2 else (b, a)
        fv = 1.5 / 16 if side % 2 == 0 else 14.5 / 16
        for k in range(int(length)):
            for j in range(14):
                f = (j + 0.5) / 14
                along = k + f
                mv = 1.5 / 16 + (13 / 16) * (f if k % 2 == 0 else 1 - f)
                c = lo.copy(); c[axis] += along; c[fixed] += fv; c[moving] += mv
                s.box(c - t / 2, c + t / 2, albedo)


def truss_stage(box_outline=False):
    """the test rig: horizontal truss, vertical truss, a structure of several trusses, a tarp, a complex model; box_outline: each truss
    replaced by its outline box (what BeamShadows put in the shadow map for the structures drawn by a renderer, before rev. 16c)"""
    s = Scene()
    def T(axis, lo, length):
        if box_outline:
            hi = np.array(lo, float) + 1.0; hi[axis] = lo[axis] + length
            s.box(lo, hi, 0.16)
        else:
            truss(s, axis, lo, length)
    T(0, (-5.0, 0.5, -15.5), 10)            # horizontal truss across the stage
    T(1, (3.0, -3.0, -18.0), 6)             # vertical truss
    T(2, (-3.0, 2.0, -21.0), 10)            # structure: a truss along z resting on the horizontal one...
    T(1, (-3.0, -3.0, -21.0), 5)            # ...and a vertical leg
    s.box((-9.0, -3.0, -23.0), (-5.0, 1.5, -22.95), 0.55)          # tarp (thin sheet)
    for x0, z0 in ((0.0, -19.0), (1.0, -19.0), (0.0, -18.2), (1.0, -18.2)):   # a "model of another mod": a stool, legs, seat, bars
        s.box((x0, -3.0, z0), (x0 + 0.1, -2.0, z0 + 0.1), 0.4)
    s.box((-0.05, -2.0, -19.05), (1.15, -1.9, -18.05), 0.45)
    s.box((0.0, -2.6, -19.0), (1.1, -2.55, -18.9), 0.4)
    return s


def stage():
    s = Scene()
    s.box((-9, 0.6, -15.2), (9, 0.85, -14.95), 0.12)            # truss tube across the stage
    s.box((-9, 0.6, -16.6), (9, 0.85, -16.35), 0.12)
    for k in range(4):                                          # stairs
        s.box((3.0, -3, -19 + k * 0.8), (8.0, -3 + 0.5 * (4 - k), -18.2 + k * 0.8), 0.48)
    s.box((-6, -3, -21), (-2, -2.5, -18), 0.5)                  # a slab
    return s


def main():
    os.makedirs(OUT, exist_ok=True)
    only_truss = len(sys.argv) > 3 and sys.argv[3] == "truss"
    S = stage()
    eye, look = (0, 1.0, 2.0), (0, -1.0, -16)
    fix = (-1.5, 4.5, -15.8)
    V, W7, W19, P = "csc_vortex_x1", "csc_lumora_w7", "csc_lumora_w19", "csc_pulsestrike_m"
    shots = [
        ("01_vortex_beam", fix, (1.5, -3, -17.5), (V, 0.0, 0.5, 0, 0, 1, 1, 1, 0), (1, 1, 1), "VORTEX X1 BEAM (zoom 0, 2 deg)"),
        ("02_vortex_spot", fix, (1.5, -3, -17.5), (V, 1.0, 0.5, 0, 0, 1, 1, 1, 0), (1, 1, 1), "VORTEX X1 SPOT (zoom 1, focus sharp)"),
        ("03_vortex_spot_soft", fix, (1.5, -3, -17.5), (V, 1.0, 0.0, 0, 0, 1, 1, 1, 0), (1, 1, 1), "VORTEX X1 SPOT, focus soft"),
        ("04_vortex_gobo3", fix, (1.5, -3, -17.5), (V, 0.7, 0.5, 3, 0, 1, 1, 1, 0), (0.3, 0.6, 1.0), "VORTEX X1 GOBO 3 in the beam and on the floor"),
        ("05_vortex_gobo6", fix, (5.0, -3, -18.5), (V, 0.7, 0.5, 6, 0, 1, 1, 1, 0), (1.0, 0.45, 0.1), "VORTEX X1 GOBO 6 on stairs, truss shadow"),
        ("06_vortex_gobo6_rot", fix, (5.0, -3, -18.5), (V, 0.7, 0.5, 6, 0, 1, 1, 1, 0), (1.0, 0.45, 0.1), "same, gobo wheel turned 60 deg"),
        ("07_vortex_prism", fix, (1.5, -3, -17.5), (V, 0.3, 0.5, 2, 1, 1, 1, 1, 0), (0.9, 0.2, 1.0), "VORTEX X1 GOBO 2 + 3-FACET PRISM"),
        ("08_w7", fix, (1.5, -3, -17.5), (W7, 0.35, 0.5, 0, 0, 1, 1, 1, 0), (1.0, 0.2, 0.6), "LUMORA W7 (19 lenses) home zoom"),
        ("09_w7_narrow", fix, (1.5, -3, -17.5), (W7, 0.0, 0.5, 0, 0, 1, 1, 1, 0), (1.0, 0.2, 0.6), "LUMORA W7 narrowest zoom"),
        ("10_w19", fix, (1.5, -3, -17.5), (W19, 0.35, 0.5, 0, 0, 1, 1, 1, 0), (0.2, 1.0, 0.5), "LUMORA W19 (37 lenses) home zoom"),
        ("11_w19_narrow", fix, (1.5, -3, -17.5), (W19, 0.0, 0.5, 0, 0, 1, 1, 1, 0), (0.2, 1.0, 0.5), "LUMORA W19 narrowest zoom"),
        ("12_pulse_blinder", fix, (1.0, -1.0, -6.0), (P, 1.0, 0.0, 0, 0, 1, 1, 1, 0), (1.0, 0.85, 0.6), "PULSESTRIKE M blinder (zoom 70 deg)"),
        ("13_pulse_flash", fix, (1.0, -1.0, -6.0), (P, 1.0, 0.0, 0, 0, 1, 1, 1, 1), (1.0, 1.0, 1.0), "PULSESTRIKE M strobe flash"),
        ("14_pulse_zoom", fix, (1.5, -3, -17.5), (P, 0.0, 0.0, 0, 0, 1, 1, 1, 0), (1.0, 0.85, 0.6), "PULSESTRIKE M zoom 20 deg"),
    ]
    near = [
        ("15_w7_near", (W7, 0.0, 0.5, 0, 0, 1, 1, 1.5, 0), "LUMORA W7 near the face, narrow zoom: lens shafts"),
        ("16_w19_near", (W19, 0.0, 0.5, 0, 0, 1, 1, 1.5, 0), "LUMORA W19 near the face, narrow zoom"),
        ("17_pulse_near", (P, 0.0, 0.0, 0, 0, 1, 1, 1.5, 0), "PULSESTRIKE M near the face: the panel and its cells"),
    ]
    rot = {"06_vortex_gobo6_rot": 85.0}
    pics = []
    if only_truss:
        shots, near = [], []
    for name, f, aim, st, col, title in shots:
        im = render(S, eye, look, f, aim, st, col, rot.get(name, 25.0), title)
        im.save(os.path.join(OUT, name + ".png")); pics.append(im)
    for name, st, title in near:
        im = render(Scene(), (0.0, 0.0, 0.0), (0, 0, -6), (-2.5, 0.0, -3.0), (6.0, 0.0, -3.4), st, (0.4, 0.7, 1.0), 25.0, title)
        im.save(os.path.join(OUT, name + ".png")); pics.append(im)
    for k, hz in enumerate(() if only_truss else (0.0, 1.0, 1.5, 2.25)):
        im = render(S, eye, look, fix, (1.5, -3, -17.5), (V, 0.7, 0.5, 3, 0, 1, 1, hz, 0), (0.3, 0.6, 1.0), 25.0,
                    "HAZE %s (%.2f)" % (("none", "light", "medium", "dense")[k], hz))
        im.save(os.path.join(OUT, "%02d_haze_%d.png" % (18 + k, k))); pics.append(im)
    for k, (dbg, title) in enumerate(() if only_truss else ((0, "VORTEX spot straight down through a truss tube"), (4, "DIAGNOSTIC occlusions: green lit, red shadowed"),
                                        (5, "DIAGNOSTIC optical mask (gobo 4) on the surfaces"))):
        im = render(S, eye, look, (-1.5, 4.5, -15.6), (-1.5, -3, -15.0), (V, 1.0, 0.5, 4 if dbg == 5 else 0, 0, 1, 1, 1, 0), (1, 1, 1), 25.0, title, dbg)
        im.save(os.path.join(OUT, "%02d_occlusion_%d.png" % (22 + k, dbg))); pics.append(im)
    # rev. 16b: the situations of the pictures of 9 November
    row = [((-7.0 + 2.0 * k, 0.3, -12.0), (-7.0 + 2.0 * k, -0.5, 6.0)) for k in range(8)]
    for name, st, col, title in () if only_truss else (
            ("25_pulse_row_blinder", ("csc_pulsestrike_m", 0.6, 0.0, 0, 0, 1, 1, 1, 0), (1.0, 1.0, 1.0), "8 PULSESTRIKE facing the audience, blinder (was a white wall)"),
            ("26_pulse_row_flash", ("csc_pulsestrike_m", 0.6, 0.0, 0, 0, 1, 1, 1, 1), (1.0, 1.0, 1.0), "same, on a strobe flash"),
            ("27_pulse_row_red", ("csc_pulsestrike_m", 0.6, 0.0, 0, 0, 1, 1, 1, 0), (1.0, 0.05, 0.05), "same, red")):
        f0, a0 = row[0]
        im = render(Scene(), (0.0, 0.5, 2.0), (0, 0, -12), f0, a0, st, col, 25.0, title, 0, [(f, a, st, col) for f, a in row[1:]])
        im.save(os.path.join(OUT, name + ".png")); pics.append(im)
    for name, st, title in () if only_truss else (
            ("28_pulse_matrix_split", ("csc_pulsestrike_m", 0.0, 0.0, 0, 0, 1, 1, 1, 0, "split"), "PULSESTRIKE zones: red / blue halves (zoom 20 deg)"),
            ("29_pulse_matrix_rainbow", ("csc_pulsestrike_m", 0.3, 0.0, 0, 0, 1, 1, 1, 0, "rainbow"), "PULSESTRIKE zones: one colour per column"),
            ("30_w7_rings", ("csc_lumora_w7", 0.0, 0.5, 0, 0, 1, 1, 1, 0, "rings"), "LUMORA W7 cells: centre red, ring 1 amber, ring 2 blue"),
            ("31_w19_rings", ("csc_lumora_w19", 0.0, 0.5, 0, 0, 1, 1, 1, 0, "rings"), "LUMORA W19 cells: same rings")):
        im = render(S, eye, look, (-1.5, 3.0, -15.8), (0.5, -3, -16.5), st, (1, 1, 1), 25.0, title)
        im.save(os.path.join(OUT, name + ".png")); pics.append(im)
    for k, hz in enumerate(() if only_truss else (1.0, 2.25)):
        im = render(S, eye, look, fix, (1.5, -3, -17.5), (W19, 0.35, 0.5, 0, 0, 1, 1, hz, 0), (0.2, 1.0, 0.5), 25.0,
                    "W19 haze %s: veil over the scenery" % ("light" if k == 0 else "dense"))
        im.save(os.path.join(OUT, "%02d_veil_%d.png" % (32 + k, k))); pics.append(im)
    # rev. 16c: VORTEX gobo on trusses and complex geometry (same gobo, same fixture), no haze then light haze
    TS, TSB = truss_stage(), truss_stage(box_outline=True)
    tfix, teye, tlook = (0.5, 7.0, -11.0), (4.0, 1.5, -8.5), (-1.0, -0.5, -17.0)
    gobo = (V, 0.55, 0.6, 6, 0, 1, 1, 0.0, 0)
    targets = (("floor", (-1.0, -3.0, -13.0)), ("wall", (-2.0, 0.0, -30.0)), ("horizontal_truss", (-1.0, 1.0, -15.0)),
               ("vertical_truss", (3.5, 0.0, -17.5)), ("structure", (-2.5, 1.5, -18.0)), ("tarp", (-7.0, -0.5, -23.0)),
               ("model", (0.55, -2.2, -18.6)))
    for k, (name, aim) in enumerate(targets):
        for hz in (0.0, 1.0):
            st = gobo[:7] + (hz,) + gobo[8:]
            im = render(TS, teye, tlook, tfix, aim, st, (1.0, 0.75, 0.35), 25.0, "VORTEX gobo 6 on %s, haze %s" % (name.replace("_", " "), "none" if hz == 0 else "light"))
            im.save(os.path.join(OUT, "t%02d_%s_haze%d.png" % (k, name, int(hz)))); pics.append(im)
    st = gobo[:7] + (1.0,) + gobo[8:]
    for tag, sh in (("before_outline_box", TSB), ("after_real_tubes", None)):
        im = render(TS, teye, tlook, tfix, (-1.0, 1.0, -15.0), st, (1.0, 0.75, 0.35), 25.0,
                    "truss in the shadow map: %s" % tag.replace("_", " "), 0, (), sh)
        im.save(os.path.join(OUT, "t10_%s.png" % tag)); pics.append(im)
    for dbg, tag in ((6, "depth"), (7, "normals"), (4, "visibility")):
        im = render(TS, teye, tlook, tfix, (-2.5, 1.5, -18.0), st, (1.0, 0.75, 0.35), 25.0, "DIAGNOSTIC %s" % tag, dbg)
        im.save(os.path.join(OUT, "t11_diag_%s.png" % tag)); pics.append(im)
    # contact sheet
    cols = 3
    rows = (len(pics) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * W, rows * H), (0, 0, 0))
    for i, im in enumerate(pics):
        sheet.paste(im, ((i % cols) * W, (i // cols) * H))
    sheet.save(os.path.join(OUT, "contact_sheet.png"))
    open(os.path.join(OUT, "harness.log"), "w").write("\n".join(LOG) + "\n")
    print("%d pictures, %d shader runs -> %s" % (len(pics), len(LOG), OUT))


if __name__ == "__main__":
    main()
