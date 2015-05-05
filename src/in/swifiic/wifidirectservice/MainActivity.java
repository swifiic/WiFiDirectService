package in.swifiic.wifidirectservice;

import com.example.bootstart.R;

import android.os.Bundle;
import android.view.View;
import android.app.Activity;
import android.content.Intent;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

	}
	//start the service
		public void onClickStartService(View V)
		{
			//start the service from here //MyService is your service class name
			startService(new Intent(this, WifiDirectService.class));
		}
		//Stop the started service
		public void onClickStopService(View V)
		{
			//Stop the running service from here//MyService is your service class name
			//Service will only stop if it is already running.
			stopService(new Intent(this, WifiDirectService.class));
		}
	
}
