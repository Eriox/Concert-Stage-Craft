package com.concertstage.stage;

import java.util.*;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bounded collision precision, independent of the full-resolution visual geometry.
 * X/Z expand by less than 1/32 block, Y by less than 1/64; walk/tread levels stay exact.
 * Balanced unions avoid repeatedly optimizing an increasingly complex accumulated shape.
 */
public final class StageCollision {
    private StageCollision() {}
    private static double snap(double value,int axis,boolean upper,double[] preserveY) {
        if(axis==1){
            if(Math.abs(value*20-Math.rint(value*20))<1e-6)return Math.rint(value*20)/20;
            for(double y:preserveY)if(Math.abs(value-y)<1e-6)return y;
        }
        int resolution=axis==1?64:32;
        return (upper?Math.ceil(value*resolution-1e-7):Math.floor(value*resolution+1e-7))/resolution;
    }
    public static VoxelShape build(Collection<double[]> boxes,double... preserveY) {
        Set<String> seen=new HashSet<>();List<VoxelShape> layer=new ArrayList<>();
        for(double[] original:boxes){double[] b=new double[6];for(int axis=0;axis<3;axis++){b[axis]=snap(original[axis],axis,false,preserveY);b[axis+3]=snap(original[axis+3],axis,true,preserveY);}
            if(b[0]>=b[3]||b[1]>=b[4]||b[2]>=b[5])continue;
            if(seen.add(Arrays.toString(b)))layer.add(Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]));
        }
        if(layer.isEmpty())return Shapes.empty();
        while(layer.size()>1){List<VoxelShape> next=new ArrayList<>((layer.size()+1)/2);for(int i=0;i<layer.size();i+=2)next.add(i+1<layer.size()?Shapes.or(layer.get(i),layer.get(i+1)):layer.get(i));layer=next;}
        return layer.get(0);
    }
}
