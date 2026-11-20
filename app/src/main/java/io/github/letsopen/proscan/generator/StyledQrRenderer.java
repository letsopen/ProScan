package io.github.letsopen.proscan.generator;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.google.zxing.qrcode.encoder.ByteMatrix;

/**
 * QR Canvas 渲染器：定位眼和数据模块分离，液态形态只做模块间平滑连通。
 * 不使用模糊或改变模块拓扑；四周始终保留 4 模块静区。
 */
public final class StyledQrRenderer {
    private StyledQrRenderer() {}

    public static Bitmap render(ByteMatrix matrix, CodeStyleConfig config, int size) {
        int modules = matrix.getWidth();
        float module = size / (float) (modules + 8);
        float origin = module * 4;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(false);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        QrStyle style = config.getQrStyle();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(style.getStartColor());
        if (style == QrStyle.GRADIENT) {
            paint.setShader(new LinearGradient(0, 0, size, size,
                    style.getStartColor(), style.getEndColor(), Shader.TileMode.CLAMP));
        }

        if (style == QrStyle.CLASSIC) drawClassic(canvas, matrix, origin, module, paint);
        else if (style == QrStyle.LIQUID) drawLiquid(canvas, matrix, origin, module, paint);
        else drawDots(canvas, matrix, origin, module, paint);

        // 单独绘制三个标准 7x7 定位眼：外圈 7、隔离圈 5、内芯 3。
        float finderRadius = style == QrStyle.CLASSIC ? 0 : module * 1.35f;
        drawFinder(canvas, origin, origin, module, paint, finderRadius, style);
        drawFinder(canvas, origin + (modules - 7) * module, origin, module, paint, finderRadius, style);
        drawFinder(canvas, origin, origin + (modules - 7) * module, module, paint, finderRadius, style);
        return bitmap;
    }

    private static boolean dark(ByteMatrix m, int x, int y) {
        return x >= 0 && y >= 0 && x < m.getWidth() && y < m.getHeight() && m.get(x, y) == 1;
    }

    private static boolean isFinder(int x, int y, int n) {
        return (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7);
    }

    private static void drawClassic(Canvas canvas, ByteMatrix m, float origin, float unit, Paint paint) {
        int n = m.getWidth();
        // 相邻方块直接连通；直角边界落在模块整数坐标上。
        for (int y = 0; y < n; y++) {
            int runStart = -1;
            for (int x = 0; x <= n; x++) {
                boolean dark = x < n && dark(m, x, y) && !isFinder(x, y, n);
                if (dark && runStart < 0) runStart = x;
                if (!dark && runStart >= 0) {
                    canvas.drawRect(origin + runStart * unit, origin + y * unit,
                            origin + x * unit, origin + (y + 1) * unit, paint);
                    runStart = -1;
                }
            }
        }
    }

    private static void drawDots(Canvas canvas, ByteMatrix m, float origin, float unit, Paint paint) {
        int n = m.getWidth();
        float radius = unit * 0.42f;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            if (dark(m, x, y) && !isFinder(x, y, n)) {
                canvas.drawCircle(origin + (x + 0.5f) * unit, origin + (y + 0.5f) * unit, radius, paint);
            }
        }
    }

    private static void drawLiquid(Canvas canvas, ByteMatrix m, float origin, float unit, Paint paint) {
        int n = m.getWidth();
        float radius = unit * 0.3f;
        RectF rect = new RectF();
        Path path = new Path();
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            if (!dark(m, x, y) || isFinder(x, y, n)) continue;
            float l = origin + x * unit, t = origin + y * unit;
            float r = l + unit, b = t + unit;
            boolean left = dark(m, x - 1, y), right = dark(m, x + 1, y);
            boolean up = dark(m, x, y - 1), down = dark(m, x, y + 1);
            // 无相邻模块的一侧才圆角；相邻方向使用直边和矩形桥接，避免缩窄有效数据区。
            float tl = !left && !up ? radius : 0;
            float tr = !right && !up ? radius : 0;
            float br = !right && !down ? radius : 0;
            float bl = !left && !down ? radius : 0;
            path.reset();
            rect.set(l, t, r, b);
            path.addRoundRect(rect, new float[]{tl, tl, tr, tr, br, br, bl, bl}, Path.Direction.CW);
            canvas.drawPath(path, paint);
            if (left) canvas.drawRect(l - unit * 0.1f, t, l, b, paint);
            if (up) canvas.drawRect(l, t - unit * 0.1f, r, t, paint);
        }
    }

    private static void drawFinder(Canvas canvas, float left, float top, float unit, Paint paint,
                                   float radius, QrStyle style) {
        paint.setStyle(Paint.Style.FILL);
        RectF outer = new RectF(left, top, left + 7 * unit, top + 7 * unit);
        canvas.drawRoundRect(outer, radius, radius, paint);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setColor(Color.WHITE);
        RectF middle = new RectF(left + unit, top + unit, left + 6 * unit, top + 6 * unit);
        canvas.drawRoundRect(middle, Math.max(0, radius * 0.6f), Math.max(0, radius * 0.6f), clear);
        RectF core = new RectF(left + 2 * unit, top + 2 * unit, left + 5 * unit, top + 5 * unit);
        if (style == QrStyle.DOTS || style == QrStyle.GRADIENT) {
            canvas.drawCircle(core.centerX(), core.centerY(), 1.5f * unit, paint);
        } else {
            canvas.drawRoundRect(core, radius * 0.4f, radius * 0.4f, paint);
        }
    }
}
