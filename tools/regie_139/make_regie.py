#!/usr/bin/env python3
"""Blockbench models for a FOH / "regie" scaffold kit (posts, nodes, ledgers, braced panels, lattice girders, flight case).
Grid: 1 cell = 16 px. Tubes are 1.2 px (= 48 mm tubes like tools/blockbench/scaffold_*.bbmodel); tube lines run through the middle of a cell.
Every element has at most ONE rotation axis (the mod's converter requires it). Texture: regie.png = scaffold.png + a red tile at (48,16).
"""
import json, math, os, uuid, base64, io, copy
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'blockbench')
BB_OUT = os.path.join(HERE, 'blockbench')
SCAF_TEX = os.path.join(HERE, '..', 'common', 'src', 'main', 'resources', 'assets', 'concertstage', 'textures', 'block', 'scaffold.png')
REF = os.path.join(HERE, 'blockbench', 'scaffold_stage_3x3x2_sud.bbmodel')

STEEL, GREY, LIGHT, DARK, YELLOW, BLACK, WOOD, RED = (0, 0), (1, 0), (2, 0), (3, 0), (0, 1), (1, 1), (2, 1), (3, 1)
TILE_RGB = {STEEL: '#c9ccd0', GREY: '#8d9096', LIGHT: '#dcdee0', DARK: '#555a60', YELLOW: '#f0be1e', BLACK: '#1e1e22', WOOD: '#a07848', RED: '#c8201e'}
T = 1.2          # tube diameter
C = 8.0          # middle of a cell


class Box:
    def __init__(self, name, f, t, tile, rot=None):
        self.name, self.f, self.t, self.tile, self.rot = name, list(map(float, f)), list(map(float, t)), tile, rot   # rot = (axis 0/1/2, angle, origin)

    def center(self):
        return [(a + b) / 2 for a, b in zip(self.f, self.t)]


def tube(name, axis, c1, c2, lo, hi, tile=STEEL, d=T):
    """Round-ish tube (square + the same square turned 45 deg about its own axis). axis 0/1/2 = x/y/z; c1, c2 = the two other coordinates."""
    others = [i for i in range(3) if i != axis]
    res = []
    for k, rot in (('a', None), ('b', 45.0)):
        f = [0, 0, 0]; t = [0, 0, 0]
        f[axis], t[axis] = lo, hi
        for i, c in zip(others, (c1, c2)):
            f[i], t[i] = c - d / 2, c + d / 2
        ctr = [(a + b) / 2 for a, b in zip(f, t)]
        res.append(Box(f'{name}_{k}', f, t, tile, (axis, rot, ctr) if rot else None))
    return res


def rosette(name, cx, cz, y, tile=RED, w=2.8, h=0.6):
    out = []
    for k, rot in (('a', None), ('b', 45.0)):
        f = [cx - w / 2, y - h / 2, cz - w / 2]; t = [cx + w / 2, y + h / 2, cz + w / 2]
        out.append(Box(f'{name}_{k}', f, t, tile, (1, rot, [cx, y, cz]) if rot else None))
    return out


def post(name, cx, cz, y0, y1, rosettes=(8,), tile=STEEL):
    els = tube(name, 1, cx, cz, y0, y1, tile)
    for y in rosettes:
        els += rosette(f'{name}_rosace{int(y)}', cx, cz, y)
    return els


def stub(name, axis, c_other, y, lo, hi, head=None):
    """Horizontal ledger piece along x (axis 0) or z (axis 2) at height y, c_other = the other horizontal coordinate."""
    if axis == 0:
        els = tube(name, 0, y, c_other, lo, hi)
    else:
        els = tube(name, 2, c_other, y, lo, hi)
    return els


def head(name, axis, c_other, y, at, sign, tile=GREY):
    """Wedge head (1.1 long, 1.6 square) at position `at` along the axis, pointing inward by sign."""
    lo, hi = (at, at + 1.1 * sign) if sign > 0 else (at + 1.1 * sign, at)
    f = [0, 0, 0]; t = [0, 0, 0]
    f[axis], t[axis] = lo, hi
    f[1], t[1] = y - 0.8, y + 0.8
    other = 2 if axis == 0 else 0
    f[other], t[other] = c_other - 0.8, c_other + 0.8
    return [Box(name, f, t, tile)]


