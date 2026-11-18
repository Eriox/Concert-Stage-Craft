#!/usr/bin/env python3
"""Bâche de scène (stage_tarp_rail / stage_tarp_top / stage_tarp_cloth) : textures, modèles vanilla (Forge + Fabric),
blockstates multipart, modèle d'objet, recette, loot, langues. Reprend le dessin de concert-stage-bache-3d
(toile PVC tendue, ourlets soudés 15 cm, œillets laiton tous les 50 cm, tendeurs à boule, renforts d'angle).

Repère local d'un morceau : toile le long de x, plan z = 8 (« c », position 0) ou z = 15,8 (« s », sur le bord +z de la case).
Le blockstate tourne ce repère selon l'axe et la position (voir ROT) ; les ourlets de bout locaux -x / +x sont reliés aux
propriétés end_neg / end_pos du monde selon la rotation.
TOP : 0 = accrochée sous un truss (tendeurs vers la membrure basse) ; n = 1..16 = haut de la toile à 2n px (rebord de scène).
BOTTOM 1 = ourlet libre, 2 = ourlet attaché ; REACH r : l'ourlet descend de 2r px dans la case du dessous (plancher bas)."""
import json, math, itertools
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]; RES=ROOT/'common/src/main/resources'; AS=RES/'assets/concertstage'; DATA=RES/'data/concertstage'
COLORS={'black':(30,31,34),'white':(226,226,222),'grey':(112,115,120),'blue':(28,52,104),'red':(150,28,26)}
PL={'c':8.0,'s':15.8}
HEM=2.4
def dump(p,d,indent=None):
    p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(d,ensure_ascii=False,indent=indent,separators=None if indent else (',',':')))

# ---------------- textures (32 px = 1 bloc) ----------------
def textures():
    rng=np.random.default_rng(7)
    for name,c in COLORS.items():
        base=np.array(c,float)
        t=base+rng.normal(0,2.2,(32,32,1))
        t[::2]+=2.5; t[:,::2]+=1.5
        Image.fromarray(np.clip(t,0,255).astype(np.uint8)).convert('RGBA').save(AS/f'textures/block/stage_tarp_{name}.png')
        h=base*0.84+rng.normal(0,1.6,(32,32,1))
        for r in (3,4,27,28): h[r]*=0.72          # cordons de soudure
        for r in range(0,32,4): h[:,r]*=0.95
        Image.fromarray(np.clip(h,0,255).astype(np.uint8)).convert('RGBA').save(AS/f'textures/block/stage_tarp_hem_{name}.png')
    im=Image.new('RGBA',(32,32),(20,20,22,255)); d=ImageDraw.Draw(im)
    d.ellipse((0,0,15,15),fill=(190,186,170,255)); d.ellipse((2,2,13,13),fill=(150,146,132,255)); d.ellipse((4,4,11,11),fill=(24,24,26,255))   # œillet laiton
    for y in range(0,16):
        for x in range(16,24): im.putpixel((x,y),(28+((x+y)%3)*9,)*3+(255,))      # sandow tressé
    d.rectangle((24,0,31,7),fill=(14,14,16,255)); d.rectangle((25,1,27,3),fill=(70,70,74,255))   # boule
    im.save(AS/'textures/block/stage_tarp_hardware.png')

# ---------------- géométrie ----------------
def faces_for(a,b,tex):
    """Faces d'une boîte fine ; uv des grandes faces calquées sur x / y modulo 16 (pavage sans débordement)."""
    x0,y0,z0=a; x1,y1,z1=b
    def ux(v): return v-16*math.floor(min(x0,x1)/16)
    def vy(v): return v-16*math.floor(min(y0,y1)/16)
    u0,u1=ux(x0),ux(x1); v0,v1=vy(y0),vy(y1)
    big=[round(u0,4),round(16-v1,4),round(u1,4),round(16-v0,4)]
    big_m=[round(16-u1,4),round(16-v1,4),round(16-u0,4),round(16-v0,4)]
    thin_v=[0,round(16-v1,4),1,round(16-v0,4)]; thin_u=[round(u0,4),0,round(u1,4),1]
    return {'south':{'uv':big,'texture':tex},'north':{'uv':big_m,'texture':tex},'east':{'uv':thin_v,'texture':tex},
            'west':{'uv':thin_v,'texture':tex},'up':{'uv':thin_u,'texture':tex},'down':{'uv':thin_u,'texture':tex}}
