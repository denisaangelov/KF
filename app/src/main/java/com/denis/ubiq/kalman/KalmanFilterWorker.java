package com.denis.ubiq.kalman;

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
import android.util.Pair;

import com.denis.ubiq.MapActivity;
import com.denis.ubiq.items.*;
import com.denis.ubiq.listeners.*;
import com.denis.ubiq.utils.WriteUtils;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.model.LatLng;

import static android.content.Context.*;
import static android.hardware.Sensor.*;
import static android.location.LocationManager.*;
import static android.os.SystemClock.elapsedRealtimeNanos;
import static com.denis.ubiq.utils.CalculationUtils.*;
import static com.denis.ubiq.utils.Constants.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class KalmanFilterWorker implements SensorEventListener, LocationListener, Runnable, IKalmanFilterWorker {

    private static String TAG = "KalmanFilterWorker";
    private static int DELAY = 500;

    private static int tooOldLocationDelta = 5000; // 1000 * 60 * 2;
    public final AtomicBoolean isRunning = new AtomicBoolean( false );
    private final MapActivity mapActivity;
    private final SettingsClient settingsClient;
    private final LocationManager locationManager;
    private final SensorManager sensorManager;
    public int rate;
    private long stepCounter = 1;

    private KalmanFilterModel kalmanFilterModel;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;

    private float[] linearAcceleration = new float[4];
    private float[] rotationMatrix = new float[16];
    private float[] rotationMatrixInv = new float[16];
    private float[] acceleration = new float[4];
    private float magneticDeclination = 0.0F;

    private Queue<TimestampItem> sensorFusionItems = new PriorityQueue<>();
    private long predictionStep = 5;
    private Location lastBestLocation;

    public KalmanFilterWorker( MapActivity mapActivity, Location location, int rate ) {
        this( mapActivity );
        this.kalmanFilterModel = new KalmanFilterModel( location );
        this.rate = rate;
        this.tooOldLocationDelta = rate * 1000;
    }

    public KalmanFilterWorker( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
        this.settingsClient = LocationServices.getSettingsClient( mapActivity );
        this.locationManager = ( LocationManager ) mapActivity.getSystemService( LOCATION_SERVICE );
        this.sensorManager = ( SensorManager ) mapActivity.getSystemService( SENSOR_SERVICE );

        buildLocationSettings();
    }

    public void buildLocationSettings() {
        buildLocationRequest();
        buildLocationSettingsRequest();
    }

    private void buildLocationRequest() {
        this.locationRequest = new LocationRequest();
        locationRequest.setInterval( UPDATE_INTERVAL );
        locationRequest.setFastestInterval( FASTEST_UPDATE_INTERVAL );
        locationRequest.setPriority( LocationRequest.PRIORITY_HIGH_ACCURACY );
    }

    private void buildLocationSettingsRequest() {
        locationSettingsRequest = new LocationSettingsRequest.Builder().addLocationRequest( locationRequest ).build();
    }

    public void register() {
        settingsClient.checkLocationSettings( locationSettingsRequest )
                      .addOnSuccessListener( mapActivity, new LocationOnSuccessListener( this ) )
                      .addOnFailureListener( mapActivity, new LocationOnFailureListener( mapActivity ) );
    }

    @Override
    public void run() {
        isRunning.set( true );
        while( isRunning.get() ) {
            try {
                Thread.sleep( DELAY );
            } catch( InterruptedException e ) {
                e.printStackTrace();
                continue;
            }

            TimestampItem item;
            while( ( item = sensorFusionItems.poll() ) != null ) {
                if( item instanceof SensorItem ) {
                    kalmanFilterModel.updateProcessModel( ( SensorItem ) item );
                    double[] predictedPosition = kalmanFilterModel.predict( ( ( SensorItem ) item ) );
                    LatLng position = convertMetersToLatLng( predictedPosition[0], predictedPosition[1] );

                    String kalmanString;
                    if( ++stepCounter > predictionStep ) {
                        Message message = Message.obtain();
                        message.obj = new Pair<>( null, position );
                        mapActivity.mapHandler.sendMessage( message );
                        kalmanString = "KalmanForetell";
                    } else {
                        kalmanString = "KalmanPredict";
                    }

                    WriteUtils.writeToLog( "%s, %s, %s: PositionX=%s PositionY=%s; VelocityX=%s VelocityY=%s",
                                           getCurrentTime(),
                                           item.timestamp,
                                           kalmanString,
                                           position.latitude,
                                           position.longitude,
                                           predictedPosition[2],
                                           predictedPosition[3] );
                } else {
                    kalmanFilterModel.updateMeasurementModel( ( GpsItem ) item );
                    double[] stateEstimation = kalmanFilterModel.correct( ( GpsItem ) item );

                    if( stepCounter >= rate ) {
                        Location estimatedLocation = getEstimatedLocation( stateEstimation );
                        LatLng measuredPosition = new LatLng( ( ( GpsItem ) item ).latitude, ( ( GpsItem ) item ).longitude );
                        LatLng estimatedPosition = new LatLng( estimatedLocation.getLatitude(), estimatedLocation.getLongitude() );

                        Message message = Message.obtain();
                        message.obj = new Pair<>( measuredPosition, estimatedPosition );
                        mapActivity.mapHandler.sendMessage( message );
                        stepCounter = 0;

                        WriteUtils.writeToLog( "%s, %s, %s: latitude=%s; longitude=%s; accuracy=%s; speed=%s; bearing=%s",
                                               getCurrentTime(),
                                               NANOSECONDS.toMillis( estimatedLocation.getElapsedRealtimeNanos() ),
                                               "KalmanUpdate",
                                               estimatedLocation.getLatitude(),
                                               estimatedLocation.getLongitude(),
                                               estimatedLocation.getAccuracy(),
                                               estimatedLocation.getSpeed(),
                                               estimatedLocation.getBearing() );
                    }
                }
            }
        }
    }

    private Location getEstimatedLocation( double[] stateEstimation ) {
        double velocityX = stateEstimation[2];
        double velocityY = stateEstimation[3];
        long duration = elapsedRealtimeNanos();

        LatLng position = convertMetersToLatLng( stateEstimation[0], stateEstimation[1] );

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
                float accuracy = lastBestLocation != null ? lastBestLocation.getAccuracy() : 0.0F;
                SensorItem sensorItem = new SensorItem( absoluteEastAcceleration,
                                                        absoluteNorthAcceleration,
                                                        accuracy,
                                                        NANOSECONDS.toMillis( elapsedRealtimeNanos() ) );
                sensorFusionItems.add( sensorItem );

                WriteUtils.writeToLog( "%s, %s, %s: eastAcceleration=%s northAcceleration=%s noise=%s;",
                                       getCurrentTime(),
                                       NANOSECONDS.toMillis( elapsedRealtimeNanos() ),
                                       "LinearAcceleration",
                                       sensorItem.eastAcceleration,
                                       sensorItem.northAcceleration,
                                       sensorItem.positionNoise );
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector( rotationMatrix, event.values );
                Matrix.invertM( rotationMatrixInv, 0, rotationMatrix, 0 );
                break;
        }
    }

    @Override
    public void onAccuracyChanged( Sensor sensor, int accuracy ) { }

    @Override
    public void onLocationChanged( Location location ) {
        lastBestLocation = getBetterLocation( location, lastBestLocation );

        long timestamp = NANOSECONDS.toMillis( lastBestLocation.getElapsedRealtimeNanos() );

        GpsItem gpsItem = new GpsItem( lastBestLocation.getLatitude(),
                                       lastBestLocation.getLongitude(),
                                       lastBestLocation.getAltitude(),
                                       lastBestLocation.getSpeed(),
                                       lastBestLocation.getBearing(),
                                       lastBestLocation.getAccuracy(),
                                       timestamp );
        sensorFusionItems.add( gpsItem );

        magneticDeclination = new GeomagneticField( ( float ) lastBestLocation.getLatitude(),
                                                    ( float ) lastBestLocation.getLongitude(),
                                                    ( float ) lastBestLocation.getAltitude(),
                                                    timestamp ).getDeclination();

        WriteUtils.writeToLog( "%s, %s, %s(%s): latitude=%s; longitude=%s; accuracy=%s; speed=%s; bearing=%s",
                               getCurrentTime(),
                               timestamp,
                               "GPS",
                               lastBestLocation.getProvider(),
                               lastBestLocation.getLatitude(),
                               lastBestLocation.getLongitude(),
                               lastBestLocation.getAccuracy(),
                               lastBestLocation.getSpeed(),
                               lastBestLocation.getBearing() );

    }

    protected Location getBetterLocation( Location newLocation, Location currentBestLocation ) {
        if( currentBestLocation == null ) {
            // A new location is always better than no location
            return newLocation;
        }

        // Check whether the new location fix is newer or older
        // Check whether the new location fix is newer or older
        long timeDelta = newLocation.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > tooOldLocationDelta;
        boolean isSignificantlyOlder = timeDelta < -tooOldLocationDelta;
        boolean isNewer = timeDelta > 0;

        // If it's been more than 5 seconds since the current location, use the new location
        // because the user has likely moved
        if( isSignificantlyNewer ) {
            return newLocation;
            // If the new location is more than two minutes older, it must be worse
        } else if( isSignificantlyOlder ) {
            return currentBestLocation;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = ( int ) ( newLocation.getAccuracy() - currentBestLocation.getAccuracy() );
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider( newLocation.getProvider(), currentBestLocation.getProvider() );

        // Determine location quality using a combination of timeliness and accuracy
        if( isMoreAccurate ) {
            return newLocation;
        } else if( isNewer && !isLessAccurate ) {
            return newLocation;
        } else if( isNewer && !isSignificantlyLessAccurate && isFromSameProvider ) {
            return newLocation;
        }
        return currentBestLocation;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider( String provider1, String provider2 ) {
        if( provider1 == null ) {
            return provider2 == null;
        }
        return provider1.equals( provider2 );
    }

    @Override
    public void onStatusChanged( String provider, int status, Bundle extras ) { }

    @Override
    public void onProviderEnabled( String provider ) { }

    @Override
    public void onProviderDisabled( String provider ) { }

    public void start() {
        requestLocationUpdates();
        registerSensorListeners();
        new Thread( this ).start();
    }

    public void requestLocationUpdates() {
        locationManager.removeUpdates( this );

        if( ActivityCompat.checkSelfPermission( mapActivity, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission( mapActivity, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
            return;
        }
        locationManager.requestLocationUpdates( GPS_PROVIDER, UPDATE_INTERVAL, 0F, this );
        locationManager.requestLocationUpdates( NETWORK_PROVIDER, UPDATE_INTERVAL, 0F, this );
    }

    public void registerSensorListeners() {
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_LINEAR_ACCELERATION ), hertz2periodUs( Hz ) );
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_ROTATION_VECTOR ), hertz2periodUs( Hz ) );
    }

    public void stop() {
        isRunning.set( false );
        sensorFusionItems.clear();
        unregisterListeners();
    }

    public void unregisterListeners() {
        locationManager.removeUpdates( this );
        sensorManager.unregisterListener( this );
    }
}
