package com.dji.ImportSDKDemo.Receivers;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dji.ImportSDKDemo.CameraHandler;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.media.MediaManager;

public class OnAppClosedReceiver extends BroadcastReceiver {
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;

    private static final String TAG = "OnAppClosedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Check for the specific action that indicates the app is being closed
        // This is an example using Intent.ACTION_SHUTDOWN, you may need to adjust
        // this depending on your specific needs.
        if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
            logIt("Device is shutting down, app is being closed.");

            setCameraMode();
        }

    }


    private void setCameraMode() {
        BaseProduct product = CameraHandler.getProductInstance();
        if (product != null) {
            if (CameraHandler.getCameraInstance() != null) {
                MediaManager mMediaManager = CameraHandler.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)){
                        DJILog.e(TAG, "Media Manager is busy.");
                    }
                } else {
                    logIt("MediaManager is null");
                }
            }else {
                // Handle the case where the camera instance is null
                logIt("Camera disconnected");
            }
        } else {
            // Handle the case where the product is null
            logIt("Drone Disconnected, please reconnect the drone and try again");
        }
    }


    private void continueToSetCameraMode() {
        BaseProduct product = CameraHandler.getProductInstance();
        if (product != null && CameraHandler.getCameraInstance() != null) {
            CameraHandler.getCameraInstance().getMode(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.CameraMode>() {
                @Override
                public void onSuccess(SettingsDefinitions.CameraMode cameraMode) {
                    if (cameraMode != SettingsDefinitions.CameraMode.SHOOT_PHOTO) {
                        // The camera is currently in playback mode
                        CameraHandler.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                            if (djiError == null) {
                                // Successfully exited playback mode
                                logIt("Exited playback mode");
                            } else {
                                // Handle the error
                                logIt("Exit Playback failed: " + djiError.getDescription());
                            }
                        });
                    }
                }
                @Override
                public void onFailure(DJIError error) {
                    // Handle the error
                    logIt("Get Mode failed: " + error.getDescription());
                }
            });
        }

    }


    public final MediaManager.FileListStateListener updateFileListStateListener = state -> {

        switch (state) {
            case SYNCING:
                // The file list is being synchronized. You might want to show a loading indicator.
                currentFileListState = state;
                // Update UI to show that file list synchronization is in progress
                logIt("Syncing media file list...");
                DJILog.e(TAG, "recalling setCameraMode");

                break;
            case INCOMPLETE:
                // The file list has not been completely synchronized.
                currentFileListState = state;
                logIt("Media file list incomplete.");
                break;
            case UP_TO_DATE:
                // The file list is complete. You can now access the full list of media files.
                currentFileListState = state;
                logIt("Media file list synchronized.");
                continueToSetCameraMode();
                break;
            case DELETING:
                // Files are being deleted from the list.
                currentFileListState = state;
                // Update UI to show that files are currently being deleted.
                logIt("Deleting media files...");
                setCameraMode();
                DJILog.e(TAG, "recalling setCameraMode");
                break;
            case UNKNOWN:
            default:
                // An unknown state.
                currentFileListState = state;
                // Update UI for an unknown state.
                logIt("Unknown media file list state.");
                break;
        }
    };

    private void logIt(String message) {
        Log.d(TAG, message);
    }

}

