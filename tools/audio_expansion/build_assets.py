"""1.3.0 rev. 4 — CSC Professional Audio Expansion and AUREX C6 Portable into the mod (run from the repository root; relaunching
replaces what it made). Reads the GLB exports delivered on 13 November 2026 (not modified, not in the jar) and writes:

  assets/concertstage/textures/block/aurex_pro_atlas.png    one atlas for every new piece: flat material swatches, cabinet coating,
                                                            perforated grille (alpha)
  assets/concertstage/speakers/<id>.json                    near = LOD1, far = LOD2, box = LOD3 (triangles, 8 floats a vertex: x y z u v
                                                            nx ny nz), the mod's coordinates (1 m = 1 block, front +Z)
  assets/concertstage/models/speakers/<id>.obj              inventory model (LOD2)
  assets/concertstage/models/item/<id>.json                 forge:obj item model on that atlas

The geometry is taken as delivered (no remodelling): only moved so that the hinge / pivot plane sits where the mod's rigging expects it
(cabinets: front pivot at z = 0, bottom at y = 0; supports: centred on the cabinet they carry) and its UVs moved into the atlas.

Usage: python3 tools/audio_expansion/build_assets.py <dir with CSC_Professional_Audio_Expansion and CSC_AUREX_C6_Portable>
"""
import io, json, math, pathlib, sys
import numpy as np
from PIL import Image

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from glb import Glb

ROOT = pathlib.Path(__file__).resolve().parents[2]
A = ROOT / 'common/src/main/resources/assets/concertstage'
SRC = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else '/tmp/aud')
PRO = SRC / 'CSC_Professional_Audio_Expansion/GLB'
POR = SRC / 'CSC_AUREX_C6_Portable/GLB'

# id: (glb folder, glb stem, z of the front pivot / centre in the glTF frame, label)
#   cabinets: their front pivot (Blender CSC_Bottom_Front_*, front plane of the C6 / B18P) goes to z = 0
#   supports ("centred"): their centre stays on the cabinet centre, the renderer places them there
PIECES = {
    'csc_aurex_la12': (PRO, 'CSC_AUREX_LA12', 0.345),
    'csc_aurex_la8': (PRO, 'CSC_AUREX_LA8', 0.27),
    'csc_aurex_c6': (POR, 'CSC_AUREX_C6', 0.173),
    'csc_aurex_b18p': (POR, 'CSC_AUREX_B18P', 0.37),
    'csc_aurex_la12_ground_frame': (PRO, 'CSC_AUREX_LA12_Ground_Frame', 0.0),
    'csc_aurex_la8_ground_frame': (PRO, 'CSC_AUREX_LA8_Ground_Frame', 0.0),
    'csc_aurex_la12_fly_frame': (PRO, 'CSC_AUREX_LA12_LA_Fly_Frame', 0.0),
    'csc_aurex_la8_fly_frame': (PRO, 'CSC_AUREX_LA8_LA_Fly_Frame', 0.0),
    'csc_orvane_lx8_stack_adapter': (PRO, 'CSC_Orvane_LX8_Stack_Adapter', 0.0),
    'csc_orvane_lx12_stack_adapter': (PRO, 'CSC_Orvane_LX12_Stack_Adapter', 0.0),
    'csc_veyra_a1_stack_adapter': (PRO, 'CSC_Veyra_A1_Stack_Adapter', 0.0),
    'csc_veyra_a2_stack_adapter': (PRO, 'CSC_Veyra_A2_Stack_Adapter', 0.0),
    'csc_noryx_a1_stack_adapter': (PRO, 'CSC_Noryx_A1_Stack_Adapter', 0.0),
    'csc_noryx_a2_stack_adapter': (PRO, 'CSC_Noryx_A2_Stack_Adapter', 0.0),
    'csc_aurex_portable_pole': (POR, 'CSC_AUREX_Portable_Pole', 0.0),
}

