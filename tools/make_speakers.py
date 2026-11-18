"""Enceintes CSC Veyra (lot 01) et CSC Noryx (lot 02) : exports GAME_READY (GLB) -> maillages du jeu (1.1.0).

Pour chaque modèle : `assets/concertstage/speakers/<id>.json` (triangles x, y, z, u, v, nx, ny, nz en mètres = blocs ; origine au centre
du bord avant au sol, façade vers +Z, hauteur +Y ; LOD proche et lointain ; enveloppe ; points d'accroche CSC_Attach_*), une icône
d'inventaire (OBJ + modèle d'objet) et une texture commune `textures/block/speakers_atlas.png` :
  - grille perforée (alpha) répétée 8 x 4 fois (les UV de la grille dépassent [0, 1]) ;
  - une pastille par couleur de matériau (les matériaux unis du GLB sont des couleurs linéaires, converties en sRGB) ;
  - plaque arrière et badge de chaque modèle.
Les fichiers HD (.blend) restent dans tools/speaker_source/hd comme références. Relancer remplace tout."""
import io, json, math, pathlib, sys
import numpy as np
from PIL import Image
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import speaker_glb as glb

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / 'tools/speaker_source'
A = ROOT / 'common/src/main/resources/assets/concertstage'
MODELS = [('csc_veyra_a1', 'CSC_Veyra_A1'), ('csc_veyra_a2', 'CSC_Veyra_A2'), ('csc_veyra_b2', 'CSC_Veyra_B2'),
          ('csc_noryx_a1', 'CSC_Noryx_A1'), ('csc_noryx_a2', 'CSC_Noryx_A2'), ('csc_noryx_b1', 'CSC_Noryx_B1')]
S = 1024
GRILLE = (0, 0, 8, 4, 64)            # x, y, tiles u, tiles v, tile px
SWATCH_Y, SWATCH = 264, 16
REAR = (0, 296, 384, 128)            # rear plates: 2 per row
BADGE = (0, 696, 384, 80)            # badges: 2 per row
PAD = 2


