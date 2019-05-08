package org.area515.resinprinter.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MockNetworkManager implements NetworkManager {
	@Override
	public List<NetInterface> getNetworkInterfaces() {
		WirelessEncryption encryption = new WirelessEncryption();
		encryption.setEncryptionClass(EncryptionClass.WPA2);
		encryption.getGroupCipher().add(WirelessCipher.TKIP);
		encryption.getPairwiseCipher().add(WirelessCipher.TKIP);
		
		List<WirelessEncryption> encryptions = new ArrayList<WirelessEncryption>();
		encryptions.add(encryption);
		
		WirelessNetwork wNetwork0 = new WirelessNetwork();
		WirelessNetwork wNetwork1 = new WirelessNetwork();
		WirelessNetwork wNetwork2 = new WirelessNetwork();
		wNetwork0.setSupportedWirelessEncryption(encryptions);
		wNetwork0.setSsid("Test Wireless Network");
		wNetwork1.setSupportedWirelessEncryption(encryptions);
		wNetwork1.setSsid("Test Another Wireless Network");
		wNetwork2.setSupportedWirelessEncryption(encryptions);
		wNetwork2.setSsid("Test Final Wireless Network");
		
		List<WirelessNetwork> wirelessNetworks0 = new ArrayList<WirelessNetwork>();
		List<WirelessNetwork> wirelessNetworks1 = new ArrayList<WirelessNetwork>();
		List<WirelessNetwork> wirelessNetworks2 = new ArrayList<WirelessNetwork>();
		wirelessNetworks0.add(wNetwork0);
		wirelessNetworks1.add(wNetwork1);
		wirelessNetworks2.add(wNetwork2);
		
		NetInterface netInterface0 = new NetInterface();		
		NetInterface netInterface1 = new NetInterface();		
		NetInterface netInterface2 = new NetInterface();		
		netInterface0.setName("wlan0");
		netInterface1.setName("wlan0");
		netInterface2.setName("wlan1");
		wNetwork0.setParentInterfaceName(netInterface0.getName());
		wNetwork1.setParentInterfaceName(netInterface1.getName());
		wNetwork2.setParentInterfaceName(netInterface2.getName());

		netInterface0.setWirelessNetworks(wirelessNetworks0);
		netInterface1.setWirelessNetworks(wirelessNetworks1);
		netInterface2.setWirelessNetworks(wirelessNetworks2);
		
		ArrayList<NetInterface> iFaces = new ArrayList<NetInterface>();
		iFaces.add(netInterface0);
		iFaces.add(netInterface1);
		iFaces.add(netInterface2);
		return iFaces;
	}

	@Override
	public void connectToWirelessNetwork(WirelessNetwork net) {
		//Pretty much do nothing...
		//throw new IllegalArgumentException("There was a problem connecting to the network");
	}

	@Override
	public String getIpAddress(String nic)
	{
		return null;
	}
}
