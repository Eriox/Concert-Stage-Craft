#!/usr/bin/env python3
"""Plateaux à hauteur ronde (1.1.0-aurex.2).

Tous les plateaux scéniques dont le plancher est à une hauteur décimale (0,2 / 0,4 / 0,6 / 0,8 / 1,2 m) passent à un nombre entier de
blocs, au moins 1 m : leur plancher tombe pile sur une limite de bloc, un bloc posé dessus ne laisse plus d'écart.

  0,2 / 0,4 / 0,6 / 0,8 m -> 1 m ; 1,2 m -> 1 m.

Transformation verticale y -> f(y) d'une seule pièce (collisions, cases, modèles OBJ de chaque case, modèle d'objet, maillage des pièces
montées) :
  * la bande du plateau (épaisseur BAND sous le plancher) est déplacée telle quelle : planche et cadre gardent leur épaisseur ;
  * en dessous, les pieds sont étirés (ou raccourcis) linéairement ;
  * au-dessus du plancher (garde-corps, cabine DJ, régie), tout monte du même écart, resserré si besoin pour rester dans la grille.
Une face OBJ va à la case de son centre ; une case devenue vide redevient libre (le bloc d'un ancien monde y cède la place, comme pour
l'abaissement 2,2 -> 2,0 m de tools/lower_festival_200.py). Les identifiants (…_h040) ne changent pas : les mondes existants gardent
leurs pièces, à la nouvelle hauteur. Les escaliers (marches de 20 cm, modulaires), les supports réglables et le plateau sans pieds ne
changent pas. Relancer ne fait rien (planchers déjà entiers)."""
import json, math, pathlib

R = pathlib.Path(__file__).resolve().parents[1] / 'common/src/main/resources'
A = R / 'assets/concertstage'
SPECS = R / 'data/concertstage/stage_equipment.json'
KINDS = ('platform', 'riser', 'assembly', 'extension')
SKIP = {'stage_deck_2x1_without_legs'}
BAND = 0.1
EPS = 1e-6


def target(walk):
    return max(1, int(math.floor(walk + 0.5)))


class YMap:
    def __init__(self, walk, new, top, gridtop):
        self.w, self.W = walk, new
        self.band = min(BAND, walk / 2)
        above = top - walk
        room = gridtop - new
        self.s = 1.0 if above <= room + EPS else max(0.0, room / above)

    def __call__(self, y):
        w, W, b = self.w, self.W, self.band
        if y > w + EPS:
            return round(W + (y - w) * self.s, 7)
        if y >= w - b - EPS:
            return round(y + (W - w), 7)
        return round(y * (W - b) / (w - b), 7)


# ---------------------------------------------------------------- OBJ
def read_obj(path):
    v, vt, vn, faces, head = [], [], [], [], []
    mat = None
    for line in path.read_text().splitlines():
        t = line.split()
        if not t:
            continue
        if t[0] == 'v':
            v.append([float(t[1]), float(t[2]), float(t[3])])
        elif t[0] == 'vt':
            vt.append([float(x) for x in t[1:3]])
        elif t[0] == 'vn':
            vn.append([float(x) for x in t[1:4]])
        elif t[0] == 'usemtl':
            mat = t[1]
        elif t[0] == 'mtllib':
            head = [line]
        elif t[0] == 'f':
            corners = []
            for c in t[1:]:
                i = c.split('/')
                vi = int(i[0]); ti = int(i[1]) if len(i) > 1 and i[1] else None; ni = int(i[2]) if len(i) > 2 and i[2] else None
                corners.append((v[vi - 1 if vi > 0 else vi], vt[ti - 1] if ti else None, vn[ni - 1] if ni else None))
            faces.append((mat, corners))
    return head, faces


def write_obj(path, head, faces):
    out = ['# 1 unit = 1 Minecraft block'] + head
    vi = ti = ni = 1
    cur = object()
    for mat, corners in faces:
        if mat != cur:
            out.append('usemtl ' + mat) if mat else None
            cur = mat
        refs = []
        for p, uv, n in corners:
            out.append('v %s %s %s' % tuple(f'{c:.7g}' for c in p))
            r = str(vi); vi += 1
            if uv is not None:
                out.append('vt %s %s' % tuple(f'{c:.7g}' for c in uv)); r += '/' + str(ti); ti += 1
            if n is not None:
                out.append('vn %s %s %s' % tuple(f'{c:.7g}' for c in n)); r += ('' if uv is not None else '/') + '/' + str(ni); ni += 1
            refs.append(r)
        out.append('f ' + ' '.join(refs))
    path.write_text('\n'.join(out) + '\n')


def normal_of(pts, old):
    a, b, c = pts[0], pts[1], pts[2]
    u = [b[i] - a[i] for i in range(3)]; w = [c[i] - a[i] for i in range(3)]
    n = [u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]]
    l = math.sqrt(sum(x * x for x in n))
    if l < 1e-12 or old is None:
        return old
    n = [x / l for x in n]
    if sum(n[i] * old[i] for i in range(3)) < 0:
        n = [-x for x in n]
    return [round(x, 6) for x in n]


