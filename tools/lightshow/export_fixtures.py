#!/usr/bin/env python3
"""Export the four validated CSC 1.2.0 fixtures (.blend) to compact game meshes, without Blender.

NOTE (1.2.0 remodel): VORTEX X1, LUMORA W7, LUMORA W19 and PULSESTRIKE M are now rebuilt in Blender 5.3 like the 13 new
fixtures (source-assets/new_lights) and put in place by integrate_remodelled_fixtures.py. The default mode below reads the
former Blender 4.x files and would put the former models back: keep it for reference, use --new-lights for the current sources.

Usage: python3 -I export_fixtures.py <folder with the .blend files> <assets/concertstage folder>

What it does, for each fixture:
- reads every mesh object and its world transform (parent chain, parent inverse, Euler / quaternion rotation, scale);
- sorts the objects by the pivot empty they hang from: base (01), pan yoke (02), tilt head (03);
- expresses each part in the frame of its pivot, so the game can turn the yoke around Y (pan) and the head around its tilt axis;
- converts Blender (Z up, front -Y) to Minecraft (Y up, front north = -Z): mc = (-x, z, y);
- triangulates the faces (fan), keeps the material colour (Principled Base Color, linear -> sRGB) per triangle;
- emissive materials (LED cells, white strips, hybrid source) become numbered zones that the game lights at run time;
- a far LOD drops the small detail objects (bolts, labels, grilles) and keeps the silhouette and the emitters;
- writes assets/concertstage/lightshow/<id>.json plus a report.

The bevel modifiers of the files ("Congés") are not applied (they only round the edges); text objects are skipped.
The geometry itself is never redrawn or changed: only re-expressed in game coordinates.
"""
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from blend_reader import Blend  # noqa: E402

FIXTURES = [
    # id, file, kind, zone naming
    ('csc_vortex_x1', 'VORTEX_X1_R02_Finition.blend'),
    ('csc_lumora_w7', 'CSC_LUMORA_W7.blend'),
    ('csc_lumora_w19', 'CSC_LUMORA_W19.blend'),
    ('csc_pulsestrike_m', 'CSC_PULSESTRIKE_M.blend'),
]

CD_PROP_FLOAT3, CD_PROP_INT32, CD_PROP_FLOAT2 = 48, 11, 49
Q = 4096                 # positions are stored as ints in 1/4096 block
NEAR_GRID = 0.0025       # 2.5 mm
FAR_GRID = 0.014         # 14 mm


# ---------------------------------------------------------------- math
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_inv(m):
    # general 4x4 inverse (Gauss-Jordan)
    a = [row[:] + [1.0 if i == j else 0.0 for j in range(4)] for i, row in enumerate(m)]
    for c in range(4):
        p = max(range(c, 4), key=lambda r: abs(a[r][c]))
        a[c], a[p] = a[p], a[c]
        d = a[c][c]
        if abs(d) < 1e-12:
            raise ValueError('singular')
        a[c] = [x / d for x in a[c]]
        for r in range(4):
            if r != c:
                f = a[r][c]
                a[r] = [x - f * y for x, y in zip(a[r], a[c])]
    return [row[4:] for row in a]


