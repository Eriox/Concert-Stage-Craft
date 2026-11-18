package com.concertstage.stage;

import java.util.ArrayList;
import java.util.List;

/** Shared, Minecraft-independent transforms for chunk models, attachments and collision. */
public final class StageMath {
    private StageMath() {}
    public static int index(int sx,int sz,int x,int y,int z) { return x+sx*(z+sz*y); }
    public static int[] cell(int sx,int sz,int part) { return new int[]{part%sx,part/(sx*sz),(part/sx)%sz}; }
    public static int[] rotateOffset(int x,int y,int z,int turns) {
        for(int k=0;k<Math.floorMod(turns,4);k++){int t=x;x=-z;z=t;}return new int[]{x,y,z};
    }
    public static double[] rotate(double x,double y,double z,int turns) {
        for(int k=0;k<Math.floorMod(turns,4);k++){double t=x;x=-z;z=t;}return new double[]{x,y,z};
    }
    public static double[] cellBox(double[] b,int turns) {
        double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};double[] hi={-lo[0],-lo[1],-lo[2]};
        for(int k=0;k<8;k++){double[] p=rotate(b[(k&1)==0?0:3]-.5,b[(k&2)==0?1:4],b[(k&4)==0?2:5]-.5,turns);p[0]+=.5;p[2]+=.5;for(int a=0;a<3;a++){lo[a]=Math.min(lo[a],p[a]);hi[a]=Math.max(hi[a],p[a]);}}
        return new double[]{lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]};
    }
    public static double[] transformBox(double[] b,double x,double y,double z,int turns) {
        double[] r=cellBox(b,turns),origin=rotate(-.5,0,-.5,turns);
        // cellBox rotates around (.5,.5); subtract that offset to rotate around zero.
        double dx=x-.5-origin[0],dz=z-.5-origin[2];r[0]+=dx;r[3]+=dx;r[1]+=y;r[4]+=y;r[2]+=dz;r[5]+=dz;return r;
    }
    public static double[] intersect(double[] b,double[] limits) {
        double[] r=new double[6];for(int a=0;a<3;a++){r[a]=Math.max(b[a],limits[a]);r[a+3]=Math.min(b[a+3],limits[a+3]);if(r[a+3]-r[a]<1e-8)return null;}return r;
    }
    public static boolean overlaps(double[] a,double[] b,double epsilon) {
        for(int i=0;i<3;i++)if(Math.min(a[i+3],b[i+3])-Math.max(a[i],b[i])<=epsilon)return false;return true;
    }
    public static float[] transformQuad(float[] q,double x,double y,double z,int turns) {
        float[] out=q.clone();for(int j=0;j<32;j+=8){double[] p=rotate(q[j],q[j+1],q[j+2],turns),n=rotate(q[j+5],q[j+6],q[j+7],turns);out[j]=(float)(p[0]+x);out[j+1]=(float)(p[1]+y);out[j+2]=(float)(p[2]+z);for(int a=0;a<3;a++)out[j+5+a]=(float)n[a];}return out;
    }
    private static List<double[]> plane(List<double[]> in,int axis,double value,boolean above) {
        List<double[]> out=new ArrayList<>();if(in.isEmpty())return out;double[] prev=in.get(in.size()-1);boolean was=above?prev[axis]>=value:prev[axis]<=value;
        for(double[] p:in){boolean inside=above?p[axis]>=value:p[axis]<=value;if(was!=inside){double t=(value-prev[axis])/(p[axis]-prev[axis]);double[] v=new double[8];for(int i=0;i<8;i++)v[i]=prev[i]+t*(p[i]-prev[i]);out.add(v);}if(inside)out.add(p);prev=p;was=inside;}return out;
    }
    public static List<float[]> clipQuad(float[] q,double[] bounds) {
        List<double[]> p=new ArrayList<>();for(int j=0;j<32;j+=8){double[] v=new double[8];for(int i=0;i<8;i++)v[i]=q[j+i];p.add(v);}
        for(int a=0;a<3;a++){if(Double.isFinite(bounds[a]))p=plane(p,a,bounds[a],true);if(Double.isFinite(bounds[a+3]))p=plane(p,a,bounds[a+3],false);}
        if(p.size()==4){float[] v=new float[32];int n=0;for(double[] a:p)for(double x:a)v[n++]=(float)x;return List.of(v);}
        List<float[]> out=new ArrayList<>();for(int j=1;j+1<p.size();j++){float[] v=new float[32];int k=0;for(int idx:new int[]{0,j,j+1,j+1})for(double x:p.get(idx))v[k++]=(float)x;out.add(v);}return out;
    }
}
