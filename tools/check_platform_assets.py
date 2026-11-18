#!/usr/bin/env python3
"""Resource graph, unchanged source models, collision walk surfaces, inventory projections."""
import json,math,re,hashlib,zipfile
from pathlib import Path
import numpy as np
import integrate_platforms as p
import improve_inventory as inv
R=p.ROOT;A=p.AS;D=p.DATA
checks=0
def check(ok,msg):
 global checks;checks+=1
 if not ok:raise AssertionError(msg)
def walk_boxes(s):
 out=[];sx,sy,sz=s['grid']
 for i,vs in enumerate(s['cells']):
  cell=np.array([i%sx,i//(sx*sz),(i//sx)%sz]);out.extend(np.array(b)+np.r_[cell,cell]for b in vs[0])
 return np.array(out)
def main():
 specs=json.loads((D/'stage_equipment.json').read_text())['equipment'];check(len([s for s in specs if not s['id'].startswith('csc_')])==119,'connection catalogue 119 definitions (+ CSC V3; frame removed, 12 FlexDeck added in 1.18.0)')
 source_report={x['id']:x for x in json.loads((p.DOC/'model_integration.json').read_text())}
 for s in specs:
  key=s['id'];check(len(s['cells'])==math.prod(s['grid']),'cell count '+key)
  check(len(s['occupied'])==len(s['cells']),'occupied flags '+key)
  for variants in s['cells']:
   for boxes in variants:
    for b in boxes:check(len(b)==6 and all(math.isfinite(x)for x in b)and all(0<=b[i]<b[i+3]<=1.000001 for i in range(3)),'collision bounds '+key)
  if s.get('internal')or s.get('connection_generated')or s.get('generated')or key.startswith('csc_'):continue   # FlexDeck : make_flexdecks.py   # CSC V3 : vérifiés par integrate_scaffold_v3 / check_platform_structure
  if s['kind']=='legacy':
   check(not(D/'recipes'/(key+'.json')).exists(),'no scaffold recipe');check(not(A/'models/block'/(key+'.json')).exists(),'old scaffold model removed');continue
  data=json.loads((p.SOURCE/'Models'/(key+'.bbmodel')).read_text());check(hashlib.sha256((p.SOURCE/'Models'/(key+'.bbmodel')).read_bytes()).hexdigest()==source_report[key]['source_sha256'],'untouched source '+key)
  # Every face still has the supplied atlas/UV/rotation; compare source OBJ with editable BB geometry.
  q=p.obj_quads(p.SOURCE/'Exports_OBJ'/(key+'.obj'));bb=p.cube_quads(data);check(len(q)==len(bb)==6*len(data['elements']),'all detailed faces '+key)
  check(all(a[0]==b[0]and np.allclose(a[1],b[1],atol=1e-6)for a,b in zip(q,bb)),'UV and geometry preservation '+key)
  check(len(data['textures'])==3,'three source textures '+key)
  if s['walk']>0 and s['kind']not in['stairs']:
   boxes=walk_boxes(s)
   # Sample walking areas away from bolts, edge lips, and the DJ booth itself.
   for x in np.arange(.15,s['width'],.25):
    for z in np.arange(.15,s['depth'],.25):
     if s['id'].startswith('dj_')and z<.5:continue
     ok=any(b[0]-1e-6<=x<=b[3]+1e-6 and b[2]-1e-6<=z<=b[5]+1e-6 and abs(b[4]-s['walk'])<.003 for b in boxes)
     check(ok,'continuous walking surface '+key+f' at {x},{z}')
  if s['kind']=='stairs':
   boxes=walk_boxes(s);steps=round(s['walk']/.2)
   for n in range(steps):
    z=n*.3+.15;h=(n+1)*.2
    check(any(b[0]<.5<b[3]and b[2]<z<b[5]and abs(b[4]-h)<.003 for b in boxes),'stair tread '+key+f' {n}')
  item=json.loads((A/'models/item'/(key+'.json')).read_text());gui=item['display']['gui'];vs=np.concatenate([q[:,:3]for _,q in inv.read_obj(A/item['model'].split(':')[1])]);check_gui(vs,gui,key)
  loot=json.loads((D/'loot_tables/blocks'/(key+'.json')).read_text());check(len(loot['pools'])==1,'one item drop pool '+key)
  for locale in['en_us','fr_fr']:
   lang=json.loads((A/'lang'/(locale+'.json')).read_text())
   for k in['block.concertstage.'+key,'tooltip.concertstage.dimensions.'+key,'tooltip.concertstage.use.'+key]:check(k in lang,'localized '+k)
 for row in json.loads((p.DOC/'inventory_models.json').read_text()):
  item=json.loads((A/'models/item'/(row['id']+'.json')).read_text())
  if 'model' not in item:check(item.get('parent','').startswith('concertstage:block/truss'),'truss item uses vanilla block model '+row['id']);continue
  q=inv.read_obj(A/item['model'].split(':')[1]);check_gui(np.concatenate([q[:,:3]for _,q in q]),item['display']['gui'],row['id']);check(row['quads']>0,'real tower/truss geometry')
 # Walk every generated reference, including inherited vanilla fallbacks and OBJ materials.
 missing=[]
 for f in A.rglob('*.json'):
  d=json.loads(f.read_text())
  def resource(v,base,suffix):
   if not isinstance(v,str)or not v.startswith('concertstage:'):return
   relative=v.split(':',1)[1];path=A/base/(relative+suffix)
   if not path.exists():missing.append([str(f.relative_to(A)),str(path.relative_to(A))])
  if f.parent.name=='blockstates':
   def visit(v):
    if isinstance(v,dict):
     if'model'in v:resource(v['model'],'models','.json')
     for x in v.values():visit(x)
    elif isinstance(v,list):
     for x in v:visit(x)
   visit(d)
  if 'models'in f.relative_to(A).parts and f.suffix=='.json':
   resource(d.get('parent'),'models','.json')
   for tex in d.get('textures',{}).values():resource(tex,'textures','.png')
   if d.get('loader')=='forge:obj':
    resource(d.get('model'),'','');obj=A/d['model'].split(':',1)[1]
    if obj.exists():
     libs=[line.split(maxsplit=1)[1]for line in obj.read_text().splitlines()if line.startswith('mtllib ')];check(bool(libs),'OBJ material library '+str(obj))
     for name in libs:check((obj.parent/name).exists(),'OBJ material file '+name)
 check(not missing,'Missing resources: '+json.dumps(missing[:30]))
 # Recipe ingredients never mention removed or imaginary equipment.
 ids=set(re.findall(r'block\("([^"\n]+)"',(R/'common/src/main/java/com/concertstage/StageContent.java').read_text()))|set(p.NAMES)
 recipe_sets={}
 for f in(D/'recipes').glob('*.json'):
  d=json.loads(f.read_text());values=[]
  if d.get('type')=='minecraft:crafting_shapeless':
   signature=tuple(sorted(json.dumps(x,sort_keys=True)for x in d['ingredients']));check(signature not in recipe_sets,'unambiguous crafting recipe '+f.stem);recipe_sets[signature]=f.stem
  def walk(x):
   if isinstance(x,dict):
    if'item'in x:values.append(x['item'])
    for v in x.values():walk(v)
   elif isinstance(x,list):
    for v in x:walk(v)
  walk(d)
  for v in values:
   if v.startswith('concertstage:'):check((A/'models/item'/(v.split(':')[1]+'.json')).exists(),'registered recipe item '+v)
 report={'assertions':checks,'models':22,'rigging_item_models':68,'missing_resource_references':missing,'scope':'Static resource/collision/projection checks; not a Minecraft runtime test.'};p.dump(p.DOC/'asset_validation.json',report);print(json.dumps(report))
def check_gui(vs,g,key):
 rx,ry,rz=np.radians(g['rotation']);cx,sx,cy,sy=math.cos(rx),math.sin(rx),math.cos(ry),math.sin(ry);R=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])
 v=((vs-.5)*g['scale'])@R.T+np.array(g['translation'])/16
 check(np.max(np.abs(v[:,:2]))<.431,'uncropped GUI '+key);check(np.max(np.abs((v.max(axis=0)+v.min(axis=0))[:2]))<1e-5,'centred GUI '+key)
if __name__=='__main__':main()
