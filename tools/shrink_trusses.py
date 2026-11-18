#!/usr/bin/env python3
"""Reduces the cross-section of every truss model (or, with FULL_SIZE = True, keeps them at one full block).

Since 1.2.9 FULL_SIZE is True: every truss is as wide as a base block (16 px, tubes 3 px, centres 1.5 / 14.5 px from the cell edge) and this
script just copies the originals from tools/blockbench/fullsize to tools/blockbench. Set FULL_SIZE = False to get the 10 px section of
1.2.5 - 1.2.8 back (then also set TrussGap.TUBE_GAP = 3, StageContent shapes, CurtainShapes, SHIFT in generate_assets.py and the mast of the
towers, see README 1.2.9).

Reads the full-size originals in tools/blockbench/fullsize/*.bbmodel and writes the smaller versions to
tools/blockbench/*.bbmodel (the files tools/generate_assets.py converts). Safe to run again: the originals are never touched.

Full size : tubes 3 px wide, tube centres 1.5 / 14.5 px from the cell edge (section 16 px = 1 block).
Smaller   : tubes 2 px wide, tube centres 4 / 12 px from the cell edge (section 10 px), flanges stay on the cell boundary,
            so every piece still fits every other piece. Diagonals are rebuilt at 45 degrees (22.5 on the 2-block-tall faces),
            the only angles Minecraft block models accept.
"""
import base64, copy, json, math, os, re, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'blockbench', 'fullsize')
DST = os.path.join(HERE, 'blockbench')

# ---------------------------------------------------------------- parameters
FULL_SIZE = True     # 1.2.9: trusses are one block wide (16 px). False: the 10 px section described below
SPACING = 8.0        # distance between two tube centres of a section (must be 8 so the 45 degree diagonals repeat every block)
TUBE = 2.0           # tube width (full size: 3)
OLD_SPACING = 13.0   # full size: 14.5 - 1.5
K_POS = SPACING / OLD_SPACING     # position scale inside a cell, around the cell centre
K_SIZE = TUBE / 3.0               # size scale of tubes, nodes, flanges, bolts
DIAG = 0.9           # diagonal thickness
TAN225 = math.tan(math.radians(22.5))

TILE = {'alu': [0, 0, 4, 4], 'alu_dark': [4, 0, 8, 4], 'anod': [8, 0, 12, 4], 'yellow': [12, 0, 16, 4],
        'red': [0, 4, 4, 8], 'black': [4, 4, 8, 8], 'chain': [8, 4, 12, 8], 'plate': [12, 4, 16, 8]}
FACES = ('north', 'east', 'south', 'west', 'up', 'down')

# piece -> (cross axes that get squeezed, length axis or None, length in px or None)
PIECES = {
    'truss_h1_l2': ((1, 2), 0, 32),
    'truss_h2_l2': ((1, 2), 0, 32),
    'truss_v2_h2': ((0, 2), 1, 32),
    'truss_corner_3': ((0, 1, 2), None, None),
    'truss_corner_3_h2': ((0, 1, 2), None, None),
    'truss_corner_6': ((0, 1, 2), None, None),
    'truss_corner_6_h2': ((0, 1, 2), None, None),
}

# Connectors (3-way joints only): the closed top of a 3-way joint (and the horizontal tubes of a beam) stop 3 px inside the cell, whereas the
# ends of the columns / 6-way joints reach the cell boundary. A column placed on (or under) a beam therefore floated 2.7 px
# away. Each chord of these pieces gets a short stub with the same flange as a column end, up to the cell boundary.
# piece -> (x positions of the chords, z positions, y of the top tube face or None, y of the bottom tube face or None, height)
CONNECTORS = {
    # beams (truss_h1_l2 / truss_h2_l2) deliberately have no connectors: plain tubes
    'truss_corner_3':    ([4, 12], [4, 12], 13.0, None, 16.0),
    'truss_corner_3_h2': ([4, 12], [4, 12], 29.0, None, 32.0),
}
FLANGE_H = 1.2       # height of the flange of a column end (same as the columns)

