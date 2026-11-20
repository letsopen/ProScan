package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;

import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.scanner.ScanCallbackHolder;

/**
 * ML Kit Barcode Scanning 独立版本实现
 * 不需要 Google Play Services，模型打包在应用中
 * 实际解码由 UnifiedScanActivity 通过 CameraX + ML Kit 完成
 * 参考: https://developers.google.com/ml-kit/vision/barcode-scanning/android
 */
public class MlKitBarcodeScanner implements BarcodeScanner {

    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        ScanCallbackHolder.set(callback, "ML_KIT_STANDALONE");
        Intent intent = new Intent(activity, UnifiedScanActivity.class);
        activity.startActivityForResult(intent, 2);
    }

    @Override
    public String getName() {
        return "Google MLK";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        // ML Kit 独立版本总是可用（模型打包在应用中）
        return true;
    }
}
