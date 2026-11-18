#!/usr/bin/env python3
"""Generate original editable rigging meshes, Forge OBJ models and cached tower quads.
16 modelling units = one block; legacy multiblock dimensions and chord axes retained.
"""
from pathlib import Path
import argparse,base64,copy,io,json,math,uuid
import numpy as np
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];AS=ROOT/'common/src/main/resources/assets/concertstage';BB=ROOT/'tools/blockbench/realistic';DOC=ROOT/'tools/realistic_docs'
HEIGHTS=(10,14,18,22);CARRIAGES={'corner3':(1,False),'corner3_h2':(2,False),'corner6':(1,True),'corner6_h2':(2,True)}
LEGACY={'truss_h1_l1':(1,1,1),'truss_h1_l2':(2,1,1),'truss_h2_l2':(2,2,1),'truss_h2_l1':(1,2,1),'truss_v2_h2':(1,2,1),'truss_corner_3':(1,1,1),'truss_corner_3_h2':(1,2,1),'truss_corner_6':(1,1,1),'truss_corner_6_h2':(1,2,1),'truss_base_v2':(1,1,1),'truss_h2_l2_x30d':(2,2,1),'truss_x30d_l1':(1,1,1),'truss_x30d_l2':(2,1,1),'truss_h1_l2_petit':(2,1,1)}
EXTRA={'truss_ladder_l1':(1,1,1),'truss_ladder_l2':(2,1,1),'truss_ladder_v2':(1,2,1),'truss_junction_90':(1,1,1),'truss_junction_t':(1,1,1),'truss_junction_cross':(1,1,1)}
PITCH=.75;CHAIN_Z=-1.5;DRIVE_X=6.5;LOAD_X=9.5
MAT_NAMES=['aluminium','weld','steel','rubber','motor','chain','sprocket','label','coupler','bush','flange','scale'];MATS={n:(i%4*128,i//4*128,i%4*128+128,i//4*128+128)for i,n in enumerate(MAT_NAMES)}
def uid():return str(uuid.uuid4())
def rnd(x):return round(float(x),5)
def dump(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')))
def font(size):
 for name in ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf','DejaVuSans.ttf','arial.ttf']:
  try:return ImageFont.truetype(name,size)
  except OSError:pass
 return ImageFont.load_default()
def texture(black=False):
 rng=np.random.default_rng(8295);a=np.zeros((512,512,4),dtype=np.uint8);a[:]=[31,34,38,255]
 colors=[(162,167,174),(132,136,141),(58,63,69),(18,20,23),(39,47,55),(89,94,99),(70,75,81),(204,199,174),(156,160,166),(25,29,34),(136,141,147),(119,123,128)]
 if black:colors[0]=(36,39,44);colors[1]=(48,51,56);colors[8]=(44,47,52);colors[10]=(40,43,48)
 for i,c in enumerate(colors):
  x,y,_,_=MATS[MAT_NAMES[i]];noise=rng.normal(0,1.6,(128,128,1))+rng.normal(0,4.,(128,1,1));a[y:y+128,x:x+128,:3]=np.clip(np.array(c)[None,None,:]+noise,0,255)
 im=Image.fromarray(a);d=ImageDraw.Draw(im);x,y,_,_=MATS['label'];d.rectangle((x+5,y+5,x+123,y+123),fill=(28,33,39));d.text((x+10,y+10),'LIFT',font=font(17),fill=(193,201,208));d.text((x+10,y+40),'UP / DOWN',font=font(11),fill=(171,181,190));d.polygon([(x+14,y+99),(x+31,y+68),(x+48,y+99)],fill=(184,163,74));d.line((x+31,y+77,x+31,y+88),fill=(30,33,36),width=3)
 x,y,_,_=MATS['chain'];d.rectangle((x,y,x+127,y+127),fill=(0,0,0,0))
 for j in range(0,128,6):
  if(j//6)%2:d.ellipse((x+59,y+j-2,x+69,y+j+5),outline=(116,121,126,255),width=2)
  else:d.rounded_rectangle((x+62,y+j-2,x+66,y+j+5),radius=2,outline=(89,95,102,255),width=2)
 # Dense, subdued link texture for the chain heap inside the bag.
 x,y,_,_=MATS['scale']
 for k in range(120):
  px=x+int(rng.integers(4,119));py=y+int(rng.integers(3,121));d.ellipse((px,py,px+7,py+4),outline=(67,74,81),width=1)
 return im
TEXTURES={b:texture(b)for b in[False,True]}
class Model:
 def __init__(self,name,lod=False):self.name=name;self.lod=lod;self.elements=[];self.outliner=[];self.current=None;self.animations=[];self.connections=[]
 def group(self,name,origin=(0,0,0),parent=None):
  g={'name':name,'uuid':uid(),'origin':list(map(rnd,origin)),'rotation':[0,0,0],'export':True,'visibility':True,'isOpen':False,'children':[]};(parent['children']if parent else self.outliner).append(g);self.current=g;return g
 def mesh(self,name,vs,faces,mat='aluminium'):
  uv=MATS[mat];corners=[[uv[0]+2,uv[1]+2],[uv[2]-2,uv[1]+2],[uv[2]-2,uv[3]-2],[uv[0]+2,uv[3]-2]];vertices={str(i):list(map(rnd,v))for i,v in enumerate(vs)};fs={}
  for i,face in enumerate(faces):
   keys=list(dict.fromkeys(map(str,face)))
   if len(keys)<3:continue
   p=np.array([vertices[k]for k in keys[:3]])
   if np.linalg.norm(np.cross(p[1]-p[0],p[2]-p[0]))<1e-9:continue
   fs[str(i)]={'vertices':keys,'uv':{k:corners[j%4]for j,k in enumerate(keys)},'texture':0}
  e={'name':name,'type':'mesh','uuid':uid(),'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':fs,'visibility':True,'export':True,'color':0};self.elements.append(e);self.current['children'].append(e['uuid']);return e
 def box(self,name,a,b,mat='steel'):
  x,y,z=a;X,Y,Z=b;return self.mesh(name,[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],[[0,3,2,1],[4,5,6,7],[0,1,5,4],[3,7,6,2],[0,4,7,3],[1,2,6,5]],mat)
 def pipe(self,name,a,b,r,n=8,wall=None,mat='aluminium',cap=False):
  a=np.array(a,float);b=np.array(b,float);axis=b-a;L=np.linalg.norm(axis)
  if L<1e-6:return
  axis/=L;ref=np.array([0,1,0])if abs(axis[1])<.9 else np.array([1,0,0]);u=np.cross(ref,axis);u/=np.linalg.norm(u);v=np.cross(axis,u);n=4 if self.lod else n;vs=[];fs=[]
  for c in[a,b]:vs.extend((c+r*(math.cos(k*math.tau/n)*u+math.sin(k*math.tau/n)*v)).tolist()for k in range(n))
  for k in range(n):fs.append([k,(k+1)%n,n+(k+1)%n,n+k])
  if wall and not self.lod:
   ri=r-wall;depth=min(L/2,.8)
   for end,c,d in[(0,a,axis),(1,b,-axis)]:
    o=len(vs)
    for center in[c,c+d*depth]:vs.extend((center+ri*(math.cos(k*math.tau/n)*u+math.sin(k*math.tau/n)*v)).tolist()for k in range(n))
    for k in range(n):
     j=(k+1)%n;q=end*n;fs.append([q+k,o+k,o+j,q+j]if end==0 else[q+k,q+j,o+j,o+k]);fs.append([o+k,o+n+k,o+n+j,o+j]if end==0 else[o+k,o+j,o+n+j,o+n+k])
  elif cap and not self.lod:
   for k in range(1,n-1):fs.extend([[0,k+1,k],[n,n+k,n+k+1]])
  return self.mesh(name,vs,fs,mat)
 def lathe(self,name,center,axis,profile,n=10,mat='coupler'):
  center=np.array(center,float);axis=np.array(axis,float);axis/=np.linalg.norm(axis);ref=np.array([0,1,0])if abs(axis[1])<.9 else np.array([1,0,0]);u=np.cross(ref,axis);u/=np.linalg.norm(u);v=np.cross(axis,u);n=4 if self.lod else n;vs=[];fs=[]
  for s,r in profile:vs.extend((center+s*axis+r*(math.cos(k*math.tau/n)*u+math.sin(k*math.tau/n)*v)).tolist()for k in range(n))
  for j in range(len(profile)):
   q=(j+1)%len(profile)
   for k in range(n):fs.append([j*n+k,j*n+(k+1)%n,q*n+(k+1)%n,q*n+k])
  return self.mesh(name,vs,fs,mat)
 def brace(self,name,a,b,r,parent_axis,rmain,mat='aluminium'):
  a=np.array(a,float);b=np.array(b,float);d=b-a;d/=np.linalg.norm(d);pa=np.array(parent_axis,float);pa/=np.linalg.norm(pa);ref=np.array([0,1,0])if abs(d[1])<.9 else np.array([1,0,0]);u=np.cross(ref,d);u/=np.linalg.norm(u);v=np.cross(d,u);n=4 if self.lod else 6
  def cut(rad,travel):
   q=rad-pa*np.dot(rad,pa);t=travel-pa*np.dot(travel,pa);A=np.dot(t,t);B=2*np.dot(q,t);C=np.dot(q,q)-(rmain*.975)**2
   return max(0,(-B+math.sqrt(max(0,B*B-4*A*C)))/(2*A))if A>1e-8 else 0
  vs=[]
  for endpoint,travel in[(a,d),(b,-d)]:
   for k in range(n):
    rad=r*(math.cos(k*math.tau/n)*u+math.sin(k*math.tau/n)*v);vs.append((endpoint+rad+travel*cut(rad,travel)).tolist())
  fs=[[k,(k+1)%n,n+(k+1)%n,n+k]for k in range(n)];self.mesh(name+' · coupe en selle',vs,fs,mat)
  if False:
   for endpoint,travel in[(a,d),(b,-d)]:
    vs=[]
    for rr,shift in[(r+.035,0),(r,.075)]:
     for k in range(n):
      rad=rr*(math.cos(k*math.tau/n)*u+math.sin(k*math.tau/n)*v);vs.append((endpoint+rad+travel*(cut(rad,travel)+shift)).tolist())
    self.mesh(name+' · cordon de soudure',vs,fs,'weld')
 def connector(self,name,point,inward,r):
  self.lathe(name+' · douille conique',point,inward,[(0,r*1.07),(.32,r*1.12),(1.2,r),(1.2,r*.68),(0,r*.68)],8)
  if self.lod:return
  c=np.array(point,float)+np.array(inward,float)*.65;axis=np.array(inward,float);radial=np.array([0,1,0])if abs(axis[1])<.9 else np.array([1,0,0]);other=np.cross(axis,radial);self.pipe(name+' · broche',c-radial*(r+.14),c+radial*(r+.14),.085,6,mat='steel',cap=True)
 def append(self,other,offset=(0,0,0),group_name=None):
  ids={e['uuid']:uid()for e in other.elements}
  for original in other.elements:
   e=copy.deepcopy(original);e['uuid']=ids[e['uuid']]
   for p in e['vertices'].values():
    for i in range(3):p[i]=rnd(p[i]+offset[i])
   self.elements.append(e)
  def clone(g):
   g=copy.deepcopy(g);g['uuid']=uid();g['origin']=[rnd(x+offset[i])for i,x in enumerate(g['origin'])];g['children']=[ids[c]if isinstance(c,str)else clone(c)for c in g['children']];return g
  groups=[clone(g)for g in other.outliner]
  if group_name:g=self.group(group_name,offset);g['children']=groups;return g
  self.outliner.extend(groups);self.connections.extend([{**c,'point':[rnd(x+offset[i])for i,x in enumerate(c['point'])]}for c in other.connections])
 def data(self,black=False):
  b=io.BytesIO();TEXTURES[black].save(b,format='PNG');return {'meta':{'format_version':'4.12','model_format':'free','box_uv':False},'name':self.name,'resolution':{'width':512,'height':512},'elements':self.elements,'outliner':self.outliner,'textures':[{'name':'rigging_detail'+('_noir'if black else'')+'.png','path':'','id':'0','uuid':uid(),'source':'data:image/png;base64,'+base64.b64encode(b.getvalue()).decode(),'width':512,'height':512,'uv_width':512,'uv_height':512,'mode':'bitmap','saved':True,'visible':True}],'animations':self.animations,'display':{}}
 def quads(self):
  out=[]
  for e in self.elements:
   for f in e['faces'].values():
    ids=f['vertices'];p=np.array([e['vertices'][k]for k in ids]);n=np.cross(p[1]-p[0],p[2]-p[0]);n/=np.linalg.norm(n)
    for j in range(1,len(ids)-1):
     use=ids if len(ids)==4 else[ids[0],ids[j],ids[j+1],ids[j+1]];q=[]
     for k in use:q.extend([*e['vertices'][k],f['uv'][k][0]/512,f['uv'][k][1]/512,*n])
     out.append(list(map(rnd,q)))
     if len(ids)==4:break
  return out

def box_truss(name,L=32,H=16,W=16,axis=0,lod=False,small=False):
 m=Model(name,lod);lo=4 if small else 1.5;hi=12 if small else H-1.5;zl=4 if small else 1.5;zh=12 if small else W-1.5;r=.95 if small else 1.25;rd=.42 if small else .52;p=lambda s,u,v:(s,u,v)if axis==0 else(u,s,v);main=(1,0,0)if axis==0 else(0,1,0);m.group('01 · Membrures cylindriques creuses')
 for y in[lo,hi]:
  for z in[zl,zh]:m.pipe('Membrure',p(0,y,z),p(L,y,z),r,8)
 stations=np.linspace(1.8,L-1.8,max(2,round(L/16)+1));m.group('02 · Entretoises et diagonales soudées')
 for s in stations:
  for z in[zl,zh]:m.brace('Entretoise de bout',p(s,lo,z),p(s,hi,z),rd,main,r)
  for y in[lo,hi]:m.brace('Entretoise de face',p(s,y,zl),p(s,y,zh),rd,main,r)
 for j,(a,b)in enumerate(zip(stations[:-1],stations[1:])):
  for k,z in enumerate([zl,zh]):m.brace('Diagonale latérale',p(a,lo if(j+k)%2==0 else hi,z),p(b,hi if(j+k)%2==0 else lo,z),rd,main,r)
  for k,y in enumerate([lo,hi]):m.brace('Diagonale horizontale',p(a,y,zl if(j+k)%2==0 else zh),p(b,y,zh if(j+k)%2==0 else zl),rd,main,r)
 m.group('03 · Douilles, broches et clavettes')
 for s,d in[(0,main),(L,tuple(-x for x in main))]:
  for y in[lo,hi]:
   for z in[zl,zh]:m.connector('Raccord',p(s,y,z),d,r);m.connections.append({'point':list(p(s,y,z)),'axis':axis})
 return m

# turned : X30D tourné de 90° autour de son axe (pointe vers +Z, base verticale côté −Z)
def triangle(name,L=32,lod=False,turned=False):
 m=Model(name,lod);pts=[(3.982,12.64),(3.982,3.36),(12.018,8)];r=.95;rd=.42
 if turned:pts=[(z,y)for y,z in pts]
 m.group('01 · Trois membrures triangulaires')
 for y,z in pts:m.pipe('Membrure creuse',(0,y,z),(L,y,z),r,8)
 m.group('02 · Triangulation des trois faces');stations=np.linspace(1.6,L-1.6,3)
 for i in range(3):
  a,b=pts[i],pts[(i+1)%3]
  for x in stations:m.brace('Entretoise',(x,*a),(x,*b),rd,(1,0,0),r)
  for j,(x0,x1)in enumerate(zip(stations[:-1],stations[1:])):
   u,v=(a,b)if(j+i)%2==0 else(b,a);m.brace('Diagonale',(x0,*u),(x1,*v),rd,(1,0,0),r)
 m.group('03 · Assemblages triangulaires')
 for x,d in[(0,(1,0,0)),(L,(-1,0,0))]:
  for y,z in pts:m.connector('Raccord',(x,y,z),d,r);m.connections.append({'point':[x,y,z],'axis':0})
 return m

def corner(name,H=16,ports=None,lod=False):
 ports=ports or[(0,1),(1,1),(2,1)];m=Model(name,lod);lim=[(1.5,14.5),(1.5,H-1.5),(1.5,14.5)];m.group('01 · Cage de jonction soudée')
 for axis in range(3):
  other=[i for i in range(3)if i!=axis]
  for u in lim[other[0]]:
   for v in lim[other[1]]:
    a=[0.,0.,0.];b=a.copy();a[axis],b[axis]=lim[axis];a[other[0]]=b[other[0]]=u;a[other[1]]=b[other[1]]=v;m.pipe('Tube du nœud',a,b,1.25,8)
 m.group('02 · Diagonales du nœud')
 for fixed in range(3):
  o=[i for i in range(3)if i!=fixed];parent=[0,0,0];parent[o[0]]=1
  for face in lim[fixed]:
   a=[0.,0.,0.];b=a.copy();a[fixed]=b[fixed]=face;a[o[0]]=lim[o[0]][0];b[o[0]]=lim[o[0]][1];a[o[1]]=lim[o[1]][0];b[o[1]]=lim[o[1]][1];m.brace('Diagonale du nœud',a,b,.52,parent,1.25)
 m.group('03 · Ports de raccordement')
 for axis,sign in ports:
  o=[i for i in range(3)if i!=axis]
  for u in lim[o[0]]:
   for v in lim[o[1]]:
    p=[0.,0.,0.];p[axis]=(H if axis==1 else 16)if sign>0 else 0;p[o[0]]=u;p[o[1]]=v;inside=p.copy();inside[axis]=lim[axis][1 if sign>0 else 0];m.pipe('Embout de liaison au nœud',p,inside,1.25,8);d=[0,0,0];d[axis]=-sign;m.connector('Raccord du nœud',p,d,1.25);m.connections.append({'point':p,'axis':axis})
 return m

def ladder(name,L=32,vertical=False,lod=False):
 m=Model(name,lod);p=lambda s,y,z:(y,s,z)if vertical else(s,y,z);axis=(0,1,0)if vertical else(1,0,0);m.group('01 · Deux membrures creuses')
 for y in[4,12]:m.pipe('Membrure ladder',p(0,y,8),p(L,y,8),.95,8)
 m.group('02 · Barreaux et soudures')
 for s in np.linspace(1.8,L-1.8,max(2,round(L/4))):m.brace('Barreau',p(s,4,8),p(s,12,8),.42,axis,.95)
 m.group('03 · Raccords ladder')
 for s,d in[(0,axis),(L,tuple(-v for v in axis))]:
  for y in[4,12]:m.connector('Raccord ladder',p(s,y,8),d,.95);m.connections.append({'point':list(p(s,y,8)),'axis':1 if vertical else 0})
 return m

def standard(name,lod=False):
 if name in ('truss_x30d_l1','truss_x30d_l2'):return triangle(name,L=16 if name.endswith('l1') else 32,lod=lod)
 if name=='truss_h2_l2_x30d':
  m=box_truss(name,H=32,lod=lod);x=triangle('Adaptateur X30D',L=16,lod=lod)
  # X30D en travers du carré (axe Z, dans le 1er bloc), pointe en haut : rotation de 90° autour de Y (x,z)->(z,16-x)
  for e in x.elements:
   for p in e['vertices'].values():p[0],p[2]=rnd(p[2]),rnd(16-p[0])
  x.connections=[{**c,'point':[c['point'][2],c['point'][1],16-c['point'][0]],'axis':2}for c in x.connections]
  m.append(x);m.group('04 · Supports soudés de l’adaptateur')
  for z in[1.8,14.2]:
   for xo,xi in[(1.5,3.36),(14.5,12.64)]:
    m.brace('Liaison de membrure basse',(xo,1.5,z),(xi,3.982,z),.42,(0,0,1),.95)
  return m
 if name.startswith('truss_ladder'):return ladder(name,16 if name.endswith('l1')else 32,name.endswith('v2'),lod)
 if name.startswith('truss_junction'):
  ports={'90':[(0,1),(2,1)],'t':[(0,-1),(0,1),(2,1)],'cross':[(0,-1),(0,1),(2,-1),(2,1)]};return corner(name,ports=ports[name.rsplit('_',1)[1]],lod=lod)
 if name.startswith('truss_corner'):return corner(name,32 if name.endswith('h2')else 16,[(a,s)for a in range(3)for s in[-1,1]]if'_6'in name else None,lod)
 if name=='truss_base_v2':
  m=box_truss(name,L=16,axis=1,lod=lod);m.group('04 · Platine et boulonnerie');m.box('Platine de pied',(0,0,0),(16,.65,16))
  for x in[2,14]:
   for z in[2,14]:m.pipe('Écrou de platine',(x,.65,z),(x,.9,z),.30,6,mat='coupler',cap=True)
  return m
 if name=='truss_v2_h2':return box_truss(name,axis=1,lod=lod)
 return box_truss(name,L=16 if name.endswith('l1')else 32,H=32 if name in('truss_h2_l2','truss_h2_l1')else 16,small=name.endswith('petit'),lod=lod)


def chain_link(alternate=False):
 m=Model('chain_link');m.group('Maillon de chaîne');vs=[];n=8;wn=3;right=np.array([0,0,1]if alternate else[1,0,0],float);up=np.array([0,1,0],float);normal=np.cross(right,up)
 for i in range(n):
  t=i*math.tau/n;center=right*.22*math.cos(t)+up*.38*math.sin(t);out=right*math.cos(t)/.22+up*math.sin(t)/.38;out/=np.linalg.norm(out)
  for j in range(wn):vs.append((center+.06*(out*math.cos(j*math.tau/wn)+normal*math.sin(j*math.tau/wn))).tolist())
 fs=[]
 for i in range(n):
  for j in range(wn):fs.append([i*wn+j,i*wn+(j+1)%wn,((i+1)%n)*wn+(j+1)%wn,((i+1)%n)*wn+j])
 m.mesh('Maillon à section ronde',vs,fs,'sprocket');return m
LINKS=[chain_link(False),chain_link(True)]
def add_link(m,point,k,angle=0):
 e=copy.deepcopy(LINKS[k%2].elements[0]);e['uuid']=uid();co=math.cos(angle);si=math.sin(angle)
 for p in e['vertices'].values():
  x,y,z=p;p[:]=[rnd(point[0]+x*co-y*si),rnd(point[1]+x*si+y*co),rnd(point[2]+z)]
 m.elements.append(e);m.current['children'].append(e['uuid'])

def mast_cell(h,cell,lod=False):
 m=Model('mast_'+str(h),lod);m.group('Mât et équipement fixe');y0=cell*16;y1=y0+16;r=.8
 if cell==0:
  m.box('Platine de base renforcée',(-1,.55,-1),(17,2.6,17),'steel')
  for x in[1.25,14.75]:
   for z in[1.25,14.75]:m.pipe('Boulon du pied',(x,2.6,z),(x,3.1,z),.28,6,mat='coupler',cap=True)
  for z in[1.25,14.75]:m.pipe('Ceinture de reprise des stabilisateurs',(1.25,11,z),(14.75,11,z),.32,8,mat='steel')
  for x in[1.25,14.75]:m.pipe('Ceinture de reprise des stabilisateurs',(x,11,1.25),(x,11,14.75),.32,8,mat='steel')
  reach=18+(h-10)*.2
  for axis in[0,2]:
   for sign in[-1,1]:
    start=np.array([8.,2.4,8.]);end=start.copy();end[axis]=8+sign*reach;m.pipe('Stabilisateur amovible',start,end,.60,8,mat='steel',cap=True);x,z=end[0],end[2]
    m.box('Patin de protection',(x-1.5,0,z-1.5),(x+1.5,.22,z+1.5),'rubber');m.box('Platine de répartition',(x-1.4,.22,z-1.4),(x+1.4,.55,z+1.4),'steel');m.pipe('Vérin de nivellement',(x,.55,z),(x,3.4,z),.15,6,mat='coupler',cap=True);m.lathe('Écrou de vérin',(x,2.8,z),(0,1,0),[(0,.31),(.28,.31),(.28,.15),(0,.15)],6)
    attach=np.array([8.,11.,8.]);attach[axis]=1.25 if sign<0 else 14.75;m.pipe('Jambe de force du stabilisateur',attach,end,.27,6,mat='steel',cap=True);cross=np.array([0,0,.7])if axis==0 else np.array([.7,0,0]);m.pipe('Broche captive',end+np.array([0,-.1,0])-cross,end+np.array([0,-.1,0])+cross,.12,6,mat='coupler',cap=True)
  m.box('Console du palan au sol',(3.5,2.4,-2.6),(12.2,3.8,1.7),'steel');m.box('Carter du palan à chaîne',(4.4,3.8,-2.5),(10.8,10.8,1.7),'motor');m.box('Plaque signalétique sans marque',(5.1,5.4,-2.58),(9.2,9.4,-2.5),'label');m.pipe('Moteur électrique',(2.5,7,-.4),(4.4,7,-.4),1.45,10,mat='motor',cap=True);m.pipe('Réducteur',(10.8,7,-.4),(12,7,-.4),1.3,10,mat='steel',cap=True)
  for y in[6.1,6.6,7.1,7.6,8.1]:m.box('Ailette de refroidissement',(2.4,y,-1.45),(4.2,y+.1,.65),'steel')
  m.box('Sac à chaîne · fond',(11.6,3.6,-2.6),(15.6,3.95,1.3),'rubber')
  for a,b in[((11.6,3.95,-2.6),(12,9.3,1.3)),((15.2,3.95,-2.6),(15.6,9.3,1.3)),((12,3.95,-2.6),(15.2,9.3,-2.2)),((12,3.95,.9),(15.2,9.3,1.3))]:m.box('Sac à chaîne · enveloppe',a,b,'rubber')
  m.pipe('Sortie de chaîne et guide',(DRIVE_X,10.8,CHAIN_Z),(DRIVE_X,11.5,CHAIN_Z),.38,8,wall=.12,mat='steel');m.pipe('Guide du brin mou',(10.4,9,CHAIN_Z),(12.6,9.3,CHAIN_Z),.17,6,mat='sprocket');m.pipe('Support du capteur bas',(14.75,14.8,1.25),(16.4,14.8,6.1),.15,6,mat='steel');m.box('Fin de course basse',(15.8,14.2,5.2),(17.1,15.6,7),'motor');m.box('Butée élastomère basse',(.1,15.1,.1),(15.9,15.8,.5),'rubber')
 start=max(y0,2.6);stop=min(y1,h*16-1.9)
 for x in[1.25,14.75]:
  for z in[1.25,14.75]:m.pipe('Tube de mât',(x,start,z),(x,stop,z),r,8)
 if 0<cell:
  lo=y0+.8;hi=y1-.8 if cell<h-1 else y1-8.2      # cellule de tête : treillis jusque sous les traverses de tête (1.9.2)
  for z in[1.25,14.75]:m.brace('Diagonale du mât',(1.25,lo if cell%2==0 else hi,z),(14.75,hi if cell%2==0 else lo,z),.4,(0,1,0),r);m.brace('Traverse du mât',(1.25,lo,z),(14.75,lo,z),.4,(0,1,0),r)
  for x in[1.25,14.75]:m.brace('Diagonale du mât',(x,lo,1.25),(x,hi,14.75),.4,(0,1,0),r);m.brace('Traverse du mât',(x,lo,1.25),(x,lo,14.75),.4,(0,1,0),r)
 if cell==h-1:
  yy=h*16
  # tête ouverte : cadre périmétrique soudé + goussets d'angle (au lieu d'une dalle pleine), bouchons sur les membrures
  for z in[1.25,14.75]:m.pipe('Cadre de tête',(1.25,yy-1.4,z),(14.75,yy-1.4,z),.62,8,mat='steel',cap=True)
  for x in[1.25,14.75]:m.pipe('Cadre de tête',(x,yy-1.4,1.25),(x,yy-1.4,14.75),.62,8,mat='steel',cap=True)
  for x in[1.25,14.75]:
   for z in[1.25,14.75]:
    m.box('Gousset d’angle de tête',(x-1.6,yy-2.2,z-1.6),(x+1.6,yy-1.9,z+1.6),'steel');m.pipe('Bouchon de membrure',(x,yy-1.9,z),(x,yy-1.5,z),.95,8,mat='coupler',cap=True)
  for x in[1.25,14.75]:
   for z in[1.25,14.75]:m.brace('Jambe de force de tête',(x,yy-6,z),(8 if x<8 else 8,yy-1.4,z),.32,(0,1,0),r) if False else None
  for x in[1.25,14.75]:m.pipe('Traverse de tête',(x,yy-6,1.25),(x,yy-6,14.75),.55,8,mat='steel',cap=True)
  m.pipe('Support du renvoi',(1.25,yy-5,1.25),(14.75,yy-5,1.25),.55,8,mat='steel',cap=True)
  for z in[-2.05,-.95]:m.box('Flasque du renvoi',(6.9,yy-7.6,z-.12),(9.1,yy-4.5,z+.12),'steel')
  for x in[6.9,9.1]:m.pipe('Console des paliers',(x,yy-7.5,1.25),(x,yy-7.5,-2.2),.22,6,mat='steel',cap=True)
  m.pipe('Axe du renvoi',(8,yy-5,-2.4),(8,yy-5,-.6),.24,8,mat='coupler',cap=True);m.box('Protection supérieure du renvoi',(5.6,yy-2.4,-3.2),(10.4,yy-2,1),'motor');m.pipe('Support du capteur haut',(14.75,yy-15.3,1.25),(16.4,yy-15.3,6.1),.15,6,mat='steel');m.box('Fin de course haute',(15.8,yy-16,5.2),(17.1,yy-14.6,7),'motor')
 return m

def carriage(kind,lod=False):
 cells,six=CARRIAGES[kind];H=cells*16;m=Model('carriage_'+kind,lod);m.group('01 · Chariot enveloppant le mât')
 for x in[-.25,16.25]:
  for z in[-.25,16.25]:m.pipe('Montant du chariot',(x,.9,z),(x,H-.9,z),.45,8,mat='steel')
 rows=[1.5,H-1.5]if cells==1 else[1.5,14.5,17.5,H-1.5]
 for y in rows:
  for z in[-.25,16.25]:m.pipe('Cadre horizontal',(-.25,y,z),(16.25,y,z),.45,8,mat='steel')
  for x in[-.25,16.25]:m.pipe('Cadre horizontal',(x,y,-.25),(x,y,16.25),.45,8,mat='steel')
 m.group('02 · Galets, axes et boulons')
 for y in[4,H-4]:
  for x in[0,16]:
   for z,outer in[(1.25,-.25),(14.75,16.25)]:m.pipe('Axe boulonné de galet',(x,y,min(z,outer)-.2),(x,y,max(z,outer)+.2),.15,6,mat='coupler',cap=True);m.lathe('Galet au contact du tube',(x,y,z),(0,0,1),[(-.23,.45),(.23,.45),(.23,.18),(-.23,.18)],8,'bush')
 m.group('03 · Adaptateurs et raccords de truss');dirs=[(0,-1),(0,1),(2,-1),(2,1)]if six else[(0,1),(2,1)]
 for axis,sign in dirs:
  for y in rows:
   for cross in[1.5,14.5]:
    p=[cross,y,cross];p[axis]=16 if sign>0 else 0;inside=p.copy();inside[axis]=16.25 if sign>0 else -.25;d=[0,0,0];d[axis]=-sign;m.pipe('Embout porteur',p,inside,1.25,8);m.connector('Connexion du chariot',p,d,1.25)
 m.group('04 · Traction et fin de course');yy=H-1.8;m.pipe('Traverse de traction',(-.25,yy,-.25),(16.25,yy,-.25),.42,8,mat='steel');m.pipe('Patte de traction',(LOAD_X,yy,-.25),(LOAD_X,yy,CHAIN_Z),.28,8,mat='steel',cap=True);m.lathe('Manille de traction',(LOAD_X,yy,CHAIN_Z),(0,0,1),[(-.16,.38),(.16,.38),(.16,.21),(-.16,.21)],8);m.box('Palette de fin de course',(16.35,H-1.8,5.8),(16.9,H-.8,6.4),'steel');return m

def wheel(name,radius=1.5,lod=False):
 m=Model(name,lod);m.group('Roue à empreintes');m.lathe('Roue de chaîne à gorge',(0,0,0),(0,0,1),[(-.32,radius+.15),(-.12,radius+.15),(-.12,radius),(.12,radius),(.12,radius+.15),(.32,radius+.15),(.32,.25),(-.32,.25)],12,'sprocket')
 for i in range(4 if lod else 8):
  a=i*math.tau/(4 if lod else 8);m.pipe('Rayon de roue',(.25*math.cos(a),.25*math.sin(a),.34),(radius*.72*math.cos(a),radius*.72*math.sin(a),.34),.09,4,mat='steel')
 return m

def kf(channel,time,value,step=False):return {'channel':channel,'data_points':[dict(zip(['x','y','z'],map(str,value)))],'uuid':uid(),'time':rnd(time),'color':-1,'interpolation':'step'if step else'linear'}
def full_tower(h,kind,black=False,pose_lift=0,animated=True):
 cells,_=CARRIAGES[kind];travel=(h-2-cells)*16;m=Model(f'motor_tower_{h}_{kind}'+('_noir'if black else''))
 for c in range(h):m.append(mast_cell(h,c),group_name=f'Mât · cellule {c+1}')
 gear=m.append(wheel('Moteur',.7),(8,7,-2.75),'Entraînement moteur');head=m.append(wheel('Renvoi'),(8,h*16-5,CHAIN_Z),'Renvoi supérieur');moving=m.append(carriage(kind),(0,16+pose_lift,0),'Chariot mobile');m.group('Chaîne motrice');headY=h*16-5
 for k in range(int((headY-11.5)/PITCH)+1):add_link(m,(DRIVE_X,headY-k*PITCH,CHAIN_Z),k)
 eye=16+pose_lift+cells*16-1.8;load=[]
 for k in range(max(0,int((headY-eye)/PITCH)+1)):
  cy=headY-k*PITCH;g=m.group(f'Chaîne de charge · maillon {k+1}',(LOAD_X,cy,CHAIN_Z));add_link(m,(LOAD_X,cy,CHAIN_Z),k);load.append((g['uuid'],cy))
 m.group('Chaîne sur le renvoi')
 for i in range(7):
  a=math.pi*i/6;add_link(m,(8+1.5*math.cos(a),headY+1.5*math.sin(a),CHAIN_Z),i,a)
 if animated and pose_lift==0:
  duration=travel/16;eye0=16+cells*16-1.8
  for down in[False,True]:
   anim={'uuid':uid(),'name':'descendre'if down else'monter','loop':'once','override':False,'length':duration,'snapping':20,'animators':{}};values=(travel,0)if down else(0,travel);anim['animators'][moving['uuid']]={'name':'Chariot mobile','type':'bone','keyframes':[kf('position',0,(0,values[0],0)),kf('position',duration,(0,values[1],0))]}
   for g,r in[(head,1.5),(gear,.7)]:anim['animators'][g['uuid']]={'name':g['name'],'type':'bone','keyframes':[kf('rotation',0,(0,0,values[0]/r*180/math.pi)),kf('rotation',duration,(0,0,values[1]/r*180/math.pi))]}
   for group,cy in load:
    cross=(cy-eye0+.35)/16
    if not 0<cross<duration:continue
    t=duration-cross if down else cross;initial=(0,0,0)if down else(1,1,1);last=(1,1,1)if down else(0,0,0);anim['animators'][group]={'name':'Maillon de charge','type':'bone','keyframes':[kf('scale',0,initial,True),kf('scale',t,last,True)]}
   m.animations.append(anim)
 return m

def write_obj(model,path):
 lines=['# 1 OBJ unit = 1 Minecraft block','mtllib '+path.with_suffix('.mtl').name];count=1;normalindex=1
 for i,e in enumerate(model.elements):
  lines.extend(['g part_'+str(i),'usemtl rigging'])
  for f in e['faces'].values():
   ids=f['vertices'];p=np.array([e['vertices'][k]for k in ids]);n=np.cross(p[1]-p[0],p[2]-p[0]);n/=np.linalg.norm(n)
   for k in ids:lines.append('v %.6f %.6f %.6f'%tuple(np.array(e['vertices'][k])/16))
   for k in ids:
    u,v=f['uv'][k];lines.append('vt %.6f %.6f'%(u/512,1-v/512))
   lines.append('vn %.6f %.6f %.6f'%tuple(n));lines.append('f '+' '.join(f'{count+j}/{count+j}/{normalindex}'for j in range(len(ids))));count+=len(ids);normalindex+=1
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text('\n'.join(lines)+'\n');path.with_suffix('.mtl').write_text('newmtl rigging\nKa 0.2 0.2 0.2\nKd 1 1 1\nKs 0 0 0\nmap_Kd #detail\n')
def export_block(name,m,black):
 key=name+('_noir'if black else'');write_obj(m,AS/'models/rigging'/(key+'.obj'));p=AS/'models/block'/(key+'.json')
 if p.exists():d=json.loads(p.read_text())
 else:
  base='truss_corner_3'if'junction'in name else'truss_h1_l2_petit';d=copy.deepcopy(json.loads((AS/'models/block'/(base+('_noir'if black else'')+'.json')).read_text()))
 d.update(loader='forge:obj',model='concertstage:models/rigging/'+key+'.obj',flip_v=True,automatic_culling=False,shade_quads=True);d.setdefault('textures',{})['detail']='concertstage:block/rigging_detail'+('_noir'if black else'');d['textures']['particle']=d['textures']['detail'];dump(p,d);dump(BB/'trusses'/(key+'.bbmodel'),m.data(black))
def export_extra_blocks():
 for name,size in EXTRA.items():
  for black in[False,True]:
   key=name+('_noir'if black else'');variants={}
   for i,facing in enumerate(['north','east','south','west']):
    for part in range(27):
     row={'model':'concertstage:block/'+(key if part==0 else'empty_truss')}
     if i:row['y']=i*90
     variants[f'facing={facing},part={part}']=row
   dump(AS/'blockstates'/(key+'.json'),{'variants':variants});display=json.loads((AS/'models/item/truss_h1_l2_petit.json').read_text()).get('display',{});dump(AS/'models/item'/(key+'.json'),{'parent':'concertstage:block/'+key,'display':display});loot=json.loads((ROOT/'common/src/main/resources/data/concertstage/loot_tables/blocks/truss_h1_l2_petit.json').read_text());dump(ROOT/'common/src/main/resources/data/concertstage/loot_tables/blocks'/(key+'.json'),json.loads(json.dumps(loot).replace('concertstage:truss_h1_l2_petit','concertstage:'+key)))
   if black:recipe={'type':'minecraft:crafting_shapeless','ingredients':[{'item':'concertstage:'+name},{'item':'minecraft:black_dye'}],'result':{'item':'concertstage:'+key}}
   elif name=='truss_ladder_l1':recipe={'type':'minecraft:crafting_shaped','pattern':['I I',' B ','I I'],'key':{'I':{'item':'minecraft:iron_ingot'},'B':{'item':'minecraft:iron_bars'}},'result':{'item':'concertstage:'+key}}
   elif name=='truss_ladder_l2':recipe={'type':'minecraft:crafting_shapeless','ingredients':[{'item':'concertstage:truss_ladder_l1'}]*2,'result':{'item':'concertstage:'+key}}
   elif name=='truss_ladder_v2':recipe={'type':'minecraft:crafting_shaped','pattern':['L','I'],'key':{'L':{'item':'concertstage:truss_ladder_l2'},'I':{'item':'minecraft:iron_ingot'}},'result':{'item':'concertstage:'+key}}
   else:recipe={'type':'minecraft:crafting_shapeless','ingredients':[{'item':'concertstage:'+('truss_corner_6'if name.endswith('cross')else'truss_corner_3')}]+[{'item':'minecraft:iron_bars'}]*(2 if name.endswith('_t')else 1),'result':{'item':'concertstage:'+key}}
   dump(ROOT/'common/src/main/resources/data/concertstage/recipes'/(key+'.json'),recipe)
   labels={'truss_ladder_l1':('Ladder truss 1 m','Truss ladder 1 m'),'truss_ladder_l2':('Ladder truss 2 m','Truss ladder 2 m'),'truss_ladder_v2':('Vertical ladder truss 2 m','Truss ladder verticale 2 m'),'truss_junction_90':('90-degree box-truss junction','Jonction de truss à 90°'),'truss_junction_t':('T box-truss junction','Jonction de truss en T'),'truss_junction_cross':('4-way box-truss junction','Jonction de truss en croix')}
   for i,lang in enumerate(['en_us','fr_fr']):
    p=AS/'lang'/(lang+'.json');d=json.loads(p.read_text());d['block.concertstage.'+key]=labels[name][i]+((' (black)'if i==0 else' (noire)')if black else'');dump(p,d)
 # Lightweight vanilla fallback for ladders outside Forge.
 for name in['truss_ladder_l1','truss_ladder_l2','truss_ladder_v2']:
  L=16 if name.endswith('l1')else 32;vertical=name.endswith('v2');elements=[]
  def box(a,b):return {'from':a,'to':b,'faces':{f:{'texture':'#0','uv':[0,0,4,4]}for f in['up','down','north','south','east','west']}}
  for y in[4,12]:elements.append(box([y-.45,0,7.55],[y+.45,L,8.45])if vertical else box([0,y-.45,7.55],[L,y+.45,8.45]))
  for p in np.linspace(1.8,L-1.8,max(2,round(L/4))):elements.append(box([4,float(p)-.18,7.82],[12,float(p)+.18,8.18])if vertical else box([float(p)-.18,4,7.82],[float(p)+.18,12,8.18]))
  for b in[False,True]:
   p=AS/'models/block'/(name+('_noir'if b else'')+'.json');d=json.loads(p.read_text());d['elements']=elements;dump(p,d)
 p=ROOT/'common/src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json';d=json.loads(p.read_text());d['values']=list(dict.fromkeys(d['values']+['concertstage:'+n+s for n in EXTRA for s in['','_noir']]));dump(p,d)

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--pilot',action='store_true');args=parser.parse_args();catalog=[]
 for black,im in TEXTURES.items():
  key='rigging_detail'+('_noir'if black else'')+'.png'
  for p in[AS/'textures/block'/key,BB/'textures'/key]:p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
 for name in(['truss_h1_l2']if args.pilot else list(LEGACY)+list(EXTRA)):
  m=standard(name)
  for black in[False,True]:export_block(name,m,black);catalog.append({'name':name+('_noir'if black else''),'parts':len(m.elements),'quads':len(m.quads()),'connections':m.connections})
  print('Truss',name,len(m.elements),flush=True)
 if not args.pilot:export_extra_blocks()
 for h in([10]if args.pilot else HEIGHTS):
  dump(AS/'rigging'/f'mast_{h}.json',{'height':h,'cells':[{'near':mast_cell(h,c).quads(),'far':mast_cell(h,c,True).quads()}for c in range(h)]})
  for kind in(['corner3']if args.pilot else CARRIAGES):
   for black in[False,True]:
    m=full_tower(h,kind,black);dump(BB/'towers'/(m.name+'.bbmodel'),m.data(black));catalog.append({'name':m.name,'parts':len(m.elements),'quads':len(m.quads()),'height':h,'carriage':kind,'max_lift_blocks':h-2-CARRIAGES[kind][0]});print('Tower',m.name,len(m.elements),flush=True)
 for kind in(['corner3']if args.pilot else CARRIAGES):
  m=carriage(kind);dump(AS/'rigging'/('carriage_'+kind+'.json'),{'near':m.quads(),'far':carriage(kind,True).quads()})
  for black in[False,True]:dump(BB/'carriages'/('carriage_'+kind+('_noir'if black else'')+'.bbmodel'),m.data(black))
 for name,r in[('head_wheel',1.5),('motor_wheel',.7)]:dump(AS/'rigging'/(name+'.json'),{'near':wheel(name,r).quads(),'far':wheel(name,r,True).quads()})
 fill=Model('bag_fill');fill.group('Chaîne stockée');fill.mesh('Tas de chaîne dans le sac',[(12,0,-2.2),(15.2,0,-2.2),(15.2,0,.9),(12,0,.9),(12,.6,-2.2),(15.2,.6,-2.2),(15.2,.6,.9),(12,.6,.9),(13.6,1,-.65)],[[0,1,5,4],[1,2,6,5],[2,3,7,6],[3,0,4,7],[4,5,8],[5,6,8],[6,7,8],[7,4,8]],'scale');dump(AS/'rigging/bag_fill.json',{'near':fill.quads()});dump(AS/'rigging/chain_link.json',{'a':LINKS[0].quads(),'b':LINKS[1].quads(),'pitch':PITCH});dump(DOC/'catalogue.json',catalog);print('DONE',len(catalog),'main models',flush=True)
 import match_truss_lighting;match_truss_lighting.main()   # truss éclairés comme les tours
if __name__=='__main__':main()
