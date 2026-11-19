package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;

import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.scanner.ScanCallbackHolder;

/**
 * BoofCV 扫码实现（org.boofcv:boofcv-recognition，纯 Java）
 * 实际解码由 UnifiedScanActivity 通过 BoofCvFrameDecoder 完成
 * 支持 QR Code / Micro QR / Aztec 二维条码
 */
public class BoofCvBarcodeScanner implements BarcodeScanner {

    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        ScanCallbackHolder.set(callback, "BOOFCV");
        Intent intent = new Intent(activity, UnifiedScanActivity.class);
        activity.startActivity(intent);
    }

    @Override
    public String getName() {
        return "BoofCV";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        // 纯 Java 实现，总是可用
        return true;
    }
}
