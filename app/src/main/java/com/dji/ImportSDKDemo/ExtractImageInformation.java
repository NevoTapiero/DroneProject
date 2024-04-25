package com.dji.ImportSDKDemo;

import android.support.media.ExifInterface;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ExtractImageInformation {

    private static final String TAG = "ExtractImageInfo";

    public static Date extractImageTime(InputStream inputStream) {
        try {
            ExifInterface exif = new ExifInterface(inputStream);
            String dateTimeString = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (dateTimeString != null) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                return format.parse(dateTimeString);
            }
        } catch (IOException | ParseException e) {
            Log.e(TAG, "Error extracting image time", e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing input stream", e);
            }
        }
        return null;
    }

  public static Map<String, Double> extractImageLocation(InputStream inputStream) {
        Map<String, Double> returnLocation = new HashMap<>();
        try {
            ExifInterface exif = new ExifInterface(inputStream);
            String altitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF);
            String altitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE);
            String latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            String latitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String longitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
            Log.d(TAG, latitude + " " + longitude + " " + latitudeRef + " " + longitudeRef + " " + altitudeRef + " " + altitude);
            double[] latLong = exif.getLatLong();
            if (latLong != null) {
                returnLocation.put("latitude", latLong[0]);
                returnLocation.put("longitude", latLong[1]);
                return returnLocation;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error extracting image location", e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing input stream", e);
            }
        }
        return null;
    }
}
