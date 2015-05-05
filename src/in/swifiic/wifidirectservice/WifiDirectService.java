package in.swifiic.wifidirectservice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
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
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class WifiDirectService extends Service implements ChannelListener, PeerListListener, ConnectionInfoListener {
	
	public static final String TAG = "WifiP2pService";
    private WifiP2pManager manager;
	private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;
    public boolean isConnected = false;
    public boolean isRecieved = false;
    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
	private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
	private boolean isGroupOwner = false;
	private boolean isGroupCreated = false;
	
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
        
        registerReceiver(broadcastReceiver, new IntentFilter("DISCOVER_PEERS_ACTION"));

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        super.onCreate();
        throttle();
	}
	
	BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        isRecieved = true;
	        if(intent.getAction().equals("DISCOVER_PEERS_ACTION"))
	        {
	        	Toast.makeText(context, "alarm called", Toast.LENGTH_LONG).show();
	        	throttle();
	        }
	    }
	};
	@Override
	public void onDestroy() {
		disconnect();
		Toast.makeText(this, "MyService Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		unregisterReceiver(receiver);
		unregisterReceiver(broadcastReceiver);
		super.onDestroy();
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Toast.makeText(this, "My Service Started", Toast.LENGTH_SHORT).show();
		Log.d(TAG, "onStart");	
		receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
		registerReceiver(receiver, intentFilter);
		return super.onStartCommand(intent, flags, startId);
	}
	
	private synchronized void findPeers() //runs discover peers which causes it to send an intent to the broadcastreciever
	{
		//Toast.makeText(this, "Finding Peers", Toast.LENGTH_SHORT).show();
		manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                //Toast.makeText(WifiDirectService.this, "stop Discovery Initiated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(WifiDirectService.this, "stop Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                //Toast.makeText(WifiDirectService.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(WifiDirectService.this, "Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
                WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE); 
                wm.setWifiEnabled(false);
                wm.setWifiEnabled(true);
            }
        });
	}
	private synchronized void throttle() //TODO
	{
		//
		Intent intent = new Intent(this, MyAlarmManager.class);
		long scTime = (60*1/2)*1000;//1/2mins
		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
		AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + scTime, pendingIntent);
		findPeers();	//calling findPeers() after 2 min	
	}
	
	private synchronized void connect(WifiP2pConfig config) { // connects to the specified device config
    	Toast.makeText(WifiDirectService.this, "Connecting to " + config.deviceAddress, Toast.LENGTH_SHORT).show();
        manager.connect(channel, config, new ActionListener() {
            @Override
            public void onSuccess() {
            	Toast.makeText(WifiDirectService.this, "Connect done.",
                        Toast.LENGTH_SHORT).show();
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(WifiDirectService.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
	
    private synchronized void disconnect() { // disconnects all connections
        manager.removeGroup(channel, new ActionListener() 
        {
            @Override
            public void onFailure(int reasonCode) 
            {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
            }
            @Override
            public void onSuccess() 
            {
            	//TODO
            }
        });
    }
    @Override
	public synchronized void onPeersAvailable(final WifiP2pDeviceList peerList) 
    { 
    	// runs whenever wifidirectbroadcastreciever recieves a  WIFI_P2P_PEERS_CHANGED_ACTION intent
		Toast.makeText(WifiDirectService.this, "Checking Peer List", Toast.LENGTH_SHORT).show();
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        if (peers.size() == 0) 
        {
            Log.d(WifiDirectService.TAG, "No devices found");
            //Toast.makeText(this, "No Peers found", Toast.LENGTH_SHORT).show();
            findPeers();
            throttle();
            return;
        }
        else
        {
        	//Toast.makeText(this, "Peers found " + peers.size(), Toast.LENGTH_SHORT).show();
        	//case 1: I am the group owner then send a request to everyone
    	    if(isGroupOwner == true && isGroupCreated == true)
    	    {
    	    	//I need to send request to every peer
    	    	//Toast.makeText(this, "Case 1", Toast.LENGTH_SHORT).show();
    	    	Log.d(WifiDirectService.TAG, "Case 1");
    	    	for (Iterator<WifiP2pDevice> iter = peers.iterator(); iter.hasNext(); )
    	    	{
    	    		WifiP2pDevice element = iter.next();
    	    		if(element.status == WifiP2pDevice.AVAILABLE)
    	    		{
    	    			WifiP2pConfig config = new WifiP2pConfig();
    	                config.deviceAddress = element.deviceAddress;
    	                config.wps.setup = WpsInfo.PBC;
    	                connect(config);
    	    		}
    	    		else; //need to handle other statuses?
    	    	}
    	    }
    	    //case 2: if some other device is a group owner then send wait for a request
    	    if(isGroupOwner == false && isGroupCreated == false)
    	    {
    	    	//this means I am not connected to any other device so connect to the
    	    	//group owner if existing
    	    	//Toast.makeText(this, "Case 2", Toast.LENGTH_SHORT).show();
    	    	boolean isGroupOwnerFound = false;
    	    	Log.d(WifiDirectService.TAG, "Case 2");
    	    	for (Iterator<WifiP2pDevice> iter = peers.iterator(); iter.hasNext(); ) 
    	    	{
            	    WifiP2pDevice element = iter.next();            	    
            	    if(element.isGroupOwner())
            	    {
    	                isGroupOwnerFound = true;
    	                break;
            	    }
            	}
    	    	//case 3: race condition, no group has been created
    	    	//and requires a group to be created. so wait a random time and connect to the other peer 
    	    	if(isGroupOwnerFound == false)
    	    	{
    	    		Log.d(WifiDirectService.TAG, "Case 3");
    	    		//Toast.makeText(this, "Possible Race Condition", Toast.LENGTH_SHORT).show();
    	    		Random r = new Random();
    	    		int randomInterval = r.nextInt(25000 - 1000) + 1000;//1000ms to 25000ms
    	    		final Handler handler = new Handler();
    	    		Toast.makeText(this, "waiting for " + randomInterval, Toast.LENGTH_SHORT).show();
    	    		handler.postDelayed(new Runnable() 
    	    		{
    	    		  @Override
    	    		  public void run() 
    	    		  {
    	    			  peers.clear();
    	    		      peers.addAll(peerList.getDeviceList());
    	    			  //Toast.makeText(context, "in run", Toast.LENGTH_SHORT).show();
    	    			  //TODO confusing part... does the status of the device gets refreshed after connecting?
    	    			  if(!peers.isEmpty() && (peers.get(0).status == WifiP2pDevice.CONNECTED || peers.get(0).status == WifiP2pDevice.INVITED))
    	    			  {
    	    				  //already connected do nothing
    	    			  }
    	    			  else if(!peers.isEmpty())
    	    			  {
    	    				  Log.d(WifiDirectService.TAG, "in run connect");
    	    				  //not connected need to connect
    	    				  WifiP2pConfig config = new WifiP2pConfig();
    	    	              config.deviceAddress = peers.get(0).deviceAddress;
    	    	              config.wps.setup = WpsInfo.PBC;
    	    	              connect(config);
    	    			  }
    	    		  }
    	    		}, randomInterval);
    	    	}
    	    }
    	    
        	
        }
    }

	@Override
    public void onChannelDisconnected() 
	{
        // we will try once more
        if (manager != null && !retryChannel)
        {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        }
        else 
        {
            Toast.makeText(this, "Severe! Channel is probably lost permanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

	@Override
	public void onConnectionInfoAvailable(WifiP2pInfo info) 
	{
		isGroupOwner = info.isGroupOwner;
		isGroupCreated =  info.groupFormed;
		//TODO need to refresh this after every connection
	}
}
