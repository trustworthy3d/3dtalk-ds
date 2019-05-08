package org.area515.resinprinter.job;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.apache.xpath.operations.String;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.exception.NoPrinterFoundException;
import org.area515.resinprinter.exception.SliceHandlingException;
import org.area515.resinprinter.job.Customizer.PrinterStep;
import org.area515.resinprinter.job.render.RenderingCache;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.printer.PrinterConfiguration;
import org.area515.resinprinter.printer.SlicingProfile;
import org.area515.resinprinter.printer.SlicingProfile.InkConfig;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.server.Main;
import org.area515.resinprinter.services.CustomizerService;
import org.area515.resinprinter.services.PrinterService;
import org.area515.resinprinter.slice.StlError;
import org.area515.util.Log4jTimer;
import org.area515.util.TemplateEngine;

public abstract class AbstractPrintFileProcessor<G,E> implements PrintFileProcessor<G,E>{
	private static final Logger logger = LogManager.getLogger();
	public static final String EXPOSURE_TIMER = "exposureTime";
	private Map<PrintJob, DataAid> renderingCacheByPrintJob;

	// FIXME: 2017/9/18 zyd add for detect machine state -s
	private Pattern GCODE_Weight_PATTERN = Pattern.compile("\\s*Weight:\\s*(-?[\\d\\.]+).*");
	private Pattern GCODE_Liquid_Level_PATTERN = Pattern.compile("\\s*Liquid_Level:([H,L]).*");
	private Pattern GCODE_Bottle_Resin_Type_PATTERN = Pattern.compile("\\s*Bottle_Resin_Type:(\\d+).*");
	private Pattern GCODE_Trough_Resin_Type_PATTERN = Pattern.compile("\\s*Trough_Resin_Type:(\\d+).*");
	private Pattern GCODE_Door_Limit_PATTERN = Pattern.compile("\\s*Door_Limit_State:([H,L]).*");
	private Pattern GCODE_Led_Temperature_PATTERN = Pattern.compile("\\s*Temperature_Alarm_State:([H,L]).*");
	
	private boolean needPerformAfterPause = false;
	// FIXME: 2017/9/18 zyd add for detect machine state -e
	
	public static class DataAid {
		public ScriptEngine scriptEngine;
		public Printer printer;
		public PrintJob printJob;
		public PrinterConfiguration configuration;
		public SlicingProfile slicingProfile;
		public InkConfig inkConfiguration;
		public double xPixelsPerMM;
		public double yPixelsPerMM;
		public int xResolution;
		public int yResolution;
		public double sliceHeight;
		public InkDetector inkDetector;
		public long currentSliceTime;
		public Paint maskPaint;
		public boolean optimizeWithPreviewMode;
		private AffineTransform affineTransform;
		public RenderingCache cache = new RenderingCache();
		public Customizer customizer;

		public DataAid(PrintJob printJob) throws JobManagerException {
			this.printJob = printJob;
			this.scriptEngine = HostProperties.Instance().buildScriptEngine();
			printer = printJob.getPrinter();
			printJob.setStartTime(System.currentTimeMillis());
		    configuration = printer.getConfiguration();
			slicingProfile = configuration.getSlicingProfile();
			inkConfiguration = slicingProfile.getSelectedInkConfig();
			xPixelsPerMM = slicingProfile.getDotsPermmX();
			yPixelsPerMM = slicingProfile.getDotsPermmY();
			xResolution = slicingProfile.getxResolution();
			yResolution = slicingProfile.getyResolution();
			optimizeWithPreviewMode = false;
			customizer = printJob.getCustomizer();
			
			if (customizer == null) {
				customizer = new Customizer();
				customizer.setNextStep(PrinterStep.PerformHeader);
				customizer.setNextSlice(0);
				customizer.setPrintableName(FilenameUtils.getBaseName(printJob.getJobFile().getName()));
				customizer.setPrintableExtension(FilenameUtils.getExtension(printJob.getJobFile().getName()));
				customizer.setPrinterName(printer.getName());
				customizer.setName(printJob.getJobFile().getName() + "." + printer.getName());
				Customizer otherCustomizer = CustomizerService.INSTANCE.getCustomizers().get(customizer.getName());
				if (otherCustomizer != null) {
					customizer.setName(customizer.getName() + "." + System.currentTimeMillis());
				}
				CustomizerService.INSTANCE.addOrUpdateCustomizer(customizer);
			}
			if (customizer.getNextStep() == null) {
				customizer.setNextStep(PrinterStep.PerformHeader);
			}
			if (customizer.getZScale() == null) {
				customizer.setZScale(1.0);
			}
			//We must make sure our customizer is perfectly setup at this point, everyone should be able to depend on our customizer after this setup process
			
			//This file processor requires an ink configuration
			if (inkConfiguration == null) {
				throw new JobManagerException("Your printer doesn't have a selected ink configuration.");
			}
			
			//TODO: how do I integrate slicingProfile.getLiftDistance()
			sliceHeight = inkConfiguration.getSliceHeight();
		}
		
