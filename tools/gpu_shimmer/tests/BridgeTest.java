import org.joml.*;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.shaders.Uniform;
public class BridgeTest {
 public static void main(String[]args)throws Exception {
  var c=Class.forName("BridgeTarget");var apply=c.getDeclaredMethod("lightSpace",Matrix4f.class,ShaderInstance.class);apply.setAccessible(true);
  var clear=c.getDeclaredMethod("clearLightSpace",ShaderInstance.class);clear.setAccessible(true);
  var s=new ShaderInstance();var m=new Matrix4f().translate(10,20,30);apply.invoke(null,m,s);
  if(m.m30()!=10)throw new AssertionError("null uniform must preserve original matrix");clear.invoke(null,s);
  s.f_173320_=new Uniform();apply.invoke(null,m,s);var u=s.f_173320_;
  if(u.x!=10||u.y!=20||u.z!=30||u.uploads!=1||m.m30()!=0)throw new AssertionError("offset/upload mismatch");
  clear.invoke(null,s);if(u.x!=0||u.y!=0||u.z!=0||u.uploads!=2)throw new AssertionError("uniform leaked");
  System.out.println("JVM verification and generated uniform bridge: passed (null shader uniform, offset upload, state reset).");
 }
}
