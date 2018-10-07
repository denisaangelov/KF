package com.denis.ubiq;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import android.hardware.*;
import android.location.*;
import android.os.*;
import android.support.v4.app.*;
import android.util.*;
import android.view.*;
import android.widget.*;

import com.denis.ubiq.kalman.*;
import com.denis.ubiq.listeners.*;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.hardware.Sensor.*;
import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.widget.Toast.LENGTH_LONG;
import static com.denis.ubiq.utils.Constants.*;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback, SensorEventListener {

    private static Marker navigationMarker;
    public Handler handler;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private GoogleMap map;
    private Location currentLocation;
    private Location filteredLocation;
    private KalmanFilterWorker worker;
    private float[] acceleration = new float[4];
    private float[] magneticField = new float[4];
    private float[] orientationRotationMatrix = new float[9];
    private float[] accMagOrientation = new float[3];

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_maps );

        this.settingsClient = LocationServices.getSettingsClient( this );

        this.locationManager = ( LocationManager ) getSystemService( LOCATION_SERVICE );

        buildLocationSettings();

        this.sensorManager = ( SensorManager ) getSystemService( SENSOR_SERVICE );

        ( ( Switch ) findViewById( R.id.toggleTracking ) ).setOnCheckedChangeListener( new SwitchButtonListener( this ) );

        ( ( SupportMapFragment ) getSupportFragmentManager().findFragmentById( R.id.map ) ).getMapAsync( this );
    }

    private void buildLocationSettings() {
        buildLocationRequest();
        buildLocationSettingsRequest();
    }

    private void buildLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval( UPDATE_INTERVAL );
        locationRequest.setFastestInterval( FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS );
        locationRequest.setPriority( LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY );
    }

    private void buildLocationSettingsRequest() {
        locationSettingsRequest = new LocationSettingsRequest.Builder().addLocationRequest( locationRequest ).build();
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    public void registerSensorListeners() {
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_ACCELEROMETER ), SENSOR_DELAY_NORMAL );
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_MAGNETIC_FIELD ), SENSOR_DELAY_NORMAL );
    }

    @Override
    public void onMapReady( GoogleMap googleMap ) {
        map = googleMap;
        settingsClient.checkLocationSettings( locationSettingsRequest ).addOnSuccessListener( this, new SingleUpdateOnSuccessListener( this ) );
        handler = new MapHandler( map );
    }

    public void requestSingleUpdate() {
        if( !( ActivityCompat.checkSelfPermission( this, ACCESS_FINE_LOCATION ) == PERMISSION_GRANTED ) ) {
            requestPermissions();
        }
        locationManager.requestSingleUpdate( GPS_PROVIDER, new SingleUpdateLocationListener( this ), null );
    }

    private void requestPermissions() {
        if( ActivityCompat.shouldShowRequestPermissionRationale( this, ACCESS_FINE_LOCATION ) ) {
            showToast( getString( R.string.permission_rationale ), LENGTH_LONG );
            startLocationPermissionRequest();

            Log.i( TAG, "Displaying permission rationale to provide additional context." );
        } else {
            startLocationPermissionRequest();

            Log.i( TAG, "Requesting permission" );
        }
    }

    public void showToast( final String text, int length ) {
        Toast.makeText( this, text, length ).show();
    }

    private void startLocationPermissionRequest() {
        ActivityCompat.requestPermissions( this, new String[] { ACCESS_FINE_LOCATION }, REQUEST_PERMISSIONS_REQUEST_CODE );
    }

    public void startFilteringHandler() {
        getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
        if( worker == null ) {
            worker = new KalmanFilterWorker( this );
        }
        worker.begin();
    }

    public void stopFilteringHandler() {
        worker.stop();
    }

    public void addMarker( Location location ) {
        if( map != null ) {
            navigationMarker = map.addMarker( markerOptions.position( new LatLng( location.getLatitude(), location.getLongitude() ) ) );
        }
    }

    public void moveToPosition( View view ) {
        if( currentLocation != null ) {
            moveToPosition( new LatLng( currentLocation.getLatitude(), currentLocation.getLongitude() ) );
        }
    }

    public void moveToPosition( LatLng latLng ) {
        if( map != null ) {
            map.animateCamera( CameraUpdateFactory.newLatLngZoom( latLng, 15 ) );
        }
    }

    public void initKalmanFilterModel( Location location ) {
        if( worker == null ) {
            worker = new KalmanFilterWorker( this );
            worker.kalmanFilterModel = new KalmanFilterModel( location );
        }
    }

    @Override
    public void onSensorChanged( SensorEvent event ) {
        switch( event.sensor.getType() ) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy( event.values, 0, acceleration, 0, event.values.length );
                calculateAccMagOrientation();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy( event.values, 0, magneticField, 0, event.values.length );
                break;
        }
    }

    private void calculateAccMagOrientation() {
        if( SensorManager.getRotationMatrix( orientationRotationMatrix, null, acceleration, magneticField ) ) {
            float[] orientation = SensorManager.getOrientation( orientationRotationMatrix, accMagOrientation );

            if( accMagOrientation != null && navigationMarker != null ) {
                navigationMarker.setRotation( ( float ) Math.toDegrees( orientation[0] ) );
            }
        }
    }

    @Override
    public void onAccuracyChanged( Sensor sensor, int accuracy ) {

    }

    private static class MapHandler extends Handler {

        GoogleMap map;

        MapHandler( GoogleMap map ) {
            super( Looper.getMainLooper() );
            this.map = map;
        }

        @Override
        public void handleMessage( Message msg ) {
            super.handleMessage( msg );

            LatLng measuredPosition = ( LatLng ) ( ( Pair ) msg.obj ).first;
            map.addCircle( greenCircleOptions.center( measuredPosition ) );
            map.addCircle( blueCircleOptions.center( getNoisyPosition( measuredPosition ) ) );

            LatLng estimatedPosition = ( LatLng ) ( ( Pair ) msg.obj ).second;
            map.addCircle( redCircleOptions.center( estimatedPosition ) );
            navigationMarker.setPosition( estimatedPosition );
        }

        private LatLng getNoisyPosition( LatLng position ) {
            double latitude = position.latitude + ( ThreadLocalRandom.current().nextDouble( 0.00002, 0.0002 ) * ( new Random().nextBoolean()
                                                                                                                  ? -1
                                                                                                                  : 1 ) );
            double longitude = position.longitude + ( ThreadLocalRandom.current().nextDouble( 0.00002, 0.0002 ) * ( new Random().nextBoolean()
                                                                                                                    ? -1
                                                                                                                    : 1 ) );
            return new LatLng( latitude, longitude );
        }
    }

}
