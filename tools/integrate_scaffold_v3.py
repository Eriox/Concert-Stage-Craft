#!/usr/bin/env python3
"""Intègre les modèles CSC Stage Platforms & Scaffolding V3 (tools/scaffold_v3_source) comme équipements de scène.
Ajoute/remplace les entrées « csc_* » de stage_equipment.json sans toucher aux autres ; découpe par cellule (OBJ Forge),
collisions fonctionnelles, blockstates multipart, items 3D, maillages, loot, recettes et langues."""
from pathlib import Path
import json, math, shutil, hashlib, sys
import numpy as np
sys.path.insert(0, str(Path(__file__).resolve().parent))
import integrate_platforms as P
ROOT=Path(__file__).resolve().parents[1]; SRC=ROOT/'tools/scaffold_v3_source'; AS=P.AS; DATA=P.DATA
GLOBAL=['metals','anti_slip','hardware','csc_v3_atlas']       # emplacements de matériaux communs aux modèles V3
SKIP_COLLISION=('rosace','clavette','tête de','griffe','bouchon','emboîtement','boulon','rondelle','vis ','écrou','levier','axe de serrage',
                'crochet','échelon','montant d’échelle','patin d’échelle','soudure','perçage','oreille','repère')
def key_of(stem): return stem.lower()

