package com.concertstage.stage;
import java.util.*;
/** Continuous positions for 30 cm stair pitch and partial deck heights. */
public final class StagePose{
 private StagePose(){}
 public static double[] rotate(double x,double y,double z,double angle){double r=Math.toRadians(angle),c=Math.cos(r),s=Math.sin(r);return new double[]{x*c-z*s,y,x*s+z*c};}
 public static double[] point(double[] p,double x,double y,double z,double a){double[] r=rotate(p[0],p[1],p[2],a);r[0]+=x;r[1]+=y;r[2]+=z;return r;}
 public static double[] box(double[] b,double x,double y,double z,double a){double[] lo={1e30,1e30,1e30},hi={-1e30,-1e30,-1e30};for(int k=0;k<8;k++){double[] p=point(new double[]{b[(k&1)==0?0:3],b[(k&2)==0?1:4],b[(k&4)==0?2:5]},x,y,z,a);for(int i=0;i<3;i++){lo[i]=Math.min(lo[i],p[i]);hi[i]=Math.max(hi[i],p[i]);}}return new double[]{lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]};}
 public static float[] quad(float[] q,double x,double y,double z,double a){float[] out=q.clone();for(int k=0;k<32;k+=8){double[] p=point(new double[]{q[k],q[k+1],q[k+2]},x,y,z,a),n=rotate(q[k+5],q[k+6],q[k+7],a);for(int i=0;i<3;i++){out[k+i]=(float)p[i];out[k+5+i]=(float)n[i];}}return out;}
 public static List<float[]> clipped(float[] in,double[] limits,double[] bounds){float[] q=in.clone();for(int a=0;a<3;a++){boolean lower=true;for(int v=0;v<32;v+=8){if(Math.abs(q[v+a]-limits[a])<1e-6)q[v+a]=(float)limits[a];else if(Math.abs(q[v+a]-limits[a+3])<1e-6)q[v+a]=(float)limits[a+3];lower&=q[v+a]==(float)limits[a];}if(lower&&bounds[a]<limits[a]-1e-6)return List.of();}return StageMath.clipQuad(q,limits);}
 public static int steps(double h){int n=(int)Math.round(h/.2);return n>=1&&n<=40&&Math.abs(n*.2-h)<.002?n:0;}
 public static double[] stairOrigin(double x,double z,double y,int n,double a){double[] d=rotate(.5,0,n*.3,a);return new double[]{x-d[0],y,z-d[2]};}
 public static Map<String,Integer> refunds(String str){Map<String,Integer> out=new LinkedHashMap<>();if(str==null||str.length()>2048)return out;for(String t:str.split(";")){String[] p=t.split("=");if(p.length!=2||!p[0].matches("[a-z0-9_]+"))continue;try{int n=Integer.parseInt(p[1]);if(n>0&&n<=64)out.merge(p[0],n,Integer::sum);}catch(NumberFormatException ignored){}}return out;}
 public static String refundString(Map<String,Integer> m){List<String> out=new ArrayList<>();for(var e:m.entrySet())if(e.getValue()>0)out.add(e.getKey()+"="+e.getValue());return String.join(";",out);}
}
