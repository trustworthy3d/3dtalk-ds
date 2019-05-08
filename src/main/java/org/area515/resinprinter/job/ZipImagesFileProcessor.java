package org.area515.resinprinter.job;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.exception.SliceHandlingException;
import org.area515.resinprinter.job.render.RenderedData;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.server.Main;
import org.area515.resinprinter.twodim.GraphicsMagickRender;
import org.area515.resinprinter.twodim.SVGImageRender;
import org.area515.resinprinter.twodim.SimpleImageRenderer;

import se.sawano.java.text.AlphanumericComparator;

public class ZipImagesFileProcessor extends CreationWorkshopSceneFileProcessor implements Previewable {
	private static final Logger logger = LogManager.getLogger();

	@Override
	public String[] getFileExtensions() {
		return new String[]{"imgzip"};
	}
	
	@Override
	public BufferedImage getCurrentImage(PrintJob printJob) {
		return getCurrentImageFromCache(printJob);
	}
	
	@Override
	public boolean acceptsFile(File processingFile) {
		if (processingFile.getName().toLowerCase().endsWith(".imgzip") || processingFile.getName().toLowerCase().endsWith(".zip")) {
			if (zipHasGCode(processingFile) == false) {
				// if the zip does not have GCode, treat it as a zip of pngs
				logger.info("Accepting new printable {} as a {}", processingFile.getName(), this.getFriendlyName());
				return true;
			}
		}
		return false;
	}
	
	@Override
	public JobStatus processFile(PrintJob printJob) throws Exception {
		try {
			DataAid dataAid = initializeJobCacheWithDataAid(printJob);

			SortedMap<String, File> imageFiles = findImages(printJob.getJobFile());

			// FIXME: 2017/9/18 zyd add for detect machine state -s
			File cfgFile = findConfigFile(printJob.getJobFile());
			parseConfigFile(dataAid, cfgFile);

			printJob.setTotalSlices(imageFiles.size());

			NotificationManager.jobChanged(dataAid.printer, dataAid.printJob);
			if (dataAid.slicingProfile.getDetectionEnabled())
			{
				performDetectMaterialWeight(dataAid);
			}
			while (true)
			{
				if (dataAid.slicingProfile.getDetectionEnabled())
				{
					performDetectDoorLimit(dataAid);
					performDetectLedTemperature(dataAid);
					performDetectResinType(dataAid);
					performDetectLiquidLevel(dataAid);
				}
				if (dataAid.printer.isPrintPaused())
				{
					NotificationManager.jobChanged(dataAid.printer, dataAid.printJob);
					if (!dataAid.printer.waitForPauseIfRequired())
					{
						return dataAid.printer.getStatus();
					}
					NotificationManager.jobChanged(dataAid.printer, dataAid.printJob);
				}
				else
				{
					break;
				}
			}
			// FIXME: 2017/9/18 zyd add for detect machine state -e

			Iterator<File> imgIter = imageFiles.values().iterator();
			
			// Iterate the image stack up to the slice index requested by the customizer
			if (imgIter.hasNext()) {
				int sliceIndex = dataAid.customizer.getNextSlice();
				while (imgIter.hasNext() && sliceIndex > 0) {
					sliceIndex--;
					imgIter.next();
				}
			}

			// Preload first image then loop
			if (imgIter.hasNext()) {
				File imageFile = imgIter.next();

				// FIXME: 2017/10/12 zyd add for support svg file -s
				Future<RenderedData> prepareImage;
				if (imageFile.getName().toLowerCase().endsWith(".svg")) {
					if (HostProperties.Instance().isUseGraphicsMagick())
						prepareImage = Main.GLOBAL_EXECUTOR.submit(new GraphicsMagickRender(dataAid, this, imageFile));
					else
						prepareImage = Main.GLOBAL_EXECUTOR.submit(new SVGImageRender(dataAid, this, imageFile));
				}
				else
					prepareImage = Main.GLOBAL_EXECUTOR.submit(new SimpleImageRenderer(dataAid, this, imageFile));
				// FIXME: 2017/10/12 zyd add for support svg file -e

				performHeader(dataAid);

				boolean slicePending = true;

				do {

					JobStatus status = performPreSlice(dataAid, null);
					if (status != null) {
						break;
					}

					RenderedData imageData = prepareImage.get();
					dataAid.cache.setCurrentRenderingPointer(imageFile);
					
					if (imgIter.hasNext()) {
						imageFile = imgIter.next();
						// FIXME: 2017/10/12 zyd add for support svg file -s
						if (imageFile.getName().toLowerCase().endsWith(".svg")) {
							if (HostProperties.Instance().isUseGraphicsMagick())
								prepareImage = Main.GLOBAL_EXECUTOR.submit(new GraphicsMagickRender(dataAid, this, imageFile));
							else
								prepareImage = Main.GLOBAL_EXECUTOR.submit(new SVGImageRender(dataAid, this, imageFile));

						}
						else
							prepareImage = Main.GLOBAL_EXECUTOR.submit(new SimpleImageRenderer(dataAid, this, imageFile));
						// FIXME: 2017/10/12 zyd add for support svg file -e
					} else {
						slicePending = false;
					}

					status = printImageAndPerformPostProcessing(dataAid, imageData.getPrintableImage());

					if (status != null) {
						break;
					}
				} while (slicePending);
			}
			return performFooter(dataAid);
		} finally {
			clearDataAid(printJob);
		}
	}
	
