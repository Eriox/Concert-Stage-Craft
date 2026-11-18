#!/usr/bin/env python3
"""CSC LUMICORE X1 lighting desk: an original console, built from boxes and prisms (no external reference geometry).

Writes (assets/concertstage):
- models/lightshow/csc_lumicore_x1.obj : the desk, palette texture (lightshow_palette.png, colours appended to palette.json)
- lightshow/lumicore_layout.json        : where the screens, faders and keys are, for the block entity renderer (live screens, LEDs)

Model frame: the operator stands on +Z (south); one block cell is x 0..1, z 0..1; the desk spans x -0.5..1.5 (2 m) and
z 0.02..0.98, height 1.42 m at the screens. Blockstate: facing=south y 0, west 90, north 180, east 270 (FACING = towards the operator).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from export_fixtures import CELLS, palette_uv, write_palette  # noqa: E402

COL = {
    'graphite': '1e2126', 'body': '15171b', 'panel': '23272d', 'edge': '6c737c', 'key': '3a3f46', 'keytop': '4a5058',
    'cap': 'b8bdc4', 'slot': '08090b', 'green': '3ccf6a', 'amber': 'f2b33d', 'red': 'e5484d', 'blue': '35c9e8', 'violet': 'a77bff',
    'glass': '0a0d11', 'rubber': '0c0d0f', 'white': 'e8eaed',
}


class Mesh:
    def __init__(self):
        self.tris = []           # (p0, p1, p2, colour)

    def quad(self, a, b, c, d, col):
        self.tris.append((a, b, c, col))
        self.tris.append((a, c, d, col))

    @staticmethod
    def mirrored(xf):
        """True when the transform flips handedness (the faces must then be wound the other way)."""
        if xf is None:
            return False
        o = xf((0.0, 0.0, 0.0))
        e = [[a - b for a, b in zip(xf(v), o)] for v in ((1.0, 0, 0), (0, 1.0, 0), (0, 0, 1.0))]
        det = (e[0][0] * (e[1][1] * e[2][2] - e[1][2] * e[2][1]) - e[0][1] * (e[1][0] * e[2][2] - e[1][2] * e[2][0])
               + e[0][2] * (e[1][0] * e[2][1] - e[1][1] * e[2][0]))
        return det < 0

    def box(self, lo, hi, col, xf=None):
        x0, y0, z0 = lo
        x1, y1, z1 = hi
        p = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
        flip = Mesh.mirrored(xf)
        if xf:
            p = [xf(q) for q in p]
        f = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (3, 7, 6, 2), (0, 4, 7, 3), (1, 2, 6, 5)]
        for a, b, c, d in f:
            if flip:
                self.quad(p[d], p[c], p[b], p[a], col)
            else:
                self.quad(p[a], p[b], p[c], p[d], col)

    def prism(self, centre, radius, y0, y1, sides, col, xf=None):
        cx, cz = centre
        ring0, ring1 = [], []
        for i in range(sides):
            a = 2 * math.pi * (i + 0.5) / sides
            ring0.append((cx + radius * math.cos(a), y0, cz + radius * math.sin(a)))
            ring1.append((cx + radius * math.cos(a), y1, cz + radius * math.sin(a)))
        flip = Mesh.mirrored(xf)
        if xf:
            ring0 = [xf(q) for q in ring0]
            ring1 = [xf(q) for q in ring1]
        top = xf((cx, y1, cz)) if xf else (cx, y1, cz)
        for i in range(sides):
            j = (i + 1) % sides
            if flip:
                self.quad(ring0[j], ring1[j], ring1[i], ring0[i], col)
                self.tris.append((top, ring1[i], ring1[j], col))
            else:
                self.quad(ring0[i], ring1[i], ring1[j], ring0[j], col)
                self.tris.append((top, ring1[j], ring1[i], col))


def rot_x(p, origin, deg):
    a = math.radians(deg)
    y, z = p[1] - origin[1], p[2] - origin[2]
    return (p[0], origin[1] + y * math.cos(a) - z * math.sin(a), origin[2] + y * math.sin(a) + z * math.cos(a))


def build():
    m = Mesh()
    layout = {'screens': [], 'faders': [], 'executor_keys': [], 'encoders': [], 'keys': {}, 'leds': []}
    # ---- frame and legs
    for x in (-0.44, 1.38):
        for z in (0.12, 0.82):
            m.box((x, 0.0, z), (x + 0.06, 0.74, z + 0.06), COL['graphite'])
        m.box((x, 0.08, 0.12), (x + 0.06, 0.12, 0.88), COL['graphite'])           # side stretcher
        m.box((x - 0.01, 0.0, 0.08), (x + 0.07, 0.025, 0.92), COL['rubber'])       # foot runner
    m.box((-0.44, 0.08, 0.15), (1.44, 0.12, 0.19), COL['graphite'])                # rear stretcher
    # ---- body under the desk
    m.box((-0.48, 0.70, 0.04), (1.48, 0.84, 0.96), COL['body'])
    m.box((-0.49, 0.835, 0.03), (1.49, 0.85, 0.97), COL['edge'])                     # aluminium rim
    # ---- sloped work surface: rises from the front (z 0.96, y 0.85) towards the screen bridge (z 0.40)
    slope = 12.0
    origin = (0.0, 0.85, 0.96)

    def desk(p):          # desk frame: x, height above the surface, distance from the front edge (towards -Z)
        x, h, d = p
        return rot_x((x, 0.85 + h, 0.96 - d), origin, slope)

    m.box((-0.47, -0.04, 0.0), (1.47, 0.0, 0.56), COL['panel'], xf=lambda q: desk(q))
    # armrest
    m.box((-0.47, 0.0, 0.0), (1.47, 0.028, 0.07), COL['rubber'], xf=lambda q: desk(q))
    # ---- screen bridge at the back
    m.box((-0.48, 0.85, 0.04), (1.48, 1.02, 0.42), COL['body'])
    m.box((-0.48, 1.02, 0.04), (1.48, 1.035, 0.42), COL['edge'])
    # status LED strip on the bridge front
    m.box((-0.40, 0.95, 0.4205), (1.40, 0.958, 0.4215), COL['blue'])
    layout['leds'].append({'from': [-0.40, 0.95, 0.4216], 'to': [1.40, 0.958, 0.4216]})
    # ---- three screens, tilted back 18 degrees
    tilt = 18.0
    for i, cx in enumerate((-0.12, 0.50, 1.12)):
        w, h = 0.56, 0.36
        base = (0.0, 1.035, 0.30)

        def scr(q, cx=cx):
            return rot_x((q[0], q[1], q[2]), (cx, 1.035, 0.30), -tilt)
        m.box((cx - w / 2 - 0.02, 1.035, 0.26), (cx + w / 2 + 0.02, 1.035 + h + 0.04, 0.30), COL['graphite'], xf=scr)
        m.box((cx - w / 2, 1.035 + 0.02, 0.2999), (cx + w / 2, 1.035 + 0.02 + h, 0.3005), COL['glass'], xf=scr)
        # stand behind
        m.box((cx - 0.06, 1.0, 0.12), (cx + 0.06, 1.18, 0.24), COL['graphite'])
        c = scr((cx, 1.035 + 0.02 + h / 2, 0.3012))
        nrm = [0.0, math.sin(math.radians(tilt)), math.cos(math.radians(tilt))]
        up = [0.0, math.cos(math.radians(tilt)), -math.sin(math.radians(tilt))]
        layout['screens'].append({'centre': [round(v, 4) for v in c], 'normal': nrm, 'up': up, 'width': w, 'height': h})
    # ---- playback section: 10 executor faders with 3 keys each (left / centre)
    for i in range(10):
        x = -0.40 + i * 0.075
        # fader slot and cap
        m.box((x - 0.006, 0.0, 0.10), (x + 0.006, 0.004, 0.26), COL['slot'], xf=lambda q: desk(q))
        cap_d = 0.14
        m.box((x - 0.018, 0.004, cap_d - 0.011), (x + 0.018, 0.026, cap_d + 0.011), COL['cap'], xf=lambda q: desk(q))
        layout['faders'].append({'x': x, 'd0': 0.10, 'd1': 0.26})
        for k, col in enumerate(('key', 'key', 'green')):
            d = 0.29 + k * 0.045
            m.box((x - 0.022, 0.0, d), (x + 0.022, 0.014, d + 0.034), COL[col], xf=lambda q: desk(q))
            m.box((x - 0.018, 0.014, d + 0.004), (x + 0.018, 0.016, d + 0.008), COL['white' if k < 2 else 'green'], xf=lambda q: desk(q))
        layout['executor_keys'].append({'x': x, 'd': 0.29})
    # ---- encoders under the centre screen
    for i in range(5):
        x = 0.36 + i * 0.07
        m.prism((x, 0.48), 0.022, 0.0, 0.03, 10, COL['rubber'], xf=lambda q: desk((q[0], q[1], q[2])))
        m.prism((x, 0.48), 0.012, 0.03, 0.034, 8, COL['edge'], xf=lambda q: desk((q[0], q[1], q[2])))
        layout['encoders'].append({'x': x, 'd': 0.48})
    # ---- programming keypad (right)
    labels = ['STORE', 'UPDATE', 'CLEAR', 'GROUP', 'PRESET', 'CUE', 'EFFECT', 'AUTO', 'HIGH', 'SOLO', 'TIME', 'DEL',
              '7', '8', '9', 'THRU', '4', '5', '6', '+', '1', '2', '3', '-', '0', '.', 'AT', 'ENTER']
    for r in range(7):
        for c in range(4):
            x = 0.42 + c * 0.052
            d = 0.10 + r * 0.044
            col = 'red' if labels[r * 4 + c] in ('CLEAR', 'DEL') else 'violet' if labels[r * 4 + c] == 'AUTO' else 'key'
            m.box((x - 0.021, 0.0, d), (x + 0.021, 0.012, d + 0.036), COL[col], xf=lambda q: desk(q))
    # ---- master playback: GO (large green), PAUSE, BACK; grand master fader and BLACKOUT on the right
    m.box((0.70, 0.0, 0.12), (0.84, 0.022, 0.24), COL['green'], xf=lambda q: desk(q))
    m.box((0.86, 0.0, 0.12), (0.94, 0.016, 0.18), COL['amber'], xf=lambda q: desk(q))
    m.box((0.86, 0.0, 0.19), (0.94, 0.016, 0.24), COL['key'], xf=lambda q: desk(q))
    layout['keys']['go'] = [0.77, 0.18]
    m.box((1.04, 0.0, 0.10), (1.052, 0.004, 0.30), COL['slot'], xf=lambda q: desk(q))
    m.box((1.028, 0.004, 0.19), (1.064, 0.03, 0.214), COL['red'], xf=lambda q: desk(q))
    layout['keys']['grand_master'] = [1.046, 0.10, 0.30]
    m.box((1.12, 0.0, 0.12), (1.30, 0.024, 0.22), COL['red'], xf=lambda q: desk(q))
    m.box((1.15, 0.024, 0.15), (1.27, 0.026, 0.19), COL['white'], xf=lambda q: desk(q))
    layout['keys']['blackout'] = [1.21, 0.17]
    # trackball / touch zone
    m.prism((1.30, 0.40), 0.035, 0.0, 0.012, 12, COL['edge'], xf=lambda q: desk((q[0], q[1], q[2])))
    m.prism((1.30, 0.40), 0.026, 0.012, 0.03, 12, COL['glass'], xf=lambda q: desk((q[0], q[1], q[2])))
    # side handles
    for x in (-0.50, 1.47):
        m.box((x, 0.88, 0.30), (x + 0.03, 0.92, 0.80), COL['edge'])
    layout['desk'] = {'origin': list(origin), 'slope_deg': slope}
    return m, layout


def main():
    dst = sys.argv[1]
    pal_path = os.path.join(dst, 'lightshow', 'palette.json')
    pal = json.load(open(pal_path))
    colors = pal['colors']
    for c in COL.values():
        if c not in colors:
            colors.append(c)
    assert len(colors) <= CELLS * CELLS
    m, layout = build()
    lines = ['mtllib lightshow.mtl', 'usemtl palette']
    vi = 0
    for a, b, c, col in m.tris:
        u, v = palette_uv(colors.index(col))
        ax, ay, az = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        bx, by, bz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
        nx, ny, nz = ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx
        nl = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        for p in (a, b, c):
            lines.append('v %.4f %.4f %.4f' % p)
            lines.append('vt %.5f %.5f' % (u, v))
            lines.append('vn %.4f %.4f %.4f' % (nx / nl, ny / nl, nz / nl))
        lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d' % (vi + 1, vi + 1, vi + 1, vi + 2, vi + 2, vi + 2, vi + 3, vi + 3, vi + 3))
        vi += 3
    with open(os.path.join(dst, 'models', 'lightshow', 'csc_lumicore_x1.obj'), 'w') as f:
        f.write('\n'.join(lines) + '\n')
    with open(os.path.join(dst, 'lightshow', 'lumicore_layout.json'), 'w') as f:
        json.dump(layout, f, indent=1)
    pal['colors'] = colors
    json.dump(pal, open(pal_path, 'w'))
    write_palette(colors, os.path.join(dst, 'textures', 'block', 'lightshow_palette.png'))
    print('lumicore triangles', len(m.tris), 'palette', len(colors))


if __name__ == '__main__':
    main()
