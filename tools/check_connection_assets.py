from pathlib import Path
import json,math
import numpy as np
import integrate_platforms as p
R=p.ROOT;A=p.AS;D=p.DATA;checks=0
def check(v,msg):
 global checks;checks+=1
 if not v:raise AssertionError(msg)
def main():
 specs={s['id']:s for s in json.loads((D/'stage_equipment.json').read_text())['equipment']};rows=json.loads((p.DOC/'connection_models.json').read_text());check(len(rows)==2,'tall variants (stage_structure_h300_* removed in 1.17.0)')
 for row in rows:
  s=specs[row['id']];check(abs(row['height_after']-row['height_before']-(0.8 if row.get('lowered_in') else 1))<1e-7,'exact +1 (+0.8 since 1.17.3)');check(abs(s['walk']-row['height_after'])<1e-7,'collision height');d=json.loads((R/'tools/platform_derived'/(s['id']+'.bbmodel')).read_text());check(len(d['textures'])==3,'three materials')
  if 'source_cubes'in row:
   old=json.loads((p.SOURCE/'Models'/(row['from']+'.bbmodel')).read_text());check(len(old['elements'])==len(d['elements']),'parts conserved')
   for a,b in zip(old['textures'],d['textures']):check(a['source']==b['source'],'atlas unchanged')
   for a,b in zip(old['elements'],d['elements']):
    if a['name'].startswith('Panneau multiplis'):check(np.allclose(np.array(b['from'])-a['from'],[0,16,0])and np.allclose(np.array(b['to'])-a['to'],[0,16,0]),'horizontal deck +16')
    if max(a['from'][1],a['to'][1])/16<=.04:check(np.allclose(p.cube_vertices(a),p.cube_vertices(b)),'feet remain grounded')
  boxes=[];sx,sy,sz=s['grid']
  for i,vs in enumerate(s['cells']):
   cell=np.array([i%sx,i//(sx*sz),(i//sx)%sz]);boxes +=[np.array(b)+np.r_[cell,cell]for b in vs[0]]
  for x in np.arange(.25,s['width'],.5):
   for z in np.arange(.25,s['depth'],.5):
    if s['id'].endswith('_tour')and 1<x<2 and 1<z<2:continue
    check(any(b[0]<=x<=b[3]and b[2]<=z<=b[5]and abs(b[4]-s['walk'])<.003 for b in boxes),'walkable deck '+s['id'])
 for n in range(1,41):
  key=f'connected_stairs_{n:02d}';s=specs[key];d=json.loads((R/'tools/platform_derived/flights'/(key+'.bbmodel')).read_text());steps=[e for e in d['elements']if e['name'].startswith('Marche ')and 'tôle antidérapante'in e['name']];check(len(steps)==n,'tread count')
  for i,e in enumerate(steps):check(abs(e['to'][1]/16-(i+1)*.2)<1e-6,'20 cm rise')
  check(abs(steps[-1]['to'][2]/16-s['run'])<1e-6,'flush landing');check(len(s['feet'])==4+2*((n-1)//6),'intermediate supports')
 for key,s in specs.items():
  if not(s.get('internal')or s.get('connection_generated')):continue
  file=A/'stage_meshes'/(key+'.json');check(file.exists(),'mesh exists');data=json.loads(file.read_text());total=0
  for slot,quads in data.items():
   check(int(slot)<5,'material slot')
   for q in quads:
    total+=1;check(len(q)==32 and all(math.isfinite(x)for x in q),'finite quad')
    for i in range(0,32,8):check(abs(sum(v*v for v in q[i+5:i+8])-1)<.00002,'normal')
  check(total>0,'true 3D geometry')
  for b in s['raw_boxes']:check(len(b)==6 and all(math.isfinite(x)for x in b)and all(b[i]<b[i+3]for i in range(3)),'collision box')
  if s['kind']=='truss':check(bool(s['ports']),'connectors')
 out={'assertions':checks,'raised_variants':2,'flights':40,'adapters':6,'truss_variants':38,'editable_models':55,'minecraft_runtime':False};p.dump(p.DOC/'connection_assets_validation.json',out);print(json.dumps(out))
if __name__=='__main__':main()
