"""CSC FlexDeck (1.18.0) : plateformes de plusieurs tailles qui se joignent les unes aux autres, y compris en diagonale.

Rectangles 1 × 1, 2 × 1, 2 × 2, 1 × 0,5 m et triangles rectangles 1 × 1 et 2 × 2 m (côté long à 45°), à 40 cm et à 1 m (le plancher
d'1 m est pile sur un bloc : tout bloc s'y pose à ras). Plateau antidérapant, cadre alu, pieds alu à chaque angle (et au milieu des
grands côtés). Le contour (`outline`) sert à l'accrochage bord à bord des plateformes posées en biais (StagePlatformJoin) et à la
collision des plateformes tournées.

Génère : stage_equipment.json (spec), stage_meshes/<id>.json (pièce posée en biais), modèles OBJ par case + objet, blockstate,
modèles de bloc, modèle d'objet, langues, recette, loot table, tags. Relancer remplace les FlexDeck existants."""
import json, math, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / 'common/src/main/resources'
A = RES / 'assets/concertstage'
D = RES / 'data/concertstage'

# matériaux : (slot de stage_meshes / material_N de l'OBJ, région uv dans la texture, origine haut-gauche)
ALU = (0, (0.004, 0.004, 0.496, 0.496))
BLACK = (0, (0.004, 0.504, 0.496, 0.996))
TOP = (1, (0.004, 0.004, 0.996, 0.996))
UV_PER_M = 0.245

def rect(w, d): return [(0.0, 0.0), (w, 0.0), (w, d), (0.0, d)]
def tri(l): return [(0.0, 0.0), (l, 0.0), (0.0, l)]          # côté long de (l,0) à (0,l) : 45°

SIZES = [  # (suffixe, contour, largeur, profondeur, nom FR, nom EN)
    ('1x1', rect(1, 1), 1, 1, '1 × 1 m', '1 × 1 m'),
    ('2x1', rect(2, 1), 2, 1, '2 × 1 m', '2 × 1 m'),
    ('2x2', rect(2, 2), 2, 2, '2 × 2 m', '2 × 2 m'),
    ('1x0p5', rect(1, 0.5), 1, 0.5, '1 × 0,5 m', '1 × 0.5 m'),
    ('tri_1x1', tri(1), 1, 1, 'triangle 1 × 1 m', 'triangle 1 × 1 m'),
    ('tri_2x2', tri(2), 2, 2, 'triangle 2 × 2 m', 'triangle 2 × 2 m'),
]
HEIGHTS = [(0.4, 'h040', '40 cm', '40 cm'), (1.0, 'h100', '1 m', '1 m')]
GUI = {'1x1': ([-0.0, 2.40747, 1.25093], 0.6081118), '2x1': ([2.29333, 2.75165, -1.15213], 0.4054079),
       '2x2': ([3.44, 1.20374, 0.62546], 0.3040559), '1x0p5': ([-0.0, 2.40747, 1.25093], 0.6081118),
       'tri_1x1': ([-0.0, 2.40747, 1.25093], 0.6081118), 'tri_2x2': ([3.44, 1.20374, 0.62546], 0.3040559)}

def centroid(P): return (sum(p[0] for p in P) / len(P), sum(p[1] for p in P) / len(P))

def inset(P, d):
    """Contour rentré de d (polygone convexe) : chaque côté décalé vers l'intérieur, sommets aux intersections."""
    n = len(P); lines = []
    c = centroid(P)
    for i in range(n):
        a, b = P[i], P[(i + 1) % n]
        ex, ez = b[0] - a[0], b[1] - a[1]; L = math.hypot(ex, ez); ex /= L; ez /= L
        nx, nz = -ez, ex
        if nx * (c[0] - a[0]) + nz * (c[1] - a[1]) < 0: nx, nz = -nx, -nz
        lines.append(((a[0] + nx * d, a[1] + nz * d), (ex, ez)))
    out = []
    for i in range(n):
        (p1, d1), (p2, d2) = lines[i - 1], lines[i]
        den = d1[0] * d2[1] - d1[1] * d2[0]
        t = ((p2[0] - p1[0]) * d2[1] - (p2[1] - p1[1]) * d2[0]) / den
        out.append((p1[0] + d1[0] * t, p1[1] + d1[1] * t))
    return out

