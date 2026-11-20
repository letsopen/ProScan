package com.example.proscan.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.proscan.R;

/**
 * iOS Camera 风格取景层：40% 炭黑蒙版、圆角镂空和琥珀黄 L 型四角。
 * 画笔与 Path 只在初始化时创建，绘制阶段无对象分配。
 */
public class ViewfinderView extends View {
    private static final float FRAME_WIDTH_RATIO = 0.76f;
    private static final float FRAME_HEIGHT_RATIO = 0.72f;
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();
    private final Path cornerPath = new Path();
    private float cornerLength;
    private float cornerRadius;

    public ViewfinderView(Context context) { super(context); init(); }
    public ViewfinderView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public ViewfinderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        float density = getResources().getDisplayMetrics().density;
        cornerLength = 30 * density;
        cornerRadius = 10 * density;
        cornerPaint.setColor(getResources().getColor(R.color.accent_amber, getContext().getTheme()));
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(4 * density);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** 返回相对本 View 的取景区域，供提示文字和可访问性布局使用。 */
    public RectF getFrameRect() { return new RectF(frameRect); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float frameWidth = Math.min(width * FRAME_WIDTH_RATIO, 340 * getResources().getDisplayMetrics().density);
        float frameHeight = frameWidth * FRAME_HEIGHT_RATIO;
        float left = (width - frameWidth) / 2f;
        // 略高于视觉中心，为底部操作坞与提示文字留出呼吸空间
        float top = (height - frameHeight) * 0.43f;
        frameRect.set(left, top, left + frameWidth, top + frameHeight);

        int layer = canvas.saveLayer(0, 0, width, height, null);
        canvas.drawColor(getResources().getColor(R.color.camera_scrim, getContext().getTheme()));
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRoundRect(frameRect, cornerRadius * 2, cornerRadius * 2, maskPaint);
        maskPaint.setXfermode(null);
        canvas.restoreToCount(layer);

        float l = frameRect.left;
        float t = frameRect.top;
        float r = frameRect.right;
        float b = frameRect.bottom;
        float len = Math.min(cornerLength, frameWidth * 0.18f);
        float cr = Math.min(cornerRadius, len / 2f);

        // 四角均为连续圆弧 + 两端短线，避免生硬直角和额外完整边框
        drawCorner(canvas, l, t, len, cr, false, false);
        drawCorner(canvas, r, t, len, cr, true, false);
        drawCorner(canvas, l, b, len, cr, false, true);
        drawCorner(canvas, r, b, len, cr, true, true);
    }

    private void drawCorner(Canvas canvas, float x, float y, float len, float radius,
                            boolean fromRight, boolean fromBottom) {
        float sx = fromRight ? -1f : 1f;
        float sy = fromBottom ? -1f : 1f;
        cornerPath.reset();
        cornerPath.moveTo(x + sx * len, y);
        cornerPath.lineTo(x + sx * radius, y);
        cornerPath.quadTo(x, y, x, y + sy * radius);
        cornerPath.lineTo(x, y + sy * len);
        canvas.drawPath(cornerPath, cornerPaint);
    }
}
