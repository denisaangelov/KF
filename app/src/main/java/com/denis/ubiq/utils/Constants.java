package com.denis.ubiq.utils;

import com.denis.ubiq.*;
import com.google.android.gms.maps.model.*;

import static android.graphics.Color.*;

public final class Constants {

    public static final String TAG = MapActivity.class.getSimpleName();

    public static final int REQUEST_CHECK_SETTINGS = 0x1;
    public static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    public static final long UPDATE_INTERVAL = 1000;
    public static final long FASTEST_UPDATE_INTERVAL = UPDATE_INTERVAL / 2;
    public static final int Hz = 10;

    public static CircleOptions greenCircleOptions = new CircleOptions().clickable( true )
                                                                        .radius( 7 )
                                                                        .fillColor( GREEN )
                                                                        .strokeWidth( 1 )
                                                                        .strokeColor( BLACK );
    public static CircleOptions blueCircleOptions = new CircleOptions().clickable( true )
                                                                       .radius( 7 )
                                                                       .fillColor( GREEN )
                                                                       .strokeWidth( 1 )
                                                                       .strokeColor( RED );
    public static CircleOptions redCircleOptions = new CircleOptions().clickable( true )
                                                                      .radius( 7 )
                                                                      .fillColor( RED )
                                                                      .strokeWidth( 1 )
                                                                      .strokeColor( BLACK );

    public static PolylineOptions greenPolylineOptions = new PolylineOptions().clickable( true ).color( GREEN ).width( 7F ).geodesic( true );
    public static PolylineOptions redPolylineOptions = new PolylineOptions().clickable( true ).color( RED ).width( 7F ).geodesic( true );

    public static MarkerOptions markerOptions = new MarkerOptions().icon( BitmapDescriptorFactory.fromResource( R.drawable.ic_navigation ) )
                                                                   .title( "Marker" );

}
