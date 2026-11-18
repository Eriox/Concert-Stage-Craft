package com.concertstage.audio;

import com.concertstage.StageContent;
import com.concertstage.block.ring.RingTrussCellBlock;
import com.concertstage.block.ring.RingTrussCellBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Speaker Array Manager: one block holds a whole set of cabinets of the same family and kind - a Line Array hung under a truss (it grows
 * downwards, each cabinet hinged on the front bottom edge of the one above, at its splay angle) or a ground stack (it grows upwards). The
 * array is turned by {@link #yaw()} and tilted by {@link #tilt()} (CSC Array Tool). Collision: invisible cells around the cabinets (the
 * same cells as the circular trusses). Linked to a CSC Audio Console in an audio group.
 */
public class SpeakerBlockEntity extends BlockEntity {
    public static final int MAX_LINE = 24, MAX_SUB = 8;
    /** Height of the flying frame (bumper) at the top of a hung array. */
    public static final double BUMPER = 0.12;
    public static final double TILT_MIN = -15, TILT_MAX = 15;

    public record Cabinet(UUID id, String model, float splay) {}

    private final List<Cabinet> cabinets = new ArrayList<>();
    private float yaw = 0, tilt = 0, volume = 1;
    private boolean hanging = true;
    private String support = "", supportCabinet = "";
    private BlockPos console;
    /** Where the array was when it was linked: after a lift it tells its console where it went. */
    private BlockPos linkedAt;
    private int group = AudioGroups.MAIN_LEFT;
    private long[] cells = new long[0];
    private int revision;
    /**
     * Rev. 4: a stack standing on another array (AUREX LA on an S218-F stack, a stack adapter on its sub, the C6 pole on the B18P):
     * {@code base} lifts or lowers its bottom from the bottom of its block (top of the sub stack, which is rarely on a block boundary),
     * {@code standsOn} is the sub array, relative to this block (null: on the ground / flown).
     */
    private float base;
    private BlockPos standsOn;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.SPEAKER_ENTITY, pos, state);
    }

    private static final java.util.Set<BlockPos> CLIENT_ARRAYS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static java.util.Set<BlockPos> clientArrays() { return CLIENT_ARRAYS; }
    @Override public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level.isClientSide) CLIENT_ARRAYS.add(worldPosition.immutable());
        else com.concertstage.audio.net.AudioContinuity.add(this);
    }
    @Override public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) CLIENT_ARRAYS.remove(worldPosition);
        else com.concertstage.audio.net.AudioContinuity.remove(this);
    }
    public List<Cabinet> cabinets() { return cabinets; }
    public float yaw() { return yaw; }
    public float tilt() { return tilt; }
    public float volume() { return volume; }
    public boolean hanging() { return hanging; }
    public BlockPos console() { return console; }
    public int group() { return group; }
    public int revision() { return revision; }

    public SpeakerModels.Model firstModel() {
        return cabinets.isEmpty() ? null : SpeakerModels.get(cabinets.get(0).model());
    }

    public double supportDrop() { return hanging && !support.isEmpty() ? .30 : 0; }
    public float base() { return base; }
    /** Rev. 4: the array this one stands on (world position), or null. */
    public BlockPos standsOn() { return standsOn == null ? null : worldPosition.offset(standsOn); }
    /** Rev. 4: height of the support under the first cabinet of a stack (0 without one). */
    public double groundLift() {
        if (hanging || support.isEmpty()) return 0;
        SpeakerFrames.Frame f = SpeakerFrames.get(support);
        return f == null ? BUMPER : f.lift();
    }
    /** Rev. 4: it stands on {@code sub} (world position), its bottom {@code base} blocks from the bottom of its own block. */
    public void standOn(BlockPos sub, float base) {
        this.standsOn = sub == null ? null : sub.subtract(worldPosition);
        this.base = Math.max(-1, Math.min(1, base));
        changed(true);
    }
    /** Rev. 4: top of the cabinets above the bottom of the block (a sub stack: where something standing on it starts). */
    public double stackTop() {
        double top = 0;
        for (Pose p : poses()) top = Math.max(top, p.y() + p.model().height() * Math.cos(Math.toRadians(p.pitch())));
        return top;
    }
    /**
     * Rev. 4: the array standing on this one (a stack on its subs), searched just above its top; null when there is none. A sub with a
     * stack on it keeps its cabinets (nothing added, nothing taken off) so the stack never floats or sinks into it.
     */
    public SpeakerBlockEntity dependent() {
        if (level == null || cabinets.isEmpty()) return null;
        int top = (int) Math.ceil(stackTop()) + 1;
        for (int dy = 1; dy <= top + 1; dy++) {
            BlockPos q = worldPosition.above(dy);
            if (level.getBlockEntity(q) instanceof SpeakerBlockEntity s && worldPosition.equals(s.standsOn())) return s;
        }
        return null;
    }
    public String support() { return support; }
    public SpeakerModels.Model supportModel() {
        if (firstModel() != null) return firstModel();
        SpeakerFrames.Frame f = SpeakerFrames.get(support);
        return f == null ? null : SpeakerModels.get(SpeakerModels.get(supportCabinet) == null ? f.model() : supportCabinet);
    }
    public void initFrame(String id, float yaw) {
        SpeakerFrames.Frame frame = SpeakerFrames.get(id);
        if (frame == null) return;
        cabinets.clear(); support = id; supportCabinet = frame.model(); hanging = frame.flying(); this.yaw = snapYaw(yaw); changed(true);
    }
    public boolean installFrame(String id) {
        SpeakerFrames.Frame frame = SpeakerFrames.get(id);
        SpeakerModels.Model m = firstModel();
        if (frame == null || !support.isEmpty() || frame.flying() != hanging
            || m != null && !frame.accepts(m)) return false;
        support = id; supportCabinet = m == null ? frame.model() : m.id(); revision++;
        if (!canFit()) { support = ""; revision++; return false; }
        changed(true); return true;
    }
    /** Eight corners of the support, in the same coordinates used by its renderer. */
    public Pose supportPose() {
        SpeakerModels.Model m = supportModel(); if (m == null) return null;
        if (!hanging) return new Pose(0, base, m.depth()/2, 0, m);
        return new Pose(0, 1-BUMPER-supportDrop(), m.depth()/2, tilt, m);
    }
    public boolean isSub() {
        SpeakerModels.Model m = firstModel();
        return m != null && m.isSub();
    }

    /** First cabinet, when the block is placed. */
    public void init(String model, float yaw, boolean hanging) {
        cabinets.clear();
        cabinets.add(new Cabinet(UUID.randomUUID(), model, 0));
        this.yaw = snapYaw(yaw);
        this.hanging = hanging;
        SpeakerModels.Model m = SpeakerModels.get(model);
        if (m != null && m.isSub()) group = AudioGroups.SUBS;
        if (m != null && m.isMonitor()) { this.hanging = false; this.tilt = 0; group = AudioGroups.MONITOR_1; }
        changed(true);
    }

    public static float snapYaw(float yaw) {
        float y = Math.round(yaw / 5f) * 5f;
        y = ((y % 360) + 360) % 360;
        return y > 180 ? y - 360 : y;
    }

    /** Another cabinet of the same family and kind, under a hung array or on top of a stack; false if full or not compatible. */
    public boolean add(String model) {
        SpeakerModels.Model m = SpeakerModels.get(model), first = firstModel();
        SpeakerFrames.Frame frame = SpeakerFrames.get(support);
        if (m == null || (first != null && !first.fits(m)) || frame != null
                && !frame.accepts(m)) return false;
        if (cabinets.size() >= SpeakerModels.max(m)) return false;
        if (dependent() != null) return false;                          // rev. 4: a stack stands on these subs
        float splay = m.isSub() || cabinets.isEmpty() ? 0 : cabinets.get(cabinets.size() - 1).splay();
        cabinets.add(new Cabinet(UUID.randomUUID(), model, splay));
        revision++;
        if (!canFit()) { cabinets.remove(cabinets.size()-1); revision++; return false; }
        if (cabinets.size() == 1) {
            if (frame != null) supportCabinet = model;
            if (m.isSub()) group = AudioGroups.SUBS;
        }
        changed(true);
        return true;
    }

    /** Takes the last cabinet off (the lowest of a hung array, the top of a stack); returns its model, or null when it was the only one. */
    public String removeLast() {
        if (cabinets.isEmpty() || cabinets.size() == 1 && support.isEmpty()) return null;
        if (dependent() != null) return null;                           // rev. 4: a stack stands on these subs
        Cabinet c = cabinets.remove(cabinets.size() - 1);
        changed(true);
        return c.model();
    }

    /**
     * From the CSC Array Tool (checked): yaw, tilt and one splay per cabinet (the first one is ignored), each clamped to what the rigging
     * of the model allows.
     */
    public void applyAngles(float yaw, float tilt, float[] splays) { tryApplyAngles(yaw,tilt,splays); }
    public boolean tryApplyAngles(float yaw, float tilt, float[] splays) {
        if (!Float.isFinite(yaw) || !Float.isFinite(tilt)) return false;
        float oldYaw=this.yaw,oldTilt=this.tilt;
        List<Cabinet> previous=List.copyOf(cabinets);
        this.yaw = Math.max(-180, Math.min(180, yaw));
        this.tilt = (float) Math.max(TILT_MIN, Math.min(TILT_MAX, tilt));
        if (firstModel() != null && firstModel().isMonitor()) this.tilt = tilt < -7.5f ? -15 : 0;
        for (int i = 0; i < cabinets.size(); i++) {
            Cabinet c = cabinets.get(i);
            SpeakerModels.Model m = SpeakerModels.get(c.model());
            float max = m == null ? 0 : (float) m.splayMax();
            float s = i == 0 || splays == null || i >= splays.length || !Float.isFinite(splays[i]) ? 0 : Math.max(0, Math.min(max, splays[i]));
            cabinets.set(i, new Cabinet(c.id(), c.model(), s));
        }
        revision++;
        if (!canFit()) {
            this.yaw=oldYaw;this.tilt=oldTilt;cabinets.clear();cabinets.addAll(previous);revision++;
            changed(false);return false;
        }
        changed(true);
        return true;
    }

    public void link(BlockPos console, int group) {
        this.console = console;
        this.linkedAt = worldPosition;
        if (group >= 0) this.group = Math.max(0, Math.min(AudioGroups.COUNT - 1, group));
        changed(false);
    }

    public void unlink() {
        this.console = null;
        changed(false);
    }

    public void setGroup(int group) {
        this.group = Math.max(0, Math.min(AudioGroups.COUNT - 1, group));
        changed(false);
    }

    public void setVolume(float v) {
        if (!Float.isFinite(v)) return;
        this.volume = Math.max(0, Math.min(1.5f, v));
        changed(false);
    }

    private void changed(boolean shape) {
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            if (shape) rebuildCells();
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ geometry (shared by the renderer, the collision and the audio)

    /** Origin (front bottom centre) and pitch of each cabinet in the array frame (before yaw; origin = centre of the bottom of the block). */
    public record Pose(double x, double y, double z, double pitch, SpeakerModels.Model model) {}

    private List<Pose> cachedPoses;
    private AABB cachedBounds;
    private int cachedRevision = -1;

    /** Poses of the cabinets (cached until the array changes). */
    public List<Pose> poses() {
        if (cachedRevision != revision || cachedPoses == null) {
            cachedPoses = List.copyOf(poses(cabinets, tilt, hanging, groundLift(), supportDrop(), base));
            cachedBounds = null;
            cachedRevision = revision;
        }
        return cachedPoses;
    }

    public static List<Pose> poses(List<Cabinet> cabinets, float tilt, boolean hanging) { return poses(cabinets, tilt, hanging, false); }
    public static List<Pose> poses(List<Cabinet> cabinets, float tilt, boolean hanging, boolean base) { return poses(cabinets,tilt,hanging,base,0); }
    public static List<Pose> poses(List<Cabinet> cabinets, float tilt, boolean hanging, boolean base, double drop) {
        return poses(cabinets, tilt, hanging, base ? BUMPER : 0, drop, 0);
    }

    /** Rev. 4: {@code lift} the support under a stack (frame, adapter, pole), {@code base} the bottom of the stack in its block. */
    public static List<Pose> poses(List<Cabinet> cabinets, float tilt, boolean hanging, double lift, double drop, double base) {
        List<Pose> out = new ArrayList<>();
        if (cabinets.isEmpty()) return out;
        SpeakerModels.Model first = SpeakerModels.get(cabinets.get(0).model());
        if (first == null) return out;
        if (first.isMonitor()) {
            double pitch = tilt < -7.5f ? -15 : 0;
            double up = -first.depth() * Math.sin(Math.toRadians(pitch));
            out.add(new Pose(0, up, first.depth()/2, pitch, first));
            return out;
        }
        double zf = first.depth() / 2;
        double hx = 0, hy = hanging ? 1 - BUMPER - drop : base + lift, hz = zf;
        double theta = tilt;
        for (int i = 0; i < cabinets.size(); i++) {
            SpeakerModels.Model m = SpeakerModels.get(cabinets.get(i).model());
            if (m == null) continue;
            if (i > 0) theta += hanging ? cabinets.get(i).splay() : -cabinets.get(i).splay();
            double r = Math.toRadians(theta), c = Math.cos(r), s = Math.sin(r);
            if (hanging) {
                // the front top edge of this cabinet on the hinge (front bottom edge of the one above / the bumper)
                double ox = hx, oy = hy - m.height() * c, oz = hz - m.height() * s;
                out.add(new Pose(ox, oy, oz, theta, m));
                hx = ox; hy = oy; hz = oz;
            } else {
                out.add(new Pose(hx, hy, hz, theta, m));
                hy += m.height() * c; hz += m.height() * s;               // next one on the front top edge of this one
            }
        }
        return out;
    }

    /** A point of the array frame (cabinet-local x, y, z of the pose) to world coordinates. */
    public Vec3 toWorld(Pose p, double x, double y, double z) {
        double r = Math.toRadians(p.pitch()), c = Math.cos(r), s = Math.sin(r);
        double ay = y * c - z * s, az = y * s + z * c;
        double px = p.x() + x, py = p.y() + ay, pz = p.z() + az;
        double yr = Math.toRadians(yaw), cy = Math.cos(yr), sy = Math.sin(yr);
        return new Vec3(worldPosition.getX() + 0.5 + px * cy - pz * sy, worldPosition.getY() + py, worldPosition.getZ() + 0.5 + px * sy + pz * cy);
    }

    /** Where the sound of the array comes from (middle of the cabinets) and where it goes (unit vector), in world coordinates. */
    public Vec3[] acousticAxis() {
        List<Pose> ps = poses();
        if (ps.isEmpty()) return new Vec3[] {Vec3.atCenterOf(worldPosition), new Vec3(0, 0, 1)};
        Pose mid = ps.get(ps.size() / 2);
        Vec3 c = toWorld(mid, 0, mid.model().height() / 2, -mid.model().depth() / 2);
        double wedgeAngle = mid.model().isMonitor() ? Math.atan2(.54, .33) : 0;
        Vec3 f = toWorld(mid, 0, mid.model().height()/2 + Math.sin(wedgeAngle), Math.cos(wedgeAngle))
            .subtract(toWorld(mid, 0, mid.model().height()/2, 0));
        return new Vec3[] {c, f.normalize()};
    }

    /** Vertical spread of the array (degrees between the aim of the first and the last cabinet). */
    public double verticalSpread() {
        List<Pose> ps = poses();
        return ps.size() < 2 ? 0 : Math.abs(ps.get(ps.size() - 1).pitch() - ps.get(0).pitch());
    }

    /** Box around every cabinet (world, cached). */
    public AABB bounds() {
        List<Pose> ps = poses();
        if (cachedBounds != null) return cachedBounds;
        AABB box = null;
        for (Pose p : ps) {
            SpeakerModels.Model m = p.model();
            for (int k = 0; k < 8; k++) {
                Vec3 v = toWorld(p, (k & 1) == 0 ? -m.width() / 2 : m.width() / 2, (k & 2) == 0 ? 0 : m.height(), (k & 4) == 0 ? 0 : -m.depth());
                AABB b = new AABB(v, v);
                box = box == null ? b : box.minmax(b);
            }
        }
        Pose frame = supportPose();
        if (frame != null && (hanging || !support.isEmpty())) for (int k = 0; k < 8; k++) {
            Vec3 v = toWorld(frame, (k & 1) == 0 ? -frame.model().width()/2-.04 : frame.model().width()/2+.04,
                (k & 2) == 0 ? 0 : supportHeight(), (k & 4) == 0 ? .04 : -frame.model().depth()-.04);
            AABB point = new AABB(v,v); box = box == null ? point : box.minmax(point);
        }
        if (supportDrop()>0) {
            Vec3 pick=new Vec3(worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5);
            AABB hook=new AABB(pick,pick).inflate(.015);box=box==null?hook:box.minmax(hook);
        }
        return cachedBounds = box == null ? new AABB(worldPosition) : box;
    }

    /** Rev. 4: height of the support box (bumper, ground frame with what is under it, the C6 pole). */
    private double supportHeight() {
        return hanging ? BUMPER : Math.max(BUMPER, groundLift());
    }

    // ------------------------------------------------------------------ collision cells

    /** Part of the head block's own cell covered by the array (its shape). */
    private float[] cachedHead;
    private int headRevision = -1;

    public float[] headBox() {
        if (cachedHead == null || headRevision != revision) {
            float[] b = cellBoxes().get(0L);
            cachedHead = b == null ? new float[] {0.3f, 0.3f, 0.3f, 0.7f, 0.7f, 0.7f} : b;
            headRevision = revision;
        }
        return cachedHead;
    }

    private Map<Long, float[]> cellBoxes() {
        Map<Long, float[]> out = new LinkedHashMap<>();
        Pose frame = supportPose();
        if (frame != null && (hanging || !support.isEmpty())) {
            double[] lo={1e9,1e9,1e9},hi={-1e9,-1e9,-1e9};
            for(int k=0;k<8;k++) {
                Vec3 v=toWorld(frame,(k&1)==0?-frame.model().width()/2-.04:frame.model().width()/2+.04,
                    (k&2)==0?0:supportHeight(),(k&4)==0?.04:-frame.model().depth()-.04);
                double[] a={v.x-worldPosition.getX(),v.y-worldPosition.getY(),v.z-worldPosition.getZ()};
                for(int i=0;i<3;i++){lo[i]=Math.min(lo[i],a[i]);hi[i]=Math.max(hi[i],a[i]);}
            }
            addBox(out,new double[]{lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]});
        }
        if (supportDrop()>0 && frame!=null) {
            for(int side:new int[]{-1,1}) {
                Vec3 a=toWorld(frame,side*frame.model().width()*.22,BUMPER,-frame.model().depth()/2);
                Vec3 b=new Vec3(worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5);
                addBox(out,new double[]{Math.min(a.x,b.x)-worldPosition.getX()-.01,Math.min(a.y,b.y)-worldPosition.getY()-.01,Math.min(a.z,b.z)-worldPosition.getZ()-.01,
                    Math.max(a.x,b.x)-worldPosition.getX()+.01,Math.max(a.y,b.y)-worldPosition.getY()+.0,Math.max(a.z,b.z)-worldPosition.getZ()+.01});
            }
        }
        for (Pose p : poses()) {
            SpeakerModels.Model m = p.model();
            double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
            for (int k = 0; k < 8; k++) {
                Vec3 v = toWorld(p, (k & 1) == 0 ? -m.width() / 2 : m.width() / 2, (k & 2) == 0 ? 0 : m.height(), (k & 4) == 0 ? 0 : -m.depth());
                double[] a = {v.x - worldPosition.getX(), v.y - worldPosition.getY(), v.z - worldPosition.getZ()};
                for (int i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], a[i]); hi[i] = Math.max(hi[i], a[i]); }
            }
            addBox(out, new double[] {lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]});
        }
        return out;
    }

    private static void addBox(Map<Long, float[]> out, double[] b) {
        for (int cx = (int) Math.floor(b[0]); cx <= (int) Math.floor(b[3] - 1e-6); cx++)
            for (int cy = (int) Math.floor(b[1]); cy <= (int) Math.floor(b[4] - 1e-6); cy++)
                for (int cz = (int) Math.floor(b[2]); cz <= (int) Math.floor(b[5] - 1e-6); cz++) {
                    float[] bx = out.computeIfAbsent(BlockPos.asLong(cx, cy, cz), k -> new float[] {1, 1, 1, 0, 0, 0});
                    double[] lo = {b[0] - cx, b[1] - cy, b[2] - cz}, hi = {b[3] - cx, b[4] - cy, b[5] - cz};
                    for (int i = 0; i < 3; i++) {
                        bx[i] = (float) Math.max(0, Math.min(bx[i], lo[i]));
                        bx[i + 3] = (float) Math.min(1, Math.max(bx[i + 3], hi[i]));
                    }
                }
    }

    public boolean canFit() {
        if (level == null || level.isClientSide) return true;
        for (var entry : cellBoxes().entrySet()) {
            BlockPos q=worldPosition.offset(BlockPos.of(entry.getKey()));
            if(q.equals(worldPosition))continue;
            var state=level.getBlockState(q);
            if(state.isAir())continue;
            if(level.getBlockEntity(q) instanceof RingTrussCellBlockEntity cell && worldPosition.equals(cell.anchor()))continue;
            float[] b=entry.getValue();
            if(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                net.minecraft.world.phys.shapes.Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]),
                state.getCollisionShape(level,q),net.minecraft.world.phys.shapes.BooleanOp.AND))return false;
        }
        return true;
    }
    public void rebuildCells() {
        if (level == null || level.isClientSide) return;
        if (console != null && linkedAt != null && !linkedAt.equals(worldPosition)) {          // arrived somewhere else with a lift
            if (level.isLoaded(console) && level.getBlockEntity(console) instanceof AudioConsoleBlockEntity c) c.moved(linkedAt, worldPosition);
            linkedAt = worldPosition;
            setChanged();
        }
        Map<Long, float[]> boxes = cellBoxes();
        boxes.remove(0L);
        for (long rel : cells) {
            if (boxes.containsKey(rel)) continue;
            BlockPos q = worldPosition.offset(BlockPos.of(rel));
            if (level.getBlockState(q).getBlock() instanceof RingTrussCellBlock && level.getBlockEntity(q) instanceof RingTrussCellBlockEntity c
                && worldPosition.equals(c.anchor())) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
        }
        List<Long> placed = new ArrayList<>();
        java.util.Set<Long> before = com.concertstage.block.ring.CellOwners.set(cells);
        for (var e : boxes.entrySet()) {
            BlockPos q = worldPosition.offset(BlockPos.of(e.getKey()));
            if (!level.hasChunkAt(q)) {                                            // not loaded: a cell already there stays on the list
                if (before.contains(e.getKey())) placed.add(e.getKey());
                continue;
            }
            BlockState s = level.getBlockState(q);
            boolean ours = s.getBlock() instanceof RingTrussCellBlock && level.getBlockEntity(q) instanceof RingTrussCellBlockEntity c
                && worldPosition.equals(c.anchor());
            if (!(s.isAir() || ours)) continue;                                    // a truss, another block: the cabinet passes through it
            if (s.isAir()) level.setBlock(q, StageContent.RING_TRUSS_CELL.defaultBlockState(), 3);
            if (level.getBlockEntity(q) instanceof RingTrussCellBlockEntity cell) cell.set(e.getValue(), worldPosition, false);
            placed.add(e.getKey());
        }
        cells = placed.stream().mapToLong(Long::longValue).toArray();
        cellSet = null;
    }

    private java.util.Set<Long> cellSet;

    /** True when {@code q} is one of its collision cells (the cells check their owner, see RingTrussCellBlock#orphan). */
    public boolean ownsCell(BlockPos q) {
        if (cellSet == null) cellSet = com.concertstage.block.ring.CellOwners.set(cells);
        return cellSet.contains(q.subtract(worldPosition).asLong());
    }

    /** World positions of its collision cells (a lift takes them away, the array rebuilds them where it arrives). */
    public List<BlockPos> cellPositions() {
        List<BlockPos> out = new ArrayList<>();
        for (long rel : cells) out.add(worldPosition.offset(BlockPos.of(rel)));
        return out;
    }

    public void clearCells() {
        if (level == null || level.isClientSide) return;
        for (long rel : cells) {
            BlockPos q = worldPosition.offset(BlockPos.of(rel));
            if (level.getBlockState(q).getBlock() instanceof RingTrussCellBlock
                    && level.getBlockEntity(q) instanceof RingTrussCellBlockEntity cell
                    && worldPosition.equals(cell.anchor())) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
        }
        cells = new long[0];
        cellSet = null;
    }

    // ------------------------------------------------------------------ save / sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Cabinet c : cabinets) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", c.id());
            t.putString("Model", c.model());
            t.putFloat("Splay", c.splay());
            list.add(t);
        }
        tag.put("Cabinets", list);
        tag.putString("Support", support);
        tag.putString("SupportModel", supportCabinet);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Tilt", tilt);
        tag.putFloat("Volume", volume);
        tag.putBoolean("Hanging", hanging);
        tag.putInt("Group", group);
        if (console != null) tag.putLong("Console", console.asLong());
        if (linkedAt != null) tag.putLong("LinkedAt", linkedAt.asLong());
        tag.putLongArray("Cells", cells);
        if (base != 0) tag.putFloat("Base", base);
        if (standsOn != null) tag.putLong("StandsOn", standsOn.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        supportCabinet = tag.getString("SupportModel");
        support = SpeakerFrames.get(tag.getString("Support")) == null ? "" : tag.getString("Support");
        cabinets.clear();
        ListTag list = tag.getList("Cabinets", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_LINE, list.size()); i++) {
            CompoundTag t = list.getCompound(i);
            String model = t.getString("Model");
            if (SpeakerModels.get(model) == null) continue;
            float s = t.getFloat("Splay");
            cabinets.add(new Cabinet(t.hasUUID("Id") ? t.getUUID("Id") : UUID.randomUUID(), model, Float.isFinite(s) ? Math.max(0, Math.min(10, s)) : 0));
        }
        float y = tag.getFloat("Yaw"), t = tag.getFloat("Tilt"), v = tag.contains("Volume") ? tag.getFloat("Volume") : 1;
        yaw = Float.isFinite(y) ? Math.max(-180, Math.min(180, y)) : 0;
        tilt = Float.isFinite(t) ? (float) Math.max(TILT_MIN, Math.min(TILT_MAX, t)) : 0;
        volume = Float.isFinite(v) ? Math.max(0, Math.min(1.5f, v)) : 1;
        hanging = !tag.contains("Hanging") || tag.getBoolean("Hanging");
        group = Math.max(0, Math.min(AudioGroups.COUNT - 1, tag.getInt("Group")));
        console = tag.contains("Console") ? BlockPos.of(tag.getLong("Console")) : null;
        linkedAt = tag.contains("LinkedAt") ? BlockPos.of(tag.getLong("LinkedAt")) : null;
        cells = tag.getLongArray("Cells");
        cellSet = null;
        float b = tag.getFloat("Base");
        base = Float.isFinite(b) ? Math.max(-1, Math.min(1, b)) : 0;
        standsOn = tag.contains("StandsOn") ? BlockPos.of(tag.getLong("StandsOn")) : null;
        revision++;
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    /** Forge: the array is drawn well outside its block. */
    public AABB getRenderBoundingBox() {
        return bounds().inflate(0.5);
    }
}