SIZE = 1024
# flat swatches: 32 x 32 cells on the first row; the coating and the grille: 256 x 256 tiles on the second row
SWATCH = {}
COAT = (0, 256, 256, 512)
GRILLE = (256, 256, 512, 512)


def srgb(c):
    return int(round(255 * (max(0.0, min(1.0, c)) ** (1 / 2.2))))


def build_atlas(sample):
    im = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 255))
    names = []
    for f in sample:
        g = Glb(f)
        for m in g.j.get('materials', []):
            pbr = m.get('pbrMetallicRoughness', {})
            if 'baseColorTexture' in pbr or m['name'] in names: continue
            names.append(m['name'])
            c = pbr.get('baseColorFactor', [1, 1, 1, 1])
            k = len(names) - 1
            x0, y0 = k * 32, 0
            SWATCH[m['name']] = ((x0 + 16) / SIZE, (y0 + 16) / SIZE)
            im.paste((srgb(c[0]), srgb(c[1]), srgb(c[2]), 255), (x0, y0, x0 + 32, y0 + 32))
    g = Glb(sample[0])
    by = {g.j['images'][i]['name']: i for i in range(len(g.j['images']))}
    coat = Image.open(io.BytesIO(g.image(by['CSC_Cabinet_Albedo']))).convert('RGBA').resize((256, 256), Image.LANCZOS)
    im.paste(coat, COAT[:2])
    gr = Image.open(io.BytesIO(g.image(by['CSC_Perforated_Grille_RGBA']))).convert('RGBA').resize((256, 256), Image.LANCZOS)
    # cutout render type: hard alpha, the holes show the dark inside of the cabinet
    px = gr.load()
    for y in range(256):
        for x in range(256):
            r, gg, b, a = px[x, y]
            px[x, y] = (r, gg, b, 255 if a >= 128 else 0)
    im.paste(gr, GRILLE[:2])
    return im


def tile(region, uv):
    """A tiling UV (repeated texture) into one tile of the atlas: the triangle keeps its place in its own tile."""
    x0, y0, x1, y1 = region
    inset = 2 / SIZE
    u = np.clip(uv[:, 0], 0, 1)
    v = np.clip(uv[:, 1], 0, 1)
    return np.c_[x0 / SIZE + inset + u * ((x1 - x0) / SIZE - 2 * inset), y0 / SIZE + inset + v * ((y1 - y0) / SIZE - 2 * inset)]


def triangles(path, zshift):
    g = Glb(path)
    out = []
    for name, m, p in g.primitives():
        at = p['attributes']
        pos = g.accessor(at['POSITION']).astype(np.float64)
        nor = g.accessor(at['NORMAL']).astype(np.float64) if 'NORMAL' in at else None
        uv = g.accessor(at['TEXCOORD_0']).astype(np.float64) if 'TEXCOORD_0' in at else np.zeros((len(pos), 2))
        idx = g.accessor(p['indices']).reshape(-1) if 'indices' in p else np.arange(len(pos))
        w = (m @ np.c_[pos, np.ones(len(pos))].T).T[:, :3]
        nm = np.linalg.inv(m[:3, :3]).T
        if nor is None:
            nor = np.zeros_like(pos)
        wn = (nm @ nor.T).T
        ln = np.linalg.norm(wn, axis=1, keepdims=True)
        wn = wn / np.where(ln > 1e-9, ln, 1)
        mat = g.material(p.get('material'))
        pbr = mat.get('pbrMetallicRoughness', {})
        tex = None
        if 'baseColorTexture' in pbr:
            tex = g.j['images'][g.j['textures'][pbr['baseColorTexture']['index']]['source']]['name']
        for t in range(0, len(idx) - 2, 3):
            tri = idx[t:t + 3]
            P = w[tri].copy()
            P[:, 2] -= zshift
            N = wn[tri]
            if not np.any(N):
                n = np.cross(P[1] - P[0], P[2] - P[0])
                N = np.repeat([n / (np.linalg.norm(n) or 1)], 3, axis=0)
            if tex is None:
                su, sv = SWATCH.get(mat.get('name'), SWATCH.get('Black'))
                UV = np.array([[su, sv]] * 3)
            else:
                T = uv[tri]
                T = T - np.floor(T.min(axis=0))
                UV = tile(GRILLE if 'Grille' in tex else COAT, T)
            for k in range(3):
                out.append([*P[k], *UV[k], *N[k]])
    return np.array(out, dtype=np.float64)