# ------------------------------------------------------------------ pieces (cell = 0..16 on every axis)
def poteau_h1():
    return {'tube': post('poteau', C, C, 0, 16, rosettes=(8,))}


def poteau_pied():
    base = [Box('semelle', [5.5, 0, 5.5], [10.5, 0.5, 10.5], GREY),
            *tube('verin', 1, C, C, 0.5, 4.0, STEEL, 0.9),
            Box('ecrou_a', [7.0, 2.0, 7.0], [9.0, 3.2, 9.0], DARK),
            Box('ecrou_b', [7.0, 2.0, 7.0], [9.0, 3.2, 9.0], DARK, (1, 45.0, [8, 2.6, 8]))]
    return {'pied': base, 'tube': post('poteau', C, C, 3.6, 16, rosettes=(8,))}


def lisse_l1(axis=0):
    g = {'tube': stub('lisse', axis, C, 8, 0, 16)}
    g['tetes'] = head('tete_0', axis, C, 8, 0, +1) + head('tete_1', axis, C, 8, 16, -1)
    return g


def barriere_l1():
    """Guard rail bay for the FOH posts: one cell between two posts that are 2 cells apart (post centres at x = -8 and x = 24 of this cell).
    Three rails (top 14.8, middle 8 = rosette height, low 1.4) run from post surface to post surface, three balusters and a toe board.
    Works with regie_poteau_h1, regie_poteau_pied_h1 (jack) and the nodes."""
    lo, hi = -8 + 1.6, 24 - 1.6
    g = {'lisses': [], 'balustres': [], 'plinthe': [], 'colliers': []}
    for name, y in (('haute', 14.8), ('milieu', 8.0), ('basse', 1.4)):
        g['lisses'] += tube(f'lisse_{name}', 0, y, C, lo, hi)
    for i, x in enumerate((4.0, 8.0, 12.0)):
        g['balustres'] += tube(f'balustre{i}', 1, x, C, 1.4, 14.8, STEEL, 0.9)
    g['plinthe'] += [Box('plinthe', [lo, 0.0, C - 0.5], [hi, 3.0, C + 0.5], DARK)]
    for y in (14.8, 8.0):
        g['colliers'] += [Box(f'collier_g{int(y)}', [lo - 0.2, y - 0.9, C - 1.1], [lo + 1.0, y + 0.9, C + 1.1], DARK),
                          Box(f'collier_d{int(y)}', [hi - 1.0, y - 0.9, C - 1.1], [hi + 0.2, y + 0.9, C + 1.1], DARK)]
    return g


def noeud(dirs):
    """Post + horizontal stubs from the centre to the cell edge in the given directions ('+x','-x','+z','-z'), rosette level y = 8."""
    g = {'tube': post('poteau', C, C, 0, 16, rosettes=(8,))}
    st, hd = [], []
    for d in dirs:
        axis = 0 if d[1] == 'x' else 2
        if d[0] == '+':
            st += stub(f'lisse{d}', axis, C, 8, C, 16); hd += head(f'tete{d}', axis, C, 8, C + 1.6, +1)
        else:
            st += stub(f'lisse{d}', axis, C, 8, 0, C); hd += head(f'tete{d}', axis, C, 8, C - 1.6, -1)
    g['lisses'] = st
    g['tetes'] = hd
    return g


def brace(name, x0, y0, x1, y1, z, tile=STEEL, d=T):
    """Diagonal tube in the XY plane from (x0,y0) to (x1,y1) at depth z (one box turned about z)."""
    L = math.hypot(x1 - x0, y1 - y0)
    ang = math.degrees(math.atan2(y1 - y0, x1 - x0))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return [Box(name, [cx - L / 2, cy - d / 2, z - d / 2], [cx + L / 2, cy + d / 2, z + d / 2], tile, (2, round(ang, 4), [cx, cy, z]))]


