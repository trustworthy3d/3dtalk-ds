package org.area515.resinprinter.gcode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.AlreadyAssignedException;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.JobStatus;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.printer.MachineConfig;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.serial.SerialManager;
import org.area515.util.IOUtilities;
import org.area515.util.IOUtilities.ParseState;
import org.area515.util.TemplateEngine;

import freemarker.template.TemplateException;

public abstract class GCodeControl {
	public static Logger logger = LogManager.getLogger();
	private int SUGGESTED_TIMEOUT_FOR_ONE_GCODE = 1000 * 60 * 2;//2 minutes
	private Pattern GCODE_RESPONSE_PATTERN = Pattern.compile("(?i)(?:(.*o?k)(.*)|<?([^>]*)>|\\[?([^]]*)\\])\r?\n");
	// FIXME: 2017/9/1 zyd add for read completed response -s
	private Pattern GCODE_COMPLETED_PATTERN = Pattern.compile("(cmd_comp)(.*)\r?\n");
	// FIXME: 2017/9/1 zyd add for read completed response -e

    private Printer printer;
    private ReentrantLock gCodeLock = new ReentrantLock();
    private StringBuilder builder = new StringBuilder();
    private int parseLocation = 0;
    private int gcodeTimeout;
    private boolean restartSerialOnTimeout;

	// FIXME: 2017/9/28 zyd add for gcode log -s
	private ArrayList<String> gcodeLogHistory = new ArrayList<String>(200);
	// FIXME: 2017/9/28 zyd add for gcode log -e

	public GCodeControl(Printer printer) {
    	this.printer = printer;
    	this.gcodeTimeout = printer.getConfiguration().getMachineConfig().getPrinterResponseTimeoutMillis() != null?printer.getConfiguration().getMachineConfig().getPrinterResponseTimeoutMillis():SUGGESTED_TIMEOUT_FOR_ONE_GCODE;
    	this.restartSerialOnTimeout = printer.getConfiguration().getMachineConfig().getRestartSerialOnTimeout() != null?printer.getConfiguration().getMachineConfig().getRestartSerialOnTimeout():false;
    }
	
    private Printer getPrinter() {
    	return printer;
    }

	// FIXME: 2017/9/28 zyd add for gcode log -s
	private void addGCodeLog(String log)
	{
		if (gcodeLogHistory.size() == 200)
		{
			gcodeLogHistory.remove(0);
		}
		gcodeLogHistory.add(log);
		NotificationManager.sendMessage(getGCodeLog());
	}

	public String getGCodeLog()
	{
		return StringUtils.join(gcodeLogHistory.toArray());
	}
	// FIXME: 2017/9/28 zyd add for gcode log -e

	private PrinterResponse readUntilOkOrStoppedPrinting(boolean exitIfPrintInactive) throws IOException {
		PrinterResponse line = null;
		StringBuilder responseBuilder = new StringBuilder();
		ParseState state = null;
		Matcher matcher = null;
		do {
			state = IOUtilities.readLine(exitIfPrintInactive?getPrinter():null, getPrinter().getPrinterFirmwareSerialPort(), builder, parseLocation, 5000, IOUtilities.CPU_LIMITING_DELAY);
			parseLocation = state.parseLocation;
			if (state.currentLine != null) {
				if (line == null) {
					line = new PrinterResponse();
					line.setFullResponse(responseBuilder);
				}
				responseBuilder.append(state.currentLine);
				matcher = GCODE_RESPONSE_PATTERN.matcher(state.currentLine);
				line.setLastLineMatcher(matcher);
			}
			
			logger.info("Read: {}", state.currentLine);
			addGCodeLog(state.currentLine);

		} while (matcher != null && !matcher.matches() && !state.timeout);
		if (state.timeout && restartSerialOnTimeout) {
			try {
				getPrinter().getPrinterFirmwareSerialPort().restartCommunications();
			} catch (AlreadyAssignedException | InappropriateDeviceException e) {
				throw new IOException("Problems restarting serial port:" + getPrinter().getPrinterFirmwareSerialPort(), e);
			}
		}
		return line;
	}

