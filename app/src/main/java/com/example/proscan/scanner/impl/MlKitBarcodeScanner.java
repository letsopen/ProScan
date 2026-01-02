package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.proscan.R;
import com.example.proscan.scanner.BarcodeScanner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ML Kit Barcode Scanning 独立版本实现
 * 不需要 Google Play Services，模型打包在应用中
 * 参考: https://developers.google.com/ml-kit/vision/barcode-scanning/android
 */
public class MlKitBarcodeScanner implements BarcodeScanner {
    
    private ScanCallback callback;
    
    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        // 保存回调引用
        MlKitBarcodeScannerHelper.setCallback(callback);
        
        // 启动扫码Activity
        Intent intent = new Intent(activity, MlKitScanActivity.class);
        activity.startActivity(intent);
    }
    
    @Override
    public String getName() {
        return "ML Kit (Standalone)";
    }
    
    @Override
    public boolean isAvailable(Activity activity) {
        // ML Kit 独立版本总是可用（模型打包在应用中）
        return true;
    }
    
    /**
     * 扫码Activity
     */
    public static class MlKitScanActivity extends AppCompatActivity {
        private PreviewView previewView;
        private ImageView scanFrame;
        private TextView scanText;
        private ProcessCameraProvider cameraProvider;
        private ExecutorService cameraExecutor;
        private ScanCallback currentCallback;
        
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_mlkit_scan);
            
            previewView = findViewById(R.id.preview_view);
            scanFrame = findViewById(R.id.scan_frame);
            scanText = findViewById(R.id.scan_text);
            
            cameraExecutor = Executors.newSingleThreadExecutor();
            
            // 获取回调（通过静态方式传递，实际项目中可以使用更好的方式）
            currentCallback = MlKitBarcodeScannerHelper.getCallback();
            
            startCamera();
        }
        
        private void startCamera() {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                    ProcessCameraProvider.getInstance(this);
            
            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider provider = cameraProviderFuture.get();
                    bindCameraUseCases(provider);
                } catch (ExecutionException | InterruptedException e) {
                    if (currentCallback != null) {
                        currentCallback.onError("相机初始化失败: " + e.getMessage());
                    }
                    finish();
                }
            }, ContextCompat.getMainExecutor(this));
        }
        
        private void bindCameraUseCases(ProcessCameraProvider provider) {
            // 保存 provider 引用
            this.cameraProvider = provider;
            
            // 配置预览
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            
            // 配置图像分析（用于扫码）
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            
            imageAnalysis.setAnalyzer(cameraExecutor, new BarcodeAnalyzer());
            
            // 选择后置相机
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            
            try {
                // 解绑所有用例
                provider.unbindAll();
                
                // 绑定用例到生命周期
                Camera camera = provider.bindToLifecycle(
                        (LifecycleOwner) this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );
            } catch (Exception e) {
                if (currentCallback != null) {
                    currentCallback.onError("相机绑定失败: " + e.getMessage());
                }
                finish();
            }
        }
        
        private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
            private com.google.mlkit.vision.barcode.BarcodeScanner scanner;
            private boolean isScanning = true; // 标记是否正在扫码
            
            BarcodeAnalyzer() {
                // 创建扫码器（独立版本，不需要 Google Play Services）
                scanner = BarcodeScanning.getClient(
                        new com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
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
                                        Barcode.FORMAT_UPC_E
                                )
                                .build()
                );
            }
            
            @Override
            public void analyze(@NonNull ImageProxy image) {
                // 如果已经扫码成功，不再处理
                if (!isScanning) {
                    image.close();
                    return;
                }
                
                InputImage inputImage = InputImage.fromMediaImage(
                        image.getImage(),
                        image.getImageInfo().getRotationDegrees()
                );
                
                scanner.process(inputImage)
                        .addOnSuccessListener(barcodes -> {
                            if (!barcodes.isEmpty() && isScanning) {
                                Barcode barcode = barcodes.get(0);
                                String rawValue = barcode.getRawValue();
                                
                                if (rawValue != null && currentCallback != null) {
                                    // 停止扫码
                                    isScanning = false;
                                    
                                    // 震动反馈
                                    Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                                    if (vibrator != null && vibrator.hasVibrator()) {
                                        vibrator.vibrate(300);
                                    }
                                    
                                    // 返回结果
                                    currentCallback.onSuccess(rawValue);
                                    
                                    // 关闭扫码器
                                    scanner.close();
                                    
                                    // 结束Activity
                                    runOnUiThread(() -> finish());
                                }
                            }
                            image.close();
                        })
                        .addOnFailureListener(e -> {
                            image.close();
                            // 继续处理下一帧，不报告错误
                        });
            }
        }
        
        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (cameraExecutor != null) {
                cameraExecutor.shutdown();
            }
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
            // Activity销毁时清除回调引用，防止内存泄漏
            if (isFinishing()) {
                MlKitBarcodeScannerHelper.clearCallback();
            }
        }
        
        @Override
        public void onBackPressed() {
            super.onBackPressed();
            if (currentCallback != null) {
                currentCallback.onCancel();
            }
        }
    }
    
    /**
     * 辅助类：用于在Activity之间传递回调
     */
    static class MlKitBarcodeScannerHelper {
        private static ScanCallback callback;
        
        static void setCallback(ScanCallback callback) {
            MlKitBarcodeScannerHelper.callback = callback;
        }
        
        static ScanCallback getCallback() {
            ScanCallback result = callback;
            callback = null; // 使用后清除
            return result;
        }
    }
}