def add_connectors(els, tpl, spec):
    xs, zs, top, bottom, height = spec
    out = []
    for x in xs:
        for z in zs:
            for side, face in (('haut', top), ('bas', bottom)):
                if face is None: continue
                if side == 'haut':
                    t0, t1 = face, height - FLANGE_H + 0.05          # tube from the beam to the flange
                    f0, f1 = height - FLANGE_H, height               # flange on the cell boundary
                    p0, p1 = height - 0.12, height                   # pin
                else:
                    t0, t1 = FLANGE_H - 0.05, face
                    f0, f1 = 0.0, FLANGE_H
                    p0, p1 = 0.0, 0.12
                n = f'raccord_{side}_{x}_{z}'
                h, hf = TUBE / 2, 3.8 * K_SIZE / 2
                out.append(new_el(tpl, n + '_tube', [x - h, t0, z - h], [x + h, t1, z + h], 'alu'))
                out.append(new_el(tpl, n + '_bride_a', [x - hf, f0, z - hf], [x + hf, f1, z + hf], 'alu_dark'))
                out.append(new_el(tpl, n + '_bride_b', [x - hf, f0, z - hf], [x + hf, f1, z + hf], 'alu_dark',
                                  [0, 45, 0], [x, (f0 + f1) / 2, z]))
                out.append(new_el(tpl, n + '_goupille', [x - 0.4, p0, z - 0.4], [x + 0.4, p1, z + 0.4], 'black'))
    return els + out

def cell_of(v):
    return max(0, int(math.floor((v - 1e-6) / 16.0)))

def M(v):
    """Position of a point inside its cell: squeezed towards the cell centre."""
    c = cell_of(v) * 16.0
    return c + 8.0 + (v - c - 8.0) * K_POS

def new_el(template, name, frm, to, tile, rot=None, origin=None):
    e = copy.deepcopy(template)
    e['name'] = name
    e['uuid'] = str(uuid.uuid4())
    e['from'] = [round(v, 4) for v in frm]
    e['to'] = [round(v, 4) for v in to]
    e['origin'] = [round(v, 4) for v in (origin or [(a + b) / 2 for a, b in zip(frm, to)])]
    e['faces'] = {f: {'uv': list(TILE[tile]), 'texture': 0} for f in FACES}
    e.pop('rotation', None)
    if rot: e['rotation'] = rot
    return e

def classify(name):
    n = re.sub(r'^haut_', '', name)
    if re.match(r'^(arete_[xyz]_|membrure)', n): return 'bar'
    if n.startswith('noeud'): return 'node'
    if re.match(r'^(bride|trou|boulon|goupille)', n): return 'flange'
    if n.startswith('plaque'): return 'plate'
    if n.startswith('diag'): return 'diag'
    if n.startswith('gousset'): return 'gousset'
    return 'other'

def bar_axis(e):
    f, t = e['from'], e['to']
    ext = [t[i] - f[i] for i in range(3)]
    return max(range(3), key=lambda i: ext[i])

def flange_normal(e):
    ext = [e['to'][i] - e['from'][i] for i in range(3)]
    return min(range(3), key=lambda i: ext[i])

