package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;

import com.example.proscan.scanner.BarcodeScanner;
import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions;

import java.lang.ref.WeakReference;

/**
 * 华为 HMS Core Scan Kit 扫码实现
 * 参考: https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/android-build-scan-capabilities-0000001050042010
 */
public class HuaweiBarcodeScanner implements BarcodeScanner {
    
    private static final int REQUEST_CODE_SCAN = 2001;
    private static WeakReference<ScanCallback> currentCallback;
    
    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        // 保存回调引用
        currentCallback = new WeakReference<>(callback);
        
        try {
            // 配置扫码选项
            HmsScanAnalyzerOptions options = new HmsScanAnalyzerOptions.Creator()
                    .setHmsScanTypes(
                            HmsScan.QRCODE_SCAN_TYPE,
                            HmsScan.AZTEC_SCAN_TYPE,
                            HmsScan.DATAMATRIX_SCAN_TYPE,
                            HmsScan.PDF417_SCAN_TYPE,
                            HmsScan.CODE128_SCAN_TYPE,
                            HmsScan.CODE39_SCAN_TYPE,
                            HmsScan.CODE93_SCAN_TYPE,
                            HmsScan.CODABAR_SCAN_TYPE,
                            HmsScan.EAN13_SCAN_TYPE,
                            HmsScan.EAN8_SCAN_TYPE,
                            HmsScan.ITF14_SCAN_TYPE,
                            HmsScan.UPCCODE_A_SCAN_TYPE,
                            HmsScan.UPCCODE_E_SCAN_TYPE
                    )
                    .create();
            
            // 启动扫码
            ScanUtil.startScan(activity, REQUEST_CODE_SCAN, options);
        } catch (Exception e) {
            if (callback != null) {
                callback.onError("启动扫码失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理扫码结果（需要在Activity的onActivityResult中调用）
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data Intent数据
     * @return 是否已处理
     */
    public static boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SCAN) {
            ScanCallback callback = currentCallback != null ? currentCallback.get() : null;
            if (callback == null) {
                return false;
            }
            
            if (resultCode == Activity.RESULT_OK && data != null) {
                HmsScan hmsScan = data.getParcelableExtra(ScanUtil.RESULT);
                if (hmsScan != null && hmsScan.getOriginalValue() != null) {
                    callback.onSuccess(hmsScan.getOriginalValue());
                } else {
                    callback.onError("扫码结果为空");
                }
            } else {
                callback.onCancel();
            }
            
            // 清除回调引用
            currentCallback = null;
            return true;
        }
        return false;
    }
    
    @Override
    public String getName() {
        return "Huawei Scan Kit";
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
            if (options == null) {
                return false;
            }
            
            // 检查HMS Core是否可用（可选，更严格的检查）
            // 注意：ScanUtil.startScan 会在运行时检查HMS Core，这里只做基本检查
            return true;
        } catch (NoClassDefFoundError | Exception e) {
            // 如果类不存在，说明HMS Core未安装或不可用
            return false;
        }
    }
}

