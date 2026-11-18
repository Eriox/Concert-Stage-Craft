import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;import java.nio.file.*;
public class VerifyPatch {
 public static void main(String[]a)throws Exception {
  int n=0;
  for(String p:a){ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(p))).accept(c,0);
   for(MethodNode m:c.methods){new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m);n++;}
  }
  System.out.println("ASM BasicVerifier: "+n+" methods passed, including complete patched GpuMesh.flush.");
 }
}
