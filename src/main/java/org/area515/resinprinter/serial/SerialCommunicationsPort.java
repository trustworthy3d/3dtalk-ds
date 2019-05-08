package org.area515.resinprinter.serial;

import java.io.IOException;

import org.area515.resinprinter.display.AlreadyAssignedException;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.printer.ComPortSettings;

public interface SerialCommunicationsPort {
	public void open(String controllingDevice, int timeout, ComPortSettings settings) throws AlreadyAssignedException, InappropriateDeviceException;
	public void close();
	public void setName(String name);
	public String getName();
	public void write(byte[] data) throws IOException;
	public byte[] read() throws IOException;
	// FIXME: 2018/7/23 zyd add for firmware install -s
	public byte[] readBytes(int size) throws IOException;
	// FIXME: 2018/7/23 zyd add for firmware install -e
	public void restartCommunications() throws AlreadyAssignedException, InappropriateDeviceException;
}
