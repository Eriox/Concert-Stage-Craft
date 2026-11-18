# Compatibility geometry only; the 22 supplied projects are never regenerated.
from pathlib import Path
import math,json,uuid,base64,io,copy
import numpy as np
from PIL import Image
U=16.;uid=lambda:str(uuid.uuid4());CATALOG=[]
ROOT=Path(__file__).resolve().parent/'platform_source'
IMAGES=[Image.open(ROOT/'Textures'/n)for n in ['metals.png','anti_slip.png','hardware.png']]
SOURCES=[]
for im in IMAGES:
 b=io.BytesIO();im.save(b,format='PNG');SOURCES.append('data:image/png;base64,'+base64.b64encode(b.getvalue()).decode())
MATS={'alu':(0,[0,0,256,256]),'anodized':(0,[256,0,512,256]),'steel':(0,[0,256,256,512]),'darkmetal':(0,[256,256,512,512]),'deck':(1,[0,0,512,512]),'bolt':(2,[0,0,64,64]),'rubber':(2,[64,0,128,64]),'grip':(2,[128,0,192,64]),'scale':(2,[192,0,256,64])}
class Model:
 def __init__(self,name):self.name=name;self.elements=[];self.outliner=[];self.active=None;self.connections=[];self.notes=[]
 def group(self,name,origin=(0,0,0),parent=None):
  g=dict(name=name,origin=[round(v*U,6)for v in origin],rotation=[0,0,0],uuid=uid(),export=True,visibility=True,isOpen=False,children=[])
  (parent['children']if parent else self.outliner).append(g);self.active=g;return g
 def box(self,name,lo,hi,mat='alu',rotation=(0,0,0),origin=None):
  assert all(b>a for a,b in zip(lo,hi)),(name,lo,hi)
  ti,rect=MATS[mat];x,y,z=[b-a for a,b in zip(lo,hi)];dims={'north':(x,y),'south':(x,y),'east':(z,y),'west':(z,y),'up':(x,z),'down':(x,z)};faces={}
  for key,(a,b)in dims.items():
   if mat in ['bolt','grip','scale']:uv=list(rect)
   else:uv=[rect[0]+2,rect[1]+2,rect[0]+2+min(rect[2]-rect[0]-4,max(1,a*128)),rect[1]+2+min(rect[3]-rect[1]-4,max(1,b*128))]
   faces[key]={'uv':[round(v,5)for v in uv],'texture':ti,'rotation':0}
   if mat=='deck' and key!='up':faces[key]={'uv':[2,66,126,126],'texture':2,'rotation':0}
  o=origin if origin is not None else tuple((a+b)/2 for a,b in zip(lo,hi))
  e={'name':name,'type':'cube','uuid':uid(),'from':[round(v*U,6)for v in lo],'to':[round(v*U,6)for v in hi],'origin':[round(v*U,6)for v in o],'rotation':[round(v,6)for v in rotation],'faces':faces,'autouv':0,'rescale':False,'locked':False,'shade':True,'visibility':True,'export':True,'color':0}
  self.elements.append(e);self.active['children'].append(e['uuid']);return e
 def tube(self,name,axis,lo,hi,wall=.003,mat='alu'):
  # Four separate walls; hollow ends and visible real section thickness.
  others=[i for i in range(3)if i!=axis];a,b=others;t=min(wall,(hi[a]-lo[a])/3,(hi[b]-lo[b])/3)
  for side in [0,1]:
   l=list(lo);h=list(hi)
   if side==0:h[a]=l[a]+t
   else:l[a]=h[a]-t
   self.box(name+' · paroi',l,h,mat)
  for side in [0,1]:
   l=list(lo);h=list(hi);l[a]+=t;h[a]-=t
   if side==0:h[b]=l[b]+t
   else:l[b]=h[b]-t
   self.box(name+' · retour',l,h,mat)
 def beam(self,name,a,b,width=.025,thickness=.006,mat='steel'):
  delta=np.array(b)-a;center=(np.array(a)+b)/2;length=float(np.linalg.norm(delta));active=np.where(np.abs(delta)>1e-8)[0]
  # A planar member, expressed with one genuine cube rotation.
  if 2 not in active:lo=center-[length/2,width/2,thickness/2];hi=center+[length/2,width/2,thickness/2];rot=(0,0,math.degrees(math.atan2(delta[1],delta[0])))
  elif 1 not in active:lo=center-[length/2,thickness/2,width/2];hi=center+[length/2,thickness/2,width/2];rot=(0,-math.degrees(math.atan2(delta[2],delta[0])),0)
  else:lo=center-[thickness/2,width/2,length/2];hi=center+[thickness/2,width/2,length/2];rot=(-math.degrees(math.atan2(delta[1],delta[2])),0,0)
  self.box(name,lo,hi,mat,rot,center)
 def bolt(self,name,c,axis=1,head=.014):
  # A square washer and compact bolt head, with a hex socket texture.
  c=np.array(c,dtype=float);size=np.array([head*1.5]*3);size[axis]=.002;self.box(name+' · rondelle carrée',c-size/2,c+size/2,'darkmetal')
  size=np.array([head]*3);size[axis]=.006;cc=c.copy();cc[axis]+=.003;self.box(name+' · tête six pans',cc-size/2,cc+size/2,'bolt')
 def append(self,source,offset=(0,0,0),prefix='module'):
  mapping={e['uuid']:uid()for e in source.elements}
  for e0 in source.elements:
   e=copy.deepcopy(e0);e['uuid']=mapping[e['uuid']]
   for key in ['from','to','origin']:e[key]=[round(v+offset[i]*U,6)for i,v in enumerate(e[key])]
   self.elements.append(e)
  def clone(g):
   g=copy.deepcopy(g);g['uuid']=uid();g['origin']=[round(v+offset[i]*U,6)for i,v in enumerate(g['origin'])];g['children']=[mapping[c]if isinstance(c,str)else clone(c)for c in g['children']];return g
  self.outliner.append(dict(name=prefix,uuid=uid(),origin=[v*U for v in offset],rotation=[0,0,0],export=True,visibility=True,isOpen=False,children=[clone(g)for g in source.outliner]))
 def data(self):
  return {'meta':{'format_version':'4.12','model_format':'free','box_uv':False},'name':self.name,'model_identifier':self.name,'visible_box':[0,0,0],'resolution':{'width':512,'height':512},'elements':self.elements,'outliner':self.outliner,'textures':[{'path':'','name':name,'folder':'','namespace':'concertstage','id':str(i),'uuid':uid(),'source':SOURCES[i],'width':im.width,'height':im.height,'uv_width':im.width,'uv_height':im.height,'mode':'bitmap','saved':True,'render_mode':'default','visible':True,'particle':i==0}for i,(name,im)in enumerate(zip(['metals.png','anti_slip.png','hardware.png'],IMAGES))],'animations':[],'animation_controllers':[],'display':{}}
 def save(self,filename,category,dimension,description):
  data=self.data();p=ROOT/'Models'/filename;p.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')))
  CATALOG.append(dict(file=filename,category=category,dimensions_m=dimension,cubes=len(self.elements),description=description,connection_points=self.connections,notes=self.notes))
  return self

