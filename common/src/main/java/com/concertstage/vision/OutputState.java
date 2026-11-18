package com.concertstage.vision;

import com.concertstage.light.core.Bytes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

/**
 * CSC 1.3.0 - one output of a CSC VISION PROCESSOR VP1: its PROGRAM (what the walls show) and PREVIEW (the next source), the running
 * transition (CUT / DISSOLVE / FADE, 0.2 to 3 s, timed on the game clock so every player sees it at the same moment), the media
 * playback (start time, pause, loop: a player coming back to a wall sees the right moment of the video), the brightness, and the
 * automatic camera switcher (interval 2 to 120 s, sequential or random without immediate repeat, cameras chosen, pause, the manual
 * TAKE always wins and restarts the countdown). An output can mirror another one (same program, shared images).
 *
 * <p>Sources are strings: {@code black}, {@code test:bars}, {@code test:grid}, {@code cam:x,y,z} (a camera block), {@code media:name}.
 */
public final class OutputState {
    public static final int CUT = 0, DISSOLVE = 1, FADE = 2;
    public static final String BLACK = "black", BARS = "test:bars", GRID = "test:grid";

    public String program = BARS, preview = BLACK, from = BLACK;
    public int transition = DISSOLVE;
    /** Transition length in ticks (4 to 60 = 0.2 to 3 s). */
    public int duration = 20;
    /** Game time at which the last transition started (to program from {@link #from}). */
    public long transStart = -1000;
    public int brightness = 100;
    /** Output whose program this one copies (-1: its own). */
    public int mirrorOf = -1;
    // media
    public long mediaStart;
    public long pausedAt = -1;
    public boolean loop = true;
    // automatic switcher
    public boolean auto;
    public boolean autoPaused;
    public boolean random;
    public int interval = 10;
    public long nextSwitch;
    public List<Long> autoCams = new ArrayList<>();
    public String lastAuto = "";

    public static String cam(net.minecraft.core.BlockPos p) { return "cam:" + p.getX() + "," + p.getY() + "," + p.getZ(); }

    public static net.minecraft.core.BlockPos camPos(String src) {
        if (src == null || !src.startsWith("cam:")) return null;
        try {
            String[] a = src.substring(4).split(",");
            return new net.minecraft.core.BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Progress of the running transition at this game time (1 = finished). */
    public float progress(double gameTime) {
        if (transition == CUT) return 1f;
        double t = (gameTime - transStart) / Math.max(1, duration);
        return (float) Math.max(0, Math.min(1, t));
    }

    /** Playback position of the media in seconds at this game time. */
    public double mediaSeconds(double gameTime) {
        return ((pausedAt >= 0 ? pausedAt : gameTime) - mediaStart) / 20.0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Program", program); t.putString("Preview", preview); t.putString("From", from);
        t.putInt("Transition", transition); t.putInt("Duration", duration); t.putLong("TransStart", transStart);
        t.putInt("Brightness", brightness); t.putInt("Mirror", mirrorOf);
        t.putLong("MediaStart", mediaStart); t.putLong("PausedAt", pausedAt); t.putBoolean("Loop", loop);
        t.putBoolean("Auto", auto); t.putBoolean("AutoPaused", autoPaused); t.putBoolean("Random", random); t.putInt("Interval", interval);
        t.putLong("NextSwitch", nextSwitch); t.put("AutoCams", new LongArrayTag(autoCams)); t.putString("LastAuto", lastAuto);
        return t;
    }

    public static OutputState load(CompoundTag t) {
        OutputState o = new OutputState();
        if (t.contains("Program")) o.program = t.getString("Program");
        if (t.contains("Preview")) o.preview = t.getString("Preview");
        if (t.contains("From")) o.from = t.getString("From");
        o.transition = t.getInt("Transition"); o.duration = Math.max(4, Math.min(60, t.contains("Duration") ? t.getInt("Duration") : 20));
        o.transStart = t.getLong("TransStart"); o.brightness = t.contains("Brightness") ? t.getInt("Brightness") : 100;
        o.mirrorOf = t.contains("Mirror") ? t.getInt("Mirror") : -1;
        o.mediaStart = t.getLong("MediaStart"); o.pausedAt = t.contains("PausedAt") ? t.getLong("PausedAt") : -1; o.loop = !t.contains("Loop") || t.getBoolean("Loop");
        o.auto = t.getBoolean("Auto"); o.autoPaused = t.getBoolean("AutoPaused"); o.random = t.getBoolean("Random");
        o.interval = Math.max(2, Math.min(120, t.contains("Interval") ? t.getInt("Interval") : 10));
        o.nextSwitch = t.getLong("NextSwitch");
        o.autoCams = new ArrayList<>();
        for (long l : t.getLongArray("AutoCams")) o.autoCams.add(l);
        o.lastAuto = t.getString("LastAuto");
        return o;
    }

    public void write(Bytes.Writer w) {
        w.str(program).str(preview).str(from).varInt(transition).varInt(duration).sLong(transStart).varInt(brightness).sInt(mirrorOf)
            .sLong(mediaStart).sLong(pausedAt).bool(loop).bool(auto).bool(autoPaused).bool(random).varInt(interval).sLong(nextSwitch)
            .varInt(autoCams.size());
        for (long c : autoCams) w.sLong(c);
    }

    public static OutputState read(Bytes.Reader r) {
        OutputState o = new OutputState();
        o.program = r.str(256); o.preview = r.str(256); o.from = r.str(256); o.transition = r.varInt(); o.duration = r.varInt();
        o.transStart = r.sLong(); o.brightness = r.varInt(); o.mirrorOf = r.sInt(); o.mediaStart = r.sLong(); o.pausedAt = r.sLong();
        o.loop = r.bool(); o.auto = r.bool(); o.autoPaused = r.bool(); o.random = r.bool(); o.interval = r.varInt(); o.nextSwitch = r.sLong();
        int n = r.count(64);
        for (int i = 0; i < n; i++) o.autoCams.add(r.sLong());
        return o;
    }
}
