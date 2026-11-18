"""Abaisse la scène de festival préassemblée (et le praticable 2 × 1 assorti) de 2,2 m à 2,0 m : un plancher à une limite de bloc, pour
que les vrais blocs (enceintes, blocs fonctionnels de tout mod) se posent à ras du plancher.

Les vérins de pied sont raccourcis de 20 cm : tout ce qui est au-dessus de CUT descend de 0,2 m, le reste ne bouge pas (aucun sommet,
aucune boîte n'est entre 0,12 et 0,34 m, contrôlé). Les cases du 3e niveau (plancher de 2,0 à 2,2 m) passent dans celles du 2e niveau et
deviennent libres. Les identifiants (…_h220) restent ceux d'avant pour les mondes existants. Relancer ne fait rien (walk déjà à 2,0)."""
import json, pathlib, re
R = pathlib.Path(__file__).resolve().parents[1] / 'common/src/main/resources'
A = R / 'assets/concertstage'
SPECS = R / 'data/concertstage/stage_equipment.json'
DROP, CUT, LO, HI = 0.2, 0.22, 0.12, 0.34

def ymap(y):
    assert not (LO < y < HI), y
    return round(y - DROP, 7) if y > CUT else y

def obj_read(p):
    return p.read_text().splitlines() if p.exists() else []

def obj_shift(lines, ly, into):
    """Sommets d'une case ly (repère de la case) vers le repère de la case `into` après abaissement."""
    out = []
    for l in lines:
        if l.startswith('v '):
            _, x, y, z = l.split()[:4]
            out.append(f'v {x} {round(ymap(ly + float(y)) - into, 7):g} {z}')
        else:
            out.append(l)
    return out

def obj_merge(a, b):
    """Ajoute les faces de b à la suite de a (indices OBJ cumulés)."""
    nv = sum(1 for l in a if l.startswith('v ')); nt = sum(1 for l in a if l.startswith('vt ')); nn = sum(1 for l in a if l.startswith('vn '))
    out = list(a)
    for l in b:
        if l.startswith('f '):
            parts = []
            for t in l.split()[1:]:
                i = t.split('/'); i[0] = str(int(i[0]) + nv)
                if len(i) > 1 and i[1]: i[1] = str(int(i[1]) + nt)
                if len(i) > 2 and i[2]: i[2] = str(int(i[2]) + nn)
                parts.append('/'.join(i))
            out.append('f ' + ' '.join(parts))
        elif l.startswith('#') or l.startswith('mtllib'):
            continue
        else:
            out.append(l)
    return out

def clip(raw, part, sx, sz):
    lx = part % sx; lz = (part // sx) % sz; ly = part // (sx * sz); out = []
    for b in raw:
        c = [max(b[0], lx), max(b[1], ly), max(b[2], lz), min(b[3], lx + 1), min(b[4], ly + 1), min(b[5], lz + 1)]
        if c[0] < c[3] and c[1] < c[4] and c[2] < c[5]:
            out.append([round(c[0] - lx, 7), round(c[1] - ly, 7), round(c[2] - lz, 7), round(c[3] - lx, 7), round(c[4] - ly, 7), round(c[5] - lz, 7)])
    return out

def lower_spec(s):
    sx, sy, sz = s['grid']
    s['walk'] = round(s['walk'] - DROP, 3)
    s['raw_boxes'] = [[b[0], ymap(b[1]), b[2], b[3], ymap(b[4]), b[5]] for b in s['raw_boxes']]
    s['raw_bounds'][4] = s['walk']          # le plancher est le haut : la case au-dessus reste libre
    cells = [[clip(s['raw_boxes'], p, sx, sz)] for p in range(sx * sy * sz)]
    s['cells'] = cells
    s['occupied'] = [bool(o and c[0]) for o, c in zip(s['occupied'], cells)]

def lower_cells(name, count, layer, path_of, model_of, blockstates):
    """Cases OBJ d'une famille : abaissement, le 3e niveau fusionné dans le 2e, ses modèles retirés."""
    top = 2 * layer
    for p in range(count):
        ly = p // layer
        f = path_of(p)
        if not f.exists(): continue
        if ly >= 2:
            continue
        lines = obj_shift(obj_read(f), ly, ly)
        if ly == 1:
            for q in range(p + layer, count, layer):
                g = path_of(q)
                if g.exists(): lines = obj_merge(lines, obj_shift(obj_read(g), q // layer, 1))
        f.write_text('\n'.join(lines) + '\n')
    gone = set()
    for p in range(top, count):
        f = path_of(p)
        if f.exists(): f.unlink()
        m = model_of(p)
        if m.exists(): m.unlink(); gone.add('concertstage:block/stage/' + m.stem)
    for bs in blockstates:
        d = json.loads(bs.read_text())
        for k, v in d['variants'].items():
            if v.get('model') in gone: d['variants'][k] = {'model': 'concertstage:block/stage/empty'}
        bs.write_text(json.dumps(d, separators=(',', ':')))

def lower_whole_obj(p):
    p.write_text('\n'.join(obj_shift(obj_read(p), 0, 0)) + '\n')

def lower_mesh(p):
    m = json.loads(p.read_text())
    for k in m:
        for q in m[k]:
            for i in range(1, len(q), 8): q[i] = ymap(q[i])
    p.write_text(json.dumps(m, separators=(',', ':')))

def main():
    data = json.loads(SPECS.read_text()); eq = data['equipment']
    ids = [s['id'] for s in eq if s['id'] == 'raised_stage_platform_2x1_h220' or (s['id'].endswith('h220') or '_h220_tower' in s['id']) and 'festival' in s['id']]
    todo = [s for s in eq if s['id'] in ids and abs(s['walk'] - 2.2) < 1e-6]
    if not todo:
        print('déjà à 2,0 m'); return
    for s in todo: lower_spec(s)
    SPECS.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')))
    W, V, M, BS = A / 'models/stage/world', A / 'models/stage/v3', A / 'models/block/stage', A / 'blockstates'
    done = {s['id'] for s in todo}
    if 'assembly_festival_stage_8x4_h220' in done:
        k = 'assembly_festival_stage_8x4_h220'
        lower_cells(k, 128, 32, lambda p: W / f'{k}_{p}_0.obj', lambda p: M / f'{k}_{p}_0.json', [BS / f'{k}.json'])
        lower_whole_obj(A / f'models/stage/items/{k}.obj'); lower_mesh(A / f'stage_meshes/{k}.json')
    csc = sorted(i for i in done if i.startswith('csc_festival'))
    if csc:
        k = 'csc_festival_stage_8x4_h220_cell'
        lower_cells(k, 96, 32, lambda p: V / f'{k}_{p}.obj', lambda p: M / f'{k}_{p}_0.json', [BS / f'{i}.json' for i in csc])
        for i in csc: lower_whole_obj(V / f'items/{i}.obj')
    if 'raised_stage_platform_2x1_h220' in done:
        k = 'raised_stage_platform_2x1_h220'
        lower_cells(k, 8, 2, lambda p: W / f'{k}_{p}_0.obj', lambda p: M / f'{k}_{p}_0.json', [BS / f'{k}.json'])
        lower_whole_obj(A / f'models/stage/items/{k}.obj'); lower_mesh(A / f'stage_meshes/{k}.json')
    print('abaissés :', sorted(done))

if __name__ == '__main__':
    main()
