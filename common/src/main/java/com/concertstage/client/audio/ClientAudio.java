package com.concertstage.client.audio;

import com.concertstage.audio.core.ArrayMix;
import com.concertstage.audio.core.ListenerMix;
import com.concertstage.audio.core.SoundSystems;

import com.concertstage.client.ui.Tr;
import com.concertstage.audio.AudioConsoleBlockEntity;
import com.concertstage.audio.AudioGroups;
import com.concertstage.audio.SpeakerBlockEntity;
import com.concertstage.audio.core.Acoustics;
import com.concertstage.audio.core.TrackPcm;
import com.concertstage.audio.net.AudioNet;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Client-side console mixer: one decoder and one stereo sound per audible console.
 * All linked arrays contribute to the same sample clock; movement changes gain and pan only.
 * Decoding and DSP stay off the game thread. */
public final class ClientAudio {
    private static final Map<BlockPos, Playback> PLAYBACKS = new HashMap<>();
    private static final Map<BlockPos, Voice> VOICES = new HashMap<>();
    private static ClientLevel lastLevel;
    private static long startedSources,droppedSources,clockCorrections,observedStarvations;
    private static int activeSoundSources;
    private static void diagnosticEvent(String event){if(AudioSettings.diagnosticsLog)com.mojang.logging.LogUtils.getLogger().info("CSC_AUDIO {}",event);}
    public static java.util.List<String> diagnostics(){
        var rows=new java.util.ArrayList<String>();
        rows.add(Tr.t("Minecraft active / retained: ")+activeSoundSources+Tr.t(" / retained: ")+VOICES.size());
        rows.add(Tr.t("Start requests / inactive events: ")+startedSources+" / "+droppedSources);
        rows.add(Tr.t("Clock corrections: ")+clockCorrections);
        // 1.1.1 audio fix: what the arrays form (main system, delay towers) and where they come from
        var inst=installations();for(int i=0;i<Math.min(6,inst.size());i++)rows.add(inst.get(i));
        rows.add(Tr.f("Room reverb: wet %.2f, size %.2f · obstacles: %s",SoundSpace.wet(),SoundSpace.room(),Tr.onOff(AudioSettings.occlusion)));
        rows.add(Tr.f("Concert realism: %s · echo %.0f ms at %.0f %% · %s",Tr.onOff(AudioSettings.concert),SoundSpace.echoMs(),SoundSpace.echoGain()*100,Tr.t(SoundSpace.outdoors()?"open air":"indoors")));
        long total=com.concertstage.audio.core.PlaylistPcm.STARVATIONS.get(),last=com.concertstage.audio.core.PlaylistPcm.lastStarvationNanos;
        rows.add(Tr.t("PCM starvation events (>80 ms): ")+total);
        rows.add(Tr.t("Since last PCM starvation: ")+(last==0?Tr.t("none"):((System.nanoTime()-last)/1_000_000_000L)+" s"));
        // 1.1.1: the level of what is sent to Minecraft (before its Master / Jukebox sliders), loudest console
        Voice loud=null;for(Voice v:VOICES.values())if(loud==null||v.loudness>loud.loudness)loud=v;
        rows.add(loud==null||!Float.isFinite(loud.loudness)?Tr.t("Output level: no sound"):Tr.f("Output %.1f LUFS (3 s) · peak %.1f dBFS · limiter -%.1f dB · %d groups",loud.loudness,20*Math.log10(Math.max(1e-6,loud.peak)),loud.limiterDb,loud.groups));
        var consoles=new java.util.ArrayList<>(PLAYBACKS.values());consoles.sort(java.util.Comparator.comparing(pb->!pb.playing));
        for(var pb:consoles){
            var feed=pb.continuous;
            var c=RemoteConsoles.get(pb.pos);long position=c==null||c.getLevel()==null?0:c.positionMs(c.getLevel().getGameTime());
            rows.add(Tr.t("Console ")+pb.pos.toShortString()+": "+(pb.playing?Tr.t("Playing"):Tr.t("Stopped/paused"))+" @ "+position+Tr.t(" ms"));
            rows.add(Tr.t("Sync clock drift: ")+(pb.playing?(pb.nowMs()-position)+Tr.t(" ms"):Tr.t("not playing")));
            rows.add(Tr.t("Arrays loaded / linked: ")+pb.loadedArrays+" / "+pb.linkedArrays);
            rows.add(Tr.t("Decoder: ")+(feed==null?(pb.waitingForFile?Tr.t("Waiting download"):Tr.t("Not opened")):feed.decoderState()));
            rows.add(Tr.t("VU decoder: ")+(pb.pcm==null?Tr.t("not opened"):pb.pcm.failed()?Tr.t("failed"):pb.pcm.ready()?Tr.t("ready"):Tr.t("starting")));
            rows.add(Tr.t("PCM ready chunks: ")+(feed==null?0:feed.bufferedChunks())+" / 150");
            rows.add(Tr.t("Stream error: ")+(feed==null||feed.failure()==null?Tr.t("none"):feed.failure()));
            break;
        }
        return rows;
    }
    /** Console whose screen is open: its VU meters are computed. */
    private static BlockPos watched;

