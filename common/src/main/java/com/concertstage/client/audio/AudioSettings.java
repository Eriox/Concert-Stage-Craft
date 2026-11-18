package com.concertstage.client.audio;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/**
 * Client settings of the sound system ({@code config/concertstage-audio.properties}): volume of the mod's speakers (on top of the
 * "Jukebox/Note Blocks" slider of Minecraft, which they also follow), how many consoles may play at once (Minecraft has 8 streamed
 * sources in all), whether tracks played on a server may be downloaded, and the size of the local cache.
 */
public final class AudioSettings {
    public static volatile com.concertstage.audio.core.SoundProfile profile=com.concertstage.audio.core.SoundProfile.DEFAULT;
    /**
     * Gain of the console output before its limiter. 1.1.1: 1 by default (the arrays now add up by systems, ArrayMix, so the front
     * of house no longer needs the x2.8 of 1.1.0, which pushed the limiter on every peak near the PA); a 1.1.0 setting is converted
     * once (divided by 2.8: the same choice relative to the default). See {@link #GAIN_MODEL}.
     */
    public static float outputGain = 1f;
    /** Version of the gain staging the saved output_gain belongs to (absent = 1.1.0). */
    public static final int GAIN_MODEL = 2;
    /**
     * 1.1.1: level of the mains / line arrays and of the monitors on this client (0..2, 1 = as the console sends them). The subwoofers
     * have theirs already: "Subwoofer level" (SoundProfile.subLevel, applied to the Subs group).
     */
    public static volatile float levelMains = 1f, levelMonitors = 1f;

    /** The level of this kind of speaker (levelMains / levelMonitors; 1 for subwoofers). */
    public static double typeLevel(com.concertstage.audio.SpeakerModels.Model m) {
        if (m == null || m.isSub()) return 1;
        return m.isMonitor() ? levelMonitors : levelMains;
    }
    public static float volume = 1f;
    // ---------------------------------------------------------------- 1.1.1 audio fix
    /** Attenuation curve: "gentle" (slopes x0.75), "realistic" (x1, the laws of each kind of speaker), "strong" (x1.3), "legacy" (the old single curve, its sliders). */
    public static volatile String attenuationCurve = "realistic";
    /** How much the sound moves between the ears with the place of the speakers (0 = centred, 1 = full). */
    public static volatile float spatialIntensity = 1f;
    /** The sound of far installations arrives later (343 m/s, relative to the first heard): towers and mains line up where they overlap. */
    public static volatile boolean propagation = true;
    /** Delay towers get their delay from their distance to the main system (otherwise the console's Delay group delay). */
    public static volatile boolean autoDelays = true;
    /** Detection of the delay towers: automatic, only arrays put in the Delay group, or off (everything as its console group). */
    public static volatile com.concertstage.audio.core.SoundSystems.Mode towerMode = com.concertstage.audio.core.SoundSystems.Mode.AUTO;
    /** Added to the automatic delay of a tower (ms, 0..30): the mains stay a little ahead, so the image stays on the stage. */
    public static volatile float towerTrimMs = 10f;
    /** Crossover of the subs from the voicing of the model (80-100 Hz); off once the crossover slider is moved. */
    public static volatile boolean subCrossoverAuto = true;
    /** Towers heard more than 35 ms after the first sound (an echo) are turned down (see ListenerMix). */
    public static volatile boolean antiEcho = true;
    /** Tells in the chat which delay towers were recognised and where they should stand. */
    public static volatile boolean towerHints = true;

    /** Slope multiplier of the attenuation curve (NaN for "legacy"). */
    public static double curveScale() {
        return switch (attenuationCurve) { case "gentle" -> .75; case "strong" -> 1.3; case "legacy" -> Double.NaN; default -> 1; };
    }