def ccw(P):
    s = sum(P[i][0] * P[(i + 1) % len(P)][1] - P[(i + 1) % len(P)][0] * P[i][1] for i in range(len(P)))
    return P if s > 0 else P[::-1]

def uvmap(mat, a, b):
    slot, (u0, v0, u1, v1) = mat
    return (min(u1, max(u0, u0 + a * UV_PER_M)), min(v1, max(v0, v0 + b * UV_PER_M)))

def prism(P, y0, y1, side, top=None, bottom=None):
    """Prisme droit sur un polygone convexe (x, z) : faces (slot, [(x,y,z,u,v)], normale)."""
    top = top or side; bottom = bottom or side
    P = ccw(P); n = len(P); faces = []
    # dessus (normale +y) : avec x à droite et z vers l'avant, ccw en (x,z) donne une normale -y ; on inverse pour le dessus
    up = [(p[0], y1, p[1]) for p in P[::-1]]
    faces.append((top, up, (0, 1, 0)))
    faces.append((bottom, [(p[0], y0, p[1]) for p in P], (0, -1, 0)))
    for i in range(n):
        a, b = P[i], P[(i + 1) % n]
        ex, ez = b[0] - a[0], b[1] - a[1]; L = math.hypot(ex, ez)
        nx, nz = ez / L, -ex / L                                   # extérieur pour un contour ccw en (x,z)
        faces.append((side, [(a[0], y0, a[1]), (b[0], y0, b[1]), (b[0], y1, b[1]), (a[0], y1, a[1])], (nx, 0, nz)))
    return faces

def fix_winding(pts, n):
    """(v1-v0)×(v2-v1) doit suivre la normale (face visible de l'extérieur)."""
    if len(pts) < 3: return pts
    a, b, c = pts[0], pts[1], pts[2]
    u = (b[0] - a[0], b[1] - a[1], b[2] - a[2]); v = (c[0] - b[0], c[1] - b[1], c[2] - b[2])
    cr = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
    return pts if cr[0] * n[0] + cr[1] * n[1] + cr[2] * n[2] >= 0 else pts[::-1]

def with_uv(mat, pts, n):
    out = []
    for (x, y, z) in pts:
        if abs(n[1]) > 0.5: a, b = x, z
        else:
            # le long du côté : projection horizontale sur la direction du côté
            a, b = (x * -n[2] + z * n[0]), -y
        out.append((x, y, z) + uvmap(mat, a, b))
    # décale pour que chaque face parte du coin de la région (pas de débord)
    slot, (u0, v0, u1, v1) = mat
    mu = min(p[3] for p in out); mv = min(p[4] for p in out)
    return [(p[0], p[1], p[2], min(u1, p[3] - mu + u0), min(v1, p[4] - mv + v0)) for p in out]

def geometry(P, H):
    faces = []
    plate = 0.022
    faces += prism(P, H - plate, H, BLACK, top=TOP, bottom=BLACK)                   # plateau antidérapant, chant noir
    rail_in = inset(P, 0.045)
    for i in range(len(P)):                                                          # cadre alu sous le plateau
        a, b, c, d = P[i], P[(i + 1) % len(P)], rail_in[(i + 1) % len(P)], rail_in[i]
        faces += prism([a, b, c, d], H - plate - 0.07, H - plate, ALU)
    legs = []
    li = inset(P, 0.075)
    for i, p in enumerate(li):
        legs.append(p)
        q = li[(i + 1) % len(li)]
        if math.hypot(q[0] - p[0], q[1] - p[1]) > 1.6: legs.append(((p[0] + q[0]) / 2, (p[1] + q[1]) / 2))
    if len(P) == 4 and (P[2][0] - P[0][0]) >= 2 and (P[2][1] - P[0][1]) >= 2: legs.append(centroid(P))
    for (x, z) in legs:
        s = 0.025
        faces += prism(rect_at(x, z, s), 0.012, H - plate - 0.07, ALU)              # pied tube 50 × 50
        faces += prism(rect_at(x, z, 0.055), 0.0, 0.012, BLACK)                    # semelle
    if H >= 0.8:                                                                     # lisses basses entre pieds (1 m)
        lb = inset(P, 0.085)
        for i in range(len(lb)):
            a, b = lb[i], lb[(i + 1) % len(lb)]
            ex, ez = b[0] - a[0], b[1] - a[1]; L = math.hypot(ex, ez); nx, nz = -ez / L * 0.012, ex / L * 0.012
            faces += prism([(a[0] - nx, a[1] - nz), (b[0] - nx, b[1] - nz), (b[0] + nx, b[1] + nz), (a[0] + nx, a[1] + nz)], 0.16, 0.19, ALU)
    out = []
    for mat, pts, n in faces:
        pts = fix_winding(pts, n)
        out.append((mat[0], with_uv(mat, pts, n), n))
    return out, legs

