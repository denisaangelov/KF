package com.denis.ubiq.listeners;

import android.widget.CompoundButton;

import com.denis.ubiq.*;

public class SwitchButtonListener implements CompoundButton.OnCheckedChangeListener {

    private final MapActivity mapActivity;

    public SwitchButtonListener( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
    }

    @Override
    public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {
        if( isChecked ) {
            mapActivity.startFilteringHandler();
            buttonView.setText( R.string.stop_updates );
        } else {
            mapActivity.stopFilteringHandler();
            buttonView.setText( R.string.start_updates );
        }
    }
}