def panneau(cross=False, L=2):
    """Braced bay: two posts (cell centres x = 8 and 8+16(L-1)), a ledger at y=8, and one (or two) diagonal brace(s). Footprint L x 1 x 1 cells."""
    xs = [C + 16 * i for i in range(L)]
    g = {'poteaux': [], 'lisses': [], 'tetes': [], 'diagonales': []}
    for i, x in enumerate(xs):
        g['poteaux'] += post(f'poteau{i}', x, C, 0, 16, rosettes=(8,))
    g['lisses'] += tube('lisse_haute', 0, 8, C, xs[0], xs[-1])
    g['lisses'] += tube('lisse_basse', 0, 0.9, C, xs[0], xs[-1])
    g['lisses'] += tube('lisse_haut', 0, 15.1, C, xs[0], xs[-1])
    for i in range(L - 1):
        # exactly 45 degrees (Minecraft only accepts element rotations of 0, 22.5 and 45): corner to corner of the ledger lines
        g['diagonales'] += brace(f'diag{i}', xs[i] + 1.0, 1.1, xs[i] + 15.0, 15.1, C - 0.0)
        if cross:
            g['diagonales'] += brace(f'diag{i}x', xs[i] + 1.0, 15.1, xs[i] + 15.0, 1.1, C + 1.4)
    return g


def _girder(length, cells, name='poutre', slope=None):
    """Lattice girder along x, section 6 wide (z 5..11) x 8 high (y 4..12), 4 chord tubes and zig-zag diagonals in both side planes.
    slope = (rise_px, run_px) : the whole girder is turned about z around its start point (0, 8)."""
    ch = {}
    y0, y1, z0, z1 = 4.0, 12.0, 4.0, 12.0      # tube lines of the 1x2 truss: curtains, tarps and rails sit on it like on a truss
    sq = slope is not None
    els = {'membrures': [], 'diagonales': [], 'extremites': []}
    for k, (y, z) in enumerate([(y0, z0), (y0, z1), (y1, z0), (y1, z1)]):
        if sq:
            els['membrures'].append(Box(f'membrure{k}', [0, y - T / 2, z - T / 2], [length, y + T / 2, z + T / 2], STEEL))
        else:
            els['membrures'] += tube(f'membrure{k}', 0, y, z, 0, length)
    pitch = length / (2 * cells)
    ya_, yb_ = y0 + 0.6, y1 - 0.6
    for zz, tag in ((z0, 'av'), (z1, 'ar')):
        for i in range(2 * cells):
            xa, xb = i * pitch + 0.6, (i + 1) * pitch - 0.6
            if not slope:                              # exactly 45 degrees; the first / last bay is moved in so the (unrotated) element box stays in the model window
                xb = xa + (yb_ - ya_)
                if i == 0: xa, xb = xa + 0.82, xb + 0.82
                if i == 2 * cells - 1: xa, xb = xa - 0.82, xb - 0.82
            ya, yb = y0 + 0.6, y1 - 0.6              # inner faces of the chords: 6.8 px, so 8 px bays give exactly 45 degrees
            if slope:
                span = (yb - ya) / math.tan(math.radians(22.5))        # slope girder: shallow 22.5 degree zig-zag (local angle +-22.5 -> 45 / 0 after the slope)
                if i >= 3: continue
                xa = 0.6 + i * (length - 1.2 - span) / 2.0
                xb = xa + span
            if i % 2 == 0:
                els['diagonales'] += brace(f'diag_{tag}{i}', xa, ya, xb, yb, zz, STEEL, 0.9)
            else:
                els['diagonales'] += brace(f'diag_{tag}{i}', xa, yb, xb, ya, zz, STEEL, 0.9)
    for i in range(cells * 2 + 1):
        x = i * pitch
        for z in (z0, z1):
            els['diagonales'].append(Box(f'montant_{i}_{int(z)}', [min(max(x - 0.4, 0), length - 0.8), y0, z - 0.4], [min(max(x + 0.4, 0.8), length), y1, z + 0.4], STEEL))
    if slope:
        els['diagonales'] = [b for b in els['diagonales'] if not b.name.startswith('montant_') or b.name.split('_')[1] in ('0', '2', '4', '6')]
    els['extremites'] += [Box('plaque_0', [0, 3.4, 3.4], [0.5, 12.6, 12.6], RED), Box('plaque_1', [length - 0.5, 3.4, 3.4], [length, 12.6, 12.6], RED)]
    if slope:
        rise, run = slope
        ang = math.degrees(math.atan2(rise, run))
        for g in els.values():
            for b in g:
                _turn(b, ang, [2.0, 8.0, 8.0])
    return els


