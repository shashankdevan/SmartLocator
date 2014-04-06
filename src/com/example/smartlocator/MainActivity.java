package com.example.smartlocator;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import com.google.android.gms.maps.CameraUpdateFactory;
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

    public static final int THRESHOLD = 3;
    private Handler locationUpdateHandler = new Handler();
    private GoogleMap map;
    private float[] gravity = {0, 0, 0};
    private static final String TAG = "ACCELEROMETER";

    private long currentPeakTime;
    private float currentPeak = 0;
    private long lastKnownPeakTime;
    private float lastKnownPeak = 0;
    private float acclMagnitude = 0;
    private boolean acclDirection = true;
    private int stepCount = 0;

    private float totalDistance = 0;
    private long lastRecordedTime;

    private float totalAccl = 0;
    private int readingCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

//        LocationManager locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
//        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5, 0, this);

        currentPeakTime = System.currentTimeMillis();
        lastRecordedTime = System.currentTimeMillis();
        AccelerometerListener accelerometerListener = new AccelerometerListener();
        SensorManager sensorManager = (SensorManager) getSystemService(Service.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(accelerometerListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private class AccelerometerListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            isolateGravity(event);
            countDistance(getMagnitude(event));

//            countDistance(getMagnitude(event));
//            Log.d(TAG, " Axis: " + Float.valueOf(event.values[0]).toString() + "  " + Float.valueOf(event.values[1]).toString() + "  " + Float.valueOf(event.values[2]).toString());
//            Log.d(TAG, "Total: " + Float.valueOf(getMagnitude(event)).toString());
//            detectStep(getMagnitude(event));
        }

        private void countDistance(float acceleration) {
            float accl;
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastRecordedTime) > 3000) {
                accl = totalAccl / readingCount;
                totalDistance += accl * Math.pow((currentTime - lastRecordedTime), 2) / 1000000;
                Log.d(TAG, "Distance: " + totalDistance);
                lastRecordedTime = System.currentTimeMillis();
                totalAccl = readingCount = 0;
            } else {
                totalAccl += acceleration;
                readingCount += 1;
            }
        }

        private void detectStep(float currentAcclMagnitude) {
            if (acclDirection) {
                if (currentAcclMagnitude > acclMagnitude) {
                    currentPeak = acclMagnitude = currentAcclMagnitude;
                    currentPeakTime = System.currentTimeMillis();
                } else if ((acclMagnitude - currentAcclMagnitude) > THRESHOLD) {
                    if ((currentPeakTime - lastKnownPeakTime) > 200) {
                        stepCount += 1;
                    }
                    lastKnownPeak = currentPeak;
                    lastKnownPeakTime = currentPeakTime;
                    acclMagnitude = currentAcclMagnitude;
                    acclDirection = false;
                }
            } else {
                if (currentAcclMagnitude < acclMagnitude) {
                    acclMagnitude = currentAcclMagnitude;
                } else if ((currentAcclMagnitude - acclMagnitude) > THRESHOLD) {
                    acclDirection = true;
                }
            }

            Log.d(TAG, "StepCount: " + stepCount);
        }

        private float getMagnitude(SensorEvent event) {
            return (float) Math.sqrt(Math.pow(event.values[0] - gravity[0], 2)
                    + Math.pow(event.values[1] - gravity[1], 2)
                    + Math.pow(event.values[2] - gravity[2], 2));
        }

        private void isolateGravity(SensorEvent event) {
            final float alpha = (float) 0.8;

            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

    private class LocationRefresher implements Runnable {
        Location location;

        public LocationRefresher(Location location_) {
            location = location_;
        }

        @Override
        public void run() {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15));
            placeMarker(position);
        }

        public void placeMarker(LatLng position) {
            map.clear();
            map.addMarker(new MarkerOptions()
                    .position(position)
                    .title(position.latitude + "," + position.longitude));
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
