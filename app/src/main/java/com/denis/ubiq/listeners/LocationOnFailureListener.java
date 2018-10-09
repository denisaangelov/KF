package com.denis.ubiq.listeners;

import android.content.IntentSender;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.denis.ubiq.MapActivity;
import com.denis.ubiq.utils.Constants;
import com.google.android.gms.common.api.*;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnFailureListener;

import static android.widget.Toast.LENGTH_LONG;
import static com.denis.ubiq.utils.Constants.TAG;

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
                Log.i( TAG, "Location settings are not satisfied. Attempting to upgrade location settings..." );
                try {
                    ResolvableApiException rae = ( ResolvableApiException ) e;
                    rae.startResolutionForResult( mapActivity, Constants.REQUEST_CHECK_SETTINGS );
                } catch( IntentSender.SendIntentException sie ) {
                    Log.i( TAG, "PendingIntent unable to execute request." );
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                String errorMessage = "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings.";
                Toast.makeText( mapActivity, errorMessage, LENGTH_LONG ).show();
                Log.e( TAG, errorMessage );
        }
    }
}
