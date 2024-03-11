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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import dji.common.camera.SettingsDefinitions;
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

    private static final String TAG = "ClassificationActivity";
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private FusedLocationProviderClient fusedLocationClient;

    //--------------------------------------------------------
    Boolean fromOnDestroy = false;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    ProgressBar mProgressBar;
    private MediaManager mMediaManager;
    private FileListAdapter mListAdapter;
    private final List<MediaFile> mediaFileList = new ArrayList<>();
    private ProgressBar progressBar;
    private ProgressBar progressBarPerImage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classification);

        progressBar = findViewById(R.id.downloadProgressBar);
        progressBarPerImage = findViewById(R.id.downloadPerImageProgressBar);


        // Initialize fusedLocationClient and other components
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleImageSelection(result.getData());
            }
        });

        //-------------------------------------------------------- Initializing MediaManager:
        mProgressBar = findViewById(R.id.progressBar);

        initMediaManager();
        initializeUIComponents();
        registerSDK();
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

            }
            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                showToast("Product Connected");
                onProductReconnected();
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
        RecyclerView recyclerView = findViewById(R.id.mediaFileRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this)); // Set the LayoutManager
        recyclerView.setAdapter(mListAdapter); // Set the adapter


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
                    updateAdapterWithNewData(newMediaFiles); // Directly update adapter
                } else {
                    // Handle the error
                    showToast("Get Media File List Failed:" + djiError.getDescription());
                    refreshMediaFileList();
                }
            }));
        }else {
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
                runOnUiThread(() -> mProgressBar.setVisibility(View.INVISIBLE));
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
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) new URL("https://your.api.endpoint/image_classification").openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);

                int responseCode = urlConnection.getResponseCode();
                runOnUiThread(() -> {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(ClassificationActivity.this, "Scanning initiated successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ClassificationActivity.this, "Error initiating scanning: " + responseCode, Toast.LENGTH_SHORT).show();
                    }
                });

                urlConnection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Scan initiation failed: ", e);
            }
        }).start();
    }


    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void handleImageSelection(Intent data) {
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                ClipData.Item item = clipData.getItemAt(i);
                Uri selectedImageUri = item.getUri();
                upLoadImages(selectedImageUri);
            }
        } else if (data.getData() != null) {
            Uri selectedImageUri = data.getData();
            upLoadImages(selectedImageUri);
        }
    }



    private void upLoadImages(Uri imageUri) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String imageName = UUID.randomUUID().toString() + ".jpg";
        StorageReference imageRef = storageRef.child("unclassified/" + imageName);



        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
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
                                            showToast("Image and location saved successfully");
                                            // Attempt to delete the image file after successful upload and Firestore document creation
                                            try {
                                                boolean deleted = new File(Objects.requireNonNull(imageUri.getPath())).delete();
                                                if (deleted) {
                                                    showToast("Image deleted from device");
                                                } else {
                                                    showToast("Failed to delete image from device");
                                                }
                                            } catch (Exception e) {
                                                showToast("Error deleting image: " + e.getMessage());
                                            }
                                        })
                                        .addOnFailureListener(e -> showToast("Error adding document: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> showToast("Image upload failed: " + e.getMessage()));
                }));
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

    private final BroadcastReceiver loadingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case UploadForegroundService.ACTION_START_LOADING:
                        showLoading();
                        break;
                    case UploadForegroundService.ACTION_STOP_LOADING:
                        String message = intent.getStringExtra("message");
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

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
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
