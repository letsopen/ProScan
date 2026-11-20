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
