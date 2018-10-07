package com.denis.ubiq.kalman;

import android.location.Location;
import org.apache.commons.math3.filter.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.random.*;

import com.denis.ubiq.items.*;

import static com.denis.ubiq.utils.CalculationUtils.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class KalmanFilterModel {

    private double dt = 1d;

    private double positionNoise = 10d;
    private double accelerationNoise = 0.1d;
    private double velocityNoise = 1d;

    // state transition matrix
    private RealMatrix A = new Array2DRowRealMatrix( new double[][] { { 1, 0, dt, 0 }, { 0, 1, 0, dt }, { 0, 0, 1, 0 }, { 0, 0, 0, 1 } } );

    // input transition matrix
    private RealMatrix B = new Array2DRowRealMatrix( new double[][] { { Math.pow( dt, 2d ) / 2d, 0 },
                                                                      { 0, Math.pow( dt, 2d ) / 2d },
                                                                      { dt, 0 },
                                                                      { 0, dt } } );

    // process error covariance matrix
    private RealMatrix Q = new Array2DRowRealMatrix( new double[][] { { Math.pow( dt, 4d ) / 4d, 0d, Math.pow( dt, 3d ) / 2d, 0d },
                                                                      { 0d, Math.pow( dt, 4d ) / 4d, 0d, Math.pow( dt, 3d ) / 2d },
                                                                      { Math.pow( dt, 3d ) / 2d, 0d, Math.pow( dt, 2d ), 0d },
                                                                      { 0d, Math.pow( dt, 3d ) / 2d, 0d, Math.pow( dt, 2d ) } } );

    // measurement error covariance matrix
    private RealMatrix R = new Array2DRowRealMatrix( new double[][] { { 1, 0 }, { 0, 1 } } );

    // measurement transition matrix
    private RealMatrix H = new Array2DRowRealMatrix( new double[][] { { 1d, 0d, 0d, 0d }, { 0d, 1d, 0d, 0d } } );

    // initial estimate error covariance matrix
    private RealMatrix P = new Array2DRowRealMatrix( new double[][] { { 1, 0, 0, 0 }, { 0, 1, 0, 0 }, { 0, 0, 1, 0 }, { 0, 0, 0, 1 } } );

    // state
    private RealVector x;

    // control input u = [accX, accY]
    private RealVector u = new ArrayRealVector( new double[] { 0.0, 0.0 } );

    private KalmanFilter filter;

    private RandomGenerator rand = new JDKRandomGenerator();
    private RealVector tmpPNoise = new ArrayRealVector( new double[] { Math.pow( dt, 2d ) / 2d, dt, 0, 0 } );
    private RealVector mNoise = new ArrayRealVector( 2 );

    private long lastPredictTimeStamp;
    private long steps;
    private DefaultProcessModel processModel;
    private DefaultMeasurementModel measurementModel;

    public KalmanFilterModel( Location location ) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        double xVelocity = location.getSpeed() * Math.cos( location.getBearing() );
        double yVelocity = location.getSpeed() * Math.sin( location.getBearing() );
        double positionNoise = location.getAccuracy();

        this.dt = 1;
        this.lastPredictTimeStamp = NANOSECONDS.toMillis( location.getElapsedRealtimeNanos() );

        this.x = new ArrayRealVector( new double[] { latitudeToMeters( latitude ), longitudeToMeters( longitude ), xVelocity, yVelocity } );
        this.Q = Q.scalarMultiply( Math.pow( accelerationNoise, 2 ) );
        this.P = P.scalarMultiply( positionNoise );

        this.processModel = new DefaultProcessModel( A, B, Q, x, P );

        this.H = new Array2DRowRealMatrix( new double[][] { { 1d, 0d, 0d, 0d }, { 0d, 1d, 0d, 0d } } );
        this.R = R.scalarAdd( Math.pow( positionNoise, 2 ) ); // TODO or just positionNoise

        this.measurementModel = new DefaultMeasurementModel( H, R );

        this.filter = new KalmanFilter( processModel, measurementModel );
    }

    public void updateProcessModel( long timestamp ) {
        this.dt = ( timestamp - lastPredictTimeStamp ) / 1000.0;

        this.A = new Array2DRowRealMatrix( new double[][] { { 1, 0, dt, 0 }, { 0, 1, 0, dt }, { 0, 0, 1, 0 }, { 0, 0, 0, 1 } } );
        this.B = new Array2DRowRealMatrix( new double[][] { { Math.pow( dt, 2d ) / 2d, 0 }, { 0, Math.pow( dt, 2d ) / 2d }, { dt, 0 }, { 0, dt } } );
        this.Q = new Array2DRowRealMatrix( new double[][] { { Math.pow( dt, 4d ) / 4d, 0d, Math.pow( dt, 3d ) / 2d, 0d },
                                                            { 0d, Math.pow( dt, 4d ) / 4d, 0d, Math.pow( dt, 3d ) / 2d },
                                                            { Math.pow( dt, 3d ) / 2d, 0d, Math.pow( dt, 2d ), 0d },
                                                            { 0d, Math.pow( dt, 3d ) / 2d, 0d, Math.pow( dt, 2d ) } } );
        this.Q = this.Q.scalarMultiply( Math.pow( accelerationNoise, 2 ) );

        this.processModel = new DefaultProcessModel( A, B, Q, x, P );
        this.filter.processModel = new DefaultProcessModel( A, B, Q, x, P );

        this.lastPredictTimeStamp = timestamp;
    }

    public void updateMeasurementModel( GpsItem item ) {
        this.R = new Array2DRowRealMatrix( new double[][] { { 1, 0 }, { 0, 1 } } );
        this.R = R.scalarAdd( Math.pow( item.positionNoise, 2 ) );

        this.measurementModel = new DefaultMeasurementModel( H, R );
        this.filter.measurementModel = new DefaultMeasurementModel( H, R );
    }

    public double[] predict( SensorItem item ) {
        RealVector u = new ArrayRealVector( new double[] { item.eastAcceleration, item.northAcceleration } );
        filter.predict( u );
        return filter.getStateEstimation();
    }

    public double[] correct( GpsItem item ) {
        RealVector z = new ArrayRealVector( new double[] { latitudeToMeters( item.latitude ), longitudeToMeters( item.longitude ) } );
        filter.correct( z );
        return filter.getStateEstimation();
    }
}
