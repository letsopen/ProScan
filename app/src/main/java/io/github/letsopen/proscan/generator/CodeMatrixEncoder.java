package io.github.letsopen.proscan.generator;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** 只做底层矩阵编码，不负责 Bitmap 像素和风格绘制。 */
public final class CodeMatrixEncoder {
    private CodeMatrixEncoder() {}

    public static ByteMatrix encodeQr(String content) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.MARGIN, 0);
        // Encoder 输出不含边距的原始模块矩阵；静区由渲染层精确保证。
        return Encoder.encode(content, ErrorCorrectionLevel.M, hints).getMatrix();
    }

    public static BitMatrix encodeBarcode(String content, String format) throws WriterException {
        BarcodeFormat barcodeFormat = "EAN13".equals(format) ? BarcodeFormat.EAN_13 : BarcodeFormat.CODE_128;
        // 原始矩阵不含界面留白；条码静区、文字和卡片由 StyledBarcodeRenderer 绘制。
        return new MultiFormatWriter().encode(content, barcodeFormat, 1, 1);
    }
}
