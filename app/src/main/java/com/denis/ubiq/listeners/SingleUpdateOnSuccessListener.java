package com.denis.ubiq.listeners;

import android.util.Log;

import com.denis.ubiq.MapActivity;
import com.denis.ubiq.utils.Constants;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.tasks.OnSuccessListener;

public class SingleUpdateOnSuccessListener implements OnSuccessListener<LocationSettingsResponse> {

    private final MapActivity mapActivity;

    public SingleUpdateOnSuccessListener( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
    }

    @Override
    @SuppressWarnings( "MissingPermission" )
    public void onSuccess( LocationSettingsResponse locationSettingsResponse ) {
        mapActivity.requestSingleUpdate();

        Log.i( Constants.TAG, "All location settings are satisfied." );
    }
}
