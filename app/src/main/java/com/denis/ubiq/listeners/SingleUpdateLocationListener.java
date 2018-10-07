package com.denis.ubiq.listeners;

import android.location.*;
import android.os.Bundle;

import com.denis.ubiq.MapActivity;
import com.google.android.gms.maps.model.LatLng;

public class SingleUpdateLocationListener implements LocationListener {

    private final MapActivity mapActivity;

    public SingleUpdateLocationListener( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
    }

    public void onLocationChanged( Location location ) {
        mapActivity.registerSensorListeners();
        mapActivity.addMarker( location );
        mapActivity.moveToPosition( new LatLng( location.getLatitude(), location.getLongitude() ) );
        mapActivity.initKalmanFilterModel( location );
    }

    public void onStatusChanged( String provider, int status, Bundle extras ) { }

    public void onProviderEnabled( String provider ) { }

    public void onProviderDisabled( String provider ) { }
}
