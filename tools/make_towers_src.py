#!/usr/bin/env python3
"""Writes the four motorised towers (10, 14, 18 and 22 blocks) with a mast that is ONE FULL BLOCK wide (16 px).

Output: tools/blockbench/towers_src/tour_truss_motorisee_<N>_blocs.bbmodel   (read by tools/build_motor_towers.py)
Texture, resolution and Blockbench settings are taken from tools/blockbench/reference/tour_truss_motorisee_10_blocs.bbmodel
(the reference tower: the trusses get their colours from it, see tools/retexture_trusses.py), which this script never changes.

Since 1.2.9 every truss is as wide as a base block, so the mast is built like the full-size column `truss_v2_h2`:
    - four 3 x 3 px chords on the cell edge (tube centres 1.5 / 14.5 px), one diagonal per block on each of the four faces, 45 degrees,
      alternating direction block after block (same numbers as the column, so a column placed on a tower lines up);
    - at the bottom: base plate, four stabiliser arms with feet and braces (they stick out of the cell, the renderer has a one block margin),
      the winch motor inside the lowest cell and a yellow control box on the front;
    - at the top: head plate, pulley and lamp. The chain runs from the motor to the pulley through the middle of the mast.
(Before 1.2.9 the mast was 10 px wide, 1.5 px tubes, and this script did not exist: the towers came from a one-off generator.)
Safe to run again.
"""
import copy, json, math, os, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
BB = os.path.join(HERE, 'blockbench')
REF = os.path.join(BB, 'reference', 'tour_truss_motorisee_10_blocs.bbmodel')
OUT = os.path.join(BB, 'towers_src')
HEIGHTS = (10, 14, 18, 22)

# tiles of the 16 x 16 tower texture
UV = {'a': [0, 0, 8, 8],      # light aluminium
      'd': [8, 0, 16, 8],     # anthracite (feet, motor, chain)
      'y': [8, 8, 16, 16]}    # yellow (control box, lamp)
FACES = ('north', 'east', 'south', 'west', 'up', 'down')

TUBE = 3.0          # chord section, like the full-size trusses
DIAG_T = 1.2        # diagonal thickness (same as the column)
DIAG_LEN = 13 * math.sqrt(2)   # between the chord centres (1.5 and 14.5), 45 degrees
ARM = 12.5          # stabiliser arm length beyond the mast


class Model:
    def __init__(self):
        self.elements, self.groups = [], {}

    def cube(self, group, name, frm, to, mat='a', rot=(0, 0, 0), origin=None):
        o = origin or [(a + b) / 2 for a, b in zip(frm, to)]
        e = {
            'name': name, 'box_uv': False, 'rescale': False, 'locked': False, 'light_emission': 0,
            'render_order': 'default', 'allow_mirror_modeling': True,
            'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to], 'autouv': 0, 'color': 0,
            'origin': [round(v, 4) for v in o], 'rotation': [round(v, 4) for v in rot],
            'faces': {f: {'uv': list(UV[mat]), 'texture': 0} for f in FACES}, 'type': 'cube', 'uuid': str(uuid.uuid4()),
        }
        self.elements.append(e)
        self.groups.setdefault(group, []).append(e['uuid'])