# ---------------------------------------------------------------- one piece
def variant_of(key):
    return 1 if 'joined=true' in key or 'mounted=true' in key else 0


def facing_y(key):
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        if 'facing=' + f in key:
            return y
    return 0


def part_of(key):
    for t in key.split(','):
        if t.startswith('part='):
            return int(t[5:])
    return -1


def do_piece(s):
    sid = s['id']
    gx, gy, gz = s['grid']
    n = gx * gy * gz
    walk = s['walk']
    W = target(walk)
    top = max([b[4] for b in s['raw_boxes']] + [walk])
    f = YMap(walk, W, top, gy)
    lay = gx * gz

    # --- collisions
    s['raw_boxes'] = [[b[0], f(b[1]), b[2], b[3], f(b[4]), b[5]] for b in s['raw_boxes']]
    s['raw_bounds'] = [s['raw_bounds'][0], f(s['raw_bounds'][1]), s['raw_bounds'][2], s['raw_bounds'][3], f(s['raw_bounds'][4]), s['raw_bounds'][5]]
    nvar = max(len(c) for c in s['cells'])
    cells = [[[] for _ in range(nvar)] for _ in range(n)]
    for p in range(n):
        ly = p // lay
        for v, boxes in enumerate(s['cells'][p]):
            for b in boxes:
                y0, y1 = f(b[1] + ly), f(b[4] + ly)
                for q in range(int(math.floor(y0 + EPS)), int(math.ceil(y1 - EPS))):
                    if q < 0 or q >= gy:
                        continue
                    a, c = max(y0, q), min(y1, q + 1)
                    if c - a > EPS:
                        cells[p % lay + q * lay][v].append([b[0], round(a - q, 7), b[2], b[3], round(c - q, 7), b[5]])
    for p in range(n):
        cells[p] = cells[p][:max(1, len(s['cells'][p]))]       # same number of variants per cell as before (variant 1 falls back on 0)
    s['cells'] = cells
    s['walk'] = float(W)
    for key in ('feet',):
        if s.get(key):
            s[key] = [[pt[0], f(pt[1]), pt[2]] for pt in s[key]]

    # --- models of the cells
    bs_path = A / 'blockstates' / (sid + '.json')
    bs = json.loads(bs_path.read_text())
    variants = bs['variants']
    model_of = {}                                   # (part, variant) -> model name (north entries)
    for k, val in variants.items():
        if 'facing=north' in k:
            model_of[(part_of(k), variant_of(k))] = val['model']
    objs, head, template = {}, [], None
    for (p, v), m in model_of.items():
        if m.endswith('/empty') or p >= n:
            continue
        mj = A / 'models/block' / (m.split(':', 1)[1].split('/', 1)[1] + '.json')
        model = json.loads(mj.read_text())
        template = template or model
        obj = A / model['model'].split(':', 1)[1]
        h, faces = read_obj(obj)
        head = head or h
        objs[(p, v)] = (mj, obj, faces)
    newfaces = {}
    for (p, v), (_, _, faces) in objs.items():
        ly = p // lay
        for mat, corners in faces:
            pts = [[c[0][0], f(c[0][1] + ly), c[0][2]] for c in corners]
            cy = sum(pt[1] for pt in pts) / len(pts)
            q = min(gy - 1, max(0, int(math.floor(cy + EPS))))
            moved = [([pt[0], round(pt[1] - q, 7), pt[2]], c[1], c[2]) for pt, c in zip(pts, corners)]
            nrm = normal_of([m[0] for m in moved], corners[0][2])
            moved = [(m[0], m[1], nrm if nrm is not None else m[2]) for m in moved]
            newfaces.setdefault((p % lay + q * lay, v), []).append((mat, moved))
    used = set()
    for (p, v), faces in newfaces.items():
        if (p, v) in objs:
            mj, obj, _ = objs[(p, v)]
        else:                                       # geometry moved into a cell that had none: new model for it
            tdir = A / template['model'].split(':', 1)[1].rsplit('/', 1)[0]          # same folder (and materials) as the other cells
            obj = tdir / f'{sid}_{p}_{v}.obj'
            mj = A / 'models/block/stage' / f'{sid}_{p}_{v}.json'
            model = json.loads(json.dumps(template))
            model['model'] = template['model'].rsplit('/', 1)[0] + '/' + obj.name
            mj.write_text(json.dumps(model, separators=(',', ':')))
            name = 'concertstage:block/stage/' + mj.stem
            for k in variants:
                if part_of(k) == p and variant_of(k) == v:
                    y = facing_y(k)
                    variants[k] = {'model': name, 'y': y} if y else {'model': name}
        write_obj(obj, head, faces)
        used.add((p, v))
    for (p, v), (mj, obj, _) in objs.items():
        if (p, v) in used:
            continue
        obj.unlink(missing_ok=True); mj.unlink(missing_ok=True)   # nothing left in this cell
        for k in variants:
            if part_of(k) == p and variant_of(k) == v:
                variants[k] = {'model': 'concertstage:block/stage/empty'}
    bs_path.write_text(json.dumps(bs, separators=(',', ':')))
    occ = [bool(any(cells[p]) or any((p, v) in used for v in range(nvar))) for p in range(n)]
    s['occupied'] = occ

    # --- item model and mounted-piece mesh (whole piece, one frame)
    item = json.loads((A / 'models/item' / (sid + '.json')).read_text())
    if 'model' in item:
        io = A / item['model'].split(':', 1)[1]
        h, faces = read_obj(io)
        out = []
        for mat, corners in faces:
            pts = [[c[0][0], f(c[0][1]), c[0][2]] for c in corners]
            nrm = normal_of(pts, corners[0][2])
            out.append((mat, [(pt, c[1], nrm if nrm is not None else c[2]) for pt, c in zip(pts, corners)]))
        write_obj(io, h, out)
    mesh = A / 'stage_meshes' / (sid + '.json')
    if mesh.exists():
        m = json.loads(mesh.read_text())
        for k in m:
            for q in m[k]:
                for i in range(1, len(q), 8):
                    q[i] = f(q[i])
        mesh.write_text(json.dumps(m, separators=(',', ':')))
    return walk, W, round(f.s, 3)


