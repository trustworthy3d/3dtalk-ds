package org.area515.resinprinter.notification;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;

import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.slice.StlError;
import org.area515.util.JacksonEncoder;

/**
 * Created by zyd on 2017/9/27.
 */

@ServerEndpoint(value="/gCodeLogNotification", encoders={JacksonEncoder.class})
public class WebSocketGCodeLogNotifier implements Notifier
{
    private static ConcurrentHashMap<String, Session> sessionsBySessionId = new ConcurrentHashMap<String, Session>();
	private Long lastClientPing;

    @OnOpen
    public void onOpen(Session session) {
        sessionsBySessionId.putIfAbsent(session.getId(), session);
    }

    @OnClose
    public void onClose(Session session) {
        sessionsBySessionId.remove(session.getId());
    }

    @OnError
    public void onError(Session session, Throwable cause) {
        sessionsBySessionId.remove(session.getId());
    }

	@OnMessage
	public void onPingMessage(String message, Session session) {
		lastClientPing = System.currentTimeMillis();
	}

	@Override
	public void register(URI uri, ServerContainer container) throws InappropriateDeviceException {
		// TODO Auto-generated method stub
		try {
			container.addEndpoint(WebSocketGCodeLogNotifier.class);
		} catch (DeploymentException e) {
			throw new InappropriateDeviceException("Couldn't deploy", e);
		}

	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void jobChanged(Printer printer, PrintJob job) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void printerChanged(Printer printer) {
		// TODO Auto-generated method stub
		
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
		return lastClientPing;
	}

	@Override
	public void remoteMessageReceived(String message) {
		// TODO Auto-generated method stub
		
	}

    @Override
    public void sendMessage(String message) {
        // TODO Auto-generated method stub
        for (Session currentSession : sessionsBySessionId.values()) {
            currentSession.getAsyncRemote().sendObject(new HostEvent(message, NotificationEvent.SendMessage));
        }
    }
}
