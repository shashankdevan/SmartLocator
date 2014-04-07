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

    public static final String WARN = "WARNING";

    public static final float ACCL_THRESHOLD = 2.5f;
    public static final int MARKER_UPDATE_INTERVAL = 2000;
    public static final int GPS_UPDATE_INTERVAL = 60000;
    public static final int DEFAULT_ZOOM_LEVEl = 18;

    final private double STEP_SIZE = 0.419;
    final private double METER_PER_LAT_DEGREE = 78095.9773719797;
    final private double METER_PER_LNG_DEGREE = 90163.65604055098;
    final private int MAGNETIC_DECLINATION = 0;

    private Handler locationUpdateHandler = new Handler();

    private GoogleMap map;

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

    private long lastLocationUpdateTime;
    private long lastMarkerUpdateTime;
    private double lastLat, lastLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        LocationManager locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5, 0, this);

        SensorManager sensorManager = (SensorManager) getSystemService(Service.SENSOR_SERVICE);

        SensorListener sensorListener = new SensorListener();

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);

        currentPeakTime = System.currentTimeMillis();
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

            if (lastLocationUpdateTime == 0)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLat, lastLng), DEFAULT_ZOOM_LEVEl));
            else if ((System.currentTimeMillis() - lastLocationUpdateTime) > GPS_UPDATE_INTERVAL)
                lastLocationUpdateTime = System.currentTimeMillis();
        }

    }

}