    /** What ListenerMix needs of these settings. */
    public static com.concertstage.audio.core.ListenerMix.Settings listenerSettings() {
        double scale = curveScale();
        return new com.concertstage.audio.core.ListenerMix.Settings(distanceAttenuation, directionality, Double.isNaN(scale) ? 1 : scale,
            Double.isNaN(scale) ? distanceCurve : null, volume, levelMains, levelMonitors, 1, spatialIntensity, propagation, autoDelays, antiEcho);
    }
    public static int maxStreams = 4;
    public static boolean downloads = true;
    public static int cacheMb = 1024;
    public static volatile boolean outdoor=true, distanceAttenuation=true, directionality=true, airAbsorption=true;
    public static volatile boolean diagnosticsLog=false;
    public static volatile com.concertstage.audio.core.DistanceCurve distanceCurve=new com.concertstage.audio.core.DistanceCurve(12,192,.65,1);
    public static volatile float smoothingMs=60;
    public static volatile int streamBufferMs=250;
    public static final java.util.Map<String,Double> rangeFactors=new java.util.concurrent.ConcurrentHashMap<>(java.util.Map.of("veyra",1.0,"noryx",.85,"orvane",1.0,"subs",1.0,"front_fill",.6,"delay",1.0,"monitors",.22,"aurex_main",1.0));
    // 1.1.1: monitors (AUREX M15-W) carry about 25 blocks — the stage, not the audience (1.1.0: the 118 blocks of a main)
    /** 1.1.0: obstacles between the arrays and the listener, room reverb, and the quality profile that set them. */
    public static volatile boolean occlusion = true, reverb = true;
    public static volatile int occlusionInterval = 4;
    public static volatile float reverbAmount = 1f;
    /** aurex.2: concert realism (echo of the place, glue, concert bass, wind) and its two amounts (0..1). */
    public static volatile boolean concert = true;
    public static volatile float concertGlue = .5f, concertBass = .5f;
    /** Listening EQ: frequency of each of the five bands (shelf 90 Hz, bells 300 / 1 k / 3 k, shelf 8 kHz) and the bells' Q. */
    public static volatile float[] eqHz = {90, 300, 1000, 3000, 8000};
    public static volatile float eqQ = .8f;
    /** "performance", "balanced", "high" or "custom" (something was changed by hand after choosing a profile). */
    public static volatile String quality = "high";

    /**
     * Quality profile: sets in one go the sound settings that cost the most and the render quality of the mod's blocks.
     * Performance: 2 consoles, obstacles checked every 10 ticks, no reverb, low render quality. Balanced: 3 consoles, every 5 ticks,
     * reverb, medium. High quality: 4 consoles, every 2 ticks, reverb, high. The PCM block (stability) is not touched.
     */
    public static void applyQuality(String q) {
        switch (q) {
            case "performance" -> { maxStreams = 2; occlusion = true; occlusionInterval = 10; reverb = false; com.concertstage.client.RenderQuality.set(com.concertstage.client.RenderQuality.LOW); }
            case "balanced" -> { maxStreams = 3; occlusion = true; occlusionInterval = 5; reverb = true; com.concertstage.client.RenderQuality.set(com.concertstage.client.RenderQuality.MEDIUM); }
            default -> { q = "high"; maxStreams = 4; occlusion = true; occlusionInterval = 2; reverb = true; com.concertstage.client.RenderQuality.set(com.concertstage.client.RenderQuality.HIGH); }
        }
        quality = q;
        save();
    }

    private static boolean loaded;

