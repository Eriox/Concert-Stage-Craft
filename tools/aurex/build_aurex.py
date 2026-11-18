"""Native Blender authoring and export. No Blockbench input/output.
Coordinates in helpers: Minecraft X/Y/Z; Blender X/-Z/Y, metres.
Run: blender -b --python tools/aurex/build_aurex.py
"""
import pathlib,os,math,json
ROOT=pathlib.Path(__file__).resolve().parents[2]
# Reuse only Blender geometry/export helpers, never an existing cabinet design.
source=(ROOT/'tools/audio_v2/build_models.py').read_text().split('\nfor ident,w,h,d,sub,variant in')[0]
exec(compile(source,str(ROOT/'tools/audio_v2/build_models.py'),'exec'),globals())
OUT=ROOT/'tools/aurex/Models';OUT.mkdir(parents=True,exist_ok=True)
BASE=OUT.parent
ATLAS=ROOT/'common/src/main/resources/assets/concertstage/textures/block/aurex_atlas.png'
COLORS['Coating']=(23,25,28)
for p in ('Textures','Renders','Documentation'): (BASE/p).mkdir(exist_ok=True)
SPECS={'csc_aurex_m12':(1.6,.6,.9),'csc_aurex_s218_f':(1.8,1.15,1.1),'csc_aurex_m15_w':(.78,.46,.70)}

_coarse_grille=grille
def grille(name,x0,y0,x1,y1,z):
    if not HD:return _coarse_grille(name,x0,y0,x1,y1,z)
    cols=max(2,int((x1-x0)/.008));rows=max(2,int((y1-y0)/.008));dx=(x1-x0)/cols;dy=(y1-y0)/rows;verts=[];faces=[]
    outer=[(-.5,-.5),(0,-.5),(.5,-.5),(.5,0),(.5,.5),(0,.5),(-.5,.5),(-.5,0)]
    for c in range(cols):
        for r in range(rows):
            x=x0+(c+.5)*dx;y=y0+(r+.5)*dy;k=len(verts)
            verts.extend([(x+u*dx,y+v*dy,z) for u,v in outer]);verts.extend([(x+.002*math.cos(-3*math.pi/4+i*math.pi/4),y+.002*math.sin(-3*math.pi/4+i*math.pi/4),z) for i in range(8)])
            for i in range(8):faces.append((k+i,k+(i+1)%8,k+8+(i+1)%8,k+8+i))
    return mesh(name,verts,faces,'Grille')

def label(text,x,y,z,size=.025):
    cu=bpy.data.curves.new('Engraved '+text,'FONT');cu.body=text;cu.size=size;cu.extrude=.0004;cu.align_x='CENTER'
    o=bpy.data.objects.new('Identity '+text,cu);COL.objects.link(o);o.location=V((x,y,z));o.rotation_euler=(math.pi/2,0,0);o.data.materials.append(MATS['White'])
    bpy.context.view_layer.objects.active=o;o.select_set(True);bpy.ops.object.convert(target='MESH');o.select_set(False)
    if not o.data.uv_layers:o.data.uv_layers.new()
    o['secondary']=True

def cone(name,x,y,z,r):
    # Cone, surround and dustcap are visible through the HD perforations.
    seg=48 if HD else 16;verts=[];faces=[]
    for radius,zz in [(r,z),(r*.85,z-.012),(r*.24,z-.065),(0,z-.047)]:
        verts += [(x+radius*math.cos(i*2*math.pi/seg),y+radius*math.sin(i*2*math.pi/seg),zz) for i in range(seg)]
    for ring in range(3):
        for i in range(seg):faces.append((ring*seg+i,ring*seg+(i+1)%seg,(ring+1)*seg+(i+1)%seg,(ring+1)*seg+i))
    mesh(name,verts,faces,'Rubber')
    for i in range(8):screw('Driver basket bolt',x+(r+.008)*math.cos(i*math.pi/4),y+(r+.008)*math.sin(i*math.pi/4),z)