def rect_at(x, z, s): return [(x - s, z - s), (x + s, z - s), (x + s, z + s), (x - s, z + s)]

def collision(P, H, legs, W, Dp):
    """Boîtes : plateau (rectangle entier ou bandes de 1/16 m sous le côté long des triangles) et pieds."""
    boxes = []
    top0 = H - 0.1
    if len(P) == 4:
        boxes.append([0.0, round(top0, 4), 0.0, W, H, Dp])
    else:
        l = P[1][0]; n = int(round(l * 16))
        for i in range(n):
            x0, x1 = i / 16, (i + 1) / 16; zmax = l - (i + 0.5) / 16
            if zmax > 0.005: boxes.append([round(x0, 4), round(top0, 4), 0.0, round(x1, 4), H, round(zmax, 4)])
    for (x, z) in legs:
        boxes.append([round(x - 0.03, 4), 0.0, round(z - 0.03, 4), round(x + 0.03, 4), round(top0, 4), round(z + 0.03, 4)])
    return boxes

def clip_poly(pts, axis, value, above):
    out = []
    if not pts: return out
    prev = pts[-1]; was = prev[axis] >= value if above else prev[axis] <= value
    for p in pts:
        inside = p[axis] >= value if above else p[axis] <= value
        if inside != was:
            t = (value - prev[axis]) / (p[axis] - prev[axis]); out.append(tuple(prev[i] + t * (p[i] - prev[i]) for i in range(5)))
        if inside: out.append(p)
        prev, was = p, inside
    return out

def obj(faces, offset=None, box=None):
    """OBJ (1 unité = 1 bloc) ; avec box, faces découpées à la case et ramenées dans son repère."""
    V, T, N, F = [], [], [], []
    for slot in (0, 1, 2):
        rows = []
        for s, pts, n in faces:
            if s != slot: continue
            p = list(pts)
            if box:
                for ax in range(3):
                    p = clip_poly(p, ax, box[ax], True); p = clip_poly(p, ax, box[ax + 3], False)
                if len(p) < 3: continue
            o = offset or (0, 0, 0)
            p = [(q[0] - o[0], q[1] - o[1], q[2] - o[2], q[3], q[4]) for q in p]
            polys = [p] if len(p) <= 4 else [[p[0], p[i], p[i + 1]] for i in range(1, len(p) - 1)]
            for poly in polys:
                if len(poly) == 3: poly = poly + [poly[2]]
                rows.append((poly, n))
        if not rows: continue
        F.append(f'usemtl material_{slot}')
        for poly, n in rows:
            N.append(n); ni = len(N); idx = []
            for q in poly:
                V.append(q[:3]); T.append((q[3], 1 - q[4])); idx.append(f'{len(V)}/{len(T)}/{ni}')
            F.append('f ' + ' '.join(idx))
    if not F: return None
    lines = ['# 1 unit = 1 Minecraft block', 'mtllib stage_materials.mtl']
    lines += [f'v {v[0]:.6g} {v[1]:.6g} {v[2]:.6g}' for v in V]
    lines += [f'vt {t[0]:.7g} {t[1]:.7g}' for t in T]
    lines += [f'vn {n[0]:.6g} {n[1]:.6g} {n[2]:.6g}' for n in N]
    return '\n'.join(lines + F) + '\n'

