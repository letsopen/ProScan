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
        options.setTryHarder(true);      // 增加扫描线密度
        options.setTryRotate(true);      // 竖排条码（饮料罐常见）
        options.setTryInvert(true);      // 金属罐反色/强反光
        options.setTryDownscale(true);   // 降采样低通滤波：把反光断裂的条纹"糊"连贯

        // 二值化：瓶身受光不均，必须局部自适应阈值，不能用全局直方图
        options.setBinarizer(BarcodeReader.Binarizer.LOCAL_AVERAGE);

        // 降采样参数：高分辨率下金属反光与曲面锯齿被放大，降采样相当于低通滤波
        options.setDownscaleThreshold(500);
        options.setDownscaleFactor(2);

        // 最小命中行数降为 1：曲面反光常导致整码只有极窄一条清晰扫描线；
        // EAN-13/UPC-A/CODE-128 自带校验位，minLineCount=1 几乎不引入误识
        options.setMinLineCount(1);

        reader = new BarcodeReader(options);
    }

    @Override
    public String[] decodeDetailed(ImageProxy frame) {
        try {
            if (frame.getFormat() != ImageFormat.YUV_420_888) {
                return null;
            }
            List<BarcodeReader.Result> results = reader.read(frame);
            if (results != null && !results.isEmpty()) {
                BarcodeReader.Result result = results.get(0);
                String text = result.getText();
                return (text != null && !text.isEmpty())
                        ? new String[]{text, formatName(result.getFormat())} : null;
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
    public String[] decodeBitmapDetailed(Bitmap bitmap) {
        try {
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }
            List<BarcodeReader.Result> results = reader.read(bitmap, null, 0);
            if (results != null && !results.isEmpty()) {
                BarcodeReader.Result result = results.get(0);
                String text = result.getText();
                return (text != null && !text.isEmpty())
                        ? new String[]{text, formatName(result.getFormat())} : null;
            }
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "decode bitmap failed: " + t.getMessage());
            return null;
        }
    }

    private String formatName(BarcodeReader.Format format) {
        if (format == null) return FORMAT_UNKNOWN;
        return format.name().replace('_', '-');
    }

    @Override
    public void close() {
        // BarcodeReader 无显式 close，交给 GC
    }
}
