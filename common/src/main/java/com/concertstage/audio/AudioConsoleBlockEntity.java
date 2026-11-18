package com.concertstage.audio;

import com.concertstage.StageContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CSC Audio Console: playlist of imported tracks (the files themselves are in the server's audio library), transport, master and
 * group faders, mutes, 3-band EQ and the list of linked speaker arrays. The server owns the state; clients get it through the block
 * entity update and play it in sync: the playhead is {@code (game time - startTick) * 50 ms}, so every client computes the same
 * position from the same game clock.
 */
public class AudioConsoleBlockEntity extends BlockEntity {
    public static final int MAX_TRACKS = 64, MAX_SPEAKERS = 64;
    public static final int STOPPED = 0, PLAYING = 1, PAUSED = 2;
    public static final float EQ_RANGE = 12;
    /** How far a speaker can be linked from its console (blocks). */
    public static final int LINK_RANGE = 160;

    public record Track(String sha1, String name, String type, long size, long durationMs, String owner) {}

    private final List<Track> playlist = new ArrayList<>();
    private final List<BlockPos> speakers = new ArrayList<>();
    private int current = -1, state = STOPPED, session;
    private long startTick, startRemainderMs, pausedMs;
    private float master = 0.8f;
    private final float[] faders = new float[AudioGroups.COUNT];
    { java.util.Arrays.fill(faders, .8f); }
    private int mutes;
    private final float[] groupDelays=new float[AudioGroups.COUNT];
    private final String[] groupLabels=new String[AudioGroups.COUNT];
    private CompoundTag plannerPlan=new CompoundTag();
    private final float[] eq = {0, 0, 0};
    private int repeatMode = REPEAT_ALL;
    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;
    private String label = "";

    public AudioConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.AUDIO_CONSOLE_ENTITY, pos, state);
    }

    /** Consoles present in the client's level (the client sound engine goes through them every tick). */
    private static final java.util.Set<BlockPos> CLIENT_CONSOLES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static java.util.Set<BlockPos> clientConsoles() {
        return CLIENT_CONSOLES;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide) CLIENT_CONSOLES.add(worldPosition.immutable());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) CLIENT_CONSOLES.remove(worldPosition);
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox() { return new net.minecraft.world.phys.AABB(worldPosition).inflate(1); }
    public void loadAudioSnapshot(Level level, CompoundTag tag) { super.setLevel(level); load(tag); }

    // ------------------------------------------------------------------ read
    public List<Track> playlist() { return playlist; }
    public List<BlockPos> speakers() { return speakers; }
    public int current() { return current; }
    public int state() { return state; }
    /** Changes whenever the clients must restart their streams (new track, play after stop / pause, seek). */
    public int session() { return session; }
    public long startTick() { return startTick; }
    public float master() { return master; }
    public float fader(int g) { return faders[g]; }
    public boolean muted(int g) { return (mutes & (1 << g)) != 0; }
    public float groupDelay(int g){return g>=0&&g<AudioGroups.COUNT?groupDelays[g]:0;}
    public String groupLabel(int g){return g>=0&&g<AudioGroups.COUNT&&groupLabels[g]!=null?groupLabels[g]:"";}
    public void setGroupDelay(int g,float ms){if(g>=0&&g<AudioGroups.COUNT&&Float.isFinite(ms)){groupDelays[g]=Math.max(0,Math.min(1500,ms));changed();}}
    public void setGroupLabel(int g,String label){if(g>=0&&g<AudioGroups.COUNT&&label!=null){StringBuilder clean=new StringBuilder();for(int i=0;i<label.length()&&clean.length()<32;i++){char ch=label.charAt(i);if(!Character.isISOControl(ch))clean.append(ch);}groupLabels[g]=clean.toString().strip();changed();}}
    public CompoundTag plannerPlan(){return plannerPlan.copy();}
    public boolean setPlannerPlan(CompoundTag input){var clean=PlannerState.clean(input,worldPosition,level,speakers);if(clean==null)return false;plannerPlan=clean;changed();return true;}
    public float eq(int band) { return eq[band]; }
    /** Legacy API: whether the playlist may wrap. */
    public boolean repeat() { return repeatMode != REPEAT_OFF; }
    public int repeatMode() { return repeatMode; }
    /** Natural successor; manual Next deliberately ignores Repeat One. */
    public int nextTrackIndex() {
        return com.concertstage.audio.core.RepeatMode.successor(current, playlist.size(), repeatMode, true);
    }
    public String label() { return label; }

    public Track currentTrack() {
        return current >= 0 && current < playlist.size() ? playlist.get(current) : null;
    }

    /** Playhead (ms) at a game time (with the partial tick on the client). */
    public long positionMs(double gameTime) {
        if (state == PAUSED) return pausedMs;
        if (state != PLAYING) return 0;
        return Math.max(0, (long) ((gameTime - startTick) * 50 - startRemainderMs));
    }

    /** True when at least one linked array is in the subwoofer group (the mains then get the high pass of the crossover). */
    public boolean drivesSubs() {
        if (level == null) return false;
        for (BlockPos p : speakers)
            if (level.getBlockEntity(p) instanceof SpeakerBlockEntity s && s.group() == AudioGroups.SUBS) return true;
        return false;
    }

    // ------------------------------------------------------------------ transport (server)
    private long now() {
        return level == null ? 0 : level.getGameTime();
    }

    public void play() {
        if (playlist.isEmpty()) return;
        if (current < 0 || current >= playlist.size()) current = 0;
        long from = state == PAUSED ? pausedMs : 0;
        startAt(from);
    }

    private void startAt(long ms) {
        state = PLAYING;
        startTick = now() - ms / 50;
        startRemainderMs = -(ms % 50);
        pausedMs = 0;
        session++;
        changed();
    }

    public void pause() {
        if (state != PLAYING) return;
        pausedMs = positionMs(now());
        state = PAUSED;
        session++;
        changed();
    }

    public void stop() {
        if (state == STOPPED) return;
        state = STOPPED;
        pausedMs = 0;
        session++;
        changed();
    }

    public void select(int index) {
        if (index < 0 || index >= playlist.size()) return;
        current = index;
        startAt(0);
    }

    public void next() {
        if (playlist.isEmpty()) return;
        int n = current + 1;
        if (n >= playlist.size()) {
            if (repeatMode == REPEAT_OFF) { current = 0; stop(); return; }
            n = 0;
        }
        select(n);
    }

    public void previous() {
        if (playlist.isEmpty()) return;
        if (state == PLAYING && positionMs(now()) > 3000) { select(current); return; }     // like any player: back to the start first
        select(current <= 0 ? playlist.size() - 1 : current - 1);
    }

    public void seek(long ms) {
        Track t = currentTrack();
        if (t == null) return;
        long max = t.durationMs() > 0 ? t.durationMs() - 500 : ms;
        ms = Math.max(0, Math.min(max, ms));
        if (state == PLAYING) startAt(ms);
        else { state = PAUSED; pausedMs = ms; session++; changed(); }
    }

    public boolean addTrack(Track t) {
        for (Track o : playlist) if (o.sha1().equals(t.sha1())) return true;
        if (playlist.size() >= MAX_TRACKS) return false;
        playlist.add(t);
        if (current < 0) current = 0;
        changed();
        return true;
    }

    public void removeTrack(int index) {
        if (index < 0 || index >= playlist.size()) return;
        boolean playingIt = index == current && state != STOPPED;
        playlist.remove(index);
        if (index < current) current--;
        else if (index == current) {
            if (playingIt) { state = STOPPED; session++; }
            if (current >= playlist.size()) current = playlist.size() - 1;
        }
        changed();
    }

    public void moveTrack(int index, int dir) {
        int j = index + dir;
        if (index < 0 || j < 0 || index >= playlist.size() || j >= playlist.size()) return;
        Track t = playlist.get(index);
        playlist.set(index, playlist.get(j));
        playlist.set(j, t);
        if (current == index) current = j;
        else if (current == j) current = index;
        changed();
    }

    public void setMaster(float v) { master = clamp01(v); changed(); }
    public void setFader(int g, float v) { if (g >= 0 && g < AudioGroups.COUNT) { faders[g] = clamp01(v); changed(); } }
    public void toggleMute(int g) { if (g >= 0 && g < AudioGroups.COUNT) { mutes ^= 1 << g; changed(); } }
    public void setEq(int band, float db) { if (band >= 0 && band < 3 && Float.isFinite(db)) { eq[band] = Math.max(-EQ_RANGE, Math.min(EQ_RANGE, db)); changed(); } }
    /** Kept for existing callers; cycles Off -> All -> One -> Off. */
    public void toggleRepeat() { setRepeatMode((repeatMode + 1) % 3); }
    public void setRepeatMode(int mode) {
        repeatMode = com.concertstage.audio.core.RepeatMode.clamp(mode);
        changed();
    }

    private void advanceNaturally(long elapsed) {
        // Keep elapsed overshoot, including sub-tick durations, instead of adding a 250 ms pause.
        // Bound the loop when a world resumes after a very long absence.
        for (int n = 0; n < MAX_TRACKS * 4; n++) {
            Track track = currentTrack();
            if (track == null || track.durationMs() <= 0 || elapsed < track.durationMs()) break;
            elapsed -= track.durationMs();
            int next = nextTrackIndex();
            if (next < 0) { stop(); return; }
            current = next;
            if (repeatMode == REPEAT_ONE && track.durationMs() > 0) elapsed %= track.durationMs();
        }
        // Natural transitions preserve the continuous stream session. Manual transport still increments it.
        state = PLAYING;
        startTick = now() - elapsed / 50;
        startRemainderMs = -(elapsed % 50);
        pausedMs = 0;
        changed();
    }
    public void setLabel(String s) { label = s == null ? "" : s.length() > 32 ? s.substring(0, 32) : s; changed(); }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0.8f;
    }

    // ------------------------------------------------------------------ links
    public boolean link(BlockPos speaker) {
        if (speakers.contains(speaker)) return true;
        if (speakers.size() >= MAX_SPEAKERS) return false;
        speakers.add(speaker.immutable());
        changed();
        return true;
    }

    public void unlink(BlockPos speaker) {
        if (speakers.remove(speaker)) changed();
    }

    /** A speaker array was broken (or unlinked from its side): its console forgets it. */
    public static void forgetSpeaker(Level level, BlockPos console, BlockPos speaker) {
        if (level == null || console == null || level.isClientSide || !level.isLoaded(console)) return;
        if (level.getBlockEntity(console) instanceof AudioConsoleBlockEntity c) c.unlink(speaker);
    }

    /** Removes links to arrays that are no longer there (or linked to another console); returns how many. Only loaded positions. */
    public int pruneLinks() {
        if (level == null) return 0;
        int before = speakers.size();
        speakers.removeIf(p -> level.isLoaded(p) && !(level.getBlockEntity(p) instanceof SpeakerBlockEntity s && worldPosition.equals(s.console())));
        if (speakers.size() != before) changed();
        return before - speakers.size();
    }

    /** A lifted array arrives one or more blocks higher: its link follows it (called by the array itself). */
    public void moved(BlockPos from, BlockPos to) {
        int i = speakers.indexOf(from);
        if (i >= 0) { speakers.set(i, to.immutable()); changed(); }
    }

    // ------------------------------------------------------------------ tick (server)
    public static void serverTick(Level level, BlockPos pos, BlockState state, AudioConsoleBlockEntity be) {
        if (be.state != PLAYING) return;
        Track t = be.currentTrack();
        if (t == null) { be.stop(); return; }
        long elapsed = be.positionMs(level.getGameTime());
        if (t.durationMs() > 0 && elapsed >= t.durationMs()) be.advanceNaturally(elapsed);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ save / sync
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Track t : playlist) {
            CompoundTag c = new CompoundTag();
            c.putString("Sha1", t.sha1());
            c.putString("Name", t.name());
            c.putString("Type", t.type());
            c.putLong("Size", t.size());
            c.putLong("Duration", t.durationMs());
            c.putString("Owner", t.owner());
            list.add(c);
        }
        tag.put("Playlist", list);
        ListTag sp = new ListTag();
        for (BlockPos p : speakers) sp.add(LongTag.valueOf(p.asLong()));
        tag.put("Speakers", sp);
        tag.putInt("Current", current);
        tag.putInt("State", state);
        tag.putInt("Session", session);
        tag.putLong("Start", startTick);
        tag.putLong("StartRemainderMs", startRemainderMs);
        tag.putLong("Paused", pausedMs);
        tag.putFloat("Master", master);
        for (int g = 0; g < AudioGroups.COUNT; g++) tag.putFloat("Fader" + g, faders[g]);
        tag.putInt("Mutes", mutes);
        for(int g=0;g<AudioGroups.COUNT;g++){tag.putFloat("GroupDelay"+g,groupDelays[g]);tag.putString("GroupLabel"+g,groupLabel(g));}
        tag.put("DelayPlanner",plannerPlan.copy());
        for (int b = 0; b < 3; b++) tag.putFloat("Eq" + b, eq[b]);
        tag.putBoolean("Repeat", repeat()); // backwards-readable save
        tag.putInt("RepeatMode", repeatMode);
        tag.putString("Label", label);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        playlist.clear();
        ListTag list = tag.getList("Playlist", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_TRACKS, list.size()); i++) {
            CompoundTag c = list.getCompound(i);
            String sha = c.getString("Sha1"), type = c.getString("Type");
            if (!com.concertstage.audio.core.AudioFiles.validSha1(sha)) continue;
            if (!type.equals("mp3") && !type.equals("wav")) continue;
            playlist.add(new Track(sha, c.getString("Name"), type, c.getLong("Size"), c.getLong("Duration"), c.getString("Owner")));
        }
        speakers.clear();
        ListTag sp = tag.getList("Speakers", Tag.TAG_LONG);
        for (int i = 0; i < Math.min(MAX_SPEAKERS, sp.size()); i++) speakers.add(BlockPos.of(((LongTag) sp.get(i)).getAsLong()));
        current = Math.max(-1, Math.min(playlist.size() - 1, tag.getInt("Current")));
        if (!tag.contains("Current")) current = playlist.isEmpty() ? -1 : 0;
        state = Math.max(0, Math.min(2, tag.getInt("State")));
        if (current < 0) state = STOPPED;
        session = tag.getInt("Session");
        startTick = tag.getLong("Start");
        startRemainderMs = Math.max(-49, Math.min(49, tag.getLong("StartRemainderMs")));
        pausedMs = Math.max(0, tag.getLong("Paused"));
        master = tag.contains("Master") ? clamp01(tag.getFloat("Master")) : 0.8f;
        for (int g = 0; g < AudioGroups.COUNT; g++) faders[g] = tag.contains("Fader" + g) ? clamp01(tag.getFloat("Fader" + g)) : 0.8f;
        for(int g=0;g<AudioGroups.COUNT;g++){float ms=tag.getFloat("GroupDelay"+g);groupDelays[g]=Float.isFinite(ms)?Math.max(0,Math.min(1500,ms)):0;String label=tag.getString("GroupLabel"+g);groupLabels[g]=label.substring(0,Math.min(32,label.length()));}
        plannerPlan=tag.getCompound("DelayPlanner").copy();
        mutes = tag.getInt("Mutes") & ((1 << AudioGroups.COUNT) - 1);
        for (int b = 0; b < 3; b++) { float v = tag.getFloat("Eq" + b); eq[b] = Float.isFinite(v) ? Math.max(-EQ_RANGE, Math.min(EQ_RANGE, v)) : 0; }
        repeatMode = tag.contains("RepeatMode") ? com.concertstage.audio.core.RepeatMode.clamp(tag.getInt("RepeatMode"))
                : (!tag.contains("Repeat") || tag.getBoolean("Repeat") ? REPEAT_ALL : REPEAT_OFF);
        label = tag.getString("Label");
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
