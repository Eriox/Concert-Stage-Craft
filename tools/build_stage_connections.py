#!/usr/bin/env python3
"""Derived construction and connection resources; original 22 models are immutable inputs."""
import copy,json,math
import numpy as np
import integrate_platforms as p
import improve_inventory as inv
import compatibility_stage_builder as b
A=p.AS;D=p.DATA;DER=p.ROOT/'tools/platform_derived'
def lift(v,H):
 v=np.array(v,float);y=v[1]/16;c=H-.09;v[1]=16*(y if y<=.04 else y+1 if y>=c else .04+(y-.04)*(c+.96)/(c-.04));return v

def taller(source,H):
 d=copy.deepcopy(source)
 for e in d['elements']:
  lo=np.array(e['from'],float);hi=np.array(e['to'],float);o=np.array(e['origin']);R=p.rotation(e);center=((lo+hi)/2-o)@R.T+o;size=hi-lo;axis=int(np.argmax(size))
  if abs(R[1,axis])>.2 and size[axis]>2.4:
   a=lift(center-R[:,axis]*size[axis]/2,H);z=lift(center+R[:,axis]*size[axis]/2,H);v=R[:,axis];w=(z-a)/np.linalg.norm(z-a);k=np.cross(v,w);c=float(v@w);K=np.array([[0,-k[2],k[1]],[k[2],0,-k[0]],[-k[1],k[0],0]]);RR=(np.eye(3)+K+K@K/(1+c))@R;size[axis]=np.linalg.norm(z-a);cc=(a+z)/2;e['from']=(cc-size/2).tolist();e['to']=(cc+size/2).tolist();e['origin']=cc.tolist();e['rotation']=np.degrees([math.atan2(RR[2,1],RR[2,2]),math.asin(max(-1,min(1,-RR[2,0]))),math.atan2(RR[1,0],RR[0,0])]).tolist()
  else:
   dy=16 if center[1]/16>H-.25 else lift(center,H)[1]-center[1]
   for key in ['from','to','origin']:e[key]=(np.array(e[key])+[0,dy,0]).tolist()
 return d

def raw(key,d,q,kind,walk=0,feet=None,ports=None):
 vertices=np.concatenate([qq[:,:3]for _,qq in q]);bounds=np.r_[vertices.min(axis=0),vertices.max(axis=0)].tolist();s={'id':key,'internal':True,'connection_generated':True,'kind':kind,'mode':'','grid':[1,1,1],'width':bounds[3]-bounds[0],'depth':bounds[5]-bounds[2],'walk':walk,'mountable':False,'occupied':[True],'cells':[[[]]],'raw_boxes':p.collision(d,np.zeros(3))if d else[bounds],'raw_bounds':bounds,'feet':feet or [],'ports':ports or []};p.dump(A/'stage_meshes'/(key+'.json'),p.mesh_json(q));return s

def clipped(q,axis,limit):
 out=[];seen=set()
 for slot,quad in q:
  face=p.clip(quad,axis,limit,False)
  if len(face)<3:continue
  faces=[face]if len(face)==4 else[np.array([face[0],face[i],face[i+1],face[i+1]])for i in range(1,len(face)-1)]
  for f in faces:
   signature=(slot,tuple(np.round(f.flatten(),7)))
   if signature not in seen:out.append((slot,f));seen.add(signature)
 return out

