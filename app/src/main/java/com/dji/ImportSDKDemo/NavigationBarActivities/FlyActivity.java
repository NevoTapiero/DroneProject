package com.dji.ImportSDKDemo.NavigationBarActivities;

import static com.dji.ImportSDKDemo.Services.DJISDKService.PRODUCT_CONNECTED;
import static com.dji.ImportSDKDemo.Services.DJISDKService.PRODUCT_DISCONNECTED;
import static com.dji.ImportSDKDemo.Services.DJISDKService.REGISTERED;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.ImportSDKDemo.BaseActivity;
import com.dji.ImportSDKDemo.CameraHandler;
import com.dji.ImportSDKDemo.GoFlyActivity;
import com.dji.ImportSDKDemo.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;


public class FlyActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = FlyActivity.class.getName();
    protected Button loginBtn, logoutBtn, mBtnOpen;
    protected TextView mTextConnectionStatus, mTextProduct;
    private EditText bridgeModeEditText;
    private static final String LAST_USED_BRIDGE_IP = "bridgeip";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fly);
        initUI();

        //Initialize DJI SDK Manager
        new Handler(Looper.getMainLooper());

    }





    private void initUI(){

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_fly);


        // Set listener for navigation item selection using if-else instead of switch
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            //noinspection StatementWithEmptyBody
            if (itemId == R.id.nav_fly) {
            } else if (itemId == R.id.nav_scan) {
                startActivity(new Intent(this, ScanActivity.class));

            } else if (itemId == R.id.nav_gallery) {
                startActivity(new Intent(this, GalleryActivity.class));

            } else if (itemId == R.id.nav_profile) {
                //startActivity(new Intent(this, ProfileActivity.class));
            }
            return true;
        });

        mTextConnectionStatus = findViewById(R.id.text_connection_status);
        mTextProduct = findViewById(R.id.text_product_info);

        TextView mVersionTv = findViewById(R.id.tv_version);
        mVersionTv.setText(getResources().getString(R.string.sdk_version, DJISDKManager.getInstance().getSDKVersion()));

       /* Button checkCameraModeBtn = findViewById(R.id.btnCheckMode);
        checkCameraModeBtn.setOnClickListener(v -> checkCameraMode());*/


        mBtnOpen = findViewById(R.id.btn_open);
        mBtnOpen.setOnClickListener(this);
        mBtnOpen.setEnabled(CameraHandler.getProductInstance() != null);

        bridgeModeEditText = findViewById(R.id.edittext_bridge_ip);
        SharedPreferences sharedPreferences = this.getSharedPreferences("NPreference", Context.MODE_PRIVATE);
        sharedPreferences.edit().putString(LAST_USED_BRIDGE_IP, "").apply();
        bridgeModeEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if (event != null && event.isShiftPressed()) {
                    return false;
                } else {
                    // the user is done typing.
                    handleBridgeIPTextChange();
                }
            }
            return false; // pass on to other listeners.
        });
        bridgeModeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.toString().contains("\n")) {
                    // the user is done typing.
                    // remove new line character
                    final String currentText = bridgeModeEditText.getText().toString();
                    bridgeModeEditText.setText(currentText.substring(0, currentText.indexOf('\n')));
                    handleBridgeIPTextChange();
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_open) {
            Intent intent = new Intent(this, GoFlyActivity.class);
            startActivity(intent);
        }
    }

    // handleBridgeIPTextChange: Handles changes to the Bridge Mode IP address text field.
    private void handleBridgeIPTextChange() {
        // Process the entered IP address for Bridge Mode and save it to SharedPreferences.

        // the user is done typing.
        final String bridgeIP = bridgeModeEditText.getText().toString();

        if (!TextUtils.isEmpty(bridgeIP)) {
            DJISDKManager.getInstance().enableBridgeModeWithBridgeAppIP(bridgeIP);
            Toast.makeText(getApplicationContext(),"BridgeMode ON!\nIP: " + bridgeIP,Toast.LENGTH_SHORT).show();
            SharedPreferences sharedPreferences = this.getSharedPreferences("NPreference", Context.MODE_PRIVATE);
            sharedPreferences.edit().putString(LAST_USED_BRIDGE_IP, bridgeIP).apply();
        }
    }

    private void showToast(final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_SHORT).show());

    }

    private final BroadcastReceiver droneStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (REGISTERED.equals(intent.getAction())) {
                // Logic to execute when product is disconnected
                Toast.makeText(context, "SDK Registered", Toast.LENGTH_SHORT).show();
            } else if (PRODUCT_CONNECTED.equals(intent.getAction())) {
                // Logic to execute when product is disconnected
                Toast.makeText(context, "Product connected", Toast.LENGTH_SHORT).show();
            } else if (PRODUCT_DISCONNECTED.equals(intent.getAction())) {
                // Logic to execute when product is disconnected
                Toast.makeText(context, "Product disconnected", Toast.LENGTH_SHORT).show();
            }
        }
    };


    @Override
    public void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();

        IntentFilter droneStatusFilter = new IntentFilter();
        droneStatusFilter.addAction(PRODUCT_DISCONNECTED);
        droneStatusFilter.addAction(REGISTERED);
        droneStatusFilter.addAction(PRODUCT_CONNECTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(droneStatusReceiver, droneStatusFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(droneStatusReceiver, droneStatusFilter);
        }


    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
        // Unregister the receiver
        unregisterReceiver(droneStatusReceiver);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy");
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


    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        showToast("Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });

    }

    private void logoutAccount(){
        UserAccountManager.getInstance().logoutOfDJIUserAccount(error -> {
            if (null == error) {
                showToast("Logout Success");
            } else {
                showToast("Logout Error:"
                        + error.getDescription());
            }
        });
    }
}