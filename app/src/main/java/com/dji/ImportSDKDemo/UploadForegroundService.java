package com.dji.ImportSDKDemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import dji.common.error.DJIError;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;


public class UploadForegroundService extends Service {
    public static final String ACTION_START_LOADING = "com.example.action.START_LOADING";
    public static final String ACTION_STOP_LOADING = "com.example.action.STOP_LOADING";

    private int totalFiles = 0; // Total number of files to download
    private int successfulUploads = 0; // Counter for successful uploads

    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private FusedLocationProviderClient fusedLocationClient;
    private File destDir; // Declare here, but don't initialize yet
    private NotificationManager notificationManager;
    // Initialize cacheDir here, where the context is ready
    private File cacheDir;
    @Override
    public void onCreate() {
        super.onCreate();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.getApplicationContext());
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
        // Initialize destDir here, where the context is ready
        destDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MediaManagerDemo");
        cacheDir = new File(getExternalFilesDir(null), "DJI/com.dji.ImportSDKDemo/CACHE_IMAGE");
        if (!destDir.exists()) {
            boolean success = destDir.mkdirs(); // Create the directory if it doesn't exist
            if (success) {
                //updateNotification("Created destDir:" + destDir.getAbsolutePath());
            } else;
                //updateNotification("Directory Creation" + "Failed to create directory:" + destDir.getAbsolutePath());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent startLoadingIntent = new Intent(ACTION_START_LOADING);
        sendBroadcast(startLoadingIntent);
        // Call updateNotification at the beginning to setup foreground service
        updateNotification();
        initMediaManager();

        return START_NOT_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void initMediaManager() {
        if (CameraHandler.getProductInstance() == null) {
            //updateNotification("Drone Disconnected, please reconnect the drone and try again");
        } else {
            if (CameraHandler.getCameraInstance() != null && CameraHandler.getCameraInstance().isMediaDownloadModeSupported()) {
                MediaManager mMediaManager = CameraHandler.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    List<MediaFile> newMediaFiles = mMediaManager.getSDCardFileListSnapshot();
                    downloadAllImageFiles(newMediaFiles);
                }
            } else {
                //updateNotification("Media Download Mode not Supported");
            }
        }
    }


    private final MediaManager.FileListStateListener updateFileListStateListener = state -> {
        switch (state) {
            case SYNCING:
                // The file list is being synchronized. You might want to show a loading indicator.
                //updateNotification("Syncing media file list...");

                break;
            case INCOMPLETE:
                // The file list has not been completely synchronized.
                //updateNotification("Media file list incomplete.");

                break;
            case UP_TO_DATE:
                // The file list is complete. You can now access the full list of media files.
                //updateNotification("Media file list synchronized.");

                break;
            case DELETING:
                // Files are being deleted from the list.
                //updateNotification("Deleting media files...");

                break;
            case UNKNOWN:
            default:
                // Handle any unknown states.
                //updateNotification("Unknown media file list state.");
                break;
        }
    };

    private void downloadAllImageFiles(List<MediaFile> mediaFileList) {
        if (mediaFileList != null) {
            // Before starting downloads
            totalFiles = mediaFileList.size();
            for (MediaFile mediaFile : mediaFileList) {
                if (mediaFile.getMediaType() == MediaFile.MediaType.JPEG) { // Ensure it's an image file
                    downloadMediaFile(mediaFile);
                }
            }
            if (successfulUploads == totalFiles) {
                // All files have been uploaded
                Intent stopLoadingIntent = new Intent(ACTION_STOP_LOADING);
                sendBroadcast(stopLoadingIntent);
                stopSelf(); // Call this to stop the service
            }
        } else {
            //no files
            Intent stopLoadingIntent = new Intent(ACTION_STOP_LOADING);
            sendBroadcast(stopLoadingIntent);
            stopSelf(); // Call this to stop the service
        }

    }


    private void downloadMediaFile(MediaFile mediaFile) {
        if (mediaFile != null && mediaFile.getMediaType() == MediaFile.MediaType.JPEG) { // Check if it's an image
            mediaFile.fetchFileData(destDir, "MediaManagerDemo", new DownloadListener<String>() {
                @Override
                public void onStart() {
                }

                @Override
                public void onRateUpdate(long l, long l1, long l2) {

                }

                @Override
                public void onRealtimeDataUpdate(byte[] bytes, long l, boolean b) {

                }

                @Override
                public void onProgress(long total, long current) {
                    // Optionally update progress
                }

                @Override
                public void onSuccess(String filePath) {
                    // Upload to Firebase
                    uploadAllImageFiles(destDir);
                }

                @Override
                public void onFailure(DJIError error) {
                    //updateNotification("Download Failed:" + error.getDescription());
                }
            });
        }
    }




    private void uploadAllImageFiles(File destDir) {
        File[] files = destDir.listFiles();
        File[] cacheFiles = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".jpg")) {
                    upLoadImages(Uri.fromFile(file));
                }
            }
        }
        else if (cacheFiles != null) {
            uploadAllCacheImageFiles(cacheDir);
        }
    }

    private void uploadAllCacheImageFiles(File destDir) {
        File[] files = destDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".jpg")) {
                    upLoadImages(Uri.fromFile(file));
                }
            }
        }

    }

    private void upLoadImages(Uri imageUri) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String imageName = UUID.randomUUID().toString() + ".jpg";
        StorageReference imageRef = storageRef.child("unclassified/" + imageName);


        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    if (ContextCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                                Map<String, Object> docData = new HashMap<>();
                                docData.put("imageUrl", downloadUri.toString());
                                docData.put("imageName", imageName);
                                docData.put("timestamp", FieldValue.serverTimestamp());

                                if (location != null) {
                                    docData.put("latitude", location.getLatitude());
                                    docData.put("longitude", location.getLongitude());
                                }

                                FirebaseFirestore.getInstance().collection("unclassified").add(docData)
                                        .addOnSuccessListener(documentReference -> {
                                            // Attempt to delete the image file after successful upload and Firestore document creation
                                            try {
                                                boolean deleted = new File(Objects.requireNonNull(imageUri.getPath())).delete();
                                                if (deleted) {
                                                    //updateNotification("Image deleted from device");
                                                } else {
                                                    //updateNotification("Failed to delete image from device");
                                                }
                                            } catch (Exception e) {
                                                //updateNotification("Error deleting image: " + e.getMessage());
                                            }
                                        })
                                        .addOnFailureListener(e ->{}/* updateNotification("Error adding document: " + e.getMessage())*/);

                                // After each file download completion
                                successfulUploads++; // or increase another counter if download fails
                                int progress = successfulUploads; // Or successfulUploads + failedUploads if tracking failures
                                updateProgress(progress);

                            })
                            .addOnFailureListener(e -> {}/*updateNotification("Image upload failed: " + e.getMessage())*/);
                }));

    }

    private void updateProgress(int progress) {
        Intent progressUpdate = new Intent("ACTION_PROGRESS_UPDATE");
        progressUpdate.putExtra("progress", progress);
        sendBroadcast(progressUpdate);
    }


    private void updateNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Upload Status")
                .setContentText("Service Started")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true) // To avoid sound and vibration on every update
                .build();
        // Start foreground with the updated notification
        startForeground(1, notification);
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}



