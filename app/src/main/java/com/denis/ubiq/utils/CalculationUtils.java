package com.denis.ubiq.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.google.android.gms.maps.model.LatLng;

public class CalculationUtils {

    private static final double EARTH_RADIUS = 6371 * 1000;

    public static double longitudeToMeters( double lon ) {
        double distance = calculateDistance( 0.0, lon, 0.0, 0.0 );
        return distance * ( lon < 0.0 ? -1.0 : 1.0 );
    }

    public static double calculateDistance( double lat1, double lon1, double lat2, double lon2 ) {
        double deltaLat = Math.toRadians( lat2 - lat1 );
        double deltaLon = Math.toRadians( lon2 - lon1 );
        double a = Math.pow( Math.sin( deltaLat / 2.0 ), 2.0 ) + Math.cos( Math.toRadians( lat1 ) ) * Math.cos( Math.toRadians( lat2 ) ) * Math.pow(
            Math.sin( deltaLon / 2.0 ),
            2.0 );
        double c = 2.0 * Math.atan2( Math.sqrt( a ), Math.sqrt( 1.0 - a ) );
        return EARTH_RADIUS * c;
    }

    public static double latitudeToMeters( double lat ) {
        double distance = calculateDistance( lat, 0.0, 0.0, 0.0 );
        return distance * ( lat < 0.0 ? -1.0 : 1.0 );
    }

    public static LatLng convertMetersToLatLng( double latMeters, double lonMeters ) {
        LatLng point = new LatLng( 0.0, 0.0 );
        LatLng pointEast = pointPlusDistanceEast( point, lonMeters );
        LatLng pointNorth = pointPlusDistanceNorth( pointEast, latMeters );
        return pointNorth;
    }

    private static LatLng pointPlusDistanceEast( LatLng point, double distance ) {
        return haversineFormulaTranslation( point, distance, 90.0 );
    }

    private static LatLng pointPlusDistanceNorth( LatLng point, double distance ) {
        return haversineFormulaTranslation( point, distance, 0.0 );
    }

    private static LatLng haversineFormulaTranslation( LatLng point, double distance, double azimuthDegrees ) {
        double radiusFraction = distance / EARTH_RADIUS;
        double bearing = Math.toRadians( azimuthDegrees );
        double lat1 = Math.toRadians( point.latitude );
        double lng1 = Math.toRadians( point.longitude );

        double lat2_part1 = Math.sin( lat1 ) * Math.cos( radiusFraction );
        double lat2_part2 = Math.cos( lat1 ) * Math.sin( radiusFraction ) * Math.cos( bearing );
        double lat2 = Math.asin( lat2_part1 + lat2_part2 );

        double lng2_part1 = Math.sin( bearing ) * Math.sin( radiusFraction ) * Math.cos( lat1 );
        double lng2_part2 = Math.cos( radiusFraction ) - Math.sin( lat1 ) * Math.sin( lat2 );
        double lng2 = lng1 + Math.atan2( lng2_part1, lng2_part2 );

        lng2 = ( lng2 + 3.0 * Math.PI ) % ( 2.0 * Math.PI ) - Math.PI;

        return new LatLng( Math.toDegrees( lat2 ), Math.toDegrees( lng2 ) );
    }

    public static String getCurrentTime() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat( "HH:mm:ss.SSS" ); //.format( new Date() )
        return sdf.format( cal.getTime() );
    }

    public static int hertz2periodUs( double hz ) { return ( int ) ( 1.0e6 / ( 1.0 / hz ) );}

    public static float nano2sec( float nano ) { return ( float ) ( nano / 1e9 ); }

    public static float[] getRotationMatrixFromOrientation( float[] o ) {
        float[] xM = new float[9];
        float[] yM = new float[9];
        float[] zM = new float[9];

        float sinX = ( float ) Math.sin( o[1] );
        float cosX = ( float ) Math.cos( o[1] );
        float sinY = ( float ) Math.sin( o[2] );
        float cosY = ( float ) Math.cos( o[2] );
        float sinZ = ( float ) Math.sin( o[0] );
        float cosZ = ( float ) Math.cos( o[0] );

        // rotation about x-axis (pitch)
        xM[0] = 1.0f;
        xM[1] = 0.0f;
        xM[2] = 0.0f;
        xM[3] = 0.0f;
        xM[4] = cosX;
        xM[5] = sinX;
        xM[6] = 0.0f;
        xM[7] = -sinX;
        xM[8] = cosX;

        // rotation about y-axis (roll)
        yM[0] = cosY;
        yM[1] = 0.0f;
        yM[2] = sinY;
        yM[3] = 0.0f;
        yM[4] = 1.0f;
        yM[5] = 0.0f;
        yM[6] = -sinY;
        yM[7] = 0.0f;
        yM[8] = cosY;

        // rotation about z-axis (azimuth)
        zM[0] = cosZ;
        zM[1] = sinZ;
        zM[2] = 0.0f;
        zM[3] = -sinZ;
        zM[4] = cosZ;
        zM[5] = 0.0f;
        zM[6] = 0.0f;
        zM[7] = 0.0f;
        zM[8] = 1.0f;

        // rotation order is y, x, z (roll, pitch, azimuth)
        float[] resultMatrix = matrixMultiplication( xM, yM );
        resultMatrix = matrixMultiplication( zM, resultMatrix );
        return resultMatrix;
    }

    public static float[] matrixMultiplication( float[] A, float[] B ) {
        float[] result = new float[9];

        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

        return result;
    }
}