	// FIXME: 2017/9/1 zyd add for read completed response -s
	private PrinterResponse readUntilCompletedOrStoppedPrinting(boolean exitIfPrintInactive) throws IOException
	{
		PrinterResponse line = null;
		StringBuilder responseBuilder = new StringBuilder();
		ParseState state = null;
		Matcher matcher = null;
		do {
			state = IOUtilities.readLine(exitIfPrintInactive?getPrinter():null, getPrinter().getPrinterFirmwareSerialPort(), builder, parseLocation, gcodeTimeout, IOUtilities.CPU_LIMITING_DELAY);
			parseLocation = state.parseLocation;
			if (state.currentLine != null) {
				if (line == null) {
					line = new PrinterResponse();
					line.setFullResponse(responseBuilder);
				}
				responseBuilder.append(state.currentLine);
				matcher = GCODE_COMPLETED_PATTERN.matcher(state.currentLine);
				line.setLastLineMatcher(matcher);
			}

			logger.info("Read: {}", state.currentLine);
			addGCodeLog(state.currentLine);
		} while (matcher != null && !matcher.matches() && !state.timeout);
		if (state.timeout && restartSerialOnTimeout) {
			try {
				getPrinter().getPrinterFirmwareSerialPort().restartCommunications();
			} catch (AlreadyAssignedException | InappropriateDeviceException e) {
				throw new IOException("Problems restarting serial port:" + getPrinter().getPrinterFirmwareSerialPort(), e);
			}
		}
		return line;
	}

	private boolean isResponseOk(Matcher matcher)
	{
		if (matcher.matches() && matcher.group(1).toLowerCase().endsWith("ok"))
		{
			return true;
		}
		return false;
	}

	private boolean isResponseError(String response)
	{
		Pattern GCODE_ERROR_PATTERN = Pattern.compile("\\s*(fatal:|error:).*");
		Matcher matcher = GCODE_ERROR_PATTERN.matcher(response);
		if (matcher.find())
		{
			return true;
		}
		return false;
	}
	// FIXME: 2017/9/1 zyd add for read completed response -e
	
	private boolean isPausableError(Matcher matcher, PrintJob printJob) {
		if (matcher.group(1) == null || !matcher.group(1).toLowerCase().endsWith("rror:")) {
			return false;
		}
		
		String responseRegEx = printJob.getPrinter().getConfiguration().getMachineConfig().getPauseOnPrinterResponseRegEx();
		return responseRegEx != null && responseRegEx.trim().length() > 0 && matcher.group(2) != null && matcher.group(2).matches(responseRegEx);
	}
	
	String sendGcodeAndRespectPrinter(PrintJob printJob, String cmd) throws IOException
	{
		gCodeLock.lock();
		try
		{
			if (!cmd.endsWith("\n"))
			{
				cmd += "\n";
			}

			StringBuilder builder = new StringBuilder();

			logger.info("Write : {}", cmd);
			// FIXME: 2017/9/28 zyd add for gcode log -s
			addGCodeLog("Write: " + cmd);
			// FIXME: 2017/9/28 zyd add for gcode log -e
			int resend = 0;
			while (true)
			{
				getPrinter().getPrinterFirmwareSerialPort().write(cmd.getBytes());
				PrinterResponse response = readUntilOkOrStoppedPrinting(false);
				if (response == null || isResponseError(response.getFullResponse().toString()))
				{
					if (resend >= 1)
					{
						getPrinter().setStatus(JobStatus.ErrorControlBoard);
						NotificationManager.printerChanged(getPrinter());
						return "";//I think this should be null, but I'm preserving backwards compatibility
					}
					else
					{
						resend++;
						continue;
					}
				}

				// FIXME: 2017/9/1 zyd add for read completed response -s
				builder.append(response.getFullResponse().toString());

				if (isResponseOk(response.getLastLineMatcher()))
				{
					PrinterResponse response_completed = readUntilCompletedOrStoppedPrinting(false);
					if (response_completed != null)
						builder.append(response_completed.getFullResponse().toString());
				}
				// FIXME: 2017/9/1 zyd add for read completed response -e

				if (isPausableError(response.getLastLineMatcher(), printJob))
				{
					printJob.setErrorDescription(response.getLastLineMatcher().group(2));
					logger.info("Received error from printer:" + response.getLastLineMatcher().group(2));
					getPrinter().setStatus(JobStatus.PausedWithWarning);
					NotificationManager.jobChanged(getPrinter(), printJob);
				}
				return builder.toString();
			}
		} finally
		{
			gCodeLock.unlock();
		}
	}
    
    public String sendGcode(String cmd) {
		gCodeLock.lock();
        try {
        	if (!cmd.endsWith("\n")) {
        		cmd += "\n";
        	}
			StringBuilder builder = new StringBuilder();
        	
        	logger.info("Write: {}", cmd);
			addGCodeLog("Write: " + cmd);
			int resend = 0;
			while (true)
			{
				getPrinter().getPrinterFirmwareSerialPort().write(cmd.getBytes());
				PrinterResponse response = readUntilOkOrStoppedPrinting(false);
				if (response == null || isResponseError(response.getFullResponse().toString()))
				{
					if (resend >= 1)
					{
						getPrinter().setStatus(JobStatus.ErrorControlBoard);
						NotificationManager.printerChanged(getPrinter());
						return "";//I think this should be null, but I'm preserving backwards compatibility
					}
					else
					{
						resend++;
						continue;
					}
				}

				// FIXME: 2017/9/1 zyd add for read completed response -s
				builder.append(response.getFullResponse().toString());

				if (isResponseOk(response.getLastLineMatcher()))
				{
					PrinterResponse response_completed = readUntilCompletedOrStoppedPrinting(false);
					if (response_completed != null)
						builder.append(response_completed.getFullResponse().toString());
				}
				// FIXME: 2017/9/1 zyd add for read completed response -e
				return builder.toString();
			}
		} catch (IOException ex) {
        	logger.error("Couldn't send:" + cmd, ex);
        	return "IO Problem!";
        } finally {
        	gCodeLock.unlock();
        }
    }
    
