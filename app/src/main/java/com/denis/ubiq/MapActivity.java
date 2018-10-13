package com.denis.ubiq;

import java.text.DecimalFormat;
import java.util.*;

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
import com.denis.ubiq.orientation.OrientationWorker;
import com.denis.ubiq.utils.WriteUtils;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.widget.Toast.LENGTH_LONG;
import static com.denis.ubiq.utils.CalculationUtils.getCurrentTime;
import static com.denis.ubiq.utils.Constants.*;
import static java.util.Objects.requireNonNull;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    public Handler mapHandler;
    public Handler orientationHandler;
    private KalmanFilterWorker kalmanWorker;
    private OrientationWorker orientationWorker;

    private boolean isGpsVisible = true;
    private boolean isKalmanVisible = true;
    private Marker navigationMarker;

    private LatLng currentPosition;
    private LatLng initialPosition;
    private List<Circle> circles = new ArrayList();
    private List<Polyline> redPolylines = new ArrayList();
    private List<Polyline> greenPolylines = new ArrayList();
    private Button toggleBtn;
    private ImageButton positionBtn;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private LocationManager locationManager;
    private GoogleMap map;

    private int rate = 3;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.map_activity );

        SupportMapFragment mapFragment = ( SupportMapFragment ) getSupportFragmentManager().findFragmentById( R.id.map );
        requireNonNull( mapFragment ).getMapAsync( this );

        this.settingsClient = LocationServices.getSettingsClient( this );

        this.locationManager = ( LocationManager ) getSystemService( LOCATION_SERVICE );

        buildLocationSettings();

        ( ( Switch ) findViewById( R.id.toggleBtn ) ).setOnCheckedChangeListener( new SwitchButtonListener( this ) );

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
        locationRequest.setFastestInterval( FASTEST_UPDATE_INTERVAL );
        locationRequest.setPriority( LocationRequest.PRIORITY_HIGH_ACCURACY );
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
        if( orientationWorker != null ) {
            orientationWorker.unregisterListeners();
        }
        if( kalmanWorker != null ) {
            kalmanWorker.unregisterListeners();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if( orientationWorker != null ) {
            orientationWorker.registerSensorListeners();
        }
        if( kalmanWorker != null && kalmanWorker.isRunning.get() ) {
            kalmanWorker.start();
        }
    }

    @Override
    public void onMapReady( GoogleMap googleMap ) {
        if( map != null ) {
            map.setIndoorEnabled( false );
            map.setBuildingsEnabled( false );
            map.setMapType( GoogleMap.MAP_TYPE_TERRAIN );
        }
        googleMap.setIndoorEnabled( true );
        googleMap.setBuildingsEnabled( true );
        googleMap.setMapType( GoogleMap.MAP_TYPE_TERRAIN );

        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled( true );

        settingsClient.checkLocationSettings( locationSettingsRequest )
                      .addOnSuccessListener( this, new SingleUpdateOnSuccessListener( this ) )
                      .addOnFailureListener( this, new LocationOnFailureListener( this ) );
        mapHandler = new MapHandler( this );
        orientationHandler = new OrientationHandler( this );
    }

    public void startFilteringHandler() {
        getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
        if( kalmanWorker == null ) {
            kalmanWorker = new KalmanFilterWorker( this );
        }
        kalmanWorker.register();
    }

    public void stopFilteringHandler() {
        kalmanWorker.stop();
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

    public void onSingleUpdateLocationSuccess( Location location ) {
        orientationWorker = new OrientationWorker( this );

        initKalmanFilterModel( location );

        initialPosition = new LatLng( location.getLatitude(), location.getLongitude() );
        currentPosition = new LatLng( location.getLatitude(), location.getLongitude() );
        addMarker( currentPosition );
        moveToPosition( currentPosition );
        enableDisableButtons( true );

        updateTextView( location.getLatitude(), location.getLongitude() );
        WriteUtils.writeToLog( "%s, %s, %s: latitude=%s; longitude=%s; accuracy=%s; speed=%s; bearing=%s",
                               getCurrentTime(),
                               location.getTime(),
                               "InitialGPS",
                               location.getLatitude(),
                               location.getLongitude(),
                               location.getAccuracy(),
                               location.getSpeed(),
                               location.getBearing() );
    }

    private void initKalmanFilterModel( Location location ) {
        if( kalmanWorker == null ) {
            kalmanWorker = new KalmanFilterWorker( this, location, rate );
        }
    }

    private void addMarker( LatLng position ) {
        if( map != null && position != null ) {
            navigationMarker = map.addMarker( markerOptions.position( position ) );
        }
    }

    private void updateTextView( double latitude, double longitude ) {
        TextView textView = findViewById( R.id.textView );
        textView.setText( String.format( "latitude: %s longitude: %s",
                                         new DecimalFormat( "#.###" ).format( latitude ),
                                         new DecimalFormat( "#.###" ).format( longitude ) ) );
    }

    @Override
    public boolean onCreateOptionsMenu( final Menu menu ) {
        getMenuInflater().inflate( R.menu.menu, menu );
        return true;
    }

    public void clear( MenuItem item ) {
        if( map != null ) {
            map.clear();
            clearCircles();
            clearPolylines( redPolylines );
            clearPolylines( greenPolylines );

            if( currentPosition != null ) {
                addMarker( currentPosition );
            }
        }
    }

    private void clearCircles() {
        for( Circle circle : circles ) {
            circle.remove();
        }
        circles.clear();
    }

    private void clearPolylines( List<Polyline> polylines ) {
        for( Polyline polyline : polylines ) {
            polyline.remove();
        }
        polylines.clear();
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
            case R.id.ten:
                rate = 10;
                break;
        }

        if( kalmanWorker != null ) {
            kalmanWorker.rate = rate;
        }
    }

    public void addPolyline( MenuItem item ) {
        item.setChecked( !item.isChecked() );

        switch( item.getItemId() ) {
            case R.id.kalman_poly:
                if( item.isChecked() ) {
                    redPolylines.add( map.addPolyline( redPolylineOptions ) );
                } else {
                    clearPolylines( redPolylines );
                }
                break;
            case R.id.gps_poly:
                if( item.isChecked() ) {
                    greenPolylines.add( map.addPolyline( greenPolylineOptions ) );
                } else {
                    clearPolylines( greenPolylines );
                }
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
        }
    }

    private void handleMapMessage( LatLng measuredPosition, LatLng estimatedPosition ) {
        if( measuredPosition != null ) {
            if( isGpsVisible ) {
                circles.add( map.addCircle( greenCircleOptions.center( measuredPosition ) ) );
                greenPolylineOptions.add( measuredPosition );
            }
        }

        if( estimatedPosition != null ) {
            if( isKalmanVisible ) {
                circles.add( map.addCircle( redCircleOptions.center( estimatedPosition ) ) );
                redPolylineOptions.add( estimatedPosition );
            }

            navigationMarker.setPosition( estimatedPosition );
            currentPosition = estimatedPosition;

            updateTextView( currentPosition.latitude, currentPosition.longitude );
        }
    }

    private void handleOrientationMessage( float[] orientation ) {
        if( orientation != null && navigationMarker != null ) {
            navigationMarker.setRotation( ( float ) Math.toDegrees( orientation[0] ) );
        }
    }

    private static class MapHandler extends Handler {

        MapActivity mapActivity;

        MapHandler( MapActivity mapActivity ) {
            super( Looper.getMainLooper() );
            this.mapActivity = mapActivity;
        }

        @Override
        public void handleMessage( Message msg ) {
            super.handleMessage( msg );
            mapActivity.handleMapMessage( ( LatLng ) ( ( Pair ) msg.obj ).first, ( LatLng ) ( ( Pair ) msg.obj ).second );
        }
    }

    private static class OrientationHandler extends Handler {

        private final MapActivity mapActivity;

        OrientationHandler( MapActivity mapActivity ) {
            super( Looper.getMainLooper() );
            this.mapActivity = mapActivity;
        }

        @Override
        public void handleMessage( Message msg ) {
            super.handleMessage( msg );
            mapActivity.handleOrientationMessage( ( float[] ) msg.obj );
        }
    }

}
