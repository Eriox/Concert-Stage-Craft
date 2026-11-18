#!/usr/bin/env python3
"""Allègement mémoire / chargement (1.7.1) : retire des OBJ de jeu les facettes « fil » invisibles.
Monde : facettes dont le plus petit côté < 0,1 px (1/160 bloc) — chants de parois de 1 à 3 mm, têtes de vis, rondelles.
Items : plus petit côté < 0,2 px ou aire < (0,5 px)² — invisibles dans une icône.
Les sources Blockbench et les exports d'origine (tools/) ne sont pas modifiés ; relancer après integrate_*.py."""
from pathlib import Path
import numpy as np, sys
AS=Path(__file__).resolve().parents[1]/'common/src/main/resources/assets/concertstage/models'
RULES={'stage/world':(1/160,0),'stage/v3':(1/160,0),'stage/items':(1/80,1/1024),'stage/v3/items':(1/80,1/1024),'rigging/inventory':(1/80,1/1024)}
def process(path,min_edge,min_area):
    lines=path.read_text().splitlines(); V=[]; out=[]; kept=removed=0; pending=[]
    # le format écrit par nos outils : v.../vt.../vn.../f par facette ; on reconstruit en ne gardant que les facettes utiles
    head=[l for l in lines if l.startswith(('#','mtllib'))]; mat=None; body=[]; vbuf=[]; tbuf=[]; nbuf=None
    allv=[];allt=[];alln=[]
    for l in lines:
        if l.startswith('v '): allv.append(l)
        elif l.startswith('vt '): allt.append(l)
        elif l.startswith('vn '): alln.append(l)
    faces=[]; mat=None
    for l in lines:
        if l.startswith('usemtl'): mat=l
        elif l.startswith('g '): faces.append(('g',l))
        elif l.startswith('f '): faces.append((mat,l))
    P=np.array([[float(x) for x in l.split()[1:4]] for l in allv])
    res=list(head); cv=ct=cn=1; last=None
    for m,l in faces:
        if m=='g': res.append(l); last=None; continue
        idx=[tuple(int(x)-1 for x in t.split('/')) for t in l.split()[1:]]
        p=P[[i[0] for i in idx]]; e=[np.linalg.norm(p[(k+1)%len(p)]-p[k]) for k in range(len(p))]; e=[x for x in e if x>1e-9]
        area=0.5*np.linalg.norm(np.cross(p[2]-p[0],p[1]-p[0]))+(0.5*np.linalg.norm(np.cross(p[3]-p[0],p[2]-p[0])) if len(p)>3 else 0)
        if not e or min(e)<min_edge or area<min_area: removed+=1; continue
        if m!=last: res.append(m); last=m
        for i in idx: res.append(allv[i[0]])
        for i in idx: res.append(allt[i[1]])
        res.append(alln[idx[0][2]])
        res.append('f '+' '.join(f'{cv+k}/{ct+k}/{cn}' for k in range(len(idx)))); cv+=len(idx); ct+=len(idx); cn+=1; kept+=1
    if kept==0: return 0,removed           # jamais d'OBJ vide : on garde l'original
    path.write_text('\n'.join(res)+'\n'); return kept,removed
tot=[0,0]
for d,(me,ma) in RULES.items():
    k=r=0
    for f in sorted((AS/d).glob('*.obj')):
        a,b=process(f,me,ma); k+=a; r+=b
    print(f'{d:20s} gardées {k:7d}  retirées {r:7d}'); tot[0]+=k; tot[1]+=r
print('total gardées',tot[0],'retirées',tot[1])