def flight(n):
 run=n*.3;m=b.stairs(n*.2);m.group('04 · Jonctions et appuis intermédiaires')
 for j in range(6,n,6):
  z=j*.3-.15;y=j*.2-.075
  for x in [.06,.94]:m.tube('Pied de jonction de limon',1,(x-.02,.014,z-.02),(x+.02,y,z+.02),.003,'alu');m.box('Patin intermédiaire',(x-.05,0,z-.05),(x+.05,.014,z+.05),'rubber')
  for x in [.015,.985]:m.tube('Montant intermédiaire de main courante',1,(x-.0175,y,z-.0175),(x+.0175,j*.2+1.1,z+.0175),.0025,'anodized')
 d=m.data();seen=set();kept=[]
 for e in d['elements']:
  sig=json.dumps({k:e[k]for k in['from','to','origin','rotation','faces']},sort_keys=True)
  if sig not in seen:seen.add(sig);kept.append(e)
 d['elements']=kept;ids={e['uuid']for e in kept}
 def clean(nodes):
  out=[]
  for node in nodes:
   if isinstance(node,str):
    if node in ids:out.append(node)
   else:node['children']=clean(node['children']);out.append(node)
  return out
 d['outliner']=clean(d['outliner'])
 for e in kept:
  if e['name']==f'Marche {n} · tôle antidérapante':e['to'][2]=run*16
 key=f'connected_stairs_{n:02d}';d['name']=key;p.dump(DER/'flights'/(key+'.bbmodel'),d);s=raw(key,d,clipped(p.cube_quads(d),2,run),'flight',n*.2,[[x,0,z]for x in[.06,.94]for z in[.15,run-.15]+[j*.3-.15 for j in range(6,n,6)]])
 for box in s['raw_boxes']:box[5]=min(box[5],run)
 s['raw_boxes']=[bb for bb in s['raw_boxes']if all(bb[i]<bb[i+3]for i in range(3))];s['run']=run;return s

def adapter(mm):
 h=mm/1000;m=b.Model('tower_stage_adapter');m.group('01 · Cadre porteur et plateau');m.box('Plateau d’appui antidérapant',(0,h-.018,0),(3,h,3),'deck')
 for z in [.035,2.965]:m.tube('Profil de rive',0,(0,h-.07,z-.025),(3,h-.018,z+.025),.004,'alu')
 for x in [.035,2.965]:m.tube('Profil de bout',2,(x-.025,h-.07,.06),(x+.025,h-.018,2.94),.004,'alu')
 for z in [.28125,1.5,2.71875]:m.tube('Traverse porteuse sous embase',0,(.06,h-.07,z-.025),(2.94,h-.035,z+.025),.004,'alu')
 for x in [.28125,1.5,2.71875]:m.tube('Traverse secondaire sous plateau',2,(x-.025,h-.035,.06),(x+.025,h-.018,2.94),.003,'alu')
 m.group('02 · Pieds et contreventements');feet=[[x,0,z]for x in[.08,2.92]for z in[.08,2.92]]+[[x,0,z]for x in[.28125,2.71875]for z in[.28125,2.71875]]
 for x,_,z in feet:m.box('Patin sous embase',(x-.05,0,z-.05),(x+.05,.01,z+.05),'rubber');m.tube('Pied porteur',1,(x-.035,.01,z-.035),(x+.035,h-.07,z+.035),.004,'alu')
 for x in [.08,2.92]:
  if h>=.4:m.beam('Renfort d’embase',(x,.04,.08),(x,h-.08,2.92),.035,.025,'anodized')
 d=m.data();key=f'tower_stage_adapter_{mm:03d}';d['name']=key;p.dump(DER/'adapters'/(key+'.bbmodel'),d);s=raw(key,d,clipped(p.cube_quads(d),1,h),'adapter',h,feet);s['raw_boxes']=[[*bb[:3],bb[3],min(h,bb[4]),bb[5]]for bb in s['raw_boxes']if bb[1]<h];return s

def guards(q,boxes,sides):
 if not sides:return q,boxes
 out=[];coll=[];n=len(q)//(len(sides)*3);nb=len(boxes)//(len(sides)*3)
 for i,side in enumerate(sides):
  dx,dy,dz={'south':(0,0,-.08),'east':(-.08,0,0),'west':(.08,0,0)}[side]
  for j in range(3):
   k=i*3+j;out+=p.translate(q[k*n:(k+1)*n],[dx,dy,dz]);coll.extend([[v+[dx,dy,dz,dx,dy,dz][a]for a,v in enumerate(bb)]for bb in boxes[k*nb:(k+1)*nb]])
 return out,coll

