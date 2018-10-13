package com.denis.ubiq.utils;

import java.io.*;

import android.os.Environment;
import android.util.Log;

import static com.denis.ubiq.utils.Constants.TAG;

public class WriteUtils {

    public static void writeToLog( String format, Object... args ) {
        String log = String.format( format, args );
        Log.i( TAG, log );

        //        WriteUtils.writeToFile( log, ( String ) args[2] );
        //        WriteUtils.writeToFile( log, "All" );
    }

    public static void writeToFile( String log, String fileName ) {
        try {
            File root = new File( Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/output" );
            if( !root.exists() ) {
                root.mkdirs();
            }

            File file = new File( root, fileName + ".txt" );
            try( BufferedWriter writer = new BufferedWriter( new FileWriter( file, true ) ) ) {
                writer.append( log );
                writer.newLine();
            }
        } catch( IOException e ) {
            e.printStackTrace();
        }
    }
}