def horn(x,y,z,w,h):
    verts=[(x+a*w/2,y+b*h/2,z) for a,b in [(-1,-1),(1,-1),(1,1),(-1,1)]]+[(x+a*w*.13,y+b*h*.20,z-.12) for a,b in [(-1,-1),(1,-1),(1,1),(-1,1)]]
    mesh('Moulded asymmetric HF waveguide',verts,[(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)],'Recess')
    for dy in (-h*.22,h*.22):box('HF phase support',(x-.017,y+dy-.004,z-.11),(x+.017,y+dy+.004,z-.025),'Rubber')

def shell(w,h,d,taper):
    # Panel shell with open front and true side-handle cutouts.
    for sign in (-1,1):
        zs=[-d,-d*.52-.09,-d*.52+.09,-.022];ys=[.025,h*.52-.065,h*.52+.065,h]
        verts=[(sign*w/2*(1-(1-taper)*(-z/d)),y,z) for z in zs for y in ys]
        faces=[]
        for zi in range(3):
            for yi in range(3):
                if zi==1 and yi==1:continue
                f=(zi*4+yi,(zi+1)*4+yi,(zi+1)*4+yi+1,zi*4+yi+1)
                faces.append(f if sign<0 else tuple(reversed(f)))
        o=mesh(('Right' if sign>0 else 'Left')+' panel with genuine carry recess',verts,faces,'Coating')
        so=o.modifiers.new('18 mm cabinet wall','SOLIDIFY');so.thickness=.018;so.offset=-1
        xx=sign*w/2*(1-(1-taper)*.52)
        # A closed cup behind the aperture; the grip bridges its mounting lugs.
        box('Recessed handle cup backing',(min(xx-sign*.045,xx-sign*.043),h*.52-.067,-d*.52-.092),(max(xx-sign*.045,xx-sign*.043),h*.52+.067,-d*.52+.092),'Recess')
    # Separate cap panels and rear closure, continuous at the four side edges.
    for name,y in [('Top',h),('Bottom',.025)]:
        mesh(name+' panel',[(-w/2,y,-.022),(w/2,y,-.022),(w*taper/2,y,-d),(-w*taper/2,y,-d)],[(0,1,2,3)],'Coating')
        o=list(COL.objects)[-1];m=o.modifiers.new('18 mm laminated panel','SOLIDIFY');m.thickness=.018
    box('Rear composite panel',(-w*taper/2,.025,-d),(w*taper/2,h,-d+.018),'Coating',.004)
    # Handle cups sit on actual panel surfaces, with a dark pocket and a captured grip.
    for sign in (-1,1):
        z=-d*.52;xx=sign*(w/2*(1-(1-taper)*.52));a=Vector((xx,h*.52,z))
        # Recess impression and strong end brackets, integrated with the shell.
        for dy in (-.052,.052):
            beam('Handle end mounting',a+Vector((-sign*.018,dy,-.065)),a+Vector((-sign*.018,dy,.065)),.012,'Rigging',8)
        beam('Recessed side handle',a+Vector((-sign*.018,0,-.065)),a+Vector((-sign*.018,0,.065)),.014,'Rubber',12)
        for yy in (.065,h-.045):
            box('Rigging steel side plate',(min(xx-sign*.018,xx),yy-.022,-.075),(max(xx-sign*.018,xx),yy+.022,-.025),'Rigging',.003)
    for x in (-w*.31,w*.31):
        for z in (-.05,-d+.05):box('Stacking protection foot',(x-.034,0,z-.032),(x+.034,.025,z+.032),'Rubber',.004)

