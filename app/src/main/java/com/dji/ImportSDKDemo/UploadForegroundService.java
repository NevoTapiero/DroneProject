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
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
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
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    Boolean fromOnDestroy = false;

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
                showToastService("Created destDir:" + destDir.getAbsolutePath());
            } else
                showToastService("Failed to create directory:" + destDir.getAbsolutePath());
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


    public static String generateBatchId() {
        // Define the date format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());

        // Get the current date and time
        Date now = new Date();

        // Format the current date and time according to the specified format

        return dateFormat.format(now);
    }

    private void initMediaManager() {
        BaseProduct product = CameraHandler.getProductInstance();
        if (product != null) {
            setCameraMode();
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
                    if (mediaFileList != null) {
                        mediaFileList.clear();
                    }
                }
                // Fetch the updated list
                mediaFileList = mMediaManager.getSDCardFileListSnapshot();

                // Sort media files by creation time in descending order
                if (mediaFileList != null) {
                    mediaFileList.sort((lhs, rhs) -> Long.compare(rhs.getTimeCreated(), lhs.getTimeCreated()));
                }
                ensureFilesAreReady(mediaFileList);


            } else {
                // Failed to refresh the media list
                showToastService("Failed to refresh the media list: " + djiError.getDescription() + "retrying...");
                refreshMediaFileList();
            }
        });
    }

    private final MediaManager.FileListStateListener updateFileListStateListener = state -> {
        switch (state) {
            case SYNCING:
                // The file list is being synchronized.
                currentFileListState = state;
                showToastService("File list is being synchronized");

                break;
            case INCOMPLETE:
                // The file list has not been completely synchronized.
                currentFileListState = state;
                mediaFileList.clear();
                showToastService("File list is not complete");

                break;
            case UP_TO_DATE:
                // The file list is complete. You can now access the full list of media files.
                currentFileListState = state;
                showToastService("File list is complete");
                continueToSetCameraMode();

                break;
            case DELETING:
                // Files are being deleted from the list.
                currentFileListState = state;
                showToastService("Files are being deleted from the list");

                break;
            case UNKNOWN:
            default:
                // Handle any unknown states.
                currentFileListState = state;
                showToastService("Unknown state: " + state);

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
            handleMediaManagerError( "Media file list incomplete.");
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
            handleMediaManagerError( "No files to download");

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
                    showToastService("Download Failed:" + error.getDescription());
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
                    imagePath = file.getPath();
                    upLoadImages(Uri.fromFile(file), fileToDelete, imagePath, file);
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
                        showToastService("File Moved:" + file.getName());
                    }
                }
            }
        }
    }

    private void upLoadImages(Uri imageUri, List<MediaFile> fileList, String imagePath, File file) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String imageName = UUID.randomUUID().toString() + ".jpg";
        StorageReference imageRef = storageRef.child("unclassified/" + imageName);
        String batchId =  generateBatchId();

        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    if (ContextCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                     Map<String, Object> docData = new HashMap<>();
                    docData.put("imageUrl", downloadUri.toString());
                    docData.put("imageName", imageName);
                    Date timestamp = extractImageTime(imagePath);

                    if (timestamp != null) {
                        docData.put("timestamp", timestamp);
                    }

                    Map<String, Double> returnLocation = extractImageLocation(file);
                    if (returnLocation != null) {
                        docData.put("latitude", returnLocation.get("latitude"));
                        docData.put("longitude", returnLocation.get("longitude"));
                    }



                    FirebaseFirestore.getInstance().collection("unclassified").document(batchId).set(docData)
                            .addOnSuccessListener(documentReference -> {
                                // Attempt to delete the image file after successful upload and Firestore document creation
                                try {
                                    //deleteImageFromDrone(fileList);
                                    boolean deleted = new File(Objects.requireNonNull(imageUri.getPath())).delete();
                                    if (deleted) {
                                        showToastService("Image deleted from device");
                                    } else {
                                        showToastService("Failed to delete image from device");
                                    }
                                } catch (Exception e) {
                                    showToastService("Error deleting image: " + e.getMessage());
                                }
                            })
                            .addOnFailureListener(e -> showToastService("Error adding document: " + e.getMessage()));


                })
                        .addOnFailureListener(e -> showToastService("Image upload failed: " + e.getMessage())));

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

    private void showToastService(String message) {
        Intent toastMessage = new Intent("ACTION_SHOW_TOAST");
        toastMessage.putExtra("message", message);
        sendBroadcast(toastMessage);
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


    private void setCameraMode() {
        BaseProduct product = CameraHandler.getProductInstance();
        if (product != null) {
            if (CameraHandler.getCameraInstance() != null) {
                mMediaManager = CameraHandler.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)){
                        showToastService("Media Manager is busy.");
                    }
                } else {
                    showToastService("MediaManager is null");
                }
            }else {
                // Handle the case where the camera instance is null
                showToastService("Camera disconnected");
            }
        } else {
            // Handle the case where the product is null
            showToastService("Drone Disconnected, please reconnect the drone and try again");
        }
    }

    private void continueToSetCameraMode() {
        BaseProduct product = CameraHandler.getProductInstance();
        Model model = product.getModel();
        if (fromOnDestroy) {
            if (CameraHandler.getCameraInstance() != null) {
                CameraHandler.getCameraInstance().getMode(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.CameraMode>() {
                    @Override
                    public void onSuccess(SettingsDefinitions.CameraMode cameraMode) {
                        if (cameraMode == SettingsDefinitions.CameraMode.PLAYBACK) {
                            CameraHandler.getCameraInstance().exitPlayback(djiError -> {
                                if (djiError == null) {
                                    // Successfully exited playback mode
                                    showToastService("Exited playback mode");
                                    CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError2 -> {
                                        if (djiError2 == null) {
                                            // Successfully set to SHOOT_PHOTO mode, or choose any other mode as needed
                                            showToastService("Set to SHOOT_PHOTO mode");
                                            refreshMediaFileList();
                                        } else {
                                            // Handle the error
                                            showToastService("Set mode failed: " + djiError2.getDescription());
                                        }
                                    });
                                } else {
                                    // Handle the error
                                    showToastService("Exit playback mode failed: " + djiError.getDescription());
                                }
                            });
                        } else {
                            CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                                if (djiError == null) {
                                    // Successfully set to SHOOT_PHOTO mode, or choose any other mode as needed
                                    showToastService("Set to SHOOT_PHOTO mode");
                                    refreshMediaFileList();
                                } else {
                                    // Handle the error
                                    showToastService("Set mode failed: " + djiError.getDescription());
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(DJIError error) {
                        // Handle the error
                        showToastService("Get Mode failed: " + error.getDescription());
                    }
                });
            }
        }else {
            // Check if the product model matches any of the specified drones
            if (model == Model.MATRICE_300_RTK || model == Model.MAVIC_AIR_2 || model == Model.DJI_AIR_2S || model == Model.DJI_MINI_2) {
                if (Objects.requireNonNull(CameraHandler.getCameraInstance()).isFlatCameraModeSupported()) {
                    // For other drone models, set camera mode to FLAT
                    CameraHandler.getCameraInstance().enterPlayback(djiError -> {
                        if (djiError == null) {
                            // Successfully set to PLAYBACK mode, or choose any other mode as needed
                            showToastService("Set to PLAYBACK mode");
                            refreshMediaFileList();
                        } else {
                            // Handle the error
                            showToastService("Set mode failed: " + djiError.getDescription());
                        }
                    });
                }else {
                    showToastService("Playback Mode not Supported");
                }
            } else {
                if (Objects.requireNonNull(CameraHandler.getCameraInstance()).isMediaDownloadModeSupported()) {
                    CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, djiError -> {
                        if (djiError == null) {
                            // Successfully set to MEDIA_DOWNLOAD mode, or choose any other mode as needed
                            showToastService("Set to MEDIA_DOWNLOAD mode");
                            refreshMediaFileList();
                        } else {
                            // Handle the error
                            showToastService("Set mode failed: " + djiError.getDescription());
                        }
                    });

                } else {
                    showToastService("Media Download Mode not Supported");
                }
            }
        }


    }


    @Override
    public void onDestroy() {
        fromOnDestroy = true;
        setCameraMode();
        super.onDestroy();
    }
}