    private ClientAudio() {}

    public static void init() {
        AudioSettings.load();
        AudioNet.setClientHandler(b -> { int type = b.readVarInt(); if (type == AudioNet.S_LIFT) com.concertstage.client.LiftClient.receive(b); else Transfers.receive(type, b); });
    }

    public static void watch(BlockPos console) {
        watched = console;
    }

    public static Playback playback(BlockPos console) {
        return PLAYBACKS.get(console);
    }

    /** Leaving the world, or stopping everything. */
    public static void reset() {
        com.concertstage.client.LiftClient.reset();
        for (Voice v : VOICES.values()) v.dead = true;
        VOICES.clear();activeSoundSources=0;
        for (Playback p : PLAYBACKS.values()) p.close();
        PLAYBACKS.clear();
        SCENES.clear();
        Transfers.reset();
        RemoteConsoles.reset();
        SoundSpace.reset();
        DelayPlannerOverlay.clear();
        lastLevel = null;
    }

    // ------------------------------------------------------------------ playback of one console
    /** What one console plays on this client: its track decoder, its clock, the console values the streams read, its meters. */
    public static final class Playback {
        final BlockPos pos;
        volatile int session = Integer.MIN_VALUE;
        String sha1;
        long observedStart = Long.MIN_VALUE;
        volatile com.concertstage.audio.core.PlaylistPcm.Plan preparedPlan;
        volatile int preparedIndex;
        volatile TrackPcm pcm;
        volatile com.concertstage.audio.core.PlaylistPcm continuous;
        TrackPcm.Reader monitor;
        /** Playhead clock: position (ms) = (nanoTime - anchor) / 1e6. */
        private volatile long anchorNanos;
        volatile boolean playing;
        volatile float eqLow, eqMid, eqHigh;
        volatile boolean subs;
        /** Meters (0..1, decaying): master L / R and each group. */
        public float vuL, vuR;
        public final float[] vuGroup = new float[AudioGroups.COUNT];
        public boolean waitingForFile;
        /** The current track is in the local cache (checked once per track, then every second while waiting: no disk access every tick). */
        boolean fileReady;
        String checkedSha;
        int recheck, prefetchTick;
        /** Key of the playlist the prepared plan was built from (the plan is built again only when the playlist changes). */
        int planKey;
        int loadedArrays, linkedArrays;

        Playback(BlockPos pos) {
            this.pos = pos;
        }

        long nowMs() {
            return Math.max(0, (System.nanoTime() - anchorNanos) / 1_000_000L);
        }

        void close() {
            if (continuous != null) continuous.close();
            continuous = null;
            if (monitor != null) monitor.close();
            if (pcm != null) pcm.close();
            monitor = null;
            pcm = null;
            playing = false;
        }
    }

    // ------------------------------------------------------------------ one console being heard
    /** A console sound; immutable gain snapshots cross from game thread to sound thread. */
    public static final class Voice {
        final BlockPos speaker;
        final Playback playback;
        final int session;
        volatile float eqLow, eqMid, eqHigh;
        volatile float peak, reductionDb;
        /** 1.1.1: short-term loudness of the output (LUFS), limiter reduction (dB), groups mixed. */
        volatile float loudness = Float.NEGATIVE_INFINITY, limiterDb;
        volatile int groups;
        volatile float airCutoff=20000;
        volatile float reverbWet, reverbRoom = .5f, reverbDamp = .5f;
        /** aurex.2: echo of the place (ms, level) and wind (0..1) for the concert realism. */
        volatile float echoMs, echoGain, wind;
        volatile boolean dead;
        volatile float[] groupDelays=new float[ListenerMix.SLOTS];
        volatile float[] weights = new float[ListenerMix.SLOTS * 2];
        /** 1.1.1 audio fix: crossover of the subs from their voicing (NaN = the listening profile's). */
        volatile float crossoverHz = Float.NaN;
        SpeakerSoundInstance sound;
        int age;

        Voice(BlockPos speaker, Playback playback) {
            this.speaker = speaker;
            this.playback = playback;
            this.session = playback.session;
        }

        /** On a background thread, when Minecraft starts the sound: a reader at the playhead of the console. */
        AudioStream open() {
            if (dead || session != playback.session || !playback.playing) return EMPTY;
            // Plan snapshots are prepared on the game thread; no world access on this sound thread.
            var plan = playback.preparedPlan;
            if (plan == null) return EMPTY;
            var feed = new com.concertstage.audio.core.PlaylistPcm(plan, playback.preparedIndex, playback.nowMs());
            if(dead || session!=playback.session || !playback.playing){feed.close();return EMPTY;}
            playback.continuous = feed;
            return new SpeakerAudioStream(feed, this);
        }
    }