def build(n, ref):
    m = Model()
    H = 16 * n
    y0, y1 = 2.0, H - 6.0          # chords from the base plate to the head plate

    # ---------------- base
    m.cube('Base', 'plateau', [0, 0, 0], [16, 2, 16], 'd')
    for sx in (1, -1):
        def mx(x):
            return x if sx == 1 else 16 - x
        a, b = sorted([mx(16), mx(16 + ARM)])
        m.cube('Base', 'bras_x', [a, 2, 7], [b, 3.5, 9])
        a, b = sorted([mx(16 + ARM - 2), mx(16 + ARM + 1)])
        m.cube('Base', 'pied_x', [a, 0, 6], [b, 2, 10], 'd')
        L = math.hypot(ARM, ARM)
        cx, cy = mx(16 + ARM / 2), 3.5 + ARM / 2
        m.cube('Base', 'jambe_x', [cx - L / 2, cy - 0.5, 7.25], [cx + L / 2, cy + 0.5, 8.75], rot=(0, 0, -45 * sx))
    for sz in (1, -1):
        def mz(z):
            return z if sz == 1 else 16 - z
        a, b = sorted([mz(16), mz(16 + ARM)])
        m.cube('Base', 'bras_z', [7, 2, a], [9, 3.5, b])
        a, b = sorted([mz(16 + ARM - 2), mz(16 + ARM + 1)])
        m.cube('Base', 'pied_z', [6, 0, a], [10, 2, b], 'd')
        L = math.hypot(ARM, ARM)
        cz, cy = mz(16 + ARM / 2), 3.5 + ARM / 2
        m.cube('Base', 'jambe_z', [7.25, cy - 0.5, cz - L / 2], [8.75, cy + 0.5, cz + L / 2], rot=(45 * sz, 0, 0))

    # ---------------- mast: four chords + one diagonal per block and face (same geometry as the full-size column)
    for i, (px, pz) in enumerate([(0, 0), (13, 0), (0, 13), (13, 13)]):
        m.cube('Tour', f'montant_{i}', [px, y0, pz], [px + TUBE, y1, pz + TUBE])
    h = DIAG_LEN / 2
    for k in range(n - 1):                         # the top cell only holds the head
        yc = 8.0 + 16.0 * k
        s = -1 if k % 2 == 0 else 1                # alternates block after block, like truss_v2_h2
        for name, z0, sg in (('avant', 0.9, s), ('arriere', 13.9, s)):
            m.cube('Tour', f'diag_{name}_{k}', [8 - h, yc - DIAG_T / 2, z0], [8 + h, yc + DIAG_T / 2, z0 + DIAG_T],
                   rot=(0, 0, 45 * sg), origin=[8, yc, z0 + DIAG_T / 2])
        for name, x0, sg in (('droite', 13.9, -s), ('gauche', 0.9, s)):
            m.cube('Tour', f'diag_{name}_{k}', [x0, yc - DIAG_T / 2, 8 - h], [x0 + DIAG_T, yc + DIAG_T / 2, 8 + h],
                   rot=(45 * sg, 0, 0), origin=[x0 + DIAG_T / 2, yc, 8])
        # small gusset plates where the diagonals meet the chords (every block boundary)
    # ---------------- motor (inside the lowest cell), control box, chain
    m.cube('Moteur', 'moteur', [4.5, 2, 4.5], [11.5, 9, 11.5], 'd')
    m.cube('Moteur', 'coffret', [5.5, 4, 16], [10.5, 9, 18.5], 'y')
    m.cube('Moteur', 'cable_chaine', [7.75, 9, 7.75], [8.25, y1, 8.25], 'd')

    # ---------------- head
    m.cube('Tete', 'plateau_tete', [0, y1, 0], [16, y1 + 2, 16], 'a')
    m.cube('Tete', 'poulie_treuil', [4.5, y1 + 2, 4.5], [11.5, y1 + 4.5, 11.5], 'd')
    m.cube('Tete', 'lampe', [5.5, y1 + 4.5, 5.5], [10.5, H, 10.5], 'y')

    outliner = []
    for g, children in m.groups.items():
        outliner.append({'name': g, 'origin': [8, 0, 8], 'color': 0, 'uuid': str(uuid.uuid4()), 'export': True,
                         'mirror_uv': False, 'isOpen': False, 'locked': False, 'visibility': True, 'autouv': 0,
                         'children': children})
    d = copy.deepcopy(ref)
    d['name'] = f'tour_truss_motorisee_{n}_blocs'
    d['elements'] = m.elements
    d['outliner'] = outliner
    d.pop('animations', None)
    return d


def main():
    ref = json.load(open(REF, encoding='utf-8'))
    os.makedirs(OUT, exist_ok=True)
    for n in HEIGHTS:
        d = build(n, ref)
        path = os.path.join(OUT, d['name'] + '.bbmodel')
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(d, f)
        top = max(e['to'][1] for e in d['elements'])
        print(f'{d["name"]:36s} {len(d["elements"]):4d} elements, height {top / 16:.2f} blocks')


if __name__ == '__main__':
    main()
