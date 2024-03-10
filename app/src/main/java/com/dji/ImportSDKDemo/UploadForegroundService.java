package com.dji.ImportSDKDemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.media.ExifInterface;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJICameraError;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;


public class UploadForegroundService extends Service {
    public static final String ACTION_START_LOADING = "com.example.action.START_LOADING";
    public static final String ACTION_STOP_LOADING = "com.example.action.STOP_LOADING";
    public static final String ACTION_NOTIFY_TRIES = "com.example.action.NOTIFY_TRIES";
    public static final String ACTION_START_LOADING_PER_IMAGE = "com.example.action.ACTION_START_LOADING_PER_IMAGE";
    public static final String ACTION_STOP_LOADING_PER_IMAGE = "com.example.action.ACTION_STOP_LOADING_PER_IMAGE";
    private List<MediaFile> mediaFileList;
    private final MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;

    private int successfulUploads = 0; // Counter for successful uploads
    int totalFiles;
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private File destDir; // Declare here, but don't initialize yet
    private NotificationManager notificationManager;
    private File cacheDir;
    private MediaManager mMediaManager;
    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();

        cacheDir = new File(getExternalFilesDir(null), "DJI/com.dji.ImportSDKDemo/CACHE_IMAGE");

        // Initialize destDir here, where the context is ready
        destDir = new File(getExternalFilesDir(null), "Pictures");
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
        getFileList();
        //initMediaManager();

