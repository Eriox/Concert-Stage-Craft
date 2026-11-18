"""Generates every texture, model, blockstate, recipe, loot table and lang file.
- Blockbench models in tools/blockbench/*.bbmodel are converted to Minecraft block models
  (truss_* and the chain hoist), their embedded textures are extracted.
- The rest (stairs, curtains, small textures) is generated procedurally.
Run from anywhere: python3 tools/generate_assets.py"""
import json, math, os, struct, zlib, base64
import numpy as np
PROJECT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
MOD = 'concertstage'
ROOT = os.path.join(PROJECT, 'common', 'src', 'main', 'resources')
BB = os.path.join(PROJECT, 'tools', 'blockbench')
A = os.path.join(ROOT, 'assets', MOD)
D = os.path.join(ROOT, 'data', MOD)
DYES = {'white': (249, 255, 254), 'orange': (249, 128, 29), 'magenta': (199, 78, 189), 'light_blue': (58, 179, 218), 'yellow': (254, 216, 61), 'lime': (128, 199, 31), 'pink': (243, 139, 170), 'gray': (71, 79, 82), 'light_gray': (157, 157, 151), 'cyan': (22, 156, 156), 'purple': (137, 50, 184), 'blue': (60, 68, 170), 'brown': (131, 84, 50), 'green': (94, 124, 22), 'red': (176, 46, 38), 'black': (29, 29, 33)}
DROPS = range(0, 6)
TRUSSES = {'truss_h1_l1': ('truss_h1_l1', (1, 1, 1), (0, 0, 0)), 'truss_h1_l2': ('truss_h1_l2', (2, 1, 1), (0, 0, 0)), 'truss_h2_l2': ('truss_h2_l2', (2, 2, 1), (0, 0, 0)), 'truss_v2_h2': ('truss_v2_h2', (1, 2, 1), (0, 0, 0)), 'truss_corner_3': ('truss_corner_3', (1, 1, 1), (0, 0, 0)), 'truss_corner_3_h2': ('truss_corner_3_h2', (1, 2, 1), (0, 0, 0)), 'truss_corner_6': ('truss_corner_6', (1, 1, 1), (0, 0, 0)), 'truss_corner_6_h2': ('truss_corner_6_h2', (1, 2, 1), (0, 0, 0)), 'truss_base_v2': ('truss_base_v2', (1, 1, 1), (0, 0, 0)), 'truss_h2_l2_x30d': ('truss_h2_l2_x30d', (2, 2, 1), (0, 0, 0)), 'truss_x30d_l2': ('truss_x30d_l2', (2, 1, 1), (0, 0, 0)), 'truss_h1_l2_petit': ('truss_h1_l2_petit', (2, 1, 1), (0, 0, 0))}
HOISTS = {'palan_chaine': ('palan_chaine', (1, 1, 1), (0, 0, 0))}
TRUSSES_NOIR = {k + '_noir': v for k, v in TRUSSES.items()}
MULTI = {**{k: ('truss',) + v[1:] + (v[0],) for k, v in TRUSSES.items()}, **{k: ('truss_noir',) + v[1:] + (v[0],) for k, v in TRUSSES_NOIR.items()}, **{k: ('palan_chaine',) + v[1:] + (v[0],) for k, v in HOISTS.items()}}
ITEM_BLOCKS = ['stage_riser', 'stage_stairs'] + list(TRUSSES) + list(TRUSSES_NOIR) + list(HOISTS) + ['curtain_rail']
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tower_lang import HEIGHTS as TOWER_HEIGHTS, CORNERS as TOWER_CORNERS, TOWER_BLOCKS, add_lang
ITEM_BLOCKS += TOWER_BLOCKS

def wj(path, obj, compact=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        if compact:
            json.dump(obj, f, separators=(',', ':'), ensure_ascii=False)
        else:
            json.dump(obj, f, indent=2, ensure_ascii=False)

def write_png(path, fn):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b''
    for y in range(16):
        raw += b'\x00'
        for x in range(16):
            p = fn(x, y)
            if len(p) == 3:
                p = (*p, 255)
            raw += bytes((int(max(0, min(255, v))) for v in p))

    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 4294967295)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)

def noise(x, y, seed=0):
    h = x * 374761393 + y * 668265263 + seed * 2147483647 & 4294967295
    h = (h ^ h >> 13) * 1274126177 & 4294967295
    return ((h ^ h >> 16) & 255) / 255.0

def scale(c, f):
    return tuple((v * f for v in c))

def tex(name, fn):
    write_png(os.path.join(A, 'textures', 'block', name + '.png'), fn)

def T(name):
    return f'{MOD}:block/{name}'

def block_model(name, obj, compact=False):
    wj(os.path.join(A, 'models', 'block', name + '.json'), obj, compact)

