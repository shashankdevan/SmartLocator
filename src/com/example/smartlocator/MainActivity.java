package com.example.smartlocator;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.util.Log;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;
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

    public static final String WARN = "WARNING";

    public static final float ACCL_THRESHOLD = 2.5f;
    public static final int MARKER_UPDATE_INTERVAL = 2000;
    public static final int GPS_UPDATE_INTERVAL = 60000;
    public static final int DEFAULT_ZOOM_LEVEL = 18;

    final private double STEP_SIZE = 0.419;
    final private double METER_PER_LAT_DEGREE = 78095.9773719797;
    final private double METER_PER_LNG_DEGREE = 90163.65604055098;
    final private int MAGNETIC_DECLINATION = 0;

    private GoogleMap map;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private SensorListener sensorListener = new SensorListener();

    private Handler locationUpdateHandler = new Handler();
    private boolean isFirstUpdate = true;
    private long currentPeakTime;
    private long lastKnownPeakTime;
    private float acclMagnitude = 0;

    private boolean acclDirection = true;

    private int stepCount = 0;
    public static final int MIN_STEP_TIME = 200;
    private float[] acclReadings = new float[3];

    private float[] filteredAccl = new float[3];
    private boolean readAccl = false;
    private float[] magnReadings = new float[3];

    private float[] filteredMagn = new float[3];
    private boolean readMagn = false;
    private long lastMarkerUpdateTime;
    private double lastLat, lastLng;

    private CameraPosition cameraPosition = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);

        sensorManager = (SensorManager) getSystemService(Service.SENSOR_SERVICE);
        currentPeakTime = System.currentTimeMillis();
    }


    private void updateBestKnownLocation() {
        showEnableGpsDialog();
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (gpsLocation != null && networkLocation != null) {
                if (gpsLocation.getTime() > networkLocation.getTime())
                    updateLocationOnGpsDisabled(gpsLocation);
                else
                    updateLocationOnGpsDisabled(networkLocation);
            } else if (gpsLocation != null) {
                updateLocationOnGpsDisabled(gpsLocation);
            } else if (networkLocation != null) {
                updateLocationOnGpsDisabled(networkLocation);
            }
        }
    }

    private void showEnableGpsDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("GPS Settings");
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");

	    alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog,int which) {
	            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
	            startActivity(intent);
	        }
	    });

        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        alertDialog.show();
    }

    private void updateLocationOnGpsDisabled(Location location) {
        lastLat = location.getLatitude();
        lastLng = location.getLongitude();
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLat, lastLng), DEFAULT_ZOOM_LEVEL));
    }

    @Override
    protected void onResume() {
        super.onResume();

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);

        if (locationManager.isProviderEnabled(locationManager.GPS_PROVIDER))
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_UPDATE_INTERVAL, 0, this);
        else
            updateBestKnownLocation();

        if (cameraPosition != null) {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            cameraPosition = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorListener);
        locationManager.removeUpdates(this);
        cameraPosition = map.getCameraPosition();
    }

    public class SensorListener implements SensorEventListener {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    lowPassAccl(event);
                    acclReadings = filteredAccl.clone();
                    readAccl = true;
                    detectStep(getMagnitude(event));
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    lowPassMagn(event);
                    magnReadings = filteredMagn.clone();
                    readMagn = true;
                    break;
                default:
                    Log.d(WARN, "Invalid Sensor Received");
            }

            SensorLocationRefresher task = new SensorLocationRefresher();
            locationUpdateHandler.post(task);
        }

        private void detectStep(float currentAcclMagnitude) {
            if (acclDirection) {
                if (currentAcclMagnitude > acclMagnitude) {
                    acclMagnitude = currentAcclMagnitude;
                    currentPeakTime = System.currentTimeMillis();
                } else if ((acclMagnitude - currentAcclMagnitude) > ACCL_THRESHOLD) {
                    if ((currentPeakTime - lastKnownPeakTime) > MIN_STEP_TIME)
                        stepCount += 1;
                    lastKnownPeakTime = currentPeakTime;
                    acclMagnitude = currentAcclMagnitude;
                    acclDirection = false;
                }
            } else {
                if (currentAcclMagnitude < acclMagnitude) {
                    acclMagnitude = currentAcclMagnitude;
                } else if ((currentAcclMagnitude - acclMagnitude) > ACCL_THRESHOLD) {
                    acclDirection = true;
                }
            }

            Log.d("STEP", String.valueOf(stepCount));
        }

        private float getMagnitude(SensorEvent event) {
            return (float) Math.sqrt(Math.pow(event.values[0] - filteredAccl[0], 2)
                    + Math.pow(event.values[1] - filteredAccl[1], 2)
                    + Math.pow(event.values[2] - filteredAccl[2], 2));
        }

        private void lowPassAccl(SensorEvent event) {
            final float alpha = (float) 0.8;

            for (int i = 0; i < event.values.length; i++)
                filteredAccl[i] = alpha * filteredAccl[i] + (1 - alpha) * event.values[i];
        }

        private void lowPassMagn(SensorEvent event) {
            final float alpha = (float) 0.8;

            for (int i = 0; i < event.values.length; i++)
                filteredMagn[i] = alpha * filteredMagn[i] + (1 - alpha) * event.values[i];
        }

    }

    @Override
    public void onLocationChanged(Location location) {
        GpsLocationRefresher task = new GpsLocationRefresher(location);
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

    private class SensorLocationRefresher implements Runnable {

        @Override
        public void run() {
            if (readAccl && readMagn && (System.currentTimeMillis() - lastMarkerUpdateTime) > MARKER_UPDATE_INTERVAL) {
                readAccl = readMagn = false;

                float[] R = new float[9];
                float[] I = new float[9];
                float[] orientation = new float[3];

                SensorManager.getRotationMatrix(R, I, acclReadings, magnReadings);
                SensorManager.getOrientation(R, orientation);

                updateLocation(orientation[0] + Math.toRadians(MAGNETIC_DECLINATION));
                lastMarkerUpdateTime = System.currentTimeMillis();
            }
        }

        private void updateLocation(double azimuthalAngle) {
            Log.d("ANGLE", Double.valueOf(Math.toDegrees(azimuthalAngle)).toString());
            Log.d("METERS", String.valueOf((stepCount * STEP_SIZE) * Math.sin(azimuthalAngle)));

            lastLat += (stepCount * STEP_SIZE) * Math.cos(azimuthalAngle) / METER_PER_LAT_DEGREE;
            lastLng += (stepCount * STEP_SIZE) * Math.sin(azimuthalAngle) / METER_PER_LNG_DEGREE;

            stepCount = 0;

            placeMarker(new LatLng(lastLat, lastLng));
        }

        public void placeMarker(LatLng position) {
//            map.moveCamera(CameraUpdateFactory.newLatLng(position));
            map.clear();
            map.addMarker(new MarkerOptions()
                    .position(position)
                    .title(position.latitude + ", " + position.longitude));
        }

    }

    private class GpsLocationRefresher implements Runnable {

        Location location;

        public GpsLocationRefresher(Location location_) {
            location = location_;
        }

        @Override
        public void run() {
            lastLat = location.getLatitude();
            lastLng = location.getLongitude();
            if (isFirstUpdate) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLat, lastLng), DEFAULT_ZOOM_LEVEL));
                isFirstUpdate = false;
            }
        }

    }

}
    