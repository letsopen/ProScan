package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;

import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.scanner.ScanCallbackHolder;
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions;

/**
 * 华为 HMS Core Scan Kit 扫码实现
 * 实际解码由 UnifiedScanActivity 通过 ScanUtil.decodeWithBitmap 完成
 * 参考: https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/android-build-scan-capabilities-0000001050042010
 */
public class HuaweiBarcodeScanner implements BarcodeScanner {

    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        ScanCallbackHolder.set(callback, "HUAWEI_SCAN_KIT");
        Intent intent = new Intent(activity, UnifiedScanActivity.class);
        activity.startActivity(intent);
    }

    @Override
    public String getName() {
        return "Huawei HMS";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        // 1. 首先检查制造商是否为 Huawei 或 Honor
        String manufacturer = android.os.Build.MANUFACTURER;
        if (!"HUAWEI".equalsIgnoreCase(manufacturer) && !"HONOR".equalsIgnoreCase(manufacturer)) {
            return false;
        }

        // 2. 检查是否在华为设备上，或者是否安装了HMS Core
        try {
            // 简单检查：尝试创建扫码选项
            HmsScanAnalyzerOptions options = new HmsScanAnalyzerOptions.Creator().create();
            return options != null;
        } catch (NoClassDefFoundError | Exception e) {
            // 如果类不存在，说明HMS Core未安装或不可用
            return false;
        }
    }
}