		public AffineTransform getAffineTransform(BufferedImage buildPlatformImage, BufferedImage printImage) throws ScriptException {			
			if (customizer != null && customizer.getAffineTransformSettings() != null) {
				if (this.affineTransform == null || customizer.getAffineTransformSettings().getAffineTransformScriptCalculator() != null) {
					this.affineTransform = customizer.createAffineTransform(this, buildPlatformImage, printImage);
				}
			} else {
				this.affineTransform = new AffineTransform();
				affineTransform.translate(xResolution/2, yResolution/2);
				affineTransform.translate(-printImage.getWidth()/2 , -printImage.getHeight()/2);
			}
			
			return this.affineTransform;
		}
	}
	
	@Override
	public Double getBuildAreaMM(PrintJob printJob) {
		DataAid aid = getDataAid(printJob);
		if (aid == null) {
			return null;
		}
		
		Double area = aid.cache.getCurrentArea();
		if (area == null) {
			return null;
		}
		
		return aid.cache.getCurrentArea() / (aid.xPixelsPerMM * aid.yPixelsPerMM);
	}
	
	@Override
	public BufferedImage getCurrentImage(PrintJob printJob) {
		return getCurrentImageFromCache(printJob);
	}
	
	protected BufferedImage getCurrentImageFromCache(PrintJob printJob) {
		DataAid data = getDataAid(printJob);
		if (data == null) {
			return null;
		}
		
		ReentrantLock lock = data.cache.getCurrentLock();
		lock.lock();
		try {
			BufferedImage currentImage = data.cache.getCurrentImage();
			if (currentImage == null)
				return null;
			
			return currentImage.getSubimage(0, 0, currentImage.getWidth(), currentImage.getHeight());
		} finally {
			lock.unlock();
		}
	}
	
	private final Map<PrintJob, DataAid> getRenderingCacheByPrintJob() {
		if (renderingCacheByPrintJob == null) {
			renderingCacheByPrintJob = new HashMap<>();
		}
		
		return renderingCacheByPrintJob;
	}
	
	public final DataAid initializeJobCacheWithDataAid(PrintJob printJob) throws InappropriateDeviceException, JobManagerException {
		DataAid aid = createDataAid(printJob);
		getRenderingCacheByPrintJob().put(printJob, aid);
		return aid;
	}
	
	private void moveToNextPrinterStep(Customizer customizer, PrinterStep newState) {
		customizer.setNextStep(newState);
		CustomizerService.INSTANCE.addOrUpdateCustomizer(customizer);
	}

	// FIXME: 2017/9/18 zyd add for detect machine state -s
	public void performDetectMaterialWeight(DataAid aid) throws InappropriateDeviceException
	{
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		if (!aid.slicingProfile.getDetectionMaterialWeightEnabled())
			return;

		double estimateMaterialWeight = aid.printJob.getEstimateMaterialWeight();
		double currentMaterialWeight = 0;
		String receive = aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, "M270", false);
		Matcher matcher = GCODE_Weight_PATTERN.matcher(receive);
		if (matcher.find())
		{
			currentMaterialWeight = Double.parseDouble(matcher.group(1));
		}

