#!/usr/bin/env python3
"""Compile and execute the actual lift truss predicate using minimal Block/registry doubles.
This checks classification, not world movement. Run Forge GameTests for capture/arrival.
Requires a JDK; java com.sun.tools.javac.Main supports installations without a javac launcher.
"""
from pathlib import Path
import re, subprocess, sys, tempfile
ROOT = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT/'common/src/main/java/com/concertstage/lift/LiftControl.java'
s = source.read_text()
def method(name):
    start=s.index('    private static boolean '+name+'('); opening=s.index('{',start); depth=1; end=opening+1
    while depth:
        depth += (s[end]=='{')-(s[end]=='}');end+=1
    return s[start:end]
methods='\n'.join(method(n) for n in ['isTruss','isSmallTruss'])
content=(ROOT/'common/src/main/java/com/concertstage/StageContent.java').read_text()
registered=sorted(set(re.findall(r'public static final Block (TRUSS_\w+)\s*=',content)))
refs=sorted(set(re.findall(r'StageContent\.(\w+)',methods))|set(registered))
fields='\n'.join(f'static final Block {n}=new Block("{n}");' for n in refs)
checks='\n'.join(f'check({str("BASE" not in n).lower()},StageContent.{n});' for n in registered)
code='''public class LiftPolicyRegression {
record Block(String name) {}
static class StageContent {FIELDS}
METHODS
static int count,errors;
static void check(boolean expected,Block b){count++;if(isTruss(b)!=expected){errors++;System.out.println("FAIL: "+b.name());}}
public static void main(String[] args){CHECKS
check(false,new Block("foreign block"));
check(false,new Block("stage platform"));
System.out.println(count+" classifications; "+errors+" failures");if(errors>0)System.exit(1);
}}
'''.replace('FIELDS',fields).replace('METHODS',methods).replace('CHECKS',checks)
with tempfile.TemporaryDirectory(prefix='csc-lift-policy-') as tmp:
    p=Path(tmp);(p/'LiftPolicyRegression.java').write_text(code)
    subprocess.run(['java','com.sun.tools.javac.Main','-source','17','-target','17','-Xlint:-options','-d',tmp,str(p/'LiftPolicyRegression.java')],check=True)
    sys.exit(subprocess.run(['java','-Xverify:all','-cp',tmp,'LiftPolicyRegression']).returncode)
