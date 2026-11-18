package com.concertstage.audio.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** One continuous 48 kHz stereo feed. Decoding, rate conversion and lookahead run off the game thread.
 * The bounded queue holds at most six seconds; EOF boundaries never become empty sound buffers. */
public final class PlaylistPcm implements AutoCloseable {
    public static final int RATE = 48000, FRAMES = 1920;
    public record Entry(String id, Path file, String type) {}
    public record Plan(List<Entry> tracks, int repeat) {
        public Plan { tracks = List.copyOf(tracks); repeat = RepeatMode.clamp(repeat); }
    }
    private record Chunk(short[] samples, boolean end, long sequence, String id) {}
    private final ArrayBlockingQueue<Chunk> queue = new ArrayBlockingQueue<>(150);
    private volatile Plan plan;
    private volatile boolean closed;
    private volatile String failure;
    private volatile String state="Starting";
    public static final java.util.concurrent.atomic.AtomicLong STARVATIONS=new java.util.concurrent.atomic.AtomicLong();
    public static volatile long lastStarvationNanos;
    public String decoderState(){return state;}
    public boolean closed(){return closed;}
    private final Object stateLock = new Object();
    private final java.util.Set<Thread> workers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile long generation, producedSequence, consumedSequence;
    private volatile String consumedId;
    private Chunk reading;
    private int offset;
    private volatile long outputFrames, transitions;
    public PlaylistPcm(Plan plan, int startIndex, long startMs) {
        this.plan = plan;
        if(startIndex>=0 && startIndex<plan.tracks.size()) consumedId=plan.tracks.get(startIndex).id;
        launch(startIndex,startMs,0,0);
    }
    private void launch(int index,long ms,long token,long sequence) {
        Thread worker=new Thread(() -> {
            try { produce(index,ms,token,sequence); } finally {workers.remove(Thread.currentThread());}
        },"CSC prepared playlist");
        worker.setDaemon(true);workers.add(worker);worker.start();
    }
    public void configure(Plan next) {
        synchronized(stateLock) {
            boolean changed=!plan.equals(next);plan=next;
            if(!changed || producedSequence<=consumedSequence) return;
            // Retain all queued samples of the audible track. Only invalidate the speculative successor.
            long sequence=consumedSequence;
            queue.removeIf(chunk -> chunk.sequence>sequence || chunk.end);
            int current=-1;
            for(int i=0;i<next.tracks.size();i++) if(next.tracks.get(i).id.equals(consumedId)){current=i;break;}
            int successor=current<0?-1:RepeatMode.successor(current,next.tracks.size(),next.repeat,true);
            generation++;producedSequence=sequence;
            launch(successor,0,generation,sequence+1);
        }
    }
    public String failure() { return failure; }
    public long outputFrames() { return outputFrames; }
    public long transitions() { return transitions; }
    public int bufferedChunks() { return queue.size(); }
    private void put(Chunk chunk,long token) throws InterruptedException {
        while(!closed && token==generation) {
            synchronized(stateLock) {
                if(token!=generation || closed)return;
                if(queue.offer(chunk))return;
            }
            Thread.sleep(5);
        }
    }
    private void produce(int index, long startMs,long token,long sequence) {
        try {
            while (!closed && token==generation) {
                Plan p = plan;
                if (index < 0 || index >= p.tracks.size()) break;
                Entry entry = p.tracks.get(index);
                // The existing client transfer service fetches authorised missing files. Never touch game state here.
                state="Waiting for cached file";
                while (!closed && token==generation && !Files.isRegularFile(entry.file)) Thread.sleep(50);
                if (closed || token!=generation) return;
                state="Decoding / buffered";
                long emitted = 0;
                try (TrackDecoder decoder = AudioFiles.open(entry.file, entry.type)) {
                    decoder.skip(Math.max(0,startMs) * decoder.sampleRate() / 1000);
                    SincResampler resampler = new SincResampler(decoder, RATE);   // 1.1.1: band-limited (was linear)
                    for (;;) {
                        short[] data = new short[FRAMES * 2];
                        int n = resampler.read(data);
                        if (n == 0 || closed || token!=generation) break;
                        emitted += n;
                        if (n != FRAMES) data = java.util.Arrays.copyOf(data,n*2);
                        put(new Chunk(data,false,sequence,entry.id),token);
                    }
                }
                if (emitted == 0) throw new IOException("Empty audio track: " + entry.id);
                if(closed || token!=generation)return;
                transitions++;
                synchronized(stateLock) {
                if(token!=generation)return;
                p = plan;
                int current = -1;
                for (int i=0;i<p.tracks.size();i++) if (p.tracks.get(i).id.equals(entry.id)) {current=i;break;}
                if (current < 0) break;
                index = RepeatMode.successor(current,p.tracks.size(),p.repeat,true);
                startMs = 0;
                sequence++;
                producedSequence=sequence;
                }
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
          catch (IOException | RuntimeException ex) { failure = ex.toString();state="Decoder failed"; }
        finally {
            if (!closed && token==generation) try { put(new Chunk(new short[0],true,sequence,consumedId),token); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
    }
    /** Called only by the sound thread. Appends the next prepared track within the very same callback. */
    public int read(short[] out, int frames) {
        int done = 0;
        long waitStart=0;boolean reported=false;
        long deadline = System.nanoTime() + 3_000_000_000L;
        try {
            while (done < frames && !closed) {
                if (reading == null) {
                    synchronized(stateLock) {
                        reading=queue.poll();
                        if(reading!=null){offset=0;consumedSequence=reading.sequence;consumedId=reading.id;}
                    }
                    if(reading==null){
                        if(waitStart==0)waitStart=System.nanoTime();
                        if(!reported&&(outputFrames>0||done>0)&&System.nanoTime()-waitStart>80_000_000L){reported=true;STARVATIONS.incrementAndGet();lastStarvationNanos=System.nanoTime();}
                        if(System.nanoTime()>deadline){failure="Prepared audio unavailable after 3000 ms";break;}Thread.sleep(5);continue;}
                }
                waitStart=0;
                if (reading.end) break;
                int n = Math.min(frames-done,reading.samples.length/2-offset);
                System.arraycopy(reading.samples,offset*2,out,done*2,n*2);
                done+=n;offset+=n;deadline=System.nanoTime()+3_000_000_000L;
                if (offset == reading.samples.length/2) reading=null;
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        outputFrames += done;
        return done == 0 ? -1 : done;
    }
    @Override public void close() { closed=true;state="Closed";for(Thread worker:workers)worker.interrupt();queue.clear(); }
}