		if (estimateMaterialWeight > currentMaterialWeight)
		{
			System.out.println("bottle out of material");
			aid.printer.setStatus(JobStatus.PausedBottleOutOfMaterial);
		}
	}

	public void parseConfigFile(DataAid aid, File cfgFile)
	{
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		try
		{
			InputStream stream = new FileInputStream(cfgFile);
			Properties properties = new Properties();
			properties.load(stream);

			double estimateMaterialWeight = new Double(properties.getProperty("estimateMaterialWeight", "0"));
			aid.printJob.setEstimateMaterialWeight(estimateMaterialWeight);

			double sliceHeight = new Double(properties.getProperty("sliceHeight", "0"));
			if (sliceHeight > 0)
				aid.inkConfiguration.setSliceHeight(sliceHeight);
			if (!aid.slicingProfile.getParameterEnabled())
			{
				int numberOfFirstLayers = new Integer(properties.getProperty("numberOfFirstLayers", "3"));
				int firstLayerTime = new Integer(properties.getProperty("firstLayerTime", "20000"));
				int layerTime = new Integer(properties.getProperty("layerTime", "8000"));
				int resumeLayerTime = new Integer(properties.getProperty("resumeLayerTime", "10000"));
				double liftDistance = new Double(properties.getProperty("liftDistance", "8"));
				double liftFeedSpeed = new Double(properties.getProperty("liftFeedSpeed", "500"));
				double liftRetractSpeed = new Double(properties.getProperty("liftRetractSpeed", "100"));
				int delayTimeBeforeSolidify = new Integer(properties.getProperty("delayTimeBeforeSolidify", "0"));
				int delayTimeAfterSolidify = new Integer(properties.getProperty("delayTimeAfterSolidify", "0"));
				int delayTimeAsLiftedTop = new Integer(properties.getProperty("delayTimeAsLiftedTop", "0"));
				int delayTimeForAirPump = new Integer(properties.getProperty("delayTimeForAirPump", "60"));

				aid.inkConfiguration.setNumberOfFirstLayers(numberOfFirstLayers);
				aid.inkConfiguration.setFirstLayerExposureTime(firstLayerTime);
				aid.inkConfiguration.setExposureTime(layerTime);
				aid.slicingProfile.setResumeLayerExposureTime(resumeLayerTime);
				aid.slicingProfile.setLiftDistance(liftDistance);
				aid.slicingProfile.setLiftFeedSpeed(liftFeedSpeed);
				aid.slicingProfile.setLiftRetractSpeed(liftRetractSpeed);
				aid.slicingProfile.setDelayTimeBeforeSolidify(delayTimeBeforeSolidify);
				aid.slicingProfile.setDelayTimeAfterSolidify(delayTimeAfterSolidify);
				aid.slicingProfile.setDelayTimeAsLiftedTop(delayTimeAsLiftedTop);
				aid.slicingProfile.setDelayTimeForAirPump(delayTimeForAirPump);
			}
		}
		catch (IOException e)
		{
			System.out.println(e.toString());
		}

	}

	public void performDetectDoorLimit(DataAid aid) throws InappropriateDeviceException, InterruptedException
	{
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		if (!aid.slicingProfile.getDetectionDoorLimitEnabled())
			return;

		if (aid.printer.getStatus().isPaused())
			return;

		//读取舱门状态
		String receive = aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, "M278", false);
		Matcher matcher = GCODE_Door_Limit_PATTERN.matcher(receive);
		if (matcher.find() && matcher.group(1).equals("L"))
		{
			aid.printer.setStatus(JobStatus.PausedDoorOpened);
		}
	}

	public void performDetectLedTemperature(DataAid aid) throws InappropriateDeviceException, InterruptedException
	{
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		if (aid.printer.getStatus().isPaused())
			return;

		//读取Led板温度状态
		String receive = aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, "M279", false);
		Matcher matcher = GCODE_Led_Temperature_PATTERN.matcher(receive);
		if (matcher.find() && matcher.group(1).equals("H"))
		{
			aid.printer.setStatus(JobStatus.PausedLedOverTemperature);
		}
	}

	public void performDetectResinType(DataAid aid) throws InappropriateDeviceException, InterruptedException
	{
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		if (!aid.slicingProfile.getDetectionResinTypeEnabled())
			return;

		if (aid.printer.getStatus().isPaused())
			return;

		String receive;
		Matcher matcher;

		//读取树脂材料瓶中的数据
		String Bottle_Resin_Type = "";
		for(int count = 0; count < 2; count++)
		{
			receive = aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, "M273", false);
			matcher = GCODE_Bottle_Resin_Type_PATTERN.matcher(receive);
			if (matcher.find())
			{
				Bottle_Resin_Type = matcher.group(1);
				break;
			}
		}

		//读取料槽中的数据
		receive = aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, "M274", false);
		matcher = GCODE_Trough_Resin_Type_PATTERN.matcher(receive);
		String Trough_Resin_Type = "";
		if (matcher.find())
		{
			Trough_Resin_Type = matcher.group(1);
		}

		if (!Bottle_Resin_Type.equals(Trough_Resin_Type) || Bottle_Resin_Type.equals("") || Trough_Resin_Type.equals(""))
		{
			aid.printer.setStatus(JobStatus.PausedUnconformableMaterial);
		}
	}

	public void performDetectLiquidLevel(DataAid aid) throws InappropriateDeviceException, InterruptedException
	{
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		if (!aid.slicingProfile.getDetectionLiquidLevelEnabled())
			return;

		if (aid.printer.getStatus().isPaused())
			return;

		//读取料槽液位限位状态
		int count = 4;
		while (count-- >= 0)
		{
			String receive = aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, "M276", false);
			Matcher matcher = GCODE_Liquid_Level_PATTERN.matcher(receive);
			if (matcher.find() && matcher.group(1).equals("L"))
			{
				if (count < 0)
				{
					aid.printer.setStatus(JobStatus.PausedGrooveOutOfMaterial);
					return;
				}
				else
				{
					aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, "M277 S1", false);
					// FIXME: 2017/9/15 zyd add for set delay time -s
					if (aid.slicingProfile.getDelayTimeForAirPump() > 0)
					{
						Thread.sleep(aid.slicingProfile.getDelayTimeForAirPump()*1000);
					}
					// FIXME: 2017/9/15 zyd add for set delay time -e
					aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, "M277 S0", false);
				}
			}
			else
				return;
		}
	}
	// FIXME: 2017/9/18 zyd add for detect machine state -e
	
	public void performHeader(DataAid aid) throws InappropriateDeviceException, IOException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		
		if (aid.printer.isProjectorPowerControlSupported()) {
			aid.printer.setProjectorPowerStatus(true);
		}

		//Set the default exposure time(this is only used if there isn't an exposure time calculator)
		aid.printJob.setExposureTime(aid.inkConfiguration.getExposureTime());

		//Perform the gcode associated with the printer start function
		if (aid.slicingProfile.getgCodeHeader() != null && 
			aid.slicingProfile.getgCodeHeader().trim().length() > 0 &&
			aid.customizer.getNextStep() == PrinterStep.PerformHeader) {
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeHeader(), true);
			moveToNextPrinterStep(aid.customizer, PrinterStep.PerformPreSlice);
		}
		
		if (aid.inkConfiguration != null) {
			aid.inkDetector = aid.inkConfiguration.getInkDetector(aid.printJob);
		}

		//Set the initial values for all variables.
		aid.printJob.setExposureTime(aid.inkConfiguration.getExposureTime());
		aid.printJob.setZLiftDistance(aid.slicingProfile.getLiftDistance());
		// FIXME: 2017/9/25 zyd add for parameters -s
		aid.printJob.setZLiftFeedSpeed(aid.slicingProfile.getLiftFeedSpeed());
		aid.printJob.setZLiftRetractSpeed(aid.slicingProfile.getLiftRetractSpeed());
		// FIXME: 2017/9/25 zyd add for parameters -e
		// FIXME: 2018/5/14 zyd add for set delay time -s
		aid.printJob.setDelayTimeBeforeSolidify(aid.slicingProfile.getDelayTimeBeforeSolidify());
		// FIXME: 2018/5/14 zyd add for set delay time -e

		//Initialize bulb hours only once per print
		aid.printer.getBulbHours();
	}
	
	public JobStatus performPreSlice(DataAid aid, List<StlError> errors) throws InappropriateDeviceException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		aid.currentSliceTime = System.currentTimeMillis();

		//Show the errors to our users if the stl file is broken, but we'll keep on processing like normal
		if (errors != null && !errors.isEmpty() && aid.customizer.getNextStep() == PrinterStep.PerformPreSlice) {
			NotificationManager.errorEncountered(aid.printJob, errors);
		}
		
		if (!aid.printer.isPrintActive()) {
			return aid.printer.getStatus();
		}

		//Execute preslice gcode
		if (aid.slicingProfile.getgCodePreslice() != null && 
			aid.slicingProfile.getgCodePreslice().trim().length() > 0 && 
			aid.customizer.getNextStep() == PrinterStep.PerformPreSlice) {
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodePreslice(), false);
		}
		
		moveToNextPrinterStep(aid.customizer, PrinterStep.PerformExposure);
		return null;
	}
	
	public JobStatus printImageAndPerformPostProcessing(DataAid aid, BufferedImage sliceImage) throws ExecutionException, InterruptedException, InappropriateDeviceException, ScriptException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		if (sliceImage == null) {
			throw new IllegalStateException("You must specify a sliceImage to display");
		}
		
		if (aid.customizer.getNextStep() != PrinterStep.PerformExposure) {
			return null;
		}
		
		//Start but don't wait for a potentially heavy weight operation to determine if we are out of ink.
		if (aid.inkDetector != null) {
			aid.inkDetector.startMeasurement();
		}

		if (!aid.printer.isPrintActive())
		{
			return aid.printer.getStatus();
		}

		//Determine the dynamic amount of time we should expose our resin
		if (aid.slicingProfile.getExposureTimeCalculator() != null && aid.slicingProfile.getExposureTimeCalculator().trim().length() > 0) {
			Number value = calculate(aid, aid.slicingProfile.getExposureTimeCalculator(), "exposure time script");
			if (value != null) {
				aid.printJob.setExposureTime(value.intValue());
			}
		}

		// FIXME: 2018/5/14 zyd add for set delay time -s
		if (aid.slicingProfile.getDelayTimeBeforeSolidifyCalculator() != null && aid.slicingProfile.getDelayTimeBeforeSolidifyCalculator().trim().length() > 0)
		{
			Number value = calculate(aid, aid.slicingProfile.getDelayTimeBeforeSolidifyCalculator(), "delay time before solidify script");
			if (value != null) {
				aid.printJob.setDelayTimeBeforeSolidify(value.intValue());
			}
		}
		if (aid.printJob.getDelayTimeBeforeSolidify() > 0)
		{
			Thread.sleep(aid.printJob.getDelayTimeBeforeSolidify());
		}
		// FIXME: 2018/5/14 zyd add for set delay time -e

		aid.printer.showImage(sliceImage);
		logger.info("ExposureStart:{}", ()->Log4jTimer.startTimer(EXPOSURE_TIMER));
		 	
		if (aid.slicingProfile.getgCodeShutter() != null && aid.slicingProfile.getgCodeShutter().trim().length() > 0) {
			aid.printer.setShutterOpen(true);
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeShutter(), false);
			aid.printer.startExposureTiming();
		}

		if (aid.slicingProfile.getDetectionEnabled())
		{
			Main.GLOBAL_EXECUTOR.submit(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						performDetectDoorLimit(aid);
						performDetectLedTemperature(aid);
						performDetectResinType(aid);
					} catch (Exception e)
					{
					}
				}
			});
		}

		//Sleep for the amount of time that we are exposing the resin.
		// FIXME: 2017/11/6 zyd add for increase exposure time if the job has been paused -s
		if (needPerformAfterPause)
		{
			if (aid.slicingProfile.getResumeLayerExposureTime() < aid.printJob.getExposureTime())
				Thread.sleep(aid.printJob.getExposureTime());
			else
				Thread.sleep(aid.slicingProfile.getResumeLayerExposureTime());
		}
		else
			Thread.sleep(aid.printJob.getExposureTime());
		// FIXME: 2017/11/6 zyd add for increase exposure time if the job has been paused -e


		if (aid.slicingProfile.getgCodeShutter() != null && aid.slicingProfile.getgCodeShutter().trim().length() > 0) {
			aid.printer.setShutterOpen(false);
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeShutter(), false);
			aid.printer.stopExposureTiming();
		}

		//Blank the screen
		aid.printer.showBlankImage();

		// FIXME: 2017/9/15 zyd add for set delay time -s
		if (aid.slicingProfile.getDelayTimeAfterSolidify() > 0)
		{
			Thread.sleep(aid.slicingProfile.getDelayTimeAfterSolidify());
		}
		// FIXME: 2017/9/15 zyd add for set delay time -e

		logger.info("ExposureTime:{}", ()->Log4jTimer.completeTimer(EXPOSURE_TIMER));

		//Perform two actions at once here:
		// 1. Pause if the user asked us to pause
		// 2. Get out if the print is cancelled
		// FIXME: 2017/9/18 zyd add for move z to min position as print job paused -s
		if (!aid.printJob.isZLiftDistanceOverriden() && aid.slicingProfile.getzLiftDistanceCalculator() != null && aid.slicingProfile.getzLiftDistanceCalculator().trim().length() > 0) {
			Number value = calculate(aid, aid.slicingProfile.getzLiftDistanceCalculator(), "lift distance script");
			if (value != null) {
				aid.printJob.setZLiftDistance(value.doubleValue());
			}
		}
		// FIXME: 2017/9/25 zyd add for parameter -s
		if (!aid.printJob.isZLiftFeedSpeedOverriden() && aid.slicingProfile.getzLiftFeedSpeedCalculator() != null && aid.slicingProfile.getzLiftFeedSpeedCalculator().trim().length() > 0) {
			Number value = calculate(aid, aid.slicingProfile.getzLiftFeedSpeedCalculator(), "lift feed speed script");
			if (value != null) {
				aid.printJob.setZLiftFeedSpeed(value.doubleValue());
			}
		}

		if (!aid.printJob.isZLiftFeedSpeedOverriden() && aid.slicingProfile.getzLiftRetractSpeedCalculator() != null && aid.slicingProfile.getzLiftRetractSpeedCalculator().trim().length() > 0) {
			Number value = calculate(aid, aid.slicingProfile.getzLiftRetractSpeedCalculator(), "lift retract speed script");
			if (value != null) {
				aid.printJob.setZLiftRetractSpeed(value.doubleValue());
			}
		}
		// FIXME: 2017/9/25 zyd add for parameter -e
		//Perform the lift feed gcode manipulation
		aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeLiftFeed(), false);

		needPerformAfterPause = false;
		while (true)
		{
			if (!aid.printer.isPrintActive())
			{
				return aid.printer.getStatus();
			}

			if (aid.printer.isPrintPaused())
			{
				needPerformAfterPause = true;

				NotificationManager.jobChanged(aid.printer, aid.printJob);
				if (aid.slicingProfile.getgCodeBeforePause() != null && aid.slicingProfile.getgCodeBeforePause().trim().length() > 0) {
					aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeBeforePause(), false);
				}
				if (!aid.printer.waitForPauseIfRequired())
				{
					return aid.printer.getStatus();
				}
				NotificationManager.jobChanged(aid.printer, aid.printJob);
			}

			if (aid.slicingProfile.getDetectionEnabled())
			{
				if (needPerformAfterPause)
				{
					performDetectDoorLimit(aid);
					performDetectLedTemperature(aid);
					performDetectResinType(aid);
				}
				performDetectLiquidLevel(aid);
			}

			if (!aid.printer.isPrintPaused())
			{
				if (needPerformAfterPause)
				{
					if (aid.slicingProfile.getgCodeAfterPause() != null && aid.slicingProfile.getgCodeAfterPause().trim().length() > 0)
					{
						aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeAfterPause(), false);
					}
				}

				break;
			}
		}
		//Perform the lift retract gcode manipulation
		aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeLiftRetract(), false);
		// FIXME: 2017/9/18 zyd add for move z to min position as print job paused -e

		Double buildArea = getBuildAreaMM(aid.printJob);
		// Log slice settings (in JSON for extraction and processing)
		logger.info("{ \"layer\": {}, \"exposureTime\": {}, \"liftDistance\": {}, \"liftFeedSpeed\": {} , \"liftRetractSpeed\": {} , \"layerAreaMM2\": {} }",
			aid.printJob.getCurrentSlice(), aid.printJob.getExposureTime(), aid.printJob.getZLiftDistance(),
			aid.printJob.getZLiftFeedSpeed(), aid.printJob.getZLiftRetractSpeed(), buildArea);
		
		//Perform area and cost manipulations for current slice
		aid.printJob.addNewSlice(System.currentTimeMillis() - aid.currentSliceTime, buildArea);
		
		//Notify the client that the printJob has increased the currentSlice
		NotificationManager.jobChanged(aid.printer, aid.printJob);
		
		moveToNextPrinterStep(aid.customizer, PrinterStep.PerformPreSlice);
		
		return null;
	}

	public JobStatus performFooter(DataAid aid) throws IOException, InappropriateDeviceException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		if (aid.slicingProfile.getgCodeFooter() != null && aid.slicingProfile.getgCodeFooter().trim().length() > 0) {
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeFooter(), false);
		}
		
		if (aid.printer.isProjectorPowerControlSupported()) {
			aid.printer.setProjectorPowerStatus(false);
		}

		if (!aid.printer.isPrintActive()) {
			return aid.printer.getStatus();
		}

		return JobStatus.Completed;
	}

	private Number calculate(DataAid aid, String calculator, String calculationName) throws ScriptException {
		try {
			Number num = (Number)TemplateEngine.runScript(aid.printJob, aid.printer, aid.scriptEngine, calculator, calculationName, null);
			if (num == null || Double.isNaN(num.doubleValue())) {
				return null;
			}
			return num;
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("The result of your " + calculationName + " needs to evaluate to an instance of java.lang.Number");
		}
	}

	public void applyBulbMask(DataAid aid, Graphics2D g2, int width, int height) throws ScriptException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		
		if (aid.slicingProfile.getProjectorGradientCalculator() == null || aid.slicingProfile.getProjectorGradientCalculator().trim().length() == 0) {
			return;
		}
		
		if (!aid.configuration.getMachineConfig().getMonitorDriverConfig().isUseMask()) {
			return;
		}
		
		try {
			if (aid.maskPaint == null) {
				aid.maskPaint = (Paint)TemplateEngine.runScript(aid.printJob, aid.printer, aid.scriptEngine, aid.slicingProfile.getProjectorGradientCalculator(), "projector gradient script", null);
			}
			g2.setPaint(aid.maskPaint);
			g2.fillRect(0, 0, width, height);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("The result of your bulb mask script needs to evaluate to an instance of java.awt.Paint");
		}
	}

	public BufferedImage applyImageTransforms(DataAid aid, BufferedImage img) throws ScriptException, JobManagerException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		if (img == null) {
			throw new IllegalStateException("BufferedImage is null");
		}

		if (aid.optimizeWithPreviewMode) {
			return img;
		}
		
		/*try {
			ImageIO.write(img, "png",  new File("start.png"));
		} catch (IOException e) {
		}//*/

		BufferedImage after = new BufferedImage(aid.xResolution, aid.yResolution, BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D g = (Graphics2D)after.getGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, aid.xResolution, aid.yResolution);
		
		/*try {
			ImageIO.write(after, "png",  new File("afterFill.png"));
		} catch (IOException e) {
		}//*/
		
		AffineTransform transform = aid.getAffineTransform(after, img);
		g.drawImage(img, transform, null);
		
		/*try {
			ImageIO.write(after, "png",  new File("afterDraw.png"));
		} catch (IOException e) {
		}//*/
		if (aid.customizer.getImageManipulationCalculator() != null && aid.customizer.getImageManipulationCalculator().trim().length() > 0) {
			Map<String, Object> overrides = new HashMap<>();
			overrides.put("affineTransform", transform);
			TemplateEngine.runScriptInImagingContext(after, img, aid.printJob, aid.printer, aid.scriptEngine, overrides, aid.customizer.getImageManipulationCalculator(), "Image manipulation script", false);
		}
		/*try {
			ImageIO.write(after, "png",  new File("afterImageManipulation.png"));
		} catch (IOException e) {
		}//*/
		applyBulbMask(aid, (Graphics2D)after.getGraphics(), aid.xResolution, aid.yResolution);
		/*try {
			ImageIO.write(after, "png",  new File("afterBulbMask.png"));
		} catch (IOException e) {
		}//*/

		return after;
	}
	
	public BufferedImage buildPreviewSlice(Customizer customizer, File jobFile, Previewable previewable) throws NoPrinterFoundException, SliceHandlingException {
		//find the first activePrinter
		String printerName = customizer.getPrinterName();
		Printer activePrinter = null;
		if (printerName == null || printerName.isEmpty()) {
			//if customizer doesn't have a printer stored, set first active printer as printer
			try {
				activePrinter = PrinterService.INSTANCE.getFirstAvailablePrinter();				
			} catch (NoPrinterFoundException e) {
				throw new NoPrinterFoundException("No printers found for slice preview. You must have a started printer or specify a valid printer in the Customizer.");
			}
			
		} else {
			try {
				activePrinter = PrinterService.INSTANCE.getPrinter(printerName);
			} catch (InappropriateDeviceException e) {
				logger.warn("Could not locate printer {}", printerName, e);
			}
		}

		try {
			//instantiate a new print job based on the jobFile and set its printer to activePrinter
			PrintJob printJob = new PrintJob(jobFile);
			printJob.setPrinter(activePrinter);
			printJob.setCustomizer(customizer);
			printJob.setPrintFileProcessor(this);
			printJob.setCurrentSlice(customizer.getNextSlice());
			
			//instantiate new dataaid
			DataAid dataAid = createDataAid(printJob); //TODO: Eventually we should just use the internal cache inside the dataAid
			BufferedImage image = customizer.getOrigSliceCache();
			
			if (image == null) {
				dataAid.optimizeWithPreviewMode = true;
				image = previewable.renderPreviewImage(dataAid);
				dataAid.optimizeWithPreviewMode = false;
				customizer.setOrigSliceCache(image);
			}
			
			image = applyImageTransforms(dataAid, image);
			return image;
		} catch (ScriptException | JobManagerException e) {
			throw new SliceHandlingException(e);
		}
	}
	
	public DataAid createDataAid(PrintJob printJob) throws JobManagerException {
		return new DataAid(printJob);
	}

	public DataAid getDataAid(PrintJob job) {
		return getRenderingCacheByPrintJob().get(job);
	}
	
	public void clearDataAid(PrintJob job) {
		getRenderingCacheByPrintJob().remove(job);
	}
}