    private static final AudioStream EMPTY = new AudioStream() {
        private final AudioFormat f = new AudioFormat(44100, 16, 1, true, false);
        @Override public AudioFormat getFormat() { return f; }
        @Override public ByteBuffer read(int size) { return null; }
        @Override public void close() { }
    };

    // ------------------------------------------------------------------ tick
    public static void tick() {
        com.concertstage.client.RenderQuality.autoTick();     // 1.1.1: AUTO render quality (called here: both loaders tick the sound)
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) { reset(); lastLevel = level; }
        if (level == null || mc.player == null) return;
        com.concertstage.client.LiftClient.tick();
        Transfers.tick();
        LinkLines.tick(mc);
        RemoteConsoles.tick();
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        double gameTime = level.getGameTime();

        // consoles
        java.util.Set<BlockPos> consoles = new java.util.HashSet<>(AudioConsoleBlockEntity.clientConsoles());
        consoles.addAll(RemoteConsoles.positions());
        for (BlockPos pos : consoles) {
            AudioConsoleBlockEntity c = RemoteConsoles.get(pos);
            if (c == null) continue;
            Playback pb = PLAYBACKS.computeIfAbsent(pos, Playback::new);
            update(pb, c, gameTime, ear);
        }
        PLAYBACKS.entrySet().removeIf(e -> {
            if (RemoteConsoles.get(e.getKey()) != null) return false;
            e.getValue().close();
            return true;
        });
        SCENES.keySet().removeIf(p -> !PLAYBACKS.containsKey(p));