def box(out,a,b,tex,uv=None):
    """Ajoute une boîte, découpée aux multiples de 16 en x et en y pour garder des uv dans la case."""
    xs=sorted({a[0],b[0],*[16*k for k in range(-1,3) if min(a[0],b[0])<16*k<max(a[0],b[0])]})
    ys=sorted({a[1],b[1],*[16*k for k in range(-1,3) if min(a[1],b[1])<16*k<max(a[1],b[1])]})
    for i in range(len(xs)-1):
        for j in range(len(ys)-1):
            f=[xs[i],ys[j],min(a[2],b[2])]; t=[xs[i+1],ys[j+1],max(a[2],b[2])]
            if t[0]-f[0]<1e-6 or t[1]-f[1]<1e-6: continue
            fc=faces_for(f,t,tex)
            if uv: fc={k:{'uv':uv,'texture':tex} for k in fc}
            out.append({'from':[round(v,4) for v in f],'to':[round(v,4) for v in t],'faces':fc})
G_UV=[0,0,8,8]; CORD_UV=[8,0,12,8]; BALL_UV=[12,0,16,4]
def grommet(out,x,y,P):
    box(out,(x-0.7,y-0.7,P-0.3),(x+0.7,y+0.7,P+0.3),'#hw',G_UV)
def sheet(out,y0,y1,P):
    box(out,(0,y0,P-0.1),(16,y1,P+0.1),'#t')
def hemband(out,x0,y0,x1,y1,P):
    box(out,(x0,y0,P-0.2),(x1,y1,P+0.2),'#h')
def model(elements,textures):
    return {'ambientocclusion':False,'textures':{**textures,'particle':textures.get('#t',textures.get('t','')).lstrip('#') if False else textures.get('t',textures.get('hw'))},'elements':elements}
def tex(color): return {'t':f'concertstage:block/stage_tarp_{color}','h':f'concertstage:block/stage_tarp_hem_{color}','hw':'concertstage:block/stage_tarp_hardware'}
HW={'hw':'concertstage:block/stage_tarp_hardware','t':'concertstage:block/stage_tarp_hardware'}

def top_height(n): return 16.0 if n==0 else 2.0*n
def part_top(n,p):
    P=PL[p]; T=top_height(n); e=[]
    sheet(e,0,T,P); hemband(e,0,max(0,T-HEM),16,T,P); return e
def hw_top(n,p):
    P=PL[p]; T=top_height(n); e=[]
    gy=T-1.2 if T>=2.4 else T/2
    for x in (4,12):
        grommet(e,x,gy,P)
        if n==0:      # tendeur à boule autour de la membrure basse du truss au-dessus (axe y 17,5 ; z 14,5 si toile en rive)
            zc=14.5 if p=='s' else P
            box(e,(x-0.15,gy+0.6,P+0.15),(x+0.15,19.0,P+0.45),'#hw',CORD_UV)
            if p=='s':
                box(e,(x-0.15,18.75,12.9),(x+0.15,19.05,P+0.45),'#hw',CORD_UV)
                box(e,(x-0.15,16.2,12.9),(x+0.15,19.05,13.2),'#hw',CORD_UV)
            box(e,(x-0.8,gy+0.4,P+0.45),(x+0.8,gy+2.0,P+2.05),'#hw',BALL_UV)
        else:         # attaché au rebord de la scène (côté +z de la case quand la toile est en rive)
            box(e,(x-0.15,gy-0.15,P),(x+0.15,gy+0.15,min(17.5,P+1.7)),'#hw',CORD_UV)
            box(e,(x-0.8,gy-0.8,P-1.9),(x+0.8,gy+0.8,P-0.3),'#hw',BALL_UV)
    return e
def part_bottom(r,p):
    P=PL[p]; e=[]; yb=-2.0*r
    if r: sheet(e,yb,0,P)
    hemband(e,0,yb,16,yb+HEM,P); return e