    /**
     * Unfortunately this chitchat isn't like gcode responses. Instead, the reads seem to go on forever without an indication of 
     * when they are going to stop. 
     * 
     * @return
     * @throws IOException
     */
    public String readWelcomeChitChat() throws IOException {
		try {
			StringBuilder builder = new StringBuilder();
			builder.append(IOUtilities.readWithTimeout(getPrinter().getPrinterFirmwareSerialPort(), SerialManager.READ_TIME_OUT, SerialManager.CPU_LIMITING_DELAY));
			Thread.sleep(4000);
			String str = executeSetAbsolutePositioning();
			if (str.equals(""))
				throw new IOException();
			builder.append(str);
			return builder.toString();
		} catch (InterruptedException e) {
			throw new IOException();
		}
    }
    public String executeSetAbsolutePositioning() {
    	return sendGcode("G90\r\n");
    }
    public String executeSetRelativePositioning() {
    	return sendGcode("G91\r\n");
    }
    public String executeMoveX(double dist) {
    	return sendGcode(String.format("G1 X%1.3f\r\n", dist));
    }
    public String executeMoveY(double dist) {
    	return sendGcode(String.format("G1 Y%1.3f\r\n", dist));
    }
    public String executeMoveZ(double dist) {
    	return sendGcode(String.format("G1 Z%1.3f\r\n", dist));
    }
    public String executeMotorsOn() {
    	return sendGcode("M17\r\n");
    }
    public String executeMotorsOff() {
    	return sendGcode("M18\r\n");
    }
    public String executeXHome() {
        return sendGcode("G28 X\r\n");
    }
    public String executeYHome() {
        return sendGcode("G28 Y\r\n");
    }
    public String executeZHome() {
        return sendGcode("G28 Z\r\n");
    }
	public String executeHomeAll() {
        return sendGcode("G28\r\n");
    }

	// FIXME: 2017/10/26 zyd add for execute query temperature -s
	public String executeQueryTemperature() {
		return sendGcode("M105\r\n");
	}
	// FIXME: 2017/10/26 zyd add for execute query temperature -e
	// FIXME: 2017/9/20 zyd add for execute shutter -s
	public String executeShutterOn() {
		return sendGcode("M106 S255\r\n");
	}
	public String executeShutterOff() {
		return sendGcode("M106 S0\r\n");
	}
	// FIXME: 2017/9/20 zyd add for execute shutter -e
	// FIXME: 2017/9/26 zyd add for execute weight -s
	public String executeMaterialWeight() {
		String materialWeight = "0";
		String receive = sendGcode("M270\r\n");
		Pattern GCODE_Weight_PATTERN = Pattern.compile("\\s*Weight:\\s*(-?[\\d\\.]+).*");
		Matcher matcher = GCODE_Weight_PATTERN.matcher(receive);
		if (matcher.find())
		{
			materialWeight = matcher.group(1);
		}
		return materialWeight;
	}
	public String executeNetWeight() {
		return sendGcode("M271 S1\r\n");
	}
	// FIXME: 2017/9/26 zyd add for execute weight -e
	// FIXME: 2017/10/20 zyd add for execute water pump -s
	public String executeWaterPumpOn() {
		return sendGcode("M266 S1\r\n");
	}
	public String executeWaterPumpOff() {
		return sendGcode("M266 S0\r\n");
	}
	// FIXME: 2017/10/20 zyd add for execute water pump -e
	// FIXME: 2017/11/1 zyd add for execute detect machine status -s
	public String executeDetectLiquidLevel() {
		String Liquid_Level = "L";
		Pattern GCODE_Liquid_Level_PATTERN = Pattern.compile("\\s*Liquid_Level:([H,L]).*");
		String receive = sendGcode("M276\r\n");
		Matcher matcher = GCODE_Liquid_Level_PATTERN.matcher(receive);
		if (matcher.find())
		{
			Liquid_Level = matcher.group(1);
		}
		return Liquid_Level;
	}
	public String executeDetectBottleType() {
		String Bottle_Resin_Type = "0";
		Pattern GCODE_Bottle_Resin_Type_PATTERN = Pattern.compile("\\s*Bottle_Resin_Type:(\\d+).*");
		String receive = sendGcode("M273\r\n");
		Matcher matcher = GCODE_Bottle_Resin_Type_PATTERN.matcher(receive);
		if (matcher.find())
		{
			Bottle_Resin_Type = matcher.group(1);
		}
		return Bottle_Resin_Type;
	}
	public String executeDetectTroughType() {
		String Trough_Resin_Type = "0";
		Pattern GCODE_Trough_Resin_Type_PATTERN = Pattern.compile("\\s*Trough_Resin_Type:(\\d+).*");
		String receive = sendGcode("M274\r\n");
		Matcher matcher = GCODE_Trough_Resin_Type_PATTERN.matcher(receive);
		if (matcher.find())
		{
			Trough_Resin_Type = matcher.group(1);
		}
		return Trough_Resin_Type;
	}
	// FIXME: 2017/11/1 zyd add for execute detect machine status -e
	// FIXME: 2018/1/26 zyd add for led pwm value -s
	public String executeReadLedPwmValue() {
		String Led_Pwm_Value = "0";
		Pattern GCODE_LED_PWM_VAL_PATTERN = Pattern.compile("\\s*UVLED_PWM_VAL:(\\d+).*");
		String receive = sendGcode("M268\r\n");
		Matcher matcher = GCODE_LED_PWM_VAL_PATTERN.matcher(receive);
		if (matcher.find())
		{
			Led_Pwm_Value = matcher.group(1);
		}
		return Led_Pwm_Value;
	}

