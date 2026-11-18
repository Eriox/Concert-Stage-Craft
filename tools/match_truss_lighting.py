#!/usr/bin/env python3
"""Truss = même rendu que les tours truss. Les tours (rendu de bloc-entité) sont éclairées par la lumière « entité » de Minecraft :
deux directions fixes dans le monde L0 = (0,2 ; 1 ; -0,7) et L1 = (-0,2 ; 1 ; 0,7), f = min(1, 0,4 + 0,6 × (max(0, n·L0) + max(0, n·L1))).
Les truss (modèles OBJ cuits) avaient l'ombrage directionnel des blocs, d'où un gris / noir différent.
Ce script cuit l'éclairage des tours dans les truss : pour chaque orientation utilisée par un blockstate, l'OBJ est tourné d'avance
(plus de rotation y dans le blockstate, la lumière reste fixe dans le monde comme pour les tours) et chaque face reçoit un matériau
dont la couleur diffuse Kd = f ; « shade_quads » est coupé. Sûr à relancer (les OBJ d'origine restent la source)."""
import json, math, re
from pathlib import Path
AS=Path(__file__).resolve().parents[1]/'common/src/main/resources/assets/concertstage'
L=[(0.2,1.0,-0.7),(-0.2,1.0,0.7)]
L=[tuple(c/math.sqrt(sum(x*x for x in v)) for c in v) for v in L]
def factor(n):
    return min(1.0,0.4+0.6*sum(max(0.0,sum(a*b for a,b in zip(n,l))) for l in L))
def rot(x,z,k):   # rotation y de blockstate (k quarts de tour, sens Minecraft) autour du centre de la case
    for _ in range(k): x,z=1.0-z,x
    return x,z
def rotn(x,z,k):
    for _ in range(k): x,z=-z,x
    return x,z
def bake(src,dst,k):
    lines=src.read_text().splitlines(); out=[]; vn=[]; used=set()
    for ln in lines:
        if ln.startswith('vn '): vn.append(tuple(map(float,ln.split()[1:4])))
    vni=0
    for ln in lines:
        if ln.startswith('mtllib'): out.append('mtllib '+dst.with_suffix('.mtl').name); continue
        if ln.startswith('usemtl'): continue
        if ln.startswith('v '):
            x,y,z=map(float,ln.split()[1:4]); x,z=rot(x,z,k); out.append(f'v {x:.6f} {y:.6f} {z:.6f}'); continue
        if ln.startswith('vn '):
            x,y,z=vn[vni]; vni+=1; x,z=rotn(x,z,k); out.append(f'vn {x:.6f} {y:.6f} {z:.6f}'); continue
        if ln.startswith('f '):
            ni=int(ln.split()[1].split('/')[2])-1; x,y,z=vn[ni]; x,z=rotn(x,z,k)
            q=round(factor((x,y,z))*50); used.add(q); out.append(f'usemtl lit_{q:02d}'); out.append(ln); continue
        out.append(ln)
    dst.parent.mkdir(parents=True,exist_ok=True); dst.write_text('\n'.join(out)+'\n')
    dst.with_suffix('.mtl').write_text(''.join(f'newmtl lit_{q:02d}\nKa 0 0 0\nKd {q/50:.2f} {q/50:.2f} {q/50:.2f}\nKs 0 0 0\nmap_Kd #detail\n' for q in sorted(used)))
FACE={'north':'east','east':'south','south':'west','west':'north','up':'up','down':'down'}
def rot_elements(els,k):
    """Éléments vanilla (repli Fabric) tournés de k quarts de tour comme le faisait le blockstate."""
    out=[]
    for e in els:
        e=json.loads(json.dumps(e))
        for _ in range(k):
            (x0,y0,z0),(x1,y1,z1)=e['from'],e['to']
            a=(16-z0,x0); b=(16-z1,x1)
            e['from']=[min(a[0],b[0]),y0,min(a[1],b[1])]; e['to']=[max(a[0],b[0]),y1,max(a[1],b[1])]
            e['faces']={FACE[f]:v for f,v in e.get('faces',{}).items()}
            r=e.get('rotation')
            if r:
                ox,oy,oz=r['origin']; r['origin']=[16-oz,oy,ox]
                if r['axis']=='x': r['axis']='z'
                elif r['axis']=='z': r['axis']='x'; r['angle']=-r['angle']
        out.append(e)
    return out
def main():
    made=set(); n=0
    for bs in sorted(AS.glob('blockstates/truss_*.json')):
        d=json.loads(bs.read_text())
        if 'variants' not in d: continue
        changed=False
        for key,v in d['variants'].items():
            m=v['model'].split(':')[1]
            base=re.sub(r'_lit_r\d$','',m)
            mp=AS/'models'/(base+'.json')
            if not mp.exists(): continue
            md=json.loads(mp.read_text())
            if md.get('loader')!='forge:obj': continue
            k=(v.get('y',0)//90)%4
            if m!=base: k=int(m[-1])    # déjà converti : on régénère
            name=f'{base}_lit_r{k}'
            if name not in made:
                src=AS/md['model'].split(':')[1]
                dst=AS/'models/rigging/lit'/(Path(name).name+'.obj')
                bake(src,dst,k)
                nd=dict(md)
                if md.get('elements'): nd['elements']=rot_elements(md['elements'],k)
                nd['model']='concertstage:'+str(dst.relative_to(AS)).replace('\\','/'); nd['shade_quads']=False
                # repli vanilla (Fabric) : éléments tournés par le blockstate comme avant
                (AS/'models'/(name+'.json')).write_text(json.dumps(nd,separators=(',',':'))); made.add(name); n+=1
            nv={'model':'concertstage:'+name}
            if md.get('elements'): pass
            if v!=nv: d['variants'][key]=nv; changed=True
        if changed: bs.write_text(json.dumps(d,separators=(',',':')))
    print('modèles truss éclairés comme les tours :',n)
if __name__=='__main__': main()
