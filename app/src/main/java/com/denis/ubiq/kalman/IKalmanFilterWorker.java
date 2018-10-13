package com.denis.ubiq.kalman;

public interface IKalmanFilterWorker {
    void requestLocationUpdates();
    void registerSensorListeners();
    void unregisterListeners();
    void start();
    void stop();
}
