package com.dji.ImportSDKDemo.Receivers;

import static dji.midware.usb.P3.DJIUsbAccessoryReceiver.ACTION_USB_ACCESSORY_ATTACHED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dji.ImportSDKDemo.ApplicationState;
import com.dji.ImportSDKDemo.PermissionActivity;



// OnDJIUSBAttachedReceiver: BroadcastReceiver that handles the USB accessory attached event for DJI devices.
public class OnDJIUSBAttachedReceiver extends BroadcastReceiver {

    // onReceive: Called when the BroadcastReceiver is receiving an Intent broadcast.
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction())) { // Replace with actual USB action if different
            if (!ApplicationState.isAppStarted) {
                Intent newIntent = new Intent(context, PermissionActivity.class);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(newIntent);
            }
        }
    }

}
