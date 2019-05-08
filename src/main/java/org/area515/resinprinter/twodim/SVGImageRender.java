package org.area515.resinprinter.twodim;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscodingHints;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.SVGConstants;
import org.apache.commons.io.FileUtils;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.AbstractPrintFileProcessor.DataAid;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.render.CurrentImageRenderer;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by zyd on 2017/10/12.
 */

public class SVGImageRender extends CurrentImageRenderer
{
    public SVGImageRender(DataAid aid, AbstractPrintFileProcessor<?, ?> processor, Object imageIndexToBuild) {
        super(aid, processor, imageIndexToBuild);
    }

    @Override
    public BufferedImage renderImage(BufferedImage image) throws JobManagerException
    {
        return convertSVGToPNG();
    }

    public BufferedImage convertSVGToPNG() throws JobManagerException
    {
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

            try
            {
                TranscoderInput input = new TranscoderInput(new FileInputStream((File)imageIndexToBuild));
                ImageTranscoder trans = new ImageTranscoder()
                {
                    @Override
                    public BufferedImage createImage(int w, int h)
                    {
                        return new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
                    }

                    @Override
                    public void writeImage(BufferedImage image, TranscoderOutput out) throws TranscoderException
                    {
                        BufferedImage image1 = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
                        Graphics2D graphics2D = image1.createGraphics();
                        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        graphics2D.drawImage(image, null, 0, 0);
                        imagePointer[0] = image1;
                    }
                };
                trans.setTranscodingHints(transcoderHints);
                trans.transcode(input, null);
            }
            catch (TranscoderException ex)
            {
                throw new IOException(imageIndexToBuild + " doesn't seem to be an SVG file.");
            }

            return imagePointer[0];
        }
        catch (IOException e)
        {
            throw new JobManagerException("Couldn't load image file:" + imageIndexToBuild, e);
        }
    }

}
