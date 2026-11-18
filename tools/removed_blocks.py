#!/usr/bin/env python3
"""Blocs supprimés définitivement (1.17.0) : anciens échafaudages (scaffold_stage_*), structures porteuses 3 x 3 m (stage_structure_h300_*),
échafaudages et plateaux d'ouverture de tour CSC (csc_tower_*, csc_stagedeck_tower*, csc_stagedeck_sleeve*).
purge() retire leurs fichiers, leurs entrées de stage_equipment.json, leurs langues et leurs tags ; les générateurs l'appellent à la fin
pour qu'ils ne reviennent jamais."""
import json, os
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'common/src/main/resources'
IDS = json.loads((Path(__file__).with_name('removed_blocks.json')).read_text())

def removed(i):
    return i in IDS

def purge():
    n = 0
    for base, _, fs in os.walk(RES):
        for f in fs:
            stem = f.rsplit('.', 1)[0]
            if any(stem == i or stem.startswith(i + '_') for i in IDS):
                os.remove(os.path.join(base, f)); n += 1
    p = RES / 'data/concertstage/stage_equipment.json'
    d = json.loads(p.read_text())
    d['equipment'] = [e for e in d['equipment'] if e['id'] not in IDS]
    p.write_text(json.dumps(d, separators=(',', ':')))
    for lg in ('fr_fr', 'en_us'):
        p = RES / 'assets/concertstage/lang' / (lg + '.json'); L = json.loads(p.read_text())
        for k in [k for k in L if any(k.endswith('.' + i) or ('.' + i + '.') in k for i in IDS)]: del L[k]
        p.write_text(json.dumps(L, ensure_ascii=False, indent=2))
    for p in RES.glob('data/**/tags/**/*.json'):
        t = json.loads(p.read_text()); v = t.get('values', [])
        nv = [x for x in v if not (isinstance(x, str) and x.split(':')[-1] in IDS)]
        if len(nv) != len(v): t['values'] = nv; p.write_text(json.dumps(t, indent=2))
    return n

if __name__ == '__main__':
    print('supprimés :', purge())
