import com.concertstage.audio.core.*;
import java.nio.file.*;
import java.util.*;
import javax.sound.sampled.*;
import java.io.*;
public final class PlaylistPcmTest {
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 static Path wav(Path dir,String name,int rate,int frames,int value)throws Exception {
  byte[] bytes=new byte[frames*4];for(int i=0;i<frames*2;i++){bytes[i*2]=(byte)value;bytes[i*2+1]=(byte)(value>>8);}
  Path p=dir.resolve(name+".wav");try(var a=new AudioInputStream(new ByteArrayInputStream(bytes),new AudioFormat(rate,16,2,true,false),frames)){AudioSystem.write(a,AudioFileFormat.Type.WAVE,p.toFile());}return p;
 }
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory("csc-gapless");
  Path a=wav(dir,"a",44100,4410,12000),b=wav(dir,"b",48000,4800,18000);
  var entries=List.of(new PlaylistPcm.Entry("a",a,"wav"),new PlaylistPcm.Entry("b",b,"wav"));
  try(var feed=new PlaylistPcm(new PlaylistPcm.Plan(entries,RepeatMode.OFF),0,0)){
   short[] data=new short[1538];int total=0,n;
   while((n=feed.read(data,769))>0){for(int i=0;i<n;i++){int expected=total+i<4800?12000:18000;check(data[i*2]==expected&&data[i*2+1]==expected,"boundary signal at "+(total+i));}total+=n;}
   check(total==9600,"exact mixed-rate count: "+total);check(feed.failure()==null,"no decoder failure");
  }
  try(var feed=new PlaylistPcm(new PlaylistPcm.Plan(entries,RepeatMode.ONE),0,0)){
   short[] data=new short[2000];for(int j=0;j<25;j++){check(feed.read(data,1000)==1000,"repeat fills callback");for(short v:data)check(v==12000,"no inserted silence in repeat");}
   check(feed.bufferedChunks()<=150,"bounded queue");
  }
  try(var feed=new PlaylistPcm(new PlaylistPcm.Plan(entries,RepeatMode.ALL),0,0)){
   short[] data=new short[20000];check(feed.read(data,10000)==10000,"All wraps");for(int i=9600;i<10000;i++)check(data[i*2]==12000,"All restarts first track");
  }
  try(var feed=new PlaylistPcm(new PlaylistPcm.Plan(entries,RepeatMode.ALL),0,0)){
   long deadline=System.currentTimeMillis()+3000;
   while(feed.bufferedChunks()<20&&System.currentTimeMillis()<deadline)Thread.sleep(5);
   short[] head=new short[2000];check(feed.read(head,1000)==1000,"audible start");
   feed.configure(new PlaylistPcm.Plan(entries,RepeatMode.ONE));
   short[] rest=new short[30000];check(feed.read(rest,15000)==15000,"live switch fills");
   for(short v:rest)check(v==12000,"live Repeat One discards stale successor without cutting current track");
   feed.configure(new PlaylistPcm.Plan(entries,RepeatMode.OFF));
   short[] tail=new short[20000];int end=feed.read(tail,10000);
   check(end==8000,"live Off preserves current tail then final track: "+end);
   for(int i=0;i<end;i++)check(tail[i*2]==(i<3200?12000:18000),"live Off routing at "+i);
   check(feed.read(tail,1)==-1,"Off terminates after last track");
  }
  if(args.length>0) {
   Path mp3=Path.of(args[0],"t.mp3");long decoded=0;int sourceRate;
   try(var d=AudioFiles.open(mp3,"mp3")){sourceRate=d.sampleRate();short[] buf=new short[8192];int n;while((n=d.read(buf))>0)decoded+=n;}
   var mixed=List.of(new PlaylistPcm.Entry("mp3",mp3,"mp3"),entries.get(1));
   try(var feed=new PlaylistPcm(new PlaylistPcm.Plan(mixed,RepeatMode.OFF),0,0)) {
    long count=0;short[] data=new short[4096];int n;while((n=feed.read(data,2048))>0)count+=n;
    long expected=(decoded*48000+sourceRate-1)/sourceRate+4800;
    check(count==expected,"MP3/WAV decoded frame count: "+count+" vs "+expected);
    check(feed.failure()==null,"mixed format decode");
   }
  }
  var missing=List.of(entries.get(0),new PlaylistPcm.Entry("missing",dir.resolve("not-downloaded.wav"),"wav"));
  try(var feed=new PlaylistPcm(new PlaylistPcm.Plan(missing,RepeatMode.OFF),0,0)){
   short[] data=new short[9600];check(feed.read(data,4800)==4800,"first track before missing download");
   long before=PlaylistPcm.STARVATIONS.get();check(feed.read(data,4800)==-1,"missing download terminates bounded read");
   check(PlaylistPcm.STARVATIONS.get()>before,"real starvation instrumented");check(feed.failure()!=null,"timeout error exposed");
  }
  System.out.println("PlaylistPcmTest PASS: exact WAV boundaries, 44.1/48 kHz, Repeat One/All, bounded queue, no zero insertion");
 }
}
