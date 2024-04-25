package com.dji.ImportSDKDemo.NavigationBarActivities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dji.ImportSDKDemo.CameraHandler;
import com.dji.ImportSDKDemo.GoFlyActivity;
import com.dji.ImportSDKDemo.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.realname.AircraftBindingState;
import dji.common.realname.AppActivationState;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.realname.AppActivationManager;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;
import dji.thirdparty.afinal.core.AsyncTask;


public class FlyActivity extends AppCompatActivity implements View.OnClickListener {
    private ConstraintLayout buttonPanel;
    private static final String TAG = FlyActivity.class.getName();
    //a boolean flag to track SDK registration status
    private boolean isSDKRegistered = false;
    private AppActivationManager appActivationManager;
    private AppActivationState.AppActivationStateListener activationStateListener;
    private AircraftBindingState.AircraftBindingStateListener bindingStateListener;

    private static final String[] REQUIRED_PERMISSION_LIST = new String[] {
            Manifest.permission.VIBRATE, // Gimbal rotation
            Manifest.permission.INTERNET, // API requests
            Manifest.permission.ACCESS_WIFI_STATE, // WIFI connected products
            Manifest.permission.ACCESS_COARSE_LOCATION, // Maps
            Manifest.permission.ACCESS_NETWORK_STATE, // WIFI connected products
            Manifest.permission.ACCESS_FINE_LOCATION, // Maps
            Manifest.permission.CHANGE_WIFI_STATE, // Changing between WIFI and USB connection
            //Manifest.permission.WRITE_EXTERNAL_STORAGE, // Log files
            Manifest.permission.BLUETOOTH, // Bluetooth connected products
            Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
            //Manifest.permission.READ_EXTERNAL_STORAGE, // Log files
            Manifest.permission.RECORD_AUDIO, // Speaker accessory
            Manifest.permission.ACCESS_MEDIA_LOCATION, // media files location

    };
    private final AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;
    protected Button loginBtn, logoutBtn;
    protected TextView bindingStateTV, appActivationStateTV;
    private EditText bridgeModeEditText;

    private static final String LAST_USED_BRIDGE_IP = "bridgeip";

    TextView mTextConnectionStatus;
    TextView mTextProduct;
    private Button mBtnOpen;
    private static boolean isAppStarted = false;


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSDKAndCheckPermissions();

        //Initialize DJI SDK Manager
        new Handler(Looper.getMainLooper());

        setContentView(R.layout.activity_fly);

        initUI();

