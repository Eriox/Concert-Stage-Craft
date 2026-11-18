import json,sys,io,base64,math,numpy as np,matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
from PIL import Image
sys.path.insert(0,'/home/claude/v3/tools'); from lib3 import element_polys
FACES=('north','east','south','west','up','down')
def load(path, skip=None):
    d=json.load(open(path)); texs=[]
    for t in d['textures']:
        im=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1]))).convert('RGB')
        texs.append((np.array(im).astype(float), t.get('uv_width',im.size[0]), t.get('uv_height',im.size[1])))
    def col(tex,us,vs):
        a,uw,vh=texs[tex]; H,W=a.shape[:2]
        x0,x1=int(max(0,min(us)*W/uw)),int(min(W,max(us)*W/uw)+1); y0,y1=int(max(0,min(vs)*H/vh)),int(min(H,max(vs)*H/vh)+1)
        return a[y0:y1,x0:x1].reshape(-1,3).mean(0)/255
    out=[]
    for e in d['elements']:
        if skip and skip(e): continue
        P=element_polys(e)
        if e['type']=='mesh':
            fs=list(e['faces'].values())
            for q,f in zip(P,[f for f in fs if len(f['vertices'])>=3]):
                uv=np.array(list(f['uv'].values())) if f.get('uv') else np.zeros((1,2))
                t=f.get('texture'); c=col(t,uv[:,0],uv[:,1]) if t is not None and texs else np.array([.6,.6,.6])
                out.append((q,c))
        elif P:
            order=['down','up','north','south','west','east']
            for q,fn in zip(P,order):
                f=e['faces'].get(fn); 
                if not f or f.get('texture') is None: c=np.array([.6,.6,.6])
                else: u=f['uv']; c=col(f['texture'],[u[0],u[2]],[u[1],u[3]])
                out.append((q,c))
    return out
def render(path, png, title=None, views=('front','side','top','persp'), skip=None, dpi=80, size=5, persp_az=35):
    P0=load(path, skip); P=[]
    for q,c in P0:
        if len(q)==4:
            n=int(min(12,max(1,math.ceil(np.linalg.norm(q[1]-q[0])/4)))); m=int(min(12,max(1,math.ceil(np.linalg.norm(q[3]-q[0])/4))))
            if n*m>1:
                for i in range(n):
                    for j in range(m):
                        def B(s,t): return (1-s)*(1-t)*q[0]+s*(1-t)*q[1]+s*t*q[2]+(1-s)*t*q[3]
                        P.append((np.array([B(i/n,j/m),B((i+1)/n,j/m),B((i+1)/n,(j+1)/m),B(i/n,(j+1)/m)]),c))
                continue
        P.append((q,c))
    L=np.array([.35,.85,.4]); L/=np.linalg.norm(L)
    def shade(q,c):
        n=np.cross(q[1]-q[0],q[2]-q[0]); nn=np.linalg.norm(n)
        k=.45+.55*abs(n@L/nn) if nn>1e-9 else .7
        return np.clip(c*k,0,1)
    az=math.radians(persp_az); el=math.radians(28)
    V={'front':('face (XY)',lambda q:q[:,[0,1]],lambda q:q[:,2].mean()),
       'side':('profil (ZY)',lambda q:q[:,[2,1]]*[-1,1],lambda q:q[:,0].mean()),
       'top':('dessus (XZ)',lambda q:q[:,[0,2]]*[1,-1],lambda q:q[:,1].mean()),
       'persp':('perspective',lambda q:np.c_[q[:,0]*math.cos(az)-q[:,2]*math.sin(az), q[:,1]*math.cos(el)+(q[:,0]*math.sin(az)+q[:,2]*math.cos(az))*math.sin(el)],
                lambda q:float(-(q[:,0]*math.sin(az)*math.cos(el)-q[:,1]*math.sin(el)+q[:,2]*math.cos(az)*math.cos(el)).mean()))}
    fig,ax=plt.subplots(1,len(views),figsize=(size*len(views),size))
    ax=np.atleast_1d(ax)
    for a,v in zip(ax,views):
        t,f,dp=V[v]; order=sorted(P,key=lambda p:dp(p[0]))
        polys=[f(q) for q,_ in order]
        a.add_collection(PolyCollection(polys,facecolors=[shade(q,c) for q,c in order],edgecolors='none'))
        pts=np.vstack(polys); a.set_xlim(pts[:,0].min()-1,pts[:,0].max()+1); a.set_ylim(pts[:,1].min()-1,pts[:,1].max()+1)
        a.set_aspect('equal'); a.set_title(t,fontsize=10); a.set_facecolor('#e4e8ee'); a.tick_params(labelsize=7)
    if title: fig.suptitle(title,fontsize=12)
    plt.tight_layout(); plt.savefig(png,dpi=dpi); plt.close()
if __name__=='__main__': render(sys.argv[1],sys.argv[2])
