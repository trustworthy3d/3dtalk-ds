package org.area515.resinprinter.network;

import java.util.List;

public interface NetworkManager {
	public List<NetInterface> getNetworkInterfaces();
	public void connectToWirelessNetwork(WirelessNetwork net);
	// FIXME: 2017/9/1  zyd add for get ip address -s
	public String getIpAddress(String nic);
	// FIXME: 2017/9/1  zyd add for get ip address -e
}
