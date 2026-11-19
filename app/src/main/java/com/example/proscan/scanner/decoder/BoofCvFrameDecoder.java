package com.example.proscan.scanner.decoder;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import org.ddogleg.struct.DogArray_I8;

import boofcv.alg.fiducial.aztec.AztecCode;
import boofcv.alg.fiducial.microqr.MicroQrCode;
import boofcv.alg.fiducial.qrcode.QrCode;
import boofcv.abst.fiducial.AztecCodePreciseDetector;
import boofcv.abst.fiducial.MicroQrCodePreciseDetector;
import boofcv.abst.fiducial.QrCodePreciseDetector;
import boofcv.android.ConvertBitmap;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;

/**
 * BoofCV 解码器（org.boofcv:boofcv-recognition / boofcv-android 0.44）
 * <p>
 * 支持 QR Code、Micro QR、Aztec 三种二维条码。
 * 相机帧路径：YUV_420_888 的 Y plane 本身即灰度数据，直接拷入复用的 GrayU8，
 * 避免每帧分配与 GC（仅分辨率变化时重新分配）。
 * 位图路径：ConvertBitmap.bitmapToGray 复用 GrayU8 + DogArray_I8 存储缓冲。
 */
public class BoofCvFrameDecoder implements FrameDecoder {

    private static final String TAG = "BoofCvDecoder";

    // 复用的灰度缓冲区与字节存储，避免每帧 GC
    private GrayU8 gray;
    private final DogArray_I8 pixelStore = new DogArray_I8();

    private QrCodePreciseDetector<GrayU8> qrDetector;
    private MicroQrCodePreciseDetector<GrayU8> microQrDetector;
    private AztecCodePreciseDetector<GrayU8> aztecDetector;

    private void ensureDetectors() {
        if (qrDetector == null) {
            qrDetector = FactoryFiducial.qrcode(null, GrayU8.class);
            microQrDetector = FactoryFiducial.microqr(null, GrayU8.class);
            aztecDetector = FactoryFiducial.aztec(null, GrayU8.class);
        }
    }

    /** 将 YUV_420_888 的 Y plane 拷入复用的 GrayU8（宽度对齐 rowStride 时按 stride 采样） */
    private GrayU8 yPlaneToGray(ImageProxy frame) {
        ImageProxy.PlaneProxy yPlane = frame.getPlanes()[0];
        int width = frame.getWidth();
        int height = frame.getHeight();
        int rowStride = yPlane.getRowStride();
        int pixelStride = yPlane.getPixelStride();

        if (gray == null || gray.width != width || gray.height != height) {
            gray = new GrayU8(width, height);
        }

        java.nio.ByteBuffer buf = yPlane.getBuffer();
        buf.rewind();
        byte[] row = new byte[rowStride];
        for (int rowIdx = 0; rowIdx < height; rowIdx++) {
            int offset = rowIdx * rowStride;
            buf.position(offset);
            if (offset + rowStride <= buf.capacity()) {
                buf.get(row);
            } else {
                buf.get(row, 0, buf.capacity() - offset);
            }
            if (pixelStride == 1) {
                System.arraycopy(row, 0, gray.data, rowIdx * width, width);
            } else {
                for (int col = 0; col < width; col++) {
                    gray.data[rowIdx * width + col] = row[col * pixelStride];
                }
            }
        }
        buf.rewind();
        return gray;
    }

    /** 依次尝试 QR / MicroQR / Aztec，返回第一个成功结果 */
    private String runDetectors(GrayU8 input) {
        ensureDetectors();
        try {
            qrDetector.process(input);
            for (QrCode qr : qrDetector.getDetections()) {
                if (qr.message != null && !qr.message.isEmpty()) {
                    return qr.message;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "QR detect failed: " + t.getMessage());
        }
        try {
            microQrDetector.process(input);
            for (MicroQrCode mq : microQrDetector.getDetections()) {
                if (mq.message != null && !mq.message.isEmpty()) {
                    return mq.message;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "MicroQR detect failed: " + t.getMessage());
        }
        try {
            aztecDetector.process(input);
            for (AztecCode az : aztecDetector.getDetections()) {
                if (az.message != null && !az.message.isEmpty()) {
                    return az.message;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Aztec detect failed: " + t.getMessage());
        }
        return null;
    }

    @Override
    public String decode(ImageProxy frame) {
        try {
            if (frame.getFormat() != ImageFormat.YUV_420_888) {
                return null;
            }
            GrayU8 input = yPlaneToGray(frame);
            return runDetectors(input);
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
            GrayU8 input = ConvertBitmap.bitmapToGray(bitmap, gray, pixelStore);
            gray = input;
            return runDetectors(input);
        } catch (Throwable t) {
            Log.w(TAG, "decode bitmap failed: " + t.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        gray = null;
    }
}
