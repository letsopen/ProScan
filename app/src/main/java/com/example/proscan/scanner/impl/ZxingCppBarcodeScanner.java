package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;

import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.scanner.ScanCallbackHolder;

/**
 * ZXing-CPP 扫码实现（io.github.zxing-cpp:android）
 * 实际解码由 UnifiedScanActivity 通过 ZxingCppFrameDecoder 完成
 */
public class ZxingCppBarcodeScanner implements BarcodeScanner {

    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        ScanCallbackHolder.set(callback, "ZXING_CPP");
        Intent intent = new Intent(activity, UnifiedScanActivity.class);
        activity.startActivityForResult(intent, 2);
    }

    @Override
    public String getName() {
        return "ZXING-CPP";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        // 纯本地实现（含 JNI .so），总是可用
        return true;
    }
}