def main():
 specs=[s for s in json.loads((D/'stage_equipment.json').read_text())['equipment']if not s.get('connection_generated')];by={s['id']:s for s in specs};old_config=p.config;report=[]
 for old,key in [('raised_stage_platform_2x1_h120','raised_stage_platform_2x1_h220'),('assembly_festival_stage_8x4_h120','assembly_festival_stage_8x4_h220')]:
  src=json.loads((p.SOURCE/'Models'/(old+'.bbmodel')).read_text());d=taller(src,by[old]['walk']);d['name']=key;p.dump(DER/(key+'.bbmodel'),d);base=by[old];p.config=lambda k,m,base=base:(int(base['width']),int(base['depth']),base['walk']+1,True,base['kind'],np.zeros(3),'');s=p.export_one(key,d,p.cube_quads(d),{});s.update(connection_generated=True,raised_from=old);specs.append(s);report.append({'id':key,'from':old,'height_before':base['walk'],'height_after':s['walk'],'source_cubes':len(src['elements']),'derived_cubes':len(d['elements'])})
 for old,d,q,bay,sides in p.make_legacy():
  key=old.replace('scaffold_stage_','stage_structure_h300_');tall=taller(d,2);gq,gb=guards(q[len(p.cube_quads(d)):],d.get('_extra_collision',[]),sides);gq=p.translate(gq,[0,1,0]);full=p.cube_quads(tall)+gq;p.config=lambda k,m:(3,3,3,False,'platform',np.zeros(3),'');s=p.export_one(key,tall,full,{});s.update(connection_generated=True,raised_from=old);rawboxes=s['raw_boxes']+[[v+(1 if i in[1,4]else 0)for i,v in enumerate(bb)]for bb in gb];s['raw_boxes']=rawboxes
  for part in range(len(s['cells'])):s['cells'][part][0]=p.clipped_boxes(rawboxes,np.array([part%3,part//9,(part//3)%3]),s['grid'])
  bb=copy.deepcopy(tall);bb['name']=key;verts={};faces={}
  for i,(slot,quad)in enumerate(gq):
   names=[];uv={}
   for j,v in enumerate(quad):
    name=f'v{i}_{j}';names.append(name);verts[name]=(v[:3]*16).tolist();uv[name]=[float(v[3]*512),float(v[4]*512)]
   faces[f'f{i}']={'vertices':names,'uv':uv,'texture':slot}
  if faces:
   uid=b.uid();bb['elements'].append({'name':'Garde-corps adaptés','type':'mesh','uuid':uid,'origin':[0,0,0],'vertices':verts,'faces':faces,'visibility':True,'export':True,'color':0});bb['outliner'].append({'name':'Garde-corps rehaussés','uuid':b.uid(),'origin':[0,48,0],'rotation':[0,0,0],'children':[uid],'visibility':True,'export':True})
  p.dump(DER/(key+'.bbmodel'),bb);specs.append(s);report.append({'id':key,'from':old,'height_before':2,'height_after':3})
 p.config=old_config
 specs +=[flight(n)for n in range(1,41)];specs +=[adapter(mm)for mm in[100,200,400,600,800,896]]
 catalog={row['name']:row for row in json.loads((p.ROOT/'tools/realistic_docs/catalogue.json').read_text())if'connections'in row}
 for obj in sorted((A/'models/rigging').glob('truss*.obj')):
  key=obj.stem;q=inv.read_obj(obj);groups={}
  for port in catalog[key]['connections']:
   pt=np.array(port['point'])/16;axis=port['axis'];groups.setdefault((axis,round(float(pt[axis]),6)),[]).append(pt.tolist())
  ports=[]
  for (axis,plane),points in groups.items():
   normal=np.zeros(3);normal[axis]=-1 if plane==0 else 1;ports.append({'center':np.mean(points,axis=0).tolist(),'normal':normal.tolist(),'points':points})
  s=raw(key,None,q,'truss',ports=ports);specs.append(s);mesh=p.mesh_json(q);p.dump(A/'stage_meshes'/(key+'.json'),{str(i):[]for i in range(3)}|{str(4 if key.endswith('_noir')else 3):mesh['0']})
 p.dump(D/'stage_equipment.json',{'version':2,'equipment':specs});p.dump(p.DOC/'connection_models.json',report);finish(specs,report);print('Exported 9 raised structures, 40 flights, 6 adapters and 36 trusses',flush=True)

def finish(specs,report):
 labels={'sans_barrieres':('without railings','sans garde-corps'),'sud':('south railing','garde-corps sud'),'est':('east railing','garde-corps est'),'ouest':('west railing','garde-corps ouest'),'sud_est':('south/east railings','garde-corps sud/est'),'sud_tour':('south railing, tower bay','garde-corps sud, passage tour'),'sud_est_tour':('south/east railings, tower bay','garde-corps sud/est, passage tour')}
 base='stage_structure_h300_sans_barrieres';recipes={'sans_barrieres':['minecraft:iron_block']*8+['minecraft:black_carpet'],'sud':[base,'safety_railings_2m','safety_railings_1m'],'est':[base,'safety_railings_2m','safety_railings_1m','minecraft:iron_nugget'],'ouest':[base,'safety_railings_2m','safety_railings_1m','minecraft:stick'],'sud_est':[base,'safety_railings_2m','safety_railings_2m','safety_railings_1m','safety_railings_1m'],'sud_tour':[base,'truss_base_v2','safety_railings_2m','safety_railings_1m'],'sud_est_tour':[base,'truss_base_v2','safety_railings_2m','safety_railings_2m','safety_railings_1m','safety_railings_1m']}
 for row in report:
  key=row['id'];p.dump(D/'loot_tables/blocks'/(key+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'concertstage:'+key}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]});ingredients=recipes[key.removeprefix('stage_structure_h300_')]if key.startswith('stage_structure_h300_')else[row['from'],'minecraft:iron_block','minecraft:iron_bars'];p.dump(D/'recipes'/(key+'.json'),{'type':'minecraft:crafting_shapeless','ingredients':[{'item':i if ':'in i else'concertstage:'+i}for i in ingredients],'result':{'item':'concertstage:'+key}})
 messages={'stairs_incompatible':('Incompatible stair edge or height.','Bord ou hauteur incompatible.'),'stairs_click_upper_end':('Click the upper end to extend.','Cliquer en haut pour prolonger.'),'stairs_already_reaches_deck':('The flight already reaches the deck.','Cet escalier atteint déjà le plateau.'),'stairs_no_ground':('All stair feet need level solid supports.','Tous les pieds exigent des appuis solides au même niveau.'),'stairs_too_high':('Maximum flight height: 8 m.','Hauteur maximale : 8 m.'),'stairs_more_modules':('Hold enough modules in the same stack.','Tenir assez de modules dans la même pile.'),'stairs_landing_blocked':('Clear the platform landing first.','Dégager l’arrivée sur le plateau.'),'rig_click_connector':('Click a connector end.','Cliquer sur une extrémité de connexion.'),'rig_incompatible':('Connector patterns do not match.','Ces connexions ne sont pas compatibles.'),'rig_click_deck_top':('Click the deck walking surface.','Cliquer sur le dessus du plateau.'),'rig_no_support':('Complete support is required; towers need a clear 3 × 3 m base.','Appuis complets requis ; base libre de 3 × 3 m pour les tours.')}
 for locale,fr in [('en_us',False),('fr_fr',True)]:
  f=A/'lang'/(locale+'.json');lang=json.loads(f.read_text());lang.update({'message.concertstage.'+key:v[int(fr)]for key,v in messages.items()})
  for row in report:
   key=row['id'];s=next(s for s in specs if s['id']==key);name=lang.get('block.concertstage.'+row['from'],'Stage structure')
   if key.startswith('stage_structure_h300_'):name=('Structure porteuse 3 × 3 m — '+labels[key.removeprefix('stage_structure_h300_')][1])if fr else('Stage structure 3 × 3 m — '+labels[key.removeprefix('stage_structure_h300_')][0])
   lang['block.concertstage.'+key]=name+f' — {s["walk"]:g} m';lang['tooltip.concertstage.dimensions.'+key]=f'{s["width"]:g} × {s["depth"]:g} m · {s["walk"]:g} m';lang['tooltip.concertstage.use.'+key]='Rehaussée de 1 bloc ; pieds et renforts adaptés.'if fr else'Raised one block; extended legs and bracing.'
  for key in ['stage_stairs_h040','stage_stairs_h080','stage_stairs_h120']:lang['tooltip.concertstage.use.'+key]='Bord de scène : accès ajusté. En haut : prolonger. Accroupi + main vide : retirer un module.'if fr else'Deck edge: fitted access. Upper end: extend. Sneak + empty hand: remove one module.'
  p.dump(f,lang)
 for f in[p.ROOT/'common/src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json',D/'tags/blocks/stage_equipment.json']:
  data=json.loads(f.read_text());data['values']=list(dict.fromkeys(data['values']+['concertstage:'+r['id']for r in report]));p.dump(f,data)
if __name__=='__main__':main(); import removed_blocks; removed_blocks.purge()