    private AudioSettings() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("concertstage-audio.properties");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Properties p = new Properties();
        try {
            Path f = file();
            if (Files.isRegularFile(f)) try (Reader r = Files.newBufferedReader(f)) { p.load(r); }
            // 1.1.1 audio fix (absent from older files: the defaults)
            String curve = p.getProperty("attenuation_curve", "realistic").trim();
            attenuationCurve = java.util.List.of("gentle", "realistic", "strong", "legacy").contains(curve) ? curve : "realistic";
            try { float v = Float.parseFloat(p.getProperty("spatial_intensity", "1")); spatialIntensity = Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 1; } catch (NumberFormatException ignored) { }
            propagation = Boolean.parseBoolean(p.getProperty("sound_propagation", "true").trim());
            autoDelays = Boolean.parseBoolean(p.getProperty("delay_towers_auto_delay", "true").trim());
            try { towerMode = com.concertstage.audio.core.SoundSystems.Mode.valueOf(p.getProperty("delay_towers", "AUTO").trim().toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException ignored) { towerMode = com.concertstage.audio.core.SoundSystems.Mode.AUTO; }
            try { float v = Float.parseFloat(p.getProperty("delay_towers_trim_ms", "10")); towerTrimMs = Float.isFinite(v) ? Math.max(0, Math.min(30, v)) : 10; } catch (NumberFormatException ignored) { }
            subCrossoverAuto = Boolean.parseBoolean(p.getProperty("sub_crossover_auto", "true").trim());
            antiEcho = Boolean.parseBoolean(p.getProperty("anti_echo", "true").trim());
            towerHints = Boolean.parseBoolean(p.getProperty("delay_tower_hints", "true").trim());
            for(String key:rangeFactors.keySet())try{double value=Double.parseDouble(p.getProperty("range_factor."+key,String.valueOf(rangeFactors.get(key))));if(Double.isFinite(value))rangeFactors.put(key,Math.max(.1,Math.min(4,value)));}catch(NumberFormatException ignored){}
            streamBufferMs=Math.max(40,Math.min(500,Integer.parseInt(p.getProperty("stream_buffer_ms","250"))));
            diagnosticsLog=Boolean.parseBoolean(p.getProperty("diagnostics_log","false"));
            distanceCurve=new com.concertstage.audio.core.DistanceCurve(Double.parseDouble(p.getProperty("reference_distance","12")),Double.parseDouble(p.getProperty("maximum_distance","192")),Double.parseDouble(p.getProperty("rolloff","0.65")),Double.parseDouble(p.getProperty("speaker_gain","1")));
            smoothingMs=Float.parseFloat(p.getProperty("gain_smoothing_ms","60"));if(!Float.isFinite(smoothingMs))smoothingMs=60;smoothingMs=Math.max(5,Math.min(500,smoothingMs));
            outdoor=Boolean.parseBoolean(p.getProperty("outdoor","true"));distanceAttenuation=Boolean.parseBoolean(p.getProperty("distance_attenuation","true"));directionality=Boolean.parseBoolean(p.getProperty("directionality","true"));airAbsorption=Boolean.parseBoolean(p.getProperty("air_absorption","true"));
            for(int i=0;i<13;i++)try {profile=profile.with(i,Float.parseFloat(p.getProperty("processing."+i,String.valueOf(profile.value(i)))));}catch(NumberFormatException ignored){}
            volume = Math.max(0, Math.min(1, Float.parseFloat(p.getProperty("volume", "1"))));
            if (!Float.isFinite(volume)) volume = 1f;
            outputGain = Float.parseFloat(p.getProperty("output_gain", "1"));
            if (!Float.isFinite(outputGain)) outputGain = 1f;
            if (p.getProperty("output_gain") != null && !String.valueOf(GAIN_MODEL).equals(p.getProperty("gain_model"))) outputGain /= 2.8f;   // 1.1.0 file
            outputGain = Math.max(.25f, Math.min(4, outputGain));
            levelMains = level(p, "level_mains"); levelMonitors = level(p, "level_monitors");
            maxStreams = Math.max(1, Math.min(6, Integer.parseInt(p.getProperty("max_streams", "4").trim())));
            downloads = Boolean.parseBoolean(p.getProperty("downloads", "true").trim());
            occlusion = Boolean.parseBoolean(p.getProperty("occlusion", "true").trim());
            reverb = Boolean.parseBoolean(p.getProperty("reverb", "true").trim());
            occlusionInterval = Math.max(1, Math.min(20, Integer.parseInt(p.getProperty("occlusion_interval_ticks", "4").trim())));
            reverbAmount = Float.parseFloat(p.getProperty("reverb_amount", "1"));
            concert = Boolean.parseBoolean(p.getProperty("concert_realism", "true").trim());
            try { concertGlue = Math.max(0, Math.min(1, Float.parseFloat(p.getProperty("concert_glue", "0.5")))); } catch (NumberFormatException ignored) { }
            try { concertBass = Math.max(0, Math.min(1, Float.parseFloat(p.getProperty("concert_bass", "0.5")))); } catch (NumberFormatException ignored) { }
            if (!Float.isFinite(concertGlue)) concertGlue = .5f;
            if (!Float.isFinite(concertBass)) concertBass = .5f;
            if (!Float.isFinite(reverbAmount)) reverbAmount = 1f;
            reverbAmount = Math.max(0, Math.min(2, reverbAmount));
            quality = p.getProperty("quality", "high").trim();
            float[] hz = eqHz.clone();
            for (int b = 0; b < 5; b++) try { float v = Float.parseFloat(p.getProperty("eq_hz." + b, String.valueOf(hz[b]))); if (Float.isFinite(v)) hz[b] = Math.max(20, Math.min(20000, v)); } catch (NumberFormatException ignored) { }
            eqHz = hz;
            try { float q = Float.parseFloat(p.getProperty("eq_q", "0.8")); if (Float.isFinite(q)) eqQ = Math.max(.3f, Math.min(4f, q)); } catch (NumberFormatException ignored) { }
            cacheMb = Math.max(64, Math.min(20000, Integer.parseInt(p.getProperty("cache_mb", "1024").trim())));
        } catch (IOException | RuntimeException ignored) {
            // defaults
        }
    }

