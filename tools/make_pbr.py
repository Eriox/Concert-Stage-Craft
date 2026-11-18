#!/usr/bin/env python3
"""Cartes spéculaires labPBR (<texture>_s.png) lues par Oculus avec les packs de shaders PBR (Complementary, BSL, SEUS PTGI…) :
R = lissé, G = F0 / métal (230 fer, 232 aluminium, 233 chrome ; < 230 = diélectrique), B = 0, A = 255 (pas d'émission).
Sans shaders, Minecraft ignore ces fichiers."""
from pathlib import Path
import numpy as np
from PIL import Image
T=Path(__file__).resolve().parents[1]/'common/src/main/resources/assets/concertstage/textures/block'
def spec(src, rule):
    im=np.asarray(Image.open(src).convert('RGBA')).astype(float); rgb=im[...,:3]
    lum=rgb.mean(-1); sat=rgb.max(-1)-rgb.min(-1)
    r,g=rule(lum,sat,rgb)
    out=np.zeros(im.shape,np.uint8); out[...,0]=np.clip(r,0,255); out[...,1]=np.clip(g,0,255); out[...,3]=255
    Image.fromarray(out).save(src.with_name(src.stem+'_s.png')); return src.name
def metal(kind):
    return lambda lum,sat,rgb: (np.where(sat<40,120+lum*0.35,60), np.where((sat<40)&(lum>60),kind,20))
done=[]
done.append(spec(T/'rigging_detail.png', lambda l,s,c:(140+l*0.3, np.where(l>40,232,230))))
if (T/'rigging_detail_noir.png').exists(): done.append(spec(T/'rigging_detail_noir.png', lambda l,s,c:(95+l*0.2, np.full(l.shape,22))))
done.append(spec(T/'stage/metals.png', metal(230)))
done.append(spec(T/'stage/hardware.png', metal(230)))
done.append(spec(T/'stage/anti_slip.png', lambda l,s,c:(np.full(l.shape,45),np.full(l.shape,12))))
done.append(spec(T/'stage/csc_v3_atlas.png', metal(230)))
def regie(l,s,c):
    r=np.full(l.shape,70.0); g=np.full(l.shape,15.0)
    def reg(x,y,rv,gv): r[y*4:y*4+16,x*4:x*4+16]=rv; g[y*4:y*4+16,x*4:x*4+16]=gv
    for (x,y) in ((0,0),(4,0),(8,0),(12,0)): reg(x,y,150,230)     # acier galvanisé
    reg(0,4,40,10); reg(4,4,170,232); reg(8,4,60,15); reg(12,4,215,233); reg(0,8,120,20); reg(4,8,30,10)
    return r,g
if (T/'regie_v2.png').exists(): done.append(spec(T/'regie_v2.png', regie))
for p in sorted(T.glob('stage_tarp_*.png')):
    if p.stem.endswith('_s'): continue
    if 'hardware' in p.stem: done.append(spec(p, lambda l,s,c:(np.where(l>100,200,60),np.where(l>100,231,15))))   # œillets laiton
    else: done.append(spec(p, lambda l,s,c:(np.full(l.shape,105),np.full(l.shape,18))))                          # PVC enduit
for n in ('truss.png','metal.png'):
    if (T/n).exists(): done.append(spec(T/n, lambda l,s,c:(140+l*0.3,np.full(l.shape,232))))
print(len(done),'cartes _s :',', '.join(done))