        // the room around the listener (reverb), once a second; arrays not heard any more forgotten
        SoundSpace.probe(level, ear, level.getGameTime());
        if (level.getGameTime() % 20 == 0) SoundSpace.trim(level.getGameTime());
        // One source/read head per console. All arrays contribute to the same PCM frame.
        java.util.Set<BlockPos> chosen = new java.util.HashSet<>();
        double heading = Math.toRadians(mc.gameRenderer.getMainCamera().getYRot());
        for (Playback pb : PLAYBACKS.values()) {
            if (!pb.playing || (!pb.fileReady && pb.continuous == null)) continue;
            AudioConsoleBlockEntity c = RemoteConsoles.get(pb.pos);
            if (c == null) continue;
            // 1.1.1 audio fix: the arrays of the console as pure records (rebuilt only when one changes), what they form (main system,
            // delay towers: classified only when the geometry or the settings change), then this player's own mix (ListenerMix)
            Scene sc = SCENES.computeIfAbsent(pb.pos, k -> new Scene());
            scene(sc, c, level, pb);
            pb.loadedArrays = sc.sources.size(); pb.linkedArrays = c.speakers().size();
            final Scene fsc = sc;
            WALLS.clear();
            ListenerMix.Walls walls = new ListenerMix.Walls() {
                public double walls(int i) {
                    if (!AudioSettings.occlusion) return 0;
                    SoundSystems.Source a = fsc.sources.get(i);
                    // one line of sight per system (arrays of one group and model in the same 3.5-block cell: a hang, a stack)
                    String modelId = a.model() == null ? "" : a.model().id();
                    long cell = (((long) Math.floor(a.x() / ArrayMix.CLUSTER_BLOCKS)) * 73856093L) ^ (((long) Math.floor(a.y() / ArrayMix.CLUSTER_BLOCKS)) * 19349663L)
                        ^ (((long) Math.floor(a.z() / ArrayMix.CLUSTER_BLOCKS)) * 83492791L) ^ (a.group() * 31L + modelId.hashCode()) * 2654435761L;
                    Double cached = WALLS.get(cell);
                    if (cached != null) return cached;
                    double w = SoundSpace.walls(level, fsc.positions.get(i), new Vec3(a.x(), a.y(), a.z()), ear, level.getGameTime());
                    WALLS.put(cell, w);
                    return w;
                }
                // low frequencies go through walls: a sub behind a wall loses about a third of what the highs lose
                public double gain(double w, boolean sub) { return SoundSpace.gain(sub ? w * .35 : w); }
            };
            for (int g = 0; g < AudioGroups.COUNT; g++) { sc.faders[g] = c.fader(g); sc.mutes[g] = c.muted(g); sc.groupDelays[g] = c.groupDelay(g); }
            ListenerMix.Desk desk = new ListenerMix.Desk(c.master(), sc.faders, sc.mutes, sc.groupDelays);
            ListenerMix.Settings set = AudioSettings.listenerSettings();
            ListenerMix.Result r = ListenerMix.compute(sc.sources, sc.layout, desk, ear.x, ear.y, ear.z, heading, set, sc, walls, sc.result);
            double sum = r.sum, weightedDistance = r.weightedDistance, heardWalls = r.heardWalls, heard = r.heard;
            float[] weights = r.weights.clone();
            Voice v = VOICES.get(pb.pos);
            if (v != null && (v.playback != pb || v.session != pb.session)) {
                v.dead = true; VOICES.remove(pb.pos); v = null;
            }
            // Keep a started source alive while inaudible: moving never recreates its reader.
            if (v == null && (sum < .0001 || VOICES.size() >= AudioSettings.maxStreams)) continue;
            chosen.add(pb.pos);
            if (v == null) { v = new Voice(pb.pos, pb); VOICES.put(pb.pos, v); }
            v.airCutoff=AudioSettings.outdoor&&AudioSettings.airAbsorption?(float)Math.max(2000,20000/(1+weightedDistance/Math.max(.001,sum)/80)):20000;
            if (heard > 0) v.airCutoff = Math.min(v.airCutoff, SoundSpace.cutoff(heardWalls / heard));     // the walls the sound goes through
            v.reverbWet = SoundSpace.wet(); v.reverbRoom = SoundSpace.room(); v.reverbDamp = SoundSpace.damp();
            v.echoMs = SoundSpace.echoMs(); v.echoGain = SoundSpace.echoGain();
            double meanDistance = weightedDistance / Math.max(.001, sum);   // far from the PA in the open air: the wind takes the highs
            v.wind = AudioSettings.outdoor && SoundSpace.outdoors() ? (float) Math.max(0, Math.min(1, (meanDistance - 40) / 120)) : 0;
            // delays: changed only when they moved by more than 2 ms (each change is a 20 ms crossfade in the delay line)
            ListenerMix.settle(r.delays, sc.appliedDelays, 2f);
            v.groupDelays = sc.appliedDelays.clone();
            v.crossoverHz = AudioSettings.subCrossoverAuto ? r.crossoverHz : Float.NaN;
            v.weights = weights;
            v.eqLow = pb.eqLow; v.eqMid = pb.eqMid; v.eqHigh = pb.eqHigh;
        }
        // update / start / stop the sounds
        Iterator<Map.Entry<BlockPos, Voice>> it = VOICES.entrySet().iterator();
        while (it.hasNext()) {
            Voice v = it.next().getValue();
            if (!chosen.contains(v.speaker)) v.weights = new float[ListenerMix.SLOTS * 2];
            v.age++;
            boolean gone = v.dead || !v.playback.playing || v.session != v.playback.session;
            if (!gone && v.sound == null) {
                v.sound = new SpeakerSoundInstance(v);
                mc.getSoundManager().play(v.sound);startedSources++;diagnosticEvent("source request "+v.speaker);
            } else if (!gone && v.age > 4 && !mc.getSoundManager().isActive(v.sound)) {
                droppedSources++;diagnosticEvent("sound engine inactive "+v.speaker+" age="+v.age+" decoder="+(v.playback.continuous==null?"none":v.playback.continuous.decoderState())+" failure="+(v.playback.continuous==null?"none":v.playback.continuous.failure()));
                gone = true;                                           // the sound engine dropped it (end of track, no free source): retried
            }
            if (gone) { v.dead = true; it.remove(); }
        }
        activeSoundSources=0;for(Voice voice:VOICES.values())if(voice.sound!=null&&mc.getSoundManager().isActive(voice.sound))activeSoundSources++;
        long starvation=com.concertstage.audio.core.PlaylistPcm.STARVATIONS.get();if(starvation!=observedStarvations){diagnosticEvent("PCM starvation count "+starvation);observedStarvations=starvation;}
        // Meters are also used by the 3D desk when its screen is closed.
        for (Playback pb : PLAYBACKS.values()) {
            AudioConsoleBlockEntity c = RemoteConsoles.get(pb.pos);
            if (c != null) meters(pb, c);
        }

    }

    /** 1.1.1: scratch of the obstacle checks (game thread only). */
    private static final java.util.HashMap<Long, Double> WALLS = new java.util.HashMap<>();

    // ------------------------------------------------------------------ 1.1.1 audio fix: the arrays of one console
    private static final Map<BlockPos, Scene> SCENES = new HashMap<>();
    /** Arrays carried by a lift are heard where the lift is for this long at most (then forgotten until they land). */
    private static final long CARRY_TICKS = 20 * 120;

    /**
     * The arrays of one console as pure records, their classification and this player's last mix. Rebuilt only when an array changes
     * (signature of positions, revisions, angles, groups, links); classified only when the geometry or the settings change.
     */
    static final class Scene implements ListenerMix.ArrayInfo {
        long signature = Long.MIN_VALUE, layoutKey = Long.MIN_VALUE;
        final List<SoundSystems.Source> sources = new ArrayList<>();
        final List<BlockPos> positions = new ArrayList<>();
        final List<String> rangeKeys = new ArrayList<>();
        float[] volumes = new float[0];
        SoundSystems.Layout layout = SoundSystems.Layout.EMPTY;
        final ListenerMix.Result result = new ListenerMix.Result();
        final float[] appliedDelays = new float[ListenerMix.SLOTS];
        final float[] faders = new float[AudioGroups.COUNT], groupDelays = new float[AudioGroups.COUNT];
        final boolean[] mutes = new boolean[AudioGroups.COUNT];
        /** Last record of each array, kept while a lift carries it (BlockPos -> source, tick seen). */
        final Map<BlockPos, SoundSystems.Source> lastSeen = new HashMap<>();
        final Map<BlockPos, Long> lastSeenTick = new HashMap<>();
        boolean moving;
        long classifyNanos; int classifications;
        /** Where the delay towers should stand (computed with the classification) and the last advice told in the chat. */
        com.concertstage.audio.core.TowerAdvice.Advice advice = com.concertstage.audio.core.TowerAdvice.Advice.NONE;
        String told;

        @Override public double rangeFactor(int i, SoundSystems.Source s, SoundSystems.Installation inst) {
            String key = inst != null && inst.role() == SoundSystems.Role.TOWER && !(s.model() != null && s.model().isSub()) ? "delay" : rangeKeys.get(i);
            return AudioSettings.rangeFactors.getOrDefault(key, 1.0) * AudioSettings.distanceCurve.maximum() / 192.0;
        }

        @Override public double volume(int i) { return i < volumes.length ? volumes[i] : 1; }
    }

    /** Brings a scene up to date with the world (cheap when nothing changed: one hash over the linked arrays). */
    private static void scene(Scene sc, AudioConsoleBlockEntity c, ClientLevel level, Playback pb) {
        long now = level.getGameTime();
        long sig = 1469598103934665603L;
        boolean moving = false;
        for (BlockPos sp : c.speakers()) {
            sig = sig * 1099511628211L ^ sp.asLong();
            sig = sig * 1099511628211L ^ Float.floatToIntBits(c.groupDelay(AudioGroups.DELAY));
            if (level.getBlockEntity(sp) instanceof SpeakerBlockEntity a && pb.pos.equals(a.console()) && !a.cabinets().isEmpty()) {
                sig = sig * 1099511628211L ^ (a.revision() * 31L + a.group()) ^ ((long) Float.floatToIntBits(a.yaw()) << 20) ^ Float.floatToIntBits(a.volume());
            } else if (sc.lastSeen.containsKey(sp) && now - sc.lastSeenTick.getOrDefault(sp, now) < CARRY_TICKS) {
                moving = true;
                sig = sig * 1099511628211L ^ 0x5bd1e995L;
            }
        }
        if (sig != sc.signature || moving || sc.moving) {
            sc.signature = sig;
            sc.moving = moving;
            sc.sources.clear(); sc.positions.clear(); sc.rangeKeys.clear();
            List<Float> vols = new ArrayList<>();
            for (BlockPos sp : c.speakers()) {
                SoundSystems.Source src = null; float vol = 1; String key = null;
                if (level.getBlockEntity(sp) instanceof SpeakerBlockEntity a && pb.pos.equals(a.console()) && !a.cabinets().isEmpty()) {
                    src = source(sp, a);
                    if (src == null) continue;
                    sc.lastSeen.put(sp, src); sc.lastSeenTick.put(sp, now);
                    vol = a.volume();
                    var m = a.firstModel();
                    int g = a.group();
                    key = m.isSub() ? "subs" : m.isMonitor() ? "monitors" : c.groupDelay(g) > 0 ? "delay" : g == AudioGroups.FRONT_FILL ? "front_fill" : g == AudioGroups.DELAY ? "delay" : m.family();
                } else {
                    // carried by a lift: heard where the lift has taken it (positions of the cabinets as they were, moved up or down)
                    SoundSystems.Source last = sc.lastSeen.get(sp);
                    if (last == null || now - sc.lastSeenTick.getOrDefault(sp, now) >= CARRY_TICKS) continue;
                    double dy = Double.NaN;
                    for (var lift : level.getEntitiesOfClass(com.concertstage.lift.LiftStructureEntity.class, new net.minecraft.world.phys.AABB(sp).inflate(2, 160, 2))) {
                        dy = lift.travelled(sp, now);
                        if (!Double.isNaN(dy)) break;
                    }
                    if (Double.isNaN(dy)) continue;                     // not on a lift (broken, unloaded): silent as before
                    src = new SoundSystems.Source(last.id(), last.group(), last.model(), last.cabinets(), last.lengthM(), last.x(), last.y() + dy, last.z(),
                        last.yaw(), last.pitchTop(), last.pitchBottom());
                    var m = last.model();
                    key = m.isSub() ? "subs" : m.isMonitor() ? "monitors" : last.group() == AudioGroups.FRONT_FILL ? "front_fill" : last.group() == AudioGroups.DELAY ? "delay" : m.family();
                }
                sc.sources.add(src); sc.positions.add(sp); sc.rangeKeys.add(key); vols.add(vol);
            }
            sc.volumes = new float[vols.size()];
            for (int i = 0; i < vols.size(); i++) sc.volumes[i] = vols.get(i);
            // forget arrays not linked any more
            sc.lastSeen.keySet().retainAll(new java.util.HashSet<>(c.speakers()));
            sc.lastSeenTick.keySet().retainAll(sc.lastSeen.keySet());
        }
        // classification: only when the arrays' places / aims / groups or the settings change (a lift moving up or down keeps it)
        long key = 17;
        for (SoundSystems.Source s : sc.sources)
            key = key * 31 + (s.id() * 1000003L ^ s.group() * 7919L ^ Double.doubleToLongBits(s.yaw()) ^ (s.model() == null ? 0 : s.model().id().hashCode()) ^ Math.round(s.x() * 4) * 92821L ^ Math.round(s.z() * 4) * 68917L);
        if (key != sc.layoutKey) {
            long t0 = System.nanoTime();
            // 1.3.0 rev. 4 (asked 13 November: "the delay towers as in 1.1.0, the rest of the sound as in 1.1.1"): no tower recognised,
            // no automatic delay, no placement advice; a tower is what the player makes of it, as in 1.1.0: arrays in the Delay group
            // (or any group with a delay) of the console, with the group's delay set on the console or by the CSC Delay Planner, and the
            // reach of the "delay" range factor. The 1.1.1 levels, laws, subs and mixer are unchanged.
            sc.layout = SoundSystems.classify(sc.sources, SoundSystems.Mode.OFF, 0);
            sc.advice = com.concertstage.audio.core.TowerAdvice.Advice.NONE;
            sc.classifyNanos = System.nanoTime() - t0;
            sc.classifications++;
            sc.layoutKey = key;
            tell(sc, pb.pos);
            if (AudioSettings.diagnosticsLog) {
                for (SoundSystems.Installation i : sc.layout.installations())
                    diagnosticEvent("console " + pb.pos.toShortString() + " installation " + i.index() + " " + i.role() + " arrays=" + i.arrays()
                        + String.format(java.util.Locale.ROOT, " at %.0f %.0f %.0f delay %.1f ms: %s", i.x(), i.y(), i.z(), i.delayMs(), i.reason()));
            }
        }
    }

    /** The towers of a console and where they should be, as lines of text (chat, Diag. tab). */
    static List<String> towerText(Scene sc) {
        List<String> out = new ArrayList<>();
        var a = sc.advice;
        if (!sc.layout.hasMain()) return out;
        StringBuilder delays = new StringBuilder();
        int n = 0;
        for (SoundSystems.Installation i : sc.layout.installations())
            if (i.role() == SoundSystems.Role.TOWER) {
                double fwd = (i.x() - sc.layout.mainX()) * sc.layout.aimX() + (i.z() - sc.layout.mainZ()) * sc.layout.aimZ();
                delays.append(n++ == 0 ? "" : ", ").append(String.format(java.util.Locale.ROOT, Tr.t("%.0f blocks: %.1f ms"), fwd, i.delayMs()));
            }
        if (n > 0) out.add(Tr.f("Delay towers recognised: %d (%s), delays set automatically", n, delays.toString()));
        if (!a.recommended().isEmpty()) {
            StringBuilder r = new StringBuilder();
            for (int k = 0; k < a.recommended().size(); k++) r.append(k == 0 ? "" : ", ").append(Math.round(a.recommended().get(k)));
            out.add(Tr.f("Where to put the towers: about %s blocks in front of the mains", r.toString())
                + (Double.isNaN(a.spacing()) ? "" : Tr.f(" (one every %.0f blocks)", a.spacing())));
        } else if (n == 0) out.add(Tr.t("No delay tower needed: the mains cover the whole audience"));
        for (var b : a.built()) {
            if (Double.isNaN(b.offset())) out.add(Tr.f("Tower %d (%.0f blocks): not needed by the mains' coverage", b.tower() + 1, b.distance()));
            else if (Math.abs(b.offset()) <= 10) out.add(Tr.f("Tower %d (%.0f blocks): well placed", b.tower() + 1, b.distance()));
            else out.add(Tr.f(b.offset() > 0 ? "Tower %d (%.0f blocks): %.0f blocks too far, move it towards the stage" : "Tower %d (%.0f blocks): %.0f blocks too close, move it away from the stage",
                b.tower() + 1, b.distance(), Math.abs(b.offset())));
        }
        if (!a.built().isEmpty() && a.dipDb() > 3)
            out.add(Tr.f("Coverage: the level drops %.0f dB too low around %.0f blocks", a.dipDb(), a.dipAt()));
        return out;
    }

    /** Tells the player in the chat when the installation of a console near him changes (once per change). */
    private static void tell(Scene sc, BlockPos console) {
        if (!AudioSettings.towerHints || !sc.layout.hasMain()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dx = mc.player.getX() - sc.layout.mainX(), dz = mc.player.getZ() - sc.layout.mainZ();
        if (dx * dx + dz * dz > 160 * 160) return;
        List<String> lines = towerText(sc);
        if (sc.layout.towers() == 0 && sc.advice.recommended().isEmpty()) return;
        String text = String.join("\n", lines);
        if (text.equals(sc.told)) return;
        sc.told = text;
        for (String l : lines) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("[CSC] " + l).withStyle(net.minecraft.ChatFormatting.AQUA), false);
    }

    /** The pure record of an array (acoustic centre, aim, size). */
    private static SoundSystems.Source source(BlockPos sp, SpeakerBlockEntity a) {
        var m = a.firstModel();
        if (m == null) return null;
        Vec3[] axis = a.acousticAxis();
        double length = 0;
        List<SpeakerBlockEntity.Pose> poses = a.poses();
        for (SpeakerBlockEntity.Pose p : poses) length += p.model().height();
        double top = poses.isEmpty() ? 0 : poses.get(0).pitch(), bottom = poses.isEmpty() ? 0 : poses.get(poses.size() - 1).pitch();
        return new SoundSystems.Source(sp.asLong(), a.group(), m, a.cabinets().size(), length, axis[0].x, axis[0].y, axis[0].z, a.yaw(), top, bottom);
    }

    /** Diagnostics: installations of the consoles heard (Diag. tab). */
    public static List<String> installations() {
        List<String> out = new ArrayList<>();
        for (var e : SCENES.entrySet()) {
            Scene sc = e.getValue();
            int towers = sc.layout.towers();
            out.add(Tr.f("Console %s: %d arrays, %d delay towers (classified %d x, last %.2f ms)", e.getKey().toShortString(), sc.sources.size(), towers,
                sc.classifications, sc.classifyNanos / 1e6));
            out.addAll(towerText(sc));
            for (SoundSystems.Installation i : sc.layout.installations()) {
                if (i.role() == SoundSystems.Role.MAIN) continue;
                double lvl = i.index() < sc.result.installationLevel.length ? sc.result.installationLevel[i.index()] : 0;
                out.add(String.format(java.util.Locale.ROOT, "  %s %d: %d arrays, %s, %.0f ms, here %.1f dB", i.role() == SoundSystems.Role.TOWER ? Tr.t("Delay tower") : Tr.t("Other"),
                    i.index(), i.arrays(), Tr.t(i.reason()), i.delayMs(), lvl > 0 ? 20 * Math.log10(lvl) : -99.0));
            }
        }
        return out;
    }

    private static void update(Playback pb, AudioConsoleBlockEntity c, double gameTime, Vec3 ear) {
        AudioConsoleBlockEntity.Track t = c.currentTrack();
        boolean play = c.state() == AudioConsoleBlockEntity.PLAYING && t != null;
        pb.eqLow = c.eq(0); pb.eqMid = c.eq(1); pb.eqHigh = c.eq(2);
        pb.subs = c.drivesSubs();
        String sha = play ? t.sha1() : null;
        if (c.session() != pb.session) {
            pb.close();
            pb.session = c.session();
            pb.sha1 = sha;
            pb.waitingForFile = false;
            if (play) pb.anchorNanos = System.nanoTime() - c.positionMs(gameTime) * 1_000_000L;
        }
        if (!java.util.Objects.equals(sha,pb.sha1) || c.startTick() != pb.observedStart) {
            if (pb.monitor != null) pb.monitor.close();
            if (pb.pcm != null) pb.pcm.close();
            pb.monitor = null; pb.pcm = null; pb.sha1 = sha; pb.observedStart=c.startTick();
            pb.anchorNanos = System.nanoTime() - c.positionMs(gameTime) * 1_000_000L;
        }
        // the plan is built again only when the playlist or the repeat mode changed (not every tick: 64 paths and lists per console)
        int key = c.repeatMode() * 31 + c.playlist().size();
        for (AudioConsoleBlockEntity.Track track : c.playlist()) key = key * 31 + track.sha1().hashCode();
        if (pb.preparedPlan == null || key != pb.planKey) {
            pb.planKey = key;
            pb.preparedPlan = new com.concertstage.audio.core.PlaylistPcm.Plan(c.playlist().stream()
                .map(track -> new com.concertstage.audio.core.PlaylistPcm.Entry(track.sha1(),AudioCache.file(track.sha1(),track.type()),track.type())).toList(),c.repeatMode());
            if (pb.continuous != null) pb.continuous.configure(pb.preparedPlan);
        }
        pb.preparedIndex = c.current();
        pb.playing = play;
        if (!play) { java.util.Arrays.fill(pb.vuGroup, 0); pb.vuL = pb.vuR = 0; return; }
        long ms = c.positionMs(gameTime);
        long drift = pb.nowMs() - ms;
        if (Math.abs(drift) > 150) {pb.anchorNanos = System.nanoTime() - ms * 1_000_000L;clockCorrections++;diagnosticEvent("clock correction "+drift+" ms");}    // keep to the game clock
        boolean near = Math.sqrt(c.getBlockPos().distToCenterSqr(ear)) < 220 || pos(c).equals(watched);
        if (!near) for (BlockPos sp : c.speakers()) {
            if (c.getLevel().getBlockEntity(sp) instanceof SpeakerBlockEntity array
                    && array.acousticAxis()[0].distanceToSqr(ear) < 512 * 512) { near = true; break; }
        }
        if (!near && pb.pcm == null) return;
        // is the file here? once per track, then once a second while it is being received (each check touches the disk)
        if (!t.sha1().equals(pb.checkedSha)) { pb.checkedSha = t.sha1(); pb.fileReady = false; pb.recheck = 0; }
        if (!pb.fileReady && --pb.recheck <= 0) {
            pb.recheck = 20;
            pb.fileReady = AudioCache.has(t.sha1(), t.type());
            pb.waitingForFile = !pb.fileReady;
            if (!pb.fileReady) Transfers.request(c.getBlockPos(), t.sha1(), t.type());
        }
        // the meters' own decoder runs only while they can be seen: console screen open, or the desk close by (its 3D meters);
        // the sound itself comes from the playlist stream (memory and CPU: one decoder per console instead of two)
        boolean meters = pos(c).equals(watched) || c.getBlockPos().distToCenterSqr(ear) < 32 * 32;
        if (!meters && pb.pcm != null) {
            if (pb.monitor != null) pb.monitor.close();
            pb.pcm.close();
            pb.pcm = null; pb.monitor = null;
        }
        if (meters && pb.pcm == null && pb.fileReady) {
            pb.pcm = new TrackPcm(AudioCache.file(t.sha1(), t.type()), t.type(), pb.nowMs());
            pb.monitor = pb.pcm.reader(0);
        }
        // Anticipate the immediate successor through the existing authorised SHA-1 cache transfer.
        // Download prefetch is distinct from a future continuous PCM queue.
        int nextIndex = c.nextTrackIndex();
        if (pb.fileReady && (pb.prefetchTick = (pb.prefetchTick + 1) % 20) == 0 && nextIndex >= 0 && nextIndex < c.playlist().size()) {      // once a second
            AudioConsoleBlockEntity.Track next = c.playlist().get(nextIndex);
            if (!AudioCache.has(next.sha1(), next.type())) Transfers.request(c.getBlockPos(), next.sha1(), next.type());
        }
        if (pb.monitor != null && pb.pcm != null && pb.pcm.ready()) pb.monitor.seek(pb.pcm.frameAt(pb.nowMs()));
    }

    private static BlockPos pos(AudioConsoleBlockEntity c) {
        return c.getBlockPos();
    }

    private static short[] meterBuf = new short[0];

    /** Real meters: peaks of the track at the playhead (about one tick of audio), through the faders and mutes of the console. */
    private static void meters(Playback pb, AudioConsoleBlockEntity c) {
        float l = 0, r = 0;
        if (pb.playing && pb.pcm != null && pb.pcm.ready() && pb.monitor != null) {
            int frames = Math.max(256, pb.pcm.sampleRate() / 20);
            if (meterBuf.length < frames * 2) meterBuf = new short[frames * 2];
            long at = pb.pcm.frameAt(pb.nowMs()) - frames;
            if (pb.monitor.peek(Math.max(0, at), meterBuf, frames)) {
                for (int i = 0; i < frames; i++) {
                    l = Math.max(l, Math.abs(meterBuf[2 * i]) / 32768f);
                    r = Math.max(r, Math.abs(meterBuf[2 * i + 1]) / 32768f);
                }
            }
        }
        float m = c.master();
        pb.vuL = Math.max(pb.vuL * 0.82f, l * m);
        pb.vuR = Math.max(pb.vuR * 0.82f, r * m);
        for (int g = 0; g < AudioGroups.COUNT; g++) {
            int side = AudioGroups.side(g);
            float src = side < 0 ? l : side > 0 ? r : Math.max(l, r);
            float v = c.muted(g) ? 0 : src * m * c.fader(g);
            pb.vuGroup[g] = Math.max(pb.vuGroup[g] * 0.82f, v);
        }
    }

    /** Console streams currently retained (including a silent stream awaiting listener movement). */
    public static long inactiveSourceEvents(){return droppedSources;}

    public static float gainReductionDb(){float peak=0;for(Voice v:VOICES.values())peak=Math.max(peak,v.reductionDb);return peak;}

    public static int activeVoices() {
        return VOICES.size();
    }
}