def rear(w,h,d):
    before=set(COL.objects)
    box('Recessed connector flange',(-.14,.105,-d-.001),(.14,.28,-d+.019),'Rigging',.004)
    for x in (-.075,.075):
        beam('Twist-lock connector collar',(x,.19,-d-.002),(x,.19,-d+.015),.028,'Steel',16)
        beam('Recessed contact insert',(x,.19,-d-.003),(x,.19,-d-.002),.018,'Recess',16)
        for i in range(4):
            a=i*math.pi/2;beam('Connector contact',(x+.009*math.cos(a),.19+.009*math.sin(a),-d-.004),(x+.009*math.cos(a),.19+.009*math.sin(a),-d-.003),.002,'Steel',6)
    for x in (-.125,.125):
        for y in (.12,.265):screw('Connector flange fixing',x,y,-d-.002)
    for x in (-w*.32,w*.32):box('Rear impact runner',(x-.018,.032,-d-.005),(x+.018,h-.035,-d+.016),'Rubber',.003)

    for ob in set(COL.objects)-before:
        if ob.type=='MESH':
            for vert in ob.data.vertices:vert.co.y-=.006
            ob.data.update()

def main():
    w,h,d=SPECS['csc_aurex_m12'];shell(w,h,d,.81)
    cone('12 inch low-mid cone',-.39,.30,-.055,.1524);horn(.32,.32,-.06,.54,.30)
    for x0,x1 in [(-.755,-.015),(.015,.755)]:grille('Removable folded microperforated grille',x0,.065,x1,.55,-.014)
    box('Central vertical structural rib',(-.014,.045,-.031),(.014,.563,-.004),'Rigging',.003)
    for sign in (-1,1):
        box('Front sculpted corner rail',(sign*.771-.010,.034,-.040),(sign*.771+.010,.57,-.006),'Rigging',.003)
    label('CSC  AUREX M12',.34,.075,-.003,.029);rear(w*.81,h,d)
    for x in (-w*.45,w*.45):
        for y in (.06,h-.055):screw('Captive grille fastener',x,y,-.007)
    for x in (-w/2+.018,w/2-.018):
        for z in (-.025,-d+.025):empty('CSC_Attach_Cabinet_'+str(x)+'_'+str(z),(x,h-.025,z))
    empty('CSC_Attach_Stand',(0,0,-d*.50))

def sub():
    w,h,d=SPECS['csc_aurex_s218_f'];shell(w,h,d,1)
    for x in (-.43,.43):
        cone('18 inch long-excursion cone',x,.60,-.080,.2286)
        grille('Double eighteen grille half',x-.395,.155,x+.395,1.075,-.016)
        box('Front slot reflex port',(x-.27,.047,-.08),(x+.27,.115,-.018),'Recess')
    box('Front structural divider',(-.016,.025,-.055),(.016,1.13,-.006),'Rigging',.003)
    for sign in (-1,1):
        x=sign*(w/2-.018)
        for z in (-.025,-d+.025):
            box('Continuous load-bearing rigging strip',(x-.016,.025,z-.018),(x+.016,h-.025,z+.018),'Rigging',.003)
            for yy in (.025,h-.025):
                beam('Cabinet captive rigging pin',(x-.016,yy,z),(x+.016,yy,z),.010,'Steel',12)
                empty('CSC_Attach_'+('Top' if yy>.5 else 'Bottom')+'_'+('L' if sign<0 else 'R')+('_Front' if z>-.5 else '_Rear'),(x,yy,z))
    rear(w,h,d);label('CSC  AUREX S218-F',0,.12,-.003,.032)
    for x in (-.86,.86):
        for y in (.15,1.065):screw('Captive grille fastener',x,y,-.008)