def leg(m,x,z,H,telescopic=False):
 parent=m.group(f'Pied démontable · x{x:.2f} z{z:.2f}',(x,0,z))
 m.box('Patin antimarque',(x-.05,0,z-.05),(x+.05,.007,z+.05),'rubber');m.box('Platine de répartition acier',(x-.048,.007,z-.048),(x+.048,.014,z+.048),'darkmetal')
 m.box('Vis de nivellement M12',(x-.006,.014,z-.006),(x+.006,.058,z+.006),'darkmetal')
 for y in [.024,.034]:m.box('Contre-écrou du vérin',(x-.011,y,z-.011),(x+.011,y+.006,z+.011),'bolt')
 m.box('Insert fileté du pied',(x-.025,.045,z-.025),(x+.025,.049,z+.025),'alu')
 top=H-.04;start=max(.15,H*.38)if telescopic else .047
 if telescopic:
  m.tube('Tube intérieur télescopique 38 × 38',1,(x-.019,.048,z-.019),(x+.019,start+.115,z+.019),.003,'anodized')
 m.tube('Pied porteur aluminium 48 × 48',1,(x-.024,start,z-.024),(x+.024,top,z+.024),.003,'alu')
 if telescopic:
  yy=start+.065;m.box('Broche traversante de verrouillage',(x-.037,yy-.005,z-.005),(x+.037,yy+.005,z+.005),'darkmetal');m.box('Tête de broche captive',(x+.036,yy-.014,z-.014),(x+.047,yy+.014,z+.014),'grip')
  m.box('Graduation du tube intérieur',(x-.010,.065,z-.0195),(x+.010,start-.005,z-.019),'scale')
 socketlen=min(.06,H-.11);bottom=top-socketlen
 m.tube('Récepteur du pied 60 × 60',1,(x-.03,bottom,z-.03),(x+.03,top+.006,z+.03),.004,'anodized')
 # Cam acts on the receiver and clamps the inserted leg.
 side=-1 if x<.5 else 1;xx=x+side*.032;yy=top-.055
 m.box('Axe de serrage du récepteur',(min(x,xx)-.004,yy-.005,z-.005),(max(x,xx)+.004,yy+.005,z+.005),'darkmetal')
 m.box('Levier de serrage captif',(xx-.006,yy-.009,z-.010),(xx+.006,yy+.009,z+.055),'grip',origin=(xx,yy,z))
 m.box('Patte de liaison récepteur / cadre',(x-.036,H-.09,z-.036),(x+.036,H-.082,z+.036),'alu')
 for dz in [-.026,.026]:m.bolt('Boulon de la patte',(x,H-.078,z+dz))
 return parent

