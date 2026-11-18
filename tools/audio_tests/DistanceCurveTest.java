import com.concertstage.audio.core.*;
public class DistanceCurveTest {
 public static void main(String[] args){
  for(double r:new double[]{1,12,100})for(double max:new double[]{16,192,512})for(double roll:new double[]{.1,.65,3}){
   var c=new DistanceCurve(r,max,roll,1);double before=1;
   for(double d=0;d<=max+5;d+=.1){double gain=c.gain(d);if(!Double.isFinite(gain)||gain>before+1e-12||gain<0)throw new AssertionError("monotonic "+d);before=gain;}
  }
  var c=new DistanceCurve(12,192,.65,1);double before=1;
  for(double d:new double[]{2,5,10,25,50,100,150}){double gain=c.gain(d);if(gain>=before)throw new AssertionError("strict decay "+d);before=gain;System.out.printf(java.util.Locale.ROOT,"distance %.0f m -> %.6f%n",d,gain);}
  if(c.gain(Double.NaN)!=0)throw new AssertionError("non finite distance");
  System.out.println("DistanceCurveTest PASS: monotonic continuous curve, near/far coverage and finite bounds");
 }
}