	@Override
	public Double getBuildAreaMM(PrintJob processingFile) {
		DataAid aid = super.getDataAid(processingFile);
		
		if (aid == null || aid.cache.getCurrentArea() == null) {
			return null;
		}
		
		return aid.cache.getCurrentArea() / (aid.xPixelsPerMM * aid.yPixelsPerMM);
	}

	@Override
	public BufferedImage renderPreviewImage(DataAid dataAid) throws SliceHandlingException {
		try {
			prepareEnvironment(dataAid.printJob.getJobFile(), dataAid.printJob);
			
			SortedMap<String, File> imageFiles = findImages(dataAid.printJob.getJobFile());
			
			dataAid.printJob.setTotalSlices(imageFiles.size());
			Iterator<File> imgIter = imageFiles.values().iterator();
	
			// Preload first image then loop
			int sliceIndex = dataAid.customizer.getNextSlice();
			while (imgIter.hasNext() && sliceIndex > 0) {
				sliceIndex--;
				imgIter.next();
			}
			
			if (!imgIter.hasNext()) {
				throw new IOException("No Image Found for index:" + dataAid.customizer.getNextSlice());
			}
			File imageFile = imgIter.next();

			// FIXME: 2017/10/12 zyd add for support svg file -s
			RenderedData stdImage;
			if (imageFile.getName().toLowerCase().endsWith(".svg"))
			{
				if (HostProperties.Instance().isUseGraphicsMagick()) {
					GraphicsMagickRender renderer = new GraphicsMagickRender(dataAid, this, imageFile);
					stdImage = renderer.call();
				}
				else {
					SVGImageRender renderer = new SVGImageRender(dataAid, this, imageFile);
					stdImage = renderer.call();
				}
			}
			else
			{
				SimpleImageRenderer renderer = new SimpleImageRenderer(dataAid, this, imageFile);
				stdImage = renderer.call();
			}
			// FIXME: 2017/10/12 zyd add for support svg file -e
			return stdImage.getPrintableImage();
		} catch (IOException | JobManagerException e) {
			throw new SliceHandlingException(e);
		}
	}

	@Override
	public String getFriendlyName() {
		return "Zip of Slice Images";
	}
	
	private SortedMap<String, File> findImages(File jobFile) throws JobManagerException {
		// FIXME: 2017/10/12 zyd add for support svg file -s
		String [] extensions = {"png", "PNG", "svg", "SVG"};
		// FIXME: 2017/10/12 zyd add for support svg file -e
		boolean recursive = true;

		Collection<File> files =
				FileUtils.listFiles(buildExtractionDirectory(jobFile.getName()),
				extensions, recursive);

		TreeMap<String, File> images = new TreeMap<>(new AlphanumericComparator());

		for (File file : files) {
			images.put(file.getName(), file);
		}
		
		return images;
	}

	// FIXME: 2017/10/13 zyd add for zip's configuration file -s
	private File findConfigFile(File jobFile) throws JobManagerException{

		String[] extensions = {"cfg"};
		boolean recursive = true;

		List<File> files = new ArrayList<File>(FileUtils.listFiles(buildExtractionDirectory(jobFile.getName()), extensions, recursive));

		if (files.size() > 1){
			throw new JobManagerException("More than one cfg file exists in print directory");
		}else if (files.size() == 0){
			throw new JobManagerException("cfg file was not found. Did you include the cfg when you exported your scene?");
		}

		return files.get(0);
	}
	// FIXME: 2017/10/13 zyd add for zip's configuration file -e
}
