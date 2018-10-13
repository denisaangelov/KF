package com.denis.ubiq.orientation;

import java.util.*;

import android.hardware.*;
import android.os.Message;

import com.denis.ubiq.MapActivity;

import static android.content.Context.SENSOR_SERVICE;
import static android.hardware.Sensor.*;
import static android.hardware.SensorManager.SENSOR_DELAY_FASTEST;
import static com.denis.ubiq.utils.CalculationUtils.*;

public class OrientationWorker extends TimerTask implements SensorEventListener {

    private static final int TIME_CONSTANT = 30;
    private static final float FILTER_COEFFICIENT = 0.98f;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float EPSILON = 0.000000001f;

    private final MapActivity mapActivity;
    private final SensorManager sensorManager;

    private float[] gyro = new float[3];
    private float[] gyroMatrix = new float[] { 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.1F };
    private float[] gyroOrientation = new float[] { 0.0F, 0.0F, 0.0F };
    private float[] acceleration = new float[4];
    private float[] magneticField = new float[4];
    private float[] accMagOrientation = new float[3];
    private float[] accMagRotationMatrix = new float[9];
    private float[] fusedOrientation = new float[4];
    private float timestamp;
    private boolean initState = true;

    public OrientationWorker( MapActivity mapActivity ) {
        this.mapActivity = mapActivity;
        this.sensorManager = ( SensorManager ) mapActivity.getSystemService( SENSOR_SERVICE );
        registerSensorListeners();

        // wait for one second until gyroscope and magnetometer/accelerometer
        // data is initialised then schedule the complementary filter task
        new Timer().scheduleAtFixedRate( this, 1000, TIME_CONSTANT );
    }

    public void registerSensorListeners() {
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_ACCELEROMETER ), SENSOR_DELAY_FASTEST );
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_MAGNETIC_FIELD ), SENSOR_DELAY_FASTEST );
        sensorManager.registerListener( this, sensorManager.getDefaultSensor( TYPE_GYROSCOPE ), SENSOR_DELAY_FASTEST );
    }

    @Override
    public void onSensorChanged( SensorEvent event ) {
        switch( event.sensor.getType() ) {
            case TYPE_ACCELEROMETER:
                // copy new accelerometer data into acceleration array
                // then calculate new orientation
                System.arraycopy( event.values, 0, acceleration, 0, event.values.length );
                calculateAccMagOrientation();
                break;
            case TYPE_MAGNETIC_FIELD:
                // copy new magnetometer data into magnet array
                System.arraycopy( event.values, 0, magneticField, 0, event.values.length );
                break;
            case TYPE_GYROSCOPE:
                processGyroData( event );
                break;
        }
    }

    @Override
    public void onAccuracyChanged( Sensor sensor, int i ) {

    }

    private void calculateAccMagOrientation() {
        if( SensorManager.getRotationMatrix( accMagRotationMatrix, null, acceleration, magneticField ) ) {
            SensorManager.getOrientation( accMagRotationMatrix, accMagOrientation );
        }
    }

    private void processGyroData( SensorEvent event ) {
        // don't start until first accelerometer/magnetometer orientation has been acquired
        if( accMagOrientation == null ) {
            return;
        }

        // initialisation of the gyroscope based rotation matrix
        if( initState ) {
            float[] initMatrix = getRotationMatrixFromOrientation( accMagOrientation );
            float[] test = new float[3];
            SensorManager.getOrientation( initMatrix, test );
            gyroMatrix = matrixMultiplication( gyroMatrix, initMatrix );
            initState = false;
        }

        // copy the new gyro values into the gyro array
        // convert the raw gyro data into a rotation vector
        float[] deltaVector = new float[4];
        if( timestamp != 0 ) {
            final float dT = nano2sec( event.timestamp - timestamp );
            System.arraycopy( event.values, 0, gyro, 0, 3 );
            getRotationVectorFromGyro( gyro, deltaVector, dT / 2.0f );
        }

        // measurement done, save current time for next interval
        timestamp = event.timestamp;

        // convert rotation vector into rotation matrix
        float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector( deltaMatrix, deltaVector );

        // apply the new rotation interval on the gyroscope based rotation matrix
        gyroMatrix = matrixMultiplication( gyroMatrix, deltaMatrix );

        // get the gyroscope based orientation from the rotation matrix
        SensorManager.getOrientation( gyroMatrix, gyroOrientation );
    }

    private void getRotationVectorFromGyro( float[] gyroValues, float[] deltaRotationVector, float timeFactor ) {
        float[] normValues = new float[3];

        // Calculate the angular speed of the sample
        float omegaMagnitude = ( float ) Math.sqrt( gyroValues[0] * gyroValues[0] + gyroValues[1] * gyroValues[1] + gyroValues[2] * gyroValues[2] );

        // Normalize the rotation vector if it's big enough to get the axis
        if( omegaMagnitude > EPSILON ) {
            normValues[0] = gyroValues[0] / omegaMagnitude;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = ( float ) Math.sin( thetaOverTwo );
        float cosThetaOverTwo = ( float ) Math.cos( thetaOverTwo );
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }

    public void unregisterListeners() {
        sensorManager.unregisterListener( this );
    }

    @Override
    public void run() {
        float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;
        fusedOrientation[0] = FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0];

        fusedOrientation[1] = FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1];

        fusedOrientation[2] = FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2];

        // overwrite gyro matrix and orientation with fused orientation
        // to compensate gyro drift
        gyroMatrix = getRotationMatrixFromOrientation( fusedOrientation );
        System.arraycopy( fusedOrientation, 0, gyroOrientation, 0, 3 );

        Message message = Message.obtain();
        message.obj = fusedOrientation;
        mapActivity.orientationHandler.sendMessage( message );

    }
}
