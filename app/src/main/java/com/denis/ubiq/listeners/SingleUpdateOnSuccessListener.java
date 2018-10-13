package com.denis.ubiq.listeners;

import android.util.Log;

import com.denis.ubiq.MapActivity;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.tasks.OnSuccessListener;

import static com.denis.ubiq.utils.Constants.TAG;

public class SingleUpdateOnSuccessListener implements OnSuccessListener<LocationSettingsResponse> {

    private final MapActivity mapActivity;

    public SingleUpdateOnSuccessListener( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
    }

    @Override
    @SuppressWarnings( "MissingPermission" )
    public void onSuccess( LocationSettingsResponse locationSettingsResponse ) {
        mapActivity.requestSingleUpdate();
        Log.i( TAG, "All location settings are satisfied." );
    }
}