def _rot(rx, ry, rz):
    a, b, c = map(math.radians, (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def display(base_scale=1.0, center=(0.5, 0.5, 0.5), maxdim=16.0):
    """Item display transforms. base_scale shrinks big models; center (block units) keeps them centred in the slot."""
    k = 16.0 / maxdim * base_scale
    out = {}
    specs = {'gui': ((30, 225, 0), 0.625), 'ground': ((0, 0, 0), 0.25), 'fixed': ((0, 0, 0), 0.5), 'thirdperson_righthand': ((75, 45, 0), 0.375), 'firstperson_righthand': ((0, 45, 0), 0.4), 'firstperson_lefthand': ((0, 225, 0), 0.4)}
    base_t = {'ground': (0, 3, 0), 'thirdperson_righthand': (0, 2.5, 0)}
    q = np.array(center) - 0.5
    for name, (rot, s) in specs.items():
        sc = s * k
        t = -_rot(*rot) @ (sc * q) * 16
        bt = np.array(base_t.get(name, (0, 0, 0)), float)
        t = np.clip(t + bt, -80, 80)
        out[name] = {'rotation': list(rot), 'translation': [round(float(v), 3) for v in t], 'scale': [round(sc, 4)] * 3}
    return out

def item_model(name, parent, disp=None):
    wj(os.path.join(A, 'models', 'item', name + '.json'), {'parent': f'{MOD}:block/{parent}', 'display': disp or display()})
ALL = ['north', 'south', 'east', 'west', 'up', 'down']

def box(frm, to, faces=ALL, tx='#t'):
    return {'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': {f: {'texture': tx} for f in faces}}

def clamp(v):
    return max(-16.0, min(32.0, v))

def convert_bbmodel(path, select=None, d=None):
    """select: optional predicate on the element name (keep only some elements); d: an already loaded (and modified) model."""
    if d is None:
        d = json.load(open(path, encoding='utf-8'))
    elements, dropped, clamped = ([], 0, 0)
    for e in d['elements']:
        if e.get('type', 'cube') != 'cube':
            continue
        if select is not None and (not select(e['name'])):
            continue
        frm = [clamp(v) for v in e['from']]
        to = [clamp(v) for v in e['to']]
        if frm != [round(v, 6) for v in e['from']] or to != [round(v, 6) for v in e['to']]:
            clamped += 1
        if any((to[i] - frm[i] < 0 for i in range(3))):
            dropped += 1
            continue
        el = {'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to], 'faces': {}}
        r = e.get('rotation')
        if r and any((abs(v) > 1e-09 for v in r)):
            axes = [i for i, v in enumerate(r) if abs(v) > 1e-09]
            assert len(axes) == 1, ('multi-axis rotation', e['name'])
            i = axes[0]
            assert r[i] in (-45, -22.5, 22.5, 45), ('bad angle', e['name'], r[i])
            el['rotation'] = {'angle': r[i], 'axis': 'xyz'[i], 'origin': [round(v, 4) for v in e['origin']]}
            if e.get('rescale'):
                el['rotation']['rescale'] = True
        for fn, fd in e['faces'].items():
            if fd.get('texture') is None:
                continue
            f = {'uv': [round(v, 4) for v in fd['uv']], 'texture': '#0'}
            if fd.get('rotation'):
                f['rotation'] = fd['rotation']
            el['faces'][fn] = f
        if el['faces']:
            elements.append(el)
    return (d, elements, dropped, clamped)

def bbox_of(elements):
    mn = np.array([1000000000.0] * 3)
    mx = np.array([-1000000000.0] * 3)
    for e in elements:
        mn = np.minimum(mn, e['from'])
        mx = np.maximum(mx, e['to'])
    return (mn, mx)
extracted = set()

def extract_texture(d, name):
    if name in extracted:
        return
    t = d['textures'][0]
    src = t['source'].split(',', 1)[1]
    path = os.path.join(A, 'textures', 'block', name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'wb').write(base64.b64decode(src))
    extracted.add(name)
import re, copy as _copy
HOOK_RE = re.compile('^(masse_|tige_bas|crochet_bas_|pointe_bas)')
HOOK_DY = -6.0
LINK_PITCH, LINK_FIRST = (0.95, 7.6)

def _links(d, n0, n1, dy=0.0):
    """Copies of maillon_0 / maillon_1 for the link numbers n0..n1-1 (their y is 7.6 - 0.95 n)."""
    src = {e['name']: e for e in d['elements']}
    out = []
    for n in range(n0, n1):
        e = _copy.deepcopy(src['maillon_0' if n % 2 == 0 else 'maillon_1'])
        shift = LINK_FIRST - LINK_PITCH * n - e['from'][1] + dy
        e['name'] = f'maillon_{n}'
        for k in ('from', 'to', 'origin'):
            e[k][1] = round(e[k][1] + shift, 4)
        out.append(e)
    return out

def hoist_full_model(path):
    d = json.load(open(path, encoding='utf-8'))
    for e in d['elements']:
        if HOOK_RE.match(e['name']):
            for k in ('from', 'to', 'origin'):
                e[k][1] = round(e[k][1] + HOOK_DY, 4)
    mass_top = 3.8 + HOOK_DY
    n = 6
    while LINK_FIRST - LINK_PITCH * n >= mass_top - 1.7:
        n += 1
    d['elements'] += _links(d, 6, n)
    return convert_bbmodel(path, d=d)[1]

def export_hoist_hook(path):
    """towers/hoist_hook.json: the cubes of the hook assembly (authored position) and the two link shapes (n = 0 / 1) with their pitch."""
    d = json.load(open(path, encoding='utf-8'))

    def cube(e):
        f, t = (e['from'], e['to'])
        o = e.get('origin') or [(f[i] + t[i]) / 2 for i in range(3)]
        r = e.get('rotation') or [0, 0, 0]
        uv = e['faces']['north']['uv']
        return [round(v, 3) for v in (*f, *t, *o, *r)] + [int(v) for v in uv]
    hook = [cube(e) for e in d['elements'] if HOOK_RE.match(e['name'])]
    byname = {e['name']: e for e in d['elements']}
    wj(os.path.join(A, 'towers', 'hoist_hook.json'), {'hook': hook, 'link_a': cube(byname['maillon_0']), 'link_b': cube(byname['maillon_1']), 'pitch': LINK_PITCH, 'first_link': 6, 'hook_dy': HOOK_DY, 'mass_top': 3.8}, compact=True)
report = []
for name, (fam, file) in {**{k: ('truss', v[0]) for k, v in TRUSSES.items()}, **{k: ('truss_noir', v[0]) for k, v in TRUSSES_NOIR.items()}, **{k: ('palan_chaine', v[0]) for k, v in HOISTS.items()}}.items():
    if name in HOISTS:
        d, elements, dropped, clamped = convert_bbmodel(os.path.join(BB, file + '.bbmodel'), select=lambda n: not HOOK_RE.match(n))
    else:
        d, elements, dropped, clamped = convert_bbmodel(os.path.join(BB, file + '.bbmodel'))
    if fam == 'truss_noir':
        import shutil
        os.makedirs(os.path.join(A, 'textures', 'block'), exist_ok=True)
        shutil.copyfile(os.path.join(BB, 'reference', 'truss_noir.png'), os.path.join(A, 'textures', 'block', 'truss_noir.png'))
    else:
        extract_texture(d, fam)
    tx = T(fam)
    block_model(name, {'ambientocclusion': False, 'textures': {'0': tx, 'particle': tx}, 'elements': elements}, compact=True)
    mn, mx = bbox_of(elements)
    size = mx - mn
    center = tuple(((mn + mx) / 2 / 16).tolist())
    if name in HOISTS:
        full = hoist_full_model(os.path.join(BB, file + '.bbmodel'))
        block_model(name + '_full', {'ambientocclusion': False, 'textures': {'0': tx, 'particle': tx}, 'elements': full}, compact=True)
        fmn, fmx = bbox_of(full)
        item_model(name, name + '_full', display(1.0, tuple(((fmn + fmx) / 2 / 16).tolist()), float((fmx - fmn).max())))
        export_hoist_hook(os.path.join(BB, file + '.bbmodel'))
    else:
        item_model(name, name, display(1.0, center, float(size.max())))
    report.append((name, len(elements), dropped, clamped, [round(float(v), 1) for v in mn], [round(float(v), 1) for v in mx]))
for fam in ('truss', 'truss_noir', 'palan_chaine'):
    block_model(f'empty_{fam}', {'textures': {'particle': T(fam)}})

def metal_tex(x, y):
    n = noise(x, y) * 26
    base = 170 + n + (10 if y % 4 == 0 else 0)
    return (base, base + 5, base + 14)
tex('metal', metal_tex)

def _lerp(a, b, t):
    return tuple((a[i] * (1 - t) + b[i] * t for i in range(3)))

def curtain_tex(c):
    """Glossy PVC sheet: one soft highlight band per block (follows the wave of the model), a faint welded seam on
    the block edge, a little vertical reflection streaking and a slightly lighter top (light comes from above)."""

    def f(x, y):
        s = 0.5 + 0.5 * math.cos(2 * math.pi * (x + 0.5) / 16 - 0.9)
        col = scale(c, (0.72 + 0.32 * s ** 2) * (1.04 - 0.08 * y / 15) * (0.985 + 0.03 * noise(x, 0, 21)))
        col = _lerp(col, (236, 240, 248), 0.34 * s ** 7)
        if x == 0:
            col = scale(col, 0.8)
        return scale(col, 0.985 + 0.03 * noise(x, y, 7))
    return f

def curtain_hem_tex(c):
    """Reinforced welded hem: two bright weld lines, a darker doubled-over band between them."""
    base = curtain_tex(c)

    def f(x, y):
        col = base(x, y)
        if y in (3, 6):
            return _lerp(scale(col, 1.18), (225, 230, 238), 0.22)
        if y in (4, 5):
            return scale(col, 0.72)
        return scale(col, 0.9)
    return f
for n, c in DYES.items():
    tex(f'curtain_{n}', curtain_tex(c))
    tex(f'curtain_hem_{n}', curtain_hem_tex(c))

def grommet(x, y):
    d = math.hypot(x - 7.5, y - 7.5)
    if d < 2.2:
        return (48, 48, 54)
    if d < 6.5:
        return (196, 198, 206) if d < 5.2 else (120, 122, 130)
    return (150, 152, 160)
tex('grommet', grommet)

def item_tex(name, fn):
    write_png(os.path.join(A, 'textures', 'item', name + '.png'), fn)

def ruler(x, y):
    if y in (2, 3) and 2 <= x <= 13:
        return (170, 175, 185)
    if 4 <= y <= 12 and 3 <= x <= 12:
        return scale((176, 36, 44), [0.6, 0.9, 1.1, 0.9][x % 4])
    return (0, 0, 0, 0)
item_tex('curtain_tool', ruler)

def set_square(x, y):
    if 2 <= x <= 13 and 2 <= y <= 13 and (x <= y):
        edge = x == 2 or y == 13 or x == y
        if edge:
            return (92, 58, 20)
        if x == 3 and y % 2 == 0:
            return (92, 58, 20)
        if x == 4 and y % 4 == 0:
            return (92, 58, 20)
        return scale((232, 150, 44), [0.9, 1.0, 1.08, 1.0][(x + y) % 4])
    return (0, 0, 0, 0)
item_tex('angle_tool', set_square)
UV_TUBE, UV_STEEL, UV_YELLOW, UV_PLATE = ([0, 0, 4, 4], [4, 0, 8, 4], [0, 4, 4, 8], [4, 4, 8, 8])
LEGACY_STEP_TEXTURE = {'0': T('stage/metals'), 'particle': T('stage/metals')}

def sbox(frm, to, uv=UV_TUBE, top=None, rot=None):
    faces = {f: {'uv': list(uv), 'texture': '#0'} for f in ALL}
    if top:
        faces['up'] = {'uv': list(top), 'texture': '#0'}
    el = {'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': faces}
    if rot:
        el['rotation'] = rot
    return el

def diag_tube(x0, x1, zc, yc, length, thick, uv=UV_TUBE):
    """Tube along the 45 degree slope of the stairs (rises toward -z)."""
    h = length / 2
    t = thick / 2
    return sbox([x0, yc - t, zc - h], [x1, yc + t, zc + h], uv, rot={'angle': 45, 'axis': 'x', 'origin': [(x0 + x1) / 2, yc, zc]})

def jack_foot(cx, cz):
    return [sbox([cx - 1.7, 0, cz - 1.7], [cx + 1.7, 0.5, cz + 1.7], UV_STEEL), sbox([cx - 1.0, 0.5, cz - 1.0], [cx + 1.0, 2.6, cz + 1.0], UV_YELLOW)]

def stairs_core():
    els = []
    for i in range(1, 5):
        y = 4 * i
        z0 = 16 - 4 * i
        z1 = 20 - 4 * i
        els.append(sbox([1, y - 1.3, z0], [15, y, z1], UV_STEEL, top=UV_PLATE))
        els.append(sbox([1, y - 1.7, z0 - 0.1], [15, y + 0.1, z0 + 1.0], UV_TUBE))
        els.append(sbox([1, y - 3.4, z1 - 1.6], [15, y - 1.9, z1 - 0.1], UV_TUBE))
    return els

def stairs_side(x0, x1):
    """Stringer + posts + hand rail on one side (x0..x1 is the 1.5 wide strip next to the block edge)."""
    els = []
    w = x1 - x0
    cx = (x0 + x1) / 2
    els.append(diag_tube(x0, x1, 8, 5.0, 20.5, 1.6))
    els.append(diag_tube(x0 + 0.1, x1 - 0.1, 8, 18.0, 20.5, 1.3))
    for zc, kind in ((1.5, 'top'), (8.0, 'mid'), (14.5, 'low')):
        rail_y = 16 - zc + 10
        bottom = 2.6 if kind == 'top' else max(0.0, 16 - zc - 3.0)
        els.append(sbox([x0, bottom, zc - 0.75], [x1, rail_y + 0.65, zc + 0.75], UV_TUBE))
        if kind == 'top':
            els += jack_foot(cx, zc)
    els.append(sbox([x0, 2.6, 0.2], [x1, 3.9, 2.8], UV_STEEL))
    return els

def stairs_el(els):
    return {'ambientocclusion': False, 'textures': LEGACY_STEP_TEXTURE, 'elements': els}
block_model('stage_stairs_core', stairs_el(stairs_core()))
block_model('stage_stairs_left', stairs_el(stairs_side(0, 1.5)))
block_model('stage_stairs_right', stairs_el(stairs_side(14.5, 16)))
block_model('stage_stairs_inventory', stairs_el(stairs_core() + stairs_side(0, 1.5) + stairs_side(14.5, 16)))
item_model('stage_stairs', 'stage_stairs_inventory')

def riser_els():
    els = []
    for x0 in (0, 14.5):
        for z0 in (0, 14.5):
            els.append(sbox([x0, 2.6, z0], [x0 + 1.5, 16, z0 + 1.5]))
            els += jack_foot(x0 + 0.75, z0 + 0.75)
    for y0 in (3.0, 13.2):
        els.append(sbox([1.5, y0, 0], [14.5, y0 + 1.4, 1.5]))
        els.append(sbox([1.5, y0, 14.5], [14.5, y0 + 1.4, 16]))
        els.append(sbox([0, y0, 1.5], [1.5, y0 + 1.4, 14.5]))
        els.append(sbox([14.5, y0, 1.5], [16, y0 + 1.4, 14.5]))
    els.append(sbox([0, 14.6, 0], [16, 16, 16], UV_STEEL, top=UV_PLATE))
    return els
block_model('stage_riser', stairs_el(riser_els()))
item_model('stage_riser', 'stage_riser')
SHIFT = 6.5
WAVE = [0.55 * math.sin(2 * math.pi * (i + 0.5) / 8) for i in range(8)]

def cloth_elements(y1, dz=0.0):
    return [box([2 * i, 0, 7.45 + WAVE[i] + dz], [2 * i + 2, y1, 8.55 + WAVE[i] + dz], ALL, '#t') for i in range(8)]

def eyelet(x, dz):
    faces = {f: {'texture': '#g' if f in ('north', 'south') else '#m'} for f in ALL}
    return {'from': [x - 1.2, 10.2, 6.5 + dz], 'to': [x + 1.2, 12.6, 9.5 + dz], 'faces': faces}

def cloth_top_elements(dz):
    els = cloth_elements(13, dz)
    els.append(box([0, 9.2, 6.7 + dz], [16, 13, 9.3 + dz], ALL, '#h'))
    for x in (4, 12):
        els.append(eyelet(x, dz))
        els.append(box([x - 0.4, 12.6, 7.3 + dz], [x + 0.4, 13.6, 8.7 + dz], ALL, '#m'))
    els.append(box([0, 13, min(6 + dz, 12.5)], [16, 16, min(10 + dz, 16)], ALL, '#m'))
    return els

def cloth_tex(n):
    return {'t': T(f'curtain_{n}'), 'h': T(f'curtain_hem_{n}'), 'g': T('grommet'), 'm': T('metal'), 'particle': T(f'curtain_{n}')}
for n in DYES:
    for tag, dz in (('', 0.0), ('s_', SHIFT)):
        block_model(f'cloth_{tag}{n}', {'ambientocclusion': False, 'textures': cloth_tex(n), 'elements': cloth_elements(16, dz)})
        block_model(f'cloth_top_{tag}{n}', {'ambientocclusion': False, 'textures': cloth_tex(n), 'elements': cloth_top_elements(dz)})
for d in range(1, 6):
    for tag, dz in (('', 0.0), ('s_', SHIFT)):
        block_model(f'rail_clips_{tag}{d}', {'ambientocclusion': False, 'textures': {'m': T('metal'), 'particle': T('metal')}, 'elements': [box([x, 16, 6.5 + dz], [x + 1.6, 16 + d, 9.5 + dz], ALL, '#m') for x in (1.5, 7.2, 12.9)]})
item_model('curtain_rail', 'cloth_top_black')
wj(os.path.join(A, 'models', 'item', 'angle_tool.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'{MOD}:item/angle_tool'}})
wj(os.path.join(A, 'models', 'item', 'curtain_tool.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'{MOD}:item/curtain_tool'}})

def bs(name, obj):
    wj(os.path.join(A, 'blockstates', name + '.json'), obj)
HROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

def m(model, x=0, y=0):
    d = {'model': f'{MOD}:block/{model}'}
    if x:
        d['x'] = x
    if y:
        d['y'] = y
    return d
for name, (fam, size, main, _) in MULTI.items():
    sx, sy, sz = size
    mx, my, mz = main
    main_idx = mx + sx * (mz + sz * my)
    host_idx = {}
    v = {}
    for f, r in HROT.items():
        for p in range(27):
            if name in HOISTS:
                v[f'angled=false,facing={f},part={p}'] = m(name, y=r) if p == main_idx else m(f'empty_{fam}')
                v[f'angled=true,facing={f},part={p}'] = m(f'empty_{fam}')
            else:
                v[f'facing={f},part={p}'] = m(name, y=r) if p == main_idx else m(f'empty_{fam}')
    bs(name, {'variants': v})
bs('stage_riser', {'variants': {'': m('stage_riser')}})
bs('angled_holder', {'variants': {'': m('empty_truss')}})
parts = []
for f, r in HROT.items():
    parts.append({'when': {'facing': f}, 'apply': m('stage_stairs_core', y=r)})
    parts.append({'when': {'facing': f, 'left': 'false'}, 'apply': m('stage_stairs_left', y=r)})
    parts.append({'when': {'facing': f, 'right': 'false'}, 'apply': m('stage_stairs_right', y=r)})
bs('stage_stairs', {'multipart': parts})
SHROT = {('x', 1): 0, ('x', 2): 180, ('z', 1): 270, ('z', 2): 90}
parts = []
for fg, ax, rot in ((('east', 'west'), 'x', 0), (('north', 'south'), 'z', 90)):
    fgs = '|'.join(fg)
    for n in DYES:
        parts.append({'when': {'facing': fgs, 'color': n, 'shift': '0'}, 'apply': m(f'cloth_top_{n}', y=rot)})
        for sh in (1, 2):
            parts.append({'when': {'facing': fgs, 'color': n, 'shift': str(sh)}, 'apply': m(f'cloth_top_s_{n}', y=SHROT[ax, sh])})
    for d in range(1, 6):
        parts.append({'when': {'facing': fgs, 'drop': str(d), 'shift': '0'}, 'apply': m(f'rail_clips_{d}', y=rot)})
        for sh in (1, 2):
            parts.append({'when': {'facing': fgs, 'drop': str(d), 'shift': str(sh)}, 'apply': m(f'rail_clips_s_{d}', y=SHROT[ax, sh])})
bs('curtain_rail', {'multipart': parts})
parts = []
for ax, rot in (('x', 0), ('z', 90)):
    for n in DYES:
        parts.append({'when': {'axis': ax, 'top': 'false', 'color': n, 'shift': '0'}, 'apply': m(f'cloth_{n}', y=rot)})
        parts.append({'when': {'axis': ax, 'top': 'true', 'color': n, 'shift': '0'}, 'apply': m(f'cloth_top_{n}', y=rot)})
        for sh in (1, 2):
            parts.append({'when': {'axis': ax, 'top': 'false', 'color': n, 'shift': str(sh)}, 'apply': m(f'cloth_s_{n}', y=SHROT[ax, sh])})
            parts.append({'when': {'axis': ax, 'top': 'true', 'color': n, 'shift': str(sh)}, 'apply': m(f'cloth_top_s_{n}', y=SHROT[ax, sh])})
    for d in range(1, 6):
        parts.append({'when': {'axis': ax, 'top': 'true', 'drop': str(d), 'shift': '0'}, 'apply': m(f'rail_clips_{d}', y=rot)})
        for sh in (1, 2):
            parts.append({'when': {'axis': ax, 'top': 'true', 'drop': str(d), 'shift': str(sh)}, 'apply': m(f'rail_clips_s_{d}', y=SHROT[ax, sh])})
bs('curtain_cloth', {'multipart': parts})
for b in ITEM_BLOCKS:
    wj(os.path.join(D, 'loot_tables', 'blocks', b + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': f'{MOD}:{b}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
wj(os.path.join(ROOT, 'data', 'concertstage', 'tags', 'blocks', 'never_lift.json'), {'replace': False, 'values': []})
wj(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'blocks', 'mineable', 'pickaxe.json'), {'replace': False, 'values': [f'{MOD}:{b}' for b in ITEM_BLOCKS]})

def recipe(name, pattern, key, count=1):
    wj(os.path.join(D, 'recipes', name + '.json'), {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': pattern, 'key': {k: {'item': v} for k, v in key.items()}, 'result': {'item': f'{MOD}:{name}', 'count': count}})

def shapeless(name, items):
    wj(os.path.join(D, 'recipes', name + '.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'building', 'ingredients': [{'item': i} for i in items], 'result': {'item': f'{MOD}:{name}', 'count': 1}})
I, B, BL, IB = ('minecraft:iron_ingot', 'minecraft:iron_bars', 'minecraft:black_concrete', 'minecraft:iron_block')
recipe('truss_h1_l1', ['I I', 'B B', 'I I'], {'I': I, 'B': B}, 2)
recipe('truss_h1_l2', ['I I', 'BBB', 'I I'], {'I': I, 'B': B})
recipe('truss_h2_l2', ['III', 'BIB', 'III'], {'I': I, 'B': B})
recipe('truss_v2_h2', ['IBI', 'I I', 'IBI'], {'I': I, 'B': B})
recipe('truss_corner_3', ['IBI', 'BIB', 'III'], {'I': I, 'B': B})
recipe('truss_corner_3_h2', ['IBI', 'BIB', 'IBI'], {'I': I, 'B': B})
recipe('truss_corner_6', ['BIB', 'III', 'BIB'], {'I': I, 'B': B})
recipe('truss_corner_6_h2', ['BIB', 'IXI', 'BIB'], {'I': I, 'B': B, 'X': IB})
recipe('truss_base_v2', [' I ', 'III', 'III'], {'I': I})
recipe('truss_h1_l2_petit', ['I I', 'BBB'], {'I': I, 'B': B}, 2)
recipe('truss_x30d_l2', [' B ', 'I I', 'III'], {'I': I, 'B': B}, 2)
shapeless('truss_h2_l2_x30d', [f'{MOD}:truss_h2_l2', f'{MOD}:truss_x30d_l2'])
recipe('palan_chaine', ['III', 'RPR', ' C '], {'I': I, 'R': 'minecraft:redstone', 'P': 'minecraft:piston', 'C': 'minecraft:chain'})
for k in TRUSSES:
    shapeless(k + '_noir', [f'{MOD}:{k}', 'minecraft:black_dye'])
recipe('stage_riser', ['CC'], {'C': BL}, 2)
recipe('stage_stairs', ['C  ', 'CC ', 'CCC'], {'C': BL}, 8)
recipe('curtain_rail', ['III', 'B B'], {'I': I, 'B': B}, 2)
recipe('curtain_tool', ['  I', ' S ', 'S  '], {'I': I, 'S': 'minecraft:stick'})
recipe('angle_tool', ['I  ', 'II ', 'III'], {'I': I})
for h, cols in zip(TOWER_HEIGHTS, (3, 4, 5, 6)):
    for c in TOWER_CORNERS:
        shapeless(f'motor_tower_{h}_{c}', [f'{MOD}:truss_corner_{c[6:]}', 'minecraft:piston', f'{MOD}:truss_base_v2'] + [f'{MOD}:truss_v2_h2'] * cols)
        shapeless(f'motor_tower_{h}_{c}_noir', [f'{MOD}:motor_tower_{h}_{c}', 'minecraft:black_dye'])
recipe('lift_remote', [' T ', 'IBI', 'IRI'], {'T': 'minecraft:redstone_torch', 'I': I, 'B': 'minecraft:stone_button', 'R': 'minecraft:redstone'})
en = {'itemGroup.concertstage': 'Concert Stage', 'block.concertstage.stage_riser': 'Stage Riser', 'block.concertstage.stage_stairs': 'Stage Stairs', 'block.concertstage.truss_h1_l1': 'Truss 1x1', 'block.concertstage.truss_h1_l2': 'Truss 1x2', 'block.concertstage.truss_h2_l2': 'Truss 2x2 (panel)', 'block.concertstage.truss_v2_h2': 'Truss Column 2', 'block.concertstage.truss_corner_3': 'Truss Corner (3-way)', 'block.concertstage.truss_corner_3_h2': 'Truss Corner (3-way, 2 high)', 'block.concertstage.truss_corner_6': 'Truss Corner (6-way)', 'block.concertstage.truss_corner_6_h2': 'Truss Corner (6-way, 2 high)', 'block.concertstage.truss_base_v2': 'Truss Base', 'block.concertstage.truss_h1_l2_petit': 'Small Truss 1x2 (10 px)', 'block.concertstage.truss_x30d_l2': 'X30D Triangular Truss 1x2', 'block.concertstage.truss_h2_l2_x30d': 'Truss 2x2 (panel) with X30D', 'block.concertstage.palan_chaine': 'Chain Hoist D8+', 'block.concertstage.angled_holder': 'Angled Truss', 'item.concertstage.angle_tool': 'Angle Tool', 'item.concertstage.angle_tool.bend': 'Angle: %s°', 'item.concertstage.angle_tool.tip1': 'Hold it (either hand) while placing a truss or a motor tower', 'item.concertstage.angle_tool.tip2': 'Click in the air to change the angle: 10, 20, 30, 45... (sneak: back)', 'item.concertstage.angle_tool.tip3': 'A truss on the side of a turned tower takes its angle; a line carries on straight, as long as you like; sneak to bend it', 'message.concertstage.angle_set': 'Angle: %s°', 'message.concertstage.angle_blocked': 'No room to place the truss at that angle', 'message.concertstage.angle_placed': 'Angled truss placed: %s°', 'block.concertstage.curtain_rail': 'Curtain Rail', 'block.concertstage.curtain_cloth': 'Curtain', 'item.concertstage.curtain_tool': 'Tarp Tool', 'stage_size.concertstage.small': 'Small', 'stage_size.concertstage.medium': 'Medium', 'stage_size.concertstage.large': 'Large', 'tool_mode.concertstage.length': 'Length', 'tool_mode.concertstage.height': 'Height / Width', 'message.concertstage.curtain_size': 'Size: %s x %s', 'message.concertstage.tool_hint': 'Right-click a curtain rail or its cloth to open the curtain tool', 'gui.concertstage.title_curtain': 'Tarp Tool - Curtain', 'gui.concertstage.title_tarp': 'Tarp Tool - Horizontal tarp', 'gui.concertstage.length': 'Length', 'gui.concertstage.height': 'Height', 'gui.concertstage.width': 'Width', 'gui.concertstage.value': '%s: %s blocks', 'gui.concertstage.position': 'Cloth position: %s', 'gui.concertstage.pos.center': 'Centre', 'gui.concertstage.pos.north': 'North side', 'gui.concertstage.pos.south': 'South side', 'gui.concertstage.pos.east': 'East side', 'gui.concertstage.pos.west': 'West side', 'gui.concertstage.done': 'Done'}
fr = {'itemGroup.concertstage': 'Scène de concert', 'block.concertstage.stage_riser': 'Contremarche de scène', 'block.concertstage.stage_stairs': 'Escalier de scène', 'block.concertstage.truss_h1_l1': 'Truss 1x1', 'block.concertstage.truss_h1_l2': 'Truss 1x2', 'block.concertstage.truss_h2_l2': 'Truss 2x2 (panneau)', 'block.concertstage.truss_v2_h2': 'Truss colonne 2', 'block.concertstage.truss_corner_3': 'Coin de truss (3 voies)', 'block.concertstage.truss_corner_3_h2': 'Coin de truss (3 voies, 2 de haut)', 'block.concertstage.truss_corner_6': 'Coin de truss (6 voies)', 'block.concertstage.truss_corner_6_h2': 'Coin de truss (6 voies, 2 de haut)', 'block.concertstage.truss_base_v2': 'Pied de truss', 'block.concertstage.truss_h1_l2_petit': 'Petit truss 1x2 (10 px)', 'block.concertstage.truss_x30d_l2': 'Truss triangulaire X30D 1x2', 'block.concertstage.truss_h2_l2_x30d': 'Truss 2x2 (panneau) avec X30D', 'block.concertstage.palan_chaine': 'Palan à chaîne D8+', 'block.concertstage.angled_holder': 'Truss en angle', 'item.concertstage.angle_tool': "Outil d'angle", 'item.concertstage.angle_tool.bend': 'Angle : %s°', 'item.concertstage.angle_tool.tip1': "À tenir en main (l'une ou l'autre) en posant un truss ou une tour motorisée", 'item.concertstage.angle_tool.tip2': "Clic dans le vide : change l'angle, 10, 20, 30, 45... (accroupi : en arrière)", 'item.concertstage.angle_tool.tip3': "Un truss sur le flanc d'une tour tournée prend son angle ; une ligne se prolonge tout droit, sans limite ; accroupi : coude", 'message.concertstage.angle_set': 'Angle : %s°', 'message.concertstage.angle_blocked': 'Pas de place pour poser le truss avec cet angle', 'message.concertstage.angle_placed': 'Truss posé à %s°', 'block.concertstage.curtain_rail': 'Rail de rideau', 'block.concertstage.curtain_cloth': 'Rideau', 'item.concertstage.curtain_tool': 'Outil bâche', 'stage_size.concertstage.small': 'Petite', 'stage_size.concertstage.medium': 'Moyenne', 'stage_size.concertstage.large': 'Grande', 'tool_mode.concertstage.length': 'Longueur', 'tool_mode.concertstage.height': 'Hauteur / Largeur', 'message.concertstage.curtain_size': 'Taille : %s x %s', 'message.concertstage.tool_hint': "Clic droit sur un rail de rideau ou son tissu pour ouvrir l'outil", 'gui.concertstage.title_curtain': 'Outil bâche - Rideau', 'gui.concertstage.title_tarp': 'Outil bâche - Bâche horizontale', 'gui.concertstage.length': 'Longueur', 'gui.concertstage.height': 'Hauteur', 'gui.concertstage.width': 'Largeur', 'gui.concertstage.value': '%s : %s blocs', 'gui.concertstage.position': 'Position du tissu : %s', 'gui.concertstage.pos.center': 'Centré', 'gui.concertstage.pos.north': 'Côté nord', 'gui.concertstage.pos.south': 'Côté sud', 'gui.concertstage.pos.east': 'Côté est', 'gui.concertstage.pos.west': 'Côté ouest', 'gui.concertstage.done': 'Terminé'}
for _k in TRUSSES:
    en[f'block.concertstage.{_k}_noir'] = en[f'block.concertstage.{_k}'] + ' (black)'
    fr[f'block.concertstage.{_k}_noir'] = fr[f'block.concertstage.{_k}'] + ' (noir)'
add_lang(en, fr)
wj(os.path.join(A, 'lang', 'en_us.json'), en)
wj(os.path.join(A, 'lang', 'fr_fr.json'), fr)
print('Converted Blockbench models:')
for r in report:
    print('  %-32s elements=%-4d dropped=%d clamped=%-3d bbox=%s..%s' % r)
print('Assets generated in', ROOT)
if __name__ == '__main__':
    from refine_truss_models import main as refine_rigging
    refine_rigging()

# New stages override only their own resources after the preserved rigging exports.
if __name__ == "__main__":
    from integrate_platforms import main as integrate_stages
    from finish_platform_assets import finish
    from improve_inventory import main as normalize_inventory
    integrate_stages()
    finish()
    normalize_inventory()

if __name__ == "__main__":
    from build_stage_connections import main as connections
    connections()
