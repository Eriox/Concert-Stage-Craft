import org.objectweb.asm.*;
import java.nio.file.*;
public final class PatchGpuMesh implements Opcodes {
 static final String OWNER="com/concertstage/client/GpuMesh",SHADER="net/minecraft/client/renderer/ShaderInstance",UNIFORM="com/mojang/blaze3d/shaders/Uniform",MATRIX="org/joml/Matrix4f",VECTOR="org/joml/Vector3f";
 static final String CHUNK="f_173320_",SET="m_5889_",UPLOAD="m_85633_";
 static int poses,clears;
 public static void bridge(ClassVisitor cw) {
  MethodVisitor m=cw.visitMethod(ACC_PRIVATE|ACC_STATIC,"lightSpace","(L"+MATRIX+";L"+SHADER+";)L"+MATRIX+";",null,null);m.visitCode();
  m.visitVarInsn(ALOAD,1);m.visitFieldInsn(GETFIELD,SHADER,CHUNK,"L"+UNIFORM+";");Label body=new Label();m.visitJumpInsn(IFNONNULL,body);m.visitVarInsn(ALOAD,0);m.visitInsn(ARETURN);
  m.visitLabel(body);m.visitFrame(F_SAME,0,null,0,null);
  m.visitTypeInsn(NEW,VECTOR);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,VECTOR,"<init>","()V",false);m.visitVarInsn(ASTORE,2);
  m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,2);m.visitMethodInsn(INVOKESTATIC,"com/concertstage/client/GpuLightSpace","split","(L"+MATRIX+";L"+VECTOR+";)V",false);
  m.visitVarInsn(ALOAD,1);m.visitFieldInsn(GETFIELD,SHADER,CHUNK,"L"+UNIFORM+";");
  for(String axis:new String[]{"x","y","z"}){m.visitVarInsn(ALOAD,2);m.visitFieldInsn(GETFIELD,VECTOR,axis,"F");}
  m.visitMethodInsn(INVOKEVIRTUAL,UNIFORM,SET,"(FFF)V",false);
  m.visitVarInsn(ALOAD,1);m.visitFieldInsn(GETFIELD,SHADER,CHUNK,"L"+UNIFORM+";");m.visitMethodInsn(INVOKEVIRTUAL,UNIFORM,UPLOAD,"()V",false);
  m.visitVarInsn(ALOAD,0);m.visitInsn(ARETURN);m.visitMaxs(4,3);m.visitEnd();
  m=cw.visitMethod(ACC_PRIVATE|ACC_STATIC,"clearLightSpace","(L"+SHADER+";)V",null,null);m.visitCode();
  m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,SHADER,CHUNK,"L"+UNIFORM+";");Label clear=new Label();m.visitJumpInsn(IFNONNULL,clear);m.visitInsn(RETURN);m.visitLabel(clear);m.visitFrame(F_SAME,0,null,0,null);
  m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,SHADER,CHUNK,"L"+UNIFORM+";");m.visitInsn(FCONST_0);m.visitInsn(FCONST_0);m.visitInsn(FCONST_0);m.visitMethodInsn(INVOKEVIRTUAL,UNIFORM,SET,"(FFF)V",false);
  m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,SHADER,CHUNK,"L"+UNIFORM+";");m.visitMethodInsn(INVOKEVIRTUAL,UNIFORM,UPLOAD,"()V",false);m.visitInsn(RETURN);m.visitMaxs(4,1);m.visitEnd();
 }
 public static void main(String[]args)throws Exception {
  ClassReader cr=new ClassReader(Files.readAllBytes(Path.of(args[0])));ClassWriter cw=new ClassWriter(cr,0);
  cr.accept(new ClassVisitor(ASM9,cw){
   public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
    MethodVisitor target=super.visitMethod(access,name,desc,sig,ex);
    if(!name.equals("flush"))return target;
    return new MethodVisitor(ASM9,target){
     public void visitMethodInsn(int op,String own,String nam,String des,boolean itf){
      if(own.equals(SHADER)&&nam.equals("m_173362_")) {super.visitInsn(DUP);super.visitMethodInsn(INVOKESTATIC,OWNER,"clearLightSpace","(L"+SHADER+";)V",false);clears++;}
      super.visitMethodInsn(op,own,nam,des,itf);
      if(own.equals(MATRIX)&&nam.equals("mul")&&des.equals("(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;")){super.visitVarInsn(ALOAD,9);super.visitMethodInsn(INVOKESTATIC,OWNER,"lightSpace","(L"+MATRIX+";L"+SHADER+";)L"+MATRIX+";",false);poses++;}
     }
    };
   }
   public void visitEnd(){bridge(this.cv);super.visitEnd();}
  },0);
  if(poses!=1||clears!=1)throw new AssertionError("Unexpected binary, refusing patch: "+poses+"/"+clears);
  Files.write(Path.of(args[1]),cw.toByteArray());
  ClassWriter test=new ClassWriter(0);test.visit(V17,ACC_PUBLIC,"BridgeTarget",null,"java/lang/Object",null);bridge(test);test.visitEnd();Files.write(Path.of(args[2]),test.toByteArray());
  System.out.println("Patched exactly one pose and one uniform reset; existing stack frames retained.");
 }
}
