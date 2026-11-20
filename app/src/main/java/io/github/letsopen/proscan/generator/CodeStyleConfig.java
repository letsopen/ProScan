package io.github.letsopen.proscan.generator;

import android.graphics.Bitmap;

/** 码图渲染统一配置，所有导出图都是不透明 RGB 位图。 */
public final class CodeStyleConfig {
    public static final int QR_EXPORT_SIZE = 1024;
    public static final int BARCODE_EXPORT_WIDTH = 1200;
    public static final int BARCODE_EXPORT_HEIGHT = 600;

    private final QrStyle qrStyle;
    private final BarcodeStyle barcodeStyle;
    private final String barcodeFormat;

    private CodeStyleConfig(QrStyle qrStyle, BarcodeStyle barcodeStyle, String barcodeFormat) {
        this.qrStyle = qrStyle;
        this.barcodeStyle = barcodeStyle;
        this.barcodeFormat = barcodeFormat;
    }
    public static CodeStyleConfig qr(QrStyle style) { return new CodeStyleConfig(style, null, null); }
    public static CodeStyleConfig barcode(BarcodeStyle style, String format) {
        return new CodeStyleConfig(null, style, format);
    }
    public QrStyle getQrStyle() { return qrStyle; }
    public BarcodeStyle getBarcodeStyle() { return barcodeStyle; }
    public String getBarcodeFormat() { return barcodeFormat; }
}