def wedge():
    w,h,d=SPECS['csc_aurex_m15_w']
    # Two angled floor faces and a sloping acoustic baffle, independent of the main/sub geometry.
    cross=[(0,.13),(-.54,h),(-d,.29),(-d,.025),(-.12,.025)]
    verts=[(x,y,z) for x in (-w/2,w/2) for z,y in cross]
    faces=[tuple(reversed(range(5))),tuple(range(5,10))]+[(i,(i+1)%5,(i+1)%5+5,i+5) for i in range(5)]
    body=mesh('Asymmetric dual-angle plywood wedge',verts,faces,'Coating',.008)
    # Draw on baffle coordinates: height follows z, all pieces seated to that plane.
    origin=Vector((0,.13,0));u=Vector((1,0,0));v=Vector((0,h-.13,-.54)).normalized();normal=u.cross(v).normalized()
    before=set(COL.objects)
    cone('15 inch wedge low-mid cone',-.105,.30,-.033,.1905);horn(.245,.35,-.035,.19,.26)
    grille('Sloping strengthened perforated grille',-.365,.025,.365,.607,.008)
    for x in (-.365,.365):
        for y in (.032,.60):screw('Wedge grille fixing',x,y,.016)
    label('CSC AUREX M15-W',0,.037,.022,.025)
    for o in set(COL.objects)-before:
        if o.type=='MESH':
            # Current helper mesh uses MC coordinates. Reorient every authored front-face detail.
            for vert in o.data.vertices:
                b=o.matrix_world@vert.co;p=Vector((b.x,b.z,-b.y));vert.co=V(origin+u*p.x+v*p.y+normal*p.z)
            o.matrix_world.identity();o.data.update()
    for x in (-.27,.27):
        for z in (-.16,-d+.015):box('Non-slip floor runner',(x-.035,0,z-.015),(x+.035,.026,z+.015),'Rubber',.004)
    rear(w,.29,d)
    for sign in (-1,1):
        x=sign*w/2
        cutter=box('Temporary recess cutter',(x-.07,.10,-.49),(x+.07,.24,-.31),'Recess')
        mod=body.modifiers.new('Machined carry recess','BOOLEAN');mod.operation='DIFFERENCE';mod.object=cutter
        bpy.context.view_layer.objects.active=body;bpy.ops.object.modifier_apply(modifier=mod.name);bpy.data.objects.remove(cutter,do_unlink=True)
        xx=x-sign*.035
        box('Blind recess backing',(min(xx-sign*.008,xx),.10,-.49),(max(xx-sign*.008,xx),.24,-.31),'Recess')
        beam('Recessed wedge grip',(xx,.17,-.47),(xx,.17,-.33),.014,'Rubber')
        for z in (-.47,-.33):beam('Recess handle end fixing',(xx,.13,z),(xx,.21,z),.012,'Rigging')
    empty('CSC_Monitor_Aim',(0,.24,-.26));empty('CSC_Floor_Angle_0',(0,0,-.07));empty('CSC_Floor_Angle_15',(0,0,-d))

def aurex_frame(w,d):
    frame(w-.036,d-.050,True)
    # Four mechanical corner clevises align with the sub's top/bottom rigging strips.
    for x in (-w/2+.018,w/2-.018):
        for z in (-.025,-d+.025):
            for dx in (-.014,.014):box('Paired load clevis',(x+dx-.004,0,z-.022),(x+dx+.004,.066,z+.022),'Steel',.002)
            beam('Retained clevis pin',(x-.022,.025,z),(x+.022,.025,z),.010,'Steel',12)
    for x in (-w*.22,w*.22):
        empty('CSC_Attach_Hoist_L' if x<0 else 'CSC_Attach_Hoist_R',(x,.12,-d*.5))

