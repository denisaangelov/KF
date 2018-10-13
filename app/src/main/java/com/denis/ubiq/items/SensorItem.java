package com.denis.ubiq.items;

public class SensorItem extends TimestampItem {

    public double eastAcceleration;
    public double northAcceleration;
    public float positionNoise;

    public SensorItem( double eastAcceleration, double northAcceleration, float accuracy, long timestamp ) {
        super( timestamp );
        this.eastAcceleration = eastAcceleration;
        this.northAcceleration = northAcceleration;
        this.positionNoise = accuracy;
    }
}