    private static float level(Properties p, String key) {
        try {
            float v = Float.parseFloat(p.getProperty(key, "1"));
            return Float.isFinite(v) ? Math.max(0, Math.min(2, v)) : 1f;
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    public static void save() {
        Properties p = new Properties();
        rangeFactors.forEach((key,value)->p.setProperty("range_factor."+key,String.valueOf(value)));
        p.setProperty("stream_buffer_ms",String.valueOf(streamBufferMs));
        p.setProperty("diagnostics_log",String.valueOf(diagnosticsLog));
        p.setProperty("reference_distance",String.valueOf(distanceCurve.reference()));p.setProperty("maximum_distance",String.valueOf(distanceCurve.maximum()));p.setProperty("rolloff",String.valueOf(distanceCurve.rolloff()));p.setProperty("speaker_gain",String.valueOf(distanceCurve.output()));p.setProperty("gain_smoothing_ms",String.valueOf(smoothingMs));
        p.setProperty("outdoor",String.valueOf(outdoor));p.setProperty("distance_attenuation",String.valueOf(distanceAttenuation));p.setProperty("directionality",String.valueOf(directionality));p.setProperty("air_absorption",String.valueOf(airAbsorption));
        for(int i=0;i<13;i++)p.setProperty("processing."+i,String.valueOf(profile.value(i)));
        p.setProperty("output_gain", String.valueOf(outputGain));
        p.setProperty("gain_model", String.valueOf(GAIN_MODEL));
        p.setProperty("level_mains", String.valueOf(levelMains));
        p.setProperty("level_monitors", String.valueOf(levelMonitors));
        p.setProperty("volume", String.valueOf(volume));
        p.setProperty("max_streams", String.valueOf(maxStreams));
        p.setProperty("downloads", String.valueOf(downloads));
        p.setProperty("occlusion", String.valueOf(occlusion));
        p.setProperty("reverb", String.valueOf(reverb));
        p.setProperty("occlusion_interval_ticks", String.valueOf(occlusionInterval));
        p.setProperty("reverb_amount", String.valueOf(reverbAmount));
        p.setProperty("concert_realism", String.valueOf(concert));
        p.setProperty("concert_glue", String.valueOf(concertGlue));
        p.setProperty("concert_bass", String.valueOf(concertBass));
        p.setProperty("quality", quality);
        for (int b = 0; b < 5; b++) p.setProperty("eq_hz." + b, String.valueOf(eqHz[b]));
        p.setProperty("eq_q", String.valueOf(eqQ));
        p.setProperty("cache_mb", String.valueOf(cacheMb));
        p.setProperty("attenuation_curve", attenuationCurve);
        p.setProperty("spatial_intensity", String.valueOf(spatialIntensity));
        p.setProperty("sound_propagation", String.valueOf(propagation));
        p.setProperty("delay_towers_auto_delay", String.valueOf(autoDelays));
        p.setProperty("delay_towers", towerMode.name().toLowerCase(java.util.Locale.ROOT));
        p.setProperty("delay_towers_trim_ms", String.valueOf(towerTrimMs));
        p.setProperty("sub_crossover_auto", String.valueOf(subCrossoverAuto));
        p.setProperty("anti_echo", String.valueOf(antiEcho));
        p.setProperty("delay_tower_hints", String.valueOf(towerHints));
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f)) { p.store(w, "Concert Stage Craft - sound system (client)"); }
        } catch (IOException ignored) {
            // kept for this session
        }
    }
}