        return START_NOT_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }




    private void getFileList() {
        mMediaManager = Objects.requireNonNull(CameraHandler.getCameraInstance()).getMediaManager();
        if (null != mMediaManager) {
            // Add the file list state listener
            mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);

            // Set camera mode to MEDIA_DOWNLOAD before fetching file list
            CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, error -> {
                if (error == null) {
                    // Camera mode is set, now proceed to refresh file list
                    refreshMediaFileList();
                } else {
                    // Handle error setting camera mode
                    handleMediaManagerError("Set cameraMode failed: " + error.getDescription());
                }
            });
        } else {
            handleMediaManagerError("Drone Disconnected, please reconnect the drone and try again");
        }
    }


    private void refreshMediaFileList() {
        // Proceed to refresh the file list
        mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, djiError -> {

            // Successfully refreshed the media list

            if (null == djiError) {
                // Clear previous list if the current state is not INCOMPLETE
                if (currentFileListState != MediaManager.FileListState.INCOMPLETE) {
                    mediaFileList.clear();
                }

                // Fetch the updated list
                mediaFileList = mMediaManager.getSDCardFileListSnapshot();

                // Sort media files by creation time in descending order
                Objects.requireNonNull(mediaFileList).sort((lhs, rhs) -> Long.compare(rhs.getTimeCreated(), lhs.getTimeCreated()));

                ensureFilesAreReady(mediaFileList);


            } else {
                // Failed to refresh the media list
                Intent stopLoadingIntent = new Intent(ACTION_STOP_LOADING);
                stopLoadingIntent.putExtra("message", "Failed to refresh media list");
                sendBroadcast(stopLoadingIntent);
                stopSelf(); // Call this to stop the service
            }
        });
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



    private void ensureFilesAreReady(List<MediaFile> mediaFiles) {
        final int maxAttempts = 10; // Max number of attempts to check file readiness
        final long waitTimeMillis = 1000; // Wait time between checks, e.g., 4 seconds

        boolean allFilesReady = false;
        int attemptCounter = 0;

        while (!allFilesReady && attemptCounter < maxAttempts) {
            allFilesReady = true; // Assume all files are ready, and verify in the loop below.

            // Check each file in the list to see if it's ready (e.g., fileSize > 0)
            for (MediaFile mediaFile : mediaFiles) {
                if (mediaFile.getFileSize() <= 0) {
                    allFilesReady = false; // Found a file that's not ready.
                    break; // No need to check further files this round.
                }
            }

            if (!allFilesReady) {
                try {
                    Thread.sleep(waitTimeMillis); // Wait before checking again.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // Exit if the thread was interrupted during sleep.
                }

                attemptCounter++;
                Intent notifyTriesIntent = new Intent(ACTION_NOTIFY_TRIES);
                notifyTriesIntent.putExtra("message", "Media file list incomplete. Attempt #" + attemptCounter);
                sendBroadcast(notifyTriesIntent);
            }
        }

        if (allFilesReady) {
            Intent notifyTriesIntent = new Intent(ACTION_NOTIFY_TRIES);
            notifyTriesIntent.putExtra("message", "Media file list synchronized. Attempt #" + attemptCounter);
            sendBroadcast(notifyTriesIntent);
            // Proceed with processing the files as they are all ready.
            downloadAllImageFiles(mediaFiles);
        } else {
            // Handle the scenario where not all files are ready after the maximum attempts.
            Intent stopLoadingIntent = new Intent(ACTION_STOP_LOADING);
            stopLoadingIntent.putExtra("message", "Media file list incomplete.");
            sendBroadcast(stopLoadingIntent);
            stopSelf(); // Call this to stop the service
        }
    }




    private void downloadAllImageFiles(List<MediaFile> mediaFileList) {
        if (mediaFileList != null) {
            // Total number of files to download
            totalFiles = mediaFileList.size();
            for (MediaFile mediaFile : mediaFileList) {
                //if ((mediaFile.getMediaType() != MediaFile.MediaType.PANORAMA) && (mediaFile.getMediaType() != MediaFile.MediaType.SHALLOW_FOCUS)) {
                    if (mediaFile.getMediaType() == MediaFile.MediaType.JPEG) { // Ensure it's an image file
                        downloadMediaFile(mediaFile);
                    }
                //}
            }

        } else {
            //no files
            Intent stopLoadingIntent = new Intent(ACTION_STOP_LOADING);
            stopLoadingIntent.putExtra("message", "No files to download");
            sendBroadcast(stopLoadingIntent);
            stopSelf(); // Call this to stop the service
        }

    }


    private void downloadMediaFile(MediaFile mediaFile) {
        if (mediaFile != null && mediaFile.getMediaType() == MediaFile.MediaType.JPEG) { // Check if it's an image
            List<MediaFile> mediaFileListToDelete = new ArrayList<>();
            mediaFileListToDelete.add(mediaFile);
        mediaFile.fetchFileData(destDir, mediaFile.getFileName(), new DownloadListener<String>() {
                @Override
                public void onStart() {
                    Intent startLoadingIntentPerImage = new Intent(ACTION_START_LOADING_PER_IMAGE);
                    sendBroadcast(startLoadingIntentPerImage);

                }

                @Override
                public void onRateUpdate(long l, long l1, long l2) {
                    int progress = (int) (1.0 * l1 / l * 100);
                    updateProgressPerImage(progress);
                }

                @Override
                public void onRealtimeDataUpdate(byte[] bytes, long l, boolean b) {
                }

                @Override
                public void onProgress(long total, long current) {
                    //updateProgressPerImage((int) ((current * 100) / total));
                }

                @Override
                public void onSuccess(String filePath) {
                    Intent stopLoadingIntentPerImage = new Intent(ACTION_STOP_LOADING_PER_IMAGE);
                    sendBroadcast(stopLoadingIntentPerImage);
                    // Upload to Firebase
                    if (Objects.requireNonNull(destDir.listFiles()).length > 0) {
                        uploadAllImageFiles(destDir, mediaFileListToDelete);
                    }
                    if (Objects.requireNonNull(cacheDir.listFiles()).length > 0){
                        moveFilesLocation();
                    }
                }

                @Override
                public void onFailure(DJIError error) {
                    //updateNotification("Download Failed:" + error.getDescription());
                }
            });
        }
    }




    private void uploadAllImageFiles(File destDir, List<MediaFile> fileToDelete) {
        File[] files = destDir.listFiles();
        String imagePath;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".jpg")) {
                    upLoadImages(Uri.fromFile(file), fileToDelete, imagePath = file.getPath(), file);
                }
            }
        }
    }

    private void moveFilesLocation() {
        File[] files = destDir.listFiles();
        File newFile;
        if (files != null) {
            for (File file : files) {
                newFile = new File(destDir, file.getName());
                if (file.renameTo(newFile)) {
                    if(file.delete()){
                        //updateNotification("File Moved:" + file.getName());
                    }
                }
            }
        }
    }

    private void upLoadImages(Uri imageUri, List<MediaFile> fileList, String imagePath, File file) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String imageName = UUID.randomUUID().toString() + ".jpg";
        StorageReference imageRef = storageRef.child("unclassified/" + imageName);

        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    if (ContextCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                     Map<String, Object> docData = new HashMap<>();
                    docData.put("imageUrl", downloadUri.toString());
                    docData.put("imageName", imageName);
                    docData.put("timestamp", extractImageTime(imagePath));

                    Map<String, Double> returnLocation = extractImageLocation(file);
                    if (returnLocation != null) {
                        docData.put("latitude", returnLocation.get("latitude"));
                        docData.put("longitude", returnLocation.get("longitude"));
                    }

                    FirebaseFirestore.getInstance().collection("unclassified").add(docData)
                            .addOnSuccessListener(documentReference -> {
                                // Attempt to delete the image file after successful upload and Firestore document creation
                                try {
                                    deleteImageFromDrone(fileList);
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


                })
                        .addOnFailureListener(e -> {}/*updateNotification("Image upload failed: " + e.getMessage())*/));

        updateSuccessfulUploads();
    }

    private void updateSuccessfulUploads(){
        // After each file download completion
        successfulUploads++; // or increase another counter if download fails
        int progress = successfulUploads;
        updateProgress(progress);


        if (successfulUploads == totalFiles) {
            // All files have been uploaded
            Intent stopLoadingIntent = new Intent(ACTION_STOP_LOADING);
            stopLoadingIntent.putExtra("message", "All files uploaded successfully");
            sendBroadcast(stopLoadingIntent);
            stopSelf(); // Call this to stop the service
        }
    }


    private Map<String, Double> extractImageLocation(File imageFile) {
        Map<String, Double> returnLocation = new HashMap<>();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);

            if (gpsDirectory != null) {
                // Check if GPS info exists
                if (gpsDirectory.containsTag(GpsDirectory.TAG_LATITUDE) && gpsDirectory.containsTag(GpsDirectory.TAG_LONGITUDE)) {
                    // Retrieve the latitude and longitude values (might require conversion)
                    double latitude = gpsDirectory.getGeoLocation().getLatitude();
                    double longitude = gpsDirectory.getGeoLocation().getLongitude();
                    returnLocation.put("latitude", latitude);
                    returnLocation.put("longitude", longitude);

                    return returnLocation;

                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }


    /**
     * Extracts the capture time of an image.
     * @param imagePath The path to the image file.
     * @return The capture time of the image, or null if not available.
     */
    private Date extractImageTime(String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            String dateTimeString = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (dateTimeString != null) {
                // Parse the date time string according to the format
                SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                return format.parse(dateTimeString);
            }
        } catch (IOException e) {
            //
        } catch (Exception e) { // Including parsing exceptions
            //
        }
        return null;
    }


    private void deleteImageFromDrone(List<MediaFile> mediaFileList) {
        mMediaManager.deleteFiles(mediaFileList, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {
            @Override
            public void onSuccess(List<MediaFile> x, DJICameraError y) {
                //updateNotification("Image deleted from device");
            }

            @Override
            public void onFailure(DJIError error) {
                //updateNotification("Error deleting image: " + error.getDescription());
            }
        });
    }

    private void handleMediaManagerError(String message) {
        Intent stopLoadingIntent = new Intent(ACTION_STOP_LOADING);
        stopLoadingIntent.putExtra("message", message);
        sendBroadcast(stopLoadingIntent);
        stopSelf(); // Call this to stop the service
    }

    private void updateProgress(int progress) {
        Intent progressUpdateBar = new Intent("ACTION_PROGRESS_UPDATE");
        progressUpdateBar.putExtra("message", "files uploaded: " + successfulUploads + " of " + totalFiles);
        progressUpdateBar.putExtra("progress", progress);
        sendBroadcast(progressUpdateBar);
    }

    private void updateProgressPerImage(int progress) {
        Intent progressUpdatePerImage = new Intent("ACTION_PROGRESS_UPDATE_PER_IMAGE");
        progressUpdatePerImage.putExtra("progress", progress);
        sendBroadcast(progressUpdatePerImage);
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



