package com.dji.ImportSDKDemo;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import com.dji.ImportSDKDemo.Receivers.USBConnection.DJIConnectionControlActivity;
import com.dji.ImportSDKDemo.Receivers.USBConnection.OnDJIUSBAttachedReceiver;
import com.secneo.sdk.Helper;

public class MApplication extends Application {

    // onCreate: Called when the application is starting, before any other application objects have been created.
    @Override
    public void onCreate() {
        super.onCreate();

        // Create a BroadcastReceiver to listen for when a USB accessory is attached.
        BroadcastReceiver br = new OnDJIUSBAttachedReceiver();
        // Create an IntentFilter and add an action for accessory attachment.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIConnectionControlActivity.ACCESSORY_ATTACHED);
        // Register the BroadcastReceiver to listen for the accessory attached event.
        registerReceiver(br, filter);

    }


    // attachBaseContext: Called by the system when the application is starting, before onCreate and before the app class loader is created.
    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        // Install the helper for the DJI SDK. The Helper class ensures that the DJI SDK is initialized properly.
        Helper.install(MApplication.this);
    }

}