	public String executeWriteLedPwmValue(int value) {
        if (value >=0 && value <= 255) {
            return sendGcode(String.format("M267 S%d\r\n", value));
        }
        return null;
    }
	// FIXME: 2018/1/26 zyd add for led pwm value -e

	// FIXME: 2018/6/7 zyd add for get firmware version -s
	public String executeGetFirmwareVersion() {
		String Firmware_Version = "1.0.0";
		Pattern GCODE_FIRMWARE_PATTERN = Pattern.compile("\\s*FIRMWARE_NAME:Repetier_([\\d\\.]+).*");
		String receive = sendGcode("M115\r\n");
		Matcher matcher = GCODE_FIRMWARE_PATTERN.matcher(receive);
		if (matcher.find())
		{
			Firmware_Version = matcher.group(1);
		}
		return Firmware_Version;
	}
	// FIXME: 2018/6/7 zyd add for get firmware version -e

	private void parseCommentCommand(String comment) {
		//If a comment was encountered, parse it to determine if something interesting was in there.
		Pattern delayPattern = Pattern.compile(";\\s*<\\s*Delay\\s*>\\s*(\\d+).*", Pattern.CASE_INSENSITIVE);
		Matcher matcher = delayPattern.matcher(comment);
		if (matcher.matches()) {
			try {
				int sleepTime = Integer.parseInt(matcher.group(1));
				logger.info("Sleep:{}", sleepTime);
				Thread.sleep(sleepTime);
				logger.info("Sleep complete");
			} catch (InterruptedException e) {
				logger.error("Interrupted while waiting for sleep to complete.", e);
			}
		}
    }
    
    public String executeGCodeWithTemplating(PrintJob printJob, String gcodes, boolean stopSendingGCodeWhenPrintInactive) throws InappropriateDeviceException {
		Pattern gCodePattern = Pattern.compile("\\s*([^;]*)\\s*(;.*)?", Pattern.CASE_INSENSITIVE);
		try {
			if (gcodes == null || gcodes.trim().isEmpty()) {
				return null;
			}
			
			StringBuilder buffer = new StringBuilder();
			gcodes = TemplateEngine.buildData(printJob, printJob.getPrinter(), gcodes);
			if (gcodes == null) {
				return null;
			}
			
			for (String gcode : gcodes.split("[\r]?\n")) {
				if (stopSendingGCodeWhenPrintInactive && !printJob.getPrinter().isPrintActive()) {
					break;
				}
				
				if (gcode != null) {
					Matcher matcher = gCodePattern.matcher(gcode);
					if (matcher.matches()) {
						String singleGCode = matcher.group(1);
						String comment = matcher.group(2);
						if (singleGCode != null && singleGCode.trim().length() > 0) {
							buffer.append(sendGcodeAndRespectPrinter(printJob, singleGCode));
						}
						if (comment != null) {
							parseCommentCommand(comment);
						}
					}
				}
			}
			
			return buffer.toString();
		} catch (IOException | TemplateException e) {
			throw new InappropriateDeviceException(MachineConfig.NOT_CAPABLE, e);
		}
    }
}
