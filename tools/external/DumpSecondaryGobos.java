import com.concertstage.light.core.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;

/** Export the second gobo wheel directly from the CPU twin of the renderer's optical mask. */
public class DumpSecondaryGobos {
    public static void main(String[] args)throws Exception {
        Path directory=Path.of(args[0]);Files.createDirectories(directory);
        for(int slot=1;slot<=8;slot++) {
            float[] u=new float[OpticalProfile.UNIFORM_FLOATS];u[16]=slot;
            BufferedImage image=new BufferedImage(256,256,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<256;y++)for(int x=0;x<256;x++) {
                int v=Math.round(255*ProfessionalOptics.secondary(u,(x+.5f)/128-1,(y+.5f)/128-1));
                image.setRGB(x,y,v*0x010101);
            }
            ImageIO.write(image,"PNG",directory.resolve("gobo_2_"+slot+".png").toFile());
        }
    }
}
