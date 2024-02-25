package com.dji.ImportSDKDemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dji.sdk.sdkmanager.DJISDKManager;

/**
 * This receiver will detect the USB attached event.
 * It will check if the app has been previously started.
 * In case if the app is already running it will prevent new activity
 * from being started and get the activity in stack in focus
 */

// OnDJIUSBAttachedReceiver: BroadcastReceiver that handles the USB accessory attached event for DJI devices.
public class OnDJIUSBAttachedReceiver extends BroadcastReceiver {

    // onReceive: Called when the BroadcastReceiver is receiving an Intent broadcast.
    @Override
    public void onReceive(Context context, Intent intent) {

        // Check if the MainActivity has been started.
        if (!MainActivity.isStarted()) {
            // If MainActivity is not running, start the app with MainActivity in focus.
            // This ensures that the app starts normally when the USB accessory is attached.
            Intent startIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());

            // Set flags to bring the existing activity instance to the front, or start a new instance if needed.
            startIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(startIntent);

            MainActivity.setAppStarted(true);
        } else {
            // If MainActivity is already running, broadcast a custom intent indicating that the USB accessory is attached.
            // This can be used by the app components to handle the USB accessory connection while the app is running.
            Intent attachedIntent = new Intent();
            attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
            context.sendBroadcast(attachedIntent);
        }

    }
}
