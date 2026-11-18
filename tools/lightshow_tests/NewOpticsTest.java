import com.concertstage.light.core.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Executes new masks in the actual GLSL include on software OpenGL and compares with Java. */
public class NewOpticsTest {
    public static void main(String[] args)throws Exception {
        String harness=System.getProperty("harness");if(harness==null)throw new IllegalArgumentException("-Dharness is required");
        int cases=0,n=64;double worst=0;
        for(FixtureProfile p:List.of(FixtureProfile.PHANTOM_S1,FixtureProfile.PROFILE_P1,FixtureProfile.RAZOR_B1,FixtureProfile.LASERCORE_L1)) {
            for(int mode=0;mode<(p==FixtureProfile.LASERCORE_L1 || p==FixtureProfile.PHANTOM_S1?10:3);mode++) {
                float[] v=new float[Attr.COUNT];for(Attr a:Attr.ALL)v[a.ordinal()]=p.home(a);
                OpticalProfile op=OpticalProfile.of(p,new float[0][],1);float[] u=new float[OpticalProfile.UNIFORM_FLOATS];
                op.uniforms(false,p==FixtureProfile.RAZOR_B1,.61f,10,.8f,u,0);
                if(p==FixtureProfile.PROFILE_P1&&mode>0){v[Attr.BLADE_1.ordinal()]=.6f;v[Attr.BLADE_3.ordinal()]=.3f;v[Attr.FRAME_ROT.ordinal()]=mode==1?.5f:.8f;}
                if(p==FixtureProfile.PHANTOM_S1) {
                    v[Attr.GOBO_2.ordinal()]=(mode%8+1)/8f;v[Attr.GOBO_2_ROT.ordinal()]=.8f;
                    v[Attr.ANIMATION.ordinal()]=mode>=8?.8f:0;
                    v[Attr.PRISM_2.ordinal()]=mode==9?1:0;
                }
                v[Attr.LASER_PATTERN.ordinal()]=(mode%5)/5f;ProfessionalOptics.write(p,v,mode<5?0:2.7,u,0);
                StringBuilder uniforms=new StringBuilder("f TargetSize 64 64\nf ProbeArgs 1 5 0.1 0\n");
                uniforms.append(String.format(Locale.ROOT,"f OptKind %f %f %f %f\nf OptShape %f %f %f %f\n",u[0],u[1],u[2],u[3],u[4],u[5],u[6],u[7]));
                for(int i=0;i<6;i++)uniforms.append(String.format(Locale.ROOT,"f OptRing%d %f %f %f %f\n",i,u[8+i*4],u[9+i*4],u[10+i*4],u[11+i*4]));
                Path dir=Files.createTempDirectory("csc-optics-");Path inf=dir.resolve("uniforms.txt"),out=dir.resolve("out.raw");Files.writeString(inf,uniforms);
                ProcessBuilder pb=new ProcessBuilder(harness,"tools/shader_harness/passthrough.vsh","tools/shader_harness/optics_probe.fsh","common/src/main/resources/assets/minecraft/shaders/include",inf.toString(),"64","64",out.toString()).redirectErrorStream(true);
                pb.environment().put("EGL_PLATFORM","surfaceless");Process proc=pb.start();
                String log=new String(proc.getInputStream().readAllBytes());if(proc.waitFor()!=0)throw new AssertionError(log);
                FloatBuffer pixels=ByteBuffer.wrap(Files.readAllBytes(out)).order(ByteOrder.nativeOrder()).asFloatBuffer();double error=0;
                for(int y=0;y<n;y++)for(int x=0;x<n;x++) {
                    float qx=((x+.5f)/n*2-1)*1.1f,qy=((y+.5f)/n*2-1)*1.1f;
                    float cpu=OpticalProfile.lightAt(u,qx,qy,1,5,.1f),gpu=pixels.get((y*n+x)*4);
                    error=Math.max(error,Math.abs(cpu-gpu)/Math.max(1,Math.abs(cpu)));
                }
                if(error>.001)throw new AssertionError(p.id+" Java/GLSL mismatch "+error);
                worst=Math.max(worst,error);cases++;System.out.println("ok "+p.id+" case "+mode+" CPU/GLSL relative error "+error);
                Files.delete(inf);Files.delete(out);Files.delete(dir);
            }
        }
        System.out.println("passed "+cases+" x 4096 points, failed 0; worst relative error "+worst);
    }
}