        IntentFilter filter = new IntentFilter(DJISDKManager.USB_ACCESSORY_ATTACHED);
        registerReceiver(usbAccessoryReceiver, filter);


    }

    private final BroadcastReceiver usbAccessoryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DJISDKManager.USB_ACCESSORY_ATTACHED.equals(intent.getAction())) {
                connectToProduct();
            }
        }
    };




    public void setSDKAndCheckPermissions(){
        checkAndRequestPermissions();
    }



    @Override
    public void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        setUpListener();
        IntentFilter filter = new IntentFilter(DJISDKManager.USB_ACCESSORY_ATTACHED);
        registerReceiver(usbAccessoryReceiver, filter);
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
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
        super.onDestroy();
        Log.e(TAG, "onDestroy");
        tearDownListener();
        unregisterReceiver(usbAccessoryReceiver);
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
                startActivity(new Intent(this, ClassificationActivity.class));

            } else if (itemId == R.id.nav_gallery) {
                startActivity(new Intent(this, GalleryActivity.class));

            } else if (itemId == R.id.nav_profile) {
                //startActivity(new Intent(this, ProfileActivity.class));
            }
            return true;
        });

        bindingStateTV = findViewById(R.id.tv_binding_state_info);
        appActivationStateTV = findViewById(R.id.tv_activation_state_info);
        loginBtn = findViewById(R.id.btn_login);
        logoutBtn = findViewById(R.id.btn_logout);
        loginBtn.setOnClickListener(this);
        logoutBtn.setOnClickListener(this);
        mTextConnectionStatus = findViewById(R.id.text_connection_status);
        mTextProduct = findViewById(R.id.text_product_info);

        TextView mVersionTv = findViewById(R.id.tv_version);
        mVersionTv.setText(getResources().getString(R.string.sdk_version, DJISDKManager.getInstance().getSDKVersion()));

        buttonPanel = findViewById(R.id.buttonPanel);

       /* Button checkCameraModeBtn = findViewById(R.id.btnCheckMode);
        checkCameraModeBtn.setOnClickListener(v -> checkCameraMode());*/


        mBtnOpen = findViewById(R.id.btn_open);
        mBtnOpen.setOnClickListener(this);
        mBtnOpen.setEnabled(false);

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

    @SuppressLint("SetTextI18n")
    public void refreshSDKRelativeUI() {
        BaseProduct mProduct = CameraHandler.getProductInstance();

        runOnUiThread(() -> {
            if (null != mProduct && mProduct.isConnected()) {
                Log.v(TAG, "refreshSDK: True");
                Toast.makeText(this, "refreshSDK: True", Toast.LENGTH_SHORT).show();
                mBtnOpen.setEnabled(true);

                String str = mProduct instanceof Aircraft ? "DJIAircraft" : "DJIHandHeld";
                mTextConnectionStatus.setText("Status: " + str + " connected");

                if (null != mProduct.getModel()) {
                    mTextProduct.setText(mProduct.getModel().getDisplayName());
                } else {
                    mTextProduct.setText(R.string.Empty_String);
                }

            } else {
                Log.v(TAG, "refreshSDK: False");
                Toast.makeText(this, "refreshSDK: False", Toast.LENGTH_SHORT).show();
                mBtnOpen.setEnabled(false);
                mTextProduct.setText(R.string.Empty_String);
                mTextConnectionStatus.setText(R.string.connection_loose);
            }
        });
    }



    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btn_login) {
            loginAccount();
        }if (id == R.id.btn_logout) {
            logoutAccount();
        }else if (v.getId() == R.id.btn_open) {
            mBtnOpen.setSelected(true);
            buttonPanel.setEnabled(false);
            Intent intent = new Intent(this, GoFlyActivity.class);
            startActivity(intent);
            mBtnOpen.setSelected(false);
            buttonPanel.setEnabled(true);

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




    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    // checkAndRequestPermissions: Checks for missing permissions and requests them if necessary.
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        // Check each permission in your list
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(eachPermission);
            }
        }

        // Check for notification permission on Android Tiramisu (API level 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Check for notification permission on Android Tiramisu (API level 30) and above

        // Check for notification permission on Android Tiramisu (API level 30) and above

        // If there are permissions that need to be requested, request them
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSION_CODE);
        } else {
            // All permissions are granted, proceed with your functionality
            startSDKRegistration();
        }
    }

    /**
     * Result of runtime permission request
     */
    // onRequestPermissionsResult: Handles the result of the permission request.


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
                startSDKRegistration();
            } else {
                // Notify the user that some permissions were denied
                showToast("Some permissions were denied.");
                checkAndRequestPermissions();
            }
        }
    }



    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(() -> {
                showToast("registering, pls wait...");
                DJISDKManager.getInstance().registerApp(FlyActivity.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                    @Override
                    public void onRegister(DJIError djiError) {
                        if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                            // SDK registration successful
                            isSDKRegistered = true;
                            showToast("Register Success");
                            DJISDKManager.getInstance().startConnectionToProduct();
                            initData();// Initialize data and listeners

                        } else {
                            // SDK registration failed
                            isSDKRegistered = false;
                            showToast("Register sdk fails, please check the bundle id and network connection!");
                        }
                        Log.v(TAG, djiError.getDescription());
                    }

                    @Override
                    public void onProductDisconnect() {
                        Log.d(TAG, "onProductDisconnect");
                        showToast("Product Disconnected");
                        refreshSDKRelativeUI();

                    }
                    @Override
                    public void onProductConnect(BaseProduct baseProduct) {
                        Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                        showToast("Product Connected");
                        refreshSDKRelativeUI();

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
            });
        }
    }


    // Function to connect to the product
    private void connectToProduct() {
        // Check if SDK registration is finished
        if (isSDKRegistered) {
            AsyncTask.execute(() -> {
                showToast("Connecting to product, please wait...");
                DJISDKManager.getInstance().startConnectionToProduct();
            });
        } else {
            showToast("SDK registration not finished");
        }
    }



    private void showToast(final String toastMsg) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_SHORT).show());

    }

    @SuppressLint("SetTextI18n")
    private void initData(){
        setUpListener();

        appActivationManager = DJISDKManager.getInstance().getAppActivationManager();

        if (appActivationManager != null) {
            appActivationManager.addAppActivationStateListener(activationStateListener);
            appActivationManager.addAircraftBindingStateListener(bindingStateListener);
            FlyActivity.this.runOnUiThread(() -> {
                appActivationStateTV.setText("" + appActivationManager.getAppActivationState());
                bindingStateTV.setText("" + appActivationManager.getAircraftBindingState());
            });
        }

    }
    @SuppressLint("SetTextI18n")
    private void setUpListener() {
        // Example of Listener
        activationStateListener = appActivationState -> FlyActivity.this.runOnUiThread(() -> appActivationStateTV.setText("" + appActivationState));

        bindingStateListener = bindingState -> FlyActivity.this.runOnUiThread(() -> bindingStateTV.setText("" + bindingState));
    }

    @SuppressLint("SetTextI18n")
    private void tearDownListener() {
        if (activationStateListener != null) {
            appActivationManager.removeAppActivationStateListener(activationStateListener);
            FlyActivity.this.runOnUiThread(() -> appActivationStateTV.setText("Unknown"));
        }
        if (bindingStateListener !=null) {
            appActivationManager.removeAircraftBindingStateListener(bindingStateListener);
            FlyActivity.this.runOnUiThread(() -> bindingStateTV.setText("Unknown"));
        }
    }

    public static boolean isStarted() {
        return isAppStarted;
    }

    public static void setAppStarted(boolean isStarted) {
        FlyActivity.isAppStarted = isStarted;
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

}