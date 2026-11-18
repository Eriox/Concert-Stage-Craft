#!/usr/bin/env python3
"""Kit de régie v2 « CSC RingFrame » (inspiré des échafaudages multidirectionnels à rosaces : montants Ø48,3 à rosaces tous les 50 cm,
lisses à têtes de clavette, vérins de pied à platine, garde-corps à plinthe bois, cadres contreventés) + poutre alu « CSC LiteBeam 25 »
+ « CSC RoadCase 60 ». Modèles vanilla (Forge + Fabric), mêmes emprises et collisions que le kit 1.3.9, texture regie_v2.png."""
import json, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]; AS=ROOT/'common/src/main/resources/assets/concertstage'
R={'tube':(0,0),'rosette':(4,0),'wedge':(8,0),'plate':(12,0),'wood':(0,4),'alu':(4,4),'case':(8,4),'chrome':(12,4),'accent':(0,8),'rubber':(4,8)}
def texture():
    rng=np.random.default_rng(3); im=np.zeros((64,64,4),np.uint8); im[...,3]=255
    def fill(key,base,noise=4,streak=None):
        x,y=R[key]; a=np.array(base,float)+rng.normal(0,noise,(16,16,1))
        if streak=='v': a+=np.linspace(-14,14,16)[None,:,None]*np.cos(np.linspace(0,math.pi,16))[None,:,None]
        if streak=='h': a+=rng.normal(0,6,(16,1,1))
        im[y*4:y*4+16,x*4:x*4+16,:3]=np.clip(a,0,255)
    fill('tube',(176,180,182),3,'v'); fill('rosette',(128,132,136),3); fill('wedge',(98,100,104),5); fill('plate',(140,140,136),4)
    fill('wood',(170,128,74),6,'h'); fill('alu',(205,208,212),2,'v'); fill('case',(26,26,28),3); fill('chrome',(220,222,226),2,'v')
    fill('accent',(30,90,170),3); fill('rubber',(20,20,20),2)
    img=Image.fromarray(im); d=ImageDraw.Draw(img)
    for cx,cy in ((20,4),(28,4),(20,12),(28,12),(24,2),(24,14),(18,8),(30,8)): d.ellipse((cx-1,cy-1,cx+1,cy+1),fill=(40,40,44,255))   # trous de rosette
    for y in range(48,64,4): d.line((48,y,63,y),fill=(110,110,106,255))                                                            # filets du vérin
    d.rectangle((32,16,47,17),fill=(60,60,62,255)); d.rectangle((32,30,47,31),fill=(60,60,62,255))                                     # bords contreplaqué
    img.save(AS/'textures/block/regie_v2.png')
def uv(key):
    x,y=R[key]; return [x,y,x+4,y+4]
def el(a,b,key,rot=None):
    e={'from':[round(v,4) for v in a],'to':[round(v,4) for v in b],'faces':{f:{'uv':uv(key),'texture':'#0'} for f in ('north','south','east','west','up','down')}}
    if rot: e['rotation']={'angle':rot[0],'axis':rot[1],'origin':rot[2]}
    return e
def tube(out,a,b,r,key='tube'):
    """Tube rond approché : carré + carré tourné de 45° autour de son axe."""
    ax=[i for i in range(3) if abs(a[i]-b[i])>1e-6][0]; c=[(a[i]+b[i])/2 for i in range(3)]
    lo=[c[i]-r if i!=ax else min(a[ax],b[ax]) for i in range(3)]; hi=[c[i]+r if i!=ax else max(a[ax],b[ax]) for i in range(3)]
    out.append(el(lo,hi,key)); r2=r*0.92; lo2=[c[i]-r2 if i!=ax else lo[i] for i in range(3)]; hi2=[c[i]+r2 if i!=ax else hi[i] for i in range(3)]
    out.append(el(lo2,hi2,key,(45,'xyz'[ax],c)))
def rosette(out,y,c=(8,8)):
    for rot in (None,(45,'y',[c[0],y,c[1]])): out.append(el((c[0]-2.6,y-0.3,c[1]-2.6),(c[0]+2.6,y+0.3,c[1]+2.6),'rosette',rot))
def post(out,y0=0,y1=16,c=(8,8)):
    tube(out,(c[0],y0,c[1]),(c[0],y1,c[1]),1.6)
    for y in (7.7,15.7):
        if y0<y<y1: rosette(out,y,c)
def wedge_head(out,x,dirx,y=8,z=8,axis='x'):
    """Tête de lisse coulée + clavette verticale, contre la rosette du montant en x (dirx = sens de la lisse)."""
    if axis=='x':
        a,b=sorted((x,x+2.0*dirx)); out.append(el((a,y-1.3,z-1.1),(b,y+1.3,z+1.1),'wedge'))
        m=x+0.9*dirx; out.append(el((m-0.25,y-2.4,z-0.5),(m+0.25,y+2.0,z+0.5),'wedge'))
    else:
        a,b=sorted((z,z+2.0*dirx)); out.append(el((x-1.1,y-1.3,a),(x+1.1,y+1.3,b),'wedge'))
        m=z+0.9*dirx; out.append(el((x-0.5,y-2.4,m-0.25),(x+0.5,y+2.0,m+0.25),'wedge'))
