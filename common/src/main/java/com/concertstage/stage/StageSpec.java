package com.concertstage.stage;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Generated from supplied Blockbench files, loaded once on either logical side. */
public final class StageSpec {
    public String id,kind,mode;
    public int[] grid,main,anchor;
    public boolean mountable,bay,internal,connection_generated; public String raised_from; public double run; public double[][] feet; public Port[] ports; public static final class Port{public double[] center,normal;public double[][] points;}
    public double width,depth,walk;
    public boolean[] occupied;
    public double[][][][] cells;
    public double[][] raw_boxes;
    public double[] raw_bounds;
    /** Footprint seen from above, in the piece's own frame ([x, z] corners); null: the raw_bounds rectangle. FlexDeck triangles have three. */
    public double[][] outline;
    public double[][] outline(){if(outline!=null&&outline.length>=3)return outline;double[] b=raw_bounds;return new double[][]{{b[0],b[2]},{b[3],b[2]},{b[3],b[5]},{b[0],b[5]}};}
    public static boolean inside(double[][] poly,double x,double z){boolean in=false;for(int i=0,j=poly.length-1;i<poly.length;j=i++){if((poly[i][1]>z)!=(poly[j][1]>z)&&x<(poly[j][0]-poly[i][0])*(z-poly[i][1])/(poly[j][1]-poly[i][1])+poly[i][0])in=!in;}return in;}
    public String[] rail_sides;
    private final Map<Integer,VoxelShape> shapes=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Root{StageSpec[] equipment;}
    private static final Map<String,StageSpec> ALL=load();
    private static Map<String,StageSpec> load(){
        try(var input=StageSpec.class.getResourceAsStream("/data/concertstage/stage_equipment.json")){
            if(input==null)throw new IllegalStateException("Missing generated stage equipment definitions");
            // read straight into the objects (no JSON tree of the 10 MB file in memory)
            Root root=new Gson().fromJson(new java.io.BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8),1<<16),Root.class);Map<String,StageSpec> out=new LinkedHashMap<>();
            for(StageSpec spec:root.equipment){if(spec.grid.length!=3||spec.count()>256||spec.cells.length!=spec.count())throw new IllegalStateException("Invalid stage footprint: "+spec.id);out.put(spec.id,spec);}
            share(out.values());return Collections.unmodifiableMap(out);
        }catch(IOException e){throw new IllegalStateException("Cannot load stage equipment definitions",e);}
    }
    /**
     * The pieces share most of their geometry (the festival stages with a tower cell are the same stage but for one column; decks repeat
     * the same tubes in every cell): 99 000 cell boxes but 5 200 different ones, 109 000 raw boxes but 29 000. Identical boxes and identical
     * lists of boxes are kept once (they are never written after loading), which saves about 10 MB of memory on both sides.
     */
    private static void share(Collection<StageSpec> specs){
        Map<Box,double[]> boxes=new java.util.HashMap<>();Map<Boxes,double[][]> lists=new java.util.HashMap<>();
        for(StageSpec s:specs){
            if(s.raw_boxes!=null)for(int i=0;i<s.raw_boxes.length;i++)s.raw_boxes[i]=boxes.computeIfAbsent(new Box(s.raw_boxes[i]),k->k.v);
            for(double[][][] part:s.cells)for(int v=0;v<part.length;v++){
                double[][] list=part[v];for(int i=0;i<list.length;i++)list[i]=boxes.computeIfAbsent(new Box(list[i]),k->k.v);
                part[v]=lists.computeIfAbsent(new Boxes(list),k->k.v);
            }
        }
    }
    private record Box(double[] v){public boolean equals(Object o){return o instanceof Box b&&java.util.Arrays.equals(v,b.v);}public int hashCode(){return java.util.Arrays.hashCode(v);}}
    private record Boxes(double[][] v){public boolean equals(Object o){return o instanceof Boxes b&&java.util.Arrays.equals(v,b.v);}public int hashCode(){return java.util.Arrays.hashCode(v);}}
    public static Collection<StageSpec> all(){return ALL.values();}
    public static StageSpec get(String key){StageSpec s=ALL.get(key);if(s==null)throw new IllegalArgumentException("Unknown stage equipment "+key);return s;}
    public static StageSpec find(String key){return ALL.get(key);}
    public int count(){return grid[0]*grid[1]*grid[2];}
    public int index(int x,int y,int z){return StageMath.index(grid[0],grid[2],x,y,z);}
    public int[] local(int part){return StageMath.cell(grid[0],grid[2],part);}
    public static int turns(Direction facing){return(facing.get2DDataValue()+2)%4;}
    public BlockPos offset(int part,int turns){int[] c=local(part);int[] r=StageMath.rotateOffset(c[0],c[1],c[2],turns);return new BlockPos(r[0],r[1],r[2]);}
    public VoxelShape shape(int part,int turns,int variant){
        if(part<0||part>=count())return Shapes.empty();int v=Math.min(variant,cells[part].length-1),key=(part*2+v)*4+turns;
        return shapes.computeIfAbsent(key,k->{List<double[]> boxes=new ArrayList<>();for(double[] b:cells[part][v])boxes.add(StageMath.cellBox(b,turns));return StageCollision.build(boxes,walk-local(part)[1]);});
    }
}
