#!/usr/bin/env python3
"""Compile the real MovingBlockEntityRenderer against small Minecraft API stand-ins.
Checks BER invocation, cached instances, relocation, missing renderers and failure isolation.
Does not compile the whole mod or test a Minecraft/OpenGL runtime. Requires JDK 17.
"""
from pathlib import Path
import os,shutil,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
sources={
'com/concertstage/lift/LiftStructureEntity.java':'''package com.concertstage.lift; import net.minecraft.nbt.CompoundTag; public class LiftStructureEntity {public static class Entry {public Object state="fixture-state";public CompoundTag renderNbt;}}''',
'net/minecraft/nbt/CompoundTag.java':'''package net.minecraft.nbt; public class CompoundTag {public int pan=45;public CompoundTag copy(){CompoundTag t=new CompoundTag();t.pan=pan;return t;}}''',
'net/minecraft/core/BlockPos.java':'''package net.minecraft.core; public record BlockPos(int x,int y,int z){}''',
'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level; public class Level {}''',
'net/minecraft/world/level/block/entity/BlockEntity.java':'''package net.minecraft.world.level.block.entity;
import net.minecraft.core.BlockPos;import net.minecraft.world.level.Level;import net.minecraft.nbt.CompoundTag;
public class BlockEntity { public static int created;public static boolean missing;public BlockPos pos;public Level level;public int pan;public Object state;
public static BlockEntity loadStatic(BlockPos p,Object s,CompoundTag n){created++;if(missing)return null;BlockEntity b=new BlockEntity();b.pos=p;b.state=s;b.pan=n.pan;n.pan=-1;return b;}
public BlockPos getBlockPos(){return pos;}public Level getLevel(){return level;}public void setLevel(Level l){level=l;}}
''',
'com/mojang/blaze3d/vertex/PoseStack.java':'''package com.mojang.blaze3d.vertex; public class PoseStack {public int depth;public void pushPose(){depth++;}public void popPose(){depth--;if(depth<0)throw new AssertionError("underflow");}}''',
'net/minecraft/client/renderer/MultiBufferSource.java':'''package net.minecraft.client.renderer;public interface MultiBufferSource{}''',
'net/minecraft/client/renderer/texture/OverlayTexture.java':'''package net.minecraft.client.renderer.texture;public class OverlayTexture{public static final int NO_OVERLAY=655360;}''',
'net/minecraft/client/renderer/blockentity/BlockEntityRenderer.java':'''package net.minecraft.client.renderer.blockentity;import net.minecraft.world.level.block.entity.BlockEntity;import com.mojang.blaze3d.vertex.PoseStack;import net.minecraft.client.renderer.MultiBufferSource;public interface BlockEntityRenderer<T extends BlockEntity>{void render(T b,float p,PoseStack s,MultiBufferSource m,int light,int overlay);}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;import net.minecraft.world.level.block.entity.BlockEntity;public class Minecraft{private static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}public final Dispatcher dispatcher=new Dispatcher();public Dispatcher getBlockEntityRenderDispatcher(){return dispatcher;}public static class Dispatcher{public BlockEntityRenderer<BlockEntity> renderer;@SuppressWarnings("unchecked") public <T extends BlockEntity> BlockEntityRenderer<T> getRenderer(T b){return (BlockEntityRenderer<T>)renderer;}}}''',
'com/mojang/logging/LogUtils.java':'''package com.mojang.logging;public class LogUtils{public static int warnings;public static LogUtils getLogger(){return new LogUtils();}public void warn(String s,Object... args){warnings++;}}''',
'com/concertstage/client/MovingRenderCheck.java':'''package com.concertstage.client;
import com.concertstage.lift.LiftStructureEntity.Entry;import com.mojang.blaze3d.vertex.PoseStack;import com.mojang.logging.LogUtils;import net.minecraft.client.Minecraft;import net.minecraft.core.BlockPos;import net.minecraft.nbt.CompoundTag;import net.minecraft.world.level.Level;import net.minecraft.world.level.block.entity.BlockEntity;
public class MovingRenderCheck{
static int checks,calls;static BlockEntity seen;static float partial;static int light;
static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
public static void main(String[] a){
MovingBlockEntityRenderer r=new MovingBlockEntityRenderer();Entry e=new Entry();Level l=new Level();BlockPos p=new BlockPos(-5,10,3);PoseStack pose=new PoseStack();
Minecraft.getInstance().dispatcher.renderer=(be,part,stack,buff,lit,ov)->{calls++;seen=be;partial=part;light=lit;check(ov==655360,"overlay");check(stack.depth==1,"isolated pose");};
check(!r.render(e,l,p,.5f,pose,null,123),"no data signals static fallback");check(calls==0&&BlockEntity.created==0,"ordinary blocks need no BER instance");
e.renderNbt=new CompoundTag();check(r.render(e,l,p,.5f,pose,null,123),"BER signals successful render to avoid duplicate item model");check(calls==1&&seen.pan==45&&seen.state.equals(e.state),"render captured fixture state and settings");check(seen.getLevel()==l&&seen.getBlockPos().equals(p),"renderer receives world and moving cell");check(partial==.5f&&light==123,"partial tick and lighting forwarded");check(e.renderNbt.pan==45,"loading cannot mutate synced data");check(pose.depth==0,"pose restored after rendering");
BlockEntity first=seen;r.render(e,l,p,.75f,pose,null,456);check(seen==first&&BlockEntity.created==1,"reuse instance within block cell");
for(int y:new int[]{11,12,11,10})r.render(e,l,new BlockPos(-5,y,3),.25f,pose,null,123);check(BlockEntity.created==5&&seen.getBlockPos().equals(p),"ascent and descent update cached block position");
Level other=new Level();r.render(e,other,p,.2f,pose,null,123);check(seen.getLevel()==other&&BlockEntity.created==6,"world changes invalidate instance");
Minecraft.getInstance().dispatcher.renderer=null;int previous=calls;check(!r.render(e,other,p,0,pose,null,0),"missing BER signals fallback");check(calls==previous&&pose.depth==0,"no registered BER is harmless");
Entry missing=new Entry();missing.renderNbt=new CompoundTag();BlockEntity.missing=true;r.render(missing,l,p,0,pose,null,0);check(pose.depth==0,"unknown block entity type is harmless");BlockEntity.missing=false;
Minecraft.getInstance().dispatcher.renderer=(be,part,stack,buff,lit,ov)->{throw new IllegalStateException("requires live world block");};Entry broken=new Entry();broken.renderNbt=new CompoundTag();r.render(broken,l,p,0,pose,null,0);r.render(broken,l,p,0,pose,null,0);check(LogUtils.warnings==1&&pose.depth==0,"incompatible renderer logged once and pose restored");
Minecraft.getInstance().dispatcher.renderer=(be,part,stack,buff,lit,ov)->calls++;previous=calls;r.render(e,other,p,0,pose,null,0);check(calls==previous+1,"one failed attachment does not hide other fixtures");
System.out.println(checks+" real renderer helper checks passed (API stand-ins, not Minecraft)");}}
'''
}
with tempfile.TemporaryDirectory(prefix='moving-render-check-')as tmp:
 dest=Path(tmp)
 for name,body in sources.items():
  f=dest/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
 production=root/'common/src/main/java/com/concertstage/client/MovingBlockEntityRenderer.java';target=dest/'com/concertstage/client/MovingBlockEntityRenderer.java';target.write_text(production.read_text())
 jdk=Path(os.environ['JAVA_HOME'])/'bin'if os.environ.get('JAVA_HOME')else None
 javac=str(jdk/'javac')if jdk else shutil.which('javac');java=str(jdk/'java')if jdk else shutil.which('java')
 if not javac or not java:raise SystemExit('JDK 17 required')
 subprocess.run([javac,'-d',str(dest),*[str(p)for p in dest.rglob('*.java')]],check=True)
 subprocess.run([java,'-cp',str(dest),'com.concertstage.client.MovingRenderCheck'],check=True)