def ledger_x(out,x0,x1,y=8,z=8,heads=()):
    tube(out,(x0,y,z),(x1,y,z),0.8)
    for x,d in heads: wedge_head(out,x,d,y,z,'x')
def model(elements,name,parent_display=None):
    d={'ambientocclusion':False,'textures':{'0':'concertstage:block/regie_v2','particle':'concertstage:block/regie_v2'},'elements':elements}
    (AS/'models/block'/(name+'.json')).write_text(json.dumps(d,separators=(',',':')))
def build():
    texture()
    e=[]; post(e); model(e,'regie_poteau_h1')
    e=[]; e.append(el((4.5,0,4.5),(11.5,0.5,11.5),'plate'))
    tube(e,(8,0.5,8),(8,6.5,8),0.9,'plate')
    e.append(el((5.2,5.0,7.3),(10.8,5.8,8.7),'wedge')); e.append(el((7.3,5.0,5.2),(8.7,5.8,10.8),'wedge'))   # écrou papillon
    post(e,6.0,16); rosette(e,6.3); model(e,'regie_poteau_pied_h1')
    def node(px,nx,pz,nz):
        e=[]; post(e)
        if px: ledger_x(e,9.6,16,heads=[(9.6,1)])
        if nx: ledger_x(e,0,6.4,heads=[(6.4,-1)])
        if pz: tube(e,(8,8,9.6),(8,8,16),0.8); wedge_head(e,8,1,8,9.6,'z')
        if nz: tube(e,(8,8,0),(8,8,6.4),0.8); wedge_head(e,8,-1,8,6.4,'z')
        return e
    model(node(True,False,True,False),'regie_noeud_angle'); model(node(True,True,True,False),'regie_noeud_t'); model(node(True,True,True,True),'regie_noeud_croix')
    e=[]; ledger_x(e,0,16); e.append(el((7.2,7.0,7.0),(8.8,9.0,9.0),'accent')); model(e,'regie_lisse_l1')   # bague d'identification
    e=[]; ledger_x(e,0,16,y=14.8); ledger_x(e,0,16,y=8.0); e.append(el((0,0,7.6),(16,2.6,8.4),'wood'))
    for x in (0.4,15.0): e.append(el((x,0,7.3),(x+0.6,2.8,8.7),'wedge'))   # clips de plinthe
    model(e,'regie_barriere_l1')
    e=[]; post(e,c=(8,8)); post(e,c=(24,8))
    for y,(h) in ((1.0,[(9.6,1),(22.4,-1)]),(8.0,[(9.6,1),(22.4,-1)]),(15.1,[(9.6,1),(22.4,-1)])): ledger_x(e,9.6,22.4,y=y,heads=h)
    L=math.hypot(12.8,13.2); cx,cy=16,8.05
    e.append(el((cx-L/2,cy-0.7,7.3),(cx+L/2,cy+0.7,8.7),'tube',(45,'z',[cx,cy,8]))); e.append(el((cx-L/2-0.2,cy-0.55,7.45),(cx+L/2+0.2,cy+0.55,8.55),'tube',(45,'z',[cx,cy,8])))
    model(e,'regie_panneau_l2h1')
    def girder(L):
        e=[]
        for y in (4,12):
            for z in (4,12): tube(e,(0,y,z),(L,y,z),0.8,'alu')
        n=int(L/16)*2
        for i in range(n+1):
            x=min(L-0.5,max(0.5,i*L/n))
            for z in (4,12): e.append(el((x-0.4,4,z-0.4),(x+0.4,12,z+0.4),'alu'))
            for y in (4,12): e.append(el((x-0.4,y-0.4,4),(x+0.4,y+0.4,12),'alu'))
        for i in range(n):
            x0=i*L/n; cx=x0+L/(2*n); Ld=min(math.hypot(L/n,8),2*(32-cx)-0.1,2*(cx+16)-0.1)
            for z in (4,12): e.append(el((cx-Ld/2,7.6,z-0.35),(cx+Ld/2,8.4,z+0.35),'alu',((45 if i%2==0 else -45),'z',[cx,8,z])))
        for x in (0,L):
            for y in (4,12):
                for z in (4,12): e.append(el((x-0.01 if x else 0,y-1.1,z-1.1),(x if x else 0.6,y+1.1,z+1.1),'accent') if x==0 else el((x-0.6,y-1.1,z-1.1),(x,y+1.1,z+1.1),'accent'))
        return e
    model(girder(16),'regie_poutre_treillis_l1'); model(girder(32),'regie_poutre_treillis_l2')
    e=[]; e.append(el((0.6,1.6,3.1),(15.4,9.6,12.9),'case'))
    for (a,b) in (((0.2,1.4,2.7),(15.8,1.9,3.2)),((0.2,1.4,12.8),(15.8,1.9,13.3)),((0.2,9.4,2.7),(15.8,10,3.2)),((0.2,9.4,12.8),(15.8,10,13.3)),
                  ((0.2,1.4,2.7),(0.7,10,3.2)),((15.3,1.4,2.7),(15.8,10,3.2)),((0.2,1.4,12.8),(0.7,10,13.3)),((15.3,1.4,12.8),(15.8,10,13.3)),
                  ((0.2,1.4,2.7),(0.7,1.9,13.3)),((15.3,1.4,2.7),(15.8,1.9,13.3)),((0.2,9.4,2.7),(0.7,10,13.3)),((15.3,9.4,2.7),(15.8,10,13.3))): e.append(el(a,b,'chrome'))
    for x in (0.2,14.6):
        for y in (1.4,8.8):
            for z in (2.7,12.1): e.append(el((x,y,z),(x+1.2,y+1.2,z+1.2),'chrome'))   # coins boule
    for x in (3.5,11.5): e.append(el((x-0.9,6.0,2.75),(x+0.9,7.4,3.1),'chrome'))           # fermetures papillon
    for z in (6.5,9.5): pass
    e.append(el((0.25,5.2,6.4),(0.6,6.4,9.6),'rubber')); e.append(el((15.4,5.2,6.4),(15.75,6.4,9.6),'rubber'))   # poignées encastrées
    e.append(el((5.5,9.6,6.2),(10.5,9.75,9.8),'accent'))                                                           # étiquette du capot
    for x in (2.0,14.0):
        for z in (4.6,11.4):
            e.append(el((x-0.9,0.0,z-0.9),(x+0.9,1.4,z+0.9),'rubber')); e.append(el((x-1.1,1.2,z-1.1),(x+1.1,1.6,z+1.1),'chrome'))   # roulettes
    model(e,'regie_flight_case')
    names={'regie_poteau_h1':('CSC RingFrame — montant 1 m','CSC RingFrame — 1 m standard'),
           'regie_poteau_pied_h1':('CSC RingFrame — montant 1 m sur vérin','CSC RingFrame — 1 m standard on base jack'),
           'regie_noeud_angle':('CSC RingFrame — nœud d’angle','CSC RingFrame — corner node'),
           'regie_noeud_t':('CSC RingFrame — nœud en T','CSC RingFrame — T node'),
           'regie_noeud_croix':('CSC RingFrame — nœud en croix','CSC RingFrame — cross node'),
           'regie_lisse_l1':('CSC RingFrame — lisse 1 m','CSC RingFrame — 1 m ledger'),
           'regie_barriere_l1':('CSC RingFrame — garde-corps à plinthe','CSC RingFrame — guard rail with toe board'),
           'regie_panneau_l2h1':('CSC RingFrame — cadre contreventé 2 m','CSC RingFrame — 2 m braced frame'),
           'regie_poutre_treillis_l1':('CSC LiteBeam 25 — 1 m','CSC LiteBeam 25 — 1 m'),
           'regie_poutre_treillis_l2':('CSC LiteBeam 25 — 2 m','CSC LiteBeam 25 — 2 m'),
           'regie_flight_case':('CSC RoadCase 60','CSC RoadCase 60')}
    for i,lg in enumerate(('fr_fr','en_us')):
        p=AS/'lang'/(lg+'.json'); d=json.loads(p.read_text())
        for k,v in names.items(): d['block.concertstage.'+k]=v[i]
        d['itemGroup.concertstage.regie']=('Régie (CSC RingFrame)','FOH (CSC RingFrame)')[i]
        p.write_text(json.dumps(d,ensure_ascii=False,indent=2))
    print('régie v2 ok')

