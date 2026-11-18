#!/usr/bin/env python3
"""Real production interpolation and chain path tests; no Minecraft world/graphics claimed."""
from pathlib import Path
import subprocess,tempfile,os,shutil
root=Path(__file__).resolve().parents[1];source=(root/'common/src/main/java/com/concertstage/block/TowerBlockEntity.java').read_text()
def method(signature):
 start=source.index(signature);b=source.index('{',start);depth=1;i=b+1
 while depth:depth+=(source[i]=='{')-(source[i]=='}');i+=1
 return source[start:i]
harness='''import com.concertstage.client.TowerRiggingMath;import java.util.*;
public class RiggingMathCheck {
static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
static class Lift {static final int UNITS=20;int units,startUnits,targetUnits;boolean moving=true;long startTick=100;
'''+method('public int unitsAt(long now)')+'\n'+method('public double liftBlocks(long now, float partial)')+'''}
public static void main(String[]args){
for(int h:new int[]{10,14,18,22})for(int cells:new int[]{1,2}){
 int max=h-2-cells;double total=TowerRiggingMath.visibleChain(h,cells,0)+TowerRiggingMath.storedChain(0);
 for(boolean down:new boolean[]{false,true}){
 Lift lift=new Lift();lift.startUnits=down?max*20:0;lift.targetUnits=down?0:max*20;
 for(int t=0;t<=max*20+20;t++)for(float partial:new float[]{0,.25f,.5f,.99f}){
 double value=lift.liftBlocks(100+t,partial);check(value>=0&&value<=max,"old course limits");
 check(Math.abs(TowerRiggingMath.visibleChain(h,cells,value)+TowerRiggingMath.storedChain(value)-total)<1e-8,"chain conservation");
 check(TowerRiggingMath.head(h)-TowerRiggingMath.eye(cells,value)>=12.8-1e-8,"head clearance");
 check(1+value+cells<=h-1+1e-8,"sleeve below head");check(TowerRiggingMath.phase(value)>=0&&TowerRiggingMath.phase(value)<2*TowerRiggingMath.PITCH,"two-link pattern");
 check(Math.abs(TowerRiggingMath.wheelDegrees(value,1.5)/180*Math.PI*1.5-value*16)<1e-8,"wheel and right load strand move upward together");
 }
 lift.moving=false;lift.units=max*20;check(lift.liftBlocks(10000,.5f)==max,"stopped position preserved");
 }
 for(double value:new double[]{0,.123,max*.5,max})for(double phase:new double[]{-TowerRiggingMath.phase(value),TowerRiggingMath.phase(value),Math.PI*TowerRiggingMath.HEAD_RADIUS-TowerRiggingMath.phase(value)}){
 double head=TowerRiggingMath.head(h);Set<Integer> seen=new HashSet<>();
 for(int cell=0;cell<h;cell++)for(int k=TowerRiggingMath.firstLink(cell,head,phase);k<=TowerRiggingMath.lastLink(cell,head,phase);k++){
 check(seen.add(k),"one renderer owner per link");double y=head+phase-k*TowerRiggingMath.PITCH;check(y>=cell*16-1e-8&&y<(cell+1)*16+1e-8,"correct cell ownership");
 }
 }
}
for(double d:new double[]{.1,.2,.749,.751,1.499,1.501})check(Math.abs(TowerRiggingMath.phase(d/16)-TowerRiggingMath.phase((d+2*TowerRiggingMath.PITCH)/16))<1e-8,"phase parity continuity");
System.out.println(checks+" real lift/rigging math assertions passed");}}
'''
with tempfile.TemporaryDirectory()as td:
 p=Path(td);(p/'RiggingMathCheck.java').write_text(harness);jdk=Path(os.environ['JAVA_HOME'])/'bin'if os.environ.get('JAVA_HOME')else None;javac=str(jdk/'javac')if jdk else shutil.which('javac');java=str(jdk/'java')if jdk else shutil.which('java')
 subprocess.run([javac,'-d',td,str(root/'common/src/main/java/com/concertstage/client/TowerRiggingMath.java'),str(p/'RiggingMathCheck.java')],check=True);subprocess.run([java,'-cp',td,'RiggingMathCheck'],check=True)