def main():
    data = json.loads(SPECS.read_text())
    done = []
    for s in data['equipment']:
        if s['kind'] in KINDS and s['id'] not in SKIP and abs(s['walk'] - round(s['walk'])) > EPS:
            done.append((s['id'],) + do_piece(s))
        elif s['kind'] in KINDS and s['id'] not in SKIP and s['walk'] < 1 - EPS and s['walk'] > EPS:
            done.append((s['id'],) + do_piece(s))
    SPECS.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')))
    for d in done:
        print('%-36s %.2f m -> %d m  (au-dessus x%s)' % d)
    if not done:
        print('planchers déjà entiers')


if __name__ == '__main__':
    main()


# ---------------------------------------------------------------- inventory icons (same framing as tools/integrate_platforms.py)
def refit_gui(ids):
    import numpy as np
    data = json.loads(SPECS.read_text())
    specs = {s['id']: s for s in data['equipment']}
    for sid in ids:
        p = A / 'models/item' / (sid + '.json')
        item = json.loads(p.read_text())
        if 'model' not in item:
            continue
        vs = np.array([[float(x) for x in l.split()[1:4]] for l in (A / item['model'].split(':', 1)[1]).read_text().splitlines() if l.startswith('v ')])
        rx, ry = np.radians([30, 225])
        R = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]]) @ np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
        v = (vs - .5) @ R.T; lo = v.min(axis=0); hi = v.max(axis=0)
        scale = .86 / max(hi[0] - lo[0], hi[1] - lo[1]); tr = -(lo + hi) / 2 * scale * 16
        gui = {'rotation': [30, 225, 0], 'translation': [round(float(x), 5) for x in tr], 'scale': [round(float(scale), 7)] * 3}
        d = item.setdefault('display', {})
        d['gui'] = gui; d['fixed'] = gui
        d['ground'] = {**gui, 'scale': [round(float(scale) * .65, 7)] * 3, 'translation': [round(float(tr[0]) * .65, 5), round(2 + float(tr[1]) * .65, 5), round(float(tr[2]) * .65, 5)]}
        for k, m in (('firstperson_righthand', .9), ('firstperson_lefthand', .9), ('thirdperson_righthand', .7), ('thirdperson_lefthand', .7)):
            d[k] = {**gui, 'scale': [round(float(scale) * m, 7)] * 3}
        p.write_text(json.dumps(item, separators=(',', ':')))
        if sid in specs:
            specs[sid]['gui'] = gui
    SPECS.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')))


ROUNDED = ['stage_deck_2x1_h020', 'stage_deck_2x1_h040', 'stage_deck_2x1_h080', 'raised_stage_platform_2x1_h120', 'drum_riser_4x2_h060',
           'musician_riser_2x1_h040', 'dj_platform_2x2_h080', 'stage_extension_1x1_h040', 'stage_extension_2x0p5_h040',
           'assembly_solo_stage_4x2_h040', 'assembly_festival_stage_8x4_h120', 'csc_dj_platform_2x2_h080', 'csc_drumriser_2x2_h040',
           'csc_foh_platform_4x2_h040', 'csc_keyboard_riser_2x1_h020', 'csc_technical_platform_2x2_h120', 'flexdeck_1x1_h040',
           'flexdeck_2x1_h040', 'flexdeck_2x2_h040', 'flexdeck_1x0p5_h040', 'flexdeck_tri_1x1_h040', 'flexdeck_tri_2x2_h040']

if __name__ == '__main__':
    refit_gui(ROUNDED)