def drops():
    """Variantes décalées vers le bas (propriété drop, 1 à 15 px) : pièce posée sur un plancher plus bas que le haut de sa case."""
    n=0
    for bs in sorted(AS.glob('blockstates/regie_*.json')):
        if bs.stem.startswith('regie_tarp'): continue
        d=json.loads(bs.read_text()); v=d['variants']; out={}
        base_keys=sorted({','.join(x for x in k.split(',') if not x.startswith('drop=')) for k in v})
        for k in base_keys:
            src=v.get(k) or v.get(k+',drop=0')
            for dr in range(16):
                m=src['model']
                if dr and not m.endswith('empty_regie'):
                    name=m.split(':')[1].split('/')[-1].split('_d')[0] if '_d' in m.split('/')[-1] and m.split('/')[-1].rsplit('_d',1)[1].isdigit() else m.split(':')[1].split('/')[-1]
                    md=json.loads((AS/'models/block'/(name+'.json')).read_text())
                    for e in md['elements']:
                        e['from'][1]-=dr; e['to'][1]-=dr
                        if 'rotation' in e: e['rotation']['origin'][1]-=dr
                    (AS/'models/block'/(f'{name}_d{dr}.json')).write_text(json.dumps(md,separators=(',',':'))); n+=1
                    m=f'concertstage:block/{name}_d{dr}'
                row={'model':m}
                if src.get('y'): row['y']=src['y']
                out[k+f',drop={dr}']=row
        bs.write_text(json.dumps({'variants':out},separators=(',',':')))
    print('variantes décalées',n)
if __name__=='__main__': build(); drops()
