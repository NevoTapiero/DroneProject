package com.dji.ImportSDKDemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dji.ImportSDKDemo.Authentication.SignInActivity;
import com.dji.ImportSDKDemo.Services.DJISDKService;

import java.util.ArrayList;
import java.util.List;

public class PermissionActivity extends BaseActivity {

    private final List<String> permissionsNeeded = new ArrayList<>();
    private static final int REQUEST_PERMISSION_CODE = 12345;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApplicationState.isAppStarted = true;

        permissionsNeeded.add(Manifest.permission.VIBRATE);
        permissionsNeeded.add(Manifest.permission.INTERNET);
        permissionsNeeded.add(Manifest.permission.ACCESS_WIFI_STATE);
        permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionsNeeded.add(Manifest.permission.ACCESS_NETWORK_STATE);
        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsNeeded.add(Manifest.permission.CHANGE_WIFI_STATE);
        permissionsNeeded.add(Manifest.permission.BLUETOOTH);
        permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
        permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);


        String version = Build.VERSION.SDK_INT + "";
        showToast("SDK Version: " + version, this);

        // Check for notification permission on Android 11 and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsNeeded.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        }


        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
        }


        checkAndRequestPermissions();
    }


    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Convert your List to an array for requesting permissions
        String[] permissions = this.permissionsNeeded.toArray(new String[0]);

        // Check each permission in your list
        for (String eachPermission : permissions) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(eachPermission);
            }
        }

        // If there are permissions that need to be requested, request them
        if (allPermissionsGranted()) {
            proceedToApp();
        } else {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSION_CODE);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : permissionsNeeded) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE && grantResults.length > 0) {
            if (allPermissionsGranted()) {
                proceedToApp();
            } else {
                showToast("Permissions not granted by the user.", this);
            }
        }
    }

    private void proceedToApp() {
        Intent serviceIntent = new Intent(this, DJISDKService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        Intent intent = new Intent(this, SignInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        finish();
    }
}
