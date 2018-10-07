package com.denis.ubiq.listeners;

import android.location.*;
import android.os.Bundle;

import com.denis.ubiq.MapActivity;

public class SingleUpdateLocationListener implements LocationListener {

    private final MapActivity mapActivity;

    public SingleUpdateLocationListener( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
    }

    public void onLocationChanged( Location location ) {
        mapActivity.onSingleUpdateLocationSuccess( location );
    }

    public void onStatusChanged( String provider, int status, Bundle extras ) { }

    public void onProviderEnabled( String provider ) { }

    public void onProviderDisabled( String provider ) { }
}
