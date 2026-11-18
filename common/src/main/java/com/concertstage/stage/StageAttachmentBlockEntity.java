package com.concertstage.stage;

import com.concertstage.StageContent;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Unticked, per occupied attachment cell; the base is still drawn by the chunk renderer. */
public final class StageAttachmentBlockEntity extends BlockEntity {
    public record Entry(String token,String key,double x,double y,double z,int turn,double[] clip,String refund,double angle,String support) {
        public Entry(String token,String key,double x,double y,double z,int turn,double[] clip){this(token,key,x,y,z,turn,clip,key+"=1",0,"");}
        public double degrees(){return turn*90.+angle;}
        public CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("Token",token);t.putString("Equipment",key);t.putString("Refund",refund);t.putDouble("Angle",angle);t.putString("Support",support);t.putDouble("X",x);t.putDouble("Y",y);t.putDouble("Z",z);t.putInt("Turn",turn);for(int a=0;a<6;a++)t.putDouble("Clip"+a,clip[a]);return t;}
        public static Entry load(CompoundTag t){double[] b=new double[6];for(int a=0;a<6;a++)b[a]=t.getDouble("Clip"+a);return new Entry(t.getString("Token"),t.getString("Equipment"),t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"),Math.floorMod(t.getInt("Turn"),4),b,t.contains("Refund")?t.getString("Refund"):t.getString("Equipment")+"=1",t.getDouble("Angle"),t.getString("Support"));}
        public List<double[]> boxes(){StageSpec s=StageSpec.find(key);List<double[]> out=new ArrayList<>();if(s==null)return out;
            // a platform turned by the platform protractor: its hundreds of tubes would become big axis-aligned boxes (a solid block
            // that traps players and costs a lot); its collision is its deck, in small tiles following the turned outline
            double deg=degrees();if(Math.abs(deg/90.0-Math.rint(deg/90.0))>1e-6&&StagePlatformAngle.turnable(s)){double[] b=s.raw_bounds;double top=s.walk>0?s.walk:b[4];
                double[][] poly=s.outline();for(double u=b[0];u<b[3]-1e-6;u+=0.25)for(double w=b[2];w<b[5]-1e-6;w+=0.25){if(!StageSpec.inside(poly,Math.min(b[3],u+0.125),Math.min(b[5],w+0.125)))continue;double cu=(u+Math.min(b[3],u+0.25))/2,cw=(w+Math.min(b[5],w+0.25))/2,hu=(Math.min(b[3],u+0.25)-u)/2,hw=(Math.min(b[5],w+0.25)-w)/2;
                    // the square inside the turned tile: neighbours joined edge to edge (StagePlatformJoin) never overlap
                    double h=Math.max(.02,Math.min(hu,hw)/(Math.abs(Math.cos(Math.toRadians(deg)))+Math.abs(Math.sin(Math.toRadians(deg))))-.03);double[] m=StagePose.point(new double[]{cu,0,cw},x,y,z,deg);
                    double[] tile={m[0]-h,y+Math.max(b[1],top-0.12),m[2]-h,m[0]+h,y+top,m[2]+h};
                    double[] c=StageMath.intersect(tile,clip);if(c!=null)out.add(c);}
                return out;}
            // 1.2.0 beta 2b: a railing at an angle (on a turned platform): each tube in short lengths, so its collision follows the tube
            // instead of becoming a wide flat box across the deck
            if(Math.abs(deg/90.0-Math.rint(deg/90.0))>1e-6&&"railing".equals(s.kind)){for(double[] raw:s.raw_boxes){double len=raw[3]-raw[0];int n=Math.max(1,(int)Math.ceil(len/0.125));for(int k=0;k<n;k++){double[] seg={raw[0]+len*k/n,raw[1],raw[2],raw[0]+len*(k+1)/n,raw[4],raw[5]};double[] b=StageMath.intersect(StagePose.box(seg,x,y,z,deg),clip);if(b!=null)out.add(b);}}return out;}
            for(double[] raw:s.raw_boxes){double[] b=StageMath.intersect(StagePose.box(raw,x,y,z,degrees()),clip);if(b!=null)out.add(b);}return out;}
    }
    private volatile List<Entry> entries=List.of();
    private volatile VoxelShape shape;
    private volatile VoxelShape combined;
    private BlockState combinedState;
    private volatile int revision;
    public StageAttachmentBlockEntity(BlockPos pos,BlockState state){super(StageContent.STAGE_ATTACHMENT_ENTITY,pos,state);}
    public List<Entry> entries(){return entries;}
    public int revision(){return revision;}
    public void clearLocal(){entries=List.of();shape=null;combined=null;}
    public void replace(List<Entry> value){entries=List.copyOf(value);shape=null;combined=null;revision++;setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    public VoxelShape shape(){VoxelShape s=shape;if(s!=null)return s;List<double[]> boxes=new ArrayList<>();for(Entry entry:entries)boxes.addAll(entry.boxes());shape=StageCollision.build(boxes);return shape;}
    public VoxelShape withBase(BlockState state,VoxelShape base){VoxelShape cached=combined;if(cached!=null&&combinedState==state)return cached;cached=Shapes.or(base,shape());combinedState=state;combined=cached;return cached;}
    protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);ListTag list=new ListTag();for(Entry e:entries)list.add(e.save());tag.put("StageAttachments",list);}
    /** Read on the client from every update packet, and from the save: a piece refused here is neither drawn nor kept (platforms
     *  turned by the protractor were refused, so they stayed invisible and vanished on reload). */
    public void load(CompoundTag tag){super.load(tag);List<Entry> next=new ArrayList<>();ListTag list=tag.getList("StageAttachments",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(64,list.size());i++){Entry e=Entry.load(list.getCompound(i));if(StageSpec.find(e.key())!=null&&(e.key().startsWith("safety_railings_")||e.key().equals("stage_deck_2x1_without_legs")||StageSpec.find(e.key()).internal||StagePlatformAngle.turnable(StageSpec.find(e.key())))&&Double.isFinite(e.angle())&&Math.abs(e.angle())<=3600&&Math.abs(e.x())<=32&&Math.abs(e.y())<=32&&Math.abs(e.z())<=32&&e.clip()[0]<e.clip()[3]&&e.clip()[1]<e.clip()[4]&&e.clip()[2]<e.clip()[5]&&Double.isFinite(e.x())&&Double.isFinite(e.y())&&Double.isFinite(e.z())&&Arrays.stream(e.clip()).allMatch(Double::isFinite))next.add(e);}entries=List.copyOf(next);shape=null;combined=null;revision++;}
    public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    /** Forge calls it when the block entity is loaded (validation of the pieces it holds); on Fabric the neighbour updates do it. */
    public void onLoad(){if(level!=null&&!level.isClientSide)level.scheduleTick(worldPosition,getBlockState().getBlock(),1);}
    public AABB getRenderBoundingBox(){double top=1;for(Entry e:entries)top=Math.max(top,e.clip()[4]);return new AABB(worldPosition).inflate(.13).expandTowards(0,top-1,0);}
}
