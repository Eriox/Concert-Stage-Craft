#!/usr/bin/env python3
"""Audit actual mesh/UV data, connectors, resource contracts and baseline world/control compatibility.
No claim of Minecraft gameplay or old-world loading is made by this data audit.
"""
from pathlib import Path
import json,math,base64,io,re,argparse,zipfile
from PIL import Image
root=Path(__file__).resolve().parents[1];bb=root/'tools/blockbench/realistic';assets=root/'common/src/main/resources/assets/concertstage';checks=0

def check(ok,msg):
 global checks
 checks+=1
 if not ok:raise AssertionError(msg)
def read(p):return json.loads(p.read_text())
files=sorted(bb.rglob('*.bbmodel'));check(len(files)==82,"82 complete models (80 + BoxTruss 60 1 m alu / noir, aurex.2)")
for p in files:
 d=read(p);check(d['meta']['model_format']=='free','native free mesh format');ids={e['uuid']for e in d['elements']};check(len(ids)==len(d['elements']),'unique elements');seen=[];groups=set()
 def walk(nodes):
  for n in nodes:
   if isinstance(n,str):seen.append(n)
   else:check(n['uuid']not in groups,'unique groups');groups.add(n['uuid']);walk(n['children'])
 walk(d['outliner']);check(set(seen)==ids and len(seen)==len(ids),'all elements grouped exactly once')
 for t in d['textures']:
  im=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1])));check(im.size==(512,512),'embedded atlas')
 for e in d['elements']:
  check(e['type']=='mesh'and len(e['vertices'])>=3,'editable geometry')
  for v in e['vertices'].values():check(len(v)==3 and all(math.isfinite(x)for x in v),'finite vertex')
  for f in e['faces'].values():
   check(3<=len(f['vertices'])<=4 and len(set(f['vertices']))==len(f['vertices']),'valid polygon');check(f['texture']==0 and all(k in e['vertices']and k in f['uv']for k in f['vertices']),'textured faces');check(all(0<=v<=512 for uv in f['uv'].values()for v in uv),'atlas UV bounds')
 for a in d.get('animations',[]):check(a['length']>0 and all(k in groups for k in a['animators']),'valid animated groups')
def port_matches(e,point,axis):
 vs=list(e['vertices'].values());return all(abs(sum(v[i]for v in vs)/len(vs)-point[i])<1e-4 for i in range(3)if i!=axis)and min(abs(min(v[axis]for v in vs)-point[axis]),abs(max(v[axis]for v in vs)-point[axis]))<1e-4
for row in read(root/'tools/realistic_docs/catalogue.json'):
 if 'connections'not in row:continue
 d=read(bb/'trusses'/(row['name']+'.bbmodel'));ports=[e for e in d['elements']if 'douille conique'in e['name']];check(len(ports)==len(row['connections']),'physical connector count')
 for c in row['connections']:check(any(port_matches(e,c['point'],c['axis'])for e in ports),'connector on legacy attachment grid')
for p in (bb/'carriages').glob('*.bbmodel'):
 d=read(p);kind=p.stem.removeprefix('carriage_').removesuffix('_noir');rows=[1.5,14.5,17.5,30.5]if kind.endswith('h2')else[1.5,14.5];dirs=[(0,0),(0,16),(2,0),(2,16)]if 'corner6'in kind else[(0,16),(2,16)];ports=[e for e in d['elements']if 'douille conique'in e['name']];check(len(ports)==len(rows)*2*len(dirs),'sleeve ports')
 for axis,plane in dirs:
  for y in rows:
   for cross in[1.5,14.5]:
    point=[cross,y,cross];point[axis]=plane;check(any(port_matches(e,point,axis)for e in ports),'sleeve matches truss')
for p in (assets/'rigging').glob('*.json'):
 d=read(p)
 def quads(rows):
  for q in rows:
   check(len(q)==32 and all(math.isfinite(x)for x in q),'runtime quad layout')
   for i in range(0,32,8):check(.95<sum(x*x for x in q[i+5:i+8])<1.05,'unit normal')
 if 'cells'in d:
  check(len(d['cells'])==d['height'],'one mesh per cell')
  for c in d['cells']:quads(c['near']);quads(c['far']);check(len(c['near'])>len(c['far']),'LOD reduction')
 else:
  for key in ['near','far','a','b']:
   if key in d:quads(d[key])
