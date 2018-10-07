 package com.denis.ubiq.kalman;

 import org.apache.commons.math3.filter.*;
 import org.apache.commons.math3.linear.*;
 import org.apache.commons.math3.random.*;

 public class KalmanExample {

 	// discrete time interval (100ms) between to steps
 	private final double dt = 0.1d;
 	// position measurement noise (10 meter)
 	private final double measurementNoise = 10d;
 	// acceleration noise (meter/sec^2)
 	private final double accelNoise = 0.2d;
 	//A - state transition matrix
 	private RealMatrix A;
 	//B - control input matrix
 	private RealMatrix B;
 	//H - measurement matrix
 	private RealMatrix H;
 	//Q - process noise covariance matrix (error in the process)
 	private RealMatrix Q;
 	//R - measurement noise covariance matrix (error in the measurement)
 	private RealMatrix R;
 	//PO - error covariance matrix
 	private RealMatrix PO;
 	//x state
 	private RealVector x;
 	// constant control input, increase velocity by 0.1 m/s per cycle [vX, vY]
 	private RealVector u = new ArrayRealVector(new double[]{ 0.1d, 0.1d });
 	private RealVector tmpPNoise = new ArrayRealVector(new double[]{ Math.pow(dt, 2d) / 2d, dt });
 	private RealVector mNoise = new ArrayRealVector(1);
 	private KalmanFilter filter;

 	public KalmanExample() {
 		//A and B describe the physic model of the user moving specified as matrices
 		A = new Array2DRowRealMatrix(new double[][]{ { 1d, 0d, dt, 0d },
                                                     { 0d, 1d, 0d, dt },
                                                     { 0d, 0d, 1d, 0d },
                                                     { 0d, 0d, 0d, 1d } });

 		B = new Array2DRowRealMatrix(new double[][]{ { Math.pow(dt, 2d) / 2d },
                                                     { Math.pow(dt, 2d) / 2d },
                                                     { dt },
                                                     { dt } });

 		//only observe first 2 values - the position coordinates
 		H = new Array2DRowRealMatrix(new double[][]{ { 1d, 0d, 0d, 0d },
                                                     { 0d, 1d, 0d, 0d }, });

 		Q = new Array2DRowRealMatrix(new double[][]{ { Math.pow(dt, 4d) / 4d, 0d, Math.pow(dt, 3d) / 2d, 0d },
 													 { 0d, Math.pow(dt, 4d) / 4d, 0d, Math.pow(dt, 3d) / 2d },
 													 { Math.pow(dt, 3d) / 2d, 0d, Math.pow(dt, 2d), 0d },
 													 { 0d, Math.pow(dt, 3d) / 2d, 0d, Math.pow(dt, 2d) } });

 		R = new Array2DRowRealMatrix(new double[][]{ { Math.pow(measurementNoise, 2d), 0d },
                                                     { 0d, Math.pow(measurementNoise, 2d) } });

         /*PO = new Array2DRowRealMatrix(new double[][] {
 														{ 1d, 1d, 1d, 1d },
                                                         { 1d, 1d, 1d, 1d },
                                                         { 1d, 1d, 1d, 1d },
                                                         { 1d, 1d, 1d, 1d }
                                                      });*/

 		// x = [ 0 0 0 0] state consists of position and velocity[pX, pY, vX, vY]
 		//TODO: inititate with map center?
 		x = new ArrayRealVector(new double[]{ 0, 0, 0, 0 });

 		ProcessModel pm = new DefaultProcessModel(A, B, Q, x, PO);
 		MeasurementModel mm = new DefaultMeasurementModel(H, R);
 		filter = new KalmanFilter(pm, mm);
 	}

 	/**
 	 * Use Kalmanfilter to decrease measurement errors
 	 *
 	 * @param position
 	 * @return
 	 */
 	public void estimate(double[] position) {
 		RandomGenerator rand = new JDKRandomGenerator();

 		// predict the state estimate one time-step ahead
 		filter.predict(u);

 		// noise of the process
 		RealVector pNoise = tmpPNoise.mapMultiply(accelNoise * position[0]);

 		// x = A * x + B * u + pNoise (state prediction)
 		x = A.operate(x).add(B.operate(u)).add(pNoise);

 		// noise of the measurement
 		mNoise.setEntry(0, measurementNoise * rand.nextGaussian());

 		// z = H * x + m_noise (measurement prediction)
 		RealVector z = H.operate(x).add(mNoise);

 		// correct the state estimate with the latest measurement
 		filter.correct(z);

 		//get the corrected state - the position
 		double pX = filter.getStateEstimation()[0];
 		double pY = filter.getStateEstimation()[1];
 	}

 	public void a() {
 		// discrete time interval
 		double T = 0.1d;
 		// position measurement noise (meter)
 		double measurementNoise = 10d;
 		// acceleration noise (meter/sec^2)
 		double accelerationNoise = 0.2d;

 		// A = [ 1 T ]
 		//     [ 0 1 ]
 		RealMatrix A = new Array2DRowRealMatrix(new double[][]{ { 1, T }, { 0, 1 } });
 		// B = [ T^2/2 ]
 		//     [ T     ]
 		RealMatrix B = new Array2DRowRealMatrix(new double[][]{ { Math.pow(T, 2d) / 2d }, { T } });
 		// H = [ 1 0 ]
 		RealMatrix H = new Array2DRowRealMatrix(new double[][]{ { 1d, 0d } });
 		// x = [ 0 0 ]
 		RealVector x = new ArrayRealVector(new double[]{ 0, 0 });

 		RealMatrix tmp = new Array2DRowRealMatrix(new double[][]{ { Math.pow(T, 4d) / 4d, Math.pow(T, 3d) / 2d },
 																  { Math.pow(T, 3d) / 2d, Math.pow(T, 2d) } });
 		// Q = [ T^4/4 T^3/2 ]
 		//     [ T^3/2 T^2   ]
 		RealMatrix Q = tmp.scalarMultiply(Math.pow(accelerationNoise, 2));
 		// P0 = [ 1 1 ]
 		//      [ 1 1 ]
 		RealMatrix P0 = new Array2DRowRealMatrix(new double[][]{ { 1, 1 }, { 1, 1 } });
 		// R = [ measurementNoise^2 ]
 		RealMatrix R = new Array2DRowRealMatrix(new double[]{ Math.pow(measurementNoise, 2) });

 		// constant control input, increase velocity by 0.1 m/s per cycle
 		RealVector u = new ArrayRealVector(new double[]{ 0.1d });

 		ProcessModel pm = new DefaultProcessModel(A, B, Q, x, P0);
 		MeasurementModel mm = new DefaultMeasurementModel(H, R);
 		KalmanFilter filter = new KalmanFilter(pm, mm);

 		RandomGenerator rand = new JDKRandomGenerator();

 		RealVector tmpPNoise = new ArrayRealVector(new double[]{ Math.pow(T, 2d) / 2d, T });
 		RealVector mNoise = new ArrayRealVector(1);

 		// iterate 60 steps
 		for (int i = 0; i < 60; i++) {
 			filter.predict(u);

 			// simulate the process
 			RealVector pNoise = tmpPNoise.mapMultiply(accelerationNoise * rand.nextGaussian());

 			// x = A * x + B * u + pNoise
 			x = A.operate(x).add(B.operate(u)).add(pNoise);

 			// simulate the measurement
 			mNoise.setEntry(0, measurementNoise * rand.nextGaussian());

 			// z = H * x + m_noise
 			RealVector z = H.operate(x).add(mNoise);

 			filter.correct(z);

 			double position = filter.getStateEstimation()[0];
 			double velocity = filter.getStateEstimation()[1];
 		}
 	}
 }
