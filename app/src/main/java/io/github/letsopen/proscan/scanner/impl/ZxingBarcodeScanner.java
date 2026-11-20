package io.github.letsopen.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;

import io.github.letsopen.proscan.scanner.BarcodeScanner;
import io.github.letsopen.proscan.scanner.ScanCallbackHolder;

/**
 * ZXing 扫码实现
 * 实际解码由 UnifiedScanActivity 通过 MultiFormatReader 完成
 */
public class ZxingBarcodeScanner implements BarcodeScanner {

    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        ScanCallbackHolder.set(callback, "ZXING");
        Intent intent = new Intent(activity, UnifiedScanActivity.class);
        activity.startActivity(intent);
    }

    @Override
    public String getName() {
        return "ZXing";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        // ZXing 总是可用（只要设备有相机）
        return true;
    }
}