def flat(a):
    return [round(float(x), 4) for x in a.reshape(-1)]


def write_obj(pid, tris):
    lines = ['mtllib speakers.mtl', 'usemtl atlas']
    vs, vts, vns, fs = [], [], [], []
    for i in range(0, len(tris), 3):
        face = []
        for k in range(3):
            x, y, z, u, v, nx, ny, nz = tris[i + k]
            vs.append('v %.5f %.5f %.5f' % (x, y, z))
            vts.append('vt %.6f %.6f' % (u, 1 - v))
            vns.append('vn %.4f %.4f %.4f' % (nx, ny, nz))
            n = len(vs)
            face.append('%d/%d/%d' % (n, n, n))
        fs.append('f ' + ' '.join(face))
    (A / 'models/speakers' / (pid + '.obj')).write_text('\n'.join(lines + vs + vts + vns + fs) + '\n')


def item_model(pid, width):
    s = round(0.7 / max(0.35, width), 5)
    d = {"rotation": [25, 210, 0], "translation": [0, 0, 0], "scale": [s, s, s]}
    j = {"loader": "forge:obj", "model": "concertstage:models/speakers/%s.obj" % pid, "flip_v": True, "automatic_culling": False,
         "shade_quads": True, "textures": {"atlas": "concertstage:block/aurex_pro_atlas", "particle": "concertstage:block/aurex_pro_atlas"},
         "render_type": "minecraft:cutout",
         "display": {k: d for k in ("gui", "ground", "fixed", "firstperson_righthand", "thirdperson_righthand")}}
    (A / 'models/item' / (pid + '.json')).write_text(json.dumps(j))


def main():
    sample = [PRO / 'CSC_AUREX_LA12_LOD1.glb', POR / 'CSC_AUREX_C6_LOD1.glb', PRO / 'CSC_AUREX_LA12_Ground_Frame_LOD1.glb',
              POR / 'CSC_AUREX_Portable_Pole_LOD1.glb']
    atlas = build_atlas(sample)
    atlas.save(A / 'textures/block/aurex_pro_atlas.png')
    report = {}
    for pid, (folder, stem, zs) in PIECES.items():
        # cabinets: LOD1 close by; supports (frames, adapters, pole): their LOD1 carries engraved labels (12 000+ triangles for a
        # frame), LOD2 is the close-by level there
        levels = (1, 2, 3) if zs > 0 else (2, 3, 3)
        lods = [triangles(folder / ('%s_LOD%d.glb' % (stem, k)), zs) for k in levels]
        near, far, box = lods
        lo, hi = near[:, :3].min(axis=0), near[:, :3].max(axis=0)
        j = {"id": pid, "width": round(float(hi[0] - lo[0]), 4), "height": round(float(hi[1] - lo[1]), 4),
             "depth": round(float(hi[2] - lo[2]), 4), "bounds": flat(np.r_[lo, hi]), "near": flat(near), "far": flat(far), "box": flat(box)}
        (A / 'speakers' / (pid + '.json')).write_text(json.dumps(j, separators=(',', ':')))
        write_obj(pid, far)
        item_model(pid, j['width'])
        report[pid] = {"triangles": [len(x) // 3 for x in lods], "bounds": [round(float(x), 3) for x in np.r_[lo, hi]]}
    (ROOT / 'tools/audio_expansion/assets_report.json').write_text(json.dumps(report, indent=1))
    for k, v in report.items(): print(k, v)


if __name__ == '__main__':
    main()
