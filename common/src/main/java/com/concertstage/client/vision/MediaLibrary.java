package com.concertstage.client.vision;

import com.concertstage.vision.OutputState;
import com.concertstage.vision.VisionNet;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;

/**
 * CSC 1.3.0 - the pictures shown by the walls: test patterns, images and animated GIF (sent by the server, or found in the player's
 * media folder), videos (read by FFmpeg from the player's media folder). Decoding is never done on the render thread: a worker
 * decodes, the render thread only uploads finished frames. Media not shown for a while are released when the memory budget of the
 * profile is exceeded.
 */
public final class MediaLibrary {
    /** A picture ready to draw: texture, and whether its rows go bottom-up (camera images). */
    public record Frame(int texture, boolean flipV, float aspect) {}

    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "CSC LiveVision media");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final ConcurrentLinkedQueue<Runnable> UPLOADS = new ConcurrentLinkedQueue<>();
    private static final Map<String, Media> MEDIA = new LinkedHashMap<>(16, 0.75f, true);
    private static VTex bars, grid;
    private static long usedBytes;

    private MediaLibrary() {}

    /** Folder of this player's media (videos, and images that need no server): {@code <game>/config/concertstage/livevision/media}. */
    public static Path localFolder() {
        Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("concertstage").resolve("livevision").resolve("media");
        try { Files.createDirectories(p); } catch (Exception ignored) { }
        return p;
    }

    private static Path find(String name) {
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        Path p = localFolder().resolve(name);
        if (Files.isRegularFile(p)) return p;
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            Path s = VisionNet.mediaFolder(server).resolve(name);
            if (Files.isRegularFile(s)) return s;
        }
        return null;
    }

    // ------------------------------------------------------------------ frames
    /** The picture of {@code src} at this moment for this output, or null (black). */
    public static Frame frame(String src, OutputState o, double gameTime) {
        if (src == null || src.equals(OutputState.BLACK)) return null;
        if (src.equals(OutputState.BARS)) return new Frame(pattern(true).id(), false, 16f / 9f);
        if (src.equals(OutputState.GRID)) return new Frame(pattern(false).id(), false, 16f / 9f);
        if (src.startsWith("cam:")) return CameraFeeds.frame(OutputState.camPos(src));
        if (src.startsWith("media:")) {
            String name = src.substring(6);
            Media m = MEDIA.get(name);
            if (m == null) {
                m = new Media(name);
                MEDIA.put(name, m);
                m.start();
            }
            double t = o == null ? gameTime / 20.0 : o.mediaSeconds(gameTime);
            boolean loop = o == null || o.loop;
            return m.frame(t, loop);
        }
        return null;
    }

    /** State of a media for the regie: "", "loading", "missing", "ffmpeg". */
    public static String state(String name) {
        Media m = MEDIA.get(name);
        return m == null ? "" : m.error != null ? m.error : m.ready ? "" : "loading";
    }

    static void received(String name, byte[] data) {
        Media m = MEDIA.get(name);
        if (m != null && m.waitingServer) {
            m.waitingServer = false;
            WORKER.submit(() -> m.decodeImage(data));
        }
    }

    /** Render thread, every tick: uploads the decoded frames, keeps the memory budget. */
    public static void tick() {
        long t0 = System.nanoTime();
        Runnable r;
        int n = 0;
        while ((r = UPLOADS.poll()) != null) {
            r.run();
            if (++n > 64 || System.nanoTime() - t0 > 4_000_000) break;
        }
        long budget = (long) VisionSettings.memoryBudgetMb << 20;
        usedBytes = 0;
        for (Media m : MEDIA.values()) usedBytes += m.bytes();
        VisionStats.mediaBytes = usedBytes;
        long now = System.currentTimeMillis();
        Iterator<Media> it = MEDIA.values().iterator();      // least recently used first
        while (it.hasNext() && usedBytes > budget) {
            Media m = it.next();
            if (now - m.lastUse < 2000) break;
            usedBytes -= m.bytes();
            m.free();
            it.remove();
        }
        for (Media m : MEDIA.values()) if (m.video != null && now - m.lastUse > 5000) m.video.idle();
    }

    public static void clear() {
        for (Media m : MEDIA.values()) m.free();
        MEDIA.clear();
        UPLOADS.clear();
    }

    // ------------------------------------------------------------------ test patterns
    private static VTex pattern(boolean barsPattern) {
        VTex t = barsPattern ? bars : grid;
        if (t != null && t.id() >= 0) return t;
        int w = 640, h = 360;
        byte[] px = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = barsPattern ? barsColor(x, y, w, h) : gridColor(x, y, w, h);
                int o = (y * w + x) * 4;
                px[o] = (byte) (c >> 16); px[o + 1] = (byte) (c >> 8); px[o + 2] = (byte) c; px[o + 3] = (byte) 255;
            }
        }
        t = new VTex(w, h);
        t.upload(px);
        if (barsPattern) bars = t; else grid = t;
        return t;
    }

    /** SMPTE-like colour bars: 75 % bars, a reverse strip, then -I / white / Q / black steps (PLUGE). */
    private static int barsColor(int x, int y, int w, int h) {
        int[] top = {0xC0C0C0, 0xC0C000, 0x00C0C0, 0x00C000, 0xC000C0, 0xC00000, 0x0000C0};
        int[] mid = {0x0000C0, 0x131313, 0xC000C0, 0x131313, 0x00C0C0, 0x131313, 0xC0C0C0};
        int i = Math.min(6, x * 7 / w);
        if (y < h * 2 / 3) return top[i];
        if (y < h * 3 / 4) return mid[i];
        int j = x * 6 / w;
        int[] low = {0x00214C, 0xFFFFFF, 0x32006A, 0x131313, 0x090909, 0x1D1D1D};
        return low[Math.min(5, j)];
    }

    /** Alignment grid: 16 x 9 cells, centre cross, circle, corner markers (checks the mapping and the aspect of a wall). */
    private static int gridColor(int x, int y, int w, int h) {
        int cw = w / 16, ch = h / 9;
        boolean line = x % cw == 0 || y % ch == 0 || x == w - 1 || y == h - 1;
        double dx = x - w / 2.0, dy = y - h / 2.0;
        double r = Math.sqrt(dx * dx + dy * dy);
        boolean circle = Math.abs(r - h * 0.42) < 1.5;
        boolean cross = Math.abs(dx) < 1.5 || Math.abs(dy) < 1.5;
        boolean corner = (x < cw && y < ch) || (x >= w - cw && y < ch) || (x < cw && y >= h - ch) || (x >= w - cw && y >= h - ch);
        if (circle) return 0xFFD000;
        if (cross) return 0xFFFFFF;
        if (corner) return x < w / 2 ? (y < h / 2 ? 0xE02020 : 0x2060FF) : (y < h / 2 ? 0x20C040 : 0xFFFFFF);
        if (line) return 0xB0B0B0;
        int g = ((x / cw + y / ch) & 1) == 0 ? 0x202020 : 0x303030;
        return g;
    }

    // ------------------------------------------------------------------ one media
    private static final class Media {
        final String name;
        volatile boolean ready, waitingServer;
        volatile String error;
        long lastUse = System.currentTimeMillis();
        /** Still images and GIF: textures and the time each one ends (seconds); a still image has one frame. */
        final List<VTex> frames = new ArrayList<>();
        final List<Double> ends = new ArrayList<>();
        float aspect = 16f / 9f;
        VideoStream video;

        Media(String name) { this.name = name; }

        void start() {
            if (VisionNet.isVideo(name)) {
                Path p = find(name);
                if (p == null) { error = "missing"; return; }
                // 1.2.1: FFmpeg is built into the mod; the first video unpacks it (a few seconds, on a worker thread)
                WORKER.submit(() -> {
                    String ff = Ffmpeg.path();
                    if (ff == null) { error = "ffmpeg"; return; }
                    UPLOADS.add(() -> {
                        if (error != null) return;
                        video = new VideoStream(ff, p);
                        ready = true;
                    });
                });
                return;
            }
            Path p = find(name);
            if (p != null) {
                WORKER.submit(() -> {
                    try { decodeImage(Files.readAllBytes(p)); } catch (Exception e) { error = "missing"; }
                });
            } else {
                waitingServer = true;
                VisionClient.requestMedia(name);
            }
        }

        /** Worker thread: decodes a still image or every frame of a GIF, then hands the pixels to the render thread. */
        void decodeImage(byte[] data) {
            try {
                List<BufferedImage> imgs = new ArrayList<>();
                List<Double> delays = new ArrayList<>();
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) readGif(data, imgs, delays);
                if (imgs.isEmpty()) {
                    BufferedImage b = ImageIO.read(new ByteArrayInputStream(data));
                    if (b == null) b = Ffmpeg.decodeStill(data);                 // 1.2.1: WebP and other formats Java cannot read
                    if (b == null) { error = "missing"; return; }
                    imgs.add(b);
                    delays.add(1e9);
                }
                List<byte[]> px = new ArrayList<>();
                int w = 0, h = 0;
                for (BufferedImage b : imgs) {
                    BufferedImage s = fit(b, 1280, 1280);
                    w = s.getWidth(); h = s.getHeight();
                    px.add(rgba(s));
                    VisionStats.mediaDecoded++;
                }
                final int fw = w, fh = h;
                UPLOADS.add(() -> {
                    double t = 0;
                    for (int i = 0; i < px.size(); i++) {
                        VTex tex = new VTex(fw, fh);
                        tex.upload(px.get(i));
                        frames.add(tex);
                        t += delays.get(i);
                        ends.add(t);
                    }
                    aspect = fw / (float) Math.max(1, fh);
                    ready = true;
                });
            } catch (Throwable e) {
                error = "missing";
            }
        }

        Frame frame(double t, boolean loop) {
            lastUse = System.currentTimeMillis();
            if (video != null) {
                VTex tex = video.frame(t, loop);
                return tex == null || tex.id() < 0 ? null : new Frame(tex.id(), false, video.aspect());
            }
            if (!ready || frames.isEmpty()) return null;
            if (frames.size() == 1) return new Frame(frames.get(0).id(), false, aspect);
            double total = ends.get(ends.size() - 1);
            double tt = loop ? ((t % total) + total) % total : Math.min(Math.max(0, t), total - 1e-6);
            for (int i = 0; i < ends.size(); i++) if (tt < ends.get(i)) return new Frame(frames.get(i).id(), false, aspect);
            return new Frame(frames.get(frames.size() - 1).id(), false, aspect);
        }

        long bytes() {
            long b = 0;
            for (VTex t : frames) b += t.bytes();
            if (video != null) b += video.bytes();
            return b;
        }

        void free() {
            for (VTex t : frames) t.free();
            frames.clear();
            if (video != null) video.close();
        }
    }

    /** Scaled copy no larger than mw x mh (keeps the proportions), always TYPE_INT_ARGB. */
    static BufferedImage fit(BufferedImage b, int mw, int mh) {
        double k = Math.min(1.0, Math.min(mw / (double) b.getWidth(), mh / (double) b.getHeight()));
        int w = Math.max(1, (int) Math.round(b.getWidth() * k)), h = Math.max(1, (int) Math.round(b.getHeight() * k));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(b, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static byte[] rgba(BufferedImage b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] argb = b.getRGB(0, 0, w, h, null, 0, w);
        byte[] out = new byte[w * h * 4];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            out[i * 4] = (byte) (c >> 16); out[i * 4 + 1] = (byte) (c >> 8); out[i * 4 + 2] = (byte) c; out[i * 4 + 3] = (byte) 255;
        }
        return out;
    }

    /** Every frame of an animated GIF, composed on a canvas (frame offsets, "restore to background" disposal), with its delay. */
    private static void readGif(byte[] data, List<BufferedImage> out, List<Double> delays) throws Exception {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) return;
        ImageReader reader = readers.next();
        try (InputStream in = new ByteArrayInputStream(data); ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            reader.setInput(iis, false);
            int n = Math.min(reader.getNumImages(true), 600);
            BufferedImage canvas = null;
            for (int i = 0; i < n; i++) {
                BufferedImage f = reader.read(i);
                IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                int x = 0, y = 0, delay = 10;
                String disposal = "none";
                for (int c = 0; c < root.getLength(); c++) {
                    IIOMetadataNode node = (IIOMetadataNode) root.item(c);
                    if (node.getNodeName().equals("ImageDescriptor")) {
                        x = Integer.parseInt(node.getAttribute("imageLeftPosition"));
                        y = Integer.parseInt(node.getAttribute("imageTopPosition"));
                    } else if (node.getNodeName().equals("GraphicControlExtension")) {
                        delay = Integer.parseInt(node.getAttribute("delayTime"));
                        disposal = node.getAttribute("disposalMethod");
                    }
                }
                if (canvas == null) {
                    int w = f.getWidth() + x, h = f.getHeight() + y;
                    try {
                        IIOMetadataNode s = (IIOMetadataNode) reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
                        for (int c = 0; c < s.getLength(); c++) {
                            IIOMetadataNode node = (IIOMetadataNode) s.item(c);
                            if (node.getNodeName().equals("LogicalScreenDescriptor")) {
                                w = Math.max(w, Integer.parseInt(node.getAttribute("logicalScreenWidth")));
                                h = Math.max(h, Integer.parseInt(node.getAttribute("logicalScreenHeight")));
                            }
                        }
                    } catch (Exception ignored) { }
                    canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }
                var g = canvas.createGraphics();
                g.drawImage(f, x, y, null);
                g.dispose();
                BufferedImage copy = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g2 = copy.createGraphics();
                g2.setColor(java.awt.Color.BLACK);
                g2.fillRect(0, 0, copy.getWidth(), copy.getHeight());
                g2.drawImage(canvas, 0, 0, null);
                g2.dispose();
                out.add(copy);
                delays.add(Math.max(2, delay) / 100.0);
                if (disposal.equals("restoreToBackgroundColor")) {
                    var g3 = canvas.createGraphics();
                    g3.setComposite(java.awt.AlphaComposite.Clear);
                    g3.fillRect(x, y, f.getWidth(), f.getHeight());
                    g3.dispose();
                }
            }
        } finally {
            reader.dispose();
        }
    }

    // ------------------------------------------------------------------ FFmpeg
    /**
     * Finds FFmpeg. 1.2.1: FFmpeg is built into the mod (LGPL build of the JavaCPP Presets, see THIRD_PARTY.md): the program of this
     * computer's platform is unpacked once into {@code config/concertstage/livevision/ffmpeg-bundled/}. Order: a program the player put
     * as {@code config/concertstage/livevision/ffmpeg(.exe)}, the built-in one, then the system PATH.
     */
    static final class Ffmpeg {
        /** Version of the bundled build (folder name: a new build is unpacked next to the old one). */
        static final String BUNDLE = "6.1.1-1.5.10";
        private static String found;
        private static boolean searched;
        private static String source = "none";

        static synchronized String path() {
            if (searched) return found;
            searched = true;
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("concertstage").resolve("livevision");
            for (String n : new String[] {"ffmpeg.exe", "ffmpeg"}) {
                Path p = dir.resolve(n);
                if (Files.isRegularFile(p)) { found = p.toString(); source = "player"; return found; }
            }
            try {
                String b = bundled(dir.resolve("ffmpeg-bundled"));
                if (b != null && works(b)) { found = b; source = "built-in"; return found; }
                if (b != null) com.mojang.logging.LogUtils.getLogger().warn("[CSC] built-in FFmpeg does not start: {}", b);
            } catch (Throwable e) {
                com.mojang.logging.LogUtils.getLogger().warn("[CSC] built-in FFmpeg not usable: {}", e.toString());
            }
            if (works("ffmpeg")) { found = "ffmpeg"; source = "PATH"; }
            return found;
        }

        /** Where the FFmpeg in use comes from (for the log and the regie). */
        static synchronized String source() { return source; }

        /** windows-x86_64, linux-x86_64, macosx-x86_64, macosx-arm64, or null. */
        static String platform() {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
            boolean arm = arch.contains("aarch64") || arch.contains("arm64");
            boolean x64 = arch.contains("amd64") || arch.contains("x86_64");
            if (os.contains("win")) return x64 ? "windows-x86_64" : null;
            if (os.contains("mac") || os.contains("darwin")) return arm ? "macosx-arm64" : x64 ? "macosx-x86_64" : null;
            if (os.contains("linux")) return x64 ? "linux-x86_64" : null;
            return null;
        }

        /** Unpacks the built-in FFmpeg of this platform (once) and returns its program, or null when the mod has none for it. */
        private static String bundled(Path root) throws Exception {
            root = root.toAbsolutePath().normalize();                               // "./config" in a dev run: the check below needs it whole
            String plat = platform();
            if (plat == null) return null;
            Path dir = root.resolve(BUNDLE + "-" + plat);
            Path exe = dir.resolve(plat.startsWith("windows") ? "ffmpeg.exe" : "ffmpeg");
            Path done = dir.resolve(".complete");
            if (Files.isRegularFile(done) && Files.isRegularFile(exe)) return exe.toString();
            int files = 0;
            try (InputStream in = MediaLibrary.class.getResourceAsStream("/csc_ffmpeg/" + plat + ".zip")) {
                if (in == null) return null;
                Path tmp = root.resolve(BUNDLE + "-" + plat + ".part");
                deleteTree(tmp);
                Files.createDirectories(tmp);
                try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(in)) {
                    java.util.zip.ZipEntry e;
                    while ((e = zin.getNextEntry()) != null) {
                        if (e.isDirectory()) continue;
                        Path out = tmp.resolve(e.getName()).normalize();
                        if (!out.startsWith(tmp)) continue;                                // never outside the folder
                        Files.createDirectories(out.getParent());
                        Files.copy(zin, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        out.toFile().setExecutable(true, false);
                        files++;
                    }
                }
                deleteTree(dir);
                Files.move(tmp, dir);
                Files.writeString(done, BUNDLE);
            }
            com.mojang.logging.LogUtils.getLogger().info("[CSC] built-in FFmpeg {} unpacked for {} ({} files)", BUNDLE, plat, files);
            return Files.isRegularFile(exe) ? exe.toString() : null;
        }

        private static void deleteTree(Path p) throws java.io.IOException {
            if (!Files.exists(p)) return;
            try (var w = Files.walk(p)) {
                for (Path q : w.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
            }
        }

        /** 1.2.1: one picture decoded by FFmpeg (bytes in, PNG out through pipes), or null. Worker thread. */
        static BufferedImage decodeStill(byte[] data) {
            String exe = path();
            if (exe == null) return null;
            try {
                Process pr = process(exe, "-v", "error", "-nostdin", "-i", "pipe:0", "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                Thread feed = new Thread(() -> {
                    try (var out = pr.getOutputStream()) { out.write(data); } catch (Exception ignored) { }
                }, "CSC FFmpeg input");
                feed.setDaemon(true);
                feed.start();
                byte[] png = pr.getInputStream().readAllBytes();
                pr.waitFor();
                return png.length == 0 ? null : ImageIO.read(new ByteArrayInputStream(png));
            } catch (Exception e) {
                return null;
            }
        }

        /** The program starts and answers -version. */
        private static boolean works(String exe) {
            try {
                Process pr = process(exe, "-version").redirectErrorStream(true).start();
                pr.getInputStream().readAllBytes();
                return pr.waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        }

        /** A process of FFmpeg; the built-in one finds its libraries next to it. */
        static ProcessBuilder process(String exe, String... args) {
            List<String> cmd = new ArrayList<>();
            cmd.add(exe);
            cmd.addAll(List.of(args));
            return process(cmd);
        }

        static ProcessBuilder process(List<String> cmd) {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Path exe = Path.of(cmd.get(0));
            if (exe.getParent() != null) {
                String lib = exe.getParent().toString();
                String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
                String var = os.contains("mac") ? "DYLD_LIBRARY_PATH" : os.contains("win") ? null : "LD_LIBRARY_PATH";
                if (var != null) {
                    String old = pb.environment().get(var);
                    pb.environment().put(var, old == null || old.isEmpty() ? lib : lib + java.io.File.pathSeparator + old);
                }
                pb.directory(exe.getParent().toFile());
            }
            return pb;
        }
    }

    /**
     * A video read by FFmpeg (raw RGBA frames through a pipe, scaled to the profile's video size and frame rate). A reader thread
     * keeps a few frames ahead of the playback time; the render thread uploads the frame due. A jump (seek, pause, restart) restarts
     * FFmpeg at the new time. Loop: the length is read from FFmpeg's header ("Duration:").
     */
    static final class VideoStream {
        private final String ffmpeg;
        private final Path file;
        private final int w, h, fps;
        private final VTex tex;
        private volatile double wanted = 0;
        private volatile double duration = -1;
        private volatile boolean closed;
        private final ArrayDeque<Object[]> queue = new ArrayDeque<>();     // {Double time, byte[] rgba}
        private Thread thread;
        private Process process;
        private double streamStart = -1;
        private double shown = -1;

        VideoStream(String ffmpeg, Path file) {
            this.ffmpeg = ffmpeg;
            this.file = file;
            this.h = Math.max(90, VisionSettings.videoHeight);
            this.w = (h * 16 / 9) & ~1;
            this.fps = Math.max(5, Math.min(30, VisionSettings.videoFps * 2));
            this.tex = new VTex(w, h);
            probe();
        }

        float aspect() { return w / (float) h; }

        long bytes() { return tex.bytes() + (long) queue.size() * w * h * 4; }

        private void probe() {
            WORKER.submit(() -> {
                try {
                    Process p = Ffmpeg.process(ffmpeg, "-hide_banner", "-i", file.toAbsolutePath().toString()).redirectErrorStream(true).start();
                    String s = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    p.waitFor();
                    var m = java.util.regex.Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)").matcher(s);
                    if (m.find()) duration = Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3));
                } catch (Exception ignored) { }
            });
        }

        /** Render thread: the frame for playback time {@code t} (seconds), or the last one while the next is not decoded. */
        VTex frame(double t, boolean loop) {
            double d = duration;
            if (d > 0) t = loop ? ((t % d) + d) % d : Math.max(0, Math.min(t, d - 0.05));
            else t = Math.max(0, t);
            wanted = t;
            boolean restart = thread == null || !thread.isAlive() || t < streamStart - 0.05 || (shown >= 0 && Math.abs(t - shown) > 1.5 && queueEmptyOrBehind(t));
            if (restart) restartAt(t);
            Object[] best = null;
            synchronized (queue) {
                while (!queue.isEmpty() && (double) queue.peekFirst()[0] <= t + 0.5 / fps) best = queue.pollFirst();
                queue.notifyAll();
            }
            if (best != null) {
                ByteBufferHolder.upload(tex, (byte[]) best[1]);
                shown = (double) best[0];
                VisionStats.mediaDecoded++;
            }
            return tex.id() >= 0 ? tex : null;
        }

        private boolean queueEmptyOrBehind(double t) {
            synchronized (queue) {
                return queue.isEmpty() || (double) queue.peekLast()[0] < t - 1.0;
            }
        }

        private void restartAt(double t) {
            stopProcess();
            synchronized (queue) { queue.clear(); }
            streamStart = t;
            shown = t;
            final double start = t;
            thread = new Thread(() -> run(start), "CSC LiveVision video");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }

        private void run(double start) {
            List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-v", "error", "-nostdin", "-ss", String.format(java.util.Locale.ROOT, "%.3f", start),
                "-i", file.toAbsolutePath().toString(), "-an", "-vf",
                "scale=" + w + ":" + h + ":force_original_aspect_ratio=decrease,pad=" + w + ":" + h + ":(ow-iw)/2:(oh-ih)/2,fps=" + fps,
                "-f", "rawvideo", "-pix_fmt", "rgba", "-"));
            try {
                Process p = Ffmpeg.process(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                synchronized (this) { process = p; }
                InputStream in = p.getInputStream();
                int size = w * h * 4;
                long i = 0;
                while (!closed && !Thread.currentThread().isInterrupted()) {
                    byte[] f = in.readNBytes(size);
                    if (f.length < size) break;
                    double ts = start + i++ / (double) fps;
                    synchronized (queue) {
                        while (!closed && queue.size() >= 4 && ts > wanted + 0.3) queue.wait(200);
                        queue.addLast(new Object[] {ts, f});
                        while (queue.size() > 8) queue.pollFirst();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                stopProcess();
            }
        }

        private synchronized void stopProcess() {
            if (process != null) { process.destroy(); process = null; }
            if (thread != null && thread != Thread.currentThread()) thread.interrupt();
        }

        /** Not shown for a while: FFmpeg is stopped (restarted at the right time when the video is shown again). */
        void idle() {
            if (thread != null && thread.isAlive()) { stopProcess(); thread = null; }
        }

        void close() {
            closed = true;
            stopProcess();
            tex.free();
        }
    }

    /** Uploads a frame through a reused native buffer (one per thread: the render thread). */
    private static final class ByteBufferHolder {
        private static java.nio.ByteBuffer buf;

        static void upload(VTex tex, byte[] rgba) {
            if (buf == null || buf.capacity() < rgba.length) {
                if (buf != null) org.lwjgl.system.MemoryUtil.memFree(buf);
                buf = org.lwjgl.system.MemoryUtil.memAlloc(rgba.length);
            }
            buf.clear();
            buf.put(rgba).flip();
            tex.upload(buf);
        }
    }
}
