import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
/** Parse-only Java 17 syntax audit: deliberately does not claim Minecraft type checking. */
public class ParseJavaSyntax {
 public static void main(String[] args)throws Exception {
  var paths=new ArrayList<Path>();
  for(String root:args)try(var stream=Files.walk(Path.of(root))){stream.filter(p->p.toString().endsWith(".java")).forEach(paths::add);}
  var diagnostics=new DiagnosticCollector<JavaFileObject>();var compiler=ToolProvider.getSystemJavaCompiler();
  try(var fm=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)){
   JavacTask task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none","-source","17"),null,fm.getJavaFileObjectsFromPaths(paths));
   for(var tree:task.parse()){}
  }
  long errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
  diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).forEach(System.out::println);
  System.out.println(paths.size()+" Java files parsed in Java 17 syntax mode; "+errors+" syntax errors (no Forge type check).");
  if(errors>0)System.exit(1);
 }
}