# ---------------------------------------------------------------- shrinking of the existing elements
def shrink_elements(elements, cross):
    flanges = []
    for e in elements:
        if classify(e['name']) == 'flange' and re.match(r'^(haut_)?bride', e['name']) and not e['name'].endswith('_b'):
            n = flange_normal(e)
            flanges.append((n, [(e['from'][i] + e['to'][i]) / 2 for i in range(3)], e['from'][n], e['to'][n]))

    def end_is_open(e, b, high):
        """Does a flange sit on this end of the bar (same tube line)? Then the bar must keep reaching the cell boundary."""
        c = [(e['from'][i] + e['to'][i]) / 2 for i in range(3)]
        end = e['to'][b] if high else e['from'][b]
        for n, fc, f0, f1 in flanges:
            if n != b: continue
            if any(abs(fc[i] - c[i]) > 0.05 for i in range(3) if i != b): continue
            if abs((f1 if high else f0) - end) < 1.0: return True
        return False

    out = []
    for e in elements:
        cat = classify(e['name'])
        if cat in ('diag', 'gousset'): continue
        e = copy.deepcopy(e)
        f, t = list(e['from']), list(e['to'])
        c = [(f[i] + t[i]) / 2 for i in range(3)]
        s = [t[i] - f[i] for i in range(3)]
        nf, nt = list(f), list(t)
        if cat == 'bar':
            b = bar_axis(e)
            for a in range(3):
                if a == b:
                    if a in cross:
                        lo = f[a] if end_is_open(e, a, False) else M(f[a])
                        hi = t[a] if end_is_open(e, a, True) else M(t[a])
                        nf[a], nt[a] = lo, hi
                else:
                    cc = M(c[a]) if a in cross else c[a]
                    nf[a], nt[a] = cc - s[a] * K_SIZE / 2, cc + s[a] * K_SIZE / 2
        elif cat in ('node',):
            for a in range(3):
                cc = M(c[a]) if a in cross else c[a]
                nf[a], nt[a] = cc - s[a] * K_SIZE / 2, cc + s[a] * K_SIZE / 2
        elif cat == 'flange':
            n = flange_normal(e)
            for a in range(3):
                if a == n: continue
                cc = M(c[a]) if a in cross else c[a]
                nf[a], nt[a] = cc - s[a] * K_SIZE / 2, cc + s[a] * K_SIZE / 2
        elif cat == 'plate':
            for a in range(3):
                if a in cross:
                    nf[a], nt[a] = M(f[a]), M(t[a])
                    if nt[a] - nf[a] < 0.6:
                        mid = (nf[a] + nt[a]) / 2; nf[a], nt[a] = mid - 0.3, mid + 0.3
        e['from'] = [round(v, 4) for v in nf]
        e['to'] = [round(v, 4) for v in nt]
        e['origin'] = [round((nf[i] + nt[i]) / 2, 4) for i in range(3)]
        out.append(e)
    return out

# ---------------------------------------------------------------- diagonals
def chord_lines(cross, elements):
    """Tube-centre coordinates along each cross axis after shrinking: list of the distinct values."""
    vals = {a: set() for a in cross}
    for e in elements:
        if classify(e['name']) != 'bar': continue
        b = bar_axis(e)
        for a in cross:
            if a == b: continue
            vals[a].add(round((e['from'][a] + e['to'][a]) / 2, 2))
    return {a: sorted(v) for a, v in vals.items()}

def diag_between(template, name, Lax, Cax, Nax, p0, p1, nval):
    """Diagonal tube from p0=(L, C) to p1=(L, C) in the plane N = nval."""
    dL, dC = p1[0] - p0[0], p1[1] - p0[1]
    length = math.hypot(dL, dC)
    e = [[0, 0, 0] for _ in range(1)][0]
    centre = [0.0, 0.0, 0.0]
    centre[Lax] = (p0[0] + p1[0]) / 2; centre[Cax] = (p0[1] + p1[1]) / 2; centre[Nax] = nval
    half = [0.0, 0.0, 0.0]
    half[Lax] = DIAG / 2; half[Cax] = length / 2; half[Nax] = DIAG / 2
    eL = [0, 0, 0]; eL[Lax] = 1
    eC = [0, 0, 0]; eC[Cax] = 1
    eN = [0, 0, 0]; eN[Nax] = 1
    cross = [eN[1] * eC[2] - eN[2] * eC[1], eN[2] * eC[0] - eN[0] * eC[2], eN[0] * eC[1] - eN[1] * eC[0]]
    sgn = sum(cross[i] * eL[i] for i in range(3))
    ang = math.degrees(math.atan(sgn * dL / dC))
    ang = round(ang * 2) / 2.0
    assert ang in (-45.0, -22.5, 22.5, 45.0), (name, ang)
    rot = [0, 0, 0]; rot[Nax] = ang
    return new_el(template, name, [centre[i] - half[i] for i in range(3)], [centre[i] + half[i] for i in range(3)],
                  'alu', rot, centre)

