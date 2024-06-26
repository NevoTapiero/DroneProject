package com.dji.ImportSDKDemo.NavigationBarActivities;

import static com.dji.ImportSDKDemo.ExtractImageInformation.extractImageLocation;
import static com.dji.ImportSDKDemo.ExtractImageInformation.extractImageTime;
import static com.dji.ImportSDKDemo.HistoryLog.LogFunctions.fetchData;
import static com.dji.ImportSDKDemo.HistoryLog.LogFunctions.uploadListToFirestore;
import static com.dji.ImportSDKDemo.Services.UploadForegroundService.ACTION_NOTIFY_TRIES;
import static com.dji.ImportSDKDemo.Services.UploadForegroundService.ACTION_PROGRESS_UPDATE;
import static com.dji.ImportSDKDemo.Services.UploadForegroundService.ACTION_PROGRESS_UPDATE_PER_IMAGE;
import static com.dji.ImportSDKDemo.Services.UploadForegroundService.ACTION_START_LOADING;
import static com.dji.ImportSDKDemo.Services.UploadForegroundService.ACTION_START_LOADING_PER_IMAGE;
import static com.dji.ImportSDKDemo.Services.UploadForegroundService.ACTION_STOP_LOADING;
import static com.dji.ImportSDKDemo.Services.UploadForegroundService.ACTION_STOP_LOADING_PER_IMAGE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.ImportSDKDemo.BaseActivity;
import com.dji.ImportSDKDemo.CameraHandler;
import com.dji.ImportSDKDemo.DroneMedia.FileListAdapter;
import com.dji.ImportSDKDemo.HistoryLog.ClassificationCount;
import com.dji.ImportSDKDemo.HistoryLog.LogAdapter;
import com.dji.ImportSDKDemo.HistoryLog.LogEntry;
import com.dji.ImportSDKDemo.R;
import com.dji.ImportSDKDemo.Services.UploadForegroundService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJICameraError;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;

public class ScanActivity extends BaseActivity {
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private int successfulUploads = 0; // Counter for successful uploads
    private static final String TAG = "ScanActivity";
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private final List<String> selectedBatches = new ArrayList<>(), categories = new ArrayList<>(Arrays.asList("Corn_common_rust", "Corn_healthy", "Corn_Infected", "Corn_northern_leaf_blight", "Corn_gray_leaf_spots", "unclassified"));
    private String batchId, batchTimeStamp;
    private StringBuilder logBatch = new StringBuilder();
    private boolean fromOnDestroy = false;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    private MediaManager mMediaManager;
    private FileListAdapter mListAdapter;
    private boolean overlayState = false;
    private View overlayLayout;

