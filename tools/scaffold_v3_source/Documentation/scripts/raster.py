"""Rendu z-buffer logiciel des .bbmodel (aperçus fiables, sans matplotlib)."""
import sys, math, numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0,'/home/claude/v3/tools')
from prev3 import load
LIGHT=np.array([.35,.85,.45]); LIGHT/=np.linalg.norm(LIGHT)
def view_fn(v, az=35, el=28):
    a,e=math.radians(az),math.radians(el)
    if v=='front': return lambda P: (P[:,0], P[:,1], P[:,2])
    if v=='back':  return lambda P: (-P[:,0], P[:,1], -P[:,2])
    if v=='side':  return lambda P: (-P[:,2], P[:,1], -P[:,0])     # vu depuis +X... profil
    if v=='top':   return lambda P: (P[:,0], -P[:,2], -P[:,1])
    if v=='persp':
        ex=np.array([math.cos(a),0,-math.sin(a)]); ey=np.array([math.sin(a)*math.sin(e),math.cos(e),math.cos(a)*math.sin(e)])
        n=np.cross(ex,ey)
        return lambda P: (P@ex, P@ey, P@n)
    raise ValueError(v)
TITLES={'front':'face','back':'arrière','side':'profil','top':'dessus','persp':'perspective'}
def render_view(polys, v, px=520, az=35, el=28, bg=(228,232,238), bounds=None):
    f=view_fn(v,az,el)
    tris=[];cols=[]
    for q,c in polys:
        n=np.cross(q[1]-q[0],q[2]-q[0]); nn=np.linalg.norm(n)
        if nn<1e-12: continue
        k=.42+.58*abs(n@LIGHT/nn); col=np.clip(np.array(c)*k*255,0,255)
        for i in range(1,len(q)-1): tris.append(np.array([q[0],q[i],q[i+1]])); cols.append(col)
    if not tris: return Image.new('RGB',(px,px),bg)
    T=np.array(tris); C=np.array(cols)
    flat=T.reshape(-1,3); X,Y,Z=f(flat); X=X.reshape(-1,3); Y=Y.reshape(-1,3); Z=Z.reshape(-1,3)
    if bounds is None: bounds=(X.min(),X.max(),Y.min(),Y.max())
    x0,x1,y0,y1=bounds; span=max(x1-x0,y1-y0)*1.06+1e-6
    W=px; H=max(60,int(px*(y1-y0)*1.06/span)+20) if (y1-y0)<(x1-x0) else px
    s=(W-20)/max(x1-x0,1e-6) if (x1-x0)>=(y1-y0) else (H-20)/max(y1-y0,1e-6)
    W=int((x1-x0)*s)+20; H=int((y1-y0)*s)+20
    PX=(X-x0)*s+10; PY=H-((Y-y0)*s+10)
    zb=np.full((H,W),np.inf); img=np.zeros((H,W,3))+np.array(bg)
    for i in range(len(T)):
        xs,ys,zs=PX[i],PY[i],Z[i]
        bx0,bx1=max(0,int(xs.min())),min(W-1,int(math.ceil(xs.max()))); by0,by1=max(0,int(ys.min())),min(H-1,int(math.ceil(ys.max())))
        if bx1<bx0 or by1<by0: continue
        gx,gy=np.meshgrid(np.arange(bx0,bx1+1)+.5,np.arange(by0,by1+1)+.5)
        d=(ys[1]-ys[2])*(xs[0]-xs[2])+(xs[2]-xs[1])*(ys[0]-ys[2])
        if abs(d)<1e-12:
            continue
        l0=((ys[1]-ys[2])*(gx-xs[2])+(xs[2]-xs[1])*(gy-ys[2]))/d
        l1=((ys[2]-ys[0])*(gx-xs[2])+(xs[0]-xs[2])*(gy-ys[2]))/d
        l2=1-l0-l1; m=(l0>=-1e-3)&(l1>=-1e-3)&(l2>=-1e-3)
        if not m.any():
            cx,cy=int(xs.mean()),int(ys.mean())
            if 0<=cx<W and 0<=cy<H:
                zz=zs.mean()
                if zz<zb[cy,cx]: zb[cy,cx]=zz; img[cy,cx]=C[i]
            continue
        z=l0*zs[0]+l1*zs[1]+l2*zs[2]; sub=zb[by0:by1+1,bx0:bx1+1]; w=m&(z<sub)
        sub[w]=z[w]; img[by0:by1+1,bx0:bx1+1][w]=C[i]
    # contours légers par discontinuité de profondeur
    zf=np.where(np.isinf(zb),1e9,zb); edge=np.zeros((H,W),bool)
    edge[:-1]|=np.abs(np.diff(zf,axis=0))>0.6; edge[:,:-1]|=np.abs(np.diff(zf,axis=1))>0.6
    img[edge]=img[edge]*0.55
    return Image.fromarray(img.astype('uint8'))
def render(path, png, views=('front','side','top','persp'), px=520, title=None, skip=None, az=35, extra=None):
    P=load(path, skip)
    if extra: P+=extra
    ims=[(v,render_view(P,v,px,az)) for v in views]
    W=sum(i.size[0] for _,i in ims)+10*(len(ims)+1); H=max(i.size[1] for _,i in ims)+(52 if title else 30)
    S=Image.new('RGB',(W,H),'white'); d=ImageDraw.Draw(S); x=10; top=(28 if title else 4)
    if title: d.text((10,6),title,fill='black')
    for v,i in ims:
        S.paste(i,(x,top+20)); d.text((x+4,top+4),TITLES[v],fill=(60,60,60)); x+=i.size[0]+10
    S.save(png); return png
if __name__=='__main__': render(sys.argv[1],sys.argv[2])