def deck(W=2,D=1,H=.4,legs=True,brace=False,top=True,name='stage_deck'):
 m=Model(name);g=m.group('01 · Plateau et profilés aluminium',(0,H,0));floor=H-.09
 if top:m.box('Panneau multiplis 18 mm · antidérapant',( .018,H-.018,.018),(W-.018,H,D-.018),'deck')
 # Exterior C profiles with small contact lands; no full solid beam hiding the underside.
 for z,sign in [(0,1),(D,-1)]:
  za,zb=(0,.004)if sign==1 else(D-.004,D)
  m.box('Profil longitudinal · âme 4 mm',(0,floor,za),(W,H if top else H-.018,zb),'alu')
  za,zb=(0,.055)if sign==1 else(D-.055,D)
  for ya,yb in [(floor,floor+.004),(H-.022,H-.018)]:m.box('Profil longitudinal · aile',(0,ya,za),(W,yb,zb),'alu')
  if top:
   zc0,zc1=(0,.018)if sign==1 else(D-.018,D)
   m.box('Lèvre supérieure affleurante',(0,H-.003,zc0),(W,H,zc1),'alu')
  # Retained outward groove/rib section, inside nominal module footprint.
  za,zb=(.003,.009)if sign==1 else(D-.009,D-.003)
  for yy in [H-.067,H-.043]:m.box('Nervure du profil de liaison',(0,yy,za),(W,yy+.004,zb),'anodized')
 for x,sign in [(0,1),(W,-1)]:
  xa,xb=(0,.004)if sign==1 else(W-.004,W)
  m.box('Profil de bout · âme 4 mm',(xa,floor,.004),(xb,H if top else H-.018,D-.004),'alu')
  xa,xb=(0,.055)if sign==1 else(W-.055,W)
  for ya,yb in [(floor,floor+.004),(H-.022,H-.018)]:m.box('Profil de bout · aile',(xa,ya,.055),(xb,yb,D-.055),'alu')
  if top:
   xc0,xc1=(0,.018)if sign==1 else(W-.018,W)
   m.box('Lèvre de bout affleurante',(xc0,H-.003,.018),(xc1,H,D-.018),'alu')
 m.group('02 · Traverses sous plateau et goussets',(0,H-.05,0))
 for x in np.linspace(.5,W-.5,max(1,int(W/.5)-1)):
  m.tube('Traverse porteuse 40 × 20',2,(x-.020,H-.058,.055),(x+.020,H-.018,D-.055),.003,'alu')
 for x,sx in [(.085,1),(W-.085,-1)]:
  for z,sz in [(.085,1),(D-.085,-1)]:
   m.box('Équerre d’angle sous plateau',(min(x,x+sx*.11),H-.034,min(z,z+sz*.11)),(max(x,x+sx*.11),H-.027,max(z,z+sz*.11)),'anodized')
   m.beam('Gousset diagonal d’angle',(x-sx*.04,H-.083,z+sz*.115),(x+sx*.115,H-.083,z-sz*.04),.025,.006,'alu')
 if legs:
  for x in [.085,W-.085]:
   for z in [.085,D-.085]:leg(m,x,z,H,H>=.6)
 if not legs:
  m.group('03 · Récepteurs de pieds conservés sous le deck',(0,0,0))
  for x in [.085,W-.085]:
   for z in [.085,D-.085]:
    m.tube('Récepteur de pied 60 × 60',1,(x-.03,H-.10,z-.03),(x+.03,H-.034,z+.03),.004,'anodized')
    m.box('Patte de liaison du récepteur',(x-.036,H-.09,z-.036),(x+.036,H-.082,z+.036),'alu')
    side=-1 if x<W/2 else 1;xx=x+side*.032;yy=H-.095
    m.box('Axe de serrage du récepteur',(min(x,xx)-.004,yy-.005,z-.005),(max(x,xx)+.004,yy+.005,z+.005),'darkmetal')
    m.box('Levier captif du récepteur',(xx-.006,yy-.009,z-.010),(xx+.006,yy+.009,z+.055),'grip')
 if brace and legs:
  m.group('07 · Cadre bas et contreventements boulonnés',(0,H/2,0));low=max(.16,H*.22);upper=H-.18
  for z in [.085,D-.085]:
   m.tube('Lisse basse 30 × 30',0,(.085,low-.015,z-.015),(W-.085,low+.015,z+.015),.003,'darkmetal')
   for sign in [-1,1]:
    zz=z+sign*.016
    m.beam('Diagonale de stabilité · plat 25 × 5',(.085,low,zz),(W-.085,upper,zz)if sign==-1 else(W-.085,upper,zz),.025,.005,'steel')if sign==-1 else m.beam('Contre-diagonale de stabilité',(W-.085,low,zz),(.085,upper,zz),.025,.005,'steel')
   for x in [.085,W-.085]:
    for yy in [low,upper]:m.bolt('Fixation de contreventement',(x,yy,z-.020),2)
  for x in [.085,W-.085]:
   m.tube('Entretoise courte de stabilité',2,(x-.015,low-.015,.085),(x+.015,low+.015,D-.085),.003,'darkmetal');m.beam('Diagonale courte',(x,low,.085),(x,upper,D-.085),.025,.005,'steel')
 m.group('08 · Connecteurs et accès de bridage',(0,H-.05,0))
 for z in [0,D]:
  for x in [W*.25,W*.75]:
   m.box('Accès au connecteur de rive',(x-.025,H-.062,z if z==0 else z-.002),(x+.025,H-.038,z+.002 if z==0 else z),'grip')
   m.connections.append({'name':'rive_longue','position':[x,H-.05,z]})
 for x in [0,W]:
  m.box('Logement du verrou de bout',(x if x==0 else x-.002,H-.063,D*.5-.025),(x+.002 if x==0 else x,H-.037,D*.5+.025),'grip');m.connections.append({'name':'rive_courte','position':[x,H-.05,D/2]})
 for x in [.009,W-.009]:
  for z in [.06,D-.06]:m.bolt('Boulon d’assemblage du cadre',(x,H-.005,z),1,.010)
 return m

