package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;

import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.scanner.ScanCallbackHolder;
import com.huawei.hms.api.ConnectionResult;
import com.huawei.hms.api.HuaweiApiAvailability;

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
        activity.startActivityForResult(intent, 2);
    }

    @Override
    public String getName() {
        return "HUAWEI HMS";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        // 以 HMS Core 实际服务状态为准，不限制设备品牌。
        // 未安装、版本不兼容、被禁用或需更新时均隐藏入口，避免选中后才发现不可用。
        try {
            int status = HuaweiApiAvailability.getInstance()
                    .isHuaweiMobileServicesAvailable(activity.getApplicationContext());
            return status == ConnectionResult.SUCCESS;
        } catch (Throwable t) {
            // 服务 SDK 初始化异常时安全回落到其他引擎。
            return false;
        }
    }
}