def euler_xyz(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    # Blender XYZ euler: R = Rz * Ry * Rx
    return [[cy * cz, sx * sy * cz - cx * sz, cx * sy * cz + sx * sz],
            [cy * sz, sx * sy * sz + cx * cz, cx * sy * sz - sx * cz],
            [-sy, sx * cy, cx * cy]]


def euler_any(r, mode):
    rx, ry, rz = r
    Rx = [[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]]
    Ry = [[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]]
    Rz = [[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]]
    order = {1: 'XYZ', 2: 'XZY', 3: 'YXZ', 4: 'YZX', 5: 'ZXY', 6: 'ZYX'}[mode]
    mats = {'X': Rx, 'Y': Ry, 'Z': Rz}
    m = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    for ax in order:                     # first axis applied first: R = M_last * ... * M_first
        n = mats[ax]
        m = [[sum(n[i][k] * m[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return m


def quat_mat(q):
    w, x, y, z = q
    n = math.sqrt(w * w + x * x + y * y + z * z) or 1.0
    w, x, y, z = w / n, x / n, y / n, z / n
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]


def local_matrix(ob):
    loc = [a + b for a, b in zip(ob.get('loc'), ob.get('dloc'))]
    size = [a * b for a, b in zip(ob.get('size'), ob.get('dscale'))]
    mode = ob.get('rotmode')
    if mode == 0:
        r = quat_mat(ob.get('quat'))
    elif mode in (1, 2, 3, 4, 5, 6):
        rot = [a + b for a, b in zip(ob.get('rot'), ob.get('drot'))]
        r = euler_any(rot, mode)
    else:                                 # axis angle
        ax = ob.get('rotAxis')
        ang = ob.get('rotAngle')
        s = math.sin(ang / 2)
        r = quat_mat([math.cos(ang / 2), ax[0] * s, ax[1] * s, ax[2] * s])
    return [[r[0][0] * size[0], r[0][1] * size[1], r[0][2] * size[2], loc[0]],
            [r[1][0] * size[0], r[1][1] * size[1], r[1][2] * size[2], loc[1]],
            [r[2][0] * size[0], r[2][1] * size[1], r[2][2] * size[2], loc[2]],
            [0, 0, 0, 1]]


def flat4(m16):
    # DNA float[4][4] is column-major in memory: m[col][row]
    return [[m16[c * 4 + r] for c in range(4)] for r in range(4)]


def apply(m, p):
    return [m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2] + m[0][3],
            m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2] + m[1][3],
            m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2] + m[2][3]]


def to_mc(p):
    return [-p[0], p[2], p[1]]


def srgb(c):
    c = max(0.0, min(1.0, c))
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


# ---------------------------------------------------------------- blend access
def world_matrices(b):
    obs = {o.off: o for o in b.ids(b'OB')}
    by_ptr = {}
    for blk in b.blocks:
        if blk.code == b'OB\0\0':
            by_ptr[blk.old] = blk.offset
    cache = {}

    def world(o):
        if o.off in cache:
            return cache[o.off]
        m = local_matrix(o)
        par = o.get('parent')
        if par and par in by_ptr:
            p = obs[by_ptr[par]]
            pi = flat4(o.get('parentinv'))
            m = mat_mul(mat_mul(world(p), pi), m)
        cache[o.off] = m
        return m
    return obs, by_ptr, world


def material_info(b, ma):
    """(rgb sRGB 0..1, emissive?, emission rgb, transmission)"""
    base = [ma.get('r'), ma.get('g'), ma.get('b')]
    emission = None
    transmission = 0.0
    nt = b.deref(ma.get('nodetree'), 'bNodeTree')
    if nt:
        for n in b.listbase(nt.get('nodes'), 'bNode'):
            if 'Principled' not in n.get('idname'):
                continue
            for s in b.listbase(n.get('inputs'), 'bNodeSocket'):
                ident = s.get('identifier')
                if ident == 'Base Color':
                    v = b.deref(s.get('default_value'), 'bNodeSocketValueRGBA')
                    if v:
                        base = v.get('value')[:3]
                elif ident == 'Emission Color':
                    v = b.deref(s.get('default_value'), 'bNodeSocketValueRGBA')
                    if v:
                        emission = v.get('value')[:3]
                elif ident == 'Transmission Weight':
                    v = b.deref(s.get('default_value'), 'bNodeSocketValueFloat')
                    if v:
                        transmission = v.get('value')
    emissive = emission is not None and not all(abs(c - 1.0) < 1e-4 for c in emission)
    return [srgb(c) for c in base], emissive, emission, transmission


def mesh_layers(b, cd):
    out = {}
    blk = b.block_of(cd.get('layers'))
    if blk is None:
        return out
    sz = b.struct_size('CustomDataLayer')
    for i in range(cd.get('totlayer')):
        L = b.view(blk.offset + i * sz, 'CustomDataLayer')
        out[L.get('name')] = (L.get('type'), L.get('data'))
    return out


def read_mesh(b, me):
    import struct
    nv, npoly, nl = me.get('totvert'), me.get('totpoly'), me.get('totloop')
    vl = mesh_layers(b, me.get('vdata'))
    ll = mesh_layers(b, me.get('ldata'))
    pl = mesh_layers(b, me.get('pdata'))
    pos = struct.unpack_from('<%df' % (3 * nv), b.raw(vl['position'][1]))
    corner = struct.unpack_from('<%di' % nl, b.raw(ll['.corner_vert'][1]))
    offs_raw = b.raw(me.get('poly_offset_indices'))
    offs = struct.unpack_from('<%di' % (npoly + 1), offs_raw) if offs_raw else None
    if offs is None:
        raise ValueError('no face offsets')
    mat_idx = None
    if 'material_index' in pl:
        mat_idx = struct.unpack_from('<%di' % npoly, b.raw(pl['material_index'][1]))
    verts = [pos[i * 3:i * 3 + 3] for i in range(nv)]
    faces = [[corner[k] for k in range(offs[f], offs[f + 1])] for f in range(npoly)]
    return verts, faces, mat_idx


def object_materials(b, ob, me):
    """Material views per slot (object slots override mesh slots when matbits says so)."""
    n = max(ob.get('totcol') or 0, me.get('totcol') or 0)
    me_mats = []
    if me.get('mat'):
        raw = b.raw(me.get('mat'))
        import struct
        me_mats = list(struct.unpack_from('<%dQ' % me.get('totcol'), raw)) if raw else []
    ob_mats = []
    if ob.get('mat'):
        raw = b.raw(ob.get('mat'))
        import struct
        ob_mats = list(struct.unpack_from('<%dQ' % ob.get('totcol'), raw)) if raw else []
    bits = b.raw(ob.get('matbits')) or b''
    out = []
    for i in range(n):
        use_ob = i < len(bits) and bits[i] and i < len(ob_mats) and ob_mats[i]
        ptr = ob_mats[i] if use_ob else (me_mats[i] if i < len(me_mats) else 0)
        out.append(b.deref(ptr, 'Material') if ptr else None)
    return out


ZONE_ORDER = re.compile(r'(\d+)(?:\.(\d+))?')


def zone_key(name):
    """Sort key of an emissive material: LED RGB zone N first, then Blanc A.B, then Cellule NN, then the central cell."""
    n = name.lower()
    nums = [int(x) for x in re.findall(r'\d+', n)]
    if 'rgb' in n:
        return (0, nums)
    if 'blanc' in n:
        return (1, nums)
    if 'centrale' in n:
        return (3, nums)
    return (2, nums)


def export(blend_path, fid):
    b = Blend(blend_path)
    obs, by_ptr, world = world_matrices(b)
    pivots = {}
    for o in obs.values():
        nm = o.name()
        if o.get('type') == 0 and re.match(r'^0[123] ', nm):
            pivots[nm[:2]] = o
    base_pivot = pivots['01']
    pan_pivot = pivots.get('02')
    tilt_pivot = pivots['03']

    def part_of(o):
        p = o
        while p is not None:
            if pan_pivot is not None and p.off == pan_pivot.off:
                return 'yoke'
            if p.off == tilt_pivot.off:
                return 'head'
            par = p.get('parent')
            p = obs[by_ptr[par]] if par and par in by_ptr else None
        return 'base'

    # pivot frames: the delivery pose may have pan / tilt turned (VORTEX R02); the parts are expressed in the pivot frame,
    # i.e. in the neutral pose (pan 0, tilt 0)
    frames = {'base': world(base_pivot), 'head': world(tilt_pivot)}
    if pan_pivot is not None:
        frames['yoke'] = world(pan_pivot)
    inv = {k: mat_inv(v) for k, v in frames.items()}

    materials = {}
    zones = {}            # material name -> zone index (assigned after sorting)
    emissive_names = set()
    parts = {k: {'tris': [], 'objs': []} for k in frames}
    skipped = []
    for o in obs.values():
        if o.get('type') != 1:
            continue
        me = b.deref(o.get('data'), 'Mesh')
        if me is None:
            continue
        # hidden objects (render hidden) are not part of the fixture
        if (o.get('restrictflag') or 0) & 4:
            skipped.append(o.name())
            continue
        part = part_of(o)
        m = mat_mul(inv[part], world(o))
        try:
            verts, faces, mat_idx = read_mesh(b, me)
        except Exception as e:  # noqa
            skipped.append(o.name() + ' (' + str(e) + ')')
            continue
        slots = object_materials(b, o, me)
        wv = [to_mc(apply(m, v)) for v in verts]
        lo = [min(v[i] for v in wv) for i in range(3)] if wv else [0, 0, 0]
        hi = [max(v[i] for v in wv) for i in range(3)] if wv else [0, 0, 0]
        diag = math.dist(lo, hi)
        obj_tris = []
        for fi, f in enumerate(faces):
            if len(f) < 3:
                continue
            si = mat_idx[fi] if mat_idx else 0
            ma = slots[si] if 0 <= si < len(slots) else None
            mname = ma.name() if ma else '?'
            if mname not in materials:
                materials[mname] = material_info(b, ma) if ma else ([0.5, 0.5, 0.5], False, None, 0.0)
            col, emissive, emission, trans = materials[mname]
            if emissive:
                emissive_names.add(mname)
            for k in range(1, len(f) - 1):
                obj_tris.append((wv[f[0]], wv[f[k]], wv[f[k + 1]], mname))
        parts[part]['objs'].append({'name': o.name(), 'diag': diag, 'tris': obj_tris})

    ordered = sorted(emissive_names, key=zone_key)
    for i, n in enumerate(ordered):
        zones[n] = i

    # beam axis and lens: emissive triangles of the head, in head frame
    em_pts, em_normals, area_total = [], [0.0, 0.0, 0.0], 0.0
    for ob in parts['head']['objs']:
        for a, c, d, mn in ob['tris']:
            if mn in zones:
                u = [c[i] - a[i] for i in range(3)]
                v = [d[i] - a[i] for i in range(3)]
                n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
                ar = math.sqrt(sum(x * x for x in n)) / 2
                cen = [(a[i] + c[i] + d[i]) / 3 for i in range(3)]
                em_pts.append((cen, ar))
                for i in range(3):
                    em_normals[i] += n[i] / 2
                area_total += ar
    centre = [sum(p[0][i] * p[1] for p in em_pts) / max(area_total, 1e-9) for i in range(3)]
    # neutral pose: the optics look along the head axis through the emitter centre (the summed normals are biased by
    # curved emitters, e.g. the VORTEX hybrid source)
    nl = math.sqrt(sum(x * x for x in centre))
    axis = [x / nl for x in centre] if nl > 1e-3 else [0.0, 0.0, -1.0]
    radius = max((math.dist(p[0], centre) for p in em_pts), default=0.02)

    # zone rectangles (head frame): the emitters face north (-Z); x / y extent and front-most z of each zone
    zone_box = {}
    for ob in parts['head']['objs']:
        for a, c, d, mn in ob['tris']:
            if mn in zones:
                z = zones[mn]
                bx = zone_box.setdefault(z, [1e9, 1e9, 1e9, -1e9, -1e9, -1e9])
                for p in (a, c, d):
                    for i in range(3):
                        bx[i] = min(bx[i], p[i]); bx[3 + i] = max(bx[3 + i], p[i])

    def cluster(tris, grid):
        """Vertex clustering: snaps to a grid of `grid` blocks, drops the triangles that collapse and the duplicates.
        Returns quantised ints (1/4096 block)."""
        out = []
        seen = set()
        for a, c, d in tris:
            q = tuple(tuple(int(round(round(x / grid) * grid * Q)) for x in p) for p in (a, c, d))
            if q[0] == q[1] or q[1] == q[2] or q[0] == q[2]:
                continue
            key = tuple(sorted(q))
            if key in seen:
                continue
            seen.add(key)
            for p in q:
                out.extend(p)
        return out

    def pack(objs, min_diag, grid):
        """Static triangles grouped by colour + emissive triangles grouped by zone, simplified on a grid."""
        static = {}
        emit = {}
        for ob in objs:
            if ob['diag'] < min_diag and not any(t[3] in zones for t in ob['tris']):
                continue
            for a, c, d, mn in ob['tris']:
                col = materials[mn][0]
                # emitters stay in the static mesh with their (dark) lens colour: the game lights them with a glow overlay
                key = '%02x%02x%02x' % tuple(int(round(min(1.0, x * 1.25 + 0.03) * 255)) for x in col)
                static.setdefault(key, []).append((a, c, d))
        return {'static': {k: cluster(v, grid) for k, v in static.items()}}

    out = {'id': fid, 'quant': Q, 'source': os.path.basename(blend_path), 'blender_version': b.version, 'units': 'blocks (1 m = 1 block)',
           'front': 'north (-Z)', 'parts': {}, 'lod_far': {}, 'pivots': {}, 'zones': {}, 'beam': {}}
    for k in parts:
        out['parts'][k] = pack(parts[k]['objs'], 0.0, NEAR_GRID)
        out['lod_far'][k] = pack(parts[k]['objs'], 0.045, FAR_GRID)
        out.setdefault('lod_icon', {})[k] = pack(parts[k]['objs'], 0.07, 0.022)
    # pivot positions in the frame of their parent part (the game turns the yoke about Y at that point, the head about X)
    if pan_pivot is not None:
        out['pivots']['pan'] = [round(x, 5) for x in to_mc(apply(mat_mul(inv['base'], frames['yoke']), [0, 0, 0]))]
        out['pivots']['tilt'] = [round(x, 5) for x in to_mc(apply(mat_mul(inv['yoke'], frames['head']), [0, 0, 0]))]
    else:
        out['pivots']['pan'] = None
        out['pivots']['tilt'] = [round(x, 5) for x in to_mc(apply(mat_mul(inv['base'], frames['head']), [0, 0, 0]))]
    def zone_entry(n):
        bx = zone_box.get(zones[n])
        e = {'name': n, 'kind': 'rgb' if 'rgb' in n.lower() else 'white' if 'blanc' in n.lower() else 'cell',
             'tint': [round(x, 3) for x in (materials[n][2] or [1, 1, 1])]}
        if bx:
            e['centre'] = [round((bx[0] + bx[3]) / 2, 4), round((bx[1] + bx[4]) / 2, 4), round(bx[2] - 0.0006, 4)]
            e['half'] = [round((bx[3] - bx[0]) / 2, 4), round((bx[4] - bx[1]) / 2, 4)]
        return e
    out['zones'] = {str(zones[n]): zone_entry(n) for n in ordered}
    out['beam'] = {'origin': [round(x, 4) for x in centre], 'axis': [round(x, 4) for x in axis], 'lens_radius': round(radius, 4)}

    def count(pk):
        return sum(len(v) for v in pk['static'].values()) // 9
    rep = {'objects': {k: len(parts[k]['objs']) for k in parts}, 'triangles': {k: count(out['parts'][k]) for k in parts},
           'triangles_far': {k: count(out['lod_far'][k]) for k in parts}, 'zones': len(zones), 'skipped': skipped,
           'beam': out['beam'], 'pivots': out['pivots']}
    allpts = [[x / Q for x in p] for k in out['parts'] for v in out['parts'][k]['static'].values()
              for p in [v[i:i + 3] for i in range(0, len(v), 3)]]
    rep['bounds_neutral_parts'] = [[round(min(p[i] for p in allpts), 3) for i in range(3)], [round(max(p[i] for p in allpts), 3) for i in range(3)]]
    return out, rep


# ---------------------------------------------------------------- palette atlas and inventory models
PALETTE_SIZE = 64          # texture px; 16 x 16 cells of 4 px
CELLS = 16


def palette_uv(index):
    cx, cy = index % CELLS, index // CELLS
    return ((cx + 0.5) / CELLS, 1.0 - (cy + 0.5) / CELLS)       # OBJ v goes up (flip_v in the model json)


def write_palette(colors, path):
    from PIL import Image
    img = Image.new('RGBA', (PALETTE_SIZE, PALETTE_SIZE), (255, 255, 255, 255))
    cell = PALETTE_SIZE // CELLS
    for i, c in enumerate(colors):
        cx, cy = i % CELLS, i // CELLS
        rgb = tuple(int(c[j:j + 2], 16) for j in (0, 2, 4)) + (255,)
        for x in range(cell):
            for y in range(cell):
                img.putpixel((cx * cell + x, cy * cell + y), rgb)
    img.save(path)


def write_obj(data, colors, path, pan=0.0, tilt=0.0, part_key='lod_far'):
    """Inventory model: the far LOD in the neutral pose, centred on the block, one palette cell per colour."""
    Q = data['quant']
    lines = ['mtllib lightshow.mtl', 'usemtl palette']
    vi = 0
    tilt_p = data['pivots']['tilt']
    for part in ('base', 'yoke', 'head'):
        if part not in data[part_key]:
            continue
        for col, arr in data[part_key][part]['static'].items():
            u, v = palette_uv(colors.index(col))
            for t in range(0, len(arr), 9):
                pts = []
                for k in range(3):
                    x, y, z = arr[t + 3 * k] / Q, arr[t + 3 * k + 1] / Q, arr[t + 3 * k + 2] / Q
                    if part == 'head':
                        x, y, z = x + tilt_p[0], y + tilt_p[1], z + tilt_p[2]
                    pts.append((x + 0.5, y, z + 0.5))
                ax, ay, az = pts[1][0] - pts[0][0], pts[1][1] - pts[0][1], pts[1][2] - pts[0][2]
                bx, by, bz = pts[2][0] - pts[0][0], pts[2][1] - pts[0][1], pts[2][2] - pts[0][2]
                nx, ny, nz = ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx
                nl = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
                for p in pts:
                    lines.append('v %.4f %.4f %.4f' % p)
                    lines.append('vt %.5f %.5f' % (u, v))
                    lines.append('vn %.4f %.4f %.4f' % (nx / nl, ny / nl, nz / nl))
                lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d' % (vi + 1, vi + 1, vi + 1, vi + 2, vi + 2, vi + 2, vi + 3, vi + 3, vi + 3))
                vi += 3
    with open(path, 'w') as f:
        f.write('\n'.join(lines) + '\n')


def main():
    if len(sys.argv) > 1 and sys.argv[1] == '--new-lights':
        import subprocess
        if len(sys.argv) != 5:
            raise SystemExit('export_fixtures.py --new-lights <blender executable> <blend folder> <assets folder>')
        backend = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'export_new_lights_blender.py')
        subprocess.run([sys.argv[2], '-b', '-t', '2', '--python', backend, '--', sys.argv[3], sys.argv[4]], check=True)
        return
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.join(dst, 'lightshow'), exist_ok=True)
    report = {}
    exported = {}
    for fid, fname in FIXTURES:
        data, rep = export(os.path.join(src, fname), fid)
        exported[fid] = (data, rep)
    # one palette for the four fixtures (+ a few console colours appended by make_lumicore.py)
    colors = []
    for fid, (data, rep) in exported.items():
        for key in ('parts', 'lod_far', 'lod_icon'):
            for part in data[key].values():
                for col in part['static']:
                    if col not in colors:
                        colors.append(col)
    if 'ffffff' not in colors:
        colors.insert(0, 'ffffff')
    else:
        colors.remove('ffffff'); colors.insert(0, 'ffffff')
    assert len(colors) <= CELLS * CELLS - 32, len(colors)
    for fid, (data, rep) in exported.items():
        path = os.path.join(dst, 'lightshow', fid + '.json')
        icon = data.pop('lod_icon')
        data['lod_low'] = icon                     # also the lowest level of detail in game (far away, or no GPU cache)
        data['palette_white_uv'] = [0.5 / CELLS, 0.5 / CELLS]
        with open(path, 'w') as f:
            json.dump(data, f, separators=(',', ':'))
        rep['bytes'] = os.path.getsize(path)
        report[fid] = rep
        os.makedirs(os.path.join(dst, 'models', 'lightshow'), exist_ok=True)
        write_obj(data, colors, os.path.join(dst, 'models', 'lightshow', fid + '.obj'), part_key='lod_low')
        print(fid, json.dumps({k: rep[k] for k in ('triangles', 'triangles_far', 'zones', 'bytes')}))
    with open(os.path.join(dst, 'models', 'lightshow', 'lightshow.mtl'), 'w') as f:
        f.write('newmtl palette\nKd 1 1 1\nmap_Kd #palette\n')
    os.makedirs(os.path.join(dst, 'textures', 'block'), exist_ok=True)
    with open(os.path.join(dst, 'lightshow', 'palette.json'), 'w') as f:
        json.dump({'cells': CELLS, 'colors': colors}, f)
    write_palette(colors, os.path.join(dst, 'textures', 'block', 'lightshow_palette.png'))
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'export_report.json'), 'w') as f:
        json.dump(report, f, indent=1, ensure_ascii=False)


if __name__ == '__main__':
    main()
