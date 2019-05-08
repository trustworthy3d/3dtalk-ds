package org.area515.resinprinter.image;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscodingHints;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.keys.Rectangle2DKey;
import org.apache.batik.transcoder.keys.StringKey;
import org.apache.batik.util.SVGConstants;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import javax.imageio.ImageIO;

/**
 * Created by zyd on 2017/9/29.
 */

public class ConvertSVGToPNG
{

    public void transformAndCopy(File svgFile)
    {
        if (!svgFile.getAbsolutePath().endsWith(".svg"))
            return;

        File pngFile = new File(svgFile.getAbsolutePath()+".png");

        try
        {
            final BufferedImage[] imagePointer = new BufferedImage[1];

            TranscodingHints transcoderHints = new TranscodingHints();
            transcoderHints.put(ImageTranscoder.KEY_XML_PARSER_VALIDATING, Boolean.FALSE);
            transcoderHints.put(ImageTranscoder.KEY_DOM_IMPLEMENTATION, SVGDOMImplementation.getDOMImplementation());
            transcoderHints.put(ImageTranscoder.KEY_DOCUMENT_ELEMENT_NAMESPACE_URI, SVGConstants.SVG_NAMESPACE_URI);
            transcoderHints.put(ImageTranscoder.KEY_DOCUMENT_ELEMENT, "svg");
            transcoderHints.put(ImageTranscoder.KEY_WIDTH, new Float(2560));
            transcoderHints.put(ImageTranscoder.KEY_HEIGHT, new Float(1600));
//            transcoderHints.put(ImageTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, new Float(0.075));

            try
            {
                TranscoderInput input = new TranscoderInput(new FileInputStream(svgFile));
                ImageTranscoder trans = new ImageTranscoder()
                {
                    @Override
                    public BufferedImage createImage(int w, int h)
                    {
                        return new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
                    }

                    @Override
                    public void writeImage(BufferedImage image, TranscoderOutput out) throws TranscoderException
                    {
                        BufferedImage image1 = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
                        Graphics2D graphics2D = image1.createGraphics();
//                        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        graphics2D.drawImage(image, null, 0, 0);
                        imagePointer[0] = image1;
                    }
                };
                trans.setTranscodingHints(transcoderHints);
                trans.transcode(input, null);

                ImageIO.write(imagePointer[0], "png", pngFile);
            }
            catch (Exception ex)
            {
                System.out.println(ex.toString());
            }
        }
        catch (Exception e)
        {
            System.out.println(e.toString());
        }
    }

    @Test
    public void transformOne()
    {
        File file = new File("D:\\Users\\zyd\\3D Objects\\37.svg");
        transformAndCopy(file);
    }

    @Test
    public void transformALL()
    {
        File baseFile = new File("D:\\Users\\zyd\\3D Objects\\min");
        File[] files = baseFile.listFiles();
        for (File file : files)
        {
            transformAndCopy(file);
        }
    }

    @Test
    public void test01() throws Exception
    {
        InputStream inputStream = new FileInputStream(new File("D:\\Users\\zyd\\Desktop\\1.svg"));
        File svgfile = File.createTempFile("tmp", "svg");
        OutputStream outputStream = new FileOutputStream(svgfile);
        IOUtils.copy(inputStream, outputStream);
        System.out.println(svgfile.getAbsolutePath());
    }
}
