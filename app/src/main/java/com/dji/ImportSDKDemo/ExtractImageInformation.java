package com.dji.ImportSDKDemo;

import android.support.media.ExifInterface;
import android.util.Log;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;

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

  /*public static Map<String, Double> extractImageLocation(InputStream inputStream) {
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
    }*/


    public static Map<String, Double> extractImageLocation(InputStream inputStream) {
        Map<String, Double> returnLocation = new HashMap<>();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null && gpsDirectory.getGeoLocation() != null) {
                returnLocation.put("latitude", gpsDirectory.getGeoLocation().getLatitude());
                returnLocation.put("longitude", gpsDirectory.getGeoLocation().getLongitude());
                return returnLocation;
            }
        } catch (Exception e) {
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

    /*public static Map<String, Double> extractImageLocation(InputStream inputStream) {
        Map<String, Double> returnLocation = new HashMap<>();
        try {
            ExifInterface exif = new ExifInterface(inputStream);
            // Retrieve altitude data
            double altitude = exif.getAttributeDouble(ExifInterface.TAG_GPS_ALTITUDE, Double.NaN);
            String altitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF);

            // Retrieve latitude and longitude data
            double latitude = exif.getAttributeDouble(ExifInterface.TAG_GPS_LATITUDE, Double.NaN);
            String latitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            double longitude = exif.getAttributeDouble(ExifInterface.TAG_GPS_LONGITUDE, Double.NaN);
            String longitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);

            Log.d(TAG, "Altitude: " + altitude + " " + altitudeRef);
            Log.d(TAG, "Latitude: " + latitude + " " + latitudeRef);
            Log.d(TAG, "Longitude: " + longitude + " " + longitudeRef);

            // Check if latitude and longitude are valid
            if (!Double.isNaN(latitude) && !Double.isNaN(longitude)) {
                // Convert latitude and longitude to degrees if necessary
                if ("S".equals(latitudeRef)) {
                    latitude = -latitude;
                }
                if ("W".equals(longitudeRef)) {
                    longitude = -longitude;
                }
                returnLocation.put("latitude", latitude);
                returnLocation.put("longitude", longitude);
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
    }*/

}
