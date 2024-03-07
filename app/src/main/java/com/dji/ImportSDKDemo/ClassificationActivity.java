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

    ProgressBar mProgressBar;
    private MediaManager mMediaManager;
    private FileListAdapter mListAdapter;
    private final List<MediaFile> mediaFileList = new ArrayList<>();

    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classification);

        progressBar = findViewById(R.id.downloadProgressBar);



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
            if (CameraHandler.getCameraInstance() != null && CameraHandler.getCameraInstance().isMediaDownloadModeSupported()) {
                mMediaManager = CameraHandler.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                        getFileList();
                }
            } else {
                showToast("Media Download Mode not Supported");
            }
        }
    }

    private void getFileList() {
        runOnUiThread(() -> mProgressBar.setVisibility(View.INVISIBLE));
        if (mMediaManager != null) {
            mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, djiError -> runOnUiThread(() -> {
                mProgressBar.setVisibility(View.INVISIBLE); // Ensure UI update code here is on UI thread
                if (null == djiError) {
                    // Successfully refreshed the media list
                    List<MediaFile> newMediaFiles = mMediaManager.getSDCardFileListSnapshot();
                    updateAdapterWithNewData(newMediaFiles); // Directly update adapter
                } else {
                    // Handle the error
                    showToast("Get Media File List Failed:" + djiError.getDescription());
                }
            }));
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
                runOnUiThread(() -> {
                    // Update UI to show that file list synchronization is in progress
                    showToast("Syncing media file list...");
                });
                break;
            case INCOMPLETE:
                // The file list has not been completely synchronized.
                runOnUiThread(() -> {
                    // Update UI to indicate the file list is incomplete.
                    showToast("Media file list incomplete.");
                });
                break;
            case UP_TO_DATE:
                // The file list is complete. You can now access the full list of media files.
                runOnUiThread(() -> {
                    // Update UI to reflect that the media file list is ready for access.
                    showToast("Media file list synchronized.");
                    // Optionally, refresh your UI component that displays the media file list.
                });
                break;
            case DELETING:
                // Files are being deleted from the list.
                runOnUiThread(() -> {
                    // Update UI to show that files are currently being deleted.
                    showToast("Deleting media files...");
                });
                break;
            case UNKNOWN:
            default:
                // Handle any unknown states.
                runOnUiThread(() -> {
                    // Update UI for an unknown state.
                    showToast("Unknown media file list state.");
                });
                break;
        }
    };

    // This method could be called when a disconnection event is detected
    private void onProductDisconnected() {
        int size = mediaFileList.size();
        mediaFileList.clear();
        runOnUiThread(() -> {
            // Update UI
            mListAdapter.notifyItemRangeRemoved(0, size);
        });

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
                        hideLoading();
                        updateAdapterWithNewData(mediaFileList);
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
            progressBar.setProgress(progress);
        }
    };


    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.INVISIBLE);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UploadForegroundService.ACTION_START_LOADING);
        filter.addAction(UploadForegroundService.ACTION_STOP_LOADING);
        registerReceiver(loadingReceiver, filter);
    }


    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(loadingReceiver);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        // Register receiver with an intent filter
        registerReceiver(progressUpdateReceiver, new IntentFilter("ACTION_PROGRESS_UPDATE"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister receiver
        unregisterReceiver(progressUpdateReceiver);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }



}
