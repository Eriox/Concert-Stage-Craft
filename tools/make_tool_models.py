#!/usr/bin/env python3
"""Modèles 3D (éléments vanilla) des outils : outil bâche (tendeur à cliquet), équerre d'angle, CSC RingCommand et CSC RoofCommand (tablettes)."""
import json, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
AS=Path(__file__).resolve().parents[1]/'common/src/main/resources/assets/concertstage'
R={'black':(0,0),'rubber':(4,0),'yellow':(8,0),'green':(12,0),'steel':(0,4),'grad':(4,4),'blue':(8,4),'cyan':(12,4),'screen':(0,8),'orange':(4,8),'white':(8,8),'webbing':(12,8),'purple':(0,12)}
def texture():
    rng=np.random.default_rng(1); im=np.zeros((64,64,4),np.uint8); im[...,3]=255
    cols={'black':(32,33,36),'rubber':(22,22,24),'yellow':(236,184,40),'green':(52,190,120),'steel':(178,182,188),'grad':(220,222,214),'blue':(70,120,240),'cyan':(53,201,232),
          'screen':(12,22,28),'orange':(240,120,40),'white':(235,237,240),'webbing':(220,170,40),'purple':(176,124,255)}
    for k,(x,y) in R.items():
        a=np.array(cols[k],float)+rng.normal(0,3,(16,16,1)); im[y*4:y*4+16,x*4:x*4+16,:3]=np.clip(a,0,255)
    img=Image.fromarray(im); d=ImageDraw.Draw(img)
    for i in range(0,16,2): d.line((16+i,16,16+i,16+(5 if i%4==0 else 3)),fill=(30,30,30,255))   # graduations
    for y in range(4,16,3): d.line((4,y,15,y),fill=(12,12,12,255))                             # grip
    for y in range(32,48,2): d.line((48,y,63,y),fill=(170,120,20,255))                          # sangle
    d.ellipse((2,34,13,45),outline=(53,201,232,255))                                            # icône d'anneau à l'écran
    img.save(AS/'textures/item/tools_3d.png')
def uv(k): x,y=R[k]; return [x,y,x+4,y+4]
def el(a,b,k,rot=None):
    e={'from':list(a),'to':list(b),'faces':{f:{'uv':uv(k),'texture':'#0'} for f in ('north','south','east','west','up','down')}}
    if rot: e['rotation']={'angle':rot[0],'axis':rot[1],'origin':list(rot[2])}
    return e
HAND={'thirdperson_righthand':{'rotation':[0,90,-35],'translation':[0,1.25,-3.5],'scale':[0.85,0.85,0.85]},
      'thirdperson_lefthand':{'rotation':[0,-90,35],'translation':[0,1.25,-3.5],'scale':[0.85,0.85,0.85]},
      'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.13,3.2,1.13],'scale':[0.68,0.68,0.68]},
      'firstperson_lefthand':{'rotation':[0,90,-25],'translation':[1.13,3.2,1.13],'scale':[0.68,0.68,0.68]},
      'gui':{'rotation':[25,-35,0],'translation':[0,0,0],'scale':[0.9,0.9,0.9]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.5,0.5,0.5]},
      'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[1,1,1]}}
def save(name,els):
    (AS/'models/item'/(name+'.json')).write_text(json.dumps({'textures':{'0':'concertstage:item/tools_3d','particle':'concertstage:item/tools_3d'},'elements':els,'display':HAND},indent=1))
def build():
    texture()
    # outil bâche : tendeur à cliquet (poignée caoutchouc, corps vert, enrouleur de sangle jaune, crochet acier)
    e=[el((7,0,7),(9,7,9),'rubber'),el((6.5,7,6.5),(9.5,11,9.5),'green'),el((5.5,8,7.2),(10.5,10,8.8),'steel'),
       el((6,11,6.6),(10,14,9.4),'webbing'),el((5.6,12,7.6),(10.4,13,8.4),'steel'),el((7.6,14,7.6),(8.4,16,8.4),'steel'),
       el((7.6,15.2,8.4),(8.4,16,9.8),'steel'),el((9.5,9.3,7.6),(12,9.9,8.4),'black',(-22.5,'z',(9.5,9.6,8)))]
    save('curtain_tool',e)
    # équerre d'angle : branches acier graduées, rapporteur bleu, molette
    e=[el((2,1,7.6),(4,15,8.4),'grad'),el((2,1,7.6),(14,3,8.4),'grad'),el((3.5,2.5,7.4),(11,3.2,8.6),'blue',(45,'z',(3.5,2.5,8))),
       el((3,8,7.5),(9,8.8,8.5),'blue',(22.5,'z',(3,8,8))),el((8,2.6,7.5),(9,8.6,8.5),'blue',(-22.5,'z',(8.5,5,8))),
       el((2.6,1.6,7.2),(4.2,3.2,8.8),'orange')]
    save('angle_tool',e)
    # CSC RingCommand : tablette à cadre cyan, écran avec anneau, poignées caoutchouc
    e=[el((2,2,7.4),(14,12,8.6),'black'),el((2.4,2.4,7.3),(13.6,11.6,7.4),'screen'),el((1.4,3,7.2),(2,11,8.8),'rubber'),el((14,3,7.2),(14.6,11,8.8),'rubber'),
       el((2,11.6,7.4),(14,12.2,8.6),'cyan'),el((6,1.2,7.6),(10,2,8.4),'cyan')]
    for i in range(8):
        a=2*math.pi*i/8; x=8+2.6*math.cos(a); y=7+2.6*math.sin(a); e.append(el((x-0.4,y-0.4,7.2),(x+0.4,y+0.4,7.3),'cyan'))
    save('ring_truss_tool',e)
    # CSC RoofCommand : tablette à cadre violet, écran avec un pignon de toit
    e=[el((2,2,7.4),(14,12,8.6),'black'),el((2.4,2.4,7.3),(13.6,11.6,7.4),'screen'),el((1.4,3,7.2),(2,11,8.8),'rubber'),el((14,3,7.2),(14.6,11,8.8),'rubber'),
       el((2,11.6,7.4),(14,12.2,8.6),'purple'),el((6,1.2,7.6),(10,2,8.4),'purple'),
       el((3.4,4,7.2),(12.6,4.6,7.3),'purple')]
    for i in range(9):
        x=3.6+i*1.1; y=4.6+(4.4-abs(x-8)*0.95); e.append(el((x-0.45,y-0.35,7.2),(x+0.45,y+0.35,7.3),'purple'))
    save('roof_truss_tool',e)
    print('outils 3D ok')
if __name__=='__main__': build()
