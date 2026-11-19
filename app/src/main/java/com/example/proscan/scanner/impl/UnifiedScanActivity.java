package com.example.proscan.scanner.impl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Camera;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.proscan.R;
import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.scanner.ScanCallbackHolder;
import com.example.proscan.view.ViewfinderView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.ml.scan.HmsScanAnalyzer;
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions;
import com.huawei.hms.mlsdk.common.MLFrame;
import com.example.proscan.scanner.decoder.FrameDecoder;
import com.example.proscan.scanner.decoder.ZxingCppFrameDecoder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnifiedScanActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ViewfinderView viewfinderView;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private BarcodeScanner.ScanCallback callback;
    private String decoder;
    private boolean active = true;
    private volatile boolean processing = false;
    private com.google.mlkit.vision.barcode.BarcodeScanner mlkitScanner;
    private FrameDecoder frameDecoder;
    private HmsScanAnalyzer hmsAnalyzer;

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
        callback = ScanCallbackHolder.get();
        decoder = ScanCallbackHolder.getDecoder();
        mlkitScanner = BarcodeScanning.getClient(
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
                        ).build()
        );
        // 按当前引擎创建帧解码器
        if ("ZXING_CPP".equals(decoder)) {
            frameDecoder = new ZxingCppFrameDecoder();
        } else if ("HUAWEI_SCAN_KIT".equals(decoder)) {
            // 增强 AI 分析器：实时流模式（photoMode=false），针对曲面/反光/污损优化
            hmsAnalyzer = new HmsScanAnalyzer(
                    new HmsScanAnalyzerOptions.Creator()
                            .setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                            .setPhotoMode(false)
                            .setParseResult(true)
                            .create());
        }
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(this);
        f.addListener(() -> {
            try {
                ProcessCameraProvider provider = f.get();
                bind(provider);
            } catch (ExecutionException | InterruptedException e) {
                if (callback != null) callback.onError("相机初始化失败: " + e.getMessage());
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bind(ProcessCameraProvider provider) {
        this.cameraProvider = provider;
        Preview preview = new Preview.Builder()
                // 分辨率锁定 1280x720：4K 会放大金属反光噪点且降低帧率，
                // 低于 720p 则 1D 条码条纹分辨率不足
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(
                                new android.util.Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build())
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(
                                new android.util.Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build())
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyze);
        try {
            provider.unbindAll();
            Camera camera = provider.bindToLifecycle((LifecycleOwner) this,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis);
            setupTapToFocus(camera.getCameraControl());
        } catch (Exception e) {
            if (callback != null) callback.onError("相机绑定失败: " + e.getMessage());
            finish();
        }
    }

    /**
     * 点击对焦/测光：金属饮料罐极易局部过曝（反光处死白），
     * 用户点击条码位置触发局部测光压低曝光，3 秒后自动恢复连续对焦
     */
    private void setupTapToFocus(androidx.camera.core.CameraControl cameraControl) {
        if (viewfinderView == null || previewView == null) return;
        viewfinderView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                androidx.camera.core.MeteringPointFactory factory = previewView.getMeteringPointFactory();
                androidx.camera.core.MeteringPoint point =
                        factory.createPoint(event.getX(), event.getY());
                androidx.camera.core.FocusMeteringAction action =
                        new androidx.camera.core.FocusMeteringAction.Builder(
                                point, androidx.camera.core.FocusMeteringAction.FLAG_AF
                                        | androidx.camera.core.FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                .build();
                cameraControl.startFocusAndMetering(action);
                v.performClick();
                return true;
            }
            return false;
        });
    }

    private void analyze(@NonNull ImageProxy image) {
        if (!active) { image.close(); return; }
        if (processing) { image.close(); return; }
        processing = true;
        if ("ML_KIT_STANDALONE".equals(decoder)) {
            processMlkit(image);
        } else if ("HUAWEI_SCAN_KIT".equals(decoder)) {
            processHuaweiFrame(image);
        } else if ("ZXING_CPP".equals(decoder)) {
            processWithFrameDecoder(image);
        } else {
            processMlkit(image);
        }
    }

    /** ZXing-CPP：FrameDecoder 内部负责 close(image) 与 processing 复位 */
    private void processWithFrameDecoder(ImageProxy image) {
        try {
            String result = frameDecoder.decode(image);
            if (result != null && active) {
                emitSuccess(result);
                return;
            }
        } catch (Throwable t) {
            // 吞帧继续
        }
        processing = false;
    }

    private void processMlkit(ImageProxy image) {
        InputImage inputImage = InputImage.fromMediaImage(
                image.getImage(),
                image.getImageInfo().getRotationDegrees()
        );
        mlkitScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty() && active) {
                        String value = barcodes.get(0).getRawValue();
                        if (value != null) emitSuccess(value);
                    }
                })
                .addOnFailureListener(e -> {
                    // ignore
                })
                .addOnCompleteListener(task -> {
                    processing = false;
                    image.close();
                });
    }

    private void emitSuccess(String value) {
        active = false;
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(300);
        if (callback != null) callback.onSuccess(value);
        runOnUiThread(this::finish);
    }

    /**
     * 华为 HMS：使用 HmsScanAnalyzer 直接分析 YUV 帧的 NV21 字节流（MLFrame.fromByteBuffer）
     * 显式 setPhotoMode(false) 避免走静态图慢速管线；
     * quadrant 传入相机旋转角度，保证竖排/横排条码都能命中
     */
    private void processHuaweiFrame(ImageProxy image) {
        try {
            if (image.getFormat() != ImageFormat.YUV_420_888 || image.getPlanes().length < 3) {
                return;
            }
            byte[] nv21 = yuv420ToNv21(image);
            int rotation = image.getImageInfo().getRotationDegrees();
            // MLFrame 相机流入口：writeByteBufferData(buf, format, w, h) + setQuadrant(rotation/90)
            MLFrame frame = new MLFrame.Creator()
                    .writeByteBufferData(ByteBuffer.wrap(nv21), ImageFormat.NV21,
                            image.getWidth(), image.getHeight())
                    .setQuadrant(rotation / 90)
                    .create();
            android.util.SparseArray<HmsScan> scans = hmsAnalyzer.analyseFrame(frame);
            if (scans != null && scans.size() > 0 && active) {
                String value = scans.valueAt(0).getOriginalValue();
                if (value != null) emitSuccess(value);
            }
        } catch (Throwable t) {
            // 单帧失败继续下一帧
        } finally {
            processing = false;
            image.close();
        }
    }

    private byte[] yuv420ToNv21(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] nv21 = new byte[width * height * 3 / 2];
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
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (mlkitScanner != null) {
            try { mlkitScanner.close(); } catch (Exception ignored) {}
        }
        if (frameDecoder != null) {
            frameDecoder.close();
            frameDecoder = null;
        }
        if (hmsAnalyzer != null) {
            try { hmsAnalyzer.destory(); } catch (Throwable ignored) {}
            hmsAnalyzer = null;
        }
        if (isFinishing()) ScanCallbackHolder.clear();
        super.onDestroy();
    }
}
