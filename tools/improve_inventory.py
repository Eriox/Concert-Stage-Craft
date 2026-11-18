#!/usr/bin/env python3
"""Static 3D item models, normalized from actual vertices, including full motor tower silhouettes."""
import json,re,math
import numpy as np
import integrate_platforms as p
A=p.AS

def read_obj(path):
 v=[];uv=[];ns=[];q=[]
 for line in path.read_text().splitlines():
  x=line.split()
  if not x:continue
  if x[0]=='v':v.append(list(map(float,x[1:4])))
  elif x[0]=='vt':uv.append([float(x[1]),1-float(x[2])])
  elif x[0]=='vn':ns.append(list(map(float,x[1:4])))
  elif x[0]=='f':
   rows=[]
   for z in x[1:]:
    vi,ti,ni=[int(y)-1 for y in z.split('/')];rows.append([*v[vi],*uv[ti],*ns[ni]])
   if len(rows)==3:rows.append(rows[-1])
   q.append((0,np.array(rows)))
 return q

def writer(path,quads):
 p.write_obj(path,quads)
 s=path.read_text().replace('mtllib stage_materials.mtl','mtllib inventory_rigging.mtl').replace('material_0','rigging');path.write_text(s)
 path.with_name('inventory_rigging.mtl').write_text('newmtl rigging\nKd 1 1 1\nKa 0.2 0.2 0.2\nKs 0 0 0\nmap_Kd #detail\n')

def item(key,obj,quads,black=False):
 d={'loader':'forge:obj','model':'concertstage:'+str(obj.relative_to(A)),'flip_v':True,'automatic_culling':False,'shade_quads':True,'textures':{'detail':'concertstage:block/rigging_detail'+('_noir'if black else''),'particle':'concertstage:block/rigging_detail'+('_noir'if black else'')},'elements':[],'render_type':'minecraft:cutout','gui_light':'side','display':p.gui_transform(quads,75 if key.startswith('motor_tower_')else 30)}
 p.dump(A/'models/item'/(key+'.json'),d)
 return {'id':key,'quads':len(quads),'gui':d['display']['gui'],'bounds':[np.concatenate([q[:,:3]for _,q in quads]).min(axis=0).tolist(),np.concatenate([q[:,:3]for _,q in quads]).max(axis=0).tolist()]}

def tower(height,kind):
 def load(name,prop):return[(0,np.array(q).reshape(4,8))for q in json.loads((A/'rigging'/(name+'.json')).read_text())[prop]]
 out=[]
 for cell in json.loads((A/'rigging'/f'mast_{height}.json').read_text())['cells']:out +=[(0,np.array(q).reshape(4,8))for q in cell['far']]
 out+=p.translate(load('carriage_'+kind,'far'),[0,16,0]);head=height*16-5;cells=2 if'h2'in kind else 1
 for name,delta in [('head_wheel',[8,head,-1.5]),('motor_wheel',[8,7,-2.75])]:out+=p.translate(load(name,'far'),delta)
 # Static distance-LOD chain ribbons, crossed in two planes, attached to the actual eye and sprocket.
 for x,low in [(6.5,11.5),(9.5,16+cells*16-1.8)]:
  for axis in [0,2]:
   points=[]
   for side,y in [(-1,low),(1,low),(1,head),(-1,head)]:
    v=[x,y,-1.5];v[axis]+=side*.28;points.append([*v,184/512 if side<0 else 200/512,128/512 if y==head else 256/512,*([0,0,1]if axis==0 else[1,0,0])])
   out.append((0,np.array(points)))
 for _,q in out:q[:,:3]/=16
 return out

def main():
 report=[]
 for obj in sorted((A/'models/rigging').glob('truss*.obj')):
  q=read_obj(obj);report.append(item(obj.stem,obj,q,obj.stem.endswith('_noir')))
 for h in [10,14,18,22]:
  for c in ['corner3','corner3_h2','corner6','corner6_h2']:
   q=tower(h,c);obj=A/'models/rigging/inventory'/f'motor_tower_{h}_{c}.obj';writer(obj,q)
   for suffix in['','_noir']:report.append(item(f'motor_tower_{h}_{c}{suffix}',obj,q,bool(suffix)))
 p.dump(p.DOC/'inventory_models.json',report);print('Normalized',len(report),'3D rigging inventory models')
if __name__=='__main__':main()
