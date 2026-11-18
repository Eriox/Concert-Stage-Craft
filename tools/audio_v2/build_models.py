"""Blender 4.5: original CSC Orvane cabinets, six supports and an animated-ready digital desk.
Uses real metres. Minecraft XYZ -> Blender (X,-Z,Y). Native files contain separate HD/GAME collections.
"""
import bpy, math, json, pathlib, sys, os
from mathutils import Vector
ROOT=pathlib.Path(__file__).resolve().parents[2]; A=ROOT/'common/src/main/resources/assets/concertstage'; OUT=ROOT/'tools/audio_v2/Models';OUT.mkdir(parents=True,exist_ok=True)
ATLAS=A/'textures/block/speakers_atlas.png'
COLORS={'Coating':(36,39,43),'Grille':(24,27,30),'Rigging':(61,66,72),'Steel':(148,155,160),'Rubber':(15,17,20),'Accent':(47,112,126),'Screen':(12,25,31),'White':(220,230,232),'Red':(220,40,45),'Green':(40,210,100),'Amber':(235,156,36),'Recess':(8,10,12)}
UV={key:((800+(i%12)*16+8)/1024,(320+(i//12)*16+8)/1024) for i,key in enumerate(COLORS)}
# The atlas is prepared by prepare_atlas.py before launching Blender.
MATS={};COL=None;HD=False
V=lambda p:Vector((p[0],-p[2],p[1]))
def setup():
 global MATS
 bpy.ops.wm.read_factory_settings(use_empty=True)
 bpy.context.scene.unit_settings.system='METRIC';bpy.context.scene.unit_settings.scale_length=1
 MATS={}
 for key,rgb in COLORS.items():
  m=bpy.data.materials.new('CSC_'+key);m.use_nodes=True;p=m.node_tree.nodes.get('Principled BSDF');p.inputs['Base Color'].default_value=(*[(x/255/12.92 if x/255 <= .04045 else ((x/255+.055)/1.055)**2.4) for x in rgb],1);p.inputs['Roughness'].default_value=.7 if key in ('Coating','Rubber') else .48;p.inputs['Metallic'].default_value=.8 if key in ('Steel','Rigging','Grille') else 0
  m['mc_key']=key;MATS[key]=m
  if key=='Coating':
   noise=m.node_tree.nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=320
   bump=m.node_tree.nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.16;bump.inputs['Distance'].default_value=.0007;m.node_tree.links.new(noise.outputs['Fac'],bump.inputs['Height']);m.node_tree.links.new(bump.outputs['Normal'],p.inputs['Normal'])
def mesh(name,verts,faces,mat='Coating', bevel=0):
 me=bpy.data.meshes.new(name);me.from_pydata([V(p) for p in verts],[],faces);me.update();o=bpy.data.objects.new(name,me);COL.objects.link(o);o.data.materials.append(MATS[mat]);o.data.uv_layers.new(name='UVMap')
 if bevel:
  mod=o.modifiers.new('Machined edge radii','BEVEL');mod.width=bevel;mod.segments=3 if HD else 1
 return o
def box(name,lo,hi,mat='Coating',bevel=0):
 x,y,z=lo;X,Y,Z=hi
 return mesh(name,[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)],mat,bevel)
def append_box(o,lo,hi):
 x,y,z=lo;X,Y,Z=hi;me=o.data;verts=[v.co.copy() for v in me.vertices];faces=[tuple(p.vertices) for p in me.polygons];start=len(verts)
 verts += [V(p) for p in [(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]]
 faces += [tuple(start+i for i in f) for f in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]]
 me.clear_geometry();me.from_pydata(verts,[],faces);me.update()
 if not me.uv_layers:me.uv_layers.new(name='UVMap')

def beam(name,a,b,r,mat='Rigging',segments=None):
 n=segments or (12 if HD else 8);a=Vector(a);b=Vector(b);axis=(b-a).normalized();u=axis.cross(Vector((0,1,0)))
 if u.length<.01:u=axis.cross(Vector((1,0,0)))
 u.normalize();v=axis.cross(u);verts=[]
 for p in (a,b):
  for i in range(n):verts.append(p+r*(math.cos(i*2*math.pi/n)*u+math.sin(i*2*math.pi/n)*v))
 faces=[tuple(reversed(range(n))),tuple(range(n,n*2))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
 return mesh(name,verts,faces,mat)
def empty(name,p):
 o=bpy.data.objects.new(name,None);COL.objects.link(o);o.location=V(p);o.empty_display_type='SPHERE';o.empty_display_size=.025
 return o
def screw(name,x,y,z):
 beam(name,(x,y,z-.002),(x,y,z+.006),.008 if HD else .007,'Steel',8)
 if HD:box(name+'_drive',(x-.004,y-.001,z+.006),(x+.004,y+.001,z+.0065),'Recess')
def grille(name,x0,y0,x1,y1,z):
 if not HD:
  o=box(name,(x0,y0,z-.005),(x1,y1,z),'Grille');o['grille']=True;return
 # Rectangular cells surrounding actual octagonal perforations, with a black backing cabinet behind.
 cols=max(2,int((x1-x0)/.025));rows=max(2,int((y1-y0)/.025));dx=(x1-x0)/cols;dy=(y1-y0)/rows;verts=[];faces=[]
 outer=[(-.5,-.5),(0,-.5),(.5,-.5),(.5,0),(.5,.5),(0,.5),(-.5,.5),(-.5,0)]
 for c in range(cols):
  for r in range(rows):
   x=x0+(c+.5)*dx;y=y0+(r+.5)*dy;start=len(verts)
   verts.extend([(x+u*dx,y+v*dy,z) for u,v in outer]);verts.extend([(x+.007*math.cos(-3*math.pi/4+i*math.pi/4),y+.007*math.sin(-3*math.pi/4+i*math.pi/4),z) for i in range(8)])
   for k in range(8):faces.append((start+k,start+(k+1)%8,start+8+(k+1)%8,start+8+k))
 mesh(name,verts,faces,'Grille')
def handle(name,x,y,z,side=False):
 if side:
  sign=1 if x>0 else -1;cx=x-sign*.07
  box(name+'_cup',(cx-.002,y-.07,z-.10),(cx+.002,y+.07,z+.10),'Recess')
  beam(name+'_grip',(x+sign*.006,y,z-.065),(x+sign*.006,y,z+.065),.013,'Rubber')
  for dz in (-.08,.08):box(name+'_mount',(min(cx,x+sign*.012),y-.035,z+dz-.015),(max(cx,x+sign*.012),y+.035,z+dz+.015),'Rigging',.004)
 else:
  box(name+'_cup',(x-.10,y-.065,z-.004),(x+.10,y+.065,z),'Recess')
  beam(name+'_grip',(x-.075,y,z+.012),(x+.075,y,z+.012),.013,'Rubber')
  for dx in (-.085,.085):box(name+'_mount',(x+dx-.012,y-.035,z-.005),(x+dx+.012,y+.035,z+.019),'Rigging',.003)
def cabinet(w,h,d,sub=False,variant=0):
 inset=.028;back=w*.89;rearH=h*.88 if not sub else h
 body=mesh('Cabinet | tapered multi-panel shell',[(-w/2+inset,.018,-.015),(w/2-inset,.018,-.015),(w/2-inset,h,-.015),(-w/2+inset,h,-.015),(-back/2,.025,-d+.02),(back/2,.025,-d+.02),(back/2,rearH,-d+.02),(-back/2,rearH,-d+.02)],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)],'Coating',.008)
 # Genuine blind recesses, retained as editable Boolean operands in Blender.
 def pocket(name,lo,hi):
  cutter=box('Construction cutter | '+name,lo,hi,'Recess');cutter['csc_cutter']=True;cutter.hide_render=True;cutter.hide_set(True)
  mod=body.modifiers.new('Recess | '+name,'BOOLEAN');mod.operation='DIFFERENCE';mod.solver='EXACT';mod.object=cutter
 for side in (-1,1):
  x=side*(w/2*(1-.11*.55)-.015);pocket('side carry cup',(x-.09,h*.48-.075,-d*.55-.105),(x+.09,h*.48+.075,-d*.55+.105))
 for x in (-back*.27,back*.27):pocket('rear carry cup',(x-.11,h*.55-.075,-d-.02),(x+.11,h*.55+.075,-d+.076))
 pocket('connector panel',(-.115,.075,-d-.02),(.115,.225,-d+.075))
 # Geometry remains within the documented outer envelope.
 for side in (-1,1):
  x=side*(w/2-.018)
  box('Front rigging rail',(x-.018,.012,-.055),(x+.018,h-.012,-.019),'Rigging',.004)
  for y in (.025,h-.025):
   beam('Captive front connection pin',(x-.018,y,-.025),(x+.018,y,-.025),.011,'Steel',12)
   empty('CSC_Attach_'+('Left' if side<0 else 'Right')+('_Bottom' if y<h/2 else '_Top'),(x,y,-.025))
  box('Identification rail',(side*(w/2-.06)-.008,.04,-.008),(side*(w/2-.06)+.008,h-.04,-.002),'Accent')
  handle('Side carry handle',side*(w/2*(1-.11*.55)-.015),h*.48,-d*.55,True)
  for y in (.09,h-.09):
   for z in (-d*.26,-d*.72):
    fraction=-z/d;xx=side*((w/2-.028)*(1-fraction)+(back/2)*fraction)
    beam('Side structural bolt',(xx-side*.004,y,z),(xx+side*.007,y,z),.007,'Steel',8)
  for yy in (.09,h*.80):
   beam('Side reinforcement member',(side*(w/2-.025),yy,-.06),(side*(back/2+.004),yy*.92,-d+.025),.012,'Rigging')
 box('Top front extrusion',(-w/2+.018,h-.012,-.040),(w/2-.018,h,-.020),'Rigging',.001)
 # Independent protective grille panels, different layouts on the two cabinets.
 count=2 if variant==1 or sub else 3; usable=w-.15;gap=.022;panel=(usable-gap*(count-1))/count
 gy0=.12 if sub else .045;gy1=h-.08
 for j in range(count):
  x0=-usable/2+j*(panel+gap);grille('Perforated grille bay '+str(j+1),x0,gy0,x0+panel,gy1,-.002)
 if sub:
  for x in (-w*.28,w*.28):
   box('Bass reflex duct rim',(x-.16,.027,-.012),(x+.16,.096,-.005),'Rigging',.005)
   box('Bass reflex duct',(x-.145,.039,-.011),(x+.145,.084,-.004),'Recess')
 for x in (-back*.27,back*.27):
  box('Rear recessed cup',(x-.105,h*.55-.07,-d+.074),(x+.105,h*.55+.07,-d+.075),'Recess')
  beam('Rear grip',(x-.07,h*.55,-d+.020),(x+.07,h*.55,-d+.020),.012,'Rubber')
  for dx in (-.08,.08):box('Grip end bracket',(x+dx-.012,h*.55-.025,-d+.02),(x+dx+.012,h*.55+.025,-d+.075),'Rigging',.003)
 for side in (-1,1):
  x=side*(back/2-.018)
  box('Rear angle-selection spine',(x-.018,.018,-d+.015),(x+.018,rearH-.012,-d+.05),'Rigging',.003)
  for y in (.025,rearH-.025):
   beam('Rear rigging pivot',(x-.016,y,-d+.025),(x+.016,y,-d+.025),.010,'Steel')
   empty('CSC_Attach_Rear_'+('Left' if side<0 else 'Right')+('_Bottom' if y<rearH/2 else '_Top'),(x,y,-d+.025))
  for j in range(5):beam('Angle index hole',(x-.019,rearH-.065-j*.021,-d+.024),(x+.019,rearH-.065-j*.021,-d+.024),.004,'Recess',8)
 for x in (-back*.43,back*.43):box('Rear protection runner',(x-.02,.03,-d),(x+.02,rearH-.03,-d+.024),'Rubber',.003)
 box('Recessed connector bay',(-.11,.08,-d+.073),(.11,.22,-d+.074),'Recess',.005)
 for x in (-.06,.06):
  beam('Locking audio connector',(x,.15,-d+.028),(x,.15,-d+.073),.025,'Rigging',16)
  beam('Connector socket',(x,.15,-d+.025),(x,.15,-d+.027),.016,'Recess',12)
  for dx in (-.021,.021):screw('Connector screw',x+dx,.15,-d+.022)
 for x in (-w*.31,w*.31):
  for z in (-d*.18,-d*.78):box('Stacking foot',(x-.045,0,z-.045),(x+.045,.024,z+.045),'Rubber',.005)
 # Original small maker/model plate.
 o=box('CSC Orvane identity plate',(-.11,.025,-.003),(.11,.061,.0),'White');o['badge']=variant
 for x in (-w/2+.10,w/2-.10):
  for y in (.035,h-.035):screw('Grille captive screw',x,y,-.008)
 # The service hatch follows the roof rake and is physically seated on the shell.
 z0=-d*.68;z1=-d*.23;x0=-w*.22;x1=w*.22
 roof=lambda z:h+(rearH-h)*(-z-.015)/(d-.035)
 if sub:
  pocket('flush service hatch',(x0-.002,h-.012,z0-.002),(x1+.002,h+.02,z1+.002))
  box('Service hatch gasket',(x0-.002,h-.008,z0-.002),(x1+.002,h-.006,z1+.002),'Rubber')
 y0=roof(z0)+(-.007 if sub else .001);y1=roof(z1)+(-.007 if sub else .001)
 mesh('Seated service hatch',[(x0,y0,z0),(x1,y0,z0),(x1,y1,z1),(x0,y1,z1),(x0,y0+.006,z0),(x1,y0+.006,z0),(x1,y1+.006,z1),(x0,y1+.006,z1)],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)],'Rigging',.001)
 normal=Vector((0,1,-(h-rearH)/(d-.035))).normalized()
 for x in (x0+.014,x1-.014):
  for z in (z0+.014,z1-.014):
   start=Vector((x,roof(z)+(-.005 if sub else .006),z));beam('Service hatch bolt',start,start+normal*.004,.005,'Steel',8)
 empty('CSC_Attach_Top',(0,h,-d/2));empty('CSC_Attach_Bottom',(0,0,-d/2))
