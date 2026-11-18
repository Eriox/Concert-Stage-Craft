import com.concertstage.light.core.*;
import com.concertstage.light.dmx.*;
import com.concertstage.light.analysis.*;
import java.util.*;

/** Regression and end-to-end packet/engine tests for the 13 supplied fixtures. No Minecraft/console import is claimed. */
public class NewFixturesTest {
    static int checks;
    static void check(boolean ok,String label) { if(!ok)throw new AssertionError(label);checks++; }
    static void near(float a,float b,String label){check(Math.abs(a-b)<.004f,label+": "+a+" != "+b);}
    static Show.Patch patch(FixtureProfile p,int mode){Show.Patch f=new Show.Patch();f.fid=1;f.type=p.id;f.universe=0;f.address=1;f.dmxMode=mode;return f;}
    static float[] home(FixtureProfile p){float[] v=new float[Attr.COUNT];for(Attr a:Attr.ALL)v[a.ordinal()]=p.home(a);return v;}
    static byte[] defaults(DmxMode m){byte[] d=new byte[512];for(int i=0;i<m.channels.length;i++)d[i]=(byte)m.channels[i].def();return d;}
    static void set(byte[] d,DmxMode m,Attr a,int value){for(int c:m.channelsOf(a))d[c-1]=(byte)value;}
    static Engine.Output evaluate(FixtureProfile p,byte[] d){Show s=new Show();s.patch.add(patch(p,0));Live l=new Live();l.control=Live.EXTERNAL;l.previousControl=Live.EXTERNAL;DmxData dm=new DmxData();dm.set(0,d);return new Engine().evaluate(s,l,2000,Engine.Music.NONE,dm).get(1);}
    public static void main(String[] args) throws Exception {
        check(FixtureProfile.all().size()==17,"17 unique registered fixture types");
        check(Attr.COUNT<=63,"extended attribute bitmask remains representable");
        // rev. 17: the 13 new fixtures drawn at the size of the historical ones (x1.5 to x2.6), the four historical ones unchanged
        near(FixtureProfile.VORTEX_X1.renderScale,1.95f,"VORTEX X1 size unchanged");near(FixtureProfile.LUMORA_W7.renderScale,1.90f,"LUMORA W7 size unchanged");
        near(FixtureProfile.LUMORA_W19.renderScale,2.10f,"LUMORA W19 size unchanged");near(FixtureProfile.PULSESTRIKE_M.renderScale,2.60f,"PULSESTRIKE M size unchanged");
        float[][] pivots={{0.5f},{0.44f},{0.33f},{0.165f},{0.205f},{0.12f},{0.155f},{0.23f},{0.26f},{1.46f},{0.43f},{0.165f},{0.215f}};
        FixtureProfile[] news={FixtureProfile.PHANTOM_S1,FixtureProfile.PROFILE_P1,FixtureProfile.RAZOR_B1,FixtureProfile.FLASHCORE_FX,FixtureProfile.PIXELBAR_B16,FixtureProfile.PIXELTUBE_T1,FixtureProfile.BLINDER_X2,FixtureProfile.BLINDER_X4,FixtureProfile.BLINDER_X8,FixtureProfile.FOLLOWSPOT_F1,FixtureProfile.PIXELWASH_W37,FixtureProfile.LASERCORE_L1,FixtureProfile.FLOODLIGHT_F8};
        for(int i=0;i<news.length;i++){check(news[i].renderScale>=1.5f&&news[i].renderScale<=2.6f,news[i].id+" drawn larger: x"+news[i].renderScale);
            near(news[i].tiltPivotY,pivots[i][0]*news[i].renderScale,news[i].id+" tilt axis height follows the size");}
        for(FixtureProfile p:FixtureProfile.all())check(DmxMode.of(p,p.hasSections()?DmxMode.COMPACT_V1:DmxMode.COMPACT).footprint()<=8,p.id+" Compact <= 8 channels (64 per universe; rev. 21: zone fixtures, their Compact before rev. 21)");
        Set<DmxMode> identities=Collections.newSetFromMap(new IdentityHashMap<>());
        Show rig=new Show();int fid=1;
        for(FixtureProfile p:FixtureProfile.all()) {
            for(int mode=0;mode<4;mode++) {
                DmxMode m=DmxMode.of(p,mode);check(identities.add(m),p.id+" no personality cache collision");
                check(m.profile==p&&m.mode==mode,p.id+" correct cache identity");
                check(m.footprint()<=512,p.id+" bounded footprint");
                check((m.footprint()==0)==(mode==3),p.id+" matrix has zero footprint");
                if(mode==0)check(m.missing().isEmpty(),p.id+" Full reaches every declared attribute");
                check(mode==3 || p.has(Attr.TILT)==(m.channelsOf(Attr.TILT).length>0),p.id+" static heads have no fake tilt channels");
                if(mode==3)continue;
                byte[] d=defaults(m);float[] v=home(p),z=new float[p.zones*4];DmxData dm=new DmxData();dm.set(0,d);
                check(DmxDecode.decode(patch(p,mode),dm,v,z),p.id+" decode mode "+mode+" on universe zero");
                for(float x:v)check(Float.isFinite(x)&&x>=0&&x<=1,p.id+" finite normalized attribute");
                // Last legal start address, then the mode at its own start. No channel is allowed to spill.
                Show.Patch end=patch(p,mode);end.address=513-m.footprint();Show es=new Show();es.patch.add(end);
                check(AutoPatch.conflicts(es).isEmpty(),p.id+" fits at last legal address");
            }
            DmxMode m=DmxMode.of(p,0);byte[] d=defaults(m);set(d,m,Attr.DIMMER,255);
            for(DmxMode.Channel c:m.channels) if(c.kind()==DmxMode.Kind.ZONE_W)check(p.rgbwPixels,p.id+" RGBW only on supported cells");
            int pixels=0;for(DmxMode.Channel c:m.channels)if(c.zone()>=0)pixels++;
            int expected=p.rgbZones*3+p.whiteZones+(p.zones-p.rgbZones-p.whiteZones)*(p.rgbwPixels?4:3);
            check(pixels==expected,p.id+" complete cell channels");
            // Real packet encoder -> validator -> source merger -> universe -> decoder.
            for(int protocol=0;protocol<2;protocol++) {
                byte[] packet=protocol==0?DmxPackets.artDmx(0,1,d,512):DmxPackets.sacnData(1,100,1,false,new byte[16],"new-fixture-test",d,512);
                DmxPackets.Frame frame=DmxPackets.parse(packet,packet.length,"127.0.0.1");check(frame!=null,p.id+" valid protocol packet");
                DmxMerge merge=new DmxMerge();int u=merge.receive(frame,0);byte[] slots=new byte[512];check(merge.snapshot(u,slots)>=0,p.id+" merged packet");
                DmxData dm=new DmxData();dm.set(u,slots);Show.Patch pa=patch(p,0);pa.universe=u;float[] v=home(p);
                check(DmxDecode.decode(pa,dm,v,new float[p.zones*4]),p.id+" protocol reaches decoder");near(v[0],1,p.id+" received dimmer");
            }
            Show ms=new Show();ms.patch.add(patch(p,3));ms.external.matrixUniverse=0;ms.external.matrixAddress=1;
            byte[] matrix=new byte[512];matrix[0]=(byte)255;matrix[1]=(byte)255;matrix[14]=(byte)128;matrix[15]=(byte)128;
            DmxData md=new DmxData();md.set(0,matrix);Live ml=new Live();ml.control=ml.previousControl=Live.EXTERNAL;
            Engine.Output mo=new Engine().evaluate(ms,ml,2000,Engine.Music.NONE,md).get(1);
            near(mo.get(Attr.DIMMER),1,p.id+" Virtual Matrix intensity");check(mo.beamR>0,p.id+" Virtual Matrix emits");
            Show bs=new Show();bs.patch.add(patch(p,0));byte[] bad=Commands.op(Commands.PATCH_DMX).varInt(1).varInt(0).varInt(512).varInt(0).bool(false).bool(false).f32(0).f32(0).toBytes();
            check(Commands.apply(bs,new Live(),bad,0,null)==Commands.REJECTED,p.id+" overflowing patch rejected atomically");
            Engine.Output o=evaluate(p,d);near(o.get(Attr.DIMMER),1,p.id+" engine dimmer");
            if(p.hasSections()){ // rev. 21: the zone layouts have no master dimmer: the three zone dimmers at 0 are the blackout
                for(Attr z:new Attr[]{Attr.TOP_DIM,Attr.MID_DIM,Attr.BOT_DIM})set(d,m,z,0);o=evaluate(p,d);
                float lit=0;for(int z=0;z<p.zones;z++)lit+=o.zones[z*4+3];near(lit,0,p.id+" blackout (every zone at 0)");
            } else {set(d,m,Attr.DIMMER,0);o=evaluate(p,d);near(o.get(Attr.DIMMER),0,p.id+" blackout");}
            Show.Patch f=patch(p,0);f.fid=fid++;rig.patch.add(f);
        }
        check(AutoPatch.plan(rig,0,0,AutoPatch.SHARE_NONE).fits(),"17-type rig reduces modes to fit one universe");
        Show decoded=Show.fromBytes(rig.toBytes());check(decoded!=null&&decoded.patch.size()==17,"all fixture IDs survive show serialization");
        Show old=Show.fromBytes(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("tools/lightshow_tests/fixtures/legacy_show_v2.bin")));
        check(old!=null && old.patch.size()==4 && old.lists.size()==1,"real format-2 show written by original source loads");
        Look oldCue=old.lists.get(0).cues.get(0).looks.get(1);near(oldCue.get(Attr.DIMMER),.5f,"old cue dimmer");near(oldCue.get(Attr.PAN),.25f,"old cue pan");
        check(Float.isNaN(oldCue.get(Attr.GOBO_2)),"old cue has no invented second gobo value");
        Look look=new Look();for(Attr a:Attr.ALL)look.set(a,.37f);Bytes.Writer writer=new Bytes.Writer();look.write(writer);Look copy=Look.read(new Bytes.Reader(writer.toBytes()));
        for(Attr a:Attr.ALL)near(copy.get(a),.37f,"attribute ID persistence "+a);
        Bytes.Writer oldLook=new Bytes.Writer();oldLook.varInt(0xFFFF);for(int i=0;i<16;i++)oldLook.unit(.42f);oldLook.varInt(0);
        Look legacy=Look.read(new Bytes.Reader(oldLook.toBytes()));for(int i=0;i<16;i++)near(legacy.v[i],.42f,"legacy look attribute "+i);
        for(int i=16;i<Attr.COUNT;i++)check(Float.isNaN(legacy.v[i]),"legacy sparse look keeps new attribute unset "+i);
        for(FixtureProfile p:List.of(FixtureProfile.PIXELBAR_B16,FixtureProfile.PIXELWASH_W37,FixtureProfile.FLOODLIGHT_F8)) {
            DmxMode m=DmxMode.of(p,0);byte[] d=defaults(m);set(d,m,Attr.DIMMER,255);set(d,m,Attr.RED,0);set(d,m,Attr.GREEN,0);set(d,m,Attr.BLUE,0);set(d,m,Attr.WHITE,0);
            for(int i=0;i<m.channels.length;i++)if(m.channels[i].kind()==DmxMode.Kind.ZONE_W&&m.channels[i].zone()==0)d[i]=(byte)255;
            Engine.Output o=evaluate(p,d);check(o.zones[0]>.99&&o.zones[1]>.9&&o.zones[2]>.9,p.id+" white emitter has all RGB components");
            near(o.beamR,1f/p.zones,p.id+" one pixel contributes its share of total flux");
            for(int z=1;z<p.zones;z++)check(o.zones[z*4]+o.zones[z*4+1]+o.zones[z*4+2]<.001,p.id+" adjacent pixel stays black");
        }
        FixtureProfile p=FixtureProfile.PHANTOM_S1;DmxMode m=DmxMode.of(p,0);byte[] d=defaults(m);set(d,m,Attr.CYAN,255);Engine.Output o=evaluate(p,d);near(o.beamR,0,"CMY cyan removes red");check(o.beamG>.99&&o.beamB>.99,"CMY leaves green and blue");
        p=FixtureProfile.FOLLOWSPOT_F1;m=DmxMode.of(p,0);d=defaults(m);set(d,m,Attr.DIMMER,255);set(d,m,Attr.DOUSER,0);near(evaluate(p,d).get(Attr.DIMMER),0,"douser closes lens and beam together");
        float[] v=home(FixtureProfile.PROFILE_P1),u=new float[OpticalProfile.UNIFORM_FLOATS];ProfessionalOptics.write(FixtureProfile.PROFILE_P1,v,0,u,0);near(ProfessionalOptics.framing(u,.5f,0),1,"open blade");v[Attr.BLADE_1.ordinal()]=.75f;ProfessionalOptics.write(FixtureProfile.PROFILE_P1,v,0,u,0);near(ProfessionalOptics.framing(u,.5f,0),0,"blade clips right side");near(ProfessionalOptics.framing(u,-.8f,0),1,"blade preserves opposite side");
        v=home(FixtureProfile.RAZOR_B1);ProfessionalOptics.write(FixtureProfile.RAZOR_B1,v,0,u,0);near(u[13],8,"eight-facet RAZOR prism");
        v=home(FixtureProfile.LASERCORE_L1);ProfessionalOptics.write(FixtureProfile.LASERCORE_L1,v,0,u,0);check(ProfessionalOptics.laser(u,0,0)>0,"laser dot");check(ProfessionalOptics.laser(u,.4f,.4f)==0,"laser excludes cone interior");v[Attr.LASER_PATTERN.ordinal()]=.4f;ProfessionalOptics.write(FixtureProfile.LASERCORE_L1,v,0,u,0);check(ProfessionalOptics.laser(u,0,0)==0,"circle has dark centre");
        for(int universe:new int[]{1,63999}) {
            byte[] slots=new byte[512];slots[0]=(byte)255;
            byte[] packet=DmxPackets.sacnData(universe,100,1,false,new byte[16],"boundary",slots,512);
            DmxPackets.Frame frame=DmxPackets.parse(packet,packet.length,"127.0.0.1");check(frame!=null&&frame.universe()==universe,"sACN valid boundary "+universe);
            Show s=new Show();s.patch.add(patch(FixtureProfile.PIXELWASH_W37,0));
            byte[] command=Commands.op(Commands.PATCH_DMX).varInt(1).varInt(universe).varInt(1).varInt(0).bool(false).bool(false).f32(0).f32(0).toBytes();
            check(Commands.apply(s,new Live(),command,0,null)==Commands.SHOW,"patch accepts sACN boundary "+universe);
        }
        byte[] zero=DmxPackets.sacnData(0,100,1,false,new byte[16],"invalid",new byte[512],512);check(DmxPackets.parse(zero,zero.length,"127.0.0.1")==null,"sACN rejects universe zero");
        near(BeamOptics.strobeIntensity(0,10,0),0,"closed dimmer remains dark during a flash");
        near(BeamOptics.strobeIntensity(0,10,.05),0,"closed dimmer remains dark between flashes");
        check(BeamOptics.strobeIntensity(1,10,0)>0,"strobe on phase");
        near(BeamOptics.strobeIntensity(1,10,.07),0,"strobe off phase");
        int id=LiveAutoShow.prepare(rig,Movement.stage(rig,0,0,10));check(id>0,"AutoShow prepares all fixture families");
        Show.CueList list=rig.lists.stream().filter(x->x.id==id).findFirst().orElseThrow();Set<Integer> reached=new HashSet<>();for(Show.Cue c:list.cues)reached.addAll(c.looks.keySet());check(reached.size()==17,"AutoShow reaches every fixture type");
        for(int n:new int[]{50,100,200}) {
            Show s=new Show();List<FixtureProfile> ps=new ArrayList<>(FixtureProfile.all());for(int i=0;i<n;i++){Show.Patch f=patch(ps.get(i%ps.size()),0);f.fid=i+1;s.patch.add(f);}
            Engine engine=new Engine();Live l=new Live();for(int i=0;i<400;i++)engine.evaluate(s,l,i*20,Engine.Music.NONE);
            long t=System.nanoTime();for(int i=0;i<1000;i++)engine.evaluate(s,l,i*20,Engine.Music.NONE);
            System.out.printf(Locale.ROOT,"CPU mixed rig %d fixtures: %.1f us/evaluation (no GPU or FPS measurement)%n",n,(System.nanoTime()-t)/1e6);
        }
        System.out.println("passed "+checks+", failed 0");
    }
}