def hw_bottom(b,r,p):
    P=PL[p]; e=[]; yb=-2.0*r; gy=yb+1.2
    for x in (4,12):
        grommet(e,x,gy,P)
        if b==2:      # sandow vers le support (plancher, lisse, sol)
            box(e,(x-0.15,max(-16,yb-1.4),P-0.15),(x+0.15,gy-0.6,P+0.15),'#hw',CORD_UV)
            box(e,(x-0.7,max(-16,yb-1.4),P+0.15),(x+0.7,max(-15.2,yb-0.2),P+1.35),'#hw',BALL_UV)
    return e
def end_x(side): return (0,HEM) if side=='neg' else (16-HEM,16)
def part_end(y0,y1,side,p,corner_top=False,corner_bot=False):
    P=PL[p]; e=[]; xa,xb=end_x(side)
    hemband(e,xa,y0,xb,y1,P)
    cx=(0,3.2) if side=='neg' else (12.8,16)
    if corner_top: box(e,(cx[0],y1-3.2,P-0.28),(cx[1],y1,P+0.28),'#h')
    if corner_bot: box(e,(cx[0],y0,P-0.28),(cx[1],y0+3.2,P+0.28),'#h')
    return e
def hw_end(side,p):
    P=PL[p]; e=[]; grommet(e,1.2 if side=='neg' else 14.8,8,P); return e

# ---------------- blockstates ----------------
# (axe, position) -> (rotation y, côté monde du bout local +x)
ROT={('x',0):(0,'pos'),('x',1):(0,'pos'),('x',2):(180,'neg'),('z',0):(90,'pos'),('z',1):(270,'neg'),('z',2):(90,'pos')}
def P_of(shift): return 'c' if shift==0 else 's'
def ap(m,y):
    d={'model':m}
    if y: d['y']=y
    return d
def local_side(world,shift_axis):
    rot,lp=ROT[shift_axis]
    return 'pos' if world==lp else 'neg'