def srgb(c):
    c = max(0.0, min(1.0, c))
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def main():
    atlas = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    swatches = {}
    grille_done = False
    out_models = {}
    for k, (mid, name) in enumerate(MODELS):
        j, acc, image = glb.load(SRC / f'{name}_GAME_READY.glb')
        imgs = {im.get('name'): Image.open(io.BytesIO(image(i))).convert('RGBA') for i, im in enumerate(j['images'])}
        tex_of = {i: j['images'][t['source']]['name'] for i, t in enumerate(j['textures'])}
        if not grille_done:
            g = imgs['grille_alpha'].resize((GRILLE[4], GRILLE[4]), Image.LANCZOS)
            for tu in range(GRILLE[2]):
                for tv in range(GRILLE[3]):
                    atlas.paste(g, (GRILLE[0] + tu * GRILLE[4], GRILLE[1] + tv * GRILLE[4]))
            grille_done = True
        rx = REAR[0] + (k % 2) * REAR[2]; ry = REAR[1] + (k // 2) * REAR[3]
        atlas.paste(imgs[f'{name}_rear'].resize((REAR[2] - 2 * PAD, REAR[3] - 2 * PAD), Image.LANCZOS), (rx + PAD, ry + PAD))
        bx = BADGE[0] + (k % 2) * BADGE[2]; by = BADGE[1] + (k // 2) * BADGE[3]
        atlas.paste(imgs[f'{name}_badge'].resize((BADGE[2] - 2 * PAD, BADGE[3] - 2 * PAD), Image.LANCZOS), (bx + PAD, by + PAD))
        regions = {f'{name}_rear': (rx + PAD, ry + PAD, REAR[2] - 2 * PAD, REAR[3] - 2 * PAD),
                   f'{name}_badge': (bx + PAD, by + PAD, BADGE[2] - 2 * PAD, BADGE[3] - 2 * PAD)}

        def swatch(color):
            key = tuple(round(c, 4) for c in color)
            if key not in swatches:
                n = len(swatches); x = 520 + (n % 30) * SWATCH; y = SWATCH_Y - 8 + (n // 30) * SWATCH
                lift = 1.35                                                     # very dark coatings: a little lighter in game light
                rgb = tuple(int(round(255 * min(1, srgb(c) * lift))) for c in color[:3])
                atlas.paste(Image.new('RGBA', (SWATCH, SWATCH), rgb + (255,)), (x, y))
                swatches[key] = ((x + SWATCH / 2) / S, (y + SWATCH / 2) / S)
            return swatches[key]

        tris_near, tris_far = [], []
        lo = np.array([1e9] * 3); hi = -lo
        for i, n, M in glb.walk(j):
            if 'mesh' not in n: continue
            N3 = np.linalg.inv(M[:3, :3]).T
            for p in j['meshes'][n['mesh']]['primitives']:
                P = acc(p['attributes']['POSITION']); idx = acc(p['indices']).astype(int)
                Nn = acc(p['attributes']['NORMAL']) if 'NORMAL' in p['attributes'] else None
                UV = acc(p['attributes']['TEXCOORD_0']) if 'TEXCOORD_0' in p['attributes'] else np.zeros((len(P), 2))
                W = (np.c_[P, np.ones(len(P))] @ M.T)[:, :3]
                Wn = (Nn @ N3.T) if Nn is not None else None
                lo = np.minimum(lo, W.min(0)); hi = np.maximum(hi, W.max(0))
                mat = j['materials'][p['material']]
                pbr = mat.get('pbrMetallicRoughness', {})
                tex = tex_of.get(pbr.get('baseColorTexture', {}).get('index')) if 'baseColorTexture' in pbr else None
                color = pbr.get('baseColorFactor', [1, 1, 1, 1])
                size = float(np.linalg.norm(W.max(0) - W.min(0)))
                small = size < 0.05                                            # screws, small connectors: near LOD only
                for t in range(0, len(idx), 3):
                    tri = idx[t:t + 3]
                    uv = UV[tri].copy()
                    if tex == 'grille_alpha':                                  # tiled: shift the triangle into the repeated region
                        uv -= np.floor(uv.min(0))
                        uv[:, 0] = np.clip(uv[:, 0], 0, GRILLE[2]); uv[:, 1] = np.clip(uv[:, 1], 0, GRILLE[3])
                        uv = (np.array([GRILLE[0], GRILLE[1]]) + uv * GRILLE[4]) / S
                    elif tex in regions:
                        x0, y0, w, h = regions[tex]
                        uv = np.c_[(x0 + np.clip(uv[:, 0], 0, 1) * w) / S, (y0 + np.clip(uv[:, 1], 0, 1) * h) / S]
                    else:
                        su = swatch(color); uv = np.array([su, su, su])
                    nrm = Wn[tri] if Wn is not None else np.tile(np.cross(W[tri[1]] - W[tri[0]], W[tri[2]] - W[tri[0]]), (3, 1))
                    row = []
                    for v in range(3):
                        nv = nrm[v] / (np.linalg.norm(nrm[v]) or 1)
                        row += [round(float(W[tri[v], 0]), 4), round(float(W[tri[v], 1]), 4), round(float(W[tri[v], 2]), 4),
                                round(float(uv[v, 0]), 5), round(float(uv[v, 1]), 5),
                                round(float(nv[0]), 3), round(float(nv[1]), 3), round(float(nv[2]), 3)]
                    tris_near.append(row)
                    if not small: tris_far.append(row)
        att = {}
        for i, n, M in glb.walk(j):
            nm = n.get('name') or ''
            if nm.startswith('CSC_Attach_'): att[nm.split('.')[0][len('CSC_Attach_'):].lower()] = [round(float(v), 4) for v in M[:3, 3]]
        data = {'id': mid, 'name': name.replace('_', ' '), 'bounds': [round(float(v), 4) for v in list(lo) + list(hi)],
                'width': round(float(hi[0] - lo[0]), 4), 'height': round(float(hi[1] - lo[1]), 4), 'depth': round(float(hi[2] - lo[2]), 4),
                'attach': att, 'near': [v for r in tris_near for v in r], 'far': [v for r in tris_far for v in r]}
        (A / 'speakers').mkdir(parents=True, exist_ok=True)
        (A / 'speakers' / f'{mid}.json').write_text(json.dumps(data, separators=(',', ':')))
        out_models[mid] = (tris_far, data)
        print(mid, 'near', len(tris_near), 'far', len(tris_far), 'size', data['width'], data['height'], data['depth'])
    (A / 'textures/block').mkdir(parents=True, exist_ok=True)
    atlas.save(A / 'textures/block/speakers_atlas.png')
    # inventory icons: far LOD as OBJ (centred, 1 block = 1 m), item model with the atlas
    (A / 'models/speakers').mkdir(parents=True, exist_ok=True)
    (A / 'models/speakers/speakers.mtl').write_text('newmtl atlas\nKd 1 1 1\nmap_Kd #atlas\n')
    for mid, (tris, d) in out_models.items():
        b = d['bounds']; cx, cz = (b[0] + b[3]) / 2, (b[2] + b[5]) / 2
        lines = ['# 1 unit = 1 Minecraft block', 'mtllib speakers.mtl', 'usemtl atlas']
        vi = 0
        for r in tris:
            for v in range(3):
                x, y, z, u, w, nx, ny, nz = r[v * 8:v * 8 + 8]
                lines.append(f'v {x - cx + 0.5:.4f} {y:.4f} {z - cz + 0.5:.4f}')
                lines.append(f'vt {u:.5f} {1 - w:.5f}')
                lines.append(f'vn {nx} {ny} {nz}')
            lines.append(f'f {vi + 1}/{vi + 1}/{vi + 1} {vi + 2}/{vi + 2}/{vi + 2} {vi + 3}/{vi + 3}/{vi + 3}')
            vi += 3
        (A / 'models/speakers' / f'{mid}.obj').write_text('\n'.join(lines) + '\n')
        big = max(d['width'], d['height'], d['depth'])
        s = round(0.62 / big, 4)
        disp = lambda r, t, sc: {'rotation': r, 'translation': t, 'scale': [sc, sc, sc]}
        (A / 'models/item' / f'{mid}.json').write_text(json.dumps({
            'loader': 'forge:obj', 'model': f'concertstage:models/speakers/{mid}.obj', 'flip_v': True, 'automatic_culling': False, 'shade_quads': True,
            'textures': {'atlas': 'concertstage:block/speakers_atlas', 'particle': 'concertstage:block/speakers_atlas'}, 'elements': [], 'render_type': 'minecraft:cutout',
            'gui_light': 'side',
            'display': {'gui': disp([25, 210, 0], [0, 1.5, 0], s * 1.15), 'ground': disp([0, 0, 0], [0, 2, 0], s * 0.6), 'fixed': disp([0, 180, 0], [0, 0, 0], s),
                        'thirdperson_righthand': disp([75, 45, 0], [0, 2.5, 0], s * 0.55), 'firstperson_righthand': disp([0, 225, 0], [0, 2, 0], s * 0.6),
                        'firstperson_lefthand': disp([0, 225, 0], [0, 2, 0], s * 0.6)}}, separators=(',', ':')))
    print('atlas swatches', len(swatches))


if __name__ == '__main__':
    main()