def quads_of(d):
    """Quads [x,y,z,u,v,nx,ny,nz] en blocs, avec l'emplacement de matériau global, pour cubes ET meshes."""
    tex=[t['name'].rsplit('.',1)[0] for t in d['textures']]; out=[]
    for e in d['elements']:
        if not e.get('visibility',True) or not e.get('export',True): continue
        if e['type']=='cube':
            vs=P.cube_vertices(e)
            for side,ids in P.FACES.items():
                f=e['faces'].get(side)
                if not f or f.get('texture') is None: continue
                t=d['textures'][f['texture']]; u0,v0,u1,v1=f['uv']; uv=np.roll([[u0,v1],[u1,v1],[u1,v0],[u0,v0]],-f.get('rotation',0)//90,axis=0)
                p=vs[ids]; n=np.cross(p[1]-p[0],p[2]-p[0]); nn=np.linalg.norm(n)
                if nn<1e-12: continue
                n/=nn; out.append((GLOBAL.index(tex[f['texture']]),np.array([[*v,a/t['uv_width'],b/t['uv_height'],*n] for v,(a,b) in zip(p,uv)])))
        else:
            o=np.array(e.get('origin',[0,0,0]),float); V={k:(np.array(v,float)+o)/16 for k,v in e['vertices'].items()}
            for f in e['faces'].values():
                ids=f['vertices']
                if len(ids)<3 or f.get('texture') is None: continue
                t=d['textures'][f['texture']]; slot=GLOBAL.index(tex[f['texture']])
                pts=[V[k] for k in ids]; n=np.cross(pts[1]-pts[0],pts[2]-pts[0]); nn=np.linalg.norm(n)
                if nn<1e-12: continue
                n/=nn; rows=[[*V[k],f['uv'][k][0]/t['uv_width'],f['uv'][k][1]/t['uv_height'],*n] for k in ids]
                if len(rows)==4: out.append((slot,np.array(rows)))
                else:
                    for j in range(1,len(rows)-1):
                        out.append((slot,np.array([rows[0],rows[j],rows[j+1],rows[j+1]])))
    return out

def collision_boxes(d):
    boxes=[]
    for e in d['elements']:
        name=e['name'].lower()
        if any(s in name for s in SKIP_COLLISION) or not e.get('export',True): continue
        if e['type']=='cube':
            a=np.array(e['from'],float); b=np.array(e['to'],float); rotated=any(abs(x)>1e-7 for x in e.get('rotation',[0,0,0]))
            axis=int(np.argmax(b-a)); steps=max(1,math.ceil((b[axis]-a[axis])/16/.0625)) if rotated else 1
            for j in range(steps):
                aa=a.copy(); bb=b.copy(); aa[axis]=a[axis]+(b[axis]-a[axis])*j/steps; bb[axis]=a[axis]+(b[axis]-a[axis])*(j+1)/steps
                vs=P.cube_vertices(e,aa,bb); boxes.append([*vs.min(axis=0),*vs.max(axis=0)])
        else:
            o=np.array(e.get('origin',[0,0,0]),float); V=(np.array(list(e['vertices'].values()),float)+o)/16; n=len(V)
            # tube généré : deux couronnes de n/2 sommets → enveloppe découpée le long de l'axe
            if n%2==0 and n>=8:
                h=n//2; c0=V[:h].mean(0); c1=V[h:].mean(0); r=float(np.linalg.norm(V[0]-c0)); L=float(np.linalg.norm(c1-c0))
                ext=np.ptp(V,axis=0)
                if L>r*2 and np.count_nonzero(ext>2*r+1e-4)>=2:
                    steps=max(1,math.ceil(L/.0625))
                    for j in range(steps):
                        p=c0+(c1-c0)*j/steps; q=c0+(c1-c0)*(j+1)/steps; lo=np.minimum(p,q)-r; hi=np.maximum(p,q)+r; boxes.append([*lo,*hi])
                    continue
            boxes.append([*V.min(axis=0),*V.max(axis=0)])
    return [[round(float(x),7) for x in b] for b in boxes]

def write_obj(path,quads):
    lines=['# 1 unit = 1 Minecraft block','mtllib csc_v3_materials.mtl'];count=1;normal=1
    for slot,q in quads:
        lines.append('usemtl material_'+str(slot))
        for v in q: lines.append('v '+' '.join(f'{x:.6f}' for x in v[:3]))
        for v in q: lines.append('vt %.6f %.6f'%(v[3],1-v[4]))
        lines.append('vn '+' '.join(f'{x:.6f}' for x in q[0,5:8])); lines.append('f '+' '.join(f'{count+i}/{count+i}/{normal}' for i in range(len(q)))); count+=len(q); normal+=1
    path.parent.mkdir(parents=True,exist_ok=True); path.write_text('\n'.join(lines)+'\n')
    path.with_name('csc_v3_materials.mtl').write_text(''.join(f'newmtl material_{i}\nKd 1 1 1\nKa 0.2 0.2 0.2\nKs 0 0 0\nmap_Kd #{t}\n' for i,t in enumerate(GLOBAL)))
def model_json(obj,display=None):
    d={'loader':'forge:obj','model':'concertstage:'+str(obj.relative_to(AS)).replace('\\','/'),'flip_v':True,'automatic_culling':False,'shade_quads':True,
       'textures':{**{t:'concertstage:block/stage/'+t for t in GLOBAL},'particle':'concertstage:block/stage/metals'},'elements':[],'render_type':'minecraft:cutout'}
    if display: d['display']=display; d['gui_light']='side'
    return d

# ---------------- catalogue ----------------
FESTIVAL_TOWER_CELLS=[(1,2)]   # (x, z) de la colonne laissée libre pour la tour (version A)
# Variantes : toutes les cases intérieures (x 1..6 sur 8, z 1..2 sur 4), sans dupliquer la source.
#   A = (1,2) ; B = (1,1) ; les autres : CSC_Festival_Stage_8x4_H220_Tower_C{x+1}_R{z+1}
FESTIVAL_SRC='CSC_Festival_Stage_8x4_H220_Tower'
FESTIVAL_VARIANTS={FESTIVAL_SRC+'_B':[(1,1)]}
for _z in (1,2):
    for _x in range(2,7): FESTIVAL_VARIANTS[f'{FESTIVAL_SRC}_C{_x+1}_R{_z+1}']=[(_x,_z)]
ALIASES={k:FESTIVAL_SRC for k in FESTIVAL_VARIANTS}   # variantes sans source dupliquée
def config(stem):
    s=stem
    if s.startswith('CSC_StageDeck_') and not s.startswith('CSC_StageDeck_Tower') and not s.startswith('CSC_StageDeck_Sleeve'):
        return dict(kind='platform',walk=.104,offset=(0,.014,0))
    if s.startswith('CSC_StageDeck_'): return dict(kind='platform',walk=.104,offset=(0,.014,0))
    if s.startswith('CSC_Leg_'): return dict(kind='support',walk=0.,offset=(.5,0,.5))
    if s.startswith('CSC_Tower_'): return dict(kind='platform',walk=2.0,offset=(0,0,0))
    if s in FESTIVAL_VARIANTS: return dict(kind='assembly',walk=2.2,offset=(0,0,0),empty=FESTIVAL_VARIANTS[s])
    if s.startswith('CSC_Festival_'): return dict(kind='assembly',walk=2.2,offset=(0,0,0),empty=FESTIVAL_TOWER_CELLS)
    h={'H020':.2,'H040':.4,'H080':.8,'H120':1.2}[s.rsplit('_',1)[1]]
    return dict(kind='assembly' if 'FOH' in s else 'riser',walk=h,offset=None)

def export(stem):
    key=key_of(stem); src=SRC/'Models'/(ALIASES.get(stem,stem)+'.bbmodel'); d=json.loads(src.read_text()); cfg=config(stem)
    q=quads_of(d); boxes=collision_boxes(d)
    allv=np.concatenate([x[:,:3] for _,x in q]); lo=allv.min(0); hi=allv.max(0)
    off=np.array(cfg['offset'] if cfg['offset'] is not None else [max(0,math.ceil(-lo[0]-.15)),0,max(0,math.ceil(-lo[2]-.15))],float)
    q=P.translate(q,off); boxes=[[b[0]+off[0],b[1]+off[1],b[2]+off[2],b[3]+off[0],b[4]+off[1],b[5]+off[2]] for b in boxes]
    lo=lo+off; hi=hi+off
    sx=max(1,math.ceil(hi[0]-.15)); sz=max(1,math.ceil(hi[2]-.15)); ny=max(1,math.ceil(hi[1]-1e-5))
    grid=[sx,ny,sz]; count=sx*ny*sz; assert count<=256,(stem,grid)
    if 'CornerPass' in stem:
        # la cellule 0 (cellule maîtresse, toujours occupée) ne doit pas être celle de la tour : rotation de 180° dans l'emprise
        q=P.transform(q,(sx,0,sz),2); boxes=[[sx-b[3],b[1],sz-b[5],sx-b[0],b[4],sz-b[2]] for b in boxes]; off=np.array([sx,0,sz],float)*0+off; flip=True
    else: flip=False
    spatial=P.spatial_geometry(q,grid); cells=[]; occ=[]; parts=[]; kept=[]; light=[]
    fest=ALIASES.get(stem,stem)==FESTIVAL_SRC
    ckey='csc_festival_stage_8x4_h220_cell' if fest else key   # scènes de festival : modèles de cases partagés par toutes les variantes
    for part in range(count):
        xyz=np.array([part%sx,part//(sx*sz),(part//sx)%sz]); cq=P.cell_geometry(spatial[part],xyz,grid)
        free=(int(xyz[0]),int(xyz[2])) in cfg.get('empty',[])
        rim=xyz[0] in (0,sx-1) and xyz[2] in (0,sz-1)   # angles seulement
        if not free:
            moved=[(sl,np.c_[qq[:,:3]+xyz,qq[:,3:]]) for sl,qq in cq]; kept.extend(moved)
            light.extend(m for m in moved if rim or m[1][:,1].min()>2.05)   # objet léger : plancher + pieds d'angle
        cells.append([[] if free else P.clipped_boxes(boxes,xyz,grid)]); occ.append((bool(cq) and not free) or part==0)
        if cq:
            obj=AS/'models/stage/v3'/f'{ckey}_{part}.obj'; mp=AS/'models/block/stage'/f'{ckey}_{part}_0.json'
            write_obj(obj,cq); P.dump(mp,model_json(obj))
        model='concertstage:block/stage/'+f'{ckey}_{part}_0' if cq and not free else 'concertstage:block/stage/empty'
        for turn,facing in enumerate(['north','east','south','west']):
            apply={'model':model}
            if turn: apply['y']=turn*90
            parts.append({'when':{'facing':facing,'part':str(part)},'apply':apply})
    P.dump(AS/'blockstates'/(key+'.json'),{'multipart':[{'apply':{'model':'concertstage:block/stage/empty'}},*parts]})
    if cfg.get('empty'): q=light if fest else kept      # l'item montre aussi la case libre
    obj=AS/'models/stage/v3/items'/(key+'.obj'); write_obj(obj,q); display=P.gui_transform(q); P.dump(AS/'models/item'/(key+'.json'),model_json(obj,display))
    raw=P.translate(q,-off) if not flip else q
    mq=[(sl,z) for sl,z in raw if z[:,1].min()>2.15]   # scène de festival : non montable, maillage dynamique jamais utilisé -> plancher seul
    if False: P.dump(AS/'stage_meshes'/(key+'.json'),   # V3 : jamais montés (non montables) -> maillage dynamique inutile (1.9.0)
{str(i):[[round(float(x),6) for x in (z if len(z)==4 else np.array([z[0],z[1],z[2],z[2]])).flatten()] for s,z in (mq if fest else raw) if s==i] for i in range(len(GLOBAL))})
    rb=np.concatenate([x[:,:3] for _,x in raw]); 
    dep=0.5 if '_half' in key else float(sz)
    spec={'id':key,'grid':grid,'width':float(sx),'depth':dep,'walk':cfg['walk'],'mountable':False,'kind':cfg['kind'],'mode':'','occupied':occ,'cells':cells,
          'raw_boxes':[[round(float(b[i])-(0 if flip else off[i%3]),7) for i in range(6)] for b in boxes],'raw_bounds':list(map(float,np.r_[rb.min(0),rb.max(0)])),'gui':display['gui'],'csc_v3':True}
    return key,spec,{'id':key,'source':stem,'sha256':hashlib.sha256(src.read_bytes()).hexdigest(),'grid':grid,'quads':len(q),'occupied':int(sum(occ)),'cells':count}

def main():
    shutil.copy2(SRC/'csc_v3_atlas.png',AS/'textures/block/stage/csc_v3_atlas.png')
    path=DATA/'stage_equipment.json'; data=json.loads(path.read_text())
    keep=[e for e in data['equipment'] if not e['id'].startswith('csc_')]; specs=[]; audit=[]
    stems=sorted([p.stem for p in (SRC/'Models').glob('*.bbmodel')]+list(ALIASES))
    stems=[x for x in stems if not x.startswith(('CSC_Tower_','CSC_StageDeck_Tower','CSC_StageDeck_Sleeve'))]   # supprimés (1.17.0)
    for stem in stems:
        key,spec,a=export(stem); specs.append(spec); audit.append(a); print('V3',key,spec['grid'],a['quads'],'quads',flush=True)
    data['equipment']=keep+specs; P.dump(path,data); P.dump(ROOT/'tools/platform_docs/scaffold_v3_integration.json',audit)
    print('Intégrés',len(specs))
    import removed_blocks; removed_blocks.purge()   # blocs supprimés définitivement (1.17.0)
    import multipart_to_variants; multipart_to_variants.main()   # blockstates en variants (mémoire au chargement)
if __name__=='__main__': main()
