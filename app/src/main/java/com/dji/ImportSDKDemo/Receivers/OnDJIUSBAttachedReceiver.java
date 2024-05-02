package com.dji.ImportSDKDemo.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dji.ImportSDKDemo.ApplicationState;
import com.dji.ImportSDKDemo.PermissionActivity;


// OnDJIUSBAttachedReceiver: BroadcastReceiver that handles the USB accessory attached event for DJI devices.
public class OnDJIUSBAttachedReceiver extends BroadcastReceiver {
    public static final String USB_ACCESSORY_ATTACHED_WHEN_APP_STARTED = "com.dji.ImportSDKDemo.action.USB_ACCESSORY_ATTACHED_WHEN_APP_STARTED";

    // onReceive: Called when the BroadcastReceiver is receiving an Intent broadcast.
    @Override
    public void onReceive(Context context, Intent intent) {
            if (!ApplicationState.isAppStarted) {
                Intent newIntent = new Intent(context, PermissionActivity.class);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(newIntent);
            } else {
                Intent attachedIntent = new Intent();
                attachedIntent.setAction(USB_ACCESSORY_ATTACHED_WHEN_APP_STARTED);
                context.sendBroadcast(attachedIntent);
            }

    }

}

