package org.area515.resinprinter.notification;

import java.io.File;
import java.net.URI;
import java.util.List;

import javax.websocket.server.ServerContainer;

import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.slice.StlError;

public class UartScreenHostNotifier implements Notifier {

	@Override
	public void register(URI uri, ServerContainer container) throws InappropriateDeviceException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void jobChanged(Printer printer, PrintJob job) {
		// TODO Auto-generated method stub
//		System.out.println("totle:"+ Integer.toString(job.getTotalSlices()));
//		System.out.println(" current:"+ Integer.toString(job.getCurrentSlice()));
		if (printer.getUartScreenControl() != null)
		{
			System.out.print("uartscreen job changed\n");
			printer.getUartScreenControl().notifyState(printer, job);
		}
	}

	@Override
	public void printerChanged(Printer printer) {
		// TODO Auto-generated method stub
		if (printer.getUartScreenControl() != null)
		{
			System.out.print("zyd uartscreen printer changed\n");
			printer.getUartScreenControl().notifyState(printer, null);
		}
	}

	@Override
	public void fileUploadComplete(File fileUploaded) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void geometryError(PrintJob job, List<StlError> error) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void hostSettingsChanged() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendPingMessage(String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Long getTimeOfLastClientPing() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void remoteMessageReceived(String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendMessage(String message) {
		// TODO Auto-generated method stub

	}
}
