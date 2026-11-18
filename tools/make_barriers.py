#!/usr/bin/env python3
"""Barrières de scène et de festival (modèles vanilla, Forge + Fabric), inspirées des barrières de front de scène (sections de 1 m,
~1,2 m de haut, plaque de pied côté public, marche côté fosse pour la sécurité, main courante ronde lisse) et des barrières de ville
« Vauban » (2 m × 1,10 m, acier galvanisé, barreaux verticaux, pieds plats, crochets d'assemblage). Noms fictifs CSC.
Repère : 16 u = 1 m ; côté public vers -z (face au joueur qui pose), fosse vers +z."""
import json, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]; RES=ROOT/'common/src/main/resources'; AS=RES/'assets/concertstage'; DATA=RES/'data/concertstage'
R={'alu':(0,0),'perf':(4,0),'checker':(8,0),'tube':(12,0),'rubber':(0,4),'blue':(4,4),'stripe':(8,4),'steel':(12,4),
   'slats':(0,8),'hexperf':(4,8),'screen':(8,8),'galva':(12,8),'foot':(0,12),'eyelet':(4,12)}
def texture():
    rng=np.random.default_rng(9); im=np.zeros((64,64,4),np.uint8); im[...,3]=255
    cols={'alu':(196,200,204),'perf':(170,174,178),'checker':(150,152,150),'tube':(184,188,190),'rubber':(26,26,28),'blue':(32,92,176),'stripe':(236,190,40),'steel':(110,114,118),
          'slats':(200,203,206),'hexperf':(186,190,194),'screen':(22,23,24),'galva':(160,166,168),'foot':(44,46,48),'eyelet':(176,180,184)}
    for k,(x,y) in R.items():
        a=np.array(cols[k],float)+rng.normal(0,3,(16,16,1))
        if k in('alu','tube'): a+=np.linspace(-10,10,16)[None,:,None]
        im[y*4:y*4+16,x*4:x*4+16,:3]=np.clip(a,0,255)
    img=Image.fromarray(im); d=ImageDraw.Draw(img)
    for yy in range(1,16,3):
        for xx in range(1,16,3): d.point((16+xx,yy),fill=(40,42,46,255))           # tôle perforée
    for yy in range(0,16,4):
        for xx in range(0,16,4): d.line((32+xx,yy+1,32+xx+2,yy+3),fill=(190,192,190,255))   # larmé
    for i in range(-16,16,4): d.line((32+i,16,32+i+15,31),fill=(20,20,20,255),width=2)       # bandes jaune / noir
    for yy in range(32,48,4): d.line((0,yy,15,yy),fill=(140,144,148,255)); d.line((0,yy+1,15,yy+1),fill=(222,224,226,255))      # lames de plancher alu
    for yy in range(0,16,3):
        for xx in range(0 if yy%2 else 1,16,3): d.rectangle((16+xx,32+yy,16+xx+1,32+yy+1),fill=(58,60,64,255))  # tôle à trous ronds en quinconce
    for yy in range(0,16,2):
        for xx in range(yy%4//2,16,2): d.point((32+xx,32+yy),fill=(46,48,50,255))         # toile tricotée PEHD
    for yy in range(0,16,5): d.line((48,32+yy,63,32+yy+2),fill=(184,190,192,255))           # galvanisé (fleurs de zinc)
    img.save(AS/'textures/block/csc_barrier.png')
def uv(k): x,y=R[k]; return [x,y,x+4,y+4]
def el(a,b,k,rot=None):
    e={'from':[round(v,4) for v in a],'to':[round(v,4) for v in b],'faces':{f:{'uv':uv(k),'texture':'#0'} for f in ('north','south','east','west','up','down')}}
    if rot: e['rotation']={'angle':rot[0],'axis':rot[1],'origin':list(rot[2])}
    return e
def tube_x(out,x0,x1,y,z,r,k='tube'):
    out.append(el((x0,y-r,z-r),(x1,y+r,z+r),k)); out.append(el((x0,y-r*0.92,z-r*0.92),(x1,y+r*0.92,z+r*0.92),k,(45,'x',(8,y,z))))
def crowdguard(kind):
    """Front de scène 1 m : plaque de pied larmée côté public (z 0..20), tablier perforé incliné + main courante à 19,2 px,
    jambes de force et marche de fosse (z 21..30). kind : straight / gate / end."""
    e=[]
    e.append(el((0,0,0),(16,0.6,20.5),'checker'))                     # plaque de pied
    e.append(el((0.2,0.6,0.2),(15.8,0.9,1.0),'stripe'))               # nez de plaque (bande de visibilité)
    for x in (0.4,15.6):                                              # cadres latéraux (les sections s'emboîtent)
        e.append(el((x-0.4,0,19.5),(x+0.4,19.2,21.0),'alu'))
        e.append(el((x-0.35,0.6,21.0),(x+0.35,14.5,22.0),'alu',(-22.5,'x',(x,0.6,21.0))))   # jambe de force vers la fosse
        e.append(el((x-0.4,0,21),(x+0.4,0.8,30),'steel'))              # longeron bas côté fosse
    if kind!='gate':
        e.append(el((0.8,1.2,19.9),(15.2,18.0,20.6),'perf'))          # tablier perforé (lisse côté public)
    else:
        e.append(el((0.8,1.2,19.9),(2.2,18.0,20.6),'perf')); e.append(el((13.8,1.2,19.9),(15.2,18.0,20.6),'perf'))
        e.append(el((2.6,1.4,20.0),(9.4,17.6,20.5),'perf',(-22.5,'y',(2.6,8,20.2))))          # vantail entrouvert
        e.append(el((8.6,9.0,19.2),(9.2,10.6,20.0),'blue'))           # poignée
    tube_x(e,0,16,19.2,20.25,1.3)                                     # main courante ronde
    e.append(el((0,18.2,19.0),(16,18.6,21.5),'rubber'))              # joue de main courante
    e.append(el((0.6,6.4,21.5),(15.4,7.0,26.5),'checker'))            # marche de fosse
    e.append(el((0.6,6.0,26.3),(15.4,6.4,26.7),'stripe'))
    for x in (2,14): e.append(el((x-0.4,0.8,25.5),(x+0.4,6.4,26.3),'steel'))
    e.append(el((6.5,15.4,20.6),(9.5,17.2,20.8),'blue'))              # plaque d'identification CSC
    if kind=='end':
        e.append(el((0,0.6,1.0),(0.8,8.0,19.5),'perf'))   # joue de fin de ligne côté public
        e.append(el((0,7.6,1.0),(1.2,8.4,19.5),'alu'))
    return e
def vauban():
    """Barrière de ville 2 m × 1,10 m (17,6 px) : cadre, 14 barreaux, pieds plats, crochets."""
    e=[]
    for y in (2.2,17.0): tube_x(e,0.8,31.2,y,8,0.75)
    for x in (0.8,31.2): e.append(el((x-0.75,1.0,7.25),(x+0.75,17.6,8.75),'tube'))
    for i in range(14):
        x=2.6+i*26.8/13; e.append(el((x-0.35,2.9,7.65),(x+0.35,16.3,8.35),'tube'))
    for x in (4,28):                                                     # pieds plats
        e.append(el((x-0.6,0,2),(x+0.6,0.6,14),'steel')); e.append(el((x-0.5,0.6,7.4),(x+0.5,2.2,8.6),'steel'))
    e.append(el((-0.8,15.6,7.6),(0.6,16.4,8.4),'steel')); e.append(el((31.2,4.0,7.6),(32.0,4.8,8.4),'steel'))   # crochets
    e.append(el((12,11,7.2),(20,14.5,7.3),'blue'))                       # plaque d'identification
    return e
def tube_z(out,z0,z1,x,y,r,k='tube'):
    out.append(el((x-r,y-r,z0),(x+r,y+r,z1),k)); out.append(el((x-r*0.92,y-r*0.92,z0),(x+r*0.92,y+r*0.92,z1),k,(45,'z',(x,y,8))))
def tube_y(out,y0,y1,x,z,r,k='tube'):
    out.append(el((x-r,y0,z-r),(x+r,y1,z+r),k)); out.append(el((x-r*0.92,y0,z-r*0.92),(x+r*0.92,y1,z+r*0.92),k,(45,'y',(x,8,z))))
def swap_xz(els):
    """Même pièce tournée d'un quart de tour pour une aile le long de z (x <-> z, sans rotation : seules des barres droites)."""
    o=[]
    for e in els:
        f,t=e['from'],e['to']; n=dict(e); n['from']=[f[2],f[1],f[0]]; n['to']=[t[2],t[1],t[0]]
        if 'rotation' in e:
            r=dict(e['rotation']); og=r['origin']; r['origin']=[og[2],og[1],og[0]]
            r['axis']={'x':'z','z':'x','y':'y'}[r['axis']]; r['angle']=-r['angle']; n['rotation']=r
        o.append(n)
    return o
def prolyte_panel(x0,x1,zp=19.6,door=None):
    """Panneau aluminium type crash barrière : cadre en profils arrondis, tôle à trous, deux « A » de renfort, main courante ronde à
    19,8 px (1,24 m). Il court le long de x de x0 à x1, face public à z = zp. door=(xa,xb) : vantail ouvert vers la fosse à la place."""
    e=[]; H=19.8; z0=zp; z1=zp+1.4
    for x in (x0,x1): tube_y(e,0,H-0.8,x+(0.7 if x==x0 else -0.7),z0+0.7,0.7,'alu')
    tube_x(e,x0,x1,H-0.8,z0+0.7,0.85,'alu')                                       # main courante arrondie
    e.append(el((x0+0.5,0.6,z0+0.2),(x1-0.5,1.6,z1-0.2),'alu'))                  # traverse basse
    spans=[(x0+1.2,x1-1.2)] if not door else [(x0+1.2,door[0]),(door[1],x1-1.2)]
    for a,b in spans:
        if b-a<1: continue
        e.append(el((a,1.6,z0+0.45),(b,H-1.6,z0+0.95),'hexperf'))                # tôle perforée
        n=max(1,round((b-a)/7.5)); w=(b-a)/n
        for i in range(n):
            cx=a+w*(i+0.5); h=min(3.0,w*0.38)
            if i: e.append(el((a+w*i-0.45,1.6,z0+0.3),(a+w*i+0.45,H-1.6,z0+1.1),'alu'))   # montant intermédiaire
            if w<6: continue
            for sgn in (-1,1):                                                   # jambes du « A »
                e.append(el((cx+sgn*h*0.5-0.45,H-9.2,z0+0.25),(cx+sgn*h*0.5+0.45,H-1.8,z0+1.15),'alu',(sgn*22.5,'z',(cx+sgn*h*0.5,H-5.5,z0+0.7))))
            e.append(el((cx-h*0.55,H-6.6,z0+0.25),(cx+h*0.55,H-5.8,z0+1.15),'alu'))    # barre du « A »
            e.append(el((cx-h*0.9,H-9.6,z0+0.25),(cx+h*0.9,H-8.8,z0+1.15),'alu'))      # pied du triangle
    for x in (x0,x1-0.6):
        for y in (3.0,H-4): e.append(el((x,y,z0+0.4),(x+0.6,y+1.4,z0+1.0),'steel'))   # broches d'assemblage
    return e
def prolyte_foot(x0,x1,z0,z1):
    """Plaque de pied en lames d'aluminium (côté public) avec nez arrondi et longerons latéraux."""
    e=[el((x0,0,z0),(x1,0.7,z1),'slats')]
    tube_x(e,x0,x1,0.55,z0+0.6,0.6,'alu')
    for x in (x0,x1-0.7): e.append(el((x,0,z0+1),(x+0.7,1.0,z1),'alu'))
    return e
def prolyte_back(x0,x1,zp=19.6):
    """Jambes de force arrière (fosse) : de la mi-hauteur du panneau jusqu'au longeron de fosse."""
    e=[]
    for x in (x0+0.8,x1-0.8):
        e.append(el((x-0.4,-2.0,zp+1.4),(x+0.4,7.5,zp+2.2),'alu',(-45,'x',(x,7.5,zp+1.8))))
        e.append(el((x-0.45,0,zp+1.4),(x+0.45,0.9,zp+8.6),'alu'))
    e.append(el((x0+0.4,0,zp+7.6),(x1-0.4,0.9,zp+8.6),'alu'))
    return e
def prolyte(kind):
    e=[]
    if kind=='straight':
        e+=prolyte_foot(0,16,0,19.6)+prolyte_panel(0,16)+prolyte_back(0,16)
    elif kind=='cable':                                                          # BAR-32-003 : passage de câbles + porte intégrée
        e+=prolyte_foot(0,16,0,19.6)+prolyte_panel(0,16,door=(4.6,15.2))+prolyte_back(0,4.6)
        e.append(el((5.0,0.4,20.4),(15.4,18.4,21.0),'hexperf',(-45,'y',(15.4,8,20.6))))   # porte ouverte vers la fosse
        e.append(el((5.0,0.4,20.0),(5.9,18.4,21.4),'alu',(-45,'y',(15.4,8,20.6))))
        e.append(el((6.4,9.2,19.6),(7.2,10.8,20.4),'blue',(-45,'y',(15.4,8,20.6))))
        e.append(el((0.8,0,0.2),(4.2,1.7,31.8),'rubber'))                        # pont de câbles en caoutchouc sous le panneau
        e.append(el((1.3,1.7,0.2),(3.7,1.9,31.8),'stripe'))
        e.append(el((0.6,0.4,19.4),(4.4,2.6,21.2),'rubber'))                     # passe-câbles
    elif kind=='corner90':                                                       # BAR-31-001 ouvert à 90° : public à l'extérieur
        e+=prolyte_foot(0,32,0,19.6)+swap_xz(prolyte_foot(19.6,32,21,32))
        e+=prolyte_panel(0,19.6)+swap_xz(prolyte_panel(21,32,zp=19.6))
        tube_y(e,0,20.2,20.3,20.3,1.1,'alu')                                     # charnière
        e+=prolyte_back(0,19.6)
    elif kind=='corner270':                                                      # BAR-31-001 ouvert à 270° : public à l'intérieur
        e+=prolyte_foot(0,10.4,0,19.6)
        e+=prolyte_panel(0,10.4)+swap_xz([x for x in prolyte_panel(0,19.6,zp=10.4)])
        tube_y(e,0,20.2,11.1,20.3,1.1,'alu')
        for x in (1.0,9.0): e.append(el((x-0.45,0,21.0),(x+0.45,0.9,28.2),'alu'))
        for z in (1.0,10.0): e.append(el((11.8,0,z-0.45),(19.0,0.9,z+0.45),'alu'))
    return e
def festival_stairs(left=True, right=True):
    """Escalier de scène de festival : 10 contremarches de 20 cm (3,2 px), 9 marches de 33 cm (5,33 px) sur 3 blocs, la 10e contremarche
    arrive sur le plancher de la scène (2,0 m = 32 px, une limite de bloc) au bout z = 48. Limons et main courante des deux côtés. Monte vers +z."""
    e=[]; R=3.2; N=9; G=48/N
    for i in range(N):
        z0=round(i*G,4); z1=round((i+1)*G,4); y=round((i+1)*R,4)
        e.append(el((1.0,y-0.8,z0),(15.0,y,z1),'checker'))                      # marche en tôle larmée
        e.append(el((1.0,y-0.9,z0-0.05),(15.0,y,z0+0.7),'stripe'))              # nez de marche
        e.append(el((1.4,round(y-R,4),z0+0.15),(14.6,y-0.8,z0+0.45),'steel'))   # contremarche ajourée
        for x in (0,15.0):
            e.append(el((x,round(max(0,y-R-1.6),4),z0),(x+1.0,round(y+0.4,4),z1),'alu'))   # limon (en escalier)
    e.append(el((1.0,29.2,46.6),(15.0,32.0,48.0),'stripe'))                     # dernière contremarche (bord du plancher)
    for x in [v for v,on in ((0.5,left),(15.5,right)) if on]:          # rampe de chaque côté (retirée contre un escalier voisin)
        for i in (0,3,6,8):
            z=round(i*G+G/2,4); y=round((i+1)*R,4)
            e.append(el((x-0.45,y,z-0.45),(x+0.45,round(y+14.4,4),z+0.45),'alu'))   # poteaux 0,9 m
        for i in range(N):
            z0=round(i*G,4); z1=round((i+1)*G,4); y=round((i+1)*R+14.4,4)
            e.append(el((x-0.5,y-0.5,z0),(x+0.5,round(y+0.5+R,4),z1),'alu'))       # main courante (en escalier, suit la pente)
    e.append(el((0,0,0.2),(16,0.6,1.0),'steel'))                                # pied
    return e
def heras(screen):
    """Clôture mobile 3 blocs × 2 (≈ 3,5 m × 2 m) : cadre tube galvanisé, grillage soudé, plots en caoutchouc, colliers.
    screen : toile tricotée noire 1,76 × 3,41 m côté -z, œillets tous les 50 cm attachés au cadre."""
    e=[]; W=48; zc=8
    for x in (1.2,W-1.2): e.append(el((x-0.6,2.4,zc-0.6),(x+0.6,31.4,zc+0.6),'galva'))
    for y in (3.0,30.8): e.append(el((0.6,y-0.6,zc-0.6),(W-0.6,y+0.6,zc+0.6),'galva'))
    x=4.0
    while x<W-2: e.append(el((x-0.15,3.6,zc-0.15),(x+0.15,30.2,zc+0.15),'galva')); x+=2.9      # fils verticaux
    for y in range(6,30,4): e.append(el((1.8,y-0.15,zc-0.15),(W-1.8,y+0.15,zc+0.15),'galva'))   # fils horizontaux
    for x in (2.0,W-2.0):
        e.append(el((x-2.6,0,zc-5),(x+2.6,2.4,zc+5),'foot'))                    # plot
        e.append(el((x-1.6,2.4,zc-1.2),(x+1.6,2.9,zc+1.2),'foot'))
    for x in (0.2,W-0.8): e.append(el((x,27.2,zc-1.1),(x+0.6,28.6,zc+1.1),'steel'))   # colliers
    if screen:
        z=zc-0.85
        e.append(el((1.9,3.2,z-0.08),(W-1.9,30.6,z),'screen'))
        for k in range(7):
            ex=2.4+k*(W-4.8)/6
            for y in (4.0,29.8): e.append(el((ex-0.35,y-0.35,z-0.2),(ex+0.35,y+0.35,z+0.1),'eyelet'))
        for y in (9.0,16.6,24.2):
            for ex in (2.4,W-2.4): e.append(el((ex-0.35,y-0.35,z-0.2),(ex+0.35,y+0.35,z+0.1),'eyelet'))
    return e
ITEMS={'barrier_crowdguard_1m':(('CSC CrowdGuard 120 — barrière de front de scène 1 m','CSC CrowdGuard 120 — front-of-stage barrier 1 m'),crowdguard('straight'),(1,2,2)),
       'barrier_crowdguard_gate':(('CSC CrowdGuard 120 — portillon de fosse','CSC CrowdGuard 120 — pit gate'),crowdguard('gate'),(1,2,2)),
       'barrier_crowdguard_end':(('CSC CrowdGuard 120 — fin de ligne','CSC CrowdGuard 120 — end section'),crowdguard('end'),(1,2,2)),
       'barrier_cityline_2m':(('CSC CityLine 200 — barrière de ville 2 m','CSC CityLine 200 — crowd control barrier 2 m'),vauban(),(2,2,1)),
       'barrier_aluguard_1m':(('CSC AluGuard 124 — crash barrière aluminium 1 m','CSC AluGuard 124 — aluminium crowd barrier 1 m'),prolyte('straight'),(1,2,2)),
       'festival_stairs_h220':(('Escalier de scène de festival — 2 m','Festival stage stairs — 2 m'),festival_stairs(),(1,3,3)),
       'fence_site_3m':(('CSC SiteFence — clôture mobile 3,5 m','CSC SiteFence — temporary fence panel 3.5 m'),heras(False),(3,2,1)),
       'fence_site_3m_screen':(('CSC SiteFence — clôture mobile avec toile noire','CSC SiteFence — temporary fence with black screen'),heras(True),(3,2,1))}
RECIPES={'barrier_crowdguard_1m':['minecraft:iron_ingot']*4+['minecraft:iron_bars']*2+['minecraft:heavy_weighted_pressure_plate'],
         'barrier_crowdguard_gate':['concertstage:barrier_crowdguard_1m','minecraft:iron_door'],
         'barrier_crowdguard_end':['concertstage:barrier_crowdguard_1m','minecraft:iron_bars','minecraft:iron_ingot'],
         'barrier_cityline_2m':['minecraft:iron_bars']*3+['minecraft:iron_ingot']*2,
         'barrier_aluguard_1m':['minecraft:iron_ingot']*3+['minecraft:iron_bars']*2+['minecraft:light_weighted_pressure_plate'],
         'festival_stairs_h220':['minecraft:iron_ingot']*4+['minecraft:iron_bars']*2+['minecraft:smooth_stone_slab']*3,
         'fence_site_3m':['minecraft:iron_bars']*6+['minecraft:iron_ingot']*2+['minecraft:black_concrete'],
         'fence_site_3m_screen':['concertstage:fence_site_3m','concertstage:fence_screen_black']}
COUNT={'barrier_cityline_2m':2,'fence_site_3m':2}
LOOT={'fence_site_3m_screen':['concertstage:fence_site_3m','concertstage:fence_screen_black']}
CELL=lambda e:[int(((a+b)/2)//16) for a,b in zip(e['from'],e['to'])]
def split(els,size):
    """Répartit les éléments d'une pièce de plusieurs blocs entre ses cases (modèle par case, coordonnées de la case) : les éléments droits
    sont découpés aux limites des cases, les éléments tournés vont à la case de leur centre."""
    out={}
    sx,sy,sz=size
    for e in els:
        if 'rotation' in e:
            c=[min(max(v,0),n-1) for v,n in zip(CELL(e),size)]; o=[c[0]*16,c[1]*16,c[2]*16]
            n=json.loads(json.dumps(e)); n['from']=[round(a-b,4) for a,b in zip(e['from'],o)]; n['to']=[round(a-b,4) for a,b in zip(e['to'],o)]
            n['rotation']['origin']=[round(a-b,4) for a,b in zip(e['rotation']['origin'],o)]
            out.setdefault(tuple(c),[]).append(n); continue
        for cx in range(sx):
            for cy in range(sy):
                for cz in range(sz):
                    o=(cx*16,cy*16,cz*16); f=[];t=[];ok=True
                    for i in range(3):
                        a=max(e['from'][i],o[i]); b=min(e['to'][i],o[i]+16)
                        flat=e['from'][i]==e['to'][i]
                        if (b<=a and not flat) or (flat and not (o[i]<=a<o[i]+16 or (a==o[i]+16 and o[i]+16==[sx,sy,sz][i]*16))): ok=False;break
                        if flat: b=a
                        f.append(round(a-o[i],4)); t.append(round(b-o[i],4))
                    if ok:
                        n=json.loads(json.dumps(e)); n['from']=f; n['to']=t; out.setdefault((cx,cy,cz),[]).append(n)
    return out
def fits(els): return all(-16<=v<=32 for e in els for v in e['from']+e['to'])
def half(els,size):
    """Modèle d'objet d'une pièce trop grande : à demi-échelle, centrée (l'affichage double l'échelle)."""
    sx,sy,sz=size; c=(sx*8,0,sz*8); o=[]
    for e in els:
        n=json.loads(json.dumps(e)); n['from']=[round((v-c[i])/2+8,4) for i,v in enumerate(e['from'])]; n['to']=[round((v-c[i])/2+8,4) for i,v in enumerate(e['to'])]
        if 'rotation' in n: n['rotation']['origin']=[round((v-c[i])/2+8,4) for i,v in enumerate(n['rotation']['origin'])]
        o.append(n)
    return o
def dump(p,d,i=None): p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(d,ensure_ascii=False,indent=i))
def build():
    texture(); tex={'0':'concertstage:block/csc_barrier','particle':'concertstage:block/csc_barrier'}
    for k,(names,els,(sx,sy,sz)) in ITEMS.items():
        size=(sx,sy,sz)
        if fits(els):
            dump(AS/'models/block'/(k+'.json'),{'ambientocclusion':False,'textures':tex,'elements':els}); parts={0:k}; item_parent='concertstage:block/'+k; m=1
        else:
            parts={}
            for (cx,cy,cz),pe in split(els,size).items():
                p=cx+sx*(cz+sz*cy); name=f'{k}_p{p}'; parts[p]=name
                assert fits(pe),(k,p)
                dump(AS/'models/block'/(name+'.json'),{'ambientocclusion':False,'textures':tex,'elements':pe})
            dump(AS/'models/block'/(k+'.json'),{'ambientocclusion':False,'textures':tex,'elements':half(els,size)}); item_parent='concertstage:block/'+k; m=2
        v={}
        for f,y in (('north',0),('east',90),('south',180),('west',270)):
            for part in range(27):
                row={'model':'concertstage:block/'+(parts.get(part,'empty_regie'))}
                if y and part in parts: row['y']=y
                v[f'facing={f},part={part}']=row
        if k=='festival_stairs_h220':
            # escaliers côte à côte : sans la rampe du côté d'un escalier voisin (rail_l = côté x=0, rail_r = côté x=16)
            v={}
            for rl in (True,False):
                for rr in (True,False):
                    suffix='' if rl and rr else ('_nl' if rr else '_nr' if rl else '_nb')
                    names={}
                    if suffix:
                        for (cx,cy,cz),pe in split(festival_stairs(rl,rr),size).items():
                            p=cx+sx*(cz+sz*cy); names[p]=f'{k}_p{p}{suffix}'
                            dump(AS/'models/block'/(names[p]+'.json'),{'ambientocclusion':False,'textures':tex,'elements':pe})
                    else: names=parts
                    for f,y in (('north',0),('east',90),('south',180),('west',270)):
                        for part in range(27):
                            row={'model':'concertstage:block/'+(names.get(part,'empty_regie'))}
                            if y and part in names: row['y']=y
                            v[f'facing={f},part={part},rail_l={str(rl).lower()},rail_r={str(rr).lower()}']=row
        dump(AS/'blockstates'/(k+'.json'),{'variants':v})
        S=lambda a:[round(a*m,3)]*3
        dump(AS/'models/item'/(k+'.json'),{'parent':item_parent,'display':{'gui':{'rotation':[30,225,0],'translation':[0,-2,0],'scale':S(0.42 if max(sx,sz)<3 else 0.3)},
             'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':S(0.25)},'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':S(0.4)},
             'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':S(0.3)},'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':S(0.3)},
             'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,0,0],'scale':S(0.3)}}})
        dump(DATA/'loot_tables/blocks'/(k+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':i}],'conditions':[{'condition':'minecraft:survives_explosion'}]} for i in LOOT.get(k,['concertstage:'+k])]},2)
        dump(DATA/'recipes'/(k+'.json'),{'type':'minecraft:crafting_shapeless','category':'building','ingredients':[{'item':i} for i in RECIPES[k]],'result':{'item':'concertstage:'+k,'count':COUNT.get(k,1)}},2)
        for i,lg in enumerate(('fr_fr','en_us')):
            p=AS/'lang'/(lg+'.json'); d=json.loads(p.read_text()); d['block.concertstage.'+k]=names[i]; p.write_text(json.dumps(d,ensure_ascii=False,indent=2))
    # toile de clôture (objet) : modèle plat, recette
    dump(AS/'models/item/fence_screen_black.json',{'parent':'minecraft:item/generated','textures':{'layer0':'concertstage:item/fence_screen_black'}})
    ic=Image.new('RGBA',(16,16),(0,0,0,0)); d=ImageDraw.Draw(ic)
    d.polygon([(2,4),(13,2),(14,11),(3,14)],fill=(26,27,28,255)); d.line([(2,4),(13,2),(14,11),(3,14),(2,4)],fill=(70,72,74,255))
    for q in ((3,5),(8,4),(12,3),(4,12),(9,12),(13,10)): d.point(q,fill=(200,204,208,255))
    for yy in range(5,13,2): d.line((4,yy,12,yy-1),fill=(44,46,48,255))
    (AS/'textures/item').mkdir(parents=True,exist_ok=True); ic.save(AS/'textures/item/fence_screen_black.png')
    dump(DATA/'recipes/fence_screen_black.json',{'type':'minecraft:crafting_shapeless','category':'building','ingredients':[{'item':'minecraft:black_wool'}]*3+[{'item':'minecraft:string'},{'item':'minecraft:iron_nugget'}],'result':{'item':'concertstage:fence_screen_black','count':1}},2)
    for i,lg in enumerate(('fr_fr','en_us')):
        p=AS/'lang'/(lg+'.json'); d2=json.loads(p.read_text())
        d2['item.concertstage.fence_screen_black']=('Toile de clôture noire 1,76 × 3,41 m','Black fence screen 1.76 × 3.41 m')[i]
        d2['tooltip.concertstage.fence_screen_black']=('Clic droit sur une clôture mobile pour la poser ; cisailles pour la retirer.','Right-click a temporary fence to fit it; shears to take it off.')[i]
        p.write_text(json.dumps(d2,ensure_ascii=False,indent=2))
    p=RES/'data/minecraft/tags/blocks/mineable/pickaxe.json'; t=json.loads(p.read_text()); t['values']=list(dict.fromkeys(t['values']+['concertstage:'+k for k in ITEMS])); p.write_text(json.dumps(t,indent=2))
    bad=[(k,e['from'],e['to']) for k,(n,els,s) in ITEMS.items() for e in els if (fits(els) and any(v<-16 or v>32 for v in e['from']+e['to'])) or any(a>b for a,b in zip(e['from'],e['to']))]
    print('barrières ok, éléments hors bornes :',bad)
if __name__=='__main__': build()
