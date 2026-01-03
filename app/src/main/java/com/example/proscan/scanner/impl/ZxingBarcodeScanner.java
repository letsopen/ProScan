package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;

import com.example.proscan.CustomCaptureActivity;
import com.example.proscan.scanner.BarcodeScanner;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.lang.ref.WeakReference;

/**
 * ZXing 扫码实现
 * 注意：ZXing使用Activity Result机制，需要在Activity的onActivityResult中调用handleActivityResult
 */
public class ZxingBarcodeScanner implements BarcodeScanner {
    
    private static WeakReference<ScanCallback> currentCallback;
    
    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        currentCallback = new WeakReference<>(callback);
        com.example.proscan.scanner.ScanCallbackHolder.set(callback, "ZXING");
        Intent intent = new Intent(activity, UnifiedScanActivity.class);
        activity.startActivity(intent);
    }
    
    /**
     * 处理扫码结果（需要在Activity的onActivityResult中调用）
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data Intent数据
     * @return 是否已处理
     */
    public static boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            ScanCallback callback = currentCallback != null ? currentCallback.get() : null;
            if (callback == null) {
                return false;
            }
            
            if (result.getContents() == null) {
                callback.onCancel();
            } else {
                callback.onSuccess(result.getContents());
            }
            // 清除回调引用
            currentCallback = null;
            return true;
        }
        return false;
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

