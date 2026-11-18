#!/usr/bin/env python3
"""Exercise the actual syncData/readData/unitsAt method bodies from LiftStructureEntity (1.1.0: movement in the entity data, blocks in
LiftPayload; the legacy block format of readData is still checked with hand-made packets) and the position packing of LiftPayload.

Uses Java stand-ins for NBT, block states and the world, without Minecraft dependencies.
Checks lossless render-data transfer, indexing, retargets, copies and late observers.
Does not compile the full mod, test real Minecraft packets, or validate in-game rendering.
Run: python3 tools/check_lift_sync.py [optional-path-to-LiftStructureEntity.java]
Requires a Java 17 JDK on PATH or in JAVA_HOME.
"""
from pathlib import Path
import subprocess
import os
import sys
import tempfile
import shutil
source_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'common/src/main/java/com/concertstage/lift/LiftStructureEntity.java'
src = source_path.read_text()
payload_src = (source_path.parent / 'LiftPayload.java').read_text()
def method(signature, text=None):
    global src
    if text is not None:
        saved = src; src = text
        try: return method(signature)
        finally: src = saved
    start=src.index(signature)
    brace=src.index('{',start); depth=1; end=brace+1
    while depth:
        depth += (src[end]=='{')-(src[end]=='}'); end+=1
    return src[start:end]
