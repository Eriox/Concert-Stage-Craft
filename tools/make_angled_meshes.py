#!/usr/bin/env python3
"""Maillages dynamiques (stage_meshes) des plateformes CSC V3 pour la pose en biais avec le rapporteur de plateformes (1.12.0).
Mêmes coordonnées que raw_boxes (repère source) ; emplacement de matériau 5 = csc_v3_atlas (0-2 : metals, anti_slip, hardware)."""
from pathlib import Path
import json, sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import integrate_scaffold_v3 as V
SLOT={0:0,1:1,2:2,3:5}
def eligible(stem):
    if stem.startswith(('CSC_Tower_','CSC_StageDeck_Tower','CSC_StageDeck_Sleeve','CSC_Festival_','CSC_Leg_')): return False
    return V.config(stem)['kind'] in ('platform','riser','assembly','extension')
def main():
    out=[]
    for p in sorted((V.SRC/'Models').glob('*.bbmodel')):
        if not eligible(p.stem): continue
        q=V.quads_of(json.loads(p.read_text()))
        mesh={}
        for s,z in q:
            z=z if len(z)==4 else __import__('numpy').array([z[0],z[1],z[2],z[2]])
            mesh.setdefault(str(SLOT[s]),[]).append([round(float(x),5) for x in z.flatten()])
        V.P.dump(V.AS/'stage_meshes'/(V.key_of(p.stem)+'.json'),mesh); out.append(V.key_of(p.stem))
    print(len(out),'maillages :',out)
if __name__=='__main__': main()
