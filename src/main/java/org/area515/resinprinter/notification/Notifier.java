package org.area515.resinprinter.notification;

import java.io.File;
import java.net.URI;
import java.util.List;

import javax.websocket.server.ServerContainer;

import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.slice.StlError;

public interface Notifier {
	public enum NotificationEvent {
		PrinterChanged,
		PrintJobChanged,
		GeometryError,
		FileUploadComplete,
		SettingsChanged,
		Ping,
		// FIXME: 2017/9/27 zyd add for gcode log -s
		SendMessage,
		// FIXME: 2017/9/27 zyd add for gcode log -e
		RemoteMessage
	}
	
	//Management methods for Notifier
	public void register(URI uri, ServerContainer container) throws InappropriateDeviceException;
	public void stop();
	
	//Events 
	public void jobChanged(Printer printer, PrintJob job);
	public void printerChanged(Printer printer);
	public void fileUploadComplete(File fileUploaded);
	public void geometryError(PrintJob job, List<StlError> error);
	public void hostSettingsChanged();
	public void sendPingMessage(String message);
	public Long getTimeOfLastClientPing();
	public void remoteMessageReceived(String message);
	// FIXME: 2017/9/27 zyd add for gcode log -s
	public void sendMessage(String message);
	// FIXME: 2017/9/27 zyd add for gcode log -e
}
