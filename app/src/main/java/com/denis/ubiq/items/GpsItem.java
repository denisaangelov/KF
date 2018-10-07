package com.denis.ubiq.items;

public class GpsItem extends TimestampItem {

    public double latitude;
    public double longitude;
    public double altitude;
    public double speed;
    public double course;
    public double positionNoise;

    public GpsItem( double latitude, double longitude, double altitude, double speed, double course, double positionNoise, long timestamp ) {
        super( timestamp );
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.course = course;
        this.positionNoise = positionNoise;
    }
}