def join(m,x,z,H,along='x'):
 m.group('Brides de jonction des modules',(x,H,z))
 for delta in [-.20,.20]:
  if along=='x':lo=(x-.06,H-.095,z+delta-.025);hi=(x+.06,H-.087,z+delta+.025);points=[(x-.032,H-.086,z+delta),(x+.032,H-.086,z+delta)]
  else:lo=(x+delta-.025,H-.095,z-.06);hi=(x+delta+.025,H-.087,z+.06);points=[(x+delta,H-.086,z-.032),(x+delta,H-.086,z+.032)]
  m.box('Bride de liaison sous les ailes',lo,hi,'darkmetal')
  for c in points:m.bolt('Vis captive du connecteur',c)

def grid(nx,nz,H,name,brace=False):
 m=Model(name)
 for i in range(nx):
  for j in range(nz):m.append(deck(2,1,H,brace=brace),(i*2,0,j),f'Module {i+1} / {j+1}')
 for i in range(1,nx):
  for j in range(nz):join(m,2*i,j+.5,H,'x')
 for j in range(1,nz):
  for i in range(nx):join(m,i*2+1,j,H,'z')
 return m

def rail(L=2,corner=False):
 m=Model('safety_railings');m.group('01 · Poteaux et lisses — fixation au chant',(0,0,0))
 for x in [.085,L-.085]:
  m.tube('Poteau garde-corps 40 × 40',1,(x-.02,-.08,-.045),(x+.02,1.10,-.005),.003,'anodized')
  m.box('Étrier du poteau contre profil de rive',(x-.04,-.09,-.06),(x+.04,-.01,.02),'darkmetal')
  for y in [-.07,-.025]:m.bolt('Serrage de l’étrier',(x,y,-.066),2)
 for y in [.55,1.08]:m.tube('Lisse horizontale 40 × 30',0,(.065,y-.015,-.045),(L-.065,y+.015,-.005),.003,'anodized')
 m.box('Plinthe de protection 100 mm',(.065,.015,-.045),(L-.065,.115,-.035),'steel')
 m.group('02 · Colliers et bouchons',(0,0,0))
 for x in [.085,L-.085]:
  m.box('Bouchon antichoc',(x-.021,1.10,-.046),(x+.021,1.106,-.004),'rubber')
  for y in [.55,1.08]:m.bolt('Vis d’assemblage poteau / lisse',(x,y,-.052),2,.010)
 m.connections=[dict(name='poteau_sur_rive',position=[x,0,0])for x in [.085,L-.085]]
 return m

