package com.denis.ubiq.listeners;

import android.content.IntentSender;
import android.support.annotation.NonNull;
import android.util.Log;

import com.denis.ubiq.MapActivity;
import com.denis.ubiq.utils.Constants;
import com.google.android.gms.common.api.*;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnFailureListener;

import static android.widget.Toast.LENGTH_LONG;

public class LocationOnFailureListener implements OnFailureListener {

    private final MapActivity mapActivity;

    public LocationOnFailureListener( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
    }

    @Override
    public void onFailure( @NonNull Exception e ) {
        int statusCode = ( ( ApiException ) e ).getStatusCode();
        switch( statusCode ) {
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                Log.i( Constants.TAG, "Location settings are not satisfied. Attempting to upgrade location settings..." );
                try {
                    ResolvableApiException rae = ( ResolvableApiException ) e;
                    rae.startResolutionForResult( mapActivity, Constants.REQUEST_CHECK_SETTINGS );
                } catch( IntentSender.SendIntentException sie ) {
                    Log.i( Constants.TAG, "PendingIntent unable to execute request." );
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                String errorMessage = "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings.";
                mapActivity.showToast( errorMessage, LENGTH_LONG );

                Log.e( Constants.TAG, errorMessage );
        }
    }
}
