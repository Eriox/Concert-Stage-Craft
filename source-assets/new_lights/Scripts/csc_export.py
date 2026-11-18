"""Blender 5.3 backend for export_fixtures.py --new-lights.
Exports supplied evaluated geometry and LODs into the existing CSC mesh schema.
No source .blend is saved or changed; manual child pivots are baked at their supplied rest pose.

Command line (all the .blend of a folder):  blender -b --python export_new_lights_blender.py -- <blend folder> <assets folder>
From a Blender session:                     export_new_lights_blender.run(src, dst, names=[...])   (merges into the existing report)
"""
import bpy,json,sys,hashlib,math
from pathlib import Path
from mathutils import Vector
Q=4096
# Front lens radius of the single-optic fixtures (m). VORTEX X1: 1.2.0 remodel, the beam leaves the whole front lens.
LENS={'CSC_PHANTOM_S1':.098,'CSC_PROFILE_P1':.078,'CSC_RAZOR_B1':.094,'CSC_FOLLOWSPOT_F1':.08,'CSC_LASERCORE_L1':.005,'CSC_VORTEX_X1':.071}

def mc(p):return [-p.x,p.z,p.y]
def linear(c):return 12.92*c if c<=.0031308 else 1.055*c**(1/2.4)-.055
def rgb(mat):
 c=mat.diffuse_color[:3] if mat else (.04,.04,.04)
 if mat and mat.use_nodes:
  n=next((n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED'),None)
  if n:c=n.inputs['Base Color'].default_value[:3]
 return ''.join('%02x'%max(0,min(255,round(linear(x)*255))) for x in c)
def desc(o):
 return dict(name=o.name,parent=o.parent.name if o.parent else None,type=o.type,collections=[c.name for c in o.users_collection],world=[list(row) for row in wm(o)])
def wm(o):
 # World matrix from the parent chain. Not o.matrix_world: the LOD1 / LOD2 collections are hidden in the files, the dependency
 # graph does not evaluate their objects and their matrix_world stays without the pivot they hang from (the far levels of
 # detail were exported one pivot too low before this was fixed).
 m=o.matrix_basis.copy()
 return wm(o.parent)@o.matrix_parent_inverse@m if o.parent else m
def ancestor(o,name):
 while o:
  if o.name==name:return True
  o=o.parent
 return False

def export_one(path,dst):
 bpy.ops.wm.open_mainfile(filepath=str(path.resolve()),load_ui=False)
 obs=bpy.data.objects; root=obs['ROOT'];pan=obs.get('PAN');tilt=obs.get('TILT') or obs.get('ROLL') or root
 pivot={'base':wm(root),'yoke':wm((pan or root)),'head':wm(tilt)}
 inv={k:v.inverted() for k,v in pivot.items()}
 data=dict(id=path.stem.lower(),quant=Q,parts={},lod_far={},lod_low={},pivots={'pan':mc(wm(pan).translation) if pan else None,'tilt':mc(wm(tilt).translation-(wm(pan).translation if pan else Vector((0,0,0))))},zones={},palette_white_uv=[.5/16,.5/16])
 stats={}; inventory=[];cells=[]
 for o in sorted(obs,key=lambda x:x.name):
  if not ancestor(o,'ROOT'):continue
  inventory.append(desc(o))
  if o.type!='MESH':continue
  names=[c.name for c in o.users_collection];lod=2 if 'LOD2' in names else 1 if 'LOD1' in names else 0
  key=['parts','lod_far','lod_low'][lod]
  part='head' if ancestor(o,tilt.name) and tilt!=root else 'yoke' if pan and ancestor(o,'PAN') else 'base'
  groups=data[key].setdefault(part,{'static':{}})['static']
  ev=o.evaluated_get(bpy.context.evaluated_depsgraph_get());me=ev.to_mesh();me.calc_loop_triangles()
  mat=inv[part]@wm(o)
  verts=[mc(mat@v.co) for v in me.vertices]
  # determinant of (-X,Z,Y) is positive: triangle winding is preserved.
  for tri in me.loop_triangles:
   material=me.materials[tri.material_index] if len(me.materials)>tri.material_index else None
   col=rgb(material);arr=groups.setdefault(col,[])
   arr.extend(round(c*Q) for i in tri.vertices for c in verts[i])
  if lod==0 and 'LED_CELLS' in names:
   pts=[mc(inv['head']@wm(o)@v.co) for v in me.vertices]
   lo=[min(p[k] for p in pts) for k in range(3)];hi=[max(p[k] for p in pts) for k in range(3)]
   kind='white' if path.stem.startswith('CSC_BLINDER') or o.name.startswith('STROBE_') else 'rgb' if o.name.startswith('RGB_') else 'cell'
   # The emitting face is the frontmost plane, not the centre of the reflector's housing.
   cells.append(dict(name=o.name,kind=kind,centre=[(lo[0]+hi[0])/2,(lo[1]+hi[1])/2,lo[2]],half=[(hi[0]-lo[0])/2,(hi[1]-lo[1])/2]))
  ev.to_mesh_clear()
 # RGB banks first, white strips next; numeric ordering follows supplied cell names.
 cells.sort(key=lambda z:({'rgb':0,'white':1,'cell':2}[z['kind']],z['name']))
 data['zones']={str(i):z for i,z in enumerate(cells)}
 emit=obs.get('EMIT_MAIN') or obs.get('EMIT_APERTURE') or obs.get('EMIT_STROBE')
 if emit:origin=mc(inv['head']@wm(emit).translation)
 elif cells:origin=[sum(c['centre'][k] for c in cells)/len(cells) for k in range(3)]
 else:raise ValueError('No emission point '+path.stem)
 radius=LENS.get(path.stem,.025)
 if cells:radius=max(math.hypot(c['centre'][0]-origin[0],c['centre'][1]-origin[1])+max(c['half']) for c in cells)
 data['beam']=dict(origin=origin,axis=[0,0,-1],lens_radius=radius)
 if not cells:data['zones']={'0':dict(name='Main lens',kind='cell',centre=origin,half=[radius,radius])}
 for key in ['parts','lod_far','lod_low']:
  for part in ['base','yoke','head']:
   if part not in data[key] and (part!='yoke' or pan):data[key][part]={'static':{}}
  stats[key]=sum(len(a)//9 for v in data[key].values() for a in v['static'].values())
  assert stats[key]>0,(path,key)
 out=dst/'lightshow'/f'{path.stem.lower()}.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(data,separators=(',',':')))
 print(path.stem,stats,'zones',len(cells),flush=True)
 return dict(source_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),triangles=stats,zones=len(cells),pivots=data['pivots'],beam=data['beam'],objects=inventory,materials=[dict(name=m.name,rgb=rgb(m)) for m in bpy.data.materials],bytes=out.stat().st_size)

def run(src,dst,names=None):
 src=Path(src);dst=Path(dst);rp=dst/'lightshow/new_lights_export_report.json'
 report=json.loads(rp.read_text()) if names and rp.exists() else {}
 for path in sorted(src.glob('*.blend')):
  if names and path.stem not in names:continue
  report[path.stem.lower()]=export_one(path,dst)
 rp.parent.mkdir(parents=True,exist_ok=True);rp.write_text(json.dumps(report,indent=1))
 return report

if __name__=='__main__' and '--' in sys.argv:
 run(*sys.argv[sys.argv.index('--')+1:][:2])
