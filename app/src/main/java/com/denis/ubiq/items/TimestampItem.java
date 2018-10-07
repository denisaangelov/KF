package com.denis.ubiq.items;

import android.support.annotation.NonNull;

public class TimestampItem implements Comparable<TimestampItem> {

    public long timestamp;

    public TimestampItem( long timestamp ) {
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo( @NonNull TimestampItem o ) {
        return ( int ) ( this.timestamp - o.timestamp );
    }
}
