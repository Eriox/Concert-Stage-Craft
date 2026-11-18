#!/usr/bin/env python3
"""Parse Java 17 source using the JDK parser; this does not resolve Minecraft/Forge APIs."""
from pathlib import Path
import os,subprocess,tempfile
r=Path(__file__).resolve().parents[1]
java='''import javax.tools.*;import com.sun.source.util.*;import java.io.*;import java.util.*;
public class SyntaxCheck{public static void main(String[]args)throws Exception{JavaCompiler c=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> d=new DiagnosticCollector<>();try(StandardJavaFileManager f=c.getStandardFileManager(d,null,null)){JavacTask t=(JavacTask)c.getTask(null,f,d,List.of("--release","17","-proc:none"),null,f.getJavaFileObjectsFromStrings(Arrays.asList(args)));int count=0;for(var unit:t.parse())count++;boolean ok=true;for(var x:d.getDiagnostics())if(x.getKind()==Diagnostic.Kind.ERROR){System.err.println(x);ok=false;}if(!ok)throw new AssertionError("Java syntax errors");System.out.println(count+" Java sources parsed successfully (syntax only; no Minecraft/Forge API resolution)");}}}'''
with tempfile.TemporaryDirectory()as t:
 p=Path(t);(p/'SyntaxCheck.java').write_text(java);jdk=Path(os.environ['JAVA_HOME'])/'bin';subprocess.run([str(jdk/'javac'),str(p/'SyntaxCheck.java')],check=True);files=[str(f)for loader in['common','forge','fabric']for f in(r/loader/'src/main/java').rglob('*.java')];subprocess.run([str(jdk/'java'),'-cp',str(p),'SyntaxCheck',*files],check=True)
