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
import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.RGBLuminanceSource;

import java.io.ByteArrayOutputStream;
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
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyze);
        try {
            provider.unbindAll();
            provider.bindToLifecycle((LifecycleOwner) this,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis);
        } catch (Exception e) {
            if (callback != null) callback.onError("相机绑定失败: " + e.getMessage());
            finish();
        }
    }

    private void analyze(@NonNull ImageProxy image) {
        if (!active) { image.close(); return; }
        Bitmap bitmap = toBitmap(image);
        if (bitmap != null) {
            if ("ML_KIT_STANDALONE".equals(decoder)) {
                processMlkit(image, bitmap);
            } else if ("HUAWEI_SCAN_KIT".equals(decoder)) {
                processHuawei(bitmap);
            } else if ("ZXING".equals(decoder)) {
                processZxing(bitmap);
            } else {
                processMlkit(image, bitmap);
            }
        }
        image.close();
    }

    private void processMlkit(ImageProxy image, Bitmap bitmap) {
        InputImage inputImage = InputImage.fromMediaImage(
                image.getImage(),
                image.getImageInfo().getRotationDegrees()
        );
        com.google.mlkit.vision.barcode.BarcodeScanner scanner = BarcodeScanning.getClient(
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
        scanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty() && active) {
                        String value = barcodes.get(0).getRawValue();
                        if (value != null) emitSuccess(value);
                    }
                })
                .addOnFailureListener(e -> {});
    }

    private void processHuawei(Bitmap bitmap) {
        HmsScanAnalyzerOptions options = new HmsScanAnalyzerOptions.Creator()
                .setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                .create();
        HmsScan[] scans = ScanUtil.decodeWithBitmap(this, bitmap, options);
        if (scans != null && scans.length > 0 && active) {
            String value = scans[0].getOriginalValue();
            if (value != null) emitSuccess(value);
        }
    }

    private void processZxing(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        RGBLuminanceSource source = new RGBLuminanceSource(w, h, pixels);
        BinaryBitmap bin = new BinaryBitmap(new HybridBinarizer(source));
        Reader reader = new MultiFormatReader();
        try {
            Result result = reader.decode(bin);
            if (result != null && result.getText() != null && active) {
                emitSuccess(result.getText());
            }
        } catch (Exception ignored) {}
    }

    private void emitSuccess(String value) {
        active = false;
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(300);
        if (callback != null) callback.onSuccess(value);
        runOnUiThread(this::finish);
    }

    private Bitmap toBitmap(ImageProxy image) {
        try {
            if (image.getFormat() != ImageFormat.YUV_420_888 || image.getPlanes().length < 3) return null;
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
        if (isFinishing()) ScanCallbackHolder.clear();
        super.onDestroy();
    }
}
