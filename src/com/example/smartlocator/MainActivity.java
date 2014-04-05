package com.example.smartlocator;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
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
    private Marker marker;
    private LocationManager locationManager;
    private CameraUpdate cameraUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        locationManager = (LocationManager)getSystemService(Service.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5,0,this);
    }

    private class LocationRefresher implements Runnable {
        Location location;

        public LocationRefresher(Location location_) {
            location = location_;
        }

        @Override
        public void run() {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            cameraUpdate = CameraUpdateFactory.newLatLngZoom(position, 15);
            map.animateCamera(cameraUpdate);

            placeMarker(position);
        }

        public void placeMarker(LatLng position){
            MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title(position.latitude + "," + position.longitude);
            map.clear();
            marker = map.addMarker(markerOptions);
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