    private final List<MediaFile> mediaFileList = new ArrayList<>();
    private ProgressBar progressBar, progressBarPerImage;
    private RecyclerView rvLogEntry;
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseUser user = mAuth.getCurrentUser();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final DocumentReference firebaseRef = db.collection("Users").document(Objects.requireNonNull(user).getUid()).collection("count_classified_classes").document("countDict");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classification);

        initUI();

        // Initialize fusedLocationClient and other components
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleImageSelection(result.getData());
            }
        });

    }



    public static String generateBatchTimeStamp() {
        // Format the current date and time according to the specified format and return it
        return new SimpleDateFormat("yyyy.MM.dd => HH:mm:ss", Locale.getDefault()).format(new Date());
    }



    private void initUI() {
        progressBar = findViewById(R.id.downloadProgressBar);
        progressBarPerImage = findViewById(R.id.downloadPerImageProgressBar);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_scan);

        // Set listener for navigation item selection using if-else instead of switch
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_fly) {
                startActivity(new Intent(this, FlyActivity.class));

            } else //noinspection StatementWithEmptyBody
                if (itemId == R.id.nav_scan) {

            } else if (itemId == R.id.nav_gallery) {
                startActivity(new Intent(this, GalleryActivity.class));

            } else if (itemId == R.id.nav_profile) {
                //startActivity(new Intent(this, ProfileActivity.class));
            }
            return true;
        });


        Button uploadButton = findViewById(R.id.uploadButton);
        uploadButton.setOnClickListener(v -> launchImagePicker());

        /*Button checkCameraModeBtn = findViewById(R.id.btnCheckMode);
        checkCameraModeBtn.setOnClickListener(v -> checkCameraMode());*/

        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(v -> {
            if (selectedBatches.isEmpty()) {
                showToast("Please select at least one batch to scan", getApplicationContext());
            }else {
                Toast.makeText(this, selectedBatches.toString(), Toast.LENGTH_SHORT).show();
                initiateScanning(user.getUid(), selectedBatches);
            }
        });

        Button fetchAndUploadButton = findViewById(R.id.upload_btn_firebase);

        fetchAndUploadButton.setOnClickListener(v ->{
            if (!mediaFileList.isEmpty()){
                askForBatchNameDrone(this);
            }else {
                showToast("Drone Disconnected, please reconnect the drone and try again", getApplicationContext());
            }
        });

        overlayLayout = findViewById(R.id.overlayLayout);
        Button openOverlayDroneFiles = findViewById(R.id.openOverlayDroneFiles);
        openOverlayDroneFiles.setOnClickListener(v -> toggleOverlay());

        if (CameraHandler.getProductInstance() != null) {
            fetchAndUploadButton.setEnabled(true);
        }else {
            fetchAndUploadButton.setEnabled(false);
        }

        Button selectBatchesButton = findViewById(R.id.selectBatchesButton);
        selectBatchesButton.setOnClickListener(v -> loadBatches());

        mListAdapter = new FileListAdapter(mediaFileList);
        RecyclerView mediaRecyclerView = findViewById(R.id.mediaFileRecyclerView);
        mediaRecyclerView.setAdapter(mListAdapter); // Set the adapter


        rvLogEntry = findViewById(R.id.rvLogHistory);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setReverseLayout(true); // This line reverses the layout
        layoutManager.setStackFromEnd(true); // This line ensures items start from the bottom
        rvLogEntry.setLayoutManager(layoutManager);

        fetchData(new FirestoreCallback() {
            @Override
            public void onComplete(List<LogEntry> result) {
                // Create the adapter and set it to the RecyclerView
                LogAdapter logAdapter = new LogAdapter(result, item -> {
                    if (item.getBatchName().contains(",")) {
                        List<String> batchNames = Arrays.asList(item.getBatchName().split(","));
                        showClassifiedBatchSelectionDialog(batchNames);
                    }else {
                        Toast.makeText(ScanActivity.this, "Clicked: " + item.getMessage(), Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(ScanActivity.this, GalleryActivity.class);
                        intent.putExtra("batchId", item.getBatchName());
                        startActivity(intent);
                        finish();
                    }

                });
                rvLogEntry.setAdapter(logAdapter);
            }

            @Override
            public void onError(String message) {
                // Handle any errors, e.g., show an error message
                Toast.makeText(getApplicationContext(), "Error: " + message, Toast.LENGTH_SHORT).show();
            }
        });


    }

    private void toggleOverlay() {
        if (overlayState) {
            overlayLayout.setVisibility(View.GONE);
        } else {
            overlayLayout.setVisibility(View.VISIBLE);
            initMediaManager();
        }
        overlayState = !overlayState; // Toggle the state
    }

    private void startUploadService() {
        Intent serviceIntent = new Intent(this, UploadForegroundService.class);
        serviceIntent.putExtra("BatchID", batchId);

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
                    showToast("Get Media File List Failed:" + djiError.getDescription(), getApplicationContext());
                }
            }));
        } else {
            showToast("Media Manager is null", getApplicationContext());
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
                    showToast("Syncing media file list...", getApplicationContext());
                });
                DJILog.e(TAG, "recalling setCameraMode");

                break;
            case INCOMPLETE:
                // The file list has not been completely synchronized.
                currentFileListState = state;
                mediaFileList.clear();
                runOnUiThread(() -> {
                    // Update UI to indicate the file list is incomplete.
                    showToast("Media file list incomplete.", getApplicationContext());
                });
                break;
            case UP_TO_DATE:
                // The file list is complete. You can now access the full list of media files.
                currentFileListState = state;
                runOnUiThread(() -> {
                    // Update UI to reflect that the media file list is ready for access.
                    showToast("Media file list synchronized.", getApplicationContext());
                    // Optionally, refresh your UI component that displays the media file list.
                });
                continueToSetCameraMode();
                break;
            case DELETING:
                // Files are being deleted from the list.
                currentFileListState = state;
                runOnUiThread(() -> {
                    // Update UI to show that files are currently being deleted.
                    showToast("Deleting media files...", getApplicationContext());
                });
                DJILog.e(TAG, "recalling setCameraMode");
                break;

            case RESET:
                currentFileListState = state;
                runOnUiThread(() -> showToast("The file list is reset. retrying...", getApplicationContext()));

                break;
            case RENAMING:
                currentFileListState = state;
                runOnUiThread(() -> showToast("A renaming operation is in progress." ,getApplicationContext()));


                break;
            case UNKNOWN:
            default:
                // An unknown state.
                currentFileListState = state;
                runOnUiThread(() -> {
                    // Update UI for an unknown state.
                    showToast("Unknown media file list state.", getApplicationContext());
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
                    showToast("MediaManager is null" ,getApplicationContext());
                }
            }else {
                // Handle the case where the camera instance is null

                showToast("Camera disconnected", getApplicationContext());
            }
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
                                    showToast("Exited playback mode", getApplicationContext());
                                    CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError2 -> {
                                        if (djiError2 == null) {
                                            // Successfully set to SHOOT_PHOTO mode, or choose any other mode as needed
                                            showToast("Set to SHOOT_PHOTO mode", getApplicationContext());
                                            refreshMediaFileList();
                                        } else {
                                            // Handle the error
                                            showToast("Set mode failed: " + djiError2.getDescription(), getApplicationContext());
                                        }
                                    });
                                } else {
                                    // Handle the error
                                    showToast("Exit playback mode failed: " + djiError.getDescription(), getApplicationContext());

                                }
                            });
                        } else {
                            CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                                if (djiError == null) {
                                    // Successfully set to SHOOT_PHOTO mode, or choose any other mode as needed
                                    showToast("Set to SHOOT_PHOTO mode", getApplicationContext());
                                    refreshMediaFileList();
                                } else {
                                    // Handle the error
                                    showToast("Set mode failed: " + djiError.getDescription(), getApplicationContext());
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(DJIError error) {
                        // Handle the error
                        showToast("Set mode failed: " + error.getDescription(), getApplicationContext());
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
                                showToast("Set to PLAYBACK mode", getApplicationContext());
                                refreshMediaFileList();
                            } else {
                                // Handle the error
                                showToast("Set mode failed: " + djiError.getDescription(), getApplicationContext());
                            }
                        });
                    }else {
                        showToast("Playback Mode not Supported", getApplicationContext());
                    }
                } else {
                    if (CameraHandler.getCameraInstance().isMediaDownloadModeSupported()) {
                        CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, djiError -> {
                            if (djiError == null) {
                                // Successfully set to MEDIA_DOWNLOAD mode, or choose any other mode as needed
                                showToast("Set to MEDIA_DOWNLOAD mode", getApplicationContext());
                                refreshMediaFileList();
                            } else {
                                // Handle the error
                                showToast("Set mode failed: " + djiError.getDescription(), getApplicationContext());
                            }
                        });

                    } else {
                        showToast("Media Download Mode not Supported", getApplicationContext());
                    }
                }
            }else {
                // Handle the case where the camera instance is null
                showToast("Camera disconnected. please reconnect the drone and try again", getApplicationContext());
            }
        }


    }


    private void checkCameraMode() {
        if (CameraHandler.getCameraInstance() != null) {
            CameraHandler.getCameraInstance().getMode(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.CameraMode>() {
                @Override
                public void onSuccess(SettingsDefinitions.CameraMode cameraMode) {
                    showToast("Camera Mode: " + cameraMode, getApplicationContext());
                }
                @Override
                public void onFailure(DJIError error) {
                    // Handle the error
                    showToast("Get Mode failed: " + error.getDescription(), getApplicationContext());
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
        batchTimeStamp = generateBatchTimeStamp();
        askForBatchName(this); // Ensure this generates a unique ID for each batch
    }


    private void initiateScanning(String userID, List<String> batches) {
        Executor executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> setScreenTouchable(false));
        executor.execute(() -> {
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL("https://europe-west1-msdk-app-3a2d5.cloudfunctions.net/image_classification");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);

                // Create JSON payload
                JSONObject payload = new JSONObject();
                payload.put("user_id", userID);
                payload.put("batches", new JSONArray(batches));
                String jsonInputString = payload.toString();

                // Write JSON payload to output stream
                try (OutputStream os = urlConnection.getOutputStream()) {
                    byte[] input;
                    input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Read the response from the server
                int responseCode = urlConnection.getResponseCode();

                handler.post(() -> {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(ScanActivity.this, "Scanning initiated successfully!", Toast.LENGTH_SHORT).show();
                        updateURL();
                        buildLogBatch();
                        fetchClassificationCounts();
                        selectedBatches.clear();

                    } else {
                        Toast.makeText(ScanActivity.this, "Error initiating scanning: " + responseCode, Toast.LENGTH_SHORT).show();
                    }
                    setScreenTouchable(true);
                });
            } catch (Exception e) {
                Log.e(TAG, "Scan initiation failed: ", e);
                handler.post(() -> {
                    Toast.makeText(ScanActivity.this, "Exception: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    setScreenTouchable(true);
                });
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        });
    }

    private void updateURL() {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        for (String batch : selectedBatches) {
            for (String category : categories) {
                db.collection("Users")
                        .document(Objects.requireNonNull(user).getUid())
                        .collection(category)
                        .document(batch)
                        .collection("images")
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String imageName = document.getId();
                                    StorageReference imageRef = storageRef.child("Users/"+ Objects.requireNonNull(user).getUid() + "/" + category  + "/" + imageName);
                                    imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> db.collection("Users")
                                            .document(Objects.requireNonNull(user).getUid())
                                            .collection(category)
                                            .document(batch)
                                            .collection("images")
                                            .document(imageName)
                                            .update("imageUrl", downloadUri.toString()))
                                            .addOnFailureListener(e -> showToast("Failed to update image url: " + e.getMessage(), getApplicationContext()));
                                }
                            } else {
                                Log.d(TAG, "Error getting documents: ", task.getException());
                            }
                        });
            }
        }
    }

    private void handleImageSelection(Intent data) {
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

    private void askForBatchName(Activity context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter Batch Name");

        // Set up the input
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            String batchName = input.getText().toString();
            if (!batchName.isEmpty()) {
                handleBatchName(batchName);
            } else {
                Toast.makeText(context, "Batch name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());


        builder.show();

    }

    private void handleBatchName(String batchName) {
        Log.d("BatchName", "Received batch name: " + batchName);
        batchId = batchName;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        imagePickerLauncher.launch(intent);
    }



    private void askForBatchNameDrone(Activity context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter Batch Name");

        // Set up the input
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            String batchName = input.getText().toString();
            if (!batchName.isEmpty()) {
                handleBatchNameDrone(batchName);
            } else {
                Toast.makeText(context, "Batch name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();

    }

    private void handleBatchNameDrone(String batchName) {
        Log.d("BatchName", "Received batch name: " + batchName);
        batchId = batchName;
        showToast(String.valueOf(mediaFileList.size()), getApplicationContext());
        progressBar.setMax(mediaFileList.size());
        startUploadService();
    }

    private void upLoadImages(Uri selectedImageUri, int totalFiles) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String imageName = UUID.randomUUID().toString();
        StorageReference imageRef = storageRef.child("Users/"+ Objects.requireNonNull(user).getUid() + "/unclassified/" + imageName);
        imageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }

                            Map<String, Object> docData = new HashMap<>();
                            docData.put("imageUrl", downloadUri.toString());
                            docData.put("imageName", imageName);
                            docData.put("classTag", "unclassified");


                            try {
                                InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
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
                            batchDocData.put("timestamp", batchTimeStamp);

                            FirebaseFirestore.getInstance()
                                    .collection("Users")
                                    .document(user.getUid())
                                    .collection("unclassified")
                                    .document(batchId)
                                    .set(batchDocData)
                                    .addOnSuccessListener(documentReference -> {
                                    })
                                    .addOnFailureListener(e -> showToast("Error adding document: " + e.getMessage(), getApplicationContext()));

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
                                    })
                                    .addOnFailureListener(e -> showToast("Error adding document: " + e.getMessage(), getApplicationContext()));
                        })
                        .addOnFailureListener(e -> showToast("Image upload failed: " + e.getMessage(), getApplicationContext())));

        updateSuccessfulUploads(totalFiles);
    }


    private void updateSuccessfulUploads(int totalFiles){
        // After each file download completion counting the successful uploads and updating the recycler view
        successfulUploads++;
        if(successfulUploads == totalFiles){
            selectedBatches.clear();
            selectedBatches.add(batchId);
            buildLogBatch();
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
        String logMessage = logBatch.toString();
        LogEntry logEntry = new LogEntry(logMessage ,message);
        uploadListToFirestore(logEntry);
        logBatch = new StringBuilder();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(this::updateLogInView, 5000);
    }

    private void buildLogBatch() {
        for (int i = 0; i < selectedBatches.size(); i++) {
            if (i > 0) logBatch.append(", "); // This will add a comma before each name except the first
            logBatch.append(selectedBatches.get(i)); // selectedBatches is a List of Strings
        }
        selectedBatches.clear();
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


    public interface FirestoreCallback {
        void onComplete(List<LogEntry> result);
        void onError(String message);
    }


    // Helper method to update the UI with the image count
    private void updateImageCountUI(int imageCount) {
         String message;
        if (imageCount == 1) {
            message = imageCount  + " image uploaded";
        }else {
            message = imageCount  + " images uploaded";
        }
        String logMessage = logBatch.toString();
        LogEntry logEntry = new LogEntry(logMessage, message);
        uploadListToFirestore(logEntry);
        logBatch = new StringBuilder();

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(this::updateLogInView, 5000);
    }



    public void updateLogInView() {
        LogAdapter adapter = (LogAdapter) rvLogEntry.getAdapter();

        fetchData(new FirestoreCallback() {
            @Override
            public void onComplete(List<LogEntry> result) {
                //syncing the adapter with the new data
                LogEntry element = result.get(result.size() - 1);
                if (adapter != null) {
                    showToast("Log size: " + result.size(), getApplicationContext());
                    adapter.updateData(element);
                }
                //scroll to the top to show the newest entry
                rvLogEntry.scrollToPosition(result.size() - 1);
            }

            @Override
            public void onError(String message) {
                // Handle any errors, e.g., show an error message
                Toast.makeText(getApplicationContext(), "Error: " + message, Toast.LENGTH_SHORT).show();
            }
        });

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
                askForBatchNameDrone(this);
            } else {
                // Notify the user that some permissions were denied
                showToast("Some permissions were denied.", getApplicationContext());
                askForBatchNameDrone(this);
            }
        }
    }


    // Initialize the BroadcastReceiver of the downloadBar
    private final BroadcastReceiver fromUploadServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case ACTION_START_LOADING:
                        progressBar.setVisibility(View.VISIBLE);
                        setScreenTouchable(false);
                        break;
                    case ACTION_STOP_LOADING:
                        String messageSTOP_LOADING = intent.getStringExtra("message");
                        int imageCount = intent.getIntExtra("imageCount", 0);
                        selectedBatches.clear();
                        selectedBatches.add(intent.getStringExtra("batchID"));
                        buildLogBatch();
                        updateImageCountUI(imageCount);
                        Toast.makeText(context, messageSTOP_LOADING, Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.INVISIBLE);
                        setScreenTouchable(true);
                        initMediaManager();
                        break;
                    case ACTION_START_LOADING_PER_IMAGE:
                        progressBarPerImage.setVisibility(View.VISIBLE);
                        break;
                    case ACTION_STOP_LOADING_PER_IMAGE:
                        progressBarPerImage.setVisibility(View.INVISIBLE);
                        break;
                    case ACTION_NOTIFY_TRIES:
                        String messageNOTIFY_TRIES = intent.getStringExtra("message");
                        Toast.makeText(context, messageNOTIFY_TRIES, Toast.LENGTH_SHORT).show();
                    case ACTION_PROGRESS_UPDATE:
                        int progressPROGRESS_UPDATE = intent.getIntExtra("progress", 0);
                        String messagePROGRESS_UPDATE = intent.getStringExtra("message");
                        Toast.makeText(context, messagePROGRESS_UPDATE, Toast.LENGTH_SHORT).show();
                        progressBar.setProgress(progressPROGRESS_UPDATE);
                    case ACTION_PROGRESS_UPDATE_PER_IMAGE:
                        int progressUPDATE_PER_IMAGE = intent.getIntExtra("progress", 0);
                        progressBarPerImage.setProgress(progressUPDATE_PER_IMAGE);
                }
            }
        }
    };
    public void setScreenTouchable(boolean touchable) {
        FrameLayout dontTouchOverlay = findViewById(R.id.dontTouchOverlay);
        if (touchable) {
            dontTouchOverlay.setVisibility(View.GONE); // Hide overlay to enable touch events
        } else {
            dontTouchOverlay.setVisibility(View.VISIBLE); // Show overlay to disable touch events
        }
    }


    private void loadBatches() {
        if (user != null) {
            db.collection("Users").document(user.getUid()).collection("unclassified")
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            List<String> batchNames = new ArrayList<>();
                            Log.d("Firestore Success", user.getUid());  // Log each batch ID
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                batchNames.add(document.getId());
                                Log.d("Firestore Success", "Batch ID: " + document.getId());  // Log each batch ID
                            }
                            if (batchNames.isEmpty()) {
                                Log.d("Firestore", "No batches found");
                                Toast.makeText(this, "you have 0 batches to classify", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.d("Firestore", "you have " + batchNames.size() + " batches");
                                showBatchSelectionDialog(batchNames);
                            }
                        } else {
                            Log.d("Firestore Error", "Error getting documents: ", task.getException());
                        }
                    });
        } else {
            Log.d("Firestore", "User not logged in");
            // Consider prompting user or handling login
        }
    }




    private void showBatchSelectionDialog(List<String> batchNames) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Batches");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_batch_selection, null);
        ListView listView = dialogView.findViewById(R.id.listViewBatches);
        CheckBox selectAllCheckbox = dialogView.findViewById(R.id.select_all_checkbox);

        // Set up the adapter for the ListView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, batchNames);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < batchNames.size(); i++) {
                listView.setItemChecked(i, isChecked);
            }
        });

        builder.setView(dialogView);

        builder.setPositiveButton("OK", (dialog, which) -> {
            // Handle OK
            SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
            selectedBatches.clear();
            for (int i = 0; i < batchNames.size(); i++) {
                if (checkedItems.get(i)) {
                    selectedBatches.add(batchNames.get(i));
                }
            }
            Toast.makeText(this, "You selected: " + selectedBatches.size() + " batches", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> selectedBatches.clear());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showClassifiedBatchSelectionDialog(List<String> classifiedBatchNames) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Batches");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_batch_selection_gallery, null);
        ListView listView = dialogView.findViewById(R.id.listViewBatchesGallery);

        // Set up the adapter for the ListView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, classifiedBatchNames);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        builder.setView(dialogView);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String batchId;
            int checkedItem = listView.getCheckedItemPosition();
            if (checkedItem >= 0) {  // Ensure an item is actually selected
                batchId = classifiedBatchNames.get(checkedItem);
                Toast.makeText(this, "You selected: " + batchId, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(ScanActivity.this, GalleryActivity.class);
                intent.putExtra("batchId", batchId);
                startActivity(intent);
                finish();
            }else {
                // Handle the case where no selection was made
                Toast.makeText(this, "No batch selected!", Toast.LENGTH_SHORT).show();
            }

        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
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
        filter.addAction(ACTION_START_LOADING);
        filter.addAction(ACTION_STOP_LOADING);
        filter.addAction(ACTION_NOTIFY_TRIES);
        filter.addAction(ACTION_START_LOADING_PER_IMAGE);
        filter.addAction(ACTION_STOP_LOADING_PER_IMAGE);
        filter.addAction(ACTION_PROGRESS_UPDATE_PER_IMAGE);
        filter.addAction(ACTION_PROGRESS_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fromUploadServiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(fromUploadServiceReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(fromUploadServiceReceiver);
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
