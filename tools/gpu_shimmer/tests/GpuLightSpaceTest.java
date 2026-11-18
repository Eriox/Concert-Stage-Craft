import com.concertstage.client.GpuLightSpace;
import org.joml.*;
import java.util.Random;
public class GpuLightSpaceTest {
 public static void main(String[] args) {
  Random r=new Random(14); int checks=0;
  for(int i=0;i<10000;i++) {
   Matrix4f original=new Matrix4f().rotateXYZ(r.nextFloat()*6,r.nextFloat()*6,r.nextFloat()*6)
      .translate(r.nextFloat()*200-100,r.nextFloat()*200-100,r.nextFloat()*200-100);
   Matrix4f view=new Matrix4f(original); Vector3f off=new Vector3f();GpuLightSpace.split(view,off);
   Vector3f vertex=new Vector3f(r.nextFloat()*4,r.nextFloat()*4,r.nextFloat()*4);
   Vector3f before=original.transformPosition(new Vector3f(vertex));
   Vector3f after=view.transformPosition(new Vector3f(vertex).add(off));
   if(before.distance(after)>.0001f) throw new AssertionError("Projection changed: "+before.distance(after));
   if(view.m30()!=0 || view.m31()!=0 || view.m32()!=0)throw new AssertionError("translation remains");
   checks+=2;
  }
  Vector3f world=new Vector3f(-328,210,-321),local=new Vector3f(.5f,5,.5f);
  for(int distance:new int[]{1,8,16,32,64,128,256}) {
   Vector3f camera=new Vector3f(world).add(distance,3,distance);
   Matrix4f mv=new Matrix4f().rotateXYZ(.2f,1.3f,0).translate(new Vector3f(world).sub(camera));
   Vector3f offset=new Vector3f();GpuLightSpace.split(mv,offset);
   Vector3f lightPosition=new Vector3f(local).add(offset).add(camera);
   if(lightPosition.distance(new Vector3f(world).add(local))>.0002f)throw new AssertionError("Camera changes light position");
   checks++;
  }
  System.out.println(checks+" mathematical checks passed; actual JOML 1.10.5");
 }
}
