package com.example.proscan.scanner.impl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.proscan.R;
import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.view.ViewfinderView;
import com.google.common.util.concurrent.ListenableFuture;
import com.king.wechat.qrcode.WeChatQRCodeDetector;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WeChatBarcodeScanner implements BarcodeScanner {

    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        Helper.setCallback(callback);
        Intent intent = new Intent(activity, WeChatScanActivity.class);
        activity.startActivity(intent);
    }

    @Override
    public String getName() {
        return "WeChat OpenCV";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        try {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static class WeChatScanActivity extends AppCompatActivity {
        private PreviewView previewView;
        private ViewfinderView viewfinderView;
        private ProcessCameraProvider cameraProvider;
        private ExecutorService cameraExecutor;
        private ScanCallback currentCallback;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setContentView(R.layout.activity_mlkit_scan);
            previewView = findViewById(R.id.preview_view);
            viewfinderView = findViewById(R.id.viewfinder_view);
            cameraExecutor = Executors.newSingleThreadExecutor();
            currentCallback = Helper.getCallback();
            WeChatQRCodeDetector.init(this);
            startCamera();
        }

        private void startCamera() {
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
            future.addListener(() -> {
                try {
                    ProcessCameraProvider provider = future.get();
                    bind(provider);
                } catch (ExecutionException | InterruptedException e) {
                    if (currentCallback != null) {
                        currentCallback.onError("相机初始化失败: " + e.getMessage());
                    }
                    finish();
                }
            }, ContextCompat.getMainExecutor(this));
        }

        private void bind(ProcessCameraProvider provider) {
            this.cameraProvider = provider;
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            imageAnalysis.setAnalyzer(cameraExecutor, new Analyzer());
            CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
            try {
                provider.unbindAll();
                Camera camera = provider.bindToLifecycle((LifecycleOwner) this, selector, preview, imageAnalysis);
                CameraControl control = camera.getCameraControl();
                control.cancelFocusAndMetering();
                setupTapToFocus(control);
            } catch (Exception e) {
                if (currentCallback != null) {
                    currentCallback.onError("相机绑定失败: " + e.getMessage());
                }
                finish();
            }
        }

        private void setupTapToFocus(CameraControl control) {
            if (viewfinderView != null) {
                viewfinderView.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        MeteringPointFactory factory = previewView.getMeteringPointFactory();
                        MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                        FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build();
                        control.startFocusAndMetering(action);
                        v.performClick();
                        return true;
                    }
                    return false;
                });
            }
        }

        private class Analyzer implements ImageAnalysis.Analyzer {
            private boolean active = true;

            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (!active) {
                    image.close();
                    return;
                }
                Bitmap bitmap = toBitmap(image);
                if (bitmap != null) {
                    List<String> results = WeChatQRCodeDetector.detectAndDecode(bitmap);
                    if (results != null && !results.isEmpty() && active) {
                        String value = results.get(0);
                        if (value != null && currentCallback != null) {
                            active = false;
                            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            if (vibrator != null && vibrator.hasVibrator()) {
                                vibrator.vibrate(300);
                            }
                            currentCallback.onSuccess(value);
                            runOnUiThread(() -> finish());
                        }
                    }
                }
                image.close();
            }
        }

        private Bitmap toBitmap(ImageProxy image) {
            try {
                if (image.getFormat() != ImageFormat.YUV_420_888 || image.getPlanes().length < 3) {
                    return null;
                }
                byte[] nv21 = yuv420ToNv21(image);
                YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
                byte[] jpegBytes = out.toByteArray();
                return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
            } catch (Exception e) {
                return null;
            }
        }

        private byte[] yuv420ToNv21(ImageProxy image) {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] nv21 = new byte[width * height * 3 / 2];
            int ySize = width * height;
            int uvSize = width * height / 2;
            int pos = 0;
            ImageProxy.PlaneProxy yPlane = planes[0];
            ImageProxy.PlaneProxy uPlane = planes[1];
            ImageProxy.PlaneProxy vPlane = planes[2];
            int rowStrideY = yPlane.getRowStride();
            int pixelStrideY = yPlane.getPixelStride();
            byte[] yBuffer = new byte[yPlane.getBuffer().remaining()];
            yPlane.getBuffer().get(yBuffer);
            for (int row = 0; row < height; row++) {
                int offset = row * rowStrideY;
                for (int col = 0; col < width; col++) {
                    nv21[pos++] = yBuffer[offset + col * pixelStrideY];
                }
            }
            int rowStrideU = uPlane.getRowStride();
            int pixelStrideU = uPlane.getPixelStride();
            int rowStrideV = vPlane.getRowStride();
            int pixelStrideV = vPlane.getPixelStride();
            byte[] uBuffer = new byte[uPlane.getBuffer().remaining()];
            byte[] vBuffer = new byte[vPlane.getBuffer().remaining()];
            uPlane.getBuffer().get(uBuffer);
            vPlane.getBuffer().get(vBuffer);
            for (int row = 0; row < height / 2; row++) {
                int uRowOffset = row * rowStrideU;
                int vRowOffset = row * rowStrideV;
                for (int col = 0; col < width / 2; col++) {
                    int uIndex = uRowOffset + col * pixelStrideU;
                    int vIndex = vRowOffset + col * pixelStrideV;
                    nv21[pos++] = vBuffer[vIndex];
                    nv21[pos++] = uBuffer[uIndex];
                }
            }
            return nv21;
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
            if (isFinishing()) {
                Helper.clearCallback();
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

    static class Helper {
        private static ScanCallback callback;
        static void setCallback(ScanCallback cb) { callback = cb; }
        static ScanCallback getCallback() { return callback; }
        static void clearCallback() { callback = null; }
    }
}