def _turn(b, ang, pivot):
    """Turns a box about z by ang around pivot: new centre, rotation = old z rotation + ang (all the girder's rotations are about z)."""
    c = b.center()
    s, co = math.sin(math.radians(ang)), math.cos(math.radians(ang))
    dx, dy = c[0] - pivot[0], c[1] - pivot[1]
    nx, ny = pivot[0] + dx * co - dy * s, pivot[1] + dx * s + dy * co
    hx, hy, hz = [(b.t[i] - b.f[i]) / 2 for i in range(3)]
    old = b.rot[1] if b.rot and b.rot[0] == 2 else 0.0
    assert b.rot is None or b.rot[0] == 2
    b.f = [nx - hx, ny - hy, c[2] - hz]; b.t = [nx + hx, ny + hy, c[2] + hz]
    b.rot = (2, round(old + ang, 4), [nx, ny, c[2]])


def poutre_l2():
    return _girder(32.0, 2)


def poutre_l1():
    return _girder(16.0, 1)


def poutre_pente_l3():
    # Minecraft block models only allow element rotations of 22.5 / 45 degrees: the roof girder is turned by exactly 22.5 degrees.
    # The model is authored for the MIDDLE cell of 3 (x from -16 to 32), and moved so that the unrotated element boxes fit that window.
    L = 41.5
    g = _girder(L, 3, slope=(L * math.sin(math.radians(22.5)), L * math.cos(math.radians(22.5))))
    lo = min(b.f[0] for els in g.values() for b in els)
    dx = -15.5 - lo
    for els in g.values():
        for b in els:
            b.f[0] += dx; b.t[0] += dx
            if b.rot: b.rot = (b.rot[0], b.rot[1], [b.rot[2][0] + dx, b.rot[2][1], b.rot[2][2]])
    return g


def flight_case():
    g = {'caisse': [], 'coins': [], 'ferrures': []}
    g['caisse'] += [Box('corps', [0.6, 0.6, 3.6], [15.4, 9.4, 12.4], BLACK),
                    Box('couvercle', [0.3, 7.0, 3.3], [15.7, 9.7, 12.7], DARK),
                    Box('rail_bas', [0.3, 0.0, 3.3], [15.7, 0.8, 12.7], DARK)]
    for xi, xx in enumerate((0.0, 14.6)):
        for zi, zz in enumerate((3.0, 11.4)):
            for yi, yy in enumerate((0.0, 8.6)):
                g['coins'].append(Box(f'coin_{xi}{yi}{zi}', [xx + 0.2, yy, zz + 0.2], [xx + 1.2, yy + 1.4, zz + 1.4], GREY))
    for i, x in enumerate((4.0, 12.0)):
        g['ferrures'] += [Box(f'loquet_{i}', [x - 1.2, 6.2, 2.9], [x + 1.2, 8.2, 3.3], LIGHT)]
    g['ferrures'] += [Box('poignee_a', [6.0, 4.0, 2.7], [10.0, 5.0, 3.3], LIGHT), Box('poignee_b', [6.0, 4.0, 12.7], [10.0, 5.0, 13.3], LIGHT)]
    return g


PIECES = {
    'regie_poteau_h1': poteau_h1,
    'regie_poteau_pied_h1': poteau_pied,
    'regie_lisse_l1': lambda: lisse_l1(0),
    'regie_noeud_croix': lambda: noeud(['+x', '-x', '+z', '-z']),
    'regie_noeud_t': lambda: noeud(['+x', '-x', '+z']),
    'regie_noeud_angle': lambda: noeud(['+x', '+z']),
    'regie_panneau_l2h1': lambda: panneau(False, 2),
    'regie_poutre_treillis_l1': poutre_l1,
    'regie_poutre_treillis_l2': poutre_l2,
    'regie_barriere_l1': barriere_l1,
    'regie_flight_case': flight_case,
}