def frame(w,d,flying):
 t=.035;top=.07 if flying else .105;bottom=.015 if flying else .06
 for x in (-w/2,w/2):
  box('Longitudinal square section',(x-t/2,bottom,-d-.025),(x+t/2,top,.025),'Rigging',.003)
 for z in (0,-d,-d*.5):box('Cross member',(-w/2,bottom,z-t/2),(w/2,top,z+t/2),'Rigging',.003)
 for x in (-w/2,w/2):
  for z in (0,-d):
   box('Corner gusset',(x-.035,bottom,z-.035),(x+.035,top+.008,z+.035),'Rigging',.003)
   beam('M12 structural fixing',(x,top-.004,z),(x,top+.003,z),.006,'Steel',8)
   if not flying:box('Adjustable elastomer foot',(x-.04,0,z-.04),(x+.04,.06,z+.04),'Rubber',.004)
 for x in (-w/2+.018,w/2-.018):
  box('Cabinet clevis',(x-.02,0,-.045),(x+.02,.10,.015),'Steel',.003)
  beam('Locking pin',(x-.028,.025 if flying else .10,-.025),(x+.028,.025 if flying else .10,-.025),.010,'Steel')
  empty('CSC_Attach_Cabinet_'+('Left' if x<0 else 'Right'),(x,0 if flying else .12,-.025))
 if flying:
  for x in (-w*.22,w*.22):
   # Open U-shaped lifting lug, not a floating decorative solid block.
   for dx in (-.018,.018):beam('Shackle side',(x+dx,.07,-d*.5),(x+dx,.11,-d*.5),.008,'Steel')
   beam('Shackle crown',(x-.018,.11,-d*.5),(x+.018,.11,-d*.5),.008,'Steel')
   empty('CSC_Attach_Hoist_'+('Left' if x<0 else 'Right'),(x,.12,-d*.5))
 else:
  for z in (-d*.18,-d*.78):
   box('Load bearing foot crossmember',(-w/2,.06,z-.025),(w/2,.105,z+.025),'Rigging',.003)
   for x in (-w*.31,w*.31):box('Cabinet support pad',(x-.05,.105,z-.05),(x+.05,.12,z+.05),'Rubber',.002)
  for z in (-d*.30,-d*.70):box('Telescoping base crossrail',(-w/2-.04,.03,z-.025),(w/2+.04,.06,z+.025),'Rigging',.003)
 empty('CSC_Attach_Top',(0,.12,-d*.5));empty('CSC_Attach_Bottom',(0,0,-d*.5))
