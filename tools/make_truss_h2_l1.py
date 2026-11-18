#!/usr/bin/env python3
"""Ajoute le panneau CSC BoxTruss 100 de 1 × 2 m (truss_h2_l1, 1 bloc de long, 2 de haut), aluminium et noir.
Il complète une travée de panneaux 2 × 2 m quand l'écart entre deux tours (ou deux pièces) est impair.
Étapes : 1) avant refine_truss_models.py : repli vanilla (--fallback) ; 2) après : ressources (--resources)."""
import json, sys, copy
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).resolve().parent))
import integrate_platforms as p
ROOT=Path(__file__).resolve().parents[1]; A=p.AS; D=p.DATA
NEW='truss_h2_l1'; SRC='truss_h2_l2'
def dump(path,d,indent=None): path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(d,ensure_ascii=False,indent=indent))

def fallback():
    """Éléments vanilla (Fabric / sans OBJ) : ceux du 2 × 2 coupés à un bloc."""
    for suf in ('','_noir'):
        d=json.loads((A/'models/block'/(SRC+suf+'.json')).read_text()); out=[]; diag_seen=set()
        for e in d['elements']:
            e=copy.deepcopy(e); f,t=e['from'],e['to']; rot=e.get('rotation'); cx=(f[0]+t[0])/2
            if t[0]-f[0]>20:                                    # membrure le long de x : coupée à 16
                t[0]=15.95
                if rot: rot['origin'][0]=8.0
            elif rot and rot['axis'] in('y','z') and abs(rot['angle'])!=45:   # diagonale de face : une seule, recentrée
                if cx>16: continue
                dx=8-cx; f[0]+=dx; t[0]+=dx; rot['origin'][0]+=dx
            elif rot and rot['axis']=='y' and t[0]-f[0]>16:     # diagonale horizontale : une seule, raccourcie au bloc
                if cx>16: continue
                L=(16-2*1.5)*2**.5; c=8; f[0]=c-L/2; t[0]=c+L/2; rot['origin'][0]=c; rot['angle']=-45
            elif cx>16:                                          # cadre et raccords du bout : reculés d'un bloc
                f[0]-=16; t[0]-=16
                if rot: rot['origin'][0]-=16
            out.append(e)
        d['elements']=out; d['model']='concertstage:models/rigging/'+NEW+suf+'.obj'
        dump(A/'models/block'/(NEW+suf+'.json'),d)
        xs=[x for e in out for x in (e['from'][0],e['to'][0])]; print('repli',NEW+suf,len(out),'éléments, x',min(xs),max(xs))

def resources():
    import improve_inventory as inv
    import build_stage_connections as bsc
    specs=json.loads((D/'stage_equipment.json').read_text()); eq=[s for s in specs['equipment'] if not s['id'].startswith(NEW)]
    cat={r['name']:r for r in json.loads((ROOT/'tools/realistic_docs/catalogue.json').read_text()) if 'connections' in r}
    pick=ROOT/'common/src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json'; pk=json.loads(pick.read_text())
    for suf in ('','_noir'):
        key=NEW+suf; src=SRC+suf
        bs=json.loads((A/'blockstates'/(src+'.json')).read_text())
        dump(A/'blockstates'/(key+'.json'),json.loads(json.dumps(bs).replace('concertstage:block/'+src+'"','concertstage:block/'+key+'"')),2)
        obj=A/'models/rigging'/(key+'.obj'); q=inv.read_obj(obj)
        item=json.loads((A/'models/item'/(src+'.json')).read_text()); item['model']='concertstage:models/rigging/'+key+'.obj'; item['display']=p.gui_transform(q)
        dump(A/'models/item'/(key+'.json'),item)
        loot=json.loads((D/'loot_tables/blocks'/(src+'.json')).read_text())
        dump(D/'loot_tables/blocks'/(key+'.json'),json.loads(json.dumps(loot).replace('concertstage:'+src+'"','concertstage:'+key+'"')),2)
        rec=({'type':'minecraft:crafting_shapeless','category':'building','ingredients':[{'item':'concertstage:'+SRC}],'result':{'item':'concertstage:'+NEW,'count':2}} if not suf else
             {'type':'minecraft:crafting_shapeless','category':'building','ingredients':[{'item':'concertstage:'+NEW},{'item':'minecraft:black_dye'}],'result':{'item':'concertstage:'+key,'count':1}})
        dump(D/'recipes'/(key+'.json'),rec,2)
        pk['values']=list(dict.fromkeys(pk['values']+['concertstage:'+key]))
        # spécification de connexion (comme build_stage_connections pour les truss)
        groups={}
        for port in cat[key]['connections']:
            pt=np.array(port['point'])/16; ax=port['axis']; groups.setdefault((ax,round(float(pt[ax]),6)),[]).append(pt.tolist())
        ports=[]
        for (ax,plane),pts in groups.items():
            n=np.zeros(3); n[ax]=-1 if plane==0 else 1; ports.append({'center':np.mean(pts,axis=0).tolist(),'normal':n.tolist(),'points':pts})
        s=bsc.raw(key,None,q,'truss',ports=ports); mesh=p.mesh_json(q)
        p.dump(A/'stage_meshes'/(key+'.json'),{str(i):[] for i in range(3)}|{str(4 if suf else 3):mesh['0']})
        i=next(j for j,x in enumerate(eq) if x['id']==src); eq.insert(i+1,s)
        for lang,name in (('fr_fr','CSC BoxTruss 100 — panneau 1 × 2 m'),('en_us','CSC BoxTruss 100 — 1 × 2 m panel')):
            lp=A/'lang'/(lang+'.json'); ld=json.loads(lp.read_text()); ld['block.concertstage.'+key]=name+((' (noir)' if lang=='fr_fr' else ' (black)') if suf else ''); dump(lp,ld,2)
    specs['equipment']=eq; p.dump(D/'stage_equipment.json',specs); dump(pick,pk,2)
    print('ressources',NEW,'ok')
if __name__=='__main__':
    {'--fallback':fallback,'--resources':resources}[sys.argv[1]]()
