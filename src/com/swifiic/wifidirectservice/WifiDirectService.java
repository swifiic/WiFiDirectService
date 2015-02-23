package com.swifiic.wifidirectservice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class WifiDirectService extends Service implements ChannelListener, PeerListListener, ConnectionInfoListener {
	
	public static final String TAG = "WifiP2pService";
    private WifiP2pManager manager;
    @SuppressWarnings("unused")
	private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;
    public boolean isConnected = false;
    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
	private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
	public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		throw new UnsupportedOperationException("Not yet implemented");
	}
	@Override
	public void onCreate() {

		//Toast.makeText(this, "Congrats! MyService Created", Toast.LENGTH_SHORT).show();
		Log.d(TAG, "onCreate");
		
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        //manager.
        channel = manager.initialize(this, getMainLooper(), null);
        super.onCreate();
	}
	
	
	@Override
	public void onDestroy() {
		disconnect();
		Toast.makeText(this, "MyService Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		super.onDestroy();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Toast.makeText(this, "My Service Started", Toast.LENGTH_SHORT).show();
		Log.d(TAG, "onStart");	
		receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
		registerReceiver(receiver, intentFilter);
		findPeers();
		return super.onStartCommand(intent, flags, startId);
	}
	
	public void findPeers()
	{
		//Toast.makeText(this, "Finding Peers", Toast.LENGTH_SHORT).show();
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(WifiDirectService.this, "Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(WifiDirectService.this, "Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
	}
	
	public void connect(WifiP2pConfig config) {
    	Toast.makeText(WifiDirectService.this, "Connecting...", Toast.LENGTH_SHORT).show();
        manager.connect(channel, config, new ActionListener() {
        	
            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(WifiDirectService.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
	
    public void disconnect() {
        manager.removeGroup(channel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
            	//TODO
            }

        });
    }
	
    @Override
	public void onPeersAvailable(WifiP2pDeviceList peerList) {
		Toast.makeText(WifiDirectService.this, "Checking Peer List", Toast.LENGTH_SHORT).show();
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        if (peers.size() == 0) {
            Log.d(WifiDirectService.TAG, "No devices found");
            Toast.makeText(this, "No Peers found", Toast.LENGTH_SHORT).show();
            findPeers();
            //pService.isConnected = false;
            return;
        }
        
        //else if(isConnected == false){
        else{
        	//WifiP2pDevice deviceTemp = peers.get(0);
        	for (Iterator<WifiP2pDevice> iter = peers.iterator(); iter.hasNext(); ) {
        	    WifiP2pDevice element = iter.next();
        	    
        	    if(element.status == WifiP2pDevice.AVAILABLE || element.status == WifiP2pDevice.FAILED || element.status == WifiP2pDevice.INVITED)
        	    {
	        	    WifiP2pConfig config = new WifiP2pConfig();
	                config.deviceAddress = element.deviceAddress;
	                config.wps.setup = WpsInfo.KEYPAD;
	                config.wps.pin = "00000000";
	                connect(config);
        	    }
        	}
        	//WifiP2pConfig config = new WifiP2pConfig();
            //config.deviceAddress = deviceTemp.deviceAddress;
            //config.wps.setup = WpsInfo.KEYPAD;
            //config.wps.pin = "00000000";
            //connect(config);
            //isConnected = true;
        }
    }

	@Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            //resetData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost permanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

	@Override
	public void onConnectionInfoAvailable(WifiP2pInfo info) {
		// TODO Auto-generated method stub
		
	}
}
