package com.example.proscan.scanner.decoder;

import android.graphics.Bitmap;

import androidx.camera.core.ImageProxy;

/** 解码器统一接口。 */
public interface FrameDecoder {
    /** 格式未知时返回此值，由界面显示通用条码标签。 */
    String FORMAT_UNKNOWN = "Barcode";

    /** 返回 null 表示没有结果；非 null 数组依次为文本、引擎真实码制。 */
    String[] decodeDetailed(ImageProxy frame);
    String[] decodeBitmapDetailed(Bitmap bitmap);

    default String decode(ImageProxy frame) {
        String[] value = decodeDetailed(frame);
        return value == null ? null : value[0];
    }

    default String decodeBitmap(Bitmap bitmap) {
        String[] value = decodeBitmapDetailed(bitmap);
        return value == null ? null : value[0];
    }

    void close();
}