def build():
    """Modèles composés (un par aspect local) + blockstates « variants » : un seul modèle cuit par état, pas de multipart
    (un multipart garde tous ses sélecteurs dans le modèle cuit de CHAQUE état : 13 millions de paires pour la bâche en 1.8.2)."""
    import shutil
    textures(); n_models=0
    out=AS/'models/block/stage_tarp'
    if out.exists(): shutil.rmtree(out)
    MB='concertstage:block/stage_tarp/'
    def save(name,elements,textures):
        nonlocal n_models
        t=dict(textures); t['particle']=t['t']
        dump(out/(name+'.json'),{'ambientocclusion':False,'textures':t,'elements':elements}); n_models+=1
    E=lambda b: 'e'+('1' if b[0] else '0')+('1' if b[1] else '0')
    for c in COLORS:
        T=tex(c)
        for p in PL:
            for b in (0,1,2):
                for r in (range(9) if b else [0]):
                    for ends in itertools.product((False,True),repeat=2):
                        e=[]; sheet(e,0,16,PL[p])
                        if b: e+=part_bottom(r,p)+hw_bottom(b,r,p)
                        for side,on in zip(('neg','pos'),ends):
                            if not on: continue
                            e+=part_end(0,16,side,p)+hw_end(side,p)
                            if b: e+=part_end(-2.0*r,0,side,p,corner_bot=True) if r else part_end(0,3.2,side,p,corner_bot=True)
                        save(f'{c}/cloth_{p}_b{b}_r{r}_{E(ends)}',e,T)
            for n in range(17):
                for ends in itertools.product((False,True),repeat=2):
                    e=part_top(n,p)+hw_top(n,p)
                    for side,on in zip(('neg','pos'),ends):
                        if on: e+=part_end(0,top_height(n),side,p,corner_top=True)
                    save(f'{c}/top_{p}_{n}_{E(ends)}',e,T)
    def local_ends(ax,sh,en,ep):
        rot,lp=ROT[(ax,sh)]
        return rot,((en,ep) if lp=='pos' else (ep,en))
    v={}
    for ax in ('x','z'):
        for sh in (0,1,2):
            for c in COLORS:
                for b in (0,1,2):
                    for r in range(9):
                        for en,ep in itertools.product((False,True),repeat=2):
                            rot,le=local_ends(ax,sh,en,ep)
                            v[f'axis={ax},bottom={b},color={c},end_neg={str(en).lower()},end_pos={str(ep).lower()},reach={r},shift={sh}']=ap(MB+f'{c}/cloth_{P_of(sh)}_b{b}_r{r if b else 0}_{E(le)}',rot)
    dump(AS/'blockstates/stage_tarp_cloth.json',{'variants':v})
    def top_variants(key,vals):
        v={}
        for kv,ax in vals:
            for sh in (0,1,2):
                for c in COLORS:
                    for n in range(17):
                        for en,ep in itertools.product((False,True),repeat=2):
                            rot,le=local_ends(ax,sh,en,ep)
                            v[f'color={c},end_neg={str(en).lower()},end_pos={str(ep).lower()},{key}={kv},shift={sh},top={n}']=ap(MB+f'{c}/top_{P_of(sh)}_{n}_{E(le)}',rot)
        return v
    dump(AS/'blockstates/stage_tarp_top.json',{'variants':top_variants('axis',[('x','x'),('z','z')])})
    dump(AS/'blockstates/stage_tarp_rail.json',{'variants':top_variants('facing',[('east','x'),('west','x'),('north','z'),('south','z')])})
    mp=v
    # --- objet : un module d'angle haut-gauche noir, accroché ---
    e=[]; P=8.0
    sheet(e,0,16,P); hemband(e,0,16-HEM,16,16,P); hemband(e,0,0,HEM,16,P); box(e,(0,12.8,P-0.28),(3.2,16,P+0.28),'#h')
    for x in (4,12): grommet(e,x,14.8,P); box(e,(x-0.15,15.4,P+0.15),(x+0.15,19,P+0.45),'#hw',CORD_UV); box(e,(x-0.8,15.2,P+0.45),(x+0.8,16.8,P+2.05),'#hw',BALL_UV)
    grommet(e,1.2,8,P)
    T=tex('black'); T['particle']=T['t']
    dump(AS/'models/item/stage_tarp_rail.json',{'parent':'minecraft:block/block','ambientocclusion':False,'textures':T,'elements':e,
         'display':{'gui':{'rotation':[20,200,0],'translation':[0,-1,0],'scale':[0.6,0.6,0.6]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.4,0.4,0.4]},
                    'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[0.6,0.6,0.6]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[0.375,0.375,0.375]},
                    'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[0.4,0.4,0.4]},'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,0,0],'scale':[0.4,0.4,0.4]}}})
    # --- données ---
    dump(DATA/'loot_tables/blocks/stage_tarp_rail.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'concertstage:stage_tarp_rail'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]},2)
    dump(DATA/'recipes/stage_tarp_rail.json',{'type':'minecraft:crafting_shaped','category':'building','pattern':['SNS','LLL','LLL'],
         'key':{'S':{'item':'minecraft:string'},'N':{'item':'minecraft:iron_nugget'},'L':{'item':'minecraft:leather'}},'result':{'item':'concertstage:stage_tarp_rail','count':2}},2)
    p=RES/'data/minecraft/tags/blocks/mineable/axe.json'
    lang={'fr_fr':{'block.concertstage.stage_tarp_rail':'Bâche de scène (PVC, œillets, tendeurs)','block.concertstage.stage_tarp_top':'Bâche de scène','block.concertstage.stage_tarp_cloth':'Bâche de scène',
                   'gui.concertstage.title_stage_tarp':'Bâche de scène','gui.concertstage.tarp_slant_on':'Tendue en biais vers le rebord de la scène : oui','gui.concertstage.tarp_slant_off':'Tendue en biais vers le rebord de la scène : non (droite)','message.concertstage.tarp_size':'Bâche : %s de long, %s de haut au plus',
                   'tooltip.concertstage.use.stage_tarp_rail':'Sous un truss : accrochée à la membrure. Contre une scène : attachée au rebord du plancher. Chaque colonne descend jusqu’au support et s’y attache. Outil bâche : taille ; colorant : couleur.'},
          'en_us':{'block.concertstage.stage_tarp_rail':'Stage Tarp (PVC, eyelets, bungees)','block.concertstage.stage_tarp_top':'Stage Tarp','block.concertstage.stage_tarp_cloth':'Stage Tarp',
                   'gui.concertstage.title_stage_tarp':'Stage Tarp','gui.concertstage.tarp_slant_on':'Stretched at a slant to the stage rim: yes','gui.concertstage.tarp_slant_off':'Stretched at a slant to the stage rim: no (straight)','message.concertstage.tarp_size':'Tarp: %s long, at most %s high',
                   'tooltip.concertstage.use.stage_tarp_rail':'Under a truss: hung from the chord. Against a stage: tied to the deck rim. Each column hangs down to its support and is tied to it. Tarp Tool: size; dye: colour.'}}
    for lg,kv in lang.items():
        lp=AS/'lang'/(lg+'.json'); d=json.loads(lp.read_text()); d.update(kv); dump(lp,d,2)
    print('modèles',n_models,'; états cloth',len(mp))

# ---------------- bâche de régie (compacte, noire, sur la ligne des tubes) ----------------
def build_regie():
    """Toile fine (0,06 px), ourlets 1,6 px, petits œillets ; tendeurs vers l'élément au-dessus (TOP = 2 px par cran :
    lisse / nœud à 8 px, poutre treillis à 4 px, truss à 1,5 px) et vers l'élément dessous (REACH)."""
    out=AS/'models/block/regie_tarp'
    import shutil
    if out.exists(): shutil.rmtree(out)
    H=1.6; T=tex('black'); n=0
    PR={'c':8.0,'s':15.85}                  # au milieu de la case (ligne des tubes) ou contre l'élément voisin
    def sh(e,y0,y1,P): box(e,(0,y0,P-0.03),(16,y1,P+0.03),'#t')
    def hb(e,x0,y0,x1,y1,P): box(e,(x0,y0,P-0.12),(x1,y1,P+0.12),'#h')
    def gr(e,x,y,P): box(e,(x-0.45,y-0.45,P-0.18),(x+0.45,y+0.45,P+0.18),'#hw',G_UV)
    def save(name,e):
        nonlocal n
        t=dict(T); t['particle']=t['t']; dump(out/(name+'.json'),{'ambientocclusion':False,'textures':t,'elements':e}); n+=1
    E=lambda b:'e'+('1' if b[0] else '0')+('1' if b[1] else '0')
    for p,P in PR.items():
        # TOP 0..8 : accrochée sous l'élément au-dessus (tendeurs montant de 2·TOP px) ; TOP 9..16 : attachée de côté à l'élément voisin,
        # haut de la toile à 2·(TOP-8) px, tendeurs horizontaux jusqu'à la ligne de tubes du voisin (8 px dans sa case)
        for t in range(17):
            side=t>8; T_=2.0*(t-8) if side else 16.0
            for ends in itertools.product((False,True),repeat=2):
                e=[]; sh(e,0,T_,P); hb(e,0,max(0,T_-H),16,T_,P)
                for x in (4,12):
                    gr(e,x,T_-H/2,P)
                    if side: box(e,(x-0.1,T_-H/2-0.1,P),(x+0.1,T_-H/2+0.1,24.0),'#hw',CORD_UV); box(e,(x-0.35,T_-H/2-0.6,23.2),(x+0.35,T_-H/2+0.6,24.4),'#hw',CORD_UV)
                    elif t: box(e,(x-0.1,16-H/2,P-0.1),(x+0.1,16+2*t+0.6,P+0.1),'#hw',CORD_UV); box(e,(x-0.35,16+2*t-0.2,P-0.9),(x+0.35,16+2*t+0.7,P+0.9),'#hw',CORD_UV)
                for sd,on in zip(('neg','pos'),ends):
                    if on: xa,xb=(0,H) if sd=='neg' else (16-H,16); hb(e,xa,0,xb,T_,P); gr(e,(xa+xb)/2,min(8,T_/2),P)
                save(f'top_{p}_{t}_{E(ends)}',e)
        for b in (0,1,2):
            for r in (range(9) if b else [0]):
                for ends in itertools.product((False,True),repeat=2):
                    e=[]; sh(e,0,16,P); yb=-2.0*r
                    if b:
                        if r: sh(e,yb,0,P)
                        hb(e,0,yb,16,yb+H,P)
                        for x in (4,12):
                            gr(e,x,yb+H/2,P)
                            if b==2: box(e,(x-0.1,max(-16,yb-1.2),P-0.1),(x+0.1,yb+H/2,P+0.1),'#hw',CORD_UV)
                    for sd,on in zip(('neg','pos'),ends):
                        if on: xa,xb=(0,H) if sd=='neg' else (16-H,16); hb(e,xa,(yb if b else 0),xb,16,P); gr(e,(xa+xb)/2,8,P)
                    save(f'cloth_{p}_b{b}_r{r}_{E(ends)}',e)
    MB='concertstage:block/regie_tarp/'
    def le(ax,sh_,en,ep):
        rot,lp=ROT[(ax,sh_)]
        return rot,((en,ep) if lp=='pos' else (ep,en))
    v={}
    for ax in ('x','z'):
        for sh_ in (0,1,2):
            for c in COLORS:
                for b in (0,1,2):
                    for r in range(9):
                        for en,ep in itertools.product((False,True),repeat=2):
                            rot,ends=le(ax,sh_,en,ep)
                            v[f'axis={ax},bottom={b},color={c},end_neg={str(en).lower()},end_pos={str(ep).lower()},reach={r},shift={sh_}']=ap(MB+f'cloth_{P_of(sh_)}_b{b}_r{r if b else 0}_{E(ends)}',rot)
    dump(AS/'blockstates/regie_tarp_cloth.json',{'variants':v})
    def topv(key,vals):
        v={}
        for kv,ax in vals:
            for sh_ in (0,1,2):
                for c in COLORS:
                    for t in range(17):
                        for en,ep in itertools.product((False,True),repeat=2):
                            rot,ends=le(ax,sh_,en,ep)
                            v[f'color={c},end_neg={str(en).lower()},end_pos={str(ep).lower()},{key}={kv},shift={sh_},top={t}']=ap(MB+f'top_{P_of(sh_)}_{t}_{E(ends)}',rot)
        return v
    dump(AS/'blockstates/regie_tarp_top.json',{'variants':topv('axis',[('x','x'),('z','z')])})
    dump(AS/'blockstates/regie_tarp_rail.json',{'variants':topv('facing',[('east','x'),('west','x'),('north','z'),('south','z')])})
    P=8.0
    item=json.loads((AS/'models/item/stage_tarp_rail.json').read_text())
    e=[]; sh(e,0,16,P); hb(e,0,16-H,16,16,P); hb(e,0,0,H,16,P)
    for x in (4,12): gr(e,x,16-H/2,P); box(e,(x-0.1,16-H/2,P-0.1),(x+0.1,24.6,P+0.1),'#hw',CORD_UV)
    item['elements']=e; dump(AS/'models/item/regie_tarp_rail.json',item)
    dump(DATA/'loot_tables/blocks/regie_tarp_rail.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'concertstage:regie_tarp_rail'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]},2)
    dump(DATA/'recipes/regie_tarp_rail.json',{'type':'minecraft:crafting_shapeless','category':'building','ingredients':[{'item':'concertstage:stage_tarp_rail'},{'item':'minecraft:black_dye'},{'item':'minecraft:string'}],'result':{'item':'concertstage:regie_tarp_rail','count':1}},2)
    for lg,kv in {'fr_fr':{'block.concertstage.regie_tarp_rail':'Bâche de régie (compacte, noire)','block.concertstage.regie_tarp_top':'Bâche de régie','block.concertstage.regie_tarp_cloth':'Bâche de régie','gui.concertstage.title_regie_tarp':'Bâche de régie'},
                  'en_us':{'block.concertstage.regie_tarp_rail':'FOH Tarp (compact, black)','block.concertstage.regie_tarp_top':'FOH Tarp','block.concertstage.regie_tarp_cloth':'FOH Tarp','gui.concertstage.title_regie_tarp':'FOH Tarp'}}.items():
        lp=AS/'lang'/(lg+'.json'); d=json.loads(lp.read_text()); d.update(kv); dump(lp,d,2)
    print('régie : modèles',n)

if __name__=='__main__': build(); build_regie()
