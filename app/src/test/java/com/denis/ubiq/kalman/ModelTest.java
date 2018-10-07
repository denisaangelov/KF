package com.denis.ubiq.kalman;

import org.apache.commons.math3.filter.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.random.*;
import org.junit.Test;

public class ModelTest {

    // discrete time interval
    private double dt = 0.1d;
    // position measurement noise (meter)
    private double measurementNoise = 10d;
    // acceleration noise (meter/sec^2)
    private double accelerationNoise = 0.2d;

    // A = [ 1 dt ]
    //     [ 0  1 ]
    private RealMatrix A = new Array2DRowRealMatrix( new double[][] { { 1, dt },
                                                                      { 0, 1 } } );
    // B = [ dt^2/2 ]
    //     [ dt     ]
    private RealMatrix B = new Array2DRowRealMatrix( new double[][] { { Math.pow( dt, 2d ) / 2d },
                                                                      { dt } } );
    // H = [ 1 0 ]
    private RealMatrix H = new Array2DRowRealMatrix( new double[][] { { 1d, 0d } } );
    // x = [ 0 0 ]
    private RealVector x = new ArrayRealVector( new double[] { 0, 0 } );

    private RealMatrix tmp = new Array2DRowRealMatrix( new double[][] { { Math.pow( dt, 4d ) / 4d, Math.pow( dt, 3d ) / 2d },
                                                                        { Math.pow( dt, 3d ) / 2d, Math.pow( dt, 2d ) } } );
    // Q = [ dt^4/4 dt^3/2 ]
    //     [ dt^3/2 dt^2   ]
    private RealMatrix Q = tmp.scalarMultiply( Math.pow( accelerationNoise, 2 ) );
    // P0 = [ 1 1 ]
    //      [ 1 1 ]
    private RealMatrix P0 = new Array2DRowRealMatrix( new double[][] { { 1, 1 }, { 1, 1 } } );
    // R = [ measurementNoise^2 ]
    private RealMatrix R = new Array2DRowRealMatrix( new double[] { Math.pow( measurementNoise, 2 ) } );

    // constant control input, increase velocity by 0.1 m/s per cycle
    private RealVector u = new ArrayRealVector( new double[] { 0.1d } );

    private ProcessModel pm = new DefaultProcessModel( A, B, Q, x, P0 );
    private MeasurementModel mm = new DefaultMeasurementModel( H, R );
    private KalmanFilter filter = new KalmanFilter( pm, mm );

    private RandomGenerator rand = new JDKRandomGenerator();

    private RealVector tmpPNoise = new ArrayRealVector( new double[] { Math.pow( dt, 2d ) / 2d, dt } );
    private RealVector mNoise = new ArrayRealVector( 1 );

    public static float distFrom( float lat1, float lng1, float lat2, float lng2 ) {
        double earthRadius = 6371000; //meters
        double dLat = Math.toRadians( lat2 - lat1 );
        double dLng = Math.toRadians( lng2 - lng1 );
        double a = Math.sin( dLat / 2 ) * Math.sin( dLat / 2 )
                   + Math.cos( Math.toRadians( lat1 ) ) * Math.cos( Math.toRadians( lat2 ) ) * Math.sin(dLng / 2 ) * Math.sin( dLng / 2 );
        double c = 2 * Math.atan2( Math.sqrt( a ), Math.sqrt( 1 - a ) );
        float dist = ( float ) ( earthRadius * c );

        return dist;
    }

    @Test
    public void distFrom_test() {
        float lat1= 42.697708F;
        float long1= 23.321867F;
        float lat2=42.66685153F;
        float long2=23.34731124F;
        System.out.println(distFrom(lat1,long1,lat2,long2));
    }
    @Test
    public void kalmanFilter_algorithm_test() throws Exception {
        for( int i = 0; i < 60; i++ ) {
            filter.predict( u );

            // simulate the process
            RealVector pNoise = tmpPNoise.mapMultiply( accelerationNoise * rand.nextGaussian() );

            // x = A * x + B * u + pNoise
            x = A.operate( x ).add( B.operate( u ) ).add( pNoise );

            // simulate the measurement
            mNoise.setEntry( 0, measurementNoise * rand.nextGaussian() );

            // z = H * x + m_noise
            RealVector z = H.operate( x ).add( mNoise );

            filter.correct( z );

            double positionX = filter.getStateEstimation()[0];
            double positionY = filter.getStateEstimation()[1];
//            double velocityX = filter.getStateEstimation()[2];
//            double velocityY = filter.getStateEstimation()[3];
//            System.out.println( String.format( "position: [%s] [%s], velocity: [%s] [%s]", positionX ,velocityX ));
        }
    }
}