def clamp():
    # Hinged split collar around a 50 mm tube, connected to a forged lower eye.
    n=32 if HD else 12;verts=[];faces=[]
    for yy in (-.022,.022):
        for r in (.0255,.034):
            verts.extend([(r*math.cos(i*2*math.pi/n),.065+r*math.sin(i*2*math.pi/n),yy) for i in range(n)])
    for ringA,ringB in [(0,n),(2*n,3*n),(0,2*n),(n,3*n)]:
        for i in range(n):faces.append((ringA+i,ringA+(i+1)%n,ringB+(i+1)%n,ringB+i))
    mesh('Split aluminium 50 mm tube collar',verts,faces,'Steel')
    beam('Clamp closing bolt',(.032,.045,-.03),(.032,.045,.03),.006,'Steel',8)
    for x in (-.017,.017):beam('Captive eye shank',(x,.037,0),(x,.010,0),.007,'Rigging')
    beam('Lower attachment pin',(-.023,.01,0),(.023,.01,0),.006,'Steel',8)
    empty('CSC_Attach_Truss_Tube',(0,.065,0));empty('CSC_Attach_Hoist',(0,.010,0))

# Custom UV export keeps the existing shared Minecraft atlas byte-for-byte unchanged.
def finish(ident,builder,dims):
    global COL,HD
    setup();cols=[]
    MATS['Coating'].node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.82
    for HD in (True,False):
        COL=bpy.data.collections.new('HIGH_DETAIL' if HD else 'GAME_READY');bpy.context.scene.collection.children.link(COL);builder();cols.append(COL)
    img=bpy.data.images.load(str(ATLAS));img.pack()
    for col in cols:
        for o in col.all_objects:
            if o.type!='MESH':continue
            if not o.data.uv_layers:o.data.uv_layers.new(name='UVMap')
            key=o.data.materials[0]['mc_key'];uv=UV[key]
            for loop in o.data.loops:o.data.uv_layers.active.data[loop.index].uv=(uv[0],1-uv[1])
            if o.get('grille'):
                mat=o.data.materials[0].copy();o.data.materials[0]=mat
                node=mat.node_tree.nodes.new('ShaderNodeTexImage');node.image=img
                mat.node_tree.links.new(node.outputs['Color'],mat.node_tree.nodes.get('Principled BSDF').inputs['Base Color'])
                # Actual microperforation pattern at a stable texel density in game.
                pts=[v.co for v in o.data.vertices];lo=[min(p[k] for p in pts) for k in range(3)]
                for loop in o.data.loops:
                    p=o.data.vertices[loop.vertex_index].co
                    uu=(802+(p.x-lo[0])*200)/1024;vv=(702+(p.z-lo[2])*200)/1024
                    # Existing atlas tile is 224px; wrap within it for larger surfaces.
                    o.data.uv_layers.active.data[loop.index].uv=(.7832+(uu-.7832)%.2148,1-(.6855+(vv-.6855)%.2148))
            # Separate editable per-object unwrap, for future baking without collapsed swatch UVs.
            o.data.uv_layers.new(name='BakeUV');bpy.context.view_layer.objects.active=o;o.select_set(True)
            bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.uv.smart_project(island_margin=.015);bpy.ops.object.mode_set(mode='OBJECT');o.select_set(False);o.data.uv_layers.active_index=0
    # One non-overlapping bake atlas for the complete GAME_READY equipment.
    bpy.ops.object.select_all(action='DESELECT')
    meshes=[o for o in cols[1].all_objects if o.type=='MESH' and not o.get('csc_cutter')]
    for o in meshes:o.data.uv_layers.active_index=1;o.select_set(True)
    bpy.context.view_layer.objects.active=meshes[0];bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.uv.select_all(action='SELECT');bpy.ops.uv.pack_islands(rotate=True,margin=.003);bpy.ops.object.mode_set(mode='OBJECT')
    uv_edges=[]
    for o in meshes:
        uv=o.data.uv_layers[1].data
        for poly in o.data.polygons:
            points=[list(uv[i].uv) for i in poly.loop_indices];uv_edges.append(points)
        o.data.uv_layers.active_index=0;o.select_set(False)
    (BASE/'Documentation'/f'{ident}_uv.json').write_text(json.dumps(uv_edges))
    coat=MATS['Coating'];nodes=coat.node_tree.nodes;links=coat.node_tree.links;uvnode=nodes.new('ShaderNodeUVMap');uvnode.uv_map='BakeUV'
    for file,slot in [('aurex_coating_roughness.png','Roughness'),('aurex_coating_normal.png','Normal')]:
        tex=nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(BASE/'Textures'/file));tex.image.colorspace_settings.name='Non-Color';tex.image.pack();links.new(uvnode.outputs['UV'],tex.inputs['Vector'])
        if slot=='Normal':
            normal=nodes.new('ShaderNodeNormalMap');normal.inputs['Strength'].default_value=.18;links.new(tex.outputs['Color'],normal.inputs['Color'])
            bumps=[n for n in nodes if n.type=='BUMP']
            links.new(normal.outputs['Normal'],bumps[0].inputs['Normal'] if bumps else nodes.get('Principled BSDF').inputs['Normal'])
        else:links.new(tex.outputs['Color'],nodes.get('Principled BSDF').inputs[slot])
    result=export_mesh(cols[1].all_objects,ident,dims)
    item=A/'models/item'/f'{ident}.json';model=json.loads(item.read_text());model['textures']={'atlas':'concertstage:block/aurex_atlas','particle':'concertstage:block/aurex_atlas'};item.write_text(json.dumps(model))
    low=[];dep=bpy.context.evaluated_depsgraph_get()
    for o in cols[1].all_objects:
        if o.type!='MESH' or not any(k in o.name for k in ('panel','wedge','grille','square section','Cross member','Split aluminium')):continue
        eo=o.evaluated_get(dep);me=eo.to_mesh();me.calc_loop_triangles()
        for tri in me.loop_triangles:
            for li in tri.loops:
                p=eo.matrix_world@me.vertices[me.loops[li].vertex_index].co;n=eo.matrix_world.to_3x3()@me.polygons[tri.polygon_index].normal
                if o.get('grille'):u,v=.83,.74
                else:u,v=UV[o.data.materials[0]['mc_key']]
                low.extend(round(float(x),6) for x in (p.x,p.z,-p.y,u,v,n.x,n.z,-n.y))
        eo.to_mesh_clear()
    result['box']=low or result['far']
    (A/'speakers'/f'{ident}.json').write_text(json.dumps(result,separators=(',',':')))
    # Add actual mesh bounds as technical metadata (registry dimensions are nominal envelope).
    cols[1].hide_render=True;cols[1].hide_viewport=True
    sc=bpy.context.scene;sc['CSC_Model']=ident;sc['CSC_Units']='1 m = 1 block; Z up in Blender';sc['CSC_Origin']='centre front bottom edge';sc['CSC_Authored']='Blender native, original AUREX geometry';sc['CSC_Dimensions']=list(dims)
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(ident+'.blend')))
    cols[0].hide_render=True;cols[0].hide_viewport=True;cols[1].hide_render=False;cols[1].hide_viewport=False
    bpy.ops.object.select_all(action='DESELECT')
    for o in cols[1].all_objects:o.hide_set(False);o.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(OUT/(ident+'.glb')),export_format='GLB',use_selection=True,export_apply=True)
    print('AUREX BUILT',ident,'game triangles',len(result['near'])//24,flush=True)

if __name__=='__main__':
    for ident,builder,dims in [
      ('csc_aurex_m12',main,SPECS['csc_aurex_m12']),('csc_aurex_s218_f',sub,SPECS['csc_aurex_s218_f']),('csc_aurex_m15_w',wedge,SPECS['csc_aurex_m15_w']),
      ('csc_aurex_flying_frame',lambda:aurex_frame(1.8,1.1),(1.8,.12,1.1)),
      ('csc_aurex_main_flying_frame',lambda:frame(1.564,.85,True),(1.6,.12,.9)),
      ('csc_aurex_truss_coupler',clamp,(.08,.1,.06))]:
        if os.getenv('CSC_AUREX_FILTER') and ident!=os.environ['CSC_AUREX_FILTER']:continue
        finish(ident,builder,dims)
