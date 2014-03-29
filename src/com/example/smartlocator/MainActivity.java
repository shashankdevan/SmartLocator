package com.example.smartlocator;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.Service;

public class MainActivity extends Activity implements LocationListener {

	private Handler locationUpdateHandler = new Handler();
	private GoogleMap map;
	private MarkerOptions marker;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

		/* register this class to Location Service for updates
		 */
		LocationManager location = (LocationManager)getSystemService(Service.LOCATION_SERVICE);
		location.requestLocationUpdates(LocationManager.GPS_PROVIDER,10,0,this);

	}

	private  class LocationRefresher implements Runnable {
		Location location;

		public LocationRefresher(Location location_) {
			location = location_;
		}

		@Override
		public void run() {
			marker = new MarkerOptions()
			.position(new LatLng(location.getLatitude(), location.getLongitude()))
			.title("My Location");

			map.addMarker(marker);
		}

	}

	@Override
	public void onLocationChanged(Location location) {
		LocationRefresher task = new LocationRefresher(location);
		locationUpdateHandler.post(task);
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub

	}


}
