package com.denis.ubiq.items;

import java.util.*;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimestampItemTest {

    @Test
    public void priorityQueueTest() {
        SensorItem sensorItem1 = new SensorItem( 0.0D, 0.0D, 1 );
        SensorItem sensorItem2 = new SensorItem( 0.0D, 0.0D, 3 );
        SensorItem sensorItem3 = new SensorItem( 0.0D, 0.0D, 6 );
        GpsItem gpsItem1 = new GpsItem( 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 2 );
        GpsItem gpsItem2 = new GpsItem( 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 4 );
        GpsItem gpsItem3 = new GpsItem( 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 5 );

        Queue<TimestampItem> queue = new PriorityQueue<>();
        queue.add( sensorItem1 );
        queue.add( sensorItem2 );
        queue.add( sensorItem3 );

        queue.add( gpsItem1 );
        queue.add( gpsItem2 );
        queue.add( gpsItem3 );

        assertEquals( sensorItem1, queue.poll() );
        assertEquals( gpsItem1, queue.poll() );
        assertEquals( sensorItem2, queue.poll() );
        assertEquals( gpsItem2, queue.poll() );
        assertEquals( gpsItem3, queue.poll() );
        assertEquals( sensorItem3, queue.poll() );

        assertEquals( 0, queue.size() );
    }
}