import json,struct,numpy as np
CT={5126:np.float32,5123:np.uint16,5125:np.uint32,5121:np.uint8}
NC={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def load(f):
    d=open(f,'rb').read(); l=struct.unpack('<I',d[12:16])[0]; j=json.loads(d[20:20+l])
    o=20+l; bl=struct.unpack('<I',d[o:o+4])[0]; binc=d[o+8:o+8+bl]
    def acc(i):
        a=j['accessors'][i]; bv=j['bufferViews'][a['bufferView']]; off=bv.get('byteOffset',0)+a.get('byteOffset',0)
        n=NC[a['type']]; dt=np.dtype(CT[a['componentType']]); cnt=a['count']; stride=bv.get('byteStride',0)
        if stride and stride!=n*dt.itemsize:
            arr=np.array([np.frombuffer(binc,dt,n,off+k*stride) for k in range(cnt)])
        else: arr=np.frombuffer(binc,dt,cnt*n,off).reshape(cnt,n) if n>1 else np.frombuffer(binc,dt,cnt,off)
        if a.get('normalized'): arr=arr.astype(np.float32)/np.iinfo(dt).max
        return arr.astype(np.float64) if dt==np.float32 else arr
    def image(i):
        im=j['images'][i]; bv=j['bufferViews'][im['bufferView']]; off=bv.get('byteOffset',0); return binc[off:off+bv['byteLength']]
    return j,acc,image
def mat4(n):
    if 'matrix' in n: return np.array(n['matrix']).reshape(4,4).T
    T=np.eye(4); T[:3,3]=n.get('translation',[0,0,0])
    x,y,z,w=n.get('rotation',[0,0,0,1])
    R=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
    S=np.diag(n.get('scale',[1,1,1])); M=np.eye(4); M[:3,:3]=R@S; return T@M
def walk(j):
    out=[]
    def rec(i,P):
        n=j['nodes'][i]; M=P@mat4(n); out.append((i,n,M))
        for c in n.get('children',[]): rec(c,M)
    for s in j['scenes'][j.get('scene',0)]['nodes']: rec(s,np.eye(4))
    return out
