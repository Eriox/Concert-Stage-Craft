#!/usr/bin/env python3
"""Execute real multipart constructor/cache method bodies using small API stand-ins.
Checks deferred construction and unchanged eager behavior; not a Forge runtime benchmark.
"""
from pathlib import Path
import os,subprocess,tempfile
r=Path(__file__).resolve().parents[1];src=(r/'common/src/main/java/com/concertstage/block/MultiPartBlock.java').read_text()
def method(signature):
 a=src.index(signature);b=src.index('{',a);depth=1;e=b+1
 while depth:depth+=(src[e]=='{')-(src[e]=='}');e+=1
 return src[a:e]
ctor=method('    protected MultiPartBlock(').replace('protected MultiPartBlock(', 'StartupSubject(')
methods='\n'.join(method(s)for s in['    private VoxelShape partShape(','    private int index(','    private int mainIndex(','    private int anchorIndex(','    private boolean isVirtual(','    protected static VoxelShape rotate('])
java='''import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
class Properties{}
class Direction{static final Object NORTH=new Object();}
class State{State setValue(Object k,Object v){return this;}}
class Def{State any(){return new State();}}
class Base{Def stateDefinition=new Def();Base(Properties p){}void registerDefaultState(State s){}}
interface Consumer{void accept(double a,double b,double c,double d,double e,double f);}
class VoxelShape{List<double[]> boxes=new ArrayList<>();void forAllBoxes(Consumer c){for(double[] b:boxes)c.accept(b[0],b[1],b[2],b[3],b[4],b[5]);}boolean isEmpty(){return boxes.isEmpty();}}
class Shapes{static VoxelShape empty(){return new VoxelShape();}static VoxelShape box(double...b){var s=empty();s.boxes.add(b);return s;}static VoxelShape or(VoxelShape a,VoxelShape b){var s=empty();s.boxes.addAll(a.boxes);s.boxes.addAll(b.boxes);return s;}}
class StartupSubject extends Base{
static final Object FACING=new Object(),PART=new Object();enum Placement{FRONT,LOOK}interface ShapeProvider{VoxelShape get(int x,int y,int z);}
final int sx,sy,sz,mx,my,mz,ax,ay,az;final Placement placement;final boolean chain,deferredShapes;final VoxelShape[][] shapes;final ShapeProvider shapeProvider;
'''+ctor+methods+'''
static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
public static void main(String[]args)throws Exception{
AtomicInteger calls=new AtomicInteger();ShapeProvider provider=(x,y,z)->{calls.incrementAndGet();return Shapes.box(.1,.2,.3,.8,.7,.9);};
StartupSubject lazy=new StartupSubject(new Properties(),3,3,3,1,1,1,1,0,1,Placement.FRONT,false,provider,true);ok(calls.get()==0,"NO collision work during deferred construction");
var one=lazy.partShape(3,1);ok(calls.get()==1,"only requested cell built");ok(lazy.partShape(3,1)==one&&calls.get()==1,"cached orientation");
for(int turn=0;turn<4;turn++)lazy.partShape(3,turn);ok(calls.get()==1,"rotations reuse authored cell");
ExecutorService threads=Executors.newFixedThreadPool(4);List<Future<?>> jobs=new ArrayList<>();for(int i=0;i<100;i++)jobs.add(threads.submit(()->lazy.partShape(8,2)));for(var job:jobs)job.get();threads.shutdown();ok(calls.get()==2,"concurrent cache builds once");
calls.set(0);StartupSubject eager=new StartupSubject(new Properties(),3,3,3,1,1,1,1,0,1,Placement.FRONT,false,provider,false);ok(calls.get()==27,"existing eager constructor behavior retained");
for(int part=0;part<27;part++)for(int turn=0;turn<4;turn++){VoxelShape l=lazy.partShape(part,turn),e=eager.partShape(part,turn);ok(l.boxes.size()==e.boxes.size(),"same box count");for(int i=0;i<l.boxes.size();i++)for(int axis=0;axis<6;axis++)ok(Math.abs(l.boxes.get(i)[axis]-e.boxes.get(i)[axis])<1e-8,"same rotated shape");}
StartupSubject empty=new StartupSubject(new Properties(),3,3,3,1,1,1,1,0,1,Placement.FRONT,false,(x,y,z)->Shapes.empty(),true);ok(empty.isVirtual(0),"empty cell stays virtual");ok(!empty.isVirtual(empty.mainIndex())&&!empty.isVirtual(empty.anchorIndex()),"main and anchor retained");
System.out.println(checks+" production constructor/cache assertions passed; zero collision-provider calls during deferred construction (API stand-ins)");}}
'''
# Confirm the production stage subclass opts in, without applying this change to truss constructors.
lp=r/'common/src/main/java/com/concertstage/stage/LegacyStageBlock.java'
if lp.exists():legacy=lp.read_text();assert 'properties.dynamicShape()'in legacy and ',true);'in legacy   # supprimé en 1.17.0
with tempfile.TemporaryDirectory()as t:
 d=Path(t);(d/'StartupSubject.java').write_text(java);jdk=Path(os.environ['JAVA_HOME'])/'bin';subprocess.run([str(jdk/'javac'),str(d/'StartupSubject.java')],check=True);subprocess.run([str(jdk/'java'),'-cp',str(d),'StartupSubject'],check=True)
