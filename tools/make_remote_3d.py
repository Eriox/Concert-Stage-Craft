#!/usr/bin/env python3
"""Télécommande de levage en 3D : boîtier radio industriel (jaune / caoutchouc noir), arrêt d'urgence, antenne,
écran LCD, boutons monter / descendre / marche / arrêt. Texture 128 px, modèle d'item JSON vanilla (Forge et Fabric)."""
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np, json
AS=Path(__file__).resolve().parents[1]/'common/src/main/resources/assets/concertstage'
W=128; im=Image.new('RGBA',(W,W),(0,0,0,0)); d=ImageDraw.Draw(im); rng=np.random.default_rng(3)
def fill(x0,y0,x1,y1,col,noise=5):
    a=np.zeros((y1-y0,x1-x0,4),np.uint8); a[...,:3]=np.clip(np.array(col)+rng.normal(0,noise,(y1-y0,x1-x0,1)),0,255); a[...,3]=255
    im.paste(Image.fromarray(a),(x0,y0))
fill(0,0,32,32,(236,182,20)); d.rectangle((0,0,31,31),outline=(200,150,12),width=2)
fill(32,0,64,32,(30,31,33),3); fill(64,0,96,32,(200,30,26),5); fill(96,0,128,32,(122,126,132),4)
d.rectangle((0,32,63,63),fill=(16,36,24)); d.rectangle((3,35,60,60),fill=(70,140,92))
d.text((6,37),'LIFT  RC',fill=(12,30,18)); d.text((6,48),'12.0 m',fill=(12,30,18)); d.rectangle((44,49,57,56),outline=(12,30,18)); d.rectangle((45,50,53,55),fill=(12,30,18))
d.rectangle((64,32,127,63),fill=(214,216,218)); d.rectangle((66,34,125,61),outline=(90,92,96)); d.text((70,37),'CSC',fill=(28,28,28)); d.text((70,49),'LIFT REMOTE',fill=(70,70,70))
def button(x0,col,arrow=None):
    d.rectangle((x0,64,x0+15,79),fill=(22,22,24)); d.ellipse((x0+1,65,x0+14,78),fill=col); d.ellipse((x0+3,66,x0+10,70),fill=tuple(min(255,c+40) for c in col))
    if arrow=='up': d.polygon([(x0+8,67),(x0+13,75),(x0+3,75)],fill=(245,245,245))
    if arrow=='down': d.polygon([(x0+8,76),(x0+13,68),(x0+3,68)],fill=(245,245,245))
button(0,(62,64,68),'up'); button(16,(62,64,68),'down'); button(32,(40,160,64)); button(48,(204,34,30))
(AS/'textures/item').mkdir(parents=True,exist_ok=True); im.save(AS/'textures/item/lift_remote_3d.png')
UV=lambda x0,y0,x1,y1:[x0/8,y0/8,x1/8,y1/8]
YEL=UV(0,0,32,32); BLK=UV(32,0,64,32); RED=UV(64,0,96,32); GRY=UV(96,0,128,32); LCD=UV(0,32,64,64); PLATE=UV(64,32,128,64)
UP=UV(0,64,16,80); DOWN=UV(16,64,32,80); GO=UV(32,64,48,80); STOP=UV(48,64,64,80)
E=[]
def el(name,a,b,uv,front=None,back=None,rot=None):
    f={k:{'uv':uv,'texture':'#0'} for k in ('north','east','south','west','up','down')}
    if front: f['south']={'uv':front,'texture':'#0'}
    if back: f['north']={'uv':back,'texture':'#0'}
    e={'name':name,'from':a,'to':b,'faces':f}
    if rot: e['rotation']=rot
    E.append(e)
el('Boîtier',[5,1.6,7],[11,10.6,9.5],YEL)
el('Capot supérieur caoutchouc',[4.6,10.4,6.6],[11.4,12,9.9],BLK)
el('Pare-chocs inférieur',[4.6,0.4,6.6],[11.4,2,9.9],BLK)
el('Bande latérale gauche',[4.7,2,6.8],[5.1,10.4,9.7],BLK)
el('Bande latérale droite',[10.9,2,6.8],[11.3,10.4,9.7],BLK)
el('Socle d’arrêt d’urgence',[6.6,12,7.2],[9.4,12.6,9.3],YEL)
el('Bouton coup-de-poing',[6.9,12.6,7.15],[9.1,13.6,9.35],RED)
el('Bouton coup-de-poing',[6.9,12.6,7.15],[9.1,13.6,9.35],RED,rot={'angle':45,'axis':'y','origin':[8,13.1,8.25]})
el('Antenne',[10,12,7.7],[10.6,15.4,8.3],BLK)
el('Embout d’antenne',[9.9,15.4,7.6],[10.7,16,8.4],BLK)
el('Écran LCD',[5.8,7.0,9.5],[10.2,9.8,9.75],BLK,front=LCD)
el('Bouton monter',[5.9,4.7,9.5],[7.8,6.6,9.9],BLK,front=UP)
el('Bouton descendre',[8.2,4.7,9.5],[10.1,6.6,9.9],BLK,front=DOWN)
el('Bouton marche',[5.9,2.5,9.5],[7.8,4.3,9.9],BLK,front=GO)
el('Bouton arrêt',[8.2,2.5,9.5],[10.1,4.3,9.9],BLK,front=STOP)
el('Plaque signalétique',[6,3,6.8],[10,7,7.0],GRY,back=PLATE)
el('Anneau de dragonne',[7.3,-0.4,7.9],[8.7,0.4,8.6],GRY)
model={'credit':'Concert Stage — télécommande 3D','textures':{'0':'concertstage:item/lift_remote_3d','particle':'concertstage:item/lift_remote_3d'},'elements':E,'gui_light':'front',
 'display':{'gui':{'rotation':[12,-28,0],'translation':[0,0.2,0],'scale':[1.05,1.05,1.05]},
            'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.5,0.5,0.5]},
            'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[1,1,1]},
            'thirdperson_righthand':{'rotation':[75,180,0],'translation':[0,2.5,1.5],'scale':[0.55,0.55,0.55]},
            'thirdperson_lefthand':{'rotation':[75,180,0],'translation':[0,2.5,1.5],'scale':[0.55,0.55,0.55]},
            'firstperson_righthand':{'rotation':[-8,-10,0],'translation':[-1.5,3.4,-1.2],'scale':[0.7,0.7,0.7]},
            'firstperson_lefthand':{'rotation':[-8,10,0],'translation':[1.5,3.4,-1.2],'scale':[0.7,0.7,0.7]}}}
(AS/'models/item/lift_remote.json').write_text(json.dumps(model,ensure_ascii=False,indent=1))
print(len(E),'éléments')
