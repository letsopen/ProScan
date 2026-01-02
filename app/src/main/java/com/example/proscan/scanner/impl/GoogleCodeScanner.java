package com.example.proscan.scanner.impl;

import android.app.Activity;

import com.example.proscan.scanner.BarcodeScanner;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.android.gms.common.moduleinstall.ModuleInstallResponse;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

/**
 * Google ML Kit Code Scanner 实现
 * 参考: https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner#scan_a_code
 */
public class GoogleCodeScanner implements BarcodeScanner {
    
    private GmsBarcodeScanner scanner;
    private ScanCallback callback;
    
    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        this.callback = callback;
        
        try {
            // 配置扫码选项
            GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                            Barcode.FORMAT_QR_CODE,
                            Barcode.FORMAT_AZTEC,
                            Barcode.FORMAT_DATA_MATRIX,
                            Barcode.FORMAT_PDF417,
                            Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_CODE_39,
                            Barcode.FORMAT_CODE_93,
                            Barcode.FORMAT_CODABAR,
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_ITF,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E)
                    .enableAutoZoom() // 启用自动缩放（需要16.1.0及以上版本）
                    .build();
            
            // 获取扫码器实例
            scanner = GmsBarcodeScanning.getClient(activity, options);
            
            // 检查模块是否已安装
            ModuleInstallClient moduleInstallClient = ModuleInstall.getClient(activity);
            Task<ModuleInstallResponse> installTask = 
                    moduleInstallClient.areModulesAvailable(scanner.getModules());
            
            installTask.addOnSuccessListener(response -> {
                if (!response.areModulesAvailable()) {
                    // 模块未安装，请求安装
                    ModuleInstallRequest request = ModuleInstallRequest.newBuilder()
                            .addApi(scanner.getModules().get(0))
                            .build();
                    moduleInstallClient.installModules(request)
                            .addOnSuccessListener(installResponse -> {
                                // 安装成功，开始扫码
                                performScan();
                            })
                            .addOnFailureListener(e -> {
                                // 安装失败
                                if (callback != null) {
                                    callback.onError("无法安装扫码模块: " + e.getMessage());
                                }
                            });
                } else {
                    // 模块已安装，直接扫码
                    performScan();
                }
            }).addOnFailureListener(e -> {
                // 检查失败，尝试直接扫码
                performScan();
            });
            
        } catch (Exception e) {
            if (callback != null) {
                callback.onError("初始化扫码器失败: " + e.getMessage());
            }
        }
    }
    
    private void performScan() {
        if (scanner == null || callback == null) {
            return;
        }
        
        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    // 扫码成功
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null && callback != null) {
                        callback.onSuccess(rawValue);
                    } else if (callback != null) {
                        callback.onError("扫码结果为空");
                    }
                })
                .addOnCanceledListener(() -> {
                    // 扫码取消
                    if (callback != null) {
                        callback.onCancel();
                    }
                })
                .addOnFailureListener(e -> {
                    // 扫码失败
                    if (callback != null) {
                        callback.onError("扫码失败: " + e.getMessage());
                    }
                });
    }
    
    @Override
    public String getName() {
        return "Google ML Kit";
    }
    
    @Override
    public boolean isAvailable(Activity activity) {
        // Google Code Scanner 需要 Google Play Services
        try {
            // 简单检查：尝试创建实例
            GmsBarcodeScanner testScanner = GmsBarcodeScanning.getClient(activity);
            return testScanner != null;
        } catch (Exception e) {
            return false;
        }
    }
}

