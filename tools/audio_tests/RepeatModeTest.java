import com.concertstage.audio.core.RepeatMode;
public class RepeatModeTest {
 public static void main(String[] args) {
  for(int count=0;count<65;count++) for(int i=-1;i<=count;i++) for(int mode=0;mode<3;mode++) {
   int n=RepeatMode.successor(i,count,mode,true);
   if(count==0)check(n==-1,"empty");
   else if(i<0||i>=count)check(n==0,"invalid index");
   else if(mode==2)check(n==i,"one repeats");
   else if(i<count-1)check(n==i+1,"next");
   else check(n==(mode==0?-1:0),"end policy");
   int manual=RepeatMode.successor(i,count,mode,false);
   if(count>0&&i>=0&&i<count&&mode==2) check(manual==(i+1)%count,"manual next");
  }
  System.out.println("RepeatModeTest PASS: empty/single/multiple playlists, all repeat modes, invalid indices and manual Next");
 }
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
}