def mesh_json(faces):
    out = {}
    for slot, pts, n in faces:
        polys = [pts] if len(pts) <= 4 else [[pts[0], pts[i], pts[i + 1]] for i in range(1, len(pts) - 1)]
        for poly in polys:
            if len(poly) == 3: poly = poly + [poly[2]]
            q = []
            for p in poly: q += [round(p[0], 5), round(p[1], 5), round(p[2], 5), round(p[3], 6), round(p[4], 6), n[0], n[1], n[2]]
            out.setdefault(str(slot), []).append(q)
    return out

def clip_box(raw, lx, ly, lz):
    out = []
    for b in raw:
        c = [max(b[0], lx), max(b[1], ly), max(b[2], lz), min(b[3], lx + 1), min(b[4], ly + 1), min(b[5], lz + 1)]
        if c[0] < c[3] and c[1] < c[4] and c[2] < c[5]:
            out.append([round(c[0] - lx, 7), round(c[1] - ly, 7), round(c[2] - lz, 7), round(c[3] - lx, 7), round(c[4] - ly, 7), round(c[5] - lz, 7)])
    return out

TEX = {"metals": "concertstage:block/stage/metals", "anti_slip": "concertstage:block/stage/anti_slip", "hardware": "concertstage:block/stage/hardware", "particle": "concertstage:block/stage/metals"}

