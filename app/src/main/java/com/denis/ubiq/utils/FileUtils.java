package com.denis.ubiq.utils;

import java.io.*;

import android.os.Environment;

public class FileUtils {

    private static void writeToFile( StringBuilder sb, String fileName ) throws IOException {
        File root = new File( Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/output" );
        if( !root.exists() ) {
            root.mkdirs();
        }

        File file = new File( root, fileName + ".txt" );
        try( BufferedWriter writer = new BufferedWriter( new FileWriter( file, true ) ) ) {
            writer.append( sb.toString() );
        }
    }

}
