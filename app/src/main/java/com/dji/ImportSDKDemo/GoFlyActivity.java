package com.dji.ImportSDKDemo;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import java.util.Locale;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.useraccount.UserAccountManager;

//TODO: Handle API level 30;
public class GoFlyActivity extends Activity implements SurfaceTextureListener,OnClickListener{

    private static final String TAG = FlyActivity.class.getName();
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;

    // Codec for video live view
    protected DJICodecManager mCodecManager = null;

    protected TextureView mVideoSurface = null;
    private TextView recordingTime;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go_fly);

        handler = new Handler();

        initUI();

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataListener = (videoBuffer, size) -> {
            if (mCodecManager != null) {
                mCodecManager.sendDataToDecoder(videoBuffer, size);
            }
        };

        Camera camera = CameraHandler.getCameraInstance();

        if (camera != null) {

            camera.setSystemStateCallback(cameraSystemState -> {
                int recordTime = cameraSystemState.getCurrentVideoRecordingTimeInSeconds();
                int minutes = (recordTime % 3600) / 60;
                int seconds = recordTime % 60;

                final String timeString = String.format(Locale.US, "%02d:%02d", minutes, seconds);
                final boolean isVideoRecording = cameraSystemState.isRecording();

                GoFlyActivity.this.runOnUiThread(() -> {

                    recordingTime.setText(timeString);

                    /*
                     * Update recordingTime TextView visibility and mRecordBtn's check state
                     */
                    if (isVideoRecording){
                        recordingTime.setVisibility(View.VISIBLE);
                    }else
                    {
                        recordingTime.setVisibility(View.INVISIBLE);
                    }
                });
            });

        }

    }

    protected void onProductChange() {
        initPreviewer();
        loginAccount();
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        onProductChange();

        if(mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        super.onDestroy();
    }

    private void initUI() {
        // init mVideoSurface
        mVideoSurface = findViewById(R.id.video_previewer_surface);

        recordingTime = findViewById(R.id.timer);
        Button mCaptureBtn = findViewById(R.id.btn_capture); // Initialize mCaptureBtn
        ToggleButton mRecordBtn = findViewById(R.id.btn_record);
        Button mShootPhotoModeBtn = findViewById(R.id.btn_shoot_photo_mode);
        Button mRecordVideoModeBtn = findViewById(R.id.btn_record_video_mode);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

        mCaptureBtn.setOnClickListener(this);
        mRecordBtn.setOnClickListener(this);
        mShootPhotoModeBtn.setOnClickListener(this);
        mRecordVideoModeBtn.setOnClickListener(this);

        recordingTime.setVisibility(View.INVISIBLE);

        mRecordBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startRecord();
            } else {
                stopRecord();
            }
        });
    }


    private void initPreviewer() {

        BaseProduct product = CameraHandler.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void uninitPreviewer() {
        Camera camera = CameraHandler.getCameraInstance();
        if (camera != null){
            // Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(GoFlyActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btn_capture) {
            captureAction();
        } else if (id == R.id.btn_shoot_photo_mode) {
            if (isMavicAir2() || isM300()) {
                switchCameraFlatMode(SettingsDefinitions.FlatCameraMode.PHOTO_SINGLE);
            } else {
                switchCameraMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO);
            }
        } else if (id == R.id.btn_record_video_mode) {
            if (isMavicAir2() || isM300()) {
                switchCameraFlatMode(SettingsDefinitions.FlatCameraMode.VIDEO_NORMAL);
            } else {
                switchCameraMode(SettingsDefinitions.CameraMode.RECORD_VIDEO);
            }
        }
    }


    private void switchCameraFlatMode(SettingsDefinitions.FlatCameraMode flatCameraMode){
        Camera camera = CameraHandler.getCameraInstance();
        if (camera != null) {
            camera.setFlatMode(flatCameraMode, error -> {
                if (error == null) {
                    showToast("Switch Camera Flat Mode Succeeded");
                } else {
                    showToast(error.getDescription());
                }
            });
        }
    }

    private void switchCameraMode(SettingsDefinitions.CameraMode cameraMode){
        Camera camera = CameraHandler.getCameraInstance();
        if (camera != null) {
            camera.setMode(cameraMode, error -> {
                if (error == null) {
                    showToast("Switch Camera Mode Succeeded");
                } else {
                    showToast(error.getDescription());
                }
            });
        }
    }

    // Method for taking photo
    private void captureAction(){
        final Camera camera = CameraHandler.getCameraInstance();
        if (camera != null) {
            if (isMavicAir2() || isM300()) {
                camera.setFlatMode(SettingsDefinitions.FlatCameraMode.PHOTO_SINGLE, djiError -> {
                    if (null == djiError) {
                        takePhoto();
                    }
                });
            }else {
                camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE, djiError -> {
                    if (null == djiError) {
                        takePhoto();
                    }
                });
            }
        }
    }

    private void takePhoto(){
        final Camera camera = CameraHandler.getCameraInstance();
        if (camera == null){
            return;
        }
        handler.postDelayed(() -> camera.startShootPhoto(djiError -> {
            if (djiError == null) {
                showToast("take photo: success");
            } else {
                showToast(djiError.getDescription());
            }
        }), 2000);
    }

    // Method for starting recording
    private void startRecord(){

        final Camera camera = CameraHandler.getCameraInstance();
        if (camera != null) {
            camera.startRecordVideo(djiError -> {
                if (djiError == null) {
                    showToast("Record video: success");
                }else {
                    showToast(djiError.getDescription());
                }
            }); // Execute the startRecordVideo API
        }
    }

    // Method for stopping recording
    private void stopRecord(){

        Camera camera = CameraHandler.getCameraInstance();
        if (camera != null) {
            camera.stopRecordVideo(djiError -> {
                if(djiError == null) {
                    showToast("Stop recording: success");
                }else {
                    showToast(djiError.getDescription());
                }
            }); // Execute the stopRecordVideo API
        }
    }

    private boolean isMavicAir2(){
        BaseProduct baseProduct = CameraHandler.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.MAVIC_AIR_2;
        }
        return false;
    }

    private boolean isM300(){
        BaseProduct baseProduct = CameraHandler.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.MATRICE_300_RTK;
        }
        return false;
    }
}