def stairs(H=.8):
 n=round(H/.2);D=n*.30;m=Model('stage_stairs');m.group('01 · Marches antidérapantes',(0,0,0))
 for i in range(n):
  yy=(i+1)*.2;z=i*.30
  m.box(f'Marche {i+1} · tôle antidérapante',(.015,yy-.018,z+.008),(.985,yy,z+.292),'deck')
  for xx in [.02,.94]:m.tube('Longeron sous marche',2,(xx,yy-.045,z+.008),(xx+.04,yy-.018,z+.292),.003,'alu')
  m.box('Nez de marche aluminium',( .015,yy-.023,z+.005),(.985,yy-.018,z+.032),'alu')
  for xx in [.06,.94]:m.bolt('Vis de marche',(xx,yy+.001,z+.07),1,.010)
 m.group('02 · Limons inclinés et pieds',(0,0,0))
 for xx in [.06,.94]:
  a=(xx,.125,.15);b=(xx,H-.075,D-.15)
  if n>1:m.beam('Limon porteur incliné',a,b,.060,.040,'anodized')
  for i in range(n):
   yy=(i+1)*.2;z=i*.30+.15;m.box('Selle de fixation de marche',(xx-.035,yy-.078,z-.04),(xx+.035,yy-.042,z+.04),'darkmetal')
  for z,yy in [(.15,.125),(D-.15,H-.075)]:
   m.tube('Pied de limon',1,(xx-.02,.014,z-.02),(xx+.02,yy,z+.02),.003,'alu');m.box('Patin du limon',(xx-.05,0,z-.05),(xx+.05,.014,z+.05),'rubber')
  m.box('Platine d’accrochage au plateau',(xx-.035,H-.10,D-.04),(xx+.035,H-.018,D+.04),'darkmetal');m.bolt('Accroche haute de l’escalier',(xx,H-.017,D+.012),1)
 m.group('03 · Mains courantes et montants',(0,0,0))
 for xx in [.015,.985]:
  for z,base,top in [(.15,.125,1.30),(D-.15,H-.075,H+1.10)]:m.tube('Montant de main courante',1,(xx-.0175,base,z-.0175),(xx+.0175,top,z+.0175),.0025,'anodized');m.bolt('Boulon de montant',(xx+.0185,base+.045,z),0)
  if n>1:m.beam('Main courante inclinée',(xx,1.30,.15),(xx,H+1.10,D-.15),.035,.035,'anodized')
 m.connections=[dict(name='arrivee_plateau',position=[.5,H,D])];return m