package com.dji.ImportSDKDemo.Services;

import static com.dji.ImportSDKDemo.ExtractImageInformation.extractImageLocation;
import static com.dji.ImportSDKDemo.ExtractImageInformation.extractImageTime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.dji.ImportSDKDemo.CameraHandler;
import com.dji.ImportSDKDemo.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private String batchId;
    private String bactchTimeStamp;

    private int successfulUploads = 0; // Counter for successful uploads
    int totalFiles;
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private File destDir; // Declare here, but don't initialize yet
    private NotificationManager notificationManager;
    private MediaManager mMediaManager;
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseUser user = mAuth.getCurrentUser();
    @Override
    public void onCreate() {
        super.onCreate();

        //Use of notificationManager in API level 23 and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            notificationManager = getSystemService(NotificationManager.class);
        } else { // Use of notificationManager in API level 22 and below
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }


        createNotificationChannel();

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
        if (intent != null) {
            // Retrieve the batch ID passed to the service
            batchId = intent.getStringExtra("BatchID");
        } else {
            Log.e("UploadService", "Intent is null, service not started via startForegroundService");
        }

        Intent startLoadingIntent = new Intent(ACTION_START_LOADING);
        sendBroadcast(startLoadingIntent);

        // Call updateNotification at the beginning to setup foreground service
        updateNotification();

        bactchTimeStamp = generateBatchTimeStamp(); // Ensure this generates a unique ID for each batch

        initMediaManager();


        return START_NOT_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public static String generateBatchTimeStamp() {
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
            showToastService("Drone Disconnected, please reconnect the drone and try again");
            stopSelf();
        }
    }


    private void refreshMediaFileList() {
        if (mMediaManager != null) {
            mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, djiError -> {
                if (null == djiError) {

                    // Clear previous list if the current state is not INCOMPLETE
                    if (currentFileListState != MediaManager.FileListState.INCOMPLETE) {
                        if (mediaFileList != null) {
                            mediaFileList.clear();
                        }
                    }
                    // Successfully refreshed the media list
                    mediaFileList = mMediaManager.getSDCardFileListSnapshot();

                    // Filter out files with 0 bytes and delete them
                    List<MediaFile> filesToDelete = new ArrayList<>();

                    // for api 24 and above
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        filesToDelete = Objects.requireNonNull(mediaFileList).stream()
                                .filter(file -> file.getFileSize() == 0)
                                .collect(Collectors.toList());
                    } else { // for api 23 and below
                        for (MediaFile file : mediaFileList) {
                            if (file.getFileSize() == 0) {
                                filesToDelete.add(file);
                            }
                        }
                    }

                    if (!filesToDelete.isEmpty()) {
                        mMediaManager.deleteFiles(filesToDelete, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {
                            @Override
                            public void onSuccess(List<MediaFile> successFiles, DJICameraError error) {
                                sortMediaFiles();
                            }

                            @Override
                            public void onFailure(DJIError error) {
                            }
                        });
                    } else {
                        sortMediaFiles();
                    }
                } else {
                    // Handle the error
                    showToastService("Failed to refresh the media list: " + djiError.getDescription());
                }
            });
        } else {
            showToastService("MediaManager is null");
        }
    }

    private void sortMediaFiles() {
        // Sort media files by creation time in descending order
        if (mediaFileList != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaFileList.sort((lhs, rhs) -> Long.compare(rhs.getTimeCreated(), lhs.getTimeCreated()));
            } else {
                Collections.sort(mediaFileList, (lhs, rhs) -> {
                    // Use Long.compare for comparing long values for descending order
                    return Long.compare(rhs.getTimeCreated(), lhs.getTimeCreated());
                });
            }
        }

        downloadAllImageFiles();
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
            case RESET:
                currentFileListState = state;
                showToastService("The file list is reset. please try again");
                stopSelf();

                break;
            case RENAMING:
                currentFileListState = state;
                showToastService("A renaming operation is in progress.");

            break;
            case UNKNOWN:
            default:
                // Handle any unknown states.
                currentFileListState = state;
                showToastService("Unknown state: " + state);

                break;
        }
    };


    private void downloadAllImageFiles() {
        if (mediaFileList != null) {
            // Total number of files to download
            totalFiles = mediaFileList.size();
            for (MediaFile mediaFile : mediaFileList) {
                //if ((mediaFile.getMediaType() != MediaFile.MediaType.PANORAMA) && (mediaFile.getMediaType() != MediaFile.MediaType.SHALLOW_FOCUS)) {
                    if (mediaFile.getMediaType() == MediaFile.MediaType.JPEG) { // Ensure it's an image file
                        downloadMediaFile(mediaFile);
                    }
            }

            // Upload to Firebase
            if (Objects.requireNonNull(destDir.listFiles()).length > 0) {
                uploadAllImageFiles(destDir);
            }

        } else {
            //no files
            showToastService( "No files to download");
            stopSelf();
        }

    }


    private void downloadMediaFile(MediaFile mediaFile) {
        if (mediaFile != null && mediaFile.getMediaType() == MediaFile.MediaType.JPEG) { // Check if it's an image
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

                }

                @Override
                public void onFailure(DJIError error) {
                    showToastService("Download Failed:" + error.getDescription());
                }
            });
        }
    }




    private void uploadAllImageFiles(File destDir) {
        File[] files = destDir.listFiles();
        if (files != null) {
            for (File file : files) {
                upLoadImages(file);
            }
        }
    }

    private void upLoadImages(File file) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String imageName = UUID.randomUUID().toString();
        StorageReference imageRef = storageRef.child("Users/"+ Objects.requireNonNull(user).getUid() + "/unclassified/" + imageName);
        Uri imageUri = Uri.fromFile(file);
        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }

                            Map<String, Object> docData = new HashMap<>();
                            docData.put("imageUrl", downloadUri.toString());
                            docData.put("imageName", imageName);
                            docData.put("classTag", "unclassified");

                            try {
                                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                                Date timestamp = extractImageTime(inputStream);
                                docData.put("timestamp", timestamp);

                                Map<String, Double> returnLocation = extractImageLocation(inputStream);
                                if (returnLocation != null) {
                                    docData.put("latitude", returnLocation.get("latitude"));
                                    docData.put("longitude", returnLocation.get("longitude"));
                                } else {
                                    docData.put("latitude", null);
                                    docData.put("longitude", null);
                                }
                            } catch (FileNotFoundException e) {
                                throw new RuntimeException(e);
                            }



                            Map<String, Object> batchDocData = new HashMap<>();
                            batchDocData.put("timestamp", bactchTimeStamp);

                            FirebaseFirestore.getInstance()
                                    .collection("Users")
                                    .document(user.getUid())
                                    .collection("unclassified")
                                    .document(batchId)
                                    .set(batchDocData)
                                    .addOnSuccessListener(documentReference -> {
                                    })
                                    .addOnFailureListener(e -> showToastService("Error adding document: " + e.getMessage()));


                            // Save image data within a specific batch
                            // Use a document for batch with a sub-collection for images
                            FirebaseFirestore.getInstance()
                                    .collection("Users")
                                    .document(user.getUid())
                                    .collection("unclassified")
                                    .document(batchId) // This is the batch document
                                    .collection("images") // Sub-collection for images within the batch
                                    .document(imageName) // This is the image document
                                    .set(docData) // Adds the image document within the "images" sub-collection
                                    .addOnSuccessListener(documentReference -> {
                                        // Attempt to delete the image file after successful upload and Firestore document creation
                                        deleteImageFromDrone(mediaFileList);
                                        try {
                                            boolean deleted = file.delete();
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
            stopLoadingIntent.putExtra("batchID", batchId);
            stopLoadingIntent.putExtra("imageCount", successfulUploads);
            sendBroadcast(stopLoadingIntent);
            stopSelf(); // Call this to stop the service
        }
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
        // Check if we're running on Android Oreo or higher
        //noinspection StatementWithEmptyBody
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // The user-visible name of the channel.
            CharSequence name = getString(R.string.channel_name);
            // The user-visible description of the channel.
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(channel);
        } else {
            // updateNotification() will be called anyway
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

    private void showToastService(final String text) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(UploadForegroundService.this, text, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroy() {
        fromOnDestroy = true;
        setCameraMode();
        super.onDestroy();
    }
}