def build():
    specs = json.loads((D / 'stage_equipment.json').read_text())
    specs['equipment'] = [s for s in specs['equipment'] if not s['id'].startswith('flexdeck_')]
    lang = {lg: json.loads((A / 'lang' / f'{lg}.json').read_text()) for lg in ('fr_fr', 'en_us')}
    for lg in lang:
        for k in [k for k in lang[lg] if 'flexdeck_' in k]: del lang[lg][k]
    ids = []
    for H, hk, hfr, hen in HEIGHTS:
        for key, P, W, Dp, nfr, nen in SIZES:
            i = f'flexdeck_{key}_{hk}'; ids.append(i)
            faces, legs = geometry(P, H)
            raw = collision(P, H, legs, W, Dp)
            sx, sy, sz = math.ceil(W - 1e-9), math.ceil(H - 1e-9), math.ceil(Dp - 1e-9)
            cells, occ = [], []
            for p in range(sx * sy * sz):
                lx, lz, ly = p % sx, (p // sx) % sz, p // (sx * sz)
                c = clip_box(raw, lx, ly, lz); cells.append([c]); occ.append(bool(c))
            t, s = GUI[key]; s = s * (0.8 if H > 0.5 else 1.0)
            spec = {'id': i, 'grid': [sx, sy, sz], 'width': float(W), 'depth': float(Dp), 'walk': H, 'mountable': len(P) == 4, 'kind': 'platform',
                    'mode': '', 'occupied': occ, 'cells': cells, 'raw_boxes': raw, 'raw_bounds': [0.0, 0.0, 0.0, float(W), H, float(Dp)],
                    'gui': {'rotation': [30, 225, 0], 'translation': t, 'scale': [s, s, s]},
                    'outline': [[round(a, 4), round(b, 4)] for a, b in P], 'generated': 'make_flexdecks', 'feet': [[round(x, 4), 0.0, round(z, 4)] for x, z in legs]}
            specs['equipment'].append(spec)
            (A / 'stage_meshes' / f'{i}.json').write_text(json.dumps(mesh_json(faces), separators=(',', ':')))
            variants = {}
            for f, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                for p in range(sx * sy * sz):
                    lx, lz, ly = p % sx, (p // sx) % sz, p // (sx * sz)
                    o = obj(faces, (lx, ly, lz), (lx, ly, lz, lx + 1, ly + 1, lz + 1)) if occ[p] or True else None
                    name = f'{i}_{p}_0'
                    if o is None:
                        m = {'model': 'concertstage:block/stage/empty'}
                    else:
                        if rot == 0:
                            (A / 'models/stage/world' / f'{name}.obj').write_text(o)
                            (A / 'models/block/stage' / f'{name}.json').write_text(json.dumps({'loader': 'forge:obj', 'model': f'concertstage:models/stage/world/{name}.obj', 'flip_v': True, 'automatic_culling': False, 'shade_quads': True, 'textures': TEX, 'elements': [], 'render_type': 'minecraft:cutout'}, separators=(',', ':')))
                        m = {'model': f'concertstage:block/stage/{name}'}
                        if rot: m['y'] = rot
                    variants[f'facing={f},part={p}'] = m
            (A / 'blockstates' / f'{i}.json').write_text(json.dumps({'variants': variants}, separators=(',', ':')))
            (A / 'models/stage/items' / f'{i}.obj').write_text(obj(faces))
            disp = {'rotation': [30, 225, 0], 'translation': t, 'scale': [s, s, s]}
            sc = lambda f: {'rotation': [30, 225, 0], 'translation': t, 'scale': [s * f] * 3}
            (A / 'models/item' / f'{i}.json').write_text(json.dumps({'loader': 'forge:obj', 'model': f'concertstage:models/stage/items/{i}.obj', 'flip_v': True, 'automatic_culling': False, 'shade_quads': True, 'textures': TEX, 'elements': [], 'render_type': 'minecraft:cutout',
                'display': {'gui': disp, 'fixed': disp, 'ground': sc(0.65), 'firstperson_righthand': sc(0.9), 'firstperson_lefthand': sc(0.9), 'thirdperson_righthand': sc(0.7), 'thirdperson_lefthand': sc(0.7)}, 'gui_light': 'side'}, separators=(',', ':')))
            (D / 'loot_tables/blocks' / f'{i}.json').write_text(json.dumps({'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'concertstage:{i}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}))
            slabs, metal = {'1x1': (1, 'ingot2'), '2x1': (2, 'ingot2'), '2x2': (4, 'ingot2'), '1x0p5': (1, 'nugget2'), 'tri_1x1': (1, 'ingot1'), 'tri_2x2': (2, 'ingot1')}[key]
            ing = [{'item': 'minecraft:smooth_stone_slab'}] * slabs + [{'item': 'minecraft:iron_nugget' if metal.startswith('nugget') else 'minecraft:iron_ingot'}] * int(metal[-1]) + [{'item': 'minecraft:black_carpet'}]
            if H > 0.5: ing.append({'item': 'minecraft:iron_bars'})
            (D / 'recipes' / f'{i}.json').write_text(json.dumps({'type': 'minecraft:crafting_shapeless', 'category': 'building', 'group': 'concertstage_flexdeck', 'ingredients': ing, 'result': {'item': f'concertstage:{i}', 'count': 1}}, indent=2))
            lang['fr_fr'][f'block.concertstage.{i}'] = f'CSC FlexDeck {nfr} — {hfr}'
            lang['en_us'][f'block.concertstage.{i}'] = f'CSC FlexDeck {nen} — {hen}'
            lang['fr_fr'][f'tooltip.concertstage.dimensions.{i}'] = f'{nfr} · plancher {hfr}'
            lang['en_us'][f'tooltip.concertstage.dimensions.{i}'] = f'{nen} · deck {hen}'
            if len(P) == 3:
                lang['fr_fr'][f'tooltip.concertstage.use.{i}'] = 'Remplit l’angle entre une scène droite et une plateforme à 45° ; cliquer le côté long avec une autre FlexDeck l’y accroche en diagonale.'
                lang['en_us'][f'tooltip.concertstage.use.{i}'] = 'Fills the corner between a straight stage and a 45° platform; click its long side with another FlexDeck to join it diagonally.'
            else:
                lang['fr_fr'][f'tooltip.concertstage.use.{i}'] = 'Cliquer le côté d’une plateforme posée en biais (ou d’un triangle) l’y accroche bord à bord, dans le même angle.'
                lang['en_us'][f'tooltip.concertstage.use.{i}'] = 'Click the side of an angled platform (or a triangle) to join it edge to edge, at the same angle.'
    (D / 'stage_equipment.json').write_text(json.dumps(specs, ensure_ascii=False, separators=(',', ':')))
    for lg in lang: (A / 'lang' / f'{lg}.json').write_text(json.dumps(lang[lg], ensure_ascii=False, indent=2))
    for p in (D / 'tags/blocks/stage_equipment.json', RES / 'data/minecraft/tags/blocks/mineable/pickaxe.json'):
        t = json.loads(p.read_text()); v = [x for x in t['values'] if 'flexdeck_' not in x]; v += [f'concertstage:{i}' for i in ids]; t['values'] = v
        p.write_text(json.dumps(t, indent=2))
    print('FlexDeck :', len(ids))

if __name__ == '__main__':
    build()
