package com.denis.ubiq.kalman;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.location.LocationListener;
import android.opengl.Matrix;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.util.*;

import com.denis.ubiq.MapActivity;
import com.denis.ubiq.items.*;
import com.denis.ubiq.listeners.*;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.model.LatLng;

import static android.content.Context.*;
import static android.hardware.Sensor.*;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.os.SystemClock.elapsedRealtimeNanos;
import static com.denis.ubiq.utils.CalculationUtils.*;
import static com.denis.ubiq.utils.Constants.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class KalmanFilterWorker implements SensorEventListener, LocationListener, Runnable {

    private static String TAG = "KalmanFilterWorker";
    private static int DELAY = 500;

    private final AtomicBoolean running = new AtomicBoolean( false );
    private final MapActivity mapActivity;
    private final SettingsClient settingsClient;
    private final LocationManager locationManager;
    private final SensorManager sensorManager;

    private KalmanFilterModel kalmanFilterModel;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;

    private float[] linearAcceleration = new float[4];
    private float[] rotationMatrix = new float[16];
    private float[] rotationMatrixInv = new float[16];
    private float[] acceleration = new float[4];
    private float magneticDeclination = 0.0F;

    private Queue<TimestampItem> sensorFusionItems = new PriorityQueue<>();

    public KalmanFilterWorker( MapActivity mapActivity, Location location ) {
        this( mapActivity );
        this.kalmanFilterModel = new KalmanFilterModel( location );
    }

    public KalmanFilterWorker( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
        this.settingsClient = LocationServices.getSettingsClient( mapActivity );
        this.locationManager = ( LocationManager ) mapActivity.getSystemService( LOCATION_SERVICE );
        this.sensorManager = ( SensorManager ) mapActivity.getSystemService( SENSOR_SERVICE );

        buildLocationSettings();
    }

    private void buildLocationSettings() {
        buildLocationRequest();
        buildLocationSettingsRequest();
    }

    private void buildLocationRequest() {
        this.locationRequest = new LocationRequest();
        locationRequest.setInterval( UPDATE_INTERVAL );
        locationRequest.setFastestInterval( FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS );
        locationRequest.setPriority( LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY );
    }

    private void buildLocationSettingsRequest() {
        locationSettingsRequest = new LocationSettingsRequest.Builder().addLocationRequest( locationRequest ).build();
    }

    public void begin() {
        settingsClient.checkLocationSettings( locationSettingsRequest )
                      .addOnSuccessListener( mapActivity, new LocationOnSuccessListener( this ) )
                      .addOnFailureListener( mapActivity, new LocationOnFailureListener( mapActivity ) );
    }

    public void start() {
        new Thread( this ).start();

        requestLocationUpdates();
        registerSensorListeners();
    }

    private void requestLocationUpdates() {
        locationManager.removeUpdates( this );

        if( ActivityCompat.checkSelfPermission( mapActivity, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission( mapActivity, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
            return;
        }
        locationManager.requestLocationUpdates( GPS_PROVIDER, UPDATE_INTERVAL, 0F, this );
    }

    private void registerSensorListeners() {
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_LINEAR_ACCELERATION ), hertz2periodUs( 10 ) );
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_ROTATION_VECTOR ), hertz2periodUs( 10 ) );
    }

    @Override
    public void run() {
        running.set( true );
        while( running.get() ) {
            try {
                Thread.sleep( DELAY );
            } catch( InterruptedException e ) {
                e.printStackTrace();
                continue;
            }

            TimestampItem item;
            while( ( item = sensorFusionItems.poll() ) != null ) {
                if( item instanceof SensorItem ) {
                    kalmanFilterModel.updateProcessModel( item.timestamp );

                    double[] predictedPosition = kalmanFilterModel.predict( ( ( SensorItem ) item ) );

                    LatLng position = metersToLatLng( predictedPosition[0], predictedPosition[1] );
                    Log.i( TAG,
                           String.format( "%s, %d, KalmanPredict: PositionX=%s PositionY=%s; VelocityX=%s VelocityY=%s",
                                          getCurrentTime(),
                                          item.timestamp,
                                          position.latitude,
                                          position.longitude,
                                          predictedPosition[2],
                                          predictedPosition[3] ) );
                } else {
                    kalmanFilterModel.updateMeasurementModel( ( GpsItem ) item );
                    double[] stateEstimation = kalmanFilterModel.correct( ( GpsItem ) item );
                    Location estimatedLocation = getEstimatedLocation( stateEstimation );

                    Log.i( TAG,
                           String.format( "%s, %d, KalmanUpdate: latitude=%s; longitude=%s; accuracy=%s; speed=%s; bearing=%s",
                                          getCurrentTime(),
                                          NANOSECONDS.toMillis( estimatedLocation.getElapsedRealtimeNanos() ),
                                          estimatedLocation.getLatitude(),
                                          estimatedLocation.getLongitude(),
                                          estimatedLocation.getAccuracy(),
                                          estimatedLocation.getSpeed(),
                                          estimatedLocation.getBearing() ) );

                    LatLng measuredPosition = new LatLng( ( ( GpsItem ) item ).latitude, ( ( GpsItem ) item ).longitude );
                    LatLng estimatedPosition = new LatLng( estimatedLocation.getLatitude(), estimatedLocation.getLongitude() );
                    Message message = Message.obtain();
                    message.obj = new Pair<>( measuredPosition, estimatedPosition );
                    mapActivity.handler.sendMessage( message );
                }
            }
        }
    }

    private String getCurrentTime() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat( "HH:mm:ss.SSS" ); //.format( new Date() )
        return sdf.format( cal.getTime() );
    }

    private Location getEstimatedLocation( double[] stateEstimation ) {
        double velocityX = stateEstimation[2];
        double velocityY = stateEstimation[3];
        long duration = elapsedRealtimeNanos();

        LatLng position = metersToLatLng( stateEstimation[0], stateEstimation[1] );

        Location location = new Location( TAG );
        location.setLatitude( position.latitude );
        location.setLongitude( position.longitude );
        location.setAltitude( location.getAltitude() );
        location.setSpeed( ( float ) Math.sqrt( velocityX * velocityX + velocityY * velocityY ) );
        location.setTime( NANOSECONDS.toMillis( duration ) );
        location.setElapsedRealtimeNanos( duration );
        location.setAccuracy( location.getAccuracy() );

        return location;

    }

    public void stop() {
        running.set( false );

        locationManager.removeUpdates( this );
        sensorManager.unregisterListener( this );

        sensorFusionItems.clear();
    }

    @Override
    public void onSensorChanged( SensorEvent event ) {
        switch( event.sensor.getType() ) {
            case TYPE_LINEAR_ACCELERATION:
                System.arraycopy( event.values, 0, linearAcceleration, 0, event.values.length );
                Matrix.multiplyMV( acceleration, 0, rotationMatrixInv, 0, linearAcceleration, 0 );

                float eastAcceleration = acceleration[0];
                float northAcceleration = acceleration[1];

                double absoluteEastAcceleration = eastAcceleration * Math.cos( magneticDeclination ) - northAcceleration * Math.sin(
                    magneticDeclination );
                double absoluteNorthAcceleration = northAcceleration * Math.cos( magneticDeclination ) + eastAcceleration * Math.sin(
                    magneticDeclination );
                SensorItem sensorItem = new SensorItem( absoluteEastAcceleration,
                                                        absoluteNorthAcceleration,
                                                        NANOSECONDS.toMillis( elapsedRealtimeNanos() ) );
                sensorFusionItems.add( sensorItem );

                Log.i( TAG,
                       String.format( "%s, %s, LinearAcceleration: X=%s Y=%s; AbsoluteAcceleration: X=%s Y=%s;",
                                      getCurrentTime(),
                                      NANOSECONDS.toMillis( elapsedRealtimeNanos() ),
                                      linearAcceleration[0],
                                      linearAcceleration[1],
                                      eastAcceleration,
                                      northAcceleration ) );
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector( rotationMatrix, event.values );
                Matrix.invertM( rotationMatrixInv, 0, rotationMatrix, 0 );
                break;
        }
    }

    @Override
    public void onAccuracyChanged( Sensor sensor, int accuracy ) {

    }

    @Override
    public void onLocationChanged( Location location ) {
        long timestamp = NANOSECONDS.toMillis( location.getElapsedRealtimeNanos() );

        GpsItem gpsItem = new GpsItem( location.getLatitude(),
                                       location.getLongitude(),
                                       location.getAltitude(),
                                       location.getSpeed(),
                                       location.getBearing(),
                                       location.getAccuracy(),
                                       timestamp );
        sensorFusionItems.add( gpsItem );

        magneticDeclination = new GeomagneticField( ( float ) location.getLatitude(),
                                                    ( float ) location.getLongitude(),
                                                    ( float ) location.getAltitude(),
                                                    timestamp ).getDeclination();

        Log.i( TAG,
               String.format( "%s, %d, GPS: latitude=%s; longitude=%s; accuracy=%s; speed=%s; bearing=%s",
                              getCurrentTime(),
                              timestamp,
                              location.getLatitude(),
                              location.getLongitude(),
                              location.getAccuracy(),
                              location.getSpeed(),
                              location.getBearing() ) );
    }

    @Override
    public void onStatusChanged( String provider, int status, Bundle extras ) {

    }

    @Override
    public void onProviderEnabled( String provider ) {

    }

    @Override
    public void onProviderDisabled( String provider ) {

    }
}