def desk():
 # Sloped shell, a real underframe, feet and rear service panel.
 w=.95
 mesh('Sloped magnesium console shell',[(-w,.81,.50),(w,.81,.50),(w,.89,.50),(-w,.89,.50),(-w,.81,-.52),(w,.81,-.52),(w,1.074,-.52),(-w,1.074,-.52)],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)],'Coating',.015)
 for x in (-.78,.78):
  for z in (-.34,.34):
   box('Aluminium leg',(x-.025,.04,z-.025),(x+.025,.82,z+.025),'Rigging',.004)
   box('Rubber foot',(x-.055,0,z-.055),(x+.055,.04,z+.055),'Rubber',.006)
  beam('Leg brace',(x,.15,-.34),(x,.75,.34),.012)
 box('Underframe crossbar',(-.80,.72,-.035),(.80,.76,.035),'Rigging',.004)
 for i in range(24):
  x=-.84+i*.065
  mesh('Fader travel slot',[(x-.006,.98-.18*.15+.002,.15),(x+.006,.98-.18*.15+.002,.15),(x+.006,.98-.18*.43+.002,.43),(x-.006,.98-.18*.43+.002,.43)],[(0,1,2,3)],'Recess')
  for z in (.02,-.055,-.13):
   y=.98-.18*z;beam('Encoder bank',(x,y,z),(x,y+.027,z),.014,'Rubber',12)
   box('Encoder position mark',(x-.002,y+.027,z-.012),(x+.002,y+.028,z-.005),'White')
  for z in (.075,.105):box('Illuminated key cap',(x-.016,.98-.18*z+.003,z-.011),(x+.016,.98-.18*z+.010,z+.011),'Rigging',.002)
  for j in range(3):box('Channel VU window',(x+.021, .99-.18*.28+j*.005,.28-j*.025),(x+.027,1.0-.18*.28+j*.005,.29-j*.025),'Screen')
 # Master bank.
 mesh('Master slot',[(.794,.955,.15),(.806,.955,.15),(.806,.904,.43),(.794,.904,.43)],[(0,1,2,3)],'Recess')
 # Dual tilted LCD assemblies. Same pivot and angle as AudioConsoleRenderer.
 from mathutils import Matrix
 for i in range(2):
  x=-.74+i*.84
  for dx in (.17,.49):
   box('Display pedestal foot',(x+dx-.06,1.038,-.45),(x+dx+.06,1.057,-.34),'Rigging',.004)
   box('Display pedestal stem',(x+dx-.016,1.049,-.42),(x+dx+.016,1.10,-.37),'Steel',.003)
  before=set(bpy.context.scene.objects)
  box('LCD magnesium rear shell',(x,1.085,-.48),(x+.66,1.37,-.32),'Coating',.009)
  for a,b,c,d in [(0,0,.66,.020),(0,.265,.66,.285),(0,.020,.020,.265),(.640,.020,.660,.265)]:
   box('LCD protective bezel',(x+a,1.085+b,-.326),(x+c,1.085+d,-.302),'Rigging',.003)
  box('LCD recessed glass',(x+.025,1.110,-.314),(x+.635,1.345,-.309),'Screen',.002)
  for dx in (.011,.649):
   for y in (1.098,1.357):beam('LCD bezel fastener',(x+dx,y,-.301),(x+dx,y,-.305),.002,'Steel',8)
  for dx in (.12,.20,.28,.36,.44,.52):box('LCD rear cooling slit',(x+dx,1.13,-.481),(x+dx+.022,1.19,-.479),'Recess')
  pivot=V((0,1.09,-.385));rotation=Matrix.Rotation(math.radians(-12),3,'X')
  for obj in set(bpy.context.scene.objects)-before:
   if obj.type=='MESH':
    for v in obj.data.vertices:v.co=pivot+rotation@(v.co-pivot)
    obj.data.update()
 for x in [i*.06-.48 for i in range(17)]:box('Rear ventilation slot',(x, .85,-.521),(x+.024,.91,-.519),'Recess')
 # Six logical group outputs, matching the engine; not 24 independent audio inputs.
 for i in range(6):
  x=-.65+i*.26
  box('Group '+str(i+1)+' connector mounting flange',(x-.028,.932,-.527),(x+.028,.99,-.519),'Rigging',.003)
  beam('Group '+str(i+1)+' XLR shell',(x,.96,-.534),(x,.96,-.525),.020,'Steel',12)
  beam('Group '+str(i+1)+' recessed insert',(x,.96,-.536),(x,.96,-.533),.015,'Recess',12)
  for dx,dy in [(-.006,.004),(.006,.004),(0,-.006)]:beam('XLR contact pin',(x+dx,.96+dy,-.537),(x+dx,.96+dy,-.533),.0015,'Steel',6)
  for dx in (-.021,.021):beam('Rear flange screw',(x+dx,.98,-.53),(x+dx,.98,-.527),.0025,'Steel',8)
 for x in (-.91,.91):
  box('Recessed carry handle well',(x-.022,.86,-.526),(x+.022,.91,-.521),'Recess',.004)
  for dx in (-.018,.018):beam('Carry handle attachment',(x+dx,.882,-.526),(x+dx,.882,-.540),.003,'Steel',8)
  beam('Carry grip',(x-.018,.882,-.540),(x+.018,.882,-.540),.006,'Rubber',10)
 for z in (-.34,.34):beam('Underframe lateral reinforcement',(-.76,.73,z),(.76,.73,z),.012,'Rigging')
 for x in (-.93,.93):
  for z in (-.47,.45):screw('Case panel screw',x,.99-.18*z,z)
 # Editable fader animation drivers. Minecraft reads the same console group/master values.
 for i in range(25):
  x=-.84+i*.065 if i<24 else .8;prop='CSC_Group_'+str(i//4) if i<24 else 'CSC_Master'
  bpy.context.scene[prop]=.8
  z=.4;y=.98-.18*z+.012
  o=box('Animated_Fader_'+str(i),(x-.025,y,z-.018),(x+.025,y+.018,z+.018),'Steel',.003)
  append_box(o,(x-.004,y-.014,z-.004),(x+.004,y,z+.004))
  centre=V((x,y,z));o.location=centre
  for vert in o.data.vertices:vert.co-=centre
  o.rotation_euler.x=math.atan(.18)
  for index,base,delta in [(1,centre.y,.23),(2,centre.z,.0414)]:
   curve=o.driver_add('location',index);driver=curve.driver;driver.expression=f'{base}+{delta}*value';var=driver.variables.new();var.name='value';var.targets[0].id_type='SCENE';var.targets[0].id=bpy.context.scene;var.targets[0].data_path='["'+prop+'"]'
 empty('CSC_Control_Master',(.8,.96,.2))
 for g in range(6):empty('CSC_Control_Group_'+str(g),(-.84+g*4*.065,.96,.2))
 
 for name,p in [('CSC_Screen_Track',(-.69,1.28,-.306)),('CSC_Screen_Mix',(.15,1.28,-.306))]:
  anchor=empty(name,p);anchor.location=pivot+rotation@(V(p)-pivot)
def export_mesh(objects,ident,dims=None):
 data=[];far=[];dep=bpy.context.evaluated_depsgraph_get()
 for o in objects:
  if o.type!='MESH' or o.name.startswith('Animated') or o.get('csc_cutter'):continue
  eo=o.evaluated_get(dep);me=eo.to_mesh();me.calc_loop_triangles()
  start=len(data)
  uv=me.uv_layers.active.data if me.uv_layers.active else None
  for tri in me.loop_triangles:
   for li in tri.loops:
    p=eo.matrix_world@me.vertices[me.loops[li].vertex_index].co;n=eo.matrix_world.to_3x3().inverted().transposed()@me.loops[li].normal;n.normalize();mat=me.materials[tri.material_index];key=mat.get('mc_key','Coating');u,v=UV[key]
    if o.get('grille'):
     bounds=[eo.matrix_world@Vector(b) for b in o.bound_box];lo=[min(p[k] for p in bounds) for k in range(3)];hi=[max(p[k] for p in bounds) for k in range(3)]
     u=(802+(p.x-lo[0])*200)/1024;v=(702+(hi[2]-p.z)*200)/1024
    if 'badge' in o:
     bounds=[eo.matrix_world@Vector(b) for b in o.bound_box];lo=[min(p[k] for p in bounds) for k in range(3)];hi=[max(p[k] for p in bounds) for k in range(3)]
     u=(802+(p.x-lo[0])/max(.001,hi[0]-lo[0])*218)/1024;v=(402+int(o['badge'])*80+(1-(p.z-lo[2])/max(.001,hi[2]-lo[2]))*54)/1024
    data.extend(round(float(x),6) for x in (p.x,p.z,-p.y,u,v,n.x,n.z,-n.y))
  bounds_object=[eo.matrix_world@Vector(v) for v in o.bound_box]
  diagonal=(Vector([max(v[k] for v in bounds_object) for k in range(3)])-Vector([min(v[k] for v in bounds_object) for k in range(3)])).length
  if diagonal >= .055:far.extend(data[start:])
  eo.to_mesh_clear()
 bounds=[min(data[k::8]) for k in range(3)]+[max(data[k::8]) for k in range(3)]
 result={'id':ident,'bounds':bounds,'width':dims[0] if dims else bounds[3]-bounds[0],'height':dims[1] if dims else bounds[4]-bounds[1],'depth':dims[2] if dims else bounds[5]-bounds[2],'near':data,'far':far}
 (A/'speakers'/f'{ident}.json').write_text(json.dumps(result,separators=(',',':')))
 # Forge OBJ inventory uses the same UV/material atlas and exact geometry.
 lines=['mtllib speakers.mtl','usemtl atlas'];vi=0;cx=(bounds[0]+bounds[3])/2;cz=(bounds[2]+bounds[5])/2
 for i in range(0,len(data),24):
  for k in range(3):
   x,y,z,u,v,nx,ny,nz=data[i+k*8:i+k*8+8];lines += [f'v {x-cx+.5} {y} {z-cz+.5}',f'vt {u} {1-v}',f'vn {nx} {ny} {nz}']
  lines.append('f '+' '.join(f'{vi+k}/{vi+k}/{vi+k}' for k in (1,2,3)));vi+=3
 (A/'models/speakers'/f'{ident}.obj').write_text('\n'.join(lines))
 scale=.7/max(result['width'],result['height'],result['depth'])
 model={'loader':'forge:obj','model':f'concertstage:models/speakers/{ident}.obj','flip_v':True,'automatic_culling':False,'shade_quads':True,'textures':{'atlas':'concertstage:block/speakers_atlas','particle':'concertstage:block/speakers_atlas'},'render_type':'minecraft:cutout','display':{k:{'rotation':[25,210,0],'translation':[0,0,0],'scale':[scale]*3} for k in ['gui','ground','fixed','firstperson_righthand','thirdperson_righthand']}}
 (A/'models/item'/f'{ident}.json').write_text(json.dumps(model))
 return result

def save_model(ident,builder,dims=None):
 if os.getenv("CSC_MODEL_FILTER") and ident!=os.environ["CSC_MODEL_FILTER"]:return
 global COL,HD
 setup();collections=[]
 for HD in (True,False):
  COL=bpy.data.collections.new('HIGH_DETAIL' if HD else 'GAME_READY');bpy.context.scene.collection.children.link(COL);builder();collections.append(COL)
 # Proper UV layers for every mesh and the atlas-aligned image surfaces in Blender.
 image=bpy.data.images.load(str(ATLAS));image.pack()
 for col in collections:
  for o in col.all_objects:
   if o.type!='MESH':continue
   me=o.data
   for loop in me.loops:me.uv_layers.active.data[loop.index].uv=UV[o.data.materials[0]['mc_key']]
   if o.get('grille') or 'badge' in o:
    mat=o.data.materials[0].copy();o.data.materials[0]=mat;node=mat.node_tree.nodes.new('ShaderNodeTexImage');node.image=image;bs=mat.node_tree.nodes.get('Principled BSDF');mat.node_tree.links.new(node.outputs['Color'],bs.inputs['Base Color'])
    # UV mapping in native Blender uses bottom-left image coordinates.
    pts=[v.co for v in me.vertices];lo=[min(p[k] for p in pts) for k in range(3)];hi=[max(p[k] for p in pts) for k in range(3)]
    for loop in me.loops:
     p=me.vertices[loop.vertex_index].co;u=(p.x-lo[0])/max(.001,hi[0]-lo[0]);v=(p.z-lo[2])/max(.001,hi[2]-lo[2])
     uu=(802+(p.x-lo[0])*200)/1024 if o.get('grille') else (802+u*218)/1024;vv=(702+(hi[2]-p.z)*200)/1024 if o.get('grille') else (402+int(o['badge'])*80+(1-v)*54)/1024
     me.uv_layers.active.data[loop.index].uv=(uu,1-vv)
    if o.get('grille'):
     mat.node_tree.links.new(node.outputs['Alpha'],bs.inputs['Alpha']);mat.surface_render_method='DITHERED'
 result=export_mesh(collections[1].all_objects,ident,dims)
 collections[1].hide_render=True;collections[1].hide_viewport=True
 scene=bpy.context.scene;scene['CSC_Units']='1 metre = 1 Minecraft block';scene['CSC_Model']=ident;scene['CSC_Design']='Original CSC Orvane / original CSC digital desk; no manufacturer branding'
 bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(ident+'.blend')))
 collections[0].hide_render=True;collections[0].hide_viewport=True;collections[1].hide_viewport=False
 bpy.ops.object.select_all(action='DESELECT')
 for o in collections[1].all_objects:
  if not o.get('csc_cutter'):o.hide_set(False);o.select_set(True)
 bpy.ops.export_scene.gltf(filepath=str(OUT/(ident+'.glb')),export_format='GLB',use_selection=True,export_apply=True)
 print('CSC BUILT',ident,'triangles',len(result['near'])//24)

for ident,w,h,d,sub,variant in [('csc_orvane_lx12',2.,.78,1.10,False,0),('csc_orvane_lx8',1.6,.6,.9,False,1),('csc_orvane_sb28',1.8,1.2,1.35,True,2)]:save_model(ident,lambda w=w,h=h,d=d,sub=sub,variant=variant:cabinet(w,h,d,sub,variant),(w,h,d))
for family,w,d in [('veyra',1.18,.62),('noryx',.88,.47),('orvane',2.,1.10)]:
 for flying in (True,False):save_model('csc_'+family+('_flying_frame' if flying else '_ground_frame'),lambda w=w,d=d,f=flying:frame(w,d,f))
save_model('csc_audio_console_mesh',desk)
# Shared link member, one metre long: scaled only along its length by the renderer.
save_model('csc_rig_link',lambda:box('Rigging splice',(-.012,0,-.012),(.012,1,.012),'Steel',.002))

save_model('csc_fader_cap',lambda:(box('Fader cap',(-.025,0,-.018),(.025,.018,.018),'Steel',.003),box('Tactile ridge',(-.022,.018,-.002),(.022,.021,.002),'White'),box('Fader actuator',(-.004,-.014,-.004),(.004,0,.004),'Rigging')))

def chain_link():
 verts=[];faces=[];n=12;t=6
 for i in range(n):
  angle=2*math.pi*i/n;centre=Vector((.009*math.cos(angle),.0135*math.sin(angle),0));normal=Vector((math.cos(angle),math.sin(angle),0))
  for k in range(t):verts.append(centre+.002*(math.cos(k*2*math.pi/t)*normal+Vector((0,0,math.sin(k*2*math.pi/t)))))
 for i in range(n):
  for k in range(t):faces.append((i*t+k,((i+1)%n)*t+k,((i+1)%n)*t+(k+1)%t,i*t+(k+1)%t))
 mesh('Forged oval chain link',verts,faces,'Steel')
save_model('csc_chain_link',chain_link)
