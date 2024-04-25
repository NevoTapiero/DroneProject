package com.dji.ImportSDKDemo;

import android.content.Context;
import android.widget.Toast;

public class ShowToast {
    public static void showToast(String message, Context context) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
