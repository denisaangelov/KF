package com.denis.ubiq.listeners;

import android.util.Log;

import com.denis.ubiq.kalman.KalmanFilterWorker;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.tasks.OnSuccessListener;

import static com.denis.ubiq.utils.Constants.TAG;

public class LocationOnSuccessListener implements OnSuccessListener<LocationSettingsResponse> {

    private final KalmanFilterWorker kalmanFilterWorker;

    public LocationOnSuccessListener( KalmanFilterWorker worker ) {
        this.kalmanFilterWorker = worker;
    }

    @Override
    @SuppressWarnings( "MissingPermission" )
    public void onSuccess( LocationSettingsResponse locationSettingsResponse ) {
        kalmanFilterWorker.start();
        Log.i( TAG, "All location settings are satisfied." );
    }
}
