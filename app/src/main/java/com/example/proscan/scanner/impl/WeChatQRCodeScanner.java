package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

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

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.wechat_qrcode.WeChatQRCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 微信 OpenCV 二维码识别实现
 * 基于 OpenCV 的 wechat_qrcode 模块
 * 参考: https://github.com/opencv/opencv_contrib/tree/master/modules/wechat_qrcode
 */
public class WeChatQRCodeScanner implements BarcodeScanner {
    
    private static final String TAG = "WeChatQRCodeScanner";
    private ScanCallback callback;
    
    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        // 保存回调引用
        WeChatQRCodeScannerHelper.setCallback(callback);
        
        // 启动扫码Activity
        Intent intent = new Intent(activity, WeChatScanActivity.class);
        activity.startActivity(intent);
    }
    
    @Override
    public String getName() {
        return "WeChat QRCode (OpenCV)";
    }
    
    @Override
    public boolean isAvailable(Activity activity) {
        // 检查OpenCV是否可用
        try {
            // 检查模型文件是否存在
            String[] modelFiles = {
                "detect.prototxt",
                "detect.caffemodel",
                "sr.prototxt",
                "sr.caffemodel"
            };
            
            for (String fileName : modelFiles) {
                try (InputStream is = activity.getAssets().open(fileName)) {
                    // 文件存在
                } catch (IOException e) {
                    Log.w(TAG, "Model file not found: " + fileName);
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error checking availability", e);
            return false;
        }
    }
    
    /**
     * 扫码Activity
     */
    public static class WeChatScanActivity extends AppCompatActivity {
        private PreviewView previewView;
        private ProcessCameraProvider cameraProvider;
        private ExecutorService cameraExecutor;
        private ScanCallback currentCallback;
        private WeChatQRCode weChatQRCode;
        private boolean isScanning = true;
        private BaseLoaderCallback openCVLoaderCallback;
        
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_mlkit_scan);
            
            previewView = findViewById(R.id.preview_view);
            
            cameraExecutor = Executors.newSingleThreadExecutor();
            
            // 获取回调
            currentCallback = WeChatQRCodeScannerHelper.getCallback();
            
            // 初始化OpenCV
            initOpenCV();
        }
        
        private void initOpenCV() {
            openCVLoaderCallback = new BaseLoaderCallback(this) {
                @Override
                public void onManagerConnected(int status) {
                    switch (status) {
                        case LoaderCallbackInterface.SUCCESS:
                            Log.d(TAG, "OpenCV loaded successfully");
                            // OpenCV加载成功，初始化WeChatQRCode并启动相机
                            initWeChatQRCode();
                            startCamera();
                            break;
                        default:
                            super.onManagerConnected(status);
                            if (currentCallback != null) {
                                currentCallback.onError("OpenCV初始化失败");
                            }
                            finish();
                            break;
                    }
                }
            };
            
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, openCVLoaderCallback);
            } else {
                Log.d(TAG, "OpenCV library found inside package. Using it!");
                openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            }
        }
        
        private void initWeChatQRCode() {
            try {
                // 将模型文件从assets复制到文件目录
                File filesDir = getFilesDir();
                String detectorProto = new File(filesDir, "detect.prototxt").getAbsolutePath();
                String detectorCaffe = new File(filesDir, "detect.caffemodel").getAbsolutePath();
                String srProto = new File(filesDir, "sr.prototxt").getAbsolutePath();
                String srCaffe = new File(filesDir, "sr.caffemodel").getAbsolutePath();
                
                // 复制模型文件
                copyAssetToFile("detect.prototxt", detectorProto);
                copyAssetToFile("detect.caffemodel", detectorCaffe);
                copyAssetToFile("sr.prototxt", srProto);
                copyAssetToFile("sr.caffemodel", srCaffe);
                
                // 初始化WeChatQRCode
                weChatQRCode = new WeChatQRCode(
                    detectorProto,
                    detectorCaffe,
                    srProto,
                    srCaffe
                );
                
                Log.d(TAG, "WeChatQRCode initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing WeChatQRCode", e);
                if (currentCallback != null) {
                    currentCallback.onError("初始化扫码器失败: " + e.getMessage());
                }
                finish();
            }
        }
        
        private void copyAssetToFile(String assetName, String filePath) {
            try (InputStream is = getAssets().open(assetName);
                 FileOutputStream os = new FileOutputStream(filePath)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
                Log.d(TAG, "Copied " + assetName + " to " + filePath);
            } catch (IOException e) {
                Log.e(TAG, "Error copying asset file: " + assetName, e);
                throw new RuntimeException("Failed to copy model file: " + assetName, e);
            }
        }
        
        private void startCamera() {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                    ProcessCameraProvider.getInstance(this);
            
            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider provider = cameraProviderFuture.get();
                    bindCameraUseCases(provider);
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Camera initialization failed", e);
                    if (currentCallback != null) {
                        currentCallback.onError("相机初始化失败: " + e.getMessage());
                    }
                    finish();
                }
            }, ContextCompat.getMainExecutor(this));
        }
        
        private void bindCameraUseCases(ProcessCameraProvider provider) {
            this.cameraProvider = provider;
            
            // 配置预览
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            
            // 配置图像分析（用于扫码）
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            
            imageAnalysis.setAnalyzer(cameraExecutor, new QRCodeAnalyzer());
            
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
                Log.e(TAG, "Camera binding failed", e);
                if (currentCallback != null) {
                    currentCallback.onError("相机绑定失败: " + e.getMessage());
                }
                finish();
            }
        }
        
        private class QRCodeAnalyzer implements ImageAnalysis.Analyzer {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (!isScanning || weChatQRCode == null) {
                    image.close();
                    return;
                }
                
                try {
                    // 将ImageProxy转换为OpenCV Mat
                    Mat mat = imageProxyToMat(image);
                    
                    // 检测和解码二维码
                    List<String> results = new ArrayList<>();
                    List<Mat> points = new ArrayList<>();
                    weChatQRCode.detectAndDecode(mat, results, points);
                    
                    if (!results.isEmpty() && isScanning) {
                        // 停止扫码
                        isScanning = false;
                        
                        String result = results.get(0);
                        
                        // 震动反馈
                        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null && vibrator.hasVibrator()) {
                            vibrator.vibrate(300);
                        }
                        
                        // 返回结果
                        if (currentCallback != null) {
                            currentCallback.onSuccess(result);
                        }
                        
                        // 结束Activity
                        runOnUiThread(() -> finish());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error analyzing frame", e);
                } finally {
                    image.close();
                }
            }
            
            private Mat imageProxyToMat(ImageProxy image) {
                try {
                    // 将ImageProxy转换为Bitmap，然后再转换为Mat
                    Bitmap bitmap = imageProxyToBitmap(image);
                    if (bitmap == null) {
                        return new Mat();
                    }
                    
                    // 将Bitmap转换为Mat
                    Mat mat = new Mat();
                    org.opencv.android.Utils.bitmapToMat(bitmap, mat);
                    
                    // 释放Bitmap
                    bitmap.recycle();
                    
                    return mat;
                } catch (Exception e) {
                    Log.e(TAG, "Error converting ImageProxy to Mat", e);
                    return new Mat();
                }
            }
            
            private Bitmap imageProxyToBitmap(ImageProxy image) {
                try {
                    android.graphics.Image img = image.getImage();
                    if (img == null) {
                        return null;
                    }
                    
                    int format = img.getFormat();
                    int width = image.getWidth();
                    int height = image.getHeight();
                    
                    if (format == ImageFormat.YUV_420_888) {
                        // 处理YUV格式
                        android.graphics.Image.Plane[] planes = img.getPlanes();
                        if (planes.length < 3) {
                            return null;
                        }
                        
                        android.graphics.Image.Plane yPlane = planes[0];
                        android.graphics.Image.Plane uPlane = planes[1];
                        android.graphics.Image.Plane vPlane = planes[2];
                        
                        int ySize = yPlane.getBuffer().remaining();
                        int uSize = uPlane.getBuffer().remaining();
                        int vSize = vPlane.getBuffer().remaining();
                        
                        byte[] yBuffer = new byte[ySize];
                        byte[] uBuffer = new byte[uSize];
                        byte[] vBuffer = new byte[vSize];
                        
                        yPlane.getBuffer().get(yBuffer);
                        uPlane.getBuffer().get(uBuffer);
                        vPlane.getBuffer().get(vBuffer);
                        
                        // 转换为YUV_420_888格式的字节数组
                        byte[] nv21 = new byte[ySize + uSize + vSize];
                        System.arraycopy(yBuffer, 0, nv21, 0, ySize);
                        
                        // 交错U和V
                        int uvIndex = ySize;
                        for (int i = 0; i < uSize && i < vSize; i++) {
                            nv21[uvIndex++] = vBuffer[i];
                            nv21[uvIndex++] = uBuffer[i];
                        }
                        
                        // 使用YuvImage转换为Bitmap
                        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
                        byte[] imageBytes = out.toByteArray();
                        Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                        
                        // 根据旋转角度旋转Bitmap
                        int rotationDegrees = image.getImageInfo().getRotationDegrees();
                        if (rotationDegrees != 0) {
                            Matrix matrix = new Matrix();
                            matrix.postRotate(rotationDegrees);
                            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            bitmap.recycle();
                            bitmap = rotated;
                        }
                        
                        return bitmap;
                    } else {
                        // 其他格式，尝试直接转换
                        Log.w(TAG, "Unsupported image format: " + format);
                        return null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
                    return null;
                }
            }
        }
        
        @Override
        protected void onDestroy() {
            super.onDestroy();
            isScanning = false;
            
            if (cameraExecutor != null) {
                cameraExecutor.shutdown();
            }
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
            if (weChatQRCode != null) {
                weChatQRCode.close();
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
    static class WeChatQRCodeScannerHelper {
        private static ScanCallback callback;
        
        static void setCallback(ScanCallback callback) {
            WeChatQRCodeScannerHelper.callback = callback;
        }
        
        static ScanCallback getCallback() {
            ScanCallback result = callback;
            callback = null; // 使用后清除
            return result;
        }
    }
}

