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
    final private double STEP_SIZE = 0.419;
    final private double METER_PER_LAT_DEGREE = 110958.9773719797;
    final private double METER_PER_LNG_DEGREE = 90163.65604055098;

    private float[] acclReadings = new float[3];
    private float[] magnReadings = new float[3];
    private boolean readAccl = false;
    private boolean readMagn = false;

    private double lastUpdateTime;
    private double lastLat, lastLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        LocationManager locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5, 0, this);

        SensorManager sensorManager = (SensorManager) getSystemService(Service.SENSOR_SERVICE);

        OrientationListener orientationListener = new OrientationListener();

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(orientationListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(orientationListener, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);

        currentPeakTime = System.currentTimeMillis();
    }

    public class OrientationListener implements SensorEventListener {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    acclReadings = event.values;
                    readAccl = true;
                    isolateGravity(event);
                    detectStep(getMagnitude(event));
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    magnReadings = event.values;
                    readMagn = true;
                    break;
                default:
                    Log.d("ORIENT", "Invalid Sensor Received");
            }
            long currentTime = System.currentTimeMillis();
            if (readAccl && readMagn && (currentTime - lastUpdateTime) > 1000) {
                readAccl = readMagn = false;

                float[] R = new float[9];
                float[] I = new float[9];
                float[] orientation = new float[3];
                double azimuthalAngle;

                SensorManager.getRotationMatrix(R, I, acclReadings, magnReadings);
                SensorManager.getOrientation(R, orientation);

                azimuthalAngle = orientation[0] * 57.29;
                updateLocation(azimuthalAngle);
                lastUpdateTime = currentTime;
            }
        }

        private void updateLocation(double azimuthalAngle) {
            double deltaLat = (stepCount * STEP_SIZE) * Math.cos(azimuthalAngle) / METER_PER_LAT_DEGREE;
            double deltaLng = (stepCount * STEP_SIZE) * Math.sin(azimuthalAngle) / METER_PER_LNG_DEGREE;

            lastLat += deltaLat;
            lastLng += deltaLng;
            System.out.println(lastLat + " " + lastLng);
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
        lastLat = location.getLatitude();
        lastLng = location.getLongitude();

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
