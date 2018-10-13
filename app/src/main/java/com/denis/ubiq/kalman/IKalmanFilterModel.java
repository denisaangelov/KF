package com.denis.ubiq.kalman;

import com.denis.ubiq.items.*;

public interface IKalmanFilterModel {

    void updateProcessModel( SensorItem sensorItem );
    double[] predict( SensorItem item );
    void updateMeasurementModel( GpsItem gpsItem );
    double[] correct( GpsItem item );
}
