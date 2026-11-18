package com.concertstage.stage;
import com.concertstage.block.MultiPartBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
public final class StageSurfaces{
 private StageSurfaces(){}
 public record Surface(BlockPos main,double x,double y,double z,double width,double depth,int turn){public double[] point(double x,double z){return StagePose.point(new double[]{x,0,z},this.x,this.y,this.z,turn*90.);}public double[] local(Vec3 p){return StagePose.rotate(p.x-x,p.y-y,p.z-z,-turn*90.);}}
 public static Surface at(Level l,BlockPos p){BlockState s=l.getBlockState(p);double walk,w,d;int t;BlockPos main;
 if(s.getBlock()instanceof StageEquipmentBlock b){if(b.spec.walk<=0||b.spec.kind.equals("stairs")||b.spec.kind.equals("railing"))return null;t=StageSpec.turns(s.getValue(StageEquipmentBlock.FACING));main=b.mainPos(s,p);walk=b.spec.walk;w=b.spec.width;d=b.depth(s);}
else return null;double[] v=StagePose.rotate(-.5,0,-.5,t*90.);return new Surface(main,main.getX()+.5+v[0],main.getY()+walk,main.getZ()+.5+v[2],w,d,t);}
 public static boolean topAt(Level l,double x,double y,double z){int ix=(int)Math.floor(x),iz=(int)Math.floor(z),iy=(int)Math.floor(y+.002);for(int i=0;i<2;i++){BlockPos p=new BlockPos(ix,iy-i,iz);if(!l.hasChunkAt(p))return false;boolean[] yes={false};l.getBlockState(p).getCollisionShape(l,p,CollisionContext.empty()).forAllBoxes((a,b,c,d,e,f)->{if(x>=p.getX()+a-.0001&&x<=p.getX()+d+.0001&&z>=p.getZ()+c-.0001&&z<=p.getZ()+f+.0001&&Math.abs(p.getY()+e-y)<.003)yes[0]=true;});if(yes[0])return true;}return false;}
 public static boolean supported(Level l,StageSpec s,double x,double y,double z,double a){double[][] feet=s.feet;if(feet==null||feet.length==0){double[] b=s.raw_bounds;feet=new double[][]{{b[0]+.02,b[1],b[2]+.02},{b[3]-.02,b[1],b[2]+.02},{b[0]+.02,b[1],b[5]-.02},{b[3]-.02,b[1],b[5]-.02}};}for(double[] f:feet){double[] p=StagePose.point(f,x,y,z,a);if(!l.hasChunkAt(BlockPos.containing(p[0],p[1]-.01,p[2])))return true;if(!topAt(l,p[0],p[1],p[2]))return false;}return true;}
 public static boolean clearLanding(Level l,double[] edge,double a){double[] box=StagePose.box(new double[]{-.32,.04,0,.32,1.8,.28},edge[0],edge[1],edge[2],a);for(int x=(int)Math.floor(box[0]);x<=Math.floor(box[3]);x++)for(int y=(int)Math.floor(box[1]);y<=Math.floor(box[4]);y++)for(int z=(int)Math.floor(box[2]);z<=Math.floor(box[5]);z++){BlockPos p=new BlockPos(x,y,z);if(!l.hasChunkAt(p))return false;boolean[] no={false};l.getBlockState(p).getCollisionShape(l,p,CollisionContext.empty()).forAllBoxes((aa,b,c,d,e,f)->{if(StageMath.overlaps(box,new double[]{p.getX()+aa,p.getY()+b,p.getZ()+c,p.getX()+d,p.getY()+e,p.getZ()+f},.003))no[0]=true;});if(no[0])return false;}return true;}
}
