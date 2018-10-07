package com.denis.ubiq.items;

public class SensorItem extends TimestampItem {

    public double eastAcceleration;
    public double northAcceleration;

    public SensorItem( double eastAcceleration, double northAcceleration, long timestamp ) {
        super( timestamp );
        this.eastAcceleration = eastAcceleration;
        this.northAcceleration = northAcceleration;
    }
}