java=r'''
import java.util.*;
public class LiftSyncCheck {
 static class Tag {static final int TAG_COMPOUND=10;}
 static class CompoundTag extends HashMap<String,Object> {
   void putString(String k,String v){put(k,v);}
   void putInt(String k,int v){put(k,v);} int getInt(String k){return (Integer)getOrDefault(k,0);}
   void putLong(String k,long v){put(k,v);} long getLong(String k){return (Long)getOrDefault(k,0L);}
   void putIntArray(String k,int[] v){put(k,v);} int[] getIntArray(String k){return (int[])get(k);}
   boolean contains(String k){return containsKey(k);}
   CompoundTag getCompound(String k){return (CompoundTag)getOrDefault(k,new CompoundTag());}
   ListTag getList(String k,int type){return (ListTag)getOrDefault(k,new ListTag());}
   CompoundTag copy(){CompoundTag c=new CompoundTag();for(var e:entrySet())c.put(e.getKey(),cloneValue(e.getValue()));return c;}
 }
 static Object cloneValue(Object v){return v instanceof CompoundTag c?c.copy():v instanceof ListTag l?l.copy():v instanceof int[] a?a.clone():v;}
 static class ListTag extends ArrayList<CompoundTag> {
   CompoundTag getCompound(int i){return get(i);} ListTag copy(){ListTag l=new ListTag();for(var c:this)l.add(c.copy());return l;}
 }
 record BlockPos(int getX,int getY,int getZ) {
   long asLong(){return ((long)getX&0x3ffffff)<<38|((long)getZ&0x3ffffff)<<12|((long)getY&0xfff);}
   static BlockPos of(long v){return new BlockPos((int)(v>>38),(int)(v<<52>>52),(int)(v<<26>>38));}
 }
 static class StageContent {static final Object ANGLED_HOLDER=new Object();}
 static class EntityBlockMarker {}
 record BlockState(Object getBlock){boolean hasBlockEntity(){return getBlock==StageContent.ANGLED_HOLDER||getBlock instanceof EntityBlockMarker;}}
 static class Block {
   static final Registry BLOCK_STATE_REGISTRY=new Registry();
   static class Registry {
     List<BlockState> states=new ArrayList<>();
     int getId(BlockState s){int i=states.indexOf(s);if(i<0){i=states.size();states.add(s);}return i;}
     BlockState byId(int i){return states.get(i);}
   }
 }
 static class BlockEntity {CompoundTag update=new CompoundTag();CompoundTag getUpdateTag(){return update;}Object getType(){return "fixture:test_lamp";}}
 static class BuiltInRegistries {static final Registry BLOCK_ENTITY_TYPE=new Registry();static class Registry{Object getKey(Object type){return type;}}}
 static class Entry {
   BlockPos rel; BlockState state; CompoundTag nbt; CompoundTag renderNbt;
   Entry(BlockPos r,BlockState s,CompoundTag n){rel=r;state=s;nbt=n;}
''' + method('public void captureRenderData(BlockEntity be)') + r'''
 }
 static class Level {boolean isClientSide;long getGameTime(){return 120;}}
 static class Data {CompoundTag tag;void set(Object k,CompoundTag t){tag=t;}}
 static class Rig {
   List<Entry> entries=new ArrayList<>();BlockPos anchor=new BlockPos(-613,201,77);
   long startTick=100;int startUnits=160,targetUnits=220,baseUnits=160;
   static final Object DATA=new Object();static final int UNITS=20;
   Data entityData=new Data();Level lvl=new Level();double x,y,z;boolean rebuilt;Object relBox;
   Level level(){return lvl;}void rebuildDerived(){rebuilt=true;relBox=new Object();}
   void setPos(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
'''+method('public int unitsAt(long now)')+'\n'+method('private void syncData()')+'\n'+method('private void readData(CompoundTag t)')+r'''
 }
 /** The entity data written before 1.1.0 (blocks inside), as old servers / saves send it. */
 static CompoundTag legacy(Rig r){CompoundTag t=new CompoundTag();int[] arr=new int[r.entries.size()*4];ListTag angled=new ListTag(),rendered=new ListTag();
   for(int i=0;i<r.entries.size();i++){Entry e=r.entries.get(i);arr[4*i]=e.rel.getX();arr[4*i+1]=e.rel.getY();arr[4*i+2]=e.rel.getZ();arr[4*i+3]=Block.BLOCK_STATE_REGISTRY.getId(e.state);
     if(e.state.getBlock()==StageContent.ANGLED_HOLDER&&e.nbt!=null){CompoundTag c=new CompoundTag();c.putInt("I",i);c.put("Pieces",e.nbt.getList("Pieces",10).copy());angled.add(c);}
     if(e.renderNbt!=null){CompoundTag c=new CompoundTag();c.putInt("I",i);c.put("Data",e.renderNbt.copy());rendered.add(c);}}
   t.putIntArray("B",arr);t.put("P",angled);t.put("BE",rendered);t.putLong("A",r.anchor.asLong());t.putLong("S",r.startTick);t.putInt("SU",r.startUnits);t.putInt("T",r.targetUnits);t.putInt("BU",r.baseUnits);return t;}
''' + method('private static int zig(int v)', payload_src) + '\n' + method('private static int unzig(int v)', payload_src) + r'''
 static int checks;
 static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
 static CompoundTag piece(String name,double x,float angle){CompoundTag c=new CompoundTag();c.put("Block",name);c.put("X",x);c.put("Y",0.25);c.put("Z",0.7);c.put("A",angle);return c;}
 public static void main(String[] args) {
   Rig server=new Rig();BlockState ordinary=new BlockState(new Object()),holder=new BlockState(StageContent.ANGLED_HOLDER);
   ListTag pieces=new ListTag();pieces.add(piece("truss_h1_l2",0.5,335));pieces.add(piece("truss_h2_l2",0.9,195));
   CompoundTag nbt=new CompoundTag();nbt.put("Pieces",pieces);nbt.put("PrivateData","do not send");
   server.entries.add(new Entry(new BlockPos(-3,9,4),ordinary,nbt));
   server.entries.add(new Entry(new BlockPos(2,9,-1),holder,nbt));
   CompoundTag second=new CompoundTag();ListTag more=new ListTag();more.add(piece("truss_x30d_l2",0.6,255));second.put("Pieces",more);
   server.entries.add(new Entry(new BlockPos(5,10,2),holder,second));
   // 1.1.0: the entity data carries the movement only
   server.syncData();CompoundTag motion=server.entityData.tag;
   check(!motion.contains("B")&&!motion.contains("P")&&!motion.contains("BE")&&motion.getInt("N")==3,"entity data: movement and block count only");
   Rig watcher=new Rig();watcher.lvl.isClientSide=true;watcher.entries.add(new Entry(new BlockPos(1,2,3),ordinary,null));
   watcher.readData(motion);check(watcher.entries.size()==1&&watcher.anchor.equals(server.anchor)&&watcher.y==202.0,"movement update keeps the blocks received separately");
   for(int target:new int[]{100,200,160}){server.targetUnits=target;server.syncData();watcher.readData(server.entityData.tag);check(watcher.targetUnits==target&&watcher.entries.size()==1,"descending / stop / retarget keep the blocks");}
   server.targetUnits=220;
   Rig early=new Rig();early.lvl.isClientSide=true;early.readData(motion);check(early.entries.isEmpty()&&early.rebuilt&&early.y==202.0,"observer before the blocks arrive: placed, nothing drawn");
   for(int v:new int[]{0,1,-1,63,-64,30000000,-30000000,Integer.MAX_VALUE,Integer.MIN_VALUE})check(unzig(zig(v))==v,"position packing round trip "+v);
   Random rnd=new Random(7);for(int i=0;i<10000;i++){int v=rnd.nextInt();check(unzig(zig(v))==v,"position packing random");}
   check(zig(-1)==1&&zig(1)==2&&zig(-2)==3,"small differences stay small");
   // the legacy block format (entity data of 1.0.x - 1.1.0 previews) is still read
   CompoundTag packet=legacy(server);
   check(packet.getList("P",10).size()==2,"send every angled holder, no ordinary block NBT");
   check(!packet.getList("P",10).getCompound(0).contains("PrivateData"),"only visual data is sent");
   Rig client=new Rig();client.lvl.isClientSide=true;client.readData(packet);
   check(client.entries.size()==3,"all block states transferred");
   check(client.entries.get(0).nbt==null,"ordinary blocks keep compact sync");
   check(client.entries.get(1).nbt.getList("Pieces",10).equals(pieces),"all pieces, types, angles and offsets survive");
   check(client.entries.get(2).nbt.getList("Pieces",10).equals(more),"multiple holders mapped to correct indices");
   check(client.entries.get(1).rel.equals(new BlockPos(2,9,-1)),"relative holder coordinates survive");
   check(client.anchor.equals(server.anchor),"negative anchor coordinates survive");
   check(client.rebuilt&&client.y==202.0,"derived bounds rebuilt and movement position preserved");
   pieces.getCompound(0).put("A",12f);
   check((Float)packet.getList("P",10).getCompound(0).getList("Pieces",10).getCompound(0).get("A")==335f,"packet independent of server save data");
   packet.getList("P",10).getCompound(0).getList("Pieces",10).getCompound(0).put("A",90f);
   check((Float)client.entries.get(1).nbt.getList("Pieces",10).getCompound(0).get("A")==335f,"client data independent of packet");
   for(int target:new int[]{100,200,160}) {
     server.targetUnits=target;server.syncData();client.readData(server.entityData.tag);
     check(client.targetUnits==target&&client.entries.get(1).nbt.getList("Pieces",10).size()==2,"descending / stop / retarget retains pieces");
   }
   Rig late=new Rig();late.lvl.isClientSide=true;late.readData(legacy(server));
   check(late.entries.get(2).nbt.getList("Pieces",10).size()==1,"late observer receives full render payload");
   CompoundTag bad=legacy(server).copy();CompoundTag invalid=new CompoundTag();invalid.putInt("I",99999);bad.getList("P",10).add(invalid);
   CompoundTag wrong=new CompoundTag();wrong.putInt("I",0);wrong.put("Pieces",more);bad.getList("P",10).add(wrong);
   client.readData(bad);check(client.entries.get(0).nbt==null,"bad indices and non-holder entries ignored");
   CompoundTag legacy=bad.copy();legacy.remove("P");client.readData(legacy);check(client.entries.size()==3,"old payload without pieces accepted");
   server.entries.clear();client.readData(legacy(server));check(client.entries.isEmpty(),"empty moving structure accepted");
   Rig fixtureServer=new Rig();BlockState fixture=new BlockState(new EntityBlockMarker());
   Entry captured=new Entry(new BlockPos(0,0,0),fixture,nbt);BlockEntity blockEntity=new BlockEntity();blockEntity.update.putInt("Tilt",70);captured.captureRenderData(blockEntity);
   check(captured.renderNbt.get("id").equals("fixture:test_lamp")&&captured.renderNbt.getInt("Tilt")==70,"capture uses client update tag and registry type");
   check(!captured.renderNbt.contains("PrivateData"),"capture does not send full save");blockEntity.update.putInt("Tilt",10);check(captured.renderNbt.getInt("Tilt")==70,"capture copies client update data");
   Entry noBe=new Entry(new BlockPos(0,0,0),ordinary,null);noBe.captureRenderData(null);check(noBe.renderNbt==null,"null block entity capture is harmless");
   Entry angledEntry=new Entry(new BlockPos(0,0,0),holder,nbt);angledEntry.captureRenderData(blockEntity);check(angledEntry.renderNbt==null,"angled holders retain dedicated piece sync");
   Entry lamp=new Entry(new BlockPos(-2,8,3),fixture,nbt);lamp.renderNbt=new CompoundTag();lamp.renderNbt.put("id","fixture:test_lamp");lamp.renderNbt.put("Pan",45);lamp.renderNbt.put("Tilt",70);
   fixtureServer.entries.add(new Entry(new BlockPos(0,8,0),ordinary,nbt));fixtureServer.entries.add(lamp);
   CompoundTag fixturePacket=legacy(fixtureServer);
   check(fixturePacket.getList("BE",10).size()==1,"one client update tag per rendered block entity");
   check(!fixturePacket.getList("BE",10).getCompound(0).getCompound("Data").contains("PrivateData"),"server save data excluded from client payload");
   Rig fixtureClient=new Rig();fixtureClient.lvl.isClientSide=true;fixtureClient.readData(fixturePacket);
   check(fixtureClient.entries.get(1).renderNbt.equals(lamp.renderNbt),"fixture type and pan/tilt settings transferred");
   check(fixtureClient.entries.get(1).nbt==null,"client render data remains separate from full save");
   lamp.renderNbt.put("Pan",90);check(fixtureClient.entries.get(1).renderNbt.getInt("Pan")==45,"fixture client copy is isolated");
   for(int target:new int[]{100,220,160}){fixtureServer.targetUnits=target;fixtureServer.syncData();fixtureClient.readData(fixtureServer.entityData.tag);check(fixtureClient.entries.get(1).renderNbt.getInt("Tilt")==70,"fixture survives descent, ascent, stop");}
   Rig observer=new Rig();observer.lvl.isClientSide=true;observer.readData(legacy(fixtureServer));check(observer.entries.get(1).renderNbt.getInt("Pan")==90,"late observer receives fixture settings");
   CompoundTag missing=legacy(fixtureServer).copy();missing.remove("BE");fixtureClient.readData(missing);check(fixtureClient.entries.get(1).renderNbt==null,"legacy sync without BER data accepted");
   CompoundTag invalidPacket=legacy(fixtureServer).copy();CompoundTag invalidBe=new CompoundTag();invalidBe.putInt("I",-1);invalidPacket.getList("BE",10).add(invalidBe);CompoundTag nonBe=new CompoundTag();nonBe.putInt("I",0);nonBe.put("Data",lamp.renderNbt);invalidPacket.getList("BE",10).add(nonBe);
   fixtureClient.readData(invalidPacket);check(fixtureClient.entries.get(0).renderNbt==null,"invalid BER indices and ordinary cells ignored");
   System.out.println(checks+" production-method sync checks passed (isolated Java stand-ins, not Minecraft)");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='lift-sync-check-') as tmp:
    root = Path(tmp)
    test_file = root / 'LiftSyncCheck.java'
    test_file.write_text(java)
    jdk = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
    javac = str(jdk / 'javac') if jdk else shutil.which('javac')
    runtime = str(jdk / 'java') if jdk else shutil.which('java')
    if not javac or not runtime:
        sys.exit('Java 17 JDK required (set JAVA_HOME or add javac and java to PATH).')
    subprocess.run([javac, '-d', tmp, str(test_file)], check=True)
    subprocess.run([runtime, '-cp', tmp, 'LiftSyncCheck'], check=True)
