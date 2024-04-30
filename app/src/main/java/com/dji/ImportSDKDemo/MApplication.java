package com.dji.ImportSDKDemo;

import static dji.midware.usb.P3.DJIUsbAccessoryReceiver.ACTION_USB_ACCESSORY_ATTACHED;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import com.dji.ImportSDKDemo.Receivers.OnDJIUSBAttachedReceiver;
import com.secneo.sdk.Helper;

public class MApplication extends Application {
    private BroadcastReceiver br;

    @Override
    public void onCreate() {
        super.onCreate();
        br = new OnDJIUSBAttachedReceiver();
        IntentFilter filter = new IntentFilter(ACTION_USB_ACCESSORY_ATTACHED);
        registerReceiver(br, filter);
    }

    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(MApplication.this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterReceiver(br);
    }
}
