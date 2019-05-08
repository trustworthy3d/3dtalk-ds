package org.area515.resinprinter.gcode;

import javax.script.ScriptException;

import org.apache.commons.lang3.StringUtils;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.AbstractPrintFileProcessorTest;
import org.area515.resinprinter.job.PrintJob;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestGCodeTemplating {
	@Test
	public void testEmptyGCode() throws Exception {
		AbstractPrintFileProcessor processor = Mockito.mock(AbstractPrintFileProcessor.class, Mockito.CALLS_REAL_METHODS);
		PrintJob printJob = AbstractPrintFileProcessorTest.createTestPrintJob(processor);
		Assert.assertNull(printJob.getPrinter().getGCodeControl().executeGCodeWithTemplating(printJob, null, true));
		Assert.assertNull(printJob.getPrinter().getGCodeControl().executeGCodeWithTemplating(printJob, " ", true));
	}
	
	@Test
	public void testBlockOfGCode() throws Exception {
		AbstractPrintFileProcessor processor = Mockito.mock(AbstractPrintFileProcessor.class, Mockito.CALLS_REAL_METHODS);
		PrintJob printJob = AbstractPrintFileProcessorTest.createTestPrintJob(processor);
		String gcodes = "G1 Z${ZLiftDist} F${ZLiftRate}\nG1 Z-${(ZLiftDist - LayerThickness)} F180;\n\nM18\n; <    dElAy >   ${ZLiftDist * ZLiftRate};\n;";
		Mockito.when(printJob.getPrinter().getGCodeControl().sendGcodeAndRespectPrinter(Mockito.any(PrintJob.class), Mockito.any(String.class)))
			.then(new Answer<String>() {
				private int count = 0;

				@Override
				public String answer(InvocationOnMock invocation) throws Throwable {
					switch (count) {
						case 0:
							Assert.assertEquals("G1 Z0 F0", invocation.getArguments()[1]);
							break;
						case 1:
							Assert.assertEquals("G1 Z-0 F180", invocation.getArguments()[1]);
							break;
						case 2:
							Assert.assertEquals("M18", invocation.getArguments()[1]);
							break;
						case 3:
						case 4:
							Assert.fail("Photocentric firmware can't take empty strings");
							break;
					}
					count++;
					return (String)"ok";
				}
			});
		printJob.getPrinter().getGCodeControl().executeGCodeWithTemplating(printJob, gcodes, true);
	}

	// FIXME: 2017/9/18 zyd add for test gcode -s
	@Test
	public void testPattern()
	{
		String str;
		Pattern gCodePattern = Pattern.compile("\\s*Weight:\\s*(-?\\d+\\.\\d+).*");
		Pattern GCODE_Liquid_Level_PATTERN = Pattern.compile("\\s*Liquid_Level:([H,L]).*");
		Pattern GCODE_Bottle_Resin_Type_PATTERN = Pattern.compile("\\s*Bottle_Resin_Type:(\\d+).*");
		Pattern GCODE_Trough_Resin_Type_PATTERN = Pattern.compile("\\s*Trough_Resin_Type:(\\d+).*");
		Pattern GCODE_Door_Limit_PATTERN = Pattern.compile("\\s*Door_Limit:([H,L]).*");
		Pattern GCODE_M114_RESP_PATTERN = Pattern.compile("\\s*.*Z:(-?\\d+\\.\\d+).*");
		Pattern GCODE_Led_Temperature_PATTERN = Pattern.compile("\\s*Led_Limit:([H,L]).*");
		Pattern GCODE_Temperature_PATTERN = Pattern.compile("\\s*T:\\s*(-?[\\d\\.]+).*B:(-?[\\d\\.]+).*");
		Pattern GCODE_ERROR_PATTERN = Pattern.compile("\\s*(fatal:|error:).*");
		Pattern GCODE_FIRMWARE_PATTERN = Pattern.compile("\\s*FIRMWARE_NAME:Repetier_([\\d\\.]+).*");
		str = "Weight:1.2g";
		str = "Liquid_Level:H";
		str = "Bottle_Resin_Type:200";
		str = "Trough_Resin_Type:200";
		str = "X:0.00 Y:0.00 Z:10.000 E:NAN\r\n";
		str = "Led_Limit:H";
		str = "OK\nDoor_Limit:H\ncmd_comp\n";
		str = "error:\n" +
				"Door_Limit:H\n" +
				"cmd_comp\n";
		str = "FIRMWARE_NAME:Repetier_1.1.2our ";
		//str = "T:25.00 /0 B:24.03 /0 B@:0 @:0";
		Matcher matcher = GCODE_FIRMWARE_PATTERN.matcher(str);
		if (matcher.find())
		{
			System.out.println(matcher.group(1));
		}
	}
	// FIXME: 2017/9/18 zyd add for test gcode -e

	// FIXME: 2017/9/28 zyd add for test arraylist -s
	@Test
	public void testArrayList()
	{
		ArrayList<String> loglist = new ArrayList<>(100);
		loglist.add("01\n");
		loglist.add("02\n");
		loglist.add("03\n");
		loglist.add("04\n");
		loglist.remove(1);
		loglist.add("05\n");
		System.out.println(loglist.size());
		System.out.println(StringUtils.join(loglist.toArray()));
	}
	// FIXME: 2017/9/28 zyd add for test arraylist -e
}

