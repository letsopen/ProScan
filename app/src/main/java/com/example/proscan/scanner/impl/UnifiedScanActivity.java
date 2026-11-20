package com.example.proscan.scanner.impl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proscan.R;
import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.scanner.BarcodeScannerFactory;
import com.example.proscan.scanner.ScanCallbackHolder;
import com.example.proscan.scanner.ScanResult;
import com.example.proscan.scanner.decoder.FrameDecoder;
import com.example.proscan.scanner.decoder.ZxingCppFrameDecoder;
import com.example.proscan.view.ViewfinderView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.ml.scan.HmsScanAnalyzer;
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions;
import com.huawei.hms.mlsdk.common.MLFrame;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnifiedScanActivity extends AppCompatActivity {
    private static final String TAG = "UnifiedScanActivity";
    private static final int CONFIRM_HITS = 2;
    private static final long CANDIDATE_TIMEOUT_MS = 800;
    private static final String PREFS = "proscan_prefs";
    private static final String KEY_SCANNER_TYPE = "scanner_type";

    private String lastCandidate;
    private String lastCandidateFormat = FrameDecoder.FORMAT_UNKNOWN;
    private int candidateHits;
    private long lastCandidateAt;
    private int decoderGeneration;
    private PreviewView previewView;
    private ViewfinderView viewfinderView;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private ExecutorService photoExecutor;
    private BarcodeScanner.ScanCallback callback;
    private String decoder;
    private boolean active = true;
    private boolean destroyed;
    private boolean readingPhoto;
    private volatile boolean processing = false;
    private com.google.mlkit.vision.barcode.BarcodeScanner mlkitScanner;
    private FrameDecoder frameDecoder;
    private HmsScanAnalyzer hmsAnalyzer;
    private Camera camera;
    private CameraInfo cameraInfo;
    private CameraControl cameraControl;
    private ImageButton flashButton;
    private TextView engineText;
    private TextView statusText;
    private View guidance;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_mlkit_scan);
        // 必须在 setContentView 后访问 WindowInsetsController：
        // 部分华为 ROM 在 DecorView 创建前调用 Window.getInsetsController 会触发 NPE。
        androidx.core.view.WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        previewView = findViewById(R.id.preview_view);
        viewfinderView = findViewById(R.id.viewfinder_view);
        flashButton = findViewById(R.id.button_flash);
        engineText = findViewById(R.id.text_engine);
        statusText = findViewById(R.id.status_text);
        guidance = findViewById(R.id.scan_guidance);
        cameraExecutor = Executors.newSingleThreadExecutor();
        photoExecutor = Executors.newSingleThreadExecutor();
        callback = ScanCallbackHolder.get();
        decoder = ScanCallbackHolder.getDecoder();
        if (decoder == null) decoder = BarcodeScannerFactory.ScannerType.ML_KIT_STANDALONE.name();
        createDecoder();
        setupInsets();
        setupControls();
        setupPhotoPicker();
        findViewById(R.id.button_gallery).setOnClickListener(v -> {
            if (!readingPhoto) {
                photoPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
            }
        });
        positionGuidance();
        updateEngineLabel(false);
        startCamera();
    }

    private void setupInsets() {
        View top = findViewById(R.id.top_controls);
        View bottom = findViewById(R.id.bottom_controls);
        int topPadding = top.getPaddingTop();
        int bottomPadding = bottom.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scan_root), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            top.setPadding(top.getPaddingLeft(), topPadding + bars.top,
                    top.getPaddingRight(), top.getPaddingBottom());
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(),
                    bottom.getPaddingRight(), bottomPadding + bars.bottom);
            statusText.setTranslationY(-bars.bottom);
            positionGuidance();
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupControls() {
        findViewById(R.id.button_close).setOnClickListener(v -> cancelScan());
        findViewById(R.id.button_engine).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showEngineOptions();
        });
        flashButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (cameraInfo != null && cameraInfo.getTorchState().getValue() != null) {
                boolean on = cameraInfo.getTorchState().getValue() == TorchState.ON;
                cameraControl.enableTorch(!on);
            }
        });
    }

    private void setupPhotoPicker() {
        photoPicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), this::decodePhoto);
    }

    private void positionGuidance() {
        if (guidance == null || viewfinderView == null) return;
        viewfinderView.post(() -> {
            if (destroyed) return;
            Rect frame = new Rect();
            android.graphics.RectF rect = viewfinderView.getFrameRect();
            frame.set(Math.round(rect.left), Math.round(rect.top),
                    Math.round(rect.right), Math.round(rect.bottom));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) guidance.getLayoutParams();
            params.topMargin = frame.bottom + Math.round(20 * getResources().getDisplayMetrics().density);
            guidance.setX((previewView.getWidth() - guidance.getWidth()) / 2f);
            guidance.setLayoutParams(params);
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                bind(future.get());
            } catch (ExecutionException | InterruptedException e) {
                if (callback != null) callback.onError("相机初始化失败: " + e.getMessage());
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private ResolutionSelector cameraResolution() {
        return new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(
                        new android.util.Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build();
    }

    private void bind(ProcessCameraProvider provider) {
        this.cameraProvider = provider;
        Preview preview = new Preview.Builder().setResolutionSelector(cameraResolution()).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(cameraResolution())
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyze);
        try {
            provider.unbindAll();
            camera = provider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis);
            cameraControl = camera.getCameraControl();
            cameraInfo = camera.getCameraInfo();
            setupTapToFocus(cameraControl);
            setupTorch();
        } catch (Exception e) {
            if (callback != null) callback.onError("相机绑定失败: " + e.getMessage());
            finish();
        }
    }

    private void setupTorch() {
        if (!cameraInfo.hasFlashUnit()) {
            flashButton.setVisibility(View.GONE);
            return;
        }
        cameraInfo.getTorchState().observe(this, state -> {
            boolean on = state == TorchState.ON;
            flashButton.setImageResource(on ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
            flashButton.setBackgroundResource(on ? R.drawable.bg_camera_control_active : R.drawable.bg_camera_control);
            flashButton.setImageTintList(ContextCompat.getColorStateList(this,
                    on ? R.color.text_primary : R.color.white));
            flashButton.setContentDescription(getString(on ? R.string.flash_off : R.string.flash_on));
        });
    }

    private void setupTapToFocus(CameraControl cameraControl) {
        viewfinderView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                androidx.camera.core.MeteringPoint point = previewView.getMeteringPointFactory()
                        .createPoint(event.getX(), event.getY());
                androidx.camera.core.FocusMeteringAction action =
                        new androidx.camera.core.FocusMeteringAction.Builder(point,
                                androidx.camera.core.FocusMeteringAction.FLAG_AF
                                        | androidx.camera.core.FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build();
                cameraControl.startFocusAndMetering(action);
                v.performClick();
                return true;
            }
            return false;
        });
    }

    private void analyze(@NonNull ImageProxy image) {
        if (!active || readingPhoto) { image.close(); return; }
        // 取景框尚未完成测量时暂不分析，避免偶尔退回全帧识别。
        if (!viewfinderView.isFrameReady() || previewView.getWidth() == 0 || previewView.getHeight() == 0) {
            image.close();
            return;
        }
        if (processing) { image.close(); return; }
        processing = true;
        int generation = decoderGeneration;
        if ("ML_KIT_STANDALONE".equals(decoder)) {
            processMlkit(image, generation);
        } else if ("HUAWEI_SCAN_KIT".equals(decoder)) {
            processHuaweiFrame(image, generation);
        } else if ("ZXING_CPP".equals(decoder)) {
            processWithFrameDecoder(image, generation);
        } else {
            processMlkit(image, generation);
        }
    }

    private void processWithFrameDecoder(ImageProxy image, int generation) {
        try {
            // ZXing-CPP 的 JNI 直接读取 ImageProxy.cropRect，不复制、不转 Bitmap。
            image.setCropRect(sensorCropRect(image));
            String[] result = frameDecoder.decodeDetailed(image);
            if (result != null && active && generation == decoderGeneration) {
                onDecodeCandidate(result[0], result[1], generation);
                return;
            }
        } catch (Throwable ignored) { }
        processing = false;
    }

    private void onDecodeCandidate(String value, String format, int generation) {
        if (generation != decoderGeneration || !active) {
            processing = false;
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (value.equals(lastCandidate) && now - lastCandidateAt <= CANDIDATE_TIMEOUT_MS) {
            candidateHits++;
        } else {
            candidateHits = 1;
        }
        lastCandidate = value;
        lastCandidateFormat = format == null ? FrameDecoder.FORMAT_UNKNOWN : format;
        lastCandidateAt = now;
        if (candidateHits >= CONFIRM_HITS) {
            ScanResult result = new ScanResult(value, lastCandidateFormat, currentEngineLabel());
            resetCandidate();
            emitSuccess(result);
        } else {
            processing = false;
        }
    }

    private void resetCandidate() {
        lastCandidate = null;
        candidateHits = 0;
    }

    @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processMlkit(ImageProxy image, int generation) {
        // 保留 ML Kit 原生 YUV 识别管线；结果坐标按当前 cropRect 转换到传感器坐标后筛选。
        // 相比先转 Bitmap，这条路径不会损失条码细节，也不需要额外 JPEG 编解码。
        Rect crop = sensorCropRect(image);
        image.setCropRect(crop);
        InputImage inputImage = InputImage.fromMediaImage(image.getImage(),
                image.getImageInfo().getRotationDegrees());
        mlkitScanner.process(inputImage).addOnSuccessListener(barcodes -> {
            if (!barcodes.isEmpty() && active && generation == decoderGeneration) {
                for (Barcode barcode : barcodes) {
                    android.graphics.Point[] corners = barcode.getCornerPoints();
                    if (corners == null || corners.length == 0) continue;
                    long x = 0, y = 0;
                    for (android.graphics.Point point : corners) {
                        x += point.x + crop.left;
                        y += point.y + crop.top;
                    }
                    x /= corners.length;
                    y /= corners.length;
                    if (x >= crop.left && x < crop.right && y >= crop.top && y < crop.bottom
                            && barcode.getRawValue() != null) {
                        onDecodeCandidate(barcode.getRawValue(), mlKitFormat(barcode.getFormat()), generation);
                        break;
                    }
                }
            }
        }).addOnFailureListener(e -> {
            android.util.Log.w(TAG, "ML Kit frame failed: " + e.getMessage());
        }).addOnCompleteListener(task -> {
            processing = false;
            image.close();
        });
    }

    private void processHuaweiFrame(ImageProxy image, int generation) {
        Bitmap cropped = null;
        try {
            Rect crop = sensorCropRect(image);
            image.setCropRect(crop);
            byte[] nv21 = yuv420ToNv21(image);
            boolean handled = false;
            try {
                // 正确复制 cropRect 内的 YUV 像素，恢复 HMS 原生 NV21 直读路径。
                MLFrame frame = new MLFrame.Creator().writeByteBufferData(ByteBuffer.wrap(nv21),
                        crop.width(), crop.height(), ImageFormat.NV21)
                        .setQuadrant(image.getImageInfo().getRotationDegrees() / 90).create();
                android.util.SparseArray<HmsScan> scans = hmsAnalyzer.analyseFrame(frame);
                if (scans != null && scans.size() > 0 && active && generation == decoderGeneration) {
                    HmsScan scan = scans.valueAt(0);
                    if (scan.getOriginalValue() != null) {
                        handled = true;
                        onDecodeCandidate(scan.getOriginalValue(), hmsFormat(scan.getScanTypeForm()), generation);
                    }
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "HMS analyzer frame failed: " + t);
            }
            if (!handled && active && generation == decoderGeneration) {
                cropped = nv21ToBitmap(nv21, crop.width(), crop.height(),
                        image.getImageInfo().getRotationDegrees());
                if (cropped != null) {
                    HmsScan[] scans = ScanUtil.decodeWithBitmap(getApplicationContext(), cropped,
                            new HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                                    .setPhotoMode(false).setParseResult(true).create());
                    if (scans != null && scans.length > 0 && active && generation == decoderGeneration) {
                        onDecodeCandidate(scans[0].getOriginalValue(),
                                hmsFormat(scans[0].getScanTypeForm()), generation);
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "HMS bitmap fallback failed: " + t);
        } finally {
            if (cropped != null) cropped.recycle();
            processing = false;
            image.close();
        }
    }

    /**
     * 将屏幕上的取景框映射到图像缓冲区。
     * 输入 viewWidth/viewHeight 为预览的可见区域，frame 为其中的可见取景框；
     * imageWidth/imageHeight 使用旋转后的缓冲区方向，返回传感器原始方向裁剪区域。
     * PreviewView 默认 FILL_CENTER（中央裁剪），其内部预览缓冲尺寸与显示尺寸可能不同。
     */
    static Rect mapFrameToSensorRect(android.graphics.RectF frame,
                                     float viewWidth, float viewHeight,
                                     int bufferWidth, int bufferHeight, int rotationDegrees) {
        float rotatedWidth = rotationDegrees % 180 == 0 ? bufferWidth : bufferHeight;
        float rotatedHeight = rotationDegrees % 180 == 0 ? bufferHeight : bufferWidth;
        float scale = Math.max(viewWidth / rotatedWidth, viewHeight / rotatedHeight);
        float offsetX = (rotatedWidth * scale - viewWidth) / 2f;
        float offsetY = (rotatedHeight * scale - viewHeight) / 2f;
        // 4% 余量仅避免贴边截断，仍严格限制在取景框附近。
        float paddingX = frame.width() * 0.04f;
        float paddingY = frame.height() * 0.04f;
        float rx = (frame.left - paddingX + offsetX) / scale;
        float ry = (frame.top - paddingY + offsetY) / scale;
        float rw = (frame.width() + paddingX * 2) / scale;
        float rh = (frame.height() + paddingY * 2) / scale;
        int left;
        int top;
        int right;
        int bottom;
        if (rotationDegrees == 90) {
            left = Math.round(ry);
            top = Math.round(bufferHeight - rx - rw);
            right = Math.round(ry + rh);
            bottom = Math.round(bufferHeight - rx);
        } else if (rotationDegrees == 180) {
            left = Math.round(bufferWidth - rx - rw);
            top = Math.round(bufferHeight - ry - rh);
            right = Math.round(bufferWidth - rx);
            bottom = Math.round(bufferHeight - ry);
        } else if (rotationDegrees == 270) {
            left = Math.round(bufferWidth - ry - rh);
            top = Math.round(rx);
            right = Math.round(bufferWidth - ry);
            bottom = Math.round(rx + rw);
        } else {
            left = Math.round(rx);
            top = Math.round(ry);
            right = Math.round(rx + rw);
            bottom = Math.round(ry + rh);
        }
        left = Math.max(0, left) & ~1;
        top = Math.max(0, top) & ~1;
        right = Math.min(bufferWidth, right) & ~1;
        bottom = Math.min(bufferHeight, bottom) & ~1;
        if (right - left < 2 || bottom - top < 2) return new Rect(0, 0, bufferWidth, bufferHeight);
        return new Rect(left, top, right, bottom);
    }

    private Rect sensorCropRect(ImageProxy image) {
        return mapFrameToSensorRect(viewfinderView.getFrameRect(), previewView.getWidth(), previewView.getHeight(),
                image.getWidth(), image.getHeight(), image.getImageInfo().getRotationDegrees());
    }

    /** 将已正确裁剪的 NV21 转换为正向 Bitmap，仅用于 HMS 回退。 */
    private Bitmap nv21ToBitmap(byte[] nv21, int width, int height, int rotation) {
        try {
            YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 90, output);
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size());
            if (rotation != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) bitmap.recycle();
                bitmap = rotated;
            }
            return bitmap;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "HMS cropped bitmap failed: " + t);
            return null;
        }
    }


    private void createDecoder() {
        if ("ZXING_CPP".equals(decoder)) {
            frameDecoder = new ZxingCppFrameDecoder();
        } else if ("HUAWEI_SCAN_KIT".equals(decoder)) {
            hmsAnalyzer = new HmsScanAnalyzer(new HmsScanAnalyzerOptions.Creator()
                    .setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).setPhotoMode(false).setParseResult(true).create());
        } else {
            mlkitScanner = BarcodeScanning.getClient(new com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC,
                            Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_PDF417, Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_CODABAR,
                            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_ITF,
                            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E).build());
        }
    }

    private void releaseDecoders() {
        if (mlkitScanner != null) { try { mlkitScanner.close(); } catch (Exception ignored) {} mlkitScanner = null; }
        if (frameDecoder != null) { frameDecoder.close(); frameDecoder = null; }
        if (hmsAnalyzer != null) { try { hmsAnalyzer.destory(); } catch (Throwable ignored) {} hmsAnalyzer = null; }
    }

    private void switchEngine(BarcodeScannerFactory.ScannerType type) {
        if (type.name().equals(decoder) || readingPhoto) return;
        active = false;
        decoderGeneration++;
        int generation = decoderGeneration;
        cameraExecutor.execute(() -> {
            releaseDecoders();
            runOnUiThread(() -> {
                if (destroyed || generation != decoderGeneration) return;
                decoder = type.name();
                ScanCallbackHolder.setDecoder(decoder);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SCANNER_TYPE, decoder).apply();
                createDecoder();
                resetCandidate();
                processing = false;
                readingPhoto = false;
                active = true;
                updateEngineLabel(true);
                statusText.setVisibility(View.GONE);
            });
        });
    }

    private String currentEngineLabel() {
        try {
            return BarcodeScannerFactory.ScannerType.valueOf(decoder).getLabel();
        } catch (Exception ignored) {
            return BarcodeScannerFactory.ScannerType.ML_KIT_STANDALONE.getLabel();
        }
    }

    private void updateEngineLabel(boolean animate) {
        String label = currentEngineLabel();
        if (!animate) { engineText.setText(label); return; }
        engineText.animate().alpha(0f).translationY(-4f).setDuration(100).withEndAction(() -> {
            engineText.setText(label);
            engineText.setTranslationY(4f);
            engineText.animate().alpha(1f).translationY(0f).setDuration(140).start();
        }).start();
    }

    private void showEngineOptions() {
        if (readingPhoto || successEmitted) return;
        active = false;
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.bottom_sheet_scanner_options, null);
        dialog.setContentView(content);
        RecyclerView list = content.findViewById(R.id.scanner_options);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new ScannerOptionsAdapter(type -> {
            BarcodeScanner scanner = BarcodeScannerFactory.createScanner(type);
            if (!scanner.isAvailable(this)) {
                Toast.makeText(this, R.string.engine_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            switchEngine(type);
            dialog.dismiss();
        }));
        dialog.setOnDismissListener(d -> {
            if (!destroyed && !readingPhoto && !isFinishing()) active = true;
        });
        applyBottomInsets(dialog, content, true);
        dialog.show();
    }

    private class ScannerOptionsAdapter extends RecyclerView.Adapter<ScannerOptionsAdapter.Holder> {
        private final BarcodeScannerFactory.ScannerType[] values = BarcodeScannerFactory.ScannerType.values();
        private final java.util.function.Consumer<BarcodeScannerFactory.ScannerType> onSelect;
        ScannerOptionsAdapter(java.util.function.Consumer<BarcodeScannerFactory.ScannerType> onSelect) {
            this.onSelect = onSelect;
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(getLayoutInflater().inflate(R.layout.item_scanner_option, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            BarcodeScannerFactory.ScannerType type = values[position];
            BarcodeScanner scanner = BarcodeScannerFactory.createScanner(type);
            boolean available = scanner.isAvailable(UnifiedScanActivity.this);
            boolean selected = type.name().equals(decoder);
            holder.name.setText(type.getLabel());
            holder.name.setAlpha(available ? 1f : 0.45f);
            holder.status.setText(selected ? "当前使用" : available ? "可用" : "此设备不可用");
            holder.check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            holder.itemView.setEnabled(available);
            holder.itemView.setAlpha(available ? 1f : 0.55f);
            holder.itemView.setOnClickListener(v -> onSelect.accept(type));
        }
        @Override public int getItemCount() { return values.length; }
        class Holder extends RecyclerView.ViewHolder {
            final TextView name = itemView.findViewById(R.id.option_name);
            final TextView status = itemView.findViewById(R.id.option_status);
            final android.widget.ImageView check = itemView.findViewById(R.id.option_check);
            Holder(View view) { super(view); }
        }
    }

    private void decodePhoto(Uri uri) {
        if (uri == null || readingPhoto || destroyed) return;
        readingPhoto = true;
        active = false;
        showStatus(getString(R.string.reading_photo));
        String selectedDecoder = decoder;
        photoExecutor.execute(() -> {
            Bitmap bitmap = null;
            String[] result = null;
            int orientation = readExifRotation(uri);
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    if (max > 2048) decoder.setTargetSampleSize(Math.max(1, Integer.highestOneBit(max / 2048)));
                });
                if (orientation != 0) {
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.postRotate(orientation);
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (rotated != bitmap) bitmap.recycle();
                    bitmap = rotated;
                }
                result = decodeBitmap(selectedDecoder, bitmap);
            } catch (Throwable t) {
                android.util.Log.w(TAG, "Gallery decode failed: " + t);
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
            String[] finalResult = result;
            runOnUiThread(() -> {
                if (destroyed) return;
                readingPhoto = false;
                if (finalResult != null && active == false) {
                    ScanResult scanResult = new ScanResult(finalResult[0], finalResult[1], currentEngineLabel());
                    emitSuccess(scanResult);
                } else {
                    active = true;
                    showStatus(getString(R.string.no_barcode_in_image));
                    statusText.postDelayed(() -> {
                        if (!destroyed && !readingPhoto) statusText.setVisibility(View.GONE);
                    }, 2600);
                }
            });
        });
    }

    private int readExifRotation(Uri uri) {
        try (java.io.InputStream stream = getContentResolver().openInputStream(uri)) {
            androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(stream);
            int orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
            if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception e) {
            android.util.Log.w(TAG, "EXIF unavailable: " + e.getMessage());
        }
        return 0;
    }

    private String[] decodeBitmap(String selectedDecoder, Bitmap bitmap) {
        if ("ML_KIT_STANDALONE".equals(selectedDecoder)) {
            com.google.mlkit.vision.barcode.BarcodeScanner scanner = BarcodeScanning.getClient();
            try {
                java.util.List<Barcode> results = com.google.android.gms.tasks.Tasks.await(
                        scanner.process(InputImage.fromBitmap(bitmap, 0)));
                if (!results.isEmpty()) return new String[]{results.get(0).getRawValue(),
                        mlKitFormat(results.get(0).getFormat())};
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally { scanner.close(); }
        } else if ("ZXING_CPP".equals(selectedDecoder)) {
            ZxingCppFrameDecoder decoder = new ZxingCppFrameDecoder();
            try { return decoder.decodeBitmapDetailed(bitmap); } finally { decoder.close(); }
        } else {
            HmsScan[] scans = ScanUtil.decodeWithBitmap(getApplicationContext(), bitmap,
                    new HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                            .setPhotoMode(true).setParseResult(true).create());
            if (scans != null && scans.length > 0) return new String[]{scans[0].getOriginalValue(),
                    hmsFormat(scans[0].getScanTypeForm())};
        }
        return null;
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private boolean successEmitted;

    private void emitSuccess(ScanResult result) {
        if (destroyed || successEmitted) return;
        successEmitted = true;
        active = false;
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(60);
        if (callback != null) callback.onSuccess(result.getText());
        android.content.Intent data = new android.content.Intent();
        data.putExtra("scan_text", result.getText());
        data.putExtra("scan_format", result.getFormat());
        data.putExtra("scan_engine", result.getEngine());
        setResult(RESULT_OK, data);
        runOnUiThread(this::finish);
    }

    private void cancelScan() {
        active = false;
        if (callback != null) callback.onCancel();
        finish();
    }

    @Override public void onBackPressed() { cancelScan(); }

    private void applyBottomInsets(BottomSheetDialog dialog, View content, boolean dark) {
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    Math.round(24 * getResources().getDisplayMetrics().density) + bottom);
            return insets;
        });
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false);
            ViewCompat.requestApplyInsets(content);
        });
    }

    private Bitmap toBitmap(ImageProxy image) {
        try {
            if (image.getFormat() != ImageFormat.YUV_420_888 || image.getPlanes().length < 3) return null;
            byte[] nv21 = yuv420ToNv21(image);
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
            byte[] jpegBytes = out.toByteArray();
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) bitmap.recycle();
                bitmap = rotated;
            }
            return bitmap;
        } catch (Exception e) { return null; }
    }

    private byte[] yuv420ToNv21(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        Rect crop = image.getCropRect();
        int width = crop.width(), height = crop.height();
        byte[] nv21 = new byte[width * height * 3 / 2];
        int pos = 0;
        ImageProxy.PlaneProxy yPlane = planes[0], uPlane = planes[1], vPlane = planes[2];
        ByteBuffer yBuffer = yPlane.getBuffer().duplicate();
        ByteBuffer uBuffer = uPlane.getBuffer().duplicate();
        ByteBuffer vBuffer = vPlane.getBuffer().duplicate();
        int rowStrideY = yPlane.getRowStride(), pixelStrideY = yPlane.getPixelStride();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int offset = (crop.top + row) * rowStrideY + (crop.left + col) * pixelStrideY;
                nv21[pos++] = yBuffer.get(offset);
            }
        }
        int rowStrideU = uPlane.getRowStride(), pixelStrideU = uPlane.getPixelStride();
        int rowStrideV = vPlane.getRowStride(), pixelStrideV = vPlane.getPixelStride();
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int uvRow = crop.top / 2 + row;
                int uvCol = crop.left / 2 + col;
                nv21[pos++] = vBuffer.get(uvRow * rowStrideV + uvCol * pixelStrideV);
                nv21[pos++] = uBuffer.get(uvRow * rowStrideU + uvCol * pixelStrideU);
            }
        }
        return nv21;
    }

    private String mlKitFormat(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE: return "QR Code";
            case Barcode.FORMAT_AZTEC: return "Aztec";
            case Barcode.FORMAT_DATA_MATRIX: return "Data Matrix";
            case Barcode.FORMAT_PDF417: return "PDF417";
            case Barcode.FORMAT_CODE_128: return "Code 128";
            case Barcode.FORMAT_CODE_39: return "Code 39";
            case Barcode.FORMAT_CODE_93: return "Code 93";
            case Barcode.FORMAT_CODABAR: return "Codabar";
            case Barcode.FORMAT_EAN_13: return "EAN-13";
            case Barcode.FORMAT_EAN_8: return "EAN-8";
            case Barcode.FORMAT_ITF: return "ITF";
            case Barcode.FORMAT_UPC_A: return "UPC-A";
            case Barcode.FORMAT_UPC_E: return "UPC-E";
            default: return FrameDecoder.FORMAT_UNKNOWN;
        }
    }

    private String hmsFormat(int format) {
        if (format == HmsScan.QRCODE_SCAN_TYPE) return "QR Code";
        if (format == HmsScan.AZTEC_SCAN_TYPE) return "Aztec";
        if (format == HmsScan.DATAMATRIX_SCAN_TYPE) return "Data Matrix";
        if (format == HmsScan.PDF417_SCAN_TYPE) return "PDF417";
        if (format == HmsScan.CODE39_SCAN_TYPE) return "Code 39";
        if (format == HmsScan.CODE93_SCAN_TYPE) return "Code 93";
        if (format == HmsScan.CODE128_SCAN_TYPE) return "Code 128";
        if (format == HmsScan.EAN13_SCAN_TYPE) return "EAN-13";
        if (format == HmsScan.EAN8_SCAN_TYPE) return "EAN-8";
        if (format == HmsScan.ITF14_SCAN_TYPE) return "ITF-14";
        if (format == HmsScan.UPCCODE_A_SCAN_TYPE) return "UPC-A";
        if (format == HmsScan.UPCCODE_E_SCAN_TYPE) return "UPC-E";
        if (format == HmsScan.CODABAR_SCAN_TYPE) return "Codabar";
        return FrameDecoder.FORMAT_UNKNOWN;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        active = false;
        if (cameraControl != null) cameraControl.enableTorch(false);
        if (cameraExecutor != null) {
            // 避免分析线程仍在使用解码器时由主线程提前释放。
            try { cameraExecutor.execute(this::releaseDecoders); } catch (java.util.concurrent.RejectedExecutionException ignored) {}
            cameraExecutor.shutdown();
        }
        if (photoExecutor != null) photoExecutor.shutdown();
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (isFinishing()) ScanCallbackHolder.clear();
        super.onDestroy();
    }
}
