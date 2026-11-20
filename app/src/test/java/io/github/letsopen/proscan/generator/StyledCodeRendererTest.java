package io.github.letsopen.proscan.generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.RGBLuminanceSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.EnumMap;
import java.util.Map;
import com.google.zxing.DecodeHintType;
import com.google.zxing.BarcodeFormat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class StyledCodeRendererTest {
    private String decode(Bitmap bitmap, BarcodeFormat expected) throws Exception {
        assertFalse(bitmap.hasAlpha());
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        RGBLuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), pixels);
        Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, true);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.Collections.singletonList(expected));
        Result result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
        assertNotNull(result);
        return result.getText();
    }

    @Test
    public void allQrStylesRoundTrip() throws Exception {
        String content = "https://github.com/letsopen/ProScan";
        for (QrStyle style : QrStyle.values()) {
            Bitmap bitmap = StyledQrRenderer.render(CodeMatrixEncoder.encodeQr(content),
                    CodeStyleConfig.qr(style), CodeStyleConfig.QR_EXPORT_SIZE);
            bitmap.compress(Bitmap.CompressFormat.PNG,100,new java.io.FileOutputStream("/opt/projects/ProScan/build/ui-preview/qr-"+style.name()+".png"));
            assertEquals(content, decode(bitmap, BarcodeFormat.QR_CODE));
            bitmap.recycle();
        }
    }

    @Test
    public void barcodeValidationUsesChineseRules() {
        assertEquals(null, BarcodeInputValidator.validate("ABC-123 xyz", "CODE128"));
        assertEquals(null, BarcodeInputValidator.validate("https://git.io/ProScan", "CODE128"));
        assertEquals(null, BarcodeInputValidator.validate("590123412345", "EAN13"));
        assertEquals(null, BarcodeInputValidator.validate("5901234123457", "EAN13"));
        org.junit.Assert.assertTrue(BarcodeInputValidator.validate("中文", "CODE128").contains("不能包含中文"));
        org.junit.Assert.assertTrue(BarcodeInputValidator.validate("ABC\n123", "CODE128").contains("换行"));
        org.junit.Assert.assertTrue(BarcodeInputValidator.validate("abc", "EAN13").contains("仅支持数字"));
        org.junit.Assert.assertTrue(BarcodeInputValidator.validate("5901234123456", "EAN13").contains("校验位"));
        org.junit.Assert.assertTrue(BarcodeInputValidator.validate("123", "EAN13").contains("12 位"));
    }

    @Test
    public void longUrlRejectedBeforeRendering() {
        String url = "https://github.com/letsopen/ProScan?query=" +
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String error = BarcodeInputValidator.validate(url, "CODE128");
        org.junit.Assert.assertNotNull(error);
        org.junit.Assert.assertTrue(error.contains("内容过长"));
        org.junit.Assert.assertTrue(error.contains("二维码"));
    }

    @Test
    public void longestCode128StillRoundTrips() throws Exception {
        for (int length = 20; length <= BarcodeInputValidator.CODE128_MAX_LENGTH; length += 6) {
        StringBuilder text = new StringBuilder();
        while (text.length() < length) text.append("PS2026");
        String content = text.substring(0, length);
        assertEquals(null, BarcodeInputValidator.validate(content, "CODE128"));
        Bitmap bitmap = StyledBarcodeRenderer.render(CodeMatrixEncoder.encodeBarcode(content, "CODE128"),
                CodeStyleConfig.barcode(BarcodeStyle.CLASSIC, "CODE128"), 1200, 600, content);
        assertEquals(content, decode(bitmap, BarcodeFormat.CODE_128));
        bitmap.recycle();
        }
    }

    @Test
    public void code128RoundTrip() throws Exception {
        String content = "PROSCAN-2026";
        Bitmap bitmap = StyledBarcodeRenderer.render(CodeMatrixEncoder.encodeBarcode(content, "CODE128"),
                CodeStyleConfig.barcode(BarcodeStyle.CLASSIC, "CODE128"), 1200, 600, content);
        assertEquals(content, decode(bitmap, BarcodeFormat.CODE_128));
        bitmap.recycle();
    }

    @Test
    public void allBarcodeStylesRoundTrip() throws Exception {
        String content = "590123412345";
        for (BarcodeStyle style : BarcodeStyle.values()) {
            Bitmap bitmap = StyledBarcodeRenderer.render(CodeMatrixEncoder.encodeBarcode(content, "EAN13"),
                    CodeStyleConfig.barcode(style, "EAN13"), 1200, 600, content);
            bitmap.compress(Bitmap.CompressFormat.PNG,100,new java.io.FileOutputStream("/opt/projects/ProScan/build/ui-preview/barcode-"+style.name()+".png"));
            assertEquals("5901234123457", decode(bitmap, BarcodeFormat.EAN_13));
            bitmap.recycle();
        }
    }
}
