package com.denis.ubiq;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import android.hardware.*;
import android.location.*;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.*;
import android.view.*;
import android.widget.*;

import com.denis.ubiq.kalman.KalmanFilterWorker;
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
import static com.denis.ubiq.utils.CalculationUtils.getCurrentTime;
import static com.denis.ubiq.utils.Constants.*;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener {

    private static boolean isGpsVisible = true;
    private static boolean isNoisyVisible = true;
    private static boolean isKalmanVisible = true;
    private static Marker navigationMarker;
    private static LatLng currentPosition;
    private static List<Circle> circles = new ArrayList();
    private static List<Polyline> polylines = new ArrayList();
    public Handler handler;
    private Button toggleBtn;
    private ImageButton positionBtn;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private GoogleMap map;
    private KalmanFilterWorker worker;
    private float[] acceleration = new float[4];
    private float[] magneticField = new float[4];
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];
    private int rate = 3;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.map_activity );

        this.settingsClient = LocationServices.getSettingsClient( this );

        this.locationManager = ( LocationManager ) getSystemService( LOCATION_SERVICE );

        buildLocationSettings();

        this.sensorManager = ( SensorManager ) getSystemService( SENSOR_SERVICE );

        ( ( Switch ) findViewById( R.id.toggleBtn ) ).setOnCheckedChangeListener( new SwitchButtonListener( this ) );

        ( ( MapFragment ) getFragmentManager().findFragmentById( R.id.map ) ).getMapAsync( this );

        toggleBtn = findViewById( R.id.toggleBtn );
        positionBtn = findViewById( R.id.positionBtn );

        Toolbar toolbar = findViewById( R.id.toolbar );
        setSupportActionBar( toolbar );
        toolbar.inflateMenu( R.menu.menu );

        enableDisableButtons( false );
    }

    private void buildLocationSettings() {
        buildLocationRequest();
        buildLocationSettingsRequest();
    }

    private void enableDisableButtons( boolean enabled ) {
        toggleBtn.setEnabled( enabled );
        positionBtn.setEnabled( enabled );
    }

    private void buildLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval( UPDATE_INTERVAL );
        locationRequest.setFastestInterval( UPDATE_INTERVAL / 2 );
        locationRequest.setPriority( LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY );
    }

    private void buildLocationSettingsRequest() {
        locationSettingsRequest = new LocationSettingsRequest.Builder().addLocationRequest( locationRequest ).build();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener( this );
        if( worker != null ) {
            worker.unregisterListeners();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensorListeners();
        if( worker != null && worker.isRunning.get() ) {
            worker.start();
        }
    }

    private void registerSensorListeners() {
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_ACCELEROMETER ), SENSOR_DELAY_NORMAL );
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_MAGNETIC_FIELD ), SENSOR_DELAY_NORMAL );
    }

    @Override
    public void onMapReady( GoogleMap googleMap ) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled( true );

        settingsClient.checkLocationSettings( locationSettingsRequest )
                      .addOnSuccessListener( this, new SingleUpdateOnSuccessListener( this ) )
                      .addOnFailureListener( this, new LocationOnFailureListener( this ) );
        handler = new MapHandler( map );
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

    public void moveToPosition( View view ) {
        if( currentPosition != null ) {
            moveToPosition( currentPosition );
        } else {
            requestSingleUpdate();
        }
    }

    private void moveToPosition( LatLng latLng ) {
        if( map != null ) {
            map.animateCamera( CameraUpdateFactory.newLatLngZoom( latLng, 15 ) );
        }
    }

    public void requestSingleUpdate() {
        if( !( ActivityCompat.checkSelfPermission( this, ACCESS_FINE_LOCATION ) == PERMISSION_GRANTED ) ) {
            requestPermissions();
        }
        locationManager.requestSingleUpdate( GPS_PROVIDER, new SingleUpdateLocationListener( this ), null );
    }

    private void requestPermissions() {
        if( ActivityCompat.shouldShowRequestPermissionRationale( this, ACCESS_FINE_LOCATION ) ) {
            Toast.makeText( this, getString( R.string.permission_rationale ), LENGTH_LONG ).show();
            startLocationPermissionRequest();

            Log.i( TAG, "Displaying permission rationale to provide additional context." );
        } else {
            startLocationPermissionRequest();

            Log.i( TAG, "Requesting permission" );
        }
    }

    private void startLocationPermissionRequest() {
        ActivityCompat.requestPermissions( this, new String[] { ACCESS_FINE_LOCATION }, REQUEST_PERMISSIONS_REQUEST_CODE );
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
        if( SensorManager.getRotationMatrix( rotationMatrix, null, acceleration, magneticField ) ) {
            float[] orientation = SensorManager.getOrientation( rotationMatrix, orientationAngles );

            if( orientationAngles != null && navigationMarker != null ) {
                navigationMarker.setRotation( ( float ) Math.toDegrees( orientation[0] ) );
            }
        }
    }

    @Override
    public void onAccuracyChanged( Sensor sensor, int accuracy ) {

    }

    public void onSingleUpdateLocationSuccess( Location location ) {
        registerSensorListeners();
        initKalmanFilterModel( location );

        currentPosition = new LatLng( location.getLatitude(), location.getLongitude() );
        addMarker( currentPosition );
        moveToPosition( currentPosition );
        enableDisableButtons( true );

        Log.i( TAG,
               String.format( "%s, %d, Initial GPS: latitude=%s; longitude=%s; accuracy=%s; speed=%s; bearing=%s",
                              getCurrentTime(),
                              location.getTime(),
                              location.getLatitude(),
                              location.getLongitude(),
                              location.getAccuracy(),
                              location.getSpeed(),
                              location.getBearing() ) );
    }

    private void initKalmanFilterModel( Location location ) {
        if( worker == null ) {
            worker = new KalmanFilterWorker( this, location, rate );
        }
    }

    private void addMarker( LatLng position ) {
        if( map != null && position != null ) {
            navigationMarker = map.addMarker( markerOptions.position( position ) );
        }
    }

    @Override
    public boolean onCreateOptionsMenu( final Menu menu ) {
        getMenuInflater().inflate( R.menu.menu, menu );

        return true;
    }

    public void clear( MenuItem item ) {
        if( map != null ) {
            map.clear();
            clearPolylines();
            clearCircles();

            if( currentPosition != null ) {
                addMarker( currentPosition );
            }
        }
    }

    private void clearPolylines() {
        for( Polyline polyline : polylines ) {
            polyline.remove();
        }
        polylines.clear();
    }

    private void clearCircles() {
        for( Circle circle : circles ) {
            circle.remove();
        }
        circles.clear();
    }

    public void setRate( MenuItem item ) {
        item.setChecked( true );

        switch( item.getItemId() ) {
            case R.id.one:
                rate = 1;
                break;
            case R.id.three:
                rate = 3;
                break;
            case R.id.five:
                rate = 5;
                break;
        }

        if( worker != null ) {
            worker.rate = rate;
        }
    }

    public void addPolyline( MenuItem item ) {
        item.setChecked( !item.isChecked() );

        switch( item.getItemId() ) {
            case R.id.kalman_poly:
                Polyline redPolyline = map.addPolyline( redPolylineOptions );
                redPolyline.setTag( "red" );
                polylines.add( redPolyline );
                break;
            case R.id.gps_poly:
                polylines.add( map.addPolyline( greenPolylineOptions ) );
                break;
            case R.id.noisy_poly:
                polylines.add( map.addPolyline( bluePolylineOptions ) );
                break;
        }
    }

    public void showHideMarker( MenuItem item ) {
        item.setChecked( !item.isChecked() );

        switch( item.getItemId() ) {
            case R.id.kalman:
                isKalmanVisible = item.isChecked();
                break;
            case R.id.gps:
                isGpsVisible = item.isChecked();
                break;
            case R.id.noisy:
                isNoisyVisible = item.isChecked();
                break;
        }
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
            if( measuredPosition != null ) {
                if( isGpsVisible ) {
                    circles.add( map.addCircle( greenCircleOptions.center( measuredPosition ) ) );
                    greenPolylineOptions.add( measuredPosition );
                }

                if( isNoisyVisible ) {
                    LatLng noisyPosition = getNoisyPosition( measuredPosition );
                    circles.add( map.addCircle( blueCircleOptions.center( noisyPosition ) ) );
                    bluePolylineOptions.add( noisyPosition );
                }
            }

            LatLng estimatedPosition = ( LatLng ) ( ( Pair ) msg.obj ).second;
            if( estimatedPosition != null ) {
                if( isKalmanVisible ) {
                    circles.add( map.addCircle( redCircleOptions.center( estimatedPosition ) ) );
                    redPolylineOptions.add( estimatedPosition );
                }

                navigationMarker.setPosition( estimatedPosition );
                currentPosition = estimatedPosition;
            }
        }

        private LatLng getNoisyPosition( LatLng position ) {
            double latitude = position.latitude + ThreadLocalRandom.current().nextDouble( 0.00002, 0.0002 ) * ( new Random().nextBoolean() ? -1 : 1 );
            double longitude = position.longitude + ThreadLocalRandom.current().nextDouble( 0.00002, 0.0002 ) * ( new Random().nextBoolean()
                                                                                                                  ? -1
                                                                                                                  : 1 );
            return new LatLng( latitude, longitude );
        }
    }
}
