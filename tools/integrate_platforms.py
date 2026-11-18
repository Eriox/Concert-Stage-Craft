#!/usr/bin/env python3
"""Preserve supplied geometry/UVs. Bake spatial chunks and data-driven functional collision.
The only generated derivative models are hidden replacements for old 3x3 stages.
"""
from pathlib import Path
import argparse,base64,copy,hashlib,json,math,shutil
import numpy as np
ROOT=Path(__file__).resolve().parents[1];SOURCE=ROOT/'tools/platform_source';AS=ROOT/'common/src/main/resources/assets/concertstage';DATA=ROOT/'common/src/main/resources/data/concertstage';DOC=ROOT/'tools/platform_docs'
NAMES=['stage_deck_2x1_h020','stage_deck_2x1_h040','stage_deck_2x1_h080','stage_deck_2x1_without_legs','raised_stage_platform_2x1_h120','drum_riser_4x2_h060','musician_riser_2x1_h040','dj_platform_2x2_h080','stage_stairs_h040','stage_stairs_h080','stage_stairs_h120','safety_railings_1m','safety_railings_2m','safety_railings_corner_1x1','stage_support_structures_2x1_h111','adjustable_leg_h040','adjustable_leg_h080','adjustable_leg_h120','stage_extension_1x1_h040','stage_extension_2x0p5_h040','assembly_solo_stage_4x2_h040','assembly_festival_stage_8x4_h120']
LABELS=[('Stage Deck — Low','Plateau scénique — bas'),('Stage Deck — Standard','Plateau scénique — standard'),('Stage Deck — Reinforced','Plateau scénique — renforcé'),('Stage Deck — Without Legs','Plateau scénique — sans pieds'),('Raised Stage Platform','Plateforme scénique surélevée'),('Drum Riser','Estrade de batterie'),('Musician Riser','Estrade pour musicien'),('DJ Platform & Booth','Plateforme et cabine DJ'),('Stage Stairs — 40 cm','Escalier scénique — 40 cm'),('Stage Stairs — 80 cm','Escalier scénique — 80 cm'),('Stage Stairs — 120 cm','Escalier scénique — 120 cm'),('Safety Railing — 1 m','Garde-corps — 1 m'),('Safety Railing — 2 m','Garde-corps — 2 m'),('Corner Safety Railing','Garde-corps d’angle'),('Stage Support Frame','Cadre porteur de scène'),('Removable Leg — 40 cm','Pied amovible — 40 cm'),('Telescopic Leg — 80 cm','Pied télescopique — 80 cm'),('Telescopic Leg — 120 cm','Pied télescopique — 120 cm'),('Stage Extension — 1 × 1 m','Extension de scène — 1 × 1 m'),('Stage Extension — 2 × 0.5 m','Extension de scène — 2 × 0,5 m'),('Solo Stage Assembly','Scène solo préassemblée'),('Festival Stage Assembly','Scène de festival préassemblée')]
LEGACY={'scaffold_stage_sans_barrieres':(), 'scaffold_stage_sud':('south',),'scaffold_stage_est':('east',),'scaffold_stage_ouest':('west',),'scaffold_stage_sud_est':('south','east'),'scaffold_stage_sud_tour':('south',),'scaffold_stage_sud_est_tour':('south','east')}
TEXTURES=['metals','anti_slip','hardware'];EPS=1e-7
FACES={'north':[5,4,7,6],'south':[0,1,2,3],'east':[1,5,6,2],'west':[4,0,3,7],'up':[7,3,2,6],'down':[0,4,5,1]}
def dump(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')))
def rotation(e):
 rx,ry,rz=np.radians(e.get('rotation',[0,0,0]));cx,sx,cy,sy,cz,sz=math.cos(rx),math.sin(rx),math.cos(ry),math.sin(ry),math.cos(rz),math.sin(rz)
 return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
def cube_vertices(e,a=None,b=None):
 a=np.array(e['from']if a is None else a,float)/16;b=np.array(e['to']if b is None else b,float)/16;o=np.array(e['origin'],float)/16;x,y,z=a;X,Y,Z=b
 return (np.array([[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z],[x,y,z],[X,y,z],[X,Y,z],[x,Y,z]])-o)@rotation(e).T+o

def cube_quads(d):
 out=[]
 for e in d['elements']:
  if not e.get('visibility',True)or not e.get('export',True):continue
  vs=cube_vertices(e)
  for side,ids in FACES.items():
   f=e['faces'][side];slot=f['texture'];t=d['textures'][slot];u0,v0,u1,v1=f['uv'];uv=[[u0,v1],[u1,v1],[u1,v0],[u0,v0]];uv=np.roll(uv,-f.get('rotation',0)//90,axis=0);p=vs[ids];n=np.cross(p[1]-p[0],p[2]-p[0]);n/=np.linalg.norm(n);rows=[[*v,u/t['uv_width'],w/t['uv_height'],*n]for v,(u,w)in zip(p,uv)];out.append((slot,np.array(rows)))
 return out

def obj_quads(path):
 vertices=[];uvs=[];normals=[];groups=[];slot=0;faces=[]
 for line in path.read_text().splitlines():
  p=line.split()
  if not p:continue
  if p[0]=='g':
   if faces:groups.append((slot,faces));faces=[]
  elif p[0]=='usemtl':
   if faces:groups.append((slot,faces));faces=[]
   slot=int(p[1].rsplit('_',1)[1])
  elif p[0]=='v':vertices.append(list(map(float,p[1:4])))
  elif p[0]=='vt':uvs.append(list(map(float,p[1:3])))
  elif p[0]=='vn':normals.append(list(map(float,p[1:4])))
  elif p[0]=='f':faces.append([tuple(int(k)-1 for k in x.split('/'))for x in p[1:]])
 if faces:groups.append((slot,faces))
 out=[]
 for slot,fs in groups:
  if len(fs)==2 and fs[0][0]==fs[1][0] and fs[0][2]==fs[1][1]:polys=[[fs[0][0],fs[0][1],fs[0][2],fs[1][2]]]
  else:polys=fs
  for poly in polys:
   rows=[]
   for vi,ui,ni in poly:
    u,v=uvs[ui];rows.append([*vertices[vi],u,1-v,*normals[ni]])
   out.append((slot,np.array(rows)))
 return out

def translate(quads,delta):
 d=np.array(delta);return[(slot,np.array([[*(v[:3]+d),*v[3:]]for v in q]))for slot,q in quads]
def transform(quads,origin=(0,0,0),turns=0):
 angle=turns*math.pi/2;c=round(math.cos(angle));s=round(math.sin(angle));out=[]
 for slot,q in quads:
  vs=q.copy();vs[:,0]=q[:,0]*c-q[:,2]*s+origin[0];vs[:,2]=q[:,0]*s+q[:,2]*c+origin[2];vs[:,1]+=origin[1];vs[:,5]=q[:,5]*c-q[:,7]*s;vs[:,7]=q[:,5]*s+q[:,7]*c;out.append((slot,vs))
 return out

def collision(d,offset):
 boxes=[];ignored=('boulon','rondelle','six pans','vis ','graduation','levier','broche','axe de serrage','accroche haute')
 i=0;elements=d['elements']
 while i<len(elements):
  e=elements[i];name=e['name'].lower()
  if any(x in name for x in ignored):i+=1;continue
  # Four tube walls can be one collision envelope: entities cannot enter a 40 mm bore.
  run=[e]
  if ' · paroi'in e['name'] and i+3<len(elements):
   prefix=e['name'].split(' · ')[0];candidate=elements[i:i+4]
   if all(x['name'].split(' · ')[0]==prefix for x in candidate):run=candidate
  if len(run)>1:
   vs=np.concatenate([cube_vertices(x)for x in run]);boxes.append([*(vs.min(axis=0)+offset),*(vs.max(axis=0)+offset)]);i+=len(run);continue
  a=np.array(e['from'],float);b=np.array(e['to'],float);axis=int(np.argmax(b-a));length=(b[axis]-a[axis])/16;rotated=any(abs(x)>1e-7 for x in e.get('rotation',[]));steps=max(1,math.ceil(length/.0625))if rotated else 1
  for j in range(steps):
   aa=a.copy();bb=b.copy();aa[axis]=a[axis]+(b[axis]-a[axis])*j/steps;bb[axis]=a[axis]+(b[axis]-a[axis])*(j+1)/steps;vs=cube_vertices(e,aa,bb);boxes.append([*(vs.min(axis=0)+offset),*(vs.max(axis=0)+offset)])
  i+=1
 return [[round(float(x),7)for x in b]for b in boxes]

def clip(poly,axis,value,above):
 if not len(poly):return poly
 out=[];prev=poly[-1];was=prev[axis]>=value-EPS if above else prev[axis]<=value+EPS
 for p in poly:
  inside=p[axis]>=value-EPS if above else p[axis]<=value+EPS
  if inside!=was:
   t=(value-prev[axis])/(p[axis]-prev[axis]);out.append(prev+t*(p-prev))
  if inside:out.append(p)
  prev=p;was=inside
 return np.array(out)
def cell_geometry(quads,xyz,grid):
 out=[]
 for slot,q in quads:
  if any((xyz[a]>0 and q[:,a].max()<xyz[a]-EPS)or(xyz[a]+1<grid[a]and q[:,a].min()>xyz[a]+1+EPS)for a in range(3)):continue
  p=q.copy()
  for axis in range(3):
   if xyz[axis]>0:p=clip(p,axis,xyz[axis],True)
   if xyz[axis]+1<grid[axis]:p=clip(p,axis,xyz[axis]+1,False)
   if len(p)<3:break
  if len(p)<3:continue
  p[:,:3]-=xyz
  # Avoid coplanar faces owned by two adjacent cells.
  if any(xyz[a]>0 and np.all(np.abs(p[:,a])<EPS) for a in range(3)):continue
  for j in range(1,len(p)-1):
   use=p if len(p)==4 else np.array([p[0],p[j],p[j+1],p[j+1]])
   if np.linalg.norm(np.cross(use[1,:3]-use[0,:3],use[2,:3]-use[0,:3]))>1e-12:out.append((slot,use.copy()))
   if len(p)==4:break
 return out

def spatial_geometry(quads,grid):
 cells=[[]for _ in range(math.prod(grid))]
 for row in quads:
  q=row[1];lo=q[:,:3].min(axis=0);hi=q[:,:3].max(axis=0);a=[max(0,min(grid[i]-1,math.floor(lo[i]-EPS)))for i in range(3)];b=[max(0,min(grid[i]-1,math.floor(hi[i]+EPS)))for i in range(3)]
  for y in range(a[1],b[1]+1):
   for z in range(a[2],b[2]+1):
    for x in range(a[0],b[0]+1):cells[x+grid[0]*(z+grid[2]*y)].append(row)
 return cells

def clipped_boxes(boxes,xyz,grid):
 out=[];x=np.array(xyz)
 for b in boxes:
  a=np.maximum(b[:3],x);z=np.minimum(b[3:],x+1)
  if np.all(z-a>1e-9):out.append(list(map(float,np.r_[a-x,z-x])))
 return out

def write_obj(path,quads):
 lines=['# 1 unit = 1 Minecraft block','mtllib stage_materials.mtl'];count=1;normal=1
 for slot,q in quads:
  lines.append('usemtl material_'+str(slot))
  for v in q:lines.append('v '+' '.join(f'{x:.7f}'for x in v[:3]))
  for v in q:lines.append('vt %.7f %.7f'%(v[3],1-v[4]))
  n=q[0,5:8];lines.append('vn '+' '.join(f'{x:.7f}'for x in n));lines.append('f '+' '.join(f'{count+i}/{count+i}/{normal}'for i in range(len(q))));count+=len(q);normal+=1
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text('\n'.join(lines)+'\n');path.with_name('stage_materials.mtl').write_text('\n'.join(f'newmtl material_{i}\nKd 1 1 1\nKa 0.2 0.2 0.2\nKs 0 0 0\nmap_Kd #{t}\n'for i,t in enumerate(TEXTURES)))

def model_json(obj,display=None):
 d={'loader':'forge:obj','model':'concertstage:'+str(obj.relative_to(AS)).replace('\\','/'),'flip_v':True,'automatic_culling':False,'shade_quads':True,'textures':{**{t:'concertstage:block/stage/'+t for t in TEXTURES},'particle':'concertstage:block/stage/metals'},'elements':[],'render_type':'minecraft:cutout'}
 if display:d['display']=display;d['gui_light']='side'
 return d

def gui_transform(quads,elevation=30):
 # Minecraft's item transform precedes translation of model coordinates by -0.5.
 rx,ry=np.radians([elevation,225]);R=np.array([[1,0,0],[0,math.cos(rx),-math.sin(rx)],[0,math.sin(rx),math.cos(rx)]])@np.array([[math.cos(ry),0,math.sin(ry)],[0,1,0],[-math.sin(ry),0,math.cos(ry)]])
 vs=(np.concatenate([q[:,:3]for _,q in quads])-.5)@R.T;lo=vs.min(axis=0);hi=vs.max(axis=0);scale=.86/max(hi[0]-lo[0],hi[1]-lo[1]);tr=-(lo+hi)/2*scale*16
 gui={'rotation':[elevation,225,0],'translation':[round(float(x),5)for x in tr],'scale':[round(scale,7)]*3}
 return {'gui':gui,'fixed':gui,'ground':{**gui,'scale':[scale*.65]*3,'translation':[tr[0]*.65,2+tr[1]*.65,tr[2]*.65]},'firstperson_righthand':{**gui,'scale':[scale*.9]*3},'firstperson_lefthand':{**gui,'scale':[scale*.9]*3},'thirdperson_righthand':{**gui,'scale':[scale*.7]*3},'thirdperson_lefthand':{**gui,'scale':[scale*.7]*3}}

def mesh_json(quads):
 return{str(i):[[round(float(x),7)for x in(q if len(q)==4 else np.array([q[0],q[1],q[2],q[2]])).flatten()]for slot,q in quads if slot==i]for i in range(3)}

def config(key,meta):
 walk=0.;mount=False;kind='platform';offset=np.zeros(3);sx=sz=1;mode=''
 if key.startswith('adjustable_leg'):kind='support';offset=np.array([.5,0,.5])
 elif key.startswith('safety_railings'):
  kind='railing';offset=np.array([.07,.09,.07])if key=='safety_railings_corner_1x1'else np.array([0,.09,.5]);sx=2 if key=='safety_railings_2m'else 1;mode='mounted'
 elif key.startswith('stage_stairs'):kind='stairs';walk=int(key[-3:])/100;sz=math.ceil(walk/.2*.3+.04)
 else:
  sx=int(round(meta['dimensions_m'][0]));sz=math.ceil(meta['dimensions_m'][2]);mount=True
  if key.endswith('without_legs'):offset=np.array([0,.014,0]);walk=.104
  elif key.startswith('stage_support_structures'):kind='frame';walk=0
  elif key.startswith('dj_'):kind='riser';walk=.8;mount=False
  else:
   walk=.4
   if key.endswith('h020'):walk=.2
   elif key.endswith('h080'):walk=.8
   elif key.endswith('h120'):walk=1.2
   elif key.endswith('h060'):walk=.6
   if key.startswith(('drum_','musician_','raised_')):kind='riser'
   if key.startswith('assembly_'):kind='assembly'
   if key.startswith('stage_extension'):kind='extension'
   if key=='stage_extension_2x0p5_h040':mode='joined'
 return sx,sz,walk,mount,kind,offset,mode

def export_one(key,d,quads,meta,legacy=False):
 if legacy:
  sx,ny,sz=3,3,3;offset=np.zeros(3);walk=2.;mount=False;kind='legacy';mode='';variants=[quads];boxes=collision(d,offset)+d.get('_extra_collision',[])
 else:
  sx,sz,walk,mount,kind,offset,mode=config(key,meta);quads=translate(quads,offset);variants=[quads];boxes=collision(d,offset)
  if mode=='mounted':variants.append(translate(quads,-offset))
  elif mode=='joined':variants.append(quads+translate(quads,[0,0,.5]))
  hi=np.concatenate([q[:,:3]for _,q in quads]).max(axis=0);top=max(hi[1],(1.2 if kind=='frame'else walk)+1.106 if mount else hi[1]);ny=max(1,math.ceil(top-1e-5))
 grid=[sx,ny,sz];count=sx*ny*sz;cells=[];parts=[];occ=[];spatial=[spatial_geometry(q,grid)for q in variants]
 for part in range(count):
  xyz=np.array([part%sx,part//(sx*sz),(part//sx)%sz]);vsh=[];models=[];occupied=False
  for variant,vq in enumerate(variants):
   vb=boxes
   if variant and mode=='mounted':vb=[[float(x-y)for x,y in zip(b,list(offset)*2)]for b in boxes]
   if variant and mode=='joined':vb=boxes+[[float(x+y)for x,y in zip(b,[0,0,.5,0,0,.5])]for b in boxes]
   cq=cell_geometry(spatial[variant][part],xyz,grid);vsh.append(clipped_boxes(vb,xyz,grid));has=bool(cq)or part==0;occupied|=has
   if cq:
    obj=AS/'models/stage/world'/f'{key}_{part}_{variant}.obj';write_obj(obj,cq);mp=AS/'models/block/stage'/f'{key}_{part}_{variant}.json';dump(mp,model_json(obj));model='concertstage:block/stage/'+mp.stem
   else:model='concertstage:block/stage/empty'
   models.append(model)
  cells.append(vsh);occ.append(occupied)
  for turn,facing in enumerate(['north','east','south','west']):
   for variant,model in enumerate(models):
    when={'facing':facing,'part':str(part)}
    if mode:when[mode]='true'if variant else'false'
    apply={'model':model}
    if turn:apply['y']=turn*90
    parts.append({'when':when,'apply':apply})
 dump(AS/'blockstates'/(key+'.json'),{'multipart':[{'apply':{'model':'concertstage:block/stage/empty'}},*parts]})
 obj=AS/'models/stage/items'/(key+'.obj');write_obj(obj,variants[0]);display=gui_transform(variants[0]);dump(AS/'models/item'/(key+'.json'),model_json(obj,display))
 fullraw=translate(variants[0],-offset);dump(AS/'stage_meshes'/(key+'.json'),mesh_json(fullraw))
 spec={'id':key,'grid':grid,'width':float(sx),'depth':.5 if key=='stage_extension_2x0p5_h040'else float(sz),'walk':walk,'mountable':mount,'kind':kind,'mode':mode,'occupied':occ,'cells':cells,'raw_boxes':collision(d,np.zeros(3)),'raw_bounds':list(map(float,np.r_[np.concatenate([q[:,:3]for _,q in fullraw]).min(axis=0),np.concatenate([q[:,:3]for _,q in fullraw]).max(axis=0)])),'gui':display['gui']}
 return spec

def make_legacy():
 import compatibility_stage_builder as b
 out=[]
 for name,sides in LEGACY.items():
  m=b.Model(name);bay=name.endswith('_tour')
  for x in range(3):
   for z in range(3):
    if bay and x==z==1:continue
    m.append(b.deck(1,1,2,brace=True,name='compatibility_deck'),(x,0,z),f'Module professionnel {x+1}/{z+1}')
  for side in sides:
   for j in range(3):
    ra=b.rail(1)
    # Retain the old 3-block total envelope: lower the top of the 1.10 m derivative guard to 1 m.
    for e in ra.elements:
     name_e=e['name'];high=min(e['from'][1],e['to'][1])/16>.9
     if high:
      for k in['from','to','origin']:e[k][1]-=.106*16
     elif name_e.startswith('Poteau garde-corps'):
      e['to'][1]-=.106*16;e['origin'][1]=(e['from'][1]+e['to'][1])/2
    data=ra.data();q=cube_quads(data)
    origin,turn={'south':((j+1,2,3),2),'east':((3,2,j),1),'west':((0,2,j+1),3)}[side]
    boxes=[]
    for box in collision(data,np.zeros(3)):
     lo=box[:3];hi=box[3:];pts=np.array([[x,y,z]for x in[lo[0],hi[0]]for y in[lo[1],hi[1]]for z in[lo[2],hi[2]]]);c=round(math.cos(turn*math.pi/2));si=round(math.sin(turn*math.pi/2));trans=pts.copy();trans[:,0]=pts[:,0]*c-pts[:,2]*si+origin[0];trans[:,1]+=origin[1];trans[:,2]=pts[:,0]*si+pts[:,2]*c+origin[2];boxes.append([*trans.min(axis=0),*trans.max(axis=0)])
    m.notes.append((q,origin,turn,boxes))
  data=m.data();q=cube_quads(data)
  data['_extra_collision']=[]
  for rq,origin,turn,boxes in m.notes:q+=transform(rq,origin,turn);data['_extra_collision'].extend(boxes)
  out.append((name,data,q,bay,sides))
 return out

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--pilot',action='store_true');args=parser.parse_args();DOC.mkdir(parents=True,exist_ok=True);specs=[];meta={Path(x['file']).stem:x for x in json.loads((SOURCE/'catalogue.json').read_text())}
 for t in TEXTURES:
  p=AS/'textures/block/stage'/(t+'.png');p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(SOURCE/'Textures'/(t+'.png'),p)
 dump(AS/'models/block/stage/empty.json',{'elements':[],'textures':{'particle':'concertstage:block/stage/metals'}})
 names=['stage_deck_2x1_h040']if args.pilot else NAMES
 audit=[]
 for key in names:
  p=SOURCE/'Models'/(key+'.bbmodel');d=json.loads(p.read_text());q=obj_quads(SOURCE/'Exports_OBJ'/(key+'.obj'));check=cube_quads(d)
  assert len(q)==len(check)==6*len(d['elements']),key
  assert all(np.allclose(a[1],b[1],atol=1e-6)for a,b in zip(q,check)),key+' supplied OBJ differs from editable geometry/UVs'
  spec=export_one(key,d,q,meta[key]);specs.append(spec);audit.append({'id':key,'source_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'source_cubes':len(d['elements']),'source_quads':len(q),'grid':spec['grid'],'variants':2 if spec['mode']else 1,'gui':spec['gui']});print('Integrated',key,spec['grid'],flush=True)
 if not args.pilot:
  for key,d,q,bay,sides in make_legacy():
   spec=export_one(key,d,q,{},True)
   # Keep exact legacy main/anchor cells and tower apertures; no old geometry survives.
   spec.update(main=[0,1,0]if bay else[1,1,1],anchor=[1,0,0]if bay else[1,0,1],bay=bay,rail_sides=list(sides));specs.append(spec)
 dump(DATA/'stage_equipment.json',{'version':1,'equipment':specs});dump(DOC/'model_integration.json',audit)
 print('Exported',len(names),'unchanged supplied models',flush=True)
if __name__=='__main__':main(); import removed_blocks; removed_blocks.purge()
