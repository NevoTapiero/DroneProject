package com.dji.ImportSDKDemo.Services;

import static com.dji.ImportSDKDemo.ApplicationState.isAppStarted;
import static com.dji.ImportSDKDemo.Receivers.OnDJIUSBAttachedReceiver.USB_ACCESSORY_ATTACHED;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.dji.ImportSDKDemo.R;

import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.thirdparty.afinal.core.AsyncTask;


public class DJISDKService extends Service {
    private NotificationManager notificationManager;
    private static final String CHANNEL_ID = "DJI_SDK_CHANNEL";
    private static final String TAG = DJISDKService.class.getName();
    private final AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    public static final String PRODUCT_DISCONNECTED = "com.dji.ImportSDKDemo.action.PRODUCT_DISCONNECTED";
    public static final String PRODUCT_CONNECTED = "com.dji.ImportSDKDemo.action.PRODUCT_CONNECTED";
    public static final String REGISTERED = "com.dji.ImportSDKDemo.action.REGISTERED";


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter(USB_ACCESSORY_ATTACHED);
        registerReceiver(usbAttachedReceiver, filter);

        isAppStarted = true;
        //Use of notificationManager in API level 23 and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            notificationManager = getSystemService(NotificationManager.class);
        } else { // Use of notificationManager in API level 22 and below
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }


        createNotificationChannel();

        updateNotification();

        startSDKRegistration();
    }
    private final BroadcastReceiver usbAttachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (USB_ACCESSORY_ATTACHED.equals(intent.getAction())) {
                // Handle the USB accessory attached event
                handleUsbAccessoryAttached();
            }
        }
    };

    private void handleUsbAccessoryAttached() {
        startSDKRegistration();
    }


    private void updateNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DJI Connection Service")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_drone)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true) // To avoid sound and vibration on every update
                .build();
        // Start foreground with the updated notification
        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        // Check if we're running on Android Oreo or higher
        //noinspection StatementWithEmptyBody
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // The user-visible name of the channel.
            CharSequence name = getString(R.string.channel_name);
            // The user-visible description of the channel.
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(channel);
        } else {
            // updateNotification() will be called anyway
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(() -> DJISDKManager.getInstance().registerApp(DJISDKService.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                @Override
                public void onRegister(DJIError djiError) {
                    if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                        // SDK registration successful
                        DJISDKManager.getInstance().startConnectionToProduct();
                        showToast("Registration Success");
                        // Create a new intent with a custom action to notify that the SDK is registered.
                        Intent attachedIntent = new Intent();
                        attachedIntent.setAction(REGISTERED);
                        // Broadcast the intent to notify other components in the app.
                        sendBroadcast(attachedIntent);
                    }
                    Log.v(TAG, djiError.getDescription());
                }

                @Override
                public void onProductDisconnect() {
                    Log.d(TAG, "onProductDisconnect");
                    showToast("Product Disconnected");
                    // Create a new intent with a custom action to notify that the drone disconnected.
                    Intent attachedIntent = new Intent();
                    attachedIntent.setAction(PRODUCT_DISCONNECTED);
                    // Broadcast the intent to notify other components in the app.
                    sendBroadcast(attachedIntent);
                }
                @Override
                public void onProductConnect(BaseProduct baseProduct) {
                    Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                    showToast("Product Connected");
                    // Create a new intent with a custom action to notify that the drone disconnected.
                    Intent attachedIntent=new Intent();
                    attachedIntent.setAction(PRODUCT_CONNECTED);
                    // Broadcast the intent to notify other components in the app.
                    sendBroadcast(attachedIntent);
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
            }));
        }
    }

    private void showToast(final String text) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(DJISDKService.this, text, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroy() {
        DJISDKManager.getInstance().stopConnectionToProduct();
        super.onDestroy();
        unregisterReceiver(usbAttachedReceiver);
        stopForeground(true);
    }
}
