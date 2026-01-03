package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.ViewGroup;

import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.R;
import com.example.proscan.view.ViewfinderView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;
import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.hmsscankit.RemoteView;
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
        currentCallback = new WeakReference<>(callback);
        com.example.proscan.scanner.ScanCallbackHolder.set(callback, "HUAWEI_SCAN_KIT");
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
    
    public static class HuaweiScanActivity extends AppCompatActivity {
        private RemoteView remoteView;
        private ViewfinderView viewfinderView;
        
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setContentView(R.layout.activity_mlkit_scan);
            
            viewfinderView = findViewById(R.id.viewfinder_view);
            android.view.View root = findViewById(R.id.scan_root);
            root.post(() -> {
                int width = root.getWidth();
                int height = root.getHeight();
                int frameSize = (int) (Math.min(width, height) * 0.7);
                int left = (width - frameSize) / 2;
                int top = (height - frameSize) / 2;
                Rect rect = new Rect(left, top, left + frameSize, top + frameSize);
                
                remoteView = new RemoteView.Builder()
                        .setContext(this)
                        .setBoundingBox(rect)
                        .setFormat(HmsScan.ALL_SCAN_TYPE)
                        .build();
                
                remoteView.setOnResultCallback(hmsScans -> {
                    ScanCallback callback = currentCallback != null ? currentCallback.get() : null;
                    if (hmsScans != null && hmsScans.length > 0) {
                        HmsScan scan = hmsScans[0];
                        if (scan != null && scan.getOriginalValue() != null && callback != null) {
                            runOnUiThread(() -> {
                                callback.onSuccess(scan.getOriginalValue());
                                finish();
                            });
                        }
                    }
                });
                
                if (root instanceof android.view.ViewGroup) {
                    android.view.ViewGroup vg = (android.view.ViewGroup) root;
                    vg.addView(remoteView, 0, new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    ));
                }
            });
        }
        
        @Override
        protected void onStart() {
            super.onStart();
            if (remoteView != null) remoteView.onStart();
        }
        
        @Override
        protected void onResume() {
            super.onResume();
            if (remoteView != null) remoteView.onResume();
        }
        
        @Override
        protected void onPause() {
            if (remoteView != null) remoteView.onPause();
            super.onPause();
        }
        
        @Override
        protected void onStop() {
            if (remoteView != null) remoteView.onStop();
            super.onStop();
        }
        
        @Override
        protected void onDestroy() {
            if (remoteView != null) remoteView.onDestroy();
            ScanCallback callback = currentCallback != null ? currentCallback.get() : null;
            if (isFinishing() && callback != null) {
                // 避免泄漏
                currentCallback = null;
            }
            super.onDestroy();
        }
        
        @Override
        public void onBackPressed() {
            super.onBackPressed();
            ScanCallback callback = currentCallback != null ? currentCallback.get() : null;
            if (callback != null) {
                callback.onCancel();
            }
        }
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