# ------------------------------------------------------------------ writing
def faces(tile):
    i, j = tile
    uv = [4 * i, 4 * j, 4 * i + 4, 4 * j + 4]
    return {k: {'uv': list(uv), 'texture': 0} for k in ('north', 'east', 'south', 'west', 'up', 'down')}


def make_texture():
    im = Image.open(SCAF_TEX).convert('RGBA')
    red = Image.new('RGBA', (16, 16))
    import random
    rnd = random.Random(7)
    px = red.load()
    for y in range(16):
        for x in range(16):
            v = rnd.randint(-10, 10)
            px[x, y] = (200 + v, 32 + v // 2, 30 + v // 2, 255)
    im.paste(red, (48, 16))
    return im


def to_bbmodel(name, groups, ref, tex_im):
    els, children = [], []
    for gname, boxes in groups.items():
        ids = []
        for b in boxes:
            u = str(uuid.uuid4())
            rot = [0.0, 0.0, 0.0]; origin = b.center()
            if b.rot:
                rot[b.rot[0]] = b.rot[1]; origin = list(b.rot[2])
            els.append({'name': b.name, 'box_uv': False, 'rescale': False, 'locked': False, 'light_emission': 0, 'render_order': 'default',
                        'allow_mirror_modeling': True, 'from': [round(v, 4) for v in b.f], 'to': [round(v, 4) for v in b.t], 'autouv': 0, 'color': 0,
                        'origin': [round(v, 4) for v in origin], 'rotation': rot, 'faces': faces(b.tile), 'type': 'cube', 'uuid': u})
            ids.append(u)
        children.append({'name': gname, 'origin': [0.0, 0.0, 0.0], 'rotation': [0.0, 0.0, 0.0], 'color': 0, 'uuid': str(uuid.uuid4()), 'export': True,
                         'mirror_uv': False, 'isOpen': False, 'locked': False, 'visibility': True, 'autouv': 0, 'children': ids})
    d = {k: copy.deepcopy(v) for k, v in ref.items() if k not in ('elements', 'outliner', 'textures', 'name', 'model_identifier')}
    d['name'] = name
    d['model_identifier'] = name
    d['elements'] = els
    d['outliner'] = [{'name': name, 'origin': [0.0, 0.0, 0.0], 'rotation': [0.0, 0.0, 0.0], 'color': 0, 'uuid': str(uuid.uuid4()), 'export': True,
                      'mirror_uv': False, 'isOpen': True, 'locked': False, 'visibility': True, 'autouv': 0, 'children': children}]
    buf = io.BytesIO(); tex_im.save(buf, 'PNG')
    t = copy.deepcopy(ref['textures'][0])
    t['name'] = 'regie.png'; t['uuid'] = str(uuid.uuid4())
    t['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    d['textures'] = [t]
    return d


def main():
    os.makedirs(OUT, exist_ok=True)
    ref = json.load(open(REF, encoding='utf-8'))
    tex = make_texture()
    tex.save(os.path.join(OUT, 'regie.png'))
    for name, fn in PIECES.items():
        groups = fn()
        d = to_bbmodel(name, groups, ref, tex)
        for e in d['elements']:
            assert sum(1 for v in e['rotation'] if abs(v) > 1e-9) <= 1, e['name']
        for e in d['elements']:
            r = [v for v in e['rotation'] if abs(v) > 1e-9]
            assert not r or r[0] in (-45.0, -22.5, 22.5, 45.0), (name, e['name'], r)
        json.dump(d, open(os.path.join(OUT, name + '.bbmodel'), 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
        if BB_OUT:
            json.dump(d, open(os.path.join(BB_OUT, name + '.bbmodel'), 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
        n = len(d['elements'])
        lo = [min(min(e['from'][i], e['to'][i]) for e in d['elements']) for i in range(3)]
        hi = [max(max(e['from'][i], e['to'][i]) for e in d['elements']) for i in range(3)]
        print(f'{name}: {n} elements, bbox {lo} .. {hi}')


if __name__ == '__main__':
    main()
