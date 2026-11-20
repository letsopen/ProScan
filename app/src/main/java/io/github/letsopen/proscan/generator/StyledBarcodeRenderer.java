package io.github.letsopen.proscan.generator;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.google.zxing.common.BitMatrix;

/** 一维条码卡片渲染器：条纹宽度固定整数像素，不插值、不压缩。 */
public final class StyledBarcodeRenderer {
    private StyledBarcodeRenderer() {}

    public static Bitmap render(BitMatrix matrix, CodeStyleConfig config, int width, int height, String content) {
        BarcodeStyle style = config.getBarcodeStyle();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(false);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        float density = width / 1200f;
        RectF card = new RectF(24 * density, 24 * density, width - 24 * density, height - 24 * density);
        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(card, 24 * density, 24 * density, cardPaint);

        float cardWidth = card.width();
        // zxing 的一维输出包含其静区，按完整模块矩阵等比绘制，不改变条纹宽度比例。
        float moduleWidth = (float) Math.floor((cardWidth - 192 * density) / matrix.getWidth());
        moduleWidth = Math.max(1f, moduleWidth);
        float barsWidth = matrix.getWidth() * moduleWidth;
        float left = card.centerX() - barsWidth / 2f;
        float top = card.top + 96 * density;
        float barHeight = style == BarcodeStyle.MINIMAL ? 248 * density : 304 * density;
        float bottom = top + barHeight;
        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(style.getForeground());
        Path path = new Path();
        int runStart = -1;
        for (int x = 0; x <= matrix.getWidth(); x++) {
            boolean dark = x < matrix.getWidth() && matrix.get(x, 0);
            if (dark && runStart < 0) runStart = x;
            if (!dark && runStart >= 0) {
                float l = left + runStart * moduleWidth;
                float r = left + x * moduleWidth;
                float radius = style.getEndRadius() * density;
                if (radius == 0) {
                    canvas.drawRect(l, top, r, bottom, barPaint);
                } else {
                    path.reset();
                    RectF strip = new RectF(l, top, r, bottom);
                    path.addRoundRect(strip, new float[]{radius, radius, radius, radius,
                            radius, radius, radius, radius}, Path.Direction.CW);
                    canvas.drawPath(path, barPaint);
                }
                runStart = -1;
            }
        }

        // 明文与条纹底部保留 40px 呼吸感间距，并使用等宽字体居中。
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        textPaint.setColor(style.getTextColor());
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize((style == BarcodeStyle.MINIMAL ? 38f : 44f) * density);
        textPaint.setLetterSpacing(style == BarcodeStyle.MINIMAL ? 0.10f : 0.14f);
        String displayText = "EAN13".equals(config.getBarcodeFormat()) && content.length() == 13
                ? content.charAt(0) + "  " + content.substring(1, 7) + "  " + content.substring(7)
                : content;
        canvas.drawText(displayText, width / 2f, bottom + 96 * density, textPaint);
        return bitmap;
    }
}
