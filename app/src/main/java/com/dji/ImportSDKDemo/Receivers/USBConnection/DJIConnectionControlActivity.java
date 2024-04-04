package com.dji.ImportSDKDemo.Receivers.USBConnection;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;

/**
 * This activity will launch when a USB accessory is attached and attempt to connect to the USB
 * accessory.
 */

// Activity responsible for handling USB accessory connections, particularly for DJI devices.
public class DJIConnectionControlActivity extends Activity {

    // Constant to define a custom intent action for accessory attachment.
    public static final String ACCESSORY_ATTACHED = "com.dji.ux.sample.ACCESSORY_ATTACHED";


    // onCreate: Initializes the activity and handles the intent for USB accessory connections.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create and set a simple View. This activity does not have a UI and is purely functional.
        setContentView(new View(this));

        // Get the intent that started this activity.
        Intent usbIntent = getIntent();
        if (usbIntent != null) {
            // Get the action from the intent.
            String action = usbIntent.getAction();
            // Check if the action is for a USB accessory that has been attached.
            if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                // Create a new intent with a custom action to notify that the accessory is attached.
                Intent attachedIntent=new Intent();
                attachedIntent.setAction(ACCESSORY_ATTACHED);
                // Broadcast the intent to notify other components in the app.
                sendBroadcast(attachedIntent);
            }
        }

        // Close the activity as its role is just to broadcast the attachment event and nothing else.
        finish();
    }
}