def build_diagonals(template, cross, Lax, Ltot, lines):
    """Warren zigzag on the 4 faces of a rectangular section. Square section (spacing 8): 45 degrees, nodes every 8 px.
    Section with a 24 px side: 22.5 degrees."""
    out, nodes = [], set()
    a1, a2 = cross
    for Nax, Cax in ((a2, a1), (a1, a2)):
        for ni, nval in enumerate(lines[Nax]):
            clo, chi = lines[Cax]
            width = chi - clo
            if abs(width - SPACING) < 0.01:
                # 45 degrees, nodes every 8 px. Chord parity = (index along N + index along C); even chords carry the
                # nodes at even multiples of 8, odd chords the others, so every face zigzags between its two tubes.
                for k in range(int(round(Ltot / SPACING))):
                    L0, L1 = k * SPACING, (k + 1) * SPACING
                    ci0 = (k % 2 - ni) % 2
                    c0, c1 = (clo, chi) if ci0 == 0 else (chi, clo)
                    out.append(diag_between(template, f'diag_{"xyz"[Nax]}{ni}_{k}', Lax, Cax, Nax, (L0, c0), (L1, c1), nval))
                    nodes.add((Nax, ni, Cax, c0, L0)); nodes.add((Nax, ni, Cax, c1, L1))
            else:
                run = width * TAN225
                mid = Ltot / 2.0
                top_c, bot_c = (chi, clo) if ni % 2 == 0 else (clo, chi)
                out.append(diag_between(template, f'diag_{"xyz"[Nax]}{ni}_a', Lax, Cax, Nax, (mid - run, top_c), (mid, bot_c), nval))
                out.append(diag_between(template, f'diag_{"xyz"[Nax]}{ni}_b', Lax, Cax, Nax, (mid, bot_c), (mid + run, top_c), nval))
                nodes.add((Nax, ni, Cax, top_c, mid - run)); nodes.add((Nax, ni, Cax, bot_c, mid))
                nodes.add((Nax, ni, Cax, top_c, mid + run))
    # small plates where a diagonal meets a tube (not on the end planes)
    blobs, seen = [], set()
    for Nax, ni, Cax, cval, L in sorted(nodes):
        if L <= 0.01 or L >= Ltot - 0.01: continue
        pos = [0.0, 0.0, 0.0]; pos[Lax] = L; pos[Cax] = cval; pos[Nax] = lines[Nax][ni]
        key = tuple(round(v, 2) for v in pos)
        if key in seen: continue
        seen.add(key)
        size = [2.3, 2.3, 2.3]; size[Lax] = 1.6
        blobs.append(new_el(template, f'gousset_{len(blobs)}', [pos[i] - size[i] / 2 for i in range(3)],
                            [pos[i] + size[i] / 2 for i in range(3)], 'alu_dark'))
    return out + blobs

