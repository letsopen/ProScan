package com.example.proscan.scanner.decoder;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import zxingcpp.BarcodeReader;

/**
 * ZXing-CPP 解码器（io.github.zxing-cpp:android 2.3.0）
 * <p>
 * 相机帧路径：BarcodeReader.read(ImageProxy) 原生支持 YUV_420_888，
 * 由 JNI 直接读取 Y/UV plane，无需 Java 层做色彩空间转换。
 * 位图路径：BarcodeReader.read(Bitmap) 直接解码 ARGB 位图。
 * <p>
 * 开启 tryHarder / tryRotate / tryInvert / tryDownscale 优化项。
 */
public class ZxingCppFrameDecoder implements FrameDecoder {

    private static final String TAG = "ZxingCppDecoder";

    private final BarcodeReader reader;

    public ZxingCppFrameDecoder() {
        BarcodeReader.Options options = new BarcodeReader.Options();
        // 全码制支持
        Set<BarcodeReader.Format> formats = EnumSet.of(
                BarcodeReader.Format.QR_CODE,
                BarcodeReader.Format.MICRO_QR_CODE,
                BarcodeReader.Format.RMQR_CODE,
                BarcodeReader.Format.AZTEC,
                BarcodeReader.Format.DATA_MATRIX,
                BarcodeReader.Format.PDF_417,
                BarcodeReader.Format.CODE_128,
                BarcodeReader.Format.CODE_39,
                BarcodeReader.Format.CODE_93,
                BarcodeReader.Format.CODABAR,
                BarcodeReader.Format.EAN_13,
                BarcodeReader.Format.EAN_8,
                BarcodeReader.Format.ITF,
                BarcodeReader.Format.UPC_A,
                BarcodeReader.Format.UPC_E);
        options.setFormats(formats);
        options.setTryHarder(true);
        options.setTryRotate(true);
        options.setTryInvert(true);
        options.setTryDownscale(true);
        reader = new BarcodeReader(options);
    }

    @Override
    public String decode(ImageProxy frame) {
        try {
            if (frame.getFormat() != ImageFormat.YUV_420_888) {
                return null;
            }
            List<BarcodeReader.Result> results = reader.read(frame);
            if (results != null && !results.isEmpty()) {
                String text = results.get(0).getText();
                return (text != null && !text.isEmpty()) ? text : null;
            }
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "decode frame failed: " + t.getMessage());
            return null;
        } finally {
            frame.close();
        }
    }

    @Override
    public String decodeBitmap(Bitmap bitmap) {
        try {
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }
            // Kotlin 默认参数：read(bitmap, crop=null, rotation=0)，Java 调用需显式传参
            List<BarcodeReader.Result> results = reader.read(bitmap, null, 0);
            if (results != null && !results.isEmpty()) {
                String text = results.get(0).getText();
                return (text != null && !text.isEmpty()) ? text : null;
            }
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "decode bitmap failed: " + t.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        // BarcodeReader 无显式 close，交给 GC
    }
}
