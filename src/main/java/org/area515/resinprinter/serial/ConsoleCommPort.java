package org.area515.resinprinter.serial;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.printer.ComPortSettings;

public class ConsoleCommPort implements SerialCommunicationsPort {
    private static final Logger logger = LogManager.getLogger();
	public static final String GCODE_RESPONSE_SIMULATION = "GCode response simulation";
	private static int consoleNumber = 0;
	
	private String name = GCODE_RESPONSE_SIMULATION;
	private int readCount;
	private int timeout;
	// FIXME: 2017/9/1 zyd add for get the specified response by the gcode -s
	private String lastWrite = "";
	private String lastRead = "";
	// FIXME: 2017/9/1 zyd add for get the specified response by the gcode -e
	
	public ConsoleCommPort() {
		//this.name = this.name + ":" + consoleNumber++;
	}
	
	@Override
	public void open(String printerName, int timeout, ComPortSettings settings) {
		readCount = 0;
		this.timeout = timeout;
		logger.info("Printer opened");
	}

	@Override
	public void close() {
		logger.info("Printer closed");
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void write(byte[] gcode) {
		logger.info("Printer received:{}", new String(gcode));
		// FIXME: 2017/9/1 zyd add for get the specified response by the gcode -s
		setLastWrite(new String(gcode));
		setLastRead("");
		// FIXME: 2017/9/1 zyd add for get the specified response by the gcode -e
	}

	@Override
	public byte[] read() {
		switch (readCount) {
		case 0:
			readCount = 1;
			return "Console chitchat\n".getBytes();
		case 1:
			try {
				Thread.sleep(timeout+1);
			} catch (InterruptedException e) {}
			readCount = 2;
			return null;
		}

		// FIXME: 2017/9/1 zyd add for get the specified response by the gcode -s
		if (getLastRead().equals(""))
			setLastRead("ok\n");
		else
		{
			if (getLastWrite().startsWith("M98"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("P123\n");
				else
					setLastRead("cmd_comp\n");
			}
			else if (getLastWrite().startsWith("M99"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("Unknown command: M99\n");
			}
			else if (getLastWrite().startsWith("M268"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("UVLED_PWM_VAL:199\n");
				else
					setLastRead("cmd_comp\n");
			}
			else if (getLastWrite().startsWith("M270"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("Weight:1.1\n");
				else
					setLastRead("cmd_comp\n");
			}
			else if (getLastWrite().startsWith("M278"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("Door_Limit_State:H\n");
				else
					setLastRead("cmd_comp\n");
			}
			else if (getLastWrite().startsWith("M273"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("Bottle_Resin_Type:123\n");
				else
					setLastRead("cmd_comp\n");
			}
			else if (getLastWrite().startsWith("M274"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("Trough_Resin_Type:123\n");
				else
					setLastRead("cmd_comp\n");
			}
			else if (getLastWrite().startsWith("M276"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("Liquid_Level:H\n");
				else
					setLastRead("cmd_comp\n");
			}
			else if (getLastWrite().startsWith("M279"))
			{
				if (getLastRead().equals("ok\n"))
					setLastRead("Temperature_Alarm_State:L\n");
				else
					setLastRead("cmd_comp\n");
			}
			else
			{
				setLastRead("cmd_comp\n");
			}
		}
		return getLastRead().getBytes();

		// FIXME: 2017/9/1 zyd add for get the specified response by the gcode -e
		//return "ok\n".getBytes();
	}

	@Override
	public byte[] readBytes(int size) {
		return null;
	}

	// FIXME: 2017/9/1 zyd add for get the specified response by the gcode -s
	private void setLastWrite(String lastWrite)
	{
		this.lastWrite = lastWrite;
	}

	private String getLastWrite()
	{
		return this.lastWrite;
	}

	private void setLastRead(String lastRead)
	{
		this.lastRead = lastRead;
	}

	private String getLastRead()
	{
		return this.lastRead;
	}
	// FIXME: 2017/9/1 zyd add for get the specified response by the gcode -e

	@Override
	public void restartCommunications() {
	}

	public String toString() {
		return name;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConsoleCommPort other = (ConsoleCommPort) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}