# ---------------------------------------------------------------- base : rebuilt around the smaller tubes
def build_base(src):
    tpl = src['elements'][0]
    keep = {e['name']: e for e in src['elements']
            if e['name'] in ('platine', 'platine_sur_epaisseur', 'plaque_signaletique', 'plaque_signaletique_ar')}
    els = [copy.deepcopy(keep[n]) for n in ('platine', 'platine_sur_epaisseur', 'plaque_signaletique', 'plaque_signaletique_ar')]
    lo, hi = 8 - SPACING / 2, 8 + SPACING / 2
    top = 1.5

    def octa(name, cx, cz, y0, y1, size, tile):
        h = size / 2
        a = new_el(tpl, name + '_a', [cx - h, y0, cz - h], [cx + h, y1, cz + h], tile)
        b = new_el(tpl, name + '_b', [cx - h, y0, cz - h], [cx + h, y1, cz + h], tile, [0, 45, 0], [cx, (y0 + y1) / 2, cz])
        return [a, b]
    chords = [(lo, lo), (hi, lo), (lo, hi), (hi, hi)]
    for i, (cx, cz) in enumerate(chords):
        els += octa(f'collerette{i}', cx, cz, top, top + 0.8, 3.6, 'alu_dark')
        els += octa(f'collerette_haut{i}', cx, cz, top + 0.8, top + 1.6, 2.8, 'alu')
        sx = 1 if cx < 8 else -1; sz = 1 if cz < 8 else -1
        for k, h in enumerate((3.0, 2.0, 1.0)):
            a, b = cx + sx * (1.0 + 1.0 * k), cx + sx * (1.0 + 1.0 * (k + 1))
            els.append(new_el(tpl, f'nervure_x{i}_{k}', [min(a, b), top, cz - 0.4], [max(a, b), top + h, cz + 0.4], 'alu_dark'))
            a, b = cz + sz * (1.0 + 1.0 * k), cz + sz * (1.0 + 1.0 * (k + 1))
            els.append(new_el(tpl, f'nervure_z{i}_{k}', [cx - 0.4, top, min(a, b)], [cx + 0.4, top + h, max(a, b)], 'alu_dark'))
        els += octa(f'membrure{i}', cx, cz, 0.05, 15.95, TUBE, 'alu')
        els += octa(f'bride_haut{i}', cx, cz, 14.8, 16.0, 3.8 * K_SIZE, 'alu_dark')
        els.append(new_el(tpl, f'goupille_haut{i}', [cx - 0.6, 15.88, cz - 0.6], [cx + 0.6, 16.0, cz + 0.6], 'black'))
    for i, (bx, bz) in enumerate([(8, 1.5), (8, 14.5), (1.5, 8), (14.5, 8)]):
        els += octa(f'rondelle{i}', bx, bz, top, top + 0.25, 2.6, 'black')
        els += octa(f'ecrou{i}', bx, bz, top + 0.25, top + 1.4, 1.9, 'alu')
        els.append(new_el(tpl, f'tige{i}', [bx - 0.4, top + 1.4, bz - 0.4], [bx + 0.4, top + 2.4, bz + 0.4], 'chain'))
    lines = {0: [lo, hi], 2: [lo, hi]}
    els += build_diagonals(tpl, (0, 2), 1, 16, lines)
    return els

# ---------------------------------------------------------------- main
def write(name, src, elements):
    d = copy.deepcopy(src)
    d['elements'] = elements
    d['outliner'] = [e['uuid'] for e in elements]
    with open(os.path.join(DST, name + '.bbmodel'), 'w', encoding='utf-8') as f:
        json.dump(d, f)
    return d

def copy_full_size():
    names = list(PIECES) + ['truss_base_v2']
    for name in names:
        src = json.load(open(os.path.join(SRC, name + '.bbmodel'), encoding='utf-8'))
        with open(os.path.join(DST, name + '.bbmodel'), 'w', encoding='utf-8') as f:
            json.dump(src, f)
        print(f'{name:22s} {len(src["elements"]):4d} elements (full size, copied)')

def main():
    if FULL_SIZE:
        copy_full_size()
        return
    for name, (cross, Lax, Ltot) in PIECES.items():
        src = json.load(open(os.path.join(SRC, name + '.bbmodel'), encoding='utf-8'))
        els = shrink_elements(src['elements'], cross)
        if Lax is not None:
            tpl = src['elements'][0]
            cx = tuple(a for a in cross)
            lines = chord_lines(cross, els)
            els += build_diagonals(tpl, cx, Lax, Ltot, lines)
        if name in CONNECTORS:
            els = add_connectors(els, src['elements'][0], CONNECTORS[name])
        write(name, src, els)
        print(f'{name:22s} {len(src["elements"]):4d} -> {len(els):4d} elements')
    src = json.load(open(os.path.join(SRC, 'truss_base_v2.bbmodel'), encoding='utf-8'))
    els = build_base(src)
    write('truss_base_v2', src, els)
    print(f'{"truss_base_v2":22s} {len(src["elements"]):4d} -> {len(els):4d} elements')

if __name__ == '__main__':
    main()