for p in (assets/'models/block').glob('truss*.json'):
 d=read(p)
 if d.get('loader')=='forge:obj':
  obj=assets/d['model'].split(':',1)[1];check(obj.is_file(),'OBJ exists');mtl=next(x[7:]for x in obj.read_text().splitlines()if x.startswith('mtllib '));check((obj.parent/mtl).is_file()and 'map_Kd #detail'in(obj.parent/mtl).read_text(),'MTL texture reference');check('elements'in d,'vanilla fallback retained')
recipes={p.stem:read(p)for p in(root/'common/src/main/resources/data/concertstage/recipes').glob('*.json')}
def ingredients(d):return tuple(sorted(json.dumps(x,sort_keys=True)for x in d.get('ingredients',[])))
for name in ['truss_x30d_l1','truss_ladder_l1','truss_ladder_l2','truss_ladder_v2','truss_junction_90','truss_junction_t','truss_junction_cross']:
 for suffix in['','_noir']:
  k=name+suffix;recipe=recipes[k];check(recipe['result']['item']=='concertstage:'+k,'recipe result');check(len(read(assets/'blockstates'/(k+'.json'))['variants'])==108,'multipart variants');check((assets/'models/item'/(k+'.json')).is_file(),'item exists');check((root/'common/src/main/resources/data/concertstage/loot_tables/blocks'/(k+'.json')).is_file(),'loot exists')
  if recipe['type']=='minecraft:crafting_shapeless':check(all(ingredients(o)!=ingredients(recipe)for ok,o in recipes.items()if ok!=k and o.get('type')==recipe['type']),'unambiguous recipes')
p=argparse.ArgumentParser();p.add_argument('--baseline');args=p.parse_args()
if args.baseline:
 with zipfile.ZipFile(args.baseline)as z:
  prefix=next(n[:-len('common/src/main/java/com/concertstage/StageContent.java')]for n in z.namelist()if n.endswith('/common/src/main/java/com/concertstage/StageContent.java'));oldjava=z.read(prefix+'common/src/main/java/com/concertstage/StageContent.java').decode();newjava=(root/'common/src/main/java/com/concertstage/StageContent.java').read_text();old=set(re.findall(r'public static final Block .*?;',oldjava,re.S));new=set(re.findall(r'public static final Block .*?;',newjava,re.S));check(len(old)>20 and {x for x in old if '"scaffold_stage_'not in x}<={x for x in new if '"scaffold_stage_'not in x},'non-platform block identifiers, dimensions and placements retained')
  unchanged=['common/src/main/java/com/concertstage/block/TowerBlockEntity.java','common/src/main/java/com/concertstage/block/MotorTowerBlock.java']+[f'common/src/main/java/com/concertstage/{name}.java'for name in ['lift/LiftControl','lift/LiftStructureEntity','client/TowerRenderer','client/RiggingMeshes','client/LiftStructureRenderer','client/MovingBlockEntityRenderer','block/HoistBlock','block/CurtainClothBlock','block/CurtainRailBlock','angle/AnglePlacement','item/AngleToolItem']]
  for n in z.namelist():
   rel=n.removeprefix(prefix)
   if n.endswith('/'):continue
   if rel.startswith(('common/src/main/resources/data/concertstage/recipes/','common/src/main/resources/data/concertstage/loot_tables/','common/src/main/resources/assets/concertstage/blockstates/'))and not Path(rel).stem.startswith('scaffold_stage_')and Path(rel).stem not in {'stage_riser','stage_stairs'}:check(json.loads(z.read(n))==read(root/rel),'preserved non-platform resource unchanged '+rel)
   if rel.endswith('.java')and ('/net/'in rel or'Remote'in rel):unchanged.append(rel)
  for rel in unchanged:check(z.read(prefix+rel)==(root/rel).read_bytes(),'saved data/controls untouched '+rel)
  for lang in['en_us','fr_fr']:
   rel='common/src/main/resources/assets/concertstage/lang/'+lang+'.json';new=read(root/rel);check(all(new.get(k)==v for k,v in json.loads(z.read(prefix+rel)).items()if not k.startswith('block.concertstage.scaffold_stage_')and k!='itemGroup.concertstage'),'non-platform language entries preserved')
print(checks,'mesh, texture, connection, LOD, resource and compatibility assertions passed across',len(files),'models')
