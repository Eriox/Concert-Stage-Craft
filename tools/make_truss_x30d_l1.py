#!/usr/bin/env python3
"""Generate only the 1 m TriTruss resources; never rewrite existing blocks."""
import copy,json
from pathlib import Path
import refine_truss_models as rig
import match_truss_lighting as lighting
import improve_inventory as inventory
ROOT=Path(__file__).resolve().parents[1]
AS=ROOT/'common/src/main/resources/assets/concertstage'
DATA=ROOT/'common/src/main/resources/data/concertstage'
def dump(p,d):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
def main():
    for black in (False,True):
        suffix='_noir' if black else ''
        key='truss_x30d_l1'+suffix
        old='truss_x30d_l2'+suffix
        mesh=rig.triangle('truss_x30d_l1',L=16)
        rig.export_block('truss_x30d_l1',mesh,black)
        fallback=json.loads((AS/'models/block'/(old+'.json')).read_text())
        for e in fallback.get('elements',[]):
            e['from'][0]/=2;e['to'][0]/=2
            if 'rotation' in e:e['rotation']['origin'][0]/=2
        model=json.loads((AS/'models/block'/(key+'.json')).read_text())
        model['elements']=fallback.get('elements',[])
        dump(AS/'models/block'/(key+'.json'),model)
        variants={}
        for k,facing in enumerate(('north','east','south','west')):
            lit=key+'_lit_r'+str(k)
            lighting.bake(AS/'models/rigging'/(key+'.obj'),AS/'models/rigging/lit'/(lit+'.obj'),k)
            litmodel=copy.deepcopy(model)
            litmodel['model']='concertstage:models/rigging/lit/'+lit+'.obj'
            litmodel['shade_quads']=False
            litmodel['elements']=lighting.rot_elements(model['elements'],k)
            dump(AS/'models/block'/(lit+'.json'),litmodel)
            for part in range(27):
                variants[f'facing={facing},part={part}']={'model':'concertstage:block/'+(lit if part==0 else 'empty_truss')}
        dump(AS/'blockstates'/(key+'.json'),{'variants':variants})
        obj=AS/'models/rigging'/(key+'.obj')
        inventory.item(key,obj,inventory.read_obj(obj),black)
        loot=json.loads((DATA/'loot_tables/blocks'/(old+'.json')).read_text())
        dump(DATA/'loot_tables/blocks'/(key+'.json'),json.loads(json.dumps(loot).replace(old,key)))
        dump(DATA/'recipes'/(key+'.json'),{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'concertstage:'+old}],'result':{'item':'concertstage:'+key,'count':2}})
        dump(DATA/'recipes'/(old+'_from_l1.json'),{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'concertstage:'+key}]*2,'result':{'item':'concertstage:'+old}})
        if black:
            dump(DATA/'recipes'/(key+'_dye.json'),{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'concertstage:truss_x30d_l1'},{'item':'minecraft:black_dye'}],'result':{'item':'concertstage:'+key}})
        for lang in ('fr_fr','en_us'):
            p=AS/'lang'/(lang+'.json');d=json.loads(p.read_text())
            d['block.concertstage.'+key]='CSC TriTruss 30 — 1 m'+((' (noir)' if lang=='fr_fr' else ' (black)') if black else '')
            dump(p,d)
        catalogue=ROOT/'tools/realistic_docs/catalogue.json'
        rows=json.loads(catalogue.read_text())
        rows=[row for row in rows if row['name']!=key]
        rows.append({'name':key,'parts':len(mesh.elements),'quads':len(mesh.quads()),'connections':mesh.connections})
        dump(catalogue,rows)
        print(key,'generated')
if __name__=='__main__':main()
