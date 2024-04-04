package com.dji.ImportSDKDemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.media.ExifInterface;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.ImportSDKDemo.DroneMedia.FileListAdapter;
import com.dji.ImportSDKDemo.HistoryLog.ClassificationCount;
import com.dji.ImportSDKDemo.HistoryLog.LogAdapter;
import com.dji.ImportSDKDemo.HistoryLog.LogEntry;
import com.dji.ImportSDKDemo.HistoryLog.LogManager;
import com.dji.ImportSDKDemo.Library.LibraryActivity;
import com.dji.ImportSDKDemo.Services.UploadForegroundService;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import dji.common.error.DJISDKError;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class ClassificationActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_CODE = 12345;
    //public static final String ACTION_START_LOADING = "com.example.action.START_LOADING";
    private int successfulUploads = 0; // Counter for successful uploads
    private TextView tvLoadingProgressBar;
    private static final String TAG = "ClassificationActivity";
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    //--------------------------------------------------------
    private String batchId;

    Boolean fromOnDestroy = false;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    ProgressBar mProgressBar;
    private MediaManager mMediaManager;
    private FileListAdapter mListAdapter;
    private final List<MediaFile> mediaFileList = new ArrayList<>();
    private ProgressBar progressBar;
    private ProgressBar progressBarPerImage;
    private RecyclerView rvLogEntry;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final DocumentReference firebaseRef = db.collection("count_classified_classes").document("countDict");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classification);




        //for resetting the JSON file and testing
        //LogManager.deleteJSON(getApplicationContext());


        LogManager.initializeLogFile(getApplicationContext());

        progressBar = findViewById(R.id.downloadProgressBar);
        progressBarPerImage = findViewById(R.id.downloadPerImageProgressBar);


        // Initialize fusedLocationClient and other components
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleImageSelection(result.getData());
            }
        });


        //-------------------------------------------------------- Initializing MediaManager:

        initMediaManager();
        initializeUIComponents();
        registerSDK();



    }



    public static String generateBatchId() {
        // Format the current date and time according to the specified format and return it
        return new SimpleDateFormat("yyyy.MM.dd => HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void registerSDK() {
        DJISDKManager.getInstance().registerApp(ClassificationActivity.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
            @Override
            public void onRegister(DJIError djiError) {
                if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                    // SDK registration successful
                    showToast("Register Success");
                    DJISDKManager.getInstance().startConnectionToProduct();

                } else {
                    // SDK registration failed
                    showToast("Register sdk fails, please check the bundle id and network connection!");

                }
                Log.v(TAG, djiError.getDescription());
            }

            @Override
            public void onProductDisconnect() {
                Log.d(TAG, "onProductDisconnect");
                showToast("Product Disconnected");
                onProductDisconnected();
                mProgressBar.setVisibility(View.INVISIBLE);
                tvLoadingProgressBar.setText(R.string.drone_disconnected);
            }
            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                showToast("Product Connected");
                onProductReconnected();
                mProgressBar.setVisibility(View.VISIBLE);
                tvLoadingProgressBar.setText(R.string.drone_disconnected);
            }



            @Override
            public void onProductChanged(BaseProduct baseProduct) {

            }

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                          BaseComponent newComponent) {

                if (newComponent != null) {
                    newComponent.setComponentListener(isConnected -> Log.d(TAG, "onComponentConnectivityChanged: " + isConnected));
                }
                Log.d(TAG,
                        String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                componentKey,
                                oldComponent,
                                newComponent));

            }
            @Override
            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

            }

            @Override
            public void onDatabaseDownloadProgress(long l, long l1) {

            }
        });
    }

    private void initializeUIComponents() {


        Button uploadButton = findViewById(R.id.uploadButton);
        uploadButton.setOnClickListener(v -> launchImagePicker());

        Button checkCameraModeBtn = findViewById(R.id.btnCheckMode);
        checkCameraModeBtn.setOnClickListener(v -> checkCameraMode());

        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(v -> initiateScanning());

        Button libraryButton = findViewById(R.id.libraryButton);
        libraryButton.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));

        Button fetchAndUploadButton = findViewById(R.id.upload_btn_firebase);
        fetchAndUploadButton.setOnClickListener(v ->{
            if (!mediaFileList.isEmpty()){
                checkAndRequestPermissions();
            }else
                showToast("Drone Disconnected, please reconnect the drone and try again");
        });



        mListAdapter = new FileListAdapter(mediaFileList);
        RecyclerView mediaRecyclerView = findViewById(R.id.mediaFileRecyclerView);
        mediaRecyclerView.setLayoutManager(new LinearLayoutManager(this)); // Set the LayoutManager
        mediaRecyclerView.setAdapter(mListAdapter); // Set the adapter


        rvLogEntry = findViewById(R.id.rvLogHistory);
        rvLogEntry.setLayoutManager(new LinearLayoutManager(this));
        List<LogEntry> logEntries = LogManager.readLogsFromFile(getApplicationContext());

        // Create the adapter and set it to the RecyclerView
        LogAdapter logAdapter = new LogAdapter(logEntries, item -> {
            Toast.makeText(ClassificationActivity.this, "Clicked: " + item.getMessage(), Toast.LENGTH_SHORT).show();
            // Handle the click event, e.g., navigate to a different screen with the item details
        });
        rvLogEntry.setAdapter(logAdapter);

        mProgressBar = findViewById(R.id.loadingProgressBar);

        tvLoadingProgressBar = findViewById(R.id.progressBarText);
        if (CameraHandler.getProductInstance() != null) {
            mProgressBar.setVisibility(View.VISIBLE);
            tvLoadingProgressBar.setText(R.string.drone_connected);
        }else
            tvLoadingProgressBar.setText(R.string.drone_disconnected);
    }



    private void proceedWithNotification() {
        showToast(String.valueOf(mediaFileList.size()));
        progressBar.setMax(mediaFileList.size());
        startUploadService();
    }

    private void startUploadService() {
        Intent serviceIntent = new Intent(this, UploadForegroundService.class);
        serviceIntent.putExtra("inputExtra", "Performing upload in foreground");
        ContextCompat.startForegroundService(this, serviceIntent);

    }


    private void initMediaManager() {
        if (CameraHandler.getProductInstance() == null) {
            runOnUiThread(() -> {
                int size = mediaFileList.size();
                mediaFileList.clear();
                if (mListAdapter != null) {
                    mListAdapter.notifyItemRangeRemoved(0, size);
                }
            });
            DJILog.e(TAG, "Product disconnected");
        } else {
            setCameraMode();
        }
    }

    private void refreshMediaFileList() {
        if (mMediaManager != null) {
            mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, djiError -> runOnUiThread(() -> {
                if (null == djiError) {
                    // Successfully refreshed the media list
                    List<MediaFile> newMediaFiles = mMediaManager.getSDCardFileListSnapshot();

                    // Filter out files with 0 bytes and delete them
                    List<MediaFile> filesToDelete = Objects.requireNonNull(newMediaFiles).stream()
                            .filter(file -> file.getFileSize() == 0)
                            .collect(Collectors.toList());

                    if (!filesToDelete.isEmpty()) {
                        mMediaManager.deleteFiles(filesToDelete, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {
                            @Override
                            public void onSuccess(List<MediaFile> successFiles, DJICameraError error) {
                                Log.d(TAG, "Empty files deleted successfully.");
                                // After deleting, refresh the list again to update UI
                                refreshMediaFileList();
                            }

                            @Override
                            public void onFailure(DJIError error) {
                                Log.e(TAG, "Failed to delete empty files: " + error.getDescription());
                                // Even if deletion fails, try to update UI with current files
                                updateAdapterWithNewData(newMediaFiles);
                            }
                        });
                    } else {
                        // If there are no files to delete, just update UI
                        updateAdapterWithNewData(newMediaFiles);
                    }
                } else {
                    // Handle the error
                    showToast("Get Media File List Failed:" + djiError.getDescription());
                }
            }));
        } else {
            showToast("Media Manager is null");
        }
    }


    // Directly update adapter with new data
    private void updateAdapterWithNewData(List<MediaFile> newMediaFiles) {
        if (mListAdapter != null) {
            runOnUiThread(() -> mListAdapter.updateFileList(newMediaFiles));
        }
    }

    private final MediaManager.FileListStateListener updateFileListStateListener = state -> {

        switch (state) {
            case SYNCING:
                // The file list is being synchronized. You might want to show a loading indicator.
                currentFileListState = state;
                runOnUiThread(() -> {
                    // Update UI to show that file list synchronization is in progress
                    showToast("Syncing media file list...");
                });
                DJILog.e(TAG, "recalling setCameraMode");

                break;
            case INCOMPLETE:
                // The file list has not been completely synchronized.
                currentFileListState = state;
                mediaFileList.clear();
                runOnUiThread(() -> {
                    // Update UI to indicate the file list is incomplete.
                    showToast("Media file list incomplete.");
                });
                break;
            case UP_TO_DATE:
                // The file list is complete. You can now access the full list of media files.
                currentFileListState = state;
                runOnUiThread(() -> {
                    // Update UI to reflect that the media file list is ready for access.
                    showToast("Media file list synchronized.");
                    // Optionally, refresh your UI component that displays the media file list.
                });
                runOnUiThread(() -> {
                    mProgressBar.setVisibility(View.INVISIBLE);
                    tvLoadingProgressBar.setVisibility(View.INVISIBLE);
                });
                continueToSetCameraMode();
                break;
            case DELETING:
                // Files are being deleted from the list.
                currentFileListState = state;
                runOnUiThread(() -> {
                    // Update UI to show that files are currently being deleted.
                    showToast("Deleting media files...");
                });
                DJILog.e(TAG, "recalling setCameraMode");
                break;

            case RESET:
                currentFileListState = state;
                runOnUiThread(() -> showToast("The file list is reset. retrying..."));

                break;
            case RENAMING:
                currentFileListState = state;
                runOnUiThread(() -> showToast("A renaming operation is in progress."));


                break;
            case UNKNOWN:
            default:
                // An unknown state.
                currentFileListState = state;
                runOnUiThread(() -> {
                    // Update UI for an unknown state.
                    showToast("Unknown media file list state.");
                });
                break;
        }
    };


    private void setCameraMode() {
        BaseProduct product = CameraHandler.getProductInstance();
        if (product != null) {
            if (CameraHandler.getCameraInstance() != null) {
                mMediaManager = CameraHandler.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)){
                        DJILog.e(TAG, "Media Manager is busy.");
                    }
                } else {
                    showToast("MediaManager is null");
                }
            }else {
                // Handle the case where the camera instance is null
                showToast("Camera disconnected");
            }
        } else {
            // Handle the case where the product is null
            showToast("Drone Disconnected, please reconnect the drone and try again");
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
                                    showToast("Exited playback mode");
                                    CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError2 -> {
                                        if (djiError2 == null) {
                                            // Successfully set to SHOOT_PHOTO mode, or choose any other mode as needed
                                            showToast("Set to SHOOT_PHOTO mode");
                                            refreshMediaFileList();
                                        } else {
                                            // Handle the error
                                            showToast("Set mode failed: " + djiError2.getDescription());
                                        }
                                    });
                                } else {
                                    // Handle the error
                                    showToast("Exit playback mode failed: " + djiError.getDescription());

                                }
                            });
                        } else {
                            CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                                if (djiError == null) {
                                    // Successfully set to SHOOT_PHOTO mode, or choose any other mode as needed
                                    showToast("Set to SHOOT_PHOTO mode");
                                    refreshMediaFileList();
                                } else {
                                    // Handle the error
                                    showToast("Set mode failed: " + djiError.getDescription());
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(DJIError error) {
                        // Handle the error
                        showToast("Set mode failed: " + error.getDescription());
                    }
                });

            }
        }else {
            if (CameraHandler.getCameraInstance() != null) {
                // Check if the product model matches any of the specified drones
                if (model == Model.MATRICE_300_RTK || model == Model.MAVIC_AIR_2 || model == Model.DJI_AIR_2S || model == Model.DJI_MINI_2) {
                    // This block will execute for DJI Mini 2, as well as Matrice 300 RTK, Mavic Air 2, and DJI Air 2S
                    if (CameraHandler.getCameraInstance().isFlatCameraModeSupported()) {
                        CameraHandler.getCameraInstance().enterPlayback(djiError -> {
                            if (djiError == null) {
                                // Successfully set to PLAYBACK mode, or choose any other mode as needed
                                showToast("Set to PLAYBACK mode");
                                refreshMediaFileList();
                            } else {
                                // Handle the error
                                showToast("Set mode failed: " + djiError.getDescription());
                            }
                        });
                    }else {
                        showToast("Playback Mode not Supported");
                    }
                } else {
                    if (CameraHandler.getCameraInstance().isMediaDownloadModeSupported()) {
                        CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, djiError -> {
                            if (djiError == null) {
                                // Successfully set to MEDIA_DOWNLOAD mode, or choose any other mode as needed
                                showToast("Set to MEDIA_DOWNLOAD mode");
                                refreshMediaFileList();
                            } else {
                                // Handle the error
                                showToast("Set mode failed: " + djiError.getDescription());
                            }
                        });

                    } else {
                        showToast("Media Download Mode not Supported");
                    }
                }
            }else {
                // Handle the case where the camera instance is null
                showToast("Camera disconnected. please reconnect the drone and try again");
            }
        }


    }


    private void checkCameraMode() {
        if (CameraHandler.getCameraInstance() != null) {
            CameraHandler.getCameraInstance().getMode(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.CameraMode>() {
                @Override
                public void onSuccess(SettingsDefinitions.CameraMode cameraMode) {
                    showToast("Camera Mode: " + cameraMode);
                }
                @Override
                public void onFailure(DJIError error) {
                    // Handle the error
                    showToast("Get Mode failed: " + error.getDescription());
                }
            });
        }
    }


    // This method could be called when a disconnection event is detected
    private void onProductDisconnected() {
        int size = mediaFileList.size();
        mediaFileList.clear();
        runOnUiThread(() -> {
            // Update UI
            mListAdapter.notifyItemRangeRemoved(0, size);
        });

    }

    // This method could be called when a reconnection event is detected
    private void onProductReconnected() {
        refreshMediaFileList();

    }

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        imagePickerLauncher.launch(intent);
    }

    private void initiateScanning() {
        new Thread(() -> {
            runOnUiThread(() -> showLoading(false));

            HttpURLConnection urlConnection = null;
            try {

                urlConnection = (HttpURLConnection) new URL("https://europe-west1-msdk-app-3a2d5.cloudfunctions.net/image_classification").openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);

                int responseCode = urlConnection.getResponseCode();
                runOnUiThread(() -> {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(ClassificationActivity.this, "Scanning initiated successfully!", Toast.LENGTH_SHORT).show();
                        fetchClassificationCounts();
                        hideLoading();

                    } else {
                        Toast.makeText(ClassificationActivity.this, "Error initiating scanning: " + responseCode, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Scan initiation failed: ", e);
                runOnUiThread(() -> Toast.makeText(ClassificationActivity.this, "Exception: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }).start();
    }



    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void handleImageSelection(Intent data) {
        batchId = generateBatchId(); // Ensure this generates a unique ID for each batch
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            int totalFiles = clipData.getItemCount();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                ClipData.Item item = clipData.getItemAt(i);
                Uri selectedImageUri = item.getUri();
                upLoadImages(selectedImageUri, totalFiles);
            }
        } else if (data.getData() != null) {
            int totalFiles = 1;
            Uri selectedImageUri = data.getData();
            upLoadImages(selectedImageUri, totalFiles);
        }
    }



    private void upLoadImages(Uri selectedImageUri, int totalFiles) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String imageName = UUID.randomUUID().toString();
        StorageReference imageRef = storageRef.child("unclassified/" + imageName);
        imageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }

                            Map<String, Object> docData = new HashMap<>();
                            docData.put("imageUrl", downloadUri.toString());
                            docData.put("imageName", imageName);
                            docData.put("imagePath", selectedImageUri.getPath());
                            docData.put("classTag", "unclassified");

                            Date timestamp = extractImageTime(selectedImageUri);
                            docData.put("timestamp", timestamp);

                            Map<String, Double> returnLocation = extractImageLocation(selectedImageUri);
                            if (returnLocation != null) {
                                docData.put("latitude", returnLocation.get("latitude"));
                                docData.put("longitude", returnLocation.get("longitude"));
                            } else {
                                docData.put("latitude", null);
                                docData.put("longitude", null);
                            }


                            // Save image data within a specific batch
                            // Use a document for batch with a sub-collection for images
                            FirebaseFirestore.getInstance()
                                    .collection("unclassified")
                                    .document(batchId) // This is the batch document
                                    .collection("images") // Sub-collection for images within the batch
                                    .document(imageName) // This is the image document
                                    .set(docData) // Adds the image document within the "images" sub-collection
                                    .addOnSuccessListener(documentReference -> {
                                    })
                                    .addOnFailureListener(e -> showToast("Error adding document: " + e.getMessage()));
                        })
                        .addOnFailureListener(e -> showToast("Image upload failed: " + e.getMessage())));

        updateSuccessfulUploads(totalFiles);
    }


    private void updateSuccessfulUploads(int totalFiles){
        // After each file download completion counting the successful uploads and updating the recycler view
        successfulUploads++;
        if(successfulUploads == totalFiles){
            updateImageCountUI(successfulUploads);
            successfulUploads = 0;
        }
    }

    public void fetchClassificationCounts() {
    firebaseRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> counts = documentSnapshot.getData();
                        List<ClassificationCount> classificationCounts = new ArrayList<>();
                        if (counts != null) {
                            for (Map.Entry<String, Object> entry : counts.entrySet()) {
                                classificationCounts.add(new ClassificationCount(entry.getKey(), (long) entry.getValue()));
                            }
                        }
                        // Now update the RecyclerView
                        updateClassCountView(classificationCounts);
                        firebaseRef.delete();
                    }
                })
                .addOnFailureListener(e -> Log.e("Firestore", "Error getting classification counts", e));
    }

    /**
     * update the logManager with the message and the batch ID, and update the recycle view
     * @param classificationCounts - List of classification counts
     */
    private void updateClassCountView(List<ClassificationCount> classificationCounts) {
        StringBuilder messageBuilder = getMessageBuilder(classificationCounts);

        // Now 'message' contains the desired string, e.g., "you classified 3 images to class: class1, images to class: class2..."
        String message = messageBuilder.toString();
        LogEntry logEntry = new LogEntry(batchId, message);
        LogManager.appendLogToJsonFile(getApplicationContext(), logEntry);

        updateLogInView();
    }


    /**
     * @param classificationCounts - List of classification counts
     * @return - A string builder containing the message
     */
    @NonNull
    private static StringBuilder getMessageBuilder(List<ClassificationCount> classificationCounts) {
        StringBuilder messageBuilder = new StringBuilder("You classified");
        for (ClassificationCount classificationCount : classificationCounts) {
            // Check if the builder already has some text, add a separator before adding more.
            if (messageBuilder.length() > 0) {
                messageBuilder.append(",\n");
            }
            messageBuilder.append(classificationCount.getCount())
                    .append(" images to class: ")
                    .append(classificationCount.getClassName());
        }
        return messageBuilder;
    }


    // Helper method to update the UI with the image count
    private void updateImageCountUI(int imageCount) {
         String message;
        if (imageCount == 1) {
            message = imageCount  + " image uploaded";
        }else {
            message = imageCount  + " images uploaded";
        }
        LogEntry logEntry = new LogEntry(batchId, message);
        LogManager.appendLogToJsonFile(getApplicationContext(), logEntry);

        updateLogInView();
    }



    public void updateLogInView() {
        LogAdapter adapter = (LogAdapter) rvLogEntry.getAdapter();

        //rest the adapter to sync it with the new data
        if (adapter != null) {
            adapter.resetEntries();
        }


        //syncing the adapter with the new data
        List<LogEntry> newLogEntries = LogManager.readLogsFromFile(getApplicationContext());
        if (adapter != null) {
            showToast("Log size: " + newLogEntries.size());
            adapter.updateData(newLogEntries);
        }
        //scroll to the top to show the newest entry
        rvLogEntry.scrollToPosition(0);
    }


    private Date extractImageTime(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            ExifInterface exif = null;
            if (inputStream != null) {
                exif = new ExifInterface(inputStream);
            }
            String dateTimeString = null;
            if (exif != null) {
                dateTimeString = exif.getAttribute(ExifInterface.TAG_DATETIME);
            }
            if (dateTimeString != null) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                return format.parse(dateTimeString);
            }
        } catch (IOException | ParseException e) {
            showToast("Error extracting image time: " + e.getMessage());
        }
        return null;
    }

    private Map<String, Double> extractImageLocation(Uri imageUri) {
        Map<String, Double> returnLocation = new HashMap<>();
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            ExifInterface exif = null;
            if (inputStream != null) {
                exif = new ExifInterface(inputStream);
            }

            double[] latLong = new double[0];
            if (exif != null) {
                latLong = exif.getLatLong();
            }
            if(latLong != null) {
                returnLocation.put("latitude", latLong[0]);
                returnLocation.put("longitude", latLong[1]);
                return returnLocation;
            }
        } catch (IOException e) {
            showToast("Error extracting image location: " + e.getMessage());
        }
        return null;
    }







    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                // All requested permissions are granted
                    proceedWithNotification();
            } else {
                // Notify the user that some permissions were denied
                showToast("Some permissions were denied.");
                checkAndRequestPermissions();
            }
        }
    }



    // This method could be called when you want to request the necessary permissions
    public void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        // Check if location permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request location permission
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }


        // Check for notification permission on Android Tiramisu (API level 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // If there are permissions that need to be requested, request them
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSION_CODE);
        } else {
            // All permissions are granted, proceed with your functionality
            proceedWithNotification();
        }
    }


    // Initialize the BroadcastReceiver of the downloadBar
    private final BroadcastReceiver loadingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case UploadForegroundService.ACTION_START_LOADING:
                        showLoading(true);
                        break;
                    case UploadForegroundService.ACTION_STOP_LOADING:
                        String message = intent.getStringExtra("message");
                        int imageCount = intent.getIntExtra("imageCount", 0);
                        batchId = intent.getStringExtra("batchID");
                        updateImageCountUI(imageCount);
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                        hideLoading();
                        initMediaManager();
                    break;
                }
            }
        }
    };


    // Initialize the BroadcastReceiver of the downloadBar per image
    private final BroadcastReceiver loadingReceiverForPerImage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case UploadForegroundService.ACTION_START_LOADING_PER_IMAGE:
                        showLoadingPerImage();
                        break;
                    case UploadForegroundService.ACTION_STOP_LOADING_PER_IMAGE:
                        hideLoadingPerImage();
                        //updateAdapterWithNewData(mediaFileList);
                        break;
                }
            }
        }
    };

    // Initialize the BroadcastReceiver of the downloadBar
    private final BroadcastReceiver progressUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Update progress bar
            int progress = intent.getIntExtra("progress", 0);
            String message = intent.getStringExtra("message");
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            progressBar.setProgress(progress);
        }
    };


    // Initialize the BroadcastReceiver of the downloadBar per image
    private final BroadcastReceiver progressPerImageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Update progress bar
            int progress = intent.getIntExtra("progress", 0);
            progressBarPerImage.setProgress(progress);
        }
    };


    // Initialize the BroadcastReceiver of notifying fetching tries
    private final BroadcastReceiver notifyFilesReadyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
         if (intent.getAction() != null) {
             if (intent.getAction().equals(UploadForegroundService.ACTION_NOTIFY_TRIES)) {
                 String message = intent.getStringExtra("message");
                 Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
             }
         }
        }
    };


    // Initialize the BroadcastReceiver of showing toast
    private final BroadcastReceiver showToastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                String message = intent.getStringExtra("message");
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void showLoading(boolean showBar) {
        if (showBar) {
            progressBar.setVisibility(View.VISIBLE);
        }
        setScreenTouchable(false);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.INVISIBLE);
        setScreenTouchable(true);
    }

    private void showLoadingPerImage() {
        progressBarPerImage.setVisibility(View.VISIBLE);
    }

    private void hideLoadingPerImage() {
        progressBarPerImage.setVisibility(View.INVISIBLE);
    }

    public void setScreenTouchable(boolean touchable) {
        FrameLayout overlay = findViewById(R.id.overlay);
        if (touchable) {
            overlay.setVisibility(View.GONE); // Hide overlay to enable touch events
        } else {
            overlay.setVisibility(View.VISIBLE); // Show overlay to disable touch events
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();

        // Initialize and set up all IntentFilters here
        IntentFilter filter = new IntentFilter();
        filter.addAction(UploadForegroundService.ACTION_START_LOADING);
        filter.addAction(UploadForegroundService.ACTION_STOP_LOADING);
        registerReceiver(loadingReceiver, filter);

        IntentFilter notifyFilesReadyFilter = new IntentFilter();
        notifyFilesReadyFilter.addAction(UploadForegroundService.ACTION_NOTIFY_TRIES);
        registerReceiver(notifyFilesReadyReceiver, notifyFilesReadyFilter);

        IntentFilter filterForPerImage = new IntentFilter();
        filterForPerImage.addAction(UploadForegroundService.ACTION_START_LOADING_PER_IMAGE);
        filterForPerImage.addAction(UploadForegroundService.ACTION_STOP_LOADING_PER_IMAGE);
        registerReceiver(loadingReceiverForPerImage, filterForPerImage);

        registerReceiver(progressUpdateReceiver, new IntentFilter("ACTION_PROGRESS_UPDATE"));
        registerReceiver(progressPerImageUpdateReceiver, new IntentFilter("ACTION_PROGRESS_UPDATE_PER_IMAGE"));
        registerReceiver(showToastReceiver, new IntentFilter("ACTION_SHOW_TOAST"));

    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister all receivers here
        unregisterReceiver(loadingReceiver);
        unregisterReceiver(notifyFilesReadyReceiver);
        unregisterReceiver(loadingReceiverForPerImage);
        unregisterReceiver(progressUpdateReceiver);
        unregisterReceiver(progressPerImageUpdateReceiver);
    }


    @Override
    protected void onDestroy() {
        fromOnDestroy = true;
        setCameraMode();
        if (mediaFileList != null) {
            mediaFileList.clear();
        }
        super.onDestroy();
    }



}
