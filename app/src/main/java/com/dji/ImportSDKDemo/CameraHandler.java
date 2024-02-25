package com.dji.ImportSDKDemo;

import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.products.Aircraft;
import dji.sdk.products.HandHeld;
import dji.sdk.sdkmanager.DJISDKManager;

public class CameraHandler {
    private static BaseProduct mProduct;

    public synchronized static BaseProduct getProductInstance() {
        if (null == mProduct) {
            mProduct = DJISDKManager.getInstance().getProduct();
        }
        return mProduct;
    }

    public synchronized static Camera getCameraInstance() {
        if (getProductInstance() == null) return null;
        Camera camera = null;
        if (getProductInstance() instanceof Aircraft){
            camera = getProductInstance().getCamera();
        } else if (getProductInstance() instanceof HandHeld) {
            camera = getProductInstance().getCamera();
        }
        return camera;
    }
}
