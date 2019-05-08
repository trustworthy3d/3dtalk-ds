package org.area515.resinprinter.twodim;

import org.apache.commons.io.IOUtils;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.render.CurrentImageRenderer;
import org.area515.util.IOUtilities;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.imageio.ImageIO;

/**
 * Created by zyd on 2018/8/16.
 */

public class GraphicsMagickRender extends CurrentImageRenderer
{
    public GraphicsMagickRender(AbstractPrintFileProcessor.DataAid aid, AbstractPrintFileProcessor<?, ?> processor, Object imageIndexToBuild)
    {
        super(aid, processor, imageIndexToBuild);
    }


    @Override
    public BufferedImage renderImage(BufferedImage image) throws JobManagerException
    {
        try{
            InputStream inputStream = new FileInputStream((File)imageIndexToBuild);
            File svgfile = File.createTempFile("tmp", ".svg");
            OutputStream outputStream = new FileOutputStream(svgfile);
            IOUtils.copy(inputStream, outputStream);
            File pngfile = File.createTempFile("tmp", ".png");

            IOUtilities.executeNativeCommand(new String[]{"graphicsmagick", svgfile.getAbsolutePath(), pngfile.getAbsolutePath()}, null);
            BufferedImage image1 = ImageIO.read(pngfile);

            svgfile.deleteOnExit();
            pngfile.deleteOnExit();

            return image1;
        }
        catch (IOException e) {
            throw new JobManagerException("Unable to read image:" + imageIndexToBuild, e);
        }
    }
}
